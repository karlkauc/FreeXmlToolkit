package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * A single validation problem, unifying XSD (Xerces) and Schematron findings for
 * the Validation activity's problems list.
 *
 * @param source   where it came from ({@code "XSD"} or {@code "Schematron"})
 * @param severity severity label ({@code "error"} / {@code "warning"} / …)
 * @param line     1-based line number, or 0 if unknown
 * @param message  human-readable description
 * @param ruleId   the failed rule/test expression (Schematron {@code test}), or {@code null}
 * @param context  the failing node's XPath (Schematron SVRL {@code location}), or {@code null}
 */
public record ValidationProblem(String source, String severity, int line, String message,
                                String ruleId, String context) {

    /** Convenience constructor for findings without rule/context details (e.g. XSD). */
    public ValidationProblem(String source, String severity, int line, String message) {
        this(source, severity, line, message, null, null);
    }

    /** @return {@code true} when the problem carries Schematron rule/XPath details. */
    public boolean hasDetails() {
        return (ruleId != null && !ruleId.isBlank()) || (context != null && !context.isBlank());
    }
}
