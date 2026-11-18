package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.DeclaredParameter;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.Test;

/**
 * Parameters are read from the stylesheet / query as declared (name, type, required) and a value typed as text
 * reaches a typed parameter converted to its type, as the Saxon command line binds {@code name=value}.
 */
class XsltTransformationEngineParametersTest {

    private final XsltTransformationEngine engine = XsltTransformationEngine.getInstance();

    private static final String XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xsl:output method="text"/>
              <xsl:param name="count" as="xs:integer" required="yes"/>
              <xsl:param name="day" as="xs:date" select="current-date()"/>
              <xsl:param name="label"/>
              <xsl:template match="/"><xsl:value-of select="$count * 2, $label"/></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void theDeclaredParametersOfAStylesheetAreListed() {
        List<DeclaredParameter> params = engine.stylesheetParameters(XSLT, null);

        assertEquals(List.of(new DeclaredParameter("count", "xs:integer", true),
                new DeclaredParameter("day", "xs:date", false),
                new DeclaredParameter("label", "item()*", false)), params);
    }

    @Test
    void aTextValueReachesATypedParameterConverted() {
        XsltTransformationResult result = engine.transform("<a/>", XSLT, Map.of("count", "21", "label", "x"),
                OutputFormat.TEXT);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("42 x", result.getOutputContent().strip());
    }

    @Test
    void aValueOfTheWrongFormNamesTheParameter() {
        XsltTransformationResult result = engine.transform("<a/>", XSLT, Map.of("count", "many"), OutputFormat.TEXT);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("count"), result.getErrorMessage());
    }

    @Test
    void theExternalVariablesOfAQueryAreListedAndTyped() {
        String query = """
                declare variable $n as xs:integer external;
                declare variable $sep external := ', ';
                declare variable $internal := 1;
                string-join((1 to $n) ! string(.), $sep)""";
        assertEquals(List.of(new DeclaredParameter("n", "xs:integer", true),
                new DeclaredParameter("sep", "item()*", false)), engine.queryVariables(query, null),
                "in name order");

        var outcome = engine.evaluateXQuery(null, null, query, null, Map.of("n", "3"), OutputFormat.TEXT,
                XsltTransformationEngine.XQueryOptions.DEFAULT);
        assertTrue(outcome.result().isSuccess(), outcome.result().getErrorMessage());
        assertEquals("1, 2, 3", outcome.result().getOutputContent().strip());
    }

    @Test
    void aStylesheetThatDoesNotCompileHasNoParameters() {
        assertEquals(List.of(), engine.stylesheetParameters("<xsl:bad/>", null));
    }

    private static final String STARTS = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:f="urn:f">
              <xsl:output method="text"/>
              <xsl:template name="xsl:initial-template">initial</xsl:template>
              <xsl:template name="f:hello">hello <xsl:value-of select="count(//item)"/></xsl:template>
              <xsl:template match="/" mode="m">mode <xsl:value-of select="count(//item)"/></xsl:template>
              <xsl:template match="/">default</xsl:template>
              <xsl:function name="f:twice" as="xs:integer" visibility="public">
                <xsl:param name="n" as="xs:integer"/>
                <xsl:sequence select="$n * 2"/>
              </xsl:function>
            </xsl:stylesheet>
            """;

    private XsltTransformationResult from(XsltTransformationEngine.StartPoint start, String xml) {
        return engine.transform(xml, STARTS, null, null, Map.of(), OutputFormat.TEXT, start);
    }

    @Test
    void aRunStartsAtTheInitialTemplateWithoutInput() {
        var result = from(new XsltTransformationEngine.StartPoint(
                XsltTransformationEngine.StartPoint.Kind.TEMPLATE, null, List.of()), null);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("initial", result.getOutputContent().strip());
    }

    @Test
    void aRunStartsAtANamedTemplateWithTheInputAsContext() {
        var result = from(new XsltTransformationEngine.StartPoint(
                XsltTransformationEngine.StartPoint.Kind.TEMPLATE, "f:hello", List.of()), "<a><item/><item/></a>");
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("hello 2", result.getOutputContent().strip());
    }

    @Test
    void aRunStartsInAMode() {
        var result = from(new XsltTransformationEngine.StartPoint(
                XsltTransformationEngine.StartPoint.Kind.APPLY, "m", List.of()), "<a><item/></a>");
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("mode 1", result.getOutputContent().strip());
    }

    @Test
    void aRunCallsAFunctionWithTypedArguments() {
        var result = from(new XsltTransformationEngine.StartPoint(
                XsltTransformationEngine.StartPoint.Kind.FUNCTION, "f:twice", List.of("21")), null);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("42", result.getOutputContent().strip());
    }

    @Test
    void aModeNeedsAnInput() {
        var result = from(new XsltTransformationEngine.StartPoint(
                XsltTransformationEngine.StartPoint.Kind.APPLY, "m", List.of()), "");
        assertFalse(result.isSuccess());
    }
}
