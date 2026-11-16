package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/** Tests {@link YamlDocument}: data, locations, paths and syntax errors. */
@DisplayName("YamlDocument")
class YamlDocumentTest {

    private static final String SAMPLE = """
            # a service
            name: web
            spec:
              replicas: 3
              containers:
                - name: app
                  image: "nginx:1.27"
                - name: sidecar
                  image: busybox
            """;

    @Test
    @DisplayName("reads the data like the conversions")
    void data() {
        YamlDocument document = YamlDocument.parse(SAMPLE);
        assertTrue(document.isValid());
        assertEquals(JsonParser.parseString("""
                {"name":"web","spec":{"replicas":3,"containers":[{"name":"app","image":"nginx:1.27"},
                {"name":"sidecar","image":"busybox"}]}}"""), document.json());
        assertEquals(1, document.documentCount());
    }

    @Test
    @DisplayName("locates keys and values; lineOf falls back to the nearest located parent")
    void locations() {
        YamlDocument document = YamlDocument.parse(SAMPLE);
        assertEquals(2, document.lineOf(List.of("name")));
        assertEquals(4, document.lineOf(List.of("spec", "replicas")));
        assertEquals(9, document.lineOf(List.of("spec", "containers", 1, "image")));
        // a numeric string index (as validators may report it) and a missing child
        assertEquals(8, document.lineOf(List.of("spec", "containers", "1", "missing")));
        YamlDocument.Location image = document.locationOf(List.of("spec", "containers", 0, "image"));
        assertEquals("image", SAMPLE.substring(image.keyStart(), image.keyEnd()));
        assertEquals("\"nginx:1.27\"", SAMPLE.substring(image.valueStart(), image.valueEnd()));
    }

    @Test
    @DisplayName("pathAt finds the innermost key or value")
    void pathAt() {
        YamlDocument document = YamlDocument.parse(SAMPLE);
        assertEquals(List.of("spec", "replicas"), document.pathAt(SAMPLE.indexOf("replicas") + 2));
        assertEquals(List.of("spec", "containers", 1, "image"), document.pathAt(SAMPLE.indexOf("busybox") + 1));
        assertEquals("$.spec.containers[1].image", YamlDocument.toJsonPath(List.of("spec", "containers", 1, "image")));
        assertEquals("$['a b']", YamlDocument.toJsonPath(List.of("a b")));
    }

    @Test
    @DisplayName("several documents: an array, paths start with the document's index")
    void documents() {
        String text = "a: 1\n---\nb: 2\n";
        YamlDocument document = YamlDocument.parse(text);
        assertEquals(2, document.documentCount());
        assertEquals(JsonParser.parseString("[{\"a\":1},{\"b\":2}]"), document.json());
        assertEquals(3, document.lineOf(List.of(1, "b")));
    }

    @Test
    @DisplayName("aliases are located at their anchor and not walked twice")
    void aliases() {
        String text = "base: &b\n  x: 1\ncopy: *b\n";
        YamlDocument document = YamlDocument.parse(text);
        assertTrue(document.isValid());
        assertEquals(2, document.lineOf(List.of("base", "x")));
        assertNotNull(document.locationOf(List.of("copy")));
        assertNull(document.locationOf(List.of("copy", "x")));
        assertEquals(3, document.lineOf(List.of("copy", "x")));
    }

    @Test
    @DisplayName("a syntax error has a message, line and column")
    void syntaxError() {
        YamlDocument document = YamlDocument.parse("a: 1\nb: [1, 2\nc: 3\n");
        assertFalse(document.isValid());
        assertNull(document.json());
        assertTrue(document.problem().line() >= 2, document.problem().toString());
        assertTrue(document.problem().column() > 0);
        assertFalse(document.problem().message().isBlank());
    }

    @Test
    @DisplayName("duplicate keys are an error at the key")
    void duplicateKey() {
        YamlDocument document = YamlDocument.parse("a: 1\nb: 2\na: 3\n");
        assertFalse(document.isValid());
        assertEquals(3, document.problem().line(), document.problem().toString());
    }

    @Test
    @DisplayName("an alias bomb is refused, not expanded")
    void aliasBomb() {
        StringBuilder text = new StringBuilder("a0: &a0 [x, x, x, x, x, x, x, x, x, x]\n");
        for (int i = 1; i < 12; i++) {
            text.append("a").append(i).append(": &a").append(i).append(" [");
            for (int j = 0; j < 10; j++) {
                text.append(j > 0 ? ", " : "").append("*a").append(i - 1);
            }
            text.append("]\n");
        }
        YamlDocument document = YamlDocument.parse(text.toString());
        assertFalse(document.isValid());
    }

    @Test
    @DisplayName("characters outside the BMP do not shift offsets")
    void supplementaryCharacters() {
        String text = "emoji: \"\uD83D\uDE00\"\nnext: 1\n";
        YamlDocument document = YamlDocument.parse(text);
        YamlDocument.Location next = document.locationOf(List.of("next"));
        assertEquals("next", text.substring(next.keyStart(), next.keyEnd()));
    }

    @Test
    @DisplayName("application tags (!Ref, !vault, !include) read as their plain value")
    void applicationTags() {
        YamlDocument document = YamlDocument.parse("Bucket: !Ref MyBucket\nZones: !GetAZs ''\nSub: !Sub\n  x: 1\n"
                + "List: !reference [a, b]\n");
        assertTrue(document.isValid(), String.valueOf(document.problem()));
        assertEquals(JsonParser.parseString("{\"Bucket\":\"MyBucket\",\"Zones\":\"\",\"Sub\":{\"x\":1},"
                + "\"List\":[\"a\",\"b\"]}"), document.json());
    }
}
