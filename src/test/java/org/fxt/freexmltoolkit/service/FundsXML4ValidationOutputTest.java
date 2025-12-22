package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;

/**
 * Test to output validation results to console
 */
class FundsXML4ValidationOutputTest {

    @Test
    void validateGeneratedXml() throws Exception {
        String xmlPath = "/tmp/fundsxml4_all_nodes.xml";
        File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            System.err.println("XML file not found: " + xmlPath);
            return;
        }

        String xmlContent = Files.readString(xmlFile.toPath());
        System.err.println("XML Length: " + xmlContent.length() + " chars");

        String schemaPath = "/Users/karlkauc/src/FundsXMLSchema 2/FundsXML4.xsd";
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schemaPath);

        System.err.println("Validating...");
        XsdDocumentationService.ValidationResult result = service.validateXmlAgainstSchema(xmlContent);

        System.err.println("Is Valid: " + result.isValid());
        System.err.println("Total Errors: " + result.errors().size());

        // Count by type
        System.err.println("\n=== Error Type Summary ===");
        java.util.Map<String, Long> counts = result.errors().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                e -> {
                    String msg = e.message();
                    if (msg.contains("LEI")) return "LEI Pattern";
                    if (msg.contains("Email") || msg.contains("email")) return "Email Pattern";
                    if (msg.contains("decimal") && msg.contains("''")) return "Empty Decimal";
                    if (msg.contains("SEQUENCE")) return "SEQUENCE";
                    if (msg.contains("Unlisted")) return "Unlisted";
                    if (msg.contains("BreakDown")) return "BreakDown";
                    if (msg.contains("Amount") && msg.contains("expected")) return "Amount";
                    if (msg.contains("FXRate")) return "FXRate";
                    if (msg.contains("pattern")) return "Other Pattern";
                    if (msg.contains("cvc-complex-type.2.4")) return "Structural";
                    return "Other";
                },
                java.util.stream.Collectors.counting()));

        counts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .forEach(e -> System.err.println(e.getKey() + ": " + e.getValue()));

        // Show sample errors
        System.err.println("\n=== Sample Errors (first 20) ===");
        result.errors().stream().limit(20).forEach(e ->
            System.err.println("Line " + e.lineNumber() + ": " + e.message().substring(0, Math.min(100, e.message().length()))));
    }
}
