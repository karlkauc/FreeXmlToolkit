package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XmlProcessingError;

/**
 * XPath and XQuery written by a model or an external agent, run with the restrictions of {@link XsltSandbox}:
 * a processor per run, reads only inside the given roots, no environment variables, output capped. An XQuery is
 * compiled with tracing so its deadline is checked per expression; an XPath expression has no tracing in
 * Saxon's XPath API, so only the caller's timeout ({@code ToolRegistry}) bounds it.
 *
 * <p>Nothing the query or the document contains is logged above DEBUG.
 */
public final class SandboxedQuery {

    private static final Logger logger = LogManager.getLogger(SandboxedQuery.class);

    /** The query language. */
    public enum Language { XPATH, XQUERY }

    /**
     * The outcome of a query.
     *
     * @param ok           whether it ran to completion
     * @param output       the serialized result (items one per line for XPath; the serialized sequence for XQuery)
     * @param itemCount    number of items in the result, -1 when it failed
     * @param errorCode    processor error code, or {@code null}
     * @param errorPhase   {@code compile}, {@code input} or {@code runtime}, or {@code null}
     * @param errorLine    line of the expression (compile/runtime) or of the input (input), -1 when unknown
     * @param errorMessage the processor's message, or {@code null}
     * @param timedOut     stopped at the deadline
     */
    public record Outcome(boolean ok, String output, int itemCount, String errorCode, String errorPhase,
                          int errorLine, String errorMessage, boolean timedOut) {

        static Outcome success(String output, int itemCount) {
            return new Outcome(true, output, itemCount, null, null, -1, null, false);
        }

        static Outcome failure(String code, String phase, int line, String message) {
            return new Outcome(false, "", -1, code, phase, line > 0 ? line : -1, message, false);
        }
    }

    private SandboxedQuery() {
    }

    /**
     * Runs a query.
     *
     * @param xml           the context document, or {@code null} for none
     * @param xmlBaseUri    the document's URI (relative {@code doc()} calls resolve against it), or {@code null}
     * @param query         the expression or the XQuery text
     * @param queryBaseUri  the query file's URI (XQuery module imports), or {@code null}
     * @param variables     external variables (XQuery) as strings; ignored for XPath
     * @param namespaces    prefix → URI for XPath ({@code ""} = default element namespace); the prefixes declared
     *                      on the document's root element are declared too, the given ones win
     * @param timeout       time limit
     * @param readableRoots folders the query may read from
     */
    public static Outcome run(Language language, String xml, String xmlBaseUri, String query, String queryBaseUri,
                              Map<String, String> variables, Map<String, String> namespaces, Duration timeout,
                              List<Path> readableRoots) {
        return run(language, xml, xmlBaseUri, query, queryBaseUri, variables, namespaces, timeout, readableRoots, true);
    }

    /**
     * As {@link #run(Language, String, String, String, String, Map, Map, Duration, List)}; with
     * {@code declareDocumentPrefixes == false} an XPath expression sees only the given namespaces — as in the Query
     * Console, which declares none, so an expression verified here runs the same there.
     */
    public static Outcome run(Language language, String xml, String xmlBaseUri, String query, String queryBaseUri,
                              Map<String, String> variables, Map<String, String> namespaces, Duration timeout,
                              List<Path> readableRoots, boolean declareDocumentPrefixes) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            List<Path> roots = XsltSandbox.realRoots(readableRoots);
            Processor processor = XsltSandbox.newProcessor();
            XsltSandbox.lockDown(processor);
            XsltSandbox.readOnlyWithin(processor, roots);
            XdmNode document = null;
            if (xml != null && !xml.isBlank()) {
                try {
                    document = XsltTransformationEngine.parseXmlDocument(processor, xml, xmlBaseUri);
                } catch (SaxonApiException e) {
                    return Outcome.failure(XsltTransformationEngine.localCode(e.getErrorCode()), "input",
                            e.getLineNumber(), e.getMessage());
                }
            }
            return language == Language.XPATH
                    ? xpath(processor, document, query, namespaces, declareDocumentPrefixes)
                    : xquery(processor, document, query, queryBaseUri, variables, deadline);
        } catch (IOException e) {
            return Outcome.failure(null, null, -1, "A readable folder does not exist: " + e.getMessage());
        } catch (RuntimeException e) {
            if (XsltSandbox.causedBy(e, XsltSandbox.DeadlineExceeded.class)) {
                return new Outcome(false, "", -1, null, "runtime", -1,
                        "The query did not finish within the time limit of " + timeout.toMillis() + " ms", true);
            }
            if (XsltSandbox.causedBy(e, XsltSandbox.OutputLimitExceeded.class)) {
                return Outcome.failure(null, "runtime", -1, "The query produced more than "
                        + XsltSandbox.MAX_OUTPUT_CHARS + " characters of output and was stopped");
            }
            logger.warn("Unexpected failure in a sandboxed query: {}", e.getClass().getName());
            logger.debug("Sandboxed query failure", e);
            return Outcome.failure(null, "runtime", -1, "Unexpected error: " + e.getMessage());
        } catch (StackOverflowError e) {
            return Outcome.failure(null, "runtime", -1, "The query ran out of stack (endless recursion?)");
        }
    }

    private static Outcome xpath(Processor processor, XdmNode document, String expression,
                                 Map<String, String> namespaces, boolean declareDocumentPrefixes) {
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.setLanguageVersion("3.1");
        compiler.declareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
        compiler.declareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
        compiler.declareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
        if (document != null && declareDocumentPrefixes) {
            documentNamespaces(document).forEach(compiler::declareNamespace);
        }
        if (namespaces != null) {
            namespaces.forEach(compiler::declareNamespace);
        }
        XPathSelector selector;
        try {
            selector = compiler.compile(expression).load();
        } catch (SaxonApiException e) {
            return failure(e, "compile");
        }
        try {
            if (document != null) {
                selector.setContextItem(document);
            }
            XdmValue value = selector.evaluate();
            long[] budget = {XsltSandbox.MAX_OUTPUT_CHARS};
            XsltSandbox.LimitedWriter out = new XsltSandbox.LimitedWriter(budget);
            for (XdmItem item : value) {
                writeItem(processor, item, out);
                out.write("\n");
            }
            return Outcome.success(out.toString(), value.size());
        } catch (SaxonApiException e) {
            return failure(e, "runtime");
        } catch (IOException e) {
            return Outcome.failure(null, "runtime", -1, e.getMessage());
        }
    }

    private static Outcome xquery(Processor processor, XdmNode document, String query, String queryBaseUri,
                                  Map<String, String> variables, long deadline) {
        XQueryCompiler compiler = processor.newXQueryCompiler();
        compiler.setCompileWithTracing(true);
        if (queryBaseUri != null && !queryBaseUri.isBlank()) {
            compiler.setBaseURI(java.net.URI.create(queryBaseUri));
        }
        List<XmlProcessingError> staticErrors = new java.util.ArrayList<>();
        compiler.setErrorReporter(error -> {
            if (!error.isWarning()) {
                staticErrors.add(error);
            }
        });
        XQueryExecutable executable;
        try {
            executable = compiler.compile(query);
        } catch (SaxonApiException e) {
            return compileFailure(staticErrors, e);
        }
        try {
            XQueryEvaluator evaluator = executable.load();
            evaluator.setTraceListener(new XsltSandbox.DeadlineListener(deadline));
            if (variables != null) {
                for (Map.Entry<String, String> variable : variables.entrySet()) {
                    evaluator.setExternalVariable(new QName(variable.getKey()), new XdmAtomicValue(variable.getValue()));
                }
            }
            if (document != null) {
                evaluator.setContextItem(document);
            }
            XdmValue value = evaluator.evaluate();
            long[] budget = {XsltSandbox.MAX_OUTPUT_CHARS};
            XsltSandbox.LimitedWriter out = new XsltSandbox.LimitedWriter(budget);
            Serializer serializer = processor.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            try {
                serializer.serializeXdmValue(value);
            } catch (SaxonApiException e) {
                if (!XsltTransformationEngine.isUnserializableItemError(e)) {
                    throw e;
                }
                // maps, arrays, functions, top-level attributes: no XML serialization (SENR0001)
                out = new XsltSandbox.LimitedWriter(new long[] {XsltSandbox.MAX_OUTPUT_CHARS});
                serializer = processor.newSerializer(out);
                serializer.setOutputProperty(Serializer.Property.METHOD, "adaptive");
                serializer.setOutputProperty(Serializer.Property.ITEM_SEPARATOR, "\n");
                serializer.serializeXdmValue(value);
            }
            return Outcome.success(out.toString(), value.size());
        } catch (SaxonApiException e) {
            if (XsltSandbox.causedBy(e, XsltSandbox.DeadlineExceeded.class)
                    || XsltSandbox.causedBy(e, XsltSandbox.OutputLimitExceeded.class)) {
                throw new IllegalStateException(e.getCause() != null ? e.getCause() : e);
            }
            return failure(e, "runtime");
        }
    }

    /** One item as a line: atomic values as text, attributes as {@code name="value"}, nodes serialized. */
    private static void writeItem(Processor processor, XdmItem item, java.io.Writer out)
            throws SaxonApiException, IOException {
        if (item.isAtomicValue()) {
            out.write(item.getStringValue());
            return;
        }
        if (item instanceof XdmNode node) {
            if (node.getNodeKind() == XdmNodeKind.ATTRIBUTE) {
                out.write(node.getNodeName() + "=\"" + node.getStringValue().replace("\"", "&quot;") + "\"");
                return;
            }
            if (node.getNodeKind() == XdmNodeKind.TEXT) {
                out.write(node.getStringValue());
                return;
            }
            Serializer serializer = processor.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
            serializer.serializeNode(node);
            return;
        }
        Serializer serializer = processor.newSerializer(out);
        serializer.setOutputProperty(Serializer.Property.METHOD, "adaptive");
        serializer.serializeXdmValue(item);
    }

    /** The prefixed namespaces in scope on the document's root element. */
    private static Map<String, String> documentNamespaces(XdmNode document) {
        Map<String, String> prefixes = new java.util.LinkedHashMap<>();
        for (XdmNode child : document.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                child.getUnderlyingNode().getAllNamespaces().forEach(binding -> {
                    if (!binding.getPrefix().isEmpty() && !"xml".equals(binding.getPrefix())) {
                        prefixes.put(binding.getPrefix(), binding.getNamespaceUri().toString());
                    }
                });
                break;
            }
        }
        return prefixes;
    }

    /** The first reported static error (it has the location the exception lacks), else the exception. */
    private static Outcome compileFailure(List<XmlProcessingError> staticErrors, SaxonApiException e) {
        if (staticErrors.isEmpty()) {
            return failure(e, "compile");
        }
        XmlProcessingError first = staticErrors.getFirst();
        String code = first.getErrorCode() == null ? XsltTransformationEngine.localCode(e.getErrorCode())
                : first.getErrorCode().getLocalName();
        int line = first.getLocation() == null ? e.getLineNumber() : first.getLocation().getLineNumber();
        String message = first.getMessage()
                + (staticErrors.size() > 1 ? " (and " + (staticErrors.size() - 1) + " more static error"
                + (staticErrors.size() > 2 ? "s" : "") + ")" : "");
        return Outcome.failure(code, phaseOf(code, "compile"), line, message);
    }

    private static Outcome failure(SaxonApiException e, String phase) {
        if (XsltSandbox.causedBy(e, XsltSandbox.DeadlineExceeded.class)
                || XsltSandbox.causedBy(e, XsltSandbox.OutputLimitExceeded.class)) {
            throw new IllegalStateException(e);
        }
        String code = XsltTransformationEngine.localCode(e.getErrorCode());
        return Outcome.failure(code, phaseOf(code, phase), e.getLineNumber(), e.getMessage());
    }

    /**
     * Static errors are reported while compiling; a dynamic error the optimiser finds at compile time (constant
     * folding: {@code 1 div 0} → FOAR0001) keeps its "runtime" nature by its code, not by when it surfaced.
     */
    private static String phaseOf(String code, String phase) {
        if ("compile".equals(phase) && code != null && !code.startsWith("XPST") && !code.startsWith("XQST")
                && !code.startsWith("XPTY") && !code.startsWith("XQTY")) {
            return "runtime";
        }
        return phase;
    }
}
