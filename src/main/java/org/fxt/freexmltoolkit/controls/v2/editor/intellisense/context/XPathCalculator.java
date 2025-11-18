package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates XPath contexts with caching for performance.
 * Uses the ContextAnalyzer internally but caches results.
 *
 * <p>Thread-safe implementation using ConcurrentHashMap.</p>
 */
public class XPathCalculator {

    private static final Logger logger = LogManager.getLogger(XPathCalculator.class);

    // Cache: text hash + position -> XPathContext
    private final Map<CacheKey, XPathContext> cache = new ConcurrentHashMap<>();
    private final int maxCacheSize;

    /**
     * Creates a new XPath calculator with default cache size (1000).
     */
    public XPathCalculator() {
        this(1000);
    }

    /**
     * Creates a new XPath calculator with specified cache size.
     *
     * @param maxCacheSize the maximum number of cached entries
     */
    public XPathCalculator(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * Calculates the XPath context at a specific position.
     * Uses cache when possible.
     *
     * @param text     the XML text
     * @param position the position
     * @return the XPath context
     */
    public XPathContext calculate(String text, int position) {
        CacheKey key = new CacheKey(text, position);

        // Check cache first
        XPathContext cached = cache.get(key);
        if (cached != null) {
            logger.debug("XPath cache hit for position {}", position);
            return cached;
        }

        // Calculate using ContextAnalyzer
        XmlContext xmlContext = ContextAnalyzer.analyze(text, position);
        XPathContext xpathContext = xmlContext.getXPathContext();

        // Store in cache
        if (cache.size() >= maxCacheSize) {
            evictOldest();
        }
        cache.put(key, xpathContext);

        logger.debug("XPath calculated and cached: {}", xpathContext);
        return xpathContext;
    }

    /**
     * Invalidates the entire cache.
     * Call this when the text has changed significantly.
     */
    public void invalidateCache() {
        cache.clear();
        logger.debug("XPath cache invalidated");
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Evicts the oldest cache entry (simple strategy: remove first).
     * TODO: Implement LRU eviction strategy for better performance.
     */
    private void evictOldest() {
        if (!cache.isEmpty()) {
            CacheKey first = cache.keySet().iterator().next();
            cache.remove(first);
        }
    }

    /**
     * Cache key combining text content and position.
     */
    private static class CacheKey {
        private final int textHash;
        private final int position;
        private final int hashCode;

        CacheKey(String text, int position) {
            this.textHash = text != null ? text.hashCode() : 0;
            this.position = position;
            this.hashCode = 31 * textHash + position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return textHash == cacheKey.textHash && position == cacheKey.position;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
