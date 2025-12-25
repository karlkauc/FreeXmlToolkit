package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdVisualTreeBuilder with Fund element that has type reference and inline identity constraints.
 */
@DisplayName("XsdVisualTreeBuilder Fund Element Tests")
class XsdVisualTreeBuilderFundTest {

    @Test
    @DisplayName("Fund element with type reference should have visual children from resolved type")
    void testFundElementWithTypeReference() throws Exception {
        // Load the test schema from test resources
        java.net.URL resourceUrl = getClass().getClassLoader().getResource("demo-xsd/test-fund-simple.xsd");
        assertNotNull(resourceUrl, "Test file demo-xsd/test-fund-simple.xsd should exist in test resources");
        File xsdFile = new File(resourceUrl.toURI());
        assertTrue(xsdFile.exists(), "Test file test-fund-simple.xsd should exist");

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(xsdFile);
        assertNotNull(schema, "Schema should be loaded");
        
        // Build visual tree
        XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
        VisualNode root = builder.buildFromSchema(schema);
        
        assertNotNull(root, "Root visual node should be created");
        System.out.println("Root: " + root.getLabel() + " with " + root.getChildren().size() + " children");
        
        // Root element should have children (sequence compositor)
        assertTrue(root.getChildren().size() > 0, "Root should have children");
        
        // Find the sequence compositor
        VisualNode sequence = root.getChildren().get(0);
        System.out.println("First child: " + sequence.getLabel() + " with " + sequence.getChildren().size() + " children");
        
        // Find Fund element within sequence
        VisualNode fundNode = null;
        for (VisualNode child : sequence.getChildren()) {
            if ("Fund".equals(child.getLabel())) {
                fundNode = child;
                break;
            }
        }
        
        assertNotNull(fundNode, "Fund element should be found in visual tree");
        System.out.println("Fund node: " + fundNode.getLabel() + " with " + fundNode.getChildren().size() + " children");
        
        // Fund element should have children from resolved FundType
        assertTrue(fundNode.getChildren().size() > 0, 
                "Fund element should have children from resolved FundType. " +
                "Expected at least a sequence compositor with Identifiers, Names, Currency elements.");
        
        // Print full tree for debugging
        printTree(fundNode, 0);
    }
    
    private void printTree(VisualNode node, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + node.getLabel() + " (" + node.getChildren().size() + " children)");
        for (VisualNode child : node.getChildren()) {
            printTree(child, depth + 1);
        }
    }
}
