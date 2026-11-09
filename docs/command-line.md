# Command Line (fxt-cli)

`fxt-cli` runs FreeXmlToolkit's validators, transformation engines, XML comparison and merge, schema
documentation, XML/JSON/YAML conversion and PDF output without the user interface: in build pipelines, scripts and scheduled jobs. It uses the
same engines and the same security defaults as the application, and it is installed with it.

```bash
fxt-cli validate --xsd schema/order.xsd data/
fxt-cli transform -s report.xsl -p year=2026 data/order.xml -o report.html
fxt-cli xsd-doc schema/order.xsd -o site/
fxt-cli fop -i data/order.xml -s order2fo.xsl -o order.pdf
fxt-cli compare old/order.xml new/order.xml
fxt-cli xspec tests/price.xspec -r junit.xml
fxt-cli merge base.xml mine.xml theirs.xml -o merged.xml
fxt-cli convert data/order.xml -o order.json
fxt-cli convert -t yaml data/config.json
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
| `-x`, `--xsd`, `--schema SCHEMA` | Schema for all XML files: XML Schema (`.xsd`), DTD (`.dtd`) or RELAX NG (`.rng`, `.rnc`) |
| `-s`, `--schematron SCH` | Schematron, checked in addition to the schema |
| `-j`, `--json-schema SCHEMA` | JSON Schema for all JSON files (`.json`, `.json5`) |
| `--no-schema-lookup` | Do not use the schema a file declares (see below) |
| `--include GLOB,...` | File names to validate inside folders; default `*.xml,*.json` |
| `-f`, `--report-format FORMAT` | `text` (default), `json`, `junit`, `sarif` |
| `-r`, `--report FILE` | Write the report to a file; the text summary still goes to standard output |
| `-p`, `--project FXTPROJ` | Use a [project's](projects.md) bindings (schema, Schematron, JSON Schema) and catalogs |
| `--fail-on-warning` | Warnings make a file invalid too |

**Which schema is used.** With `--xsd` / `--schema`, that schema (or DTD, or RELAX NG schema) for every XML file. With `--project`, the
schema the project binds to the file (first matching pattern), and the Schematron it binds (unless
`--schematron` is given); JSON files get the JSON Schema the project binds to them (unless
`--json-schema` is given). Otherwise each file
is checked against the schema it declares itself, found the same way as when the file is opened
in the editor: `xsi:schemaLocation` / `xsi:noNamespaceSchemaLocation` (relative to the file,
remote schemas downloaded and cached), redirected by the [Schema Library](schema-library.md) and
XML catalogs, else the Schema Library entry for the root namespace or element. A file with a
`<!DOCTYPE>` or a RELAX NG `<?xml-model?>` is validated against that DTD or RELAX NG schema. A file that
names no schema is checked for well-formedness. `--no-schema-lookup` skips the lookup.

**Large files.** XML files of 64 MB or more are validated while they are read, as a stream:
memory stays flat whatever the size (a 1 GB file needs less than 20 MB). This applies to XML
Schema and well-formedness checks; with `--schematron` the file is read into memory as usual.
After 10,000 problems the file's report stops.

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
| `--profile REPORT` | XSLT with one input: profile the run and write the report, as JSON when REPORT ends in `.json`, else as text (`-` for standard error) |
| `--warmup N` | With `--profile`: runs before the measured one (default 1), so Java's JIT compiler has warmed up |

Without `-o` and `-d` the result goes to standard output. `xsl:message` output goes to standard
error. XSLT needs an input document; XQuery and XProc can run without one. Relative
`xsl:import`, `xsl:include` and `doc()` references resolve against the stylesheet's and the
input's folder. As in the application, stylesheets cannot call Java, and `doc()`,
`document()` and `unparsed-text()` refuse remote URLs (see [Threat Model](threat-model.md));
XProc pipelines may not run programs (`p:os-exec`), run computed pipelines (`p:run`), send HTTP
requests or load documents from the network, unless `security.xproc.unrestricted=true` is set in
the settings (see [Threat Model](threat-model.md)).

## xsd-doc

Generates the documentation of an XML Schema, like **Generate Documentation** in the
application.

```bash
fxt-cli xsd-doc SCHEMA -o OUTPUT [-f html|pdf|docx]
```

`-o` is a folder for the HTML site and a file for PDF and Word. Without `-f` the format follows
the extension of `-o` (`.pdf`, `.docx`, otherwise HTML).

## xspec

Runs [XSpec](xslt-developer.md#testing-with-xspec) tests for XSLT, XQuery and Schematron.

```bash
fxt-cli xspec [options] XSPEC...
```

| Option | Meaning |
|---|---|
| `-r`, `--report FILE` | Write a report for the (single) XSpec file |
| `-f`, `--report-format FORMAT` | `junit` (default, for CI servers), `html` (XSpec's report) or `xml` (XSpec's XML report) |

Every failed test is printed with its scenario, the expected and the actual result; `-q` leaves out
the passed and pending ones. The exit code is 0 when all tests pass, 1 when a test fails and 3 when
the tests cannot run (the XSpec file or the code it tests does not compile).

## scenario

Runs the transformation scenarios of a [project file](projects.md) — the ones saved from the
Transform panel.

```bash
fxt-cli scenario [options] PROJECT [NAME...]
```

| Option | Meaning |
|---|---|
| `-l`, `--list` | List the scenarios (name, stylesheet, input, output) instead of running them |
| `-i`, `--input FILE...` | Run for these files or folders: the scenario runs once per file |
| `--include GLOB,...` | File names to use inside `--input` folders; default `*.xml` |

Without names, all scenarios run in the order of the project file. A scenario with an output file
writes its result there; the others print it to standard output. The exit code is 1 when a scenario
fails (the others still run) and 2 for an unknown scenario name.

With `--input`, each file fills the scenario's `${file}` (and an empty input); `${name}` and
`${dir}` in the output name the result after it. Without scenario names, each file runs the
scenario the project [binds to it](projects.md#bindings):

```bash
fxt-cli scenario orders.fxtproj Report --input data/      # Report for every XML file in data/
fxt-cli scenario orders.fxtproj -i data/a.xml data/b.xml  # each file's bound scenario
```

A file without a bound scenario, or one whose output an earlier file already wrote (no `${name}`
in the output), counts as failed.

## compare

Compares two XML files by structure, or two folders file by file, like **Compare with File…** in
XML mode and **Compare Folders…** in the application (see
[Compare & Merge](unified-shell.md#comparing-as-xml)). Formatting, attribute order and namespace
prefixes do not count.

```bash
fxt-cli compare [options] LEFT RIGHT
```

| Option | Meaning |
|---|---|
| `--exact-whitespace` | Whitespace in text counts (default: trimmed, runs collapsed, whitespace between elements ignored) |
| `--ignore-comments` | Do not compare comments |
| `--ignore-order` | Pair elements regardless of their position |
| `--prefixes` | Also report changed namespace prefixes |
| `-k`, `--key ATTR,...` | Attributes that identify an element among same-named siblings, e.g. `-k id` |
| `--bytes` | Folders: compare XML files by their bytes only |
| `-f`, `--format FORMAT` | `text` (default) or `json` |

For two files, the text report has one line per difference in the form
`left.xml:LINE: Attribute 'qty' changed: "1" → "2" (/order[1]/item[1]/@qty)` and a summary. For
two folders it lists every file that differs or exists on one side only, then the counts. The exit
code is 0 when the files are equivalent or the folders match, 1 when they differ, 3 when a file is
not well-formed XML.

## merge

Three-way merge of a base and two changed versions, like **Tools → Merge Files…** (see
[Merging three files](unified-shell.md#merging-three-files)). Conflicts are written with Git's
markers.

```bash
fxt-cli merge [options] BASE LEFT RIGHT
```

| Option | Meaning |
|---|---|
| `-o`, `--output FILE` | Result file (may be LEFT); without it the result goes to standard output |
| `-L`, `--label NAME` | Names in the conflict markers, up to three in the order LEFT, BASE, RIGHT |
| `--no-base` | Leave the base part (`|||||||`) out of conflict blocks |
| `--exact-whitespace` | For XML: changes that differ only in whitespace are a conflict too |

The exit code is 0 without conflicts and 1 with conflicts (the result then contains the markers).
A merge without conflicts whose XML is not well-formed gives a warning on standard error.

As a Git merge driver for XML files, in `.git/config` or `~/.gitconfig`:

```ini
[merge "fxt"]
    name = FreeXmlToolkit XML merge
    driver = fxt-cli merge -q -L ours -L base -L theirs %O %A %B -o %A
```

and in `.gitattributes`: `*.xml merge=fxt`.

## convert

Converts between XML, JSON and YAML, like **Tools → Convert to JSON… / XML… / YAML…** in the
application. The mappings are described in the
[JSON Editor](json-editor.md#converting-between-xml-json-and-yaml) chapter.

```bash
fxt-cli convert [options] INPUT...
```

| Option | Meaning |
|---|---|
| `-t`, `--to FORMAT` | `json`, `xml` or `yaml`; default: `json` for XML input, `xml` for JSON and YAML input |
| `--from FORMAT` | `xml`, `json`, `yaml`; default: by extension (`.json`/`.jsonc`/`.json5`, `.yaml`/`.yml`, everything else XML) |
| `-m`, `--mapping MAPPING` | `structured` (default) or `w3c` (XPath 3.1 `fn:json-to-xml` vocabulary, lossless) |
| `--typed` | XML to JSON or YAML: numbers and `true`/`false` become numbers and booleans |
| `--root NAME` | JSON to XML: root element when the JSON does not name one; default `json` |
| `--indent N` | Spaces per indentation level, `0` for compact output; default 2 |
| `-o`, `--output FILE` | Output file (one input) |
| `-d`, `--output-dir DIR` | Output folder for several inputs; each result is named after its input with the new extension |

Without `-o` and `-d` the result goes to standard output. A file that cannot be converted
(not well-formed, not valid JSON or YAML, not in the W3C vocabulary with `-m w3c`) is reported on
standard error and gives exit code 1; the other files are still converted.

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
| `1` | Problems found (an invalid document, a difference), or a transformation, conversion, documentation or PDF run failed |
| `2` | Invalid command line, or an input file does not exist |
| `3` | A validation or comparison could not run: the schema or Schematron does not load, a file is unreadable or not well-formed |

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
