package org.fxt.freexmltoolkit.service.xmledit;

/**
 * One text-range edit: replace the chars {@code [start, end)} with
 * {@code replacement}. The generic building block for formatting-preserving
 * document edits (Find-in-Files replace, XPath replace).
 */
public record TextEdit(int start, int end, String replacement) {

    public TextEdit {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid edit range [" + start + ", " + end + ")");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("replacement must not be null");
        }
    }
}
