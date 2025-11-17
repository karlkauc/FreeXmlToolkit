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
 * Unit tests for RemoveImportCommand.
 * Tests removing import statements from XSD schemas with proper undo support.
 */
class RemoveImportCommandTest {

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
    @DisplayName("Should execute and remove import by namespace")
    void testExecuteRemoveImport() {
        // Add import to schema
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        importElement.setAttribute("schemaLocation", "types.xsd");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify import was removed
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(0, imports.getLength());
    }

    @Test
    @DisplayName("Should fail when import not found")
    void testExecuteFailsWhenImportNotFound() {
        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/nonexistent",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should remove import with empty namespace")
    void testRemoveImportWithEmptyNamespace() {
        // Add import with empty namespace
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("schemaLocation", "types.xsd");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "",
                domManipulator
        );

        assertTrue(command.execute());

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(0, imports.getLength());
    }

    @Test
    @DisplayName("Should remove import with null namespace")
    void testRemoveImportWithNullNamespace() {
        // Add import without namespace attribute
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("schemaLocation", "types.xsd");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                null,
                domManipulator
        );

        assertTrue(command.execute());

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(0, imports.getLength());
    }

    @Test
    @DisplayName("Should remove correct import when multiple exist")
    void testRemoveCorrectImport() {
        // Add multiple imports
        Element import1 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        import1.setAttribute("namespace", "http://example.com/types1");
        schemaRoot.appendChild(import1);

        Element import2 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        import2.setAttribute("namespace", "http://example.com/types2");
        schemaRoot.appendChild(import2);

        Element import3 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        import3.setAttribute("namespace", "http://example.com/types3");
        schemaRoot.appendChild(import3);

        // Remove second import
        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types2",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify only import2 was removed
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(2, imports.getLength());

        // Verify remaining imports
        boolean found1 = false, found3 = false;
        for (int i = 0; i < imports.getLength(); i++) {
            Element imp = (Element) imports.item(i);
            String ns = imp.getAttribute("namespace");
            if ("http://example.com/types1".equals(ns)) found1 = true;
            if ("http://example.com/types3".equals(ns)) found3 = true;
        }
        assertTrue(found1 && found3);
    }

    @Test
    @DisplayName("Should undo import removal")
    void testUndo() {
        // Add import
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        importElement.setAttribute("schemaLocation", "types.xsd");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        // Execute
        command.execute();
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());

        // Undo
        assertTrue(command.undo());

        // Verify import restored
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());

        Element restoredImport = (Element) imports.item(0);
        assertEquals("http://example.com/types", restoredImport.getAttribute("namespace"));
        assertEquals("types.xsd", restoredImport.getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        // Add import
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should restore import at correct position during undo")
    void testUndoRestoresPosition() {
        // Add annotation
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        schemaRoot.appendChild(annotation);

        // Add import
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        schemaRoot.appendChild(importElement);

        // Add complexType
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        // Execute and undo
        command.execute();
        command.undo();

        // Verify order: annotation, import, complexType
        int annotationIndex = -1;
        int importIndex = -1;
        int complexTypeIndex = -1;
        int currentIndex = 0;

        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element element) {
                String localName = element.getLocalName();
                if ("annotation".equals(localName)) annotationIndex = currentIndex;
                if ("import".equals(localName)) importIndex = currentIndex;
                if ("complexType".equals(localName)) complexTypeIndex = currentIndex;
                currentIndex++;
            }
        }

        assertTrue(annotationIndex < importIndex);
        assertTrue(importIndex < complexTypeIndex);
    }

    @Test
    @DisplayName("Should restore import at end if it was last element")
    void testUndoRestoresAtEnd() {
        // Add import as last element
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        // Execute and undo
        command.execute();
        command.undo();

        // Verify import is restored
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());
    }

    @Test
    @DisplayName("Should support execute-undo-execute cycle")
    void testExecuteUndoExecuteCycle() {
        // Add import
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        // Execute
        assertTrue(command.execute());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());

        // Undo
        assertTrue(command.undo());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());

        // Cannot execute again with same command
        assertFalse(command.execute());
    }

    @Test
    @DisplayName("Should get description with namespace")
    void testGetDescriptionWithNamespace() {
        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("http://example.com/types"));
        assertTrue(description.contains("Remove import"));
    }

    @Test
    @DisplayName("Should get generic description without namespace")
    void testGetDescriptionWithoutNamespace() {
        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                null,
                domManipulator
        );

        String description = command.getDescription();
        assertEquals("Remove import statement", description);
    }

    @Test
    @DisplayName("Should handle schema without document element")
    void testNoSchemaRoot() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document emptyDoc = builder.newDocument();

        RemoveImportCommand command = new RemoveImportCommand(
                emptyDoc,
                "http://example.com/types",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("schema root"));
    }

    @Test
    @DisplayName("Should preserve all import attributes during undo")
    void testUndoPreservesAttributes() {
        // Add import with all attributes
        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        importElement.setAttribute("schemaLocation", "types.xsd");
        importElement.setAttribute("id", "import1");
        schemaRoot.appendChild(importElement);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        // Execute and undo
        command.execute();
        command.undo();

        // Verify all attributes preserved
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());

        Element restoredImport = (Element) imports.item(0);
        assertEquals("http://example.com/types", restoredImport.getAttribute("namespace"));
        assertEquals("types.xsd", restoredImport.getAttribute("schemaLocation"));
        assertEquals("import1", restoredImport.getAttribute("id"));
    }

    @Test
    @DisplayName("Should match first import when multiple have same namespace")
    void testRemoveFirstMatchingImport() {
        // Add two imports with same namespace (shouldn't happen but test defensive code)
        Element import1 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        import1.setAttribute("namespace", "http://example.com/types");
        import1.setAttribute("schemaLocation", "types1.xsd");
        schemaRoot.appendChild(import1);

        Element import2 = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        import2.setAttribute("namespace", "http://example.com/types");
        import2.setAttribute("schemaLocation", "types2.xsd");
        schemaRoot.appendChild(import2);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        command.execute();

        // Verify only first was removed
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());
        assertEquals("types2.xsd", ((Element) imports.item(0)).getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should handle import between other elements")
    void testRemoveImportBetweenElements() {
        // Create structure: annotation, import, include, complexType
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        schemaRoot.appendChild(annotation);

        Element importElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        importElement.setAttribute("namespace", "http://example.com/types");
        schemaRoot.appendChild(importElement);

        Element includeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        includeElement.setAttribute("schemaLocation", "common.xsd");
        schemaRoot.appendChild(includeElement);

        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        schemaRoot.appendChild(complexType);

        RemoveImportCommand command = new RemoveImportCommand(
                xsdDocument,
                "http://example.com/types",
                domManipulator
        );

        // Execute
        command.execute();

        // Verify structure: annotation, include, complexType
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include").getLength());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation").getLength());

        // Undo
        command.undo();

        // Verify import restored in correct position
        int importIndex = -1;
        int includeIndex = -1;
        int currentIndex = 0;

        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element element) {
                String localName = element.getLocalName();
                if ("import".equals(localName)) importIndex = currentIndex;
                if ("include".equals(localName)) includeIndex = currentIndex;
                currentIndex++;
            }
        }

        assertTrue(importIndex < includeIndex);
    }
}
