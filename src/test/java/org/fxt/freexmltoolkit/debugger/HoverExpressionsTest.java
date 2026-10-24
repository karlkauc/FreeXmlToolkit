package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Which variable reference the mouse rests on in stylesheet text. */
class HoverExpressionsTest {

    private static final String TEXT = "<xsl:value-of select=\"$total + $f:rate * count($items)\"/> $1 $x. {$avt}";

    private static Optional<String> at(String marker, int offset) {
        return HoverExpressions.variableAt(TEXT, TEXT.indexOf(marker) + offset);
    }

    @Test
    void findsTheVariableFromTheDollarOrTheName() {
        assertEquals(Optional.of("$total"), at("$total", 0), "on the $");
        assertEquals(Optional.of("$total"), at("$total", 3), "inside the name");
        assertEquals(Optional.of("$total"), at("$total", 5), "last character");
        assertEquals(Optional.of("$f:rate"), at("$f:rate", 4), "prefixed name");
        assertEquals(Optional.of("$items"), at("$items", 2), "inside a function call");
        assertEquals(Optional.of("$avt"), at("$avt", 1), "in an attribute value template");
    }

    @Test
    void ignoresEverythingElse() {
        assertTrue(at("count(", 2).isEmpty(), "a function name");
        assertTrue(at("select", 1).isEmpty(), "markup");
        assertTrue(at("$1", 1).isEmpty(), "not a name");
        assertEquals(Optional.of("$x"), at("$x.", 1), "a trailing dot is not part of the name");
        assertTrue(HoverExpressions.variableAt(TEXT, -1).isEmpty());
        assertTrue(HoverExpressions.variableAt(TEXT, TEXT.length()).isEmpty());
        assertTrue(HoverExpressions.variableAt(null, 0).isEmpty());
    }
}
