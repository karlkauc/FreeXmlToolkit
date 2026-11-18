package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What a run writes besides its result: {@code fn:trace()} lines become messages of the result (instead of standard
 * error), and the stylesheet's / query's own serialization properties win over the defaults for their method.
 */
class XsltTransformationEngineOutputTest {

    private static final String XML = "<root><item>1</item><item>2</item></root>";
    private final XsltTransformationEngine engine = XsltTransformationEngine.getInstance();

    @AfterEach
    void resetOptions() {
        engine.setOutputOptions(null);
    }

    private static String stylesheet(String output, String body) {
        return """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                  %s
                  <xsl:template match="/">%s</xsl:template>
                </xsl:stylesheet>
                """.formatted(output, body);
    }

    private static List<String> traces(XsltTransformationResult result) {
        return result.getMessages().stream()
                .filter(m -> XsltDebugMessageListener.TRACE_LEVEL.equals(m.getLevel()))
                .map(XsltTransformationResult.TransformationMessage::getMessage).toList();
    }

    @Test
    void traceLinesOfAStylesheetAreMessagesOfTheResult() {
        XsltTransformationResult result = engine.transform(XML,
                stylesheet("", "<out><xsl:value-of select=\"trace(count(//item), 'items')\"/></out>"),
                Map.of(), OutputFormat.XML);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        List<String> traces = traces(result);
        assertEquals(1, traces.size(), traces.toString());
        assertTrue(traces.getFirst().contains("items") && traces.getFirst().contains("2"), traces.getFirst());
    }

    @Test
    void traceLinesOfAQueryAreMessagesOfTheResult() {
        var outcome = engine.evaluateXQuery(XML, null, "trace(count(//item), 'n')", null, Map.of(),
                OutputFormat.XML, XsltTransformationEngine.XQueryOptions.DEFAULT);

        assertTrue(outcome.result().isSuccess(), outcome.result().getErrorMessage());
        assertEquals(1, traces(outcome.result()).size());
    }

    @Test
    void traceLinesOfAStoppableQueryAreMessagesToo() {
        var options = new XsltTransformationEngine.XQueryOptions(null, null, true, () -> false, 0);
        var outcome = engine.evaluateXQuery(XML, null, "count(trace(//item, 'items'))", null, Map.of(),
                OutputFormat.XML, options);

        assertTrue(outcome.result().isSuccess(), outcome.result().getErrorMessage());
        List<String> traces = traces(outcome.result());
        assertFalse(traces.isEmpty(), outcome.result().getMessages().toString());
        assertTrue(traces.getFirst().startsWith("items"), traces.toString());
    }

    @Test
    void traceLinesBeforeAFailureStayWithTheFailedQuery() {
        var outcome = engine.evaluateXQuery(XML, null, "(trace('before', 'step'), error())", null, Map.of(),
                OutputFormat.XML, XsltTransformationEngine.XQueryOptions.DEFAULT);

        assertFalse(outcome.result().isSuccess());
        assertEquals(1, traces(outcome.result()).size());
    }

    @Test
    void withoutXslOutputTheResultIsIndented() {
        XsltTransformationResult result = engine.transform(XML, stylesheet("", "<a><b/></a>"), Map.of(),
                OutputFormat.XML);

        assertTrue(result.getOutputContent().contains("<a>\n"), result.getOutputContent());
    }

    @Test
    void theStylesheetsIndentWinsForItsOwnMethod() {
        XsltTransformationResult result = engine.transform(XML,
                stylesheet("<xsl:output method=\"xml\" indent=\"no\"/>", "<a><b/></a>"), Map.of(), OutputFormat.XML);

        assertTrue(result.getOutputContent().contains("<a><b/></a>"), result.getOutputContent());
    }

    @Test
    void anotherMethodChosenInThePanelKeepsTheDefaults() {
        XsltTransformationResult result = engine.transform(XML,
                stylesheet("<xsl:output method=\"html\" indent=\"no\"/>", "<a><b/></a>"), Map.of(), OutputFormat.XML);

        assertTrue(result.getOutputContent().contains("<a>\n"), result.getOutputContent());
    }

    @Test
    void anHtml5StylesheetGetsNoXhtmlDoctype() {
        XsltTransformationResult result = engine.transform(XML,
                stylesheet("<xsl:output method=\"html\" html-version=\"5\"/>", "<html><body/></html>"), Map.of(),
                OutputFormat.HTML);

        assertFalse(result.getOutputContent().contains("XHTML 1.0"), result.getOutputContent());
        assertTrue(result.getOutputContent().toLowerCase().contains("<!doctype html>"), result.getOutputContent());
    }

    @Test
    void optionsApplyWhereTheStylesheetSaysNothing() {
        engine.setOutputOptions(new OutputOptions(false, true));
        XsltTransformationResult result = engine.transform(XML, stylesheet("", "<a><b/></a>"), Map.of(),
                OutputFormat.XML);

        assertEquals("<a><b/></a>", result.getOutputContent().strip());
    }

    @Test
    void theExecutionPlanOfAQueryAndAStylesheetIsXml() throws Exception {
        String query = engine.explainXQuery("declare namespace f = 'urn:f'; count(//f:item)", null, null, null);
        assertTrue(query.contains("count") && query.startsWith("<"), query);
        String stylesheet = engine.explainXslt(stylesheet("", "<out/>"), null);
        assertTrue(stylesheet.startsWith("<"), stylesheet);
        assertThrows(net.sf.saxon.s9api.SaxonApiException.class, () -> engine.explainXQuery("count(", null, null, null));
    }

    @Test
    void aQueryWithoutOutputOptionsIsIndentedAsBefore() {
        var outcome = engine.evaluateXQuery(XML, null, "<a><b/></a>", null, Map.of(), OutputFormat.XML,
                XsltTransformationEngine.XQueryOptions.DEFAULT);

        assertTrue(outcome.result().getOutputContent().contains("<a>\n"), outcome.result().getOutputContent());
    }

    @Test
    void aQuerysOwnIndentOptionWins() {
        var outcome = engine.evaluateXQuery(XML, null, """
                declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
                declare option output:indent "no";
                <a><b/></a>""", null, Map.of(), OutputFormat.XML, XsltTransformationEngine.XQueryOptions.DEFAULT);

        assertTrue(outcome.result().getOutputContent().contains("<a><b/></a>"), outcome.result().getOutputContent());
    }
}
