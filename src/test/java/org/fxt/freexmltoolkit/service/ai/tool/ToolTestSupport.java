package org.fxt.freexmltoolkit.service.ai.tool;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Helpers shared by the tool tests. */
final class ToolTestSupport {

    private ToolTestSupport() {
    }

    static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** Arguments from alternating names and values (strings, numbers, booleans, JsonElements). */
    static JsonObject args(Object... namesAndValues) {
        JsonObject args = new JsonObject();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            String name = (String) namesAndValues[i];
            Object value = namesAndValues[i + 1];
            switch (value) {
                case String s -> args.addProperty(name, s);
                case Number n -> args.addProperty(name, n);
                case Boolean b -> args.addProperty(name, b);
                case com.google.gson.JsonElement e -> args.add(name, e);
                default -> throw new IllegalArgumentException(String.valueOf(value));
            }
        }
        return args;
    }

    static ToolContext context(Path root) {
        return ToolContext.of(new WorkspaceAccess(List.of(root)), ToolLimits.DEFAULTS);
    }

    static ToolContext context(Path root, int maxOutputChars, int maxItems) {
        return ToolContext.of(new WorkspaceAccess(List.of(root)),
                new ToolLimits(Duration.ofSeconds(30), maxOutputChars, 64L * 1024 * 1024, maxItems));
    }
}
