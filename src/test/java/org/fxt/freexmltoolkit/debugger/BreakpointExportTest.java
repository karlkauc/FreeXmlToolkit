package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Export / import of all breakpoints of a session. */
class BreakpointExportTest {

    @Test
    void roundTripKeepsEveryKindAndSetting() {
        Set<Breakpoint> lines = Set.of(
                new Breakpoint("/tmp/a.xsl", 3, true),
                new Breakpoint("/tmp/lib/b.xsl", 9, false, "@n = 2",
                        new HitCondition(HitCondition.Mode.EQUAL, 3), "n={@n}", false));
        List<SymbolBreakpoint> symbols = List.of(new SymbolBreakpoint(SymbolBreakpoint.Kind.FUNCTION, "f:sum", true));
        BreakpointExport.Content content = BreakpointExport.read(BreakpointExport.write(lines, symbols));
        assertEquals(lines, content.breakpoints());
        assertEquals(symbols, content.symbols());
    }

    @Test
    void emptyExportAndForeignFiles() {
        BreakpointExport.Content empty = BreakpointExport.read(BreakpointExport.write(Set.of(), List.of()));
        assertTrue(empty.breakpoints().isEmpty());
        assertTrue(empty.symbols().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BreakpointExport.read("<xml/>"));
        assertThrows(IllegalArgumentException.class, () -> BreakpointExport.read(null));
    }
}
