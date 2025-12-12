package org.fxt.freexmltoolkit.service.xsd;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Exception thrown when XSD parsing fails.
 * Provides detailed information about the failure including location and cause.
 */
public class XsdParseException extends Exception {

    private final Path sourceFile;
    private final String schemaLocation;
    private final int lineNumber;
    private final int columnNumber;
    private final ErrorType errorType;

    /**
     * Types of XSD parsing errors.
     */
    public enum ErrorType {
        /** File not found or inaccessible */
        FILE_NOT_FOUND,
        /** XML is not well-formed */
        MALFORMED_XML,
        /** XSD schema is invalid */
        INVALID_SCHEMA,
        /** Circular include/import detected */
        CIRCULAR_REFERENCE,
        /** Referenced schema could not be resolved */
        UNRESOLVED_REFERENCE,
        /** Network error when fetching remote schema */
        NETWORK_ERROR,
        /** Maximum include depth exceeded */
        MAX_DEPTH_EXCEEDED,
        /** General parsing error */
        PARSE_ERROR
    }

    /**
     * Creates a new XsdParseException with a message.
     *
     * @param message the error message
     */
    public XsdParseException(String message) {
        super(message);
        this.sourceFile = null;
        this.schemaLocation = null;
        this.lineNumber = -1;
        this.columnNumber = -1;
        this.errorType = ErrorType.PARSE_ERROR;
    }

    /**
     * Creates a new XsdParseException with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public XsdParseException(String message, Throwable cause) {
        super(message, cause);
        this.sourceFile = null;
        this.schemaLocation = null;
        this.lineNumber = -1;
        this.columnNumber = -1;
        this.errorType = ErrorType.PARSE_ERROR;
    }

    /**
     * Creates a new XsdParseException with full details.
     *
     * @param message        the error message
     * @param cause          the underlying cause (may be null)
     * @param sourceFile     the file being parsed (may be null)
     * @param schemaLocation the schema location reference (may be null)
     * @param lineNumber     the line number (-1 if unknown)
     * @param columnNumber   the column number (-1 if unknown)
     * @param errorType      the type of error
     */
    public XsdParseException(String message, Throwable cause, Path sourceFile,
                             String schemaLocation, int lineNumber, int columnNumber,
                             ErrorType errorType) {
        super(message, cause);
        this.sourceFile = sourceFile;
        this.schemaLocation = schemaLocation;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.errorType = errorType;
    }

    /**
     * Creates a file not found exception.
     *
     * @param path the path that was not found
     * @return the exception
     */
    public static XsdParseException fileNotFound(Path path) {
        return new XsdParseException(
                "XSD file not found: " + path,
                null,
                path,
                null,
                -1, -1,
                ErrorType.FILE_NOT_FOUND
        );
    }

    /**
     * Creates a circular reference exception.
     *
     * @param sourceFile     the file containing the circular reference
     * @param schemaLocation the schema location that creates the cycle
     * @return the exception
     */
    public static XsdParseException circularReference(Path sourceFile, String schemaLocation) {
        return new XsdParseException(
                "Circular reference detected: " + schemaLocation + " in " + sourceFile,
                null,
                sourceFile,
                schemaLocation,
                -1, -1,
                ErrorType.CIRCULAR_REFERENCE
        );
    }

    /**
     * Creates an unresolved reference exception.
     *
     * @param sourceFile     the file containing the reference
     * @param schemaLocation the schema location that could not be resolved
     * @return the exception
     */
    public static XsdParseException unresolvedReference(Path sourceFile, String schemaLocation) {
        return new XsdParseException(
                "Cannot resolve schema reference: " + schemaLocation + " from " + sourceFile,
                null,
                sourceFile,
                schemaLocation,
                -1, -1,
                ErrorType.UNRESOLVED_REFERENCE
        );
    }

    /**
     * Creates a max depth exceeded exception.
     *
     * @param depth    the depth that was exceeded
     * @param maxDepth the maximum allowed depth
     * @return the exception
     */
    public static XsdParseException maxDepthExceeded(int depth, int maxDepth) {
        return new XsdParseException(
                "Maximum include depth exceeded: " + depth + " (max: " + maxDepth + ")",
                null,
                null,
                null,
                -1, -1,
                ErrorType.MAX_DEPTH_EXCEEDED
        );
    }

    /**
     * Creates a malformed XML exception.
     *
     * @param sourceFile the file with malformed XML
     * @param cause      the underlying SAX/DOM exception
     * @param line       the line number
     * @param column     the column number
     * @return the exception
     */
    public static XsdParseException malformedXml(Path sourceFile, Throwable cause,
                                                  int line, int column) {
        return new XsdParseException(
                "Malformed XML in " + sourceFile + " at line " + line + ", column " + column,
                cause,
                sourceFile,
                null,
                line, column,
                ErrorType.MALFORMED_XML
        );
    }

    /**
     * Creates a network error exception.
     *
     * @param url   the URL that failed
     * @param cause the underlying IO exception
     * @return the exception
     */
    public static XsdParseException networkError(String url, Throwable cause) {
        return new XsdParseException(
                "Failed to fetch remote schema: " + url,
                cause,
                null,
                url,
                -1, -1,
                ErrorType.NETWORK_ERROR
        );
    }

    /**
     * @return the source file being parsed, if available
     */
    public Optional<Path> getSourceFile() {
        return Optional.ofNullable(sourceFile);
    }

    /**
     * @return the schema location reference, if available
     */
    public Optional<String> getSchemaLocation() {
        return Optional.ofNullable(schemaLocation);
    }

    /**
     * @return the line number, or -1 if unknown
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * @return the column number, or -1 if unknown
     */
    public int getColumnNumber() {
        return columnNumber;
    }

    /**
     * @return the type of error
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * @return true if line/column information is available
     */
    public boolean hasLocation() {
        return lineNumber >= 0 && columnNumber >= 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("XsdParseException[").append(errorType).append("]: ");
        sb.append(getMessage());

        if (sourceFile != null) {
            sb.append(" (file: ").append(sourceFile);
            if (hasLocation()) {
                sb.append(", line: ").append(lineNumber);
                sb.append(", column: ").append(columnNumber);
            }
            sb.append(")");
        }

        return sb.toString();
    }
}
