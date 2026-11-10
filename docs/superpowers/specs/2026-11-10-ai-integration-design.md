# AI Integration — Design

**Date:** 2026-11-10
**Base:** `main` at `ec2295c` (version 3.0.0)
**Status:** Proposed design — ready for implementation planning; decisions marked *open* are settled in the first task of the phase that needs them
**Plans:** `docs/superpowers/plans/2026-11-10-ai-phase1-tool-layer-mcp.md`, `…-ai-phase2-providers-actions.md`, `…-ai-phase3-agent-mode.md`

## Context

FreeXmlToolkit has no AI feature. The two commercial competitors do: XMLSpy through a vendor-hosted
service sold as a subscription, Oxygen through a paid add-on. Both are chat-centred. Neither is free,
and neither is designed around a model the user runs or chooses.

FreeXmlToolkit has one thing a generic chat assistant lacks: every artefact a language model can
produce for XML work can be **checked deterministically in the same process** — Saxon compiles and
runs XSLT/XQuery/XPath, Xerces validates against XSD 1.1, SchXslt runs Schematron, XSpec runs tests,
`XmlStructuralDiff` compares results. These engines are already exposed as UI-free runners shared
by the shell and `fxt-cli`.

The design therefore does not add "a chat". It adds:

1. a **tool layer** that exposes the existing engines as callable, sandboxed tools,
2. an **MCP server** (`fxt-cli mcp`) so external agents (Claude Code and others) can use those tools,
3. a **provider layer** for a model the user brings (local or remote),
4. **verified actions** inside the editor: the model proposes, the tools verify, the user sees a diff.

## Goals

- AI output is never shown as "done" unless the application verified it (compiled, ran, validated).
- Works with a local model and without any account (Ollama, LM Studio, a company gateway).
- Off by default, lockable by administrator policy, with a visible account of what is sent.
- No change to what the application promises today: no document content in telemetry, no listening
  port, all HTTP through `ConnectionService`.
- Useful without any model at all: Phase 1 ships value (MCP server) with zero LLM calls in the app.

## Non-goals

- No bundled model, no hosted service, no keys shipped, no subscription.
- No AI autocompletion in the editor: schema-based IntelliSense is deterministic and stays the
  only completion source.
- No free-form chat as the primary surface. A conversation view exists only in Phase 3, as the
  front end of the tool-using agent.
- No upload of whole documents. Large files are queried by tools, not sent.
- No visual data mapper, no DITA/authoring features (prose rewriting, grammar).

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | AI is a **core feature**, not a plugin. UI-free code in `service/ai/`, UI in `controls/shell/editor/ai/`. | The plugin API (`PluginContext`) has no contribution point for quick fixes, Query Console modes, Transform panel actions or Settings cards. Adding all of them just to host AI would freeze an API before it is understood. Plugins get one new, small contribution point in Phase 3 (`addAiTool`). |
| D2 | **One tool layer, two consumers**: the MCP server and the in-app agent call the same `AiTool` implementations. | One sandbox, one set of tests, one set of limits. |
| D3 | MCP over **stdio only**, as the subcommand `fxt-cli mcp`. No HTTP transport. | `docs/threat-model.md` states the application has no listening port. `fxt-cli` already keeps stdout for results and logs on stderr. |
| D4 | *Decided 2026-11-10 (report `docs/superpowers/reports/2026-11-10-mcp-implementation-decision.md`):* official MCP Java SDK 2.1.0 (`io.modelcontextprotocol.sdk:mcp-core` + `mcp-json-jackson3`) **behind the adapter** `McpToolServer`, serving the legacy revision `2025-11-25` (the newest the SDK speaks; modern `2026-07-28` clients reach it through the stdio `server/discover` fallback). The tool layer imports no SDK or Reactor type. | The protocol is revised about twice a year; the SDK tracks it, so moving to `2026-07-28` is a version bump inside `service/ai/mcp/`. Added footprint ~2.6 MB (`mcp-core`, `mcp-json-jackson3`, `reactor-core`, `reactive-streams`); slf4j, Jackson 3 and json-schema-validator are already on the classpath. |
| D5 | **Two wire protocols only**: OpenAI-compatible chat completions, and the Anthropic Messages API. | OpenAI-compatible covers Ollama, LM Studio, vLLM, Azure OpenAI and most company gateways. A solo-maintained project cannot track more. |
| D6 | All provider traffic goes through `ConnectionService.openConnection(URI)`; streaming is read from that connection. No HTTP client library is added. | `HttpCallsGuardTest` fails the build otherwise; proxy, NTLM, timeouts and certificate settings then apply for free. `postJson` is not usable: it sets no custom headers and caps the response at 64 KB. |
| D7 | **Off by default**; policy key `ai.allowed=false` removes the feature; data levels control what may leave the machine; every remote request can be previewed. | The README and `docs/telemetry.md` promise that document content is never sent. AI must be visibly separate and explicitly chosen. |
| D8 | Every generated artefact passes a **verifier** before it is offered; results are applied only by the user, as one undo step or as a new document, after a diff. | Same contract as Schematron Quick Fixes (`service/sqf/`, one native undo step). |
| D9 | The model never receives a whole document by default. A **context builder** sends the relevant schema slice, a structure outline and a few sample nodes within a budget; for anything else the model asks a tool. | Reporting files are hundreds of MB; context windows and privacy both forbid sending them. |
| D10 | API keys: environment variable reference preferred (`apiKeyEnv`), otherwise stored encrypted like the proxy password. Never in `policy.properties` / `defaults.properties`. | Those files are world-readable (`docs/administration.md`). |
| D11 | Code supplied by a model or an external agent runs **sandboxed**: `xsl:result-document` into memory, no XProc tool, Java extensions stay off, time and output limits, file access only under declared roots. | `docs/threat-model.md`: running a stylesheet is running code; today only the debugger keeps result documents in memory. |
| D12 | Text read from documents, schemas and tool results is **untrusted data** for the model: it is passed as tool results / delimited data, never as instructions, and no tool with side effects runs without the user's confirmation. | Prompt injection through a document the user opens. |

## Architecture

```text
                 ┌────────────────────────── consumers ───────────────────────────┐
                 │                                                                │
   external agent (Claude Code, …)                       editor actions / agent (Phase 2, 3)
          │ MCP, stdio                                                  │
   cli/McpCommand ── McpToolServer (adapter)              service/ai/agent/{VerifiedGeneration, AgentLoop}
          │                                                   │                    │
          └──────────────► service/ai/tool/ToolRegistry ◄─────┘        service/ai/provider/ChatProvider
                                   │                                    ├─ OpenAiCompatibleProvider
                 AiTool implementations (sandbox, limits)               └─ AnthropicProvider
                                   │                                              │
   existing UI-free runners and services                              ConnectionService.openConnection
   ValidationRunner · TransformRunner · XSpecRunner ·
   SampleXmlRunner · SchematronCheckRunner · XmlStructuralDiff ·
   XsdNodeFactory + XsdInstanceLocator + SchemaFacets
```

### Packages

| Package | Content | Phase |
|---|---|---|
| `service/ai/tool/` | `AiTool`, `ToolResult`, `ToolContext`, `ToolLimits`, `WorkspaceAccess`, `ToolRegistry`, one class per tool | 1 |
| `service/ai/mcp/` | `McpToolServer` (adapter to the SDK or the own implementation), `StdoutGuard` | 1 |
| `cli/McpCommand` | picocli subcommand `mcp` | 1 |
| `service/ai/AiPolicy`, `service/ai/AiSettings` | policy and settings keys, data level | 2 |
| `service/ai/provider/` | `ChatProvider`, `ChatRequest`/`ChatResponse`/`ChatMessage`, `ProviderConfig`, `ProviderStore`, the two providers, `SseReader` | 2 |
| `service/ai/context/` | `ContextBuilder`, `ContextBudget`, `DataLevel`, `Redactor`, `XmlOutline` (`XmlOutline` already in Phase 1, used by a tool) | 1 / 2 |
| `service/ai/agent/` | `VerifiedGeneration`, `Verifier` + implementations; `AgentLoop` | 2 / 3 |
| `controls/shell/editor/ai/` | `AiActions`, `AiProposalView`, `AiSendPreviewDialog`, `AiSettingsCard`, `AiProviderDialog`; `AiAssistantPanel` | 2 / 3 |

`service/ai/` must not import JavaFX. A guard test enforces it (as `HttpCallsGuardTest` does for HTTP).

### Tool layer

```java
public interface AiTool {
    String name();                 // snake_case, stable: part of the public MCP surface
    String description();          // for the model: what it does, when to use it, limits
    JsonObject inputSchema();      // JSON Schema (draft 2020-12) of the arguments
    boolean readOnly();            // false = has side effects; needs confirmation / opt-in
    ToolResult call(JsonObject arguments, ToolContext context) throws ToolException;
}

public record ToolResult(String text, JsonObject structured, boolean error) { }

public interface ToolContext {
    WorkspaceAccess files();       // resolves and checks paths against the declared roots
    ToolLimits limits();           // time, output size, file size
    Optional<String> openDocument(String ref);   // in-app only: "active", a tab name; empty in MCP
}
```

Every tool argument that names input accepts **either** a `path` (checked by `WorkspaceAccess`)
**or** inline `content`. Inline content is what makes the verify loop possible: the agent passes
the stylesheet it just wrote without writing a file.

#### Tools in Phase 1 (all read-only)

| Tool | Wraps | Returns |
|---|---|---|
| `validate` | `ValidationRunner.runWithReport(xml, xsd, schematron, name, path)` — XSD/DTD/RELAX NG, Schematron, plugin validators | problems: source, severity, line, column, message, rule, context XPath; counts; whether the run itself failed |
| `xpath` | `TransformRunner.xpath(xml, expr)` | result text (capped), item count, error code + message |
| `xquery` | `TransformRunner.xquery(xml, query, variables, format)` | as above |
| `xslt_transform` | new sandboxed entry point on `XsltTransformationEngine` (see Sandbox) | principal output (capped), result documents (URI → text, capped), `xsl:message` output, error code, phase (`compile`/`input`/`runtime`), line |
| `schematron_check` | `SchematronCheckRunner.checkResult(sch)` | compile problems of a Schematron |
| `xspec_run` | `service/XSpecRunner#run(text, file)` (instance method) | per scenario/test: label, status, expected, actual; summary |
| `schema_outline` | `XsdNodeFactory.fromFile` | target namespace, global elements, named types (names only), tree of one global element to a given depth |
| `schema_describe` | `XsdInstanceLocator.locate` + `SchemaFacets` | for an instance path: declaration, type and builtin base, cardinality, facets and enumeration values, documentation, child elements with cardinality, attributes |
| `sample_xml` | `SampleXmlRunner.generateResult(xsd, mandatoryOnly, maxOccurrences, realistic, seed)` | a valid sample instance |
| `document_outline` | new `service/ai/context/XmlOutline` (streaming) | root, namespaces, distinct element paths with counts and one sample value each (bounded), size, depth |
| `compare` | `XmlStructuralDiff` | differences (XPath, kind, left/right value), `equivalent` |

Not exposed: XProc (can read and write local files, see threat model), FOP, signing, encryption,
merge, anything that writes.

#### Sandbox and limits

- **Paths:** `WorkspaceAccess` holds the allowed roots (MCP: `--root`, default the working directory;
  in-app: the project folders and the folders of open documents). A path is normalised and resolved
  through symlinks (`toRealPath`) before the check; anything outside a root is refused with a
  message naming the roots. Reuse the application's existing path validation where it fits
  (threat model row "Path traversal in file names from documents").
- **Stylesheets** (`XsltTransformationEngine.transformSandboxed`, building blocks in `service/XsltSandbox`;
  decided in Phase 1 Task 3):
  - a **new Saxon processor per run**. Saxon refuses to compile `xsl:result-document` unless
    `Feature.ALLOW_EXTERNAL_FUNCTIONS` is on, but with the flag on `system-property()` returns Java system
    properties (read when the function runs). So the stylesheet is compiled with the flag on and the flag is
    switched off before the run (`XsltSandbox.lockDown`). Saxon-HE has no reflexive Java extension functions,
    so `security.xslt.allow.extensions` plays no part. The flag belongs to the `Configuration`, hence one
    processor per run;
  - `xsl:result-document` goes into memory (the handler is shared with the debugger), relative and absolute
    `href`s alike;
  - `doc()`, `document()`, `unparsed-text()`, `collection()`, `xsl:include`/`xsl:import` read only `file:` URIs
    inside the roots (`util/RootedPaths`); remote references stay blocked by `SaxonResourceAccess`;
  - `environment-variable()` / `available-environment-variables()` see nothing;
  - **deadline**: compiled with tracing, a trace listener checks the deadline on every instruction, template
    and function entry and unwinds the run on the calling thread (`timedOut`). Endless recursion ends earlier
    with Saxon's stack error. Saxon-HE has no interrupt mechanism, so a loop inside one expression that enters
    no traced construct is not stopped this way: `ToolRegistry` gives up on the call after the timeout and
    counts the still running thread (`runawayCount()`); `fxt-cli mcp` exits after three of them;
  - output capped at 16 M characters (principal result and result documents together);
  - errors carry code, phase and line (`XsltTransformationResult.getErrorLine`).
- **XPath / XQuery** (`service/SandboxedQuery`): the same restrictions (per-run processor, reads inside the roots,
  no environment, output cap); XQuery is compiled with tracing for the deadline, XPath has none in Saxon's API.
- **Schematron** in `validate` runs through `ValidationRunner` on the shared processor; its `doc()` is not yet
  limited to the roots (open point, Phase 1 Task 6).
- **Limits** (`ToolLimits`, defaults in parentheses, settings keys `ai.tool.*`): wall-clock time per
  call (30 s), output text returned to the caller (64 KB, truncated with a marker and the full
  length), input file size read into memory (64 MB; larger inputs are refused with a hint to use
  `document_outline` / `xpath` on a path, which may stream later), number of problems / differences
  returned (200, with the total count).
- **Errors are results:** a stylesheet that does not compile is a normal `ToolResult` with
  `error=true` and the processor's code, line and message — the model needs them to repair. Only
  misuse of the tool (unknown argument, path outside the roots) is a `ToolException`.

### MCP server

- `fxt-cli mcp [--root DIR]… [--tools a,b,…]`; reads requests from stdin, writes responses to stdout.
- Implements initialisation / version negotiation and the **tools** capability only (list, call).
  No resources, prompts or sampling in Phase 1.
- `StdoutGuard`: while serving, `System.out` is redirected to stderr and the transport keeps the
  original stream, so no library output can corrupt the protocol stream.
- Policy: `ai.mcp.allowed=false` (policy only) makes the command exit with `ExitCodes.USAGE` and a
  message. `ai.allowed=false` implies it.
- No network access of its own; no telemetry (as all of `fxt-cli`). It connects only for what the
  files ask for (schema downloads), as `validate` does today.
- Plugin validators run inside `validate` automatically (`CliSupport.initServices` loads plugins),
  so FundsXML, ISO 20022, FinDatEx checks are available to agents without extra work.

### Provider layer (Phase 2)

```java
public interface ChatProvider {
    ChatResponse chat(ChatRequest request, Consumer<String> onTextDelta, CancelToken cancel)
            throws ProviderException;
}
public record ProviderConfig(String id, String name, Kind kind /* OPENAI_COMPATIBLE, ANTHROPIC */,
        String baseUrl, String model, String apiKeyEnv, int maxOutputTokens, boolean local) { }
```

- `ProviderStore` persists the list as JSON in the setting `ai.providers` and the default in
  `ai.provider.default` — the pattern of `service/fo/FoProcessorStore` (`fop.processors`,
  `fop.processor.default`). Presets: Ollama (`http://localhost:11434/v1`), LM Studio, Anthropic,
  OpenAI, Custom. No model name is hard-coded as a default beyond what the user enters or the
  provider's model list returns.
- `local` is derived from the base URL (loopback or a host listed in `ai.localHosts`) and shown as
  a badge everywhere a request can be started.
- Tool calling is part of `ChatRequest`/`ChatResponse` from the start (tool definitions, tool-call
  messages, tool results) even though Phase 2 actions use it only for the repair loop — Phase 3
  depends on it.
- The exact request and response shapes of both protocols must be taken from the providers' current
  documentation when implementing, not from memory.

### Context and privacy (Phase 2)

Data levels (`ai.dataLevel`, lockable; a remote provider can be capped lower than a local one with
`ai.dataLevel.remote`):

| Level | May be sent |
|---|---|
| `SCHEMA` | schema slices and documentation, element and attribute names, the user's own prompt, error messages with values removed |
| `STRUCTURE` | as above plus the document outline with values replaced by type placeholders (`Redactor`) |
| `EXCERPTS` (default for local providers) | as above plus the selected text or the nodes around a problem, within the budget |

- `ContextBuilder` assembles per action; `ContextBudget` counts characters as a token proxy and
  drops in a fixed order (samples → outline depth → sibling declarations).
- `AiSendPreviewDialog` shows exactly the request text; it is shown the first time for each provider
  and always when "Preview before sending" is on (default on for remote providers).
- The first use of a remote provider needs an explicit confirmation naming the host.

### Verified actions (Phase 2)

```text
user intent ─► ContextBuilder ─► ChatProvider ─► candidate
                                      ▲              │
                                      │              ▼
                         error + line + message ◄─ Verifier (tools)      max. 3 repair rounds
                                                     │ ok
                                                     ▼
                                   AiProposalView: diff · verification report · Apply / Discard
```

| Action | Entry point | Verifier |
|---|---|---|
| Explain / fix a validation problem | Problems panel row, Quick Fix menu (`QuickFixMenuFactory`) | apply the edit to a copy → `validate` → the problem is gone and no new problem appeared |
| Natural language → XPath / XQuery | Query Console (`QueryConsole`), an "Ask" mode | compiles; runs against the query target; result shown before it replaces the input |
| XSLT from a source and a target sample | Transform panel (`TransformPanel`), "Generate stylesheet…" | compiles; runs on the source; output valid against the target XSD when one is known; `compare` against the target sample reported |

Phase 3 adds: Schematron rule from a business rule (verified against positive and negative
samples), XSpec tests for an existing stylesheet, `xs:documentation` drafts, test data that is
plausible for the domain (verified by XSD validation), and the tool-using agent.

A proposal is applied through `EditorHost.replaceActiveTextRegion` (one undo step) or opened with
`EditorHost.openGeneratedDocument`. Nothing is written to disk by an action.

### Settings and policy

| Key | Values | Where |
|---|---|---|
| `ai.allowed` | `false` | **Policy only.** Removes every AI entry point, the Settings card and `fxt-cli mcp` |
| `ai.mcp.allowed` | `false` | **Policy only.** Refuses `fxt-cli mcp` |
| `ai.remote.allowed` | `false` | **Policy only.** Only providers on loopback / `ai.localHosts` |
| `ai.enabled` | `true` / `false` (default `false`) | Settings → AI |
| `ai.providers`, `ai.provider.default` | JSON list, id | Settings → AI; in `policy.properties` a fixed list |
| `ai.localHosts` | host list | hosts treated as local (a company gateway) |
| `ai.dataLevel`, `ai.dataLevel.remote` | `SCHEMA` / `STRUCTURE` / `EXCERPTS` | Settings → AI |
| `ai.preview.always` | `true` / `false` | Settings → AI |
| `ai.tool.timeoutSeconds`, `ai.tool.maxOutputKb`, `ai.tool.maxInputMb` | numbers | advanced |

Every control goes into `SettingsPanel.policyKeys()` and uses `bindDisable(...)`.

### Telemetry

Counts only, through `UsageEvents`: action id, provider kind (`local` / `openai_compatible` /
`anthropic`), outcome (`verified` / `unverified` / `failed` / `cancelled`), repair rounds, duration.
Never prompts, responses, model names entered by the user, host names, or tool arguments.
`docs/telemetry.md` gets the new events in the same change. `fxt-cli mcp` sends nothing.

## Risks

| Risk | Mitigation |
|---|---|
| Small local models produce poor XSLT | The verifier turns "poor" into "not offered" instead of "wrong"; an evaluation set (Phase 2) reports a pass rate per model so the documentation can name what works |
| Provider APIs change | Two protocols, one adapter each, contract tests against a fake server; live tests are opt-in |
| The feature erodes the "nothing leaves your machine" trust | Off by default, policy keys, data levels, preview, local badge, separate documentation page; telemetry documentation updated in the same release |
| Dependency growth (D4) | Spike with explicit criteria; adapter keeps the choice reversible |
| Maintenance load for one maintainer | Phase 1 has no model dependency at all; Phase 3 starts only after Phase 2 has users |
| Prompt injection from document content | D12; all Phase 1/2 tools are read-only; Phase 3 write tools require confirmation per call |

## Documentation to change

New pages: `docs/mcp-server.md` (Phase 1), `docs/ai-assistant.md` (Phase 2). Changed:
`docs/command-line.md`, `docs/threat-model.md`, `docs/offline-operation.md`,
`docs/administration.md`, `docs/settings.md`, `docs/telemetry.md`, `docs/comparison.md`,
`docs/whats-new.md`, `mkdocs.yml`, `README.md`, `CLAUDE.md` (a section "AI and MCP" in the style of
the existing feature sections), `.claude/rules/ai-integration.md`.

## Acceptance of the whole design

- With `ai.allowed=false` in the policy file, no AI control is visible and `fxt-cli mcp` refuses.
- With AI switched on and a local provider, no connection leaves the machine (assert in a test with
  a recording `ConnectionService`).
- An agent connected through MCP can write a stylesheet, run it, read the compile error with its
  line, fix it and confirm the output validates — without any file being written by the tools.
- Every proposal shown in the editor carries a verification report; an unverified proposal is
  labelled as such and cannot be applied with one click.
