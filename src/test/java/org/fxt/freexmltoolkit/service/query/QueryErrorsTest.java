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

    private static QueryError xpath10(String query) {
        TransformRunner.QueryRun run = TransformRunner.xpath(XML, query,
                org.fxt.freexmltoolkit.service.standards.XPathVersion.V1_0);
        assertNotNull(run.error(), run.text());
        assertEquals(org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.INVALID_INPUT,
                run.failure().status(), "the user's expression, not an app error");
        return run.error();
    }

    @Test
    void xpath10ErrorsGetSaxonsCodesAndPositions() {
        QueryError open = xpath10("//Fund[");
        assertEquals("XPST0003", open.code());
        assertEquals(7, open.column(), "at the end, where something was missing");

        QueryError extra = xpath10("//Fund]/Name");
        assertEquals("XPST0003", extra.code());
        assertEquals("]", marked(extra));

        QueryError newer = xpath10("distinct-values(//Ccy)");
        assertEquals("XPST0017", newer.code());
        assertEquals("distinct-values", marked(newer));
        assertTrue(newer.hint().contains("XPath 2.0") && newer.hint().contains("XPath 1.0"), newer.hint());

        QueryError arity = xpath10("substring(//Name)");
        assertEquals("XPST0017", arity.code());
        assertTrue(arity.hint().startsWith("substring() exists, but takes 2 or 3 arguments."), arity.hint());

        QueryError typo = xpath10("count(//Fund) + coutn(//Name)");
        assertEquals(List.of("count"), typo.suggestions().stream().map(QueryError.Suggestion::label).toList());

        QueryError variable = xpath10("//Fund[Name = $name]");
        assertEquals("XPST0008", variable.code());
        assertEquals("$name", marked(variable));
    }

    @Test
    void theCheckOfAnXPath10ExpressionUsesTheXPath10Engine() {
        var v1 = org.fxt.freexmltoolkit.service.standards.XPathVersion.V1_0;
        assertEquals("XPST0017", QueryErrors.check("distinct-values(//Ccy)", QueryLanguage.XPATH, v1, XML)
                .error().code(), "Saxon's 2.0 compiler would have accepted it");
        assertEquals("XPST0003", QueryErrors.check("//Fund[", QueryLanguage.XPATH, v1, XML).error().code());
        assertTrue(QueryErrors.check("count(//Fund)", QueryLanguage.XPATH, v1, XML).clean());
    }

    @Test
    void anXQueryForAnOlderVersionIsCheckedWhileTypingAndMarksTheConstruct() {
        String query = "for $f in //Fund return $f/Name || '-'";
        QueryErrors.Check check = QueryErrors.check(query, QueryLanguage.XQUERY, null,
                org.fxt.freexmltoolkit.service.standards.XQueryVersion.V1_0, XML);
        assertNotNull(check.error());
        assertEquals("FXT-COMPAT", check.error().code());
        int at = query.indexOf("||");
        assertTrue(check.error().hasPosition());
        assertTrue(check.error().offset() >= at,
                "at or after the || (Saxon's parser at level 1.0 stops at the end of the operand after it): "
                        + check.error().offset());
        assertTrue(QueryErrors.check(query, QueryLanguage.XQUERY, null, null, XML).clean(), "3.1 has ||");

        TransformRunner.QueryRun run = TransformRunner.xquery(XML, query, Map.of(), OutputFormat.XML,
                org.fxt.freexmltoolkit.service.standards.XQueryVersion.V1_0);
        assertEquals(check.error().offset(), run.error().offset(), "the run marks the same place");
    }

    @Test
    void anErrorInAnImportedModuleNamesTheModuleInsteadOfMarkingTheQuery(@org.junit.jupiter.api.io.TempDir
            java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("lib.xq"),
                "module namespace m = \"urn:m\";\ndeclare function m:amount() { xs:decimal('x') };");
        String base = dir.resolve("main.xq").toUri().toString();
        String query = "import module namespace m = \"urn:m\" at \"lib.xq\";\nm:amount()";
        TransformRunner.QueryRun run = TransformRunner.xquery(XML, query, Map.of(), OutputFormat.XML, null, null,
                base);
        QueryError e = run.error();
        assertNotNull(e, run.text());
        assertEquals("FORG0001", e.code());
        assertTrue(e.message().contains("in module lib.xq, line 2"), e.message());
        assertFalse(e.hasPosition(), "line 2 of lib.xq is not line 2 of the query");
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
