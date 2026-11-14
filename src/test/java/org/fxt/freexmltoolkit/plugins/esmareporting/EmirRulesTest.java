package org.fxt.freexmltoolkit.plugins.esmareporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.plugin.ElementDescriber;
import org.fxt.freexmltoolkit.plugins.regreport.CatalogDescriber;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;

/** ESMA's EMIR Refit validation rules on the bundled (fictitious) error report, with planted violations. */
class EmirRulesTest {

    private static String example() throws Exception {
        try (InputStream in = EmirRulesTest.class.getResourceAsStream("/plugins/esmareporting/examples/emir-eror-example.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        RegRuleSet rules = EsmaReportingPlugin.rulesFor(RegValidator.rootOf(xml), xml);
        assertNotNull(rules, "recognised as EMIR report");
        return rules.run(xml, "emir.xml");
    }

    private static int lineOf(String xml, String marker) {
        return (int) xml.substring(0, xml.indexOf(marker)).chars().filter(c -> c == '\n').count() + 1;
    }

    private static RegRuleSet.Finding find(List<RegRuleSet.Finding> findings, String id) {
        return findings.stream().filter(f -> f.ruleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " not reported: " + findings));
    }

    @Test
    void theExamplePassesAndIsRecognised() throws Exception {
        String xml = example();
        assertEquals(List.of(), run(xml));
        assertEquals("EMIR · 1 report", EsmaReportingPlugin.label(xml));
        assertTrue(EsmaReportingPlugin.describe(xml).endsWith("1 × EROR"), EsmaReportingPlugin.describe(xml));
        assertEquals(Map.of("EROR", 1), EsmaReportingPlugin.actions(xml));
    }

    @Test
    void mandatoryAndNotApplicableFieldsPerActionType() throws Exception {
        String xml = example().replaceFirst("\\s*<RptgTmStmp>[^<]*</RptgTmStmp>", "");
        RegRuleSet.Finding missing = find(run(xml), "EMIR-1.1");
        assertEquals(lineOf(xml, "<Err>"), missing.line());
        assertTrue(missing.message().contains("mandatory for action type EROR"), missing.message());

        // the country of counterparty 2 is not applicable for EROR ("-" in ESMA's table)
        String country = example().replace("</IdTp>", "<Ntrl><Ctry>AT</Ctry></Ntrl></IdTp>");
        RegRuleSet.Finding forbidden = find(run(country), "EMIR-1.10");
        assertTrue(forbidden.message().contains("must not be reported for action type EROR"), forbidden.message());
        assertEquals(lineOf(country, "<Ntrl>"), forbidden.line());
    }

    private static String leiOf(String xml, int index) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<LEI>([A-Z0-9]{20})</LEI>").matcher(xml);
        for (int i = 0; i <= index; i++) {
            m.find();
        }
        return m.group(1);
    }

    @Test
    void headerUtiAndIdentifiers() throws Exception {
        String lei = leiOf(example(), 0);
        String xml = example()
                .replace("<NbRcrds>1</NbRcrds>", "<NbRcrds>3</NbRcrds>")
                .replaceFirst("<UnqTxIdr>[^<]*</UnqTxIdr>", "<UnqTxIdr>uti-lowercase</UnqTxIdr>")
                .replaceFirst("<LEI>" + lei + "</LEI>", "<LEI>" + lei.substring(0, 18) + "00</LEI>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertEquals(lineOf(xml, "<NbRcrds>"), find(findings, "EMIR-HDR").line());
        assertTrue(find(findings, "EMIR-2.1").message().contains("is no UTI"));
        assertTrue(find(findings, "EMIR-1.4").message().contains("check digits"), findings.toString());
        assertEquals("The header declares 2 records, the file holds 1.",
                EmirRules.countProblem(org.fxt.freexmltoolkit.plugins.regreport.RecordNode.of("RptHdr", 1).add("NbRcrds", 2, "2").parent(), 1));
    }

    @Test
    void envelopeAndFieldLabels() throws Exception {
        String bare = example();
        String body = bare.substring(bare.indexOf("<Document"));
        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<BizData xmlns=\"urn:iso:std:iso:20022:tech:xsd:head.003.001.01\">"
                + "<Hdr/><Pyld>" + body + "</Pyld></BizData>";
        assertEquals("EMIR", EsmaReportingPlugin.kind(RegValidator.rootOf(envelope), envelope));
        assertEquals(List.of(), run(envelope));
        assertNull(EsmaReportingPlugin.kind(RegValidator.rootOf("<a/>"), "<a/>"));

        CatalogDescriber describer = new CatalogDescriber(() -> true, List.of(EsmaReportingPlugin.EMIR_FIELDS));
        for (List<String> path : List.of(
                List.of("Document", "DerivsTradRpt", "TradData", "Rpt", "Err", "CmonTradData", "TxData", "TxId", "UnqTxIdr"),
                List.of("BizData", "Pyld", "Document", "DerivsTradRpt", "TradData", "Rpt", "Err", "CmonTradData", "TxData", "TxId", "UnqTxIdr"))) {
            String text = describer.describe(new ElementDescriber.ElementInfo(path, Map.of(), Map.of(), Map.of(), "x", List.of()));
            assertNotNull(text, String.valueOf(path));
            assertTrue(text.startsWith("Field 2.1 · UTI"), text);
        }
    }
}
