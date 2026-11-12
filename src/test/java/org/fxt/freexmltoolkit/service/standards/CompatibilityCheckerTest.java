package org.fxt.freexmltoolkit.service.standards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.service.standards.CompatibilityChecker.Kind;
import org.junit.jupiter.api.Test;

class CompatibilityCheckerTest {

    private static final String XSLT20 = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/">
                <xsl:for-each-group select="//a" group-by="."><xsl:value-of select="current-grouping-key()"/></xsl:for-each-group>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void newestVersionHasNothingToCheck() {
        var report = CompatibilityChecker.check(Kind.XSLT, XSLT20, null, StandardsProfile.LATEST);
        assertTrue(report.latest());
        assertTrue(report.compatible());
    }

    @Test
    void xslt10CompilesOnTheJdkProcessor() {
        var report = CompatibilityChecker.check(Kind.XSLT, XSLT20, null,
                StandardsProfile.LATEST.withXslt(XsltVersion.V1_0));
        assertFalse(report.latest());
        assertFalse(report.compatible(), report.toString());
        var ok = CompatibilityChecker.check(Kind.XSLT, XSLT20, null,
                StandardsProfile.LATEST.withXslt(XsltVersion.V2_0));
        assertTrue(ok.compatible(), ok.toString());
    }

    @Test
    void xqueryXpathAndSchematron() {
        assertFalse(CompatibilityChecker.check(Kind.XQUERY, "head((1,2))", null,
                StandardsProfile.LATEST.withXquery(XQueryVersion.V1_0)).compatible());
        assertFalse(CompatibilityChecker.check(Kind.XPATH, "let $a := 1 return $a", null,
                StandardsProfile.LATEST.withXpath(XPathVersion.V2_0)).compatible());
        assertFalse(CompatibilityChecker.check(Kind.XPATH, "for $a in //x return $a", null,
                StandardsProfile.LATEST.withXpath(XPathVersion.V1_0)).compatible());
        assertTrue(CompatibilityChecker.check(Kind.XPATH, "count(//x[@a = $v])", null,
                StandardsProfile.LATEST.withXpath(XPathVersion.V2_0)).compatible());
        String sch = """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <pattern><rule context="r"><assert test="exists(head(a))">x</assert></rule></pattern>
                </schema>
                """;
        assertFalse(CompatibilityChecker.check(Kind.SCHEMATRON, sch, null,
                StandardsProfile.LATEST.withSchematron(SchematronBinding.XSLT2)).compatible());
        assertTrue(CompatibilityChecker.check(Kind.SCHEMATRON, sch, null, StandardsProfile.LATEST).latest());
    }
}
