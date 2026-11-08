package org.fxt.freexmltoolkit.cli;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of {@code fxt-cli validate}: one {@link FileResult} per checked file. The report
 * writers ({@link ReportWriters}) render it as text, JSON, JUnit XML or SARIF.
 *
 * @param files       the results in the order the files were checked
 * @param totalMillis wall-clock time of the whole run
 */
record ValidationReport(List<FileResult> files, long totalMillis) {

    ValidationReport {
        files = List.copyOf(files);
    }

    /** Severity of a finding; only {@link #ERROR} makes a file invalid (unless warnings fail too). */
    enum Severity {
        ERROR, WARNING, INFO;

        /** Maps the severities of XSD, Schematron ({@code role}/{@code flag}) and JSON Schema findings. */
        static Severity of(String text) {
            if (text == null) {
                return ERROR;
            }
            return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "warning", "warn" -> WARNING;
                case "info", "information", "informational", "note" -> INFO;
                default -> ERROR;
            };
        }
    }

    /**
     * One problem in a file.
     *
     * @param source  what found it: {@code XSD}, {@code Well-formed}, {@code Schematron}, {@code JSON},
     *                {@code JSON Schema}
     * @param line    1-based line, 0 when unknown
     * @param column  1-based column, 0 when unknown
     * @param ruleId  Schematron test / rule id or JSON Schema keyword, may be {@code null}
     * @param context Schematron location or JSON pointer, may be {@code null}
     */
    record Finding(String source, Severity severity, int line, int column, String message,
                   String ruleId, String context) {
    }

    /**
     * The result of one file.
     *
     * @param file     the file as given or found
     * @param schema   the XSD or JSON Schema used (given or found in the document), {@code null} for a
     *                 well-formedness check only
     * @param findings the problems, empty for a valid file
     * @param notRun   why the validation could not run (unreadable file, schema does not load), else
     *                 {@code null}; the reason is also one of the findings
     * @param millis   time spent on this file
     */
    record FileResult(Path file, Path schema, List<Finding> findings, String notRun, long millis) {

        FileResult {
            findings = List.copyOf(findings);
        }

        long count(Severity severity) {
            return findings.stream().filter(f -> f.severity() == severity).count();
        }

        /** @return whether this file fails the run */
        boolean failed(boolean warningsFail) {
            return notRun != null || count(Severity.ERROR) > 0 || (warningsFail && count(Severity.WARNING) > 0);
        }
    }

    long count(Severity severity) {
        return files.stream().mapToLong(f -> f.count(severity)).sum();
    }

    long failedFiles(boolean warningsFail) {
        return files.stream().filter(f -> f.failed(warningsFail)).count();
    }

    boolean anyNotRun() {
        return files.stream().anyMatch(f -> f.notRun() != null);
    }

    /** The exit code for this report (see {@link ExitCodes}). */
    int exitCode(boolean warningsFail) {
        if (anyNotRun()) {
            return ExitCodes.NOT_RUN;
        }
        return failedFiles(warningsFail) > 0 ? ExitCodes.FAILED : ExitCodes.OK;
    }
}
