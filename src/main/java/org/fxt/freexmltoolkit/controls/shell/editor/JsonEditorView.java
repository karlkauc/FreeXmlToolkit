package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.layout.Region;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonCodeEditor;

/**
 * {@link EditorView} adapter for the JSON text editor ({@code JsonCodeEditor}),
 * with JSON syntax highlighting. Schema support is not applicable (JSON Schema
 * validation is handled separately).
 */
final class JsonEditorView implements EditorView {

    private final JsonCodeEditor editor = new JsonCodeEditor();

    @Override
    public Region getNode() {
        return editor;
    }

    @Override
    public void setText(String text) {
        editor.setText(text);
    }

    @Override
    public String getText() {
        return editor.getText();
    }

    @Override
    public CodeArea getCodeArea() {
        return editor.getCodeArea();
    }
}
