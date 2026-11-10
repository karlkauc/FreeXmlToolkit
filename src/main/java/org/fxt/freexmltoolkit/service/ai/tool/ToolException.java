package org.fxt.freexmltoolkit.service.ai.tool;

/**
 * Misuse of a tool: a missing, unknown or mistyped argument, a path outside the workspace roots, an
 * input that is too large. The message is for the caller (a model) and names the argument concerned.
 * A failing artefact is not misuse — that is a {@link ToolResult} with {@code error == true}.
 */
public class ToolException extends Exception {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
