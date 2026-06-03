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
        if (schematronText == null || schematronText.isBlank()) {
            return List.of();
        }
        SchematronErrorDetector detector = new SchematronErrorDetector();
        try {
            return detector.detectErrors(schematronText).getAllIssues();
        } catch (Exception e) {
            return List.of();
        } finally {
            detector.dispose();
        }
    }
}
