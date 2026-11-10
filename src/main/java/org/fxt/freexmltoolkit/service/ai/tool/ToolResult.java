package org.fxt.freexmltoolkit.service.ai.tool;

import com.google.gson.JsonObject;

/**
 * The outcome of a tool call.
 *
 * @param text       what a model reads: a short human-readable rendering of the facts
 * @param structured the same facts as JSON, or {@code null}
 * @param error      {@code true} when the checked artefact failed (does not compile, is invalid, timed out)
 *                   or the call could not be made; the text then says why
 */
public record ToolResult(String text, JsonObject structured, boolean error) {

    public ToolResult {
        text = text == null ? "" : text;
    }

    public static ToolResult ok(String text, JsonObject structured) {
        return new ToolResult(text, structured, false);
    }

    public static ToolResult failed(String text, JsonObject structured) {
        return new ToolResult(text, structured, true);
    }
}
