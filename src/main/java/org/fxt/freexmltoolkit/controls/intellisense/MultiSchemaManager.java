package org.fxt.freexmltoolkit.controls.intellisense;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.fxt.freexmltoolkit.service.xsd.adapters.IntelliSenseAdapter;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Multi-schema support manager for IntelliSense.
 * Handles multiple XSD schemas simultaneously with namespace awareness.
 */
public class MultiSchemaManager {

    private static final Logger logger = LogManager.getLogger(MultiSchemaManager.class);

    // Schema registry
    private final Map<String, SchemaInfo> schemaRegistry = new ConcurrentHashMap<>();

    // Namespace to schema mapping
    private final Map<String, String> namespaceToSchemaId = new ConcurrentHashMap<>();

    // Default/primary schema
    private volatile String primarySchemaId = null;

    // Background executor for schema loading
    private final ExecutorService backgroundExecutor;

    // Schema listeners
    private final List<SchemaListener> schemaListeners = new CopyOnWriteArrayList<>();

    // Performance components
    private final CompletionCache completionCache;

    // Unified XSD parsing service and IntelliSense adapter
    private final XsdParsingService xsdParsingService;
    private final IntelliSenseAdapter intelliSenseAdapter;

    public MultiSchemaManager() {
        this.backgroundExecutor = Executors.newFixedThreadPool(3, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "MultiSchema-" + counter.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        });
        this.completionCache = new CompletionCache();
        this.xsdParsingService = new XsdParsingServiceImpl();
        this.intelliSenseAdapter = new IntelliSenseAdapter();
    }

    /**
     * Gets the XSD parsing service used by this manager.
     *
     * @return the XSD parsing service
     */
    public XsdParsingService getXsdParsingService() {
        return xsdParsingService;
    }

    /**
     * Add schema from file path
     */
    public CompletableFuture<String> addSchema(String filePath) {
        return addSchema(filePath, null, false);
    }

    /**
     * Add schema from file path with namespace
     */
    public CompletableFuture<String> addSchema(String filePath, String targetNamespace) {
        return addSchema(filePath, targetNamespace, false);
    }

    /**
     * Add schema from file path with options
     */
    public CompletableFuture<String> addSchema(String filePath, String targetNamespace, boolean setAsPrimary) {
        String schemaId = generateSchemaId(filePath);

        return CompletableFuture.supplyAsync(() -> {
            try (AutoCloseable timer =
                         PerformanceProfiler.getInstance().startOperation("schema-loading")) {

                return loadSchemaFromFile(schemaId, filePath, targetNamespace, setAsPrimary);

            } catch (Exception e) {
                // Handle profiler exceptions gracefully
                return loadSchemaFromFile(schemaId, filePath, targetNamespace, setAsPrimary);
            }
        }, backgroundExecutor);
    }

    /**
     * Helper method to load schema from file
     */
    private String loadSchemaFromFile(String schemaId, String filePath, String targetNamespace, boolean setAsPrimary) {
        try {
            // Load and parse schema using unified XsdParsingService
            Path schemaPath = new File(filePath).toPath();
            ParsedSchema parsedSchema = xsdParsingService.parse(schemaPath, XsdParseOptions.defaults());

            // Extract IntelliSense info using adapter
            IntelliSenseAdapter.SchemaInfo extractedInfo = intelliSenseAdapter.toSchemaInfo(parsedSchema);

            // Create schema info
            SchemaInfo schemaInfo = new SchemaInfo(
                    schemaId,
                    filePath,
                    targetNamespace != null ? targetNamespace : extractedInfo.targetNamespace,
                    extractedInfo,
                    System.currentTimeMillis()
            );

            // Register schema
            schemaRegistry.put(schemaId, schemaInfo);

            // Register namespace mapping
            if (schemaInfo.targetNamespace != null) {
                namespaceToSchemaId.put(schemaInfo.targetNamespace, schemaId);
            }

            // Set as primary if requested or if it's the first schema
            if (setAsPrimary || primarySchemaId == null) {
                primarySchemaId = schemaId;
            }

            // Notify listeners
            notifySchemaAdded(schemaInfo);

            logger.debug("Loaded schema {} from file: {}", schemaId, filePath);
            return schemaId;

        } catch (Exception e) {
            logger.error("Failed to load schema from file: {}", filePath, e);
            throw new RuntimeException("Failed to load schema: " + filePath, e);
        }
    }

    /**
     * Add schema from URL
     */
    public CompletableFuture<String> addSchemaFromUrl(String url, String targetNamespace) {
        String schemaId = generateSchemaId(url);

        return CompletableFuture.supplyAsync(() -> {
            try (AutoCloseable timer =
                         PerformanceProfiler.getInstance().startOperation("schema-url-loading")) {

                return loadSchemaFromUrl(schemaId, url, targetNamespace);

            } catch (Exception e) {
                // Handle profiler exceptions gracefully
                return loadSchemaFromUrl(schemaId, url, targetNamespace);
            }
        }, backgroundExecutor);
    }

    /**
     * Helper method to load schema from URL
     */
    private String loadSchemaFromUrl(String schemaId, String url, String targetNamespace) {
        try {
            // Load and parse schema from URL using unified XsdParsingService
            ParsedSchema parsedSchema = xsdParsingService.parseFromUrl(url, XsdParseOptions.defaults());

            // Extract IntelliSense info using adapter
            IntelliSenseAdapter.SchemaInfo extractedInfo = intelliSenseAdapter.toSchemaInfo(parsedSchema);

            // Create schema info
            SchemaInfo schemaInfo = new SchemaInfo(
                    schemaId,
                    url,
                    targetNamespace != null ? targetNamespace : extractedInfo.targetNamespace,
                    extractedInfo,
                    System.currentTimeMillis()
            );

            // Register schema
            schemaRegistry.put(schemaId, schemaInfo);

            // Register namespace mapping
            if (schemaInfo.targetNamespace != null) {
                namespaceToSchemaId.put(schemaInfo.targetNamespace, schemaId);
            }

            // Set as primary if it's the first schema
            if (primarySchemaId == null) {
                primarySchemaId = schemaId;
            }

            // Notify listeners
            notifySchemaAdded(schemaInfo);

            logger.debug("Loaded schema {} from URL: {}", schemaId, url);
            return schemaId;

        } catch (Exception e) {
            logger.error("Failed to load schema from URL: {}", url, e);
            throw new RuntimeException("Failed to load schema from URL: " + url, e);
        }
    }

    /**
     * Remove schema
     */
    public boolean removeSchema(String schemaId) {
        SchemaInfo removed = schemaRegistry.remove(schemaId);
        if (removed != null) {
            // Remove namespace mapping
            if (removed.targetNamespace != null) {
                namespaceToSchemaId.remove(removed.targetNamespace);
            }

            // Update primary schema if needed
            if (schemaId.equals(primarySchemaId)) {
                primarySchemaId = schemaRegistry.isEmpty() ? null : schemaRegistry.keySet().iterator().next();
            }

            // Clear related cache entries
            completionCache.clearAll(); // TODO: Make this more selective

            // Notify listeners
            notifySchemaRemoved(removed);

            return true;
        }
        return false;
    }

    /**
     * Get completion items for context with multi-schema support
     */
    public List<CompletionItem> getCompletionItems(String context, String namespace) {
        // Try to get schema for specific namespace first
        String schemaId = namespace != null ? namespaceToSchemaId.get(namespace) : primarySchemaId;

        if (schemaId != null) {
            SchemaInfo schema = schemaRegistry.get(schemaId);
            if (schema != null) {
                return getCompletionItemsForSchema(context, schema);
            }
        }

        // Fallback: combine items from all schemas
        return getCombinedCompletionItems(context);
    }

    /**
     * Get completion items from specific schema
     */
    private List<CompletionItem> getCompletionItemsForSchema(String context, SchemaInfo schema) {
        String cacheKey = schema.schemaId + ":" + context;

        return completionCache.getCompletionItems(cacheKey, ctx -> {
            try (AutoCloseable timer =
                         PerformanceProfiler.getInstance().startOperation("schema-completion-generation")) {

                return generateCompletionItemsFromSchema(context, schema);
            } catch (Exception e) {
                // Handle profiler exceptions gracefully
                return generateCompletionItemsFromSchema(context, schema);
            }
        });
    }

    /**
     * Get combined completion items from all schemas
     */
    private List<CompletionItem> getCombinedCompletionItems(String context) {
        List<CompletionItem> allItems = new ArrayList<>();

        // Collect items from all schemas
        for (SchemaInfo schema : schemaRegistry.values()) {
            List<CompletionItem> schemaItems = getCompletionItemsForSchema(context, schema);

            // Add namespace prefix to avoid conflicts
            List<CompletionItem> prefixedItems = schemaItems.stream()
                    .map(item -> addNamespacePrefix(item, schema))
                    .collect(Collectors.toList());

            allItems.addAll(prefixedItems);
        }

        // Remove duplicates and sort by relevance
        return allItems.stream()
                .distinct()
                .sorted((a, b) -> {
                    int relevanceComp = Integer.compare(b.getRelevanceScore(), a.getRelevanceScore());
                    if (relevanceComp != 0) return relevanceComp;
                    return a.getLabel().compareTo(b.getLabel());
                })
                .collect(Collectors.toList());
    }

    /**
     * Generate completion items from schema
     */
    private List<CompletionItem> generateCompletionItemsFromSchema(String context, SchemaInfo schema) {
        List<CompletionItem> items = new ArrayList<>();

        IntelliSenseAdapter.SchemaInfo schemaInfo = schema.extractedInfo;

        // Generate element completion items
        for (IntelliSenseAdapter.ElementInfo element : schemaInfo.elements) {
            if (matchesContext(element.name, context)) {
                CompletionItem item = new CompletionItem.Builder(
                        element.name,
                        "<" + element.name + "></" + element.name + ">",
                        CompletionItemType.ELEMENT
                )
                        .description(element.documentation)
                        .dataType(element.type)
                        .namespace(schema.targetNamespace, schema.getNamespacePrefix())
                        .required(element.required)
                        .relevanceScore(calculateRelevance(element.name, context))
                        .build();

                items.add(item);
            }
        }

        // Generate attribute completion items
        for (IntelliSenseAdapter.AttributeInfo attribute : schemaInfo.attributes) {
            if (matchesContext(attribute.name, context)) {
                CompletionItem item = new CompletionItem.Builder(
                        attribute.name,
                        attribute.name + "=\"\"",
                        CompletionItemType.ATTRIBUTE
                )
                        .description(attribute.documentation)
                        .dataType(attribute.type)
                        .defaultValue(attribute.defaultValue)
                        .namespace(schema.targetNamespace, schema.getNamespacePrefix())
                        .required(attribute.required)
                        .relevanceScore(calculateRelevance(attribute.name, context))
                        .build();

                items.add(item);
            }
        }

        return items;
    }

    /**
     * Add namespace prefix to completion item
     */
    private CompletionItem addNamespacePrefix(CompletionItem item, SchemaInfo schema) {
        if (schema.targetNamespace == null || schema.getNamespacePrefix() == null) {
            return item;
        }

        String prefix = schema.getNamespacePrefix();
        String prefixedLabel = prefix + ":" + item.getLabel();
        String prefixedInsertText = item.getInsertText().replace(item.getLabel(), prefixedLabel);

        return new CompletionItem.Builder(prefixedLabel, prefixedInsertText, item.getType())
                .description(item.getDescription())
                .dataType(item.getDataType())
                .defaultValue(item.getDefaultValue())
                .namespace(item.getNamespace(), prefix)
                .required(item.isRequired())
                .relevanceScore(item.getRelevanceScore())
                .build();
    }

    /**
     * Check if element/attribute matches context
     */
    private boolean matchesContext(String name, String context) {
        if (context == null || context.isEmpty()) {
            return true;
        }

        String lowerName = name.toLowerCase();
        String lowerContext = context.toLowerCase();

        return lowerName.contains(lowerContext) ||
                lowerContext.contains(lowerName) ||
                FuzzySearch.calculateFuzzyScore(lowerContext, lowerName) > 0;
    }

    /**
     * Calculate relevance score
     */
    private int calculateRelevance(String name, String context) {
        if (context == null || context.isEmpty()) {
            return 100;
        }

        int fuzzyScore = FuzzySearch.calculateFuzzyScore(context.toLowerCase(), name.toLowerCase());
        return Math.max(50, fuzzyScore);
    }

    /**
     * Set primary schema
     */
    public void setPrimarySchema(String schemaId) {
        if (schemaRegistry.containsKey(schemaId)) {
            primarySchemaId = schemaId;
            notifyPrimarySchemaChanged(schemaId);
        }
    }

    /**
     * Get primary schema
     */
    public SchemaInfo getPrimarySchema() {
        return primarySchemaId != null ? schemaRegistry.get(primarySchemaId) : null;
    }

    /**
     * Get all registered schemas
     */
    public Map<String, SchemaInfo> getAllSchemas() {
        return new HashMap<>(schemaRegistry);
    }

    /**
     * Get schema by namespace
     */
    public SchemaInfo getSchemaByNamespace(String namespace) {
        String schemaId = namespaceToSchemaId.get(namespace);
        return schemaId != null ? schemaRegistry.get(schemaId) : null;
    }

    /**
     * Get all registered namespaces
     */
    public Set<String> getRegisteredNamespaces() {
        return new HashSet<>(namespaceToSchemaId.keySet());
    }

    /**
     * Generate unique schema ID
     */
    private String generateSchemaId(String source) {
        String base = source.contains("/") ? source.substring(source.lastIndexOf("/") + 1) : source;
        if (base.contains(".")) {
            base = base.substring(0, base.lastIndexOf("."));
        }

        String candidate = base;
        int counter = 1;
        while (schemaRegistry.containsKey(candidate)) {
            candidate = base + "_" + counter++;
        }

        return candidate;
    }

    /**
     * Add schema listener
     */
    public void addSchemaListener(SchemaListener listener) {
        schemaListeners.add(listener);
    }

    /**
     * Remove schema listener
     */
    public void removeSchemaListener(SchemaListener listener) {
        schemaListeners.remove(listener);
    }

    /**
     * Notify schema added
     */
    private void notifySchemaAdded(SchemaInfo schema) {
        for (SchemaListener listener : schemaListeners) {
            try {
                listener.onSchemaAdded(schema);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    /**
     * Notify schema removed
     */
    private void notifySchemaRemoved(SchemaInfo schema) {
        for (SchemaListener listener : schemaListeners) {
            try {
                listener.onSchemaRemoved(schema);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    /**
     * Notify primary schema changed
     */
    private void notifyPrimarySchemaChanged(String schemaId) {
        SchemaInfo schema = schemaRegistry.get(schemaId);
        if (schema != null) {
            for (SchemaListener listener : schemaListeners) {
                try {
                    listener.onPrimarySchemaChanged(schema);
                } catch (Exception e) {
                    // Ignore listener exceptions
                }
            }
        }
    }

    /**
     * Refresh schema
     */
    public CompletableFuture<Void> refreshSchema(String schemaId) {
        SchemaInfo schema = schemaRegistry.get(schemaId);
        if (schema == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Reload schema
                boolean wasPrimary = schemaId.equals(primarySchemaId);
                String namespace = schema.targetNamespace;

                removeSchema(schemaId);
                addSchema(schema.source, namespace, wasPrimary);

            } catch (Exception e) {
                throw new RuntimeException("Failed to refresh schema: " + schemaId, e);
            }
        }, backgroundExecutor);
    }

    /**
     * Get cache statistics
     */
    public CompletionCache.CacheStatistics getCacheStatistics() {
        return completionCache.getStatistics();
    }

    /**
     * Clear all caches
     */
    public void clearCaches() {
        completionCache.clearAll();
    }

    /**
     * Shutdown manager
     */
    public void shutdown() {
        try {
            backgroundExecutor.shutdown();
            // Wait for termination with timeout
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
                // Wait again after shutdownNow
                if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("MultiSchemaManager executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        completionCache.shutdown();
        schemaRegistry.clear();
        namespaceToSchemaId.clear();
        schemaListeners.clear();
    }

    // Schema information container
    public static class SchemaInfo {
        public final String schemaId;
        public final String source; // File path or URL
        public final String targetNamespace;
        public final IntelliSenseAdapter.SchemaInfo extractedInfo;
        public final long loadTime;
        private volatile String namespacePrefix;

        public SchemaInfo(String schemaId, String source, String targetNamespace,
                          IntelliSenseAdapter.SchemaInfo extractedInfo, long loadTime) {
            this.schemaId = schemaId;
            this.source = source;
            this.targetNamespace = targetNamespace;
            this.extractedInfo = extractedInfo;
            this.loadTime = loadTime;
            this.namespacePrefix = generateDefaultPrefix();
        }

        public String getNamespacePrefix() {
            return namespacePrefix;
        }

        public void setNamespacePrefix(String prefix) {
            this.namespacePrefix = prefix;
        }

        private String generateDefaultPrefix() {
            if (targetNamespace == null) {
                return null;
            }

            // Try to extract prefix from namespace URI
            String ns = targetNamespace;
            if (ns.contains("/")) {
                String last = ns.substring(ns.lastIndexOf("/") + 1);
                if (!last.isEmpty()) {
                    return last.toLowerCase();
                }
            }

            // Fallback to schema ID
            return schemaId.toLowerCase();
        }

        @Override
        public String toString() {
            return String.format("Schema[id=%s, namespace=%s, elements=%d, attributes=%d]",
                    schemaId, targetNamespace,
                    extractedInfo.elements.size(),
                    extractedInfo.attributes.size());
        }
    }

    // Schema listener interface
    public interface SchemaListener {
        default void onSchemaAdded(SchemaInfo schema) {
        }

        default void onSchemaRemoved(SchemaInfo schema) {
        }

        default void onPrimarySchemaChanged(SchemaInfo schema) {
        }
    }
}