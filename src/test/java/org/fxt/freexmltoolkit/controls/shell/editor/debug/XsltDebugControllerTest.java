package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class XsltDebugControllerTest {

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <out>
                  <xsl:value-of select="count(//item)"/>
                </out>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    @Timeout(20)
    void pausesAtBreakpointThenRunsToCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            XsltDebugController controller = new XsltDebugController(executor);
            DebugSession session = controller.getSession();
            // Breakpoint on the <xsl:value-of> line (line 4 in the block above, 1-based).
            session.addBreakpoint(new Breakpoint("", 4, true));

            controller.start(XML, XSLT, Map.of(), OutputFormat.XML);

            // Wait until the Saxon thread blocks at the breakpoint.
            waitForState(session, DebugSession.State.PAUSED);
            assertNotNull(session.getPausedSnapshot(), "snapshot captured at pause");

            controller.continueRun();

            // After continue, the transform completes and the session closes (IDLE).
            waitForState(session, DebugSession.State.IDLE);
            assertNotNull(controller.getLastResult(), "result produced");
            assertTrue(controller.getLastResult().isSuccess(), "transform succeeded");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void waitForState(DebugSession session, DebugSession.State target)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (session.getState() != target && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        if (session.getState() != target) {
            throw new AssertionError("Timed out waiting for state " + target
                    + " (was " + session.getState() + ")");
        }
    }
}
