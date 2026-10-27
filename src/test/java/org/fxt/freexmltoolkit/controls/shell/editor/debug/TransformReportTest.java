package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.TemplateProfileEntry;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;

class TransformReportTest {

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/"><out><xsl:apply-templates select="//item"/></out></xsl:template>
              <xsl:template match="item"><i><xsl:value-of select="."/></i></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void reportRunReturnsProfileAndTraceData() {
        XsltTransformationResult result =
                TransformRunner.transformForReport(XML, XSLT, Map.of(), OutputFormat.XML);
        assertNotNull(result, "report result");
        assertTrue(result.isSuccess(), "transform succeeded: " + result.getErrorMessage());
        assertNotNull(result.getProfile(), "profile present");
        assertEquals(3, result.getTemplateMatches().size(),
                "one trace row per template call: " + result.getTemplateMatches());
        TemplateProfileEntry item = result.getTemplateProfile().stream()
                .filter(e -> e.name().equals("match=\"item\""))
                .findFirst().orElseThrow(() -> new AssertionError("profile: " + result.getTemplateProfile()));
        assertEquals(2, item.calls(), "item rule called twice");
        assertEquals("Template rule", item.kind());
        assertTrue(item.lineNumber() > 0, "line known");
        assertFalse(result.getCallStack().isEmpty(), "call trace recorded");
    }
}
