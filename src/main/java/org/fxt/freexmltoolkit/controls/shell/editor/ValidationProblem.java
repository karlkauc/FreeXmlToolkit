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
 */
public record ValidationProblem(String source, String severity, int line, String message,
                                String ruleId, String context, List<SqfFixSuggestion> fixes) {

    /** Convenience constructor for findings without rule/context details (e.g. XSD). */
    public ValidationProblem(String source, String severity, int line, String message) {
        this(source, severity, line, message, null, null, List.of());
    }

    /** Convenience constructor for findings without quick fixes. */
    public ValidationProblem(String source, String severity, int line, String message,
                             String ruleId, String context) {
        this(source, severity, line, message, ruleId, context, List.of());
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
