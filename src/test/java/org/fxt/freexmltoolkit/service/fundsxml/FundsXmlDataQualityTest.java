package org.fxt.freexmltoolkit.service.fundsxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.XmlParser;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridFilters;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridFormulas;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The FundsXML data-quality checks and the grid formulas / filters stylesheet. */
@DisplayName("FundsXML data quality and grid setup")
class FundsXmlDataQualityTest {

    static String fixture() throws Exception {
        try (InputStream in = FundsXmlDataQualityTest.class.getResourceAsStream("/fundsxml/dq-fixture.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** check key → findings, for checks with findings or errors */
    static Map<String, List<String>> findings(String xml) {
        return FundsXmlDataQuality.run(xml).stream().filter(r -> !r.passed()).collect(Collectors.toMap(
                r -> r.check().key, r -> r.error() != null ? List.of("ERROR " + r.error()) : r.findings()));
    }

    @Test
    @DisplayName("a consistent document passes every check")
    void cleanDocument() throws Exception {
        String xml = fixture();
        assertTrue(FundsXmlDataQuality.isFundsXml(xml));
        assertEquals(17, FundsXmlDataQuality.checks().size());
        assertEquals(Map.of(), findings(xml));
    }

    @Test
    @DisplayName("each kind of problem is found by its check")
    void problems() throws Exception {
        String xml = fixture()
                .replace("<ISIN>DE0005140008</ISIN></Identifiers>\n                <Currency>", "<ISIN>DE0005140009</ISIN></Identifiers>\n                <Currency>")
                .replace("529900T8BM49AURSDO55", "529900T8BM49AURSDO56")
                .replace("<TotalPercentage>40.00</TotalPercentage>", "<TotalPercentage>30.00</TotalPercentage>")
                .replace("<Amount ccy=\"EUR\">4000000.00</Amount>", "<Amount ccy=\"EUR\">-4000000.00</Amount>")
                .replace("<ValutaDate>2025-10-01</ValutaDate>", "<ValutaDate>2025-09-01</ValutaDate>")
                .replace("<ContentDate>2025-10-01</ContentDate>", "<ContentDate>2999-01-01</ContentDate>")
                .replace("<UniqueID>P2</UniqueID><Identifiers>", "<UniqueID>X9</UniqueID><Identifiers>");
        Map<String, List<String>> found = findings(xml);
        assertEquals(List.of("Position P2: ISIN DE0005140009 is invalid"), found.get("isin"));
        assertTrue(found.get("lei").getFirst().contains("529900T8BM49AURSDO56"), found.toString());
        assertEquals(List.of("Fund Test Fund, 2025-10-01: the position weights add up to 90 %"), found.get("weights"));
        assertTrue(found.get("value-vs-nav").getFirst().contains("positions add up to 2000000"), found.toString());
        assertEquals(List.of("Position P2 has a negative value: -4000000"), found.get("negative-values"));
        assertEquals(List.of("Transaction T1: ValutaDate 2025-09-01 is before EntryDate 2025-09-29"), found.get("transaction-dates"));
        assertEquals(List.of("ContentDate 2999-01-01 is in the future"), found.get("content-date"));
        assertEquals(List.of("Position P2 has no Asset in AssetMasterData"), found.get("asset-master"));
        assertTrue(found.get("unused-assets").getFirst().startsWith("Asset X9"), found.toString());
        assertFalse(found.values().stream().flatMap(List::stream).anyMatch(f -> f.startsWith("ERROR")), found.toString());

        String duplicate = fixture().replace("<UniqueID>P2</UniqueID>\n                <Identifiers>", "<UniqueID>P1</UniqueID>\n                <Identifiers>");
        assertTrue(findings(duplicate).get("duplicate-positions").getFirst().contains("position P1 appears 2 times"));
    }

    @Test
    @DisplayName("the report lists every check; the snippets are registered with the fundsxml tag")
    void reportAndSnippets() throws Exception {
        String html = FundsXmlDataQuality.htmlReport(FundsXmlDataQuality.run(fixture()), "fund.xml");
        assertTrue(html.contains("17 checks · all passed") && html.contains("ISIN check digits"), html);
        assertEquals(25, FundsXmlDataQuality.checks().size() + FundsXmlDataQuality.snippets().size());
        assertTrue(FundsXmlDataQuality.snippets().stream().allMatch(s -> s.query != null && !s.query.isBlank()));
    }

    @Test
    @DisplayName("grid setup: formulas and filters that evaluate without errors; running it again changes nothing")
    void gridSetup() throws Exception {
        String once = FundsXmlGridSetup.apply(fixture(), true);
        assertEquals(once, FundsXmlGridSetup.apply(once, true), "idempotent");
        assertFalse(FundsXmlGridSetup.apply(fixture(), false).contains("fxt-filter"));
        var document = new XmlParser().parse(once);
        Map<String, String> results = new java.util.LinkedHashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        collect(document.getRootElement(), results, errors);
        assertEquals(List.of(), errors);
        assertEquals("10,000,000.00 EUR", results.get("Fund/Net asset value"));
        assertEquals("10,000,000.00 EUR", results.get("Fund/Positions value"));
        assertEquals("100.00", results.get("Positions/Weight %"));
        assertEquals("P1 (60.00 %)", results.get("Positions/Largest"));
        assertEquals("EQ 2", results.get("AssetMasterData/By asset type"));
        assertEquals("BUY 1", results.get("Transactions/By kind"));
        assertTrue(once.contains("<?fxt-filter expression=\"Position[xs:decimal(TotalPercentage) gt 5]\"?>"), once);
        assertFalse(FundsXmlGridSetup.isSigned(once));
    }

    private static void collect(XmlElement element, Map<String, String> results, List<String> errors) {
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlProcessingInstruction pi && GridFormulas.isFormula(pi)) {
                var evaluated = GridFormulas.evaluate(pi);
                if (evaluated.error() != null) {
                    errors.add(element.getName() + "/" + evaluated.formula().name() + ": " + evaluated.error());
                }
                results.put(element.getName() + "/" + evaluated.formula().name(), evaluated.result());
            } else if (child instanceof XmlProcessingInstruction pi && GridFilters.isFilter(pi)) {
                try {
                    org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlGridQuery.evaluate(element, GridFilters.parse(pi.getData()));
                } catch (IllegalArgumentException e) {
                    errors.add(element.getName() + " filter: " + e.getMessage());
                }
            } else if (child instanceof XmlElement e) {
                collect(e, results, errors);
            }
        }
    }

    @Test
    @DisplayName("with the fundsxml/examples repository (-Dfundsxml.examples=…): every check and formula runs")
    void realExamples() throws Exception {
        String dir = System.getProperty("fundsxml.examples");
        Assumptions.assumeTrue(dir != null && Files.isDirectory(Path.of(dir)), "examples not available");
        try (Stream<Path> files = Files.walk(Path.of(dir))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                String xml = Files.readString(file);
                if (!FundsXmlDataQuality.isFundsXml(xml)) {
                    continue;
                }
                FundsXmlDataQuality.run(xml).forEach(r -> assertEquals(null, r.error(), file + " " + r.check().key));
                List<String> errors = new java.util.ArrayList<>();
                collect(new XmlParser().parse(FundsXmlGridSetup.apply(xml, true)).getRootElement(),
                        new java.util.HashMap<>(), errors);
                assertEquals(List.of(), errors, file.toString());
            }
        }
    }
}
