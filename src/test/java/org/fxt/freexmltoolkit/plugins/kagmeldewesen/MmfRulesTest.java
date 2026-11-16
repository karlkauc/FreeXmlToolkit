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

    private static RegRuleSet.Finding find(List<RegRuleSet.Finding> findings, String id, String severity) {
        RegRuleSet.Finding f = find(findings, id);
        assertEquals(severity, f.severity(), f.toString());
        return f;
    }

    @Test
    void fundDataLeisAndMasterFeeder() throws Exception {
        String mmfLei = "<LEI>5299009FXTMMFCP00069</LEI>";
        String xml = example()
                .replace("5299009FXTCTRPY00209", "5299009FXTMMFCP00069")
                .replace("<MstrFnd>NONE</MstrFnd>", "<MstrFnd>FDER</MstrFnd>")
                .replace("<LEI>5299009FXTDEPOSIT173</LEI>", "")
                .replace("<LEI>5299009FXTDPSTCP0187</LEI>", "<Nm>Deposit bank</Nm>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "ESMA-MMF-16", "warning").message().contains("CtrPtyData"));
        assertTrue(find(findings, "ESMA-MMF-42", "error").message().contains("feeder"));
        find(findings, "ESMA-MMF-45", "error");
        find(findings, "ESMA-MMF-37", "error");
        find(findings, "ESMA-MMF-198", "warning");

        String noLei = example().replaceFirst(java.util.regex.Pattern.quote(mmfLei), "");
        find(run(noLei), "ESMA-MMF-16", "error");
        assertTrue(noLei.contains("<RptData>"));
        find(run(noLei.replace("<RptData>", "<RptData><DataSetActn>NOTX</DataSetActn>")), "ESMA-MMF-16", "warning");
    }

    @Test
    void assetsCfiCreditAssessmentAndCollateral() throws Exception {
        String xml = example()
                .replace("<ClssfctnTp>DBXXXX</ClssfctnTp>", "<ClssfctnTp>LRXXXX</ClssfctnTp>")
                .replaceFirst("<CdtAssmntRslt>FVRB</CdtAssmntRslt>", "<CdtAssmntRslt>UFVB</CdtAssmntRslt>")
                .replace("<AsstTp>RVPO</AsstTp>", "<AsstTp>REPO</AsstTp>");
        List<RegRuleSet.Finding> findings = run(xml);
        RegRuleSet.Finding cfi = find(findings, "ESMA-MMF-93", "error");
        assertEquals(lineOf(xml, "<ClssfctnTp>LRXXXX"), cfi.line());
        assertTrue(cfi.message().contains("MMII"), cfi.message());
        assertTrue(find(findings, "ESMA-MMF-145", "warning").message().contains("UFVB"));
        assertTrue(find(findings, "ESMA-MMF-231", "error").message().contains("RVPO"));

        assertEquals(java.util.Set.of("SCRT", "STSS", "MMII"), MmfContentRules.allowedTypes("DBZXXX"));
        assertEquals(java.util.Set.of("OTCD"), MmfContentRules.allowedTypes("HFRXXX"));
        assertEquals(null, MmfContentRules.allowedTypes("ESVUFR"));
    }

    @Test
    void stressTestsYieldsAndPeriods() throws Exception {
        String xml = example()
                .replace("<StrssTstDt>2020-12-31</StrssTstDt>", "<StrssTstDt>2019-11-30</StrssTstDt>")
                .replace("<StrssTstScnroCd>LST-01</StrssTstScnroCd>", "<StrssTstScnroCd>CST-01</StrssTstScnroCd>")
                .replace("<RgTp>LT5Y</RgTp>", "<RgTp>LT1M</RgTp>");
        List<RegRuleSet.Finding> findings = run(xml);
        List<String> dates = findings.stream().filter(f -> f.ruleId().equals("ESMA-MMF-80")).map(RegRuleSet.Finding::message).toList();
        assertTrue(dates.stream().anyMatch(m -> m.contains("more than a year")), dates.toString());
        assertTrue(dates.stream().anyMatch(m -> m.contains("2020-12-31")), dates.toString());
        assertTrue(find(findings, "ESMA-MMF-83", "error").message().contains("LST-01"));
        assertTrue(find(findings, "ESMA-MMF-68", "error").message().contains("twice"));

        String one = example();
        int start = one.indexOf("<FndRpt>");
        int end = one.indexOf("</FndRpt>") + "</FndRpt>".length();
        String twice = one.substring(0, end) + "\n" + one.substring(start, end) + one.substring(end);
        assertTrue(find(run(twice), "ESMA-MMF-6", "error").message().contains("overlaps"));
    }

    @Test
    void liabilitiesSumsArrangementsAndQuarters() throws Exception {
        String xml = example()
                .replace("<BkRate>10</BkRate>", "<BkRate>20</BkRate>")
                .replace("<ArrgmntTp>SPRN</ArrgmntTp>", "<ArrgmntTp>GATE</ArrgmntTp>")
                .replace("<NtceDays>2</NtceDays>", "<NtceDays>0</NtceDays>")
                .replace("<ToPrd>QTR4</ToPrd>", "<ToPrd>QTR3</ToPrd>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "ESMA-MMF-242", "error").message().contains("110"));
        assertTrue(find(findings, "ESMA-MMF-258", "error").message().contains("GATE"));
        find(findings, "ESMA-MMF-257", "warning");
        assertTrue(find(findings, "ESMA-MMF-270", "error").message().contains("outside"), findings.toString());
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
