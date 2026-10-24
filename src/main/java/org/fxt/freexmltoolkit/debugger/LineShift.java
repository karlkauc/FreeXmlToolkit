package org.fxt.freexmltoolkit.debugger;

/**
 * Where a breakpoint line goes when the text of its file is edited, so breakpoints follow the
 * code instead of sticking to a line number:
 * <ul>
 *   <li>lines before the edit stay;</li>
 *   <li>lines whose text was deleted (inside a removed range of lines) lose their breakpoint;</li>
 *   <li>lines after the edit move by the change in line count;</li>
 *   <li>inserting whole lines at the very start of a breakpoint's line pushes it down with its text.</li>
 * </ul>
 *
 * @param startLine        1-based line where the edit starts
 * @param startColumn      0-based column where the edit starts
 * @param removedNewlines  line breaks in the removed text
 * @param insertedNewlines line breaks in the inserted text
 */
public record LineShift(int startLine, int startColumn, int removedNewlines, int insertedNewlines) {

    /** @return whether the edit changes no line numbers at all (typing within a line). */
    public boolean isNeutral() {
        return removedNewlines == 0 && insertedNewlines == 0;
    }

    /** @return the breakpoint's new line, or {@code -1} when its line was deleted. */
    public int apply(int line) {
        int endLine = startLine + removedNewlines; // the line where the removed text ended
        if (line < startLine) {
            return line;
        }
        if (line == startLine) {
            // Lines inserted at the very start of the line push its text — and the breakpoint — down.
            return startColumn == 0 && removedNewlines == 0 ? line + insertedNewlines : line;
        }
        if (line <= endLine) {
            return -1; // its line was removed (merged into the edit's line)
        }
        return line + insertedNewlines - removedNewlines;
    }

    /** Counts line breaks in {@code text}. */
    public static int newlines(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
