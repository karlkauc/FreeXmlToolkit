package org.fxt.freexmltoolkit.domain;

/**
 * Represents a validation error from XSD schema validation.
 * This class holds detailed information about validation failures including
 * line/column positions and error messages for display in the sidebar.
 *
 * @param lineNumber   The line number where the error occurred (1-based)
 * @param columnNumber The column number where the error occurred (1-based)
 * @param message      The detailed error message
 * @param severity     The severity level of the error (ERROR, WARNING, etc.)
 */
public record ValidationError(
        int lineNumber,
        int columnNumber,
        String message,
        String severity
) {

    /**
     * Compact constructor that validates and normalizes input values.
     */
    public ValidationError {
        // Validation to ensure reasonable values
        if (lineNumber < 0) lineNumber = 0;
        if (columnNumber < 0) columnNumber = 0;
        if (message == null) message = "Unknown validation error";
        if (severity == null) severity = "ERROR";
    }

    /**
     * Creates a ValidationError with default ERROR severity.
     *
     * @param lineNumber   the line number where the error occurred (1-based)
     * @param columnNumber the column number where the error occurred (1-based)
     * @param message      the detailed error message
     */
    public ValidationError(int lineNumber, int columnNumber, String message) {
        this(lineNumber, columnNumber, message, "ERROR");
    }

    /**
     * Returns a formatted string representation for display in lists.
     *
     * @return formatted display text including line/column position and message
     */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();

        if (lineNumber > 0) {
            sb.append("Line ").append(lineNumber);
            if (columnNumber > 0) {
                sb.append(", Col ").append(columnNumber);
            }
            sb.append(": ");
        }

        sb.append(message);

        return sb.toString();
    }

}