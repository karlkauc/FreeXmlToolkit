package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sample XML generation for the fund_dividend_schema_v0_6.xsd
 */
class FundDividendSchemaTest {

    private static final String SCHEMA_PATH = "/Users/karlkauc/Nextcloud/schema/fund_dividend_schema_v0_6.xsd";

    @Test
    void testGenerateSampleXmlAndValidate() throws Exception {
        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            System.out.println("Schema file not found at: " + SCHEMA_PATH);
            System.out.println("Skipping test - schema file not available");
            return;
        }

        // Generate sample XML
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(SCHEMA_PATH);

        String sampleXml = service.generateSampleXml(false, 2);

        System.out.println("=== Generated Sample XML ===");
        System.out.println(sampleXml);
        System.out.println("=== End of Sample XML ===");

        assertNotNull(sampleXml, "Sample XML should not be null");
        assertFalse(sampleXml.isBlank(), "Sample XML should not be empty");

        // Check for expected elements
        assertTrue(sampleXml.contains("FundDividends"), "Should contain FundDividends root element");
        assertTrue(sampleXml.contains("FundDividend"), "Should contain FundDividend element");
        assertTrue(sampleXml.contains("ISIN"), "Should contain ISIN element");
        assertTrue(sampleXml.contains("FiscalYear"), "Should contain FiscalYear element");
        assertTrue(sampleXml.contains("Date"), "Should contain Date element");

        // Check for required attributes
        assertTrue(sampleXml.contains("export.time="), "Should contain export.time attribute");
        assertTrue(sampleXml.contains("version="), "Should contain version attribute");
        assertTrue(sampleXml.contains("version=\"0.6\""), "Version should be 0.6 from enumeration");

        // Validate against schema
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(sampleXml);

        System.out.println("\n=== Validation Result ===");
        System.out.println("Is Valid: " + result.isValid());
        System.out.println("Message: " + result.message());
        System.out.println("=== End of Validation ===");

        // Assert validation passed
        assertTrue(result.isValid(), "Generated XML should be valid against schema. Errors: " + result.message());
    }

    @Test
    void testISINFormat() throws Exception {
        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            return;
        }

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(SCHEMA_PATH);

        String sampleXml = service.generateSampleXml(false, 1);

        // Extract ISIN value - should be exactly 12 alphanumeric characters
        int isinStart = sampleXml.indexOf("<ISIN>") + 6;
        int isinEnd = sampleXml.indexOf("</ISIN>");

        if (isinStart > 5 && isinEnd > isinStart) {
            String isinValue = sampleXml.substring(isinStart, isinEnd);
            System.out.println("ISIN value: " + isinValue + " (length: " + isinValue.length() + ")");

            assertEquals(12, isinValue.length(), "ISIN should be exactly 12 characters");
            assertTrue(isinValue.matches("[A-Z0-9]+"), "ISIN should only contain uppercase letters and digits");
        }
    }
}
