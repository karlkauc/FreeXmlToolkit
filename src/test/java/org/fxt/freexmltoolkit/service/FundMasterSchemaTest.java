package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sample XML generation for the fund_master_schema_v1_2.xsd
 */
class FundMasterSchemaTest {

    private static final String SCHEMA_PATH = "/Users/karlkauc/Nextcloud/schema/fund_master_schema_v1_2.xsd";

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
        assertTrue(sampleXml.contains("FundMaster"), "Should contain FundMaster root element");

        // Check for required attributes
        assertTrue(sampleXml.contains("export.time="), "Should contain export.time attribute");
        assertTrue(sampleXml.contains("export.type="), "Should contain export.type attribute");
        assertTrue(sampleXml.contains("version="), "Should contain version attribute");
        assertTrue(sampleXml.contains("source="), "Should contain source attribute");

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
    void testDateTimeFormat() throws Exception {
        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            return;
        }

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(SCHEMA_PATH);

        String sampleXml = service.generateSampleXml(false, 1);

        // export.time should be xs:dateTime format (e.g., 2024-01-15T10:30:00)
        // Check if the dateTime format is valid
        System.out.println("Checking export.time attribute format...");
        int exportTimeStart = sampleXml.indexOf("export.time=\"") + 13;
        int exportTimeEnd = sampleXml.indexOf("\"", exportTimeStart);

        if (exportTimeStart > 12 && exportTimeEnd > exportTimeStart) {
            String exportTimeValue = sampleXml.substring(exportTimeStart, exportTimeEnd);
            System.out.println("export.time value: " + exportTimeValue);

            // xs:dateTime format: YYYY-MM-DDTHH:MM:SS (optionally with timezone)
            assertTrue(exportTimeValue.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"),
                    "export.time should be valid xs:dateTime format, got: " + exportTimeValue);
        }
    }
}
