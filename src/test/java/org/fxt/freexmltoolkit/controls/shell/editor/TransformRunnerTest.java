package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the XSLT stage of {@link TransformRunner} (Saxon engine, no service
 * registry needed). A valid stylesheet produces output; a broken one yields an
 * {@code ERROR:} message rather than throwing.
 */
class TransformRunnerTest {

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void transformsXmlWithStylesheet() {
        String output = TransformRunner.xsltTransform(XML, XSLT);
        assertFalse(output.startsWith("ERROR:"), output);
        assertTrue(output.contains("<out>Hello</out>"), output);
    }

    @Test
    void brokenStylesheetReturnsErrorMessage() {
        String output = TransformRunner.xsltTransform(XML, "<xsl:not-a-stylesheet/>");
        assertTrue(output.startsWith("ERROR:"), "a broken stylesheet should yield an error message: " + output);
    }

    @Test
    void evaluatesJsonPath() {
        String result = TransformRunner.runJsonPath("{\"fund\":{\"id\":\"EAM_2024\"}}", "$.fund.id");
        assertTrue(result.contains("EAM_2024"), result);
    }

    private static final String PARAM_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:param name="greeting" select="'default'"/>
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="$greeting"/></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void passesParametersToTheStylesheet() {
        String out = TransformRunner.xsltTransform("<doc/>", PARAM_XSLT,
                java.util.Map.of("greeting", "Hello"),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
        assertEquals("Hello", out.strip(), out);
    }

    @Test
    void usesParameterDefaultWhenNoneProvided() {
        String out = TransformRunner.xsltTransform("<doc/>", PARAM_XSLT,
                java.util.Map.of(),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
        assertEquals("default", out.strip(), out);
    }
}
