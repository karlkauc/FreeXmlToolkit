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

    /**
     * Replaces only the {@code [start, oldEnd)} character region with
     * {@code replacement}, preserving the caret/scroll in the untouched parts of the
     * document (the thin diff layer, P6). The default replaces the region on the code
     * area directly; editors with extra refresh work (e.g. syntax highlighting) override.
     *
     * @param start       region start offset
     * @param oldEnd      region end offset (exclusive) in the current text
     * @param replacement the text to put in place of the region
     */
    default void replaceTextRegion(int start, int oldEnd, String replacement) {
        getCodeArea().replaceText(start, oldEnd, replacement);
    }

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

    /**
     * Installs (or clears, when {@code factory} is null) an extra gutter component
     * (breakpoint dots / execution arrow) on the editor's line strip.
     *
     * @return {@code true} if this editor supports a gutter (XML family); {@code false} otherwise.
     */
    default boolean setExtraGutterFactory(java.util.function.IntFunction<javafx.scene.Node> factory) {
        return false;
    }

    /** Re-renders the gutter (e.g. after breakpoint/execution-line changes). No-op if unsupported. */
    default void refreshGutter() {
    }

    /**
     * Installs a handler invoked when the user triggers "Go to Definition" on an XML element
     * that has a bound XSD. No-op for editors without an XSD-navigation concept (e.g. JSON).
     */
    default void setGoToDefinitionHandler(
            java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.editor.core.NavigationRequest> handler) {
    }
}
