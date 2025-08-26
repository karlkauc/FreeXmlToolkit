package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.*;
import java.util.concurrent.*;

/**
 * Lazy loading system for IntelliSense completion items.
 * Loads completion data on-demand and provides virtual scrolling support.
 */
public class LazyCompletionLoader {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_CACHE_SIZE = 500;

    // Executor for background loading
    private final ExecutorService backgroundExecutor;

    // Page cache for loaded completion items
    private final Map<Integer, CompletionPage> pageCache;
    private final int pageSize;
    private final int maxCacheSize;

    // Data provider interface
    public interface CompletionDataProvider {
        int getTotalItemCount();

        List<CompletionItem> loadPage(int pageIndex, int pageSize);

        void refresh(); // Called when data needs to be refreshed
    }

    // Completion data provider
    private volatile CompletionDataProvider dataProvider;
    private volatile int totalItemCount = 0;

    // Loading state tracking
    private final Set<Integer> loadingPages = ConcurrentHashMap.newKeySet();
    private final Map<Integer, CompletableFuture<CompletionPage>> loadingFutures = new ConcurrentHashMap<>();

    // Listeners for load events
    private final List<LoadListener> loadListeners = new CopyOnWriteArrayList<>();

    public LazyCompletionLoader() {
        this(DEFAULT_PAGE_SIZE, DEFAULT_CACHE_SIZE);
    }

    public LazyCompletionLoader(int pageSize, int maxCacheSize) {
        this.pageSize = pageSize;
        this.maxCacheSize = maxCacheSize;
        this.pageCache = new ConcurrentHashMap<>();
        this.backgroundExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "LazyCompletion-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Set the data provider
     */
    public void setDataProvider(CompletionDataProvider provider) {
        this.dataProvider = provider;
        refresh();
    }

    /**
     * Get completion item at specific index (lazy-loaded)
     */
    public CompletionItem getItem(int index) {
        if (index < 0 || index >= totalItemCount) {
            return null;
        }

        int pageIndex = index / pageSize;
        int itemIndex = index % pageSize;

        CompletionPage page = getPage(pageIndex);
        if (page != null && itemIndex < page.items.size()) {
            return page.items.get(itemIndex);
        }

        return null;
    }

    /**
     * Get range of completion items (lazy-loaded)
     */
    public List<CompletionItem> getItems(int startIndex, int count) {
        List<CompletionItem> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            if (index >= totalItemCount) {
                break;
            }

            CompletionItem item = getItem(index);
            if (item != null) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Get page of completion items
     */
    private CompletionPage getPage(int pageIndex) {
        CompletionPage page = pageCache.get(pageIndex);

        if (page != null) {
            page.lastAccessed = System.currentTimeMillis();
            return page;
        }

        // Start loading page if not already loading
        if (!loadingPages.contains(pageIndex)) {
            loadPageAsync(pageIndex);
        }

        return null; // Page not loaded yet
    }

    /**
     * Load page asynchronously
     */
    private void loadPageAsync(int pageIndex) {
        if (dataProvider == null || loadingPages.contains(pageIndex)) {
            return;
        }

        loadingPages.add(pageIndex);

        CompletableFuture<CompletionPage> future = CompletableFuture
                .supplyAsync(() -> loadPageSync(pageIndex), backgroundExecutor)
                .whenComplete((page, throwable) -> {
                    loadingPages.remove(pageIndex);
                    loadingFutures.remove(pageIndex);

                    if (throwable == null && page != null) {
                        // Cache the loaded page
                        pageCache.put(pageIndex, page);
                        evictOldPagesIfNecessary();

                        // Notify listeners
                        notifyPageLoaded(pageIndex, page);
                    } else {
                        // Notify listeners of error
                        notifyLoadError(pageIndex, throwable);
                    }
                });

        loadingFutures.put(pageIndex, future);
    }

    /**
     * Load page synchronously
     */
    private CompletionPage loadPageSync(int pageIndex) {
        if (dataProvider == null) {
            return null;
        }

        try (AutoCloseable timer =
                     PerformanceProfiler.getInstance().startOperation("lazy-load-page")) {

            List<CompletionItem> items = dataProvider.loadPage(pageIndex, pageSize);
            return new CompletionPage(pageIndex, items);
        } catch (Exception e) {
            // Handle profiler exceptions gracefully
            List<CompletionItem> items = dataProvider.loadPage(pageIndex, pageSize);
            return new CompletionPage(pageIndex, items);
        }
    }

    /**
     * Preload pages around current index for smoother scrolling
     */
    public void preloadAround(int currentIndex) {
        int currentPageIndex = currentIndex / pageSize;

        // Preload current page and adjacent pages
        for (int i = Math.max(0, currentPageIndex - 1);
             i <= Math.min(getTotalPageCount() - 1, currentPageIndex + 1); i++) {

            if (!pageCache.containsKey(i) && !loadingPages.contains(i)) {
                loadPageAsync(i);
            }
        }
    }

    /**
     * Get total number of items
     */
    public int getTotalItemCount() {
        return totalItemCount;
    }

    /**
     * Get total number of pages
     */
    public int getTotalPageCount() {
        return (totalItemCount + pageSize - 1) / pageSize;
    }

    /**
     * Check if page is loaded
     */
    public boolean isPageLoaded(int pageIndex) {
        return pageCache.containsKey(pageIndex);
    }

    /**
     * Check if page is currently loading
     */
    public boolean isPageLoading(int pageIndex) {
        return loadingPages.contains(pageIndex);
    }

    /**
     * Refresh all data
     */
    public void refresh() {
        // Clear cache
        pageCache.clear();
        loadingPages.clear();
        loadingFutures.values().forEach(future -> future.cancel(true));
        loadingFutures.clear();

        // Refresh data provider
        if (dataProvider != null) {
            dataProvider.refresh();
            totalItemCount = dataProvider.getTotalItemCount();
        } else {
            totalItemCount = 0;
        }

        // Notify listeners
        notifyDataRefreshed();
    }

    /**
     * Evict old pages when cache is full
     */
    private void evictOldPagesIfNecessary() {
        if (pageCache.size() <= maxCacheSize) {
            return;
        }

        List<Map.Entry<Integer, CompletionPage>> entries = new ArrayList<>(pageCache.entrySet());
        entries.sort((a, b) -> Long.compare(a.getValue().lastAccessed, b.getValue().lastAccessed));

        // Remove oldest 25% of pages
        int toRemove = pageCache.size() / 4;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            pageCache.remove(entries.get(i).getKey());
        }
    }

    /**
     * Add load listener
     */
    public void addLoadListener(LoadListener listener) {
        loadListeners.add(listener);
    }

    /**
     * Remove load listener
     */
    public void removeLoadListener(LoadListener listener) {
        loadListeners.remove(listener);
    }

    /**
     * Notify listeners of page loaded
     */
    private void notifyPageLoaded(int pageIndex, CompletionPage page) {
        for (LoadListener listener : loadListeners) {
            try {
                listener.onPageLoaded(pageIndex, page.items);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    /**
     * Notify listeners of load error
     */
    private void notifyLoadError(int pageIndex, Throwable error) {
        for (LoadListener listener : loadListeners) {
            try {
                listener.onLoadError(pageIndex, error);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    /**
     * Notify listeners of data refresh
     */
    private void notifyDataRefreshed() {
        for (LoadListener listener : loadListeners) {
            try {
                listener.onDataRefreshed(totalItemCount);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(
                pageCache.size(),
                loadingPages.size(),
                getTotalPageCount(),
                totalItemCount
        );
    }

    /**
     * Shutdown loader and cleanup resources
     */
    public void shutdown() {
        backgroundExecutor.shutdown();
        pageCache.clear();
        loadingPages.clear();
        loadingFutures.values().forEach(future -> future.cancel(true));
        loadingFutures.clear();
        loadListeners.clear();
    }

    // Page data container
    private static class CompletionPage {
        final int pageIndex;
        final List<CompletionItem> items;
        final long loadTime;
        volatile long lastAccessed;

        CompletionPage(int pageIndex, List<CompletionItem> items) {
            this.pageIndex = pageIndex;
            this.items = new ArrayList<>(items);
            this.loadTime = System.currentTimeMillis();
            this.lastAccessed = loadTime;
        }
    }

    // Load event listener interface
    public interface LoadListener {
        default void onPageLoaded(int pageIndex, List<CompletionItem> items) {
        }

        default void onLoadError(int pageIndex, Throwable error) {
        }

        default void onDataRefreshed(int totalItemCount) {
        }
    }

    // Cache statistics
    public static class CacheStatistics {
        public final int loadedPages;
        public final int loadingPages;
        public final int totalPages;
        public final int totalItems;

        CacheStatistics(int loadedPages, int loadingPages, int totalPages, int totalItems) {
            this.loadedPages = loadedPages;
            this.loadingPages = loadingPages;
            this.totalPages = totalPages;
            this.totalItems = totalItems;
        }

        public double getLoadedPercentage() {
            return totalPages == 0 ? 100.0 : (double) loadedPages / totalPages * 100.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "Cache Stats - Loaded: %d/%d pages (%.1f%%), Loading: %d, Total Items: %d",
                    loadedPages, totalPages, getLoadedPercentage(), loadingPages, totalItems
            );
        }
    }

    /**
     * Create a simple data provider from a list
     */
    public static CompletionDataProvider fromList(List<CompletionItem> items) {
        return new CompletionDataProvider() {
            private final List<CompletionItem> data = new ArrayList<>(items);

            @Override
            public int getTotalItemCount() {
                return data.size();
            }

            @Override
            public List<CompletionItem> loadPage(int pageIndex, int pageSize) {
                int startIndex = pageIndex * pageSize;
                int endIndex = Math.min(startIndex + pageSize, data.size());

                if (startIndex >= data.size()) {
                    return Collections.emptyList();
                }

                return data.subList(startIndex, endIndex);
            }

            @Override
            public void refresh() {
                // No-op for static list
            }
        };
    }
}