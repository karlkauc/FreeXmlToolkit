package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a validation error, warning, or info message with detailed location and context information.
 */
public class XmlValidationError {

    public enum ErrorType {
        FATAL("Fatal", "fatal-error", 4),
        ERROR("Error", "error", 3),
        WARNING("Warning", "warning", 2),
        INFO("Info", "info", 1);

        private final String displayName;
        private final String cssClass;
        private final int severity;

        ErrorType(String displayName, String cssClass, int severity) {
            this.displayName = displayName;
            this.cssClass = cssClass;
            this.severity = severity;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssClass() {
            return cssClass;
        }

        public int getSeverity() {
            return severity;
        }
    }

    private final ErrorType errorType;
    private final int lineNumber;
    private final int columnNumber;
    private final String message;
    private final String suggestion;
    private final LocalDateTime timestamp;

    // Additional context information
    private String elementName;
    private String attributeName;
    private String xpath;
    private String sourceCode; // Code snippet around the error
    private String errorCode; // Error code for categorization
    private String schemaReference; // Reference to relevant schema part

    public XmlValidationError(ErrorType errorType, int lineNumber, int columnNumber,
                              String message, String suggestion) {
        this.errorType = errorType;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.message = message != null ? message : "";
        this.suggestion = suggestion != null ? suggestion : "";
        this.timestamp = LocalDateTime.now();
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private ErrorType errorType = ErrorType.ERROR;
        private int lineNumber = 0;
        private int columnNumber = 0;
        private String message = "";
        private String suggestion = "";
        private String elementName;
        private String attributeName;
        private String xpath;
        private String sourceCode;
        private String errorCode;
        private String schemaReference;

        public Builder type(ErrorType errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder location(int lineNumber, int columnNumber) {
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder element(String elementName) {
            this.elementName = elementName;
            return this;
        }

        public Builder attribute(String attributeName) {
            this.attributeName = attributeName;
            return this;
        }

        public Builder xpath(String xpath) {
            this.xpath = xpath;
            return this;
        }

        public Builder sourceCode(String sourceCode) {
            this.sourceCode = sourceCode;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder schemaReference(String schemaReference) {
            this.schemaReference = schemaReference;
            return this;
        }

        public XmlValidationError build() {
            XmlValidationError error = new XmlValidationError(errorType, lineNumber, columnNumber, message, suggestion);
            error.elementName = this.elementName;
            error.attributeName = this.attributeName;
            error.xpath = this.xpath;
            error.sourceCode = this.sourceCode;
            error.errorCode = this.errorCode;
            error.schemaReference = this.schemaReference;
            return error;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Getters ==========

    public ErrorType getErrorType() {
        return errorType;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getElementName() {
        return elementName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getXpath() {
        return xpath;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSchemaReference() {
        return schemaReference;
    }

    // ========== Setters (for additional context) ==========

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setSchemaReference(String schemaReference) {
        this.schemaReference = schemaReference;
    }

    // ========== Utility Methods ==========

    /**
     * Check if this error has location information
     */
    public boolean hasLocation() {
        return lineNumber > 0 || columnNumber > 0;
    }

    /**
     * Get formatted location string
     */
    public String getLocationString() {
        if (lineNumber > 0 && columnNumber > 0) {
            return String.format("Line %d, Column %d", lineNumber, columnNumber);
        } else if (lineNumber > 0) {
            return String.format("Line %d", lineNumber);
        } else if (columnNumber > 0) {
            return String.format("Column %d", columnNumber);
        } else {
            return "Unknown location";
        }
    }

    /**
     * Get context information (element/attribute if available)
     */
    public String getContextString() {
        StringBuilder context = new StringBuilder();

        if (elementName != null && !elementName.isEmpty()) {
            context.append("Element: ").append(elementName);
            if (attributeName != null && !attributeName.isEmpty()) {
                context.append(", Attribute: ").append(attributeName);
            }
        } else if (attributeName != null && !attributeName.isEmpty()) {
            context.append("Attribute: ").append(attributeName);
        }

        if (xpath != null && !xpath.isEmpty()) {
            if (context.length() > 0) {
                context.append(", ");
            }
            context.append("XPath: ").append(xpath);
        }

        return context.toString();
    }

    /**
     * Get severity level for sorting
     */
    public int getSeverity() {
        return errorType.getSeverity();
    }

    /**
     * Check if this is a critical error
     */
    public boolean isCritical() {
        return errorType == ErrorType.FATAL || errorType == ErrorType.ERROR;
    }

    /**
     * Get CSS class for styling
     */
    public String getCssClass() {
        return errorType.getCssClass();
    }

    /**
     * Get formatted error message for display
     */
    public String getFormattedMessage() {
        StringBuilder formatted = new StringBuilder();

        // Error type and location
        formatted.append("[").append(errorType.getDisplayName()).append("] ");
        if (hasLocation()) {
            formatted.append(getLocationString()).append(": ");
        }

        // Main message
        formatted.append(message);

        // Context information
        String context = getContextString();
        if (!context.isEmpty()) {
            formatted.append(" (").append(context).append(")");
        }

        return formatted.toString();
    }

    /**
     * Get detailed description including suggestion
     */
    public String getDetailedDescription() {
        StringBuilder detailed = new StringBuilder();
        detailed.append(getFormattedMessage());

        if (suggestion != null && !suggestion.isEmpty()) {
            detailed.append("\nSuggestion: ").append(suggestion);
        }

        if (schemaReference != null && !schemaReference.isEmpty()) {
            detailed.append("\nSchema Reference: ").append(schemaReference);
        }

        if (sourceCode != null && !sourceCode.isEmpty()) {
            detailed.append("\nSource: ").append(sourceCode);
        }

        return detailed.toString();
    }

    // ========== Object Methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlValidationError that = (XmlValidationError) o;
        return lineNumber == that.lineNumber &&
                columnNumber == that.columnNumber &&
                errorType == that.errorType &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorType, lineNumber, columnNumber, message);
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }

    // ========== Static Factory Methods ==========

    public static XmlValidationError fatal(int line, int col, String message) {
        return new XmlValidationError(ErrorType.FATAL, line, col, message, "Fix critical XML error");
    }

    public static XmlValidationError error(int line, int col, String message) {
        return new XmlValidationError(ErrorType.ERROR, line, col, message, "Fix XML validation error");
    }

    public static XmlValidationError warning(int line, int col, String message) {
        return new XmlValidationError(ErrorType.WARNING, line, col, message, "Review XML content");
    }

    public static XmlValidationError info(int line, int col, String message) {
        return new XmlValidationError(ErrorType.INFO, line, col, message, "");
    }

    public static XmlValidationError fatal(String message, String suggestion) {
        return new XmlValidationError(ErrorType.FATAL, 0, 0, message, suggestion);
    }

    public static XmlValidationError error(String message, String suggestion) {
        return new XmlValidationError(ErrorType.ERROR, 0, 0, message, suggestion);
    }

    public static XmlValidationError warning(String message, String suggestion) {
        return new XmlValidationError(ErrorType.WARNING, 0, 0, message, suggestion);
    }

    public static XmlValidationError info(String message) {
        return new XmlValidationError(ErrorType.INFO, 0, 0, message, "");
    }
}