package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Lines inside a multi-line start tag map to the tag's last line, the one Saxon reports. */
class StartTagLinesTest {

    private static final String XSLT = ""
            + "<xsl:stylesheet version=\"3.0\"\n"                                  // 1
            + "    xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"           // 2
            + "  <!-- a comment <with a tag>\n"                                      // 3
            + "       over two lines -->\n"                                          // 4
            + "  <xsl:template match=\"/\">\n"                                       // 5
            + "    <xsl:value-of select=\"string-join((\n"                           // 6
            + "        'a > b',\n"                                                   // 7
            + "        'c'), ',')\"/>\n"                                             // 8
            + "    <![CDATA[ <not a tag\n"                                           // 9
            + "    ]]>\n"                                                            // 10
            + "  </xsl:template\n"                                                   // 11
            + "  >\n"                                                                // 12
            + "</xsl:stylesheet>\n";                                                 // 13

    @Test
    void linesOfAMultiLineStartTagResolveToItsLastLine() {
        int[] map = StartTagLines.map(XSLT);
        assertEquals(2, StartTagLines.resolve(map, 1), "the stylesheet's start tag ends on line 2");
        assertEquals(8, StartTagLines.resolve(map, 6), "a quoted '>' does not end the tag");
        assertEquals(8, StartTagLines.resolve(map, 7));
        assertEquals(8, StartTagLines.resolve(map, 8), "the last line is itself");
        assertEquals(3, StartTagLines.resolve(map, 3), "comments are not tags");
        assertEquals(5, StartTagLines.resolve(map, 5), "a one-line tag stays");
        assertEquals(9, StartTagLines.resolve(map, 9), "CDATA content is not a tag");
        assertEquals(11, StartTagLines.resolve(map, 11), "end tags are not instructions");
        assertEquals(99, StartTagLines.resolve(map, 99), "lines past the end stay");
    }

    @Test
    void noTextMeansNoMapping() {
        assertEquals(4, StartTagLines.resolve(StartTagLines.map(null), 4));
        assertEquals(4, StartTagLines.resolve(StartTagLines.map(""), 4));
        assertEquals(4, StartTagLines.resolve(null, 4));
    }
}
