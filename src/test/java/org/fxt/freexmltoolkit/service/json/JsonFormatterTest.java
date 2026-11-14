package org.fxt.freexmltoolkit.service.json;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.service.JsonService;
import org.junit.jupiter.api.Test;

/** Tests {@link JsonFormatter}: formatting and sorting that keep comments and JSON5 spelling. */
class JsonFormatterTest {

    private final JsonService service = new JsonService();

    private void assertSameData(String a, String b) {
        assertEquals(service.parseAuto(a), service.parseAuto(b), "formatting must not change the data:\n" + b);
    }

    @Test
    void plainJsonLooksLikeGsonPrettyPrint() {
        String json = "{\"a\":1,\"b\":[1,2,{\"c\":null}],\"d\":{},\"e\":[]}";
        String expected = """
                {
                  "a": 1,
                  "b": [
                    1,
                    2,
                    {
                      "c": null
                    }
                  ],
                  "d": {},
                  "e": []
                }""";
        assertEquals(expected, JsonFormatter.format(json, 2));
        assertEquals(service.formatJson(json, 2), JsonFormatter.format(json, 2));
    }

    @Test
    void indentIsHonoured() {
        assertEquals("{\n    \"a\": [\n        1\n    ]\n}", JsonFormatter.format("{\"a\":[1]}", 4));
    }

    @Test
    void commentsStayWithTheirMembers() {
        String jsonc = """
                // file header
                {"a": 1, // after a
                  /* before b */ "b": 2,
                  "c": [1, 2 /* two */],
                  // dangling at the end
                }
                """;
        String formatted = JsonFormatter.format(jsonc, 2);
        assertEquals("""
                // file header
                {
                  "a": 1, // after a
                  /* before b */
                  "b": 2,
                  "c": [
                    1,
                    2 /* two */
                  ],
                  // dangling at the end
                }
                """, formatted);
        assertSameData(jsonc, formatted);
    }

    @Test
    void json5SpellingIsKept() {
        String json5 = "{unquoted: 'single', hex: 0x1F, half: .5, list: [1, 2,],}";
        String formatted = JsonFormatter.format(json5, 2);
        assertTrue(formatted.contains("unquoted: 'single'"), formatted);
        assertTrue(formatted.contains("hex: 0x1F"), formatted);
        assertTrue(formatted.contains("half: .5"), formatted);
        assertTrue(formatted.contains("    2,\n  ],"), "trailing commas are kept: " + formatted);
        assertSameData(json5, formatted);
    }

    @Test
    void formattingIsAFixedPoint() {
        String jsonc = "{\n// c\n\"a\": {\"x\": [1, {\"y\": 2}]}, \"b\": \"s\" /* z */\n}\n";
        String once = JsonFormatter.format(jsonc, 2);
        assertEquals(once, JsonFormatter.format(once, 2));
        assertTrue(once.endsWith("}\n"), "the final line break is kept");
    }

    @Test
    void malformedTextIsRefused() {
        var e = assertThrows(JsonFormatter.FormatException.class, () -> JsonFormatter.format("{\"a\": 1 \"b\": 2}", 2));
        assertEquals(8, e.offset());
        assertThrows(JsonFormatter.FormatException.class, () -> JsonFormatter.format("{\"a\": ", 2));
        assertThrows(JsonFormatter.FormatException.class, () -> JsonFormatter.format("", 2));
        assertThrows(JsonFormatter.FormatException.class, () -> JsonFormatter.format("{} {}", 2));
    }

    @Test
    void sortKeysTopLevelOnly() {
        String json = "{\"b\": {\"z\": 1, \"y\": 2}, \"A\": 1, \"a\": 2}";
        String sorted = JsonFormatter.sortKeys(json, false, 2);
        assertEquals("{\n  \"A\": 1,\n  \"a\": 2,\n  \"b\": {\n    \"z\": 1,\n    \"y\": 2\n  }\n}", sorted);
    }

    @Test
    void sortKeysRecursivelyCarriesComments() {
        String jsonc = """
                {
                  // about b
                  "b": [{"d": 1, "c": 2}],
                  "a": 1 // about a
                }
                """;
        String sorted = JsonFormatter.sortKeys(jsonc, true, 2);
        assertEquals("""
                {
                  "a": 1, // about a
                  // about b
                  "b": [
                    {
                      "c": 2,
                      "d": 1
                    }
                  ]
                }
                """, sorted);
        assertSameData(jsonc, sorted);
    }
}
