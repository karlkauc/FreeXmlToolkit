package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance caching system for IntelliSense completion items.
 * Implements lazy loading, background prefetching, and intelligent cache eviction.
 */
public class CompletionCache {

    private static final int DEFAULT_MAX_CACHE_SIZE = 10000;
    private static final int DEFAULT_MAX_SEARCH_CACHE_SIZE = 1000;
    private static final long DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    // Main completion item cache
    private final Map<String, CacheEntry> completionCache;

    // Search results cache for fuzzy search
    private final Map<String, SearchCacheEntry> searchCache;

    // Background executor for async operations
    private final ExecutorService backgroundExecutor;

    // Cache configuration
    private final int maxCacheSize;
    private final int maxSearchCacheSize;
    private final long cacheTtlMs;

    // Cache statistics
    private volatile long cacheHits = 0;
    private volatile long cacheMisses = 0;
    private volatile long searchCacheHits = 0;
    private volatile long searchCacheMisses = 0;

    public CompletionCache() {
        this(DEFAULT_MAX_CACHE_SIZE, DEFAULT_MAX_SEARCH_CACHE_SIZE, DEFAULT_CACHE_TTL_MS);
    }

    public CompletionCache(int maxCacheSize, int maxSearchCacheSize, long cacheTtlMs) {
        this.maxCacheSize = maxCacheSize;
        this.maxSearchCacheSize = maxSearchCacheSize;
        this.cacheTtlMs = cacheTtlMs;

        this.completionCache = new ConcurrentHashMap<>();
        this.searchCache = new ConcurrentHashMap<>();
        this.backgroundExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "IntelliSense-Cache-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });

        // Start cache cleanup thread
        startCacheCleanupScheduler();
    }

    /**
     * Get completion items for a context (with caching)
     */
    public List<CompletionItem> getCompletionItems(String context, CompletionProvider provider) {
        CacheEntry entry = completionCache.get(context);

        if (entry != null && !entry.isExpired()) {
            cacheHits++;
            return entry.items;
        }

        cacheMisses++;

        // Load items synchronously if not cached
        List<CompletionItem> items = provider.provideCompletionItems(context);

        // Cache the result
        cacheCompletionItems(context, items);

        // Prefetch related contexts in background
        prefetchRelatedContexts(context, provider);

        return items;
    }

    /**
     * Get cached search results for fuzzy search
     */
    public List<CompletionItem> getCachedSearchResults(String query, List<CompletionItem> items) {
        String cacheKey = generateSearchCacheKey(query, items);
        SearchCacheEntry entry = searchCache.get(cacheKey);

        if (entry != null && !entry.isExpired()) {
            searchCacheHits++;
            return entry.results;
        }

        searchCacheMisses++;
        return null; // Not cached
    }

    /**
     * Cache search results
     */
    public void cacheSearchResults(String query, List<CompletionItem> items, List<CompletionItem> results) {
        String cacheKey = generateSearchCacheKey(query, items);
        searchCache.put(cacheKey, new SearchCacheEntry(results));

        // Evict old entries if cache is full
        if (searchCache.size() > maxSearchCacheSize) {
            evictOldSearchEntries();
        }
    }

    /**
     * Preload completion items for common contexts
     */
    public CompletableFuture<Void> preloadCommonContexts(CompletionProvider provider) {
        return CompletableFuture.runAsync(() -> {
            String[] commonContexts = {
                    "root", "element", "attribute", "text",
                    "xs:string", "xs:int", "xs:boolean", "xs:date"
            };

            for (String context : commonContexts) {
                if (!completionCache.containsKey(context)) {
                    List<CompletionItem> items = provider.provideCompletionItems(context);
                    cacheCompletionItems(context, items);
                }
            }
        }, backgroundExecutor);
    }

    /**
     * Cache completion items
     */
    private void cacheCompletionItems(String context, List<CompletionItem> items) {
        completionCache.put(context, new CacheEntry(items));

        // Evict old entries if cache is full
        if (completionCache.size() > maxCacheSize) {
            evictOldEntries();
        }
    }

    /**
     * Prefetch related contexts in background
     */
    private void prefetchRelatedContexts(String context, CompletionProvider provider) {
        backgroundExecutor.submit(() -> {
            Set<String> relatedContexts = generateRelatedContexts(context);

            for (String relatedContext : relatedContexts) {
                if (!completionCache.containsKey(relatedContext)) {
                    List<CompletionItem> items = provider.provideCompletionItems(relatedContext);
                    cacheCompletionItems(relatedContext, items);
                }
            }
        });
    }

    /**
     * Generate related contexts for prefetching
     */
    private Set<String> generateRelatedContexts(String context) {
        Set<String> related = new HashSet<>();

        // Add parent contexts
        if (context.contains("/")) {
            String parent = context.substring(0, context.lastIndexOf("/"));
            related.add(parent);
        }

        // Add child contexts (common patterns)
        related.add(context + "/element");
        related.add(context + "/attribute");
        related.add(context + "/text");

        // Add sibling contexts
        if (context.contains("/")) {
            String parent = context.substring(0, context.lastIndexOf("/"));
            String element = context.substring(context.lastIndexOf("/") + 1);
            related.add(parent + "/" + element + "Ref");
            related.add(parent + "/" + element + "Type");
        }

        return related;
    }

    /**
     * Generate cache key for search results
     */
    private String generateSearchCacheKey(String query, List<CompletionItem> items) {
        int itemsHash = items.stream()
                .mapToInt(item -> Objects.hash(item.getLabel(), item.getType()))
                .reduce(0, Integer::sum);

        return query + ":" + itemsHash;
    }

    /**
     * Evict old cache entries when cache is full
     */
    private void evictOldEntries() {
        if (completionCache.size() <= maxCacheSize) {
            return;
        }

        List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(completionCache.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().lastAccessed, b.getValue().lastAccessed));

        // Remove oldest 25% of entries
        int toRemove = completionCache.size() / 4;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            completionCache.remove(entries.get(i).getKey());
        }
    }

    /**
     * Evict old search cache entries
     */
    private void evictOldSearchEntries() {
        if (searchCache.size() <= maxSearchCacheSize) {
            return;
        }

        List<Map.Entry<String, SearchCacheEntry>> entries = new ArrayList<>(searchCache.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().lastAccessed, b.getValue().lastAccessed));

        // Remove oldest 25% of entries
        int toRemove = searchCache.size() / 4;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            searchCache.remove(entries.get(i).getKey());
        }
    }

    /**
     * Start background cache cleanup
     */
    private void startCacheCleanupScheduler() {
        backgroundExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // Check every minute
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Clean up expired cache entries
     */
    private void cleanupExpiredEntries() {
        completionCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        searchCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clear all caches
     */
    public void clearAll() {
        completionCache.clear();
        searchCache.clear();
        resetStatistics();
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
                cacheHits, cacheMisses,
                searchCacheHits, searchCacheMisses,
                completionCache.size(), searchCache.size()
        );
    }

    /**
     * Reset statistics
     */
    public void resetStatistics() {
        cacheHits = 0;
        cacheMisses = 0;
        searchCacheHits = 0;
        searchCacheMisses = 0;
    }

    /**
     * Shutdown cache and cleanup resources
     */
    public void shutdown() {
        backgroundExecutor.shutdown();
        clearAll();
    }

    // Cache entry classes
    private class CacheEntry {
        final List<CompletionItem> items;
        final long createdAt;
        volatile long lastAccessed;

        CacheEntry(List<CompletionItem> items) {
            this.items = new ArrayList<>(items);
            this.createdAt = System.currentTimeMillis();
            this.lastAccessed = createdAt;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > cacheTtlMs;
        }
    }

    private class SearchCacheEntry {
        final List<CompletionItem> results;
        final long createdAt;
        volatile long lastAccessed;

        SearchCacheEntry(List<CompletionItem> results) {
            this.results = new ArrayList<>(results);
            this.createdAt = System.currentTimeMillis();
            this.lastAccessed = createdAt;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > cacheTtlMs;
        }
    }

    // Statistics class
    public static class CacheStatistics {
        public final long cacheHits;
        public final long cacheMisses;
        public final long searchCacheHits;
        public final long searchCacheMisses;
        public final int completionCacheSize;
        public final int searchCacheSize;

        CacheStatistics(long cacheHits, long cacheMisses,
                        long searchCacheHits, long searchCacheMisses,
                        int completionCacheSize, int searchCacheSize) {
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.searchCacheHits = searchCacheHits;
            this.searchCacheMisses = searchCacheMisses;
            this.completionCacheSize = completionCacheSize;
            this.searchCacheSize = searchCacheSize;
        }

        public double getCacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total == 0 ? 0.0 : (double) cacheHits / total;
        }

        public double getSearchCacheHitRate() {
            long total = searchCacheHits + searchCacheMisses;
            return total == 0 ? 0.0 : (double) searchCacheHits / total;
        }

        @Override
        public String toString() {
            return String.format(
                    "Cache Stats - Hits: %d, Misses: %d (%.1f%% hit rate), " +
                            "Search Hits: %d, Search Misses: %d (%.1f%% hit rate), " +
                            "Cache Size: %d, Search Cache Size: %d",
                    cacheHits, cacheMisses, getCacheHitRate() * 100,
                    searchCacheHits, searchCacheMisses, getSearchCacheHitRate() * 100,
                    completionCacheSize, searchCacheSize
            );
        }
    }

    // Interface for completion providers
    public interface CompletionProvider {
        List<CompletionItem> provideCompletionItems(String context);
    }
}