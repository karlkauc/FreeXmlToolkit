package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingRegion;
import org.junit.jupiter.api.Test;

/** Tests {@link JsonFoldScanner}. */
class JsonFoldScannerTest {

    private static final String TEXT = """
            {
              "name": "x { not a bracket [",
              "nested": {
                "a": 1,
                "b": 2
              },
              "list": [1, 2],
              /* a
                 long
                 comment */
              "items": [
                {"c": 1},
                {"c": 2}
              ]
            }
            """;

    @Test
    void containersSpanningEnoughLinesFold() {
        Map<Integer, FoldingRegion> regions = JsonFoldScanner.scan(TEXT, 2);
        assertEquals(new FoldingRegion(0, 14, "{…}", 0), regions.get(0));
        assertEquals(new FoldingRegion(2, 5, "nested {…}", 1), regions.get(2));
        assertEquals(new FoldingRegion(7, 9, "/*…*/", 1), regions.get(7));
        assertEquals(new FoldingRegion(10, 13, "items […]", 1), regions.get(10));
        assertFalse(regions.containsKey(6), "a one-line array does not fold");
        assertFalse(regions.containsKey(1), "brackets inside strings do not count");
        assertEquals(4, regions.size());
    }

    @Test
    void malformedTextIsTolerated() {
        assertTrue(JsonFoldScanner.scan("{\n\n\n", 2).isEmpty());
        assertTrue(JsonFoldScanner.scan("]\n\n\n}", 2).isEmpty());
        assertTrue(JsonFoldScanner.scan("", 2).isEmpty());
    }
}
