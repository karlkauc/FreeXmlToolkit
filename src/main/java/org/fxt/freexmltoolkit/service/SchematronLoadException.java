package org.fxt.freexmltoolkit.service;

/**
 * Exception thrown when a Schematron file cannot be loaded or compiled.
 */
public class SchematronLoadException extends Exception {
    
    public SchematronLoadException(String message) {
        super(message);
    }
    
    public SchematronLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}