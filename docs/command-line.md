# Command Line (fxt-cli)

`fxt-cli` runs FreeXmlToolkit's validators, transformation engines, schema documentation and PDF
output without the user interface: in build pipelines, scripts and scheduled jobs. It uses the
same engines and the same security defaults as the application, and it is installed with it.

```bash
fxt-cli validate --xsd schema/order.xsd data/
fxt-cli transform -s report.xsl -p year=2026 data/order.xml -o report.html
fxt-cli xsd-doc schema/order.xsd -o site/
fxt-cli fop -i data/order.xml -s order2fo.xsl -o order.pdf
```

## Where it is

| Installation | Program |
|---|---|
| Windows (installer or ZIP) | `fxt-cli.exe` next to `FreeXmlToolkit.exe` in the installation folder |
| macOS | `FreeXmlToolkit.app/Contents/MacOS/fxt-cli` |
| Linux `.deb` / `.rpm` | `/opt/freexmltoolkit/bin/fxt-cli` |
| Linux ZIP | `bin/fxt-cli` in the extracted folder |

The installers do not add it to the `PATH`. Add the folder, or link the program, for example
`sudo ln -s /opt/freexmltoolkit/bin/fxt-cli /usr/local/bin/fxt-cli` on Linux. It brings its own
Java runtime; nothing else needs to be installed.

From a source checkout: `./gradlew runCli --args="validate src/test/resources"`.

`fxt-cli --help` lists the commands, `fxt-cli <command> --help` the options of one command, and
`fxt-cli --version` prints the version.

## validate

Checks XML files against an XML Schema (1.0 or 1.1) and/or a Schematron, and JSON files against
a JSON Schema, with the validators of the [Validation Panel](validation-panel.md).

```bash
fxt-cli validate [options] FILE|FOLDER...
```

| Option | Meaning |
|---|---|
| `-x`, `--xsd XSD` | XML Schema for all XML files |
| `-s`, `--schematron SCH` | Schematron, checked in addition to the schema |
| `-j`, `--json-schema SCHEMA` | JSON Schema for all JSON files (`.json`, `.json5`) |
| `--no-schema-lookup` | Do not use the schema a file declares (see below) |
| `--include GLOB,...` | File names to validate inside folders; default `*.xml,*.json` |
| `-f`, `--report-format FORMAT` | `text` (default), `json`, `junit`, `sarif` |
| `-r`, `--report FILE` | Write the report to a file; the text summary still goes to standard output |
| `--fail-on-warning` | Warnings make a file invalid too |

**Which schema is used.** With `--xsd`, that schema for every XML file. Without it, each file
is checked against the schema it declares itself, found the same way as when the file is opened
in the editor: `xsi:schemaLocation` / `xsi:noNamespaceSchemaLocation` (relative to the file,
remote schemas downloaded and cached), redirected by the [Schema Library](schema-library.md) and
XML catalogs, else the Schema Library entry for the root namespace or element. A file that names
no schema is checked for well-formedness. `--no-schema-lookup` skips the lookup.

**Folders** are searched recursively for the `--include` patterns; hidden folders (such as
`.git`) are skipped. Files named directly are always validated; `.json` and `.json5` files are
treated as JSON, everything else as XML.

### Reports

The text format prints one line per problem, in the form compilers use, and a summary:

```text
data/bad.xml:2:11: error: [XSD] cvc-datatype-valid.1.2.1: 'x' is not a valid value for 'integer'.
data/broken.xml:2:1: error: [Well-formed] XML document structures must start and end within the same entity.
data/order.xml:14: warning: [Schematron] Delivery date lies in the past (at /order/delivery)
4 files, 2 invalid, 2 error(s), 1 warning(s) (485 ms)
```

| Format | For |
|---|---|
| `json` | Scripts: summary plus every file with its findings (severity, source, line, column, message, rule, context) |
| `junit` | Test reports of CI servers (Jenkins, GitLab, Azure DevOps, GitHub Actions reporters): one test case per file, a failure per invalid file |
| `sarif` | SARIF 2.1.0 for code scanning (GitHub, Azure DevOps, GitLab): one result per problem with rule (`xsd`, `well-formed`, `schematron`, `json`, `json-schema`, `not-run`), level and position |

Paths in reports are relative to the working directory when the files lie below it.

## transform

Runs an XSLT 3.0 stylesheet, an XQuery 3.1 or an XProc 3.0 pipeline.

```bash
fxt-cli transform -s STYLESHEET [options] [INPUT...]
```

| Option | Meaning |
|---|---|
| `-s`, `--stylesheet FILE` | `.xsl`/`.xslt` (XSLT), `.xq`/`.xquery`/`.xqy`/`.xql`/`.xqm` (XQuery), `.xpl` (XProc) |
| `-l`, `--language LANG` | `xslt`, `xquery`, `xproc`, for other file extensions |
| `-p`, `--param NAME=VALUE` | Stylesheet parameter or external XQuery variable (string); repeatable |
| `-m`, `--method METHOD` | Output method `xml`, `html`, `xhtml`, `text`, `json`; default: the stylesheet's `xsl:output`, else `xml` |
| `-o`, `--output FILE` | Output file (one input) |
| `-d`, `--output-dir DIR` | Output folder for several inputs; each result is named after its input with the extension of the output method |

Without `-o` and `-d` the result goes to standard output. `xsl:message` output goes to standard
error. XSLT needs an input document; XQuery and XProc can run without one. Relative
`xsl:import`, `xsl:include` and `doc()` references resolve against the stylesheet's and the
input's folder. As in the application, stylesheets cannot call Java, and `doc()`,
`document()` and `unparsed-text()` refuse remote URLs (see [Threat Model](threat-model.md));
XProc pipelines run unrestricted.

## xsd-doc

Generates the documentation of an XML Schema, like **Generate Documentation** in the
application.

```bash
fxt-cli xsd-doc SCHEMA -o OUTPUT [-f html|pdf|docx]
```

`-o` is a folder for the HTML site and a file for PDF and Word. Without `-f` the format follows
the extension of `-o` (`.pdf`, `.docx`, otherwise HTML).

## fop

Renders a PDF with Apache FOP, like the [PDF Generator](pdf-generator.md).

```bash
fxt-cli fop -i XML -s XSL -o OUT.pdf [options]
fxt-cli fop --fo DOCUMENT.fo -o OUT.pdf [options]
```

| Option | Meaning |
|---|---|
| `-i`, `--input XML` / `-s`, `--stylesheet XSL` | XML and the stylesheet that turns it into XSL-FO |
| `--fo FO` | A finished XSL-FO document instead |
| `-o`, `--output PDF` | The PDF to write |
| `--title`, `--author`, `--subject` | PDF metadata |
| `--pdfa` | Produce PDF/A-1b |
| `--page-size`, `--orientation` | Passed to the stylesheet as the parameters `page-size` and `page-orientation` |

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success; all documents valid |
| `1` | Problems found (an invalid document), or a transformation, documentation or PDF run failed |
| `2` | Invalid command line, or an input file does not exist |
| `3` | A validation could not run: the schema or Schematron does not load, or a file is unreadable |

## Output, logging and settings

- **Standard output** carries only results and reports, so it can be piped or redirected.
  Messages, progress (`Wrote …`) and log output go to **standard error**. `-q` / `--quiet`
  leaves only problems and requested reports; `--verbose` adds the processing log.
- **Settings** are read from the same file as the application (`~/.freeXmlToolkit/FreeXmlToolkit.properties`,
  or `-Dfxt.properties.file`): the proxy, the Schema Library and its catalogs, and the XSD
  validator (Xerces or Saxon; XSD 1.1 schemas always use Xerces).
- **Network**: only what the files ask for (declared schemas, imports); see
  [Operation without Internet](offline-operation.md). The command line sends **no telemetry**
  and does not check for updates.
- **Java options** (for example `-Xmx8g` for very large files) go into the environment variable
  `JAVA_TOOL_OPTIONS`, or into `fxt-cli.cfg` next to the application's own launcher configuration.

## In a CI pipeline

GitHub Actions, uploading the SARIF report to code scanning:

```yaml
- name: Validate XML
  run: /opt/freexmltoolkit/bin/fxt-cli validate --xsd schema/order.xsd -f sarif -r fxt.sarif data/
- uses: github/codeql-action/upload-sarif@v3
  if: always()
  with:
    sarif_file: fxt.sarif
```

GitLab CI with a JUnit report:

```yaml
validate:
  script:
    - fxt-cli validate --xsd schema/order.xsd -f junit -r fxt-junit.xml data/
  artifacts:
    when: always
    reports:
      junit: fxt-junit.xml
```
