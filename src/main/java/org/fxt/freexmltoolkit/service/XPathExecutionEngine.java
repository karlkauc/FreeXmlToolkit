package org.fxt.freexmltoolkit.service;

import javafx.concurrent.Task;
import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.SnippetParameter;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance XPath/XQuery execution engine with Saxon integration.
 * Provides the core execution capabilities for the revolutionary Snippet Manager system.
 */
public class XPathExecutionEngine {

    private static final Logger logger = LogManager.getLogger(XPathExecutionEngine.class);

    // Singleton instance
    private static XPathExecutionEngine instance;

    // Saxon processor for XPath 3.1 and XQuery support
    private final Processor saxonProcessor;
    private final XPathCompiler xpathCompiler;
    private final XQueryCompiler xqueryCompiler;

    // Java XPath for basic operations
    private final javax.xml.xpath.XPath javaXPath;

    // Execution settings
    private boolean useSaxonForAdvanced = true;
    private long executionTimeoutMs = 30000; // 30 seconds
    private int maxResultSize = 10000; // Max number of result items
    private boolean enableProfiling = true;

    // Performance caching
    private final Map<String, XPathExpression> compiledExpressions = new ConcurrentHashMap<>();
    private final Map<String, XPathExecutable> compiledXPathExecutables = new ConcurrentHashMap<>();
    private final Map<String, XQueryExecutable> compiledXQueryExecutables = new ConcurrentHashMap<>();

    // Execution statistics
    private final Map<String, ExecutionStatistics> executionStats = new ConcurrentHashMap<>();

    // Background execution
    private final ExecutorService executorService;

    public XPathExecutionEngine() {
        // Initialize Saxon processor
        saxonProcessor = new Processor(false);
        xpathCompiler = saxonProcessor.newXPathCompiler();
        xqueryCompiler = saxonProcessor.newXQueryCompiler();

        // Initialize Java XPath
        XPathFactory xpathFactory = XPathFactory.newInstance();
        javaXPath = xpathFactory.newXPath();

        // Background executor
        executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("XPath-Execution-Thread");
            t.setDaemon(true);
            return t;
        });

        // Set up default namespaces
        setupDefaultNamespaces();

        logger.info("XPath Execution Engine initialized with Saxon {}", saxonProcessor.getSaxonProductVersion());
    }

    public static synchronized XPathExecutionEngine getInstance() {
        if (instance == null) {
            instance = new XPathExecutionEngine();
        }
        return instance;
    }

    private void setupDefaultNamespaces() {
        try {
            // Common XML namespaces
            xpathCompiler.declareNamespace("xml", "http://www.w3.org/XML/1998/namespace");
            xpathCompiler.declareNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xpathCompiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xpathCompiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");

            xqueryCompiler.declareNamespace("xml", "http://www.w3.org/XML/1998/namespace");
            xqueryCompiler.declareNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xqueryCompiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xqueryCompiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");

        } catch (Exception e) {
            logger.warn("Failed to set up default namespaces", e);
        }
    }

    // ========== Main Execution Methods ==========

    /**
     * Execute XPath/XQuery snippet synchronously
     */
    public XPathExecutionResult executeSnippet(XPathSnippet snippet, String xmlContent,
                                               Map<String, String> parameterValues) {
        if (snippet == null || xmlContent == null) {
            return XPathExecutionResult.error("Snippet or XML content is null");
        }

        long startTime = System.currentTimeMillis();
        XPathExecutionResult result;

        try {
            // Resolve parameters and variables
            String processedQuery = resolveSnippetParameters(snippet, parameterValues);

            // Apply namespaces
            applySnippetNamespaces(snippet);

            // Execute based on snippet type
            switch (snippet.getType()) {
                case XPATH:
                case XPATH_FUNCTION:
                    result = executeXPath(processedQuery, xmlContent, snippet);
                    break;
                case XQUERY:
                case XQUERY_MODULE:
                case FLWOR:
                    result = executeXQuery(processedQuery, xmlContent, snippet);
                    break;
                default:
                    result = executeXPath(processedQuery, xmlContent, snippet);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTime(executionTime);
            result.setSnippetId(snippet.getId());
            result.setExecutedAt(LocalDateTime.now());

            // Record execution statistics
            recordExecutionStatistics(snippet.getId(), executionTime, result.isSuccess());

            logger.debug("Executed snippet '{}' in {}ms", snippet.getName(), executionTime);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            result = XPathExecutionResult.error("Execution failed: " + e.getMessage());
            result.setExecutionTime(executionTime);
            result.setSnippetId(snippet.getId());

            logger.error("Failed to execute snippet: {}", snippet.getName(), e);
        }

        return result;
    }

    /**
     * Execute XPath/XQuery snippet asynchronously
     */
    public Task<XPathExecutionResult> executeSnippetAsync(XPathSnippet snippet, String xmlContent,
                                                          Map<String, String> parameterValues) {
        return new Task<XPathExecutionResult>() {
            @Override
            protected XPathExecutionResult call() throws Exception {
                updateMessage("Executing " + snippet.getName() + "...");
                updateProgress(0, 1);

                XPathExecutionResult result = executeSnippet(snippet, xmlContent, parameterValues);

                updateProgress(1, 1);
                updateMessage("Execution completed");

                return result;
            }
        };
    }

    // ========== XPath Execution ==========

    private XPathExecutionResult executeXPath(String xpathExpression, String xmlContent, XPathSnippet snippet) {
        try {
            // Parse XML document
            Document document = parseXmlDocument(xmlContent);

            if (useSaxonForAdvanced && requiresAdvancedFeatures(xpathExpression)) {
                return executeSaxonXPath(xpathExpression, document, snippet);
            } else {
                return executeJavaXPath(xpathExpression, document, snippet);
            }

        } catch (Exception e) {
            return XPathExecutionResult.error("XPath execution failed: " + e.getMessage());
        }
    }

    private XPathExecutionResult executeSaxonXPath(String xpathExpression, Document document, XPathSnippet snippet) {
        try {
            // Get or compile XPath
            String cacheKey = snippet.getId() + ":" + xpathExpression.hashCode();
            XPathExecutable executable = compiledXPathExecutables.get(cacheKey);

            if (executable == null) {
                executable = xpathCompiler.compile(xpathExpression);
                compiledXPathExecutables.put(cacheKey, executable);
            }

            // Execute XPath
            XPathSelector selector = executable.load();
            selector.setContextItem(saxonProcessor.newDocumentBuilder().wrap(new DOMSource(document)));

            XdmValue result = selector.evaluate();

            return convertSaxonResult(result, xpathExpression);

        } catch (SaxonApiException e) {
            return XPathExecutionResult.error("Saxon XPath error: " + e.getMessage());
        }
    }

    private XPathExecutionResult executeJavaXPath(String xpathExpression, Document document, XPathSnippet snippet) {
        try {
            // Get or compile XPath
            String cacheKey = snippet.getId() + ":" + xpathExpression.hashCode();
            XPathExpression compiledExpr = compiledExpressions.get(cacheKey);

            if (compiledExpr == null) {
                compiledExpr = javaXPath.compile(xpathExpression);
                compiledExpressions.put(cacheKey, compiledExpr);
            }

            // Execute XPath - try different return types
            Object result;
            XPathExecutionResult.ResultType resultType;

            try {
                // Try NODESET first (most common)
                result = compiledExpr.evaluate(document, XPathConstants.NODESET);
                resultType = XPathExecutionResult.ResultType.NODESET;
            } catch (Exception e) {
                try {
                    // Try STRING
                    result = compiledExpr.evaluate(document, XPathConstants.STRING);
                    resultType = XPathExecutionResult.ResultType.STRING;
                } catch (Exception e2) {
                    try {
                        // Try NUMBER
                        result = compiledExpr.evaluate(document, XPathConstants.NUMBER);
                        resultType = XPathExecutionResult.ResultType.NUMBER;
                    } catch (Exception e3) {
                        // Try BOOLEAN
                        result = compiledExpr.evaluate(document, XPathConstants.BOOLEAN);
                        resultType = XPathExecutionResult.ResultType.BOOLEAN;
                    }
                }
            }

            return convertJavaXPathResult(result, resultType, xpathExpression);

        } catch (Exception e) {
            return XPathExecutionResult.error("Java XPath error: " + e.getMessage());
        }
    }

    // ========== XQuery Execution ==========

    private XPathExecutionResult executeXQuery(String xqueryExpression, String xmlContent, XPathSnippet snippet) {
        try {
            // Parse XML document
            Document document = parseXmlDocument(xmlContent);

            return executeSaxonXQuery(xqueryExpression, document, snippet);

        } catch (Exception e) {
            return XPathExecutionResult.error("XQuery execution failed: " + e.getMessage());
        }
    }

    private XPathExecutionResult executeSaxonXQuery(String xqueryExpression, Document document, XPathSnippet snippet) {
        try {
            // Get or compile XQuery
            String cacheKey = snippet.getId() + ":" + xqueryExpression.hashCode();
            XQueryExecutable executable = compiledXQueryExecutables.get(cacheKey);

            if (executable == null) {
                executable = xqueryCompiler.compile(xqueryExpression);
                compiledXQueryExecutables.put(cacheKey, executable);
            }

            // Execute XQuery
            XQueryEvaluator evaluator = executable.load();
            evaluator.setContextItem(saxonProcessor.newDocumentBuilder().wrap(new DOMSource(document)));

            XdmValue result = evaluator.evaluate();

            return convertSaxonResult(result, xqueryExpression);

        } catch (SaxonApiException e) {
            return XPathExecutionResult.error("Saxon XQuery error: " + e.getMessage());
        }
    }

    // ========== Result Conversion ==========

    private XPathExecutionResult convertSaxonResult(XdmValue result, String query) {
        try {
            List<XPathExecutionResult.ResultItem> items = new ArrayList<>();

            for (XdmItem item : result) {
                XPathExecutionResult.ResultItem resultItem = new XPathExecutionResult.ResultItem();

                if (item instanceof XdmNode node) {
                    resultItem.setType(XPathExecutionResult.ResultType.NODE);
                    resultItem.setValue(node.toString());
                    resultItem.setNodeName(node.getNodeName() != null ? node.getNodeName().getLocalName() : "");
                    resultItem.setNodeType(node.getNodeKind().toString());
                } else if (item instanceof XdmAtomicValue atomic) {
                    resultItem.setType(getAtomicResultType(atomic));
                    resultItem.setValue(atomic.getStringValue());
                } else {
                    resultItem.setType(XPathExecutionResult.ResultType.OTHER);
                    resultItem.setValue(item.toString());
                }

                items.add(resultItem);

                // Limit result size for performance
                if (items.size() >= maxResultSize) {
                    break;
                }
            }

            XPathExecutionResult execResult = new XPathExecutionResult();
            execResult.setSuccess(true);
            execResult.setQuery(query);
            execResult.setResultItems(items);
            execResult.setResultCount(items.size());
            execResult.setTruncated(result.size() > maxResultSize);

            return execResult;

        } catch (Exception e) {
            return XPathExecutionResult.error("Result conversion failed: " + e.getMessage());
        }
    }

    private XPathExecutionResult convertJavaXPathResult(Object result, XPathExecutionResult.ResultType type, String query) {
        try {
            List<XPathExecutionResult.ResultItem> items = new ArrayList<>();

            if (type == XPathExecutionResult.ResultType.NODESET && result instanceof org.w3c.dom.NodeList nodeList) {

                for (int i = 0; i < Math.min(nodeList.getLength(), maxResultSize); i++) {
                    org.w3c.dom.Node node = nodeList.item(i);
                    XPathExecutionResult.ResultItem item = new XPathExecutionResult.ResultItem();
                    item.setType(XPathExecutionResult.ResultType.NODE);
                    item.setValue(nodeToString(node));
                    item.setNodeName(node.getNodeName());
                    item.setNodeType(getNodeTypeName(node.getNodeType()));
                    items.add(item);
                }
            } else {
                // Single value result
                XPathExecutionResult.ResultItem item = new XPathExecutionResult.ResultItem();
                item.setType(type);
                item.setValue(result.toString());
                items.add(item);
            }

            XPathExecutionResult execResult = new XPathExecutionResult();
            execResult.setSuccess(true);
            execResult.setQuery(query);
            execResult.setResultItems(items);
            execResult.setResultCount(items.size());

            return execResult;

        } catch (Exception e) {
            return XPathExecutionResult.error("Result conversion failed: " + e.getMessage());
        }
    }

    // ========== Helper Methods ==========

    private String resolveSnippetParameters(XPathSnippet snippet, Map<String, String> parameterValues) {
        String query = snippet.getQuery();

        if (parameterValues == null || parameterValues.isEmpty()) {
            parameterValues = new HashMap<>();
        }

        // Add default values for missing parameters
        for (SnippetParameter param : snippet.getParameters()) {
            if (!parameterValues.containsKey(param.getName()) && param.getDefaultValue() != null) {
                parameterValues.put(param.getName(), param.getDefaultValue());
            }
        }

        // Replace parameter placeholders
        for (Map.Entry<String, String> param : parameterValues.entrySet()) {
            String placeholder = "${" + param.getKey() + "}";
            query = query.replace(placeholder, param.getValue());
        }

        // Replace variables
        for (Map.Entry<String, String> var : snippet.getVariables().entrySet()) {
            String value = parameterValues.getOrDefault(var.getKey(), var.getValue());
            query = query.replace("$" + var.getKey(), value);
        }

        return query;
    }

    private void applySnippetNamespaces(XPathSnippet snippet) {
        try {
            if (snippet.hasNamespaces()) {
                for (Map.Entry<String, String> ns : snippet.getNamespaces().entrySet()) {
                    xpathCompiler.declareNamespace(ns.getKey(), ns.getValue());
                    xqueryCompiler.declareNamespace(ns.getKey(), ns.getValue());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to apply snippet namespaces", e);
        }
    }

    private Document parseXmlDocument(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        return builder.parse(new InputSource(new StringReader(xmlContent)));
    }

    private boolean requiresAdvancedFeatures(String expression) {
        // Check for XPath 2.0+ or 3.1 features
        String[] advancedFeatures = {
                "current-date(", "current-time(", "format-date(", "format-number(",
                "matches(", "replace(", "tokenize(", "distinct-values(",
                "max(", "min(", "avg(", "sum(", "for ", " return ", " if ", " then ", " else ",
                "every ", " satisfies ", "some ", "instance of", "cast as", "treat as"
        };

        String lowerExpr = expression.toLowerCase();
        for (String feature : advancedFeatures) {
            if (lowerExpr.contains(feature)) {
                return true;
            }
        }

        return false;
    }

    private XPathExecutionResult.ResultType getAtomicResultType(XdmAtomicValue atomic) {
        String typeName = atomic.getTypeName().getLocalName();
        switch (typeName) {
            case "string":
                return XPathExecutionResult.ResultType.STRING;
            case "boolean":
                return XPathExecutionResult.ResultType.BOOLEAN;
            case "integer":
            case "decimal":
            case "double":
            case "float":
                return XPathExecutionResult.ResultType.NUMBER;
            default:
                return XPathExecutionResult.ResultType.STRING;
        }
    }

    private String nodeToString(org.w3c.dom.Node node) {
        if (node.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
            return node.getNodeValue();
        } else if (node.getNodeType() == org.w3c.dom.Node.ATTRIBUTE_NODE) {
            return node.getNodeValue();
        } else {
            // For element nodes, return the text content or serialized form
            return node.getTextContent();
        }
    }

    private String getNodeTypeName(short nodeType) {
        switch (nodeType) {
            case org.w3c.dom.Node.ELEMENT_NODE:
                return "element";
            case org.w3c.dom.Node.ATTRIBUTE_NODE:
                return "attribute";
            case org.w3c.dom.Node.TEXT_NODE:
                return "text";
            case org.w3c.dom.Node.CDATA_SECTION_NODE:
                return "cdata";
            case org.w3c.dom.Node.COMMENT_NODE:
                return "comment";
            case org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE:
                return "processing-instruction";
            case org.w3c.dom.Node.DOCUMENT_NODE:
                return "document";
            default:
                return "unknown";
        }
    }

    private void recordExecutionStatistics(String snippetId, long executionTime, boolean success) {
        ExecutionStatistics stats = executionStats.computeIfAbsent(snippetId, k -> new ExecutionStatistics());
        stats.recordExecution(executionTime, success);
    }

    // ========== Performance and Caching ==========

    /**
     * Clear compilation cache
     */
    public void clearCache() {
        compiledExpressions.clear();
        compiledXPathExecutables.clear();
        compiledXQueryExecutables.clear();
        logger.debug("Cleared XPath/XQuery compilation cache");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Integer> getCacheStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("javaXPathExpressions", compiledExpressions.size());
        stats.put("saxonXPathExecutables", compiledXPathExecutables.size());
        stats.put("saxonXQueryExecutables", compiledXQueryExecutables.size());
        return stats;
    }

    /**
     * Get execution statistics for a snippet
     */
    public ExecutionStatistics getExecutionStatistics(String snippetId) {
        return executionStats.get(snippetId);
    }

    // ========== Configuration ==========

    public void setUseSaxonForAdvanced(boolean useSaxon) {
        this.useSaxonForAdvanced = useSaxon;
        logger.debug("Saxon for advanced features: {}", useSaxon ? "enabled" : "disabled");
    }

    public void setExecutionTimeout(long timeoutMs) {
        this.executionTimeoutMs = timeoutMs;
        logger.debug("Execution timeout set to {}ms", timeoutMs);
    }

    public void setMaxResultSize(int maxSize) {
        this.maxResultSize = maxSize;
        logger.debug("Max result size set to {}", maxSize);
    }

    public void setProfilingEnabled(boolean enabled) {
        this.enableProfiling = enabled;
        logger.debug("Execution profiling {}", enabled ? "enabled" : "disabled");
    }

    // ========== Cleanup ==========

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        clearCache();
        logger.debug("XPath Execution Engine shutdown");
    }

    // ========== Statistics Class ==========

    public static class ExecutionStatistics {
        private long totalExecutions = 0;
        private long successfulExecutions = 0;
        private long failedExecutions = 0;
        private long totalExecutionTime = 0;
        private long minExecutionTime = Long.MAX_VALUE;
        private long maxExecutionTime = Long.MIN_VALUE;
        private LocalDateTime lastExecution;

        public synchronized void recordExecution(long executionTime, boolean success) {
            totalExecutions++;
            totalExecutionTime += executionTime;

            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }

            minExecutionTime = Math.min(minExecutionTime, executionTime);
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            lastExecution = LocalDateTime.now();
        }

        public long getAverageExecutionTime() {
            return totalExecutions > 0 ? totalExecutionTime / totalExecutions : 0;
        }

        public double getSuccessRate() {
            return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions * 100.0 : 0.0;
        }

        // Getters
        public long getTotalExecutions() {
            return totalExecutions;
        }

        public long getSuccessfulExecutions() {
            return successfulExecutions;
        }

        public long getFailedExecutions() {
            return failedExecutions;
        }

        public long getTotalExecutionTime() {
            return totalExecutionTime;
        }

        public long getMinExecutionTime() {
            return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime;
        }

        public long getMaxExecutionTime() {
            return maxExecutionTime == Long.MIN_VALUE ? 0 : maxExecutionTime;
        }

        public LocalDateTime getLastExecution() {
            return lastExecution;
        }
    }
}