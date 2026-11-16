package org.fxt.freexmltoolkit.controls.shared;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.service.yaml.YamlTokenizer;
import org.fxt.freexmltoolkit.service.yaml.YamlTokenizer.Token;

/**
 * Syntax highlighting for YAML from {@link YamlTokenizer}. Uses the JSON editor's style classes
 * for what both languages have (keys, strings, numbers, booleans, null, comments, punctuation,
 * brackets) and {@code yaml-anchor}, {@code yaml-tag}, {@code yaml-directive} for the rest; all
 * of them fill with {@code -fxt-code-*} tokens.
 */
public final class YamlSyntaxHighlighter {

    private YamlSyntaxHighlighter() {
    }

    /**
     * @param text the YAML text
     * @return the style spans covering the whole text
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        if (text == null || text.isEmpty()) {
            builder.add(Collections.emptyList(), 0);
            return builder.create();
        }
        int last = 0;
        for (Token token : YamlTokenizer.tokenize(text)) {
            if (token.start() > last) {
                builder.add(Collections.emptyList(), token.start() - last);
            }
            String style = styleClass(token.kind());
            builder.add(style == null ? Collections.emptyList() : List.of(style), token.end() - token.start());
            last = token.end();
        }
        if (last < text.length()) {
            builder.add(Collections.emptyList(), text.length() - last);
        }
        return builder.create();
    }

    /**
     * @param kind a token kind
     * @return its style class, or {@code null} for none
     */
    public static String styleClass(YamlTokenizer.Kind kind) {
        return switch (kind) {
            case KEY -> "json-key";
            case STRING -> "json-string";
            case NUMBER -> "json-number";
            case BOOLEAN -> "json-boolean";
            case NULL -> "json-null";
            case COMMENT -> "json-comment";
            case PUNCTUATION -> "json-punctuation";
            case BRACKET -> "json-bracket";
            case ANCHOR, ALIAS -> "yaml-anchor";
            case TAG -> "yaml-tag";
            case DIRECTIVE -> "yaml-directive";
            case TEXT -> null;
        };
    }
}
