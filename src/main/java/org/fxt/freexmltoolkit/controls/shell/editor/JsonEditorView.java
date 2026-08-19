package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javafx.scene.layout.Region;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonCodeEditor;
import org.fxt.freexmltoolkit.service.JsonService;

/**
 * {@link EditorView} adapter for the JSON text editor ({@code JsonCodeEditor}),
 * with JSON syntax highlighting. Schema support binds a JSON Schema for
 * validation: {@link EditorHost} keeps the binding per tab, so {@link #loadSchema}
 * only sanity-checks the file (there is no JSON IntelliSense yet that would need
 * the schema inside the editor).
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

    @Override
    public boolean supportsSchema() {
        return true;
    }

    /**
     * Accepts the JSON Schema file when it is readable and parses as JSON.
     * Cheap and thread-agnostic: called from worker threads (auto-detection)
     * and the FX thread (manual binding) alike.
     */
    @Override
    public boolean loadSchema(File schema) {
        if (schema == null) {
            return true; // clearing a binding always succeeds
        }
        try {
            String text = Files.readString(schema.toPath(), StandardCharsets.UTF_8);
            return new JsonService().validateJson(text) == null;
        } catch (Exception e) {
            return false;
        }
    }
}
