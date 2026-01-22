package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to debug and verify the resolution of element references from imported schemas.
 * Specifically tests the ds:Signature element from xmldsig-core-schema.xsd.
 */
class ImportedSchemaRefResolutionTest {

    private XsdNodeFactory factory;

    @BeforeEach
    void setUp() {
        factory = new XsdNodeFactory();
    }

    @Test
    void testImportedSchemaElementIndexing() throws Exception {
        // Load the FundsXML4.xsd which imports xmldsig-core-schema.xsd
        Path xsdPath = Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");
        XsdSchema schema = factory.fromFile(xsdPath);

        assertNotNull(schema, "Schema should be loaded");

        // Get the imported schemas
        Map<String, XsdSchema> importedSchemas = factory.getImportedSchemas();

        System.out.println("=== Imported Schemas ===");
        System.out.println("Number of imported schemas: " + importedSchemas.size());
        for (Map.Entry<String, XsdSchema> entry : importedSchemas.entrySet()) {
            System.out.println("  - " + entry.getKey());
            XsdSchema importedSchema = entry.getValue();
            System.out.println("    Children count: " + importedSchema.getChildren().size());

            // List all global elements in the imported schema
            System.out.println("    Global elements:");
            for (XsdNode child : importedSchema.getChildren()) {
                if (child instanceof XsdElement element) {
                    System.out.println("      - " + element.getName() +
                        " (type: " + element.getType() + ", ref: " + element.getRef() + ")");
                }
            }

            // List all complex types in the imported schema
            System.out.println("    Complex types:");
            for (XsdNode child : importedSchema.getChildren()) {
                if (child instanceof XsdComplexType ct) {
                    System.out.println("      - " + ct.getName() + " (children: " + ct.getChildren().size() + ")");
                }
            }
        }

        // Check if the xmldsig namespace is imported
        String xmldsigNamespace = "http://www.w3.org/2000/09/xmldsig#";
        assertTrue(importedSchemas.containsKey(xmldsigNamespace),
            "Should have imported xmldsig schema with namespace: " + xmldsigNamespace);

        XsdSchema xmldsigSchema = importedSchemas.get(xmldsigNamespace);
        assertNotNull(xmldsigSchema, "xmldsig schema should not be null");

        // Find the Signature element
        XsdElement signatureElement = null;
        for (XsdNode child : xmldsigSchema.getChildren()) {
            if (child instanceof XsdElement element && "Signature".equals(element.getName())) {
                signatureElement = element;
                break;
            }
        }
        assertNotNull(signatureElement, "Should find Signature element in xmldsig schema");
        assertEquals("ds:SignatureType", signatureElement.getType(),
            "Signature element should have type ds:SignatureType");

        // Find the SignatureType complex type
        XsdComplexType signatureType = null;
        for (XsdNode child : xmldsigSchema.getChildren()) {
            if (child instanceof XsdComplexType ct && "SignatureType".equals(ct.getName())) {
                signatureType = ct;
                break;
            }
        }
        assertNotNull(signatureType, "Should find SignatureType complex type");

        System.out.println("\n=== SignatureType structure ===");
        printNodeStructure(signatureType, 0);

        // Check that SignatureType has children (should have sequence with element refs)
        assertFalse(signatureType.getChildren().isEmpty(),
            "SignatureType should have children");
    }

    @Test
    void testVisualTreeBuildWithImports() throws Exception {
        // Load the FundsXML4.xsd
        Path xsdPath = Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");
        XsdSchema schema = factory.fromFile(xsdPath);
        Map<String, XsdSchema> importedSchemas = factory.getImportedSchemas();

        System.out.println("=== FundsXML4.xsd loaded ===");
        System.out.println("Main schema children: " + schema.getChildren().size());
        System.out.println("Imported schemas count: " + importedSchemas.size());

        // Verify the xmldsig import was loaded
        String xmldsigNamespace = "http://www.w3.org/2000/09/xmldsig#";
        assertTrue(importedSchemas.containsKey(xmldsigNamespace),
            "Should have imported xmldsig schema");

        XsdSchema xmldsigSchema = importedSchemas.get(xmldsigNamespace);
        assertNotNull(xmldsigSchema, "xmldsig schema should not be null");

        // Verify xmldsig schema has content
        long xmldsigElementCount = xmldsigSchema.getChildren().stream()
            .filter(c -> c instanceof XsdElement).count();
        assertTrue(xmldsigElementCount > 0, "xmldsig schema should have elements");
        System.out.println("xmldsig schema has " + xmldsigElementCount + " elements");

        // Invalidate cache to ensure fresh build
        XsdVisualTreeBuilder.invalidateCache();

        // Build the visual tree with imported schemas
        XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
        VisualNode rootNode = builder.buildFromSchema(schema, null, importedSchemas);

        assertNotNull(rootNode, "Root node should not be null");

        System.out.println("\n=== Visual Tree (partial) ===");
        printVisualTreeStructure(rootNode, 0, 3); // Print only 3 levels

        // Find the ds:Signature element in the visual tree
        VisualNode signatureNode = findNodeByLabel(rootNode, "ds:Signature");

        System.out.println("\n=== Looking for ds:Signature ===");
        if (signatureNode == null) {
            // Try just "Signature"
            signatureNode = findNodeByLabel(rootNode, "Signature");
        }

        if (signatureNode != null) {
            System.out.println("Found Signature node: " + signatureNode.getLabel());
            System.out.println("Children count: " + signatureNode.getChildren().size());
            System.out.println("Children:");
            for (VisualNode child : signatureNode.getChildren()) {
                System.out.println("  - " + child.getLabel() + " (" + child.getType() + ")");
                // Print grandchildren too
                for (VisualNode grandchild : child.getChildren()) {
                    System.out.println("    - " + grandchild.getLabel() + " (" + grandchild.getType() + ")");
                }
            }

            // The Signature element should have children (from SignatureType)
            assertFalse(signatureNode.getChildren().isEmpty(),
                "Signature node should have children from SignatureType (sequence with SignedInfo, SignatureValue, KeyInfo, Object)");

            // Verify the sequence has the expected elements
            assertTrue(signatureNode.getChildren().stream()
                .anyMatch(c -> "sequence".equals(c.getLabel())),
                "Signature should have a sequence child");

            VisualNode sequenceNode = signatureNode.getChildren().stream()
                .filter(c -> "sequence".equals(c.getLabel()))
                .findFirst().orElse(null);

            if (sequenceNode != null) {
                System.out.println("\nSequence children:");
                for (VisualNode seqChild : sequenceNode.getChildren()) {
                    System.out.println("  - " + seqChild.getLabel());
                }

                // The sequence should have children (SignedInfo, SignatureValue, KeyInfo, Object)
                assertFalse(sequenceNode.getChildren().isEmpty(),
                    "Sequence should have element children (SignedInfo, SignatureValue, KeyInfo, Object)");
            }
        } else {
            System.out.println("Signature node NOT FOUND in visual tree (may not be in the main schema tree)");
        }
    }

    @Test
    void testElementRefResolutionInImportedSchema() throws Exception {
        // Load just the xmldsig schema directly
        Path xsdPath = Path.of("src/test/resources/schema/include_files/xmldsig-core-schema.xsd");
        XsdSchema xmldsigSchema = factory.fromFile(xsdPath);

        System.out.println("=== Direct load of xmldsig schema ===");
        System.out.println("Children count: " + xmldsigSchema.getChildren().size());

        // Verify that elements and types were parsed
        long elementCount = xmldsigSchema.getChildren().stream()
            .filter(c -> c instanceof XsdElement).count();
        long typeCount = xmldsigSchema.getChildren().stream()
            .filter(c -> c instanceof XsdComplexType || c instanceof XsdSimpleType).count();

        System.out.println("Element count: " + elementCount);
        System.out.println("Type count: " + typeCount);

        assertTrue(elementCount > 0, "Should have parsed elements from xmldsig schema");
        assertTrue(typeCount > 0, "Should have parsed types from xmldsig schema");

        // Build visual tree from xmldsig schema directly
        XsdVisualTreeBuilder.invalidateCache();
        XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
        VisualNode rootNode = builder.buildFromSchema(xmldsigSchema, null, null);

        System.out.println("\n=== Visual Tree from xmldsig ===");
        printVisualTreeStructure(rootNode, 0, 5);

        // Find Signature element
        VisualNode signatureNode = findNodeByLabel(rootNode, "Signature");
        assertNotNull(signatureNode, "Signature element should be found in visual tree");

        System.out.println("\n=== Signature node details ===");
        System.out.println("Label: " + signatureNode.getLabel());
        System.out.println("Detail: " + signatureNode.getDetail());
        System.out.println("Children count: " + signatureNode.getChildren().size());
        printVisualTreeStructure(signatureNode, 0, 4);

        // Verify that Signature has children (from SignatureType)
        // SignatureType has a sequence with 4 element refs: SignedInfo, SignatureValue, KeyInfo, Object
        assertFalse(signatureNode.getChildren().isEmpty(),
            "Signature node should have children from SignatureType");

        // The first child should be a sequence compositor
        assertTrue(signatureNode.getChildren().stream()
            .anyMatch(c -> "sequence".equals(c.getLabel())),
            "Signature should have a sequence child from SignatureType");
    }

    @Test
    void testIncludeDirectivesNotDuplicated() throws Exception {
        // Load the FundsXML4.xsd which includes multiple files that themselves include FundsXML4_Core.xsd
        Path xsdPath = Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");
        XsdSchema schema = factory.fromFile(xsdPath);

        assertNotNull(schema, "Schema should be loaded");

        // Count include directives
        Map<String, Integer> includeCountByLocation = new java.util.HashMap<>();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdInclude include) {
                String location = include.getSchemaLocation();
                includeCountByLocation.merge(location, 1, Integer::sum);
            }
        }

        System.out.println("=== Include directives in schema ===");
        for (Map.Entry<String, Integer> entry : includeCountByLocation.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " time(s)");
        }

        // Verify that each include appears only once
        for (Map.Entry<String, Integer> entry : includeCountByLocation.entrySet()) {
            assertEquals(1, entry.getValue(),
                "Include directive for '" + entry.getKey() + "' should appear exactly once, but appeared " + entry.getValue() + " times");
        }

        // Also verify the total number of includes matches what's expected from the original file
        // FundsXML4.xsd has these includes in the original file:
        // - FundsXML4_AssetMasterData.xsd
        // - FundsXML4_AssetMgmtCompDynData.xsd
        // - FundsXML4_Core.xsd
        // - FundsXML4_CountrySpecificData.xsd
        // - FundsXML4_FundDynamicData.xsd
        // - FundsXML4_FundStaticData.xsd
        // - FundsXML4_PortfolioData.xsd
        // - FundsXML4_RegulatoryReporting_EET.xsd
        // ... and more

        long totalIncludes = schema.getChildren().stream()
            .filter(c -> c instanceof XsdInclude)
            .count();

        System.out.println("\nTotal include directives: " + totalIncludes);

        // The original FundsXML4.xsd should have around 20 includes
        // We don't want duplicates from included files
        assertTrue(totalIncludes <= 25,
            "Should not have excessive include directives (expected <= 25, got " + totalIncludes + "). " +
            "This might indicate that includes from included files are being duplicated.");
    }

    private void printNodeStructure(XsdNode node, int depth) {
        String indent = "  ".repeat(depth);
        String name = node.getName() != null ? node.getName() : "(unnamed)";
        String type = node.getClass().getSimpleName();

        StringBuilder info = new StringBuilder();
        info.append(indent).append(type).append(": ").append(name);

        if (node instanceof XsdElement element) {
            if (element.getType() != null) info.append(" type=").append(element.getType());
            if (element.getRef() != null) info.append(" ref=").append(element.getRef());
        }

        System.out.println(info);

        for (XsdNode child : node.getChildren()) {
            printNodeStructure(child, depth + 1);
        }
    }

    private void printVisualTreeStructure(VisualNode node, int depth, int maxDepth) {
        if (depth > maxDepth) return;

        String indent = "  ".repeat(depth);
        System.out.println(indent + node.getLabel() + " (" + node.getType() + ") - " +
            node.getChildren().size() + " children");

        for (VisualNode child : node.getChildren()) {
            printVisualTreeStructure(child, depth + 1, maxDepth);
        }
    }

    private VisualNode findNodeByLabel(VisualNode node, String label) {
        if (node.getLabel().equals(label) || node.getLabel().contains(label)) {
            return node;
        }
        for (VisualNode child : node.getChildren()) {
            VisualNode found = findNodeByLabel(child, label);
            if (found != null) return found;
        }
        return null;
    }
}
