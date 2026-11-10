# MCP Server (AI agents)

`fxt-cli mcp` makes FreeXmlToolkit's engines available to AI agents — Claude Code, and any other client of the
[Model Context Protocol](https://modelcontextprotocol.io) (MCP). The agent can then **check its own XML work**
instead of guessing: validate a document against its schema, run the XSLT or XQuery it has just written and read
the exact error with its line, run XSpec tests, look up what a schema allows at a given place, outline a large
document without reading it, and compare a result with an expected one.

The server calls no language model and sends nothing anywhere. It runs on your machine, started by the agent,
and talks to it over standard input and output — there is no network port.

## When it helps

- An agent writes or repairs a stylesheet, a query or a Schematron rule: it runs it with `xslt_transform` /
  `xquery` / `schematron_check` and gets compile errors with code and line, so it can fix them before showing you
  the result.
- An agent edits an XML document: `validate` tells it whether the document is still valid against its XSD (also
  XSD 1.1), Schematron and the checks of enabled plugins (FundsXML, ISO 20022, FinDatEx, …).
- An agent works with a schema it does not know: `schema_outline` and `schema_describe` answer what an element may
  contain, its type, facets, enumeration values and documentation.
- A document is too large to paste: `document_outline` and `xpath` explore it without loading it into the agent.

## Set-up

The server is part of every installation: it is the `mcp` command of `fxt-cli`. Where the program is on your
platform is listed in [Command Line → Where it is](command-line.md#where-it-is).

### Claude Code

In the folder you want the agent to work in:

```bash
claude mcp add fxt -- /opt/freexmltoolkit/bin/fxt-cli mcp --root .
```

Use the path of `fxt-cli` on your platform (`fxt-cli.exe` in the installation folder on Windows,
`FreeXmlToolkit.app/Contents/MacOS/fxt-cli` on macOS). `claude mcp list` shows whether the server starts. The
tools then appear as `mcp__fxt__validate`, `mcp__fxt__xslt_transform` and so on. Ask for XML work as usual
("convert this to CSV with XSLT and check it"); the agent uses the tools on its own.

### Other MCP clients

Clients that read a JSON configuration file take an entry like this:

```json
{
  "mcpServers": {
    "fxt": {
      "command": "/opt/freexmltoolkit/bin/fxt-cli",
      "args": ["mcp", "--root", "/home/me/projects/orders"]
    }
  }
}
```

### Options

| Option | Meaning |
|---|---|
| `--root DIR` | A folder the tools may read; repeatable. Default: the folder the client starts the server in. |
| `--tools NAMES` | Offer only these tools, comma-separated, e.g. `--tools validate,xpath,schema_describe`. |
| `--list-tools` | Print the tools with a one-line description and exit (no server). |
| `--verbose` | Log details to standard error (also the protocol messages, which contain the tool arguments). |

The server speaks MCP revisions 2024-11-05 to 2025-11-25. Clients of revision 2026-07-28 first ask
`server/discover`, get "method not found" and continue with the handshake of the earlier revisions.

## Safety

The tools are read-only, and code an agent passes in runs restricted:

- **Nothing is written.** No tool creates or changes a file. A stylesheet's `xsl:result-document` output is returned
  in the result instead of written; downloaded schema imports are not copied next to the schema that names them.
- **Only the `--root` folders.** File arguments outside them are refused (links are resolved first, so a link
  cannot lead outside). Stylesheets, queries and XSpec tests can read with `doc()`, `unparsed-text()`,
  `collection()`, `xsl:include` / `xsl:import` only inside them too.
- **No network from code.** Stylesheets and queries cannot reach http, https or ftp. The server itself connects only
  where a document asks for its schema (`xsi:schemaLocation`, `xs:import`), as `fxt-cli validate` does — see
  [Operation without Internet](offline-operation.md).
- **No Java, no environment.** No Java extension functions (whatever *Allow XSLT extensions* says), no Java system
  properties through `system-property()`, no environment variables through `environment-variable()`.
- **Limits.** A call stops after 30 seconds; texts in a result are cut at 64 KB (with the full length given),
  lists at 200 entries (with the total); an input file is read up to 64 MB (`document_outline` streams any size);
  a transformation may produce at most 16 million characters.
- **No telemetry.** Like every `fxt-cli` command, the server sends no usage statistics or error reports.

`--tools` narrows what an agent can do further, for example to validation only.

### Limits of the protection

- **Runaway expressions.** The time limit is checked whenever a template, function or instruction starts. A loop
  inside a single XPath expression (`count(for $i in 1 to 1e12 return $i)`) is not interrupted by it: the call is
  answered as timed out, but the calculation goes on in the background. After three such calls the server stops
  itself (exit code 1) so they cannot keep using the processor; the client starts it again.
- **Schematron in `validate`** runs like the Validation panel: its `doc()` calls are not limited to the `--root`
  folders. The Schematron itself must be a file inside them.
- **A message that is not JSON** ends the server (the MCP library closes the stream). Clients do not send such
  messages.
- An agent that can also run shell commands or edit files has those rights independently of this server.

## Administration

Administrators can switch the server off for all users with the machine-wide policy file
([Administration & Policies](administration.md)):

| Key | Effect |
|---|---|
| `ai.mcp.allowed=false` | `fxt-cli mcp` refuses to start (exit code 2) and reads nothing |
| `ai.allowed=false` | Every AI feature off, including `fxt-cli mcp` |

## Troubleshooting

| Symptom | What to check |
|---|---|
| The client shows no tools | Run the configured command with `--list-tools` in a terminal; errors go to standard error. `--verbose` logs details. |
| "… is outside the allowed folders" | Add the folder with another `--root`. Relative paths resolve against the first `--root`. |
| "disabled by the administrator policy" | The policy file sets `ai.mcp.allowed` or `ai.allowed` to `false`. |
| A call times out | The input is too large or the code loops; for large documents use `document_outline` and `xpath` on a path. |
| The server ends after several timeouts | Three calls kept running past their time limit (see above); the client restarts it. |

## Tool reference

Every input that names a document comes as a pair: `…_path` (a file inside the allowed folders) **or**
`…_content` (the text itself) — never both. Passing content is how an agent checks what it has just written
without saving it. A tool whose checked artefact fails (invalid document, stylesheet that does not compile, test
that fails) answers with `isError: true` and the details; the same facts come as structured content and as JSON
text. Misuse (an unknown argument, a path outside the folders) is answered with an error message naming the
argument.

### `validate`

Validate an XML document: well-formedness, then the schema (XSD 1.0/1.1, DTD or RELAX NG), then an optional Schematron, then the checks of enabled plugins (e.g. FundsXML, ISO 20022). The schema is xsd_path if given, else the one the document declares (xsi:schemaLocation, DOCTYPE, Schema Library) unless declared_schema is false. Returns every problem with source, severity, line, column and message; 'valid' is true when the run had no errors (warnings allowed). 'not_run' means the schema or Schematron itself could not be loaded, so the document was not checked. Use it after every change to a document.

| Argument | Type | Meaning |
|---|---|---|
| `xml_path` | string | Path of the XML document (inside the allowed folders). Give this or xml_content, not both. |
| `xml_content` | string | The XML document as text. Give this or xml_path, not both. |
| `xsd_path` | string | Path of the schema (XSD, DTD, .rng or .rnc) to validate against. |
| `schematron_path` | string | Path of a Schematron (.sch) to check after the schema. |
| `declared_schema` | boolean | Use the schema the document declares when xsd_path is not given. (default `true`) |

**Result:** `valid`, `not_run` (the schema or Schematron itself did not load) and `not_run_reason`, `schema` (the XSD used), `error_count`, `warning_count`, `total`, `truncated`, `problems[]` = `{source, severity, line, column, message, rule, context}` — `source` is `Well-formed`, `XSD`, `Schematron` or a plugin's name; at most 200 problems are listed, `total` counts all.

### `xpath`

Evaluate an XPath 3.1 expression against an XML document and return the items, one per line: atomic values as text, attributes as name="value", elements serialized as XML. The prefixes declared on the root element are available; pass 'namespaces' for others, with the key "" for the default element namespace (or use *:name). Errors come back with code and phase. Reads only inside the allowed folders. Use it to inspect large documents instead of reading them whole.

| Argument | Type | Meaning |
|---|---|---|
| `xml_path` | string | Path of the XML document (inside the allowed folders). Give this or xml_content, not both. |
| `xml_content` | string | The XML document as text. Give this or xml_path, not both. |
| `expression` | string | The XPath 3.1 expression; the document node is the context item. (required) |
| `namespaces` | object of strings | Prefix → namespace URI; the key "" sets the default element namespace. |

**Result:** `ok`, `result` (the items, one per line; cut at 64 KB with the full length), `truncated`, `item_count`, `timed_out`, `error` = `{code, phase, line, message}` with phase `compile`, `input` or `runtime`.

### `xquery`

Run an XQuery 3.1 query, with an XML document as the context item when one is given. The result sequence is serialized as XML (maps, arrays and attributes adaptively); 'item_count' says how many items it has. External variables are passed as strings in 'variables' (declare them with 'declare variable $name external;'). Static and dynamic errors come back with code, phase and line. Reads only inside the allowed folders; stops at the time limit.

| Argument | Type | Meaning |
|---|---|---|
| `xml_path` | string | Path of the XML document used as the context item (optional) (inside the allowed folders). Give this or xml_content, not both. |
| `xml_content` | string | The XML document used as the context item (optional) as text. Give this or xml_path, not both. |
| `query_path` | string | Path of the XQuery (inside the allowed folders). Give this or query_content, not both. |
| `query_content` | string | The XQuery as text. Give this or query_path, not both. |
| `variables` | object of strings | External variables: name → string value. |

**Result:** as `xpath`.

### `xslt_transform`

Run an XSLT stylesheet (1.0, 2.0 or 3.0, Saxon) on an XML document and return the output. Pass the stylesheet you just wrote as xslt_content to check it without saving it. Nothing is written to disk: xsl:result-document output comes back in 'result_documents'. xsl:message output is in 'messages'. A stylesheet that does not compile or fails at run time returns 'error' with code, phase (compile, input, runtime) and line — fix the stylesheet and call again. No Java extensions, no network, reads only inside the allowed folders, stops at the time limit.

| Argument | Type | Meaning |
|---|---|---|
| `xml_path` | string | Path of the XML source document (inside the allowed folders). Give this or xml_content, not both. |
| `xml_content` | string | The XML source document as text. Give this or xml_path, not both. |
| `xslt_path` | string | Path of the XSLT stylesheet (inside the allowed folders). Give this or xslt_content, not both. |
| `xslt_content` | string | The XSLT stylesheet as text. Give this or xslt_path, not both. |
| `parameters` | object of strings | Stylesheet parameters: name → string value. |
| `output` | `xml` \| `html` \| `xhtml` \| `text` \| `json` | Output method; by default the one xsl:output declares. |

**Result:** `ok`, `output_method`, `output`, `truncated`, `result_documents` (resolved URI → text; nothing is written), `messages[]` = `{level, text, line}` (`xsl:message`), `timed_out`, `error` = `{code, phase, line, message}`.

### `xspec_run`

Run XSpec tests (for an XSLT stylesheet, an XQuery module or a Schematron) and return every scenario and test with label, status (passed, failed, pending) and, for failures, the expected and actual result. Relative references in the XSpec (stylesheet=, query-at=, schematron=, x:context href) resolve against the XSpec file; for inline xspec_content give base_path, a path inside the allowed folders that stands for where the XSpec would be. 'passed' is true when no test failed.

| Argument | Type | Meaning |
|---|---|---|
| `xspec_path` | string | Path of the XSpec description (inside the allowed folders). Give this or xspec_content, not both. |
| `xspec_content` | string | The XSpec description as text. Give this or xspec_path, not both. |
| `base_path` | string | For xspec_content: the file path relative references resolve against. |

**Result:** `kind` (`xslt`, `xquery`, `schematron`), `passed`, `summary` ("3 tests: 2 passed, 1 failed, 0 pending"), `scenarios[]` = `{label, pending, tests[] = {label, status, expected, actual}, scenarios[]}` (expected/actual only for failed tests), `timed_out`, `error` (the tests did not run).

### `document_outline`

Outline an XML document without reading it whole: root element, namespace declarations, declared schema, size, element count, depth, and every distinct element path (/a/b, no positions) with how often it occurs, its attribute names and one sample value. Works on files of any size (streamed). Use it first on an unknown or large document, then xpath for details.

| Argument | Type | Meaning |
|---|---|---|
| `xml_path` | string | Path of the XML document (inside the allowed folders). Give this or xml_content, not both. |
| `xml_content` | string | The XML document as text. Give this or xml_path, not both. |
| `max_paths` | integer | Distinct paths returned at most. (default `200`, 1–5000) |
| `samples` | boolean | Include one sample value per path. (default `true`) |

**Result:** `root`, `namespaces` (prefix → URI; `""` = default namespace), `declared_schema`, `size_bytes`, `element_count`, `max_depth`, `paths[]` = `{path, count, attributes[], sample}`, `truncated`. With `samples: false` no value of the document appears in the result.

### `schema_outline`

Outline an XSD: target namespace, global elements, named types and imports. With 'element' (a global element name) also its content tree to 'depth' levels: name, type, min, max ("unbounded"), children; 'choice': true marks alternatives, 'more': true a cut subtree, 'recursive': true a type that repeats. Start here to learn a schema, then use schema_describe for one place in detail.

| Argument | Type | Meaning |
|---|---|---|
| `xsd_path` | string | Path of the XSD. (required) |
| `element` | string | A global element whose content tree is returned. |
| `depth` | integer | Levels of the tree below the element. (default `2`, 1–6) |

**Result:** `target_namespace`, `global_elements[]`, `named_types[]` = `{name, kind}` (`complex` / `simple`), `imports[]` = `{namespace, location}`, and with `element` a `tree` of `{name, type, min, max, children[]}` — `max` is a number or `"unbounded"`; `choice: true` marks alternatives, `more: true` a cut subtree, `recursive: true` a type that already occurs above.

### `schema_describe`

Describe what an XSD declares for one place in an instance document, given as a path of element names from the root (/Order/Item/Price; prefixes and [n] are ignored), or an attribute of that element: type, built-in base type, min/max occurrences, nillable, default/fixed, facets, enumeration values, documentation, allowed child elements (choice alternatives marked) and attributes, and the file it is declared in when that is an included or imported schema. If the path does not exist, 'found' is false and the nearest path that does is returned with its children.

| Argument | Type | Meaning |
|---|---|---|
| `xsd_path` | string | Path of the XSD. (required) |
| `instance_path` | string | Element path from the document root, e.g. /Order/Item/Price. (required) |
| `attribute` | string | Name of an attribute of that element to describe instead. |

**Result:** `found`, `kind` (`element` / `attribute`), `name`, `type`, `builtin_base`, `min`, `max`, `nillable` / `use`, `default`, `fixed`, `facets` (name → value, or a list when repeated), `enumeration[]`, `documentation[]` = `{lang, text}`, `children[]` = `{name, type, min, max, choice}`, `attributes[]` = `{name, type, use}`, `declared_in` (the file, for a component of an included or imported schema). When nothing is declared at the path: `found: false`, `nearest_known_path` and `children_of_nearest`.

### `sample_xml`

Generate a sample XML instance from an XSD, for its first global element: all elements or only the mandatory ones, repeating elements up to max_occurrences, with realistic values (names, dates, amounts matching the types and facets) or placeholders. The same seed gives the same sample. Use it as test input for a stylesheet or a query.

| Argument | Type | Meaning |
|---|---|---|
| `xsd_path` | string | Path of the XSD. (required) |
| `mandatory_only` | boolean | Only elements and attributes that are required. (default `false`) |
| `max_occurrences` | integer | Repetitions of repeating elements. (default `2`, 1–10) |
| `realistic` | boolean | Realistic values instead of placeholders. (default `true`) |
| `seed` | integer | Seed for a reproducible sample. |

**Result:** `ok`, `xml`, `truncated`, `error`.

### `schematron_check`

Check a Schematron schema without running it: XML well-formedness, structure (schema, pattern, rule, assert/report), the XPath syntax of every context and test, and common mistakes. Returns the issues with severity, line and message; 'ok' is true when there is no error. Use it after writing or changing a Schematron, then validate a document with it.

| Argument | Type | Meaning |
|---|---|---|
| `schematron_path` | string | Path of the Schematron schema (inside the allowed folders). Give this or schematron_content, not both. |
| `schematron_content` | string | The Schematron schema as text. Give this or schematron_path, not both. |

**Result:** `ok` (no error), `total`, `issues[]` = `{severity, line, message}`, `error` (the check could not run).

### `compare`

Compare two XML documents structurally (not as text): every added, removed, changed or moved node with its XPath on both sides, line numbers and values. 'equivalent' is true when nothing differs. Whitespace between elements is ignored by default; options ignore comments, element order, the nodes matched by ignore_xpaths, and numeric differences up to numeric_tolerance. Use it to check a transformation's output against an expected result.

| Argument | Type | Meaning |
|---|---|---|
| `left_path` | string | Path of the left (expected) document (inside the allowed folders). Give this or left_content, not both. |
| `left_content` | string | The left (expected) document as text. Give this or left_path, not both. |
| `right_path` | string | Path of the right (actual) document (inside the allowed folders). Give this or right_content, not both. |
| `right_content` | string | The right (actual) document as text. Give this or right_path, not both. |
| `ignore_whitespace` | boolean | Trim text and collapse whitespace. (default `true`) |
| `ignore_comments` | boolean | Ignore comments. (default `false`) |
| `ignore_order` | boolean | Match child elements regardless of their order. (default `false`) |
| `ignore_xpaths` | array of strings | XPaths of nodes to leave out on both sides. |
| `numeric_tolerance` | number | Numbers that differ by at most this much count as equal. |

**Result:** `equivalent`, `total`, `truncated`, `differences[]` = `{change, kind, name, left_xpath, right_xpath, left_value, right_value, left_line, right_line, similar, description}` (`change`: `added`, `removed`, `changed`, `moved`), `left_error` / `right_error` when a document could not be read.
