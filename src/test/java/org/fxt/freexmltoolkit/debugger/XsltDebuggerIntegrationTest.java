package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test of the XSLT debugger: run a real Saxon
 * transformation under a {@link DebugSession}, verify it pauses at a
 * breakpoint, that variable/stack snapshots are captured, and that
 * Continue lets it complete successfully.
 */
class XsltDebuggerIntegrationTest {

    private static ExecutorService bg;

    @BeforeAll
    static void setUp() {
        bg = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "debug-it");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterAll
    static void tearDown() {
        bg.shutdownNow();
    }

    @Test
    void breakpointPausesAndContinueCompletes() throws Exception {
        String xml = "<root><a>hello</a></root>";
        // The XSLT spans lines 1..5 — BP set at every line so any traceable enter triggers pause
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:output method=\"xml\" indent=\"no\"/>\n"
                + "  <xsl:template match=\"/\"><out><xsl:apply-templates/></out></xsl:template>\n"
                + "  <xsl:template match=\"a\"><x><xsl:value-of select=\".\"/></x></xsl:template>\n"
                + "</xsl:stylesheet>\n";

        DebugSession session = new DebugSession();
        // Cover any line Saxon may report for the second template instruction
        for (int line = 1; line <= 5; line++) {
            session.addBreakpoint(new Breakpoint("", line, true));
        }

        CountDownLatch pausedLatch = new CountDownLatch(1);
        AtomicReference<PausedSnapshot> snapshotRef = new AtomicReference<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                snapshotRef.set(session.getPausedSnapshot());
                pausedLatch.countDown();
            }
        });

        XsltTransformationEngine engine = XsltTransformationEngine.getInstance();
        AtomicReference<XsltTransformationResult> resultRef = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        bg.submit(() -> {
            try {
                XsltTransformationResult result = engine.transformWithDebugSession(
                        xml, xslt, new HashMap<>(),
                        XsltTransformationEngine.OutputFormat.XML, session);
                resultRef.set(result);
            } finally {
                doneLatch.countDown();
            }
        });

        assertTrue(pausedLatch.await(10, TimeUnit.SECONDS), "Should have hit breakpoint within 10s");
        PausedSnapshot snap = snapshotRef.get();
        assertNotNull(snap, "Pause snapshot must be available");
        assertTrue(snap.lineNumber() >= 1 && snap.lineNumber() <= 5,
                "Pause line should be within stylesheet (was " + snap.lineNumber() + ")");
        assertNotNull(snap.callStack(), "Call stack list must not be null");
        assertFalse(snap.callStack().isEmpty(), "Call stack must not be empty at pause");
        assertNotNull(snap.variables(), "Variables list must not be null");
        assertFalse(snap.variables().isEmpty(),
                "Variables panel must show at least the context item / focus");
        // Context item should always be present as a synthetic '.' binding
        boolean hasContextItem = snap.variables().stream()
                .anyMatch(v -> ".".equals(v.name()));
        assertTrue(hasContextItem, "Context item ('.') should appear in variables");

        // Drain further pauses by continuing repeatedly until completion
        Thread continuer = new Thread(() -> {
            while (doneLatch.getCount() > 0) {
                if (session.getState() == DebugSession.State.PAUSED) {
                    session.requestContinue();
                }
                try { Thread.sleep(20); } catch (InterruptedException ie) { return; }
            }
        }, "continuer");
        continuer.setDaemon(true);
        continuer.start();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Transformation should complete after continue");

        XsltTransformationResult result = resultRef.get();
        assertNotNull(result);
        assertTrue(result.isSuccess(),
                "Transformation should succeed: " + result.getErrorMessage());
        String output = result.getOutputContent();
        assertTrue(output.contains("hello"), "Output should contain transformed value: " + output);
    }

    @Test
    void stopRequestAbortsTransformation() throws Exception {
        String xml = "<root><a>x</a><a>y</a><a>z</a></root>";
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\"><out><xsl:apply-templates select=\"//a\"/></out></xsl:template>\n"
                + "  <xsl:template match=\"a\"><x><xsl:value-of select=\".\"/></x></xsl:template>\n"
                + "</xsl:stylesheet>\n";

        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("", 3, true));

        CountDownLatch pausedLatch = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) pausedLatch.countDown();
        });

        XsltTransformationEngine engine = XsltTransformationEngine.getInstance();
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicReference<XsltTransformationResult> resultRef = new AtomicReference<>();
        bg.submit(() -> {
            try {
                resultRef.set(engine.transformWithDebugSession(
                        xml, xslt, new HashMap<>(),
                        XsltTransformationEngine.OutputFormat.XML, session));
            } finally {
                doneLatch.countDown();
            }
        });

        assertTrue(pausedLatch.await(10, TimeUnit.SECONDS));
        session.requestStop();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Transformation must finish after stop");
        XsltTransformationResult result = resultRef.get();
        assertNotNull(result);
        assertFalse(result.isSuccess(), "Stopped transformation should not be marked successful");
    }

    @Test
    void streamingModeIsRejected() {
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:mode streamable=\"yes\"/>\n"
                + "  <xsl:template match=\"/\"><xsl:stream href=\"x.xml\"><out/></xsl:stream></xsl:template>\n"
                + "</xsl:stylesheet>\n";
        DebugSession session = new DebugSession();
        XsltTransformationResult result = XsltTransformationEngine.getInstance()
                .transformWithDebugSession("<r/>", xslt, new HashMap<>(),
                        XsltTransformationEngine.OutputFormat.XML, session);
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().toLowerCase().contains("stream"),
                "Error message should mention streaming: " + result.getErrorMessage());
    }
}
