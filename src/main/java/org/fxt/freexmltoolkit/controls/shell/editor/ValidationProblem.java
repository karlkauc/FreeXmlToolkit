package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;

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
 * @param fixes    the SQF quick fixes offered for this finding (empty when none)
 * @param column   1-based column number, or 0 if unknown (the jump then goes to the line start)
 */
public record ValidationProblem(String source, String severity, int line, String message,
                                String ruleId, String context, List<SqfFixSuggestion> fixes, int column) {

    /** Constructor for findings without a known column. */
    public ValidationProblem(String source, String severity, int line, String message,
                             String ruleId, String context, List<SqfFixSuggestion> fixes) {
        this(source, severity, line, message, ruleId, context, fixes, 0);
    }

    /** Convenience constructor for findings without rule/context details (e.g. XSD). */
    public ValidationProblem(String source, String severity, int line, String message) {
        this(source, severity, line, message, null, null, List.of());
    }

    /** Convenience constructor for findings without quick fixes. */
    public ValidationProblem(String source, String severity, int line, String message,
                             String ruleId, String context) {
        this(source, severity, line, message, ruleId, context, List.of());
    }

    /** @return a copy of this problem with the given 1-based column (values below 1 mean unknown) */
    public ValidationProblem withColumn(int col) {
        return new ValidationProblem(source, severity, line, message, ruleId, context, fixes, Math.max(0, col));
    }

    /** @return {@code true} when the problem carries Schematron rule/XPath details. */
    public boolean hasDetails() {
        return (ruleId != null && !ruleId.isBlank()) || (context != null && !context.isBlank());
    }

    /** @return {@code true} when SQF quick fixes are offered for this problem. */
    public boolean hasFixes() {
        return fixes != null && !fixes.isEmpty();
    }
}
