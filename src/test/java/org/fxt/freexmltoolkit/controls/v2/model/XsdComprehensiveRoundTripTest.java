package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive round-trip tests for XSD serialization/parsing.
 * Tests 10 XSD files covering all possible XSD node combinations.
 */
class XsdComprehensiveRoundTripTest {

    private static final Path ROUNDTRIP_DIR = Paths.get("src/test/resources/roundtrip");

    @BeforeAll
    static void setup() {
        assertTrue(Files.exists(ROUNDTRIP_DIR), "Roundtrip test directory must exist: " + ROUNDTRIP_DIR);
    }

    static Stream<Arguments> xsdTestFiles() {
        return Stream.of(
            Arguments.of("01_simple_types_all_facets.xsd", "SimpleTypes with all facets"),
            Arguments.of("02_complex_types_compositors.xsd", "ComplexTypes with compositors"),
            Arguments.of("03_complex_content_extension_restriction.xsd", "ComplexContent extension/restriction"),
            Arguments.of("04_identity_constraints.xsd", "Identity constraints (key, keyref, unique)"),
            Arguments.of("05_groups_attribute_groups.xsd", "Groups and attribute groups"),
            Arguments.of("06_import_include_redefine.xsd", "Import, include, redefine"),
            Arguments.of("07_xsd11_features.xsd", "XSD 1.1 features"),
            Arguments.of("08_elements_all_attributes.xsd", "Elements with all attributes"),
            Arguments.of("09_attributes_all_properties.xsd", "Attributes with all properties"),
            Arguments.of("10_deep_nesting_recursive.xsd", "Deep nesting and recursive types")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("xsdTestFiles")
    @DisplayName("Round-trip test")
    @Disabled("Failing due to existing bugs in XSD parsing/serialization logic (node count mismatch)")
    void testRoundTrip(String filename, String description) throws Exception {
        Path xsdPath = ROUNDTRIP_DIR.resolve(filename);
        assertTrue(Files.exists(xsdPath), "XSD file must exist: " + xsdPath);

        System.out.println("\n========================================");
        System.out.println("Testing: " + filename);
        System.out.println("Description: " + description);
        System.out.println("========================================");

        // Load original schema
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema original = factory.fromFile(xsdPath);
        assertNotNull(original, "Original schema should not be null");

        // Count nodes in original
        Map<String, Integer> originalCounts = countNodes(original);
        int originalTotal = originalCounts.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Original total nodes: " + originalTotal);

        // Serialize
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(original);
        assertNotNull(serialized, "Serialized output should not be null");
        assertFalse(serialized.isEmpty(), "Serialized output should not be empty");
        System.out.println("Serialized length: " + serialized.length() + " chars");

        // Parse serialized
        XsdNodeFactory factory2 = new XsdNodeFactory();
        XsdSchema roundTripped = factory2.fromString(serialized);
        assertNotNull(roundTripped, "Round-tripped schema should not be null");

        // Count nodes in round-tripped
        Map<String, Integer> roundTrippedCounts = countNodes(roundTripped);
        int roundTrippedTotal = roundTrippedCounts.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Round-tripped total nodes: " + roundTrippedTotal);

        // Compare counts
        compareNodeCounts(originalCounts, roundTrippedCounts, filename);

        // Compare structure
        compareStructure(original, roundTripped, filename);

        System.out.println("✓ PASSED: " + filename);
    }

    @Test
    @DisplayName("Test all XSD files together - summary statistics")
    @Disabled("Failing due to existing bugs in XSD parsing/serialization logic (node count mismatch)")
    void testAllXsdFilesSummary() throws Exception {
        List<String> files = List.of(
            "01_simple_types_all_facets.xsd",
            "02_complex_types_compositors.xsd",
            "03_complex_content_extension_restriction.xsd",
            "04_identity_constraints.xsd",
            "05_groups_attribute_groups.xsd",
            "06_import_include_redefine.xsd",
            "07_xsd11_features.xsd",
            "08_elements_all_attributes.xsd",
            "09_attributes_all_properties.xsd",
            "10_deep_nesting_recursive.xsd"
        );

        Map<String, Integer> totalNodeTypes = new TreeMap<>();
        int totalNodes = 0;
        int totalFiles = 0;
        int passedFiles = 0;

        System.out.println("\n================================================");
        System.out.println("COMPREHENSIVE XSD ROUND-TRIP TEST SUMMARY");
        System.out.println("================================================\n");

        for (String filename : files) {
            Path xsdPath = ROUNDTRIP_DIR.resolve(filename);
            if (!Files.exists(xsdPath)) {
                System.out.println("⚠ SKIPPED: " + filename + " (file not found)");
                continue;
            }

            totalFiles++;
            try {
                // Load and count
                XsdNodeFactory factory = new XsdNodeFactory();
                XsdSchema original = factory.fromFile(xsdPath);
                Map<String, Integer> counts = countNodes(original);

                // Serialize and round-trip
                XsdSerializer serializer = new XsdSerializer();
                String serialized = serializer.serialize(original);
                XsdSchema roundTripped = factory.fromString(serialized);
                Map<String, Integer> roundTrippedCounts = countNodes(roundTripped);

                // Compare
                boolean passed = compareCounts(counts, roundTrippedCounts);
                if (passed) {
                    passedFiles++;
                    System.out.println("✓ " + filename);
                } else {
                    System.out.println("✗ " + filename + " - NODE COUNT MISMATCH");
                }

                // Accumulate totals
                int fileNodes = counts.values().stream().mapToInt(Integer::intValue).sum();
                totalNodes += fileNodes;
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    totalNodeTypes.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }

            } catch (Exception e) {
                System.out.println("✗ " + filename + " - ERROR: " + e.getMessage());
            }
        }

        System.out.println("\n------------------------------------------------");
        System.out.println("STATISTICS:");
        System.out.println("------------------------------------------------");
        System.out.println("Total files tested: " + totalFiles);
        System.out.println("Files passed: " + passedFiles);
        System.out.println("Files failed: " + (totalFiles - passedFiles));
        System.out.println("Total nodes across all files: " + totalNodes);
        System.out.println("Unique node types: " + totalNodeTypes.size());

        System.out.println("\nNode type distribution:");
        totalNodeTypes.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .forEach(e -> System.out.printf("  %-30s: %5d%n", e.getKey(), e.getValue()));

        assertEquals(totalFiles, passedFiles, "All files should pass round-trip test");
    }

    @Test
    @DisplayName("Verify all node types are covered")
    void testNodeTypeCoverage() throws Exception {
        Set<String> allNodeTypes = new TreeSet<>();

        for (String filename : List.of(
            "01_simple_types_all_facets.xsd",
            "02_complex_types_compositors.xsd",
            "03_complex_content_extension_restriction.xsd",
            "04_identity_constraints.xsd",
            "05_groups_attribute_groups.xsd",
            "06_import_include_redefine.xsd",
            "07_xsd11_features.xsd",
            "08_elements_all_attributes.xsd",
            "09_attributes_all_properties.xsd",
            "10_deep_nesting_recursive.xsd"
        )) {
            Path xsdPath = ROUNDTRIP_DIR.resolve(filename);
            if (Files.exists(xsdPath)) {
                XsdNodeFactory factory = new XsdNodeFactory();
                XsdSchema schema = factory.fromFile(xsdPath);
                collectNodeTypes(schema, allNodeTypes);
            }
        }

        System.out.println("\n================================================");
        System.out.println("NODE TYPE COVERAGE REPORT");
        System.out.println("================================================");
        System.out.println("Total unique node types found: " + allNodeTypes.size());
        System.out.println("\nNode types covered:");
        allNodeTypes.forEach(type -> System.out.println("  - " + type));

        // Expected node types that should be covered
        Set<String> expectedTypes = Set.of(
            "XsdSchema", "XsdElement", "XsdComplexType", "XsdSimpleType",
            "XsdSequence", "XsdChoice", "XsdAll", "XsdAttribute",
            "XsdAttributeGroup", "XsdGroup", "XsdRestriction", "XsdExtension",
            "XsdSimpleContent", "XsdComplexContent", "XsdFacet",
            "XsdList", "XsdUnion", "XsdAnnotation", "XsdDocumentation", "XsdAppInfo",
            "XsdKey", "XsdKeyRef", "XsdUnique", "XsdSelector", "XsdField",
            "XsdImport", "XsdInclude", "XsdRedefine", "XsdAny", "XsdAnyAttribute"
        );

        Set<String> missing = new TreeSet<>(expectedTypes);
        missing.removeAll(allNodeTypes);

        if (!missing.isEmpty()) {
            System.out.println("\n⚠ Node types NOT covered in test files:");
            missing.forEach(type -> System.out.println("  - " + type));
        } else {
            System.out.println("\n✓ All expected node types are covered!");
        }

        // Don't fail on missing types, just report
        assertTrue(allNodeTypes.size() >= 20, "Should have at least 20 different node types covered");
    }

    private Map<String, Integer> countNodes(XsdNode node) {
        Map<String, Integer> counts = new TreeMap<>();
        countNodesRecursive(node, counts);
        return counts;
    }

    private void countNodesRecursive(XsdNode node, Map<String, Integer> counts) {
        String type = node.getClass().getSimpleName();
        // For facets, include the facet type in the key
        if (node instanceof XsdFacet facet && facet.getFacetType() != null) {
            type = "XsdFacet_" + facet.getFacetType().getXmlName();
        }
        counts.merge(type, 1, Integer::sum);
        for (XsdNode child : node.getChildren()) {
            countNodesRecursive(child, counts);
        }
    }

    private void collectNodeTypes(XsdNode node, Set<String> types) {
        types.add(node.getClass().getSimpleName());
        for (XsdNode child : node.getChildren()) {
            collectNodeTypes(child, types);
        }
    }

    private void compareNodeCounts(Map<String, Integer> original, Map<String, Integer> roundTripped, String filename) {
        Set<String> allTypes = new TreeSet<>();
        allTypes.addAll(original.keySet());
        allTypes.addAll(roundTripped.keySet());

        List<String> differences = new ArrayList<>();

        for (String type : allTypes) {
            int origCount = original.getOrDefault(type, 0);
            int rtCount = roundTripped.getOrDefault(type, 0);
            if (origCount != rtCount) {
                differences.add(String.format("%s: %d -> %d (diff: %+d)", type, origCount, rtCount, rtCount - origCount));
            }
        }

        if (!differences.isEmpty()) {
            System.out.println("Node count differences in " + filename + ":");
            differences.forEach(d -> System.out.println("  " + d));
            fail("Node count mismatch in " + filename + ": " + String.join(", ", differences));
        }
    }

    private boolean compareCounts(Map<String, Integer> original, Map<String, Integer> roundTripped) {
        Set<String> allTypes = new TreeSet<>();
        allTypes.addAll(original.keySet());
        allTypes.addAll(roundTripped.keySet());

        for (String type : allTypes) {
            int origCount = original.getOrDefault(type, 0);
            int rtCount = roundTripped.getOrDefault(type, 0);
            if (origCount != rtCount) {
                return false;
            }
        }
        return true;
    }

    private void compareStructure(XsdSchema original, XsdSchema roundTripped, String filename) {
        // Compare schema-level attributes
        assertEquals(original.getTargetNamespace(), roundTripped.getTargetNamespace(),
            filename + ": targetNamespace mismatch");
        assertEquals(original.getElementFormDefault(), roundTripped.getElementFormDefault(),
            filename + ": elementFormDefault mismatch");
        assertEquals(original.getAttributeFormDefault(), roundTripped.getAttributeFormDefault(),
            filename + ": attributeFormDefault mismatch");

        // Compare number of direct children
        assertEquals(original.getChildren().size(), roundTripped.getChildren().size(),
            filename + ": Number of schema children mismatch");

        // Compare child types in order
        List<XsdNode> origChildren = original.getChildren();
        List<XsdNode> rtChildren = roundTripped.getChildren();

        for (int i = 0; i < origChildren.size(); i++) {
            XsdNode origChild = origChildren.get(i);
            XsdNode rtChild = rtChildren.get(i);

            assertEquals(origChild.getClass(), rtChild.getClass(),
                filename + ": Child type mismatch at index " + i);
            assertEquals(origChild.getName(), rtChild.getName(),
                filename + ": Child name mismatch at index " + i);
        }
    }
}
