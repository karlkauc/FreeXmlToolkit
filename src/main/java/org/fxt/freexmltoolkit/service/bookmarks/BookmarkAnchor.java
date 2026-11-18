package org.fxt.freexmltoolkit.service.bookmarks;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a bookmark's line again after its file changed outside the editor: the bookmark keeps
 * the trimmed text of its line, so a line that moved is found by that text near the old place.
 */
public final class BookmarkAnchor {

    /** How far (in lines, both directions) a moved line is searched for. */
    public static final int SEARCH_RADIUS = 50;

    private BookmarkAnchor() {
    }

    /** What happened to one bookmark. */
    public enum Outcome { UNCHANGED, MOVED, ORPHANED }

    /** The bookmark after re-anchoring and what happened to it. */
    public record Anchored(Bookmark bookmark, Outcome outcome) {
    }

    /** Splits a text into its lines (a trailing line break gives no extra line). */
    public static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(stripCr(text.substring(start, i)));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(stripCr(text.substring(start)));
        }
        return lines;
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /**
     * Re-anchors one bookmark against the file's current lines.
     * <ul>
     *   <li>No remembered text, or the text is still on its line: unchanged (an orphan flag is cleared).</li>
     *   <li>The text is on a line within {@link #SEARCH_RADIUS}: moved there (the nearest wins, the
     *       line above on a tie).</li>
     *   <li>Otherwise it stays on its line (clamped to the file) and is flagged orphaned.</li>
     * </ul>
     */
    public static Anchored anchor(Bookmark bookmark, List<String> lines) {
        int count = Math.max(1, lines.size());
        int line = Math.max(1, Math.min(bookmark.line(), count));
        String wanted = bookmark.lineText();
        if (wanted == null || wanted.isEmpty()) {
            Bookmark b = bookmark.withLine(line).withOrphaned(false);
            return new Anchored(b, line == bookmark.line() ? Outcome.UNCHANGED : Outcome.MOVED);
        }
        if (matches(lines, bookmark.line(), wanted)) {
            return new Anchored(bookmark.withOrphaned(false), Outcome.UNCHANGED);
        }
        for (int d = 1; d <= SEARCH_RADIUS; d++) {
            if (matches(lines, bookmark.line() - d, wanted)) {
                return new Anchored(bookmark.withLine(bookmark.line() - d).withOrphaned(false), Outcome.MOVED);
            }
            if (matches(lines, bookmark.line() + d, wanted)) {
                return new Anchored(bookmark.withLine(bookmark.line() + d).withOrphaned(false), Outcome.MOVED);
            }
        }
        return new Anchored(bookmark.withLine(line).withOrphaned(true), Outcome.ORPHANED);
    }

    private static boolean matches(List<String> lines, int line1, String wanted) {
        return line1 >= 1 && line1 <= lines.size() && Bookmark.clip(lines.get(line1 - 1)).equals(wanted);
    }
}
