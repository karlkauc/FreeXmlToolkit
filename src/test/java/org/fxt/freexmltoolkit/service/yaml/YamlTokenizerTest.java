package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.service.yaml.YamlTokenizer.Kind;
import org.fxt.freexmltoolkit.service.yaml.YamlTokenizer.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link YamlTokenizer}. */
@DisplayName("YamlTokenizer")
class YamlTokenizerTest {

    /** "kind:text" of every styled token. */
    private static List<String> styled(String text) {
        List<String> out = new ArrayList<>();
        for (Token token : YamlTokenizer.tokenize(text)) {
            if (token.kind() != Kind.TEXT) {
                out.add(token.kind() + ":" + text.substring(token.start(), token.end()));
            }
        }
        return out;
    }

    @Test
    @DisplayName("the tokens cover the whole text without gaps")
    void covers() {
        String text = "%YAML 1.2\n---\na: [1, {b: c}]  # x\n'q': |\n  line\n  two\nz: ~\n...\n";
        int last = 0;
        for (Token token : YamlTokenizer.tokenize(text)) {
            assertEquals(last, token.start(), token.toString());
            assertTrue(token.end() > token.start());
            last = token.end();
        }
        assertEquals(text.length(), last);
    }

    @Test
    @DisplayName("keys, scalars by core-schema type, comments")
    void block() {
        assertEquals(List.of("KEY:name", "PUNCTUATION::", "STRING:web server", "COMMENT:# the name"),
                styled("name: web server # the name"));
        assertEquals(List.of("KEY:n", "PUNCTUATION::", "NUMBER:0x1F"), styled("n: 0x1F"));
        assertEquals(List.of("KEY:ok", "PUNCTUATION::", "BOOLEAN:True"), styled("ok: True"));
        assertEquals(List.of("KEY:v", "PUNCTUATION::", "STRING:yes"), styled("v: yes"));
        assertEquals(List.of("KEY:x", "PUNCTUATION::", "NULL:~"), styled("x: ~"));
        assertEquals(List.of("KEY:url", "PUNCTUATION::", "STRING:http://a:b/c#d"), styled("url: http://a:b/c#d"));
    }

    @Test
    @DisplayName("sequences, quoted keys, anchors, aliases, tags")
    void structure() {
        assertEquals(List.of("PUNCTUATION:-", "KEY:\"a b\"", "PUNCTUATION::", "ANCHOR:&x", "STRING:1.2.3"),
                styled("- \"a b\": &x 1.2.3"));
        assertEquals(List.of("KEY:ref", "PUNCTUATION::", "ALIAS:*x"), styled("ref: *x"));
        assertEquals(List.of("KEY:t", "PUNCTUATION::", "TAG:!!str", "NUMBER:12"), styled("t: !!str 12"));
    }

    @Test
    @DisplayName("flow collections and block scalars")
    void flowAndBlockScalars() {
        assertEquals(List.of("BRACKET:{", "KEY:a", "PUNCTUATION::", "NUMBER:1", "PUNCTUATION:,", "KEY:b",
                "PUNCTUATION::", "BRACKET:[", "STRING:x", "PUNCTUATION:,", "BOOLEAN:true", "BRACKET:]", "BRACKET:}"),
                styled("{a: 1, b: [x, true]}"));
        assertEquals(List.of("KEY:text", "PUNCTUATION::", "PUNCTUATION:|-", "STRING:a: not a key",
                "STRING:# no comment", "KEY:next", "PUNCTUATION::", "NUMBER:1"),
                styled("text: |-\n  a: not a key\n\n  # no comment\nnext: 1\n"));
    }

    @Test
    @DisplayName("directives and document markers")
    void markers() {
        assertEquals(List.of("DIRECTIVE:%YAML 1.2", "DIRECTIVE:---", "KEY:a", "PUNCTUATION::", "NUMBER:1",
                "DIRECTIVE:..."), styled("%YAML 1.2\n---\na: 1\n...\n"));
    }

    @Test
    @DisplayName("broken text never fails")
    void tolerant() {
        for (String text : List.of("\"unterminated", "a: [1, 2", "- - -", ": :", "!", "&", "|", "key: 'it''s"
                + "\n", "\r\n\r\n", "a:\tb")) {
            int last = 0;
            for (Token token : YamlTokenizer.tokenize(text)) {
                assertEquals(last, token.start(), text);
                last = token.end();
            }
            assertEquals(text.length(), last, text);
        }
    }
}
