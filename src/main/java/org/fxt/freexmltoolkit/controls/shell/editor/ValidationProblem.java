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
 * @param link     a web page about the finding (an online lookup's entry at GLEIF, ESMA FIRDS, OpenFIGI), or {@code null}
 * @param category what kind of problem it is (filter and grouping in the PROBLEMS panel), or {@code null}: then
 *                 {@link ProblemCategory#of(ValidationProblem)} derives it
 * @param subject  the security, legal entity or record the problem is about (grouping, collective fixes), or
 *                 {@code null}
 */
public record ValidationProblem(String source, String severity, int line, String message,
                                String ruleId, String context, List<SqfFixSuggestion> fixes, int column, String link,
                                ProblemCategory category, ProblemSubject subject) {

    /** How serious a problem is: {@code "warning"} is a warning, {@code "info"} / {@code "hint"} a hint, else an error. */
    public enum Level { ERROR, WARNING, INFO }

    /** Constructor for findings without category and subject. */
    public ValidationProblem(String source, String severity, int line, String message,
                             String ruleId, String context, List<SqfFixSuggestion> fixes, int column, String link) {
        this(source, severity, line, message, ruleId, context, fixes, column, link, null, null);
    }

    /** Constructor for findings without a link. */
    public ValidationProblem(String source, String severity, int line, String message,
                             String ruleId, String context, List<SqfFixSuggestion> fixes, int column) {
        this(source, severity, line, message, ruleId, context, fixes, column, null);
    }

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
        return new ValidationProblem(source, severity, line, message, ruleId, context, fixes, Math.max(0, col), link,
                category, subject);
    }

    /** @return a copy of this problem with the given web page ({@code null} = none) */
    public ValidationProblem withLink(String url) {
        return new ValidationProblem(source, severity, line, message, ruleId, context, fixes, column, url, category,
                subject);
    }

    /** @return a copy of this problem with the given category */
    public ValidationProblem withCategory(ProblemCategory cat) {
        return new ValidationProblem(source, severity, line, message, ruleId, context, fixes, column, link, cat, subject);
    }

    /** @return a copy of this problem about the given security / entity / record ({@code null} = none) */
    public ValidationProblem withSubject(ProblemSubject subj) {
        return new ValidationProblem(source, severity, line, message, ruleId, context, fixes, column, link, category, subj);
    }

    /** @return a copy of this problem with the given quick fixes */
    public ValidationProblem withFixes(List<SqfFixSuggestion> list) {
        return new ValidationProblem(source, severity, line, message, ruleId, context, list == null ? List.of() : list,
                column, link, category, subject);
    }

    /** @return how serious the problem is ({@code "info"} and {@code "hint"} are hints, unknown labels errors) */
    public Level level() {
        if (severity == null) {
            return Level.ERROR;
        }
        return switch (severity.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "warning", "warn" -> Level.WARNING;
            case "info", "information", "hint", "note" -> Level.INFO;
            default -> Level.ERROR;
        };
    }

    /** @return the category: the one set by the source, else derived from source, rule and message */
    public ProblemCategory effectiveCategory() {
        return category != null ? category : ProblemCategory.of(this);
    }

    /** @return {@code true} when the problem names a web page to open */
    public boolean hasLink() {
        return link != null && !link.isBlank();
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
