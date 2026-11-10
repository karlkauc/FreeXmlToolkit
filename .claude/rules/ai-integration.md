# AI Integration Rules

Read this before touching `service/ai/`, `controls/shell/editor/ai/` or `cli/McpCommand`. User docs: `docs/mcp-server.md`, `docs/ai-assistant.md`.
Design: `docs/superpowers/specs/2026-11-10-ai-integration-design.md`.

## Invariants (each is pinned by a test — keep the test, do not weaken it)

| Rule | Why | Test |
|---|---|---|
| `service/ai/**` imports no `javafx.*`; MCP SDK types only in `service/ai/mcp/`, Anthropic SDK types (`com.anthropic`) only in `service/ai/provider/anthropic/` (D13) | The tool layer serves the CLI and the UI; SDK choices stay reversible | `AiLayeringGuardTest` |
| HTTP only through `ConnectionService` (`openConnection` for providers) | Proxy, NTLM, timeouts, certificates; one place opens connections | `HttpCallsGuardTest` |
| Tools write no file; `xsl:result-document` goes to memory | An agent runs code it wrote itself | `TransformRunnerSandboxTest`, registry no-write test |
| File arguments resolve through `WorkspaceAccess` only | No reads outside the declared roots | `WorkspaceAccessTest` |
| `fxt-cli mcp` writes only protocol messages to stdout | Anything else breaks the client | `StdoutGuardTest` |
| No prompt, response, tool argument, host or user-entered model name in telemetry or in logs above DEBUG | The application promises that document content is never sent | `UsageEventsTest.aiActionsCarryFixedValuesOnly`, `*ProviderTest.theKeyIsInNoLogLine` (`LogCapture`) |
| At data levels SCHEMA and STRUCTURE no document value is in a request | The data-level promise of Settings → AI | `ContextBuilderTest`, `RedactorTest` (marker values) |
| An AI proposal is applied only by the user and never to a changed document | D8 | `AiActionsUiTest` |
| With `ai.allowed=false` no AI control exists; `ai.mcp.allowed=false` refuses the command | Administrators must be able to remove the feature | policy tests |
| Tool names, arguments and result fields are public API | External agents depend on them | `ToolSurfaceSnapshotTest` |

## Conventions

- A failing stylesheet / query / validation is a `ToolResult` with `error == true` carrying code, line and message. `ToolException` is only for misuse of the tool.
- Every tool input is "`*_path` or `*_content`", never both. Inline content is what lets an agent verify what it just wrote.
- Results are bounded by `ToolLimits`; truncate and say so (`truncated`, total length / count).
- New engine logic goes into the UI-free runners (`controls/shell/editor/*Runner`) or `service/`, then a thin `AiTool` wraps it — the shell, the CLI and the tools share one implementation.
- Document text, schema documentation and tool results are **data** for a model, never instructions: delimit them, say so in the system prompt, and never let them trigger a tool with side effects without the user's confirmation.
- A model's output is offered only with its verification report; applying is the user's action and one undo step.
- Provider wire formats: read the provider's current documentation before changing a provider class. No model names in code. Anthropic goes through its Java SDK on the `ConnectionService` adapter (D13, `maxRetries(0)`); OpenAI-compatible is Chat Completions on raw HTTP (D14).
- Settings: keys `ai.*`; every control in `SettingsPanel.policyKeys()`; policy-only keys documented in `docs/administration.md` in the same change.
- When a tool is added or changed: update the snapshot, `docs/mcp-server.md`, `docs/whats-new.md`.
