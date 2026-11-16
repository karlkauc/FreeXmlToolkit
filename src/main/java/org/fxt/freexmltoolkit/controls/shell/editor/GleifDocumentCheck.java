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

    /** What a check of the active document came to. */
    public enum Kind { OK, FINDINGS, NONE, FAILED }

    /**
     * The outcome of {@link #runOnActive(EditorHost, Consumer)}.
     *
     * @param kind     all LEIs fine, findings in the PROBLEMS panel, no LEI in the document, or GLEIF not asked
     * @param message  the text for a status line or a message
     * @param leis     the number of distinct LEIs
     * @param findings the number of findings
     */
    public record Outcome(Kind kind, String message, int leis, int findings) {

        /** Shows the outcome in a panel's status line (a failure also as message, without an error report). */
        public void showIn(javafx.scene.control.Label status) {
            switch (kind) {
                case OK -> PanelStatus.success(status, message);
                case FINDINGS, NONE -> PanelStatus.info(status, message);
                case FAILED -> PanelStatus.failure(status, I18n.t("IndustryPanel.gleif-check"), message,
                        I18n.t("IndustryPanel.gleif-remedy"), message, false);
            }
        }
    }

    /** @return the outcome of a check (the findings are published separately) */
    static Outcome outcomeOf(GleifCheck.Result result) {
        if (result.failure() != null) {
            return new Outcome(Kind.FAILED, result.failure(), result.leis(), 0);
        }
        if (result.leis() == 0) {
            return new Outcome(Kind.NONE, I18n.t("IndustryPanel.gleif-none"), 0, 0);
        }
        if (result.findings().isEmpty()) {
            return new Outcome(Kind.OK, I18n.t("IndustryPanel.gleif-ok", result.leis()), result.leis(), 0);
        }
        return new Outcome(Kind.FINDINGS, I18n.t("IndustryPanel.gleif-problems", result.leis(), result.findings().size()),
                result.leis(), result.findings().size());
    }

    /**
     * Checks the LEIs of the active document in the background; the findings go to its PROBLEMS panel, {@code done}
     * gets the outcome on the FX thread. Without an active document {@code done} gets NONE at once.
     */
    public static void runOnActive(EditorHost host, java.util.function.Consumer<Outcome> done) {
        String text = host.getActiveText().orElse(null);
        if (text == null) {
            done.accept(new Outcome(Kind.NONE, I18n.t("IndustryPanel.no-document"), 0, 0));
            return;
        }
        EditorHost.ValidationTarget target = host.validationTarget();
        FxtGui.executorService.submit(() -> {
            GleifCheck.Result result = GleifCheck.check(text);
            Platform.runLater(() -> {
                if (result.failure() == null && result.leis() > 0) {
                    publish(host, target, result);
                }
                done.accept(outcomeOf(result));
            });
        });
    }

    /** Tools → Check LEIs at GLEIF: the outcome in the PROBLEMS panel, otherwise as a message. */
    public static void runOnActive(EditorHost host) {
        runOnActive(host, outcome -> {
            switch (outcome.kind()) {
                case FAILED -> DialogHelper.createStyledAlert(Alert.AlertType.WARNING, I18n.t("IndustryPanel.gleif-check"),
                        outcome.message(), I18n.t("IndustryPanel.gleif-remedy")).show();
                case OK, NONE -> DialogHelper.createStyledAlert(Alert.AlertType.INFORMATION, I18n.t("IndustryPanel.gleif-check"),
                        outcome.message(), null).show();
                case FINDINGS -> { } // the PROBLEMS panel opens
            }
        });
    }
}
