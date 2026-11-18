package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.query.QueryLanguage;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link QueryEvaluation}: one evaluation gives the same text as {@link TransformRunner} (which the CLI uses), the
 * table and the located hits; a run can be cancelled and stops at its time limit.
 */
class QueryEvaluationTest {

    private static final String XML = "<funds>\n  <Fund id=\"1\"><Name>A</Name><Ccy>EUR</Ccy><Amount>5</Amount></Fund>\n"
            + "  <Fund id=\"2\"><Name>B</Name><Ccy>USD</Ccy><Amount>7</Amount></Fund>\n</funds>";

    @BeforeAll
    static void services() {
        ServiceRegistry.initialize();
    }

    private static QueryEvaluation.Result xpath(String query, XPathVersion version) {
        return QueryEvaluation.run(QueryEvaluation.Request.of(QueryLanguage.XPATH, version, null, XML, query, null));
    }

    private static QueryEvaluation.Result xquery(String query) {
        return QueryEvaluation.run(QueryEvaluation.Request.of(QueryLanguage.XQUERY, null, null, XML, query, null));
    }

    @Test
    void theTextIsTheSameAsTheRunnersForXPath() {
        for (XPathVersion v : XPathVersion.values()) {
            for (String q : new String[] {"//Fund/Name", "//Fund/@id", "count(//Fund)", "//Name/text()",
                    "//Fund[Ccy = 'EUR']", "sum(//Amount)", "//Fund/Name/string()"}) {
                if (v == XPathVersion.V1_0 && q.endsWith("string()")) {
                    continue;
                }
                assertEquals(TransformRunner.xpath(XML, q, v).text(), xpath(q, v).text(), v + " " + q);
            }
        }
    }

    @Test
    void theTextIsTheSameAsTheRunnersForXQuery() {
        for (String q : new String[] {"//Fund/Name", "for $f in //Fund return $f/@id", "count(//Fund)",
                "for $f in //Fund return <row>{$f/Name/text()}</row>", "(1, 'a', //Name)", "map { 'a': 1 }",
                "//Fund/Name/string()", "declare option output:method 'text'; //Name/string()"}) {
            assertEquals(TransformRunner.xquery(XML, q, Map.of(), OutputFormat.XML).text(), xquery(q).text(), q);
        }
    }

    @Test
    void oneEvaluationGivesTheHitsAndTheTable() {
        int before = QueryEvaluation.EVALUATIONS.get();
        QueryEvaluation.Result r = xquery("//Fund");
        assertEquals(before + 1, QueryEvaluation.EVALUATIONS.get());
        assertTrue(r.ok());
        assertEquals(2, r.items().size());
        assertNotNull(r.items().get(0).match(), "a node of the document is located");
        assertNotNull(r.table());
        assertEquals(2, r.table().rows().size());
        assertTrue(r.table().columns().contains("Name"), r.table().columns().toString());

        QueryEvaluation.Result x = xpath("//Fund/Name", XPathVersion.V1_0);
        assertEquals(2, x.items().size(), "XPath 1.0 hits come from Saxon in compatibility mode");
    }

    @Test
    void failuresCarryTheStructuredError() {
        QueryEvaluation.Result r = xpath("//Fund[", null);
        assertFalse(r.ok());
        assertEquals("XPST0003", r.error().code());
        assertTrue(r.text().startsWith("ERROR: XPST0003"));
        assertTrue(r.items().isEmpty());
        assertNull(r.table());
    }

    @Test
    void anXQueryCanBeCancelledAndStopsAtItsTimeLimit() throws Exception {
        // stops at its next FLWOR clause, function call or constructor (pure expressions run to their end)
        String endless = "count(for $i in 1 to 1000000000 let $x := $i * 2 where $x mod 3 = 0 return $x)";
        QueryEvaluation.Cancel cancel = new QueryEvaluation.Cancel();
        var request = new QueryEvaluation.Request(QueryLanguage.XQUERY, null, null, XML, endless, null, null, null,
                null, cancel, 0);
        var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> QueryEvaluation.run(request));
        Thread.sleep(300);
        cancel.cancel();
        QueryEvaluation.Result cancelled = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(cancelled.cancelled());

        QueryEvaluation.Result limited = QueryEvaluation.run(new QueryEvaluation.Request(QueryLanguage.XQUERY, null,
                null, XML, endless, null, null, null, null, null, 300));
        assertTrue(limited.timedOut());
    }

    @Test
    void namespacesAndTheDefaultNamespaceAreDeclaredForTheQuery() {
        String ns = "<funds xmlns=\"urn:f\" xmlns:p=\"urn:p\"><Fund><p:Name>A</p:Name></Fund></funds>";
        for (QueryLanguage lang : QueryLanguage.values()) {
            QueryEvaluation.Result r = QueryEvaluation.run(new QueryEvaluation.Request(lang, null, null, ns,
                    "string(//Fund/p:Name)", null, null, Map.of("p", "urn:p"), "urn:f", null, 0));
            assertTrue(r.ok(), lang + ": " + r.text());
            assertTrue(r.text().strip().endsWith("A"), lang + ": " + r.text());
        }
    }

    @Test
    void traceLinesComeWithTheResultAlsoWhenTheRunCanBeCancelled() {
        var request = new QueryEvaluation.Request(QueryLanguage.XQUERY, null, null,
                "<funds><Fund/><Fund/></funds>", "count(trace(//Fund, 'funds'))", null, null, java.util.Map.of(),
                null, new QueryEvaluation.Cancel(), 60_000);
        QueryEvaluation.Result result = QueryEvaluation.run(request);

        assertTrue(result.ok(), result.text());
        assertFalse(result.messages().isEmpty(), "trace lines: " + result.messages());
        assertTrue(result.messages().getFirst().getMessage().startsWith("funds"));
    }
}
