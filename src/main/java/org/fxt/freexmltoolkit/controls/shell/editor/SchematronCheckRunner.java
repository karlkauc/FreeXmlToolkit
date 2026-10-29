package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;

/**
 * UI-free Schematron rule checker for the shell: runs the legacy
 * {@link SchematronErrorDetector} (XML-syntax / structural / XPath / semantic / best-practice checks)
 * over a Schematron document and returns the combined issue list. Run off the UI thread.
 */
public final class SchematronCheckRunner {

    private SchematronCheckRunner() {
    }

    /**
     * @param schematronText the Schematron document text (may be {@code null}/blank)
     * @return all detected issues (errors + warnings + infos), or an empty list for blank input
     */
    public static List<SchematronError> check(String schematronText) {
        return checkResult(schematronText).issues();
    }

    /**
     * The checker's outcome: the issues found, or why the checker itself could not run.
     *
     * @param issues  the detected issues (empty when the checker failed)
     * @param failure the checker's own failure, classified for telemetry ({@code null} = it ran)
     * @param error   the cause behind {@code failure} (for the dialog), or {@code null}
     */
    public record CheckResult(List<SchematronError> issues, org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure, Throwable error) {
        public boolean failed() {
            return failure != null;
        }
    }

    /** As {@link #check(String)}, additionally reporting a failure of the checker itself. */
    public static CheckResult checkResult(String schematronText) {
        if (schematronText == null || schematronText.isBlank()) {
            return new CheckResult(List.of(), null, null);
        }
        SchematronErrorDetector detector = new SchematronErrorDetector();
        try {
            return new CheckResult(detector.detectErrors(schematronText).getAllIssues(), null, null);
        } catch (Exception e) {
            return new CheckResult(List.of(), org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e), e);
        } finally {
            detector.dispose();
        }
    }
}
