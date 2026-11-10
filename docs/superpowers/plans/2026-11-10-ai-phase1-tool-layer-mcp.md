# AI Phase 1 — Tool Layer and MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the application's validation, query, transformation, test and schema engines as sandboxed, read-only tools, and serve them to external agents over the Model Context Protocol with `fxt-cli mcp` (stdio). No language model is called by the application in this phase.

**Architecture:** A UI-free tool layer in `service/ai/tool/` wraps the existing runners (`ValidationRunner`, `TransformRunner`, `XSpecRunner`, `SampleXmlRunner`, `SchematronCheckRunner`, `XmlStructuralDiff`, `XsdNodeFactory` + `XsdInstanceLocator` + `SchemaFacets`). `service/ai/mcp/McpToolServer` adapts a `ToolRegistry` to MCP; `cli/McpCommand` is the picocli subcommand. The tool layer knows nothing about MCP, so Phase 2/3 reuse it for the in-app agent.

**Tech Stack:** Java 25 (preview) / Gradle 9 Kotlin DSL, Gson 2.15 (already a dependency) for tool arguments and results, picocli 4.7.7, JUnit 6. Run single tests with `./gradlew test --tests "<exact.ClassName>"` (exact class name, no leading wildcard).

**Spec:** `docs/superpowers/specs/2026-11-10-ai-integration-design.md` (decisions D2, D3, D4, D11, D12; sections "Tool layer", "Sandbox and limits", "MCP server").

**Rules:** `.claude/rules/ai-integration.md`.

## Global Constraints

- `service/ai/**` imports no `javafx.*` and no MCP SDK type outside `service/ai/mcp/`. `AiLayeringGuardTest` (Task 2) pins both.
- No HTTP outside `ConnectionServiceImpl` (`HttpCallsGuardTest` stays green). This phase opens no connection of its own.
- All Phase 1 tools are read-only: they write no file. `xsl:result-document` output is captured in memory (Task 3).
- File arguments are resolved only through `WorkspaceAccess`; a path outside the roots is refused.
- stdout of `fxt-cli mcp` carries protocol messages only. Logs go to stderr (`log4j2-cli.xml`), as for every `fxt-cli` command.
- A failing stylesheet, query or validation is a **result** (`ToolResult.error == true` with code, line, message), not an exception.
- Tool names, argument names and result field names are public API from the first release: `snake_case`, documented in `docs/mcp-server.md`, covered by a snapshot test (Task 8).
- All text English; JavaDoc English; new user-facing CLI texts follow `cli/CliHelp` (one-line `header`, examples as `footer`).
- Commit after every task with a conventional message (`feat(ai): …`, `test(ai): …`, `docs(ai): …`).

## Review Focus

1. **A stylesheet that never terminates.** `XsltTransformationEngine.setTransformationTimeout` only logs. A tool call must return within `ToolLimits.timeout`. Evaluate (a) a deadline check in a trace listener that throws, like the debugger's `XsltDebugStopException`, against (b) running on a worker thread that is abandoned on timeout, with the process exiting after N abandoned workers. Pin the chosen behaviour with a test using an infinite `xsl:iterate` / recursive template. (Task 3)
2. **`xsl:result-document` with an absolute path** must not create a file — also when the stylesheet comes from a file under a root. (Task 3)
3. **Symlink out of the root** and `..` segments: `WorkspaceAccess` resolves with `toRealPath` before comparing. Non-existing files: check the nearest existing parent. (Task 2)
4. **Anything printed to stdout by a library** during a tool call must not reach the protocol stream. (Task 7, `StdoutGuardTest`)
5. **Huge results:** `//*` on a 50 MB document. Output is truncated at `ToolLimits.maxOutputChars` with the full length reported; memory stays bounded where the runner allows it. (Task 4)
6. **Schema with circular type references** in `schema_outline` / `schema_describe` (see CLAUDE.md "XSD Infinite Loops"): depth cap and visited set. (Task 5)
7. **Policy:** `ai.allowed=false` or `ai.mcp.allowed=false` → `fxt-cli mcp` exits with `ExitCodes.USAGE` before reading stdin. (Task 7)

---

### Task 1: Decide the MCP implementation (D4) — time-boxed spike

**Files:**
- Create: `docs/superpowers/reports/2026-11-10-mcp-implementation-decision.md`
- Modify (only if the SDK is chosen): `build.gradle.kts` (dependencies block near line 190, next to picocli)

**Steps:**

- [x] **Step 1:** Read the current MCP specification (https://modelcontextprotocol.io/specification — find the latest revision; at planning time it was reported as `2026-07-28`). Note: lifecycle / version negotiation, the stdio transport framing, `tools/list`, `tools/call`, structured tool results, error model. Write down which revisions a server should accept from older clients.
- [x] **Step 2:** Add the official Java SDK on a scratch branch (`io.modelcontextprotocol.sdk`, modules `mcp-core` plus one JSON module; check Maven Central for the latest **non-milestone** release) and run `./gradlew dependencies --configuration runtimeClasspath`. Record: new transitive artifacts, their licences, total jar size, whether the Jackson version it needs agrees with the pinned `tools.jackson.core` 3.x / `jackson-bom` in `build.gradle.kts`, whether it works on Java 25 with `--enable-preview`, and which spec revisions it negotiates.
- [x] **Step 3:** Decide with these criteria — choose the **SDK** when all hold: a stable release exists; it adds no logging backend and no second JSON stack that conflicts with the pinned one; added size is under ~5 MB; a stdio server with a synchronous tool handler is under ~80 lines of our code. Otherwise choose the **own implementation** (JSON-RPC 2.0 over newline-delimited stdio on Gson: initialise, initialised notification, ping, `tools/list`, `tools/call`, proper JSON-RPC errors).
- [ ] **Step 4:** Write the report (what was measured, the decision, the consequences for the SBOM and `docs/licenses.md`). Update decision D4 in the spec to "decided: …". Commit.

**Done when:** the report exists, D4 in the spec is no longer open, and the scratch branch is deleted or merged.

---

### Task 2: Tool API, registry, workspace access, limits

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/service/ai/tool/AiTool.java`
- Create: `…/service/ai/tool/ToolResult.java`, `ToolException.java`, `ToolContext.java`, `ToolLimits.java`
- Create: `…/service/ai/tool/WorkspaceAccess.java`
- Create: `…/service/ai/tool/ToolRegistry.java`
- Create: `…/service/ai/tool/ToolArgs.java` (typed access to a `JsonObject`: required/optional string, int, boolean, string map; unknown keys → `ToolException`)
- Create: `…/service/ai/tool/ToolInput.java` (resolves "`path` or `content`" argument pairs to text + optional file)
- Create: `…/service/ai/tool/ToolSchemas.java` (builder for the argument schemas; the path/content pair as `input(name, what)`)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/ai/tool/WorkspaceAccessTest.java`, `ToolArgsTest.java`, `ToolRegistryTest.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/ai/AiLayeringGuardTest.java`

**Interfaces:**

```java
public interface AiTool {
    String name();
    String description();
    JsonObject inputSchema();
    default boolean readOnly() { return true; }
    ToolResult call(JsonObject arguments, ToolContext context) throws ToolException;
}

/** @param text what a model reads; @param structured the same facts as JSON, or null */
public record ToolResult(String text, JsonObject structured, boolean error) {
    public static ToolResult ok(String text, JsonObject structured) { … }
    public static ToolResult failed(String text, JsonObject structured) { … }
}

public record ToolLimits(Duration timeout, int maxOutputChars, long maxInputBytes, int maxItems) {
    public static final ToolLimits DEFAULTS =
            new ToolLimits(Duration.ofSeconds(30), 64 * 1024, 64L * 1024 * 1024, 200);
    public String truncate(String text) { … }   // appends "… [truncated, N characters in total]"
}

public final class WorkspaceAccess {
    public WorkspaceAccess(List<Path> roots) { … }
    public Path resolve(String path) throws ToolException { … }      // inside a root, real path
    public String read(Path file, long maxBytes) throws ToolException { … }
    public List<Path> roots() { … }
}

public interface ToolContext {
    WorkspaceAccess files();
    ToolLimits limits();
    default Optional<String> openDocument(String reference) { return Optional.empty(); }
}

public final class ToolRegistry {
    public static ToolRegistry standard() { … }        // all Phase 1 tools
    public ToolRegistry only(Collection<String> names) throws IllegalArgumentException { … }
    public List<AiTool> tools() { … }
    public Optional<AiTool> find(String name) { … }
    /** Runs the tool under the context's timeout; never throws for tool-level failures. */
    public ToolResult call(String name, JsonObject arguments, ToolContext context) { … }
}
```

**Steps:**

- [x] **Step 1 (red):** `WorkspaceAccessTest` — inside root resolves; `../` escape refused; absolute path outside refused; symlink pointing outside refused (skip on platforms without symlink rights via `Assumptions`); non-existing file under a root allowed for `resolve`, refused by `read`; a file larger than `maxBytes` refused with the size in the message; relative paths resolve against the first root. `ToolArgsTest` — missing required, wrong type, unknown key each give a `ToolException` naming the argument. `ToolRegistryTest` — `only(...)` with an unknown name throws; `call` of an unknown tool returns an error result; a tool that throws `RuntimeException` yields an error result (failure contained, as plugin validators are).
- [x] **Step 2 (green):** implement. Look at `util/PathValidator` first and reuse what fits instead of duplicating checks.
- [x] **Step 3:** `AiLayeringGuardTest`: walk `src/main/java/org/fxt/freexmltoolkit/service/ai`, fail on `import javafx.` anywhere and on `io.modelcontextprotocol` outside `service/ai/mcp/` (model the test on `HttpCallsGuardTest`).
- [x] **Step 4:** run the three tests and `HttpCallsGuardTest`; commit.

---

### Task 3: Sandboxed transformation

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XsltTransformationEngine.java` — extract the in-memory result-document handling from `recordDebugArtifacts` (around line 870–885) into something both the debug run and a new sandboxed run use
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformRunner.java` — new entry point
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/TransformRunnerSandboxTest.java`

**Interfaces:**

```java
/** A transformation that cannot write files: secondary results are returned, not stored. */
public record SandboxedTransformation(XsltTransformationResult result, Map<String, String> resultDocuments,
                                      boolean timedOut) { }

public static SandboxedTransformation xsltTransformSandboxed(String xml, String xmlBaseUri,
        String xsltContent, String xsltSystemId, Map<String, Object> parameters,
        XsltTransformationEngine.OutputFormat outputFormat, Duration timeout);
```

**Steps:**

- [x] **Step 1 (red):** tests — (a) a stylesheet with `<xsl:result-document href="/tmp/…/fxt-sandbox-probe.xml">` produces the text in `resultDocuments()` and **no file**; (b) the same with a relative `href` and a stylesheet system id under a temp folder: no file in that folder; (c) Java extension call fails even when `security.xslt.allow.extensions=true` is set in the test properties; (d) remote `doc('https://…')` fails (already blocked — regression pin); (e) a non-terminating stylesheet returns `timedOut == true` within the timeout plus a margin; (f) `xsl:message` text is available on the result (it already is: `XsltDebugMessageListener` is always attached).
- [x] **Step 2 (green):** implement; decide the timeout mechanism (Review Focus 1) and say in the JavaDoc what happens to the thread.
- [x] **Step 3:** existing `TransformRunner`, debugger and CLI tests still pass (`FxtCliTest`, the debugger test classes that cover result documents). Commit.

**Outcome:** see the spec, "Sandbox and limits" → Stylesheets. Deviations from this task: the entry point
takes `readableRoots` (local `doc()` etc. restricted to the roots, as the spec requires); a per-run processor
instead of a shared one (the `ALLOW_EXTERNAL_FUNCTIONS` / `system-property()` finding); `XsltTransformationResult`
got `getErrorLine()`; `util/RootedPaths` holds the containment check shared with `WorkspaceAccess`.

---

### Task 4: Tools `validate`, `xpath`, `xquery`, `xslt_transform`

**Files:**
- Create: `…/service/ai/tool/ValidateTool.java`, `XPathTool.java`, `XQueryTool.java`, `XsltTransformTool.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/ai/tool/ValidateToolTest.java`, `QueryToolsTest.java`, `XsltTransformToolTest.java`

**Arguments and results** (argument pairs `x_path` / `x_content`: exactly one of them):

| Tool | Arguments | Structured result |
|---|---|---|
| `validate` | `xml_path` \| `xml_content`; optional `xsd_path`, `schematron_path`, `declared_schema` (bool, default true: use the schema the document names) | `valid`, `not_run` (the validation could not run), `error_count`, `warning_count`, `total`, `problems[]` = `{source, severity, line, column, message, rule, context}` (first `maxItems`) |
| `xpath` | `xml_path` \| `xml_content`; `expression` | `ok`, `result` (truncated), `truncated`, `error` |
| `xquery` | optional `xml_path` \| `xml_content`; `query_path` \| `query_content`; `variables` (object of strings) | as `xpath` |
| `xslt_transform` | `xml_path` \| `xml_content`; `xslt_path` \| `xslt_content`; `parameters` (object of strings); `output` (`xml`/`html`/`text`, default from the stylesheet via `TransformRunner.detectXsltOutputFormat`) | `ok`, `output` (truncated), `result_documents{uri: text}`, `messages[]`, `error{code, phase, line, message}`, `timed_out` |

`text` is a short human-readable rendering of the same facts (first lines of the output, or the
problems one per line as `fxt-cli validate` prints them) — look at `cli/ValidationReport` and
`cli/QueryCommand` and reuse their formatting where possible.

**Steps:**

- [x] **Step 1 (red):** per tool: happy path with inline content; with a path under a temp root; a path outside the root → error result naming the roots; both or neither of a pair → `ToolException`; broken XML; a stylesheet with a compile error returns `error.code` (e.g. `XTSE0010`), `phase == "compile"` and a line; output longer than `maxOutputChars` is truncated and `truncated == true`; `validate` with the Schematron from `FxtCliTest` reports the warning with `source == "Schematron"`; with `maxItems = 2` and five problems `total == 5` and `problems.length == 2`.
- [x] **Step 2 (green):** implement on top of `ValidationRunner.runWithReport(xml, xsd, schematron, documentName, documentPath, declaredGrammars)`, `ValidationRunner.declaredSchema`, `TransformRunner.xpath`, `TransformRunner.xquery`, `TransformRunner.xsltTransformSandboxed`. Check well-formedness before an XQuery run the way `QueryCommand` does (`XmlStructuralDiff.wellFormednessError`).
- [x] **Step 3:** register in `ToolRegistry.standard()`; commit.

**Outcome:** fields added beyond the table above (all in `ToolSurfaceSnapshotTest` from Task 8):
`validate` → `not_run_reason`, `schema` (the XSD used); `xpath` → argument `namespaces` (prefix → URI, `""` =
default element namespace; the root element's prefixes are declared automatically), `item_count`, `timed_out`;
`xquery` → `item_count`, `timed_out`; `xslt_transform` → `output_method`. `xpath` and `xquery` run sandboxed too
(`service/SandboxedQuery`: per-run processor, reads inside the roots, no environment, output cap) — they are
code from a model as much as a stylesheet is. "Schema did not load" moved from `cli/ValidateCommand` to
`ValidationRunner.isSchemaNotLoaded` (shared).

**Finding — expression loops:** Saxon-HE has no interrupt mechanism and its tracing hooks sit on templates,
functions and instructions only; a loop inside one expression (`count(for $i in 1 to 1e9, $j in … where …)`)
is optimised into a filter and never reaches the deadline listener. `ToolRegistry` gives up on such a call after
the timeout (result `timed_out`) and counts the thread in `runawayCount()` while it keeps running. Task 7 uses it.

**Known gap — Schematron in `validate`:** the Schematron runs on the shared processor through
`ValidationRunner`, so its `doc()` / `unparsed-text()` are not limited to the roots. The Schematron must be a
file inside the roots (`schematron_path` only), but its content can read elsewhere. Decide in Task 6 together
with `schematron_check` (route the Schematron stage through a sandboxed processor, or document it).

---

### Task 5: Tools `schema_outline`, `schema_describe`, `sample_xml`

**Files:**
- Create: `…/service/ai/tool/SchemaOutlineTool.java`, `SchemaDescribeTool.java`, `SampleXmlTool.java`
- Create: `…/service/ai/context/SchemaSlice.java` (UI-free rendering of a declaration and its surroundings as JSON and as compact text; Phase 2's `ContextBuilder` reuses it)
- Test: `…/service/ai/tool/SchemaToolsTest.java`, `…/service/ai/context/SchemaSliceTest.java`

**Arguments and results:**

| Tool | Arguments | Structured result |
|---|---|---|
| `schema_outline` | `xsd_path`; optional `element` (a global element name), `depth` (default 2, max 6) | `target_namespace`, `global_elements[]`, `named_types[]`, `imports[]`, and for `element`: a tree `{name, type, min, max, children[]}` cut at `depth` with `more: true` where cut |
| `schema_describe` | `xsd_path`; `instance_path` (e.g. `/FundsXML4/ControlData/ContentDate`), optional `attribute` | `found`, `name`, `type`, `builtin_base`, `min`, `max`, `nillable`, `default`, `fixed`, `facets{}`, `enumeration[]`, `documentation[] = {lang, text}`, `children[] = {name, type, min, max}`, `attributes[] = {name, type, use}`, `declared_in` (file, when from an include/import) |
| `sample_xml` | `xsd_path`; `mandatory_only` (default false), `max_occurrences` (default 2), `realistic` (default true), `seed` | `ok`, `xml` (truncated), `error` |

**Steps:**

- [x] **Step 1 (red):** use schemas from `src/test/resources/` (a small one inline, plus one with imports and one with a recursive type). Tests: outline lists the global elements; `depth` cuts with `more`; a recursive type terminates; `schema_describe` of an element with an enumeration returns the values; of an element whose type comes from an imported schema fills `declared_in`; an unknown path gives `found == false` and the nearest resolvable ancestor path in `text`; an attribute lookup works; documentation in two languages returns both.
- [x] **Step 2 (green):** load with `new XsdNodeFactory().fromFile(path)`; locate with `XsdInstanceLocator.instancePath(xpath)` + `XsdInstanceLocator.locate(schema, path, attribute)`; facets with `SchemaFacets.collect`, `resolveReferencedTypeFacets`, `resolveBaseChainFacets`, `builtinBase`. Read `controls/shell/inspector/` to see how the Properties Inspector turns a `Located` into type / cardinality / facets / documentation and follow the same rules (do not import inspector classes — they are JavaFX; move a shared, UI-free helper if one is needed). Cache parsed schemas per `(real path, last-modified)` inside the registry instance.
- [x] **Step 3:** `sample_xml` on `SampleXmlRunner.generateResult`. Register; commit.

**Outcome:** `XsdInstanceLocator` got public accessors for its content-model walk (`childElements`,
`attributesOf`, `declarationOf`, `instanceName`, `isForeignTo`) instead of a copy. Fields beyond the table:
`schema_outline` → `named_types[] = {name, kind}`, tree nodes `choice` / `recursive` (a named type already on the
way down is not entered again); `schema_describe` → `kind`, `use` (attributes), `nearest_known_path` and
`children_of_nearest` when not found. The schema cache (`SchemaCache`, 8 entries, invalidated when any involved
file changes) is shared by the three tools, not held by the registry: the registry is built per consumer, the
parsed schemas are per process.

---

### Task 6: Tools `document_outline`, `xspec_run`, `schematron_check`, `compare`

**Files:**
- Create: `…/service/ai/context/XmlOutline.java`
- Create: `…/service/ai/tool/DocumentOutlineTool.java`, `XSpecRunTool.java`, `SchematronCheckTool.java`, `CompareTool.java`
- Test: `…/service/ai/context/XmlOutlineTest.java`, `…/service/ai/tool/MoreToolsTest.java`

**Arguments and results:**

| Tool | Arguments | Structured result |
|---|---|---|
| `document_outline` | `xml_path` \| `xml_content`; `max_paths` (default 200), `samples` (default true) | `root`, `namespaces{prefix: uri}`, `declared_schema`, `size_bytes`, `element_count`, `max_depth`, `paths[] = {path, count, attributes[], sample}` ordered by first occurrence, `truncated` |
| `xspec_run` | `xspec_path` \| `xspec_content` (+ `base_path` for relative `stylesheet=` when content is inline) | `kind`, `passed`, `summary`, `scenarios[]` (label, tests: label, status, expected, actual — truncated), `error` |
| `schematron_check` | `schematron_path` \| `schematron_content` | `ok`, `issues[]` |
| `compare` | `left_path` \| `left_content`; `right_path` \| `right_content`; `ignore_whitespace` (default true), `ignore_comments`, `ignore_order`, `ignore_xpaths[]`, `numeric_tolerance` | `equivalent`, `total`, `differences[] = {change, kind, name, left_xpath, right_xpath, description}` |

**Steps:**

- [x] **Step 1 (red):** `XmlOutlineTest`: paths are positional-predicate-free (`/a/b`, not `/a/b[3]`); counts are right for repeated elements; `sample` is the first non-blank text, cut to 80 characters; `max_paths` truncates and reports it; a 30 MB generated document is outlined in bounded memory (streaming — StAX with the application's secure factory, see `plugin/XmlScan` and `SecureXmlFactory`; do not build a DOM). With `samples == false` no document value appears anywhere in the result (Phase 2's `STRUCTURE` data level depends on this — assert it by scanning the JSON for a marker value).
- [x] **Step 2 (green):** implement the outline and the four tools on `new service.XSpecRunner().run(text, file)` (instance method), `SchematronCheckRunner.checkResult`, `XmlStructuralDiff.compare(left, right, options)` (`Options.DEFAULTS` and its `with…` methods; check `Options.invalidIgnoreXPath()` and report it as a `ToolException`).
- [x] **Step 3:** XSpec runs the stylesheet under test, which may contain `xsl:result-document` (threat model, "Known limits"). Either route XSpec runs through the sandbox as well or, if that is not possible without changing `io.xspec`, state it in the tool description and in `docs/mcp-server.md` and leave `xspec_run` out of the default tool set (`--tools` must name it). Decide, write the decision into the spec's tool table, test it.
- [x] **Step 4:** register; commit.

**Outcome / decisions:**
- **`xspec_run` is in the default set**, sandboxed: `XSpecRunner.sandboxed(roots, timeout)` — a per-run processor,
  reads only inside the roots plus XSpec's bundled library (its URI prefix is allowed), no environment, Java system
  properties off while the tests run (`XsltSandbox.lockDown` after the generated tests are compiled), the generated
  tests compiled with tracing for the deadline (`XSpecRunner.TIMED_OUT`), secondary results discarded in memory.
  In XSpec the tested code always runs inside a variable, so `xsl:result-document` fails with XTDE1480 there and
  never writes anyway. The Schematron flavour writes its compiled Schematron to a temporary file in the system
  temp folder and deletes it after the run (unchanged, outside the workspace).
- **Schematron in `validate`** (Task 4 gap): left as a documented limit. ph-schematron compiles through a shared,
  path-keyed cache; sandboxing it means replacing that pipeline. In Phase 1 the Schematron must be a file inside the
  roots (no inline content), so only someone who can already write files there can use it to read elsewhere.
  Revisit before the in-app agent (Phase 2) can pass Schematron content.
- `schematron_check` is static only (`SchematronErrorDetector`: no document is processed), so it needs no sandbox.
- `document_outline` streams `xml_path` from disk with no size limit (the other tools read at most
  `ToolLimits.maxInputBytes`). Extra fields: `compare` → `left_value`, `right_value`, `left_line`, `right_line`,
  `similar`, `truncated`, `left_error`, `right_error`; `schematron_check` → `total`, `error`; `xspec_run` → `timed_out`.

---

### Task 7: `McpToolServer` and `fxt-cli mcp`

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/service/ai/mcp/McpToolServer.java`
- Create: `…/service/ai/mcp/StdoutGuard.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/cli/McpCommand.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/service/ai/AiPolicy.java` (Phase 1 part: `allowed()`, `mcpAllowed()`)
- Modify: `src/main/java/org/fxt/freexmltoolkit/cli/FxtCli.java` (add `McpCommand.class` to `subcommands`; extend `description`)
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/AdminPolicy.java` (accessors in the style of `externalPluginsAllowed()`)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/ai/mcp/McpToolServerTest.java`, `StdoutGuardTest.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/cli/FxtCliMcpTest.java`

**Interfaces:**

```java
public final class McpToolServer {
    public McpToolServer(ToolRegistry registry, ToolContext context, String serverName, String version) { … }
    /** Serves until the input ends; never writes anything but protocol messages to {@code out}. */
    public void serve(InputStream in, OutputStream out) throws IOException { … }
}
```

`McpCommand` options: `--root DIR` (repeatable; default: the working directory), `--tools NAMES`
(comma-separated subset; default: the standard set), `--list-tools` (prints names and one-line
descriptions to stdout and exits — no server), plus the inherited `-q` / `--verbose`.

**Steps:**

- [x] **Step 1 (red):** `McpToolServerTest` drives `serve` with piped streams: initialise with `2025-11-25` (the newest revision the SDK speaks, see the Task 1 report) and with an older one the server supports; `server/discover` (the `2026-07-28` probe) gets an error a modern client treats as "legacy server", so its fallback to `initialize` works; `tools/list` returns every registered tool with its input schema; `tools/call validate` with inline content returns text and structured content; an unknown tool and malformed arguments produce the error shape the spec prescribes (tool-level failures are results with the error flag, protocol misuse is a JSON-RPC error — follow the spec read in Task 1); a request before initialisation is rejected; end of input ends `serve` cleanly.
- [x] **Step 2 (red):** `StdoutGuardTest`: a tool that calls `System.out.println("noise")` during a call leaves the protocol stream byte-for-byte a sequence of valid messages; the noise arrives on stderr. `FxtCliMcpTest`: `--list-tools` prints the names; with the policy set for the test to `ai.mcp.allowed=false` (test helper `service/AdminPolicyTestAccess`; reset it after the test) the command returns `ExitCodes.USAGE` and reads nothing from stdin; `--tools nope` is a usage error naming the valid tools; `--root` pointing to a missing folder is a usage error.
- [x] **Step 3 (green):** implement with the choice from Task 1. `McpCommand.call()`: check policy → set `fxt.schema.localCopies=false` (`LocalSchemaCopies.PROPERTY`; otherwise a downloaded import is copied next to the schema that names it — a write into the user's folder) → `CliSupport.initServices()` → build `WorkspaceAccess` → install `StdoutGuard` → `serve(System.in, originalStdout)`. After each call, when `ToolRegistry.runawayCount()` reaches 3, finish the current response, write a message to stderr and exit (status `ExitCodes.FAILED`), so runaway expressions (Task 4 finding) cannot keep burning CPU; the client starts the server again. Tests for both. Server name `freexmltoolkit`, version from `VersionUtil.getVersion()`.
- [x] **Step 4:** manual check with a real client. Build (`./gradlew jar` or run through `./gradlew runCli --args="mcp --root …"` — make sure Gradle's own output does not share stdout; if it does, use the packaged launcher or `java -cp`), register the server in Claude Code, and run this scenario: *"Write an XSLT 3.0 stylesheet that turns `src/test/resources/…/<a sample>.xml` into a CSV of two fields, run it with the fxt tools, and show me the result."* Confirm the agent calls `document_outline` / `schema_describe`, then `xslt_transform` with inline content, and repairs after an error. Paste the transcript summary into the task's commit note. Record the exact registration command that worked in `docs/mcp-server.md`.
- [x] **Step 5:** commit.

**Outcome:**
- SDK `mcp-core` + `mcp-json-jackson3` 2.1.0 in `build.gradle.kts`; new runtime artifacts exactly as the Task 1
  report predicted (`reactor-core` 3.7.0, `reactive-streams` 1.0.4); `docs/licenses.md` updated.
- `McpToolServer.serve` ends on end of input (`Ending.INPUT_CLOSED`) or after `DEFAULT_RUNAWAY_LIMIT` (3) runaway
  calls (`Ending.RUNAWAYS`, the answer of the call is written first); `fxt-cli mcp` then exits with `FAILED`.
- Every structured result is also sent as serialized JSON in a second text block (the specification's
  backwards-compatibility rule): in the manual check Claude Code showed only the text of an error result, so the
  error code, phase and line were lost until then. An engine message that already names its code is not wrapped
  again (`ToolJson.describe`).
- SDK behaviour pinned by tests rather than changed: a request before `initialize` is held until the handshake (no
  error, but no tool runs); `server/discover` gets −32601, so a 2026-07-28 client falls back. Not changed: a line
  that is not JSON ends the SDK's stdio transport (documented in `docs/mcp-server.md`).
- Tools get `readOnlyHint: true`, `idempotentHint: true`, `openWorldHint: false`.

**Manual check (Step 4), 2026-11-10, Claude Code 2.1.322 as client** (`claude -p … --mcp-config … --strict-mcp-config
--allowedTools "mcp__fxt__*"`, server = `fxt-cli mcp --root <folder with funds.xml>`):
1. "Turn funds.xml into a CSV of name and nav": the agent called `document_outline`, then `xslt_transform` with the
   stylesheet inline; output `name,nav / Alpha,10.5 / Beta,11`; no file was created.
2. Deliberate syntax error first: `xslt_transform` returned `isError` with XPST0003, phase compile, line 4; the
   agent fixed the stylesheet and the second call returned the names. This run surfaced the text-only error display
   fixed above; a third run confirmed the agent now receives the JSON fields.
3. A scripted session against the real process (`java … FxtCli mcp`) showed only JSON-RPC lines on stdout and an
   empty stderr, exit 0 at end of input.

---

### Task 8: Public-surface snapshot, documentation, release notes

**Files:**
- Create: `src/test/resources/ai/mcp-tools.snapshot.json`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/ai/tool/ToolSurfaceSnapshotTest.java`
- Create: `docs/mcp-server.md`
- Modify: `docs/command-line.md` (new section `## mcp` before `## Exit codes`; add it to the command list at the top), `docs/threat-model.md` (table row "Agents running code through the MCP server" + a "Known limits" bullet if Task 6 Step 3 left one), `docs/administration.md` (`ai.allowed`, `ai.mcp.allowed` — Policy only), `docs/offline-operation.md` (sentence in the `fxt-cli` paragraph: `mcp` opens no connection of its own), `docs/comparison.md` (lines ~105–114 mention the competitors' AI add-ons), `docs/whats-new.md`, `mkdocs.yml` (use the `mkdocs-nav-sync` agent), `README.md` (one bullet under "Transform, Query and Debug" or a new "Automation and AI agents" block), `CLAUDE.md` (new section "AI and MCP" after "Plugins", same density as the neighbouring sections)

**Steps:**

- [x] **Step 1:** `ToolSurfaceSnapshotTest` serialises name, description and input schema of every standard tool and compares with the snapshot file; the failure message says how to regenerate (`-Dfxt.ai.updateSnapshot=true`) and that a change is a public API change that needs a line in `docs/whats-new.md`.
- [x] **Step 2:** `docs/mcp-server.md` for users: what it is and when it helps; set-up for Claude Code and for a generic client configuration file (command = path of the `fxt-cli` launcher, args = `mcp --root <folder>`; the launcher paths per platform are in `docs/command-line.md` "Where it is"); the tool reference generated from the snapshot (a table per tool: arguments, result fields, an example); the safety model in plain words (read-only, roots, no files written, limits, what `--tools` is for); administration (`ai.mcp.allowed`); troubleshooting (nothing happens → check stderr / `--verbose`; path refused → `--root`).
- [x] **Step 3:** run the `docs-updater` agent for the remaining pages; run `./gradlew test` completely; fix what the guard tests report (`I18nBundlesTest` is not affected — the CLI has no bundle texts; `AccessibilityAuditTest` is not affected — no UI).
- [ ] **Step 4:** commit; open the pull request titled `feat(ai): tool layer and MCP server (fxt-cli mcp)` with the spec, the decision report and the manual transcript linked.

**Outcome:** the remaining pages were updated directly instead of through the `docs-updater` agent (small,
known edits): `docs/command-line.md` (`## mcp`), `docs/threat-model.md` (row + two known limits + hardening line),
`docs/administration.md`, `docs/offline-operation.md`, `docs/comparison.md`, `docs/whats-new.md` ("Next release"),
`mkdocs.yml`, `README.md`, `CLAUDE.md` ("AI and MCP"); `docs/licenses.md` in Task 7. `mkdocs build` shows no
warning for them. The tool reference in `docs/mcp-server.md` was generated from the snapshot. Added
`ToolsWriteNothingTest` (definition of done: every standard tool called, the workspace unchanged). Step 4 (pull
request) waits for the maintainer.

---

## Definition of done for Phase 1

- `./gradlew test` is green, including `HttpCallsGuardTest`, `AiLayeringGuardTest`, `ToolSurfaceSnapshotTest`.
- `fxt-cli mcp --list-tools` lists the tools; a real MCP client completes the scenario of Task 7 Step 4.
- No tool call creates or changes a file (pinned by `TransformRunnerSandboxTest` and a registry-level test that snapshots the temp root before and after calling every tool).
- With `ai.mcp.allowed=false` the command refuses.
- User documentation, threat model and administration pages describe the feature.
