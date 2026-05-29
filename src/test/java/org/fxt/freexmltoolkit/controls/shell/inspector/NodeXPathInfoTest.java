package org.fxt.freexmltoolkit.controls.shell.inspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NodeXPathInfo}, the UI-free derivation of the inspector's
 * "Node &amp; XPath" data from the editor caret position. Wraps the existing
 * XPath calculator so the inspector stays presentation-only.
 */
class NodeXPathInfoTest {

    @Test
    void derivesElementNameAndXPathFromCaretInsideAnElement() {
        String xml = "<root><item id=\"1\">value</item></root>";
        int caret = xml.indexOf("value");

        NodeXPathInfo info = NodeXPathInfo.fromCaret(xml, caret);

        assertEquals("Element", info.kind());
        assertEquals("item", info.name());
        assertEquals("/root/item", info.xpath());
        assertTrue(info.depth() >= 1, "an element inside root must have depth >= 1");
    }

    @Test
    void nestedElementsProduceFullXPath() {
        String xml = "<root><parent><child>x</child></parent></root>";
        int caret = xml.indexOf("x");

        NodeXPathInfo info = NodeXPathInfo.fromCaret(xml, caret);

        assertEquals("child", info.name());
        assertEquals("/root/parent/child", info.xpath());
    }

    @Test
    void emptyDocumentYieldsDocumentKind() {
        NodeXPathInfo info = NodeXPathInfo.fromCaret("", 0);

        assertEquals("Document", info.kind());
        assertEquals("", info.name());
        assertEquals("/", info.xpath());
    }

    @Test
    void nullTextIsHandledGracefully() {
        NodeXPathInfo info = NodeXPathInfo.fromCaret(null, 0);
        assertEquals("Document", info.kind());
        assertNotNull(info.xpath());
    }
}
