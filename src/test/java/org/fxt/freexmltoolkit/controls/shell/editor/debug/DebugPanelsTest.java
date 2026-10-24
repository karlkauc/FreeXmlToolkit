package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void breakpointsPanelEditsConditionAndHitRuleAndShowsHits() {
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("a.xsl", 3, true));
        AtomicInteger changed = new AtomicInteger();
        org.fxt.freexmltoolkit.debugger.HitCondition every2 = new org.fxt.freexmltoolkit.debugger.HitCondition(
                org.fxt.freexmltoolkit.debugger.HitCondition.Mode.MULTIPLE_OF, 2);
        String row = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            BreakpointsView view = new BreakpointsView(session);
            view.setOnChanged(changed::incrementAndGet);
            view.setPropertiesPrompt((owner, where, current, hits) -> java.util.Optional.of(
                    new org.fxt.freexmltoolkit.debugger.ui.BreakpointPropertiesDialog.Settings("$i = 10", every2)));
            view.select(0);
            view.editSelected();
            return view.getRowText(0);
        });
        assertEquals("$i = 10|%2|0", row, "the list shows condition and hit rule");
        assertEquals("$i = 10", session.conditionAt("a.xsl", 3), "and the session uses them");
        assertEquals(every2, session.breakpointAt("a.xsl", 3).hit());
        assertEquals(1, changed.get(), "the host persists and refreshes the gutter");
    }

    @Test
    void breakpointsPanelCheckboxDisablesAndEnables() {
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("a.xsl", 3, true));
        AtomicInteger changed = new AtomicInteger();
        boolean enabledAfter = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            BreakpointsView view = new BreakpointsView(session);
            view.setOnChanged(changed::incrementAndGet);
            view.setEnabled(session.breakpointAt("a.xsl", 3), false);
            return view.isEnabledAt(0);
        });
        assertEquals(false, enabledAfter, "the row shows it disabled");
        assertEquals(false, session.hasBreakpoint("a.xsl", 3));
        assertEquals(1, session.getBreakpoints().size(), "kept, not removed");
        assertEquals(1, changed.get(), "gutter refresh + persistence");
    }

    @Test
    void watchesAreEditedInPlaceAndReportChanges() {
        java.util.List<java.util.List<String>> changes = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<String> result = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            WatchView view = new WatchView();
            view.setWatches(List.of("$a", "count(item)"));
            view.setOnWatchesChanged(changes::add);
            view.replaceWatch(1, "count(item) * 2");
            view.addWatch("$b");
            view.replaceWatch(0, "  ");          // blank = remove
            view.select(1);
            Event.fireEvent(view.table(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F2, false, false, false, false));
            assertNotNull(view.table().getEditingCell(), "F2 starts editing the expression");
            view.table().edit(-1, null);
            return view.getExpressions();
        });
        assertEquals(List.of("count(item) * 2", "$b"), result);
        assertEquals(3, changes.size(), "edit, add, remove — setWatches itself is no change");
        assertEquals(result, changes.get(2));
    }

    @Test
    void onlyNamedVariablesCanBecomeWatches() {
        assertEquals(true, VariablesView.isWatchable(new VariableBinding("$total", "5", "", VariableScope.GLOBAL)));
        assertEquals(true, VariablesView.isWatchable(new VariableBinding(".", "<a/>", "", VariableScope.LOCAL)));
        assertEquals(true, VariablesView.isWatchable(new VariableBinding("position()", "1", "", VariableScope.LOCAL)));
        assertEquals(false, VariablesView.isWatchable(new VariableBinding("$$slot3", "x", "", VariableScope.LOCAL)));
    }

    @Test
    void breakpointsPanelMarksUnreachedOnesAfterACompletedRun() {
        DebugSession session = new DebugSession();
        session.addBreakpoint(new Breakpoint("a.xsl", 3, true));
        boolean[] marks = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            BreakpointsView view = new BreakpointsView(session);
            boolean before = view.isUnreachedAt(0);
            session.markRunEnded(true);
            view.refresh();
            return new boolean[]{before, view.isUnreachedAt(0)};
        });
        assertEquals(false, marks[0]);
        assertEquals(true, marks[1], "zero hits after a completed run");
    }

    @Test
    void messagesFilterByLevelAndTextCopyAndClear() {
        java.util.concurrent.atomic.AtomicInteger counted = new java.util.concurrent.atomic.AtomicInteger();
        Object[] r = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            MessagesView view = new MessagesView();
            view.setOnCountChanged(counted::incrementAndGet);
            view.append(new org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage("INFO", "starting order 7", 3, "a.xsl:3"));
            view.append(new org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage("WARNING", "deprecated", 4, "a.xsl:4"));
            view.append(new org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage("TERMINATE", "bad order", 9, "a.xsl:9"));
            view.setFilter(MessagesView.LevelFilter.ERRORS, "");
            int errors = view.getVisibleCount();
            view.setFilter(MessagesView.LevelFilter.MESSAGES, "");
            int infos = view.getVisibleCount();
            view.setFilter(MessagesView.LevelFilter.ALL, "ORDER");
            int text = view.getVisibleCount();
            String copiedShown = view.copyToClipboard();
            view.select(1);
            String copiedSelected = view.copyToClipboard();
            int total = view.getRowCount();
            view.clear();
            return new Object[]{errors, infos, text, copiedShown, copiedSelected, total, view.getRowCount()};
        });
        assertEquals(1, r[0], "TERMINATE counts as an error");
        assertEquals(1, r[1], "plain messages only");
        assertEquals(2, r[2], "text filter is case-insensitive over level, location and text");
        assertEquals("INFO  @ a.xsl:3\nstarting order 7\nTERMINATE  @ a.xsl:9\nbad order", r[3]);
        assertEquals("TERMINATE  @ a.xsl:9\nbad order", r[4], "only the selection");
        assertEquals(3, r[5], "the count (badge) ignores the filter");
        assertEquals(0, r[6]);
        assertEquals(4, counted.get(), "three appends and the clear");
    }

    @Test
    void breakOnMenuAppliesAndRemembersTheOptions() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        try {
            props.set(BreakpointsView.BREAK_ON_ERRORS_KEY, "");
            props.set(BreakpointsView.BREAK_ON_TERMINATE_KEY, "");
            props.set(BreakpointsView.BREAK_ON_MESSAGES_KEY, "");
            DebugSession session = new DebugSession();
            boolean[] state = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
                BreakpointsView view = new BreakpointsView(session);
                boolean[] defaults = {session.isBreakOnErrors(), session.isBreakOnTerminate(), session.isBreakOnMessages()};
                view.breakOnItems().get(0).setSelected(false); // errors off
                view.breakOnItems().get(2).setSelected(true);  // every message on
                return new boolean[]{defaults[0], defaults[1], defaults[2], session.isBreakOnErrors(), session.isBreakOnMessages()};
            });
            assertEquals(true, state[0], "errors on by default");
            assertEquals(true, state[1], "terminate on by default");
            assertEquals(false, state[2], "every message off by default");
            assertEquals(false, state[3]);
            assertEquals(true, state[4]);
            DebugSession next = new DebugSession();
            WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> new BreakpointsView(next));
            assertEquals(false, next.isBreakOnErrors(), "remembered for the next session");
            assertEquals(true, next.isBreakOnMessages());
        } finally {
            props.set(BreakpointsView.BREAK_ON_ERRORS_KEY, "");
            props.set(BreakpointsView.BREAK_ON_TERMINATE_KEY, "");
            props.set(BreakpointsView.BREAK_ON_MESSAGES_KEY, "");
        }
    }
}
