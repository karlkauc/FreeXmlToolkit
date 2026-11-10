package org.fxt.freexmltoolkit.service.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The tool names, descriptions and argument schemas are public API: external agents (through {@code fxt-cli mcp})
 * depend on them. This test fails on any change; a change is deliberate only together with a line in
 * {@code docs/whats-new.md} and the tool reference in {@code docs/mcp-server.md}.
 */
class ToolSurfaceSnapshotTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/ai/mcp-tools.snapshot.json");

    static String surface() {
        JsonArray tools = new JsonArray();
        for (AiTool tool : ToolRegistry.standard().tools()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("read_only", tool.readOnly());
            entry.addProperty("description", tool.description());
            entry.add("input_schema", tool.inputSchema());
            tools.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(tools) + "\n";
    }

    @Test
    void theToolSurfaceIsUnchanged() throws Exception {
        String actual = surface();
        if (Boolean.getBoolean("fxt.ai.updateSnapshot")) {
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
        }
        String expected = Files.exists(SNAPSHOT) ? Files.readString(SNAPSHOT, StandardCharsets.UTF_8) : "";
        assertEquals(expected, actual, """
                The MCP tool surface changed. Tool names, descriptions and argument schemas are public API.
                If the change is intended: run the test with -Dfxt.ai.updateSnapshot=true to rewrite
                src/test/resources/ai/mcp-tools.snapshot.json, update the tool reference in docs/mcp-server.md
                and add a line to docs/whats-new.md.""");
    }
}
