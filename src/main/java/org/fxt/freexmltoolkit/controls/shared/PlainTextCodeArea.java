package org.fxt.freexmltoolkit.controls.shared;

import javafx.scene.input.Clipboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;

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
