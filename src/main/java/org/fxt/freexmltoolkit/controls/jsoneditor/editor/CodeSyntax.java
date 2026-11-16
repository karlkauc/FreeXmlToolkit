package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controls.shared.JsonSyntaxHighlighter;
import org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingRegion;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCaretContext;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver;

/**
 * The language-specific parts of {@link JsonCodeEditor}, so the same editor (folding, problem
 * markers, schema hover and completion, search, gutter) serves JSON and data languages read into
 * the same data model, such as YAML. All functions run off the FX thread or are cheap.
 *
 * @param formatLabel    fallback name of the format in the editor's status bar
 * @param highlighter    text → style spans
 * @param formatDetector text → the format variant shown in the status bar ({@code json}, {@code jsonc}, {@code yaml}, …)
 * @param foldScanner    text + minimum lines → foldable regions by 0-based start line
 * @param completer      text, caret offset and bound schema → completion proposals
 * @param completionTriggers characters that open the completion list after they are typed
 * @param pathAt         text + offset → the data path (String keys, Integer indexes) of the key or value there, or null
 * @param hoverInfo      text + offset → the path / type / value hover, or null
 */
public record CodeSyntax(String formatLabel,
                         Function<String, StyleSpans<Collection<String>>> highlighter,
                         Function<String, String> formatDetector,
                         BiFunction<String, Integer, Map<Integer, FoldingRegion>> foldScanner,
                         Completer completer,
                         String completionTriggers,
                         BiFunction<String, Integer, List<Object>> pathAt,
                         BiFunction<String, Integer, JsonPathCalculator.JsonHoverInfo> hoverInfo) {

    /** Schema-driven completion of one language. */
    @FunctionalInterface
    public interface Completer {
        /**
         * @param text     the document text
         * @param offset   the caret offset
         * @param resolver the bound schema (never null)
         * @return the proposals, in display order
         */
        List<JsonCompletionProvider.Proposal> complete(String text, int offset, JsonSchemaResolver resolver);
    }

    /** JSON, JSON with comments and JSON5. */
    public static final CodeSyntax JSON = new CodeSyntax("json",
            JsonSyntaxHighlighter::computeHighlighting,
            JsonSyntaxHighlighter::detectFormat,
            JsonFoldScanner::scan,
            JsonCompletionProvider::complete,
            "\":",
            JsonCaretContext::pathAt,
            JsonPathCalculator::calculatePath);
}
