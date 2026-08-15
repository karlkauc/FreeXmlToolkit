package org.fxt.freexmltoolkit.service.sqf;

/**
 * Thrown when a Schematron file cannot be parsed for SQF fix discovery
 * (e.g. it is not well-formed XML).
 */
public class SqfParseException extends Exception {

    public SqfParseException(String message) {
        super(message);
    }

    public SqfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
