package org.fxt.freexmltoolkit.service;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced XSLT 3.0 and XQuery 3.1 Transformation Engine with Saxon integration.
 * Provides professional transformation development capabilities including:
 * - XSLT 3.0 support with streaming
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
    private final XsltTransformer transformer;

    // Transformation settings
    private boolean enableProfiling = true;
    private boolean enableDebugging = false;
    private long transformationTimeoutMs = 60000; // 60 seconds
    private final int maxOutputSize = 50 * 1024 * 1024; // 50MB

    // Caching and performance
    private final Map<String, XsltExecutable> compiledStylesheets = new ConcurrentHashMap<>();
    private final Map<String, XQueryExecutable> compiledXQueries = new ConcurrentHashMap<>();
    private final Map<String, TransformationProfile> profileCache = new ConcurrentHashMap<>();
    private final long cacheTimeout = 300000; // 5 minutes

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
        configureSecuritySettings();

        xsltCompiler = saxonProcessor.newXsltCompiler();
        transformer = null; // Will be created per transformation

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
    private void configureSecuritySettings() {
        Configuration config = saxonProcessor.getUnderlyingConfiguration();

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
        long startTime = System.currentTimeMillis();

        try {
            logger.debug("Starting XSLT transformation with {} output format", outputFormat);

            // Create transformation context
            TransformationContext context = new TransformationContext(xmlContent, xsltContent,
                    parameters, outputFormat);

            // Compile XSLT if needed
            XsltExecutable executable = compileStylesheet(xsltContent, context);
            if (executable == null) {
                return XsltTransformationResult.error("Failed to compile XSLT stylesheet");
            }

            // Create transformer
            XsltTransformer transformer = executable.load();
            configureTransformer(transformer, parameters, outputFormat);

            // Set up input source
            XdmNode sourceDoc = parseXmlDocument(xmlContent);
            transformer.setInitialContextNode(sourceDoc);

            // Execute transformation
            StringWriter outputWriter = new StringWriter();
            transformer.setDestination(saxonProcessor.newSerializer(outputWriter));

            // Configure output serialization
            configureSerializer(transformer.getDestination(), outputFormat);

            // Transform with profiling
            TransformationProfile profile = new TransformationProfile();
            profile.startTransformation();

            transformer.transform();

            profile.endTransformation();
            profile.setOutputSize(outputWriter.toString().length());

            // Create successful result
            XsltTransformationResult result = XsltTransformationResult.success(
                    outputWriter.toString(), outputFormat, profile);

            result.setExecutionTime(System.currentTimeMillis() - startTime);
            result.setTransformationContext(context);

            // Cache profile for analysis
            if (enableProfiling) {
                profileCache.put(context.getCacheKey(), profile);
            }

            logger.debug("XSLT transformation completed in {}ms, output size: {} characters",
                    result.getExecutionTime(), result.getOutputContent().length());

            return result;

        } catch (SaxonApiException e) {
            logger.error("XSLT transformation failed: {}", e.getMessage(), e);
            return XsltTransformationResult.error("Transformation failed: " + e.getMessage());

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

            // Set context item (input XML) if provided
            if (xmlContent != null && !xmlContent.trim().isEmpty()) {
                XdmNode sourceDoc = parseXmlDocument(xmlContent);
                evaluator.setContextItem(sourceDoc);
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
            return XsltTransformationResult.error("XQuery execution failed: " + e.getMessage());

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
        if (xqueryContent == null) return defaultFormat;

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
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ========== Stylesheet Compilation and Caching ==========

    private XsltExecutable compileStylesheet(String xsltContent, TransformationContext context) {
        String cacheKey = context.getStylesheetCacheKey();

        // Check cache first
        XsltExecutable cached = compiledStylesheets.get(cacheKey);
        if (cached != null) {
            logger.debug("Using cached XSLT executable for stylesheet");
            return cached;
        }

        try {
            logger.debug("Compiling XSLT stylesheet");

            StreamSource source = new StreamSource(new StringReader(xsltContent));
            XsltExecutable executable = xsltCompiler.compile(source);

            // Cache compiled stylesheet
            compiledStylesheets.put(cacheKey, executable);

            // Cleanup old cache entries
            cleanupCache();

            logger.debug("XSLT stylesheet compiled and cached successfully");
            return executable;

        } catch (SaxonApiException e) {
            logger.error("Failed to compile XSLT stylesheet: {}", e.getMessage(), e);
            return null;
        }
    }

    private XdmNode parseXmlDocument(String xmlContent) throws SaxonApiException {
        DocumentBuilder builder = saxonProcessor.newDocumentBuilder();
        StreamSource source = new StreamSource(new StringReader(xmlContent));
        return builder.build(source);
    }

    private void configureTransformer(XsltTransformer transformer, Map<String, Object> parameters,
                                      OutputFormat outputFormat) throws SaxonApiException {

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
        if (enableDebugging) {
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

    private List<TemplateMatchInfo> extractTemplateMatchingInfo(XsltTransformationResult result) {
        // TODO: Implement template matching extraction using Saxon debugging APIs
        List<TemplateMatchInfo> matches = new ArrayList<>();

        // Mock implementation for now
        matches.add(new TemplateMatchInfo("match=\"/\"", "Template root", 1, 50));
        matches.add(new TemplateMatchInfo("match=\"//item\"", "Item template", 15, 200));

        return matches;
    }

    private Map<String, Object> extractVariableValues(XsltTransformationResult result) {
        // TODO: Implement variable value extraction
        Map<String, Object> variables = new HashMap<>();
        variables.put("$title", "Example Document");
        variables.put("$count", 42);
        return variables;
    }

    private List<String> extractCallStack(XsltTransformationResult result) {
        // TODO: Implement call stack extraction
        List<String> callStack = new ArrayList<>();
        callStack.add("template match=\"/\" at line 10");
        callStack.add("call-template name=\"process-items\" at line 25");
        return callStack;
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
        this.transformationTimeoutMs = timeoutMs;
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

        public TransformationContext(String xmlContent, String xsltContent,
                                     Map<String, Object> parameters, OutputFormat outputFormat) {
            this.xmlContent = xmlContent;
            this.xsltContent = xsltContent;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
            this.outputFormat = outputFormat;
            this.createdAt = LocalDateTime.now();
        }

        public String getCacheKey() {
            return "transform_" + xsltContent.hashCode() + "_" +
                    parameters.hashCode() + "_" + outputFormat.name();
        }

        public String getStylesheetCacheKey() {
            return "stylesheet_" + xsltContent.hashCode();
        }

        // Getters
        public String getXmlContent() {
            return xmlContent;
        }

        public String getXsltContent() {
            return xsltContent;
        }

        public Map<String, Object> getParameters() {
            return parameters;
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
         */
        public record TemplateMatchInfo(String pattern, String name, int lineNumber, long executionTime) {
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