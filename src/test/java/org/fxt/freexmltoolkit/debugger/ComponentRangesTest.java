package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Template and function line ranges for coverage. */
class ComponentRangesTest {

    @Test
    void findsTemplatesAndFunctionsWithTheirLines() {
        String xslt = ""
                + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:f=\"urn:f\">\n" // 1
                + "  <xsl:template match=\"item[@n > 1]\">\n"   // 2
                + "    <x/>\n"                                    // 3
                + "  </xsl:template>\n"                           // 4
                + "  <xsl:template name=\"footer\"/>\n"         // 5
                + "  <xsl:function name=\"f:total\">\n"         // 6
                + "    <xsl:sequence select=\"1\"/>\n"          // 7
                + "  </xsl:function>\n"                           // 8
                + "</xsl:stylesheet>\n";
        assertEquals(List.of(
                new ComponentRanges.Range("template match=\"item[@n > 1]\"", 2, 4),
                new ComponentRanges.Range("template name=\"footer\"", 5, 5),
                new ComponentRanges.Range("function f:total()", 6, 8)), ComponentRanges.scan(xslt));
        assertTrue(ComponentRanges.scan(null).isEmpty());
    }
}
