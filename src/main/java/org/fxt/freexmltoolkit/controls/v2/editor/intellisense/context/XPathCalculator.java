package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Calculates XPath contexts with caching for performance.
 * Uses the ContextAnalyzer internally but caches results.
 *
 * <p>Thread-safe LRU cache implementation using LinkedHashMap with access-order.</p>
 */
public class XPathCalculator {

    private static final Logger logger = LogManager.getLogger(XPathCalculator.class);

    // Cache: text hash + position -> XPathContext
    private final Map<CacheKey, XPathContext> cache;
    private final int maxCacheSize;

    /**
     * Creates a new XPath calculator with default cache size (1000).
     */
    public XPathCalculator() {
        this(1000);
    }

    /**
     * Creates a new XPath calculator with specified cache size.
     * Uses LinkedHashMap with access-order for proper LRU eviction.
     *
     * @param maxCacheSize the maximum number of cached entries
     */
    public XPathCalculator(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        // Create LRU cache using LinkedHashMap with access-order
        // accessOrder=true: ordering mode - true for access-order, false for insertion-order
        this.cache = Collections.synchronizedMap(
            new LinkedHashMap<CacheKey, XPathContext>(maxCacheSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, XPathContext> eldest) {
                    boolean shouldRemove = size() > maxCacheSize;
                    if (shouldRemove) {
                        logger.debug("LRU eviction: removing eldest XPath cache entry");
                    }
                    return shouldRemove;
                }
            }
        );
    }

    /**
     * Calculates the XPath context at a specific position.
     * Uses cache when possible. LRU eviction is handled automatically by LinkedHashMap.
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
            logger.debug("XPath cache hit for position {} (cache size: {})", position, cache.size());
            return cached;
        }

        // Calculate using ContextAnalyzer
        XmlContext xmlContext = ContextAnalyzer.analyze(text, position);
        XPathContext xpathContext = xmlContext.getXPathContext();

        // Store in cache (LRU eviction handled automatically)
        cache.put(key, xpathContext);

        logger.debug("XPath calculated and cached: {} (cache size: {})", xpathContext, cache.size());
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
