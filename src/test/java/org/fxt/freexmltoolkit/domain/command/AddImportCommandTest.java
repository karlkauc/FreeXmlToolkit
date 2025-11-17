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
 * Unit tests for AddImportCommand.
 * Tests adding import statements to XSD schemas with proper positioning and duplicate detection.
 */
class AddImportCommandTest {

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
    @DisplayName("Should execute and add import with namespace and schemaLocation")
    void testExecuteAddImport() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        // Verify import was added
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());

        Element importElement = (Element) imports.item(0);
        assertEquals("http://example.com/types", importElement.getAttribute("namespace"));
        assertEquals("types.xsd", importElement.getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should add import with namespace only")
    void testExecuteAddImportNamespaceOnly() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                null,
                domManipulator
        );

        assertTrue(command.execute());

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());

        Element importElement = (Element) imports.item(0);
        assertEquals("http://example.com/types", importElement.getAttribute("namespace"));
        assertFalse(importElement.hasAttribute("schemaLocation") &&
                   !importElement.getAttribute("schemaLocation").isEmpty());
    }

    @Test
    @DisplayName("Should add import with schemaLocation only")
    void testExecuteAddImportSchemaLocationOnly() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                null,
                "types.xsd",
                domManipulator
        );

        assertTrue(command.execute());

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());

        Element importElement = (Element) imports.item(0);
        assertEquals("types.xsd", importElement.getAttribute("schemaLocation"));
    }

    @Test
    @DisplayName("Should add import with empty strings")
    void testExecuteAddImportEmptyStrings() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "",
                "",
                domManipulator
        );

        assertTrue(command.execute());

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());
    }

    @Test
    @DisplayName("Should fail when import already exists")
    void testExecuteFailsWhenImportExists() {
        // Add first import
        Element existingImport = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");
        existingImport.setAttribute("namespace", "http://example.com/types");
        schemaRoot.appendChild(existingImport);

        // Try to add duplicate
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command::execute);
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should insert import before complexType")
    void testInsertBeforeComplexType() {
        // Add a complex type to schema
        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        command.execute();

        // Verify import is before complexType
        NodeList children = schemaRoot.getChildNodes();
        Element firstElement = null;
        Element secondElement = null;

        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                if (firstElement == null) {
                    firstElement = (Element) children.item(i);
                } else if (secondElement == null) {
                    secondElement = (Element) children.item(i);
                    break;
                }
            }
        }

        assertNotNull(firstElement);
        assertNotNull(secondElement);
        assertEquals("import", firstElement.getLocalName());
        assertEquals("complexType", secondElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert import before simpleType")
    void testInsertBeforeSimpleType() {
        Element simpleType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:simpleType");
        simpleType.setAttribute("name", "StringType");
        schemaRoot.appendChild(simpleType);

        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        command.execute();

        // Find elements
        Element firstElement = null;
        for (int i = 0; i < schemaRoot.getChildNodes().getLength(); i++) {
            if (schemaRoot.getChildNodes().item(i) instanceof Element) {
                firstElement = (Element) schemaRoot.getChildNodes().item(i);
                break;
            }
        }

        assertNotNull(firstElement);
        assertEquals("import", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert import before element")
    void testInsertBeforeElement() {
        Element element = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", "person");
        schemaRoot.appendChild(element);

        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
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
        assertEquals("import", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert import after annotation")
    void testInsertAfterAnnotation() {
        Element annotation = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:annotation");
        schemaRoot.appendChild(annotation);

        Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");
        schemaRoot.appendChild(complexType);

        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        command.execute();

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
    @DisplayName("Should append import at end if no type definitions exist")
    void testAppendAtEnd() {
        // Empty schema (except root)
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        command.execute();

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());
    }

    @Test
    @DisplayName("Should undo import addition")
    void testUndo() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        command.execute();

        // Verify import exists
        NodeList importsBeforeUndo = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, importsBeforeUndo.getLength());

        // Undo
        assertTrue(command.undo());

        // Verify import removed
        NodeList importsAfterUndo = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(0, importsAfterUndo.getLength());
    }

    @Test
    @DisplayName("Should not undo if not executed")
    void testUndoWithoutExecute() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        assertFalse(command.undo());
    }

    @Test
    @DisplayName("Should not execute twice")
    void testExecuteTwice() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        assertTrue(command.execute());
        assertFalse(command.execute());

        // Verify only one import exists
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(1, imports.getLength());
    }

    @Test
    @DisplayName("Should support execute-undo-execute cycle")
    void testExecuteUndoExecuteCycle() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        // Execute
        assertTrue(command.execute());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());

        // Undo
        assertTrue(command.undo());
        assertEquals(0, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());

        // Execute again - should work since we create new command
        AddImportCommand command2 = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );
        assertTrue(command2.execute());
        assertEquals(1, schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").getLength());
    }

    @Test
    @DisplayName("Should get description with namespace")
    void testGetDescriptionWithNamespace() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
                domManipulator
        );

        String description = command.getDescription();
        assertTrue(description.contains("http://example.com/types"));
        assertTrue(description.contains("import"));
    }

    @Test
    @DisplayName("Should get generic description without namespace")
    void testGetDescriptionWithoutNamespace() {
        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                null,
                "types.xsd",
                domManipulator
        );

        String description = command.getDescription();
        assertEquals("Add schema import", description);
    }

    @Test
    @DisplayName("Should handle multiple imports with different namespaces")
    void testMultipleImports() {
        AddImportCommand command1 = new AddImportCommand(
                xsdDocument,
                "http://example.com/types1",
                "types1.xsd",
                domManipulator
        );

        AddImportCommand command2 = new AddImportCommand(
                xsdDocument,
                "http://example.com/types2",
                "types2.xsd",
                domManipulator
        );

        assertTrue(command1.execute());
        assertTrue(command2.execute());

        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");
        assertEquals(2, imports.getLength());
    }

    @Test
    @DisplayName("Should detect duplicate empty namespace imports")
    void testDuplicateEmptyNamespace() {
        // Add first import with empty namespace
        AddImportCommand command1 = new AddImportCommand(
                xsdDocument,
                "",
                "types1.xsd",
                domManipulator
        );
        command1.execute();

        // Try to add second import with empty namespace
        AddImportCommand command2 = new AddImportCommand(
                xsdDocument,
                null,
                "types2.xsd",
                domManipulator
        );

        RuntimeException exception = assertThrows(RuntimeException.class, command2::execute);
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should handle schema without document element")
    void testNoSchemaRoot() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document emptyDoc = builder.newDocument();

        AddImportCommand command = new AddImportCommand(
                emptyDoc,
                "http://example.com/types",
                "types.xsd",
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

        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
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
        assertEquals("import", firstElement.getLocalName());
    }

    @Test
    @DisplayName("Should insert before attributeGroup element")
    void testInsertBeforeAttributeGroup() {
        Element attrGroup = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:attributeGroup");
        attrGroup.setAttribute("name", "PersonAttributes");
        schemaRoot.appendChild(attrGroup);

        AddImportCommand command = new AddImportCommand(
                xsdDocument,
                "http://example.com/types",
                "types.xsd",
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
        assertEquals("import", firstElement.getLocalName());
    }
}
