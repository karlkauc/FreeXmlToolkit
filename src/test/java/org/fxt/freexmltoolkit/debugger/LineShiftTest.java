package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Breakpoints follow edits: inserted lines push them down, deleted lines remove them. */
class LineShiftTest {

    @Test
    void insertingLines() {
        LineShift insertAfterLine3 = new LineShift(3, 12, 0, 2); // Enter twice at the end of line 3
        assertEquals(3, insertAfterLine3.apply(3), "the edited line keeps its breakpoint");
        assertEquals(7, insertAfterLine3.apply(5), "lines below move down");
        assertEquals(2, insertAfterLine3.apply(2));
        LineShift insertAtStartOfLine3 = new LineShift(3, 0, 0, 1);
        assertEquals(4, insertAtStartOfLine3.apply(3), "a line inserted above the text pushes it down");
    }

    @Test
    void deletingLines() {
        LineShift deleteLines4and5 = new LineShift(3, 20, 2, 0); // from the end of line 3 to the end of line 5
        assertEquals(3, deleteLines4and5.apply(3));
        assertEquals(-1, deleteLines4and5.apply(4), "a deleted line loses its breakpoint");
        assertEquals(-1, deleteLines4and5.apply(5));
        assertEquals(4, deleteLines4and5.apply(6), "lines below move up");
        assertTrue(new LineShift(3, 5, 0, 0).isNeutral(), "typing within a line changes nothing");
        assertEquals(2, LineShift.newlines("a\nb\n"));
    }

    @Test
    void targetsMoveAndRemoveBreakpoints() {
        List<Set<Breakpoint>> saved = new ArrayList<>();
        FileBreakpoints file = new FileBreakpoints("/tmp/s.xsl", List.of(
                new Breakpoint("/tmp/s.xsl", 2, true),
                new Breakpoint("/tmp/s.xsl", 5, true, "@n = 1"),
                new Breakpoint("/tmp/other.xsl", 5, true)), saved::add);
        assertTrue(file.shiftLines("/tmp/s.xsl", new LineShift(3, 0, 0, 2)));
        assertNotNull(file.breakpointAt("/tmp/s.xsl", 2));
        assertEquals("@n = 1", file.breakpointAt("/tmp/s.xsl", 7).condition(), "moved with its settings");
        assertFalse(saved.isEmpty(), "persisted");
        assertTrue(saved.get(saved.size() - 1).contains(new Breakpoint("/tmp/other.xsl", 5, true)), "others untouched");

        DebugSession session = new DebugSession();
        session.toggleBreakpoint("/tmp/s.xsl", 4);
        session.toggleBreakpoint("/tmp/s.xsl", 6);
        assertTrue(session.shiftLines("/tmp/s.xsl", new LineShift(3, 10, 1, 0)));
        assertNull(session.breakpointAt("/tmp/s.xsl", 4), "line 4 was joined into line 3");
        assertNotNull(session.breakpointAt("/tmp/s.xsl", 5), "line 6 moved up");
        assertFalse(session.shiftLines("/tmp/s.xsl", new LineShift(1, 3, 0, 0)), "neutral");
    }
}
