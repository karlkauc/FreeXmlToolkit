package org.fxt.freexmltoolkit.controls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XmlGraphicEditor context menu functionality.
 */
class XmlGraphicEditorTest {

    private Document document;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Create a simple test XML document
        String testXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <elementWithText>Some text content</elementWithText>
                    <elementWithoutText>
                        <child>Child content</child>
                    </elementWithoutText>
                    <emptyElement></emptyElement>
                </root>
                """;

        document = builder.parse(new ByteArrayInputStream(testXml.getBytes()));
    }

    @Test
    void testHasTextContent() {
        // Test element with text content
        Element elementWithText = (Element) document.getElementsByTagName("elementWithText").item(0);
        assertTrue(hasTextContent(elementWithText), "Element with text content should return true");

        // Test element without text content (has child elements)
        Element elementWithoutText = (Element) document.getElementsByTagName("elementWithoutText").item(0);
        assertFalse(hasTextContent(elementWithoutText), "Element without text content should return false");

        // Test empty element
        Element emptyElement = (Element) document.getElementsByTagName("emptyElement").item(0);
        assertFalse(hasTextContent(emptyElement), "Empty element should return false");
    }

    @Test
    void testCanHaveChildren() {
        // Test element with text content
        Element elementWithText = (Element) document.getElementsByTagName("elementWithText").item(0);
        assertFalse(canHaveChildren(elementWithText), "Element with text content cannot have children");

        // Test element without text content (has child elements)
        Element elementWithoutText = (Element) document.getElementsByTagName("elementWithoutText").item(0);
        assertTrue(canHaveChildren(elementWithoutText), "Element without text content can have children");

        // Test empty element
        Element emptyElement = (Element) document.getElementsByTagName("emptyElement").item(0);
        assertTrue(canHaveChildren(emptyElement), "Empty element can have children");
    }

    @Test
    void testCreateNewElementWithEmptyText() {
        Element parent = document.createElement("parent");
        String elementName = "newElement";

        // Create new element with empty text content
        Element newElement = document.createElement(elementName);
        newElement.appendChild(document.createTextNode(""));
        parent.appendChild(newElement);

        // Verify the element was created correctly
        assertEquals(elementName, newElement.getNodeName());
        assertEquals(1, newElement.getChildNodes().getLength());
        assertEquals(Node.TEXT_NODE, newElement.getFirstChild().getNodeType());
        assertEquals("", newElement.getTextContent());

        // Verify it has text content (for editing purposes)
        assertTrue(hasTextContent(newElement), "New element should have text content for editing");
    }

    @Test
    void testNewElementIsImmediatelyEditable() {
        Element parent = document.createElement("parent");
        String elementName = "editableElement";

        // Create new element with empty text content (like in the actual implementation)
        Element newElement = document.createElement(elementName);
        newElement.appendChild(document.createTextNode(""));
        parent.appendChild(newElement);

        // Verify the element is immediately editable
        assertTrue(hasTextContent(newElement), "New element should have text content for immediate editing");
        assertEquals("", newElement.getTextContent(), "New element should have empty text content");

        // Simulate editing the text content
        newElement.setTextContent("New text value");
        assertEquals("New text value", newElement.getTextContent(), "Element should be editable");
    }

    // Helper methods that mirror the private methods in XmlGraphicEditor
    // These are needed for testing since the original methods are private

    private boolean hasTextContent(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }

        return node.getChildNodes().getLength() == 1 &&
                node.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE;
    }

    private boolean canHaveChildren(Node node) {
        return !hasTextContent(node);
    }
}
