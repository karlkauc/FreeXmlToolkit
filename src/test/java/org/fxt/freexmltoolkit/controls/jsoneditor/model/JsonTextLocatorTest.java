package org.fxt.freexmltoolkit.controls.jsoneditor.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class JsonTextLocatorTest {

    private static final String JSON = """
            {
              // a comment with "quotes" and } braces
              "a": "x}y",
              "b": [1, {"c": true, "d": "e\\"f"}],
              "g": null
            }
            """;

    @Test
    void offsetMapsToTheInnermostValuePath() {
        var root = JsonTextLocator.scan(JSON);
        assertEquals(List.of(), JsonTextLocator.pathAt(root, 0));
        assertEquals(List.of(0), JsonTextLocator.pathAt(root, JSON.indexOf("x}y")));
        assertEquals(List.of(1, 1, 1), JsonTextLocator.pathAt(root, JSON.indexOf("e\\\"f")));
        assertEquals(List.of(2), JsonTextLocator.pathAt(root, JSON.indexOf("\"g\"")));
    }

    @Test
    void pathMapsToTheMemberRangeFromItsKey() {
        var root = JsonTextLocator.scan(JSON);
        var span = JsonTextLocator.spanAt(root, List.of(1));
        assertEquals("\"b\": [1, {\"c\": true, \"d\": \"e\\\"f\"}]", JSON.substring(span.start(), span.end()));
        var item = JsonTextLocator.spanAt(root, List.of(1, 0));
        assertEquals("1", JSON.substring(item.start(), item.end()));
        assertNull(JsonTextLocator.spanAt(root, List.of(5)));
    }

    @Test
    void modelPathsMatchTheTextPaths() throws Exception {
        JsonDocument doc = JsonNodeFactory.parse(JSON);
        JsonNode d = JsonTextLocator.nodeAt(doc, List.of(1, 1, 1));
        assertEquals("d", d.getKey());
        assertEquals(List.of(1, 1, 1), JsonTextLocator.pathOf(d));
        assertEquals(List.of(), JsonTextLocator.pathOf(doc.getRootValue()));
    }

    @Test
    void malformedTextDoesNotLoop() {
        assertNotNull(JsonTextLocator.scan("[1, }, {\"a\": "));
        assertNull(JsonTextLocator.scan("   "));
    }
}
