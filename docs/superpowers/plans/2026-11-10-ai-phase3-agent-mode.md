# AI Phase 3 — Agent Mode and Further Actions (Outline)

> This is an outline, not an executable plan. Write the detailed plan (house format, red/green steps) after Phase 2 has shipped and its evaluation numbers and user feedback are in. Do not start Phase 3 before that.

**Goal:** A tool-using assistant inside the application for multi-step XML work, further verified actions, and a contribution point so plugins can add domain tools.

**Spec:** `docs/superpowers/specs/2026-11-10-ai-integration-design.md`.

## Entry criteria

- Phase 2 released; the evaluation set shows which model classes make tool calls reliably (agent mode is offered only for providers whose **Test** confirms tool calling).
- Open questions from Phase 2 (proposal view placement, edit format for fixes) are settled in the spec.

## Work packages

1. **`service/ai/agent/AgentLoop`** — messages + tool definitions → model → tool calls executed through `ToolRegistry.call` → results back → until a final answer. Caps: rounds (default 12), total tool time, request budget; every step is reported to a listener; cancellable at every step. Scripted-provider tests for: multi-tool turns, a tool error the model recovers from, the caps, cancel.
2. **Write tools with confirmation** — `propose_edit` (an edit to an open document, shown in the proposal view, applied by the user), `create_document` (a new unsaved document). `AiTool.readOnly() == false`; the loop pauses and the UI asks per call; never exposed through `fxt-cli mcp` unless a later decision adds an explicit opt-in flag. No tool writes to disk.
3. **`AiAssistantPanel`** — an activity (rail button, side panel; visible only when AI is enabled) with the conversation, the tool calls as collapsible steps (name, arguments, result summary), the context chips (active document, selection, schema), and the proposals. This is the only chat-like surface, and it is the front end of the agent, not a general chatbot: the system prompt scopes it to the open project and the XML tool set.
4. **Further verified actions**
   - Schematron rule from a business rule in the user's words → verified against positive and negative samples the user marks (or the agent generates and the user confirms); optional SQF fix.
   - XSpec tests for an existing stylesheet → verified by running them; shows which templates stay uncovered (the debugger's coverage data).
   - `xs:documentation` drafts for undocumented declarations → applied through XSD editor commands (`CommandManager`, never by editing `XsdNode` directly) so every change is undoable; reviewable as a list before applying.
   - Domain-plausible test data → on top of `SampleXmlRunner`, verified by XSD and Schematron validation.
5. **Plugin contribution point** — `PluginContext.addAiTool(AiTool)` (a new method = compatible API evolution, see `docs/plugin-development.md` "Compatibility"); move `AiTool`, `ToolResult`, `ToolContext`, `ToolException` into the stable `org.fxt.freexmltoolkit.plugin` API or expose thin plugin-facing equivalents — decide deliberately, the `plugin` package is a compatibility promise. Reference use: the FundsXML plugin contributes a tool that explains a FundsXML element from the bundled documentation; FinDatEx contributes a template field lookup. Plugin tools appear in the MCP server too.
6. **Workflows worth a guided entry point** (each is the agent with a preset goal and a fixed verification): migrate documents between two schema versions (diff the schemas → stylesheet → validate the outputs of a sample folder); map between two standards from two sample files.
7. **Documentation, threat model, telemetry** as in Phase 2.

## Risks to settle in the detailed plan

- Cost and latency of long agent runs with remote providers: show rounds and an estimate; stop at the caps.
- Tool results are untrusted input to the model (D12): a document can contain text that tries to steer the agent. All write tools require confirmation; no tool reads outside the roots; no tool reaches the network.
- Scope creep into a general assistant: the panel offers no file-system or shell tools and no web access.
