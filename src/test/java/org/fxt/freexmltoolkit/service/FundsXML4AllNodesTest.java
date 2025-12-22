package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sample XML generation for FundsXML4.xsd with ALL nodes (not just mandatory)
 */
class FundsXML4AllNodesTest {

    private static final String SCHEMA_PATH = "/Users/karlkauc/src/FundsXMLSchema 2/FundsXML4.xsd";

    @Test
    void testGenerateAllNodesAndValidate() throws Exception {
        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            System.err.println("Schema file not found at: " + SCHEMA_PATH);
            return;
        }

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(SCHEMA_PATH);

        // Generate with ALL nodes (mandatoryOnly=false), maxOccurrences=1
        System.err.println("=== Generating sample XML with ALL nodes ===");
        String sampleXml = service.generateSampleXml(false, 1);

        System.err.println("Generated XML length: " + sampleXml.length() + " chars");

        // Save to temp file
        try (FileWriter fw = new FileWriter("/tmp/fundsxml4_all_nodes.xml")) {
            fw.write(sampleXml);
        }
        System.err.println("Saved to /tmp/fundsxml4_all_nodes.xml");

        // Validate
        System.err.println("\n=== Validating ===");
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(sampleXml);

        System.err.println("Is Valid: " + result.isValid());
        System.err.println("Total Error count: " + result.errors().size());

        // Count error types
        System.err.println("\n=== Error Type Summary ===");
        java.util.Map<String, Long> errorCounts = result.errors().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                e -> {
                    String msg = e.message();
                    if (msg.contains("LEI")) return "LEI Pattern";
                    if (msg.contains("Email") || msg.contains("email")) return "Email Pattern";
                    if (msg.contains("decimal") && msg.contains("''")) return "Empty Decimal";
                    if (msg.contains("SEQUENCE")) return "SEQUENCE element";
                    if (msg.contains("Unlisted")) return "Unlisted element";
                    if (msg.contains("BreakDown")) return "BreakDown";
                    if (msg.contains("Amount") && msg.contains("expected")) return "Amount expected";
                    if (msg.contains("FXRate")) return "FXRate";
                    if (msg.contains("Category") && msg.contains("expected")) return "Category";
                    if (msg.contains("pattern-valid")) return "Other Pattern";
                    if (msg.contains("2.4.a") || msg.contains("2.4.b") || msg.contains("2.4.d")) return "Structural";
                    return "Other: " + msg.substring(0, Math.min(50, msg.length()));
                },
                java.util.stream.Collectors.counting()
            ));

        errorCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .forEach(e -> System.err.println(e.getKey() + ": " + e.getValue()));

        // Show first 15 errors
        System.err.println("\n=== First 15 Errors ===");
        result.errors().stream().limit(15).forEach(e ->
            System.err.println("Line " + e.lineNumber() + ", Col " + e.columnNumber() + ": " + e.message()));

        // Write results to a file for later inspection
        try (FileWriter fw = new FileWriter("/tmp/validation_results.txt")) {
            fw.write("Total Error count: " + result.errors().size() + "\n\n");
            fw.write("=== Error Type Summary ===\n");
            errorCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    try { fw.write(e.getKey() + ": " + e.getValue() + "\n"); } catch (Exception ex) {}
                });
            fw.write("\n=== First 30 Errors ===\n");
            result.errors().stream().limit(30).forEach(e -> {
                try { fw.write("Line " + e.lineNumber() + ", Col " + e.columnNumber() + ": " + e.message() + "\n"); } catch (Exception ex) {}
            });
        }
    }
}
