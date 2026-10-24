package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

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

    private static final String XSLT_WITH_MESSAGE = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <xsl:message>first message</xsl:message>
                <out><xsl:value-of select="count(//item)"/></out>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    @Timeout(30)
    void pauseOnEntryStopsWithoutBreakpointsAndStreamsMessagesAndCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            XsltDebugController controller = new XsltDebugController(executor);
            java.util.List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.atomic.AtomicReference<org.fxt.freexmltoolkit.service.XsltTransformationResult> done =
                    new java.util.concurrent.atomic.AtomicReference<>();
            controller.addMessageListener(m -> messages.add(m.getMessage()));
            controller.addCompletionListener(done::set);
            controller.setPauseOnEntry(true);

            controller.start(XML, XSLT_WITH_MESSAGE, Map.of(), OutputFormat.XML);
            waitForState(controller.getSession(), DebugSession.State.PAUSED);
            assertTrue(controller.isRunning());
            assertTrue(messages.isEmpty(), "paused before the message instruction ran");

            controller.continueRun();
            long deadline = System.currentTimeMillis() + 15_000;
            while (done.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            assertNotNull(done.get(), "completion listener fires");
            assertTrue(done.get().isSuccess(), done.get().getErrorMessage());
            assertEquals(java.util.List.of("first message"), messages, "xsl:message streamed live");
            assertFalse(controller.isRunning());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(40)
    void restartWhilePausedRunsAgainWithTheSameBreakpoints() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            XsltDebugController controller = new XsltDebugController(executor);
            DebugSession session = controller.getSession();
            session.addBreakpoint(new Breakpoint("", 4, true));
            java.util.concurrent.atomic.AtomicInteger completions = new java.util.concurrent.atomic.AtomicInteger();
            controller.addCompletionListener(r -> completions.incrementAndGet());

            controller.start(XML, XSLT, Map.of(), OutputFormat.XML);
            waitForState(session, DebugSession.State.PAUSED);

            controller.restart(); // stops the paused run, then launches again once it unwound
            long deadline = System.currentTimeMillis() + 15_000;
            while (completions.get() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }
            waitForState(session, DebugSession.State.PAUSED);
            assertEquals(1, completions.get(), "the stopped first run completed once");
            assertTrue(session.hasBreakpoint("", 4), "breakpoints survive the restart");
            assertEquals(4, session.getPausedSnapshot().lineNumber(), "the second run pauses at the same breakpoint");

            controller.continueRun();
            waitForState(session, DebugSession.State.IDLE);
            assertTrue(controller.getLastResult().isSuccess());
            assertEquals(2, completions.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(20)
    void aBreakpointOnTheFirstLineOfAMultiLineInstructionPauses() throws Exception {
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <out>
                      <xsl:value-of
                          select="count(//item)"/>
                    </out>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            XsltDebugController controller = new XsltDebugController(executor);
            DebugSession session = controller.getSession();
            // Line 4 opens <xsl:value-of; Saxon reports the instruction at line 5, where the tag ends.
            session.addBreakpoint(new Breakpoint("", 4, true));
            controller.start(XML, xslt, Map.of(), OutputFormat.XML);
            waitForState(session, DebugSession.State.PAUSED);
            assertEquals(5, session.getPausedSnapshot().lineNumber(), "paused at the line Saxon reports");
            assertEquals(1, session.hitCount("", 4), "the hit counts for the breakpoint the user set");
            controller.continueRun();
            waitForState(session, DebugSession.State.IDLE);
            assertTrue(controller.getLastResult().isSuccess());
        } finally {
            executor.shutdownNow();
        }
    }
}
