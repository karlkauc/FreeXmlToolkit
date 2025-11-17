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
 * Unit tests for ConvertAttributeToElementCommand.
 * Tests converting XSD attributes to elements with proper content model handling.
 */
class ConvertAttributeToElementCommandTest {

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
    @DisplayName("Should convert required attribute to element with minOccurs=1")
    void testConvertRequiredAttribute() {
        // Create complexType with required attribute
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        assertTrue(command.execute());

        // Verify attribute removed
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(0, attributes.getLength());

        // Verify element created in sequence
        NodeList sequences = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence");
        assertEquals(1, sequences.getLength());

        Element sequence = (Element) sequences.item(0);
        NodeList elements = sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(1, elements.getLength());

        Element newElement = (Element) elements.item(0);
        assertEquals("id", newElement.getAttribute("name"));
        assertEquals("xs:string", newElement.getAttribute("type"));
        assertEquals("1", newElement.getAttribute("minOccurs"));
    }

    @Test
    @DisplayName("Should convert optional attribute to element with minOccurs=0")
    void testConvertOptionalAttribute() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("middleName", "xs:string", "optional");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        assertTrue(command.execute());

        // Find created element
        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("middleName", newElement.getAttribute("name"));
        assertEquals("0", newElement.getAttribute("minOccurs"));
        assertEquals("1", newElement.getAttribute("maxOccurs"));
    }

    @Test
    @DisplayName("Should convert attribute without use to optional element")
    void testConvertAttributeWithoutUse() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("nickname", "xs:string", null);
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        assertTrue(command.execute());

        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("0", newElement.getAttribute("minOccurs"));
    }

    @Test
    @DisplayName("Should preserve attribute type")
    void testPreserveAttributeType() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("age", "xs:integer", "optional");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("xs:integer", newElement.getAttribute("type"));
    }

    @Test
    @DisplayName("Should default to xs:string if no type specified")
    void testDefaultTypeToString() {
        Element complexType = createComplexType("PersonType");
        Element attribute = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:attribute");
        attribute.setAttribute("name", "status");
        attribute.setAttribute("use", "optional");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("xs:string", newElement.getAttribute("type"));
    }

    @Test
    @DisplayName("Should preserve default value")
    void testPreserveDefaultValue() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("country", "xs:string", "optional");
        attribute.setAttribute("default", "USA");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("USA", newElement.getAttribute("default"));
    }

    @Test
    @DisplayName("Should preserve fixed value")
    void testPreserveFixedValue() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("version", "xs:string", "required");
        attribute.setAttribute("fixed", "1.0");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("1.0", newElement.getAttribute("fixed"));
        assertFalse(newElement.hasAttribute("default"));
    }

    @Test
    @DisplayName("Should use existing sequence if present")
    void testUseExistingSequence() {
        Element complexType = createComplexType("PersonType");
        Element sequence = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");
        Element existingElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        existingElement.setAttribute("name", "name");
        sequence.appendChild(existingElement);
        complexType.appendChild(sequence);

        Element attribute = createAttribute("id", "xs:string", "required");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        // Should use existing sequence
        NodeList sequences = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence");
        assertEquals(1, sequences.getLength());

        // Should have 2 elements now
        NodeList elements = sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(2, elements.getLength());
    }

    @Test
    @DisplayName("Should use existing choice if present")
    void testUseExistingChoice() {
        Element complexType = createComplexType("PersonType");
        Element choice = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:choice");
        complexType.appendChild(choice);

        Element attribute = createAttribute("id", "xs:string", "required");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        // Should use existing choice
        NodeList choices = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "choice");
        assertEquals(1, choices.getLength());

        NodeList elements = choice.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(1, elements.getLength());
    }

    @Test
    @DisplayName("Should create sequence before attributes")
    void testCreateSequenceBeforeAttributes() {
        Element complexType = createComplexType("PersonType");
        Element attribute1 = createAttribute("id", "xs:string", "required");
        Element attribute2 = createAttribute("version", "xs:string", "required");
        complexType.appendChild(attribute1);
        complexType.appendChild(attribute2);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute1,
                domManipulator
        );

        command.execute();

        // Verify sequence was inserted before remaining attribute
        int sequenceIndex = -1;
        int attributeIndex = -1;
        int currentIndex = 0;

        for (int i = 0; i < complexType.getChildNodes().getLength(); i++) {
            if (complexType.getChildNodes().item(i) instanceof Element element) {
                String localName = element.getLocalName();
                if ("sequence".equals(localName)) sequenceIndex = currentIndex;
                if ("attribute".equals(localName)) attributeIndex = currentIndex;
                currentIndex++;
            }
        }

        assertTrue(sequenceIndex < attributeIndex);
    }

    @Test
    @DisplayName("Should undo conversion")
    void testUndo() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");
        attribute.setAttribute("default", "0");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        // Execute
        command.execute();

        // Verify element created, attribute removed
        assertEquals(0, complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute").getLength());
        assertEquals(1, complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").getLength());

        // Undo
        assertTrue(command.undo());

        // Verify attribute restored, sequence removed
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(1, attributes.getLength());

        Element restoredAttribute = (Element) attributes.item(0);
        assertEquals("id", restoredAttribute.getAttribute("name"));
        assertEquals("xs:string", restoredAttribute.getAttribute("type"));
        assertEquals("required", restoredAttribute.getAttribute("use"));
        assertEquals("0", restoredAttribute.getAttribute("default"));
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should get description with attribute name")
    void testGetDescription() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("id"));
        assertTrue(description.contains("Convert attribute"));
    }

    @Test
    @DisplayName("Should copy documentation from attribute to element")
    void testCopyDocumentation() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");

        // Add annotation to attribute
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        Element documentation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:documentation");
        documentation.setTextContent("Unique identifier");
        annotation.appendChild(documentation);
        attribute.appendChild(annotation);

        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        // Verify documentation copied to element
        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        NodeList annotations = newElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation");
        assertEquals(1, annotations.getLength());

        NodeList docs = newElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "documentation");
        assertEquals(1, docs.getLength());
        assertEquals("Unique identifier", docs.item(0).getTextContent());
    }

    @Test
    @DisplayName("Should handle prohibited attribute")
    void testConvertProhibitedAttribute() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("deprecated", "xs:string", "prohibited");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        // Prohibited attributes become optional elements (minOccurs=0)
        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        assertEquals("0", newElement.getAttribute("minOccurs"));
    }

    @Test
    @DisplayName("Should handle attribute in element context")
    void testConvertAttributeInElement() {
        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", "person");

        Element inlineComplexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        element.appendChild(inlineComplexType);

        Element attribute = createAttribute("id", "xs:string", "required");
        inlineComplexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        // Verify sequence created in inline complexType
        NodeList sequences = inlineComplexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence");
        assertEquals(1, sequences.getLength());
    }

    @Test
    @DisplayName("Should restore position during undo")
    void testUndoRestoresPosition() {
        Element complexType = createComplexType("PersonType");
        Element attribute1 = createAttribute("id", "xs:string", "required");
        Element attribute2 = createAttribute("version", "xs:string", "required");
        complexType.appendChild(attribute1);
        complexType.appendChild(attribute2);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute1,
                domManipulator
        );

        command.execute();
        command.undo();

        // Verify attribute1 is before attribute2
        NodeList attributes = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(2, attributes.getLength());
        assertEquals("id", ((Element) attributes.item(0)).getAttribute("name"));
        assertEquals("version", ((Element) attributes.item(1)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should handle attribute with both default and fixed")
    void testPreferFixedOverDefault() {
        Element complexType = createComplexType("PersonType");
        Element attribute = createAttribute("status", "xs:string", "optional");
        attribute.setAttribute("fixed", "active");
        attribute.setAttribute("default", "inactive");
        complexType.appendChild(attribute);

        ConvertAttributeToElementCommand command = new ConvertAttributeToElementCommand(
                xsdDocument,
                attribute,
                domManipulator
        );

        command.execute();

        Element sequence = (Element) complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence").item(0);
        Element newElement = (Element) sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);

        // Fixed should take precedence
        assertEquals("active", newElement.getAttribute("fixed"));
        assertFalse(newElement.hasAttribute("default"));
    }

    // Helper methods
    private Element createComplexType(String name) {
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", name);
        return complexType;
    }

    private Element createAttribute(String name, String type, String use) {
        Element attribute = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:attribute");
        attribute.setAttribute("name", name);
        if (type != null) {
            attribute.setAttribute("type", type);
        }
        if (use != null) {
            attribute.setAttribute("use", use);
        }
        return attribute;
    }
}
