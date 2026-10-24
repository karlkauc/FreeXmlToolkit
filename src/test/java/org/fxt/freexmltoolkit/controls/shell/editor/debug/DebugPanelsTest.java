package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.Breakpoint;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class DebugPanelsTest {

    /** Generous FX-thread timeout: the full suite runs one JVM per class and is often under load. */
    private static final long FX_TIMEOUT_MS = 10_000;

    @Start
    void start(Stage stage) {
    }

    @Test
    void variablesViewShowsBindings() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            VariablesView view = new VariablesView();
            view.setVariables(List.of(
                    new VariableBinding("count", "2", "xs:integer", VariableScope.LOCAL)));
            return view.getRowCount();
        });
        assertEquals(1, rows);
    }

    @Test
    void deleteKeyRemovesTheSelectedWatch() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            WatchView view = new WatchView();
            view.addWatch("count(item)");
            view.addWatch("$total");
            view.select(0);
            Event.fireEvent(view.table(), deleteKey());
            return view.getRowCount();
        });
        assertEquals(1, rows, "Delete removes the selected watch");
    }

    @Test
    void deleteKeyRemovesTheSelectedBreakpoint() {
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("a.xsl", 3, true));
        session.addBreakpoint(new Breakpoint("a.xsl", 7, true));
        AtomicInteger changed = new AtomicInteger();
        int rows = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            BreakpointsView view = new BreakpointsView(session);
            view.setOnChanged(changed::incrementAndGet);
            view.select(0);
            Event.fireEvent(view.list(), deleteKey());
            return view.getRowCount();
        });
        assertEquals(1, rows, "Delete removes the selected breakpoint from the list");
        assertEquals(1, session.getBreakpoints().size(), "… and from the session");
        assertEquals(1, changed.get(), "the host is told (gutter refresh)");
    }

    private static KeyEvent deleteKey() {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE, false, false, false, false);
    }

    @Test
    void callStackViewShowsFrames() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            CallStackView view = new CallStackView();
            view.setFrames(List.of(new DebugStackFrame("template match=/", "sheet.xslt", 4, List.of())));
            return view.getRowCount();
        });
        assertEquals(1, rows);
    }

    @Test
    void openValueHandlersReceiveTheRow() {
        java.util.concurrent.atomic.AtomicReference<VariableBinding> opened = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<WatchView.WatchRow> openedWatch = new java.util.concurrent.atomic.AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            VariablesView variables = new VariablesView();
            variables.setVariables(List.of(new VariableBinding("$order", "<order/>", "element(order)", VariableScope.LOCAL)));
            variables.setOnOpenValue(opened::set);
            variables.fireOpen(0);
            WatchView watch = new WatchView();
            watch.addWatch("count(item)");
            watch.setOnOpenValue(openedWatch::set);
            watch.fireOpen(0);
            return null;
        });
        assertEquals("$order", opened.get().name());
        assertEquals("count(item)", openedWatch.get().getExpression());
    }

    @Test
    void callStackOpenHandlerReceivesTheFrame() {
        java.util.concurrent.atomic.AtomicReference<DebugStackFrame> opened = new java.util.concurrent.atomic.AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            CallStackView view = new CallStackView();
            view.setFrames(List.of(new DebugStackFrame("template match=item", "sheet.xslt", 7, List.of())));
            view.setOnOpenValue(opened::set);
            view.fireOpen(0);
            return null;
        });
        assertEquals(7, opened.get().lineNumber());
    }
}
