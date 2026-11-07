package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Formulas over large contexts: evaluated off the FX thread, once per model state. */
@ExtendWith(ApplicationExtension.class)
class AsyncFormulasTest {

    private XmlEditorContext context;
    private XmlProcessingInstruction formula;
    private final List<Runnable> queued = new ArrayList<>();
    private final AtomicInteger ready = new AtomicInteger();
    private AsyncFormulas formulas;

    @Start
    void start(Stage stage) {
        // no scene needed: the FX toolkit runs the completion callbacks
    }

    @BeforeEach
    void setUp() {
        StringBuilder xml = new StringBuilder("<list><?fxt-formula name=\"Total\" expression=\"sum(item/@v)\"?>");
        for (int i = 0; i < AsyncFormulas.ASYNC_NODES; i++) {
            xml.append("<item v=\"1\"/>");
        }
        context = new XmlEditorContext();
        context.loadDocumentFromString(xml.append("</list>").toString());
        formula = (XmlProcessingInstruction) context.getDocument().getRootElement().getChild(0);
        formulas = new AsyncFormulas(() -> context.getCommandManager().getRevision(),
                context::getDocument, queued::add);
        formulas.setOnReady(ready::incrementAndGet);
    }

    private GridFormulas.Evaluated onFx() {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> formulas.evaluate(formula));
    }

    private void runWorker() {
        List<Runnable> work = new ArrayList<>(queued);
        queued.clear();
        work.forEach(Runnable::run);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void largeContextShowsPendingThenTheResultOnce() {
        assertEquals(AsyncFormulas.PENDING, onFx().result());
        assertEquals(AsyncFormulas.PENDING, onFx().result());
        assertEquals(1, queued.size(), "one evaluation per model state, however often the grid asks");

        runWorker();
        assertEquals(1, ready.get());
        assertEquals(String.valueOf(AsyncFormulas.ASYNC_NODES), onFx().result());
        assertTrue(queued.isEmpty(), "the cached result is reused");
    }

    @Test
    void aResultThatRacedAnEditIsDroppedAndComputedAgain() {
        onFx();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> context.executeCommand(
                new SetAttributeCommand(context.getDocument().getRootElement().getChildElements().get(1), "v", "2")));
        runWorker(); // finishes after the edit: its result must not be shown
        assertEquals(AsyncFormulas.PENDING, onFx().result());
        assertEquals(1, queued.size(), "evaluated again for the edited model");

        runWorker();
        assertEquals(String.valueOf(AsyncFormulas.ASYNC_NODES + 1), onFx().result());
    }

    @Test
    void smallContextIsEvaluatedAtOnce() {
        XmlEditorContext small = new XmlEditorContext();
        small.loadDocumentFromString("<l><?fxt-formula name=\"T\" expression=\"count(i)\"?><i/><i/></l>");
        var pi = (XmlProcessingInstruction) small.getDocument().getRootElement().getChild(0);
        var sync = new AsyncFormulas(() -> small.getCommandManager().getRevision(), small::getDocument, queued::add);
        assertEquals("2", WaitForAsyncUtils.waitForAsyncFx(5000, () -> sync.evaluate(pi)).result());
        assertTrue(queued.isEmpty());
    }

    @Test
    void largerStopsAtTheLimitAlsoForAWideElement() {
        XmlEditorContext wide = new XmlEditorContext();
        wide.loadDocumentFromString("<r><a/><b/><c/></r>");
        var root = wide.getDocument().getRootElement();
        assertTrue(AsyncFormulas.larger(root, 3), "root + 3 children = 4 nodes");
        assertFalse(AsyncFormulas.larger(root, 4));
        assertFalse(AsyncFormulas.larger(root.getChildElements("a").getFirst(), 1));
    }
}
