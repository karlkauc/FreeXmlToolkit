package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.finance.IdentifierCheck;
import org.fxt.freexmltoolkit.service.progress.Progress;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Checks the identifiers of the active document online on the user's request ("Check identifiers online": LEIs at
 * GLEIF, ISINs at ESMA FIRDS / OpenFIGI - each when allowed). The findings replace earlier online findings in the
 * PROBLEMS panel and keep the others (schema, rules). One implementation behind the industry, FundsXML and
 * PK-Meldewesen panels and the Tools menu.
 */
public final class IdentifierDocumentCheck {

    /** The window of a running Tools-menu check (one at a time), or {@code null}. FX thread. */
    private static javafx.stage.Stage running;

    private IdentifierDocumentCheck() {
    }

    /** @return the problems of the PROBLEMS panel with the findings of {@code result} instead of earlier online ones */
    public static List<ValidationProblem> merge(List<ValidationProblem> existing, IdentifierCheck.Result result) {
        List<ValidationProblem> problems = new ArrayList<>(existing.stream()
                .filter(p -> !IdentifierCheck.SOURCES.contains(p.source())).toList());
        problems.addAll(ValidationRunner.toProblems(new IdentifierCheck.Result(result.leis(), result.isins(),
                result.findings(), List.of(), result.leiRecords(), result.isinRecords(), result.examples())));
        return problems;
    }

    /** Shows the result in the PROBLEMS panel of the document the check read. FX thread. */
    public static void publish(EditorHost host, EditorHost.ValidationTarget target, IdentifierCheck.Result result) {
        List<ValidationProblem> problems = merge(host.isActive(target) ? List.copyOf(host.getActiveProblems()) : List.of(), result);
        int warnings = (int) problems.stream().filter(p -> "warning".equalsIgnoreCase(p.severity())).count();
        int errors = problems.size() - warnings;
        host.publishIdentifierCheck(target, problems.isEmpty() ? EditorHost.ValidationState.VALID : EditorHost.ValidationState.INVALID,
                problems.size(), errors == 0 && warnings == 0 ? I18n.t("IndustryPanel.no-problems")
                        : I18n.t("IdentifierDocumentCheck.summary", errors, warnings), problems);
        if (!result.findings().isEmpty()) {
            host.revealProblems();
        }
    }

    /** What a check of the active document came to. */
    public enum Kind { OK, FINDINGS, NONE, FAILED, CANCELLED }

    /**
     * The outcome of {@link #runOnActive(EditorHost, Consumer)}.
     *
     * @param kind     all fine, findings in the PROBLEMS panel, no identifier in the document, or no service reached
     * @param message  the text for a status line or a message (with a failed service's reason when the other one ran)
     * @param leis     the number of distinct LEIs
     * @param isins    the number of distinct ISINs
     * @param findings the number of findings
     */
    public record Outcome(Kind kind, String message, int leis, int isins, int findings) {

        /** Shows the outcome in a panel's status line (a failure also as message, without an error report). */
        public void showIn(javafx.scene.control.Label status) {
            switch (kind) {
                case OK -> PanelStatus.success(status, message);
                case FINDINGS, NONE, CANCELLED -> PanelStatus.info(status, message);
                case FAILED -> PanelStatus.failure(status, I18n.t("IdentifierDocumentCheck.title"), message,
                        I18n.t("IdentifierDocumentCheck.remedy"), message, false);
            }
        }
    }

    /** @return the outcome of a check (the findings are published separately) */
    static Outcome outcomeOf(IdentifierCheck.Result result) {
        if (result.cancelled()) {
            int all = result.leis() + result.isins();
            String message = I18n.t("IdentifierDocumentCheck.cancelled", Math.max(0, all - result.unchecked()), all)
                    + (result.findings().isEmpty() ? "" : " " + I18n.t("IdentifierDocumentCheck.cancelled-findings",
                    result.findings().size()));
            return new Outcome(Kind.CANCELLED, message, result.leis(), result.isins(), result.findings().size());
        }
        String failures = String.join(" ", result.failures().stream().map(IdentifierCheck.Failure::message).toList());
        boolean asked = result.leis() + result.isins() > 0;
        int askedServices = (result.leis() > 0 ? 1 : 0) + (result.isins() > 0 ? 1 : 0);
        if (asked && result.failures().size() >= askedServices) { // every service asked failed
            return new Outcome(Kind.FAILED, failures, result.leis(), result.isins(), 0);
        }
        if (!asked) {
            return new Outcome(Kind.NONE, result.examples() > 0
                    ? I18n.t("IdentifierDocumentCheck.only-examples", result.examples())
                    : I18n.t("IdentifierDocumentCheck.none"), 0, 0, 0);
        }
        String counted = I18n.t("IdentifierDocumentCheck.counted", result.leis(), result.isins());
        String tail = (failures.isEmpty() ? "" : " - " + failures)
                + (result.examples() > 0 ? " " + I18n.t("IdentifierDocumentCheck.examples", result.examples()) : "");
        if (result.findings().isEmpty()) {
            return new Outcome(failures.isEmpty() ? Kind.OK : Kind.FINDINGS,
                    I18n.t("IdentifierDocumentCheck.ok", counted) + tail, result.leis(), result.isins(), 0);
        }
        return new Outcome(Kind.FINDINGS, I18n.t("IdentifierDocumentCheck.problems", counted, result.findings().size()) + tail,
                result.leis(), result.isins(), result.findings().size());
    }

    /**
     * Checks the identifiers of the active document in the background; the findings go to its PROBLEMS panel,
     * {@code done} gets the outcome on the FX thread.
     */
    public static void runOnActive(EditorHost host, Consumer<Outcome> done) {
        runOnActive(host, Progress.NONE, done);
    }

    /**
     * Like {@link #runOnActive(EditorHost, Consumer)}, reporting to {@code progress} (a panel's {@link PanelJobs}
     * job); a cancelled check publishes the findings of what was answered and ends with {@link Kind#CANCELLED}.
     */
    public static void runOnActive(EditorHost host, Progress progress, Consumer<Outcome> done) {
        String text = host.getActiveText().orElse(null);
        if (text == null) {
            done.accept(new Outcome(Kind.NONE, I18n.t("IndustryPanel.no-document"), 0, 0, 0));
            return;
        }
        EditorHost.ValidationTarget target = host.validationTarget();
        FxtGui.executorService.submit(() -> {
            IdentifierCheck.Result result = IdentifierCheck.check(text, true, true, progress);
            Platform.runLater(() -> {
                if (result.leis() + result.isins() > 0) {
                    publish(host, target, result);
                }
                done.accept(outcomeOf(result));
            });
        });
    }

    /**
     * Tools → Check Identifiers Online: a small window shows the progress (with Cancel) while it runs; the outcome
     * goes to the PROBLEMS panel, otherwise to a message.
     */
    public static void runOnActive(EditorHost host) {
        if (running != null) {
            running.show();
            running.toFront();
            return;
        }
        PanelProgress view = new PanelProgress();
        view.setPrefWidth(480);
        PanelJobs jobs = new PanelJobs(view);
        PanelJobs.Job job = jobs.start("identifiers", I18n.t("IdentifierDocumentCheck.title"), true);
        javafx.scene.control.Dialog<Void> dialog = DialogHelper.createDialog(I18n.t("IdentifierDocumentCheck.title"),
                I18n.t("IdentifierDocumentCheck.running"), "bi-globe");
        dialog.initModality(javafx.stage.Modality.NONE);
        dialog.getDialogPane().setContent(view);
        dialog.getDialogPane().getButtonTypes().add(new javafx.scene.control.ButtonType(
                I18n.t("IdentifierDocumentCheck.hide"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        // Hide keeps the check running (Tools → Check Identifiers Online shows the window again); Cancel stops it
        running = (javafx.stage.Stage) dialog.getDialogPane().getScene().getWindow();
        dialog.show();
        runOnActive(host, job, outcome -> {
            job.close();
            dialog.close();
            running = null;
            switch (outcome.kind()) {
                case FAILED -> DialogHelper.createStyledAlert(Alert.AlertType.WARNING, I18n.t("IdentifierDocumentCheck.title"),
                        outcome.message(), I18n.t("IdentifierDocumentCheck.remedy")).show();
                case OK, NONE, CANCELLED -> DialogHelper.createStyledAlert(Alert.AlertType.INFORMATION,
                        I18n.t("IdentifierDocumentCheck.title"), outcome.message(), null).show();
                case FINDINGS -> { } // the PROBLEMS panel opens
            }
        });
    }
}
