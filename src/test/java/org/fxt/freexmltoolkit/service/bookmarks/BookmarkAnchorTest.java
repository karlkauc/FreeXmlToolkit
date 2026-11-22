package org.fxt.freexmltoolkit.service.bookmarks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BookmarkAnchorTest {

    private static Bookmark at(int line, String text) {
        return Bookmark.at(line, text);
    }

    @Test
    void unchangedLineStays() {
        var a = BookmarkAnchor.anchor(at(2, "<b/>"), List.of("<a/>", "  <b/>", "<c/>"));
        assertEquals(BookmarkAnchor.Outcome.UNCHANGED, a.outcome());
        assertEquals(2, a.bookmark().line());
    }

    @Test
    void movedLineIsFoundAndTheNearestWins() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lines.add("line " + i);
        }
        lines.set(4, "<target/>");   // line 5: 5 away from 10
        lines.set(12, "<target/>");  // line 13: 3 away from 10
        var a = BookmarkAnchor.anchor(at(10, "<target/>"), lines);
        assertEquals(BookmarkAnchor.Outcome.MOVED, a.outcome());
        assertEquals(13, a.bookmark().line());
        assertFalse(a.bookmark().orphaned());
    }

    @Test
    void lineGoneMakesAnOrphanThatStaysInTheFile() {
        var a = BookmarkAnchor.anchor(at(9, "<gone/>"), List.of("a", "b", "c"));
        assertEquals(BookmarkAnchor.Outcome.ORPHANED, a.outcome());
        assertEquals(3, a.bookmark().line());
        assertTrue(a.bookmark().orphaned());
    }

    @Test
    void textBeyondTheSearchRadiusIsNotUsed() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lines.add("l" + i);
        }
        lines.set(150, "<far/>");
        var a = BookmarkAnchor.anchor(at(10, "<far/>"), lines);
        assertEquals(BookmarkAnchor.Outcome.ORPHANED, a.outcome());
    }

    @Test
    void blankLineTextOnlyClamps() {
        var a = BookmarkAnchor.anchor(at(9, ""), List.of("a", "b"));
        assertEquals(BookmarkAnchor.Outcome.MOVED, a.outcome());
        assertEquals(2, a.bookmark().line());
        assertFalse(a.bookmark().orphaned());
    }

    @Test
    void linesHandleCrLfAndTrailingBreak() {
        assertEquals(List.of("a", "b"), BookmarkAnchor.lines("a\r\nb\r\n"));
        assertEquals(List.of(), BookmarkAnchor.lines(""));
    }
}
