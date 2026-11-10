package org.fxt.freexmltoolkit.service.ai.context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes document values from text that leaves the machine at data levels {@code SCHEMA} and {@code STRUCTURE}:
 * quoted values in validator messages (Xerces quotes the offending value), and text/attribute values of XML
 * fragments (replaced by type placeholders).
 */
public final class Redactor {

    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"|«[^»]*»|“[^”]*”");
    private static final Pattern ATTRIBUTE_VALUE = Pattern.compile("(\\s[\\w:.-]+\\s*=\\s*)(\"[^\"]*\"|'[^']*')");
    private static final Pattern TEXT = Pattern.compile(">([^<]+)<");

    private Redactor() {
    }

    /**
     * A validator message without document values: every quoted token becomes {@code «value»} unless the words
     * right before it say that it names a schema component — {@code element 'n'}, {@code type 'IdType'},
     * {@code attribute 'id'}, {@code for 'integer'} — and it looks like a name. So
     * {@code cvc-datatype-valid.1.2.1: 'abc' is not a valid value for 'integer'} becomes
     * {@code «value» is not a valid value for 'integer'}.
     */
    public static String message(String message) {
        if (message == null) {
            return "";
        }
        Matcher m = QUOTED.matcher(message);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group();
            String inner = token.substring(1, token.length() - 1);
            boolean names = namesComponent(message.substring(Math.max(0, m.start() - 24), m.start()));
            m.appendReplacement(out, Matcher.quoteReplacement(names && looksLikeName(inner) ? token : "«value»"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern COMPONENT_WORD = Pattern.compile(
            "(?i).*\\b(element|elements|type|attribute|attributes|for|declaration|namespace|prefix|group|of)\\s*$");

    private static boolean namesComponent(String before) {
        return COMPONENT_WORD.matcher(before).matches();
    }

    private static boolean looksLikeName(String token) {
        return !token.isEmpty() && token.length() <= 80 && token.matches("[\\p{L}_{][\\p{L}\\p{N}_.:{}\\-#/]*");
    }

    /** An XML fragment with every text and attribute value replaced by {@code {value}}; names and structure stay. */
    public static String fragment(String xml) {
        if (xml == null) {
            return "";
        }
        String withoutAttributes = ATTRIBUTE_VALUE.matcher(xml).replaceAll("$1\"{value}\"");
        Matcher m = TEXT.matcher(withoutAttributes);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String text = m.group(1);
            m.appendReplacement(out, Matcher.quoteReplacement(text.isBlank() ? m.group() : ">{value}<"));
        }
        m.appendTail(out);
        return out.toString().replaceAll("<!--.*?-->", "<!--{comment}-->");
    }
}
