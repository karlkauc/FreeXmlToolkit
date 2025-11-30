package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests round-trip serialization by counting nodes by type.
 */
class XsdRoundTripNodeCountTest {

    @Test
    void testFundsXML4RoundTripNodeCounts() throws Exception {
        // Load original schema from test resources
        Path xsdPath = Path.of("src/test/resources/FundsXML4.xsd");
        System.out.println("Loading: " + xsdPath.toAbsolutePath());

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema original = factory.fromFile(xsdPath);

        // Count nodes in original
        Map<String, Integer> originalCounts = countNodes(original);
        System.out.println("\n=== ORIGINAL SCHEMA ===");
        printCounts(originalCounts);
        int originalTotal = originalCounts.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("TOTAL: " + originalTotal);

        // Serialize
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(original);
        System.out.println("\nSerialized length: " + serialized.length() + " chars");

        // Parse serialized
        XsdNodeFactory factory2 = new XsdNodeFactory();
        XsdSchema roundTripped = factory2.fromString(serialized);

        // Count nodes in round-tripped
        Map<String, Integer> roundTrippedCounts = countNodes(roundTripped);
        System.out.println("\n=== ROUND-TRIPPED SCHEMA ===");
        printCounts(roundTrippedCounts);
        int roundTrippedTotal = roundTrippedCounts.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("TOTAL: " + roundTrippedTotal);

        // Compare
        System.out.println("\n=== COMPARISON ===");
        Set<String> allTypes = new TreeSet<>();
        allTypes.addAll(originalCounts.keySet());
        allTypes.addAll(roundTrippedCounts.keySet());

        for (String type : allTypes) {
            int orig = originalCounts.getOrDefault(type, 0);
            int rt = roundTrippedCounts.getOrDefault(type, 0);
            String status = (orig == rt) ? "OK" : "DIFF";
            System.out.println(String.format("%-25s: %5d -> %5d  %s", type, orig, rt, status));
            assertEquals(orig, rt, "Node count mismatch for type: " + type);
        }

        System.out.println("\nTotal nodes: " + originalTotal + " -> " + roundTrippedTotal);
        assertEquals(originalTotal, roundTrippedTotal, "Total node count mismatch");
    }

    private Map<String, Integer> countNodes(XsdNode node) {
        Map<String, Integer> counts = new TreeMap<>();
        countNodesRecursive(node, counts);
        return counts;
    }

    private void countNodesRecursive(XsdNode node, Map<String, Integer> counts) {
        String type = node.getClass().getSimpleName();
        // For facets, include the facet type in the key
        if (node instanceof XsdFacet facet) {
            type = "XsdFacet_" + facet.getFacetType().getXmlName();
        }
        counts.merge(type, 1, Integer::sum);
        for (XsdNode child : node.getChildren()) {
            countNodesRecursive(child, counts);
        }
    }

    private void printCounts(Map<String, Integer> counts) {
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println(String.format("%-25s: %5d", entry.getKey(), entry.getValue()));
        }
    }
}
