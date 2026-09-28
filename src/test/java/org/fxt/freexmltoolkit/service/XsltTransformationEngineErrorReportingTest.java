package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.junit.jupiter.api.Test;

/**
 * Failed transformations report the processor's reason, location and error code instead of a
 * generic "Failed to compile XSLT stylesheet", and classify the failure phase
 * ({@code compile | input | runtime}) for anonymous telemetry.
 */
class XsltTransformationEngineErrorReportingTest {

    private static final String XML = "<root><item>1</item></root>";
    private static final XsltTransformationEngine.OutputFormat FORMAT = XsltTransformationEngine.OutputFormat.XML;

    private static String stylesheet(String body) {
        return """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                  <xsl:template match="/">
                %s
                  </xsl:template>
                </xsl:stylesheet>
                """.formatted(body);
    }

    private static XsltTransformationResult transform(String xml, String xslt) {
        return XsltTransformationEngine.getInstance().transform(xml, xslt, Map.of(), FORMAT);
    }

    @Test
    void unknownInstructionReportsReasonLineAndCode() {
        XsltTransformationResult result = transform(XML, stylesheet("    <xsl:foo/>"));

        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, result.getErrorPhase());
        assertEquals("XTSE0010", result.getErrorCode());
        String message = result.getErrorMessage();
        assertTrue(message.startsWith("XSLT compile error at line 3"), message);
        assertTrue(message.contains("[XTSE0010]"), message);
        assertTrue(message.contains("xsl:foo"), message);
    }

    @Test
    void xpathSyntaxErrorReportsXpathCode() {
        XsltTransformationResult result = transform(XML, stylesheet("    <xsl:value-of select=\"count(//item\"/>"));

        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, result.getErrorPhase());
        assertEquals("XPST0003", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("line 3"), result.getErrorMessage());
    }

    @Test
    void malformedStylesheetIsACompileError() {
        XsltTransformationResult result = transform(XML,
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                        + "  <xsl:template match=\"/\">\n");

        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, result.getErrorPhase());
        assertNotNull(result.getErrorCode());
        assertFalse(result.getErrorMessage().contains("Failed to compile XSLT stylesheet"),
                result.getErrorMessage());
    }

    @Test
    void severalStaticErrorsAreListed() {
        XsltTransformationResult result = transform(XML, stylesheet("    <xsl:foo/>\n    <xsl:bar/>"));

        assertFalse(result.isSuccess());
        String message = result.getErrorMessage();
        assertTrue(message.startsWith("XSLT stylesheet has 2 compile errors:"), message);
        assertTrue(message.contains("xsl:foo") && message.contains("xsl:bar"), message);
    }

    @Test
    void malformedInputIsAnInputError() {
        XsltTransformationResult result = transform("<root><item>", stylesheet("    <out/>"));

        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_INPUT, result.getErrorPhase());
        assertEquals("SXXP0003", result.getErrorCode());
        assertTrue(result.getErrorMessage().startsWith("Input XML is not well-formed"), result.getErrorMessage());
    }

    @Test
    void dynamicErrorIsARuntimeError() {
        XsltTransformationResult result = transform(XML,
                stylesheet("    <xsl:value-of select=\"error(xs:QName('err:FOER0000'), 'boom')\""
                        + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                        + " xmlns:err=\"http://www.w3.org/2005/xqt-errors\"/>"));

        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_RUNTIME, result.getErrorPhase());
        assertEquals("FOER0000", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("boom"), result.getErrorMessage());
    }

    @Test
    void debugSessionPathReportsCompileErrorsToo() {
        XsltTransformationResult result = XsltTransformationEngine.getInstance().transformWithDebugSession(
                XML, stylesheet("    <xsl:foo/>"), Map.of(), FORMAT, new DebugSession());

        assertFalse(result.isSuccess());
        assertEquals(XsltTransformationResult.PHASE_COMPILE, result.getErrorPhase());
        assertEquals("XTSE0010", result.getErrorCode());
    }

    @Test
    void successHasNoErrorClassification() {
        XsltTransformationResult result = transform(XML, stylesheet("    <out/>"));

        assertTrue(result.isSuccess(), result::getErrorMessage);
        assertNull(result.getErrorCode());
        assertNull(result.getErrorPhase());
    }
}
