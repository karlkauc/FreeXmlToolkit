package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The shared, lazily materialized text-edit stream of a code area and the undo-free replacement. */
@ExtendWith(ApplicationExtension.class)
class TextEditsTest {

    @Start
    void start(Stage stage) {
        // the FX toolkit only
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, action);
    }

    @Test
    void oneStreamPerAreaReportsTextEditsButNotStyleChanges() throws Exception {
        List<String> seen = fx(() -> {
            CodeArea area = new CodeArea("<a>one</a>\n<b>two</b>");
            assertSame(TextEdits.of(area), TextEdits.of(area));
            List<String> edits = new ArrayList<>();
            TextEdits.of(area).subscribe(e -> edits.add(e.getPosition() + ":" + e.getRemovedLength() + ":"
                    + e.getInsertedLength() + ":" + e.getRemoved() + ">" + e.getInserted()));
            area.setStyleClass(0, 3, "tag"); // styles only
            area.replaceText(3, 6, "ONE!");
            area.replaceText(0, area.getLength(), "<x/>");
            return edits;
        });
        assertEquals(List.of("3:3:4:one>ONE!", "0:22:4:<a>ONE!</a>\n<b>two</b>><x/>"), seen);
    }

    @Test
    void sameTextComparesLineByLine() throws Exception {
        fx(() -> {
            CodeArea a = new CodeArea("x\ny\nz");
            CodeArea b = new CodeArea("x\ny\nz");
            CodeArea c = new CodeArea("x\nY\nz");
            CodeArea d = new CodeArea("x\nyz\n");
            assertTrue(TextEdits.sameText(a.getDocument(), b.getDocument()));
            assertFalse(TextEdits.sameText(a.getDocument(), c.getDocument()));
            assertFalse(TextEdits.sameText(a.getDocument(), d.getDocument()), "same length, other lines");
            return null;
        });
    }

    @Test
    void aReplacementWithoutUndoLeavesLaterEditsUndoable() throws Exception {
        String text = fx(() -> {
            CodeArea area = new CodeArea("first");
            area.getUndoManager().forgetHistory();
            area.replaceText(0, 0, "a");
            TextEdits.withoutUndo(area, () -> area.replaceText("replaced"));
            assertFalse(area.getUndoManager().isUndoAvailable(), "the replacement is no undo step");
            area.appendText("!");
            area.getUndoManager().preventMerge();
            assertTrue(area.getUndoManager().isUndoAvailable());
            area.undo();
            return area.getText();
        });
        assertEquals("replaced", text);
    }

    @Test
    void plainTextCodeAreaSharesItsPlainChangesAndKeepsCaretAndUndoRight() throws Exception {
        String result = fx(() -> {
            var area = new org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea();
            assertSame(area.multiPlainChanges(), area.multiPlainChanges());
            var reference = new CodeArea();
            for (var a : List.of(area, reference)) {
                a.replaceText("0123456789");
                a.selectRange(6, 8);
            }
            // A lengths-only change moves caret and selection exactly like an ordinary one
            TextEdits.withoutUndo(area, () -> area.replaceText(0, 5, "ab"));
            reference.replaceText(0, 5, "ab");
            assertEquals(reference.getCaretPosition(), area.getCaretPosition());
            assertEquals(reference.getSelection(), area.getSelection());
            area.selectRange(5, 7);
            reference.selectRange(5, 7);
            TextEdits.withoutUndo(area, () -> area.getContent().replace(0, 2, area.getContent().subSequence(0, 0)));
            reference.getContent().replace(0, 2, reference.getContent().subSequence(0, 0));
            assertEquals(reference.getSelection(), area.getSelection(), "a change from the document shifts the selection");
            assertEquals(3, area.getSelection().getStart());
            area.getUndoManager().forgetHistory();
            area.replaceText(0, 0, "X"); // an ordinary edit, recorded with its texts
            area.getUndoManager().preventMerge();
            area.undo();
            return area.getText();
        });
        assertEquals("56789", result);
    }
}
