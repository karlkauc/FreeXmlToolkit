package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

/**
 * xsl:message levels: Saxon gives every message the default error code XTMM9000, which must not
 * turn a plain message into an error — only terminate="yes" does.
 */
class XsltDebugMessageListenerTest {

    @Test
    void levelDependsOnTerminateNotOnTheDefaultCode() {
        assertEquals("INFO", XsltDebugMessageListener.levelOf(false, "XTMM9000"));
        assertEquals("INFO", XsltDebugMessageListener.levelOf(false, null));
        assertEquals("TERMINATE", XsltDebugMessageListener.levelOf(true, "XTMM9000"));
        assertEquals("INFO [MYAPP001]", XsltDebugMessageListener.levelOf(false, "MYAPP001"));
        assertEquals("TERMINATE [MYAPP002]", XsltDebugMessageListener.levelOf(true, "MYAPP002"));
    }

    @Test
    void aRealMessageIsInfo() throws Exception {
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><xsl:message>hello</xsl:message><out/></xsl:template>
                </xsl:stylesheet>
                """;
        org.fxt.freexmltoolkit.debugger.DebugSession session = new org.fxt.freexmltoolkit.debugger.DebugSession();
        java.util.List<XsltTransformationResult.TransformationMessage> live = new java.util.concurrent.CopyOnWriteArrayList<>();
        XsltTransformationResult result = XsltTransformationEngine.getInstance().transformWithDebugSession(
                "<root/>", xslt, null, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML, session, live::add);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(1, live.size());
        assertEquals("INFO", live.get(0).getLevel(), "not ERROR [XTMM9000]");
    }
}
