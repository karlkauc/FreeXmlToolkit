package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.scene.layout.Region;

import org.fxmisc.richtext.CodeArea;

/**
 * Abstraction over the concrete text editors the Unified host can embed, so the
 * host stays file-type-agnostic. Adapters wrap the reused widgets:
 * {@code XmlCodeEditorV2} (XML family) and {@code JsonCodeEditor} (JSON).
 * <p>
 * Both widgets expose a RichTextFX {@link CodeArea}, so caret/undo concerns are
 * handled here via {@link #getCodeArea()} default methods.
 */
public interface EditorView {

    /** @return the embeddable UI node. */
    Region getNode();

    /** Sets the editor text (clears the dirty/undo baseline as the widget sees fit). */
    void setText(String text);

    /** @return the current editor text. */
    String getText();

    /** @return the underlying code area (caret, undo, text property). */
    CodeArea getCodeArea();

    default void undo() {
        getCodeArea().undo();
    }

    default void redo() {
        getCodeArea().redo();
    }

    /** @return {@code true} if this editor supports XSD-driven IntelliSense. */
    default boolean supportsSchema() {
        return false;
    }

    /** Binds an XSD for IntelliSense. @return {@code true} on success (false if unsupported). */
    default boolean loadSchema(File xsd) {
        return false;
    }

    /** Invalidates IntelliSense caches after a schema change (no-op if unsupported). */
    default void invalidateIntelliSenseCache() {
    }
}
