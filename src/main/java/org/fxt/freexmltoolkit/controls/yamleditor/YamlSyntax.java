package org.fxt.freexmltoolkit.controls.yamleditor;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.CodeSyntax;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonPathCalculator.JsonHoverInfo;
import org.fxt.freexmltoolkit.controls.shared.YamlSyntaxHighlighter;
import org.fxt.freexmltoolkit.service.yaml.YamlCaretContext;
import org.fxt.freexmltoolkit.service.yaml.YamlCompletionProvider;
import org.fxt.freexmltoolkit.service.yaml.YamlDocument;

import com.google.gson.JsonElement;

/**
 * The YAML parts of the shared data-language code editor: highlighting ({@link YamlSyntaxHighlighter}),
 * folding by indentation ({@link YamlFoldScanner}), schema completion ({@link YamlCompletionProvider},
 * opened by typing {@code :} or Ctrl+Space) and paths for the hover ({@link YamlDocument}).
 */
public final class YamlSyntax {

    /** Above this size the hover does not read the document. */
    private static final int HOVER_LIMIT = 1024 * 1024;

    /** The YAML syntax. */
    public static final CodeSyntax YAML = new CodeSyntax("yaml",
            YamlSyntaxHighlighter::computeHighlighting,
            text -> "yaml",
            YamlFoldScanner::scan,
            YamlCompletionProvider::complete,
            ":",
            YamlSyntax::pathAt,
            YamlSyntax::hoverInfo);

    private YamlSyntax() {
    }

    /** The path of the key or value at an offset: from the parsed text, else from the indentation. */
    static List<Object> pathAt(String text, int offset) {
        if (text == null || text.length() > HOVER_LIMIT) {
            return null;
        }
        YamlDocument document = YamlDocument.parse(text);
        if (document.isValid()) {
            return document.pathAt(offset);
        }
        YamlCaretContext.Result context = YamlCaretContext.analyze(text, offset);
        return switch (context.kind()) {
            case VALUE, ITEM -> context.path();
            default -> null;
        };
    }

    /** Path, type, key and value of what is under the mouse. */
    static JsonHoverInfo hoverInfo(String text, int offset) {
        if (text == null || text.length() > HOVER_LIMIT) {
            return null;
        }
        YamlDocument document = YamlDocument.parse(text);
        if (!document.isValid()) {
            return null;
        }
        List<Object> path = document.pathAt(offset);
        if (path == null) {
            return null;
        }
        JsonElement value = document.valueAt(path);
        String type;
        String shown = null;
        if (value == null || value.isJsonNull()) {
            type = "null";
        } else if (value.isJsonObject()) {
            type = "mapping";
        } else if (value.isJsonArray()) {
            type = "sequence";
        } else if (value.getAsJsonPrimitive().isBoolean()) {
            type = "boolean";
            shown = value.getAsString();
        } else if (value.getAsJsonPrimitive().isNumber()) {
            type = "number";
            shown = value.getAsString();
        } else {
            type = "string";
            shown = value.getAsString();
        }
        String key = !path.isEmpty() && path.get(path.size() - 1) instanceof String s ? s : null;
        return new JsonHoverInfo(YamlDocument.toJsonPath(path), type, shown, key, path.size());
    }
}
