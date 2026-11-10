# Operation without Internet

FreeXmlToolkit works without an internet connection. Editing, validation against local schemas,
transformations, debugging, documentation generation, PDF output and signatures all run locally.
This page lists every feature that can contact the network, so administrators can run the
application on air-gapped machines or behind a restrictive firewall, and pre-seed what it would
otherwise download.

## Network connections

All connections use the proxy from **Settings → Network & Security → HTTP PROXY** (system,
manual or none, with authentication). Without a connection, each feature fails quietly or falls
back to its cache; the application is never blocked.

| Feature | Contacts | When | Default | Switch off | Without network |
|---|---|---|---|---|---|
| Update check | `api.github.com` (latest release) | 2 s after start; Help → About | On | Settings → Updates (`update.check.enabled=false`) | Logged only, checked again next start |
| Update download | `github.com` release assets, `api.github.com` (digest) | Only when you start an update | – | Switch off the update check | Error in the update dialog |
| Telemetry | `telemetry.status20.net` | First send 60 s after start, then every 5 min; only after the first-start notice | On | `FXT_TELEMETRY_DISABLED=1` or `DO_NOT_TRACK=1` (machine-wide), Settings → Privacy, see [Privacy & Telemetry](telemetry.md) | Queued locally (at most 5000 events / 14 days), retried with growing pauses |
| Error report | `telemetry.status20.net` | Only when you click **Send** | – | As telemetry | Error shown in the dialog |
| FundsXML extension | `api.github.com`, `codeload.github.com` (repositories `fundsxml/schema`, `fundsxml/examples`) | 5 s after start when enabled; update check at most every 24 h | **Off** | Settings → FundsXML; `fundsxml.update.check.enabled=false` | The cached copy is used |
| Schema of an opened XML file (`xsi:schemaLocation`) | The URL in the document | When the file is opened or validated | On | Map the URL to a local file (Schema Library / catalog) | Not bound; the status bar shows the schema as unresolved |
| Imported / included schemas (`xs:import`, `xs:include`) | The `schemaLocation` URL | When a schema is loaded or used for validation | On | Map the URL to a local file | Cached copy, else the import is reported as unresolved |
| Namespace-URL fallback | The namespace URI of an import without a usable location | When an import cannot be resolved otherwise | On | `-Dfxt.schema.namespaceFallback=false` | Not resolved |
| Bundled Schema Library entries | `w3.org`, `oasis-open.org`, `xbrl.org`, `web3d.org`, `schemas.xmlsoap.org`, `raw.githubusercontent.com` | On first use of the schema, then cached | On | `-Dfxt.schema.namespaceFallback=false`, or disable single entries in the Schema Library | The cached copy is used |
| JSON Schema `$schema` / `$ref` | The referenced URL | When a JSON document is validated or bound | On | Use local `$ref`s | Not resolved |
| XProc `p:http-request` and remote `href`s | The URL in the pipeline | When the pipeline runs, only with **Run XProc pipelines without restrictions** on | – | Blocked by default | Pipeline error |
| HTML preview | Remote images, scripts and styles the page refers to | When the preview is shown | – | – | Missing resources |
| Open from URL, proxy test | The URL you enter | On your action | – | – | Error message |
| AI assistant | The base URL of the provider you configured (e.g. `localhost:11434` for Ollama) | Only when you start an AI action, test a provider or load its models | **Off** | Settings → AI; `ai.allowed=false` (policy), `ai.remote.allowed=false` for local providers only | Error in the AI panel |

Not on this list because they never connect: XSLT, XQuery and XPath (also in the Query Console,
grid formulas and filters: `doc()`, `document()` and `unparsed-text()` refuse remote URLs), XSL-FO/PDF output (remote resources are refused),
XSD documentation (all assets are bundled), the user interface's icons and fonts.

The [command line](command-line.md) (`fxt-cli`) connects only for what the files ask for (the
schema rows above); it sends no telemetry, does not check for updates and does not start the
FundsXML extension. This includes `fxt-cli mcp`, the [MCP server](mcp-server.md) for AI agents: it talks to the
agent over standard input and output, opens no port and calls no AI service.

## Schemas without the internet

Give the application a local copy of every schema your documents and schemas refer to:

1. **Schema Library** (activity bar → Schema Library): add a namespace → local file mapping, or
   import an **OASIS XML catalog** whose `system`, `uri`, `rewriteSystem` or `rewriteURI` entries
   point `http(s)` locations to `file:` paths. The library is asked before any download, also
   inside imported schemas. See [Schema Library](schema-library.md).
2. **Pre-seed the cache**: run the application once on a connected machine with the same schemas,
   then copy `~/.freeXmlToolkit/cache/` and `~/.freeXmlToolkit/schema-library.json` to the
   offline machines.
3. Downloaded imports are also copied next to the schema that references them (under their
   original name, never overwriting a file). A folder prepared this way works offline. Start with
   `-Dfxt.schema.localCopies=false` to keep downloads in the cache only.

## Files and folders

| Path | Content |
|---|---|
| `~/.freeXmlToolkit/FreeXmlToolkit.properties` | All settings (another file: `-Dfxt.properties.file=<path>`) |
| `~/.freeXmlToolkit/schema-library.json` | Schema Library mappings and catalogs |
| `~/.freeXmlToolkit/cache/schemas/` | Downloaded schemas (`cache-index.json` lists them) |
| `~/.freeXmlToolkit/cache/<id>/` | Schemas from `xsi:schemaLocation` and JSON Schemas |
| `~/.freeXmlToolkit/fundsxml/` | FundsXML schemas, examples and Schematron |
| `~/.freeXmlToolkit/telemetry-queue.jsonl` | Telemetry events waiting to be sent |

`~` is the user's home folder (`%USERPROFILE%` on Windows).

## Settings for an offline machine

Put these into `FreeXmlToolkit.properties` (or a central copy, see below):

```properties
update.check.enabled=false
telemetry.usage.enabled=false
telemetry.errors.enabled=false
fundsxml.enabled=false
```

and set the environment variable `FXT_TELEMETRY_DISABLED=1` machine-wide (it also suppresses the
first-start notice; see [Privacy & Telemetry](telemetry.md#switching-it-off-for-every-user-administrators)).

For every user of a computer, and without users being able to switch them back, put these
lines into the machine-wide policy file instead (with `update.allowed=false`,
`fxt.telemetry.disabled=true` and the `fxt.schema.…` options below), see
[Administration & Policies](administration.md).

## Java options for an installed application

Options written as `-D…` on this page are Java options. In an installed application they go into
the launcher configuration in the installation's `app` folder, `FreeXmlToolkit.cfg` (on Linux
under `lib/app/`), as lines in the `[JavaOptions]` section:

```ini
[JavaOptions]
java-options=-Dfxt.schema.namespaceFallback=false
java-options=-Dfxt.properties.file=\\server\share\fxt\FreeXmlToolkit.properties
```

An update replaces this file, so software distribution should write it again after each update.
On macOS the file is part of the signed application bundle: changing it breaks the signature, so
use the [policy file](administration.md) (`fxt.…` keys) or the environment variables there instead.

See also the [Threat Model](threat-model.md) for the security side of these connections.
