package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Template / function / mode breakpoints: matching, storage and the stylesheet's suggestions. */
class SymbolBreakpointTest {

    @Test
    void matchingRules() {
        var match = new SymbolBreakpoint(SymbolBreakpoint.Kind.TEMPLATE_MATCH, "order / item", true);
        assertTrue(match.matches("order/item", null, null, null), "whitespace is ignored");
        assertFalse(match.matches("item", null, null, null));
        var fn = new SymbolBreakpoint(SymbolBreakpoint.Kind.FUNCTION, "sum", true);
        assertTrue(fn.matches(null, null, "f:sum", null), "no prefix given: any prefix");
        assertFalse(new SymbolBreakpoint(SymbolBreakpoint.Kind.FUNCTION, "g:sum", true).matches(null, null, "f:sum", null));
        var mode = new SymbolBreakpoint(SymbolBreakpoint.Kind.MODE, "list", true);
        assertTrue(mode.matches("item", null, null, "list"));
        assertFalse(mode.matches(null, null, "f:sum", "list"), "functions have no mode");
        assertFalse(match.withEnabled(false).matches("order/item", null, null, null), "disabled");
    }

    @Test
    void storageRoundTrip() {
        List<SymbolBreakpoint> symbols = List.of(
                new SymbolBreakpoint(SymbolBreakpoint.Kind.TEMPLATE_MATCH, "item[@type='a\tb']", true),
                new SymbolBreakpoint(SymbolBreakpoint.Kind.FUNCTION, "f:sum", false));
        assertEquals(symbols, WatchStore.parseSymbols(WatchStore.formatSymbols(symbols)));
        assertEquals(List.of(), WatchStore.parseSymbols("NOPE\tx\ttrue"), "unknown kinds are skipped");
    }

    @Test
    void suggestionsFromTheStylesheet() {
        String xsl = "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
                + "<xsl:mode name='summary'/>"
                + "<xsl:template match=\"item[@n &gt; 1]\" mode=\"list #default\"/>"
                + "<xsl:template name='footer'/>"
                + "<xsl:function name=\"f:twice\"/>"
                + "</xsl:stylesheet>";
        Map<SymbolBreakpoint.Kind, List<String>> s = StylesheetSymbols.scan(xsl);
        assertEquals(List.of("item[@n > 1]"), s.get(SymbolBreakpoint.Kind.TEMPLATE_MATCH));
        assertEquals(List.of("footer"), s.get(SymbolBreakpoint.Kind.TEMPLATE_NAME));
        assertEquals(List.of("f:twice"), s.get(SymbolBreakpoint.Kind.FUNCTION));
        assertEquals(List.of("list", "summary"), s.get(SymbolBreakpoint.Kind.MODE), "#default is no mode name");
    }
}
