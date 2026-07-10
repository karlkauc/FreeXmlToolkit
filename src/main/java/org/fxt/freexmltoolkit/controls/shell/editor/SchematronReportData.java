package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;

/**
 * Everything the detailed Schematron report needs from one validation run:
 * the validated document's display name, the rules file, the findings (with
 * rule/test and XPath context), and the raw SVRL report.
 *
 * @param documentName   display name of the validated document, or {@code null}
 * @param schematronFile the Schematron rules file that was applied
 * @param problems       the Schematron findings of the run (possibly empty = all rules passed)
 * @param svrl           the raw SVRL XML, or {@code null} when unavailable
 */
public record SchematronReportData(String documentName, File schematronFile,
                                   List<ValidationProblem> problems, String svrl) {

    /** @return the number of error-severity findings. */
    public long errorCount() {
        return problems.stream().filter(p -> !isWarning(p)).count();
    }

    /** @return the number of warning-severity findings. */
    public long warningCount() {
        return problems.stream().filter(SchematronReportData::isWarning).count();
    }

    private static boolean isWarning(ValidationProblem p) {
        return "warning".equalsIgnoreCase(p.severity());
    }
}
