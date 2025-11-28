package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that elements added to a ComplexType's sequence are correctly serialized.
 * This tests the scenario where an element references a named type (e.g., header type="HeaderType")
 * and a child element is added through the context menu.
 */
class AddElementToReferencedTypeTest {

    @Test
    @DisplayName("Adding element to referenced ComplexType should be serialized correctly")
    void testAddElementToReferencedType() {
        // Create a schema structure like context-sensitive-demo.xsd
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/demo");

        // Create global element "document" that references "DocumentType"
        XsdElement documentElement = new XsdElement("document");
        documentElement.setType("DocumentType");
        schema.addChild(documentElement);

        // Create DocumentType with a sequence containing "header"
        XsdComplexType documentType = new XsdComplexType("DocumentType");
        XsdSequence docSequence = new XsdSequence();

        XsdElement headerElement = new XsdElement("header");
        headerElement.setType("HeaderType");
        docSequence.addChild(headerElement);

        documentType.addChild(docSequence);
        schema.addChild(documentType);

        // Create HeaderType with a sequence containing "title" and "navigation"
        XsdComplexType headerType = new XsdComplexType("HeaderType");
        XsdSequence headerSequence = new XsdSequence();

        XsdElement titleElement = new XsdElement("title");
        titleElement.setType("xs:string");
        headerSequence.addChild(titleElement);

        XsdElement navElement = new XsdElement("navigation");
        navElement.setType("xs:string");
        headerSequence.addChild(navElement);

        headerType.addChild(headerSequence);
        schema.addChild(headerType);

        // Verify initial state
        assertEquals(3, schema.getChildren().size(), "Schema should have 3 children (document, DocumentType, HeaderType)");
        assertEquals(2, headerSequence.getChildren().size(), "HeaderType sequence should have 2 children");

        // Now simulate what AddElementCommand does when adding to 'header'
        // It navigates up from header element to find HeaderType

        // Navigate up to schema
        XsdNode current = headerElement.getParent();
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }
        assertInstanceOf(XsdSchema.class, current, "Should navigate up to schema");

        // Find HeaderType by name (like AddElementCommand does)
        XsdComplexType foundHeaderType = null;
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType ct && "HeaderType".equals(ct.getName())) {
                foundHeaderType = ct;
                break;
            }
        }

        assertNotNull(foundHeaderType, "Should find HeaderType in schema");
        assertSame(headerType, foundHeaderType, "Found HeaderType should be the same instance");

        // Find the sequence in HeaderType
        XsdSequence foundSequence = null;
        for (XsdNode child : foundHeaderType.getChildren()) {
            if (child instanceof XsdSequence seq) {
                foundSequence = seq;
                break;
            }
        }

        assertNotNull(foundSequence, "Should find sequence in HeaderType");
        assertSame(headerSequence, foundSequence, "Found sequence should be the same instance");

        // Add new element (like AddElementCommand does)
        XsdElement newElement = new XsdElement("newElement");
        newElement.setType("xs:string");
        foundSequence.addChild(newElement);

        // Verify element was added to the model
        assertEquals(3, headerSequence.getChildren().size(), "HeaderType sequence should now have 3 children");
        assertEquals(3, foundSequence.getChildren().size(), "Found sequence should now have 3 children");

        // Serialize the schema
        XsdSerializer serializer = new XsdSerializer();
        String serializedXml = serializer.serialize(schema);

        // Verify the new element is in the serialized XML
        System.out.println("Serialized XML:");
        System.out.println(serializedXml);

        assertTrue(serializedXml.contains("newElement"),
                "Serialized XML should contain 'newElement'. Actual XML:\n" + serializedXml);
        assertTrue(serializedXml.contains("<xs:element name=\"newElement\""),
                "Serialized XML should contain full element declaration");
    }

    @Test
    @DisplayName("AddElementCommand should correctly add element to referenced ComplexType")
    void testAddElementCommandToReferencedType() {
        // Create schema
        XsdSchema schema = new XsdSchema();

        // Create global element that references a type
        XsdElement headerElement = new XsdElement("header");
        headerElement.setType("HeaderType");

        // Create DocumentType containing the header element
        XsdComplexType documentType = new XsdComplexType("DocumentType");
        XsdSequence docSequence = new XsdSequence();
        docSequence.addChild(headerElement);
        documentType.addChild(docSequence);
        schema.addChild(documentType);

        // Create HeaderType
        XsdComplexType headerType = new XsdComplexType("HeaderType");
        XsdSequence headerSequence = new XsdSequence();

        XsdElement titleElement = new XsdElement("title");
        titleElement.setType("xs:string");
        headerSequence.addChild(titleElement);

        headerType.addChild(headerSequence);
        schema.addChild(headerType);

        // Execute AddElementCommand
        AddElementCommand command = new AddElementCommand(headerElement, "newElement");
        boolean success = command.execute();

        assertTrue(success, "Command should execute successfully");

        // Verify element was added to HeaderType's sequence (not to headerElement directly)
        assertEquals(0, headerElement.getChildren().size(),
                "headerElement should have no direct children (it references a type)");
        assertEquals(2, headerSequence.getChildren().size(),
                "HeaderType's sequence should have 2 children (title + newElement)");

        // Find the new element in the sequence
        XsdNode addedNode = headerSequence.getChildren().get(1);
        assertInstanceOf(XsdElement.class, addedNode, "Added node should be an element");
        assertEquals("newElement", addedNode.getName(), "Element name should be 'newElement'");

        // Serialize and verify
        XsdSerializer serializer = new XsdSerializer();
        String serializedXml = serializer.serialize(schema);

        System.out.println("Serialized XML after AddElementCommand:");
        System.out.println(serializedXml);

        assertTrue(serializedXml.contains("newElement"),
                "Serialized XML should contain 'newElement'");
    }
}
