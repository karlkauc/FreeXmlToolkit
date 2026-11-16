package org.fxt.freexmltoolkit.plugins.regreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The HTML check report: counts, findings grouped by rule with their field, escaping, extra sections. */
class RegReportHtmlTest {

    @Test
    void findingsAreGroupedByRuleWithTheirField() {
        FieldCatalog catalog = FieldCatalog.load("/plugins/kagmeldewesen/derimv/derimv-fields.json");
        List<RegRuleSet.Finding> findings = List.of(
                new RegRuleSet.Finding("DeRiMV-7", RegRuleSet.ERROR, 12, "Too high <100>"),
                new RegRuleSet.Finding("DeRiMV-10", RegRuleSet.WARNING, 3, "Unknown field"),
                new RegRuleSet.Finding("DeRiMV-7", RegRuleSet.WARNING, 30, "Second"),
                new RegRuleSet.Finding("DeRiMV-4", RegRuleSet.ERROR, 5, "ISIN"));
        String html = RegReportHtml.render("DeRiMV", "a&b.xml", "4 funds", findings, List.of(catalog),
                List.of(new RegReportHtml.Section("Extra", "<p id=\"extra\">x</p>")));
        assertTrue(html.contains("<title>DeRiMV – check report</title>"), html);
        assertTrue(html.contains("a&amp;b.xml · 4 funds"), html);
        assertTrue(html.contains("<b>2</b><span>errors</span>") && html.contains("<b>2</b><span>warnings</span>"), html);
        assertTrue(html.contains("rowspan=\"2\" class=\"rule\">DeRiMV-7</td><td rowspan=\"2\">Field 7 · Maximale Risikoangabe"), html);
        assertTrue(html.contains("Too high &lt;100&gt;"), html);
        // rule ids in numeric order: 4, 7, 10
        assertTrue(html.indexOf(">DeRiMV-4<") < html.indexOf(">DeRiMV-7<") && html.indexOf(">DeRiMV-7<") < html.indexOf(">DeRiMV-10<"), html);
        assertTrue(html.contains("<h2>Extra</h2>\n<p id=\"extra\">x</p>"), html);
    }

    @Test
    void aCleanReportSaysSo() {
        String html = RegReportHtml.render("X", "x.xml", null, List.of(), List.of(), List.of());
        assertTrue(html.contains("<b>✓</b><span>no findings</span>"), html);
        assertFalse(html.contains("Findings by rule"), html);
    }

    @Test
    void ruleIdsCompareNumbersByValue() {
        assertTrue(RegReportHtml.compareIds("X-9", "X-10") < 0);
        assertTrue(RegReportHtml.compareIds("EMIR-2.10", "EMIR-2.9") > 0);
        assertEquals(0, RegReportHtml.compareIds("A-1", "A-1"));
    }
}
