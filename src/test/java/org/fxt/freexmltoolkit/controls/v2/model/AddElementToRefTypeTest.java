package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddElementCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal test to verify that elements added to a referenced ComplexType
 * are correctly serialized. This test does NOT require JavaFX.
 */
class AddElementToRefTypeTest {

    @Test
    @DisplayName("Element added to referenced ComplexType should appear in serialized output")
    void testAddElementToReferencedTypeIsSerialized() {
        // Create schema structure mimicking context-sensitive-demo.xsd:
        // - document element references DocumentType
        // - DocumentType contains header element that references HeaderType
        // - HeaderType contains title and navigation elements

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
        System.out.println("Initial state:");
        System.out.println("  Schema children: " + schema.getChildren().size());
        System.out.println("  HeaderType sequence children: " + headerSequence.getChildren().size());
        System.out.println("  Schema hashCode: " + System.identityHashCode(schema));
        System.out.println("  HeaderType hashCode: " + System.identityHashCode(headerType));
        System.out.println("  HeaderSequence hashCode: " + System.identityHashCode(headerSequence));

        // Execute AddElementCommand on headerElement (simulates user right-click)
        AddElementCommand command = new AddElementCommand(headerElement, "newElement");
        boolean success = command.execute();

        assertTrue(success, "Command should execute successfully");

        // Verify element was added to HeaderType's sequence
        System.out.println("\nAfter AddElementCommand:");
        System.out.println("  HeaderType sequence children: " + headerSequence.getChildren().size());

        // The command should have added the element to HeaderType's sequence, not to headerElement
        assertEquals(0, headerElement.getChildren().size(),
                "headerElement should have no direct children (it references a type)");
        assertEquals(3, headerSequence.getChildren().size(),
                "HeaderType's sequence should have 3 children (title, navigation, newElement)");

        // Verify the new element is in the sequence
        XsdNode lastChild = headerSequence.getChildren().get(2);
        assertInstanceOf(XsdElement.class, lastChild, "Third child should be an element");
        assertEquals("newElement", lastChild.getName());

        // Now serialize the schema
        XsdSerializer serializer = new XsdSerializer();
        String serializedXml = serializer.serialize(schema);

        System.out.println("\nSerialized XML:");
        System.out.println(serializedXml);

        // Verify the new element is in the serialized output
        assertTrue(serializedXml.contains("newElement"),
                "Serialized XML should contain 'newElement'. Actual XML:\n" + serializedXml);
        assertTrue(serializedXml.contains("<xs:element name=\"newElement\""),
                "Serialized XML should contain full element declaration");

        // Count occurrences of xs:element in HeaderType section
        // Should have title, navigation, and newElement (3 elements)
        int headerTypeStart = serializedXml.indexOf("<xs:complexType name=\"HeaderType\">");
        int headerTypeEnd = serializedXml.indexOf("</xs:complexType>", headerTypeStart);
        String headerTypeSection = serializedXml.substring(headerTypeStart, headerTypeEnd);

        System.out.println("\nHeaderType section:");
        System.out.println(headerTypeSection);

        assertTrue(headerTypeSection.contains("name=\"title\""), "Should contain title element");
        assertTrue(headerTypeSection.contains("name=\"navigation\""), "Should contain navigation element");
        assertTrue(headerTypeSection.contains("name=\"newElement\""), "Should contain newElement");
    }

    @Test
    @DisplayName("Direct model manipulation should be reflected in serialization")
    void testDirectModelModification() {
        // Create a simple schema
        XsdSchema schema = new XsdSchema();

        XsdComplexType myType = new XsdComplexType("MyType");
        XsdSequence sequence = new XsdSequence();

        XsdElement elem1 = new XsdElement("elem1");
        elem1.setType("xs:string");
        sequence.addChild(elem1);

        myType.addChild(sequence);
        schema.addChild(myType);

        // Serialize before modification
        XsdSerializer serializer = new XsdSerializer();
        String beforeXml = serializer.serialize(schema);

        System.out.println("Before modification:");
        System.out.println(beforeXml);

        // Directly add an element to the sequence
        XsdElement elem2 = new XsdElement("elem2");
        elem2.setType("xs:string");
        sequence.addChild(elem2);

        // Verify sequence has 2 children
        assertEquals(2, sequence.getChildren().size(), "Sequence should have 2 children");

        // Serialize after modification
        String afterXml = serializer.serialize(schema);

        System.out.println("\nAfter modification:");
        System.out.println(afterXml);

        // Verify both elements are in the output
        assertTrue(afterXml.contains("name=\"elem1\""), "Should contain elem1");
        assertTrue(afterXml.contains("name=\"elem2\""), "Should contain elem2");
    }
}
