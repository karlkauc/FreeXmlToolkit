package org.fxt.freexmltoolkit.service.ai.tool;

import com.google.gson.JsonObject;

/** Result fields shared by several tools. */
final class ToolJson {

    private ToolJson() {
    }

    /**
     * The {@code error} object of a failed run: {@code code}, {@code phase} ({@code compile}, {@code input},
     * {@code runtime}), {@code line}, {@code message}; absent values are JSON {@code null}.
     */
    static JsonObject error(String code, String phase, int line, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("phase", phase);
        if (line > 0) {
            error.addProperty("line", line);
        } else {
            error.add("line", com.google.gson.JsonNull.INSTANCE);
        }
        error.addProperty("message", message);
        return error;
    }

    /** "line 3: [XTSE0010] message" for the text of a result. */
    static String describe(String code, String phase, int line, String message) {
        StringBuilder text = new StringBuilder();
        if (phase != null) {
            text.append(phase).append(" error");
        } else {
            text.append("error");
        }
        if (line > 0) {
            text.append(" at line ").append(line);
        }
        if (code != null) {
            text.append(" [").append(code).append(']');
        }
        return text.append(": ").append(message == null ? "" : message).toString();
    }

    /** Puts {@code text} truncated to the limit under {@code name} and records {@code truncated}. */
    static void putText(JsonObject target, String name, String text, ToolLimits limits) {
        String value = text == null ? "" : text;
        target.addProperty(name, limits.truncate(value));
        target.addProperty("truncated", limits.isTruncated(value));
    }
}
