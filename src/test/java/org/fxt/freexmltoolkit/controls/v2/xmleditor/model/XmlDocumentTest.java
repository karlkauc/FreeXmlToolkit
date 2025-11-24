package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlDocument class.
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlDocumentTest {

    private XmlDocument document;

    @BeforeEach
    void setUp() {
        document = new XmlDocument();
    }

    @Test
    void testDefaultValues() {
        assertEquals("1.0", document.getVersion());
        assertEquals("UTF-8", document.getEncoding());
        assertNull(document.getStandalone());
        assertEquals(0, document.getChildCount());
    }

    @Test
    void testSetVersion() {
        document.setVersion("1.1");
        assertEquals("1.1", document.getVersion());
    }

    @Test
    void testSetEncoding() {
        document.setEncoding("ISO-8859-1");
        assertEquals("ISO-8859-1", document.getEncoding());
    }

    @Test
    void testSetStandalone() {
        document.setStandalone(true);
        assertEquals(true, document.getStandalone());

        document.setStandalone(false);
        assertEquals(false, document.getStandalone());

        document.setStandalone(null);
        assertNull(document.getStandalone());
    }

    @Test
    void testSetRootElement() {
        XmlElement root = new XmlElement("root");
        document.setRootElement(root);

        assertEquals(root, document.getRootElement());
        assertEquals(1, document.getChildCount());
        assertEquals(document, root.getParent());
    }

    @Test
    void testReplaceRootElement() {
        XmlElement root1 = new XmlElement("root1");
        XmlElement root2 = new XmlElement("root2");

        document.setRootElement(root1);
        assertEquals(root1, document.getRootElement());

        document.setRootElement(root2);
        assertEquals(root2, document.getRootElement());
        assertEquals(1, document.getChildCount());
        assertNull(root1.getParent());
    }

    @Test
    void testAddChild() {
        XmlComment comment = new XmlComment("Comment");
        document.addChild(comment);

        assertEquals(1, document.getChildCount());
        assertEquals(comment, document.getChildren().get(0));
        assertEquals(document, comment.getParent());
    }

    @Test
    void testAddChildAtIndex() {
        XmlComment comment1 = new XmlComment("Comment 1");
        XmlComment comment2 = new XmlComment("Comment 2");

        document.addChild(comment1);
        document.addChild(0, comment2);

        assertEquals(2, document.getChildCount());
        assertEquals(comment2, document.getChildren().get(0));
        assertEquals(comment1, document.getChildren().get(1));
    }

    @Test
    void testRemoveChild() {
        XmlComment comment = new XmlComment("Comment");
        document.addChild(comment);

        boolean removed = document.removeChild(comment);

        assertTrue(removed);
        assertEquals(0, document.getChildCount());
        assertNull(comment.getParent());
    }

    @Test
    void testRemoveChildAtIndex() {
        XmlComment comment1 = new XmlComment("Comment 1");
        XmlComment comment2 = new XmlComment("Comment 2");

        document.addChild(comment1);
        document.addChild(comment2);

        XmlNode removed = document.removeChild(0);

        assertEquals(comment1, removed);
        assertEquals(1, document.getChildCount());
        assertEquals(comment2, document.getChildren().get(0));
    }

    @Test
    void testClearChildren() {
        document.addChild(new XmlComment("Comment 1"));
        document.addChild(new XmlComment("Comment 2"));

        document.clearChildren();

        assertEquals(0, document.getChildCount());
    }

    @Test
    void testRootElementPosition() {
        XmlComment comment = new XmlComment("Comment");
        XmlElement root = new XmlElement("root");
        XmlProcessingInstruction pi = new XmlProcessingInstruction("target", "data");

        // Add comment first
        document.addChild(comment);
        // Then set root element - it should be inserted after comment
        document.setRootElement(root);
        // Add PI
        document.addChild(pi);

        assertEquals(3, document.getChildCount());
        assertEquals(comment, document.getChildren().get(0));
        assertEquals(root, document.getChildren().get(1));
        assertEquals(pi, document.getChildren().get(2));
    }

    @Test
    void testSerialize() {
        document.setVersion("1.0");
        document.setEncoding("UTF-8");
        document.setStandalone(true);

        XmlElement root = new XmlElement("root");
        document.setRootElement(root);

        String xml = document.serialize(0);

        assertTrue(xml.contains("<?xml version=\"1.0\""));
        assertTrue(xml.contains("encoding=\"UTF-8\""));
        assertTrue(xml.contains("standalone=\"yes\""));
        assertTrue(xml.contains("<root/>"));
    }

    @Test
    void testSerializeStandaloneNo() {
        document.setStandalone(false);
        XmlElement root = new XmlElement("root");
        document.setRootElement(root);

        String xml = document.serialize(0);

        assertTrue(xml.contains("standalone=\"no\""));
    }

    @Test
    void testSerializeNoStandalone() {
        document.setStandalone(null);
        XmlElement root = new XmlElement("root");
        document.setRootElement(root);

        String xml = document.serialize(0);

        assertFalse(xml.contains("standalone"));
    }

    @Test
    void testDeepCopy() {
        document.setVersion("1.1");
        document.setEncoding("ISO-8859-1");
        document.setStandalone(true);

        XmlElement root = new XmlElement("root");
        XmlComment comment = new XmlComment("Comment");
        document.addChild(comment);
        document.setRootElement(root);

        XmlDocument copy = (XmlDocument) document.deepCopy(null);

        assertEquals("1.1", copy.getVersion());
        assertEquals("ISO-8859-1", copy.getEncoding());
        assertEquals(true, copy.getStandalone());
        assertEquals(2, copy.getChildCount());
        assertNotEquals(document.getId(), copy.getId());
        assertNotEquals(root.getId(), copy.getRootElement().getId());
    }

    @Test
    void testVisitor() {
        final boolean[] visited = {false};

        XmlNodeVisitor visitor = new XmlNodeVisitor() {
            @Override
            public void visit(XmlDocument doc) {
                visited[0] = true;
            }
        };

        document.accept(visitor);

        assertTrue(visited[0]);
    }

    @Test
    void testToString() {
        XmlElement root = new XmlElement("myRoot");
        document.setRootElement(root);

        String str = document.toString();

        assertTrue(str.contains("XmlDocument"));
        assertTrue(str.contains("version=1.0"));
        assertTrue(str.contains("root=myRoot"));
    }
}
