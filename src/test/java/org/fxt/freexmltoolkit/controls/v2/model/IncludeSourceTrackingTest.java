package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for source tracking of nodes from included files.
 * Verifies that nodes parsed from xs:include files have correct sourceInfo set.
 */
class IncludeSourceTrackingTest {

    private static final Path TEST_SCHEMA_PATH = Paths.get(
        "src/test/resources/schema/include_files/FundsXML4.xsd"
    ).toAbsolutePath();

    @Test
    void testSourceInfoSetForNodesFromIncludedFiles() throws Exception {
        // Parse the schema with includes
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(TEST_SCHEMA_PATH);

        assertNotNull(schema, "Schema should be parsed successfully");

        // The schema should have the main schema path set
        assertEquals(TEST_SCHEMA_PATH.normalize(), schema.getMainSchemaPath().normalize(),
            "Main schema path should match");

        // Find the AddressType which is defined in FundsXML4_AssetMasterData.xsd
        XsdComplexType addressType = null;
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType ct && "AddressType".equals(ct.getName())) {
                addressType = ct;
                break;
            }
        }

        // AddressType might not exist in this schema - let's find any type from an included file
        XsdNode nodeFromInclude = null;
        String expectedIncludeFile = null;

        for (XsdNode child : schema.getChildren()) {
            IncludeSourceInfo sourceInfo = child.getSourceInfo();
            if (sourceInfo != null && sourceInfo.isFromInclude()) {
                nodeFromInclude = child;
                expectedIncludeFile = sourceInfo.getFileName();
                System.out.println("Found node from include: " + child.getName() +
                    " (" + child.getClass().getSimpleName() + ") from file: " + expectedIncludeFile);
                break;
            }
        }

        // Verify we found at least one node from an included file
        assertNotNull(nodeFromInclude,
            "Should find at least one node from an included file");

        // Verify the sourceInfo is set correctly
        IncludeSourceInfo sourceInfo = nodeFromInclude.getSourceInfo();
        assertNotNull(sourceInfo, "Node from include should have sourceInfo set");
        assertTrue(sourceInfo.isFromInclude(), "Node should be marked as from include");
        assertNotNull(sourceInfo.getSourceFile(), "Node should have source file set");

        // The source file should be different from the main schema
        assertNotEquals(
            TEST_SCHEMA_PATH.normalize(),
            sourceInfo.getSourceFile().normalize(),
            "Source file for included node should be different from main schema"
        );

        System.out.println("Node: " + nodeFromInclude.getName());
        System.out.println("SourceInfo: " + sourceInfo);
        System.out.println("SourceFile: " + sourceInfo.getSourceFile());
        System.out.println("IsFromInclude: " + sourceInfo.isFromInclude());
    }

    @Test
    void testAllTypesHaveSourceInfo() throws Exception {
        // Parse the schema with includes
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(TEST_SCHEMA_PATH);

        int totalTypes = 0;
        int typesWithSourceInfo = 0;
        int typesFromIncludes = 0;
        int typesWithoutSourceInfo = 0;

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType || child instanceof XsdSimpleType) {
                totalTypes++;
                IncludeSourceInfo sourceInfo = child.getSourceInfo();

                if (sourceInfo != null) {
                    typesWithSourceInfo++;
                    if (sourceInfo.isFromInclude()) {
                        typesFromIncludes++;
                    }
                } else {
                    typesWithoutSourceInfo++;
                    System.out.println("WARNING: Type without sourceInfo: " + child.getName() +
                        " (" + child.getClass().getSimpleName() + ")");
                }
            }
        }

        System.out.println("Total types: " + totalTypes);
        System.out.println("Types with sourceInfo: " + typesWithSourceInfo);
        System.out.println("Types from includes: " + typesFromIncludes);
        System.out.println("Types without sourceInfo: " + typesWithoutSourceInfo);

        // All types should have sourceInfo
        assertEquals(totalTypes, typesWithSourceInfo,
            "All types should have sourceInfo set. " + typesWithoutSourceInfo + " types are missing it.");
    }

    @Test
    void testNodeGetSourceFileMethod() throws Exception {
        // Parse the schema with includes
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(TEST_SCHEMA_PATH);

        // Test the getSourceFile() method on nodes
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType || child instanceof XsdSimpleType) {
                Path sourceFile = child.getSourceFile();
                IncludeSourceInfo sourceInfo = child.getSourceInfo();

                if (sourceInfo != null && sourceInfo.isFromInclude()) {
                    // For nodes from includes, getSourceFile() should return the include file path
                    assertNotNull(sourceFile,
                        "getSourceFile() should return a path for node from include: " + child.getName());

                    // Path should be different from main schema
                    assertNotEquals(TEST_SCHEMA_PATH.normalize(), sourceFile.normalize(),
                        "Source file for '" + child.getName() + "' should be the include file, not main schema");

                    System.out.println("Type: " + child.getName() +
                        " -> SourceFile: " + sourceFile.getFileName());
                }
            }
        }
    }

    @Test
    void testAddressTypeFromIncludedFile() throws Exception {
        // Parse the schema with includes
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(TEST_SCHEMA_PATH);

        // Find AddressType which is from an included file
        XsdComplexType addressType = null;
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType ct && "AddressType".equals(ct.getName())) {
                addressType = ct;
                break;
            }
        }

        assertNotNull(addressType, "AddressType should be found in the schema");

        // Check sourceInfo
        IncludeSourceInfo sourceInfo = addressType.getSourceInfo();
        assertNotNull(sourceInfo, "AddressType should have sourceInfo");
        assertTrue(sourceInfo.isFromInclude(), "AddressType should be from an included file");

        // Check the source file
        Path sourceFile = addressType.getSourceFile();
        assertNotNull(sourceFile, "AddressType should have a source file");

        System.out.println("AddressType sourceInfo: " + sourceInfo);
        System.out.println("AddressType sourceFile: " + sourceFile);
        System.out.println("AddressType isFromInclude: " + sourceInfo.isFromInclude());

        // The source file should NOT be the main schema
        assertFalse(sourceFile.toString().contains("FundsXML4.xsd")
            && !sourceFile.toString().contains("FundsXML4_"),
            "AddressType should NOT be from main FundsXML4.xsd but from an included file");
    }

    @Test
    void testMultiFileSaveGroupsNodesBySourceFile() throws Exception {
        // This test verifies that the multi-file serializer correctly groups nodes
        // Parse the schema with includes
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(TEST_SCHEMA_PATH);

        // Manually group nodes by their source file (like the serializer does)
        java.util.Map<Path, java.util.List<XsdNode>> nodesByFile = new java.util.LinkedHashMap<>();
        Path mainPath = TEST_SCHEMA_PATH.normalize();
        nodesByFile.put(mainPath, new java.util.ArrayList<>());

        for (XsdNode child : schema.getChildren()) {
            Path sourceFile = child.getSourceFile();
            if (sourceFile == null) {
                sourceFile = mainPath;
            } else {
                sourceFile = sourceFile.normalize();
            }
            nodesByFile.computeIfAbsent(sourceFile, k -> new java.util.ArrayList<>()).add(child);
        }

        System.out.println("Grouped nodes into " + nodesByFile.size() + " files:");
        for (var entry : nodesByFile.entrySet()) {
            System.out.println("  - " + entry.getKey().getFileName() + ": " + entry.getValue().size() + " nodes");
        }

        // Verify we have multiple files
        assertTrue(nodesByFile.size() > 1,
            "Should have nodes grouped into multiple files (includes). Found: " + nodesByFile.size());

        // Verify the main schema has few nodes (most are from includes)
        int mainSchemaNodes = nodesByFile.get(mainPath).size();
        System.out.println("Main schema nodes: " + mainSchemaNodes);

        // The main schema should have fewer nodes than the total (since most are from includes)
        int totalChildren = schema.getChildren().size();
        assertTrue(mainSchemaNodes < totalChildren,
            "Main schema should have fewer nodes (" + mainSchemaNodes +
            ") than total (" + totalChildren + ") since most are from includes");
    }
}
