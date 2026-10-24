package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The DEBUG pane: controls, state-driven button gating, live messages and the result callback. */
@ExtendWith(ApplicationExtension.class)
class XsltDebugPaneTest {

    private static final long FX_TIMEOUT_MS = 10_000;

    private static final String XML = "<root><item>a</item><item>b</item></root>";
    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <xsl:message>hello from xslt</xsl:message>
                <out>
                  <xsl:value-of select="count(//item)"/>
                </out>
              </xsl:template>
            </xsl:stylesheet>
            """;
    /** Static error: unknown function → compilation fails, the error lands in Messages. */
    private static final String BROKEN_XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <out><xsl:value-of select="no-such-function(//item)"/></out>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private XsltDebugController controller;
    private Stage stage;

    /**
     * The pane remembers the tabs the user picks in the shared test preferences; every test
     * here starts from (and leaves) the defaults: Variables and Call Stack.
     */
    @BeforeEach
    @AfterEach
    void resetRememberedTabs() {
        try {
            var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
            props.set(XsltDebugPane.LEFT_TAB_KEY, "debug-tab-variables");
            props.set(XsltDebugPane.RIGHT_TAB_KEY, "debug-tab-call-stack");
        } catch (Throwable ignored) {
            // no properties service — nothing remembered either
        }
    }

    @Start
    void start(Stage stage) {
        this.stage = stage;
        controller = new XsltDebugController(org.fxt.freexmltoolkit.FxtGui.executorService);
    }

    @Test
    void buildsWithAllPanelsAndHeaderControls() {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        assertNotNull(pane.getBreakpointsView());
        assertNotNull(pane.getVariablesView());
        assertNotNull(pane.getCallStackView());
        assertNotNull(pane.getWatchView());
        assertNotNull(pane.getMessagesView());
        for (String id : new String[]{"#debug-continue", "#debug-pause", "#debug-step-into", "#debug-step-over", "#debug-step-out",
                "#debug-restart", "#debug-stop", "#debug-pause-on-entry", "#debug-mute", "#debug-show-in-editor",
                "#debug-close"}) {
            assertNotNull(pane.lookup(id), id + " must be in the header");
        }
        assertEquals("Ready.", pane.getStatusText());
        assertTrue(pane.lookup("#debug-continue").isDisabled(), "step controls are disabled until paused");
    }

    @Test
    void panelsAreGroupedIntoTwoTabGroups() {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        assertEquals(2, pane.getPanels().getItems().size(), "one split with two tab groups");
        assertEquals(List.of(pane.getVariablesView(), pane.getWatchView()),
                pane.getLeftTabs().getTabs().stream().map(Tab::getContent).toList(),
                "left group: Variables | Watch");
        assertEquals(List.of(pane.getCallStackView(), pane.getMessagesView(), pane.getBreakpointsView(),
                        pane.getOutputView()),
                pane.getRightTabs().getTabs().stream().map(Tab::getContent).toList(),
                "right group: Call Stack | Messages | Breakpoints | Output");
        assertEquals("Variables", pane.getLeftTabs().getTabs().get(0).getText());
        assertEquals("Breakpoints", pane.getBreakpointsTab().getText());
        assertTrue(pane.lookupAll(".fxt-side-panel-title").isEmpty(), "the tab header is the only title");
        assertEquals(1, pane.getPanels().getDividers().size());
    }

    @Test
    void countBadgesFollowMessagesAndBreakpoints() {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        assertNull(pane.getMessagesBadgeText(), "no badge without messages");
        assertNull(pane.getBreakpointsBadgeText(), "no badge without breakpoints");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            pane.getMessagesView().append(new TransformationMessage("INFO", "one", 1, ""));
            pane.getMessagesView().append(new TransformationMessage("INFO", "two", 2, ""));
        });
        assertEquals("2", pane.getMessagesBadgeText());
        assertTrue(pane.getMessagesTab().getStyleClass().contains(XsltDebugPane.UPDATED_CLASS),
                "Messages is marked while Call Stack is in front");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS,
                () -> pane.getRightTabs().getSelectionModel().select(pane.getMessagesTab()));
        assertFalse(pane.getMessagesTab().getStyleClass().contains(XsltDebugPane.UPDATED_CLASS),
                "selecting the tab clears the marker");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getMessagesView().clear());
        assertNull(pane.getMessagesBadgeText(), "badge hidden again once cleared");

        controller.getSession().addBreakpoint(new Breakpoint("", 5, true));
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getBreakpointsView().refresh());
        assertEquals("1", pane.getBreakpointsBadgeText());
    }

    @Test
    void failedRunBringsMessagesToTheFront() throws Exception {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            XsltDebugPane p = new XsltDebugPane(controller);
            stage.setScene(new Scene(p, 900, 400));
            stage.show();
            p.getRightTabs().getSelectionModel().select(0); // Call Stack in front
            return p;
        });
        AtomicReference<org.fxt.freexmltoolkit.service.XsltTransformationResult> result = new AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.setOnResult(result::set));
        controller.setPauseOnEntry(false);
        controller.start(XML, BROKEN_XSLT, Map.of(), OutputFormat.XML);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> result.get() != null);
        assertFalse(result.get().isSuccess(), "the stylesheet does not compile");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().startsWith("Error"));
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(pane.getMessagesTab(), pane.getRightTabs().getSelectionModel().getSelectedItem(),
                "a failed run shows its error in Messages");
    }

    @Test
    void dividerIsClampedAndDefaultsToHalf() {
        assertEquals(0.2, XsltDebugPane.clampDivider(0.05), 1e-9);
        assertEquals(0.8, XsltDebugPane.clampDivider(0.95), 1e-9);
        assertEquals(0.5, XsltDebugPane.clampDivider(Double.NaN), 1e-9);
        assertEquals(0.37, XsltDebugPane.clampDivider(0.37), 1e-9);

        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            XsltDebugPane p = new XsltDebugPane(controller);
            stage.setScene(new Scene(p, 1400, 400));
            stage.show();
            return p;
        });
        WaitForAsyncUtils.waitForFxEvents();
        double position = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS,
                () -> pane.getPanels().getDividerPositions()[0]);
        assertEquals(0.5, position, 0.05, "both groups share the width by default");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.applyDivider(0.95));
        WaitForAsyncUtils.waitForFxEvents();
        double clamped = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS,
                () -> pane.getPanels().getDividerPositions()[0]);
        assertTrue(clamped <= 0.8 + 0.01, "applyDivider clamps to the maximum, was " + clamped);
        double rightWidth = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getRightTabs().getWidth());
        assertTrue(rightWidth >= pane.getRightTabs().getMinWidth() - 1,
                "the right group keeps its minimum width, was " + rightWidth);
    }

    @Test
    void disposeDetachesStateListener() {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, pane::dispose);
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> controller.getSession().startSession());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Ready.", pane.getStatusText(), "status unchanged after dispose");
    }

    @Test
    void pauseShowsStateAndKeysDriveTheSession() throws Exception {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            XsltDebugPane p = new XsltDebugPane(controller);
            stage.setScene(new Scene(p, 900, 400));
            stage.show();
            return p;
        });
        AtomicReference<PausedSnapshot> paused = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger clears = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<org.fxt.freexmltoolkit.service.XsltTransformationResult> result = new AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            pane.setOnPaused(snap -> {
                if (snap == null) {
                    clears.incrementAndGet();
                } else {
                    paused.set(snap);
                }
            });
            pane.setOnResult(result::set);
            pane.getWatchView().addWatch("count(//item)");
        });
        controller.getSession().addBreakpoint(new Breakpoint("", 5, true)); // the <xsl:value-of> line
        controller.setPauseOnEntry(false);
        controller.start(XML, XSLT, Map.of(), OutputFormat.XML);

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.PAUSED);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().startsWith("Paused at"));
        assertNotNull(paused.get(), "the host is told where the run paused");
        assertEquals(5, paused.get().lineNumber());
        assertFalse(pane.lookup("#debug-continue").isDisabled(), "Continue is enabled while paused");
        assertTrue(pane.getVariablesView().getRowCount() > 0, "variables are filled on pause");
        assertTrue(pane.getCallStackView().getRowCount() > 0, "call stack is filled on pause");

        // The value viewer resolves rows to their full values from the pause.
        PausedSnapshot snap = controller.getSession().getPausedSnapshot();
        ValueSource ctx = XsltDebugPane.resolveVariable(
                new org.fxt.freexmltoolkit.debugger.VariableBinding(".", "<out/>", "element(out)",
                        org.fxt.freexmltoolkit.debugger.VariableScope.LOCAL), snap);
        assertTrue(ctx.hasValue(), "the context item has its full value");
        ValueSource pos = XsltDebugPane.resolveVariable(
                new org.fxt.freexmltoolkit.debugger.VariableBinding("position()", "1", "xs:integer",
                        org.fxt.freexmltoolkit.debugger.VariableScope.LOCAL), snap);
        assertFalse(pos.hasValue(), "position() falls back to the row text");
        assertEquals("1", pos.fallbackText());
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.openViewer(ctx));
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.openViewer(ctx));
        assertEquals(1, pane.openViewers().size(), "the same row of the same pause opens one window");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "2".equals(pane.getWatchView().getValueAt(0)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getMessagesView().getRowCount() == 1);
        // What is written so far (the serializer holds back a just-opened start tag until it
        // knows whether the element stays empty, so here it may still be empty).
        String soFar = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getOutputView().getText());
        assertFalse(soFar.contains("</out>"), "not the rest of the output: " + soFar);
        assertTrue(pane.getOutputView().getInfo().startsWith("Written so far"), pane.getOutputView().getInfo());

        // F5 while paused continues the run.
        boolean handled = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.handleShortcut(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F5, false, false, false, false)));
        assertTrue(handled, "F5 is the debugger's Continue while paused");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> result.get() != null);
        assertTrue(result.get().isSuccess(), "the finished run's result reaches the host");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().startsWith("Finished"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getOutputView().getText().contains("</out>"));
        assertTrue(pane.getOutputView().getText().startsWith(soFar), "the paused output is a prefix of the result");
        assertTrue(pane.getOutputView().getInfo().startsWith("Complete result"), "the finished run's full result");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> clears.get() >= 1);
        assertTrue(clears.get() >= 1, "after the run the host is told the pause is over");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, pane::dispose);
        assertTrue(pane.openViewers().isEmpty(), "ending the session closes the value windows");
        boolean idleKey = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.handleShortcut(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F8, false, false, false, false)));
        assertFalse(idleKey, "F8 is not consumed when the debugger is not paused (it validates)");
    }

    @Test
    void resolveVariableFindsLocalsAndGlobalsByDisplayName() {
        net.sf.saxon.s9api.XdmAtomicValue local = new net.sf.saxon.s9api.XdmAtomicValue("local");
        net.sf.saxon.s9api.XdmAtomicValue global = new net.sf.saxon.s9api.XdmAtomicValue("global");
        net.sf.saxon.s9api.XdmAtomicValue prefixed = new net.sf.saxon.s9api.XdmAtomicValue("p");
        PausedSnapshot snap = new PausedSnapshot("file:/a.xsl", 3, List.of(), List.of(), "", null,
                Map.of(new net.sf.saxon.s9api.QName("x"), local),
                Map.of(new net.sf.saxon.s9api.QName("x"), global,
                        new net.sf.saxon.s9api.QName("f", "urn:f", "cfg"), prefixed), 1, 1);
        assertSame(local, XsltDebugPane.resolveVariable(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                "$x", "local", "xs:string", org.fxt.freexmltoolkit.debugger.VariableScope.LOCAL), snap).value());
        assertSame(global, XsltDebugPane.resolveVariable(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                "$x", "global", "xs:string", org.fxt.freexmltoolkit.debugger.VariableScope.GLOBAL), snap).value());
        assertSame(prefixed, XsltDebugPane.resolveVariable(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                "$f:cfg", "p", "xs:string", org.fxt.freexmltoolkit.debugger.VariableScope.GLOBAL), snap).value());
        assertFalse(XsltDebugPane.resolveVariable(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                "$missing", "?", "", org.fxt.freexmltoolkit.debugger.VariableScope.LOCAL), snap).hasValue());
    }

    @Test
    void resolveFrameScopesXPathToTheFrame() throws Exception {
        net.sf.saxon.s9api.Processor processor = new net.sf.saxon.s9api.Processor(false);
        net.sf.saxon.s9api.XdmNode doc = processor.newDocumentBuilder().build(
                new javax.xml.transform.stream.StreamSource(new java.io.StringReader("<order><item n='1'/></order>")));
        net.sf.saxon.s9api.XdmNode item = (net.sf.saxon.s9api.XdmNode) processor.newXPathCompiler()
                .evaluateSingle("//item", doc);
        DebugStackFrame inner = new DebugStackFrame("template match=item", "file:/a.xsl", 8, List.of(),
                DebugStackFrame.FrameKind.TEMPLATE, item,
                Map.of(new net.sf.saxon.s9api.QName("inner"), new net.sf.saxon.s9api.XdmAtomicValue("item-1")));
        DebugStackFrame outer = new DebugStackFrame("template match=/", "file:/a.xsl", 4, List.of(),
                DebugStackFrame.FrameKind.TEMPLATE, doc,
                Map.of(new net.sf.saxon.s9api.QName("outer"), new net.sf.saxon.s9api.XdmAtomicValue(20)));
        DebugStackFrame bare = new DebugStackFrame("apply-templates", "file:/a.xsl", 4, List.of());
        PausedSnapshot snap = new PausedSnapshot("file:/a.xsl", 8, List.of(inner, outer, bare), List.of(), "",
                item, inner.localVariables(),
                Map.of(new net.sf.saxon.s9api.QName("g"), new net.sf.saxon.s9api.XdmAtomicValue("global")), 1, 1);

        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        ValueSource source = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.resolveFrame(outer, snap));
        assertSame(doc, source.value(), "the frame's context item is the value");
        assertEquals("frame at a.xsl:4", source.location());
        org.fxt.freexmltoolkit.debugger.WatchEvaluator eval =
                new org.fxt.freexmltoolkit.debugger.WatchEvaluator(processor, Map.of());
        assertEquals("21", eval.evaluateValue("$outer + 1", source.snapshot(), source.value()).toString(),
                "XPath sees the frame's own locals");
        assertEquals("global", eval.evaluateValue("$g", source.snapshot(), source.value()).toString(),
                "and the globals of the pause");
        assertThrows(net.sf.saxon.s9api.SaxonApiException.class,
                () -> eval.evaluateValue("$inner", source.snapshot(), source.value()),
                "but not the inner frame's locals");
        assertSame(source.snapshot(), WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS,
                () -> pane.resolveFrame(outer, snap)).snapshot(), "the frame scope is reused (one window per frame)");

        ValueSource none = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.resolveFrame(bare, snap));
        assertFalse(none.hasValue(), "a frame without captured values falls back to text");
        assertTrue(none.fallbackText().startsWith("apply-templates"), none.fallbackText());
    }

    @Test
    void variablesFollowTheSelectedFrame() throws Exception {
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
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getWatchView().addWatch("$outer"));
        controller.getSession().addBreakpoint(new Breakpoint("", 8, true));
        controller.setPauseOnEntry(false);
        controller.start(xml, xslt, Map.of(), OutputFormat.XML);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.PAUSED);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getVariablesView().getRowCount() > 0);
        WaitForAsyncUtils.waitForFxEvents();

        java.util.List<String> topNames = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> names(pane));
        assertTrue(topNames.contains("$inner"), "the innermost frame shows its locals: " + topNames);
        assertFalse(topNames.contains("$outer"), topNames.toString());
        assertNull(pane.getVariablesView().getFrameInfo(), "no banner for the innermost frame");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getWatchView().getValueAt(0).startsWith("ERROR"));

        PausedSnapshot snap = controller.getSession().getPausedSnapshot();
        int outerIndex = -1;
        for (int i = 0; i < snap.callStack().size(); i++) {
            if (snap.callStack().get(i).localVariables().containsKey(new net.sf.saxon.s9api.QName("outer"))) {
                outerIndex = i;
                break;
            }
        }
        assertTrue(outerIndex > 0, "an outer frame carries $outer: " + snap.callStack());
        final int idx = outerIndex;
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getCallStackView().selectFrame(idx));
        java.util.List<String> outerNames = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> names(pane));
        assertTrue(outerNames.contains("$outer"), "the selected frame's locals: " + outerNames);
        assertFalse(outerNames.contains("$inner"), outerNames.toString());
        assertEquals(".", outerNames.get(0), "its context item comes first");
        assertTrue(pane.getVariablesView().getFrameInfo().startsWith("Frame " + (idx + 1) + " of "),
                pane.getVariablesView().getFrameInfo());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "20".equals(pane.getWatchView().getValueAt(0)));

        ValueSource outerVar = XsltDebugPane.resolveVariable(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                "$outer", "20", "xs:integer", org.fxt.freexmltoolkit.debugger.VariableScope.LOCAL),
                pane.frameScope(snap.callStack().get(idx), snap));
        assertEquals("20", outerVar.value().toString(), "values open in the selected frame's scope");

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.getCallStackView().selectFrame(0));
        assertNull(pane.getVariablesView().getFrameInfo(), "back on the innermost frame");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> names(pane)).contains("$inner"));
        controller.stop();
    }

    private static java.util.List<String> names(XsltDebugPane pane) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < pane.getVariablesView().getRowCount(); i++) {
            names.add(pane.getVariablesView().getNameAt(i));
        }
        return names;
    }

    @Test
    void muteToggleMutesTheSessionAndRefreshesGutters() {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        java.util.concurrent.atomic.AtomicInteger changed = new java.util.concurrent.atomic.AtomicInteger();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            pane.setOnBreakpointsChanged(changed::incrementAndGet);
            ((javafx.scene.control.ToggleButton) pane.lookup("#debug-mute")).fire();
        });
        assertTrue(controller.getSession().isMuted());
        assertTrue(pane.isMuted());
        assertEquals(1, changed.get(), "gutters re-render (hollow markers)");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS,
                () -> ((javafx.scene.control.ToggleButton) pane.lookup("#debug-mute")).fire());
        assertFalse(controller.getSession().isMuted());
    }

    @Test
    void f6PausesALongRunningLoop() throws Exception {
        // A long loop without breakpoints (no recursion: that would end in a stack overflow).
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <out>
                      <xsl:for-each select="1 to 100000000">
                        <xsl:if test=". = 0"><x/></xsl:if>
                      </xsl:for-each>
                    </out>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            XsltDebugPane p = new XsltDebugPane(controller);
            stage.setScene(new Scene(p, 900, 400));
            stage.show();
            return p;
        });
        controller.setPauseOnEntry(false);
        controller.start(XML, xslt, Map.of(), OutputFormat.XML);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.RUNNING);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !pane.lookup("#debug-pause").isDisabled());
        boolean handled = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.handleShortcut(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F6, false, false, false, false)));
        assertTrue(handled, "F6 is Pause while running");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.PAUSED);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().contains("paused by request"));
        assertTrue(pane.lookup("#debug-pause").isDisabled(), "Pause is off while paused");
        assertFalse(pane.lookup("#debug-continue").isDisabled());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getCallStackView().getRowCount() > 0);
        controller.stop();
    }

    @Test
    void debugToCursorRunsToTheLineWithoutPausingOnEntry() throws Exception {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        controller.setPauseOnEntry(true); // would stop at the first instruction …
        controller.setRunToOnStart("", 5); // … but Debug to Cursor runs to the value-of line
        controller.start(XML, XSLT, Map.of(), OutputFormat.XML);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.PAUSED);
        PausedSnapshot snap = controller.getSession().getPausedSnapshot();
        assertEquals(5, snap.lineNumber(), "first stop is the cursor line");
        assertEquals("run to cursor", controller.getSession().getPauseNote());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().contains("run to cursor"));
        controller.stop();
    }

    @Test
    void addToWatchesFromVariablesShowsTheWatchTab() {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
        java.util.List<String> saved = new java.util.ArrayList<>();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            pane.getWatchView().setOnWatchesChanged(saved::addAll);
            pane.getVariablesView().setVariables(List.of(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                    "$total", "5", "xs:integer", org.fxt.freexmltoolkit.debugger.VariableScope.GLOBAL)));
            pane.getVariablesView().fireAddToWatches(0);
        });
        assertEquals(List.of("$total"), pane.getWatchView().getExpressions());
        assertEquals(List.of("$total"), saved, "persisted through the host callback");
        assertEquals(1, pane.getLeftTabs().getSelectionModel().getSelectedIndex(), "Watch comes to the front");
    }

    @Test
    void theUsersTabChoiceIsRememberedButNotThePanesOwnSwitches() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        try {
            XsltDebugPane first = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
                XsltDebugPane p = new XsltDebugPane(controller);
                p.getLeftTabs().getSelectionModel().select(1);              // user: Watch
                p.getRightTabs().getSelectionModel().select(p.getBreakpointsTab()); // user: Breakpoints
                return p;
            });
            assertEquals("debug-tab-watch", props.get(XsltDebugPane.LEFT_TAB_KEY));
            assertEquals("debug-tab-breakpoints", props.get(XsltDebugPane.RIGHT_TAB_KEY));

            XsltDebugPane second = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new XsltDebugPane(controller));
            assertEquals(1, second.getLeftTabs().getSelectionModel().getSelectedIndex(), "Watch restored");
            assertSame(second.getBreakpointsTab(), second.getRightTabs().getSelectionModel().getSelectedItem());

            // The pane's own switch (Add to Watches) does not overwrite the user's choice.
            WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
                second.getLeftTabs().getSelectionModel().select(0);          // user: Variables
                second.getVariablesView().setVariables(List.of(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                        "$x", "1", "xs:integer", org.fxt.freexmltoolkit.debugger.VariableScope.LOCAL)));
                second.getVariablesView().fireAddToWatches(0);               // pane: Watch
            });
            assertEquals(1, second.getLeftTabs().getSelectionModel().getSelectedIndex(), "Watch shown");
            assertEquals("debug-tab-variables", props.get(XsltDebugPane.LEFT_TAB_KEY), "Variables stays the choice");
            assertNotNull(first);
        } finally {
            resetRememberedTabs();
        }
    }

    @Test
    void aRuntimeErrorPausesAtTheFailingLineWithARedStatus() throws Exception {
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <xsl:variable name="zero" select="count(//nothing)"/>
                    <out><xsl:value-of select="10 idiv $zero"/></out>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            XsltDebugPane p = new XsltDebugPane(controller); // "Break on: Dynamic errors" is on by default
            stage.setScene(new Scene(p, 900, 400));
            stage.show();
            return p;
        });
        controller.getSession().setBreakOnErrors(true);
        controller.setPauseOnEntry(false);
        controller.start(XML, xslt, Map.of(), OutputFormat.XML);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.PAUSED);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().contains("error FOAR0001"));
        assertEquals(4, controller.getSession().getPausedSnapshot().lineNumber());
        assertTrue(pane.lookup("#debug-status").getStyleClass().contains(XsltDebugPane.CONDITION_ERROR_CLASS), "red");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getVariablesView().getRowCount() > 0);

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, pane::onContinue);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> pane.getStatusText().startsWith("Error"));
        assertSame(pane.getMessagesTab(), pane.getRightTabs().getSelectionModel().getSelectedItem(),
                "after Continue the run fails as before");
    }

    @Test
    void evaluateOpensTheViewerOnTheFrameContext() throws Exception {
        XsltDebugPane pane = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            XsltDebugPane p = new XsltDebugPane(controller);
            stage.setScene(new Scene(p, 900, 400));
            stage.show();
            return p;
        });
        assertTrue(pane.lookup("#debug-evaluate").isDisabled(), "only while paused");
        controller.getSession().addBreakpoint(new Breakpoint("", 5, true));
        controller.setPauseOnEntry(false);
        controller.start(XML, XSLT, Map.of(), OutputFormat.XML);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> controller.getSession().getState() == DebugSession.State.PAUSED);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !pane.lookup("#debug-evaluate").isDisabled());
        boolean handled = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.handleShortcut(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F8, false, false, true, false)));
        assertTrue(handled, "Alt+F8 is Evaluate while paused");
        ValueViewerWindow window = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.openViewers().get(0));
        assertEquals("Evaluate", window.getSource().title());
        assertSame(controller.getSession().getPausedSnapshot().contextXdm(), window.getSource().value(),
                "the frame's context item");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> window.runXPath("count(//item)"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "2".equals(window.getResultText()));
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, pane::dispose);
        controller.stop();
    }

    @Test
    void xsltContextRowsOpenTheirFullValues() {
        net.sf.saxon.s9api.XdmValue group = new net.sf.saxon.s9api.XdmValue(List.of(
                new net.sf.saxon.s9api.XdmAtomicValue("x"), new net.sf.saxon.s9api.XdmAtomicValue("y")));
        PausedSnapshot snap = new PausedSnapshot("file:/a.xsl", 3, List.of(), List.of(), "", null, Map.of(), Map.of(),
                1, 1, Map.of("current-group()", group));
        ValueSource source = XsltDebugPane.resolveVariable(new org.fxt.freexmltoolkit.debugger.VariableBinding(
                "current-group()", "x, y", "2 items", org.fxt.freexmltoolkit.debugger.VariableScope.XSLT), snap);
        assertSame(group, source.value());
        assertEquals("XSLT", source.scope());
    }
}
