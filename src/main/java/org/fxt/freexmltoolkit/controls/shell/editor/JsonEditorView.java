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
    /** Lines (0-based) of the node a view switch handed over, or -1. */
    private int linkedFirst = -1;
    private int linkedLast = -1;

    JsonEditorView() {
        CodeArea area = editor.getCodeArea();
        // The tint goes on the next edit or when the caret leaves it (deferred: re-styling from
        // inside a change re-enters the viewport).
        org.fxt.freexmltoolkit.controls.v2.editor.TextEdits.of(area).subscribe(change -> {
            if (linkedFirst >= 0) {
                javafx.application.Platform.runLater(() -> setLinkedNodeLines(-1, -1));
            }
        });
        area.currentParagraphProperty().addListener((obs, oldP, newP) -> {
            if (linkedFirst >= 0 && (newP < linkedFirst || newP > linkedLast)) {
                javafx.application.Platform.runLater(() -> setLinkedNodeLines(-1, -1));
            }
        });
    }

    @Override
    public void setLinkedNodeLines(int firstLine1, int lastLine1) {
        CodeArea area = editor.getCodeArea();
        int paragraphs = area.getParagraphs().size();
        for (int i = Math.max(0, linkedFirst); linkedFirst >= 0 && i <= linkedLast && i < paragraphs; i++) {
            area.setParagraphStyle(i, java.util.List.of());
        }
        if (firstLine1 > 0 && lastLine1 > 0) {
            linkedFirst = firstLine1 - 1;
            linkedLast = Math.max(firstLine1, lastLine1) - 1;
            for (int i = linkedFirst; i <= linkedLast && i < paragraphs; i++) {
                area.setParagraphStyle(i, java.util.List.of("linked-node-line"));
            }
        } else {
            linkedFirst = -1;
            linkedLast = -1;
        }
    }

    @Override
    public int[] getLinkedNodeLines() {
        return linkedFirst < 0 ? null : new int[] {linkedFirst + 1, linkedLast + 1};
    }

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
