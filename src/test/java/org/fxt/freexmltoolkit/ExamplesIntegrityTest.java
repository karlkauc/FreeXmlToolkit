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
    void allBundledXQueries_executeAgainstTheEquityExample() throws IOException {
        String xml = Files.readString(EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml"));
        try (var files = Files.list(EXAMPLES.resolve("xquery"))) {
            var queries = files.filter(p -> p.toString().endsWith(".xq")).sorted().toList();
            assertTrue(queries.size() >= 17, "Expected the full bundled XQuery series");

            for (Path query : queries) {
                var result = XsltTransformationEngine.getInstance().transformXQuery(
                        xml, Files.readString(query),
                        Map.of(), XsltTransformationEngine.OutputFormat.HTML);
                assertTrue(result.isSuccess(),
                        () -> query.getFileName() + " failed: " + result.getErrorMessage());
            }
        }
    }

    @Test
    void lookThroughXQuery_findsTheFundInvestment() throws IOException {
        var result = XsltTransformationEngine.getInstance().transformXQuery(
                Files.readString(EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml")),
                Files.readString(EXAMPLES.resolve("xquery/11-fund-lookthrough-analysis.xq")),
                Map.of(), XsltTransformationEngine.OutputFormat.HTML);

        assertTrue(result.isSuccess(), () -> "Look-through XQuery failed: " + result.getErrorMessage());
        // the equity example holds exactly one target-fund position (Erste Euro Bázis)
        assertTrue(result.getOutputContent().contains("HU0000706007"),
                "Look-through report should list the fund-investment candidate");
    }

    @Test
    void allBundledXPathExamples_executeAgainstTheEquityExample() throws IOException {
        String xml = Files.readString(EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml"));
        try (var files = Files.list(EXAMPLES.resolve("xpath"))) {
            var expressions = files.filter(p -> p.toString().endsWith(".xpath")).sorted().toList();
            assertTrue(expressions.size() >= 32, "Expected the full bundled XPath series");

            for (Path expression : expressions) {
                String result = xmlService.getXmlFromXpath(xml, Files.readString(expression));
                assertTrue(result != null,
                        () -> expression.getFileName() + " failed to evaluate (returned null)");
                // Files 01-20 may legitimately serialize to "" (map/array results);
                // the reporting series 21+ always yields printable text.
                if (expression.getFileName().toString().compareTo("21") >= 0) {
                    assertFalse(result.isBlank(),
                            () -> expression.getFileName() + " produced blank output");
                }
            }
        }
    }

    @Test
    void positionsCsvXQuery_producesCsvWithHeader() throws IOException {
        var result = XsltTransformationEngine.getInstance().transformXQuery(
                Files.readString(EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml")),
                Files.readString(EXAMPLES.resolve("xquery/12-positions-csv-export.xq")),
                Map.of(), XsltTransformationEngine.OutputFormat.TEXT);

        assertTrue(result.isSuccess(), () -> "Position CSV XQuery failed: " + result.getErrorMessage());
        assertTrue(result.getOutputContent().startsWith(
                        "FundISIN,FundName,PositionID,ISIN,AssetName,AssetType,Country,PositionCcy"),
                "CSV output should start with the header row");
    }

    @Test
    void allBundledXProcPipelines_executeAgainstTheEquityExample() throws IOException {
        var equityFund = EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml").toFile();
        try (var files = Files.list(EXAMPLES.resolve("xproc"))) {
            var pipelines = files.filter(p -> p.toString().endsWith(".xpl")).sorted().toList();
            assertTrue(pipelines.size() >= 8, "Expected the full bundled XProc series");

            for (Path pipeline : pipelines) {
                var result = org.fxt.freexmltoolkit.controls.shell.editor.XProcRunner.runPipeline(
                        Files.readString(pipeline), pipeline.toFile(), null, equityFund);
                assertFalse(result.isError(),
                        () -> pipeline.getFileName() + " failed: " + result.text());
                assertFalse(result.text().isBlank(),
                        () -> pipeline.getFileName() + " produced blank output");
            }
        }
    }

    @Test
    void positionsCsvPipeline_producesCsvWithHeader() throws IOException {
        Path pipeline = EXAMPLES.resolve("xproc/03-positions-csv.xpl");
        var result = org.fxt.freexmltoolkit.controls.shell.editor.XProcRunner.runPipeline(
                Files.readString(pipeline), pipeline.toFile(), null,
                EXAMPLES.resolve("xml/FundsXML4_Equity_Fund.xml").toFile());

        assertFalse(result.isError(), () -> "CSV pipeline failed: " + result.text());
        assertTrue(result.text().startsWith(
                        "FundISIN,FundName,PositionID,ISIN,AssetName,AssetClass,Currency,TotalValue,Percentage"),
                "CSV output should start with the header row");
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
