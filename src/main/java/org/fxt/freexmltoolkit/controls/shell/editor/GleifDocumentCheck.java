package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.finance.gleif.GleifCheck;
import org.fxt.freexmltoolkit.service.finance.gleif.LeiRecord;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Checks the LEIs of the active document at GLEIF on the user's request (Tools menu, extension panels): the findings
 * replace earlier GLEIF findings in the PROBLEMS panel and keep the others (schema, rules).
 */
public final class GleifDocumentCheck {

    private GleifDocumentCheck() {
    }

    /** @return the problems of the PROBLEMS panel with the GLEIF findings of {@code result} instead of earlier ones */
    public static List<ValidationProblem> merge(List<ValidationProblem> existing, GleifCheck.Result result) {
        List<ValidationProblem> problems = new ArrayList<>(existing.stream()
                .filter(p -> !GleifCheck.SOURCE.equals(p.source())).toList());
        for (GleifCheck.Finding f : result.findings()) {
            problems.add(new ValidationProblem(GleifCheck.SOURCE, f.severity() == LeiRecord.Severity.ERROR ? "error" : "warning",
                    f.line(), f.message(), GleifCheck.RULE, null).withColumn(f.column()));
        }
        return problems;
    }

    /** Shows the result in the PROBLEMS panel of the document the check read. FX thread. */
    public static void publish(EditorHost host, EditorHost.ValidationTarget target, GleifCheck.Result result) {
        List<ValidationProblem> problems = merge(host.isActive(target) ? List.copyOf(host.getActiveProblems()) : List.of(), result);
        int warnings = (int) problems.stream().filter(p -> "warning".equalsIgnoreCase(p.severity())).count();
        int errors = problems.size() - warnings;
        host.publishGleifCheck(target, problems.isEmpty() ? EditorHost.ValidationState.VALID : EditorHost.ValidationState.INVALID,
                problems.size(), summary(errors, warnings), problems);
        if (!result.findings().isEmpty()) {
            host.revealProblems();
        }
    }

    private static String summary(int errors, int warnings) {
        if (errors == 0 && warnings == 0) {
            return I18n.t("IndustryPanel.no-problems");
        }
        return I18n.t("GleifDocumentCheck.summary", errors, warnings);
    }

    /** Tools → Check LEIs at GLEIF: runs in the background, the outcome in the PROBLEMS panel or a message. */
    public static void runOnActive(EditorHost host) {
        String text = host.getActiveText().orElse(null);
        if (text == null) {
            return;
        }
        EditorHost.ValidationTarget target = host.validationTarget();
        FxtGui.executorService.submit(() -> {
            GleifCheck.Result result = GleifCheck.check(text);
            Platform.runLater(() -> {
                if (result.failure() != null) {
                    DialogHelper.createStyledAlert(Alert.AlertType.WARNING, I18n.t("IndustryPanel.gleif-check"),
                            result.failure(), I18n.t("IndustryPanel.gleif-remedy")).show();
                } else if (result.leis() == 0) {
                    DialogHelper.createStyledAlert(Alert.AlertType.INFORMATION, I18n.t("IndustryPanel.gleif-check"),
                            I18n.t("IndustryPanel.gleif-none"), null).show();
                } else {
                    publish(host, target, result);
                    if (result.findings().isEmpty()) {
                        DialogHelper.createStyledAlert(Alert.AlertType.INFORMATION, I18n.t("IndustryPanel.gleif-check"),
                                I18n.t("IndustryPanel.gleif-ok", result.leis()), null).show();
                    }
                }
            });
        });
    }
}
