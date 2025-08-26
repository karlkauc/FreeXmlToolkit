package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance profiler for IntelliSense operations.
 * Tracks timing, memory usage, and operation counts.
 */
public class PerformanceProfiler {

    private static final PerformanceProfiler INSTANCE = new PerformanceProfiler();

    // Operation timing data
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();

    // Memory tracking
    private final AtomicLong totalMemoryUsed = new AtomicLong(0);
    private final AtomicLong peakMemoryUsed = new AtomicLong(0);

    // Global operation counter
    private final AtomicLong operationCounter = new AtomicLong(0);

    // Profiling configuration
    private volatile boolean profilingEnabled = true;
    private volatile boolean memoryTrackingEnabled = true;

    private PerformanceProfiler() {
        // Private constructor for singleton
    }

    public static PerformanceProfiler getInstance() {
        return INSTANCE;
    }

    /**
     * Start timing an operation
     */
    public AutoCloseable startOperation(String operationName) {
        if (!profilingEnabled) {
            return new NoOpTimer();
        }

        return new OperationTimer(operationName);
    }

    /**
     * Record operation completion
     */
    void recordOperation(String operationName, long durationNs, long memoryUsed) {
        if (!profilingEnabled) {
            return;
        }

        operationStats.computeIfAbsent(operationName, k -> new OperationStats())
                .recordExecution(durationNs, memoryUsed);

        operationCounter.incrementAndGet();

        if (memoryTrackingEnabled && memoryUsed > 0) {
            totalMemoryUsed.addAndGet(memoryUsed);
            peakMemoryUsed.updateAndGet(current -> Math.max(current, memoryUsed));
        }
    }

    /**
     * Get statistics for all operations
     */
    public Map<String, OperationStats> getAllStats() {
        return new HashMap<>(operationStats);
    }

    /**
     * Get statistics for a specific operation
     */
    public OperationStats getStats(String operationName) {
        return operationStats.get(operationName);
    }

    /**
     * Get performance summary
     */
    public PerformanceSummary getSummary() {
        Map<String, OperationStats> stats = getAllStats();

        // Calculate totals
        long totalOperations = operationCounter.get();
        long totalDuration = stats.values().stream()
                .mapToLong(s -> s.totalDurationNs)
                .sum();

        // Find slowest operations
        List<Map.Entry<String, OperationStats>> slowestOps = stats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().averageDurationNs(), a.getValue().averageDurationNs()))
                .limit(5)
                .toList();

        // Find most frequent operations
        List<Map.Entry<String, OperationStats>> mostFrequentOps = stats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().executionCount, a.getValue().executionCount))
                .limit(5)
                .toList();

        return new PerformanceSummary(
                totalOperations,
                totalDuration,
                totalMemoryUsed.get(),
                peakMemoryUsed.get(),
                slowestOps,
                mostFrequentOps
        );
    }

    /**
     * Clear all statistics
     */
    public void clear() {
        operationStats.clear();
        totalMemoryUsed.set(0);
        peakMemoryUsed.set(0);
        operationCounter.set(0);
    }

    /**
     * Enable/disable profiling
     */
    public void setProfilingEnabled(boolean enabled) {
        this.profilingEnabled = enabled;
    }

    /**
     * Enable/disable memory tracking
     */
    public void setMemoryTrackingEnabled(boolean enabled) {
        this.memoryTrackingEnabled = enabled;
    }

    /**
     * Check if profiling is enabled
     */
    public boolean isProfilingEnabled() {
        return profilingEnabled;
    }

    // Operation timer class
    public class OperationTimer implements AutoCloseable {
        private final String operationName;
        private final long startTime;
        private final long startMemory;
        private boolean closed = false;

        OperationTimer(String operationName) {
            this.operationName = operationName;
            this.startTime = System.nanoTime();
            this.startMemory = memoryTrackingEnabled ? getCurrentMemoryUsage() : 0;
        }

        @Override
        public void close() {
            if (!closed) {
                long duration = System.nanoTime() - startTime;
                long memoryUsed = memoryTrackingEnabled ?
                        Math.max(0, getCurrentMemoryUsage() - startMemory) : 0;

                recordOperation(operationName, duration, memoryUsed);
                closed = true;
            }
        }

        public long getElapsedTimeMs() {
            return (System.nanoTime() - startTime) / 1_000_000;
        }
    }

    // No-op timer for when profiling is disabled
    private static class NoOpTimer implements AutoCloseable {
        @Override
        public void close() {
            // No-op
        }

        public long getElapsedTimeMs() {
            return 0;
        }
    }

    // Operation statistics
    public static class OperationStats {
        private volatile long executionCount = 0;
        private volatile long totalDurationNs = 0;
        private volatile long minDurationNs = Long.MAX_VALUE;
        private volatile long maxDurationNs = Long.MIN_VALUE;
        private volatile long totalMemoryUsed = 0;
        private volatile long maxMemoryUsed = 0;
        private final AtomicLong lastExecutionTime = new AtomicLong(0);

        synchronized void recordExecution(long durationNs, long memoryUsed) {
            executionCount++;
            totalDurationNs += durationNs;
            minDurationNs = Math.min(minDurationNs, durationNs);
            maxDurationNs = Math.max(maxDurationNs, durationNs);
            totalMemoryUsed += memoryUsed;
            maxMemoryUsed = Math.max(maxMemoryUsed, memoryUsed);
            lastExecutionTime.set(System.currentTimeMillis());
        }

        public long getExecutionCount() {
            return executionCount;
        }

        public long averageDurationNs() {
            return executionCount == 0 ? 0 : totalDurationNs / executionCount;
        }

        public double averageDurationMs() {
            return averageDurationNs() / 1_000_000.0;
        }

        public double minDurationMs() {
            return minDurationNs == Long.MAX_VALUE ? 0 : minDurationNs / 1_000_000.0;
        }

        public double maxDurationMs() {
            return maxDurationNs == Long.MIN_VALUE ? 0 : maxDurationNs / 1_000_000.0;
        }

        public long getTotalMemoryUsed() {
            return totalMemoryUsed;
        }

        public long getMaxMemoryUsed() {
            return maxMemoryUsed;
        }

        public long averageMemoryUsed() {
            return executionCount == 0 ? 0 : totalMemoryUsed / executionCount;
        }

        public long getLastExecutionTime() {
            return lastExecutionTime.get();
        }

        @Override
        public String toString() {
            return String.format(
                    "OperationStats{count=%d, avg=%.2fms, min=%.2fms, max=%.2fms, avgMem=%d bytes}",
                    executionCount, averageDurationMs(), minDurationMs(), maxDurationMs(), averageMemoryUsed()
            );
        }
    }

    // Performance summary
    public static class PerformanceSummary {
        public final long totalOperations;
        public final long totalDurationNs;
        public final long totalMemoryUsed;
        public final long peakMemoryUsed;
        public final List<Map.Entry<String, OperationStats>> slowestOperations;
        public final List<Map.Entry<String, OperationStats>> mostFrequentOperations;

        PerformanceSummary(long totalOperations, long totalDurationNs,
                           long totalMemoryUsed, long peakMemoryUsed,
                           List<Map.Entry<String, OperationStats>> slowestOperations,
                           List<Map.Entry<String, OperationStats>> mostFrequentOperations) {
            this.totalOperations = totalOperations;
            this.totalDurationNs = totalDurationNs;
            this.totalMemoryUsed = totalMemoryUsed;
            this.peakMemoryUsed = peakMemoryUsed;
            this.slowestOperations = slowestOperations;
            this.mostFrequentOperations = mostFrequentOperations;
        }

        public double getTotalDurationMs() {
            return totalDurationNs / 1_000_000.0;
        }

        public double getAverageDurationMs() {
            return totalOperations == 0 ? 0 : getTotalDurationMs() / totalOperations;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Performance Summary:\n");
            sb.append(String.format("Total Operations: %d\n", totalOperations));
            sb.append(String.format("Total Duration: %.2f ms\n", getTotalDurationMs()));
            sb.append(String.format("Average Duration: %.2f ms\n", getAverageDurationMs()));
            sb.append(String.format("Total Memory Used: %d bytes (%.2f MB)\n",
                    totalMemoryUsed, totalMemoryUsed / 1024.0 / 1024.0));
            sb.append(String.format("Peak Memory Used: %d bytes (%.2f MB)\n",
                    peakMemoryUsed, peakMemoryUsed / 1024.0 / 1024.0));

            sb.append("\nSlowest Operations:\n");
            for (Map.Entry<String, OperationStats> entry : slowestOperations) {
                sb.append(String.format("  %s: %.2f ms avg\n",
                        entry.getKey(), entry.getValue().averageDurationMs()));
            }

            sb.append("\nMost Frequent Operations:\n");
            for (Map.Entry<String, OperationStats> entry : mostFrequentOperations) {
                sb.append(String.format("  %s: %d executions\n",
                        entry.getKey(), entry.getValue().getExecutionCount()));
            }

            return sb.toString();
        }
    }

    /**
     * Get current memory usage (rough estimate)
     */
    private long getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Utility method to format bytes as human-readable string
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}