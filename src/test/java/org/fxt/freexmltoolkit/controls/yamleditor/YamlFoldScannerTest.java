package org.fxt.freexmltoolkit.controls.yamleditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link YamlFoldScanner}. */
@DisplayName("YamlFoldScanner")
class YamlFoldScannerTest {

    @Test
    @DisplayName("mappings and sequences fold by indentation; comments and blank lines do not end them")
    void indentation() {
        String text = """
                spec:
                  replicas: 3

                  # containers
                  containers:
                  - name: app
                    image: nginx
                  - name: db
                    image: pg
                other: 1
                """;
        Map<Integer, FoldingRegion> regions = YamlFoldScanner.scan(text, 1);
        assertEquals(8, regions.get(0).endLine(), regions.toString());       // spec: … up to "image: pg"
        assertEquals(8, regions.get(4).endLine());                           // containers: + its items at the same indent
        assertEquals(6, regions.get(5).endLine());                           // first item
        assertEquals(8, regions.get(7).endLine());                           // second item
        assertTrue(regions.get(0).elementName().startsWith("spec:"));
        assertFalse(regions.containsKey(9));
    }

    @Test
    @DisplayName("documents fold from their marker")
    void documents() {
        Map<Integer, FoldingRegion> regions = YamlFoldScanner.scan("---\na: 1\nb: 2\n---\nc: 3\nd: 4\n", 1);
        assertEquals(2, regions.get(0).endLine());
        assertEquals(5, regions.get(3).endLine());
    }

    @Test
    @DisplayName("block scalars fold with their key")
    void blockScalar() {
        Map<Integer, FoldingRegion> regions = YamlFoldScanner.scan("text: |\n  one\n  two\nnext: 1\n", 1);
        assertEquals(2, regions.get(0).endLine());
    }
}
