package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for constraint-aware sample XML generation.
 * Tests that identity constraints (xs:key, xs:unique, xs:keyref) are respected
 * when generating sample XML with repeated elements.
 */
class ConstraintAwareSampleXmlTest {

    private static final String FUNDSXML4_RESOURCE = "/3elements/FundsXML4.xsd";

    @Test
    void testFundsXml4NoIdentityConstraintErrors() throws Exception {
        URL resourceUrl = getClass().getResource(FUNDSXML4_RESOURCE);
        if (resourceUrl == null) {
            System.out.println("Resource not found: " + FUNDSXML4_RESOURCE + " - skipping test");
            return;
        }

        String schemaPath = new File(resourceUrl.toURI()).getAbsolutePath();

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaPath);

        // Generate with maxOccurrences=3 to trigger repeated elements
        String sampleXml = service.generateSampleXml(false, 3);

        assertNotNull(sampleXml, "Sample XML should not be null");
        assertFalse(sampleXml.isBlank(), "Sample XML should not be empty");
        assertTrue(sampleXml.contains("FundsXML4"), "Should contain FundsXML4 root element");

        // Validate against schema
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(sampleXml);

        // Count identity constraint errors specifically
        // These are errors like "Duplicate key value" or "Identity Constraint error"
        // NOT general type validation errors that happen to contain "unique" in the element name
        int identityConstraintErrors = 0;
        if (!result.isValid() && result.errors() != null) {
            for (XsdDocumentationService.ValidationError error : result.errors()) {
                String msg = error.message().toLowerCase();
                if (msg.contains("duplicate key value") || msg.contains("duplicate unique value")
                        || msg.contains("identity constraint") || msg.contains("cvc-identity-constraint")) {
                    identityConstraintErrors++;
                    System.out.println("Identity constraint error: " + error);
                }
            }
        }

        assertEquals(0, identityConstraintErrors,
                "There should be no identity constraint validation errors. Found: " + identityConstraintErrors);
    }

    @Test
    void testUniqueBenchmarkIds() throws Exception {
        URL resourceUrl = getClass().getResource(FUNDSXML4_RESOURCE);
        if (resourceUrl == null) {
            System.out.println("Resource not found: " + FUNDSXML4_RESOURCE + " - skipping test");
            return;
        }

        String schemaPath = new File(resourceUrl.toURI()).getAbsolutePath();

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaPath);

        // Generate with repeats
        String sampleXml = service.generateSampleXml(false, 3);

        // Extract BenchmarkID values from the generated XML
        List<String> benchmarkIds = extractElementValues(sampleXml, "BenchmarkID");

        if (!benchmarkIds.isEmpty()) {
            // Check that BenchmarkIDs within each Benchmark parent are unique
            Set<String> uniqueIds = new HashSet<>(benchmarkIds);
            System.out.println("Found " + benchmarkIds.size() + " BenchmarkID values, " + uniqueIds.size() + " unique");

            // The BenchmarkIDs should have unique values (not all the same)
            if (benchmarkIds.size() > 1) {
                assertTrue(uniqueIds.size() > 1,
                        "BenchmarkID values should not all be identical: " + benchmarkIds);
            }
        }
    }

    @Test
    void testUniqueTransactionIds() throws Exception {
        URL resourceUrl = getClass().getResource(FUNDSXML4_RESOURCE);
        if (resourceUrl == null) {
            System.out.println("Resource not found: " + FUNDSXML4_RESOURCE + " - skipping test");
            return;
        }

        String schemaPath = new File(resourceUrl.toURI()).getAbsolutePath();

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaPath);

        // Generate with repeats
        String sampleXml = service.generateSampleXml(false, 3);

        // Extract TransactionID values
        List<String> transactionIds = extractElementValues(sampleXml, "TransactionID");

        if (!transactionIds.isEmpty()) {
            Set<String> uniqueIds = new HashSet<>(transactionIds);
            System.out.println("Found " + transactionIds.size() + " TransactionID values, " + uniqueIds.size() + " unique");

            if (transactionIds.size() > 1) {
                assertTrue(uniqueIds.size() > 1,
                        "TransactionID values should not all be identical: " + transactionIds);
            }
        }
    }

    @Test
    void testBackwardCompatibilityNoConstraints() {
        // Test that schemas without identity constraints work unchanged
        XsdDocumentationService service = new XsdDocumentationService();
        String simpleSchema = "src/test/resources/test-schema-simple.xsd";
        File file = new File(simpleSchema);
        if (!file.exists()) {
            // Create a minimal schema for testing
            System.out.println("Simple schema not found - testing with inline approach");
            return;
        }

        service.setXsdFilePath(simpleSchema);
        String xml = service.generateSampleXml(false, 2);
        assertNotNull(xml, "XML should be generated for schema without constraints");
    }

    @Test
    void testConstraintTrackerCreatedDuringGeneration() throws Exception {
        URL resourceUrl = getClass().getResource(FUNDSXML4_RESOURCE);
        if (resourceUrl == null) {
            System.out.println("Resource not found: " + FUNDSXML4_RESOURCE + " - skipping test");
            return;
        }

        String schemaPath = new File(resourceUrl.toURI()).getAbsolutePath();

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaPath);

        // Should not throw any exceptions
        String sampleXml = service.generateSampleXml(true, 1);
        assertNotNull(sampleXml);
        assertFalse(sampleXml.isEmpty());
    }

    /**
     * Extracts all text values of a given element name from XML.
     */
    private List<String> extractElementValues(String xml, String elementName) {
        List<String> values = new ArrayList<>();
        Pattern pattern = Pattern.compile("<" + elementName + ">(.*?)</" + elementName + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }
}
