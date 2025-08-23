package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive XML validation result with detailed error reporting and metadata.
 */
public class XmlValidationResult {

    private boolean valid = true;
    private String schemaPath;
    private boolean autoDiscoveredSchema = false;
    private LocalDateTime validationStartTime;
    private long validationDuration; // in milliseconds

    private final List<XmlValidationError> errors = new ArrayList<>();
    private final List<XmlValidationError> warnings = new ArrayList<>();
    private final List<XmlValidationError> info = new ArrayList<>();

    private String xmlContent;
    private int totalLineCount = 0;
    private int totalCharacterCount = 0;
    private boolean isLargeFile = false;

    // Performance metrics
    private long parseTime = 0;
    private long validationTime = 0;
    private int processedChunks = 1;

    public XmlValidationResult() {
        // Default constructor
    }

    /**
     * Create empty result with message
     */
    public static XmlValidationResult createEmpty(String message) {
        XmlValidationResult result = new XmlValidationResult();
        result.addInfo(new XmlValidationError(
                XmlValidationError.ErrorType.INFO,
                0, 0, message, ""
        ));
        return result;
    }

    /**
     * Create successful validation result
     */
    public static XmlValidationResult createSuccess(String schemaPath) {
        XmlValidationResult result = new XmlValidationResult();
        result.setSchemaPath(schemaPath);
        result.addInfo(new XmlValidationError(
                XmlValidationError.ErrorType.INFO,
                0, 0,
                "XML validation successful" + (schemaPath != null ? " against " + schemaPath : ""),
                ""
        ));
        return result;
    }

    // ========== Error Management ==========

    public void addError(XmlValidationError error) {
        if (error.getErrorType() == XmlValidationError.ErrorType.ERROR ||
                error.getErrorType() == XmlValidationError.ErrorType.FATAL) {
            errors.add(error);
            valid = false;
        } else if (error.getErrorType() == XmlValidationError.ErrorType.WARNING) {
            warnings.add(error);
        } else if (error.getErrorType() == XmlValidationError.ErrorType.INFO) {
            info.add(error);
        }
    }

    public void addErrors(List<XmlValidationError> errorList) {
        for (XmlValidationError error : errorList) {
            addError(error);
        }
    }

    public void addWarning(XmlValidationError warning) {
        warnings.add(warning);
    }

    public void addInfo(XmlValidationError info) {
        this.info.add(info);
    }

    // ========== Getters and Setters ==========

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getSchemaPath() {
        return schemaPath;
    }

    public void setSchemaPath(String schemaPath) {
        this.schemaPath = schemaPath;
    }

    public boolean isAutoDiscoveredSchema() {
        return autoDiscoveredSchema;
    }

    public void setAutoDiscoveredSchema(String schemaPath) {
        this.autoDiscoveredSchema = true;
        this.schemaPath = schemaPath;
    }

    public LocalDateTime getValidationStartTime() {
        return validationStartTime;
    }

    public void setValidationStartTime(LocalDateTime validationStartTime) {
        this.validationStartTime = validationStartTime;
    }

    public long getValidationDuration() {
        return validationDuration;
    }

    public void setValidationDuration(long validationDuration) {
        this.validationDuration = validationDuration;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
        if (xmlContent != null) {
            this.totalCharacterCount = xmlContent.length();
            this.totalLineCount = (int) xmlContent.lines().count();
            this.isLargeFile = xmlContent.length() > 50000; // 50KB threshold
        }
    }

    public int getTotalLineCount() {
        return totalLineCount;
    }

    public int getTotalCharacterCount() {
        return totalCharacterCount;
    }

    public boolean isLargeFile() {
        return isLargeFile;
    }

    public long getParseTime() {
        return parseTime;
    }

    public void setParseTime(long parseTime) {
        this.parseTime = parseTime;
    }

    public long getValidationTime() {
        return validationTime;
    }

    public void setValidationTime(long validationTime) {
        this.validationTime = validationTime;
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public void setProcessedChunks(int processedChunks) {
        this.processedChunks = processedChunks;
    }

    // ========== Error Collections ==========

    public List<XmlValidationError> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<XmlValidationError> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public List<XmlValidationError> getInfo() {
        return new ArrayList<>(info);
    }

    /**
     * Get all validation issues (errors, warnings, info) combined
     */
    public List<XmlValidationError> getAllIssues() {
        List<XmlValidationError> allIssues = new ArrayList<>();
        allIssues.addAll(errors);
        allIssues.addAll(warnings);
        allIssues.addAll(info);

        // Sort by line number
        return allIssues.stream()
                .sorted((a, b) -> {
                    int lineCompare = Integer.compare(a.getLineNumber(), b.getLineNumber());
                    if (lineCompare != 0) return lineCompare;
                    return Integer.compare(a.getColumnNumber(), b.getColumnNumber());
                })
                .collect(Collectors.toList());
    }

    /**
     * Get errors by type
     */
    public List<XmlValidationError> getErrorsByType(XmlValidationError.ErrorType type) {
        return getAllIssues().stream()
                .filter(error -> error.getErrorType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Get errors for specific line
     */
    public List<XmlValidationError> getErrorsForLine(int lineNumber) {
        return getAllIssues().stream()
                .filter(error -> error.getLineNumber() == lineNumber)
                .collect(Collectors.toList());
    }

    // ========== Utility Methods ==========

    /**
     * Check if validation has any issues
     */
    public boolean hasIssues() {
        return !errors.isEmpty() || !warnings.isEmpty();
    }

    /**
     * Check if validation has fatal errors
     */
    public boolean hasFatalErrors() {
        return errors.stream()
                .anyMatch(error -> error.getErrorType() == XmlValidationError.ErrorType.FATAL);
    }

    /**
     * Get error count by type
     */
    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }

    public int getInfoCount() {
        return info.size();
    }

    public int getTotalIssueCount() {
        return errors.size() + warnings.size() + info.size();
    }

    /**
     * Get validation summary as formatted string
     */
    public String getValidationSummary() {
        StringBuilder summary = new StringBuilder();

        summary.append("XML Validation Result:\n");
        summary.append("Status: ").append(valid ? "VALID" : "INVALID").append("\n");

        if (schemaPath != null) {
            summary.append("Schema: ").append(schemaPath);
            if (autoDiscoveredSchema) {
                summary.append(" (auto-discovered)");
            }
            summary.append("\n");
        }

        summary.append("Issues: ").append(errors.size()).append(" errors, ")
                .append(warnings.size()).append(" warnings\n");

        if (validationDuration > 0) {
            summary.append("Duration: ").append(validationDuration).append("ms\n");
        }

        if (totalLineCount > 0) {
            summary.append("Document: ").append(totalLineCount).append(" lines, ")
                    .append(totalCharacterCount).append(" characters");
            if (isLargeFile) {
                summary.append(" (large file)");
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * Get detailed validation report
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append(getValidationSummary()).append("\n");

        if (!errors.isEmpty()) {
            report.append("ERRORS:\n");
            for (XmlValidationError error : errors) {
                report.append("  ").append(error.toString()).append("\n");
            }
            report.append("\n");
        }

        if (!warnings.isEmpty()) {
            report.append("WARNINGS:\n");
            for (XmlValidationError warning : warnings) {
                report.append("  ").append(warning.toString()).append("\n");
            }
            report.append("\n");
        }

        if (!info.isEmpty()) {
            report.append("INFO:\n");
            for (XmlValidationError info : this.info) {
                report.append("  ").append(info.toString()).append("\n");
            }
        }

        return report.toString();
    }

    @Override
    public String toString() {
        return String.format("XmlValidationResult{valid=%s, errors=%d, warnings=%d, schema=%s}",
                valid, errors.size(), warnings.size(), schemaPath);
    }
}