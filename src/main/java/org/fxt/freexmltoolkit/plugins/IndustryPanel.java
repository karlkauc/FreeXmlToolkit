package org.fxt.freexmltoolkit.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelActionButton;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelActionList;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelJobs;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelProgress;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.controls.shell.editor.SidePanelHeader;
import org.fxt.freexmltoolkit.controls.shell.editor.SidePanelSection;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.service.progress.CancelledException;
import org.fxt.freexmltoolkit.service.progress.Progress;
import org.fxt.freexmltoolkit.plugin.PluginUi;

/**
 * The side panel of an industry plugin: what the plugin does, the active document (what the plugin
 * recognises it as and how many problems its last check found), the check itself, an optional HTML
 * check report, the plugin's tools (new sample document, import …), optional settings and a checker
 * for identifiers (IBAN, BIC, LEI, ISIN …).
 *
 * <p>The check runs like F8: the shell validates the document (schema + every plugin validator) and the
 * problems go to the PROBLEMS panel below the editor, where a click jumps to the line; the panel shows
 * only the counts. A plugin whose check needs the saved file (an external program) passes a
 * {@link Checker}; its problems are published to the PROBLEMS panel the same way.
 */
public class IndustryPanel extends VBox {

    /** The panel's check of the active document. */
    @FunctionalInterface
    public interface Checker {
        /**
         * @param path the saved file, or {@code null} for an unsaved document
         * @throws Exception shown as "The check could not run: …" (e.g. a missing external program)
         */
        List<DocumentValidator.Problem> check(String content, java.nio.file.Path path, String name) throws Exception;

        /**
         * Like {@link #check(String, java.nio.file.Path, String)}, reporting progress; a check that can stop asks
         * {@link Progress#checkCancelled()} or registers {@link Progress#onCancel} (an external program). When it
         * cannot, Cancel only stops waiting and its result is dropped.
         */
        default List<DocumentValidator.Problem> check(String content, java.nio.file.Path path, String name,
                                                      Progress progress) throws Exception {
            return check(content, path, name);
        }

        /** @return a checker from a check method that reports progress and can stop ({@code Plugin::check}) */
        static Checker withProgress(ProgressCheck check) {
            return new Checker() {
                @Override
                public List<DocumentValidator.Problem> check(String content, java.nio.file.Path path, String name)
                        throws Exception {
                    return check.check(content, path, name, Progress.NONE);
                }

                @Override
                public List<DocumentValidator.Problem> check(String content, java.nio.file.Path path, String name,
                                                             Progress progress) throws Exception {
                    return check.check(content, path, name, progress);
                }
            };
        }
    }

    /** A background job that reports progress ({@link #background(String, ProgressJob, Consumer)}). */
    @FunctionalInterface
    public interface ProgressJob<T> {
        T run(Progress progress) throws Exception;
    }

    /** Renders a check report of the active document as HTML (run off the FX thread). */
    @FunctionalInterface
    public interface ReportBuilder {
        /**
         * @param name the document's file or display name
         * @return the report, or {@code null} when the document is not one the plugin reports on
         */
        String html(String content, String name) throws Exception;

        /** Like {@link #html(String, String)}, reporting progress (default: the plain variant). */
        default String html(String content, String name, Progress progress) throws Exception {
            return html(content, name);
        }

        /** @return a builder from a report method that reports progress ({@code Plugin::report}) */
        static ReportBuilder withProgress(ProgressReport report) {
            return new ReportBuilder() {
                @Override
                public String html(String content, String name) throws Exception {
                    return report.html(content, name, Progress.NONE);
                }

                @Override
                public String html(String content, String name, Progress progress) throws Exception {
                    return report.html(content, name, progress);
                }
            };
        }
    }

    /** A report that reports its progress (see {@link ReportBuilder#withProgress}). */
    @FunctionalInterface
    public interface ProgressReport {
        String html(String content, String name, Progress progress) throws Exception;
    }

    /** A check that reports its progress and can stop (see {@link Checker#withProgress}). */
    @FunctionalInterface
    public interface ProgressCheck {
        List<DocumentValidator.Problem> check(String content, java.nio.file.Path path, String name, Progress progress)
                throws Exception;
    }

    /** An action row of the panel's TOOLS section. */
    public record Action(String id, String text, String icon, ActionColor color, java.util.function.Consumer<PluginUi> run) {
    }

    @FXML private SidePanelHeader header;
    @FXML private Label about;
    @FXML private Label documentSummary;
    @FXML private Label checkCounts;
    @FXML private Hyperlink showProblemsLink;
    @FXML private PanelActionButton checkButton;
    @FXML private PanelActionButton reportButton;
    @FXML private SidePanelSection toolsSection;
    @FXML private PanelActionList actions;
    @FXML private SidePanelSection optionsSection;
    @FXML private VBox optionsBody;
    @FXML private SidePanelSection identifierSection;
    @FXML private IdentifierChecker identifierChecker;
    @FXML private PanelProgress progress;
    @FXML private Label status;

    private final PluginUi ui;
    private final Function<String, String> describer;
    private final Checker checker;
    /** The plugin's validator when the check is the shell's validation; {@code null} for a {@link Checker}. */
    private final DocumentValidator validator;
    private ReportBuilder reportBuilder;
    private String reportSuffix = "-report.html";
    private boolean recognised;
    /** Set while a check started from the panel waits for the shell's validation result. */
    private boolean checkPending;
    /** Stops waiting for a validation result that does not come (the run goes on in the Validation panel). */
    private final javafx.animation.PauseTransition checkTimeout = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(60));
    /** The button whose click started the running background job, shown busy until it ends. */
    private javafx.scene.control.ButtonBase trigger;
    /** The background jobs running now (a second click on a running tool is refused), shown in {@link #progress}. */
    private PanelJobs jobs;
    /** The job of a check that waits for the shell's validation, or {@code null}. */
    private PanelJobs.Job checkJob;

    /**
     * @param describer   what the document is ({@code "pain.001.001.09 — 2 payments …"}), or {@code null}
     *                    when the plugin does not recognise it
     * @param validator   the plugin's checks (they also run with F8; registered by the plugin)
     * @param identifiers the kinds the identifier checker offers (none hides it)
     */
    public IndustryPanel(PluginUi ui, String title, String aboutText, List<Action> extraActions,
                         Function<String, String> describer, DocumentValidator validator,
                         List<IdentifierKind> identifiers) {
        this(ui, title, aboutText, extraActions, describer, validator,
                (content, path, name) -> validator.validate(content, name), identifiers);
    }

    /** @param checker the check of "Check Active Document" (for checks that need the saved file) */
    public IndustryPanel(PluginUi ui, String title, String aboutText, List<Action> extraActions,
                         Function<String, String> describer, Checker checker, List<IdentifierKind> identifiers) {
        this(ui, title, aboutText, extraActions, describer, null, checker, identifiers);
    }

    private IndustryPanel(PluginUi ui, String title, String aboutText, List<Action> extraActions,
                          Function<String, String> describer, DocumentValidator validator, Checker checker,
                          List<IdentifierKind> identifiers) {
        this.ui = ui;
        this.describer = describer;
        this.validator = validator;
        this.checker = checker;
        FxmlViews.load(this, "/pages/panels/IndustryPanel.fxml");
        jobs = new PanelJobs(progress);
        header.setTitle(title);
        about.setText(aboutText);
        for (Action action : extraActions) {
            addTool(action);
        }
        boolean online = (identifiers.contains(IdentifierKind.LEI) && org.fxt.freexmltoolkit.service.finance.gleif.Gleif.allowed())
                || (identifiers.contains(IdentifierKind.ISIN) && org.fxt.freexmltoolkit.service.finance.isin.Isin.allowed());
        if (online) {
            addTool(new Action("industry-identifiers-online", I18n.t("IdentifierDocumentCheck.title"), "bi-globe2",
                    ActionColor.NEUTRAL, u -> checkIdentifiersOnline()));
        }
        show(toolsSection, !extraActions.isEmpty() || online);
        show(reportButton, false);
        show(optionsSection, false);
        show(identifierSection, !identifiers.isEmpty());
        identifierChecker.setKinds(identifiers);
        EditorHost host = ui.editorHost();
        // Deferred: the new tab's text is in place only after the tab switch has been processed
        host.activeTabProperty().addListener((obs, o, n) -> Platform.runLater(this::refreshDocument));
        host.getActiveProblems().addListener((ListChangeListener<ValidationProblem>) c -> refreshCounts());
        host.validationStatusProperty().addListener((obs, o, n) -> refreshCounts());
        // fires on every published result, also when it equals the previous one (a second check of a valid document)
        host.validationStatusProperty().addListener((javafx.beans.InvalidationListener) obs -> {
            EditorHost.ValidationStatus st = host.validationStatusProperty().get();
            if (checkPending) {
                finishCheck(st);
            }
        });
        checkTimeout.setOnFinished(e -> {
            if (checkPending) {
                checkPending = false;
                endCheckJob();
                checkButton.setDisable(!recognised);
                PanelStatus.info(status, I18n.t("IndustryPanel.check-still-running"));
            }
        });
        sceneProperty().addListener((obs, o, scene) -> {
            if (scene != null) {
                refreshDocument();
            }
        });
        refreshDocument();
    }

    /** Adds the action <b>Check Report (HTML)</b>; the report opens in the preview. */
    public IndustryPanel withReport(ReportBuilder builder, String fileSuffix) {
        this.reportBuilder = builder;
        this.reportSuffix = fileSuffix;
        show(reportButton, true);
        reportButton.setDisable(!recognised);
        return this;
    }

    /** Shows the plugin's settings in an OPTIONS section. */
    public IndustryPanel withOptions(Node options) {
        optionsBody.getChildren().setAll(options);
        show(optionsSection, true);
        return this;
    }

    /** Adds a row to the TOOLS section. */
    public IndustryPanel withTool(Action action) {
        addTool(action);
        show(toolsSection, true);
        return this;
    }

    private void addTool(Action action) {
        PanelActionButton button = new PanelActionButton();
        button.setId(action.id());
        button.setText(action.text());
        button.setIconLiteral(action.icon());
        button.setColor(action.color());
        button.setOnAction(e -> {
            trigger = button;
            invoking = this;
            try {
                action.run().accept(ui);
            } finally {
                trigger = null;
                invoking = null;
            }
        });
        actions.getChildren().add(button);
    }

    /** The panel whose tool button is being clicked (FX thread, during the click), or {@code null}. */
    private static IndustryPanel invoking;

    /**
     * @return the panel whose TOOLS row runs right now (an {@link Action} gets only the {@link PluginUi}; a tool that
     *         works in the background uses this panel's {@link #background}), or {@code null} outside a click
     */
    public static IndustryPanel invoking() {
        return invoking;
    }

    /** The panel's status line (for the plugin's tools). */
    public Label status() {
        return status;
    }

    /**
     * Runs a tool's work off the FX thread with the panel's progress bar; {@code done} gets the result
     * on the FX thread, a failure is shown in the status line and as error dialog.
     */
    public <T> void background(String what, Callable<T> job, Consumer<T> done) {
        background(what, false, progress -> job.call(), done);
    }

    /**
     * Runs a tool's work off the FX thread; it reports to the panel's progress block (what runs now, steps) and
     * stops at its next step when the user clicks Cancel ({@link Progress#checkCancelled()}).
     */
    public <T> void background(String what, ProgressJob<T> job, Consumer<T> done) {
        background(what, true, job, done);
    }

    private <T> void background(String what, boolean cancellable, ProgressJob<T> job, Consumer<T> done) {
        PanelJobs.Job progressJob = jobs.start(what, what, cancellable);
        if (progressJob == null) {
            PanelStatus.info(status, I18n.t("IndustryPanel.already-running", what));
            return;
        }
        javafx.scene.control.ButtonBase button = trigger;
        Busy busy = Busy.start(button);
        PanelStatus.info(status, I18n.t("IndustryPanel.working", what));
        FxtGui.executorService.submit(() -> {
            try {
                T result = job.run(progressJob);
                progressJob.checkCancelled(); // a job that ignored Cancel: its result is dropped
                Platform.runLater(() -> {
                    progressJob.close();
                    busy.end();
                    status.setText("");
                    status.setGraphic(null);
                    done.accept(result);
                });
            } catch (CancelledException e) {
                Platform.runLater(() -> {
                    progressJob.close();
                    busy.end();
                    PanelStatus.info(status, I18n.t("IndustryPanel.cancelled", what));
                });
            } catch (Exception | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    progressJob.close();
                    busy.end();
                    PanelStatus.failure(status, what, I18n.t("IndustryPanel.failed", what),
                            I18n.t("IndustryPanel.failed-remedy"), String.valueOf(e.getMessage()), false);
                });
            }
        });
    }

    /** @return the panel's jobs (progress block); plugins with their own jobs use it too */
    public PanelJobs jobs() {
        return jobs;
    }

    /**
     * A button shown busy while its job runs: a spinner instead of its icon and "Running: …" as text. It stays
     * enabled (a greyed spinner is hard to see); a second click is refused by {@link #background}.
     */
    private record Busy(javafx.scene.control.ButtonBase button, Node graphic, String text) {

        static Busy start(javafx.scene.control.ButtonBase button) {
            if (button == null) {
                return new Busy(null, null, null);
            }
            Busy busy = new Busy(button, button.getGraphic(), button.getText());
            ProgressIndicator spinner = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
            spinner.setPrefSize(15, 15);
            spinner.setMaxSize(15, 15);
            button.setGraphic(spinner);
            button.setText(I18n.t("IndustryPanel.running", busy.text()));
            return busy;
        }

        void end() {
            if (button != null) {
                button.setGraphic(graphic);
                button.setText(text);
            }
        }
    }

    /** Ends a check started from the panel: progress off and a status line with the time, so every run shows. */
    private void finishCheck(EditorHost.ValidationStatus st) {
        checkPending = false;
        checkTimeout.stop();
        endCheckJob();
        checkButton.setDisable(!recognised);
        refreshCounts();
        String time = java.time.LocalTime.now().withNano(0).toString();
        if (st == null || st.state() == EditorHost.ValidationState.NOT_VALIDATED) {
            PanelStatus.precondition(status, I18n.t("IndustryPanel.check-not-run", st == null ? "" : st.summary()));
        } else if (st.state() == EditorHost.ValidationState.VALID) {
            PanelStatus.success(status, I18n.t("IndustryPanel.checked-ok", time));
        } else {
            PanelStatus.info(status, I18n.t("IndustryPanel.checked-problems", time, checkCounts.getText()));
        }
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Shows what the active document is for this extension. */
    void refreshDocument() {
        String text = ui.editorHost().getActiveText().orElse(null);
        String description = text == null ? null : describer.apply(text);
        recognised = description != null;
        documentSummary.setText(text == null ? I18n.t("IndustryPanel.no-document")
                : description == null ? I18n.t("IndustryPanel.not-recognised") : description);
        checkButton.setDisable(!recognised);
        reportButton.setDisable(!recognised);
        refreshCounts();
    }

    /** The counts of the active document's last check (the PROBLEMS panel's content). */
    void refreshCounts() {
        EditorHost host = ui.editorHost();
        EditorHost.ValidationStatus state = host.validationStatusProperty().get();
        if (!recognised || state == null || state.state() == EditorHost.ValidationState.NOT_VALIDATED) {
            checkCounts.setText(recognised ? I18n.t("IndustryPanel.not-checked") : "");
            show(checkCounts, recognised);
            show(showProblemsLink, false);
            return;
        }
        int errors = 0;
        int warnings = 0;
        for (ValidationProblem p : host.getActiveProblems()) {
            if ("warning".equalsIgnoreCase(p.severity())) {
                warnings++;
            } else if (!"info".equalsIgnoreCase(p.severity())) {
                errors++;
            }
        }
        if (errors + warnings == 0 && state.state() == EditorHost.ValidationState.INVALID) {
            errors = state.problemCount();
        }
        checkCounts.setText(counts(errors, warnings));
        show(checkCounts, true);
        show(showProblemsLink, errors + warnings > 0);
    }

    /** @return {@code ✗ 2 errors · ⚠ 1 warning}, or the all-clear text */
    static String counts(int errors, int warnings) {
        if (errors == 0 && warnings == 0) {
            return I18n.t("IndustryPanel.no-problems");
        }
        List<String> parts = new ArrayList<>();
        if (errors > 0) {
            parts.add("✗ " + I18n.t(errors == 1 ? "IndustryPanel.one-error" : "IndustryPanel.errors", errors));
        }
        if (warnings > 0) {
            parts.add("⚠ " + I18n.t(warnings == 1 ? "IndustryPanel.one-warning" : "IndustryPanel.warnings", warnings));
        }
        return String.join(" · ", parts);
    }

    @FXML
    public void onCheck() {
        EditorHost host = ui.editorHost();
        String text = host.getActiveText().orElse(null);
        if (text == null) {
            return;
        }
        boolean xml = host.getActiveDocument().map(d -> d.getFileType() != null && d.getFileType().isXmlFamily()).orElse(false);
        if (validator != null && host.hasValidateHandler() && xml) {
            checkPending = true;
            checkButton.setDisable(true);
            endCheckJob();
            checkJob = jobs.start("check", I18n.t("IndustryPanel.checking"), false);
            if (checkJob != null) {
                checkJob.message(I18n.t("IndustryPanel.check-running-validation"));
            }
            PanelStatus.info(status, I18n.t("IndustryPanel.checking"));
            checkTimeout.playFromStart();
            host.runValidationInPlace(); // schema + every plugin validator, problems to the PROBLEMS panel
            return;
        }
        // other documents (CSV, Excel …): F8 runs no plugin validators there, the panel checks itself
        var document = host.getActiveDocument();
        String name = document.map(d -> d.getDisplayName()).orElse(null);
        java.nio.file.Path path = document.map(d -> d.getPath()).orElse(null);
        EditorHost.ValidationTarget target = host.validationTarget();
        PanelJobs.Job job = jobs.start("check", I18n.t("IndustryPanel.checking"), true);
        if (job == null) {
            PanelStatus.info(status, I18n.t("IndustryPanel.already-running", I18n.t("IndustryPanel.checking")));
            return;
        }
        checkButton.setDisable(true);
        PanelStatus.info(status, I18n.t("IndustryPanel.checking"));
        FxtGui.executorService.submit(() -> {
            List<DocumentValidator.Problem> problems = null;
            String failure = null;
            try {
                problems = checker.check(text, path, name, job);
            } catch (CancelledException e) {
                // reported below
            } catch (Exception e) {
                failure = e.getMessage();
            }
            List<DocumentValidator.Problem> found = problems;
            String error = failure;
            Platform.runLater(() -> {
                job.close();
                checkButton.setDisable(!recognised);
                if (job.isCancelled()) {
                    PanelStatus.info(status, I18n.t("IndustryPanel.cancelled", I18n.t("IndustryPanel.checking")));
                    return;
                }
                if (found == null) {
                    PanelStatus.failure(status, header.getTitle(), I18n.t("IndustryPanel.check-failed", error),
                            I18n.t("IndustryPanel.failed-remedy"), error, false);
                    return;
                }
                publish(host, target, found);
                String time = java.time.LocalTime.now().withNano(0).toString();
                if (found.isEmpty()) {
                    PanelStatus.success(status, I18n.t("IndustryPanel.checked-ok", time));
                } else {
                    PanelStatus.info(status, I18n.t("IndustryPanel.checked-problems", time, checkCounts.getText()));
                }
            });
        });
    }

    /** Puts a check's problems into the PROBLEMS panel of the document the check read. */
    private void publish(EditorHost host, EditorHost.ValidationTarget target, List<DocumentValidator.Problem> found) {
        String source = header.getTitle();
        List<ValidationProblem> problems = found.stream()
                .map(p -> new ValidationProblem(source, p.severity(), p.line(), p.message())).toList();
        host.publishValidation(target, problems.isEmpty() ? EditorHost.ValidationState.VALID : EditorHost.ValidationState.INVALID,
                problems.size(), problems.isEmpty() ? I18n.t("IndustryPanel.no-problems") : counts(problems.size(), 0),
                problems);
        refreshCounts();
        if (!problems.isEmpty()) {
            host.revealProblems();
        }
    }

    /** Opens the PROBLEMS panel below the editor. */
    @FXML
    public void onShowProblems() {
        ui.editorHost().revealProblems();
    }

    /** Renders the plugin's check report of the active document and opens it in the preview. */
    @FXML
    public void onReport() {
        EditorHost host = ui.editorHost();
        String text = host.getActiveText().orElse(null);
        if (text == null || reportBuilder == null) {
            return;
        }
        String name = host.getActiveDocument()
                .map(doc -> doc.getPath() != null ? doc.getPath().getFileName().toString() : doc.getDisplayName())
                .orElse("document.xml");
        trigger = reportButton;
        background(I18n.t("IndustryPanel.reportButton.text"), (ProgressJob<String>) p -> reportBuilder.html(text, name, p), html -> {
            if (html == null) {
                PanelStatus.precondition(status, I18n.t("IndustryPanel.not-recognised"));
                return;
            }
            host.openGeneratedDocument(html, EditorFileType.HTML, name.replaceFirst("(?i)\\.xml$", "") + reportSuffix);
            host.setActiveViewMode(ViewMode.PREVIEW);
        });
        trigger = null;
    }

    /** Ends the job of a check that waited for the shell's validation. */
    private void endCheckJob() {
        if (checkJob != null) {
            checkJob.close();
            checkJob = null;
        }
    }

    /**
     * Checks the LEIs (GLEIF) and ISINs (ESMA FIRDS, OpenFIGI) of the active document online; the findings replace
     * earlier online findings in the PROBLEMS panel and keep the others (schema, rules).
     */
    void checkIdentifiersOnline() {
        String what = I18n.t("IdentifierDocumentCheck.title");
        PanelJobs.Job job = jobs.start("identifiers", what, true);
        if (job == null) {
            PanelStatus.info(status, I18n.t("IndustryPanel.already-running", what));
            return;
        }
        javafx.scene.control.ButtonBase button = trigger;
        Busy busy = Busy.start(button);
        PanelStatus.info(status, I18n.t("IndustryPanel.working", what));
        org.fxt.freexmltoolkit.controls.shell.editor.IdentifierDocumentCheck.runOnActive(ui.editorHost(), job, outcome -> {
            job.close();
            busy.end();
            refreshCounts();
            outcome.showIn(status);
        });
    }

    // test access
    String documentSummaryForTesting() {
        return documentSummary.getText();
    }

    String checkCountsForTesting() {
        return checkCounts.isVisible() ? checkCounts.getText() : null;
    }

    boolean reportAvailableForTesting() {
        return reportButton.isVisible() && !reportButton.isDisabled();
    }

    void checkIdentifierForTesting(IdentifierKind kind, String value) {
        identifierChecker.check(kind, value);
    }

    String identifierResultForTesting() {
        return identifierChecker.resultText();
    }

    boolean gleifButtonShownForTesting() {
        return identifierChecker.gleifButtonShown();
    }

    String gleifResultForTesting() {
        return identifierChecker.gleifResultText();
    }

    /** Looks the LEI of the identifier checker up at GLEIF. */
    void onGleifLookup() {
        identifierChecker.onGleifLookup();
    }
}
