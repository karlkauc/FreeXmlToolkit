# fxt-cli: validate and query

The commands that check and read documents: `validate`, `query`, and the online identifier lookups `lei` and `isin`. Installation, exit codes and logging are on the [overview page](command-line.md).

## validate

Checks XML files against an XML Schema (1.0 or 1.1) and/or a Schematron, and JSON and YAML files
against a JSON Schema, with the validators of the [Validation Panel](../guides/validate/index.md).

```bash
fxt-cli validate [options] FILE|FOLDER...
```

| Option | Meaning |
|---|---|
| `-x`, `--xsd`, `--schema SCHEMA` | Schema for all XML files: XML Schema (`.xsd`), DTD (`.dtd`) or RELAX NG (`.rng`, `.rnc`) |
| `-s`, `--schematron SCH` | Schematron, checked in addition to the schema |
| `-j`, `--json-schema SCHEMA` | JSON Schema for all JSON (`.json`, `.jsonc`, `.json5`) and YAML (`.yaml`, `.yml`) files |
| `--schemastore` | Find the JSON Schema of a JSON or YAML file that declares none by its file name in the [SchemaStore](../guides/design-schemas/schema-library.md#4-schemastore) catalog |
| `--schemastore-url URL` | Address of the SchemaStore catalog (a mirror); default: the public catalog |
| `--no-schema-lookup` | Do not use the schema a file declares or its file name chooses (see below) |
| `--include GLOB,...` | File names to validate inside folders; default `*.xml,*.json,*.yaml,*.yml` |
| `-f`, `--report-format FORMAT` | `text` (default), `json`, `junit`, `sarif` |
| `-r`, `--report FILE` | Write the report to a file; the text summary still goes to standard output |
| `-p`, `--project FXTPROJ` | Use a [project's](../guides/projects.md) bindings (schema, Schematron, JSON Schema) and catalogs |
| `--fail-on-warning` | Warnings make a file invalid too |
| `--gleif` | Also check every LEI of the files at GLEIF ([Online Identifier Lookup](../industry/identifier-lookup.md)); sends the LEIs to `api.gleif.org` |
| `--isin` | Also check every ISIN of the files at ESMA FIRDS, OpenFIGI for the ISINs FIRDS does not know; sends the ISINs to these services |
| `--no-openfigi` | With `--isin`: ask only ESMA FIRDS |
| `--xsd-version VERSION` | `auto` (default: XSD 1.1 when the schema uses it), `1.0` or `1.1` |
| `--xml-version VERSION` | `auto` (default: as declared), `1.0` (a document declared as XML 1.1 is an error) or `1.1` |
| `--schematron-binding BINDING` | Query binding of a Schematron without `@queryBinding`: `xslt`, `xslt2` (default) or `xslt3`; with `xslt` / `xslt2` every context and test must be XPath 1.0 / 2.0 |

**Standards.** Without the three version options a run behaves as before. With them it checks against an older
standard, for example for a processor without XSD 1.1: `--xsd-version 1.0` compiles the schema as XSD 1.0 and
reports assertions and other XSD 1.1 constructs. A [project](../guides/projects.md) can name the versions per file pattern;
the options win. See [Language Versions](../reference/standards-versions.md).

**Which schema is used.** With `--xsd` / `--schema`, that schema (or DTD, or RELAX NG schema) for every XML file. With `--project`, the
schema the project binds to the file (first matching pattern), and the Schematron it binds (unless
`--schematron` is given); JSON and YAML files get the JSON Schema the project binds to them (unless
`--json-schema` is given). A JSON or YAML file without either uses the JSON Schema it declares itself:
a top-level `"$schema"` member, in YAML also a `# yaml-language-server: $schema=…` comment (relative to
the file, remote schemas downloaded and cached). A file that declares none gets the schema its **file
name** chooses: a [Schema Library](../guides/design-schemas/schema-library.md#file-names-json-and-yaml) entry's file patterns
(`docker-compose*.yml`, `.github/workflows/*.yml`), then, with `--schemastore`, the SchemaStore catalog.
A declared or chosen schema that cannot be loaded makes the file "not validated" (exit code 3).
Otherwise each XML file
is checked against the schema it declares itself, found the same way as when the file is opened
in the editor: `xsi:schemaLocation` / `xsi:noNamespaceSchemaLocation` (relative to the file,
remote schemas downloaded and cached), redirected by the [Schema Library](../guides/design-schemas/schema-library.md) and
XML catalogs, else the Schema Library entry for the root namespace or element. A file with a
`<!DOCTYPE>` or a RELAX NG `<?xml-model?>` is validated against that DTD or RELAX NG schema. A file that
names no schema is checked for well-formedness. `--no-schema-lookup` skips the lookup.

**Large files.** XML files of 64 MB or more are validated while they are read, as a stream:
memory stays flat whatever the size (a 1 GB file needs less than 20 MB). This applies to XML
Schema and well-formedness checks; with `--schematron` the file is read into memory as usual.
After 10,000 problems the file's report stops.

**Folders** are searched recursively for the `--include` patterns; hidden folders (such as
`.git`) are skipped. Files named directly are always validated; `.json`, `.jsonc` and `.json5` files
are treated as JSON, `.yaml` and `.yml` files as YAML, everything else as XML. To validate a hidden
folder, name it: `fxt-cli validate --schemastore .github/workflows`.

**SchemaStore.** `--schemastore` uses the catalog cached in `~/.freeXmlToolkit/schemastore/` and
downloads it when there is none or it is older than seven days (a failed update keeps the cached one;
without any catalog the command stops with exit code 2). Only the catalog and the schemas it names are
downloaded, through the application's proxy settings; no file name or content is sent. Settings of the
application do not switch it on for the command line; an administrator can forbid it
(`schemastore.enabled=false` in the [policy](../administer/policies.md)) or fix the catalog address
(`schemastore.catalogUrl`).

```bash
fxt-cli validate --schemastore .github/workflows docker-compose.yml   # GitHub workflows, Compose
fxt-cli validate config/                                              # JSON + YAML + XML, declared schemas
```

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
| `sarif` | SARIF 2.1.0 for code scanning (GitHub, Azure DevOps, GitLab): one result per problem with rule (`xsd`, `well-formed`, `schematron`, `json`, `yaml`, `json-schema`, `not-run`), level and position |

Paths in reports are relative to the working directory when the files lie below it.

## query

Evaluates an XPath, XQuery (3.1, or an older version with `--lang-version`) or JSONPath expression against files, like the
[Query Console](../guides/transform-query/query-console.md) of the application. Made for scripts: one result per input, and
an exit code like `grep`.

```bash
fxt-cli query -e EXPR [options] [FILE|FOLDER|-]...
fxt-cli query --query-file QUERY.xq [options] [FILE|FOLDER|-]...
```

| Option | Meaning |
|---|---|
| `-e`, `--expression EXPR` | The expression |
| `--query-file FILE` | Read the expression from a file instead (`.xq`, `.xquery`, `.xqy`, `.xql`, `.xqm`: XQuery) |
| `-l`, `--language LANG` | `xpath`, `xquery` or `jsonpath`. Default: from `--query-file`'s extension, `jsonpath` when all inputs are `.json`, `.yaml` or `.yml`, else `xpath` |
| `--var NAME=VALUE` | An external XQuery variable (string value); repeatable |
| `--lang-version VERSION` | XPath `1.0`, `2.0`, `3.0`, `3.1` (default) or XQuery `1.0`, `3.0`, `3.1` (default); see [Language Versions](../reference/standards-versions.md) |
| `--include GLOB,...` | File names to query inside folders; default `*.xml,*.json,*.yaml,*.yml` |
| `--[no-]with-filename` | Prefix each result line with its file; default: when there is more than one input |
| `-f`, `--format FORMAT` | `text` (default) or `json`: one object per input with `file`, `result`, `error`; a failed XPath / XQuery also has `code`, `kind` (`syntax`, `static`, `type`, `dynamic`, `document`), `line`, `column`, `hint` and `suggestions`; `kind` `document` means the input is not well-formed and has `documentLine` / `documentColumn` instead of a position in the query |
| `-o`, `--output FILE` | Write the results to `FILE` |

`-` as input reads standard input. An XQuery may run without input. YAML files are queried with
JSONPath on their data (several documents in one file form an array); XPath and XQuery are refused for them. XPath and XQuery run with
Saxon (the `map:`, `array:` and `math:` prefixes are declared) under the application's security
defaults: no Java calls, no remote `doc()`.

The exit code is 0 when at least one result is not empty, 1 when all results are empty, 3 when the
expression fails or an input cannot be read or is not well-formed (the other inputs still run, the
errors go to standard error).

A failing expression is reported with its error code, kind and position, the expression line with a caret
under the error, a hint for the usual cause and, for an unknown function, variable or prefix, the closest
correct names:

```text
fund.xml: error: XPTY0019 · type error · column 23
The required item type of the first operand of '/' is node(); the supplied value "EUR" is an atomic value
  distinct-values(//Ccy)/../Name
                        ^
Hint: A path step "/" was applied to a value (text, number, date) instead of a node.
      …
```

```bash
fxt-cli query -e 'count(//Position)' fund.xml                                   # 27
fxt-cli query -e '//Position[xs:decimal(TotalPercentage) gt 10]/Identifiers/ISIN/string()' data/
fxt-cli query --query-file report.xq --var year=2026 orders.xml
fxt-cli query -l xquery -e 'current-date()'
fxt-cli query -e '$.items[*].name' data.json
fxt-cli query -e '$.jobs.*.runs-on' .github/workflows/ci.yml
curl -s https://example.org/feed.xml | fxt-cli query -e '//title/string()' -
if fxt-cli query -e '//error' log.xml > /dev/null; then echo "errors found"; fi
fxt-cli query --lang-version 2.0 -e 'string-join(//ISIN, ",")' fund.xml        # fails with XPath 3.x
fxt-cli query --lang-version 1.0 -e 'count(//Position)' fund.xml               # the JDK's XPath 1.0 engine
```

## lei

Looks LEIs up at GLEIF: legal name, country, registration and entity status, next renewal. See
[Online Identifier Lookup](../industry/identifier-lookup.md).

```bash
fxt-cli lei [LEI...] [--file FILE]... [-f text|json]
```

| Option | Meaning |
|---|---|
| `LEI...` | LEIs to look up |
| `--file FILE` | Look up every LEI found in the file (repeatable) |
| `-f`, `--format FORMAT` | `text` (default) or `json` |

Exit code `0` when every LEI is issued and its entity active, `1` when one is invalid, unknown, lapsed, retired or
annulled or its entity inactive, `3` when GLEIF cannot be reached or the administrator turned lookups off
(`gleif.allowed=false`).

In a terminal the command shows which request runs in one line on stderr (cleared at the end); with stderr redirected
it prints nothing extra. The same holds for `isin` and `validate --gleif/--isin`. Synthetic example LEIs (`XMPL…`)
found in a file are listed, not looked up.

## isin

Looks ISINs up at ESMA FIRDS (name, CFI code, issuer LEI, currency, EU trading venues), the ISINs FIRDS does not know
at OpenFIGI (name, security type, ticker, FIGI). See [Online Identifier Lookup](../industry/identifier-lookup.md).

```bash
fxt-cli isin [ISIN...] [--file FILE]... [--no-openfigi] [-f text|json]
```

| Option | Meaning |
|---|---|
| `ISIN...` | ISINs to look up |
| `--file FILE` | Look up every ISIN found in the file (repeatable) |
| `--no-openfigi` | Ask only ESMA FIRDS |
| `-f`, `--format FORMAT` | `text` (default) or `json` |

An OpenFIGI API key in `FXT_OPENFIGI_APIKEY` raises OpenFIGI's limits. Exit code `0` when every ISIN is known, `1`
when one is invalid or known to neither service, `3` when a service cannot be reached or the administrator turned
lookups off (`isin.allowed=false`).
