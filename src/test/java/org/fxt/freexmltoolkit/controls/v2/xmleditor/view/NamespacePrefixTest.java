package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Which part of a node name the grid draws in the namespace-prefix colour. */
class NamespacePrefixTest {

    @Test
    void prefixIncludesTheColon() {
        assertEquals("xsi:", GridCanvasView.namespacePrefix("xsi:type"));
        assertEquals("xmlns:", GridCanvasView.namespacePrefix("xmlns:ipo"));
        assertEquals("ipo:", GridCanvasView.namespacePrefix("ipo:street ▲"));
    }

    @Test
    void namesWithoutAPrefixHaveNone() {
        assertNull(GridCanvasView.namespacePrefix("street"));
        assertNull(GridCanvasView.namespacePrefix(":odd"));
        assertNull(GridCanvasView.namespacePrefix("odd:"));
        assertNull(GridCanvasView.namespacePrefix(null));
    }
}
