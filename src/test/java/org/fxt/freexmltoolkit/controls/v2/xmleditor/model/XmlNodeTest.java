package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlNode base class functionality.
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlNodeTest {

    private XmlElement testNode;
    private List<PropertyChangeEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        testNode = new XmlElement("test");
        capturedEvents = new ArrayList<>();
    }

    @Test
    void testUniqueUUID() {
        XmlElement node1 = new XmlElement("test1");
        XmlElement node2 = new XmlElement("test2");

        assertNotNull(node1.getId());
        assertNotNull(node2.getId());
        assertNotEquals(node1.getId(), node2.getId());
    }

    @Test
    void testUUIDImmutable() {
        UUID originalId = testNode.getId();
        testNode.setName("changed");

        assertEquals(originalId, testNode.getId());
    }

    @Test
    void testPropertyChangeListenerAdded() {
        PropertyChangeListener listener = evt -> capturedEvents.add(evt);
        testNode.addPropertyChangeListener(listener);

        testNode.setName("newName");

        assertEquals(1, capturedEvents.size());
        assertEquals("name", capturedEvents.get(0).getPropertyName());
        assertEquals("test", capturedEvents.get(0).getOldValue());
        assertEquals("newName", capturedEvents.get(0).getNewValue());
    }

    @Test
    void testPropertyChangeListenerRemoved() {
        PropertyChangeListener listener = evt -> capturedEvents.add(evt);
        testNode.addPropertyChangeListener(listener);
        testNode.removePropertyChangeListener(listener);

        testNode.setName("newName");

        assertEquals(0, capturedEvents.size());
    }

    @Test
    void testSpecificPropertyChangeListener() {
        PropertyChangeListener listener = evt -> capturedEvents.add(evt);
        testNode.addPropertyChangeListener("name", listener);

        testNode.setName("newName");
        testNode.setAttribute("attr", "value");

        // Only name changes should be captured
        assertEquals(1, capturedEvents.size());
        assertEquals("name", capturedEvents.get(0).getPropertyName());
    }

    @Test
    void testParentChildRelationship() {
        XmlElement parent = new XmlElement("parent");
        XmlElement child = new XmlElement("child");

        parent.addChild(child);

        assertEquals(parent, child.getParent());
        assertTrue(parent.getChildren().contains(child));
    }

    @Test
    void testGetRoot() {
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        XmlElement child = new XmlElement("child");
        XmlElement grandchild = new XmlElement("grandchild");

        doc.setRootElement(root);
        root.addChild(child);
        child.addChild(grandchild);

        assertEquals(doc, grandchild.getRoot());
        assertEquals(doc, child.getRoot());
        assertEquals(doc, root.getRoot());
    }

    @Test
    void testIsRoot() {
        XmlDocument doc = new XmlDocument();
        XmlElement element = new XmlElement("element");

        assertTrue(doc.isRoot());
        assertTrue(element.isRoot());

        doc.setRootElement(element);

        assertTrue(doc.isRoot());
        assertFalse(element.isRoot());
    }

    @Test
    void testIsDescendantOf() {
        XmlElement parent = new XmlElement("parent");
        XmlElement child = new XmlElement("child");
        XmlElement grandchild = new XmlElement("grandchild");

        parent.addChild(child);
        child.addChild(grandchild);

        assertTrue(grandchild.isDescendantOf(parent));
        assertTrue(grandchild.isDescendantOf(child));
        assertTrue(child.isDescendantOf(parent));
        assertFalse(parent.isDescendantOf(child));
        assertFalse(parent.isDescendantOf(grandchild));
    }

    @Test
    void testNodeType() {
        assertEquals(XmlNodeType.ELEMENT, testNode.getNodeType());
        assertEquals(XmlNodeType.DOCUMENT, new XmlDocument().getNodeType());
        assertEquals(XmlNodeType.TEXT, new XmlText("text").getNodeType());
        assertEquals(XmlNodeType.COMMENT, new XmlComment("comment").getNodeType());
        assertEquals(XmlNodeType.CDATA, new XmlCData("cdata").getNodeType());
        assertEquals(XmlNodeType.PROCESSING_INSTRUCTION, new XmlProcessingInstruction("target", "data").getNodeType());
        assertEquals(XmlNodeType.ATTRIBUTE, new XmlAttribute("name", "value").getNodeType());
    }

    @Test
    void testDeepCopySuffix() {
        XmlElement original = new XmlElement("original");
        XmlElement copy = (XmlElement) original.deepCopy("_copy");

        assertEquals("original_copy", copy.getName());
        assertNotEquals(original.getId(), copy.getId());
    }

    @Test
    void testEqualsBasedOnUUID() {
        XmlElement element1 = new XmlElement("test");
        XmlElement element2 = new XmlElement("test");

        assertEquals(element1, element1);
        assertNotEquals(element1, element2);
    }

    @Test
    void testHashCodeBasedOnUUID() {
        XmlElement element = new XmlElement("test");

        assertEquals(element.getId().hashCode(), element.hashCode());
    }

    @Test
    void testToString() {
        String str = testNode.toString();

        assertTrue(str.contains("XmlElement"));
        assertTrue(str.contains("id="));
    }

    @Test
    void testVisitorPattern() {
        List<XmlNode> visitedNodes = new ArrayList<>();

        XmlNodeVisitor visitor = new XmlNodeVisitor() {
            @Override
            public void visit(XmlElement element) {
                visitedNodes.add(element);
            }
        };

        testNode.accept(visitor);

        assertEquals(1, visitedNodes.size());
        assertEquals(testNode, visitedNodes.get(0));
    }
}
