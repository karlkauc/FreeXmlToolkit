# fxt-cli: transform and publish

The commands that produce something from a document: `transform`, `xsd-doc`, `xspec`, `scenario` and `fop`. Installation, exit codes and logging are on the [overview page](command-line.md).

## transform

Runs an XSLT 3.0 stylesheet, an XQuery 3.1 or an XProc 3.0 pipeline. With `--lang-version` an older XSLT or
XQuery: XSLT 1.0 runs on the JDK's XSLT 1.0 processor (no `--profile`), XSLT 2.0 and XQuery 1.0 / 3.0 fail with
every newer construct listed (exit code 1) before anything runs.

```bash
fxt-cli transform -s STYLESHEET [options] [INPUT...]
```

| Option | Meaning |
|---|---|
| `-s`, `--stylesheet FILE` | `.xsl`/`.xslt` (XSLT), `.xq`/`.xquery`/`.xqy`/`.xql`/`.xqm` (XQuery), `.xpl` (XProc) |
| `-l`, `--language LANG` | `xslt`, `xquery`, `xproc`, for other file extensions |
| `-p`, `--param NAME=VALUE` | Stylesheet parameter or external XQuery variable; the text is converted to the declared type (`as="xs:integer"`, `xs:date`, …); repeatable |
| `--initial-template [NAME]` | XSLT 3.0: start at a named template - without NAME the stylesheet's `xsl:initial-template`. No input needed (with one, it is the global context item) |
| `--initial-mode NAME` | XSLT 3.0: apply templates to the input in this mode |
| `-m`, `--method METHOD` | Output method `xml`, `html`, `xhtml`, `text`, `json`; default: the stylesheet's `xsl:output`, else `xml` |
| `-o`, `--output FILE` | Output file (one input) |
| `-d`, `--output-dir DIR` | Output folder for several inputs; each result is named after its input with the extension of the output method |
| `--profile REPORT` | XSLT with one input, or XQuery with at most one: profile the run and write the report, as JSON when REPORT ends in `.json`, else as text (`-` for standard error) |
| `--warmup N` | With `--profile`: runs before the measured one (default 1), so Java's JIT compiler has warmed up |
| `--lang-version VERSION` | XSLT `1.0`, `2.0`, `3.0` (default) or XQuery `1.0`, `3.0`, `3.1` (default); see [Language Versions](../reference/standards-versions.md) |
| `--processor NAME` | XSLT: `builtin` (the bundled Saxon HE) or the name or id of an [external XSLT processor](../guides/transform-query/external-processors.md) from *Settings → XSLT Processors*. Default: the default XSLT processor of the settings. Not with `--profile` or `--lang-version` |
| `--list-processors` | Lists the XSLT processors (`*` marks the default) and exits |

Without `-o` and `-d` the result goes to standard output. `xsl:message` output goes to standard
error. XSLT needs an input document; XQuery and XProc can run without one. Relative
`xsl:import`, `xsl:include` and `doc()` references resolve against the stylesheet's and the
input's folder. As in the application, stylesheets cannot call Java, and `doc()`,
`document()` and `unparsed-text()` refuse remote URLs (see [Threat Model](../administer/threat-model.md));
XProc pipelines may not run programs (`p:os-exec`), run computed pipelines (`p:run`), send HTTP
requests or load documents from the network, unless `security.xproc.unrestricted=true` is set in
the settings (see [Threat Model](../administer/threat-model.md)).

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

Runs [XSpec](../guides/transform-query/xspec.md) tests for XSLT, XQuery and Schematron.

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

Runs the transformation scenarios of a [project file](../guides/projects.md) — the ones saved from the
Transform panel.

```bash
fxt-cli scenario [options] PROJECT [NAME...]
```

| Option | Meaning |
|---|---|
| `-l`, `--list` | List the scenarios (name, stylesheet, input, output) instead of running them |
| `-i`, `--input FILE...` | Run for these files or folders: the scenario runs once per file |
| `--include GLOB,...` | File names to use inside `--input` folders; default `*.xml` |
| `--check` | Compare each result with the scenario's [expected result](../guides/projects.md#regression-checks) instead of only running it |
| `--accept` | Like `--check`, then write each differing or missing expected file from the result (placeholders of the old file are kept) |
| `-f`, `--report-format FORMAT` | Check report: `text` (default), `junit` (JUnit XML) or `json` |
| `-r`, `--report FILE` | Write the check report to `FILE`; the summary still goes to standard output |

Without names, all scenarios run in the order of the project file. A scenario with an output file
writes its result there; the others print it to standard output. The exit code is 1 when a scenario
fails (the others still run) and 2 for an unknown scenario name.

With `--input`, each file fills the scenario's `${file}` (and an empty input); `${name}` and
`${dir}` in the output name the result after it. Without scenario names, each file runs the
scenario the project [binds to it](../guides/projects.md#bindings):

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

## fop

Renders a PDF with Apache FOP or an [external XSL-FO processor](../guides/publish-pdf.md#external-xsl-fo-processors),
like the [PDF Generator](../guides/publish-pdf.md).

```bash
fxt-cli fop -i XML -s XSL -o OUT.pdf [options]
fxt-cli fop --fo DOCUMENT.fo -o OUT.pdf [options]
fxt-cli fop --list-processors
```

| Option | Meaning |
|---|---|
| `-i`, `--input XML` / `-s`, `--stylesheet XSL` | XML and the stylesheet that turns it into XSL-FO |
| `--fo FO` | A finished XSL-FO document instead |
| `-o`, `--output PDF` | The PDF to write |
| `--title`, `--author`, `--subject` | PDF metadata |
| `--pdfa` | Produce PDF/A-1b |
| `--page-size`, `--orientation` | Passed to the stylesheet as the parameters `page-size` and `page-orientation` |
| `-p`, `--processor NAME` | `builtin` (the bundled Apache FOP) or the name or id of an external processor from *Settings → XSL-FO Processors*. Default: the default processor of the settings |
| `--list-processors` | Lists the processors (`*` marks the default) and exits |
| `--xslt-processor NAME` | The processor of the XSLT stage (`-i` + `-s`): `builtin` (Saxon HE) or an [external XSLT processor](../guides/transform-query/external-processors.md). Default: the default XSLT processor of the settings |

The external processor's warnings and errors are printed to standard error; a failed run gives
exit code 1.
