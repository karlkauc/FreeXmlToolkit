package org.fxt.freexmltoolkit.controls.shared;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Shared utility class for XML syntax highlighting.
 * Provides static methods for computing syntax highlighting spans.
 */
public final class XmlSyntaxHighlighter {

    // Syntax Highlighting Patterns.
    // The element-name group includes the namespace separator ':' (and the other XML name characters
    // '.' and '-'), so namespace-prefixed tags such as <xs:element> are fully highlighted — not just
    // the "xs" prefix, which previously left XSD documents looking unhighlighted.
    // COMMENT uses a reluctant [\s\S]*? body: comments may span lines and may contain
    // '<' and '>' in prose (e.g. "<Version>" or "4.0.0 -> 4.1.0"); a negated class like
    // [^<>] would reject those and leave the whole comment unstyled. CDATA bodies are
    // matched (and left unstyled) so literal markup inside them is never colored as tags.
    // ELEMENT can never match at "<!" or "<?" ('!'/'?' are not name characters), so the
    // alternative order is not load-bearing.
    private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)([\\w:.-]+)([^<>]*?)(\\h*/?>))"
            + "|(?<COMMENT><!--[\\s\\S]*?-->)"
            + "|(?<CDATA>(?<CDOPEN><!\\[CDATA\\[)(?<CDBODY>[\\s\\S]*?)(?<CDCLOSE>]]>))"
            + "|(?<PI>(?<PIOPEN><\\?)(?<PITARGET>[\\w:.-]+)(?<PIBODY>[\\s\\S]*?)(?<PICLOSE>\\?>))"
            // The DOCTYPE body may carry an internal DTD subset [...] whose first ']' closes it;
            // ']' cannot appear earlier because the part before '[' excludes '[', '<' and '>'.
            + "|(?<DOCTYPE>(?<DTOPEN><!)(?<DTKEY>(?i:DOCTYPE))(?<DTBODY>[^\\[<>]*(?:\\[[\\s\\S]*?])?\\s*)(?<DTCLOSE>>))");

    // Tokens styled inside the DOCTYPE body before the internal subset: quoted system/public
    // ids ("..." or '...') and bare names (root element, SYSTEM, PUBLIC).
    private static final Pattern DOCTYPE_TOKEN = Pattern.compile("(?<QUOTED>\"[^\"]*\"|'[^']*')|(?<NAME>[\\w:.-]+)");
    private static final Pattern ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");

    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;
    private static final int GROUP_ATTRIBUTE_NAME = 1;
    private static final int GROUP_EQUAL_SYMBOL = 2;
    private static final int GROUP_ATTRIBUTE_VALUE = 3;

    private XmlSyntaxHighlighter() {
        // Utility class - no instantiation
    }

    /**
     * Computes syntax highlighting for XML text.
     * Returns StyleSpans with CSS class names for different XML elements:
     * <ul>
     *   <li>tagmark - XML tag brackets (&lt;, &gt;, /&gt;, &lt;/)</li>
     *   <li>anytag - Element names</li>
     *   <li>attribute - Attribute names</li>
     *   <li>avalue - Attribute values</li>
     *   <li>comment - XML comments</li>
     * </ul>
     *
     * @param text The XML text to highlight
     * @return StyleSpans with highlighting information
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null) {
            text = "";
        }

        // Normalize line endings to match RichTextFX CodeArea, which collapses
        // \r\n and lone \r to \n internally. Without this, every \r\n in the
        // input shortens the CodeArea by one character per line, causing a
        // cumulative one-char drift of tag/element coloring downstream.
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else if (matcher.group("CDATA") != null) {
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end("CDOPEN") - matcher.start("CDOPEN"));
                spansBuilder.add(Collections.emptyList(), matcher.end("CDBODY") - matcher.start("CDBODY"));
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end("CDCLOSE") - matcher.start("CDCLOSE"));
            } else if (matcher.group("PI") != null) {
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end("PIOPEN") - matcher.start("PIOPEN"));
                spansBuilder.add(Collections.singleton("anytag"), matcher.end("PITARGET") - matcher.start("PITARGET"));
                addAttributeSpans(matcher.group("PIBODY"), spansBuilder);
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end("PICLOSE") - matcher.start("PICLOSE"));
            } else if (matcher.group("DOCTYPE") != null) {
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end("DTOPEN") - matcher.start("DTOPEN"));
                spansBuilder.add(Collections.singleton("anytag"), matcher.end("DTKEY") - matcher.start("DTKEY"));
                addDoctypeBodySpans(matcher.group("DTBODY"), spansBuilder);
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end("DTCLOSE") - matcher.start("DTCLOSE"));
            } else if (matcher.group("ELEMENT") != null) {
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                spansBuilder.add(Collections.singleton("anytag"), matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));
                addAttributeSpans(matcher.group(GROUP_ATTRIBUTES_SECTION), spansBuilder);
                spansBuilder.add(Collections.singleton("tagmark"), matcher.end(GROUP_CLOSE_BRACKET) - matcher.end(GROUP_ATTRIBUTES_SECTION));
            }
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Emits spans covering exactly {@code attributesText}: attribute names, '=' and quoted
     * values styled, everything else unstyled. Shared by the ELEMENT branch and the
     * processing-instruction branch (whose pseudo-attributes use the same syntax).
     */
    private static void addAttributeSpans(String attributesText, StyleSpansBuilder<Collection<String>> spansBuilder) {
        if (attributesText == null || attributesText.isEmpty()) {
            return;
        }
        int attrLastEnd = 0;
        Matcher amatcher = ATTRIBUTES.matcher(attributesText);
        while (amatcher.find()) {
            spansBuilder.add(Collections.emptyList(), amatcher.start() - attrLastEnd);
            spansBuilder.add(Collections.singleton("attribute"), amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
            spansBuilder.add(Collections.singleton("tagmark"), amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
            spansBuilder.add(Collections.singleton("avalue"), amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
            attrLastEnd = amatcher.end();
        }
        if (attributesText.length() > attrLastEnd) {
            spansBuilder.add(Collections.emptyList(), attributesText.length() - attrLastEnd);
        }
    }

    /**
     * Emits spans covering exactly {@code body} (the DOCTYPE content between the keyword and
     * the closing '&gt;'): bare names (root element, SYSTEM, PUBLIC) as attribute, quoted
     * system/public ids as avalue. An internal DTD subset [...] is left entirely unstyled so
     * the declarations inside are never mistaken for markup.
     */
    private static void addDoctypeBodySpans(String body, StyleSpansBuilder<Collection<String>> spansBuilder) {
        if (body == null || body.isEmpty()) {
            return;
        }
        int subsetStart = body.indexOf('[');
        String prefix = subsetStart >= 0 ? body.substring(0, subsetStart) : body;

        int lastEnd = 0;
        Matcher tmatcher = DOCTYPE_TOKEN.matcher(prefix);
        while (tmatcher.find()) {
            spansBuilder.add(Collections.emptyList(), tmatcher.start() - lastEnd);
            String style = tmatcher.group("QUOTED") != null ? "avalue" : "attribute";
            spansBuilder.add(Collections.singleton(style), tmatcher.end() - tmatcher.start());
            lastEnd = tmatcher.end();
        }
        spansBuilder.add(Collections.emptyList(), body.length() - lastEnd);
    }
}
