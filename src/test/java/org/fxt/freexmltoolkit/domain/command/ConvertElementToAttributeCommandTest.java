package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConvertElementToAttributeCommand.
 * Tests converting simple XSD elements to attributes with validation.
 */
class ConvertElementToAttributeCommandTest {

    private Document xsdDocument;
    private XsdDomManipulator domManipulator;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        xsdDocument = builder.newDocument();

        domManipulator = mock(XsdDomManipulator.class);
    }

    @Test
    @DisplayName("Should convert required element to attribute with use=required")
    void testConvertRequiredElement() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        assertTrue(command.execute());

        // Verify element removed from sequence
        NodeList elements = sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(0, elements.getLength());

        // Verify attribute created in complexType
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(1, attributes.getLength());

        Element newAttribute = (Element) attributes.item(0);
        assertEquals("id", newAttribute.getAttribute("name"));
        assertEquals("xs:string", newAttribute.getAttribute("type"));
        assertEquals("required", newAttribute.getAttribute("use"));
    }

    @Test
    @DisplayName("Should convert optional element to attribute with use=optional")
    void testConvertOptionalElement() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("middleName", "xs:string", "0", "1");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        assertTrue(command.execute());

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        assertEquals("middleName", newAttribute.getAttribute("name"));
        // optional is default, so attribute might not be present
        String use = newAttribute.getAttribute("use");
        assertTrue(use.isEmpty() || "optional".equals(use));
    }

    @Test
    @DisplayName("Should preserve element type")
    void testPreserveElementType() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("age", "xs:integer", "0", "1");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        assertEquals("xs:integer", newAttribute.getAttribute("type"));
    }

    @Test
    @DisplayName("Should default to xs:string if no type specified")
    void testDefaultTypeToString() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", "status");
        element.setAttribute("minOccurs", "0");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        assertEquals("xs:string", newAttribute.getAttribute("type"));
    }

    @Test
    @DisplayName("Should preserve default value")
    void testPreserveDefaultValue() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("country", "xs:string", "0", "1");
        element.setAttribute("default", "USA");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        assertEquals("USA", newAttribute.getAttribute("default"));
    }

    @Test
    @DisplayName("Should preserve fixed value")
    void testPreserveFixedValue() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("version", "xs:string", "1", "1");
        element.setAttribute("fixed", "1.0");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        assertEquals("1.0", newAttribute.getAttribute("fixed"));
        assertFalse(newAttribute.hasAttribute("default"));
    }

    @Test
    @DisplayName("Should fail for element with complex content")
    void testFailForComplexContent() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("address", null, "1", "1");
        Element inlineComplexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        element.appendChild(inlineComplexType);
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("complex content"));
    }

    @Test
    @DisplayName("Should fail for element with maxOccurs > 1")
    void testFailForMaxOccursGreaterThan1() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("phoneNumber", "xs:string", "0", "5");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("complex content"));
    }

    @Test
    @DisplayName("Should fail for element with child elements")
    void testFailForElementWithChildren() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("name", null, "1", "1");
        Element inlineSequence = createSequence();
        Element childElement = createElement("firstName", "xs:string", "1", "1");
        inlineSequence.appendChild(childElement);
        element.appendChild(inlineSequence);
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("complex content"));
    }

    @Test
    @DisplayName("Should undo conversion")
    void testUndo() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");
        element.setAttribute("default", "0");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        // Execute
        command.execute();

        // Verify attribute created, element removed
        assertEquals(0, sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").getLength());
        assertEquals(1, complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute").getLength());

        // Undo
        assertTrue(command.undo());

        // Verify element restored, attribute removed
        NodeList elements = sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(1, elements.getLength());

        Element restoredElement = (Element) elements.item(0);
        assertEquals("id", restoredElement.getAttribute("name"));
        assertEquals("xs:string", restoredElement.getAttribute("type"));
        assertEquals("1", restoredElement.getAttribute("minOccurs"));
        assertEquals("0", restoredElement.getAttribute("default"));

        assertEquals(0, complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute").getLength());
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should get description with element name")
    void testGetDescription() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("id"));
        assertTrue(description.contains("Convert element"));
    }

    @Test
    @DisplayName("Should copy documentation from element to attribute")
    void testCopyDocumentation() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");

        // Add annotation to element
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        Element documentation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:documentation");
        documentation.setTextContent("Unique identifier");
        annotation.appendChild(documentation);
        element.appendChild(annotation);

        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        // Verify documentation copied to attribute
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        NodeList annotations = newAttribute.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation");
        assertEquals(1, annotations.getLength());

        NodeList docs = newAttribute.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "documentation");
        assertEquals(1, docs.getLength());
        assertEquals("Unique identifier", docs.item(0).getTextContent());
    }

    @Test
    @DisplayName("Should insert attribute before existing attributes")
    void testInsertBeforeExistingAttributes() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("id", "xs:string", "1", "1");
        sequence.appendChild(element);

        // Add existing attribute
        Element existingAttr = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:attribute");
        existingAttr.setAttribute("name", "version");
        complexType.appendChild(existingAttr);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        // Verify order: id attribute before version attribute
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(2, attributes.getLength());
        assertEquals("id", ((Element) attributes.item(0)).getAttribute("name"));
        assertEquals("version", ((Element) attributes.item(1)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should handle element in choice")
    void testElementInChoice() {
        Element complexType = createComplexType("PersonType");
        Element choice = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:choice");
        complexType.appendChild(choice);

        Element element = createElement("email", "xs:string", "0", "1");
        choice.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        // Verify attribute created in complexType
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(1, attributes.getLength());
        assertEquals("email", ((Element) attributes.item(0)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should restore position during undo")
    void testUndoRestoresPosition() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element1 = createElement("firstName", "xs:string", "1", "1");
        Element element2 = createElement("id", "xs:string", "1", "1");
        Element element3 = createElement("lastName", "xs:string", "1", "1");

        sequence.appendChild(element1);
        sequence.appendChild(element2);
        sequence.appendChild(element3);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element2,
                domManipulator
        );

        command.execute();
        command.undo();

        // Verify element2 is between element1 and element3
        NodeList elements = sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(3, elements.getLength());
        assertEquals("firstName", ((Element) elements.item(0)).getAttribute("name"));
        assertEquals("id", ((Element) elements.item(1)).getAttribute("name"));
        assertEquals("lastName", ((Element) elements.item(2)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should handle element with both default and fixed")
    void testPreferFixedOverDefault() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = createElement("status", "xs:string", "0", "1");
        element.setAttribute("fixed", "active");
        element.setAttribute("default", "inactive");
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        // Fixed should take precedence
        assertEquals("active", newAttribute.getAttribute("fixed"));
        assertFalse(newAttribute.hasAttribute("default"));
    }

    @Test
    @DisplayName("Should handle element with default minOccurs/maxOccurs")
    void testDefaultOccurrences() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", "name");
        element.setAttribute("type", "xs:string");
        // No minOccurs/maxOccurs set - defaults to 1,1
        sequence.appendChild(element);

        ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                xsdDocument,
                element,
                domManipulator
        );

        command.execute();

        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        Element newAttribute = (Element) attributes.item(0);

        assertEquals("required", newAttribute.getAttribute("use"));
    }

    @Test
    @DisplayName("Should handle built-in simple types")
    void testBuiltInSimpleTypes() {
        Element complexType = createComplexType("PersonType");
        Element sequence = createSequence();
        complexType.appendChild(sequence);

        String[] types = {"xs:string", "xs:integer", "xs:decimal", "xs:boolean", "xs:date", "xsd:string"};

        for (String type : types) {
            Element element = createElement("test_" + type.replace(":", "_"), type, "0", "1");
            sequence.appendChild(element);

            ConvertElementToAttributeCommand command = new ConvertElementToAttributeCommand(
                    xsdDocument,
                    element,
                    domManipulator
            );

            assertTrue(command.execute());

            // Should preserve the type
            NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
            Element newAttribute = (Element) attributes.item(attributes.getLength() - 1);
            assertEquals(type, newAttribute.getAttribute("type"));
        }
    }

    // Helper methods
    private Element createComplexType(String name) {
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", name);
        return complexType;
    }

    private Element createSequence() {
        return xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");
    }

    private Element createElement(String name, String type, String minOccurs, String maxOccurs) {
        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", name);
        if (type != null) {
            element.setAttribute("type", type);
        }
        if (minOccurs != null) {
            element.setAttribute("minOccurs", minOccurs);
        }
        if (maxOccurs != null) {
            element.setAttribute("maxOccurs", maxOccurs);
        }
        return element;
    }
}
