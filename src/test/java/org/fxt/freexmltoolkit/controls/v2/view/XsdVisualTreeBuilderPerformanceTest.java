package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode; // Import VisualNode
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class XsdVisualTreeBuilderPerformanceTest {

    private XsdVisualTreeBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new XsdVisualTreeBuilder();
        XsdVisualTreeBuilder.invalidateCache(); // Ensure a clean cache for each test
    }

    private XsdSchema createLargeSchema(int depth, int childrenPerNode) {
        XsdSchema schema = new XsdSchema(); // Use default constructor
        schema.setTargetNamespace("http://example.com/large"); // Set target namespace
        // The "name" of the schema is implicitly "schema" based on its superclass XsdNode constructor.

        XsdComplexType rootType = new XsdComplexType("RootType");
        schema.addChild(rootType);

        XsdElement rootElement = new XsdElement("RootElement");
        rootElement.setType("RootType");
        schema.addChild(rootElement);

        buildRecursiveComplexType(rootType, depth, childrenPerNode, 0, schema); // Pass schema to add types
        return schema;
    }

    private void buildRecursiveComplexType(XsdComplexType parentType, int maxDepth, int childrenPerNode, int currentDepth, XsdSchema schema) {
        if (currentDepth >= maxDepth) {
            return;
        }

        XsdSequence sequence = new XsdSequence();
        parentType.addChild(sequence);

        for (int i = 0; i < childrenPerNode; i++) {
            XsdElement element = new XsdElement("Element_" + currentDepth + "_" + i);
            sequence.addChild(element);

            if (currentDepth + 1 < maxDepth) {
                // Create a new complex type for recursion
                XsdComplexType childComplexType = new XsdComplexType("Type_" + (currentDepth + 1) + "_" + i);
                schema.addChild(childComplexType); // Add child complex type to schema so it can be resolved
                element.setType(childComplexType.getName());
                buildRecursiveComplexType(childComplexType, maxDepth, childrenPerNode, currentDepth + 1, schema);
            } else {
                element.setType("xs:string");
            }
        }
    }

    @Test
    @DisplayName("Performance test for building a large, deeply nested visual tree")
    void testBuildLargeDeeplyNestedTreePerformance() {
        int depth = 10;
        int childrenPerNode = 3; 
        XsdSchema largeSchema = createLargeSchema(depth, childrenPerNode);

        // Define an acceptable time limit (e.g., 5 seconds for a complex schema)
        // This threshold might need adjustment based on typical CI/dev environment performance.
        // Expect this to fail (timeout) in the Red Phase
        Duration timeLimit = Duration.ofSeconds(1); 

        assertTimeoutPreemptively(timeLimit, () -> {
            VisualNode rootNode = builder.buildFromSchema(largeSchema);
            assertNotNull(rootNode, "Root visual node should not be null");
            // Further assertions could verify basic structure or node count if needed
            // For now, the main assertion is that it completes within the time limit
        }, "Building large deeply nested schema visual tree took too long");
    }

    @Test
    @DisplayName("Performance test for building a wide schema visual tree (many top-level elements)")
    void testBuildWideSchemaPerformance() {
        XsdSchema wideSchema = new XsdSchema(); // Use default constructor
        wideSchema.setTargetNamespace("http://example.com/wide"); // Set target namespace
        int numElements = 1000; 

        for (int i = 0; i < numElements; i++) {
            XsdElement element = new XsdElement("GlobalElement_" + i);
            element.setType("xs:string");
            wideSchema.addChild(element);
        }

        // Define an acceptable time limit
        // Expect this to fail (timeout) in the Red Phase
        Duration timeLimit = Duration.ofSeconds(1); 

        assertTimeoutPreemptively(timeLimit, () -> {
            VisualNode rootNode = builder.buildFromSchema(wideSchema);
            assertNotNull(rootNode, "Root visual node should not be null");
            // If numElements is 1, rootNode is the element. If >1, it's a schema wrapper.
            // XsdVisualTreeBuilder wraps multiple global elements in a schema node.
            assertTrue(rootNode.getLabel().startsWith("Schema:"), "Root node should be a schema wrapper");
            assertEquals(numElements, rootNode.getChildren().size(), "Schema wrapper should contain all global elements");
        }, "Building wide schema visual tree took too long");
    }
}