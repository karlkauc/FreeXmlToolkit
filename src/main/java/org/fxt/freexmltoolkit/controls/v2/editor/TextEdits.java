package org.fxt.freexmltoolkit.controls.v2.editor;

import java.util.List;

import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.RichTextChange;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.util.UndoUtils;
import org.reactfx.EventStream;
import org.reactfx.EventSource;

/**
 * The text edits of a code area as one shared stream, without building the changed text.
 * <p>
 * RichTextFX's {@code plainTextChanges()} creates a new stream per call, and every subscriber
 * turns each change into a {@code PlainTextChange} holding the removed and the inserted text
 * as strings: replacing a 150-MB document built 300 MB of strings per subscriber (five on an
 * XML tab) on the FX thread. Here the area has one stream; an edit knows its position and
 * lengths, and builds its texts only when asked.
 */
public final class TextEdits {

    private static final Object KEY = TextEdits.class;

    private TextEdits() {
    }

    /**
     * One text edit: {@code removedLength} characters at {@code position} replaced by
     * {@code insertedLength} characters. The texts are built on first request.
     */
    public static final class TextEdit {
        private final RichTextChange<?, ?, ?> change;
        private String removed;
        private String inserted;

        TextEdit(RichTextChange<?, ?, ?> change) {
            this.change = change;
        }

        /** @return the offset of the edit */
        public int getPosition() {
            return change.getPosition();
        }

        /** @return the number of characters removed */
        public int getRemovedLength() {
            return change.getRemoved().length();
        }

        /** @return the number of characters inserted */
        public int getInsertedLength() {
            return change.getInserted().length();
        }

        /** @return the removed text (built on first call) */
        public String getRemoved() {
            if (removed == null) {
                removed = change.getRemoved().getText();
            }
            return removed;
        }

        /** @return the inserted text (built on first call) */
        public String getInserted() {
            if (inserted == null) {
                inserted = change.getInserted().getText();
            }
            return inserted;
        }
    }

    /**
     * @param area the code area
     * @return the area's text edits (changes of styles only are left out), one stream per area
     */
    @SuppressWarnings("unchecked")
    public static <PS, SEG, S> EventStream<TextEdit> of(GenericStyledArea<PS, SEG, S> area) {
        Object known = area.getProperties().get(KEY);
        if (known != null) {
            return (EventStream<TextEdit>) known;
        }
        EventSource<TextEdit> edits = new EventSource<>();
        area.multiRichChanges().subscribe(list -> {
            for (RichTextChange<PS, SEG, S> change : list) {
                if (!sameText(change.getRemoved(), change.getInserted())) {
                    edits.push(new TextEdit(change));
                }
            }
        });
        area.getProperties().put(KEY, edits);
        return edits;
    }

    /**
     * @return whether both documents hold the same text; line by line, so a different text
     *         stops at the first different line and an equal one never builds one large string
     */
    public static boolean sameText(StyledDocument<?, ?, ?> a, StyledDocument<?, ?, ?> b) {
        if (a.length() != b.length()) {
            return false;
        }
        List<? extends Paragraph<?, ?, ?>> pa = a.getParagraphs();
        List<? extends Paragraph<?, ?, ?>> pb = b.getParagraphs();
        if (pa.size() != pb.size()) {
            return false;
        }
        for (int i = 0; i < pa.size(); i++) {
            if (!pa.get(i).getText().equals(pb.get(i).getText())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs a replacement of the whole document or a large part of it without recording it for
     * undo: the default (plain-text) undo manager built the removed and inserted text as strings
     * and kept them. The area gets a new default undo manager afterwards, so the text's undo
     * history starts after the replacement (as after loading a file).
     *
     * @param area        the area
     * @param replacement the change to the area
     */
    public static void withoutUndo(GenericStyledArea<?, ?, ?> area, Runnable replacement) {
        area.setUndoManager(UndoUtils.noOpUndoManager());
        var lightArea = area instanceof org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea plain ? plain : null;
        if (lightArea != null) {
            lightArea.setLengthsOnlyChanges(true); // caret and selection read lengths only
        }
        try {
            replacement.run();
        } finally {
            if (lightArea != null) {
                lightArea.setLengthsOnlyChanges(false);
            }
            area.setUndoManager(UndoUtils.defaultUndoManager(area));
        }
    }
}
