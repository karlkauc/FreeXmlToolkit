package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;

class TransformReportTest {

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/"><out><xsl:value-of select="count(//item)"/></out></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void reportRunReturnsProfileAndTraceData() {
        XsltTransformationResult result =
                TransformRunner.transformForReport(XML, XSLT, Map.of(), OutputFormat.XML);
        assertNotNull(result, "report result");
        assertTrue(result.isSuccess(), "transform succeeded: " + result.getErrorMessage());
        assertNotNull(result.getProfile(), "profile present");
        assertNotNull(result.getTemplateMatches(), "template matches present");
    }
}
