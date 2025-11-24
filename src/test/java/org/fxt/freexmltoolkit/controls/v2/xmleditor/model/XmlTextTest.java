package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlText class.
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlTextTest {

    private XmlText textNode;

    @BeforeEach
    void setUp() {
        textNode = new XmlText("Hello World");
    }

    @Test
    void testConstructor() {
        XmlText text = new XmlText("test");
        assertEquals("test", text.getText());
    }

    @Test
    void testConstructorNull() {
        XmlText text = new XmlText(null);
        assertEquals("", text.getText());
    }

    @Test
    void testSetText() {
        textNode.setText("New text");
        assertEquals("New text", textNode.getText());
    }

    @Test
    void testSetTextNull() {
        textNode.setText(null);
        assertEquals("", textNode.getText());
    }

    @Test
    void testIsWhitespace() {
        XmlText text = new XmlText("   ");
        assertTrue(text.isWhitespace());

        text.setText("Hello");
        assertFalse(text.isWhitespace());

        text.setText(" \n\t ");
        assertTrue(text.isWhitespace());
    }

    @Test
    void testGetNodeType() {
        assertEquals(XmlNodeType.TEXT, textNode.getNodeType());
    }

    @Test
    void testSerialize() {
        String xml = textNode.serialize(0);
        assertEquals("Hello World", xml);
    }

    @Test
    void testSerializeXmlEscaping() {
        textNode.setText("<>&");
        String xml = textNode.serialize(0);
        assertEquals("&lt;&gt;&amp;", xml);
    }

    @Test
    void testDeepCopy() {
        XmlText copy = (XmlText) textNode.deepCopy(null);

        assertEquals("Hello World", copy.getText());
        assertNotEquals(textNode.getId(), copy.getId());
    }

    @Test
    void testVisitor() {
        final boolean[] visited = {false};

        XmlNodeVisitor visitor = new XmlNodeVisitor() {
            @Override
            public void visit(XmlText text) {
                visited[0] = true;
            }
        };

        textNode.accept(visitor);

        assertTrue(visited[0]);
    }

    @Test
    void testToString() {
        String str = textNode.toString();

        assertTrue(str.contains("XmlText"));
        assertTrue(str.contains("Hello World"));
    }

    @Test
    void testToStringLongText() {
        XmlText longText = new XmlText("This is a very long text that should be truncated in toString");
        String str = longText.toString();

        assertTrue(str.contains("..."));
        assertTrue(str.length() < 50);
    }
}
