package org.fxt.freexmltoolkit.service.sqf;

/**
 * Thrown when a quick fix cannot be computed or applied — e.g. the document
 * changed since validation, the fix's {@code use-when} does not hold, or an
 * activity targets content the text-edit engine cannot handle.
 */
public class SqfExecutionException extends Exception {

    public SqfExecutionException(String message) {
        super(message);
    }

    public SqfExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
