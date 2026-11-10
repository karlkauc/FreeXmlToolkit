# AI Phase 2 — Providers, Privacy and Three Verified Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** detailed plan (refined 2026-11-10 after Phase 1). Decisions taken by the maintainer on 2026-11-10:
**D13 = official Anthropic Java SDK behind a `ConnectionService` adapter**, **D14 = Chat Completions**. Ready for
implementation.

**Goal:** Let the user connect a model of their own (local or remote) and add three editor actions whose results are
verified by the Phase 1 tools before they are offered: fix a validation problem, natural language → XPath/XQuery, and
XSLT from a source and a target sample.

**Architecture:** `service/ai/provider/` speaks two wire protocols; every byte goes through
`ConnectionService.openConnection` (directly, or through an SDK whose HTTP layer is replaced, D13).
`service/ai/context/` builds a bounded, level-filtered request. `service/ai/agent/VerifiedGeneration` runs
generate → verify → repair; the verifiers call the **Phase 1 tools through `ToolRegistry`** (one sandbox, D2).
`controls/shell/editor/ai/` adds the Settings group, provider dialog, send preview, proposal view and three entry
points.

**Tech Stack:** Java 25 / JavaFX 25, Gson, JUnit 6 + TestFX. Fake provider = JDK `com.sun.net.httpserver.HttpServer`
on loopback, **test code only** (`HttpCallsGuardTest` scans `src/main/java`; pattern: `service/ConnectionServiceTest`
`@BeforeAll setUpTestServer`). Run single tests with the exact class name; the fast runner is described in the
maintainer's notes.

**Spec:** `docs/superpowers/specs/2026-11-10-ai-integration-design.md` (D5–D12; sections "Provider layer", "Context and
privacy", "Verified actions", "Settings and policy", "Telemetry"). **Rules:** `.claude/rules/ai-integration.md`.

---

## What Phase 1 changed for this plan

| Draft said | Reality after Phase 1 / in the code | Consequence here |
|---|---|---|
| Verifiers on `TransformRunner` etc. | Sandboxed engines exist: `XsltTransformationEngine.transformSandboxed`, `SandboxedQuery.run`, `XSpecRunner.sandboxed`; tools wrap them | Verifiers call tools through `ToolRegistry.call` with an in-app `ToolContext` |
| `SchemaSlice`, `XmlOutline` | exist (`service/ai/context`), `XmlOutline.of(…, samples=false)` emits no value | `ContextBuilder` builds on them |
| `SchemaCache` reusable | package-private in `service/ai/tool` | Task 5 moves it to `service/ai/context` (public) |
| `ToolContext.openDocument` | default method, no tool reads it | Task 6: `ToolInput` reads `<name>_document` = an open document reference, only when the context offers it |
| Structured errors | `ToolResult` JSON with `error{code, phase, line, message}`; also as JSON text over MCP | repair feedback for the model is built from these fields |
| `SandboxedQuery` declares the root's prefixes | `QueryConsole` declares none (`XPathQuery(path, Map.of())`) | Task 10 adds `declareDocumentPrefixes` to `SandboxedQuery.run` and verifies with `false`, so a verified query runs identically in the console |

## Corrections to the task-level draft (from checking the code)

1. **Gutter quick fix:** the lightbulb uses `QuickFixPopup` (a `ListView<SqfFixSuggestion>`), not
   `QuickFixMenuFactory.buildQuickFixMenu`, and appears only on lines whose problems have SQF fixes — XSD errors have
   none. Entry points for Task 9 are therefore `ProblemsPanel.contextMenu(ValidationProblem)` and the row menu of
   `ValidationPanel` (currently `null` without fixes, `ValidationPanel.java:~1021`). The gutter stays out of Phase 2.
2. **Applying a fix:** `QuickFixController.applyPlan` is private and SQF-specific. Reuse the chain
   `XPathMatchLocator.locate` → `XPathReplacePlanner.plan(…, REPLACE_FRAGMENT | SET_VALUE)` → `EditPlan.applyTo(copy)`
   (verify) → `EditorHost.replaceActiveTextRegion(plan.minStart(), plan.maxEnd(), plan.mergedRegion(text))` (one native
   undo step), and re-validate like `QuickFixController.setOnFixApplied` → `ValidationPanel::revalidate`.
3. **Schema of the active document:** `EditorSchemaBinding.detectSchemaFor` is package-private with side effects. Use
   `EditorHost.schemaForValidation(String content)` (worker thread, `Supplier<File>`) or `activeSchemaProperty()`.
4. **Stale results:** `activeRevisionKey()` counts per tab; compare `getActiveDocument()` identity **and** the key (or
   the text, as `QuickFixController` does).
5. **Query Console:** there is no history; snippets come from `FavoritesService`; `XPathSnippetRepository` only feeds
   the FundsXML section. JSONPath is the relabelled XPath toggle on JSON documents — Ask mode is XML only.
6. **Settings:** sections are derived from the `settings-group-*` VBoxes in `SettingsPanel.fxml` (`sections()`), not
   registered. To hide the AI group under `ai.allowed=false`, **remove the node** in the constructor (`filter()`
   re-shows hidden nodes, `sections()` ignores visibility). `GROUP_ICONS` needs an `ai` entry. `policyKeys()` is
   package-private and `bindDisable` private → the card is wired inside `SettingsPanel` like `FoProcessorsSettings`.
7. **Policy-fixed provider list:** `FoProcessorStore` has no such logic; the lock comes from the `AdminPolicy` overlay
   and is enforced in the UI (`isLocked(KEY)`). `ProviderStore` copies that split.
8. **Loopback proxy (old Review Focus 1):** already direct — `ProxySettings.bypasses` covers `localhost`, `127.*`,
   `::1`. Only a pinning test is missing (`ConnectionServiceImpl.proxyFor` is package-private static).
9. **DiffView** is a final `Tab` that needs a right-side `File`. The proposal view uses `DiffEngine.compute`,
   `DiffHighlighter.computeHighlighting` and two `PlainTextCodeArea`s (optionally `DiffGutter`).
10. **Comparable features** (Transform output, debugger) use `EditorHost.bottomDock()` (`EditorBottomDock.register/show`)
    → the proposal view goes there (draft Task 8 asked to decide).
11. **Telemetry log leak:** `TelemetryLogAppender` sends only the throwable's class names and frames, never messages —
    the leak risk of the draft is the local log file, not telemetry. The rule stays: no content above DEBUG.
12. `AiPolicy` is static; `remoteAllowed()` is static too. There is no `AiPolicyTest` yet — Task 1 adds one.
13. `openGeneratedDocument` has three public overloads; the result is untitled and **dirty**.

## Decisions (taken 2026-11-10)

**D13 — Anthropic provider: official SDK or raw HTTP.** The Anthropic guidance for Java is to use the official SDK
(`com.anthropic:anthropic-java-core`), not hand-written HTTP. D6 of the spec forbids an HTTP client library. Both can
hold: `anthropic-java-core` 3.8.0 has a pluggable transport — `com.anthropic.core.http.HttpClient` (`execute`,
`executeAsync`, `close`) set through `ClientOptions.Builder.httpClient(…)` — so the SDK can run over
`ConnectionService.openConnection` without OkHttp.
- *SDK:* request/response/streaming/tool types maintained by Anthropic; API changes (e.g. version `2026-10-21` removing
  `temperature`) arrive as an SDK update. Cost: the core jar is ~43 MB, plus `kotlin-stdlib`/`kotlin-reflect` 1.9,
  Jackson **2**.19 (+ jdk8, jsr310, kotlin modules), `opentelemetry-api`, `jsonschema-generator`, `swagger-annotations`,
  `standardwebhooks` — a second Jackson line next to the pinned Jackson 3, SBOM and installer growth. Our code: a
  ~150-line `HttpClient` adapter.
- *Raw HTTP (the spec's D6 as written):* ~400 lines on Gson, no dependency; we track the wire format ourselves (contract
  tests against recorded shapes, live smoke tests opt-in).
- **Decided: SDK with the `ConnectionService` adapter** (Task 4, variant SDK). The raw-HTTP variant in Task 4 is kept
  only as the documented fallback.

**D14 — OpenAI-compatible protocol: Chat Completions.** OpenAI still supports Chat Completions but recommends the
Responses API for new projects, and its newest models (GPT-6 Astra, GPT-6.1 Sol) need Responses for tool calling
[developers.openai.com/api/docs/guides/migrate-to-responses; …/guides/function-calling]. Ollama, LM Studio, vLLM and
Azure's v1 API speak Chat Completions. **Decided: Chat Completions in Phase 2** (the local-first goal); a
Responses-API provider is a later, separate kind. The Settings dialog says which kind a preset uses.

**Spec updated** with D13 and D14 (D5, D6 amended).

## Global Constraints

- Everything is **off** until the user switches `ai.enabled` on; with `ai.allowed=false` (policy) no AI control, menu
  item or Settings group exists (removed, not disabled). `AiLayeringGuardTest`: `service/ai/**` no JavaFX; SDK types
  only in their adapter package (`service/ai/mcp/`, and `service/ai/provider/anthropic/` if D13 = SDK).
- All provider bytes through `ConnectionService.openConnection(URI)`; `HttpCallsGuardTest` stays green (it also bans
  `new URL(` and `java.net.http.` in main code).
- Wire formats from the current documentation, cited in Tasks 3/4; no model name hard-coded (models come from the
  provider's model list or the user).
- Nothing from a request or response above `DEBUG`, never in telemetry. Exception messages from providers may echo the
  prompt: log the class and HTTP status, not the message.
- A proposal is never applied automatically; apply = one undo step (`replaceActiveTextRegion`) or a new unsaved
  document (`openGeneratedDocument`). Stale check = document identity + revision key.
- Document text, schema documentation and tool results reach the model as delimited data; the system prompt says they
  are data, not instructions (D12).
- New UI: FXML via `FxmlViews.load`, `IconifyIcon bi-*`, `DialogHelper`, public `@FXML` handlers, i18n keys in both
  bundles, accessible names. Guards: `FxmlContractTest`, `IconifyIconCoverageTest`, `DialogCreationGuardTest`,
  `I18nBundlesTest`, `AccessibilityAuditTest`, `ContrastTest`, `SemanticColorGuardTest`.
- Commit after every task (`feat(ai): …`). No push, no pull request until the maintainer says so.

## Review Focus

1. **Read timeout of a streaming response:** a local model can take minutes to its first token. The provider sets its
   own read timeout on the connection (`ai.provider.readTimeoutSeconds`, default 300) and stays cancellable
   (`CancelToken` → `HttpURLConnection.disconnect()` from another thread).
2. **Cancel and stale apply:** closing the proposal view or Cancel ends the request and the repair loop; a result for a
   document that changed (identity or revision) is never applied.
3. **A fix that deletes content:** `ProblemFixVerifier` rejects edits that remove elements unless asked; the change size
   is shown next to the diff.
4. **Data level leaks:** at `SCHEMA` and `STRUCTURE` no text or attribute value of the document appears in the request —
   also not inside validator messages (Xerces quotes values). Marker-value tests.
5. **Remote off by policy:** `ai.remote.allowed=false` → a non-local provider cannot be saved as default, used, or
   tested (the Test button says why).
6. **API keys:** never in the log, the preview, the provider JSON, an error dialog, or the telemetry. Stored encrypted;
   keys found in `policy.properties` / `defaults.properties` are ignored with a WARN that names the key, not the value.
7. **Anthropic multi-turn rules** (repair loop, Phase 3 agent): pass `thinking` / `redacted_thinking` blocks back
   **unchanged**, append-only history; editing them is a 400 [platform.claude.com/docs/en/api/errors.md].

---

### Task 1: `AiPolicy.remoteAllowed`, `AiSettings`, `DataLevel`

**Files:**
- Modify: `service/AdminPolicy.java` (`KEY_AI_REMOTE_ALLOWED = "ai.remote.allowed"`, `aiRemoteAllowed()` in the style of
  `xmlEncryptionAllowed()`), `service/ai/AiPolicy.java` (`static boolean remoteAllowed()` = `allowed() && …`)
- Create: `service/ai/AiSettings.java`, `service/ai/context/DataLevel.java`
- Test: `service/ai/AiPolicyTest.java`, `service/ai/AiSettingsTest.java`

**Interfaces:**
```java
public enum DataLevel { SCHEMA, STRUCTURE, EXCERPTS;            // ordered: less → more data
    public DataLevel min(DataLevel other) { … } }

public final class AiSettings {
    public static AiSettings current();                          // over PropertiesService
    AiSettings(Function<String, String> getter);                 // tests
    public boolean enabled();                                     // ai.enabled, default false
    public DataLevel dataLevel();                                 // ai.dataLevel, default EXCERPTS
    public DataLevel dataLevelRemote();                           // ai.dataLevel.remote, default SCHEMA
    public DataLevel effectiveLevel(boolean localProvider);       // remote: min(dataLevel, dataLevelRemote)
    public boolean previewAlways();                               // ai.preview.always, default true for remote (see Task 7)
    public List<String> localHosts();                             // ai.localHosts, comma list
    public ToolLimits toolLimits();                               // ai.tool.timeoutSeconds / maxOutputKb / maxInputMb
}
```
- [x] **Step 1 (red):** `AiPolicyTest` with `AdminPolicyTestAccess.setCurrent/reset` — defaults all true;
  `ai.allowed=false` implies `mcpAllowed()` and `remoteAllowed()` false; `ai.remote.allowed=false` alone; values in
  `defaults.properties` do not count (policy only). `AiSettingsTest` — defaults (off, EXCERPTS local / SCHEMA remote),
  `effectiveLevel(false)` caps, unknown enum text falls back to the default, tool limits parse and clamp.
- [x] **Step 2 (green):** implement. **Step 3:** commit.

### Task 2: Provider configuration and secrets

**Files:**
- Create: `service/ai/provider/ProviderKind.java` (`OPENAI_COMPATIBLE`, `ANTHROPIC`), `ProviderPreset.java` (Ollama
  `http://localhost:11434/v1`, LM Studio `http://localhost:1234/v1`, OpenAI `https://api.openai.com/v1`, Anthropic
  `https://api.anthropic.com`, Custom), `ProviderConfig.java`, `ProviderStore.java`, `ProviderSecrets.java`
- Modify: `service/PasswordEncryptionService(Impl).java` — `isPasswordProperty` also true for keys ending in `.apiKey`
  (case-insensitive); interface JavaDoc updated
- Test: `service/ai/provider/ProviderStoreTest.java`, `ProviderConfigTest.java`; extend
  `service/PasswordEncryptionServiceTest.java`, `service/PropertiesServicePasswordEncryptionTest.java`

**Interfaces:**
```java
public record ProviderConfig(String id, String name, ProviderKind kind, ProviderPreset preset, String baseUrl,
        String model, String apiKeyEnv, AuthHeader authHeader /* BEARER, API_KEY (Azure v1) */,
        TokenLimitField tokenField /* MAX_TOKENS, MAX_COMPLETION_TOKENS (OpenAI) */, int maxOutputTokens,
        int readTimeoutSeconds) {
    public boolean isLocal(List<String> localHosts);   // loopback literal, localhost, ::1, or in ai.localHosts
}
public final class ProviderStore {                        // pattern: service/fo/FoProcessorStore
    public static final String KEY_PROVIDERS = "ai.providers", KEY_DEFAULT = "ai.provider.default";
    public ProviderStore(Function<String,String> getter, BiConsumer<String,String> setter, BooleanSupplier remoteAllowed);
    public static ProviderStore settings();
    public List<ProviderConfig> list(); public void save(List<ProviderConfig> providers);
    public Optional<ProviderConfig> defaultProvider(); public void setDefaultId(String id);  // refuses remote when !remoteAllowed
    public boolean isPolicyFixed();                       // AdminPolicy.isLocked(KEY_PROVIDERS)
}
public final class ProviderSecrets {
    public static String keyName(String providerId);      // "ai.provider.<id>.apiKey"
    public Optional<String> apiKey(ProviderConfig p);     // env var first, then the stored key; never from policy/defaults
    public void store(String providerId, String key); public void remove(String providerId);
}
```
- [x] **Step 1 (red):** round trip of the list (Gson `Stored` DTO, defaults written as null); a removed provider also
  removes its key; the properties file on disk contains `ENC(` for `ai.provider.x.apiKey`; a key present in the policy
  overlay or `defaults.properties` is ignored and logged without its value (log capture); `isLocal` for `localhost`,
  `127.0.0.1`, `[::1]`, a host from `ai.localHosts`, a public host; `setDefaultId` of a remote provider under
  `ai.remote.allowed=false` throws; an unknown preset name falls back to `CUSTOM`.
- [x] **Step 2 (green):** implement; `isPasswordProperty` extension with its own test cases. **Step 3:** commit.

### Task 3: `ChatProvider` API and the OpenAI-compatible provider (Chat Completions, D14)

**Files:**
- Create: `service/ai/provider/{ChatProvider, ChatRequest, ChatMessage, ChatResponse, ToolCall, StopReason, Usage,
  ProviderException, CancelToken, SseReader, ProviderHttp, OpenAiCompatibleProvider, ModelLister}.java`
- Test: `service/ai/provider/{FakeChatServer, SseReaderTest, OpenAiCompatibleProviderTest}.java`,
  `service/ConnectionServiceLoopbackTest.java` (pins correction 8)

**Interfaces:**
```java
public interface ChatProvider {
    ChatResponse chat(ChatRequest request, Consumer<String> onTextDelta, CancelToken cancel) throws ProviderException;
    List<String> listModels(CancelToken cancel) throws ProviderException;   // empty when the endpoint has none
}
public record ChatRequest(String system, List<ChatMessage> messages, List<AiTool> tools, int maxOutputTokens,
                          Double temperature /* OpenAI-compatible only; never sent to Anthropic (2026-10-21) */) { }
public record ChatMessage(Role role /* USER, ASSISTANT, TOOL */, String text, List<ToolCall> toolCalls,
                          String toolCallId, boolean toolError, JsonArray providerBlocks /* opaque, replayed unchanged */) { }
public record ToolCall(String id, String name, JsonObject arguments) { }
public record ChatResponse(String text, List<ToolCall> toolCalls, StopReason stop, Usage usage, ChatMessage asMessage) { }
public enum StopReason { END, MAX_TOKENS, TOOL_USE, REFUSED, CONTENT_FILTER, OTHER }
public final class ProviderException extends Exception { Kind kind(); OptionalInt retryAfterSeconds(); String requestId();
    enum Kind { AUTH, PERMISSION, NOT_FOUND, RATE_LIMIT, OVERLOADED, BAD_REQUEST, TOO_LARGE, SERVER, UNREACHABLE,
                TIMEOUT, CANCELLED, PROTOCOL } }   // message user-safe: no key, no prompt
```
`ProviderHttp` = the shared plumbing: `openConnection(URI)`, own read timeout, headers, JSON body, status → `Kind`,
`Retry-After`, cancel by `disconnect()`.

**Wire format (OpenAI-compatible), from the documentation:**
- `POST {baseUrl}/chat/completions`; `Authorization: Bearer <key>` or `api-key: <key>` (Azure v1,
  learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle); Ollama needs a non-empty key value but
  ignores it (docs.ollama.com/api/openai-compatibility).
- Body: `model`, `messages` (roles `system`, `user`, `assistant` with `tool_calls`, `tool` with `tool_call_id`), the
  token field per `ProviderConfig.tokenField` — OpenAI deprecates `max_tokens` for `max_completion_tokens`, Ollama
  documents `max_tokens` (developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create;
  Ollama page above); `temperature` only when set; `tools: [{type:"function", function:{name, description,
  parameters}}]` (names ≤ 64 chars `[a-zA-Z0-9_-]`); **no `tool_choice`** (Ollama does not support it); `stream: true`
  with `stream_options: {include_usage: true}`.
- Response: `choices[0].message.content` (string or null), `tool_calls[] {id, type:"function", function{name,
  arguments: JSON string}}`, `finish_reason` `stop|length|tool_calls|content_filter`, `usage` optional.
- Streaming: `data: {chunk}` lines, `data: [DONE]` ends (…/streaming-events); `delta.content` text, `delta.tool_calls[]`
  accumulated **by `index`**, `arguments` arrive in fragments and may be invalid JSON → `PROTOCOL` with feedback;
  `usage` on the last chunk only, may be missing when the stream breaks.
- Errors: 401 → AUTH, 403 → PERMISSION (region), 404 → NOT_FOUND, 429 → RATE_LIMIT (`Retry-After` seconds; quota and
  billing 429s are not worth retrying), 500 → SERVER, 503 → OVERLOADED (`Retry-After`); body `{"error":{"message",
  "type","code"}}`, message treated as possibly echoing the prompt (developers.openai.com/api/docs/guides/error-codes,
  …/guides/rate-limits).
- Models: `GET {baseUrl}/models` → `{data:[{id}]}`; Ollama and LM Studio offer it.

- [x] **Step 1 (red):** `SseReaderTest` — `data:` lines across chunk boundaries, comments (`:`), `event:` names, CRLF,
  `[DONE]`, a blank-line-terminated multi-line `data`. `OpenAiCompatibleProviderTest` against `FakeChatServer` (scripted
  responses, records requests): text answer; streamed deltas assembled; a tool call round trip (assistant `tool_calls`
  then `tool` message); tool-call deltas over several chunks by index; invalid argument JSON → PROTOCOL; 401/403/404/
  429 (`Retry-After` surfaced)/503; connection refused → UNREACHABLE; slow first byte beyond the read timeout →
  TIMEOUT; cancel mid-stream → CANCELLED within 1 s; token field per config; no `tool_choice` sent; the key sent and in
  no log line (log capture); `listModels`.
- [x] **Step 2 (red):** `ConnectionServiceLoopbackTest` — `proxyFor(settings with manual proxy, http://localhost:11434)`
  and `[::1]` are `Proxy.NO_PROXY`.
- [x] **Step 3 (green):** implement. **Step 4:** commit.

### Task 4: Anthropic provider (Messages API)

**Facts from the documentation** (all on platform.claude.com, fetched 2026-11-10):
- Headers `x-api-key`, `anthropic-version: 2026-10-21`, `content-type: application/json` (curl reference). Version
  `2026-10-21` **removes `temperature`, `top_p`, `top_k` — any of them is a 400 on every model**
  (/docs/en/api/versioning.md).
- Tools: `{name, description, input_schema}` (+ optional `strict: true`); `tool_choice` `auto` / `none` only — forced
  `any` / `tool` is a 400 on current models (/docs/en/api/errors.md); `stop_reason: "tool_use"`; results as
  `{"type":"tool_result","tool_use_id","content"}` in the next user message, all results of one turn together
  (/docs/en/agents-and-tools/tool-use/overview.md).
- `thinking` / `redacted_thinking` blocks must be passed back exactly as received in tool loops (errors page) →
  `ChatMessage.providerBlocks`.
- `stop_reason` may be `refusal` (→ `StopReason.REFUSED`, shown as such, never retried as a repair round).
- Streaming events `message_start`, `content_block_start|delta|stop`, `message_delta` (`stop_reason`, cumulative
  `usage`), `message_stop`, `ping`, `error` (`{"type":"error","error":{"type","message"}}`); deltas `text_delta`,
  `input_json_delta` (`partial_json`, parse after `content_block_stop`), `thinking_delta`, `signature_delta`
  (/docs/en/build-with-claude/streaming.md).
- Errors: 400 invalid_request, 401 authentication, 402 billing, 403 permission, 404 not_found, 409 conflict, 413
  request_too_large, 429 rate_limit (`retry-after`; the spend-cap 429 has none), 500 api_error, 504 timeout_error,
  529 overloaded_error; body `{"type":"error","error":{…},"request_id"}`, header `request-id` (/docs/en/api/errors.md,
  /docs/en/api/rate-limits.md).
- Models: `GET /v1/models` (`id`, `display_name`, `max_input_tokens`, `max_tokens`).

**Variant SDK (D13 = SDK)** — Files: `build.gradle.kts` (`com.anthropic:anthropic-java-core:<latest stable>`),
`service/ai/provider/anthropic/{AnthropicProvider, ConnectionServiceHttpClient}.java`; `AiLayeringGuardTest` allows
`com.anthropic` only in that package; `docs/licenses.md` + SBOM. `ConnectionServiceHttpClient implements
com.anthropic.core.http.HttpClient` maps `HttpRequest` → `openConnection`, streams the body back as `HttpResponse`;
the client is built with `maxRetries(0)` (retries are ours) and without OkHttp. Streaming via
`client.messages().createStreaming(params)`.
**Variant raw HTTP (D13 = raw)** — Files: `service/ai/provider/AnthropicProvider.java` on `ProviderHttp` + `SseReader`.

- [x] **Step 1 (red):** the same matrix as Task 3 against `FakeChatServer` with Anthropic-shaped scripts, plus: no
  `temperature` in the request even when `ChatRequest.temperature` is set; tool round trip with `tool_result`; thinking
  blocks replayed byte-identical in the next request; `refusal` → REFUSED; 529 → OVERLOADED; mid-stream `error` event →
  OVERLOADED/SERVER; `request-id` kept on the exception; `x-api-key` in no log line.
- [x] **Step 2 (red):** `ProviderContractTest` runs both providers through identical scenarios (text, stream, tool
  round trip, 401, 429, cancel) so the mapping stays symmetrical.
- [x] **Step 3:** opt-in live smoke tests (`@EnabledIfEnvironmentVariable` `FXT_AI_LIVE_OPENAI_BASEURL`,
  `FXT_AI_LIVE_ANTHROPIC_KEY`, model from `FXT_AI_LIVE_MODEL`), never in CI.
- [x] **Step 4 (green):** implement. **Step 5:** spec D5/D6 updated with the decision; commit.

### Task 5: Context builder, budget, redaction

**Files:**
- Move: `service/ai/tool/SchemaCache.java` → `service/ai/context/SchemaCache.java` (public; tools import it)
- Create: `service/ai/context/{ContextBuilder, ContextBudget, Redactor, AiContext}.java`
- Test: `service/ai/context/{ContextBuilderTest, ContextBudgetTest, RedactorTest}.java`

**Interfaces:**
```java
public record AiContext(String systemPrompt, String dataBlock, DataLevel level, int characters, List<String> dropped) { }
public final class ContextBuilder {
    public ContextBuilder(DataLevel level, ContextBudget budget);
    public AiContext forProblem(String xml, Path schema /* nullable */, ValidationProblemFacts problem);
    public AiContext forQuery(String xml, String question, boolean xquery);
    public AiContext forStylesheet(String source, String targetSample, Path targetSchema, String notes);
}
public record ValidationProblemFacts(String source, int line, int column, String message, String instancePath) { }
public final class Redactor {
    public static String outline(XmlOutline.Outline o);            // = o.toText() with samples off
    public static String message(String validatorMessage);          // strips quoted values: 'x', "x", value 'x'
    public static String fragment(String xml);                      // values → type placeholders ({decimal}, {text})
}
```
- Data is wrapped as `<document-data>…</document-data>` / `<schema-data>…</schema-data>`; the system prompt states it
  is data, not instructions (D12). `ContextBudget` counts characters (proxy for tokens) and drops in a fixed order:
  samples → outline depth → sibling declarations; `dropped` lists what went.
- The UI passes the schema `File` it gets from `EditorHost.schemaForValidation` (correction 3); `service/ai` never
  touches `EditorHost`.
- [x] **Step 1 (red):** per level, a document with marker values (`MARKER-7f3a` in text, attributes, a comment, and a
  Xerces message quoting it): at SCHEMA and STRUCTURE the marker appears nowhere in `AiContext`; at EXCERPTS only
  inside the excerpt. Budget respected, drop order as documented. A 100 MB generated file (path, streamed) yields a
  context under budget without a DOM (heap delta check, generous bound).
- [x] **Step 2 (green):** implement; tools updated to the moved `SchemaCache`. **Step 3:** commit.

### Task 6: `VerifiedGeneration` and verifiers

**Files:**
- Create: `service/ai/agent/{VerifiedGeneration, Verifier, Verification, Check, CandidateExtractor, XPathVerifier,
  XQueryVerifier, XsltVerifier, ProblemFixVerifier, FixProposal, InAppToolContext}.java`
- Modify: `service/ai/tool/ToolInput.java` — `<name>_document` (an open document reference) when
  `ToolContext.openDocument` offers it; `service/SandboxedQuery.java` — parameter `declareDocumentPrefixes`
- Test: `service/ai/agent/{VerifiedGenerationTest, XPathVerifierTest, XsltVerifierTest, ProblemFixVerifierTest,
  CandidateExtractorTest}.java` with a scripted `ChatProvider` (no HTTP)

**Interfaces:**
```java
public interface Verifier { Verification verify(String candidate, CancelToken cancel); }
public record Verification(Status status /* VERIFIED, FAILED, NOT_CHECKABLE */, List<Check> checks, String feedbackForModel) { }
public record Check(String label, boolean passed, String detail) { }
public final class VerifiedGeneration {
    public record Outcome(Status status, String candidate, Verification verification, int repairRounds,
                          List<ChatMessage> transcript) { }
    public interface Listener { void round(int n, String phase /* generating, checking */); }
    public Outcome run(ChatProvider provider, ChatRequest first, CandidateExtractor extract, Verifier verifier,
                       int maxRepairs /* 3 */, Listener progress, CancelToken cancel) throws ProviderException;
}
public record FixProposal(String xpath, Map<String,String> namespaces, ReplaceMode mode, String replacement) { }
```
- Verifiers run the Phase 1 tools via `ToolRegistry.call(name, args, InAppToolContext)`: `xpath`/`xquery`,
  `xslt_transform` (+ `validate` of the output against the target XSD, `compare` with the target sample — reported, not
  required), `validate` for fixes. `InAppToolContext` = `ToolContext` with roots (project folders + open documents'
  folders) and `openDocument`.
- `XPathVerifier` / `XQueryVerifier` call `SandboxedQuery` with `declareDocumentPrefixes=false` (correction in "What
  Phase 1 changed"), so a verified expression runs the same in the console.
- `ProblemFixVerifier`: the model answers a `FixProposal` (JSON in one fenced block) → `XPathMatchLocator.locate` →
  `XPathReplacePlanner.plan` → `EditPlan.applyTo(copy)` → `validate` on the copy: the problem is gone, no new problem,
  no element removed unless the mode is DELETE and the user asked; change size reported.
- `CandidateExtractor`: exactly one fenced block of the expected language; none or several → a failed round with
  feedback.
- Repair feedback = the tool's `error{code, phase, line, message}` and failing checks, as data. A `REFUSED` stop ends
  the run (no repair).
- [x] **Step 1 (red):** scripted provider — wrong first candidate → feedback contains code, line, message → right
  second → VERIFIED after 1 repair; never right → FAILED after `maxRepairs` with the last report; cancel between
  rounds; REFUSED ends at once; extractor cases; a fix that deletes an element is rejected; a fix whose region overlaps
  another edit is rejected; the verifier writes no file (`ToolsWriteNothingTest` pattern).
- [x] **Step 2 (green):** implement. **Step 3:** commit.

### Task 7: Settings group, provider dialog, send preview

**Files:**
- Modify: `pages/views/SettingsPanel.fxml` (new `settings-group-ai` with cards *AI assistant* and *Providers*),
  `controls/shell/editor/SettingsPanel.java` (`GROUP_ICONS` `ai` → `bi-stars`; remove the group node when
  `!AiPolicy.allowed()` before `sections()`; `policyKeys()` entries for `ai.enabled`, `ai.dataLevel`,
  `ai.dataLevel.remote`, `ai.preview.always`, `ai.providers`/`ai.provider.default`; `bindDisable` of the dependent
  controls on `ai.enabled`)
- Create: `controls/shell/editor/ai/{AiProvidersSettings, AiProviderDialog, AiSendPreviewDialog, AiConsent}.java` +
  `pages/views/AiProvidersSettings.fxml`, `pages/dialogs/{AiProviderDialog, AiSendPreviewDialog}.fxml`; i18n keys
- Test: `controls/shell/editor/ai/{AiProvidersSettingsTest, AiProviderDialogTest, AiSendPreviewDialogTest}.java`,
  extend `SettingsPanelPolicyTest`
- `AiProvidersSettings` follows `FoProcessorsSettings` (saves the list immediately, buttons disabled when
  `isPolicyFixed()`); the scalar settings go through the page's Save (`trackChanges`).
- `AiProviderDialog` follows `FoProcessorDialog` (`DialogHelper.prepare`, FXML content, `validate()`); **Test** sends a
  one-line request off the FX thread and shows the model, latency and *local / remote*; a remote provider under
  `ai.remote.allowed=false` cannot be tested or saved as default (message says why). The model field is a combo filled
  from `listModels`, editable.
- `AiSendPreviewDialog` shows the exact request text, provider, local/remote badge, data level; "Don't show again for
  this provider" stored per id. Shown the first time per provider and always when `ai.preview.always` (default on for
  remote providers).
- `AiConsent`: first use of a remote provider asks once, naming the host (stored per provider id).
- [x] **Step 1 (red):** TestFX — the group is absent under `ai.allowed=false` (no node, no navigator entry);
  `ai.enabled` off disables the dependent controls; policy-locked keys are disabled with the notice; the dialog
  validates; the API key field is a `PasswordField` and the key never appears in the provider JSON; Test against
  `FakeChatServer`; the preview shows the marker-free text at SCHEMA level.
- [x] **Step 2 (green):** implement. **Step 3:** screenshots in `AppearanceDocScreenshotGenerator`'s style (new
  `AiDocScreenshotGenerator`). **Step 4:** commit.

### Task 8: Proposal view (bottom dock)

**Files:** Create `controls/shell/editor/ai/AiProposalView.java` + `pages/views/AiProposalView.fxml`; registered with
`EditorHost.bottomDock().register("AI", view, I18n.t(…))` (correction 10). Test `AiProposalViewTest`.
- Progress (round n, generating / checking), the diff (two `PlainTextCodeArea`s, `DiffEngine.compute`,
  `DiffHighlighter.computeHighlighting`), the verification report (checks with `bi-check-circle` / `bi-x-circle`),
  change size; **Apply** (disabled with the reason when the document changed — identity + revision key), **Apply
  unverified** only after an explicit second confirmation, **Discard**, **Copy**, **Open as new document**
  (`openGeneratedDocument`), **Cancel** while running.
- [x] **Step 1 (red):** TestFX — apply is one undo step and undo restores the text exactly; apply refused after an edit
  in another tab with the same revision key; unverified needs the second confirmation; cancel stops the scripted
  provider; Open as new document creates an unsaved tab.
- [x] **Step 2 (green):** implement. **Step 3:** commit.

### Task 9: Action — explain / fix a validation problem

**Files:** Modify `controls/shell/editor/ProblemsPanel.java` (`contextMenu`), `ValidationPanel.java` (row menu also
without SQF fixes, AI items after a separator, SQF first); Create `controls/shell/editor/ai/AiActions.java`. Test
`AiProblemActionsTest`.
- "Explain" = a short answer with the schema slice of the failing node (`SchemaSlice` at the problem's instance path),
  shown as text in the proposal view, no verifier. "Fix" = `VerifiedGeneration` with `ProblemFixVerifier`; apply as in
  correction 2; re-validate.
- [x] **Step 1 (red):** with AI off or not allowed the items are absent; with a scripted provider the fix is applied as
  one undo step, undo restores; a stale proposal is not applied; a REFUSED answer shows the refusal.
- [x] **Step 2 (green):** implement. **Step 3:** commit.

### Task 10: Action — natural language → XPath / XQuery

**Files:** Modify `controls/shell/editor/QueryConsole.java` + `pages/views/QueryConsole.fxml` (an **Ask** toggle next to
XPath/XQuery, XML documents only — correction 5); `AiActions`. Test `QueryConsoleAskTest`.
- The answer is placed **into the input field** (editable) with the verification result underneath; Run stays the
  user's action. The prompt asks for namespace-independent paths (`*:name` or `Q{uri}name`) because the console
  declares no prefixes; the verifier runs with `declareDocumentPrefixes=false`.
- [x] **Step 1 (red):** scripted provider — a prefixed expression that only works with declared prefixes fails
  verification and is repaired; a non-compiling expression is repaired or shown as failed, never placed silently;
  saving as a snippet still works.
- [x] **Step 2 (green):** implement. **Step 3:** commit.

### Task 11: Action — generate a stylesheet from a source and a target sample

**Files:** Modify `pages/panels/TransformPanel.fxml` (TOOLS list: `PanelActionButton` *Generate stylesheet…*),
`TransformPanel.java`; Create `controls/shell/editor/ai/GenerateStylesheetDialog.java` + FXML. Test
`GenerateStylesheetTest`.
- Dialog: source (default `inputDocument()`), target sample (file or open document), optional target XSD, notes, XSLT
  version (default 3.0). Result: a new unsaved `.xsl` (`openGeneratedDocument`) plus the proposal view with the checks
  (compiled, ran, output valid, differences to the sample).
- [x] **Step 1 (red):** scripted provider end to end; the document is unsaved and dirty; nothing is written to disk
  (folder snapshot).
- [x] **Step 2 (green):** implement. **Step 3:** commit.

### Task 12: Telemetry, evaluation set, documentation

**Files:**
- Modify: `service/telemetry/UsageEvents.java` — `aiAction(String actionId, String providerKind, String outcome,
  int repairRounds, long startNanos)` on the `action(…)` helper; values from fixed enums only
- Create: `src/test/resources/ai/evals/*.json` (20–30 cases), `src/test/java/…/ai/eval/AiEvalRunner.java` (tag
  `ai-eval`, excluded from `test`), Gradle task `aiEval` (`-Pai.baseUrl=… -Pai.model=… [-Pai.kind=anthropic]`, key from
  the environment) writing `build/ai-eval.md`
- Docs: new `docs/ai-assistant.md`; update `docs/telemetry.md` (event + what is never sent), `docs/threat-model.md`
  (provider connection; prompt injection), `docs/offline-operation.md` (row "AI provider"), `docs/administration.md`
  (`ai.*` keys), `docs/settings.md`, `docs/comparison.md`, `docs/whats-new.md`, `README.md` (Privacy paragraph),
  `mkdocs.yml`, `CLAUDE.md` "AI and MCP", `.claude/rules/ai-integration.md`
- [x] **Step 1 (red):** `UsageEventsTest` — the event carries no free text (only the fixed values); no event when AI is
  off.
- [x] **Step 2:** eval runner; run once against one small local model and one large hosted model; numbers into
  `docs/ai-assistant.md` ("what to expect from which class of model").
- [x] **Step 3:** docs; `mkdocs build` without warnings on the changed pages; full `./gradlew test`. Commit.

## Definition of done for Phase 2

- With default settings a recording `ConnectionService` sees no AI request in a full UI test run.
- With a local provider configured, all three actions work end to end against `FakeChatServer` in TestFX tests, and
  manually against a real local model.
- At data level `SCHEMA`, the request text of every action contains no marker value from the document.
- `ai.allowed=false` removes every entry point; `ai.remote.allowed=false` blocks non-local providers.
- The evaluation report exists and its numbers are in the user documentation.
- The SDK's footprint is in `docs/licenses.md` and the SBOM.

## Outcome (implemented 2026-11-10)

All twelve tasks are implemented on `feature/ai-phase2`. Deviations from the steps above:

- **Task 3/4:** one test class per provider (`OpenAiCompatibleProviderTest`, `AnthropicProviderTest`) with the same
  scenarios (text, stream, tool round trip, errors, cancel, no key in the log) instead of a shared
  `ProviderContractTest`. `ToolCall` keeps the raw arguments and `arguments == null` for invalid JSON (the caller
  answers with a tool error) instead of a `PROTOCOL` failure. An old server that rejects `stream_options` is retried
  once without it. Live smoke tests are replaced by the `aiEval` task, which runs against a real model.
- **Task 6:** verifiers call `SandboxedQuery` directly (XPath/XQuery, to pass `declareDocumentPrefixes=false`) and the
  Phase 1 tools through `ToolRegistry` (xslt_transform, validate, compare). `ToolInput` got no `_document` argument:
  the verifiers already hold the text. `ProblemFixVerifier` keeps the plan of every applicable candidate, so
  "Apply unverified" works.
- **Task 7:** the AI settings are one self-saving pane (`AiSettingsPane`) in a new `settings-group-ai`, like the
  XSL-FO processors — not page-Save controls. No documentation screenshots yet (the generator needs a display run).
- **Task 10:** an **Ask AI…** button next to the console's target instead of an Ask toggle — it asks for the question
  in a dialog and leaves Run's meaning unchanged.
- **Task 11:** a verified stylesheet opens at once as a new unsaved document; an unverified one only via
  **Open as new document**.
- **Task 12:** the evaluation set has 22 cases; `./gradlew aiEval`. Run twice against `qwen2.5-coder:3b` (Ollama,
  CPU): 15/1/6 and 8/6/8 (first try / after repair / not verified), expected query result 0 and 1 of 14 — the
  numbers are in `docs/ai-assistant.md`. Numbers for a hosted model need a key and are still open. The report shows
  each generated query, so a "verified" but wrong answer is visible.

