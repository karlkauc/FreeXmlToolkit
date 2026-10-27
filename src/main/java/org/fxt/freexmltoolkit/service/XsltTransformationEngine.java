package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.di.ServiceRegistry;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

/**
 * Advanced XSLT 3.0 and XQuery 3.1 Transformation Engine with Saxon integration.
 * Provides professional transformation development capabilities including:
 * - XSLT 3.0 support
 * - XQuery 3.1 support for data querying and transformation
 * - Interactive debugging and profiling
 * - Multiple output formats (HTML, XML, JSON, Text)
 * - Template matching visualization
 * - Performance optimization tools
 */
public class XsltTransformationEngine {

    private static final Logger logger = LogManager.getLogger(XsltTransformationEngine.class);

    // Singleton instance
    private static XsltTransformationEngine instance;

    // Saxon processor for XSLT 3.0 support
    private final Processor saxonProcessor;
    private final XsltCompiler xsltCompiler;
    /**
     * Separate processor for interactive debug runs: line numbering is permanently on and
     * stylesheets are compiled with tracing, per run and uncached. Keeping it apart from
     * {@link #saxonProcessor} means a paused debug session never changes how a concurrent
     * normal transformation compiles or runs.
     */
    private volatile Processor debugProcessor;
    // Transformation settings
    private boolean enableProfiling = true;
    private boolean enableDebugging = false;

    // Caching and performance
    private final Map<String, XsltExecutable> compiledStylesheets = new ConcurrentHashMap<>();
    private final Map<String, XQueryExecutable> compiledXQueries = new ConcurrentHashMap<>();
    private final Map<String, TransformationProfile> profileCache = new ConcurrentHashMap<>();

    // Background execution
    private final ExecutorService executorService;

    // Output format support
    public enum OutputFormat {
        XML("xml", "application/xml", "xml"),
        HTML("html", "text/html", "html"),
        XHTML("xhtml", "application/xhtml+xml", "xhtml"),
        TEXT("text", "text/plain", "txt"),
        JSON("json", "application/json", "json");

        private final String saxonMethod;
        private final String mimeType;
        private final String fileExtension;

        OutputFormat(String saxonMethod, String mimeType, String fileExtension) {
            this.saxonMethod = saxonMethod;
            this.mimeType = mimeType;
            this.fileExtension = fileExtension;
        }

        public String getSaxonMethod() {
            return saxonMethod;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getFileExtension() {
            return fileExtension;
        }
    }

    public XsltTransformationEngine() {
        // Initialize Saxon processor with XSLT 3.0 support
        saxonProcessor = new Processor(true); // Enable Saxon-EE features if available

        // Apply security configuration to Saxon processor
        configureSecuritySettings(saxonProcessor.getUnderlyingConfiguration());

        xsltCompiler = saxonProcessor.newXsltCompiler();

        // Configure Saxon for optimal performance
        configureXsltCompiler();

        // Initialize background executor
        executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "XSLTTransformation");
            t.setDaemon(true);
            return t;
        });

        logger.info("XSLT Transformation Engine initialized with Saxon {} (XSLT 3.0 support: {})",
                saxonProcessor.getSaxonProductVersion(),
                saxonProcessor.getSaxonEdition());
    }

    /**
     * Configures security settings for the Saxon processor.
     *
     * <p>By default, Java/JavaScript extensions are disabled to prevent arbitrary code execution.
     * This can be overridden via the application settings if the user explicitly allows extensions.
     *
     * <p><b>Security Warning:</b> Enabling extensions allows XSLT stylesheets to execute
     * arbitrary Java code, which is a significant security risk.
     */
    private void configureSecuritySettings(Configuration config) {
        // Check if extensions are allowed via application settings
        boolean allowExtensions = false;
        try {
            PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
            if (propertiesService != null) {
                allowExtensions = propertiesService.isXsltExtensionsAllowed();
            }
        } catch (Exception e) {
            logger.debug("Could not read XSLT extension settings, using secure defaults: {}", e.getMessage());
        }

        if (allowExtensions) {
            logger.warn("SECURITY WARNING: XSLT Java extensions are ENABLED. " +
                       "This allows XSLT stylesheets to execute arbitrary Java code. " +
                       "Only process trusted stylesheets!");
        } else {
            // Disable extension functions for security
            // This prevents java: and other extension namespaces from executing code
            try {
                // Disable reflexive extension functions (java: namespace)
                config.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);

                logger.info("SECURITY: XSLT Java extensions are disabled (secure default)");
            } catch (Exception e) {
                logger.warn("Could not disable XSLT extensions: {}", e.getMessage());
            }
        }

        // Restrict resource access regardless of the extension setting: a malicious stylesheet
        // must not be able to reach internal/remote URLs via doc()/document()/unparsed-text()
        // (SSRF and remote data exfiltration). Local file resolution is preserved so that
        // legitimate transforms referencing sibling data files keep working.
        configureResourceAccess(config);
    }

    /**
     * Installs resolvers that block network access from doc()/document() and unparsed-text().
     *
     * <p>For doc()/document(), the Schema Library's user entries and registered catalogs are
     * consulted first: a mapped URI is served from its local target regardless of scheme. Any
     * remaining reference over http/https/ftp is rejected; local (file / relative) references
     * fall through to Saxon's default resolution. This blocks SSRF and remote exfiltration
     * without preventing legitimate local data access.
     *
     * @param config the Saxon configuration to harden
     */
    private void configureResourceAccess(Configuration config) {
        try {
            // doc() / document() (and other resource lookups) go through the ResourceResolver in
            // Saxon 12. Capture the existing (default) resolver, then install a wrapper that blocks
            // remote references and delegates everything local to the default.
            final net.sf.saxon.lib.ResourceResolver defaultResolver = config.getResourceResolver();
            config.setResourceResolver(request -> {
                if (request != null && request.uri != null) {
                    // Schema Library / catalogs first: a mapped URI is served from its local
                    // target (or its cached copy; an uncached remote entry is only downloaded
                    // when SchemaLibraryLookup's offline rule allows it).
                    var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
                    java.nio.file.Path mapped = org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                            .localFileFor(library, null, request.uri, request.baseUri,
                                    library.isRemoteDownloadAllowed())
                            .orElse(null);
                    if (mapped != null) {
                        return new javax.xml.transform.stream.StreamSource(mapped.toFile());
                    }
                }
                if (request != null && isRemoteScheme(schemeOfRequest(request))) {
                    throw new net.sf.saxon.trans.XPathException(
                            "Blocked remote resource reference (doc()/document()): " + request.uri);
                }
                // Local / relative reference: delegate to the default resolver (null = Saxon default).
                return defaultResolver != null ? defaultResolver.resolve(request) : null;
            });

            // unparsed-text() / unparsed-text-lines(): block remote, delegate local to Saxon's
            // standard resolver so encoding handling is preserved.
            final net.sf.saxon.lib.UnparsedTextURIResolver standardUnparsedText =
                    new net.sf.saxon.lib.StandardUnparsedTextResolver();
            config.setUnparsedTextURIResolver((absoluteURI, encoding, cfg) -> {
                if (absoluteURI != null && isRemoteScheme(absoluteURI.getScheme())) {
                    throw new net.sf.saxon.trans.XPathException(
                            "Blocked remote resource reference in unparsed-text(): " + absoluteURI);
                }
                return standardUnparsedText.resolve(absoluteURI, encoding, cfg);
            });

            logger.info("SECURITY: XSLT/XQuery network resource access (doc(), unparsed-text()) is blocked");
        } catch (Exception e) {
            logger.warn("Could not install restrictive resource resolvers: {}", e.getMessage());
        }
    }

    /**
     * Determines whether a URI scheme denotes a network protocol that should be blocked.
     *
     * @param scheme the URI scheme (may be {@code null})
     * @return true for http/https/ftp
     */
    private static boolean isRemoteScheme(String scheme) {
        if (scheme == null) {
            return false;
        }
        String s = scheme.toLowerCase();
        return s.equals("http") || s.equals("https") || s.equals("ftp");
    }

    /**
     * Extracts the effective URI scheme of a Saxon {@link net.sf.saxon.lib.ResourceRequest},
     * resolving a relative reference against its base URI when necessary.
     *
     * @param request the resource request
     * @return the lower-case scheme, or {@code null} if it cannot be determined (treated as local)
     */
    private static String schemeOfRequest(net.sf.saxon.lib.ResourceRequest request) {
        try {
            if (request.uri != null && !request.uri.isBlank()) {
                java.net.URI u = java.net.URI.create(request.uri.trim());
                if (u.getScheme() != null) {
                    return u.getScheme().toLowerCase();
                }
            }
            if (request.relativeUri != null && request.baseUri != null
                    && !request.baseUri.isBlank()) {
                java.net.URI resolved = java.net.URI.create(request.baseUri.trim())
                        .resolve(request.relativeUri.trim());
                return resolved.getScheme() != null ? resolved.getScheme().toLowerCase() : null;
            }
            if (request.baseUri != null && !request.baseUri.isBlank()) {
                return java.net.URI.create(request.baseUri.trim()).getScheme();
            }
        } catch (IllegalArgumentException e) {
            logger.debug("Could not parse resource request URI (uri={}, base={}): {}",
                    request.uri, request.baseUri, e.getMessage());
        }
        return null;
    }

    public static synchronized XsltTransformationEngine getInstance() {
        if (instance == null) {
            instance = new XsltTransformationEngine();
        }
        return instance;
    }

    /**
     * Get access to the Saxon processor for advanced operations.
     *
     * @return the Saxon processor instance
     */
    public Processor getSaxonProcessor() {
        return saxonProcessor;
    }

    /**
     * The processor used for interactive debug runs (see {@link #debugProcessor}). Watch
     * expressions must be compiled with this processor: Saxon rejects a context node that was
     * built by another {@code Configuration}.
     */
    public Processor getDebugProcessor() {
        Processor p = debugProcessor;
        if (p == null) {
            synchronized (this) {
                p = debugProcessor;
                if (p == null) {
                    p = new Processor(false);
                    configureSecuritySettings(p.getUnderlyingConfiguration());
                    // Saxon HE does not retain source line numbers unless line numbering is
                    // explicitly enabled on the Configuration. Without this, every
                    // traceable.getLocation().getLineNumber() returns -1 and breakpoints
                    // can never match.
                    p.getUnderlyingConfiguration().setLineNumbering(true);
                    debugProcessor = p;
                }
            }
        }
        return p;
    }

    private void configureXsltCompiler() {
        try {
            // Enable XSLT 3.0 features
            xsltCompiler.setXsltLanguageVersion("3.0");

            // Performance optimizations (simplified without advanced Saxon features)
            // These would require Saxon-EE, so we'll skip for now

            logger.debug("XSLT Compiler configured with XSLT 3.0 features");

        } catch (Exception e) {
            logger.warn("Failed to configure advanced XSLT features, falling back to basic configuration", e);
        }
    }

    // ========== Main Transformation Methods ==========

    /**
     * Transform XML using XSLT stylesheet with full profiling
     */
    public XsltTransformationResult transform(String xmlContent, String xsltContent,
                                              Map<String, Object> parameters, OutputFormat outputFormat) {
        return transform(xmlContent, xsltContent, null, null, parameters, outputFormat);
    }

    /**
     * Like {@link #transform(String, String, Map, OutputFormat)} for content that came from
     * files: {@code xsltSystemId} (the stylesheet's {@code file:} URI) lets relative
     * {@code xsl:include}/{@code xsl:import}/{@code document()} hrefs resolve and gives error
     * locations a file name; {@code xmlBaseUri} does the same for the source document.
     * Either may be {@code null}.
     */
    public XsltTransformationResult transform(String xmlContent, String xsltContent,
                                              String xsltSystemId, String xmlBaseUri,
                                              Map<String, Object> parameters, OutputFormat outputFormat) {
        long startTime = System.currentTimeMillis();

        // Debug listeners (created only when debugging is enabled)
        XsltDebugTraceListener traceListener = null;
        XsltDebugMessageListener messageListener = null;

        try {
            logger.debug("Starting XSLT transformation with {} output format (debug={})", outputFormat, enableDebugging);

            // Create transformation context
            TransformationContext context = new TransformationContext(xmlContent, xsltContent,
                    parameters, outputFormat, xsltSystemId, xmlBaseUri);

            // Start profiling before compilation so the compile phase and the memory
            // baseline are captured (a stylesheet-cache hit reports 0 ms compilation).
            TransformationProfile profile = new TransformationProfile();
            profile.startTransformation();
            profile.setInputSize(xmlContent.length());

            // Compile XSLT if needed
            profile.startCompilation();
            XsltExecutable executable;
            try {
                executable = compileStylesheet(xsltContent, context);
            } catch (StylesheetCompileException e) {
                return e.toResult();
            }
            profile.endCompilation();

            // Create transformer
            XsltTransformer transformer = executable.load();
            configureTransformer(transformer, parameters, outputFormat);

            // xsl:message output is always collected into the result (callers such as the
            // PDF/FOP panel show it); the trace listener only when debugging is enabled.
            messageListener = new XsltDebugMessageListener();
            transformer.setMessageHandler(messageListener);
            if (enableDebugging) {
                traceListener = new XsltDebugTraceListener();
                transformer.setTraceListener(traceListener);
                logger.debug("Debug listeners attached to transformer");
            }

            // Set up input source
            XdmNode sourceDoc;
            try {
                sourceDoc = parseXmlDocument(saxonProcessor, xmlContent, xmlBaseUri);
            } catch (SaxonApiException e) {
                return inputFailure(e);
            }
            transformer.setInitialContextNode(sourceDoc);

            // Execute transformation
            StringWriter outputWriter = new StringWriter();
            transformer.setDestination(saxonProcessor.newSerializer(outputWriter));

            // Configure output serialization
            configureSerializer(transformer.getDestination(), outputFormat);

            // Transform with profiling. Saxon serializes while transforming (the
            // destination is a serializer), so the transformation phase includes
            // serialization and the separate serialization phase stays 0.
            profile.startTransformationPhase();
            transformer.transform();
            profile.endTransformationPhase();

            profile.endTransformation();
            profile.setOutputSize(outputWriter.toString().length());

            // Create successful result
            XsltTransformationResult result = XsltTransformationResult.success(
                    outputWriter.toString(), outputFormat, profile);

            result.setExecutionTime(System.currentTimeMillis() - startTime);
            result.setTransformationContext(context);
            result.setCompilationTime(profile.getCompilationTime());
            result.setTransformationTime(profile.getTransformationTime());
            result.setSerializationTime(profile.getSerializationTime());
            result.setInputSize(xmlContent.length());
            result.setMemoryUsage((int) Math.max(Integer.MIN_VALUE,
                    Math.min(Integer.MAX_VALUE, profile.getMemoryDelta())));

            // Collect debug data from listeners
            if (enableDebugging && traceListener != null) {
                result.setTemplateMatches(traceListener.getTemplateMatches());
                result.setTemplateProfile(traceListener.getTemplateProfile());
                result.setVariableValues(traceListener.getVariableValues());
                result.setCallStack(traceListener.getCallStack());

                // Update profile with template execution data
                if (profile != null) {
                    profile.putAllTemplateExecutionTimes(traceListener.getTemplateExecutionTimes());
                    profile.putAllTemplateExecutionCounts(traceListener.getTemplateExecutionCounts());
                }

                logger.debug("Debug data collected: {} templates, {} variables, {} call stack entries",
                        result.getTemplateMatches().size(),
                        result.getVariableValues().size(),
                        result.getCallStack().size());
            }

            // Collect messages from message listener
            if (messageListener != null) {
                result.setMessages(messageListener.getMessages());
                if (!messageListener.getWarnings().isEmpty()) {
                    result.addAllWarnings(messageListener.getWarnings());
                }
            }

            // Cache profile for analysis
            if (enableProfiling) {
                profileCache.put(context.getCacheKey(), profile);
            }

            logger.debug("XSLT transformation completed in {}ms, output size: {} characters",
                    result.getExecutionTime(), result.getOutputContent().length());

            return result;

        } catch (SaxonApiException e) {
            logger.warn("XSLT transformation failed: {}", e.getMessage());
            return runtimeFailure(e);

        } catch (Exception e) {
            logger.error("Unexpected error during XSLT transformation", e);
            return XsltTransformationResult.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Quick transformation with default settings
     */
    public XsltTransformationResult quickTransform(String xmlContent, String xsltContent) {
        return transform(xmlContent, xsltContent, new HashMap<>(), OutputFormat.XML);
    }

    /**
     * Transform with an interactive {@link DebugSession} attached. The session
     * receives a callback at every traceable instruction so the user can hit
     * breakpoints, step, or stop.
     *
     * <p>Streaming-mode XSLTs ({@code xsl:stream}, {@code saxon:stream}) are
     * incompatible with TraceListener and rejected up front.</p>
     */
    public XsltTransformationResult transformWithDebugSession(
            String xmlContent, String xsltContent,
            Map<String, Object> parameters, OutputFormat outputFormat,
            DebugSession session) {
        return transformWithDebugSession(xmlContent, xsltContent, null, parameters, outputFormat,
                session, null);
    }

    /**
     * Runs a transformation under an interactive {@link DebugSession} on the dedicated
     * {@linkplain #getDebugProcessor() debug processor}: the stylesheet is compiled with
     * tracing and line numbers (uncached), the session's trace listener can pause the run
     * at breakpoints, and {@code xsl:message} output is both collected into the result and
     * streamed to {@code liveMessages} as it happens (on the Saxon thread).
     *
     * @param xsltSystemId the stylesheet's location (a {@code file:} URI), or {@code null} for
     *                     an in-memory stylesheet. With a systemId Saxon resolves relative
     *                     {@code xsl:include}/{@code xsl:import} hrefs and reports a per-module
     *                     systemId for every traced instruction, so breakpoints can be matched
     *                     per file.
     * @param liveMessages optional sink for {@code xsl:message} output as it is produced
     */
    public XsltTransformationResult transformWithDebugSession(
            String xmlContent, String xsltContent, String xsltSystemId,
            Map<String, Object> parameters, OutputFormat outputFormat,
            DebugSession session,
            java.util.function.Consumer<XsltTransformationResult.TransformationMessage> liveMessages) {
        return transformWithDebugSession(xmlContent, null, xsltContent, xsltSystemId, parameters, outputFormat,
                session, liveMessages);
    }

    /**
     * Like {@link #transformWithDebugSession(String, String, String, Map, OutputFormat, DebugSession,
     * java.util.function.Consumer)}, with the input's {@code inputSystemId} (its {@code file:} URI,
     * or {@code null} for in-memory text): input nodes then report that document, so the debugger
     * can show the paused context node in the input file; relative {@code doc()} URIs resolve
     * against it.
     */
    public XsltTransformationResult transformWithDebugSession(
            String xmlContent, String inputSystemId, String xsltContent, String xsltSystemId,
            Map<String, Object> parameters, OutputFormat outputFormat,
            DebugSession session,
            java.util.function.Consumer<XsltTransformationResult.TransformationMessage> liveMessages) {
        return transformWithDebugSession(xmlContent, inputSystemId, xsltContent, xsltSystemId, parameters,
                outputFormat, session, liveMessages, null);
    }

    /**
     * Like {@link #transformWithDebugSession(String, String, String, String, Map, OutputFormat,
     * DebugSession, java.util.function.Consumer)}; {@code moduleTexts} supplies the text of an
     * included / imported module by its normalised file path ({@code null} = read the file), so
     * the debugger can run the editor's unsaved text of open modules.
     */
    public XsltTransformationResult transformWithDebugSession(
            String xmlContent, String inputSystemId, String xsltContent, String xsltSystemId,
            Map<String, Object> parameters, OutputFormat outputFormat,
            DebugSession session,
            java.util.function.Consumer<XsltTransformationResult.TransformationMessage> liveMessages,
            java.util.function.Function<String, String> moduleTexts) {

        if (session == null) {
            return transform(xmlContent, xsltContent, parameters, outputFormat);
        }

        // Streaming-mode guard
        if (xsltContent != null
                && (xsltContent.contains("xsl:stream") || xsltContent.contains("saxon:stream"))) {
            session.close();
            return XsltTransformationResult.error(
                    "Streaming mode (xsl:stream / saxon:stream) is incompatible with the debugger. "
                            + "Disable streaming or run without debug.");
        }

        long startTime = System.currentTimeMillis();
        XsltDebugTraceListener traceListener = new XsltDebugTraceListener(session);
        XsltDebugMessageListener messageListener = new XsltDebugMessageListener();
        messageListener.setSink(liveMessages);
        Processor processor = getDebugProcessor();

        try {
            TransformationContext context = new TransformationContext(xmlContent, xsltContent,
                    parameters, outputFormat);

            XsltExecutable executable;
            try {
                executable = compileForDebug(processor, xsltContent, xsltSystemId, messageListener, moduleTexts);
            } catch (StylesheetCompileException e) {
                return e.toResult();
            }

            XsltTransformer transformer = executable.load();
            configureTransformer(transformer, parameters, outputFormat, true);
            transformer.setTraceListener(traceListener);
            traceListener.setTraceSink(liveMessages);
            transformer.setMessageHandler(messageListener);
            transformer.setErrorReporter(error -> {
                messageListener.reportError(error);
                traceListener.onError(error); // "break on errors": pause with the failing state
            });
            recordDebugArtifacts(processor, transformer, session, xsltSystemId, inputSystemId);

            XdmNode sourceDoc;
            try {
                sourceDoc = parseXmlDocument(processor, xmlContent, inputSystemId);
            } catch (SaxonApiException e) {
                return inputFailure(e);
            }
            transformer.setInitialContextNode(sourceDoc);

            StringWriter outputWriter = new StringWriter();
            transformer.setDestination(processor.newSerializer(outputWriter));
            configureSerializer(transformer.getDestination(), outputFormat);
            // While paused the debugger can show what has been written so far.
            session.setOutputProbe(outputWriter::toString);
            session.setOutputLength(() -> outputWriter.getBuffer().length());

            TransformationProfile profile = new TransformationProfile();
            profile.startTransformation();
            try {
                transformer.transform();
            } catch (XsltDebugStopException stop) {
                logger.debug("Debug session stopped by user");
                profile.endTransformation();
                XsltTransformationResult stopped = XsltTransformationResult.error(
                        "Debug session stopped");
                stopped.setExecutionTime(System.currentTimeMillis() - startTime);
                stopped.setTransformationContext(context);
                return stopped;
            } catch (SaxonApiException e) {
                if (e.getCause() instanceof XsltDebugStopException) {
                    logger.debug("Debug session stopped by user (wrapped)");
                    XsltTransformationResult stopped = XsltTransformationResult.error(
                            "Debug session stopped");
                    stopped.setExecutionTime(System.currentTimeMillis() - startTime);
                    stopped.setTransformationContext(context);
                    return stopped;
                }
                throw e;
            }
            profile.endTransformation();
            profile.setOutputSize(outputWriter.toString().length());

            XsltTransformationResult result = XsltTransformationResult.success(
                    outputWriter.toString(), outputFormat, profile);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            result.setTransformationContext(context);
            result.setTemplateMatches(traceListener.getTemplateMatches());
            result.setTemplateProfile(traceListener.getTemplateProfile());
            result.setVariableValues(traceListener.getVariableValues());
            result.setCallStack(traceListener.getCallStack());
            result.setMessages(messageListener.getMessages());
            if (!messageListener.getWarnings().isEmpty()) {
                result.addAllWarnings(messageListener.getWarnings());
            }
            return result;

        } catch (SaxonApiException e) {
            logger.warn("XSLT transformation (debug) failed: {}", e.getMessage());
            return runtimeFailure(e);
        } catch (Exception e) {
            logger.error("Unexpected error during debug XSLT transformation", e);
            return XsltTransformationResult.error("Unexpected error: " + e.getMessage());
        } finally {
            session.setOutputProbe(null);
            session.setOutputLength(null);
            // Mark session done so the UI re-enables Run-Debug
            if (session.getState() != DebugSession.State.STOPPED) {
                session.close();
            }
        }
    }

    /**
     * Runs an XQuery under the interactive debugger: the query is compiled with tracing on the
     * debug processor (line numbers on), {@code session} gets every traced expression (FLWOR
     * {@code let} clauses, constructors, function calls) through an
     * {@link XsltDebugTraceListener}, so breakpoints, steps, variables, watches and "break on
     * errors" work as for XSLT. {@code fn:trace()} output goes to the messages.
     *
     * @param xmlContent    the context document, or {@code null} / blank for a query without one
     * @param inputSystemId the context document's {@code file:} URI, or {@code null}
     * @param querySystemId the query file's {@code file:} URI (breakpoints match it), or {@code null}
     */
    public XsltTransformationResult transformXQueryWithDebugSession(
            String xmlContent, String inputSystemId, String xqueryContent, String querySystemId,
            Map<String, Object> externalVariables, OutputFormat outputFormat, DebugSession session,
            java.util.function.Consumer<XsltTransformationResult.TransformationMessage> liveMessages) {
        long startTime = System.currentTimeMillis();
        XsltDebugTraceListener traceListener = new XsltDebugTraceListener(session);
        XsltDebugMessageListener messageListener = new XsltDebugMessageListener();
        messageListener.setSink(liveMessages);
        Processor processor = getDebugProcessor();
        try {
            XQueryCompiler compiler = processor.newXQueryCompiler();
            compiler.setCompileWithTracing(true);
            if (querySystemId != null && !querySystemId.isBlank()) {
                compiler.setBaseURI(java.net.URI.create(querySystemId));
            }
            List<XmlProcessingError> staticErrors = new ArrayList<>();
            compiler.setErrorReporter(error -> {
                if (!error.isWarning()) {
                    staticErrors.add(error);
                }
                messageListener.reportError(error);
            });
            XQueryExecutable executable;
            try {
                executable = compiler.compile(xqueryContent == null ? "" : xqueryContent);
            } catch (SaxonApiException e) {
                XmlProcessingError first = staticErrors.isEmpty() ? null : staticErrors.get(0);
                String where = first == null ? at(e.getLineNumber(), -1) : at(first);
                String code = first != null && first.getErrorCode() != null
                        ? first.getErrorCode().getLocalName() : localCode(e.getErrorCode());
                XsltTransformationResult failure = XsltTransformationResult.error("XQuery compilation failed" + where
                        + bracket(code) + ": " + (first != null ? first.getMessage() : e.getMessage()), code, null);
                failure.setMessages(messageListener.getMessages());
                return failure;
            }
            XQueryEvaluator evaluator = executable.load();
            if (externalVariables != null) {
                for (Map.Entry<String, Object> entry : externalVariables.entrySet()) {
                    evaluator.setExternalVariable(new QName(entry.getKey()), convertToXdmValue(entry.getValue()));
                }
            }
            if (xmlContent != null && !xmlContent.isBlank()) {
                try {
                    evaluator.setContextItem(parseXmlDocument(processor, xmlContent, inputSystemId));
                } catch (SaxonApiException e) {
                    return inputFailure(e);
                }
            }
            evaluator.setTraceListener(traceListener);
            traceListener.setTraceSink(liveMessages);
            evaluator.setErrorReporter(error -> {
                messageListener.reportError(error);
                traceListener.onError(error); // "break on errors": pause with the failing state
            });
            evaluator.setTraceFunctionDestination(new net.sf.saxon.lib.Logger() {
                @Override
                public void println(String message, int severity) {
                    if (liveMessages != null) {
                        liveMessages.accept(new XsltTransformationResult.TransformationMessage("INFO", message, -1, "trace()"));
                    }
                }
            });

            OutputFormat format = detectXQueryOutputFormat(xqueryContent, outputFormat);
            StringWriter outputWriter = new StringWriter();
            Serializer serializer = processor.newSerializer(outputWriter);
            configureSerializer(serializer, format);
            session.setOutputProbe(outputWriter::toString);
            session.setOutputLength(() -> outputWriter.getBuffer().length());

            TransformationProfile profile = new TransformationProfile();
            profile.startTransformation();
            try {
                evaluator.run(serializer);
            } catch (XsltDebugStopException stop) {
                return XsltTransformationResult.error("Debug session stopped");
            } catch (SaxonApiException e) {
                if (e.getCause() instanceof XsltDebugStopException) {
                    return XsltTransformationResult.error("Debug session stopped");
                }
                XsltTransformationResult failure = runtimeFailure(e);
                failure.setMessages(messageListener.getMessages());
                return failure;
            }
            profile.endTransformation();
            profile.setOutputSize(outputWriter.toString().length());
            XsltTransformationResult result = XsltTransformationResult.success(outputWriter.toString(), format, profile);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            result.setCallStack(traceListener.getCallStack());
            result.setMessages(messageListener.getMessages());
            return result;
        } catch (RuntimeException e) {
            if (e instanceof XsltDebugStopException || e.getCause() instanceof XsltDebugStopException) {
                return XsltTransformationResult.error("Debug session stopped");
            }
            logger.error("Unexpected error during debug XQuery run", e);
            return XsltTransformationResult.error("Unexpected error: " + e.getMessage());
        } finally {
            session.setOutputProbe(null);
            session.setOutputLength(null);
            if (session.getState() != DebugSession.State.STOPPED) {
                session.close();
            }
        }
    }

    /**
     * Lets the debugger show a run's secondary results and loaded documents: every
     * {@code xsl:result-document} is written to its own buffer (nothing goes to disk during a
     * debug run) and registered with the session; every document loaded through
     * {@code doc()} / {@code document()} and every {@code collection()} is recorded.
     */
    private static void recordDebugArtifacts(Processor processor, XsltTransformer transformer, DebugSession session,
                                             String xsltSystemId, String inputSystemId) {
        String currentBase = transformer.getUnderlyingController().getBaseOutputURI();
        if (currentBase == null || currentBase.isEmpty()) {
            // Relative result-document hrefs resolve against the input (or the stylesheet).
            transformer.setBaseOutputURI(inputSystemId != null && !inputSystemId.isBlank() ? inputSystemId
                    : xsltSystemId != null && !xsltSystemId.isBlank() ? xsltSystemId : "file:///debug-output/");
        }
        transformer.setResultDocumentHandler(uri -> {
            StringWriter writer = new StringWriter();
            session.addResultDocument(uri.toString(), writer::toString);
            return processor.newSerializer(writer);
        });
        // Delegate to the secured resolver (remote references stay blocked, see configureResourceAccess).
        net.sf.saxon.lib.ResourceResolver controllerResolver = transformer.getUnderlyingController().getResourceResolver();
        net.sf.saxon.lib.ResourceResolver resolver = controllerResolver != null ? controllerResolver
                : processor.getUnderlyingConfiguration().getResourceResolver();
        String stylesheet = org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(xsltSystemId);
        transformer.setResourceResolver(request -> {
            if (request.uri != null && !org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(request.uri).equals(stylesheet)) {
                session.recordLoadedDocument(request.uri);
            }
            return resolver == null ? null : resolver.resolve(request);
        });
        var controller = transformer.getUnderlyingController();
        net.sf.saxon.lib.CollectionFinder finder = controller.getCollectionFinder();
        if (finder != null) {
            controller.setCollectionFinder((context, uri) -> {
                session.recordLoadedDocument("collection(" + (uri == null ? "" : uri) + ")");
                return finder.findCollection(context, uri);
            });
        }
    }

    /**
     * Compiles a stylesheet for an interactive debug run: tracing on, line numbers on (the
     * debug processor's configuration), systemId set so includes resolve and every module
     * reports its own location. Never cached — each debug run reflects the file as it is now.
     */
    private XsltExecutable compileForDebug(Processor processor, String xsltContent, String xsltSystemId,
                                           XsltDebugMessageListener messageListener,
                                           java.util.function.Function<String, String> moduleTexts)
            throws StylesheetCompileException {
        XsltCompiler compiler = processor.newXsltCompiler();
        compiler.setXsltLanguageVersion("3.0");
        if (moduleTexts != null) {
            // xsl:include / xsl:import of a module that is open in the editor: its (unsaved) text.
            net.sf.saxon.lib.ResourceResolver fallback = compiler.getResourceResolver();
            compiler.setResourceResolver(request -> {
                String text = request.uri == null ? null
                        : moduleTexts.apply(org.fxt.freexmltoolkit.debugger.SourceLocations.normalize(request.uri));
                if (text != null) {
                    StreamSource module = new StreamSource(new StringReader(text));
                    module.setSystemId(request.uri);
                    return module;
                }
                return fallback == null ? null : fallback.resolve(request);
            });
        }
        List<XmlProcessingError> staticErrors = new ArrayList<>();
        compiler.setErrorReporter(collectStaticErrors(staticErrors, messageListener));
        compiler.setCompileWithTracing(true);

        StreamSource source = new StreamSource(new StringReader(xsltContent));
        if (xsltSystemId != null && !xsltSystemId.isBlank()) {
            source.setSystemId(xsltSystemId);
        }
        try {
            return compiler.compile(source);
        } catch (SaxonApiException e) {
            StylesheetCompileException failure = compileFailure(staticErrors, e);
            logger.warn("Failed to compile XSLT stylesheet for debugging: {}", failure.getMessage());
            throw failure;
        }
    }

    /**
     * An error reporter that collects the static errors (de-duplicated by location, code and
     * message — Saxon may report the same error more than once) and optionally forwards
     * compile-time warnings to a message listener.
     */
    private static net.sf.saxon.lib.ErrorReporter collectStaticErrors(List<XmlProcessingError> staticErrors,
                                                                      XsltDebugMessageListener warnings) {
        java.util.Set<String> seenErrors = new java.util.HashSet<>();
        return error -> {
            if (error.isWarning()) {
                if (warnings != null) {
                    warnings.reportError(error);
                }
            } else if (seenErrors.add(at(error) + bracket(localCode(error.getErrorCode()))
                    + error.getMessage())) {
                staticErrors.add(error);
            }
        };
    }

    /**
     * Transform with live preview capabilities (for interactive development)
     */
    public XsltTransformationResult liveTransform(String xmlContent, String xsltContent,
                                                  Map<String, Object> parameters, OutputFormat outputFormat,
                                                  boolean enableLiveDebugging) {

        // Enable debugging for live transformation
        boolean originalDebugging = this.enableDebugging;
        this.enableDebugging = enableLiveDebugging;

        try {
            XsltTransformationResult result = transform(xmlContent, xsltContent, parameters, outputFormat);

            // Add live debugging information if enabled
            if (enableLiveDebugging && result.isSuccess()) {
                enhanceResultWithDebuggingInfo(result);
            }

            return result;

        } finally {
            this.enableDebugging = originalDebugging;
        }
    }

    // ========== XQuery Transformation Methods ==========

    /**
     * Execute XQuery against XML content with full profiling.
     * Supports XQuery 3.1 features via Saxon.
     *
     * @param xmlContent   The XML source document (can be null for XQuery that doesn't require input)
     * @param xqueryContent The XQuery script to execute
     * @param externalVariables External variables to pass to the XQuery
     * @param outputFormat The desired output format
     * @return The transformation result with output and profiling data
     */
    public XsltTransformationResult transformXQuery(String xmlContent, String xqueryContent,
                                                     Map<String, Object> externalVariables, OutputFormat outputFormat) {
        long startTime = System.currentTimeMillis();

        try {
            logger.debug("Starting XQuery transformation with {} output format", outputFormat);

            // Compile XQuery
            XQueryCompiler xqueryCompiler = saxonProcessor.newXQueryCompiler();

            // Detect output method from XQuery declare option statements
            OutputFormat effectiveFormat = detectXQueryOutputFormat(xqueryContent, outputFormat);

            String cacheKey = "xquery_" + xqueryContent.hashCode();
            XQueryExecutable executable = compiledXQueries.get(cacheKey);

            if (executable == null) {
                logger.debug("Compiling XQuery script");
                executable = xqueryCompiler.compile(xqueryContent);
                compiledXQueries.put(cacheKey, executable);
                cleanupXQueryCache();
            } else {
                logger.debug("Using cached XQuery executable");
            }

            // Create evaluator
            XQueryEvaluator evaluator = executable.load();

            // Set external variables
            if (externalVariables != null && !externalVariables.isEmpty()) {
                for (Map.Entry<String, Object> entry : externalVariables.entrySet()) {
                    QName varName = new QName(entry.getKey());
                    XdmValue varValue = convertToXdmValue(entry.getValue());
                    evaluator.setExternalVariable(varName, varValue);
                }
            }

            // Set context item (input XML) if provided. A non-well-formed context
            // document must not abort a query that doesn't depend on it (e.g. a
            // collection()/doc()-based query): degrade gracefully and run without a
            // context item, logging a warning instead.
            if (xmlContent != null && !xmlContent.trim().isEmpty()) {
                try {
                    XdmNode sourceDoc = parseXmlDocument(xmlContent);
                    evaluator.setContextItem(sourceDoc);
                } catch (SaxonApiException contextEx) {
                    logger.warn("XQuery context document is not well-formed; running the query without a "
                            + "context item. Queries that rely on collection()/doc() are unaffected. Cause: {}",
                            contextEx.getMessage());
                }
            }

            // Execute XQuery and serialize result
            StringWriter outputWriter = new StringWriter();
            Serializer serializer = saxonProcessor.newSerializer(outputWriter);
            configureSerializer(serializer, effectiveFormat);

            // Transform with profiling
            TransformationProfile profile = new TransformationProfile();
            profile.startTransformation();

            evaluator.run(serializer);

            profile.endTransformation();
            profile.setOutputSize(outputWriter.toString().length());

            // Create successful result
            XsltTransformationResult result = XsltTransformationResult.success(
                    outputWriter.toString(), effectiveFormat, profile);

            result.setExecutionTime(System.currentTimeMillis() - startTime);

            logger.debug("XQuery transformation completed in {}ms, output size: {} characters",
                    result.getExecutionTime(), result.getOutputContent().length());

            return result;

        } catch (SaxonApiException e) {
            logger.error("XQuery transformation failed: {}", e.getMessage(), e);
            return XsltTransformationResult.error("XQuery execution failed: " + e.getMessage(),
                    localCode(e.getErrorCode()), null);

        } catch (Exception e) {
            logger.error("Unexpected error during XQuery transformation", e);
            return XsltTransformationResult.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Quick XQuery transformation with default settings
     */
    public XsltTransformationResult quickXQueryTransform(String xmlContent, String xqueryContent) {
        return transformXQuery(xmlContent, xqueryContent, new HashMap<>(), OutputFormat.XML);
    }

    /**
     * Validate XQuery syntax without executing it.
     *
     * @param xqueryContent The XQuery script to validate
     * @return Validation result message
     */
    public String validateXQuery(String xqueryContent) {
        try {
            XQueryCompiler xqueryCompiler = saxonProcessor.newXQueryCompiler();
            xqueryCompiler.compile(xqueryContent);
            return "XQuery is valid and compiles successfully.";
        } catch (SaxonApiException e) {
            return "XQuery validation failed: " + e.getMessage();
        }
    }

    /**
     * Detect output format from XQuery declare option statements.
     * Looks for patterns like: declare option output:method "html";
     */
    private OutputFormat detectXQueryOutputFormat(String xqueryContent, OutputFormat defaultFormat) {
        if (xqueryContent == null) {
            return defaultFormat;
        }

        // Pattern to match: declare option output:method "html/xml/text/json";
        Pattern methodPattern = Pattern.compile(
                "declare\\s+option\\s+output:method\\s+[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = methodPattern.matcher(xqueryContent);
        if (matcher.find()) {
            String method = matcher.group(1).toLowerCase();
            return switch (method) {
                case "html" -> OutputFormat.HTML;
                case "xhtml" -> OutputFormat.XHTML;
                case "text" -> OutputFormat.TEXT;
                case "json" -> OutputFormat.JSON;
                default -> OutputFormat.XML;
            };
        }

        return defaultFormat;
    }

    /**
     * Cleanup XQuery cache when it exceeds the limit
     */
    private void cleanupXQueryCache() {
        if (compiledXQueries.size() > 100) {
            Iterator<String> iterator = compiledXQueries.keySet().iterator();
            for (int i = 0; i < 20 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
            logger.debug("XQuery cache cleanup performed");
        }
    }

    // ========== Batch XQuery Transformation ==========

    /**
     * Execute XQuery against multiple XML files using Saxon's collection() function.
     * The XQuery can use collection() to access all provided files.
     *
     * @param xmlFiles          List of XML files to process
     * @param xqueryContent     The XQuery script (can use collection() to access files)
     * @param externalVariables External variables to pass to XQuery
     * @param outputFormat      Desired output format
     * @return BatchTransformationResult with combined and per-file results
     */
    public BatchTransformationResult transformXQueryBatch(
            List<java.io.File> xmlFiles,
            String xqueryContent,
            Map<String, Object> externalVariables,
            OutputFormat outputFormat) {

        long startTime = System.currentTimeMillis();
        BatchTransformationResult result = new BatchTransformationResult();
        result.setTotalFiles(xmlFiles.size());
        result.setOutputFormat(outputFormat);

        if (xmlFiles == null || xmlFiles.isEmpty()) {
            result.setSuccess(false);
            result.setErrorMessage("No XML files provided for batch processing");
            return result;
        }

        if (xqueryContent == null || xqueryContent.isBlank()) {
            result.setSuccess(false);
            result.setErrorMessage("XQuery content is empty");
            return result;
        }

        try {
            // Detect output format from XQuery declarations
            OutputFormat effectiveFormat = detectXQueryOutputFormat(xqueryContent, outputFormat);

            // Configure the collection finder to provide our files
            XmlFileCollectionResolver collectionResolver = new XmlFileCollectionResolver(xmlFiles);

            // Set the collection finder on the processor's configuration
            // This is required for collection() to work in XQuery
            saxonProcessor.getUnderlyingConfiguration().setCollectionFinder(collectionResolver);

            // Also set the default collection URI
            saxonProcessor.getUnderlyingConfiguration().setDefaultCollection(
                    XmlFileCollectionResolver.DEFAULT_COLLECTION_URI);

            // Create XQuery compiler
            XQueryCompiler xqueryCompiler = saxonProcessor.newXQueryCompiler();

            // Set base URI for resolving relative paths
            if (!xmlFiles.isEmpty()) {
                java.io.File firstFile = xmlFiles.get(0);
                xqueryCompiler.setBaseURI(firstFile.getParentFile().toURI());
            }

            // Compile the XQuery
            XQueryExecutable executable = xqueryCompiler.compile(xqueryContent);
            XQueryEvaluator evaluator = executable.load();

            // Set external variables
            if (externalVariables != null) {
                for (Map.Entry<String, Object> entry : externalVariables.entrySet()) {
                    evaluator.setExternalVariable(
                            new net.sf.saxon.s9api.QName(entry.getKey()),
                            new XdmAtomicValue(entry.getValue().toString()));
                }
            }

            // Execute the XQuery and get the result
            XdmValue xdmResult = evaluator.evaluate();

            // Serialize the result
            java.io.StringWriter output = new java.io.StringWriter();
            Serializer serializer = saxonProcessor.newSerializer(output);
            serializer.setOutputProperty(Serializer.Property.METHOD, effectiveFormat.getSaxonMethod());
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION,
                    effectiveFormat == OutputFormat.XML ? "no" : "yes");

            if (effectiveFormat == OutputFormat.HTML || effectiveFormat == OutputFormat.XHTML) {
                serializer.setOutputProperty(Serializer.Property.HTML_VERSION, "5");
            }

            // Serialize each item in the result
            serializer.serializeXdmValue(xdmResult);

            String combinedOutput = output.toString();
            result.setCombinedOutput(combinedOutput);
            result.setSuccess(true);
            result.setSuccessCount(xmlFiles.size());

            logger.info("Batch XQuery transformation completed: {} files processed", xmlFiles.size());

        } catch (SaxonApiException e) {
            logger.error("Batch XQuery transformation failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage("XQuery execution failed: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Unexpected error during batch XQuery transformation", e);
            result.setSuccess(false);
            result.setErrorMessage("Unexpected error: " + e.getMessage());
        }

        result.setTotalExecutionTime(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * Execute XQuery against each file individually and collect per-file results.
     * Useful when you want separate output for each file.
     *
     * @param xmlFiles          List of XML files to process
     * @param xqueryContent     The XQuery script (uses context item, not collection())
     * @param externalVariables External variables to pass to XQuery
     * @param outputFormat      Desired output format
     * @return BatchTransformationResult with per-file results
     */
    public BatchTransformationResult transformXQueryPerFile(
            List<java.io.File> xmlFiles,
            String xqueryContent,
            Map<String, Object> externalVariables,
            OutputFormat outputFormat) {

        long startTime = System.currentTimeMillis();
        BatchTransformationResult result = new BatchTransformationResult();
        result.setTotalFiles(xmlFiles.size());
        result.setOutputFormat(outputFormat);

        if (xmlFiles == null || xmlFiles.isEmpty()) {
            result.setSuccess(false);
            result.setErrorMessage("No XML files provided for batch processing");
            return result;
        }

        StringBuilder combinedOutput = new StringBuilder();
        combinedOutput.append("<batch-results>\n");

        for (java.io.File file : xmlFiles) {
            long fileStartTime = System.currentTimeMillis();

            try {
                String xmlContent = java.nio.file.Files.readString(file.toPath());
                XsltTransformationResult fileResult = transformXQuery(
                        xmlContent, xqueryContent, externalVariables, outputFormat);

                long fileTime = System.currentTimeMillis() - fileStartTime;

                if (fileResult.isSuccess()) {
                    result.addFileResult(file, fileResult.getOutputContent(), fileTime);
                    combinedOutput.append("  <file name=\"").append(file.getName()).append("\">\n");
                    combinedOutput.append("    ").append(fileResult.getOutputContent().replace("\n", "\n    ")).append("\n");
                    combinedOutput.append("  </file>\n");
                } else {
                    result.addFileError(file, fileResult.getErrorMessage());
                    combinedOutput.append("  <file name=\"").append(file.getName()).append("\" error=\"true\">\n");
                    combinedOutput.append("    <error>").append(escapeXml(fileResult.getErrorMessage())).append("</error>\n");
                    combinedOutput.append("  </file>\n");
                }

            } catch (java.io.IOException e) {
                result.addFileError(file, "Failed to read file: " + e.getMessage());
                combinedOutput.append("  <file name=\"").append(file.getName()).append("\" error=\"true\">\n");
                combinedOutput.append("    <error>Failed to read: ").append(escapeXml(e.getMessage())).append("</error>\n");
                combinedOutput.append("  </file>\n");
            }
        }

        combinedOutput.append("</batch-results>");
        result.setCombinedOutput(combinedOutput.toString());
        result.setSuccess(result.getErrorCount() == 0);
        result.setTotalExecutionTime(System.currentTimeMillis() - startTime);

        logger.info("Per-file XQuery transformation completed: {} success, {} errors",
                result.getSuccessCount(), result.getErrorCount());

        return result;
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ========== Stylesheet Compilation and Caching ==========

    private XsltExecutable compileStylesheet(String xsltContent, TransformationContext context)
            throws StylesheetCompileException {
        String cacheKey = context.getStylesheetCacheKey();

        // Use separate cache key for debug compilations (tracing enabled)
        if (enableDebugging) {
            cacheKey = cacheKey + "_debug";
        }

        // Check cache first
        XsltExecutable cached = compiledStylesheets.get(cacheKey);
        if (cached != null) {
            logger.debug("Using cached XSLT executable for stylesheet (debug={})", enableDebugging);
            return cached;
        }

        logger.debug("Compiling XSLT stylesheet (debug={})", enableDebugging);

        // Create a new compiler instance for this compilation
        XsltCompiler compiler = saxonProcessor.newXsltCompiler();
        compiler.setXsltLanguageVersion("3.0");
        // Collect the static errors: the SaxonApiException thrown by compile() only says
        // "Errors were reported during stylesheet compilation" — the reasons and locations
        // are delivered to the error reporter.
        List<XmlProcessingError> staticErrors = new ArrayList<>();
        compiler.setErrorReporter(collectStaticErrors(staticErrors, null));

        // CRITICAL: Enable tracing when debugging is enabled
        // This is required for TraceListener to receive events
        if (enableDebugging) {
            compiler.setCompileWithTracing(true);
            logger.debug("Tracing enabled for XSLT compilation");
        }

        StreamSource source = new StreamSource(new StringReader(xsltContent));
        if (context.getXsltSystemId() != null && !context.getXsltSystemId().isBlank()) {
            source.setSystemId(context.getXsltSystemId());
        }
        XsltExecutable executable;
        try {
            executable = compiler.compile(source);
        } catch (SaxonApiException e) {
            StylesheetCompileException failure = compileFailure(staticErrors, e);
            logger.warn("Failed to compile XSLT stylesheet: {}", failure.getMessage());
            throw failure;
        }

        // Cache compiled stylesheet
        compiledStylesheets.put(cacheKey, executable);

        // Cleanup old cache entries
        cleanupCache();

        logger.debug("XSLT stylesheet compiled and cached successfully");
        return executable;

    }

    // ========== Failure reporting ==========

    /** Maximum number of static errors listed in a compile-failure message. */
    static final int MAX_REPORTED_COMPILE_ERRORS = 5;

    /** A stylesheet had static errors; carries the formatted, user-facing failure. */
    static final class StylesheetCompileException extends Exception {
        private final String code;

        StylesheetCompileException(String message, String code) {
            super(message);
            this.code = code;
        }

        XsltTransformationResult toResult() {
            return XsltTransformationResult.error(getMessage(), code, XsltTransformationResult.PHASE_COMPILE);
        }
    }

    /**
     * Builds the compile failure from the reported static errors (reason, line/column and
     * error code of each), falling back to the exception when nothing was reported.
     */
    static StylesheetCompileException compileFailure(List<XmlProcessingError> errors, SaxonApiException e) {
        if (errors.isEmpty()) {
            String code = localCode(e.getErrorCode());
            return new StylesheetCompileException("XSLT compile error" + at(e.getLineNumber(), -1)
                    + bracket(code) + ": " + e.getMessage(), code);
        }
        XmlProcessingError first = errors.getFirst();
        String code = localCode(first.getErrorCode());
        if (errors.size() == 1) {
            return new StylesheetCompileException("XSLT compile error" + at(first) + bracket(code)
                    + ": " + first.getMessage(), code);
        }
        StringBuilder sb = new StringBuilder("XSLT stylesheet has ").append(errors.size())
                .append(" compile errors:");
        errors.stream().limit(MAX_REPORTED_COMPILE_ERRORS).forEach(error -> sb.append("\n  -")
                .append(at(error)).append(bracket(localCode(error.getErrorCode())))
                .append(": ").append(error.getMessage()));
        if (errors.size() > MAX_REPORTED_COMPILE_ERRORS) {
            sb.append("\n  … and ").append(errors.size() - MAX_REPORTED_COMPILE_ERRORS).append(" more");
        }
        return new StylesheetCompileException(sb.toString(), code);
    }

    /** The input document is not well-formed. */
    private static XsltTransformationResult inputFailure(SaxonApiException e) {
        String code = localCode(e.getErrorCode());
        return XsltTransformationResult.error("Input XML is not well-formed" + bracket(code) + ": "
                + e.getMessage(), code, XsltTransformationResult.PHASE_INPUT);
    }

    /** The transformation raised a dynamic error. */
    private static XsltTransformationResult runtimeFailure(SaxonApiException e) {
        String code = localCode(e.getErrorCode());
        return XsltTransformationResult.error("Transformation failed" + at(e.getLineNumber(), -1)
                + bracket(code) + ": " + e.getMessage(), code, XsltTransformationResult.PHASE_RUNTIME);
    }

    private static String at(XmlProcessingError error) {
        var location = error.getLocation();
        return location == null ? "" : at(location.getLineNumber(), location.getColumnNumber());
    }

    private static String at(int line, int column) {
        if (line <= 0) {
            return "";
        }
        return column > 0 ? " at line " + line + ", column " + column : " at line " + line;
    }

    private static String bracket(String code) {
        return code == null ? "" : " [" + code + "]";
    }

    private static String localCode(QName code) {
        return code == null ? null : code.getLocalName();
    }

    private XdmNode parseXmlDocument(String xmlContent) throws SaxonApiException {
        return parseXmlDocument(saxonProcessor, xmlContent);
    }

    private static XdmNode parseXmlDocument(Processor processor, String xmlContent) throws SaxonApiException {
        return parseXmlDocument(processor, xmlContent, null);
    }

    private static XdmNode parseXmlDocument(Processor processor, String xmlContent, String baseUri)
            throws SaxonApiException {
        DocumentBuilder builder = processor.newDocumentBuilder();
        StreamSource source = new StreamSource(new StringReader(sanitizeXmlPrologue(xmlContent)));
        if (baseUri != null && !baseUri.isBlank()) {
            source.setSystemId(baseUri);
        }
        return builder.build(source);
    }

    /**
     * Removes a leading UTF-8 BOM and any whitespace that precedes an XML
     * declaration, so that a document whose {@code <?xml ?>} declaration is not at
     * the very first byte still parses. A blank line or BOM in front of the
     * declaration is a common copy/paste or editor artifact and would otherwise
     * trigger {@code SXXP0003 — processing instruction target "xml" is not allowed}.
     * <p>
     * Whitespace before a root element that has <em>no</em> declaration is legal
     * prolog content and is deliberately left untouched.
     *
     * @param xmlContent the raw XML text (may be {@code null})
     * @return the sanitized text, or the original value for {@code null}/empty input
     */
    static String sanitizeXmlPrologue(String xmlContent) {
        if (xmlContent == null || xmlContent.isEmpty()) {
            return xmlContent;
        }
        String result = xmlContent;
        // Strip a leading UTF-8 BOM (U+FEFF).
        if (result.charAt(0) == '﻿') {
            result = result.substring(1);
        }
        // If an XML declaration follows leading whitespace, move it to the front.
        int i = 0;
        while (i < result.length() && Character.isWhitespace(result.charAt(i))) {
            i++;
        }
        if (i > 0 && result.regionMatches(true, i, "<?xml", 0, 5)) {
            result = result.substring(i);
        }
        return result;
    }

    private void configureTransformer(XsltTransformer transformer, Map<String, Object> parameters,
                                      OutputFormat outputFormat) throws SaxonApiException {
        configureTransformer(transformer, parameters, outputFormat, enableDebugging);
    }

    private void configureTransformer(XsltTransformer transformer, Map<String, Object> parameters,
                                      OutputFormat outputFormat, boolean debug) throws SaxonApiException {

        // Set parameters
        if (parameters != null && !parameters.isEmpty()) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                QName paramName = new QName(entry.getKey());
                XdmValue paramValue = convertToXdmValue(entry.getValue());
                transformer.setParameter(paramName, paramValue);
            }
        }

        // Set output method
        transformer.setParameter(new QName("method"), new XdmAtomicValue(outputFormat.getSaxonMethod()));

        // Configure for debugging if enabled
        if (debug) {
            // Add debugging configuration
            transformer.setParameter(new QName("debug-mode"), new XdmAtomicValue(true));
        }
    }

    private void configureSerializer(Destination destination, OutputFormat outputFormat) {
        if (destination instanceof Serializer serializer) {
            serializer.setOutputProperty(Serializer.Property.METHOD, outputFormat.getSaxonMethod());
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            serializer.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");

            switch (outputFormat) {
                case HTML, XHTML:
                    serializer.setOutputProperty(Serializer.Property.DOCTYPE_PUBLIC,
                            "-//W3C//DTD XHTML 1.0 Transitional//EN");
                    serializer.setOutputProperty(Serializer.Property.DOCTYPE_SYSTEM,
                            "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");
                    break;
                case JSON:
                    serializer.setOutputProperty(Serializer.Property.MEDIA_TYPE, "application/json");
                    break;
                case TEXT:
                    serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                    break;
                default:
                    break;
            }
        }
    }

    private XdmValue convertToXdmValue(Object value) {
        if (value == null) {
            return XdmEmptySequence.getInstance();
        } else if (value instanceof String) {
            return new XdmAtomicValue((String) value);
        } else if (value instanceof Number) {
            return new XdmAtomicValue(value.toString());
        } else if (value instanceof Boolean) {
            return new XdmAtomicValue((Boolean) value);
        } else {
            return new XdmAtomicValue(value.toString());
        }
    }

    // ========== Debugging and Profiling ==========

    private void enhanceResultWithDebuggingInfo(XsltTransformationResult result) {
        try {
            // Add template matching information
            List<TemplateMatchInfo> templateMatches = extractTemplateMatchingInfo(result);
            result.setTemplateMatches(templateMatches);

            // Add variable information
            Map<String, Object> variableValues = extractVariableValues(result);
            result.setVariableValues(variableValues);

            // Add call stack information
            List<String> callStack = extractCallStack(result);
            result.setCallStack(callStack);

        } catch (Exception e) {
            logger.warn("Failed to enhance result with debugging info", e);
        }
    }

    /**
     * Extracts template matching information from the transformation result.
     * Returns the template matches that were collected during transformation
     * via the {@link XsltDebugTraceListener}.
     *
     * @param result the transformation result containing collected debug data
     * @return list of template match information, or empty list if no data available
     */
    private List<TemplateMatchInfo> extractTemplateMatchingInfo(XsltTransformationResult result) {
        List<TemplateMatchInfo> matches = result.getTemplateMatches();
        return matches != null ? matches : new ArrayList<>();
    }

    /**
     * Extracts variable values from the transformation result.
     * Returns the variable values that were collected during transformation
     * via the {@link XsltDebugTraceListener}.
     *
     * @param result the transformation result containing collected debug data
     * @return map of variable names to values, or empty map if no data available
     */
    private Map<String, Object> extractVariableValues(XsltTransformationResult result) {
        Map<String, Object> variables = result.getVariableValues();
        return variables != null ? variables : new HashMap<>();
    }

    /**
     * Extracts the call stack from the transformation result.
     * Returns the call stack history that was collected during transformation
     * via the {@link XsltDebugTraceListener}.
     *
     * @param result the transformation result containing collected debug data
     * @return list of call stack entries, or empty list if no data available
     */
    private List<String> extractCallStack(XsltTransformationResult result) {
        List<String> callStack = result.getCallStack();
        return callStack != null ? callStack : new ArrayList<>();
    }

    // ========== Cache Management ==========

    private void cleanupCache() {
        if (compiledStylesheets.size() > 100) { // Limit cache size
            // Remove oldest entries (simplified LRU)
            Iterator<String> iterator = compiledStylesheets.keySet().iterator();
            for (int i = 0; i < 20 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
            logger.debug("XSLT cache cleanup performed");
        }
    }

    /**
     * Clear all cached stylesheets and XQueries
     */
    public void clearCache() {
        compiledStylesheets.clear();
        compiledXQueries.clear();
        profileCache.clear();
        logger.info("XSLT and XQuery cache cleared");
    }

    /**
     * Get transformation statistics
     */
    public TransformationStatistics getStatistics() {
        TransformationStatistics stats = new TransformationStatistics();
        stats.setCachedStylesheets(compiledStylesheets.size());
        stats.setCachedXQueries(compiledXQueries.size());
        stats.setProfiledTransformations(profileCache.size());
        stats.setXsltVersion("3.0");
        stats.setXqueryVersion("3.1");
        stats.setSaxonVersion(saxonProcessor.getSaxonProductVersion());
        return stats;
    }

    // ========== Configuration Methods ==========

    public void setProfilingEnabled(boolean enabled) {
        this.enableProfiling = enabled;
        logger.debug("XSLT profiling {}", enabled ? "enabled" : "disabled");
    }

    public void setDebuggingEnabled(boolean enabled) {
        this.enableDebugging = enabled;
        logger.debug("XSLT debugging {}", enabled ? "enabled" : "disabled");
    }

    public void setTransformationTimeout(long timeoutMs) {
        logger.debug("XSLT transformation timeout set to {}ms", timeoutMs);
    }

    public void shutdown() {
        executorService.shutdown();
        clearCache();
        logger.info("XSLT Transformation Engine shut down");
    }

    // ========== Inner Classes ==========

    /**
     * Transformation context for caching and debugging
     */
    public static class TransformationContext {
        private final String xmlContent;
        private final String xsltContent;
        private final Map<String, Object> parameters;
        private final OutputFormat outputFormat;
        private final LocalDateTime createdAt;
        /** The stylesheet's location (file URI) for include resolution, or {@code null}. */
        private final String xsltSystemId;
        /** The source document's base URI for relative {@code document()} calls, or {@code null}. */
        private final String xmlBaseUri;

        public TransformationContext(String xmlContent, String xsltContent,
                                     Map<String, Object> parameters, OutputFormat outputFormat) {
            this(xmlContent, xsltContent, parameters, outputFormat, null, null);
        }

        public TransformationContext(String xmlContent, String xsltContent,
                                     Map<String, Object> parameters, OutputFormat outputFormat,
                                     String xsltSystemId, String xmlBaseUri) {
            this.xmlContent = xmlContent;
            this.xsltContent = xsltContent;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
            this.outputFormat = outputFormat;
            this.createdAt = LocalDateTime.now();
            this.xsltSystemId = xsltSystemId;
            this.xmlBaseUri = xmlBaseUri;
        }

        public String getCacheKey() {
            return "transform_" + xsltContent.hashCode() + "_" +
                    parameters.hashCode() + "_" + outputFormat.name();
        }

        public String getStylesheetCacheKey() {
            // Same text at another location resolves includes differently — keep them apart.
            return "stylesheet_" + xsltContent.hashCode()
                    + (xsltSystemId == null ? "" : "_" + xsltSystemId.hashCode());
        }

        public String getXsltSystemId() {
            return xsltSystemId;
        }

        public String getXmlBaseUri() {
            return xmlBaseUri;
        }

        // Getters
        public String getXmlContent() {
            return xmlContent;
        }

        public String getXsltContent() {
            return xsltContent;
        }

        public Map<String, Object> getParameters() {
        return parameters == null ? java.util.Collections.emptyMap() : java.util.Collections.unmodifiableMap(parameters);
        }

        public OutputFormat getOutputFormat() {
            return outputFormat;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * Template matching information for debugging
     * @param pattern The template match pattern
     * @param name The template name (if any)
     * @param lineNumber The line number in the stylesheet
     * @param executionTime The execution time in nanoseconds
     */
    public record TemplateMatchInfo(String pattern, String name, int lineNumber, long executionTime) {
    }

    /**
     * Profile of one template rule, named template or function over a whole run
     * @param name       the match pattern ({@code match="…"}), template name or {@code name#arity}
     * @param kind       "Template rule", "Named template" or "Function"
     * @param lineNumber the declaration's line in the stylesheet
     * @param calls      number of invocations
     * @param totalNanos inclusive time of all invocations (nested calls count in their callers too)
     */
    public record TemplateProfileEntry(String name, String kind, int lineNumber, int calls, long totalNanos) {
    }

    /**
     * Transformation statistics
     */
    public static class TransformationStatistics {
        private int cachedStylesheets;
        private int cachedXQueries;
        private int profiledTransformations;
        private String xsltVersion;
        private String xqueryVersion;
        private String saxonVersion;

        // Getters and Setters
        public int getCachedStylesheets() {
            return cachedStylesheets;
        }

        public void setCachedStylesheets(int cachedStylesheets) {
            this.cachedStylesheets = cachedStylesheets;
        }

        public int getCachedXQueries() {
            return cachedXQueries;
        }

        public void setCachedXQueries(int cachedXQueries) {
            this.cachedXQueries = cachedXQueries;
        }

        public int getProfiledTransformations() {
            return profiledTransformations;
        }

        public void setProfiledTransformations(int profiledTransformations) {
            this.profiledTransformations = profiledTransformations;
        }

        public String getXsltVersion() {
            return xsltVersion;
        }

        public void setXsltVersion(String xsltVersion) {
            this.xsltVersion = xsltVersion;
        }

        public String getXqueryVersion() {
            return xqueryVersion;
        }

        public void setXqueryVersion(String xqueryVersion) {
            this.xqueryVersion = xqueryVersion;
        }

        public String getSaxonVersion() {
            return saxonVersion;
        }

        public void setSaxonVersion(String saxonVersion) {
            this.saxonVersion = saxonVersion;
        }
    }
}
