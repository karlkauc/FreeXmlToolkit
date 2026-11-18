package org.fxt.freexmltoolkit.service.bookmarks;

/**
 * A bookmark on one line of a text document.
 *
 * @param line     1-based line
 * @param name     what the user called it, or {@code null}
 * @param number   quick-access number 1–9, or {@code 0} for none (a number is unique app-wide)
 * @param lineText the line's trimmed text when it was last known to be right (re-anchoring, preview)
 * @param created  creation time, epoch milliseconds
 * @param orphaned the line's text could not be found again after the file changed outside the editor
 */
public record Bookmark(int line, String name, int number, String lineText, long created, boolean orphaned) {

    /** Longest line text kept (preview + re-anchoring). */
    public static final int MAX_LINE_TEXT = 200;

    public Bookmark {
        name = name == null || name.isBlank() ? null : name.strip();
        number = number >= 1 && number <= 9 ? number : 0;
        lineText = clip(lineText);
    }

    /** A new bookmark without name or number. */
    public static Bookmark at(int line, String lineText) {
        return new Bookmark(line, null, 0, lineText, System.currentTimeMillis(), false);
    }

    /** @return {@code text} trimmed and shortened to {@link #MAX_LINE_TEXT}; never {@code null}. */
    public static String clip(String text) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= MAX_LINE_TEXT ? t : t.substring(0, MAX_LINE_TEXT);
    }

    public Bookmark withLine(int newLine) {
        return new Bookmark(newLine, name, number, lineText, created, orphaned);
    }

    public Bookmark withName(String newName) {
        return new Bookmark(line, newName, number, lineText, created, orphaned);
    }

    public Bookmark withNumber(int newNumber) {
        return new Bookmark(line, name, newNumber, lineText, created, orphaned);
    }

    public Bookmark withLineText(String newText) {
        return new Bookmark(line, name, number, newText, created, false);
    }

    public Bookmark withOrphaned(boolean value) {
        return new Bookmark(line, name, number, lineText, created, value);
    }

    /** @return the name, or {@code "Line N"} when it has none. */
    public String label() {
        return name != null ? name : "Line " + line;
    }
}
