package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collapses inter-tag whitespace in serialized XSD output. Safe on
 * {@code XsdSerializer} output because its text content escapes both
 * {@code <} and {@code >}, so {@code >\s+<} can only occur between tags.
 * Comment interiors are left untouched.
 */
public final class XsdOutputMinifier {

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern INTER_TAG_WHITESPACE = Pattern.compile(">\\s+<");

    private XsdOutputMinifier() {
    }

    /** @return {@code xml} with whitespace between tags removed (comments preserved verbatim) */
    public static String minify(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        StringBuilder result = new StringBuilder(xml.length());
        Matcher comments = COMMENT.matcher(xml);
        int last = 0;
        while (comments.find()) {
            String segment = collapse(xml.substring(last, comments.start())).stripTrailing();
            result.append(last == 0 ? segment : segment.stripLeading());
            result.append(comments.group());
            last = comments.end();
        }
        String tail = collapse(xml.substring(last));
        result.append(last == 0 ? tail : tail.stripLeading());
        return result.toString().strip();
    }

    private static String collapse(String segment) {
        // Serializer output only ever has whitespace-only text between tags and
        // emits comments on their own lines, so stripping around them is safe.
        return INTER_TAG_WHITESPACE.matcher(segment).replaceAll("><");
    }
}
