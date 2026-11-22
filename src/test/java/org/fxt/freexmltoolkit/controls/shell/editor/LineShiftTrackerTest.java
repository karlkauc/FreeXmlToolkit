package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.debugger.LineShift;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/** The shifts the tracker reports for typical edits, in particular at a line's end. */
@ExtendWith(ApplicationExtension.class)
class LineShiftTrackerTest {

    private List<LineShift> edit(String text, java.util.function.Consumer<CodeArea> change) {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            CodeArea area = new CodeArea(text);
            List<LineShift> shifts = new ArrayList<>();
            LineShiftTracker.install(area, "test.shift", shifts::add);
            change.accept(area);
            return shifts;
        });
    }

    @Test
    void enterAtTheEndOfALineStartsOnThatLine() {
        // "abc" + caret at its end + Enter
        List<LineShift> shifts = edit("abc\ndef\n", a -> a.insertText(3, "\n"));
        assertEquals(List.of(new LineShift(1, 3, 0, 1)), shifts);
        assertEquals(1, shifts.get(0).apply(1));
        assertEquals(3, shifts.get(0).apply(2));
    }

    @Test
    void deletingTheLineBreakAtTheEndMergesTheNextLine() {
        List<LineShift> shifts = edit("abc\ndef\nghi", a -> a.deleteText(3, 4));
        assertEquals(List.of(new LineShift(1, 3, 1, 0)), shifts);
        assertEquals(-1, shifts.get(0).apply(2));
        assertEquals(2, shifts.get(0).apply(3));
    }

    @Test
    void insertAtALineStart() {
        List<LineShift> shifts = edit("abc\ndef\n", a -> a.insertText(4, "x\ny\n"));
        assertEquals(List.of(new LineShift(2, 0, 0, 2)), shifts);
    }

    @Test
    void typingWithinALineAndReplacingEverythingGiveNothing() {
        assertTrue(edit("abc\ndef", a -> a.insertText(1, "zz")).isEmpty());
        assertTrue(edit("abc\ndef", a -> a.replaceText("1\n2\n3")).isEmpty());
    }

    @Test
    void installsOncePerKey() {
        List<LineShift> shifts = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            CodeArea area = new CodeArea("a\nb");
            List<LineShift> got = new ArrayList<>();
            LineShiftTracker.install(area, "k", got::add);
            LineShiftTracker.install(area, "k", got::add);
            area.insertText(0, "\n");
            return got;
        });
        assertEquals(1, shifts.size());
    }
}
