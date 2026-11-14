package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.plugin.ElementDescriber;
import org.fxt.freexmltoolkit.plugins.regreport.CatalogDescriber;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The FMA's AIFMD rules on the bundled (fictitious) examples, with planted violations. */
class AifmdRulesTest {

    private static String example(String name) throws Exception {
        try (InputStream in = AifmdRulesTest.class.getResourceAsStream("/plugins/kagmeldewesen/examples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        return KagMeldewesenPlugin.rulesFor(RegValidator.rootOf(xml)).run(xml, "report.xml");
    }

    private static int lineOf(String xml, String marker) {
        return (int) xml.substring(0, xml.indexOf(marker)).chars().filter(c -> c == '\n').count() + 1;
    }

    private static RegRuleSet.Finding find(List<RegRuleSet.Finding> findings, String id) {
        return findings.stream().filter(f -> f.ruleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " not reported: " + findings));
    }

    @Test
    void theExamplesPass() throws Exception {
        assertEquals(List.of(), run(example("aif-example.xml")));
        assertEquals(List.of(), run(example("aifm-example.xml")));
    }

    @Test
    void headerAndPeriodRules() throws Exception {
        String xml = example("aif-example.xml")
                .replace("ReportingMemberState=\"AT\"", "ReportingMemberState=\"DE\"")
                .replace("<ReportingPeriodStartDate>2025-01-01", "<ReportingPeriodStartDate>2025-02-01")
                .replace("<InceptionDate>2002-02-13", "<InceptionDate>2025-03-01");
        List<RegRuleSet.Finding> findings = run(xml);
        assertEquals(lineOf(xml, "<AIFReportingInfo"), find(findings, "FMA-AIF-1").line());
        assertTrue(find(findings, "FMA-AIF-6").message().contains("expected 2025-01-01"));
        RegRuleSet.Finding inception = find(findings, "FMA-AIF-22");
        assertEquals(lineOf(xml, "<InceptionDate>"), inception.line());
        assertTrue(inception.message().startsWith("Field 22 · Inception Date"), inception.message());
    }

    @Test
    void conditionalFieldsSumsAndRankings() throws Exception {
        String xml = example("aif-example.xml")
                .replace("<BaseCurrency>EUR</BaseCurrency>", "<BaseCurrency>USD</BaseCurrency>")
                .replace("<StrategyNAVRate>60.00</StrategyNAVRate>", "<StrategyNAVRate>50.00</StrategyNAVRate>")
                .replace("<EEANAVRate>100</EEANAVRate>", "<EEANAVRate>90</EEANAVRate>")
                .replace("<AggregatedValueAmount>10175125</AggregatedValueAmount>",
                        "<AggregatedValueAmount>97478154</AggregatedValueAmount>")
                .replace("<AIFNetAssetValue>79572550</AIFNetAssetValue>", "<AIFNetAssetValue>99572550</AIFNetAssetValue>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "FMA-AIF-50").message().contains("is missing although the base currency is not EUR"));
        assertTrue(find(findings, "FMA-AIF-60").message().contains("add up to 90 %"));
        assertTrue(find(findings, "FMA-AIF-78").message().contains("add up to 90 %"));
        // the amount occurs in the principal exposures (field 98) and the portfolio concentrations (field 108)
        assertEquals(lineOf(xml, "<AggregatedValueAmount>97478154"), find(findings, "FMA-AIF-98").line());
        assertTrue(find(findings, "FMA-AIF-108").line() > find(findings, "FMA-AIF-98").line());
        assertTrue(find(findings, "FMA-AIF-53").message().contains("larger than the AuM"));
    }

    @Test
    void identifiersAndBlocks() throws Exception {
        String xml = example("aif-example.xml")
                .replace("<AIFIdentifierLEI>5299009FXTAIF0000045", "<AIFIdentifierLEI>5299009FXTAIF0000046")
                .replace("<AIFContentType>3</AIFContentType>", "<AIFContentType>2</AIFContentType>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "FMA-AIF-24").message().contains("check digits"));
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("FMA-AIF-5") && f.message().contains("AIFIndividualInfo")));
        String noReporting = example("aif-example.xml").replace("<AIFNoReportingFlag>false", "<AIFNoReportingFlag>true");
        assertEquals(lineOf(noReporting, "<AIFCompleteDescription>"), find(run(noReporting), "FMA-AIF-23").line());
    }

    @Test
    void aifmFileRules() throws Exception {
        String xml = example("aifm-example.xml")
                .replace("<MarketCodeType>XXX</MarketCodeType>", "<MarketCodeType>MIC</MarketCodeType>")
                .replace("<ReportingPeriodEndDate>2025-12-31", "<ReportingPeriodEndDate>2026-01-31");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "FMA-AIFM-28").message().contains("is missing although the market code type is MIC"));
        assertTrue(find(findings, "FMA-AIFM-7").message().contains("outside the period"));
    }

    @Test
    void periodsOfTheReportingTypes() {
        assertEquals("2025-04-01", AifmdRules.periodStart("Q2", 2025).toString());
        assertEquals("2025-06-30", AifmdRules.periodEnd("H1", 2025).toString());
        assertEquals("2025-09-30", AifmdRules.periodEnd("X1", 2025).toString());
        assertNull(AifmdRules.periodStart("X2", 2025));
        assertEquals(java.time.Month.MARCH, AifmdRules.monthOf("QuantityMarch"));
        assertNull(AifmdRules.monthOf("Ranking"));
    }

    @Test
    void recognitionBadgeAndFieldLabels(@TempDir Path tmp) throws Exception {
        String aif = example("aif-example.xml");
        assertEquals("AIFMD AIF Y1/2025", KagMeldewesenPlugin.label(aif));
        assertTrue(KagMeldewesenPlugin.describe(aif).contains("INIT · Y1/2025 · 1 record"), KagMeldewesenPlugin.describe(aif));
        assertNull(KagMeldewesenPlugin.label("<Document xmlns='urn:x'/>"));
        assertNull(KagMeldewesenPlugin.rulesFor(new RegValidator.Root("AIFReportingInfo", "urn:other")));

        FieldCatalog fields = KagMeldewesenPlugin.AIF_FIELDS.get();
        assertTrue(fields.fields().size() > 250);
        CatalogDescriber describer = new CatalogDescriber(() -> true, List.of(KagMeldewesenPlugin.AIF_FIELDS));
        String text = describer.describe(new ElementDescriber.ElementInfo(List.of("AIFReportingInfo", "AIFRecordInfo", "InceptionDate"),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), "2002-02-13", List.of()));
        assertTrue(text.startsWith("Field 22 · Inception Date"), text);
        assertTrue(text.contains("Rule: Das InceptionDate muss vor dem ReportingPeriodStartDate liegen"), text);

        System.setProperty("fxt.regreport.dir", tmp.toString());
        try {
            Path dir = org.fxt.freexmltoolkit.plugins.regreport.BundledSchemas.install(KagMeldewesenPlugin.ID, "aifmd-1.2",
                    KagMeldewesenPlugin.AIFMD_RESOURCES, KagMeldewesenPlugin.AIFMD_SCHEMAS);
            for (String schema : KagMeldewesenPlugin.AIFMD_SCHEMAS) {
                assertTrue(Files.isRegularFile(dir.resolve(schema)), schema);
            }
        } finally {
            System.clearProperty("fxt.regreport.dir");
        }
        assertNotNull(fields.byPath("AIFReportingInfo/AIFRecordInfo/AIFContentType"));
    }
}
