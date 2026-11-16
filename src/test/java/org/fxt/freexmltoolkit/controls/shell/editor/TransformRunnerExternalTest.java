package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.xsltproc.ExternalXsltRunnerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The processor switch of {@link TransformRunner}: no processor = Saxon HE, else the external program. */
class TransformRunnerExternalTest {

    @TempDir
    Path dir;

    private static final String XSL = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="system-property('xsl:vendor')"/></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void theProcessorDecidesWhoTransforms() throws Exception {
        Path xsl = Files.writeString(dir.resolve("v.xsl"), XSL);
        XsltTransformationResult saxon = TransformRunner.xsltTransformResult("<a/>", null, XSL, xsl.toUri().toString(),
                Map.of(), org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT, null, null);
        assertTrue(saxon.getOutputContent().contains("Saxonica"), saxon.getOutputContent() + " | " + saxon.getErrorMessage());

        XsltTransformationResult external = TransformRunner.xsltTransformResult("<a/>", null, XSL, xsl.toUri().toString(),
                Map.of(), org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT, null, ExternalXsltRunnerTest.fake("ok", 60));
        assertTrue(external.isSuccess(), "error: " + external.getErrorMessage());
        assertTrue(!external.getOutputContent().contains("Saxonica") && !external.getOutputContent().isBlank(),
                external.getOutputContent());
        assertEquals(xsl, TransformRunner.pathOf(xsl.toUri().toString()));
    }
}
