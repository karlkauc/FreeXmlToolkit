package org.fxt.freexmltoolkit.service.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests {@link JsonTokenizer} and {@link JsonDialect}. */
class JsonDialectTest {

    @Test
    void tokensConcatenateToTheInput() {
        String text = "{ // c\n 'a': [1, .5, 0x1F, +2,], /* b\n */ b: \"x\\\"y\", c: true } #x\n@";
        StringBuilder joined = new StringBuilder();
        for (Token t : JsonTokenizer.tokenize(text)) {
            joined.append(t.text());
        }
        assertEquals(text, joined.toString());
    }

    @Test
    void slashesInsideStringsAreNoComment() {
        List<Token> tokens = JsonTokenizer.tokenize("{\"url\": \"http://example.org/*x*/\"}");
        assertTrue(tokens.stream().noneMatch(t -> t.kind().isComment()));
    }

    @Test
    void rawLineBreaksStayInsideTheString() {
        String text = "{\"text\": \"two\nlines: x\"}";
        assertEquals(JsonDialect.JSON, JsonDialect.detect(text));
        Token string = JsonTokenizer.tokenize(text).stream().filter(t -> t.kind() == Kind.STRING).skip(1)
                .findFirst().orElseThrow();
        assertFalse(string.unterminated());
    }

    @Test
    void plainJsonWithUrlsAndColonsIsJson() {
        String schema = """
                {"$schema": "http://json-schema.org/draft-07/schema#",
                 "description": "time: 10:30, see https://x.org // not a comment, it's fine"}
                """;
        assertEquals(JsonDialect.JSON, JsonDialect.detect(schema));
        assertSame(schema, JsonDialect.toStrictJson(schema));
    }

    @Test
    void commentsAreJsoncTrailingCommasJson5() {
        assertEquals(JsonDialect.JSONC, JsonDialect.detect("{\"a\": 1 // c\n}"));
        assertEquals(JsonDialect.JSON5, JsonDialect.detect("{\"a\": [1, 2,],}"));
    }

    @Test
    void json5FeaturesAreJson5() {
        assertEquals(JsonDialect.JSON5, JsonDialect.detect("{a: 1}"));
        assertEquals(JsonDialect.JSON5, JsonDialect.detect("{\"a\": 'x'}"));
        assertEquals(JsonDialect.JSON5, JsonDialect.detect("{\"a\": 0xFF}"));
        assertEquals(JsonDialect.JSON5, JsonDialect.detect("{\"a\": .5}"));
        assertEquals(JsonDialect.JSON5, JsonDialect.detect("{\"a\": Infinity}"));
    }

    @Test
    void json5BecomesStrictJsonWithTheSameLines() {
        String json5 = """
                {
                  // comment with "quotes" and 'apostrophes'
                  name: 'it\\'s "quoted"',
                  /* block
                     comment */
                  hex: 0x1F, half: .5, plus: +3, five: 5.,
                  text: "don't break",
                  list: [1, 2,],
                }
                """;
        String strict = JsonDialect.toStrictJson(json5);
        assertEquals(json5.lines().count(), strict.lines().count(), strict);
        JsonObject o = JsonParser.parseString(strict).getAsJsonObject();
        assertEquals("it's \"quoted\"", o.get("name").getAsString());
        assertEquals(31, o.get("hex").getAsInt());
        assertEquals(0.5, o.get("half").getAsDouble());
        assertEquals(3, o.get("plus").getAsInt());
        assertEquals(5, o.get("five").getAsInt());
        assertEquals("don't break", o.get("text").getAsString());
        assertEquals(2, o.getAsJsonArray("list").size());
        assertTrue(strict.lines().toList().get(2).startsWith("  \"name\""), strict);
    }

    @Test
    void lineContinuationIsJoined() {
        JsonObject o = JsonParser.parseString(JsonDialect.toStrictJson("{a: 'one \\\ntwo'}")).getAsJsonObject();
        assertEquals("one two", o.get("a").getAsString());
    }

    @Test
    void numbersConvert() {
        assertEquals("-255", JsonDialect.toJsonNumber("-0xFF"));
        assertEquals("0.5e3", JsonDialect.toJsonNumber(".5e3"));
        assertEquals("7", JsonDialect.toJsonNumber("007"));
        assertEquals("-Infinity", JsonDialect.toJsonNumber("-Infinity"));
    }
}
