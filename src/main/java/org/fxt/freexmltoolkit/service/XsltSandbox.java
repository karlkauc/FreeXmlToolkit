package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.util.RootedPaths;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.om.Item;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.XPathException;

/**
 * Building blocks of the sandboxed transformation
 * ({@link XsltTransformationEngine#transformSandboxed}) that runs stylesheets written by a model or an
 * external agent (decision D11 in {@code docs/superpowers/specs/2026-11-10-ai-integration-design.md}):
 *
 * <ul>
 *   <li>its own {@link Processor} per run ({@link #newProcessor}): no Java extensions or system properties
 *       whatever {@code security.xslt.allow.extensions} says, remote resources blocked
 *       ({@link SaxonResourceAccess}), no environment variables, line numbers on;</li>
 *   <li>{@code xsl:result-document} into memory ({@link #resultDocumentsInMemory}), shared with the debugger;</li>
 *   <li>reads ({@code doc()}, {@code document()}, {@code unparsed-text()}, {@code collection()},
 *       {@code xsl:include}/{@code xsl:import}) only from {@code file:} URIs inside the given roots;</li>
 *   <li>a deadline checked on every traced instruction ({@link DeadlineListener});</li>
 *   <li>a cap on the serialized output ({@link LimitedWriter}).</li>
 * </ul>
 */
public final class XsltSandbox {

    /** Characters a sandboxed run may serialize in total (principal result + result documents). */
    static final long MAX_OUTPUT_CHARS = 16L * 1024 * 1024;

    private XsltSandbox() {
    }

    /**
     * A new processor for one sandboxed run: remote resources blocked ({@link SaxonResourceAccess}), no
     * environment variables, line numbers on, and {@link Feature#ALLOW_EXTERNAL_FUNCTIONS} <b>on</b> — Saxon
     * refuses to compile {@code xsl:result-document} otherwise. The caller must call {@link #lockDown} after
     * compiling: with the flag on, {@code system-property()} returns Java system properties (Saxon reads the
     * flag when the function is evaluated, the result-document check happens at compile time). Saxon-HE has
     * no reflexive Java extension functions, so the flag enables nothing else ({@code TransformRunnerSandboxTest}
     * pins both). One processor per run, because the flag is a property of the shared {@link Configuration}.
     */
    static Processor newProcessor() {
        Processor p = new Processor(false);
        Configuration config = p.getUnderlyingConfiguration();
        SaxonResourceAccess.restrict(config);
        config.setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, NO_ENVIRONMENT);
        config.setLineNumbering(true);
        config.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, true);
        return p;
    }

    /** Switches external functions off again once the stylesheet is compiled (see {@link #newProcessor}). */
    static void lockDown(Processor processor) {
        processor.getUnderlyingConfiguration().setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
    }

    private static final EnvironmentVariableResolver NO_ENVIRONMENT = new EnvironmentVariableResolver() {
        @Override
        public Set<String> getAvailableEnvironmentVariables() {
            return Set.of();
        }

        @Override
        public String getEnvironmentVariable(String name) {
            return null;
        }
    };

    /** Thrown on the transforming thread when the deadline has passed; unwinds the transformation. */
    static final class DeadlineExceeded extends RuntimeException {
        DeadlineExceeded() {
            super("time limit exceeded", null, false, false);
        }
    }

    /** Thrown when the run serialized more than {@link #MAX_OUTPUT_CHARS}. */
    static final class OutputLimitExceeded extends RuntimeException {
        OutputLimitExceeded() {
            super("output limit of " + MAX_OUTPUT_CHARS + " characters exceeded", null, false, false);
        }
    }

    /**
     * Checks the deadline whenever an instruction, a template or a function is entered. Needs a stylesheet
     * compiled with tracing. A long computation inside one XPath expression that enters no traced construct
     * is not interrupted by this; the caller ({@code ToolRegistry}) then abandons the call after its timeout.
     */
    static final class DeadlineListener implements TraceListener {
        private final long deadlineNanos;

        DeadlineListener(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        private void check() {
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new DeadlineExceeded();
            }
        }

        @Override
        public void setOutputDestination(net.sf.saxon.lib.Logger stream) {
        }

        @Override
        public void open(Controller controller) {
        }

        @Override
        public void close() {
        }

        @Override
        public void enter(Traceable traceable, Map<String, Object> properties, XPathContext context) {
            check();
        }

        @Override
        public void leave(Traceable traceable) {
        }

        @Override
        public void startCurrentItem(Item item) {
            check();
        }

        @Override
        public void endCurrentItem(Item item) {
        }

        @Override
        public void startRuleSearch() {
        }

        @Override
        public void endRuleSearch(Object rule, Mode mode, Item item) {
        }
    }

    /** A writer that fails once the shared character budget is used up. */
    static final class LimitedWriter extends Writer {
        private final StringBuilder buffer = new StringBuilder();
        private final long[] budget;

        /** @param budget a one-element array shared by all writers of one run: characters still allowed */
        LimitedWriter(long[] budget) {
            this.budget = budget;
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            synchronized (budget) {
                budget[0] -= length;
                if (budget[0] < 0) {
                    throw new OutputLimitExceeded();
                }
            }
            buffer.append(chars, offset, length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return buffer.toString();
        }
    }

    /**
     * Sends every {@code xsl:result-document} of the run into its own in-memory buffer instead of a file.
     *
     * @param sink receives the resolved URI and a supplier of the text written so far
     */
    static void resultDocumentsInMemory(Processor processor, XsltTransformer transformer,
                                        BiConsumer<String, Supplier<String>> sink,
                                        Supplier<? extends Writer> writers) {
        transformer.setResultDocumentHandler(uri -> {
            Writer writer = writers.get();
            sink.accept(uri.toString(), writer::toString);
            return processor.newSerializer(writer);
        });
    }

    /**
     * Restricts what a run may read to {@code file:} URIs inside {@code realRoots}: the transformer's resource
     * resolver ({@code doc()}, {@code document()}), unparsed-text resolver and collection finder are wrapped;
     * allowed requests go on to the previous (secured) resolvers.
     */
    static void readOnlyWithin(XsltTransformer transformer, Processor processor, List<Path> realRoots) {
        Configuration config = processor.getUnderlyingConfiguration();
        Controller controller = transformer.getUnderlyingController();
        ResourceResolver resources = controller.getResourceResolver() != null ? controller.getResourceResolver()
                : config.getResourceResolver();
        transformer.setResourceResolver(guard(resources, realRoots));
        UnparsedTextURIResolver text = config.getUnparsedTextURIResolver();
        transformer.setUnparsedTextResolver((uri, encoding, cfg) -> {
            if (!RootedPaths.isFileUriWithin(uri, realRoots)) {
                throw refused(uri == null ? null : uri.toString());
            }
            return text.resolve(uri, encoding, cfg);
        });
        CollectionFinder finder = controller.getCollectionFinder() != null ? controller.getCollectionFinder()
                : config.getCollectionFinder();
        controller.setCollectionFinder((context, collectionUri) -> {
            if (collectionUri != null && !RootedPaths.isFileUriWithin(toUri(collectionUri, null), realRoots)) {
                throw refused(collectionUri);
            }
            return finder.findCollection(context, collectionUri);
        });
    }

    /**
     * As {@link #readOnlyWithin(XsltTransformer, Processor, List)}, on the configuration of a per-run
     * processor ({@link #newProcessor}), so it covers every compiler and evaluator built from it — XPath,
     * XQuery and its module imports.
     */
    static void readOnlyWithin(Processor processor, List<Path> realRoots) {
        Configuration config = processor.getUnderlyingConfiguration();
        config.setResourceResolver(guard(config.getResourceResolver(), realRoots));
        UnparsedTextURIResolver text = config.getUnparsedTextURIResolver();
        config.setUnparsedTextURIResolver((uri, encoding, cfg) -> {
            if (!RootedPaths.isFileUriWithin(uri, realRoots)) {
                throw refused(uri == null ? null : uri.toString());
            }
            return text.resolve(uri, encoding, cfg);
        });
        CollectionFinder finder = config.getCollectionFinder();
        config.setCollectionFinder((context, collectionUri) -> {
            if (collectionUri != null && !RootedPaths.isFileUriWithin(toUri(collectionUri, null), realRoots)) {
                throw refused(collectionUri);
            }
            return finder.findCollection(context, collectionUri);
        });
    }

    /** A resolver that refuses everything outside {@code realRoots} and hands the rest to {@code next}. */
    static ResourceResolver guard(ResourceResolver next, List<Path> realRoots) {
        return request -> {
            URI uri = request == null ? null : toUri(request.uri, request);
            if (!RootedPaths.isFileUriWithin(uri, realRoots)) {
                throw refused(request == null ? null : request.uri);
            }
            return next == null ? null : next.resolve(request);
        };
    }

    private static URI toUri(String uri, ResourceRequest request) {
        try {
            if (uri != null && !uri.isBlank()) {
                URI parsed = new URI(uri.strip());
                if (parsed.isAbsolute()) {
                    return parsed;
                }
            }
            if (request != null && request.relativeUri != null && request.baseUri != null) {
                return new URI(request.baseUri).resolve(request.relativeUri);
            }
        } catch (Exception malformed) {
            return null;
        }
        return null;
    }

    private static XPathException refused(String uri) {
        XPathException e = new XPathException("Access outside the allowed folders is not permitted: " + uri);
        e.setErrorCode("FODC0002");
        return e;
    }

    /** Whether {@code t} or one of its causes is a {@code type}. */
    static boolean causedBy(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    /** Real paths of the roots; a missing root is an {@link IOException}. */
    static List<Path> realRoots(List<Path> roots) throws IOException {
        return RootedPaths.toRealPaths(roots == null ? List.of() : roots);
    }
}
