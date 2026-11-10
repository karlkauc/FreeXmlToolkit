# AI Phase 2 — Providers, Privacy and Three Verified Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user connect a model of their own (local or remote) and add three editor actions whose results are verified by the Phase 1 tools before they are offered: fix a validation problem, natural language → XPath/XQuery, and XSLT from a source and a target sample.

**Architecture:** `service/ai/provider/` speaks two wire protocols over `ConnectionService.openConnection`. `service/ai/context/` builds a bounded, level-filtered request. `service/ai/agent/VerifiedGeneration` runs generate → verify → repair with `Verifier`s built on the Phase 1 tools. `controls/shell/editor/ai/` adds a Settings card, a send preview, a proposal view (diff + verification report) and the three entry points.

**Tech Stack:** Java 25 / JavaFX 25, Gson, JUnit 6 + TestFX; fake provider = `com.sun.net.httpserver.HttpServer` on loopback **in test code only** (`HttpCallsGuardTest` scans `src/main/java`).

**Spec:** `docs/superpowers/specs/2026-11-10-ai-integration-design.md` (D5–D10, D12; sections "Provider layer", "Context and privacy", "Verified actions", "Settings and policy", "Telemetry").

**Precondition:** Phase 1 is merged. Before starting, re-read the Phase 1 pull request and correct every class name, line reference and tool name in this plan that changed. This plan is at task level; turn each task into red/green steps in the house format before implementing it, and commit the refined plan first.

## Global Constraints

- Everything is **off** until the user switches `ai.enabled` on; with `ai.allowed=false` (policy) no AI control, menu item or Settings card exists (not merely disabled).
- Provider traffic only through `ConnectionService.openConnection(URI)`. No new HTTP library.
- Request and response shapes come from the providers' **current** documentation (Anthropic: https://docs.claude.com — Messages API, tool use, streaming; OpenAI-compatible: the chat completions reference and Ollama's / LM Studio's compatibility notes). Do not implement them from memory. No model name is hard-coded.
- Nothing from a request or response is logged above `DEBUG`, and never to telemetry. `TelemetryLogAppender` forwards log events — check that AI classes log no content at `WARN`/`ERROR` (exception messages from providers may echo the prompt).
- A proposal is never applied automatically. Apply = one undo step (`EditorHost.replaceActiveTextRegion`) or a new unsaved document (`EditorHost.openGeneratedDocument`).
- Document text, schema documentation and tool results are passed to the model as clearly delimited data; the system prompt says they are data, not instructions (D12).
- All new UI: FXML via `FxmlViews.load`, `IconifyIcon` `bi-*`, `DialogHelper` for dialogs, `@FXML` handlers public, texts in `i18n/messages.properties` + `messages_de.properties`, accessible names. Guard tests: `FxmlContractTest`, `IconifyIconCoverageTest`, `DialogCreationGuardTest`, `I18nBundlesTest`, `AccessibilityAuditTest`, `ContrastTest`, `SemanticColorGuardTest`.
- New Settings controls are registered in `SettingsPanel.policyKeys()` and disabled through `bindDisable(...)`.

## Review Focus

1. **Loopback behind a proxy:** a request to `http://localhost:11434` must not go to the configured proxy. Check how `ConnectionServiceImpl.newConnection` resolves the proxy for loopback hosts and `http.proxy.nonProxyHosts`; pin with a test.
2. **Read timeout on a streaming response:** local models can take minutes to first token. Timeouts come from `http.connectTimeoutSeconds` / `http.readTimeoutSeconds`; the provider needs its own, longer read timeout on the connection it gets and must stay cancellable (closing the stream from another thread).
3. **Cancel:** closing the proposal view or pressing Cancel ends the HTTP request and the repair loop; no late result is applied to a document that changed meanwhile (compare `EditorHost.activeRevisionKey()` at request and at apply time).
4. **A fix that removes the problem by deleting content.** The validation-fix verifier must reject edits that delete elements unless the user asked for it; report the size of the change next to the diff.
5. **Data level leaks:** at `SCHEMA` and `STRUCTURE` no text value of the document may appear in the request — including inside validation messages (Xerces messages quote the offending value). `Redactor` must cover problem messages; pin with a test that plants a marker value.
6. **Remote switched off by policy** (`ai.remote.allowed=false`): a provider whose base URL is not local cannot be saved as default, cannot be used, and the Test button says why.
7. **API key handling:** never written to the log, the preview, the provider JSON, or an error dialog; `PropertiesServiceImpl` encrypts only keys whose name contains "password" (`PasswordEncryptionService.isPasswordProperty`) — extend the predicate deliberately and test it, do not rename keys to trick it.

---

### Task 1: `AiPolicy`, `AiSettings`, data level

- Extend `service/ai/AiPolicy` (from Phase 1) with `remoteAllowed()`; add `service/ai/AiSettings` (typed access to the `ai.*` keys of the spec's settings table through `PropertiesService`) and `service/ai/context/DataLevel`.
- `AdminPolicy` accessors in the style of `xmlEncryptionAllowed()`.
- Tests: defaults (everything off, level `EXCERPTS` for local / `SCHEMA` for remote), policy locks, `ai.dataLevel.remote` caps the level for a non-local provider.

### Task 2: Provider configuration and secrets

- `service/ai/provider/ProviderConfig`, `ProviderStore` — follow `service/fo/FoProcessorStore` (JSON in one setting, default id, `settings()` factory, a `Stored` DTO, policy-fixed list) and its test `FoProcessorStoreTest`.
- Presets: Ollama, LM Studio, Anthropic, OpenAI, Custom. `local` derived from the URL host (loopback literal or name, or listed in `ai.localHosts`).
- Secrets: `apiKeyEnv` (name of an environment variable) or a stored key under `ai.provider.<id>.apiKey`; make `PasswordEncryptionService.isPasswordProperty` cover that suffix (interface JavaDoc and `PasswordEncryptionServiceImpl` test updated) so `PropertiesServiceImpl` encrypts it like the proxy password. A key in `policy.properties` / `defaults.properties` is ignored with a warning in the log.
- Tests: round trip; the stored properties file contains `ENC(`; removing a provider removes its key; a policy-fixed list cannot be edited.

### Task 3: `ChatProvider` API and the OpenAI-compatible provider

- `ChatProvider`, `ChatRequest` (system text, messages, tool definitions, max output tokens, temperature), `ChatMessage` (role; text; tool calls; tool result), `ChatResponse` (text, tool calls, stop reason, usage), `ProviderException` (kind: `AUTH`, `RATE_LIMIT`, `UNREACHABLE`, `BAD_REQUEST`, `SERVER`, `CANCELLED`, `PROTOCOL`; a user-safe message without the key or the prompt), `CancelToken`.
- `OpenAiCompatibleProvider`: non-streaming first, then streaming with `SseReader` (line-based reader for `text/event-stream` over the connection's input stream). Tool definitions from `AiTool.inputSchema()`.
- `listModels()` where the endpoint offers it (fills the model combo in the dialog; failure is not an error).
- Test double: `FakeChatServer` in `src/test/java/.../service/ai/provider/` (JDK `HttpServer` on an ephemeral loopback port, scripted responses, records requests). Tests: text answer; streamed answer delivered as deltas and assembled; a tool call round trip; 401 → `AUTH`; 429 → `RATE_LIMIT` with retry-after surfaced; connection refused → `UNREACHABLE`; cancel mid-stream; malformed event → `PROTOCOL`; the `Authorization` header is sent and appears in no log line (capture the log in the test).

### Task 4: Anthropic provider

- `AnthropicProvider` on the Messages API with tool use and streaming, mapped to the same `ChatRequest`/`ChatResponse`. Same test matrix against `FakeChatServer` with Anthropic-shaped scripts.
- One contract test class runs both providers through identical scenarios (`ProviderContractTest`) so the mapping stays symmetrical.
- Opt-in live smoke tests (`@EnabledIfEnvironmentVariable`, e.g. `FXT_AI_LIVE_OPENAI_BASEURL` / `FXT_AI_LIVE_ANTHROPIC_KEY`), never run in CI.

### Task 5: Context builder, budget, redaction

- `service/ai/context/ContextBuilder` with one method per action (`forProblem`, `forQuery`, `forStylesheet`), `ContextBudget` (characters as a token proxy; documented drop order), `Redactor` (replaces text and attribute values by type placeholders; strips quoted values from validator messages).
- Reuse `SchemaSlice` and `XmlOutline` from Phase 1.
- The schema of the active document: resolve like the editor does (`EditorSchemaBinding.detectSchemaFor`, project bindings, Schema Library) — pass the resolved `File` in from the UI layer; `service/ai` must not depend on `EditorHost`.
- Tests: each level against a document with marker values (Review Focus 5); the budget is respected and the drop order is as documented; a 100 MB document produces a request under the budget without loading the DOM.

### Task 6: `VerifiedGeneration` and verifiers

```java
public interface Verifier {
    /** @return what was checked and how it went; never throws for a failing candidate */
    Verification verify(String candidate, ToolContext tools);
}
public record Verification(Status status /* VERIFIED, FAILED, NOT_CHECKABLE */, List<Check> checks,
                           String feedbackForModel) { }

public final class VerifiedGeneration {
    public Outcome run(ChatProvider provider, ChatRequest first, Function<String, String> extractCandidate,
                       Verifier verifier, int maxRepairs, Listener progress, CancelToken cancel);
}
```

- `extractCandidate` takes the artefact out of the answer (one fenced block of the expected language; an answer with none or several is a failed round with feedback).
- Verifiers: `XPathVerifier` (compiles, runs on the target, reports item count and first results), `XQueryVerifier`, `XsltVerifier` (compiles → sandboxed run on the source → output well-formed → valid against the target XSD if given → `compare` with the target sample reported, not required), `ProblemFixVerifier` (apply the proposed edit to a copy → `validate` → the problem is gone, no new problems, change size within a bound — Review Focus 4).
- The model answers a fix as a **replacement for a stated text range or XPath-addressed node**, not as a whole document; look at `service/xmledit/{TextEdit,EditPlan,XPathReplacePlanner}` and `service/sqf/XmlNodeSpanLocator` for the existing formatting-preserving edit machinery and build on it.
- Tests with a scripted `ChatProvider` (no HTTP): first candidate wrong → feedback contains code, line, message → second candidate right → `VERIFIED` after 1 repair; never right → `FAILED` after `maxRepairs` with the last report; cancel between rounds.

### Task 7: Settings card, provider dialog, send preview

- `controls/shell/editor/ai/AiSettingsCard` (FXML; pattern: `FoProcessorsSettings` inside `SettingsPanel`, registered in `policyKeys()`), `AiProviderDialog` (pattern: `FoProcessorDialog`; **Test** sends a one-line request and shows model, latency and whether the host counts as local), `AiSendPreviewDialog` (the exact request text, the provider, the local/remote badge, the data level; "Do not show again for this provider").
- The card is absent under `ai.allowed=false`; a new Settings section entry appears in the `PageNavigator` only when AI is allowed.
- First use of a remote provider: confirmation naming the host (stored per provider id).
- TestFX tests in the style of the existing Settings tests; add the screenshots to the documentation generator the repository uses for Settings pages.

### Task 8: Proposal view

- `controls/shell/editor/ai/AiProposalView`: progress while generating (rounds, what is being checked), the diff (reuse `controls/diff/DiffView` or its text-diff parts — check which is embeddable; `DiffView` is a `Tab`), the verification report as a list of checks with icons, **Apply** (disabled with an explanation when the document changed since the request; an unverified proposal needs a second, explicit "Apply unverified"), **Discard**, **Copy**, and **Open as new document**.
- Opens as a tool tab (`EditorHost.openOrFocusToolTab`) or in the bottom dock (`EditorHost.bottomDock()`); choose the one the debugger and Transform output already use for comparable content and say why in the task notes.

### Task 9: Action — explain / fix a validation problem

- Entry points: the Problems panel row menu and the gutter Quick Fix menu (`QuickFixMenuFactory.buildQuickFixMenu` builds the SQF menu — add the AI items next to it, clearly separated and labelled; SQF fixes stay first because they are deterministic).
- "Explain" asks for a short explanation with the schema slice of the failing node; it has no verifier and is shown as text.
- "Fix" runs `VerifiedGeneration` with `ProblemFixVerifier`, applies through the same path as `QuickFixController.applyFix` (one undo step), then re-validates.
- Tests: scripted provider; the fix is applied as one undo step; undo restores the text exactly; with AI off the menu items are absent.

### Task 10: Action — natural language → XPath / XQuery

- `QueryConsole` gets an "Ask" toggle next to the language selection: the input is a question, the answer is an expression placed **into the input field** (so the user sees and can edit it) with the verification result underneath; Run stays the user's action. History and snippets keep working (`XPathSnippetRepository`).
- Context: `forQuery` (outline + schema slice of the paths the outline shows; namespaces as the console declares them).
- Tests: scripted provider; the namespace prefixes of the document are used; an expression that does not compile is repaired or shown as failed, never placed silently.

### Task 11: Action — generate a stylesheet from a source and a target sample

- Transform panel action "Generate stylesheet…" → dialog: source document (default: active), target sample (file or open document), optional target XSD, optional notes in the user's words, XSLT version (default 3.0).
- Result opens as a new unsaved `.xsl` document with the proposal view showing the verification (compiled, ran, output valid, differences to the sample).
- Tests: scripted provider end to end; the generated document is unsaved; nothing is written to disk.

### Task 12: Telemetry, evaluation set, documentation

- `UsageEvents.aiAction(actionId, providerKind, outcome, repairRounds, startNanos)`; `docs/telemetry.md` lists the event and repeats what is never sent. A test asserts the event carries no free text.
- **Evaluation set** `src/test/resources/ai/evals/` — 20–30 cases across the three actions (inputs, a deterministic check: expected query result, output valid against an XSD, problem gone). A Gradle task `aiEval` (not part of `test`) runs them against a configured provider (`-Pai.baseUrl=… -Pai.model=… [-Pai.kind=anthropic]`, key from the environment) and prints verified-first-try / verified-after-repair / failed per case and in total, writing `build/ai-eval.md`. Run it once against at least one small local model and one large hosted model and put the numbers into `docs/ai-assistant.md` ("what to expect from which class of model").
- `docs/ai-assistant.md` (new): what it does and does not do, setting up a local and a remote provider, the data levels with examples of what is sent, the preview, the three actions, administration. Update `docs/threat-model.md` (rows: provider connection; prompt injection through documents), `docs/offline-operation.md` (network table row "AI provider" — default off, contacts only the configured base URL), `docs/administration.md` (all `ai.*` keys), `docs/settings.md`, `docs/comparison.md`, `docs/whats-new.md`, `README.md` (and its Privacy paragraph: AI requests go to the provider the user configured and are not telemetry), `mkdocs.yml`, `CLAUDE.md` section "AI and MCP".

## Definition of done for Phase 2

- With default settings a recording `ConnectionService` sees no AI request in a full UI test run.
- With a local provider configured, all three actions work end to end against `FakeChatServer` in TestFX tests, and manually against a real local model.
- At data level `SCHEMA`, the request text of every action contains no marker value from the document.
- `ai.allowed=false` removes every entry point; `ai.remote.allowed=false` blocks non-local providers.
- The evaluation report exists and its numbers are in the user documentation.
