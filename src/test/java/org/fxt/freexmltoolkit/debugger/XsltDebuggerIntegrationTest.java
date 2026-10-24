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
import org.junit.jupiter.api.io.TempDir;

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
    void breakpointAtSpecificLineFires() throws Exception {
        // Use \n only — every line is exactly 1 line in the source
        String xml = "<root><a>x</a></root>";
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
                + "  <xsl:template match=\"/\">\n"                                                       // 2
                + "    <out>\n"                                                                          // 3
                + "      <xsl:value-of select=\"//a\"/>\n"                                               // 4 — BP here
                + "    </out>\n"                                                                          // 5
                + "  </xsl:template>\n"                                                                   // 6
                + "</xsl:stylesheet>\n";                                                                  // 7

        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("", 4, true));

        java.util.List<Integer> seenPauseLines = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch pausedLatch = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                seenPauseLines.add(session.getPausedSnapshot().lineNumber());
                pausedLatch.countDown();
            }
        });

        XsltTransformationEngine engine = XsltTransformationEngine.getInstance();
        CountDownLatch doneLatch = new CountDownLatch(1);
        bg.submit(() -> {
            try {
                engine.transformWithDebugSession(xml, xslt, new HashMap<>(),
                        XsltTransformationEngine.OutputFormat.XML, session);
            } finally {
                doneLatch.countDown();
            }
        });

        Thread continuer = new Thread(() -> {
            while (doneLatch.getCount() > 0) {
                if (session.getState() == DebugSession.State.PAUSED) session.requestContinue();
                try { Thread.sleep(20); } catch (InterruptedException ie) { return; }
            }
        }, "continuer");
        continuer.setDaemon(true);
        continuer.start();

        assertTrue(pausedLatch.await(10, TimeUnit.SECONDS),
                "Breakpoint at line 4 must fire — Saxon line numbering must be active");
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));

        // Every observed pause must be at line 4 (no other BPs were set)
        for (Integer line : seenPauseLines) {
            assertEquals(4, line.intValue(),
                    "All pauses must be at line 4 (BP location), saw lines: " + seenPauseLines);
        }
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

    @Test
    void breakpointInAnIncludedModuleFiresOnlyThere(@TempDir java.nio.file.Path tmp) throws Exception {
        // main.xsl line 4 and lib/helpers.xsl line 4 both hold instructions; the breakpoint is
        // set on the include's line 4 — the main module's line 4 must not pause.
        String main = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
                + "  <xsl:include href=\"lib/helpers.xsl\"/>\n"                                               // 2
                + "  <xsl:template match=\"/\">\n"                                                            // 3
                + "    <out><xsl:call-template name=\"helper\"/></out>\n"                                     // 4
                + "  </xsl:template>\n"                                                                        // 5
                + "</xsl:stylesheet>\n";
        String helpers = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
                + "  <xsl:template name=\"helper\">\n"                                                        // 2
                + "    <xsl:variable name=\"greeting\" select=\"'hi'\"/>\n"                                 // 3
                + "    <x><xsl:value-of select=\"$greeting\"/></x>\n"                                        // 4
                + "  </xsl:template>\n"                                                                        // 5
                + "</xsl:stylesheet>\n";
        java.nio.file.Path mainFile = tmp.resolve("main.xsl");
        java.nio.file.Path libFile = tmp.resolve("lib").resolve("helpers.xsl");
        java.nio.file.Files.createDirectories(libFile.getParent());
        java.nio.file.Files.writeString(mainFile, main);
        java.nio.file.Files.writeString(libFile, helpers);

        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint(libFile.toString(), 4, true));
        java.util.List<PausedSnapshot> pauses = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                pauses.add(session.getPausedSnapshot());
                session.requestContinue();
            }
        });
        java.util.List<String> live = new java.util.concurrent.CopyOnWriteArrayList<>();

        XsltTransformationResult result = XsltTransformationEngine.getInstance().transformWithDebugSession(
                "<root/>", main, mainFile.toUri().toString(), new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, session, m -> live.add(m.getMessage()));

        assertTrue(result.isSuccess(), "includes resolve relative to the systemId: " + result.getErrorMessage());
        assertTrue(result.getOutputContent().contains("hi"), result.getOutputContent());
        assertFalse(pauses.isEmpty(), "the include breakpoint must fire");
        for (PausedSnapshot snap : pauses) {
            assertEquals(4, snap.lineNumber());
            assertEquals(libFile.toAbsolutePath().normalize().toString(), snap.filePath(),
                    "every pause is in the include, never in main.xsl line 4");
        }
        PausedSnapshot snap = pauses.get(0);
        assertNotNull(snap.contextXdm(), "the context item is captured as an XdmItem");
        net.sf.saxon.s9api.XdmValue greeting = snap.localVariables().get(new net.sf.saxon.s9api.QName("greeting"));
        if (greeting != null) { // Saxon may inline a single-use variable; when it keeps the slot, the value is real
            assertEquals("hi", greeting.toString().replace("\"", ""), "the local $greeting carries its value");
        }
        assertTrue(snap.callStack().stream().anyMatch(f -> f.description().contains("helper")),
                "the named template is a call-stack frame: " + snap.callStack());
        DebugStackFrame top = snap.callStack().get(0);
        assertEquals(libFile.toAbsolutePath().normalize().toString(), top.filePath(),
                "frames carry their own module, not the pause file's");
        assertTrue(live.isEmpty(), "no xsl:message in this stylesheet");
    }

    @Test
    void globalVariablesShowTheirValuesAndMessagesStreamLive() throws Exception {
        String xml = "<order><item price=\"2\"/><item price=\"3\"/></order>";
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
                + "  <xsl:variable name=\"total\" select=\"sum(//item/@price)\"/>\n"                        // 2
                + "  <xsl:template match=\"/\">\n"                                                            // 3
                + "    <xsl:message>total is <xsl:value-of select=\"$total\"/></xsl:message>\n"              // 4
                + "    <out><xsl:value-of select=\"$total\"/></out>\n"                                        // 5
                + "  </xsl:template>\n"
                + "</xsl:stylesheet>\n";
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("", 5, true));
        AtomicReference<PausedSnapshot> snapRef = new AtomicReference<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                snapRef.set(session.getPausedSnapshot());
                session.requestContinue();
            }
        });
        java.util.List<String> live = new java.util.concurrent.CopyOnWriteArrayList<>();
        XsltTransformationResult result = XsltTransformationEngine.getInstance().transformWithDebugSession(
                xml, xslt, null, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML, session,
                m -> live.add(m.getMessage()));
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(java.util.List.of("total is 5"), live, "xsl:message streamed while running");
        assertEquals(1, result.getMessages().size(), "and collected into the result");
        PausedSnapshot snap = snapRef.get();
        assertNotNull(snap);
        VariableBinding total = snap.variables().stream().filter(v -> v.name().equals("$total")).findFirst().orElse(null);
        assertNotNull(total, "the evaluated global appears: " + snap.variables());
        assertEquals("5", total.value(), "with its value, not its expression");
        assertEquals(VariableScope.GLOBAL, total.scope());
        assertEquals("5", snap.globalVariables().get(new net.sf.saxon.s9api.QName("total")).toString());
    }

    @Test
    void outerFramesKeepTheirOwnContextItemAndVariables() throws Exception {
        String xml = "<order><item n=\"1\"/><item n=\"2\"/></order>";
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
                + "  <xsl:template match=\"/\">\n"                                                            // 2
                + "    <xsl:variable name=\"outer\" select=\"count(//item) * 10\"/>\n"                       // 3
                + "    <out total=\"{$outer}\"><xsl:apply-templates select=\"//item\"/>"
                + "<xsl:value-of select=\"$outer + 1\"/></out>\n"                                            // 4
                + "  </xsl:template>\n"                                                                        // 5
                + "  <xsl:template match=\"item\">\n"                                                         // 6
                + "    <xsl:variable name=\"inner\" select=\"concat('item-', @n)\"/>\n"                    // 7
                + "    <x><xsl:value-of select=\"$inner\"/><xsl:value-of select=\"$inner\"/></x>\n"       // 8
                + "  </xsl:template>\n"
                + "</xsl:stylesheet>\n";
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("", 8, true));
        AtomicReference<PausedSnapshot> snapRef = new AtomicReference<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                snapRef.compareAndSet(null, session.getPausedSnapshot());
                session.requestContinue();
            }
        });
        XsltTransformationResult result = XsltTransformationEngine.getInstance().transformWithDebugSession(
                xml, xslt, null, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML, session, m -> { });
        assertTrue(result.isSuccess(), result.getErrorMessage());
        PausedSnapshot snap = snapRef.get();
        assertNotNull(snap, "the breakpoint in the item template fired");

        DebugStackFrame top = snap.callStack().get(0);
        assertTrue(top.hasValues(), "the innermost frame carries values: " + top);
        assertEquals("item", ((net.sf.saxon.s9api.XdmNode) top.contextXdm()).getNodeName().getLocalName(),
                "the innermost frame's context is the matched item");

        DebugStackFrame outer = snap.callStack().stream()
                .filter(f -> f.contextXdm() instanceof net.sf.saxon.s9api.XdmNode n
                        && n.getNodeKind() == net.sf.saxon.s9api.XdmNodeKind.DOCUMENT)
                .findFirst().orElse(null);
        assertNotNull(outer, "a caller frame keeps the document node as its context: " + snap.callStack());
        net.sf.saxon.s9api.XdmValue outerVar = outer.localVariables().get(new net.sf.saxon.s9api.QName("outer"));
        assertNotNull(outerVar, "the caller's $outer is captured (used twice, so Saxon keeps its slot)");
        assertEquals("20", outerVar.toString(), "the caller's $outer, not visible in the inner frame");
        assertFalse(top.localVariables().containsKey(new net.sf.saxon.s9api.QName("outer")),
                "the inner frame does not see the caller's locals");
    }

    @Test
    void normalTransformIsUnaffectedByAPausedDebugSession() throws Exception {
        String xml = "<r><a/></r>";
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\"><out><xsl:apply-templates/></out></xsl:template>\n"
                + "  <xsl:template match=\"a\"><x/></xsl:template>\n"
                + "</xsl:stylesheet>\n";
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("", 3, true));
        CountDownLatch pausedLatch = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) pausedLatch.countDown();
        });
        XsltTransformationEngine engine = XsltTransformationEngine.getInstance();
        CountDownLatch doneLatch = new CountDownLatch(1);
        bg.submit(() -> {
            try {
                engine.transformWithDebugSession(xml, xslt, new HashMap<>(),
                        XsltTransformationEngine.OutputFormat.XML, session);
            } finally {
                doneLatch.countDown();
            }
        });
        assertTrue(pausedLatch.await(10, TimeUnit.SECONDS));

        // While the debug run is paused, a normal transform must run to completion with no
        // trace data attached — the debug run lives on its own processor.
        XsltTransformationResult normal = engine.transform(xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML);
        assertTrue(normal.isSuccess(), normal.getErrorMessage());
        assertTrue(normal.getTemplateMatches() == null || normal.getTemplateMatches().isEmpty(),
                "no trace listener on the normal run");

        session.requestStop();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
    }
}
