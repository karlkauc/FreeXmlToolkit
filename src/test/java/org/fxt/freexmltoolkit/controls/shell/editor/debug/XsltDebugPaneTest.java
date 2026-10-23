package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
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

    private XsltDebugController controller;
    private Stage stage;

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
        for (String id : new String[]{"#debug-continue", "#debug-step-into", "#debug-step-over", "#debug-step-out",
                "#debug-restart", "#debug-stop", "#debug-pause-on-entry", "#debug-show-in-editor", "#debug-close"}) {
            assertNotNull(pane.lookup(id), id + " must be in the header");
        }
        assertEquals("Ready.", pane.getStatusText());
        assertTrue(pane.lookup("#debug-continue").isDisabled(), "step controls are disabled until paused");
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
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "2".equals(pane.getWatchView().getValueAt(0)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getMessagesView().getRowCount() == 1);

        // F5 while paused continues the run.
        boolean handled = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.handleShortcut(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F5, false, false, false, false)));
        assertTrue(handled, "F5 is the debugger's Continue while paused");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> result.get() != null);
        assertTrue(result.get().isSuccess(), "the finished run's result reaches the host");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pane.getStatusText().startsWith("Finished"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> clears.get() >= 1);
        assertTrue(clears.get() >= 1, "after the run the host is told the pause is over");
        boolean idleKey = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> pane.handleShortcut(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F8, false, false, false, false)));
        assertFalse(idleKey, "F8 is not consumed when the debugger is not paused (it validates)");
    }
}
