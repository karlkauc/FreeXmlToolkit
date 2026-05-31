package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * P6: {@link EditorView#replaceTextRegion} rewrites only the given region of the
 * editor document (the thin diff layer applies this), updating the text while
 * preserving the caret position in the untouched part — unlike a full setText,
 * which would reset the caret.
 */
@ExtendWith(ApplicationExtension.class)
class EditorViewIncrementalReplaceTest {

    private static final String OLD = "<root>\n  <a/>\n  <b/>\n  <c/>\n</root>\n";
    private static final String NEW = "<root>\n  <a/>\n  <b/>\n  <cc/>\n</root>\n";

    private XmlEditorView view;

    @Start
    void start(Stage stage) {
        view = new XmlEditorView();
        stage.setScene(new Scene(view.getNode(), 600, 400));
        stage.show();
    }

    @Test
    void replaceRegionUpdatesTextAndPreservesCaret() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setText(OLD);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getCodeArea().moveTo(3); // caret inside "<ro|ot>" — before the change
            return null;
        });

        int[] r = TextDiff.minimalReplaceRegion(OLD, NEW);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.replaceTextRegion(r[0], r[1], NEW.substring(r[0], r[2]));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(NEW, view.getText(), "the document must reflect the new text");
        assertEquals(3, view.getCodeArea().getCaretPosition(),
                "a caret before the changed region must be preserved (no full setText)");
    }
}
