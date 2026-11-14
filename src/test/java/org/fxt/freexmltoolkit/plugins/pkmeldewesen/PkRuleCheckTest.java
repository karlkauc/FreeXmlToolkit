package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The FMA rules of the quarterly report on a synthetic report with planted violations
 * ({@code pkmeldewesen/rules-pfq.xml}: fictitious pension fund 99, no real data).
 */
class PkRuleCheckTest {

    private static String xml;
    private static List<PkRuleCheck.Finding> findings;

    @BeforeAll
    static void run() throws Exception {
        try (InputStream in = PkRuleCheckTest.class.getResourceAsStream("/pkmeldewesen/rules-pfq.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        findings = PkRuleCheck.run(xml, "rules-pfq.xml", PkRuleCheck.Options.defaults());
    }

    /** @return the line of the {@code Datenpunkt} start tag holding {@code marker}, searched after {@code from} */
    private static int lineOfEntry(String from, String marker) {
        int start = xml.indexOf(from);
        int at = xml.indexOf(marker, start);
        int entry = xml.lastIndexOf("<Datenpunkt", at);
        return lineAt(entry);
    }

    private static int lineAt(int offset) {
        return (int) xml.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static PkRuleCheck.Finding find(String id, String vrg) {
        return findings.stream().filter(f -> f.rule().id().equals(id) && f.vrg().equals(vrg)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " / VRG " + vrg + " not reported: " + findings));
    }

    @Test
    void sumOfFormblatt800IsReportedOnItsRow() {
        PkRuleCheck.Finding f = find("PK_186", "1");
        assertEquals("E", f.status());
        assertEquals("error", f.severity());
        assertEquals("R800-100", f.object());
        assertEquals(lineOfEntry("<PK.800.01_Vermögensausweis VRG=\"1\">", "<RowCode>R800-100<"), f.line());
        assertTrue(f.detail().contains("1.000,00 €") && f.detail().contains("950,00 €") && f.detail().contains("50,00 €"),
                f.detail());
    }

    @Test
    void signConventionAndFormblatt900() {
        PkRuleCheck.Finding sign = find("PK_41", "1");
        assertEquals(lineOfEntry("<PK.800.01_Vermögensausweis VRG=\"1\">", "<RowCode>R800-140<"), sign.line());
        assertEquals("Wert muss negativ sein", sign.detail());
        PkRuleCheck.Finding count = find("PK_188", "0");
        assertEquals(lineOfEntry("<PK.900.01_Anwartschafts-_und_Leistungsberechtigte_im_Quartal VRG=\"0\">",
                "<RowCode>R900-100<"), count.line());
        assertTrue(count.detail().contains("10") && count.detail().contains("9"), count.detail());
    }

    @Test
    void assetFindingsAreOnTheirEntry() {
        PkRuleCheck.Finding isin = find("PK_VA22", "1");
        assertEquals("AT0000000002", isin.object());
        assertEquals(lineOfEntry("<PK.006.02.01.01_", "AT0000000002"), isin.line());
        PkRuleCheck.Finding orphan = find("INT_A02", "1");
        assertEquals("warning", orphan.severity());
        assertEquals(lineOfEntry("<PK.006.02.01.02_", "XS0000000009"), orphan.line());
        assertTrue(orphan.rule().additional());
    }

    @Test
    void headerChecksAreOnTheRootElement() {
        PkRuleCheck.Finding lei = find("PK_VA23", "gesamt");
        assertEquals(lineAt(xml.indexOf("<Nationale_Meldung_pfq")), lei.line());
        assertTrue(lei.message().startsWith("PK_VA23 LEI-Code (Attribut) – "), lei.message());
    }

    @Test
    void keyFiguresWithoutLimitAreNoFindings() {
        assertTrue(findings.stream().noneMatch(f -> f.rule().id().equals("INT_AA2")));
        assertTrue(findings.stream().allMatch(f -> f.line() > 0));
    }

    @Test
    void additionalChecksCanBeSwitchedOff() throws Exception {
        List<PkRuleCheck.Finding> fmaOnly = PkRuleCheck.run(xml, "rules-pfq.xml",
                PkRuleCheck.Options.defaults().withAdditional(false));
        assertTrue(fmaOnly.stream().noneMatch(f -> f.rule().additional()), fmaOnly.toString());
        assertEquals(findings.stream().filter(f -> !f.rule().additional()).count(), fmaOnly.size());
        String html = PkRuleCheck.htmlReport(xml, "rules-pfq.xml", PkRuleCheck.Options.defaults().withAdditional(false));
        assertTrue(html.contains("<html"));
        assertFalse(html.contains("INT_A02"));
        assertTrue(PkRuleCheck.htmlReport(xml, "rules-pfq.xml", PkRuleCheck.Options.defaults()).contains("INT_A02"));
    }

    @Test
    void toleranceDecidesBetweenErrorAndWarning() throws Exception {
        PkRuleCheck.Options lenient = new PkRuleCheck.Options(new java.math.BigDecimal("60"),
                new java.math.BigDecimal("10"), java.math.BigDecimal.TWO, java.math.BigDecimal.TEN, 25, true);
        PkRuleCheck.Finding f = PkRuleCheck.run(xml, "rules-pfq.xml", lenient).stream()
                .filter(x -> x.rule().id().equals("PK_186") && x.vrg().equals("1")).findFirst().orElseThrow();
        assertEquals("W", f.status());
    }

    @Test
    void catalogueKnowsEveryRule() {
        PkRuleCatalog catalog = PkRuleCatalog.pfq();
        assertEquals(180, catalog.rules().size());
        assertEquals("VA00033", catalog.rule("PK_64").code());
        assertEquals("INT_LT2", catalog.rule("INT_LT2").code());
        assertNotNull(catalog.rule("PBV38"));
    }

    @Test
    void validatorAcceptsQuarterlyReportsOnlyWhileOn() {
        PkRuleValidator on = new PkRuleValidator(() -> true, PkRuleCheck.Options::defaults);
        assertTrue(on.accepts(xml, "x.xml"));
        assertFalse(on.accepts("<Nationale_Meldung_pfa GJ='2025'/>", "x.xml"));
        assertFalse(new PkRuleValidator(() -> false, PkRuleCheck.Options::defaults).accepts(xml, "x.xml"));
        List<DocumentValidator.Problem> problems = on.validate(xml, "x.xml");
        assertEquals(findings.size(), problems.size());
        assertTrue(problems.stream().anyMatch(p -> p.message().startsWith("V80020 ") && p.message().contains("VRG 1 · R800-100")));
    }

    @Test
    void systemIdKeepsTheFileName() {
        assertTrue(PkRuleCheck.systemId("C:\\data\\4968_30_all.xml").endsWith("/4968_30_all.xml"));
        assertTrue(PkRuleCheck.systemId("/tmp/a b.xml").endsWith("/a%20b.xml"));
        assertEquals("file:/report.xml", PkRuleCheck.systemId(null));
    }

    @Test
    void numbersInTheGermanFormat() {
        assertEquals("1.234,50 €", PkRuleCheck.format("1234.5", "€"));
        assertEquals("12,00 %", PkRuleCheck.format("12", "%"));
        assertEquals("7", PkRuleCheck.format("7.0", "n"));
        assertEquals("x", PkRuleCheck.format("x", "€"));
    }
}
