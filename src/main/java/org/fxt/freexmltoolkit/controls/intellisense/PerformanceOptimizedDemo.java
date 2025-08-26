package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Demo showcasing performance optimizations for IntelliSense.
 */
public class PerformanceOptimizedDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Performance Optimized IntelliSense Demo");
        System.out.println("==========================================");
        System.out.println();

        // Initialize performance components
        CompletionCache cache = new CompletionCache();
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        LazyCompletionLoader loader = new LazyCompletionLoader(50, 200);

        try {
            // Demo 1: Cache Performance
            demoCachePerformance(cache);
            System.out.println();

            // Demo 2: Lazy Loading
            demoLazyLoading(loader);
            System.out.println();

            // Demo 3: Performance Profiling
            demoPerformanceProfiling(profiler, cache);
            System.out.println();

            // Demo 4: Optimized Search
            demoOptimizedSearch(cache);
            System.out.println();

            // Show final statistics
            showFinalStatistics(cache, profiler, loader);

        } finally {
            // Cleanup resources
            cache.shutdown();
            loader.shutdown();
        }

        System.out.println();
        System.out.println("✅ Performance optimization demo completed!");
    }

    private static void demoCachePerformance(CompletionCache cache) {
        System.out.println("1️⃣  Cache Performance Demo");
        System.out.println("─────────────────────────");

        // Create a mock completion provider
        CompletionCache.CompletionProvider provider = context -> {
            // Simulate expensive operation
            try {
                Thread.sleep(10); // Simulate 10ms delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Generate mock completion items
            List<CompletionItem> items = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                items.add(new CompletionItem.Builder(
                        context + "Item" + i,
                        context + "Item" + i,
                        CompletionItemType.ELEMENT
                ).description("Generated item for " + context)
                        .relevanceScore(100 - i)
                        .build());
            }
            return items;
        };

        String[] contexts = {"element", "attribute", "text", "element", "attribute"};

        System.out.println("  Testing cache performance...");
        long startTime = System.currentTimeMillis();

        for (String context : contexts) {
            List<CompletionItem> items = cache.getCompletionItems(context, provider);
            System.out.printf("    Context '%s': %d items%n", context, items.size());
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("  Total time: %d ms%n", endTime - startTime);

        CompletionCache.CacheStatistics stats = cache.getStatistics();
        System.out.printf("  %s%n", stats);
    }

    private static void demoLazyLoading(LazyCompletionLoader loader) {
        System.out.println("2️⃣  Lazy Loading Demo");
        System.out.println("────────────────────");

        // Create large dataset
        List<CompletionItem> largeDataset = generateLargeDataset(1000);
        System.out.printf("  Generated dataset with %d items%n", largeDataset.size());

        // Set up lazy loader
        LazyCompletionLoader.CompletionDataProvider provider =
                LazyCompletionLoader.fromList(largeDataset);
        loader.setDataProvider(provider);

        System.out.printf("  Total pages: %d (page size: 50)%n", loader.getTotalPageCount());

        // Add listener to track loading
        loader.addLoadListener(new LazyCompletionLoader.LoadListener() {
            @Override
            public void onPageLoaded(int pageIndex, List<CompletionItem> items) {
                System.out.printf("    Page %d loaded with %d items%n", pageIndex, items.size());
            }
        });

        // Access items in different pages
        System.out.println("  Accessing items...");
        CompletionItem item1 = loader.getItem(0);    // Page 0
        CompletionItem item2 = loader.getItem(75);   // Page 1
        CompletionItem item3 = loader.getItem(200);  // Page 4

        System.out.printf("    Item 0: %s%n", item1 != null ? item1.getLabel() : "null");
        System.out.printf("    Item 75: %s%n", item2 != null ? item2.getLabel() : "null");
        System.out.printf("    Item 200: %s%n", item3 != null ? item3.getLabel() : "null");

        // Wait for loading to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LazyCompletionLoader.CacheStatistics cacheStats = loader.getCacheStatistics();
        System.out.printf("  %s%n", cacheStats);
    }

    private static void demoPerformanceProfiling(PerformanceProfiler profiler, CompletionCache cache) {
        System.out.println("3️⃣  Performance Profiling Demo");
        System.out.println("─────────────────────────────");

        profiler.clear();

        // Profile various operations
        try (AutoCloseable timer = profiler.startOperation("completion-generation")) {
            List<CompletionItem> items = generateLargeDataset(500);
            System.out.printf("  Generated %d completion items%n", items.size());
        } catch (Exception e) {
            // Handle gracefully
            List<CompletionItem> items = generateLargeDataset(500);
            System.out.printf("  Generated %d completion items%n", items.size());
        }

        try (AutoCloseable timer = profiler.startOperation("fuzzy-search")) {
            List<CompletionItem> items = generateLargeDataset(100);
            List<CompletionItem> results = FuzzySearch.search("elem", items);
            System.out.printf("  Fuzzy search found %d results%n", results.size());
        } catch (Exception e) {
            // Handle gracefully
            List<CompletionItem> items = generateLargeDataset(100);
            List<CompletionItem> results = FuzzySearch.search("elem", items);
            System.out.printf("  Fuzzy search found %d results%n", results.size());
        }

        try (AutoCloseable timer = profiler.startOperation("advanced-search")) {
            List<CompletionItem> items = generateLargeDataset(200);
            FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                    .maxResults(20)
                    .searchInDescription(true);
            List<CompletionItem> results = FuzzySearch.advancedSearch("attr", items, options);
            System.out.printf("  Advanced search found %d results%n", results.size());
        } catch (Exception e) {
            // Handle gracefully
            List<CompletionItem> items = generateLargeDataset(200);
            FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                    .maxResults(20)
                    .searchInDescription(true);
            List<CompletionItem> results = FuzzySearch.advancedSearch("attr", items, options);
            System.out.printf("  Advanced search found %d results%n", results.size());
        }

        // Show profiling results
        PerformanceProfiler.PerformanceSummary summary = profiler.getSummary();
        System.out.println();
        System.out.println("  Performance Summary:");
        System.out.println("  " + summary.toString().replace("\n", "\n  "));
    }

    private static void demoOptimizedSearch(CompletionCache cache) {
        System.out.println("4️⃣  Optimized Search Demo");
        System.out.println("────────────────────────");

        List<CompletionItem> items = generateLargeDataset(300);

        // Create search options with cache
        FuzzySearch.SearchOptions options = new FuzzySearch.SearchOptions()
                .maxResults(10)
                .withCache(cache)
                .parallelProcessing(true)
                .minScore(20);

        String[] queries = {"elem", "attr", "text", "elem", "attr"}; // Repeat some for cache testing

        System.out.println("  Testing optimized search with cache...");
        long totalTime = 0;

        for (String query : queries) {
            long startTime = System.nanoTime();
            List<CompletionItem> results = FuzzySearch.search(query, items, options);
            long endTime = System.nanoTime();

            long duration = (endTime - startTime) / 1_000_000; // Convert to ms
            totalTime += duration;

            System.out.printf("    Query '%s': %d results in %d ms%n",
                    query, results.size(), duration);
        }

        System.out.printf("  Total search time: %d ms%n", totalTime);
        System.out.printf("  Average per search: %.1f ms%n", (double) totalTime / queries.length);
    }

    private static void showFinalStatistics(CompletionCache cache, PerformanceProfiler profiler, LazyCompletionLoader loader) {
        System.out.println("📊 Final Performance Statistics");
        System.out.println("═══════════════════════════════");

        System.out.println("Cache Statistics:");
        System.out.println("  " + cache.getStatistics());
        System.out.println();

        System.out.println("Lazy Loader Statistics:");
        System.out.println("  " + loader.getCacheStatistics());
        System.out.println();

        System.out.println("Profiler Summary:");
        PerformanceProfiler.PerformanceSummary summary = profiler.getSummary();
        System.out.println("  " + summary.toString().replace("\n", "\n  "));
    }

    private static List<CompletionItem> generateLargeDataset(int count) {
        List<CompletionItem> items = new ArrayList<>(count);
        String[] prefixes = {"element", "attribute", "text", "namespace", "snippet"};
        CompletionItemType[] types = {CompletionItemType.ELEMENT, CompletionItemType.ATTRIBUTE,
                CompletionItemType.TEXT, CompletionItemType.NAMESPACE,
                CompletionItemType.SNIPPET};

        Random random = new Random(42); // Fixed seed for consistent results

        for (int i = 0; i < count; i++) {
            int typeIndex = i % prefixes.length;
            String prefix = prefixes[typeIndex];
            CompletionItemType type = types[typeIndex];

            CompletionItem item = new CompletionItem.Builder(
                    prefix + "Item" + i,
                    "<" + prefix + "Item" + i + ">",
                    type
            )
                    .description("This is a generated " + type.getDisplayName().toLowerCase() + " for testing")
                    .dataType(type == CompletionItemType.ELEMENT ? "xs:complexType" : "xs:string")
                    .relevanceScore(random.nextInt(200))
                    .required(random.nextBoolean())
                    .build();

            items.add(item);
        }

        return items;
    }
}