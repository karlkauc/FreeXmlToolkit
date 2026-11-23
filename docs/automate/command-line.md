---
description: fxt-cli: validate, transform, query, compare, convert, sign and encrypt XML from the command line and in build pipelines.
---

# Command Line (fxt-cli)

`fxt-cli` runs FreeXmlToolkit's validators, transformation engines, XPath/XQuery/JSONPath queries, XML
comparison and merge, schema documentation, XML/JSON/YAML conversion, PDF output, XML signatures and XML encryption without the user interface: in build pipelines, scripts and scheduled jobs. `fxt-cli mcp` offers the same engines to AI agents. It uses the
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
fxt-cli mcp --root .
```

## Commands

| Command | What it does | Reference |
|---|---|---|
| `validate` | Well-formedness, XSD, DTD, RELAX NG, Schematron, JSON Schema; files, folders, projects | [Validate and query](cli-validate.md#validate) |
| `query` | XPath, XQuery or JSONPath against files | [Validate and query](cli-validate.md#query) |
| `lei`, `isin` | Look identifiers up at GLEIF, ESMA FIRDS and OpenFIGI | [Validate and query](cli-validate.md#lei) |
| `transform` | XSLT and XQuery, with parameters and profiling | [Transform and publish](cli-transform.md#transform) |
| `xsd-doc` | Schema documentation | [Transform and publish](cli-transform.md#xsd-doc) |
| `xspec` | Run XSpec tests | [Transform and publish](cli-transform.md#xspec) |
| `scenario` | Run or check the scenarios of a project | [Transform and publish](cli-transform.md#scenario) |
| `fop` | PDF from XSL-FO | [Transform and publish](cli-transform.md#fop) |
| `compare`, `merge`, `convert` | XML-aware compare, three-way merge, XML ↔ JSON ↔ YAML | [Compare, merge, convert](cli-compare.md) |
| `sign`, `verify`, `encrypt`, `decrypt` | XML signatures and XML Encryption | [Sign and encrypt](cli-sign.md) |
| `mcp` | Tool server for AI agents | [MCP server](mcp-server.md) |

Every command prints its options with `--help`.

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

## mcp

Serves validation, XPath/XQuery, XSLT, XSpec, schema and comparison tools to an AI agent over the Model Context
Protocol on standard input and output. Started by the agent, not by you; set-up, the tools and the safety model
are on [MCP Server](mcp-server.md).

```bash
fxt-cli mcp [--root DIR]... [--tools NAMES] [--list-tools]
```

| Option | Meaning |
|---|---|
| `--root DIR` | Folder the tools may read (repeatable; default: the current folder) |
| `--tools NAMES` | Offer only these tools, comma-separated |
| `--list-tools` | Print the tools and exit |

Standard output carries only protocol messages. The tools write no file. Exit code `0` when the client closes
the connection, `1` when the server stopped itself after calls that ran past their time limit, `2` when the
administrator policy disables it (`ai.mcp.allowed=false` or `ai.allowed=false`).

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
  [Operation without Internet](../administer/offline.md). The command line sends **no telemetry**
  and does not check for updates.
- **Java options** (for example `-Xmx8g` for very large files) go into the environment variable
  `JAVA_TOOL_OPTIONS`, or into `fxt-cli.cfg` next to the application's own launcher configuration.

## In a CI pipeline

Complete workflows for GitHub Actions and GitLab CI, with SARIF and JUnit reports, are on the
[CI recipes](ci-recipes.md) page.
