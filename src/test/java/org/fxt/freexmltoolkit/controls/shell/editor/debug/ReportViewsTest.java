package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.stage.Stage;

/**
 * Smoke test: {@link ProfileView} and {@link TraceView} render a real
 * transformation report without throwing (guards the Fix B null path).
 */
@ExtendWith(ApplicationExtension.class)
class ReportViewsTest {

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/"><out><xsl:value-of select="count(//item)"/></out></xsl:template>
            </xsl:stylesheet>
            """;

    @Start
    void start(Stage stage) {
        // No UI needed; the extension just provides a JavaFX toolkit.
    }

    @Test
    void profileAndTraceViewsRenderRealReport() throws Exception {
        XsltTransformationResult result =
                TransformRunner.transformForReport(XML, XSLT, Map.of(), OutputFormat.XML);
        assertNotNull(result, "report result");
        assertTrue(result.isSuccess(), "transform succeeded: " + result.getErrorMessage());

        ProfileView profileView = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new ProfileView(result));
        TraceView traceView = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new TraceView(result));

        assertFalse(profileView.getSummaryText().isBlank(), "profile summary non-blank");
        assertTrue(profileView.getRowCount() >= 0, "profile row count sane");
        assertTrue(traceView.getMatchCount() >= 0, "trace match count sane");
        assertTrue(traceView.getMessageCount() >= 0, "trace message count sane");
    }
}
