package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link JsonPointerLocator}: mapping RFC 6901 pointers / segment paths to
 * 1-based lines in the raw JSON text.
 */
@DisplayName("JsonPointerLocator")
class JsonPointerLocatorTest {

    private static final String PRETTY = """
            {
              "name": "test",
              "nested": {
                "deep": {
                  "value": 42
                }
              },
              "items": [
                { "price": 1.5 },
                { "price": "wrong" }
              ],
              "tail": true
            }
            """;

    @Test
    @DisplayName("Root pointer maps to the first line of the document")
    void rootPointer() {
        assertEquals(1, new JsonPointerLocator(PRETTY).lineOf(""));
        assertEquals(1, new JsonPointerLocator(PRETTY).lineOf(List.of()));
    }

    @Test
    @DisplayName("Nested object members map to their value's line")
    void nestedObjects() {
        var locator = new JsonPointerLocator(PRETTY);
        assertEquals(2, locator.lineOf("/name"));
        assertEquals(3, locator.lineOf("/nested"));
        assertEquals(4, locator.lineOf("/nested/deep"));
        assertEquals(5, locator.lineOf("/nested/deep/value"));
        assertEquals(12, locator.lineOf("/tail"));
    }

    @Test
    @DisplayName("Array elements map via integer or string index segments")
    void arrayIndices() {
        var locator = new JsonPointerLocator(PRETTY);
        assertEquals(9, locator.lineOf("/items/0"));
        assertEquals(10, locator.lineOf("/items/1"));
        assertEquals(10, locator.lineOf("/items/1/price"));
        assertEquals(10, locator.lineOf(List.of("items", 1, "price")));
    }

    @Test
    @DisplayName("Braces and brackets inside string values do not confuse the scan")
    void bracesInsideStrings() {
        String json = """
                {
                  "a": "looks { like [ json ] }",
                  "b": "and \\" escaped } too",
                  "c": 3
                }
                """;
        assertEquals(4, new JsonPointerLocator(json).lineOf("/c"));
    }

    @Test
    @DisplayName("Escaped pointer segments (~0 / ~1) resolve to their keys")
    void escapedPointerSegments() {
        String json = """
                {
                  "a/b": 1,
                  "a~b": 2
                }
                """;
        var locator = new JsonPointerLocator(json);
        assertEquals(2, locator.lineOf("/a~1b"));
        assertEquals(3, locator.lineOf("/a~0b"));
    }

    @Test
    @DisplayName("Same key at different depths resolves by path, not by name")
    void sameKeyDifferentDepths() {
        String json = """
                {
                  "value": 1,
                  "outer": {
                    "value": 2
                  }
                }
                """;
        var locator = new JsonPointerLocator(json);
        assertEquals(2, locator.lineOf("/value"));
        assertEquals(4, locator.lineOf("/outer/value"));
    }

    @Test
    @DisplayName("Missing paths return -1")
    void missingPaths() {
        var locator = new JsonPointerLocator(PRETTY);
        assertEquals(-1, locator.lineOf("/does-not-exist"));
        assertEquals(-1, locator.lineOf("/items/5"));
        assertEquals(-1, locator.lineOf("/name/deeper"));
        assertEquals(-1, locator.lineOf((String) null));
        assertEquals(-1, new JsonPointerLocator("").lineOf(""));
    }

    @Test
    @DisplayName("Tolerates comments and trailing commas (JSONC flavor)")
    void toleratesCommentsAndTrailingCommas() {
        String json = """
                {
                  // line comment
                  "a": 1,
                  /* block
                     comment */
                  "b": [1, 2,],
                }
                """;
        var locator = new JsonPointerLocator(json);
        assertEquals(3, locator.lineOf("/a"));
        assertEquals(6, locator.lineOf("/b"));
    }

    @Test
    @DisplayName("Single-line input maps everything to line 1")
    void singleLineInput() {
        var locator = new JsonPointerLocator("{\"a\":{\"b\":[10,20]}}");
        assertEquals(1, locator.lineOf("/a/b/1"));
    }

    @Test
    @DisplayName("Malformed input returns -1 instead of throwing")
    void malformedInput() {
        assertEquals(-1, new JsonPointerLocator("{\"a\": ").lineOf("/b"));
        assertEquals(-1, new JsonPointerLocator("not json at all").lineOf("/a"));
    }
}
