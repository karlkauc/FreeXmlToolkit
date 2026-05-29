package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * Factory selecting the {@link EditorView} implementation for a file type.
 */
final class EditorViews {

    private EditorViews() {
    }

    /** @return a JSON editor for {@link EditorFileType#JSON}, otherwise the XML-family editor. */
    static EditorView create(EditorFileType type) {
        return type == EditorFileType.JSON ? new JsonEditorView() : new XmlEditorView();
    }
}
