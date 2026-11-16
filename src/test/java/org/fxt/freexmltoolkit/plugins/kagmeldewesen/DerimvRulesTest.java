package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;

/** The 4. DeRiMV derivative risk report: the bundled (fictitious) example, planted violations, schema and labels. */
class DerimvRulesTest {

    private static final RegRuleSet RULES = DerimvRules.rules(() -> LocalDate.of(2026, 3, 1));

    private static String example() throws Exception {
        try (InputStream in = DerimvRulesTest.class.getResourceAsStream("/plugins/kagmeldewesen/examples/derimv-example.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        return RULES.run(xml, "derimv.xml");
    }

    private static int lineOf(String xml, String marker) {
        return (int) xml.substring(0, xml.indexOf(marker)).chars().filter(c -> c == '\n').count() + 1;
    }

    private static List<RegRuleSet.Finding> findings(List<RegRuleSet.Finding> all, String id) {
        return all.stream().filter(f -> f.ruleId().equals(id)).toList();
    }

    @Test
    void theExamplePassesAndIsRecognised() throws Exception {
        String xml = example();
        assertEquals(List.of(), run(xml));
        assertNotNull(KagMeldewesenPlugin.rulesFor(RegValidator.rootOf(xml), xml), "recognised by the plugin");
        assertEquals("DeRiMV Q4/2025", KagMeldewesenPlugin.label(xml));
        assertTrue(new KagMeldewesenPlugin().recognises(xml), "offered while switched off");
        assertTrue(!new KagMeldewesenPlugin().recognises("<other/>"));
        String described = KagMeldewesenPlugin.describe(xml);
        assertTrue(described.contains("4 funds") && described.contains("Beispiel Kapitalanlagegesellschaft"), described);
    }

    @Test
    void theExampleIsValidAgainstTheBundledSchema() throws Exception {
        var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(DerimvRulesTest.class.getResource("/plugins/kagmeldewesen/derimv/Derivatemeldung.xsd"));
        schema.newValidator().validate(new StreamSource(new StringReader(example())));
    }

    @Test
    void isinCheckDigitAndDuplicates() throws Exception {
        String xml = example().replace("<ISIN>AT0000A1B2D2</ISIN>", "<ISIN>AT0000A1B2C4</ISIN>")
                .replace("<ISIN>AT0000A1B2E0</ISIN>", "<ISIN>AT0000A1B2E1</ISIN>");
        List<RegRuleSet.Finding> isin = findings(run(xml), "DeRiMV-4");
        assertEquals(2, isin.size(), isin.toString());
        assertTrue(isin.get(0).message().contains("reported twice"), isin.get(0).message());
        assertTrue(isin.get(1).message().contains("check digit"), isin.get(1).message());
        assertEquals(lineOf(xml, "AT0000A1B2E1"), isin.get(1).line());
    }

    @Test
    void exposureLimitsPerMethod() throws Exception {
        String xml = example()
                .replaceFirst("<MaxRisikoangabe>18.75</MaxRisikoangabe>", "<MaxRisikoangabe>101.00</MaxRisikoangabe>")
                .replace("<MaxRisikoangabe>6.85</MaxRisikoangabe>", "<MaxRisikoangabe>21.50</MaxRisikoangabe>")
                .replace("<MaxRisikoangabe>131.40</MaxRisikoangabe>", "<MaxRisikoangabe>215.00</MaxRisikoangabe>");
        List<RegRuleSet.Finding> all = run(xml);
        List<RegRuleSet.Finding> limits = findings(all, "DeRiMV-7");
        assertEquals(3, limits.size(), all.toString());
        assertTrue(limits.get(0).message().contains("100 %") && limits.get(0).severity().equals(RegRuleSet.ERROR));
        assertEquals(lineOf(xml, "101.00"), limits.get(0).line());
        assertTrue(limits.get(1).message().contains("20 %") && limits.get(1).severity().equals(RegRuleSet.ERROR));
        assertTrue(limits.get(2).message().contains("Relative VaR") && limits.get(2).severity().equals(RegRuleSet.WARNING));
        // the absolute VaR fund also exceeds its own limit of 10 %
        assertTrue(findings(all, "DeRiMV-9").stream().anyMatch(f -> f.message().contains("limit of the fund rules (10 %)")), all.toString());
    }

    @Test
    void exposureAboveThePeriodMaximumAndFundLimitAboveTheLaw() throws Exception {
        String xml = example().replace("<Risikoangabe>4.10</Risikoangabe>", "<Risikoangabe>7.00</Risikoangabe>")
                .replace("<GrenzenGesamtrisiko>10.00</GrenzenGesamtrisiko>", "<GrenzenGesamtrisiko>25.00</GrenzenGesamtrisiko>");
        List<RegRuleSet.Finding> all = run(xml);
        assertTrue(findings(all, "DeRiMV-7").stream().anyMatch(f -> f.message().contains("lower than the exposure")), all.toString());
        assertTrue(findings(all, "DeRiMV-9").stream().anyMatch(f -> f.message().contains("above the legal limit")), all.toString());
    }

    @Test
    void reportingPeriod() throws Exception {
        List<RegRuleSet.Finding> future = run(example().replace("Jahr=\"2025\" Quartal=\"4\"", "Jahr=\"2026\" Quartal=\"2\""));
        assertTrue(findings(future, "DeRiMV-period").getFirst().message().contains("future"), future.toString());
        List<RegRuleSet.Finding> old = run(example().replace("Jahr=\"2025\" Quartal=\"4\"", "Jahr=\"2016\" Quartal=\"3\""));
        assertTrue(findings(old, "DeRiMV-period").getFirst().message().contains("31 December 2016"), old.toString());
        assertEquals(List.of(), run(example().replace("Jahr=\"2025\" Quartal=\"4\"", "Jahr=\"2016\" Quartal=\"4\"")));
    }

    @Test
    void checkReportListsFindingsAndTheFundTable() throws Exception {
        String xml = example().replace("<MaxRisikoangabe>6.85</MaxRisikoangabe>", "<MaxRisikoangabe>9.50</MaxRisikoangabe>")
                .replace("<ISIN>AT0000A1B2E0</ISIN>", "<ISIN>AT0000A1B2E1</ISIN>");
        String html = KagMeldewesenPlugin.report(xml, "derimv.xml");
        assertTrue(html.contains("Derivative risk report (4. DeRiMV)"), html);
        assertTrue(html.contains(">DeRiMV-4<") && html.contains("Field 4 · ISIN"), html);
        assertTrue(html.contains("<h2>Funds and limits</h2>"), html);
        // the absolute VaR fund uses 9.5 of its own 10 %: 95 % → marked from 90 %, listed first
        assertTrue(html.contains("<span class=\"badge warning\">95 %</span>"), html);
        assertTrue(html.indexOf("AT0000A1B2E1") < html.indexOf("AT0000A1B2C4"), html);
        assertEquals(null, KagMeldewesenPlugin.report("<x/>", "x.xml"));
    }

    @Test
    void optionsMoveTheRelativeVarLimitAndWarnCloseToALimit() throws Exception {
        RegRuleSet strict = DerimvRules.rules(() -> LocalDate.of(2026, 3, 1),
                new DerimvRules.Options(new java.math.BigDecimal(120), new java.math.BigDecimal(60)));
        List<RegRuleSet.Finding> all = strict.run(example(), "derimv.xml");
        assertTrue(findings(all, "DeRiMV-7").stream().anyMatch(f -> f.message().contains("above 120 %")), all.toString());
        // aVaR 6.85 of the own 10 % = 68.5 % → warned; the commitment funds use 18.75 % → not
        List<RegRuleSet.Finding> use = findings(all, "DeRiMV-use");
        assertEquals(1, use.size(), all.toString());
        assertTrue(use.getFirst().message().contains("68.5 % of the fund's own limit (10 %)"), use.toString());
        assertEquals(List.of(), findings(run(example()), "DeRiMV-use"), "off by default");
    }

    @Test
    void fieldLabels() {
        FieldCatalog catalog = KagMeldewesenPlugin.DERIMV_FIELDS.get();
        assertEquals("Field 6 · Risikoangabe", catalog.byPath("Derivatemeldung/InvestmentFonds/Risikoangabe").label());
        assertNotNull(catalog.byPath("Derivatemeldung/InvestmentFonds/GrenzenGesamtrisiko"));
        assertSame(KagMeldewesenPlugin.DERIMV_FIELDS.get(), catalog);
    }
}
