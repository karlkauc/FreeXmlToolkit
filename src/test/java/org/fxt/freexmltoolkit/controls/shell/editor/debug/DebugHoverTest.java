package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.stage.Stage;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Hover values: what is evaluated under the mouse and what the popup shows. */
@ExtendWith(ApplicationExtension.class)
class DebugHoverTest {

    private static final String TEXT = "<xsl:value-of select=\"$total * 2\"/>";
    private static final PausedSnapshot SNAP = new PausedSnapshot("file:/tmp/a.xsl", 1, List.of(), List.of(), "",
            null, Map.of(new QName("total"), new XdmAtomicValue(21)), Map.of(), -1, -1);

    private CodeArea area;

    @Start
    void start(Stage stage) {
        area = new CodeArea(TEXT);
        stage.setScene(new Scene(area, 400, 200));
        stage.show();
    }

    private DebugHover hover(PausedSnapshot scope) {
        WatchEvaluator evaluator = new WatchEvaluator(new Processor(false), Map.of());
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> new DebugHover(area, () -> scope, () -> evaluator, v -> { }));
    }

    @Test
    void evaluatesTheVariableUnderTheMouseInTheScope() throws Exception {
        DebugHover hover = hover(SNAP);
        int onTotal = TEXT.indexOf("$total") + 2;
        assertEquals("$total", WaitForAsyncUtils.waitForAsyncFx(5000, () -> hover.expressionAt(onTotal)));
        AtomicReference<String> shown = new AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> hover.evaluate("$total", shown::set));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> shown.get() != null);
        assertTrue(shown.get().startsWith("21\nxs:int"), "value, then its type: " + shown.get().replace('\n', '|'));
    }

    @Test
    void aSelectionContainingTheMouseIsEvaluatedAsAWhole() throws Exception {
        DebugHover hover = hover(SNAP);
        int start = TEXT.indexOf("$total");
        int end = TEXT.indexOf(" * 2") + 4;
        String expression = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            area.selectRange(start, end);
            return hover.expressionAt(start + 8);
        });
        assertEquals("$total * 2", expression);
        AtomicReference<String> shown = new AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> hover.evaluate(expression, shown::set));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> shown.get() != null);
        assertTrue(shown.get().startsWith("42"), shown.get());
    }

    @Test
    void nothingWhileNotPausedAndErrorsAreShown() throws Exception {
        DebugHover idle = hover(null);
        assertNull(WaitForAsyncUtils.waitForAsyncFx(5000, () -> idle.expressionAt(TEXT.indexOf("$total") + 1)),
                "no evaluation while the run is not paused");
        DebugHover paused = hover(SNAP);
        AtomicReference<String> shown = new AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> paused.evaluate("$missing", shown::set));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> shown.get() != null);
        assertTrue(shown.get().startsWith("ERROR:"), shown.get());
    }
}
