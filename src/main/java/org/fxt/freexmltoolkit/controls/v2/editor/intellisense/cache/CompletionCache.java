package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for completion items to improve performance.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class CompletionCache {

    private static final Logger logger = LogManager.getLogger(CompletionCache.class);

    private final Map<CacheKey, List<CompletionItem>> cache = new ConcurrentHashMap<>();
    private final int maxSize;

    /**
     * Creates a cache with default size (1000).
     */
    public CompletionCache() {
        this(1000);
    }

    /**
     * Creates a cache with specified maximum size.
     *
     * @param maxSize the maximum number of cached entries
     */
    public CompletionCache(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Gets cached completions.
     *
     * @param xpath the XPath context
     * @param type  the context type
     * @param mode  the editor mode
     * @return cached items, or null if not found
     */
    public List<CompletionItem> get(String xpath, ContextType type, EditorMode mode) {
        CacheKey key = new CacheKey(xpath, type, mode);
        List<CompletionItem> items = cache.get(key);
        if (items != null) {
            logger.debug("Cache hit for xpath: {}, type: {}", xpath, type);
        }
        return items;
    }

    /**
     * Puts completions into cache.
     *
     * @param xpath the XPath context
     * @param type  the context type
     * @param mode  the editor mode
     * @param items the completion items
     */
    public void put(String xpath, ContextType type, EditorMode mode, List<CompletionItem> items) {
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        CacheKey key = new CacheKey(xpath, type, mode);
        cache.put(key, items);
        logger.debug("Cached {} items for xpath: {}, type: {}", items.size(), xpath, type);
    }

    /**
     * Invalidates all cache entries.
     */
    public void invalidateAll() {
        cache.clear();
        logger.debug("Cache cleared");
    }

    /**
     * Invalidates cache entries for schema-based completions.
     * Call this when the XSD schema changes.
     */
    public void invalidateForSchema() {
        // Remove all entries (could be optimized to only remove XSD-based entries)
        cache.clear();
        logger.debug("Cache invalidated for schema change");
    }

    /**
     * Gets current cache size.
     *
     * @return the number of cached entries
     */
    public int size() {
        return cache.size();
    }

    /**
     * Evicts oldest entry.
     * TODO: Implement proper LRU eviction strategy.
     */
    private void evictOldest() {
        if (!cache.isEmpty()) {
            CacheKey first = cache.keySet().iterator().next();
            cache.remove(first);
        }
    }

    /**
     * Cache key combining XPath, context type, and editor mode.
     */
    private static class CacheKey {
        private final String xpath;
        private final ContextType type;
        private final EditorMode mode;
        private final int hashCode;

        CacheKey(String xpath, ContextType type, EditorMode mode) {
            this.xpath = xpath != null ? xpath : "";
            this.type = type;
            this.mode = mode;
            this.hashCode = Objects.hash(xpath, type, mode);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(xpath, cacheKey.xpath) &&
                   type == cacheKey.type &&
                   mode == cacheKey.mode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
