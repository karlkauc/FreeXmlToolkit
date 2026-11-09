# Command Line (fxt-cli)

`fxt-cli` runs FreeXmlToolkit's validators, transformation engines, XPath/XQuery/JSONPath queries, XML
comparison and merge, schema documentation, XML/JSON/YAML conversion, PDF output, XML signatures and XML encryption without the user interface: in build pipelines, scripts and scheduled jobs. It uses the
same engines and the same security defaults as the application, and it is installed with it.

```bash
fxt-cli validate --xsd schema/order.xsd data/
fxt-cli transform -s report.xsl -p year=2026 data/order.xml -o report.html
fxt-cli query -e 'count(//item)' data/order.xml
fxt-cli xsd-doc schema/order.xsd -o site/
fxt-cli fop -i data/order.xml -s order2fo.xsl -o order.pdf
fxt-cli compare old/order.xml new/order.xml
fxt-cli xspec tests/price.xspec -r junit.xml
fxt-cli merge base.xml mine.xml theirs.xml -o merged.xml
fxt-cli convert data/order.xml -o order.json
fxt-cli convert -t yaml data/config.json
fxt-cli sign order.xml --keystore me.p12 --storepass-env KS_PW
fxt-cli encrypt order.xml -x //payment --cert partner.pem
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

`fxt-cli --help` lists the commands with a one-line summary each, and `fxt-cli --version` prints
the version. `fxt-cli <command> --help` (or `-h`, or `fxt-cli help <command>`) shows one command:
what it does, its parameters, its options grouped by topic with their allowed values and defaults,
and a few examples to copy. `-q` / `--quiet` and `--verbose` work with every command.

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
| `--profile REPORT` | XSLT with one input, or XQuery with at most one: profile the run and write the report, as JSON when REPORT ends in `.json`, else as text (`-` for standard error) |
| `--warmup N` | With `--profile`: runs before the measured one (default 1), so Java's JIT compiler has warmed up |

Without `-o` and `-d` the result goes to standard output. `xsl:message` output goes to standard
error. XSLT needs an input document; XQuery and XProc can run without one. Relative
`xsl:import`, `xsl:include` and `doc()` references resolve against the stylesheet's and the
input's folder. As in the application, stylesheets cannot call Java, and `doc()`,
`document()` and `unparsed-text()` refuse remote URLs (see [Threat Model](threat-model.md));
XProc pipelines may not run programs (`p:os-exec`), run computed pipelines (`p:run`), send HTTP
requests or load documents from the network, unless `security.xproc.unrestricted=true` is set in
the settings (see [Threat Model](threat-model.md)).

## query

Evaluates an XPath 3.1, XQuery 3.1 or JSONPath expression against files, like the
[Query Console](query-console.md) of the application. Made for scripts: one result per input, and
an exit code like `grep`.

```bash
fxt-cli query -e EXPR [options] [FILE|FOLDER|-]...
fxt-cli query --query-file QUERY.xq [options] [FILE|FOLDER|-]...
```

| Option | Meaning |
|---|---|
| `-e`, `--expression EXPR` | The expression |
| `--query-file FILE` | Read the expression from a file instead (`.xq`, `.xquery`, `.xqy`, `.xql`, `.xqm`: XQuery) |
| `-l`, `--language LANG` | `xpath`, `xquery` or `jsonpath`. Default: from `--query-file`'s extension, `jsonpath` when all inputs are `.json`, else `xpath` |
| `--var NAME=VALUE` | An external XQuery variable (string value); repeatable |
| `--include GLOB,...` | File names to query inside folders; default `*.xml,*.json` |
| `--[no-]with-filename` | Prefix each result line with its file; default: when there is more than one input |
| `-f`, `--format FORMAT` | `text` (default) or `json`: one object per input with `file`, `result`, `error` |
| `-o`, `--output FILE` | Write the results to `FILE` |

`-` as input reads standard input. An XQuery may run without input. XPath and XQuery run with
Saxon (the `map:`, `array:` and `math:` prefixes are declared) under the application's security
defaults: no Java calls, no remote `doc()`.

The exit code is 0 when at least one result is not empty, 1 when all results are empty, 3 when the
expression fails or an input cannot be read or is not well-formed (the other inputs still run, the
errors go to standard error).

```bash
fxt-cli query -e 'count(//Position)' fund.xml                                   # 27
fxt-cli query -e '//Position[xs:decimal(TotalPercentage) gt 10]/Identifiers/ISIN/string()' data/
fxt-cli query --query-file report.xq --var year=2026 orders.xml
fxt-cli query -l xquery -e 'current-date()'
fxt-cli query -e '$.items[*].name' data.json
curl -s https://example.org/feed.xml | fxt-cli query -e '//title/string()' -
if fxt-cli query -e '//error' log.xml > /dev/null; then echo "errors found"; fi
```

## xsd-doc

Generates the documentation of an XML Schema, like **Generate Documentation** in the
application, with the same options (and a few more for PDF).

```bash
fxt-cli xsd-doc SCHEMA -o OUTPUT [options]
fxt-cli xsd-doc SCHEMA --list-languages
```

`-o` is a folder for the HTML site and a file for PDF and Word. Without `-f` the format follows
the extension of `-o` (`.pdf`, `.docx`, otherwise HTML). Options that are on by default are
switched off with their `--no-` form (`--no-toc`). An option of another format than the chosen
one (`--watermark` for an HTML site) is ignored with a warning, so one script can share its
options between formats. `--verbose` prints the generation steps.

| Option | Meaning | Default |
|---|---|---|
| `-o`, `--output PATH` | Output folder (HTML) or file (PDF, Word) | — |
| `-f`, `--format FORMAT` | `html`, `pdf` or `docx` | from the extension |
| `--list-languages` | Print the documentation languages (`xml:lang`) of the schema and exit; `default` stands for documentation without a language | |

**Content**

| Option | Meaning | Default |
|---|---|---|
| `--language LANG,…` | Only documentation in these languages; documentation without a language is always kept | all |
| `--fallback-language LANG` | Language the HTML site shows when the reader's language is missing | |
| `--markdown MODE` | Render documentation as Markdown: `all`, `marked` (only where `xs:appinfo` sets `markdown="true"`) or `off` | `all` |
| `--[no-]type-definitions` | Type definitions in the source-code views | on |
| `--dedupe-data-dictionary` | One data-dictionary row per type instead of per usage | off |
| `--[no-]parallel` | Process the schema on several threads | on |

**Diagrams**

| Option | Meaning | Default |
|---|---|---|
| `--image-format FORMAT` | `svg`, `png` or `jpg`; in a PDF, `svg` keeps vector diagrams | `svg` |
| `--diagram-docs` | Documentation texts inside the diagrams | off |
| `--[no-]diagram-constraints` | Identity constraints and assertions on diagram cards | on |
| `--root-constraints` | Also the constraints of the root card | off |
| `--constraint-targets` | Mark the fields identity constraints point at | off |

**HTML site**

| Option | Meaning | Default |
|---|---|---|
| `--[no-]svg-overview` | Page with the diagram of the whole schema | on |
| `--[no-]metadata` | Generator, date and schema metadata | on |
| `--favicon FILE` | Favicon of the site (`.ico`, `.png`, `.svg`) | |

**PDF and Word**

| Option | Meaning | Default |
|---|---|---|
| `--page-size SIZE` | `a4`, `letter`, `legal`, `a3` (A3: PDF only, Word uses A4) | `a4` |
| `--landscape` | Landscape pages | portrait |
| `--cover-page` | Cover page | off |
| `--[no-]toc` | Table of contents | on |
| `--[no-]data-dictionary` | Data dictionary of all elements and attributes | on |
| `--[no-]schema-diagram` | Diagram of the whole schema | on |
| `--[no-]element-diagrams` | A diagram per element | on |
| `--element-reference SCOPE` | Element sections: `declarations`, `all` (every occurrence) or `off` | `declarations` |
| `--source-code` | XSD source of each component | off |

**PDF only**

| Option | Values | Default |
|---|---|---|
| `--color-scheme` | `blue`, `green`, `purple`, `grayscale`, `professional` | `blue` |
| `--watermark` | `none`, `draft`, `confidential`, `internal` | `none` |
| `--[no-]page-numbers`, `--page-number-position` | `left`, `center`, `right` | on, `center` |
| `--[no-]bookmarks` | PDF outline | on |
| `--margins` | `narrow` (15 mm), `normal` (20 mm), `wide` (30 mm) | `normal` |
| `--font`, `--font-size` | `helvetica`, `times`, `courier`; 9 to 12 points | `helvetica`, 11 |
| `--heading-style` | `blue`, `black`, `underlined` | `blue` |
| `--line-spacing` | `compact`, `normal`, `relaxed` | `normal` |
| `--header-style`, `--footer-style` | `standard`, `minimal`, `none` | `standard` |
| `--table-style` | `bordered`, `zebra`, `minimal`, `grid` | `bordered` |
| `--[no-]schema-overview`, `--[no-]complex-types`, `--[no-]simple-types` | Chapters | on |
| `--raster-scale` | Scale of `png`/`jpg` diagrams, 0.5 to 4 | 1 |
| `--title`, `--author`, `--subject`, `--keywords` | Document properties | schema name, user name |

**Word only**

| Option | Values | Default |
|---|---|---|
| `--word-header-style` | `professional`, `minimal`, `colorful` | `professional` |

Examples:

```bash
# HTML site with the German and English documentation, German as fallback
fxt-cli xsd-doc order.xsd -o site --language de,en --fallback-language de

# PDF in landscape with a draft watermark and the green colour scheme
fxt-cli xsd-doc order.xsd -o order.pdf --landscape --watermark draft --color-scheme green

# Word document without diagrams, with a cover page
fxt-cli xsd-doc order.xsd -o order.docx --cover-page --no-schema-diagram --no-element-diagrams
```

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
| `--check` | Compare each result with the scenario's [expected result](projects.md#regression-checks) instead of only running it |
| `--accept` | Like `--check`, then write each differing or missing expected file from the result (placeholders of the old file are kept) |
| `-f`, `--report-format FORMAT` | Check report: `text` (default), `junit` (JUnit XML) or `json` |
| `-r`, `--report FILE` | Write the check report to `FILE`; the summary still goes to standard output |

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

**Regression checks.** `--check` runs the scenarios that name an expected result (or the named
ones) and compares each result with it: XML by structure with the scenario's compare settings
(ignore XPaths, placeholders, tolerance), text line by line. A `${file}` scenario without
`--input` is checked for each file the project binds to it. One line per check (`passed`,
`FAILED` with the differences, `MISSING` when the expected file does not exist yet, `ERROR`), then
a summary. Exit code 0 when all match, 1 when one differs or fails, 3 when an expected file is
missing.

```bash
fxt-cli scenario orders.fxtproj --check -f junit -r build/scenarios.xml   # for CI
fxt-cli scenario orders.fxtproj Report --accept                            # new results are right
```

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
| `--ignore XPATH` | Leave out the nodes or attributes this XPath 1.0 selects, on both sides, e.g. `--ignore '//@created'`; repeatable |
| `--tolerance N` | Numbers in text or attribute values that differ by at most `N` are equal (default: exact) |
| `--[no-]placeholders` | Evaluate XMLUnit placeholders in LEFT: `${xmlunit.ignore}`, `${xmlunit.isNumber}`, `${xmlunit.isDateTime}`, `${xmlunit.matchesRegex(REGEX)}` (default: on) |
| `--bytes` | Folders: compare XML files by their bytes only |
| `-f`, `--format FORMAT` | `text` (default) or `json` |

LEFT is the expected document. With placeholders it can describe a result whose values change from
run to run (an ID, a date): see [Comparing as XML](unified-shell.md#comparing-as-xml).

For two files, the text report has one line per difference in the form
`left.xml:LINE: Attribute 'qty' changed: "1" → "2" (/order[1]/item[1]/@qty)` and a summary;
differences XMLUnit rates *similar* (a moved element, CDATA instead of text) are marked `[similar]`
(JSON: `"similar": true`) and still count. For
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

## sign

Signs an XML document (XML digital signature), like [Sign & Encrypt → Sign](digital-signatures.md).

```bash
fxt-cli sign INPUT --keystore FILE [--alias ALIAS] (--storepass[=PW] | --storepass-env VAR) [options]
```

| Option | Meaning |
|---|---|
| `--keystore FILE` | JKS or PKCS#12 keystore with the signing key |
| `--alias ALIAS` | The key's alias (default: the keystore's only key) |
| `--storepass`, `--storepass-env VAR` | The keystore password: prompted when `--storepass` has no value, or read from an environment variable (preferred in scripts) |
| `--keypass`, `--keypass-env VAR` | The key entry's password (default: the keystore password) |
| `--type TYPE` | `enveloped` (default), `enveloping` or `detached` |
| `--signature-alg ALG` | `rsa-sha256` (default for RSA keys), `rsa-sha384`, `rsa-sha512`, `rsa-pss-sha256/384/512`, `ecdsa-sha256` (default for EC keys), `ecdsa-sha384`, `ecdsa-sha512` |
| `--digest-alg ALG` | `sha256` (default), `sha384`, `sha512` |
| `--c14n METHOD` | `exclusive` (default), `exclusive-comments`, `inclusive`, `inclusive-comments`, `inclusive-11` |
| `-o FILE` | Output (default: `INPUT.signed.xml`, detached: `INPUT.sig.xml`) |

## verify

Checks XML signatures; with a trust store also the signer's certificate chain (PKIX).

```bash
fxt-cli verify INPUT... [--trust-store FILE (--storepass… ) | --trust-cacerts] [--revocation] [-f text|json]
```

Exit code `0` when every file is valid (and trusted), `1` when one is not, `3` on errors.

## encrypt

Encrypts XML according to W3C XML Encryption 1.1, like
[Sign & Encrypt → Encrypt](xml-encryption.md).

```bash
fxt-cli encrypt INPUT [-x XPATH]... [--content | --binary] RECIPIENTS... [options]
```

| Option | Meaning |
|---|---|
| `-x`, `--xpath XPATH` | Elements to encrypt (repeatable; default: the document element) |
| `--ns PREFIX=URI` | Namespace prefixes for `--xpath` (the document element's are known) |
| `--content` | Encrypt only the content of the elements |
| `--binary`, `--mime-type TYPE` | Encrypt INPUT as binary data (any file) |
| `--data-alg ALG` | Data algorithm (default `aes256-gcm`) |
| `--cert FILE` | A recipient certificate, PEM or DER (repeatable) |
| `--public-key FILE` | A recipient public key, PEM (repeatable) |
| `--keystore FILE --alias ALIAS` | Recipient certificates from a keystore (`--alias` repeatable; `--storepass…`) |
| `--key-file FILE`, `--key-name NAME` | A shared symmetric key (hex, Base64 or raw) and its KeyName |
| `--key-wrap ALG` | Key wrap for key files, passwords and key agreement; `none` uses the key file as data key |
| `--password`, `--password-env VAR` | A password recipient (PBKDF2); `--iterations N`, `--prf sha256|sha384|sha512` |
| `--key-transport ALG`, `--oaep-digest`, `--mgf` | RSA: `rsa-oaep` (default), `rsa-oaep-mgf1p`, `rsa-1_5`; OAEP digest and MGF |
| `--kdf concatkdf|hkdf`, `--kdf-digest` | Key derivation after ECDH-ES / X25519 / X448 |
| `--x509 KIND` | How a certificate is named: `issuer-serial` (default), `certificate`, `ski`, `subject`, `none` |
| `--recipient-name NAME`, `--property NAME=VALUE` | `Recipient` attribute; EncryptionProperties (repeatable) |
| `--keys-outside` | EncryptedKeys next to the data with a ReferenceList |
| `--list-algorithms` | List all algorithms with their URIs |
| `-o FILE` | Output (default: `INPUT.encrypted.xml`) |

Legacy algorithms (RSA-1_5, TripleDES, CBC modes) work but print a warning.

## decrypt

Decrypts every EncryptedData the given keys open (also several layers), or describes an encrypted
document without a key.

```bash
fxt-cli decrypt INPUT (--keystore FILE … | --private-key FILE | --key-file FILE | --password) [--id ID]... [-o FILE]
fxt-cli decrypt --inspect [-f text|json] INPUT
```

| Option | Meaning |
|---|---|
| `--keystore FILE`, `--alias`, `--storepass…`, `--keypass…` | Your private key from a keystore |
| `--private-key FILE` | Your private key as PEM (`--keypass` for an encrypted one; repeatable) |
| `--key-file FILE`, `--key-name NAME` | A shared symmetric key (repeatable) |
| `--password`, `--password-env VAR` | The document's password |
| `--id ID` | Only the EncryptedData with this Id (repeatable) |
| `--inspect`, `-f text|json` | Describe the encryption instead of decrypting |
| `-o FILE` | Output (default: `INPUT.decrypted.xml`; binary data `INPUT.decrypted.bin`) |

Exit code `0` when everything selected was decrypted, `1` when no key fits or data stays encrypted,
`3` on errors.

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
