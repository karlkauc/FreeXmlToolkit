package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.util.Arrays;

/**
 * Records, while a document is serialized, the line on which each element's start tag ends — the
 * line {@link XmlElement#getSourceLineNumber()} holds after parsing that text. Applied to the model
 * once the text is in the editor, the model maps the caret to its elements again without parsing
 * the text a second time (a 150-MB document after grid edits).
 */
public final class LineRecorder {

    private int scanned;
    private int line = 1;
    private XmlElement[] elements = new XmlElement[1024];
    private int[] lines = new int[1024];
    private int size;

    /** Called by the serializer right after it wrote the start tag of {@code element} into {@code out}. */
    void record(XmlElement element, CharSequence out) {
        for (int i = scanned, n = out.length(); i < n; i++) {
            if (out.charAt(i) == '\n') {
                line++;
            }
        }
        scanned = out.length();
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, size * 2);
            lines = Arrays.copyOf(lines, size * 2);
        }
        elements[size] = element;
        lines[size] = line;
        size++;
    }

    /** @return the number of elements recorded */
    public int size() {
        return size;
    }

    /** Sets the recorded line numbers on the elements (no change events: the lines are no content). */
    public void apply() {
        for (int i = 0; i < size; i++) {
            elements[i].setSourceLineNumber(lines[i]);
        }
    }
}
