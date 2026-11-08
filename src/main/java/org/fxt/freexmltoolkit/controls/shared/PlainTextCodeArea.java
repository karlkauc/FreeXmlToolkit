package org.fxt.freexmltoolkit.controls.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.scene.input.Clipboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.RichTextChange;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxt.freexmltoolkit.controls.v2.editor.TextEdits;
import org.reactfx.EventStream;

/**
 * A {@link CodeArea} that pastes plain text. The editors compute their styling themselves, so
 * RichTextFX's styled clipboard format brings nothing - and on macOS the clipboard can report that
 * format with content that is not the {@code byte[]} RichTextFX casts it to, which made every
 * Ctrl+V fail with a {@code ClassCastException} (telemetry {@code ab6bddda903ddbdb}).
 */
public class PlainTextCodeArea extends CodeArea {

    private static final Logger logger = LogManager.getLogger(PlainTextCodeArea.class);

    public PlainTextCodeArea() {
        super();
    }

    // No initializers: the caret and the selection ask for multiPlainChanges() from the super
    // constructor, before field initializers would run (and reset what they created).
    private EventStream<List<PlainTextChange>> plainChanges;
    private boolean lengthsOnly;

    /**
     * One shared stream for all listeners (RichTextFX builds a new one per call, and each turned
     * every change into strings of the removed and inserted text: the caret and the selection
     * did so for a 150-MB replacement, four times each). Changes of styles only are left out
     * line by line instead of comparing two whole texts.
     */
    @Override
    public EventStream<List<PlainTextChange>> multiPlainChanges() {
        if (plainChanges == null) {
            plainChanges = multiRichChanges().map(this::toPlainChanges).filter(list -> !list.isEmpty());
        }
        return plainChanges;
    }

    private List<PlainTextChange> toPlainChanges(
            List<RichTextChange<Collection<String>, String, Collection<String>>> changes) {
        List<PlainTextChange> plain = new ArrayList<>(changes.size());
        for (var change : changes) {
            if (TextEdits.sameText(change.getRemoved(), change.getInserted())) {
                continue;
            }
            plain.add(lengthsOnly ? new LengthsOnlyChange(change) : change.toPlainTextChange());
        }
        return plain;
    }

    /**
     * While set, a change carries its lengths and builds its texts only when asked: for a
     * replacement no undo manager records (see {@link TextEdits#withoutUndo}), whose listeners
     * — caret and selection — read the position and the net length only.
     */
    public void setLengthsOnlyChanges(boolean lengthsOnly) {
        this.lengthsOnly = lengthsOnly;
    }

    /** A plain change that knows its lengths and builds its texts on request. */
    private static final class LengthsOnlyChange extends PlainTextChange {
        private final StyledDocument<?, ?, ?> removedDocument;
        private final StyledDocument<?, ?, ?> insertedDocument;

        LengthsOnlyChange(RichTextChange<?, ?, ?> change) {
            super(change.getPosition(), "", "");
            this.removedDocument = change.getRemoved();
            this.insertedDocument = change.getInserted();
        }

        @Override
        protected int removedLength() {
            return removedDocument.length();
        }

        @Override
        protected int insertedLength() {
            return insertedDocument.length();
        }

        @Override
        public String getRemoved() {
            return removedDocument.getText();
        }

        @Override
        public String getInserted() {
            return insertedDocument.getText();
        }
    }

    /** Pastes the clipboard's text, replacing the selection; the styled format is only a fallback. */
    @Override
    public void paste() {
        paste(Clipboard.getSystemClipboard());
    }

    void paste(Clipboard clipboard) {
        if (!isEditable()) {
            return;
        }
        if (clipboard.hasString()) {
            String text = clipboard.getString();
            if (text != null) {
                replaceSelection(text);
            }
            return;
        }
        try {
            super.paste();
        } catch (ClassCastException e) {
            logger.debug("Clipboard content in an unexpected form, nothing pasted: {}", e.getMessage());
        }
    }
}
