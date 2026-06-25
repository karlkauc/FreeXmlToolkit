package org.fxt.freexmltoolkit.controls.shared;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Shared utility for lightweight XPath/XQuery syntax highlighting.
 * <p>
 * Mirrors {@link XmlSyntaxHighlighter}: a single static
 * {@link #computeHighlighting(String)} method tokenises the expression with one
 * combined regular expression and returns {@link StyleSpans} carrying CSS class
 * names. It is intentionally simple (regex-based, no full parser) because the
 * Query Console only ever holds short expressions, not whole documents.
 * <p>
 * Emitted CSS classes (styled in {@code css/unified-shell.css}):
 * <ul>
 *   <li>{@code xq-comment} - XQuery comments {@code (: ... :)}</li>
 *   <li>{@code xq-string} - string literals ({@code "..."} or {@code '...'})</li>
 *   <li>{@code xq-var} - variables ({@code $name})</li>
 *   <li>{@code xq-axis} - XPath axes ({@code child::}, {@code descendant::}, ...)</li>
 *   <li>{@code xq-keyword} - XQuery/FLWOR keywords and word operators</li>
 *   <li>{@code xq-function} - function names directly followed by '('</li>
 *   <li>{@code xq-number} - numeric literals</li>
 * </ul>
 */
public final class XPathSyntaxHighlighter {

    // Word-boundary keyword list (FLWOR clauses, conditionals, quantifiers,
    // declarations and the word operators). Order does not matter; '\b' anchors
    // them so they never match inside longer names.
    private static final String KEYWORDS = "(?<KEYWORD>\\b(?:for|let|where|order\\h+by|return|"
            + "if|then|else|some|every|satisfies|declare|function|namespace|import|module|"
            + "at|in|to|as|and|or|div|idiv|mod|union|intersect|except|instance|of|castable|"
            + "cast|treat|eq|ne|lt|le|gt|ge|is|stable|ascending|descending|empty|greatest|least)\\b)";

    // Axes are always followed by '::'. The list covers all 13 XPath forward/reverse axes.
    private static final String AXIS = "(?<AXIS>\\b(?:child|descendant-or-self|descendant|"
            + "attribute|self|following-sibling|following|namespace|parent|"
            + "ancestor-or-self|ancestor|preceding-sibling|preceding)::)";

    // Function call: an (optionally prefixed) NCName immediately before '('.
    private static final String FUNCTION = "(?<FUNCTION>[A-Za-z][\\w-]*(?::[A-Za-z][\\w-]*)?(?=\\h*\\())";

    private static final String VARIABLE = "(?<VAR>\\$[\\w][\\w.-]*(?::[\\w][\\w.-]*)?)";
    private static final String STRING = "(?<STRING>\"[^\"]*\"|'[^']*')";
    // XQuery comments are (: ... :) and may span lines.
    private static final String COMMENT = "(?<COMMENT>\\(:[\\s\\S]*?:\\))";
    private static final String NUMBER = "(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)";

    // Combined pattern. COMMENT and STRING come first so their contents are not
    // re-tokenised; AXIS before KEYWORD so e.g. "namespace::" is an axis, not a
    // keyword; FUNCTION before KEYWORD so a function named like a keyword still
    // colours as a call when followed by '('.
    private static final Pattern PATTERN = Pattern.compile(
            COMMENT + "|" + STRING + "|" + VARIABLE + "|" + AXIS + "|"
                    + FUNCTION + "|" + KEYWORDS + "|" + NUMBER);

    private XPathSyntaxHighlighter() {
        // Utility class - no instantiation
    }

    /**
     * Computes syntax-highlighting spans for an XPath or XQuery expression.
     *
     * @param text the expression text (may be {@code null})
     * @return style spans whose total length equals the (newline-normalised) text length
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null) {
            text = "";
        }
        // Normalise line endings to match RichTextFX, which collapses \r\n and
        // lone \r to \n internally; otherwise spans drift by one char per CRLF.
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        Matcher matcher = PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = styleClassFor(matcher);
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    /** Maps the matched named group to its CSS class. */
    private static String styleClassFor(Matcher matcher) {
        if (matcher.group("COMMENT") != null) {
            return "xq-comment";
        }
        if (matcher.group("STRING") != null) {
            return "xq-string";
        }
        if (matcher.group("VAR") != null) {
            return "xq-var";
        }
        if (matcher.group("AXIS") != null) {
            return "xq-axis";
        }
        if (matcher.group("FUNCTION") != null) {
            return "xq-function";
        }
        if (matcher.group("KEYWORD") != null) {
            return "xq-keyword";
        }
        return "xq-number";
    }
}
