package org.fxt.freexmltoolkit.service.telemetry;

/**
 * A throwable that stands in for another one the application only saw as text (e.g. a trace
 * JavaFX printed): the error telemetry reports the original class name instead of the stand-in's
 * ({@link ErrorSignature}). Only a class name, never the message.
 */
public interface ReportedClassName {

    /** @return the fully-qualified class name of the original exception, or {@code null} if unknown */
    String reportedClassName();
}
