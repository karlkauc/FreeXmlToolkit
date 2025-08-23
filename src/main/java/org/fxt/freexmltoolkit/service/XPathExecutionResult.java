package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive result object for XPath/XQuery execution with detailed metadata and performance metrics.
 */
public class XPathExecutionResult {

    public enum ResultType {
        STRING("String", "Text value"),
        NUMBER("Number", "Numeric value"),
        BOOLEAN("Boolean", "True/false value"),
        NODE("Node", "XML node"),
        NODESET("NodeSet", "Collection of XML nodes"),
        SEQUENCE("Sequence", "Sequence of items"),
        OTHER("Other", "Other result type"),
        ERROR("Error", "Execution error");

        private final String displayName;
        private final String description;

        ResultType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    // Execution metadata
    private boolean success;
    private String snippetId;
    private String query;
    private String errorMessage;
    private long executionTime; // in milliseconds
    private LocalDateTime executedAt;

    // Result data
    private List<ResultItem> resultItems;
    private int resultCount;
    private boolean truncated;
    private ResultType primaryResultType;

    // Performance metrics
    private long parseTime;
    private long compilationTime;
    private long evaluationTime;
    private int memoryUsage; // estimated in bytes

    // Query analysis
    private String queryType; // "XPath", "XQuery", "FLWOR", etc.
    private boolean usedAdvancedFeatures;
    private List<String> usedFunctions;
    private List<String> namespacesUsed;

    public XPathExecutionResult() {
        this.resultItems = new ArrayList<>();
        this.usedFunctions = new ArrayList<>();
        this.namespacesUsed = new ArrayList<>();
        this.success = false;
        this.resultCount = 0;
        this.truncated = false;
    }

    // ========== Factory Methods ==========

    /**
     * Create successful result
     */
    public static XPathExecutionResult success(String query, List<ResultItem> items) {
        XPathExecutionResult result = new XPathExecutionResult();
        result.success = true;
        result.query = query;
        result.resultItems = items != null ? items : new ArrayList<>();
        result.resultCount = result.resultItems.size();
        result.primaryResultType = determinePrimaryType(result.resultItems);
        return result;
    }

    /**
     * Create error result
     */
    public static XPathExecutionResult error(String errorMessage) {
        XPathExecutionResult result = new XPathExecutionResult();
        result.success = false;
        result.errorMessage = errorMessage;
        result.primaryResultType = ResultType.ERROR;
        return result;
    }

    /**
     * Create empty result
     */
    public static XPathExecutionResult empty(String query) {
        XPathExecutionResult result = new XPathExecutionResult();
        result.success = true;
        result.query = query;
        result.resultCount = 0;
        result.primaryResultType = ResultType.SEQUENCE;
        return result;
    }

    private static ResultType determinePrimaryType(List<ResultItem> items) {
        if (items == null || items.isEmpty()) {
            return ResultType.SEQUENCE;
        }

        ResultType firstType = items.get(0).getType();

        // If all items have the same type, return that type
        boolean allSameType = items.stream().allMatch(item -> item.getType() == firstType);
        if (allSameType) {
            return firstType == ResultType.NODE ? ResultType.NODESET : firstType;
        }

        return ResultType.SEQUENCE; // Mixed types
    }

    // ========== Result Analysis ==========

    /**
     * Get formatted result summary
     */
    public String getResultSummary() {
        if (!success) {
            return "Error: " + errorMessage;
        }

        if (resultCount == 0) {
            return "No results";
        }

        StringBuilder summary = new StringBuilder();
        summary.append(resultCount).append(" result");
        if (resultCount != 1) {
            summary.append("s");
        }

        if (primaryResultType != null) {
            summary.append(" (").append(primaryResultType.getDisplayName()).append(")");
        }

        if (truncated) {
            summary.append(" - truncated");
        }

        if (executionTime > 0) {
            summary.append(" - ").append(executionTime).append("ms");
        }

        return summary.toString();
    }

    /**
     * Get formatted execution statistics
     */
    public String getExecutionStatistics() {
        if (!success) {
            return "Execution failed";
        }

        StringBuilder stats = new StringBuilder();
        stats.append("Execution time: ").append(executionTime).append("ms");

        if (parseTime > 0 || compilationTime > 0 || evaluationTime > 0) {
            stats.append("\n");
            if (parseTime > 0) {
                stats.append("Parse: ").append(parseTime).append("ms ");
            }
            if (compilationTime > 0) {
                stats.append("Compile: ").append(compilationTime).append("ms ");
            }
            if (evaluationTime > 0) {
                stats.append("Evaluate: ").append(evaluationTime).append("ms");
            }
        }

        if (memoryUsage > 0) {
            stats.append("\nMemory: ").append(formatBytes(memoryUsage));
        }

        if (!usedFunctions.isEmpty()) {
            stats.append("\nFunctions used: ").append(String.join(", ", usedFunctions));
        }

        return stats.toString();
    }

    /**
     * Get detailed result report
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();

        report.append("XPath/XQuery Execution Result\n");
        report.append("=====================================\n\n");

        // Basic info
        report.append("Query: ").append(query != null ? query : "N/A").append("\n");
        report.append("Status: ").append(success ? "SUCCESS" : "ERROR").append("\n");

        if (executedAt != null) {
            report.append("Executed: ").append(executedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        }

        if (success) {
            report.append("Results: ").append(resultCount).append(" items");
            if (truncated) {
                report.append(" (truncated)");
            }
            report.append("\n");

            report.append("Type: ").append(primaryResultType != null ? primaryResultType.getDisplayName() : "Unknown").append("\n");

        } else {
            report.append("Error: ").append(errorMessage != null ? errorMessage : "Unknown error").append("\n");
        }

        // Performance metrics
        report.append("\nPerformance:\n");
        report.append("  Total time: ").append(executionTime).append("ms\n");
        if (parseTime > 0) {
            report.append("  Parse time: ").append(parseTime).append("ms\n");
        }
        if (compilationTime > 0) {
            report.append("  Compilation time: ").append(compilationTime).append("ms\n");
        }
        if (evaluationTime > 0) {
            report.append("  Evaluation time: ").append(evaluationTime).append("ms\n");
        }

        // Query analysis
        if (queryType != null || usedAdvancedFeatures) {
            report.append("\nQuery Analysis:\n");
            if (queryType != null) {
                report.append("  Type: ").append(queryType).append("\n");
            }
            report.append("  Advanced features: ").append(usedAdvancedFeatures ? "Yes" : "No").append("\n");
        }

        if (!usedFunctions.isEmpty()) {
            report.append("  Functions: ").append(String.join(", ", usedFunctions)).append("\n");
        }

        if (!namespacesUsed.isEmpty()) {
            report.append("  Namespaces: ").append(String.join(", ", namespacesUsed)).append("\n");
        }

        // Result preview
        if (success && !resultItems.isEmpty()) {
            report.append("\nResult Preview:\n");
            int previewCount = Math.min(5, resultItems.size());
            for (int i = 0; i < previewCount; i++) {
                ResultItem item = resultItems.get(i);
                report.append("  [").append(i + 1).append("] ");

                if (item.getNodeName() != null && !item.getNodeName().isEmpty()) {
                    report.append(item.getNodeName()).append(": ");
                }

                String value = item.getValue();
                if (value != null && value.length() > 100) {
                    value = value.substring(0, 97) + "...";
                }
                report.append(value).append("\n");
            }

            if (resultItems.size() > previewCount) {
                report.append("  ... and ").append(resultItems.size() - previewCount).append(" more items\n");
            }
        }

        return report.toString();
    }

    /**
     * Check if result has nodes
     */
    public boolean hasNodes() {
        return resultItems.stream().anyMatch(item ->
                item.getType() == ResultType.NODE || primaryResultType == ResultType.NODESET);
    }

    /**
     * Get all text values from result
     */
    public List<String> getTextValues() {
        List<String> values = new ArrayList<>();
        for (ResultItem item : resultItems) {
            if (item.getValue() != null) {
                values.add(item.getValue());
            }
        }
        return values;
    }

    /**
     * Get result as single string (for simple results)
     */
    public String getAsString() {
        if (!success) {
            return errorMessage;
        }

        if (resultItems.isEmpty()) {
            return "";
        }

        if (resultItems.size() == 1) {
            return resultItems.get(0).getValue();
        }

        // Multiple items - join with newlines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < resultItems.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(resultItems.get(i).getValue());
        }
        return sb.toString();
    }

    private String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    // ========== Getters and Setters ==========

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSnippetId() {
        return snippetId;
    }

    public void setSnippetId(String snippetId) {
        this.snippetId = snippetId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public List<ResultItem> getResultItems() {
        return resultItems;
    }

    public void setResultItems(List<ResultItem> resultItems) {
        this.resultItems = resultItems != null ? resultItems : new ArrayList<>();
        this.resultCount = this.resultItems.size();
        this.primaryResultType = determinePrimaryType(this.resultItems);
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public ResultType getPrimaryResultType() {
        return primaryResultType;
    }

    public void setPrimaryResultType(ResultType primaryResultType) {
        this.primaryResultType = primaryResultType;
    }

    public long getParseTime() {
        return parseTime;
    }

    public void setParseTime(long parseTime) {
        this.parseTime = parseTime;
    }

    public long getCompilationTime() {
        return compilationTime;
    }

    public void setCompilationTime(long compilationTime) {
        this.compilationTime = compilationTime;
    }

    public long getEvaluationTime() {
        return evaluationTime;
    }

    public void setEvaluationTime(long evaluationTime) {
        this.evaluationTime = evaluationTime;
    }

    public int getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(int memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public boolean isUsedAdvancedFeatures() {
        return usedAdvancedFeatures;
    }

    public void setUsedAdvancedFeatures(boolean usedAdvancedFeatures) {
        this.usedAdvancedFeatures = usedAdvancedFeatures;
    }

    public List<String> getUsedFunctions() {
        return usedFunctions;
    }

    public void setUsedFunctions(List<String> usedFunctions) {
        this.usedFunctions = usedFunctions;
    }

    public List<String> getNamespacesUsed() {
        return namespacesUsed;
    }

    public void setNamespacesUsed(List<String> namespacesUsed) {
        this.namespacesUsed = namespacesUsed;
    }

    @Override
    public String toString() {
        return String.format("XPathExecutionResult{success=%s, resultCount=%d, executionTime=%dms}",
                success, resultCount, executionTime);
    }

    // ========== Result Item Class ==========

    /**
     * Individual result item from XPath/XQuery execution
     */
    public static class ResultItem {
        private ResultType type;
        private String value;
        private String nodeName;
        private String nodeType;
        private int position; // Position in result sequence
        private String xpath; // XPath to this node (if applicable)

        public ResultItem() {
        }

        public ResultItem(ResultType type, String value) {
            this.type = type;
            this.value = value;
        }

        public ResultItem(ResultType type, String value, String nodeName, String nodeType) {
            this(type, value);
            this.nodeName = nodeName;
            this.nodeType = nodeType;
        }

        /**
         * Get formatted display string for this item
         */
        public String getDisplayString() {
            StringBuilder display = new StringBuilder();

            if (nodeName != null && !nodeName.isEmpty()) {
                display.append("[").append(nodeName).append("] ");
            }

            if (value != null) {
                String displayValue = value;
                if (displayValue.length() > 200) {
                    displayValue = displayValue.substring(0, 197) + "...";
                }
                display.append(displayValue);
            }

            return display.toString();
        }

        /**
         * Get tooltip text for this item
         */
        public String getTooltipText() {
            StringBuilder tooltip = new StringBuilder();

            if (type != null) {
                tooltip.append("Type: ").append(type.getDisplayName()).append("\n");
            }

            if (nodeName != null && !nodeName.isEmpty()) {
                tooltip.append("Node: ").append(nodeName).append("\n");
            }

            if (nodeType != null && !nodeType.isEmpty()) {
                tooltip.append("Node Type: ").append(nodeType).append("\n");
            }

            if (xpath != null && !xpath.isEmpty()) {
                tooltip.append("XPath: ").append(xpath).append("\n");
            }

            if (value != null) {
                tooltip.append("Value: ").append(value);
            }

            return tooltip.toString();
        }

        // Getters and Setters
        public ResultType getType() {
            return type;
        }

        public void setType(ResultType type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public String getXpath() {
            return xpath;
        }

        public void setXpath(String xpath) {
            this.xpath = xpath;
        }

        @Override
        public String toString() {
            return String.format("ResultItem{type=%s, value='%s', nodeName='%s'}",
                    type, value, nodeName);
        }
    }
}