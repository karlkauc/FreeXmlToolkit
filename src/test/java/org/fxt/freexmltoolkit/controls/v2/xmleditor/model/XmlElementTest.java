package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlElement class.
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlElementTest {

    private XmlElement element;

    @BeforeEach
    void setUp() {
        element = new XmlElement("test");
    }

    @Test
    void testConstructorWithName() {
        XmlElement elem = new XmlElement("book");
        assertEquals("book", elem.getName());
        assertNull(elem.getNamespacePrefix());
        assertNull(elem.getNamespaceURI());
    }

    @Test
    void testConstructorWithNamespace() {
        XmlElement elem = new XmlElement("element", "xsd", "http://www.w3.org/2001/XMLSchema");
        assertEquals("element", elem.getName());
        assertEquals("xsd", elem.getNamespacePrefix());
        assertEquals("http://www.w3.org/2001/XMLSchema", elem.getNamespaceURI());
    }

    @Test
    void testSetName() {
        element.setName("newName");
        assertEquals("newName", element.getName());
    }

    @Test
    void testGetQualifiedName() {
        element.setName("element");
        assertEquals("element", element.getQualifiedName());

        element.setNamespacePrefix("xsd");
        assertEquals("xsd:element", element.getQualifiedName());
    }

    @Test
    void testSetNamespacePrefix() {
        element.setNamespacePrefix("xsd");
        assertEquals("xsd", element.getNamespacePrefix());
    }

    @Test
    void testSetNamespaceURI() {
        element.setNamespaceURI("http://www.w3.org/2001/XMLSchema");
        assertEquals("http://www.w3.org/2001/XMLSchema", element.getNamespaceURI());
    }

    // ==================== Attributes Tests ====================

    @Test
    void testSetAttribute() {
        element.setAttribute("id", "123");
        assertEquals("123", element.getAttribute("id"));
        assertEquals(1, element.getAttributeCount());
    }

    @Test
    void testReplaceAttribute() {
        element.setAttribute("id", "123");
        element.setAttribute("id", "456");

        assertEquals("456", element.getAttribute("id"));
        assertEquals(1, element.getAttributeCount());
    }

    @Test
    void testGetAttributeNotFound() {
        assertNull(element.getAttribute("notFound"));
    }

    @Test
    void testRemoveAttribute() {
        element.setAttribute("id", "123");
        String oldValue = element.removeAttribute("id");

        assertEquals("123", oldValue);
        assertNull(element.getAttribute("id"));
        assertEquals(0, element.getAttributeCount());
    }

    @Test
    void testHasAttribute() {
        assertFalse(element.hasAttribute("id"));

        element.setAttribute("id", "123");
        assertTrue(element.hasAttribute("id"));
    }

    @Test
    void testClearAttributes() {
        element.setAttribute("id", "123");
        element.setAttribute("name", "test");

        element.clearAttributes();

        assertEquals(0, element.getAttributeCount());
        assertFalse(element.hasAttribute("id"));
        assertFalse(element.hasAttribute("name"));
    }

    @Test
    void testGetAttributes() {
        element.setAttribute("id", "123");
        element.setAttribute("name", "test");

        Map<String, String> attrs = element.getAttributes();

        assertEquals(2, attrs.size());
        assertEquals("123", attrs.get("id"));
        assertEquals("test", attrs.get("name"));
    }

    @Test
    void testAttributesUnmodifiable() {
        Map<String, String> attrs = element.getAttributes();

        assertThrows(UnsupportedOperationException.class, () -> {
            attrs.put("test", "value");
        });
    }

    // ==================== Children Tests ====================

    @Test
    void testAddChild() {
        XmlElement child = new XmlElement("child");
        element.addChild(child);

        assertEquals(1, element.getChildCount());
        assertEquals(child, element.getChildren().get(0));
        assertEquals(element, child.getParent());
    }

    @Test
    void testAddChildAtIndex() {
        XmlElement child1 = new XmlElement("child1");
        XmlElement child2 = new XmlElement("child2");

        element.addChild(child1);
        element.addChild(0, child2);

        assertEquals(2, element.getChildCount());
        assertEquals(child2, element.getChildren().get(0));
        assertEquals(child1, element.getChildren().get(1));
    }

    @Test
    void testRemoveChild() {
        XmlElement child = new XmlElement("child");
        element.addChild(child);

        boolean removed = element.removeChild(child);

        assertTrue(removed);
        assertEquals(0, element.getChildCount());
        assertNull(child.getParent());
    }

    @Test
    void testRemoveChildAtIndex() {
        XmlElement child1 = new XmlElement("child1");
        XmlElement child2 = new XmlElement("child2");

        element.addChild(child1);
        element.addChild(child2);

        XmlNode removed = element.removeChild(0);

        assertEquals(child1, removed);
        assertEquals(1, element.getChildCount());
        assertEquals(child2, element.getChildren().get(0));
    }

    @Test
    void testClearChildren() {
        element.addChild(new XmlElement("child1"));
        element.addChild(new XmlElement("child2"));

        element.clearChildren();

        assertEquals(0, element.getChildCount());
    }

    @Test
    void testIndexOf() {
        XmlElement child1 = new XmlElement("child1");
        XmlElement child2 = new XmlElement("child2");

        element.addChild(child1);
        element.addChild(child2);

        assertEquals(0, element.indexOf(child1));
        assertEquals(1, element.indexOf(child2));
    }

    @Test
    void testGetChild() {
        XmlElement child = new XmlElement("child");
        element.addChild(child);

        assertEquals(child, element.getChild(0));
    }

    @Test
    void testGetChildrenUnmodifiable() {
        List<XmlNode> children = element.getChildren();

        assertThrows(UnsupportedOperationException.class, () -> {
            children.add(new XmlElement("test"));
        });
    }

    // ==================== Text Content Tests ====================

    @Test
    void testGetTextContent() {
        element.addChild(new XmlText("Hello "));
        element.addChild(new XmlText("World"));

        assertEquals("Hello World", element.getTextContent());
    }

    @Test
    void testGetTextContentWithCData() {
        element.addChild(new XmlText("Hello "));
        element.addChild(new XmlCData("CDATA"));

        assertEquals("Hello CDATA", element.getTextContent());
    }

    @Test
    void testSetTextContent() {
        element.addChild(new XmlElement("child"));
        element.setTextContent("New text");

        assertEquals(1, element.getChildCount());
        assertTrue(element.getChildren().get(0) instanceof XmlText);
        assertEquals("New text", element.getTextContent());
    }

    @Test
    void testSetTextContentNull() {
        element.addChild(new XmlElement("child"));
        element.setTextContent(null);

        assertEquals(0, element.getChildCount());
    }

    @Test
    void testSetTextContentEmpty() {
        element.addChild(new XmlElement("child"));
        element.setTextContent("");

        assertEquals(0, element.getChildCount());
    }

    @Test
    void testHasTextContent() {
        assertFalse(element.hasTextContent());

        element.addChild(new XmlText("text"));
        assertTrue(element.hasTextContent());

        element.clearChildren();
        element.addChild(new XmlCData("cdata"));
        assertTrue(element.hasTextContent());
    }

    // ==================== Child Elements Tests ====================

    @Test
    void testGetChildElements() {
        element.addChild(new XmlElement("child1"));
        element.addChild(new XmlText("text"));
        element.addChild(new XmlElement("child2"));

        List<XmlElement> childElements = element.getChildElements();

        assertEquals(2, childElements.size());
        assertEquals("child1", childElements.get(0).getName());
        assertEquals("child2", childElements.get(1).getName());
    }

    @Test
    void testGetChildElementsByName() {
        element.addChild(new XmlElement("item"));
        element.addChild(new XmlElement("other"));
        element.addChild(new XmlElement("item"));

        List<XmlElement> items = element.getChildElements("item");

        assertEquals(2, items.size());
        assertEquals("item", items.get(0).getName());
        assertEquals("item", items.get(1).getName());
    }

    @Test
    void testGetChildElement() {
        element.addChild(new XmlElement("item"));
        element.addChild(new XmlElement("other"));

        XmlElement found = element.getChildElement("other");

        assertNotNull(found);
        assertEquals("other", found.getName());
    }

    @Test
    void testGetChildElementNotFound() {
        XmlElement found = element.getChildElement("notFound");
        assertNull(found);
    }

    // ==================== Serialization Tests ====================

    @Test
    void testSerializeEmpty() {
        String xml = element.serialize(0);
        assertEquals("<test/>", xml);
    }

    @Test
    void testSerializeWithAttributes() {
        element.setAttribute("id", "123");
        element.setAttribute("name", "test");

        String xml = element.serialize(0);

        assertTrue(xml.contains("id=\"123\""));
        assertTrue(xml.contains("name=\"test\""));
        assertTrue(xml.startsWith("<test"));
        assertTrue(xml.endsWith("/>"));
    }

    @Test
    void testSerializeWithTextContent() {
        element.addChild(new XmlText("Hello"));

        String xml = element.serialize(0);

        assertEquals("<test>Hello</test>", xml);
    }

    @Test
    void testSerializeWithChildElements() {
        XmlElement child = new XmlElement("child");
        element.addChild(child);

        String xml = element.serialize(0);

        assertTrue(xml.contains("<test>"));
        assertTrue(xml.contains("<child/>"));
        assertTrue(xml.contains("</test>"));
    }

    @Test
    void testSerializeWithNamespace() {
        XmlElement elem = new XmlElement("element", "xsd", "http://www.w3.org/2001/XMLSchema");
        String xml = elem.serialize(0);

        assertTrue(xml.contains("xsd:element"));
    }

    @Test
    void testSerializeXmlEscaping() {
        element.setAttribute("attr", "<>&\"'");
        element.addChild(new XmlText("<>&"));

        String xml = element.serialize(0);

        assertTrue(xml.contains("&lt;&gt;&amp;&quot;&apos;"));
        assertTrue(xml.contains("&lt;&gt;&amp;</test>"));
    }

    @Test
    void testSerializeIndentation() {
        XmlElement child = new XmlElement("child");
        element.addChild(child);

        String xml = element.serialize(1);

        assertTrue(xml.startsWith("  <test>"));
    }

    // ==================== Deep Copy Tests ====================

    @Test
    void testDeepCopy() {
        element.setName("original");
        element.setNamespacePrefix("ns");
        element.setNamespaceURI("http://example.com");
        element.setAttribute("id", "123");
        element.addChild(new XmlElement("child"));

        XmlElement copy = (XmlElement) element.deepCopy(null);

        assertEquals("original", copy.getName());
        assertEquals("ns", copy.getNamespacePrefix());
        assertEquals("http://example.com", copy.getNamespaceURI());
        assertEquals("123", copy.getAttribute("id"));
        assertEquals(1, copy.getChildCount());
        assertNotEquals(element.getId(), copy.getId());
        assertNotEquals(element.getChildren().get(0).getId(), copy.getChildren().get(0).getId());
    }

    @Test
    void testDeepCopyWithSuffix() {
        element.setName("original");
        XmlElement copy = (XmlElement) element.deepCopy("_copy");

        assertEquals("original_copy", copy.getName());
    }

    @Test
    void testDeepCopyNoParent() {
        XmlElement parent = new XmlElement("parent");
        parent.addChild(element);

        XmlElement copy = (XmlElement) element.deepCopy(null);

        assertNull(copy.getParent());
    }

    // ==================== Other Tests ====================

    @Test
    void testVisitor() {
        final boolean[] visited = {false};

        XmlNodeVisitor visitor = new XmlNodeVisitor() {
            @Override
            public void visit(XmlElement elem) {
                visited[0] = true;
            }
        };

        element.accept(visitor);

        assertTrue(visited[0]);
    }

    @Test
    void testToString() {
        element.setName("book");
        element.setAttribute("id", "123");
        element.addChild(new XmlElement("title"));

        String str = element.toString();

        assertTrue(str.contains("XmlElement"));
        assertTrue(str.contains("name=book"));
        assertTrue(str.contains("children=1"));
        assertTrue(str.contains("attributes=1"));
    }
}
