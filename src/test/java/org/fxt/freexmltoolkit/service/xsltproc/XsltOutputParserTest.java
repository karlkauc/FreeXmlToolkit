package org.fxt.freexmltoolkit.service.xsltproc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.Test;

/** The output of Saxon, xsltproc and RaptorXML read into messages and the first error. */
class XsltOutputParserTest {

    @Test
    void saxonRuntimeErrorWithMessagesBefore() {
        var parsed = XsltOutputParser.parse(List.of(
                "Processing invoice 4711",
                "Warning at xsl:template on line 3 column 30 of a.xsl:",
                "  SXWN9001  A variable with no following sibling instructions has no effect",
                "Error evaluating (xs:integer(.)) in xsl:value-of/@select on line 12 column 51 of a.xsl:",
                "  FORG0001  Cannot convert string \"abc\" to xs:integer"), "in.xml");
        assertEquals(2, parsed.messages().size());
        assertEquals("Processing invoice 4711", parsed.messages().get(0).getMessage());
        assertEquals("WARNING", parsed.messages().get(1).getLevel());
        assertEquals(12, parsed.errorLine());
        assertEquals("FORG0001", parsed.errorCode());
        assertEquals(XsltTransformationResult.PHASE_RUNTIME, parsed.errorPhase());
    }

    @Test
    void xsltprocInputAndCompileErrors() {
        var input = XsltOutputParser.parse(List.of("in.xml:4: parser error : Opening and ending tag mismatch: a and b"), "in.xml");
        assertEquals(XsltTransformationResult.PHASE_INPUT, input.errorPhase());
        assertEquals(4, input.errorLine());
        var compile = XsltOutputParser.parse(List.of("compilation error: file a.xsl line 9 element foo",
                "xsltParseStylesheetTop: unknown foo element"), "in.xml");
        assertEquals(XsltTransformationResult.PHASE_COMPILE, compile.errorPhase());
        assertEquals(9, compile.errorLine());
    }

    @Test
    void cleanOutputHasNoError() {
        var parsed = XsltOutputParser.parse(List.of("done"), null);
        assertNull(parsed.errorMessage());
        assertEquals(-1, parsed.errorLine());
    }
}
