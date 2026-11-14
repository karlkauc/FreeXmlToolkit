package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.fxt.freexmltoolkit.plugins.regreport.CatalogChecks;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;

/** ESMA's money market fund report rules on the bundled (fictitious) example, with planted violations. */
class MmfRulesTest {

    private static String example() throws Exception {
        try (InputStream in = MmfRulesTest.class.getResourceAsStream("/plugins/kagmeldewesen/examples/mmf-example.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        RegRuleSet rules = KagMeldewesenPlugin.rulesFor(RegValidator.rootOf(xml), xml);
        assertNotNull(rules, "recognised as MMF report");
        return rules.run(xml, "mmf.xml");
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
        assertEquals("MMF Q1–Q4/2020", KagMeldewesenPlugin.label(xml));
        assertTrue(KagMeldewesenPlugin.describe(xml).contains("1 fund report"), KagMeldewesenPlugin.describe(xml));
    }

    @Test
    void headerCodesAndValuation() throws Exception {
        String xml = example()
                .replace("<Id>EU</Id>", "<Id>XY</Id>")
                .replace("<BaseCcy>EUR</BaseCcy>", "<BaseCcy>EUX</BaseCcy>")
                .replace("<ValtnTp>MTMA</ValtnTp>", "<ValtnTp>MTMO</ValtnTp>")
                .replace("<RptgYr>2020</RptgYr>", "<RptgYr>2019</RptgYr>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "ESMA-MMF-2").message().contains("\"EU\""));
        RegRuleSet.Finding currency = find(findings, "ESMA-MMF-36");
        assertEquals(lineOf(xml, "<BaseCcy>EUX"), currency.line());
        assertTrue(currency.message().contains("no ISO 4217 currency code"), currency.message());
        assertTrue(find(findings, "ESMA-MMF-109").message().contains("MTMO"));
        assertTrue(find(findings, "ESMA-MMF-8").message().contains("2019"));
    }

    @Test
    void liquidityProfileAndDuplicates() throws Exception {
        String xml = example().replaceFirst("<Rate>25</Rate>", "<Rate>35</Rate>");
        assertTrue(find(run(xml), "ESMA-MMF-66").message().contains("110"));

        String one = example();
        int start = one.indexOf("<FndRpt>");
        int end = one.indexOf("</FndRpt>") + "</FndRpt>".length();
        String twice = one.substring(0, end) + "\n" + one.substring(start, end) + one.substring(end);
        RegRuleSet.Finding duplicate = find(run(twice), "ESMA-MMF-12");
        assertTrue(duplicate.line() > lineOf(twice, "<FndRpt>"));
    }

    @Test
    void codeChecks() {
        assertEquals(null, CatalogChecks.problem("currency", "EUR"));
        assertFalse(CatalogChecks.problem("currency", "EUX") == null);
        assertEquals(null, CatalogChecks.problem("eu-country", "AT"));
        assertFalse(CatalogChecks.problem("eu-country", "CH") == null);
        assertEquals(null, CatalogChecks.problem("country", "CH"));
        assertFalse(CatalogChecks.problem("isin", "AT0000000001") == null);
        assertEquals(1, MmfRules.quarter("QTR1"));
        assertTrue(MmfRules.isMmf("Document", "urn:iso:std:iso:20022:tech:xsd:DRAFT6auth.093.001.01", ""));
        assertFalse(MmfRules.isMmf("BizData", "urn:iso:std:iso:20022:tech:xsd:head.003.001.01", "<BizData><Pyld><Document><FinInstrmRptgTxRpt/>"));
    }
}
