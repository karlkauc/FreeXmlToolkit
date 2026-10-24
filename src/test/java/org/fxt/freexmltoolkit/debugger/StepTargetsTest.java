package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Smart Step Into finds the calls on the paused line, in their order. */
class StepTargetsTest {

    private static final String XSLT = ""
            + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:f=\"urn:f\">\n" // 1
            + "  <xsl:template match=\"/\">\n"                                                             // 2
            + "    <x a=\"{f:one(1)}\"><xsl:call-template name=\"two\"/><xsl:value-of select=\"f:one(2)\"/></x>\n" // 3
            + "    <xsl:apply-templates select=\"*\" mode=\"m\"/><xsl:apply-templates/>\n"                     // 4
            + "    <xsl:value-of\n"                                                                           // 5
            + "        select=\"f:three(.)\"/>\n"                                                             // 6
            + "    <plain/>\n"                                                                                // 7
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>\n";

    private static final List<String> FUNCTIONS = List.of("f:one", "f:three", "f:unused");

    private static List<String> labels(int line) {
        return StepTargets.find(XSLT, line, FUNCTIONS).stream().map(StepTarget::label).toList();
    }

    @Test
    void findsFunctionsAndCallTemplateInLineOrderWithoutDuplicates() {
        assertEquals(List.of("function f:one()", "template name=\"two\""), labels(3));
    }

    @Test
    void applyTemplatesWithAndWithoutMode() {
        assertEquals(List.of("templates in mode m", "next template rule (apply-templates)"), labels(4));
        StepTarget any = StepTargets.find(XSLT, 4, FUNCTIONS).get(1);
        assertNull(any.symbol());
        assertTrue(any.matches("item", null, null, null), "any template rule");
        assertFalse(any.matches(null, null, "one", null), "but not a function");
    }

    @Test
    void aMultiLineStartTagIsReadWholeAtItsLastLine() {
        assertEquals(List.of("function f:three()"), labels(6));
        assertEquals(List.of(), labels(7));
        assertEquals(List.of(), labels(99));
    }

    @Test
    void targetsMatchTheComponentTheyName() {
        StepTarget fn = StepTargets.find(XSLT, 3, FUNCTIONS).getFirst();
        assertTrue(fn.matches(null, null, "f:one", null), "the entered function's display name matches");
        assertFalse(fn.matches(null, null, "f:three", null));
    }
}
