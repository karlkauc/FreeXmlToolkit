package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

/**
 * Finds the indentation step a pretty-printed XML text uses, so that writing an edited model back
 * keeps the document's own layout instead of re-indenting every line (a grid edit on a document
 * indented with two spaces used to turn the whole file into four-space indentation).
 */
public final class XmlIndentDetector {

    /** Only the start of the document is looked at: the step shows within the first lines. */
    static final int SCAN_CHARS = 64 * 1024;

    private XmlIndentDetector() {
    }

    /**
     * @return the smallest positive number of leading spaces of a markup line, or {@code -1} when the
     *         text is not indented with spaces (single line, tabs, no indented markup)
     */
    public static int detect(String text) {
        if (text == null) {
            return -1;
        }
        int end = Math.min(text.length(), SCAN_CHARS);
        int best = Integer.MAX_VALUE;
        int lineStart = 0;
        while (lineStart < end) {
            int i = lineStart;
            while (i < end && text.charAt(i) == ' ') {
                i++;
            }
            if (i < end && text.charAt(i) == '\t') {
                return -1; // tab-indented: the serializer writes spaces only, keep the configured step
            }
            int spaces = i - lineStart;
            if (spaces > 0 && i < end && text.charAt(i) == '<') {
                best = Math.min(best, spaces);
            }
            int newline = text.indexOf('\n', i);
            if (newline < 0 || newline >= end) {
                break;
            }
            lineStart = newline + 1;
        }
        return best == Integer.MAX_VALUE || best > 8 ? -1 : best;
    }
}
