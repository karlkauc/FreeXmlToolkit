package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link YamlFormatter}. */
@DisplayName("YamlFormatter")
class YamlFormatterTest {

    @Test
    @DisplayName("re-indents block style, keeps strings that look like other types quoted")
    void format() throws Exception {
        assertEquals("a:\n  b: 1\n  c:\n  - x\n  - '007'\n", YamlFormatter.format("a: {b: 1, c: [x, '007']}\n", 2));
    }

    @Test
    @DisplayName("documents stay separate")
    void documents() throws Exception {
        assertEquals("---\na: 1\n---\nb: 2\n", YamlFormatter.format("a:    1\n---\nb:   2\n", 2));
    }

    @Test
    @DisplayName("refuses what the data does not keep")
    void refuses() {
        assertEquals("comments", YamlFormatter.lostByFormatting("a: 1 # c\n"));
        assertEquals("anchors and aliases", YamlFormatter.lostByFormatting("a: &x 1\nb: *x\n"));
        assertEquals("tags", YamlFormatter.lostByFormatting("a: !!str 1\n"));
        assertEquals("directives", YamlFormatter.lostByFormatting("%YAML 1.2\n---\na: 1\n"));
        assertNull(YamlFormatter.lostByFormatting("---\na: '#not a comment'\n"));
        assertThrows(YamlFormatter.NotFormattableException.class, () -> YamlFormatter.format("a: 1 # c\n", 2));
    }
}
