package org.fxt.freexmltoolkit.service.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.service.jsonschema.JsonCaretContext.Kind2;
import org.junit.jupiter.api.Test;

/** Tests {@link JsonCaretContext}. */
class JsonCaretContextTest {

    /** Analyzes the text at the position of the "|" marker (removed). */
    private static JsonCaretContext.Result at(String marked) {
        int offset = marked.indexOf('|');
        return JsonCaretContext.analyze(marked.replace("|", ""), offset);
    }

    @Test
    void keyPositionInAnIncompleteDocument() {
        var r = at("{\"a\": 1, \"b\": {\"x\": true, |");
        assertEquals(Kind2.KEY, r.kind());
        assertEquals(List.of("b"), r.path());
        assertEquals(Set.of("x"), r.existingKeys());
        assertEquals("", r.partial());
    }

    @Test
    void partialQuotedKeyIsReplacedWhole() {
        String text = "{\"a\": 1, \"st|\": 2}";
        var r = at(text);
        assertEquals(Kind2.KEY, r.kind());
        assertEquals("st", r.partial());
        assertTrue(r.quoted());
        String plain = text.replace("|", "");
        assertEquals("\"st\"", plain.substring(r.replaceStart(), r.replaceEnd()));
        assertEquals(Set.of("a"), r.existingKeys());
    }

    @Test
    void openStringStopsAtTheLineEnd() {
        String text = "{\n  \"na|\n  \"b\": 1\n}";
        var r = at(text);
        assertEquals(Kind2.KEY, r.kind());
        assertEquals("\"na", text.replace("|", "").substring(r.replaceStart(), r.replaceEnd()));
        assertTrue(r.existingKeys().contains("b"), "keys after the caret count too");
    }

    @Test
    void valuePositionsWithPaths() {
        var afterColon = at("{\"items\": [{\"qty\": |}]}");
        assertEquals(Kind2.VALUE, afterColon.kind());
        assertEquals(List.of("items", 0, "qty"), afterColon.path());

        var inArray = at("{\"tags\": [\"a\", \"b\", \"g|\"]}");
        assertEquals(Kind2.VALUE, inArray.kind());
        assertEquals(List.of("tags", 2), inArray.path());
        assertEquals("g", inArray.partial());

        var root = at("|");
        assertEquals(Kind2.VALUE, root.kind());
        assertEquals(List.of(), root.path());
    }

    @Test
    void json5KeysAndCommentsAreTolerated() {
        var r = at("{\n  // a comment with \"quotes\" {\n  name: 'x',\n  |\n}");
        assertEquals(Kind2.KEY, r.kind());
        assertEquals(Set.of("name"), r.existingKeys());
        assertEquals(Kind2.NONE, at("{\"a\": 1 // comm|ent\n}").kind());
        assertEquals(Kind2.NONE, at("{\"a\"| : 1}").kind());
        assertEquals(Kind2.NONE, at("{\"a\": 1 |}").kind());
    }

    @Test
    void pathAtKeysAndValues() {
        String text = "{\"a\": {\"b\": [10, 20]}, \"c\": \"x\"}";
        assertEquals(List.of("a", "b"), JsonCaretContext.pathAt(text, text.indexOf("\"b\"") + 1));
        assertEquals(List.of("a", "b", 1), JsonCaretContext.pathAt(text, text.indexOf("20")));
        assertEquals(List.of("c"), JsonCaretContext.pathAt(text, text.indexOf("\"x\"")));
        assertNull(JsonCaretContext.pathAt(text, text.indexOf('{')));
    }
}
