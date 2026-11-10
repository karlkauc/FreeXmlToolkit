package org.fxt.freexmltoolkit.service.ai.tool;

import com.google.gson.JsonObject;

/**
 * One engine of the application exposed as a callable tool, for an external agent (through
 * {@code fxt-cli mcp}) and later for the in-app assistant. The name, the argument names and the result
 * fields are public API: external agents depend on them ({@code ToolSurfaceSnapshotTest}).
 *
 * <p>A failing stylesheet, query or validation is a {@link ToolResult} with {@code error == true} that
 * carries code, line and message — a model needs them to repair its work. {@link ToolException} is only
 * for misuse of the tool (unknown argument, path outside the roots).
 */
public interface AiTool {

    /** Stable {@code snake_case} name. */
    String name();

    /** For the model: what the tool does, when to use it, its limits. */
    String description();

    /** JSON Schema (draft 2020-12) of the arguments. */
    JsonObject inputSchema();

    /** {@code false} = the tool has side effects and needs the user's confirmation or an explicit opt-in. */
    default boolean readOnly() {
        return true;
    }

    /**
     * Runs the tool.
     *
     * @throws ToolException when the tool is misused (bad arguments, path outside the roots)
     */
    ToolResult call(JsonObject arguments, ToolContext context) throws ToolException;
}
