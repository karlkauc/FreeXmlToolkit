package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * A single validation problem, unifying XSD (Xerces) and Schematron findings for
 * the Validation activity's problems list.
 *
 * @param source   where it came from ({@code "XSD"} or {@code "Schematron"})
 * @param severity severity label ({@code "error"} / {@code "warning"} / …)
 * @param line     1-based line number, or 0 if unknown
 * @param message  human-readable description
 */
public record ValidationProblem(String source, String severity, int line, String message) {
}
