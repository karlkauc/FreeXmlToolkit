package org.fxt.freexmltoolkit.service.xslt;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Static errors of a stylesheet are placed where they are: the failing token of an XPath expression in an attribute,
 * a wrong attribute's name, an unknown instruction's name — in the module that has them — with hint and corrections.
 */
class XsltErrorsTest {

    private final XsltTransformationEngine engine = XsltTransformationEngine.getInstance();

    private static String stylesheet(String body) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "<xsl:template match=\"/\">\n" + body + "\n</xsl:template>\n</xsl:stylesheet>\n";
    }

    private XsltDiagnostic first(String xslt) {
        List<XsltDiagnostic> found = engine.checkStylesheet(xslt, "file:///tmp/t.xsl", null);
        assertFalse(found.isEmpty(), "an error is expected");
        return found.getFirst();
    }

    private static String marked(String text, XsltDiagnostic d) {
        return text.substring(d.offset(), d.offset() + d.length());
    }

    @Test
    void anUnknownFunctionInASelectIsMarkedAtItsNameWithACorrection() {
        String xslt = stylesheet("<xsl:value-of select=\"count(//item) + ocunt(//x)\"/>");
        XsltDiagnostic d = first(xslt);

        assertEquals("XPST0017", d.code());
        assertEquals("ocunt", marked(xslt, d));
        assertEquals(3, d.line());
        assertTrue(d.message().contains("ocunt") && !d.message().contains("Q{"), d.message());
        assertTrue(d.suggestions().stream().anyMatch(s -> s.applyTo(xslt).contains("count(//x)")),
                d.suggestions().toString());
    }

    @Test
    void aSyntaxErrorOnTheSecondLineOfAnAttributeIsPlacedThere() {
        String xslt = stylesheet("<xsl:value-of\n   select=\"count(\n  //item\"/>");
        XsltDiagnostic d = first(xslt);

        assertEquals("XPST0003", d.code());
        assertEquals(5, d.line(), d.toString());
        assertTrue(d.offset() > xslt.indexOf("//item"), d.toString());
    }

    @Test
    void anUndeclaredVariableIsMarked() {
        String xslt = stylesheet("<xsl:value-of select=\"$total + 1\"/>");
        XsltDiagnostic d = first(xslt);

        assertEquals("XPST0008", d.code());
        assertTrue(marked(xslt, d).contains("total"), marked(xslt, d));
    }

    @Test
    void aWrongAttributeIsMarkedAtItsName() {
        String xslt = stylesheet("<xsl:value-of select=\"1\" bogus=\"x\"/>");
        XsltDiagnostic d = first(xslt);

        assertEquals("XTSE0090", d.code());
        assertEquals("bogus", marked(xslt, d));
        assertNotNull(d.hint());
    }

    @Test
    void anUnknownInstructionIsMarkedAtItsElementName() {
        String xslt = stylesheet("  <xsl:foo/>");
        XsltDiagnostic d = first(xslt);

        assertEquals("XTSE0010", d.code());
        assertEquals("xsl:foo", marked(xslt, d));
        assertEquals(3, d.line());
        assertEquals(4, d.column());
        assertNotNull(d.hint());
    }

    @Test
    void anErrorInAnAttributeValueTemplateIsMarkedInIt() {
        String xslt = stylesheet("<out a=\"{count(}\"/>");
        XsltDiagnostic d = first(xslt);

        assertEquals("XPST0003", d.code());
        assertEquals("}", marked(xslt, d));
    }

    @Test
    void warningsAreReportedAsWarnings() {
        String xslt = stylesheet("<xsl:variable name=\"v\" select=\"1\"/>");
        List<XsltDiagnostic> found = engine.checkStylesheet(xslt, "file:///tmp/t.xsl", null);

        assertEquals(1, found.size(), found.toString());
        assertTrue(found.getFirst().warning());
        assertEquals("xsl:variable", marked(xslt, found.getFirst()));
    }

    @Test
    void anErrorInAnIncludedModuleNamesTheModule(@TempDir Path dir) throws Exception {
        Path module = dir.resolve("lib.xsl");
        Files.writeString(module, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "<xsl:template name=\"t\"><xsl:value-of select=\"nope()\"/></xsl:template>\n</xsl:stylesheet>\n");
        String main = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "<xsl:include href=\"lib.xsl\"/>\n<xsl:template match=\"/\"/>\n</xsl:stylesheet>\n";
        XsltDiagnostic d = engine.checkStylesheet(main, dir.resolve("main.xsl").toUri().toString(), null).getFirst();

        assertEquals("lib.xsl", d.module());
        assertFalse(d.inMain());
        assertEquals(2, d.line());
        assertTrue(d.positionLabel().startsWith("lib.xsl, line 2"), d.positionLabel());
    }

    @Test
    void anOpenModulesTextIsChecked(@TempDir Path dir) throws Exception {
        Path module = dir.resolve("lib.xsl");
        Files.writeString(module, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"/>");
        String edited = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "<xsl:template name=\"t\"><xsl:bad/></xsl:template>\n</xsl:stylesheet>\n";
        String main = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "<xsl:include href=\"lib.xsl\"/>\n</xsl:stylesheet>\n";
        List<XsltDiagnostic> found = engine.checkStylesheet(main, dir.resolve("main.xsl").toUri().toString(),
                path -> path.endsWith("lib.xsl") ? edited : null);

        assertEquals("XTSE0010", found.getFirst().code(), found.toString());
        assertEquals("lib.xsl", found.getFirst().module());
    }

    @Test
    void aFailedTransformCarriesTheDiagnostics() {
        String xslt = stylesheet("<xsl:value-of select=\"ocunt(1)\"/>");
        XsltTransformationResult result = engine.transform("<a/>", xslt, Map.of(), XsltTransformationEngine.OutputFormat.XML);

        assertFalse(result.isSuccess());
        assertEquals("XPST0017", result.getDiagnostics().getFirst().code());
        assertTrue(result.getErrorMessage().contains("XSLT compile error"), "the message stays as before");
    }

    @Test
    void aSuccessfulTransformKeepsTheCompileWarnings() {
        String xslt = stylesheet("<xsl:variable name=\"unused" + System.nanoTime() + "\" select=\"1\"/>");
        XsltTransformationResult result = engine.transform("<a/>", xslt, Map.of(), XsltTransformationEngine.OutputFormat.XML);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(result.getDiagnostics().stream().anyMatch(XsltDiagnostic::warning), result.getDiagnostics().toString());
    }
}
