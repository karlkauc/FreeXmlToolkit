package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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

    @Test
    void runsXQueryAgainstTheActiveXml() {
        String out = TransformRunner.runXQuery(
                "<root><item>a</item><item>b</item></root>",
                "string-join(/root/item, ',')",
                java.util.Map.of(),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
        assertEquals("a,b", out.strip(), out);
    }

    @Test
    void brokenXQueryReturnsErrorMessage() {
        String out = TransformRunner.runXQuery("<doc/>", "for $x in", java.util.Map.of(),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
        assertTrue(out.startsWith("ERROR:"), out);
    }

    // ----- output-format auto-detection -------------------------------------------------------

    @Test
    void detectsOutputFormatFromXslOutputMethod() {
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.HTML,
                TransformRunner.detectXsltOutputFormat(stylesheetWithMethod("html")));
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XHTML,
                TransformRunner.detectXsltOutputFormat(stylesheetWithMethod("xhtml")));
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT,
                TransformRunner.detectXsltOutputFormat(stylesheetWithMethod("text")));
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.JSON,
                TransformRunner.detectXsltOutputFormat(stylesheetWithMethod("json")));
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML,
                TransformRunner.detectXsltOutputFormat(stylesheetWithMethod("xml")));
    }

    @Test
    void detectionDefaultsToXmlWithoutXslOutput() {
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML,
                TransformRunner.detectXsltOutputFormat(XSLT.replace("<xsl:output method=\"xml\"/>", "")));
    }

    @Test
    void detectionFallsBackToHtmlForLiteralHtmlResultElement() {
        // XSLT default output rule: a literal <html> result element implies the html method.
        String xslt = """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><html><body><xsl:value-of select="."/></body></html></xsl:template>
                </xsl:stylesheet>
                """;
        assertEquals(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.HTML,
                TransformRunner.detectXsltOutputFormat(xslt));
    }

    private static String stylesheetWithMethod(String method) {
        return """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="%s"/>
                  <xsl:template match="/"><xsl:value-of select="."/></xsl:template>
                </xsl:stylesheet>
                """.formatted(method);
    }

    /** A stylesheet that imports a sibling module by a relative href, plus an input referencing a sibling file. */
    private static java.nio.file.Path writeImportingStylesheet(java.nio.file.Path dir) throws java.io.IOException {
        java.nio.file.Files.writeString(dir.resolve("module.xslt"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template name="from-module">module:<xsl:value-of select="doc(resolve-uri('data.xml', base-uri(/)))/d"/></xsl:template>
                </xsl:stylesheet>
                """);
        java.nio.file.Files.writeString(dir.resolve("data.xml"), "<d>sibling</d>");
        java.nio.file.Path main = dir.resolve("main.xslt");
        java.nio.file.Files.writeString(main, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:import href="module.xslt"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:call-template name="from-module"/></xsl:template>
                </xsl:stylesheet>
                """);
        return main;
    }

    @Test
    void resolvesRelativeImportsAgainstTheStylesheetFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path main = writeImportingStylesheet(dir);
        String xslt = java.nio.file.Files.readString(main);
        String input = dir.resolve("input.xml").toUri().toString();
        var format = org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT;

        var result = TransformRunner.xsltTransformResult("<in/>", input, xslt, main.toUri().toString(),
                java.util.Map.of(), format);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("module:sibling", result.getOutputContent().strip());

        var report = TransformRunner.transformForReport("<in/>", input, xslt, main.toUri().toString(),
                java.util.Map.of(), format);
        assertTrue(report.isSuccess(), report.getErrorMessage());
        assertEquals("module:sibling", report.getOutputContent().strip());
    }

    @Test
    void relativeImportFailsWithoutTheStylesheetLocation(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        String xslt = java.nio.file.Files.readString(writeImportingStylesheet(dir));
        var result = TransformRunner.xsltTransformResult("<in/>", xslt, java.util.Map.of(),
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
        assertFalse(result.isSuccess(), "without a location the import cannot be found next to the stylesheet");
    }
}
