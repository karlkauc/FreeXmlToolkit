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
    @DisplayName("every finding knows the line of the node it is about; the Console query still returns text")
    void findingsCarryLines() throws Exception {
        String xml = fixture()
                .replace("<ISIN>DE0005140008</ISIN></Identifiers>\n                <Currency>", "<ISIN>DE0005140009</ISIN></Identifiers>\n                <Currency>")
                .replace("<ValutaDate>2025-10-01</ValutaDate>", "<ValutaDate>2025-09-01</ValutaDate>")
                .replace("<ContentDate>2025-10-01</ContentDate>", "<ContentDate>2999-01-01</ContentDate>");
        List<String> lines = xml.lines().toList();
        Map<String, FundsXmlDataQuality.Result> byKey = FundsXmlDataQuality.run(xml).stream()
                .filter(r -> !r.passed()).collect(Collectors.toMap(r -> r.check().key, r -> r));
        assertTrue(lines.get(byKey.get("isin").lineOf(0) - 1).contains("DE0005140009"), byKey.toString());
        int contentDate = byKey.get("content-date").lineOf(0);
        assertTrue(lines.get(contentDate - 1).contains("<ContentDate>2999-01-01"), contentDate + ": " + lines.get(contentDate - 1));
        int transaction = byKey.get("transaction-dates").lineOf(0);
        assertTrue(lines.get(transaction - 1).contains("<Transaction"), lines.get(transaction - 1));
        byKey.values().forEach(r -> {
            for (int i = 0; i < r.findings().size(); i++) {
                assertTrue(r.lineOf(i) > 0, r.check().key + ": " + r.findings().get(i));
            }
        });

        // the snippet / Query Console form of a check returns only the texts
        var check = FundsXmlDataQuality.checks().stream().filter(c -> c.key.equals("isin")).findFirst().orElseThrow();
        var processor = new net.sf.saxon.s9api.Processor(false);
        var evaluator = processor.newXQueryCompiler().compile(FundsXmlDataQuality.fullQuery(check)).load();
        evaluator.setContextItem(processor.newDocumentBuilder().build(
                new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml))));
        var value = evaluator.evaluate();
        assertEquals(1, value.size());
        assertTrue(value.itemAt(0).isAtomicValue());
        assertEquals("Position P2: ISIN DE0005140009 is invalid", value.itemAt(0).getStringValue());
    }

    @Test
    @DisplayName("the report lists every check; the snippets are registered with the fundsxml tag")
    void reportAndSnippets() throws Exception {
        String html = FundsXmlDataQuality.htmlReport(FundsXmlDataQuality.run(fixture()), "fund.xml");
        assertTrue(html.contains("17 checks · all passed") && html.contains("ISIN check digits"), html);
        assertEquals(25, FundsXmlDataQuality.checks().size() + FundsXmlDataQuality.snippets().size());
        assertTrue(FundsXmlDataQuality.snippets().stream().allMatch(s -> s.query != null && !s.query.isBlank()));
    }

    /** A share class with its own net asset value and its own portfolio (positions P1/P2 on 2025-10-01). */
    static String shareClass(String isin, String nav, String value1, String percent1, String value2, String percent2) {
        return """
                <ShareClass>
                  <Identifiers><ISIN>%s</ISIN></Identifiers>
                  <Currency>EUR</Currency>
                  <TotalAssetValues><TotalAssetValue><NavDate>2025-10-01</NavDate><TotalAssetNature>OFFICIAL</TotalAssetNature>
                    <TotalNetAssetValue><Amount ccy="EUR">%s</Amount></TotalNetAssetValue></TotalAssetValue></TotalAssetValues>
                  <Portfolios><Portfolio><NavDate>2025-10-01</NavDate><Positions>
                    <Position><UniqueID>P1</UniqueID><Identifiers><ISIN>US0378331005</ISIN></Identifiers><Currency>EUR</Currency>
                      <TotalValue><Amount ccy="EUR">%s</Amount></TotalValue><TotalPercentage>%s</TotalPercentage><Equity><Units>700</Units></Equity></Position>
                    <Position><UniqueID>P2</UniqueID><Identifiers><ISIN>DE0005140008</ISIN></Identifiers><Currency>EUR</Currency>
                      <TotalValue><Amount ccy="EUR">%s</Amount></TotalValue><TotalPercentage>%s</TotalPercentage><Equity><Units>1400</Units></Equity></Position>
                  </Positions></Portfolio></Portfolios>
                </ShareClass>
                """.formatted(isin, nav, value1, percent1, value2, percent2);
    }

    /** The fixture with two share classes (7 M + 3 M of the fund's 10 M), each with its own portfolio. */
    static String withShareClasses(String first, String second) throws Exception {
        return fixture().replace("</FundDynamicData>",
                "</FundDynamicData>\n<SingleFund><ShareClasses>" + first + second + "</ShareClasses></SingleFund>");
    }

    @Test
    @DisplayName("share-class portfolios are weighted and valued against their own share class, not the fund")
    void shareClassPortfoliosAgainstTheirOwnNode() throws Exception {
        String a = shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "60.00", "2800000.00", "40.00");
        String b = shareClass("AT0000A0K2H3", "3000000.00", "1800000.00", "60.00", "1200000.00", "40.00");
        // every portfolio adds up to 100 % and to its own node's net asset value
        assertEquals(Map.of(), findings(withShareClasses(a, b)));

        Map<String, List<String>> wrongWeights = findings(withShareClasses(a,
                shareClass("AT0000A0K2H3", "3000000.00", "1800000.00", "50.00", "1200000.00", "40.00")));
        assertEquals(List.of("Share class AT0000A0K2H3, 2025-10-01: the position weights add up to 90 %"),
                wrongWeights.get("weights"), wrongWeights.toString());
        assertEquals(1, wrongWeights.size(), wrongWeights.toString());

        Map<String, List<String>> wrongValue = findings(withShareClasses(
                shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "60.00", "2100000.00", "40.00"), b));
        assertEquals(1, wrongValue.get("value-vs-nav").size(), wrongValue.toString());
        assertTrue(wrongValue.get("value-vs-nav").getFirst().startsWith("Share class AT0000A0K2G5, 2025-10-01: positions add up to 6300000"),
                wrongValue.toString());

        Map<String, List<String>> wrongDate = findings(withShareClasses(a.replace(
                "<Portfolio><NavDate>2025-10-01</NavDate>", "<Portfolio><NavDate>2025-09-30</NavDate>"), b));
        assertEquals(List.of("Share class AT0000A0K2G5: no net asset value for the portfolio date 2025-09-30"),
                wrongDate.get("nav-dates"), wrongDate.toString());

        // the fund's grid formulas count the fund's own portfolio only
        String once = FundsXmlGridSetup.apply(withShareClasses(a, b), true);
        Map<String, String> results = new java.util.LinkedHashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        collect(new XmlParser().parse(once).getRootElement(), results, errors);
        assertEquals(List.of(), errors);
        assertEquals("10,000,000.00 EUR", results.get("Fund/Positions value"));
        assertEquals("100.00", results.get("Fund/Weights %"));
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
