package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * Factory selecting the {@link EditorView} implementation for a file type.
 */
final class EditorViews {

    private EditorViews() {
    }

    /**
     * @return a JSON editor for {@link EditorFileType#JSON}, a query editor for
     *         {@link EditorFileType#XQUERY}/{@link EditorFileType#XPATH}, otherwise
     *         the XML-family editor.
     */
    static EditorView create(EditorFileType type) {
        return switch (type) {
            case JSON -> new JsonEditorView();
            case XQUERY -> new QueryEditorView(true);
            case XPATH -> new QueryEditorView(false);
            default -> new XmlEditorView();
        };
    }
}
