package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Filters over large contexts: evaluated off the FX thread, once per model state. */
@ExtendWith(ApplicationExtension.class)
class AsyncFiltersTest {

    private static final String EXPRESSION = "item[@v = '2']";

    private XmlEditorContext context;
    private XmlElement root;
    private final List<Runnable> queued = new ArrayList<>();
    private final AtomicInteger ready = new AtomicInteger();
    private AsyncFilters filters;

    @Start
    void start(Stage stage) {
        // no scene needed: the FX toolkit runs the completion callbacks
    }

    @BeforeEach
    void setUp() {
        StringBuilder xml = new StringBuilder("<list>");
        for (int i = 0; i < AsyncFormulas.ASYNC_NODES; i++) {
            xml.append(i == 5 ? "<item v=\"2\"/>" : "<item v=\"1\"/>");
        }
        context = new XmlEditorContext();
        context.loadDocumentFromString(xml.append("</list>").toString());
        root = context.getDocument().getRootElement();
        filters = new AsyncFilters(() -> context.getCommandManager().getRevision(), context::getDocument, queued::add);
        filters.setOnReady(ready::incrementAndGet);
    }

    private AsyncFilters.Outcome onFx(String expression) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> filters.evaluate(root, expression));
    }

    private void runWorker() {
        List<Runnable> work = new ArrayList<>(queued);
        queued.clear();
        work.forEach(Runnable::run);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void largeContextIsPendingThenMatchesOnce() {
        AsyncFilters.Outcome first = onFx(EXPRESSION);
        assertTrue(first.pending());
        assertNull(first.matches(), "no earlier result: the rows stay unfiltered meanwhile");
        onFx(EXPRESSION);
        assertEquals(1, queued.size(), "one evaluation per model state, however often the grid asks");

        runWorker();
        assertEquals(1, ready.get());
        AsyncFilters.Outcome done = onFx(EXPRESSION);
        assertFalse(done.pending());
        assertEquals(1, done.matches().size());
        assertTrue(queued.isEmpty(), "the cached result is reused");
    }

    @Test
    void afterAnEditThePreviousResultIsShownUntilTheNewOneArrives() {
        onFx(EXPRESSION);
        runWorker();
        XmlElement other = root.getChildElements().get(9);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> context.executeCommand(new SetAttributeCommand(other, "v", "2")));

        AsyncFilters.Outcome stale = onFx(EXPRESSION);
        assertTrue(stale.pending());
        assertEquals(1, stale.matches().size(), "the previous result while the new one is computed");

        runWorker();
        assertEquals(2, onFx(EXPRESSION).matches().size());
    }

    @Test
    void aResultThatRacedAnEditIsDropped() {
        onFx(EXPRESSION);
        XmlElement other = root.getChildElements().get(9);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> context.executeCommand(new SetAttributeCommand(other, "v", "2")));
        runWorker(); // computed for the old model, back after the edit: dropped
        assertTrue(onFx(EXPRESSION).pending());
        runWorker();
        assertEquals(2, onFx(EXPRESSION).matches().size());
    }

    @Test
    void anInvalidExpressionReportsItsError() {
        onFx("item[");
        runWorker();
        AsyncFilters.Outcome failed = onFx("item[");
        assertFalse(failed.pending());
        assertNotNull(failed.error());
        assertNull(failed.matches());
    }

    @Test
    void smallContextIsEvaluatedAtOnce() {
        XmlEditorContext small = new XmlEditorContext();
        small.loadDocumentFromString("<l><i v=\"2\"/><i/></l>");
        var sync = new AsyncFilters(() -> small.getCommandManager().getRevision(), small::getDocument, queued::add);
        AsyncFilters.Outcome outcome = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> sync.evaluate(small.getDocument().getRootElement(), "i[@v]"));
        assertFalse(outcome.pending());
        assertEquals(1, outcome.matches().size());
        assertTrue(queued.isEmpty());
    }
}
