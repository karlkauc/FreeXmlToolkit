package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.service.SignatureService;
import org.fxt.freexmltoolkit.service.TemplateFileService;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the integrity of the bundled example collection in {@code release/examples}:
 * the FundsXML4 instances stay valid against the bundled schema, the invalid demo stays
 * invalid, the signature example verifies, the FundsXML templates load, and the
 * JSON/CSV export stylesheets execute.
 */
class ExamplesIntegrityTest {

    private static final Path EXAMPLES = Path.of("release", "examples");
    private static final File FUNDSXML4_XSD = EXAMPLES.resolve("xsd/FundsXML4.xsd").toFile();

    private final XmlService xmlService = XmlServiceImpl.getInstance();

    @ParameterizedTest
    @ValueSource(strings = {
            "xml/FundsXML4_Equity_Fund.xml",
            "xml/FundsXML_422_Bond_Fund.xml",
            "schematron/eam_rules.xml"
    })
    void bundledInstances_areValidAgainstBundledSchema(String relativePath) {
        File xml = EXAMPLES.resolve(relativePath).toFile();
        assertTrue(xml.exists(), relativePath + " must exist");

        var errors = xmlService.validateFile(xml, FUNDSXML4_XSD);
        assertEquals(0, errors.size(), () -> relativePath + " should be valid but has: " + errors);
    }

    @Test
    void invalidDemo_reportsValidationErrors() {
        File xml = EXAMPLES.resolve("xml/FundsXML4_Invalid_Demo.xml").toFile();
        assertTrue(xml.exists());

        var errors = xmlService.validateFile(xml, FUNDSXML4_XSD);
        assertFalse(errors.isEmpty(), "Invalid demo must produce XSD validation errors");
    }

    @Test
    void signatureExample_isValid() {
        File signed = EXAMPLES.resolve("signature/FundsXML4_Equity_Fund_signed.xml").toFile();
        assertTrue(signed.exists(), "Signed example must exist");

        assertTrue(new SignatureService().isSignatureValid(signed),
                "Bundled signature example must verify");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fundsxml-controldata",
            "fundsxml-fund",
            "fundsxml-position",
            "fundsxml-shareclass"
    })
    void fundsXmlTemplates_loadWithCorrectCategory(String id) throws IOException {
        Path templateFile = EXAMPLES.resolve("templates").resolve(id + ".template");
        assertTrue(Files.exists(templateFile), templateFile + " must exist");

        // Note: XmlTemplate assigns itself a fresh UUID on load - the file's id only
        // drives the filename, so name/category/content are the stable fields to check.
        var template = TemplateFileService.getInstance().loadTemplateFromFile(templateFile);
        assertTrue(template.getName().startsWith("FundsXML"),
                () -> "Unexpected template name: " + template.getName());
        assertEquals("FundsXML", template.getCategory());
        assertFalse(template.getContent().isBlank());
    }

    @Test
    void jsonExportStylesheet_producesJson() throws IOException {
        var result = XsltTransformationEngine.getInstance().transform(
                Files.readString(EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml")),
                Files.readString(EXAMPLES.resolve("xslt/FundsXML_to_JSON.xslt")),
                Map.of(), XsltTransformationEngine.OutputFormat.JSON);

        assertTrue(result.isSuccess(), () -> "JSON transform failed: " + result.getErrorMessage());
        assertTrue(result.getOutputContent().contains("\"EQUITY_FUND_DEMO_001\""),
                "JSON output should contain the document id");
    }

    @Test
    void csvExportStylesheet_producesCsvWithHeader() throws IOException {
        var result = XsltTransformationEngine.getInstance().transform(
                Files.readString(EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml")),
                Files.readString(EXAMPLES.resolve("xslt/FundsXML_Positions_to_CSV.xslt")),
                Map.of(), XsltTransformationEngine.OutputFormat.TEXT);

        assertTrue(result.isSuccess(), () -> "CSV transform failed: " + result.getErrorMessage());
        assertTrue(result.getOutputContent().startsWith(
                        "FundISIN,FundName,PositionID,ISIN,AssetName,AssetClass,Currency,TotalValue,Percentage"),
                "CSV output should start with the header row");
    }
}
