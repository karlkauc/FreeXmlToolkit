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
 * Unit tests for RemoveIncludeCommand.
 * Tests removing include statements from XSD schemas with proper undo support.
 */
class RemoveIncludeCommandTest {

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
    @DisplayName("Should execute and remove include by schemaLocation")
    void testExecuteRemoveInclude() {
        // Add include to schema
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify include was removed
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(0, includes.getLength());
    }

    @Test
    @DisplayName("Should fail when schemaLocation is null")
    void testExecuteFailsWithNullSchemaLocation() {
        RemoveIncludeCommand command = new RemoveIncludeCommand(
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
        RemoveIncludeCommand command = new RemoveIncludeCommand(
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
        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "   ",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("Schema location is required"));
    }

    @Test
    @DisplayName("Should fail when include not found")
    void testExecuteFailsWhenIncludeNotFound() {
        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "nonexistent.xsd",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should remove correct include when multiple exist")
    void testRemoveCorrectInclude() {
        // Add multiple includes
        Element include1 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include1.setAttribute("schemaLocation", "types1.xsd");
        schemaRoot.appendChild(include1);

        Element include2 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include2.setAttribute("schemaLocation", "types2.xsd");
        schemaRoot.appendChild(include2);

        Element include3 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include3.setAttribute("schemaLocation", "types3.xsd");
        schemaRoot.appendChild(include3);

        // Remove second include
        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "types2.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify only include2 was removed
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(2, includes.getLength());

        // Verify remaining includes
        boolean found1 = false, found3 = false;
        for (int i = 0; i < includes.getLength(); i++) {
            Element inc = (Element) includes.item(i);
            String loc = inc.getAttribute("schemaLocation");
            if ("types1.xsd".equals(loc)) found1 = true;
            if ("types3.xsd".equals(loc)) found3 = true;
        }
        assertTrue(found1 && found3);
    }

    @Test
    @DisplayName("Should undo include removal")
    void testUndo() {
        // Add include
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        // Execute
        command.execute();
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());

        // Undo
        assertTrue(command.undo());

        // Verify include restored
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());

        Element restoredInclude = (Element) includes.item(0);
        assertEquals("common-types.xsd", restoredInclude.getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        // Add include
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should restore include at correct position during undo")
    void testUndoRestoresPosition() {
        // Add import
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/other");
        schemaRoot.appendChild(importElement);

        // Add include
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(includeElement);

        // Add complexType
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        // Execute and undo
        command.execute();
        command.undo();

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
    @DisplayName("Should restore include at end if it was last element")
    void testUndoRestoresAtEnd() {
        // Add include as last element
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        // Execute and undo
        command.execute();
        command.undo();

        // Verify include is restored
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
    }

    @Test
    @DisplayName("Should support execute-undo-execute cycle")
    void testExecuteUndoExecuteCycle() {
        // Add include
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        // Execute
        assertTrue(command.execute());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());

        // Undo
        assertTrue(command.undo());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());

        // Cannot execute again with same command
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should get description with schemaLocation")
    void testGetDescription() {
        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("common-types.xsd"));
        assertTrue(description.contains("Remove include"));
    }

    @Test
    @DisplayName("Should handle schema without document element")
    void testNoSchemaRoot() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document emptyDoc = builder.newDocument();

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                emptyDoc,
                "common-types.xsd",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("schema root"));
    }

    @Test
    @DisplayName("Should preserve all include attributes during undo")
    void testUndoPreservesAttributes() {
        // Add include with additional attributes
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common-types.xsd");
        includeElement.setAttribute("id", "include1");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common-types.xsd",
                domManipulator
        );

        // Execute and undo
        command.execute();
        command.undo();

        // Verify all attributes preserved
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());

        Element restoredInclude = (Element) includes.item(0);
        assertEquals("common-types.xsd", restoredInclude.getAttribute("schemaLocation"));
        assertEquals("include1", restoredInclude.getAttribute("id"));
    }

    @Test
    @DisplayName("Should handle relative schemaLocation paths")
    void testRelativeSchemaLocation() {
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "../common/types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "../common/types.xsd",
                domManipulator
        );

        assertTrue(command.execute());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());
    }

    @Test
    @DisplayName("Should handle absolute schemaLocation paths")
    void testAbsoluteSchemaLocation() {
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "file:///path/to/types.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "file:///path/to/types.xsd",
                domManipulator
        );

        assertTrue(command.execute());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());
    }

    @Test
    @DisplayName("Should handle URL schemaLocation")
    void testUrlSchemaLocation() {
        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "http://example.com/schemas/common.xsd");
        schemaRoot.appendChild(includeElement);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "http://example.com/schemas/common.xsd",
                domManipulator
        );

        assertTrue(command.execute());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());
    }

    @Test
    @DisplayName("Should match first include when multiple have same schemaLocation")
    void testRemoveFirstMatchingInclude() {
        // Add two includes with same schemaLocation (shouldn't happen but test defensive code)
        Element include1 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include1.setAttribute("schemaLocation", "common.xsd");
        include1.setAttribute("id", "first");
        schemaRoot.appendChild(include1);

        Element include2 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include2.setAttribute("schemaLocation", "common.xsd");
        include2.setAttribute("id", "second");
        schemaRoot.appendChild(include2);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common.xsd",
                domManipulator
        );

        command.execute();

        // Verify only first was removed
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(1, includes.getLength());
        assertEquals("second", ((Element) includes.item(0)).getAttribute("id"));
    }

    @Test
    @DisplayName("Should handle include between other elements")
    void testRemoveIncludeBetweenElements() {
        // Create structure: import, include1, include2, complexType
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/other");
        schemaRoot.appendChild(importElement);

        Element include1 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include1.setAttribute("schemaLocation", "common1.xsd");
        schemaRoot.appendChild(include1);

        Element include2 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include2.setAttribute("schemaLocation", "common2.xsd");
        schemaRoot.appendChild(include2);

        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        schemaRoot.appendChild(complexType);

        RemoveIncludeCommand command = new RemoveIncludeCommand(
                xsdDocument,
                "common1.xsd",
                domManipulator
        );

        // Execute
        command.execute();

        // Verify structure: import, include2, complexType
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());
        assertEquals("common2.xsd",
            ((Element) schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").item(0))
            .getAttribute("schemaLocation"));

        // Undo
        command.undo();

        // Verify include1 restored in correct position
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");
        assertEquals(2, includes.getLength());

        // Verify order: include1, include2
        assertEquals("common1.xsd", ((Element) includes.item(0)).getAttribute("schemaLocation"));
        assertEquals("common2.xsd", ((Element) includes.item(1)).getAttribute("schemaLocation"));
    }
}
