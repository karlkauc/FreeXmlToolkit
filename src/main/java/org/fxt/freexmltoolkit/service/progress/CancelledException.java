package org.fxt.freexmltoolkit.service.progress;

/** Ends a job the user cancelled ({@link Progress#checkCancelled()}). Not an error: never reported as one. */
public final class CancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CancelledException() {
        super("cancelled", null, false, false);
    }

    /** A cancellation noticed through {@code cause} (an interrupted wait). */
    public CancelledException(Throwable cause) {
        super("cancelled", cause, false, false);
    }
}
