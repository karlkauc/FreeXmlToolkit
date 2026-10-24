package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;

import org.junit.jupiter.api.Test;

/** Inline values: which declaration lines show which paused value. */
class InlineValuesTest {

    private static final String XSLT = ""
            + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
            + "  <xsl:param name=\"mode\" select=\"'full'\"/>\n"                                    // 2
            + "  <xsl:variable name=\"late\" select=\"1\"/>\n"                                       // 3
            + "  <xsl:template match=\"item\">\n"                                                    // 4
            + "    <xsl:variable name=\"n\" select=\"@n\"/>\n"                                       // 5
            + "    <xsl:variable name=\"label\"\n"                                                    // 6
            + "                  select=\"concat('#', $n)\"/>\n"                                     // 7
            + "    <x><xsl:value-of select=\"$label\"/></x>\n"                                        // 8
            + "    <xsl:variable name=\"after\" select=\"2\"/>\n"                                    // 9
            + "  </xsl:template>\n"                                                                   // 10
            + "  <xsl:template name=\"other\">\n"                                                     // 11
            + "    <xsl:variable name=\"n\" select=\"0\"/>\n"                                        // 12
            + "  </xsl:template>\n"                                                                   // 13
            + "</xsl:stylesheet>\n";

    private static PausedSnapshot snapshot(int line, Map<QName, XdmValue> locals, Map<QName, XdmValue> globals) {
        return new PausedSnapshot("file:/a.xsl", line, List.of(), List.of(), "", null, locals, globals, 1, 1);
    }

    @Test
    void showsLocalsOfThePausedTemplateUpToThePausedLineAndCompletedGlobals() {
        PausedSnapshot snap = snapshot(8,
                Map.of(new QName("n"), new XdmAtomicValue("7"), new QName("label"), new XdmAtomicValue("#7"),
                        new QName("after"), new XdmAtomicValue(2)),
                Map.of(new QName("mode"), new XdmAtomicValue("full")));
        Map<Integer, String> values = InlineValues.compute(XSLT, 8, snap);
        assertEquals(Map.of(2, "$mode = full", 5, "$n = 7", 7, "$label = #7"), values,
                "the multi-line declaration shows on its last line; $after (line 9) and $late"
                        + " (not evaluated) and the other template's $n show nothing");
    }

    @Test
    void theLastDeclarationOfANameWins() {
        String xslt = XSLT.replace("<xsl:variable name=\"after\" select=\"2\"/>", "<xsl:variable name=\"n\" select=\"2\"/>");
        PausedSnapshot snap = snapshot(10, Map.of(new QName("n"), new XdmAtomicValue(2)), Map.of());
        assertEquals(Map.of(9, "$n = 2"), InlineValues.compute(xslt, 10, snap));
    }

    @Test
    void longAndEmptyValuesAreFormatted() {
        assertEquals("$s = \"\"", InlineValues.format("s", new XdmAtomicValue("")));
        String text = InlineValues.format("s", new XdmAtomicValue("line one\n   line two " + "x".repeat(200)));
        assertTrue(text.startsWith("$s = line one line two xxx"), text);
        assertTrue(text.endsWith("…"), text);
        assertTrue(text.length() <= InlineValues.MAX_TEXT + 6, text);
    }

    @Test
    void textThatDoesNotParseShowsNothing() {
        PausedSnapshot snap = snapshot(5, Map.of(new QName("n"), new XdmAtomicValue("7")), Map.of());
        assertTrue(InlineValues.compute(XSLT.replace("</xsl:template>", "</xsl:templat>"), 5, snap).isEmpty());
        assertTrue(InlineValues.compute("", 5, snap).isEmpty());
        assertTrue(InlineValues.compute(XSLT, 5, null).isEmpty());
    }
}
