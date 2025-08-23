package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive performance profiling for XSLT transformations.
 * Tracks timing, memory usage, and execution patterns for optimization analysis.
 */
public class TransformationProfile {

    // Timing information
    private long startTime;
    private long endTime;
    private long compilationTime;
    private long transformationTime;
    private long serializationTime;
    private final LocalDateTime profiledAt;

    // Performance metrics
    private long memoryBefore;
    private long memoryAfter;
    private long peakMemoryUsage;
    private int inputSize;
    private int outputSize;

    // Execution analysis
    private final Map<String, Long> templateExecutionTimes;
    private final Map<String, Integer> templateExecutionCounts;
    private final List<ProfileEvent> events;
    private final Map<String, Object> customMetrics;

    // Resource usage
    private int threadCount;
    private long cpuTime;
    private int ioOperations;

    public TransformationProfile() {
        this.templateExecutionTimes = new HashMap<>();
        this.templateExecutionCounts = new HashMap<>();
        this.events = new ArrayList<>();
        this.customMetrics = new HashMap<>();
        this.profiledAt = LocalDateTime.now();
    }

    // ========== Profiling Control ==========

    /**
     * Start profiling a transformation
     */
    public void startTransformation() {
        this.startTime = System.nanoTime();
        this.memoryBefore = getCurrentMemoryUsage();
        addEvent("transformation_start", "Transformation started", 0);
    }

    /**
     * End profiling a transformation
     */
    public void endTransformation() {
        this.endTime = System.nanoTime();
        this.memoryAfter = getCurrentMemoryUsage();
        addEvent("transformation_end", "Transformation completed", getTotalExecutionTime());
    }

    /**
     * Start profiling compilation phase
     */
    public void startCompilation() {
        addEvent("compilation_start", "XSLT compilation started", getElapsedTime());
    }

    /**
     * End profiling compilation phase
     */
    public void endCompilation() {
        long currentTime = getElapsedTime();
        this.compilationTime = currentTime - getLastEventTime("compilation_start");
        addEvent("compilation_end", "XSLT compilation completed", currentTime);
    }

    /**
     * Start profiling transformation phase
     */
    public void startTransformationPhase() {
        addEvent("transform_start", "Transformation phase started", getElapsedTime());
    }

    /**
     * End profiling transformation phase
     */
    public void endTransformationPhase() {
        long currentTime = getElapsedTime();
        this.transformationTime = currentTime - getLastEventTime("transform_start");
        addEvent("transform_end", "Transformation phase completed", currentTime);
    }

    /**
     * Start profiling serialization phase
     */
    public void startSerialization() {
        addEvent("serialization_start", "Result serialization started", getElapsedTime());
    }

    /**
     * End profiling serialization phase
     */
    public void endSerialization() {
        long currentTime = getElapsedTime();
        this.serializationTime = currentTime - getLastEventTime("serialization_start");
        addEvent("serialization_end", "Result serialization completed", currentTime);
    }

    // ========== Template Profiling ==========

    /**
     * Record template execution
     */
    public void recordTemplateExecution(String templateName, long executionTime) {
        templateExecutionTimes.put(templateName,
                templateExecutionTimes.getOrDefault(templateName, 0L) + executionTime);
        templateExecutionCounts.put(templateName,
                templateExecutionCounts.getOrDefault(templateName, 0) + 1);

        addEvent("template_execution", "Template '" + templateName + "' executed", executionTime);
    }

    /**
     * Record template matching
     */
    public void recordTemplateMatch(String pattern, String nodeName, long matchTime) {
        String eventKey = "match_" + pattern.replaceAll("[^a-zA-Z0-9]", "_");
        addEvent(eventKey, "Template matched: " + pattern + " on " + nodeName, matchTime);
    }

    // ========== Memory Profiling ==========

    /**
     * Record memory checkpoint
     */
    public void recordMemoryCheckpoint(String checkpoint) {
        long currentMemory = getCurrentMemoryUsage();
        if (currentMemory > peakMemoryUsage) {
            peakMemoryUsage = currentMemory;
        }

        customMetrics.put("memory_" + checkpoint, currentMemory);
        addEvent("memory_checkpoint", "Memory checkpoint: " + checkpoint +
                " (" + formatBytes(currentMemory) + ")", getElapsedTime());
    }

    private long getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // ========== Event Management ==========

    private void addEvent(String type, String description, long timestamp) {
        events.add(new ProfileEvent(type, description, timestamp));
    }

    private long getLastEventTime(String eventType) {
        for (int i = events.size() - 1; i >= 0; i--) {
            ProfileEvent event = events.get(i);
            if (event.type().equals(eventType)) {
                return event.timestamp();
            }
        }
        return 0;
    }

    private long getElapsedTime() {
        if (startTime == 0) return 0;
        return (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
    }

    // ========== Analysis Methods ==========

    /**
     * Get total execution time in milliseconds
     */
    public long getTotalExecutionTime() {
        if (startTime == 0 || endTime == 0) return 0;
        return (endTime - startTime) / 1_000_000; // Convert to milliseconds
    }

    /**
     * Get memory usage delta
     */
    public long getMemoryDelta() {
        if (memoryBefore == 0 || memoryAfter == 0) return 0;
        return memoryAfter - memoryBefore;
    }

    /**
     * Get most expensive templates
     */
    public List<TemplatePerformanceInfo> getMostExpensiveTemplates(int limit) {
        List<TemplatePerformanceInfo> templateInfo = new ArrayList<>();

        for (Map.Entry<String, Long> entry : templateExecutionTimes.entrySet()) {
            String templateName = entry.getKey();
            long totalTime = entry.getValue();
            int executionCount = templateExecutionCounts.getOrDefault(templateName, 1);
            long avgTime = totalTime / executionCount;

            templateInfo.add(new TemplatePerformanceInfo(templateName, totalTime, avgTime, executionCount));
        }

        // Sort by total execution time (descending)
        templateInfo.sort((a, b) -> Long.compare(b.totalTime(), a.totalTime()));

        return templateInfo.subList(0, Math.min(limit, templateInfo.size()));
    }

    /**
     * Get performance summary
     */
    public String getPerformanceSummary() {
        StringBuilder summary = new StringBuilder();

        summary.append("XSLT Performance Profile\n");
        summary.append("========================\n\n");

        // Overall timing
        summary.append("Execution Overview:\n");
        summary.append(String.format("  Total Time: %d ms\n", getTotalExecutionTime()));
        summary.append(String.format("  Compilation: %d ms (%.1f%%)\n",
                compilationTime, (compilationTime * 100.0) / getTotalExecutionTime()));
        summary.append(String.format("  Transformation: %d ms (%.1f%%)\n",
                transformationTime, (transformationTime * 100.0) / getTotalExecutionTime()));
        summary.append(String.format("  Serialization: %d ms (%.1f%%)\n",
                serializationTime, (serializationTime * 100.0) / getTotalExecutionTime()));

        // Memory analysis
        summary.append("\nMemory Analysis:\n");
        summary.append(String.format("  Memory Before: %s\n", formatBytes(memoryBefore)));
        summary.append(String.format("  Memory After: %s\n", formatBytes(memoryAfter)));
        summary.append(String.format("  Memory Delta: %s\n", formatBytes(getMemoryDelta())));
        summary.append(String.format("  Peak Usage: %s\n", formatBytes(peakMemoryUsage)));

        // Size information
        if (inputSize > 0 && outputSize > 0) {
            summary.append("\nData Processing:\n");
            summary.append(String.format("  Input Size: %s\n", formatBytes(inputSize)));
            summary.append(String.format("  Output Size: %s\n", formatBytes(outputSize)));
            summary.append(String.format("  Throughput: %.2f KB/s\n",
                    (inputSize / 1024.0) / (getTotalExecutionTime() / 1000.0)));
        }

        // Template performance
        if (!templateExecutionTimes.isEmpty()) {
            summary.append("\nTemplate Performance (Top 5):\n");
            List<TemplatePerformanceInfo> topTemplates = getMostExpensiveTemplates(5);
            for (TemplatePerformanceInfo template : topTemplates) {
                summary.append(String.format("  %s: %d ms (%d executions, %.1f ms avg)\n",
                        template.name(), template.totalTime(),
                        template.executionCount(), template.averageTime()));
            }
        }

        return summary.toString();
    }

    /**
     * Get detailed event log
     */
    public String getDetailedEventLog() {
        StringBuilder log = new StringBuilder();

        log.append("XSLT Transformation Event Log\n");
        log.append("==============================\n\n");

        for (ProfileEvent event : events) {
            log.append(String.format("[%6d ms] %s: %s\n",
                    event.timestamp(), event.type().toUpperCase(), event.description()));
        }

        return log.toString();
    }

    /**
     * Check for performance issues
     */
    public List<String> getPerformanceWarnings() {
        List<String> warnings = new ArrayList<>();

        // Check for slow compilation
        if (compilationTime > 1000) {
            warnings.add("XSLT compilation took " + compilationTime + "ms - consider caching compiled stylesheets");
        }

        // Check for memory usage
        if (getMemoryDelta() > 100 * 1024 * 1024) { // 100MB
            warnings.add("High memory usage detected: " + formatBytes(getMemoryDelta()) +
                    " - consider processing in smaller chunks");
        }

        // Check for slow templates
        List<TemplatePerformanceInfo> slowTemplates = getMostExpensiveTemplates(3);
        for (TemplatePerformanceInfo template : slowTemplates) {
            if (template.averageTime() > 100) { // 100ms average
                warnings.add("Template '" + template.name() + "' is slow: " +
                        template.averageTime() + "ms average execution time");
            }
        }

        // Check overall performance
        if (getTotalExecutionTime() > 10000) { // 10 seconds
            warnings.add("Transformation took " + getTotalExecutionTime() +
                    "ms - consider optimization strategies");
        }

        return warnings;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    // ========== Getters and Setters ==========

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getCompilationTime() {
        return compilationTime;
    }

    public long getTransformationTime() {
        return transformationTime;
    }

    public long getSerializationTime() {
        return serializationTime;
    }

    public LocalDateTime getProfiledAt() {
        return profiledAt;
    }

    public long getMemoryBefore() {
        return memoryBefore;
    }

    public long getMemoryAfter() {
        return memoryAfter;
    }

    public long getPeakMemoryUsage() {
        return peakMemoryUsage;
    }

    public int getInputSize() {
        return inputSize;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(int outputSize) {
        this.outputSize = outputSize;
    }

    public Map<String, Long> getTemplateExecutionTimes() {
        return templateExecutionTimes;
    }

    public Map<String, Integer> getTemplateExecutionCounts() {
        return templateExecutionCounts;
    }

    public List<ProfileEvent> getEvents() {
        return events;
    }

    public Map<String, Object> getCustomMetrics() {
        return customMetrics;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public long getCpuTime() {
        return cpuTime;
    }

    public void setCpuTime(long cpuTime) {
        this.cpuTime = cpuTime;
    }

    public int getIoOperations() {
        return ioOperations;
    }

    public void setIoOperations(int ioOperations) {
        this.ioOperations = ioOperations;
    }

    // ========== Inner Classes ==========

    /**
         * Profile event for detailed analysis
         */
        public record ProfileEvent(String type, String description, long timestamp) {
    }

    /**
         * Template performance information
         */
        public record TemplatePerformanceInfo(String name, long totalTime, long averageTime, int executionCount) {
    }
}