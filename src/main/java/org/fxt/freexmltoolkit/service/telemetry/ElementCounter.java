package org.fxt.freexmltoolkit.service.telemetry;

/**
 * Cheap element count for {@code element_count}: one linear pass that counts start tags
 * ({@code <} followed by a name start character) and skips end tags, processing
 * instructions, comments, CDATA sections and other {@code <!} declarations.
 *
 * <p>No parsing and no allocation: it runs on the worker thread that already holds the
 * text, and malformed input simply yields an approximate number. Only the count leaves
 * the app, never any content.
 */
public final class ElementCounter {

    private ElementCounter() {
    }

    /**
     * Counts the start tags (including self-closing ones) of an XML text.
     *
     * @param xml the document text (nullable)
     * @return the number of elements, or {@code -1} for {@code null}
     */
    public static int countStartTags(CharSequence xml) {
        if (xml == null) {
            return -1;
        }
        int n = xml.length();
        int count = 0;
        int i = 0;
        while (i < n) {
            if (xml.charAt(i) != '<') {
                i++;
                continue;
            }
            if (i + 1 >= n) {
                break;
            }
            char next = xml.charAt(i + 1);
            if (next == '!') {
                if (startsWith(xml, i, "<!--")) {
                    i = skipPast(xml, i + 4, "-->");
                } else if (startsWith(xml, i, "<![CDATA[")) {
                    i = skipPast(xml, i + 9, "]]>");
                } else {
                    i += 2; // DOCTYPE and its markup declarations: no elements
                }
            } else if (next == '?') {
                i = skipPast(xml, i + 2, "?>");
            } else {
                if (isNameStart(next)) {
                    count++;
                }
                i += 2; // end tags ('</') and stray '<' are not counted
            }
        }
        return count;
    }

    /** XML NameStartChar, simplified: letters, '_', ':' and anything non-ASCII. */
    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ':' || c >= 0x80;
    }

    private static boolean startsWith(CharSequence s, int from, String prefix) {
        if (from + prefix.length() > s.length()) {
            return false;
        }
        for (int k = 0; k < prefix.length(); k++) {
            if (s.charAt(from + k) != prefix.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    /** @return the index after the next {@code terminator} at or after {@code from}, or the length */
    private static int skipPast(CharSequence s, int from, String terminator) {
        int n = s.length();
        int last = n - terminator.length();
        char first = terminator.charAt(0);
        for (int i = from; i <= last; i++) {
            if (s.charAt(i) == first && startsWith(s, i, terminator)) {
                return i + terminator.length();
            }
        }
        return n;
    }
}
