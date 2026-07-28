package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies {@link QueryEditorView}: text roundtrip, XPath/XQuery syntax
 * highlighting on the embedded CodeArea, and the Ctrl+Enter run handler wired
 * via {@link EditorView#configureQuerySupport}.
 */
@ExtendWith(ApplicationExtension.class)
class QueryEditorViewTest {

    private QueryEditorView view;

    @Start
    void start(Stage stage) {
        view = new QueryEditorView(true);
        stage.setScene(new Scene(view.getNode(), 600, 400));
        stage.show();
    }

    @Test
    void textRoundTripAndUndoBaseline() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setText("for $i in /a return $i");
            return null;
        });
        assertEquals("for $i in /a return $i", view.getText());
        // setText resets the undo baseline — loading a file must not be undoable.
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.getCodeArea().getUndoManager().isUndoAvailable()));
    }

    @Test
    void highlightingEmitsXQueryStyleClasses() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setText("(: comment :) for $i in /a/b return \"x\"");
            return null;
        });
        Set<String> classes = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Set<String> all = new HashSet<>();
            view.getCodeArea().getStyleSpans(0, view.getCodeArea().getLength())
                    .forEach(span -> all.addAll(span.getStyle()));
            return all;
        });
        assertTrue(classes.contains("xq-keyword"), "keywords must be highlighted: " + classes);
        assertTrue(classes.contains("xq-comment"), "comments must be highlighted: " + classes);
        assertTrue(classes.contains("xq-var"), "variables must be highlighted: " + classes);
        assertTrue(classes.contains("xq-string"), "strings must be highlighted: " + classes);
    }

    @Test
    void ctrlEnterFiresTheConfiguredRunHandler() {
        AtomicBoolean ran = new AtomicBoolean();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.configureQuerySupport(() -> ran.set(true), () -> "");
            Event.fireEvent(view.getCodeArea(), new KeyEvent(KeyEvent.KEY_PRESSED,
                    "", "", KeyCode.ENTER, false, true, false, false));
            return null;
        });
        assertTrue(ran.get(), "Ctrl+Enter must fire the run handler");
    }

    @Test
    void plainEnterDoesNotFireTheRunHandler() {
        AtomicBoolean ran = new AtomicBoolean();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.configureQuerySupport(() -> ran.set(true), () -> "");
            Event.fireEvent(view.getCodeArea(), new KeyEvent(KeyEvent.KEY_PRESSED,
                    "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
        assertFalse(ran.get(), "a plain Enter must not run the query");
    }
}
