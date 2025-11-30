package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that type-level documentation is correctly parsed, accessible, and preserved during round-trip.
 */
class TypeDocumentationTest {

    @Test
    void testTypeDocumentationParsing() throws Exception {
        // Load the test XSD that has type-level documentation
        Path xsdPath = Paths.get("src/test/resources/roundtrip/10_deep_nesting_recursive.xsd");
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdPath);

        assertNotNull(schema, "Schema should not be null");

        // Find FullyAnnotatedType which has type-level documentation
        XsdComplexType fullyAnnotatedType = null;
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType ct && "FullyAnnotatedType".equals(ct.getName())) {
                fullyAnnotatedType = ct;
                break;
            }
        }

        assertNotNull(fullyAnnotatedType, "FullyAnnotatedType should exist in the schema");

        // Check documentation
        String doc = fullyAnnotatedType.getDocumentation();
        System.out.println("FullyAnnotatedType.getDocumentation() = '" + doc + "'");
        System.out.println("FullyAnnotatedType.hasDocumentations() = " + fullyAnnotatedType.hasDocumentations());
        System.out.println("FullyAnnotatedType.getDocumentations().size() = " + fullyAnnotatedType.getDocumentations().size());

        // The documentation should contain "Type-level documentation"
        assertNotNull(doc, "Documentation should not be null");
        assertTrue(doc.contains("Type-level documentation"),
            "Documentation should contain 'Type-level documentation', but was: '" + doc + "'");
    }

    @Test
    void testAllTypesDocumentation() throws Exception {
        Path xsdPath = Paths.get("src/test/resources/roundtrip/10_deep_nesting_recursive.xsd");
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdPath);

        System.out.println("\n=== All Types Documentation ===");
        int typesWithDoc = 0;
        int typesWithoutDoc = 0;

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType ct) {
                String doc = ct.getDocumentation();
                if (doc != null && !doc.isEmpty()) {
                    System.out.println("ComplexType '" + ct.getName() + "': " + doc.substring(0, Math.min(50, doc.length())));
                    typesWithDoc++;
                } else {
                    typesWithoutDoc++;
                }
            } else if (child instanceof XsdSimpleType st) {
                String doc = st.getDocumentation();
                if (doc != null && !doc.isEmpty()) {
                    System.out.println("SimpleType '" + st.getName() + "': " + doc.substring(0, Math.min(50, doc.length())));
                    typesWithDoc++;
                } else {
                    typesWithoutDoc++;
                }
            }
        }

        System.out.println("\nTypes with documentation: " + typesWithDoc);
        System.out.println("Types without documentation: " + typesWithoutDoc);

        // At least FullyAnnotatedType should have documentation
        assertTrue(typesWithDoc >= 1, "At least one type should have documentation");
    }

    @Test
    void testTypeDocumentationRoundTrip() throws Exception {
        // Load the test XSD that has type-level documentation
        Path xsdPath = Paths.get("src/test/resources/roundtrip/10_deep_nesting_recursive.xsd");
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema original = factory.fromFile(xsdPath);

        // Get original documentation
        XsdComplexType originalType = null;
        for (XsdNode child : original.getChildren()) {
            if (child instanceof XsdComplexType ct && "FullyAnnotatedType".equals(ct.getName())) {
                originalType = ct;
                break;
            }
        }
        assertNotNull(originalType, "FullyAnnotatedType should exist");
        String originalDoc = originalType.getDocumentation();
        assertNotNull(originalDoc, "Original documentation should not be null");

        System.out.println("Original documentation: " + originalDoc);

        // Serialize
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(original);

        System.out.println("\n--- Checking serialized output for FullyAnnotatedType ---");
        int startIndex = serialized.indexOf("FullyAnnotatedType");
        if (startIndex >= 0) {
            int endIndex = Math.min(startIndex + 300, serialized.length());
            System.out.println(serialized.substring(startIndex, endIndex));
        }

        // The serialized output should contain the documentation
        assertTrue(serialized.contains("Type-level documentation"),
            "Serialized XSD should contain 'Type-level documentation'");

        // Re-parse
        XsdNodeFactory factory2 = new XsdNodeFactory();
        XsdSchema roundTripped = factory2.fromString(serialized);

        // Get round-tripped documentation
        XsdComplexType roundTrippedType = null;
        for (XsdNode child : roundTripped.getChildren()) {
            if (child instanceof XsdComplexType ct && "FullyAnnotatedType".equals(ct.getName())) {
                roundTrippedType = ct;
                break;
            }
        }
        assertNotNull(roundTrippedType, "FullyAnnotatedType should exist after round-trip");
        String roundTrippedDoc = roundTrippedType.getDocumentation();

        System.out.println("Round-tripped documentation: " + roundTrippedDoc);

        // Documentation should be preserved
        assertNotNull(roundTrippedDoc, "Round-tripped documentation should not be null");
        assertEquals(originalDoc, roundTrippedDoc, "Documentation should be preserved during round-trip");
    }
}
