package org.fxt.freexmltoolkit.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive result object for XSLT transformation with detailed metadata and debugging information.
 * Provides all information needed for interactive XSLT development and debugging.
 */
public class XsltTransformationResult {

    private static final Logger logger = LogManager.getLogger(XsltTransformationResult.class);
    private static final int DEFAULT_XML_INDENT = 2;

    public enum TransformationStatus {
        SUCCESS("Success", "Transformation completed successfully"),
        ERROR("Error", "Transformation failed with error"),
        WARNING("Warning", "Transformation completed with warnings"),
        TIMEOUT("Timeout", "Transformation timed out");

        private final String displayName;
        private final String description;

        TransformationStatus(String displayName, String description) {
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
    private TransformationStatus status;
    private String outputContent;
    private String errorMessage;
    private long executionTime; // in milliseconds
    private LocalDateTime executedAt;

    // Transformation context
    private XsltTransformationEngine.TransformationContext transformationContext;
    private XsltTransformationEngine.OutputFormat outputFormat;

    // Performance metrics
    private TransformationProfile profile;
    private long compilationTime;
    private long transformationTime;
    private long serializationTime;
    private int inputSize;
    private int outputSize;
    private int memoryUsage; // estimated in bytes

    // Debugging information
    private List<XsltTransformationEngine.TemplateMatchInfo> templateMatches;
    private Map<String, Object> variableValues;
    private List<String> callStack;
    private List<String> warnings;
    private List<TransformationMessage> messages;

    // Output analysis
    private boolean isWellFormed;
    private String detectedOutputFormat;
    private Map<String, String> outputProperties;

    public XsltTransformationResult() {
        this.success = false;
        this.status = TransformationStatus.ERROR;
        this.executedAt = LocalDateTime.now();
        this.templateMatches = new ArrayList<>();
        this.variableValues = new HashMap<>();
        this.callStack = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.outputProperties = new HashMap<>();
    }

    // ========== Factory Methods ==========

    /**
     * Create successful transformation result
     */
    public static XsltTransformationResult success(String outputContent,
                                                   XsltTransformationEngine.OutputFormat outputFormat,
                                                   TransformationProfile profile) {
        XsltTransformationResult result = new XsltTransformationResult();
        result.success = true;
        result.status = TransformationStatus.SUCCESS;
        result.outputContent = outputContent != null ? outputContent : "";
        result.outputFormat = outputFormat;
        result.profile = profile;
        result.outputSize = result.outputContent.length();
        result.detectedOutputFormat = detectOutputFormat(outputContent, outputFormat);

        return result;
    }

    /**
     * Create error result
     */
    public static XsltTransformationResult error(String errorMessage) {
        XsltTransformationResult result = new XsltTransformationResult();
        result.success = false;
        result.status = TransformationStatus.ERROR;
        result.errorMessage = errorMessage;
        result.outputContent = "";

        return result;
    }

    /**
     * Create timeout result
     */
    public static XsltTransformationResult timeout(long executionTime) {
        XsltTransformationResult result = new XsltTransformationResult();
        result.success = false;
        result.status = TransformationStatus.TIMEOUT;
        result.errorMessage = "Transformation timed out after " + executionTime + "ms";
        result.executionTime = executionTime;
        result.outputContent = "";

        return result;
    }

    /**
     * Create warning result (successful but with warnings)
     */
    public static XsltTransformationResult withWarnings(String outputContent,
                                                        XsltTransformationEngine.OutputFormat outputFormat,
                                                        TransformationProfile profile,
                                                        List<String> warnings) {
        XsltTransformationResult result = success(outputContent, outputFormat, profile);
        result.status = TransformationStatus.WARNING;
        result.warnings = new ArrayList<>(warnings);

        return result;
    }

    private static String detectOutputFormat(String content, XsltTransformationEngine.OutputFormat expectedFormat) {
        if (content == null || content.trim().isEmpty()) {
            return "empty";
        }

        String trimmed = content.trim();

        // XML detection
        if (trimmed.startsWith("<?xml") || (trimmed.startsWith("<") && trimmed.endsWith(">"))) {
            return "xml";
        }

        // HTML detection
        if (trimmed.toLowerCase().contains("<!doctype html") ||
                trimmed.toLowerCase().contains("<html")) {
            return "html";
        }

        // JSON detection
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return "json";
        }

        // Default to expected format
        return expectedFormat != null ? expectedFormat.getSaxonMethod() : "text";
    }

    // ========== Result Analysis ==========

    /**
     * Get formatted result summary
     */
    public String getResultSummary() {
        StringBuilder summary = new StringBuilder();

        summary.append(status.getDisplayName());

        if (success) {
            summary.append(" - ").append(formatBytes(outputSize));
            if (executionTime > 0) {
                summary.append(" in ").append(executionTime).append("ms");
            }
            if (!warnings.isEmpty()) {
                summary.append(" (").append(warnings.size()).append(" warnings)");
            }
        } else {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                summary.append(": ").append(errorMessage);
            }
        }

        return summary.toString();
    }

    /**
     * Get detailed transformation statistics
     */
    public String getTransformationStatistics() {
        if (!success) {
            return "Transformation failed - no statistics available";
        }

        StringBuilder stats = new StringBuilder();

        // Basic statistics
        stats.append("Transformation Statistics\n");
        stats.append("========================\n\n");

        stats.append("Status: ").append(status.getDisplayName()).append("\n");
        stats.append("Output Format: ").append(outputFormat != null ? outputFormat.name() : "Unknown").append("\n");
        stats.append("Detected Format: ").append(detectedOutputFormat).append("\n");

        // Timing information
        if (profile != null) {
            stats.append("\nTiming Breakdown:\n");
            stats.append("  Compilation: ").append(profile.getCompilationTime()).append("ms\n");
            stats.append("  Transformation: ").append(profile.getTransformationTime()).append("ms\n");
            stats.append("  Serialization: ").append(profile.getSerializationTime()).append("ms\n");
            stats.append("  Total Time: ").append(executionTime).append("ms\n");
        }

        // Size information
        stats.append("\nSize Information:\n");
        if (inputSize > 0) {
            stats.append("  Input Size: ").append(formatBytes(inputSize)).append("\n");
        }
        stats.append("  Output Size: ").append(formatBytes(outputSize)).append("\n");
        if (inputSize > 0 && outputSize > 0) {
            double ratio = (double) outputSize / inputSize;
            stats.append("  Size Ratio: ").append(String.format("%.2f", ratio)).append("x\n");
        }

        // Template information
        if (!templateMatches.isEmpty()) {
            stats.append("\nTemplate Execution:\n");
            for (XsltTransformationEngine.TemplateMatchInfo template : templateMatches) {
                stats.append("  ").append(template.pattern())
                        .append(" (").append(template.executionTime()).append("ms)\n");
            }
        }

        return stats.toString();
    }

    /**
     * Get debugging information
     */
    public String getDebuggingInfo() {
        if (!success) {
            return "Transformation failed - no debugging info available";
        }

        StringBuilder debug = new StringBuilder();

        debug.append("XSLT Debugging Information\n");
        debug.append("==========================\n\n");

        // Execution context
        if (transformationContext != null) {
            debug.append("Transformation Context:\n");
            debug.append("  Created: ").append(transformationContext.getCreatedAt()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            debug.append("  Parameters: ").append(transformationContext.getParameters().size()).append("\n");
        }

        // Variable values
        if (!variableValues.isEmpty()) {
            debug.append("\nVariable Values:\n");
            for (Map.Entry<String, Object> var : variableValues.entrySet()) {
                debug.append("  ").append(var.getKey()).append(" = ").append(var.getValue()).append("\n");
            }
        }

        // Call stack
        if (!callStack.isEmpty()) {
            debug.append("\nCall Stack:\n");
            for (int i = 0; i < callStack.size(); i++) {
                debug.append("  ").append(i + 1).append(". ").append(callStack.get(i)).append("\n");
            }
        }

        // Messages and warnings
        if (!messages.isEmpty()) {
            debug.append("\nTransformation Messages:\n");
            for (TransformationMessage message : messages) {
                debug.append("  [").append(message.getLevel()).append("] ").append(message.getMessage()).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            debug.append("\nWarnings:\n");
            for (String warning : warnings) {
                debug.append("  - ").append(warning).append("\n");
            }
        }

        return debug.toString();
    }

    /**
     * Get formatted output content with metadata
     */
    public String getFormattedOutput() {
        if (!success) {
            return "Transformation failed:\n" + (errorMessage != null ? errorMessage : "Unknown error");
        }

        if (outputContent == null || outputContent.isEmpty()) {
            return "No output generated";
        }

        // Return output with basic formatting based on detected format
        switch (detectedOutputFormat.toLowerCase()) {
            case "xml":
            case "html":
                return formatXmlContent(outputContent);
            case "json":
                return formatJsonContent(outputContent);
            default:
                return outputContent;
        }
    }

    /**
     * Format XML content with proper indentation.
     * Uses XmlService.prettyFormat for consistent XML formatting.
     *
     * @param content Raw XML content to format
     * @return Formatted XML content, or original content if formatting fails
     */
    private String formatXmlContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        try {
            String formatted = XmlService.prettyFormat(content, DEFAULT_XML_INDENT);
            return formatted != null ? formatted : content;
        } catch (Exception e) {
            logger.warn("Failed to format XML content: {}", e.getMessage());
            return content;
        }
    }

    /**
     * Format JSON content with proper indentation.
     * Uses Gson with pretty printing for consistent JSON formatting.
     *
     * @param content Raw JSON content to format
     * @return Formatted JSON content, or original content if formatting fails
     */
    private String formatJsonContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Object jsonObject = JsonParser.parseString(content);
            return gson.toJson(jsonObject);
        } catch (Exception e) {
            logger.warn("Failed to format JSON content: {}", e.getMessage());
            return content;
        }
    }

    /**
     * Check if output can be previewed (for interactive development)
     */
    public boolean isPreviewable() {
        return success && outputContent != null && !outputContent.trim().isEmpty() &&
                (detectedOutputFormat.equals("html") || detectedOutputFormat.equals("xml"));
    }

    /**
     * Get preview HTML for browser display
     */
    public String getPreviewHtml() {
        if (!isPreviewable()) {
            return "<p>No preview available</p>";
        }

        if (detectedOutputFormat.equals("html")) {
            return outputContent;
        } else if (detectedOutputFormat.equals("xml")) {
            // Convert XML to viewable HTML
            return "<pre style='background-color: #f8f9fa; padding: 10px; border: 1px solid #e9ecef; overflow: auto;'>" +
                    escapeHtml(outputContent) + "</pre>";
        }

        return "<pre>" + escapeHtml(outputContent) + "</pre>";
    }

    private String escapeHtml(String content) {
        return content.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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

    public TransformationStatus getStatus() {
        return status;
    }

    public void setStatus(TransformationStatus status) {
        this.status = status;
    }

    public String getOutputContent() {
        return outputContent;
    }

    public void setOutputContent(String outputContent) {
        this.outputContent = outputContent;
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

    public XsltTransformationEngine.TransformationContext getTransformationContext() {
        return transformationContext;
    }

    public void setTransformationContext(XsltTransformationEngine.TransformationContext transformationContext) {
        this.transformationContext = transformationContext;
    }

    public XsltTransformationEngine.OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(XsltTransformationEngine.OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    public TransformationProfile getProfile() {
        return profile;
    }

    public void setProfile(TransformationProfile profile) {
        this.profile = profile;
    }

    public long getCompilationTime() {
        return compilationTime;
    }

    public void setCompilationTime(long compilationTime) {
        this.compilationTime = compilationTime;
    }

    public long getTransformationTime() {
        return transformationTime;
    }

    public void setTransformationTime(long transformationTime) {
        this.transformationTime = transformationTime;
    }

    public long getSerializationTime() {
        return serializationTime;
    }

    public void setSerializationTime(long serializationTime) {
        this.serializationTime = serializationTime;
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

    public int getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(int memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public List<XsltTransformationEngine.TemplateMatchInfo> getTemplateMatches() {
        return templateMatches;
    }

    public void setTemplateMatches(List<XsltTransformationEngine.TemplateMatchInfo> templateMatches) {
        this.templateMatches = templateMatches;
    }

    public Map<String, Object> getVariableValues() {
        return variableValues;
    }

    public void setVariableValues(Map<String, Object> variableValues) {
        this.variableValues = variableValues;
    }

    public List<String> getCallStack() {
        return callStack;
    }

    public void setCallStack(List<String> callStack) {
        this.callStack = callStack;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<TransformationMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<TransformationMessage> messages) {
        this.messages = messages;
    }

    public boolean isWellFormed() {
        return isWellFormed;
    }

    public void setWellFormed(boolean wellFormed) {
        isWellFormed = wellFormed;
    }

    public String getDetectedOutputFormat() {
        return detectedOutputFormat;
    }

    public void setDetectedOutputFormat(String detectedOutputFormat) {
        this.detectedOutputFormat = detectedOutputFormat;
    }

    public Map<String, String> getOutputProperties() {
        return outputProperties;
    }

    public void setOutputProperties(Map<String, String> outputProperties) {
        this.outputProperties = outputProperties;
    }

    @Override
    public String toString() {
        return String.format("XsltTransformationResult{status=%s, executionTime=%dms, outputSize=%d}",
                status, executionTime, outputSize);
    }

    // ========== Message Class ==========

    /**
     * Transformation message for debugging and analysis
     */
    public static class TransformationMessage {
        private final String level;
        private final String message;
        private final int lineNumber;
        private final String location;

        public TransformationMessage(String level, String message, int lineNumber, String location) {
            this.level = level;
            this.message = message;
            this.lineNumber = lineNumber;
            this.location = location;
        }

        public String getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getLocation() {
            return location;
        }
    }
}