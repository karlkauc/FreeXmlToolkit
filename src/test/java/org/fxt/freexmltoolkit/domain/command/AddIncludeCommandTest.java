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
 * Unit tests for AddIncludeCommand.
 * Tests adding include statements to XSD schemas for same-namespace schema composition.
 */
class AddIncludeCommandTest {

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
    @DisplayName("Should execute and add include with schemaLocation")
    void testExecuteAddInclude() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify include was added
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());

        Element includeElement = (Element) includes.item(0);
        assertEquals("common-types.xsd", includeElement.getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should fail when schemaLocation is null")
    void testExecuteFailsWithNullSchemaLocation() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                null,
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("Schema location is required"));
    }

    @Test
    @DisplayName("Should fail when schemaLocation is empty")
    void testExecuteFailsWithEmptySchemaLocation() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("Schema location is required"));
    }

    @Test
    @DisplayName("Should fail when schemaLocation is whitespace only")
    void testExecuteFailsWithWhitespaceSchemaLocation() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "   ",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("Schema location is required"));
    }

    @Test
    @DisplayName("Should fail when include already exists")
    void testExecuteFailsWhenIncludeExists() {
        // Add first include
        Element existingInclude = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        existingInclude.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(existingInclude);

        // Try to add duplicate
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should insert include before complexType")
    void testInsertBeforeComplexType() {
        // Add a complex type to schema
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        // Verify include is before complexType
        Element firstElement = null;
        Element secondElement = null;

        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element) {
                if (firstElement == null) {
                    firstElement = (Element) schemaRoot.getChildNodes().item(i);
                } else if (secondElement == null) {
                    secondElement = (Element) schemaRoot.getChildNodes().item(i);
                    break;
                }
            }
        }

        assertNotNull(firstElement);
        assertNotNull(secondElement);
        assertEquals("include", firstElement.getLocalName());
        assertEquals("complexType", secondElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert include before simpleType")
    void testInsertBeforeSimpleType() {
        Element simpleType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:simpleType");
        simpleType.setAttribute("name", "StringType");
        schemaRoot.appendChild(simpleType);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        Element firstElement = null;
        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element) {
                firstElement = (Element) schemaRoot.getChildNodes().item(i);
                break;
            }
        }

        assertNotNull(firstElement);
        assertEquals("include", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert include before element")
    void testInsertBeforeElement() {
        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", "person");
        schemaRoot.appendChild(element);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        Element firstElement = null;
        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element) {
                firstElement = (Element) schemaRoot.getChildNodes().item(i);
                break;
            }
        }

        assertNotNull(firstElement);
        assertEquals("include", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert include after annotation")
    void testInsertAfterAnnotation() {
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        schemaRoot.appendChild(annotation);

        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        // Verify order: annotation, include, complexType
        int annotationIndex = -1;
        int includeIndex = -1;
        int complexTypeIndex = -1;
        int currentIndex = 0;

        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element element) {
                String localName = element.getLocalName();
                if ("annotation".equals(localName)) annotationIndex = currentIndex;
                if ("include".equals(localName)) includeIndex = currentIndex;
                if ("complexType".equals(localName)) complexTypeIndex = currentIndex;
                currentIndex++;
            }
        }

        assertTrue(annotationIndex < includeIndex);
        assertTrue(includeIndex < complexTypeIndex);
    }

    @Test
    @DisplayName("Should insert include after imports")
    void testInsertAfterImports() {
        // Add import
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/other");
        schemaRoot.appendChild(importElement);

        // Add complexType
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        // Verify order: import, include, complexType
        int importIndex = -1;
        int includeIndex = -1;
        int complexTypeIndex = -1;
        int currentIndex = 0;

        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element element) {
                String localName = element.getLocalName();
                if ("import".equals(localName)) importIndex = currentIndex;
                if ("include".equals(localName)) includeIndex = currentIndex;
                if ("complexType".equals(localName)) complexTypeIndex = currentIndex;
                currentIndex++;
            }
        }

        assertTrue(importIndex < includeIndex);
        assertTrue(includeIndex < complexTypeIndex);
    }

    @Test
    @DisplayName("Should append include at end if no type definitions exist")
    void testAppendAtEnd() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
    }

    @Test
    @DisplayName("Should undo include addition")
    void testUndo() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        // Verify include exists
        NodeList includesBeforeUndo = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includesBeforeUndo.getLength());

        // Undo
        assertTrue(command.undo());

        // Verify include removed
        NodeList includesAfterUndo = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(0, includesAfterUndo.getLength());
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());

        // Verify only one include exists
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
    }

    @Test
    @DisplayName("Should support execute-undo-execute cycle")
    void testExecuteUndoExecuteCycle() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        // Execute
        assertTrue(command.execute());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());

        // Undo
        assertTrue(command.undo());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());

        // Execute again with new command
        AddIncludeCommand command2 = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );
        assertTrue(command2.execute());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());
    }

    @Test
    @DisplayName("Should get description with schemaLocation")
    void testGetDescription() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("common-types.xsd"));
        assertTrue(description.contains("include"));
    }

    @Test
    @DisplayName("Should handle multiple includes with different locations")
    void testMultipleIncludes() {
        AddIncludeCommand command1 = new AddIncludeCommand(
                xsdDocument,
                "types1.xsd",
                domManipulator
        );

        AddIncludeCommand command2 = new AddIncludeCommand(
                xsdDocument,
                "types2.xsd",
                domManipulator
        );

        assertTrue(command1.execute());
        assertTrue(command2.execute());

        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(2, includes.getLength());
    }

    @Test
    @DisplayName("Should handle schema without document element")
    void testNoSchemaRoot() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document emptyDoc = builder.newDocument();

        AddIncludeCommand command = new AddIncludeCommand(
                emptyDoc,
                "common-types.xsd",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("schema root"));
    }

    @Test
    @DisplayName("Should insert before group element")
    void testInsertBeforeGroup() {
        Element group = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:group");
        group.setAttribute("name", "PersonGroup");
        schemaRoot.appendChild(group);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        Element firstElement = null;
        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element) {
                firstElement = (Element) schemaRoot.getChildNodes().item(i);
                break;
            }
        }

        assertNotNull(firstElement);
        assertEquals("include", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert before attributeGroup element")
    void testInsertBeforeAttributeGroup() {
        Element attrGroup = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:attributeGroup");
        attrGroup.setAttribute("name", "PersonAttributes");
        schemaRoot.appendChild(attrGroup);

        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        command.execute();

        Element firstElement = null;
        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element) {
                firstElement = (Element) schemaRoot.getChildNodes().item(i);
                break;
            }
        }

        assertNotNull(firstElement);
        assertEquals("include", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should handle relative schemaLocation paths")
    void testRelativeSchemaLocation() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "../common/types.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
        assertEquals("../common/types.xsd", ((Element) includes.item(0)).getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should handle absolute schemaLocation paths")
    void testAbsoluteSchemaLocation() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "file:///path/to/types.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
        assertEquals("file:///path/to/types.xsd", ((Element) includes.item(0)).getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should handle URL schemaLocation")
    void testUrlSchemaLocation() {
        AddIncludeCommand command = new AddIncludeCommand(
                xsdDocument,
                "http://example.com/schemas/common.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
        assertEquals("http://example.com/schemas/common.xsd", ((Element) includes.item(0)).getAttribute("schemaLocation"));
    }
}
