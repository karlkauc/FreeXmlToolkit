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
 * Unit tests for CloneTypeCommand.
 * Tests cloning XSD type definitions with proper name assignment.
 */
class CloneTypeCommandTest {

    private Document xsdDocument;
    private Element schemaRoot;
    private XsdDomManipulator domManipulator;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        xsdDocument = builder.newDocument();

        // Create schema root element
        schemaRoot = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:schema");
        schemaRoot.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
        xsdDocument.appendChild(schemaRoot);

        domManipulator = mock(XsdDomManipulator.class);
    }

    @Test
    @DisplayName("Should clone complexType with new name")
    void testCloneComplexType() {
        // Create source complexType
        Element sourceType = createComplexType("PersonType");
        Element sequence = createSequence();
        Element element = createElement("name", "xs:string");
        sequence.appendChild(element);
        sourceType.appendChild(sequence);
        schemaRoot.appendChild(sourceType);

        // Mock domManipulator behavior
        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify cloned type exists
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        assertEquals(2, complexTypes.getLength());

        // Find the cloned type
        Element clonedType = null;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element type = (Element) complexTypes.item(i);
            if ("EmployeeType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        assertNotNull(clonedType);
        assertEquals("EmployeeType", clonedType.getAttribute("name"));

        // Verify structure was cloned
        NodeList sequences = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence");
        assertEquals(1, sequences.getLength());

        NodeList elements = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(1, elements.getLength());
        assertEquals("name", ((Element) elements.item(0)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should clone simpleType with new name")
    void testCloneSimpleType() {
        // Create source simpleType
        Element sourceType = createSimpleType("StringType");
        Element restriction = createRestriction("xs:string");
        sourceType.appendChild(restriction);
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "StringType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "CustomStringType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "StringType",
                "CustomStringType",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify cloned type exists
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        assertEquals(2, simpleTypes.getLength());

        // Find the cloned type
        Element clonedType = null;
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element type = (Element) simpleTypes.item(i);
            if ("CustomStringType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        assertNotNull(clonedType);
        assertEquals("CustomStringType", clonedType.getAttribute("name"));

        // Verify restriction was cloned
        NodeList restrictions = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
        assertEquals(1, restrictions.getLength());
    }

    @Test
    @DisplayName("Should fail when source type not found")
    void testFailWhenSourceTypeNotFound() {
        when(domManipulator.findTypeDefinition(xsdDocument, "NonExistentType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "NonExistentType",
                "NewType",
                domManipulator
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, command::execute);
        assertTrue(exception.getMessage().contains("Source type definition not found"));
    }

    @Test
    @DisplayName("Should fail when target type name already exists")
    void testFailWhenTargetTypeExists() {
        Element sourceType = createComplexType("PersonType");
        schemaRoot.appendChild(sourceType);

        Element existingType = createComplexType("EmployeeType");
        schemaRoot.appendChild(existingType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(existingType);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, command::execute);
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should undo type cloning")
    void testUndo() {
        Element sourceType = createComplexType("PersonType");
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        // Execute
        command.execute();

        NodeList complexTypesBeforeUndo = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        assertEquals(2, complexTypesBeforeUndo.getLength());

        // Undo
        assertTrue(command.undo());

        NodeList complexTypesAfterUndo = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        assertEquals(1, complexTypesAfterUndo.getLength());
        assertEquals("PersonType", ((Element) complexTypesAfterUndo.item(0)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        Element sourceType = createComplexType("PersonType");
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should get description with type names")
    void testGetDescription() {
        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("PersonType"));
        assertTrue(description.contains("EmployeeType"));
        assertTrue(description.contains("Clone"));
    }

    @Test
    @DisplayName("Should clone type with attributes")
    void testCloneTypeWithAttributes() {
        Element sourceType = createComplexType("PersonType");
        Element attribute = createAttribute("id", "xs:string", "required");
        sourceType.appendChild(attribute);
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        command.execute();

        // Find cloned type
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        Element clonedType = null;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element type = (Element) complexTypes.item(i);
            if ("EmployeeType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        // Verify attributes were cloned
        NodeList attributes = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        assertEquals(1, attributes.getLength());
        assertEquals("id", ((Element) attributes.item(0)).getAttribute("name"));
    }

    @Test
    @DisplayName("Should clone type with documentation")
    void testCloneTypeWithDocumentation() {
        Element sourceType = createComplexType("PersonType");
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        Element documentation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:documentation");
        documentation.setTextContent("Person type definition");
        annotation.appendChild(documentation);
        sourceType.appendChild(annotation);
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        command.execute();

        // Find cloned type
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        Element clonedType = null;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element type = (Element) complexTypes.item(i);
            if ("EmployeeType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        // Verify documentation was cloned
        NodeList docs = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "documentation");
        assertEquals(1, docs.getLength());
        assertEquals("Person type definition", docs.item(0).getTextContent());
    }

    @Test
    @DisplayName("Should clone type with nested structures")
    void testCloneTypeWithNestedStructures() {
        Element sourceType = createComplexType("PersonType");
        Element sequence = createSequence();
        Element choice = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:choice");
        Element element1 = createElement("email", "xs:string");
        Element element2 = createElement("phone", "xs:string");
        choice.appendChild(element1);
        choice.appendChild(element2);
        sequence.appendChild(choice);
        sourceType.appendChild(sequence);
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "ContactType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "ContactType",
                domManipulator
        );

        command.execute();

        // Find cloned type
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        Element clonedType = null;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element type = (Element) complexTypes.item(i);
            if ("ContactType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        // Verify nested structure was cloned
        NodeList sequences = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "sequence");
        assertEquals(1, sequences.getLength());

        NodeList choices = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "choice");
        assertEquals(1, choices.getLength());

        NodeList elements = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        assertEquals(2, elements.getLength());
    }

    @Test
    @DisplayName("Should clone simpleType with facets")
    void testCloneSimpleTypeWithFacets() {
        Element sourceType = createSimpleType("ZipCodeType");
        Element restriction = createRestriction("xs:string");
        Element pattern = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:pattern");
        pattern.setAttribute("value", "\\d{5}");
        restriction.appendChild(pattern);
        sourceType.appendChild(restriction);
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "ZipCodeType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "PostalCodeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "ZipCodeType",
                "PostalCodeType",
                domManipulator
        );

        command.execute();

        // Find cloned type
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        Element clonedType = null;
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element type = (Element) simpleTypes.item(i);
            if ("PostalCodeType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        // Verify facets were cloned
        NodeList patterns = clonedType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "pattern");
        assertEquals(1, patterns.getLength());
        assertEquals("\\d{5}", ((Element) patterns.item(0)).getAttribute("value"));
    }

    @Test
    @DisplayName("Should clone type preserves all attributes")
    void testClonePreservesAllAttributes() {
        Element sourceType = createComplexType("PersonType");
        sourceType.setAttribute("mixed", "true");
        sourceType.setAttribute("abstract", "true");
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        command.execute();

        // Find cloned type
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        Element clonedType = null;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element type = (Element) complexTypes.item(i);
            if ("EmployeeType".equals(type.getAttribute("name"))) {
                clonedType = type;
                break;
            }
        }

        // Verify attributes were cloned (except name which was changed)
        assertEquals("true", clonedType.getAttribute("mixed"));
        assertEquals("true", clonedType.getAttribute("abstract"));
        assertEquals("EmployeeType", clonedType.getAttribute("name"));
    }

    @Test
    @DisplayName("Should support execute-undo-execute cycle with new command")
    void testExecuteUndoExecuteCycle() {
        Element sourceType = createComplexType("PersonType");
        schemaRoot.appendChild(sourceType);

        when(domManipulator.findTypeDefinition(xsdDocument, "PersonType")).thenReturn(sourceType);
        when(domManipulator.findTypeDefinition(xsdDocument, "EmployeeType")).thenReturn(null);

        CloneTypeCommand command = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );

        // Execute
        assertTrue(command.execute());
        assertEquals(2, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType").getLength());

        // Undo
        assertTrue(command.undo());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType").getLength());

        // Execute again with new command
        CloneTypeCommand command2 = new CloneTypeCommand(
                xsdDocument,
                "PersonType",
                "EmployeeType",
                domManipulator
        );
        assertTrue(command2.execute());
        assertEquals(2, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType").getLength());
    }

    // Helper methods
    private Element createComplexType(String name) {
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", name);
        return complexType;
    }

    private Element createSimpleType(String name) {
        Element simpleType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:simpleType");
        simpleType.setAttribute("name", name);
        return simpleType;
    }

    private Element createSequence() {
        return xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");
    }

    private Element createElement(String name, String type) {
        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", name);
        if (type != null) {
            element.setAttribute("type", type);
        }
        return element;
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

    private Element createRestriction(String base) {
        Element restriction = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:restriction");
        restriction.setAttribute("base", base);
        return restriction;
    }
}
