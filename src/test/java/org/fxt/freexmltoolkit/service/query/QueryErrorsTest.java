package org.fxt.freexmltoolkit.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.Test;

/**
 * {@link QueryErrors}: code, kind, position and cleaned message of real Saxon failures, the corrections it
 * offers, the check while typing and the empty-result hint. Positions are pinned against Saxon 12's
 * locators (0-based XPath columns, 1-based XQuery run-time columns).
 */
class QueryErrorsTest {

    private static final String XML = "<funds><Fund><Name>A</Name><Ccy>EUR</Ccy><Amount>5</Amount></Fund>"
            + "<Fund><Name>B</Name><Amount>x</Amount></Fund></funds>";
    private static final String NS_XML = "<funds xmlns=\"urn:f\" xmlns:p=\"urn:p\"><Fund><Name>A</Name></Fund></funds>";

    private static QueryError xpath(String query) {
        return xpath(XML, query);
    }

    private static QueryError xpath(String xml, String query) {
        TransformRunner.QueryRun run = TransformRunner.xpath(xml, query);
        assertNotNull(run.error(), run.text());
        assertEquals("ERROR: " + run.error().format(), run.text());
        return run.error();
    }

    private static QueryError xquery(String xml, String query) {
        TransformRunner.QueryRun run = TransformRunner.xquery(xml, query, Map.of(), OutputFormat.XML);
        assertNotNull(run.error(), run.text());
        return run.error();
    }

    /** The text the error marks. */
    private static String marked(QueryError e) {
        int offset = e.offset();
        return e.query().substring(offset, offset + e.length());
    }

    @Test
    void syntaxErrorsPointAtTheirPosition() {
        QueryError open = xpath("//Fund[");
        assertEquals("XPST0003", open.code());
        assertEquals(QueryError.Kind.SYNTAX, open.kind());
        assertEquals(1, open.line());
        assertEquals(7, open.column(), "at the open bracket's end");

        QueryError token = xpath("//Fund[@id = ]/Name");
        assertEquals(14, token.column());
        assertEquals("]", marked(token));
        assertEquals("Unexpected token \"]\" at start of expression", token.message(), "no wrapper text");
        assertEquals("  //Fund[@id = ]/Name\n               ^", token.excerpt());
    }

    @Test
    void anXPathStepOnAValueMarksTheSlash() {
        QueryError e = xpath("distinct-values(//Ccy)/../Name");
        assertEquals("XPTY0019", e.code());
        assertEquals(QueryError.Kind.TYPE, e.kind());
        assertEquals("/", marked(e));
        assertEquals(23, e.column());
        assertTrue(e.message().contains("the supplied value \"EUR\""), "no Saxon u\"…\": " + e.message());
        assertNotNull(e.hint());
        assertEquals("https://www.w3.org/TR/xpath-31/#ERRXPTY0019", e.specUrl());
        assertTrue(e.format().startsWith("XPTY0019 · type error · column 23\n"), e.format());
    }

    @Test
    void aSlashInsideTheFailingCallsArgumentIsNotTheOneMarked() {
        String query = "distinct-values(//Fund/Ccy)/../Name";
        QueryError e = xpath(query);
        assertEquals("XPTY0019", e.code());
        assertEquals(query.indexOf(")/") + 1, e.offset(), "the '/' after distinct-values(…), not //Fund/Ccy");

        QueryWarning w = QueryErrors.check(query, QueryLanguage.XPATH, null, XML).warnings().get(0);
        assertEquals(query.indexOf(")/") + 2, w.column(), "the warning marks the same '/'");
    }

    @Test
    void conversionsAndOperatorsMarkTheCallOrOperator() {
        assertEquals("xs:decimal(Amount)", marked(xpath("//Fund/xs:decimal(Amount)")));
        QueryError plus = xpath("//Fund/Name + 1");
        assertEquals("+", marked(plus));
        assertTrue(plus.hint().contains("[1]"), "cardinality hint: " + plus.hint());
    }

    @Test
    void multiLineQueriesReportTheLine() {
        QueryError e = xpath("//Fund[Name =\n'A']\n/Name/xs:date(.)");
        assertEquals(3, e.line());
        assertEquals("xs:date(.)", marked(e));
        assertTrue(e.positionLabel().startsWith("line 3, column "), e.positionLabel());

        QueryError q = xquery(XML, "for $f in //Fund\nreturn\n  xs:decimal($f/Amount)");
        assertEquals("FORG0001", q.code());
        assertEquals(3, q.line());
        assertEquals(QueryError.Kind.DYNAMIC, q.kind());
    }

    @Test
    void anErrorInAnXQueryFunctionPointsIntoTheFunction() {
        QueryError e = xquery(XML, "declare function local:f($x) {\n  $x/y\n};\nlocal:f('a')");
        assertEquals("XPTY0019", e.code());
        assertEquals(2, e.line());
        assertEquals("/", marked(e));
        assertEquals("https://www.w3.org/TR/xquery-31/#ERRXPTY0019", e.specUrl());
    }

    @Test
    void unknownFunctionsGetTheClosestNames() {
        QueryError e = xpath("distinct-value(//Ccy)");
        assertEquals("XPST0017", e.code());
        assertEquals("distinct-value", marked(e));
        assertEquals("Cannot find a 1-argument function named distinct-value()", e.message(), "no Q{…}");
        assertEquals(List.of("distinct-values"), e.suggestions().stream().map(QueryError.Suggestion::label).toList());
        assertEquals("distinct-values(//Ccy)", e.suggestions().get(0).applyTo(e.query()));

        QueryError arity = xpath("substring(//Name)");
        assertTrue(arity.hint().startsWith("substring() exists, but takes 2 or 3 arguments."), arity.hint());

        QueryError local = xquery(XML, "declare function local:foo($x) { $x };\nlocal:fo(1)");
        assertEquals("local:fo", marked(local));
        assertEquals("declare function local:foo($x) { $x };\nlocal:foo(1)",
                local.suggestions().get(0).applyTo(local.query()));
    }

    @Test
    void unknownVariablesGetTheDeclaredOnes() {
        QueryError e = xquery(XML, "for $f in //Fund\nlet $name := $f/Name\nreturn $nme");
        assertEquals("XPST0008", e.code());
        assertEquals(3, e.line(), "located by name: Saxon gives no locator here");
        assertEquals("$nme", marked(e));
        assertEquals("$name", e.suggestions().get(0).label());
        assertTrue(e.suggestions().get(0).applyTo(e.query()).endsWith("return $name"));
    }

    @Test
    void undeclaredPrefixesOfferTheDocumentsNamespaceOrAWildcard() {
        QueryError x = xpath(NS_XML, "//p:Fund");
        assertEquals("XPST0081", x.code());
        assertEquals("p:Fund", marked(x));
        assertEquals(List.of("*:Fund"), x.suggestions().stream().map(QueryError.Suggestion::label).toList());
        assertEquals("//*:Fund", x.suggestions().get(0).applyTo(x.query()));

        QueryError q = xquery(NS_XML, "//p:Fund");
        assertEquals("declare namespace p = \"urn:p\";\n//p:Fund", q.suggestions().get(0).applyTo(q.query()));
    }

    @Test
    void theCheckReportsStaticErrorsAndWarningsWithoutRunning() {
        QueryErrors.Check syntax = QueryErrors.check("//Fund[", QueryLanguage.XPATH, null, XML);
        assertNotNull(syntax.error());
        assertEquals("XPST0003", syntax.error().code());

        QueryErrors.Check warning = QueryErrors.check("distinct-values(//Ccy)/../Name", QueryLanguage.XPATH, null, XML);
        assertNull(warning.error());
        assertEquals(1, warning.warnings().size());
        QueryWarning w = warning.warnings().get(0);
        assertEquals("SXWN9026", w.code());
        assertEquals(23, w.column(), "on the '/' like the run-time error");

        QueryErrors.Check xq = QueryErrors.check("for $f in //Fund\nreturn $g", QueryLanguage.XQUERY, null, XML);
        assertEquals("XPST0008", xq.error().code());
        assertEquals(2, xq.error().line());

        assertTrue(QueryErrors.check("count(//Fund)", QueryLanguage.XPATH, null, XML).clean());
        assertTrue(QueryErrors.check("  ", QueryLanguage.XPATH, null, XML).clean());
    }

    @Test
    void anEmptyResultOnANamespacedDocumentExplainsTheNamespace() {
        String hint = QueryErrors.emptyResultHint("//Fund/Name", NS_XML);
        assertNotNull(hint);
        assertTrue(hint.contains("urn:f") && hint.contains("*:Fund"), hint);
        assertNull(QueryErrors.emptyResultHint("//*:Fund", NS_XML), "a wildcard already");
        assertNull(QueryErrors.emptyResultHint("//Fund", XML), "no namespace");
        assertNull(QueryErrors.emptyResultHint("count(//p:Fund)", NS_XML), "prefixed");
    }

    private static final String BROKEN = "<funds>\n  <Fund>\n    <Name>A</Nam>\n  </Fund>\n</funds>";

    @Test
    void aDocumentThatIsNotWellFormedIsReportedAsTheDocumentsError() {
        QueryError x = xpath(BROKEN, "//Fund/Name");
        assertEquals("SXXP0003", x.code());
        assertEquals(QueryError.Kind.DOCUMENT, x.kind());
        assertFalse(x.hasPosition(), "nothing to mark in the query");
        assertEquals(3, x.documentLine());
        assertTrue(x.documentColumn() > 0);
        assertTrue(x.message().startsWith("The element type \"Name\" must be terminated"), x.message());
        assertNotNull(x.hint());
        assertTrue(x.format().startsWith("SXXP0003 · error in the document · document line 3, column "), x.format());

        QueryError q = xquery(BROKEN, "for $f in //Fund return $f/Name");
        assertEquals("SXXP0003", q.code(), "not XPDY0002 'the context item is absent'");
        assertEquals(3, q.documentLine());

        TransformRunner.QueryRun noContext = TransformRunner.xquery(BROKEN, "1 + 1", Map.of(), OutputFormat.XML);
        assertNull(noContext.error(), "a query that does not need the document still runs");
    }

    @Test
    void relativeDocCallsResolveNextToTheDocument(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Files.writeString(dir.resolve("other.xml"), "<x>found</x>");
        String base = dir.resolve("main.xml").toUri().toString();
        TransformRunner.QueryRun run = TransformRunner.xpath("<a/>", "string(doc('other.xml')/x)",
                org.fxt.freexmltoolkit.service.standards.XPathVersion.V3_1, base);
        assertNull(run.error(), run.text());
        assertEquals("found", run.text().strip());

        QueryError missing = xpath("<a/>", "doc('nope.xml')//x");
        assertEquals("FODC0002", missing.code());
        assertTrue(missing.hint().contains("file:///"), missing.hint());
        QueryError unsaved = xquery("<a/>", "doc('other.xml')//x");
        assertEquals("XTDE1162", unsaved.code());
        assertNotNull(unsaved.hint());
        assertNotNull(xquery("<a/>", "doc('C:\\\\data\\\\x.xml')").hint(), "FODC0005: a Windows path");
    }

    @Test
    void errorsWithoutSaxonDetailsStillFormat() {
        QueryError e = QueryErrors.of(null, "boom", "x", QueryLanguage.XPATH);
        assertEquals(QueryError.Kind.OTHER, e.kind());
        assertFalse(e.hasPosition());
        assertEquals("error\nboom", e.format());
        assertNull(e.specUrl());
    }
}
