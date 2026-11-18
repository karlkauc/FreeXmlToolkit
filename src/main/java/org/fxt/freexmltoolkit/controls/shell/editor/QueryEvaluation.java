package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.query.QueryError;
import org.fxt.freexmltoolkit.service.query.QueryErrors;
import org.fxt.freexmltoolkit.service.query.QueryLanguage;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;
import org.fxt.freexmltoolkit.service.standards.XQueryVersion;
import org.fxt.freexmltoolkit.service.telemetry.QueryFailure;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;

/**
 * Runs an XPath or XQuery of the Query Console or a query tab once and returns everything the UI shows: the
 * result text, the table, the located hits and the structured error. Before, the console evaluated a query up to
 * three times (text, table, hits). UI-free; the CLI keeps {@link TransformRunner}, which shares the evaluation
 * code in {@code XmlServiceImpl} / {@link XsltTransformationEngine}.
 */
public final class QueryEvaluation {

    /** Lets the caller stop a run; an XQuery stops at its next traced construct. */
    public static final class Cancel {
        private volatile boolean cancelled;

        /** Asks the run to stop. */
        public void cancel() {
            cancelled = true;
        }

        /** @return whether {@link #cancel()} was called */
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * What to run.
     *
     * @param language                XPath or XQuery
     * @param xpathVersion            XPath version ({@code null} = 3.1)
     * @param xqueryVersion           XQuery version ({@code null} = 3.1)
     * @param xml                     the document
     * @param query                   the expression or query
     * @param inputBaseUri            the document's {@code file:} URI, or {@code null}
     * @param queryBaseUri            the query's {@code file:} URI (modules, its own relative paths), or {@code null}
     * @param namespaces              prefixes declared before the query's own; nullable
     * @param defaultElementNamespace the namespace unprefixed element names are in, or {@code null}
     * @param cancel                  stops the run; nullable
     * @param timeoutMillis           time limit of an XQuery (0 = none); an XPath is bounded by its caller
     * @param locate                  locate the result's nodes in the document ({@link Result#items()}); without,
     *                                only {@link Result#count()} / {@link Result#nodeCount()} are filled
     */
    public record Request(QueryLanguage language, XPathVersion xpathVersion, XQueryVersion xqueryVersion, String xml,
                          String query, String inputBaseUri, String queryBaseUri, Map<String, String> namespaces,
                          String defaultElementNamespace, Cancel cancel, long timeoutMillis, boolean locate) {

        /** A request that locates its hits. */
        public Request(QueryLanguage language, XPathVersion xpathVersion, XQueryVersion xqueryVersion, String xml,
                       String query, String inputBaseUri, String queryBaseUri, Map<String, String> namespaces,
                       String defaultElementNamespace, Cancel cancel, long timeoutMillis) {
            this(language, xpathVersion, xqueryVersion, xml, query, inputBaseUri, queryBaseUri, namespaces,
                    defaultElementNamespace, cancel, timeoutMillis, true);
        }

        /** This request without locating the hits (a count is enough). */
        public Request countOnly() {
            return new Request(language, xpathVersion, xqueryVersion, xml, query, inputBaseUri, queryBaseUri,
                    namespaces, defaultElementNamespace, cancel, timeoutMillis, false);
        }

        /** A run without namespaces, cancel or time limit. */
        public static Request of(QueryLanguage language, XPathVersion xpathVersion, XQueryVersion xqueryVersion,
                                 String xml, String query, String baseUri) {
            return new Request(language, xpathVersion, xqueryVersion, xml, query, baseUri, baseUri, null, null,
                    null, 0);
        }
    }

    /**
     * A run's outcome.
     *
     * @param text      the result text, or {@code "ERROR: "} + the formatted error
     * @param failure   why it failed (telemetry), {@code null} on success
     * @param error     the structured error, or {@code null}
     * @param table     the result as a table, or {@code null} (failure, or not tabular)
     * @param items     the result items, document nodes located (empty without {@link Request#locate()})
     * @param cancelled stopped by {@link Cancel}
     * @param timedOut  stopped at the time limit
     * @param count     the number of result items
     * @param nodeCount how many of them are nodes of the document
     */
    public record Result(String text, QueryFailure failure, QueryError error, XQueryTableRunner.XQueryTable table,
                         List<XPathMatchLocator.ResultItem> items, boolean cancelled, boolean timedOut, int count,
                         int nodeCount) {

        Result(String text, QueryFailure failure, QueryError error, XQueryTableRunner.XQueryTable table,
               List<XPathMatchLocator.ResultItem> items, boolean cancelled, boolean timedOut) {
            this(text, failure, error, table, items, cancelled, timedOut, 0, 0);
        }

        /** @return whether the run produced a result */
        public boolean ok() {
            return failure == null && !cancelled && !timedOut;
        }

        /** @return the text, failure and error as {@link TransformRunner} reports them */
        public TransformRunner.QueryRun run() {
            return new TransformRunner.QueryRun(text, failure, error);
        }
    }

    /** Evaluations so far (tests: a run evaluates once). */
    static final java.util.concurrent.atomic.AtomicInteger EVALUATIONS = new java.util.concurrent.atomic.AtomicInteger();

    private QueryEvaluation() {
    }

    /** Runs {@code request} on the calling thread. */
    public static Result run(Request request) {
        EVALUATIONS.incrementAndGet();
        return request.language() == QueryLanguage.XQUERY ? xquery(request) : xpath(request);
    }

    private static Result xpath(Request r) {
        XPathVersion version = r.xpathVersion() == null ? XPathVersion.V3_1 : r.xpathVersion();
        XmlService.XPathOutcome outcome;
        try {
            outcome = ServiceRegistry.get(XmlService.class).evaluateXPath(r.xml(), r.query(), version,
                    r.inputBaseUri(), r.namespaces(), r.defaultElementNamespace(), true);
        } catch (Throwable t) {
            if (r.cancel() != null && r.cancel().isCancelled()) {
                return cancelled();
            }
            TransformRunner.QueryRun failed = TransformRunner.failed(t, t.getMessage(), QueryFailure.of(t),
                    r.query(), QueryLanguage.XPATH, version, r.xml());
            return new Result(failed.text(), failed.failure(), failed.error(), null, List.of(), false, false);
        }
        return success(outcome.text() == null ? "" : outcome.text(), r, outcome.document(), outcome.value());
    }

    private static Result xquery(Request r) {
        QueryError incompatible = QueryErrors.compatibility(r.query(), r.xqueryVersion());
        if (incompatible != null) {
            return new Result("ERROR: " + incompatible.format(), QueryFailure.invalidInput("compat.xquery"),
                    incompatible, null, List.of(), false, false);
        }
        Cancel cancel = r.cancel();
        var options = new XsltTransformationEngine.XQueryOptions(r.namespaces(), r.defaultElementNamespace(), true,
                cancel == null ? null : cancel::isCancelled,
                r.timeoutMillis() > 0 ? System.nanoTime() + r.timeoutMillis() * 1_000_000L : 0);
        XsltTransformationEngine.XQueryOutcome outcome = XsltTransformationEngine.getInstance().evaluateXQuery(
                r.xml(), r.inputBaseUri(), r.query(), r.queryBaseUri(), Map.of(),
                XsltTransformationEngine.OutputFormat.XML, options);
        if (outcome.cancelled()) {
            return cancelled();
        }
        if (outcome.timedOut()) {
            return new Result("", new QueryFailure("timeout", TelemetryEvent.Status.CANCELLED, null), null, null,
                    List.of(), false, true);
        }
        XsltTransformationResult result = outcome.result();
        if (!result.isSuccess()) {
            TransformRunner.QueryRun failed = TransformRunner.failed(result.getCause(), result.getErrorMessage(),
                    QueryFailure.ofCode(result.getErrorCode()), r.query(), QueryLanguage.XQUERY,
                    TransformRunner.xpathLevel(r.xqueryVersion()), r.xml());
            return new Result(failed.text(), failed.failure(), failed.error(), null, List.of(), false, false);
        }
        return success(result.getOutputContent(), r, outcome.document(), outcome.value());
    }

    private static Result cancelled() {
        return new Result("", new QueryFailure("cancelled", TelemetryEvent.Status.CANCELLED, null), null, null,
                List.of(), true, false);
    }

    /** Table and hits come from the one value; failing to build them never fails the run. */
    private static Result success(String text, Request r, net.sf.saxon.s9api.XdmNode document,
                                  net.sf.saxon.s9api.XdmValue value) {
        XQueryTableRunner.XQueryTable table = null;
        List<XPathMatchLocator.ResultItem> items = List.of();
        int count = 0;
        int nodes = 0;
        if (value != null) {
            count = value.size();
            for (net.sf.saxon.s9api.XdmItem item : value) {
                if (item instanceof net.sf.saxon.s9api.XdmNode node && document != null
                        && document.equals(node.getRoot()) && node != document) {
                    nodes++;
                }
            }
            if (r.locate()) {
                try {
                    table = XQueryTableRunner.toTable(value);
                } catch (RuntimeException e) {
                    table = null;
                }
                if (document != null) {
                    try {
                        items = XPathMatchLocator.items(r.xml(), document, value);
                    } catch (RuntimeException e) {
                        items = List.of();
                    }
                }
            }
        }
        return new Result(text, null, null, table, items, false, false, count, nodes);
    }
}
