package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Consumer;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;
import org.fxt.freexmltoolkit.controls.v2.editor.TextEdits;
import org.fxt.freexmltoolkit.debugger.LineShift;

/**
 * Turns the edits of a code area into {@link LineShift}s, so line markers (breakpoints,
 * bookmarks) follow the text: typing within a line and whole-text replacements (load, reload,
 * reformat) give none. One subscription per area and key.
 */
public final class LineShiftTracker {

    private LineShiftTracker() {
    }

    /**
     * Calls {@code onShift} for every edit of {@code area} that changes line numbers.
     *
     * @param key a feature key; a second install with the same key does nothing
     */
    public static void install(CodeArea area, String key, Consumer<LineShift> onShift) {
        install(area, key, () -> true, onShift);
    }

    /**
     * As {@link #install(CodeArea, String, Consumer)}; edits are only looked at while {@code wanted}
     * says so (e.g. while the document has bookmarks), so the edit texts are not built for nothing.
     */
    public static void install(CodeArea area, String key, java.util.function.BooleanSupplier wanted,
                               Consumer<LineShift> onShift) {
        if (area == null || area.getProperties().containsKey(key)) {
            return;
        }
        area.getProperties().put(key, Boolean.TRUE);
        TextEdits.of(area).subscribe(change -> {
            if (!wanted.getAsBoolean()) {
                return;
            }
            int lengthBefore = area.getLength() - change.getInsertedLength() + change.getRemovedLength();
            if (change.getRemovedLength() == lengthBefore) {
                return; // the whole text was replaced: no edit to follow
            }
            LineShift shift = shiftOf(area, change.getPosition(), change.getRemoved(), change.getInserted());
            if (!shift.isNeutral()) {
                onShift.accept(shift);
            }
        });
    }

    /** The shift of an edit at {@code position} (text before it is unchanged, so the area's current text locates it). */
    static LineShift shiftOf(CodeArea area, int position, String removed, String inserted) {
        var pos = area.offsetToPosition(Math.max(0, Math.min(position, area.getLength())), TwoDimensional.Bias.Forward);
        return new LineShift(pos.getMajor() + 1, pos.getMinor(),
                LineShift.newlines(removed), LineShift.newlines(inserted));
    }
}
