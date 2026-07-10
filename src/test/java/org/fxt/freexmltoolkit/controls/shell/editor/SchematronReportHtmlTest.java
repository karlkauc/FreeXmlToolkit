package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

class SchematronReportHtmlTest {

    @Test
    void rendersMetadataSummaryAndFindings() {
        SchematronReportData data = new SchematronReportData(
                "fund.xml", new File("/tmp/rules.sch"),
                List.of(
                        new ValidationProblem("Schematron", "error", 12, "NAV must be positive",
                                "number(Nav) > 0", "/Funds/Fund[1]/Nav"),
                        new ValidationProblem("Schematron", "warning", 0, "Currency should be ISO",
                                "matches(Ccy,'[A-Z]{3}')", "/Funds/Fund[2]/Ccy")),
                "<svrl/>");

        String html = SchematronReportHtml.build(data);

        assertTrue(html.contains("Schematron Validation Report"));
        assertTrue(html.contains("fund.xml"));
        assertTrue(html.contains("rules.sch"));
        assertTrue(html.contains("1 Error"));
        assertTrue(html.contains("1 Warning"));
        assertTrue(html.contains("NAV must be positive"));
        assertTrue(html.contains("number(Nav) &gt; 0"), "test expressions must be HTML-escaped");
        assertTrue(html.contains("/Funds/Fund[1]/Nav"));
        assertTrue(html.contains("<td>12</td>"), "line numbers must be rendered");
        assertFalse(html.contains("http://"), "the report must be self-contained (no external resources)");
    }

    @Test
    void rendersPassStateWithoutFindingsTable() {
        SchematronReportData data = new SchematronReportData(
                "fund.xml", new File("/tmp/rules.sch"), List.of(), "<svrl/>");

        String html = SchematronReportHtml.build(data);

        assertTrue(html.contains("All rules passed"));
        assertFalse(html.contains("<table class=\"findings\">"));
    }

    @Test
    void escapesHtmlMetaCharacters() {
        assertEquals("&lt;a&gt; &amp; &quot;b&quot; &#39;c&#39;",
                SchematronReportHtml.escape("<a> & \"b\" 'c'"));
        assertEquals("", SchematronReportHtml.escape(null));
    }
}
