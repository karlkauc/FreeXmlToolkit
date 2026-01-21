package org.fxt.freexmltoolkit.service;

/**
 * Exception thrown when a Schematron file cannot be loaded or compiled.
 *
 * <p>This exception is thrown during Schematron schema loading, parsing,
 * or XSLT compilation phases when the Schematron file is invalid or
 * cannot be processed.</p>
 */
public class SchematronLoadException extends Exception {

    /**
     * Constructs a new SchematronLoadException with the specified detail message.
     *
     * @param message the detail message describing the cause of the exception
     */
    public SchematronLoadException(String message) {
        super(message);
    }

    /**
     * Constructs a new SchematronLoadException with the specified detail message and cause.
     *
     * @param message the detail message describing the cause of the exception
     * @param cause   the underlying cause of this exception
     */
    public SchematronLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}