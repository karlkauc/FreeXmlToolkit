package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret;
import org.junit.jupiter.api.Test;

/** Input-node breakpoints: identity, matching, text and storage. */
class InputNodeBreakpointTest {

    @Test
    void identifiesTheNodeByFileLineAndColumn() {
        SymbolBreakpoint bp = SymbolBreakpoint.inputNode("/data/in.xml", 3, 12, "/order[1]/item[2]");
        assertEquals("/order[1]/item[2]", bp.inputLabel());
        assertEquals(3, bp.inputLine());
        assertEquals(12, bp.inputColumn());
        assertEquals("/data/in.xml", bp.inputPath());
        assertTrue(bp.matchesInputNode("/data/in.xml", 3, 12));
        assertTrue(bp.matchesInputNode("", 3, 12), "in-memory input carries no path");
        assertFalse(bp.matchesInputNode("/data/in.xml", 3, 13));
        assertFalse(bp.matchesInputNode("/data/other.xml", 3, 12));
        assertFalse(bp.withEnabled(false).matchesInputNode("/data/in.xml", 3, 12));
        assertFalse(bp.matches("item", null, null, null), "never a template match");
        assertEquals("input node /order[1]/item[2] (in.xml:3)", bp.describe());
    }

    @Test
    void isStoredWithTheOtherSymbolBreakpoints() {
        SymbolBreakpoint bp = SymbolBreakpoint.inputNode("/data/in.xml", 3, 12, "/order[1]/item[2]");
        assertEquals(List.of(bp), WatchStore.parseSymbols(WatchStore.formatSymbols(List.of(bp))));
    }

    @Test
    void theElementAtTheCaretKnowsWhereItsStartTagEnds() {
        String xml = "<order>\n  <item n=\"1\"/><item\n     n=\"2\">x</item>\n</order>";
        int caret = xml.indexOf(">x<") + 2; // inside the second item's content
        var element = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("/order/item[2]", element.xpath());
        assertEquals(xml.indexOf(">x<"), element.startTagEnd());
    }
}
