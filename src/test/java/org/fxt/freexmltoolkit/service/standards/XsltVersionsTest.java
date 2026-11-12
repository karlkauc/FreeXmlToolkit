package org.fxt.freexmltoolkit.service.standards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** XSLT 1.0 runs on the JDK's XSLTC, XSLT 2.0 is checked for 3.0 constructs, 3.0 runs as before. */
class XsltVersionsTest {

    private static final String XML = "<r><a>2</a><a>1</a></r>";

    private static String stylesheet(String version, String body) {
        return "<xsl:stylesheet version=\"" + version + "\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\""
                + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                + "<xsl:output method=\"text\"/>\n"
                + "<xsl:template match=\"/\">\n" + body + "\n</xsl:template>\n</xsl:stylesheet>";
    }

    private static XsltTransformationResult run(String xslt, XsltVersion version) {
        return TransformRunner.xsltTransformResult(XML, null, xslt, null, Map.of(), OutputFormat.TEXT, version);
    }

    @Test
    void xslt10RunsOnTheJdkProcessor() {
        var result = run(stylesheet("1.0", "<xsl:value-of select=\"count(//a)\"/>-<xsl:value-of "
                + "select=\"system-property('xsl:vendor')\"/>"), XsltVersion.V1_0);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(result.getOutputContent().startsWith("2-"), result.getOutputContent());
        assertFalse(result.getOutputContent().contains("Saxon"), result.getOutputContent());
    }

    @Test
    void xslt10RefusesXslt20WithALine() {
        var result = run(stylesheet("1.0", "<xsl:for-each-group select=\"//a\" group-by=\".\">"
                + "<xsl:value-of select=\"current-grouping-key()\"/></xsl:for-each-group>"), XsltVersion.V1_0);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().startsWith("XSLT 1.0"), result.getErrorMessage());
    }

    @Test
    void xslt10RefusesXpath20Functions() {
        var result = run(stylesheet("1.0", "<xsl:value-of select=\"upper-case('a')\"/>"), XsltVersion.V1_0);
        assertFalse(result.isSuccess());
    }

    @Test
    void xslt20RunsAFittingStylesheet() {
        var result = run(stylesheet("2.0", "<xsl:value-of select=\"string-join(for $a in //a return "
                + "string($a), ',')\"/>"), XsltVersion.V2_0);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("2,1", result.getOutputContent().strip());
    }

    @Test
    void xslt20ReportsXslt30Constructs() {
        String xslt = stylesheet("2.0", "<xsl:iterate select=\"//a\"><xsl:value-of select=\". || 'x'\"/>"
                + "</xsl:iterate>\n<xsl:value-of select=\"head(//a)\" expand-text=\"yes\"/>");
        var result = run(xslt, XsltVersion.V2_0);
        assertFalse(result.isSuccess());
        assertEquals("FXT-COMPAT", result.getErrorCode());
        String message = result.getErrorMessage();
        assertTrue(message.contains("xsl:iterate"), message);
        assertTrue(message.contains("expand-text"), message);
        assertTrue(message.contains("head#1"), message);
        assertEquals(4, result.getErrorLine());
    }

    @Test
    void xslt20AllowsXsltFunctionsAndDeclaredVersion() {
        String xslt = stylesheet("2.0", "<xsl:value-of select=\"format-date(current-date(), '[Y]'), "
                + "generate-id(), key('k', 'v'), current()\"/>");
        assertEquals(List.of(), XsltCompatibilityCheck.check(xslt, null, XsltVersion.V2_0));
    }

    @Test
    void xslt20ReportsAVersion30Declaration() {
        var issues = XsltCompatibilityCheck.check(stylesheet("3.0", "<xsl:value-of select=\"1\"/>"), null,
                XsltVersion.V2_0);
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("FXT-COMPAT-VERSION")), issues.toString());
    }

    @Test
    void xslt20ChecksAvtsAndIncludedModules(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("inc.xsl"), "<xsl:stylesheet version=\"2.0\" "
                + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n<xsl:template name=\"t\">"
                + "<xsl:try><xsl:sequence select=\"1\"/><xsl:catch/></xsl:try></xsl:template></xsl:stylesheet>");
        String main = "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "<xsl:include href=\"inc.xsl\"/>\n"
                + "<xsl:template match=\"/\"><out a=\"{let $x := 1 return $x}\"/></xsl:template></xsl:stylesheet>";
        var issues = XsltCompatibilityCheck.check(main, dir.resolve("main.xsl").toUri().toString(),
                XsltVersion.V2_0);
        assertTrue(issues.stream().anyMatch(i -> "inc.xsl".equals(i.module()) && i.message().contains("xsl:try")),
                issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.module() == null && i.line() == 3), issues.toString());
    }

    @Test
    void xslt30IsUnchecked() {
        var result = run(stylesheet("3.0", "<xsl:value-of select=\"head(//a) || 'x'\"/>"), XsltVersion.V3_0);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("2x", result.getOutputContent().strip());
    }
}
