package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sample XML generation for FundsXML4.xsd (no namespace schema)
 */
class FundsXML4SchemaTest {

    private static final String SCHEMA_PATH = "/Users/karlkauc/src/FundsXMLSchema 2/FundsXML4.xsd";

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

        String sampleXml = service.generateSampleXml(true, 1); // mandatoryOnly=true for smaller output

        System.out.println("=== Generated Sample XML (first 2000 chars) ===");
        System.out.println(sampleXml.substring(0, Math.min(2000, sampleXml.length())));
        System.out.println("=== End of Sample XML ===");

        // Check for namespace handling in the generated XML
        System.out.println("\n=== Namespace Check ===");
        boolean hasNoNamespaceSchemaLocation = sampleXml.contains("noNamespaceSchemaLocation");
        boolean hasSchemaLocation = sampleXml.contains("schemaLocation=") && !hasNoNamespaceSchemaLocation;
        System.out.println("Has noNamespaceSchemaLocation: " + hasNoNamespaceSchemaLocation);
        System.out.println("Has schemaLocation (with namespace): " + hasSchemaLocation);
        System.out.println("=== End ===");

        assertNotNull(sampleXml, "Sample XML should not be null");
        assertFalse(sampleXml.isBlank(), "Sample XML should not be empty");

        // Check for expected elements
        assertTrue(sampleXml.contains("FundsXML4"), "Should contain FundsXML4 root element");

        // For no-namespace schema, should use noNamespaceSchemaLocation
        assertTrue(sampleXml.contains("noNamespaceSchemaLocation"),
                "Should use noNamespaceSchemaLocation for schema without targetNamespace");

        // Validate against schema
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(sampleXml);

        System.out.println("\n=== Validation Result ===");
        System.out.println("Is Valid: " + result.isValid());
        System.out.println("Message: " + result.message());
        System.out.println("=== End of Validation ===");

        // Assert validation passed
        assertTrue(result.isValid(), "Generated XML should be valid against schema. Errors: " + result.message());
    }
}
