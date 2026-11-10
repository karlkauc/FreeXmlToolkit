package org.fxt.freexmltoolkit.service.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ToolArgsTest {

    @TempDir
    Path temp;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void typedAccess() throws Exception {
        ToolArgs args = new ToolArgs(json("""
                {"expression": "//a", "depth": 3, "samples": false,
                 "variables": {"x": "1", "y": "2"}, "ignore_xpaths": ["/a/b", "/a/c"]}"""),
                Set.of("expression", "depth", "samples", "variables", "ignore_xpaths", "unused"));
        assertEquals("//a", args.requiredString("expression"));
        assertEquals(3, args.optionalInt("depth", 2));
        assertEquals(7, args.optionalInt("unused", 7));
        assertFalse(args.optionalBoolean("samples", true));
        assertEquals(Map.of("x", "1", "y", "2"), args.stringMap("variables"));
        assertEquals(List.of("/a/b", "/a/c"), args.stringList("ignore_xpaths"));
        assertTrue(args.optionalString("unused").isEmpty());
        assertTrue(args.stringMap("unused").isEmpty());
    }

    @Test
    void missingRequiredArgumentIsNamed() throws Exception {
        ToolArgs args = new ToolArgs(json("{}"), Set.of("expression"));
        ToolException e = assertThrows(ToolException.class, () -> args.requiredString("expression"));
        assertTrue(e.getMessage().contains("expression"), e.getMessage());
    }

    @Test
    void wrongTypeIsNamed() throws Exception {
        ToolArgs args = new ToolArgs(json("{\"depth\": \"deep\", \"expression\": 5, \"samples\": \"yes\"}"),
                Set.of("depth", "expression", "samples"));
        assertTrue(assertThrows(ToolException.class, () -> args.optionalInt("depth", 2)).getMessage().contains("depth"));
        assertTrue(assertThrows(ToolException.class, () -> args.requiredString("expression")).getMessage()
                .contains("expression"));
        assertTrue(assertThrows(ToolException.class, () -> args.optionalBoolean("samples", true)).getMessage()
                .contains("samples"));
    }

    @Test
    void fractionalNumberIsNotAnInteger() throws Exception {
        ToolArgs args = new ToolArgs(json("{\"depth\": 2.5}"), Set.of("depth"));
        assertThrows(ToolException.class, () -> args.optionalInt("depth", 2));
    }

    @Test
    void unknownKeyIsNamed() {
        ToolException e = assertThrows(ToolException.class,
                () -> new ToolArgs(json("{\"expression\": \"//a\", \"exprssion\": \"x\"}"), Set.of("expression")));
        assertTrue(e.getMessage().contains("exprssion"), e.getMessage());
    }

    @Test
    void nullArgumentsMeanNone() throws Exception {
        ToolArgs args = new ToolArgs(null, Set.of("x"));
        assertTrue(args.optionalString("x").isEmpty());
    }

    @Test
    void inputPairTakesExactlyOne() throws Exception {
        Path root = Files.createDirectories(temp.resolve("root"));
        Files.writeString(root.resolve("a.xml"), "<a/>");
        ToolContext context = ToolContext.of(new WorkspaceAccess(List.of(root)), ToolLimits.DEFAULTS);

        ToolInput inline = ToolInput.required(new ToolArgs(json("{\"xml_content\": \"<x/>\"}"),
                Set.of("xml_path", "xml_content")), "xml", context);
        assertEquals("<x/>", inline.text());
        assertTrue(inline.file().isEmpty());

        ToolInput fromFile = ToolInput.required(new ToolArgs(json("{\"xml_path\": \"a.xml\"}"),
                Set.of("xml_path", "xml_content")), "xml", context);
        assertEquals("<a/>", fromFile.text());
        assertEquals(root.resolve("a.xml").toRealPath(), fromFile.file().orElseThrow());

        ToolException both = assertThrows(ToolException.class, () -> ToolInput.required(new ToolArgs(
                json("{\"xml_path\": \"a.xml\", \"xml_content\": \"<x/>\"}"), Set.of("xml_path", "xml_content")),
                "xml", context));
        assertTrue(both.getMessage().contains("xml_path") && both.getMessage().contains("xml_content"));
        assertThrows(ToolException.class, () -> ToolInput.required(new ToolArgs(json("{}"),
                Set.of("xml_path", "xml_content")), "xml", context));
        assertTrue(ToolInput.optional(new ToolArgs(json("{}"), Set.of("xml_path", "xml_content")), "xml", context)
                .isEmpty());
    }
}
