/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.nio.file.Path;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.util.DesktopActions;
import org.fxt.freexmltoolkit.service.progress.CancelledException;

/**
 * The PK-Meldewesen side panel: what the active report is (module, pension fund, period,
 * taxonomy), the taxonomy in use with its schema and FMA documentation, the checks (FMA rules
 * as a validator and as an HTML report) and their options.
 */
public final class PkMeldewesenPanel extends VBox {

    private final PluginUi ui;
    private final PkTaxonomy taxonomy;

    @FXML private Label reportSummary;
    @FXML private Label taxonomyLabel;
    @FXML private Label status;
    @FXML private org.fxt.freexmltoolkit.controls.shell.editor.PanelActionButton onlineButton;
    @FXML private org.fxt.freexmltoolkit.plugins.IdentifierChecker identifierChecker;
    @FXML private org.fxt.freexmltoolkit.controls.shell.editor.PanelProgress progress;
    private org.fxt.freexmltoolkit.controls.shell.editor.PanelJobs jobs;
    @FXML private javafx.scene.control.ComboBox<String> versionCombo;
    /** Set while the combo is filled, so filling it does not choose a version. */
    private boolean fillingVersions;
    @FXML private CheckBox additionalChecks;
    @FXML private TextField tolerance;
    @FXML private TextField toleranceWarning;
    @FXML private TextField lookThroughAsset;
    @FXML private TextField lookThroughTotal;

    /** The code area of the active document, watched for its text (it loads after the tab opens). */
    private org.fxmisc.richtext.CodeArea watched;
    private final javafx.animation.PauseTransition refreshDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    private final javafx.beans.value.ChangeListener<Integer> lengthListener =
            (obs, o, n) -> refreshDelay.playFromStart();

    /**
     * @param ui       the shell
     * @param taxonomy the taxonomy store
     */
    public PkMeldewesenPanel(PluginUi ui, PkTaxonomy taxonomy) {
        this.ui = ui;
        this.taxonomy = taxonomy;
        FxmlViews.load(this, "/pages/panels/PkMeldewesenPanel.fxml");
        jobs = new org.fxt.freexmltoolkit.controls.shell.editor.PanelJobs(progress);
        getStyleClass().add("fxt-wf-pension");
        boolean online = org.fxt.freexmltoolkit.service.finance.IdentifierCheck.anyAllowed();
        onlineButton.setVisible(online);
        onlineButton.setManaged(online);
        identifierChecker.setKinds(java.util.List.of(org.fxt.freexmltoolkit.plugins.IdentifierKind.LEI,
                org.fxt.freexmltoolkit.plugins.IdentifierKind.ISIN));
        refreshDelay.setOnFinished(e -> refresh());
        loadOptions();
        fillVersions();
        if (PkSettings.isEnabled()) {
            PkExtras.registerOnce(taxonomy);
        }
        versionCombo.valueProperty().addListener((obs, o, n) -> {
            if (!fillingVersions && n != null && !n.equals(taxonomy.activeVersion())) {
                chooseVersion(n);
            }
        });
        ui.editorHost().activeTabProperty().addListener((obs, o, n) -> Platform.runLater(this::watchActive));
        watchActive();
    }

    /** Follows the active document's text and shows what it is. */
    private void watchActive() {
        if (watched != null) {
            watched.lengthProperty().removeListener(lengthListener);
        }
        watched = ui.editorHost().getActiveCodeArea();
        if (watched != null) {
            watched.lengthProperty().addListener(lengthListener);
        }
        if (watched != null && PkSettings.isEnabled() && activeReport() != null) {
            PkCodeHover.installOn(watched, () -> PkDictionary.active(taxonomy));
        }
        refresh();
    }

    /** @return the report of the active document, or {@code null}; reads only the start of the text */
    private PkReport activeReport() {
        org.fxmisc.richtext.CodeArea area = ui.editorHost().getActiveCodeArea();
        if (area == null) {
            return null;
        }
        return PkReport.detect(area.getText(0, Math.min(area.getLength(), PkReport.SCAN_LIMIT)));
    }

    /** Shows the active report and the taxonomy in use. */
    void refresh() {
        PkReport report = activeReport();
        reportSummary.setText(report == null ? I18n.t("PkMeldewesenPanel.no-report") : summary(report));
        String version = taxonomy.activeVersion();
        String text = I18n.t("PkMeldewesenPanel.taxonomy-version", version);
        if (report != null && report.taxonomy() != null && !version.startsWith(report.taxonomy())) {
            text += "\n" + I18n.t("PkMeldewesenPanel.taxonomy-mismatch", report.taxonomy());
        }
        taxonomyLabel.setText(text);
    }

    /** @return the multi-line description of a report */
    static String summary(PkReport report) {
        StringBuilder text = new StringBuilder(report.label()).append(" · ").append(report.module().title());
        appendLine(text, I18n.t("PkMeldewesenPanel.pk-number"), report.pkNumber());
        appendLine(text, I18n.t("PkMeldewesenPanel.report-type"), report.reportType());
        appendLine(text, "LEI", report.attributes().get("LEI"));
        appendLine(text, I18n.t("PkMeldewesenPanel.taxonomy"), report.taxonomy());
        return text.toString();
    }

    private static void appendLine(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append('\n').append(label).append(": ").append(value);
        }
    }

    @FXML
    public void onValidate() {
        if (activeReport() == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-report"));
            return;
        }
        if (!ui.editorHost().hasValidateHandler()) {
            return; // no shell validation (embedded panel)
        }
        Run run = start("validate", "#pk-validate", I18n.t("PkMeldewesenPanel.validating"), false);
        if (run == null) {
            return;
        }
        run.job().message(I18n.t("PkMeldewesenPanel.validating-detail"));
        PanelStatus.info(status, I18n.t("PkMeldewesenPanel.validating"));
        var host = ui.editorHost();
        // the result of this run (published to the document) ends the job and shows in the status line
        javafx.beans.InvalidationListener[] listener = new javafx.beans.InvalidationListener[1];
        javafx.animation.PauseTransition timeout = new javafx.animation.PauseTransition(javafx.util.Duration.minutes(15));
        Runnable finish = () -> {
            host.validationStatusProperty().removeListener(listener[0]);
            timeout.stop();
            run.end();
        };
        listener[0] = obs -> {
            org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.ValidationStatus st = host.validationStatusProperty().get();
            if (st == null || st.state() == org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.ValidationState.NOT_VALIDATED) {
                return;
            }
            finish.run();
            // the host sets the status before the problems: read them once both are in place
            javafx.application.Platform.runLater(() -> showValidated(host.getActiveProblems()));
        };
        timeout.setOnFinished(e -> finish.run());
        // an invalidation listener only hears a property whose value was read since its last change
        host.validationStatusProperty().get();
        host.validationStatusProperty().addListener(listener[0]);
        timeout.play();
        host.runValidationInPlace();
    }

    /** "✓ Valid (schema and FMA rules)" or the counts of a validation of the active report, in the status line. */
    private void showValidated(java.util.List<org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem> problems) {
        long errors = problems.stream().filter(p -> p.level()
                == org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem.Level.ERROR).count();
        long warnings = problems.stream().filter(p -> p.level()
                == org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem.Level.WARNING).count();
        if (errors + warnings == 0) {
            PanelStatus.success(status, I18n.t("PkMeldewesenPanel.validated-ok"));
        } else {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.validated-problems", errors, warnings));
        }
    }

    /** Lists the installed taxonomy versions, the one in use selected. */
    private void fillVersions() {
        fillingVersions = true;
        try {
            versionCombo.getItems().setAll(taxonomy.versions());
            versionCombo.getSelectionModel().select(taxonomy.activeVersion());
        } finally {
            fillingVersions = false;
        }
    }

    /** Uses another taxonomy version: its schemas are bound from now on (reports opened again pick them up). */
    void chooseVersion(String version) {
        Run run = start("version", null, I18n.t("PkMeldewesenPanel.switching-version", version), false);
        if (run == null) {
            return;
        }
        FxtGui.executorService.submit(() -> {
            try {
                taxonomy.setActiveVersion(version);
                run.job().message(I18n.t("PkMeldewesenPanel.loading-dictionary", version));
                PkDictionary.active(taxonomy);
            } finally {
                Platform.runLater(() -> {
                    run.end();
                    refresh();
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.version-chosen", version));
                });
            }
        });
    }

    /** Imports a taxonomy from the FMA's download ZIP and uses it when it is the newest. */
    @FXML
    public void onImportTaxonomy() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(I18n.t("PkMeldewesenPanel.pk-import-taxonomy.text"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("ZIP", "*.zip"));
        java.io.File zip = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, ui.window());
        if (zip != null) {
            importTaxonomy(zip.toPath());
        }
    }

    /** Imports a taxonomy ZIP in the background (also the test entry point). */
    void importTaxonomy(Path zip) {
        Run run = start("import", "#pk-import-taxonomy", I18n.t("PkMeldewesenPanel.pk-import-taxonomy.text"), false);
        if (run == null) {
            return;
        }
        run.job().message(I18n.t("PkMeldewesenPanel.importing", zip.getFileName()));
        FxtGui.executorService.submit(() -> {
            try {
                String version = taxonomy.importZip(zip);
                PkDictionary.forget(taxonomy.dir(version));
                run.job().message(I18n.t("PkMeldewesenPanel.copying-schemas", version));
                taxonomy.syncActive();
                Platform.runLater(() -> {
                    run.end();
                    fillVersions();
                    refresh();
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.imported", version, taxonomy.activeVersion()));
                });
            } catch (java.io.IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.failure(status, I18n.t("PkMeldewesenPanel.pk-import-taxonomy.text"),
                            I18n.t("PkMeldewesenPanel.import-failed"), I18n.t("PkMeldewesenPanel.import-remedy"),
                            String.valueOf(e.getMessage()), false);
                });
            }
        });
    }

    /** Shows the stored rule options and saves every change. */
    private void loadOptions() {
        PkRuleCheck.Options options = PkSettings.ruleOptions();
        additionalChecks.setSelected(options.additional());
        tolerance.setText(options.tolerance().toPlainString());
        toleranceWarning.setText(options.toleranceWarning().toPlainString());
        lookThroughAsset.setText(options.lookThroughAsset().toPlainString());
        lookThroughTotal.setText(options.lookThroughTotal().toPlainString());
        additionalChecks.selectedProperty().addListener((obs, o, n) -> saveOptions());
        for (TextField field : new TextField[] {tolerance, toleranceWarning, lookThroughAsset, lookThroughTotal}) {
            field.focusedProperty().addListener((obs, o, focused) -> {
                if (!focused) {
                    saveOptions();
                }
            });
            field.setOnAction(e -> saveOptions());
        }
    }

    /** @return the options shown in the panel; invalid numbers keep the stored value */
    PkRuleCheck.Options options() {
        PkRuleCheck.Options stored = PkSettings.ruleOptions();
        return new PkRuleCheck.Options(number(tolerance, stored.tolerance()), number(toleranceWarning, stored.toleranceWarning()),
                number(lookThroughAsset, stored.lookThroughAsset()), number(lookThroughTotal, stored.lookThroughTotal()),
                stored.maxLookThroughRows(), additionalChecks.isSelected());
    }

    private static java.math.BigDecimal number(TextField field, java.math.BigDecimal fallback) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(field.getText().strip().replace(',', '.'));
            return value.signum() < 0 ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void saveOptions() {
        PkRuleCheck.Options options = options();
        PkSettings.setRuleOptions(options);
        tolerance.setText(options.tolerance().toPlainString());
        toleranceWarning.setText(options.toleranceWarning().toPlainString());
        lookThroughAsset.setText(options.lookThroughAsset().toPlainString());
        lookThroughTotal.setText(options.lookThroughTotal().toPlainString());
    }

    /** Checks the LEIs and ISINs of the active report online; findings go to the PROBLEMS panel. */
    @FXML
    public void onIdentifiersOnline() {
        if (activeReport() == null) {
            PanelStatus.precondition(status, I18n.t("PkMeldewesenPanel.no-report"));
            return;
        }
        Run run = start("identifiers", "#pk-identifiers-online", I18n.t("IdentifierDocumentCheck.title"), true);
        if (run == null) {
            return; // already running
        }
        PanelStatus.info(status, I18n.t("IndustryPanel.working", I18n.t("IdentifierDocumentCheck.title")));
        org.fxt.freexmltoolkit.controls.shell.editor.IdentifierDocumentCheck.runOnActive(ui.editorHost(), run.job(), outcome -> {
            run.end();
            outcome.showIn(status);
        });
    }

    /** Renders the HTML report of the FMA rules for the active report and opens it in the preview. */
    @FXML
    public void onRuleReport() {
        PkReport report = activeReport();
        if (report == null) {
            PanelStatus.precondition(status, I18n.t("PkMeldewesenPanel.no-report"));
            return;
        }
        if (!PkRuleCheck.supports(report.module())) {
            PanelStatus.precondition(status, I18n.t("PkMeldewesenPanel.no-rules", report.module().title()));
            return;
        }
        String xml = ui.editorHost().getActiveText().orElse(null);
        if (xml == null) {
            return;
        }
        String name = ui.editorHost().getActiveDocument()
                .map(doc -> doc.getPath() != null ? doc.getPath().getFileName().toString() : doc.getDisplayName())
                .orElse("report.xml");
        PkRuleCheck.Options options = options();
        Run run = start("rule-report", "#pk-rule-report", I18n.t("PkMeldewesenPanel.pk-rule-report.text"), true);
        if (run == null) {
            return; // already running
        }
        PanelStatus.info(status, I18n.t("PkMeldewesenPanel.checking"));
        FxtGui.executorService.submit(() -> {
            try {
                String html = PkRuleCheck.htmlReport(xml, name, options, run.job());
                run.job().message(I18n.t("PkMeldewesenPanel.opening-report"));
                Platform.runLater(() -> {
                    run.end();
                    ui.editorHost().openGeneratedDocument(html, EditorFileType.HTML,
                            name.replaceFirst("(?i)\\.xml$", "") + "-pruefbericht.html");
                    ui.editorHost().setActiveViewMode(ViewMode.PREVIEW);
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.report-done"));
                });
            } catch (CancelledException e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.info(status, I18n.t("IndustryPanel.cancelled", I18n.t("PkMeldewesenPanel.pk-rule-report.text")));
                });
            } catch (Exception | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.failure(status, I18n.t("PkMeldewesenPanel.pk-rule-report.text"),
                            I18n.t("PkMeldewesenPanel.report-failed"), I18n.t("PkMeldewesenPanel.report-remedy"),
                            String.valueOf(e.getMessage()), false);
                });
            }
        });
    }

    /** Test hook: answers the submission dialog instead of showing it. */
    static java.util.function.Function<PkSubmissionDialog, PkSubmissionDialog.Choice> submissionAnswer;

    /**
     * Prepares the active report for the FMA: checks the cleaned file against its schema and the FMA
     * rules, asks for folder and running number and writes the ZIP.
     */
    @FXML
    public void onPrepareSubmission() {
        PkReport report = activeReport();
        if (report == null) {
            PanelStatus.precondition(status, I18n.t("PkMeldewesenPanel.no-report"));
            return;
        }
        String xml = ui.editorHost().getActiveText().orElse(null);
        if (xml == null) {
            return;
        }
        Path file = ui.editorHost().getActiveDocument().map(doc -> doc.getPath()).orElse(null);
        Path folder = file != null && file.getParent() != null ? file.getParent() : Path.of(System.getProperty("user.home"));
        Path schema = taxonomy.schema(taxonomy.activeVersion(), report.module());
        Run run = start("submission", "#pk-submission", I18n.t("PkSubmissionDialog.title"), true);
        if (run == null) {
            return;
        }
        PanelStatus.info(status, I18n.t("PkMeldewesenPanel.submission-checking"));
        FxtGui.executorService.submit(() -> {
            String clean;
            String checks;
            try {
                run.job().progress(0, SUBMISSION_STEPS);
                run.job().message(I18n.t("PkMeldewesenPanel.submission-step-clean", 1, SUBMISSION_STEPS));
                clean = PkSubmission.clean(xml);
                checks = submissionChecks(xml, clean, schema, report, run.job());
            } catch (CancelledException e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.info(status, I18n.t("IndustryPanel.cancelled", I18n.t("PkSubmissionDialog.title")));
                });
                return;
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.failure(status, I18n.t("PkSubmissionDialog.title"), I18n.t("PkMeldewesenPanel.submission-failed"),
                            I18n.t("PkMeldewesenPanel.report-remedy"), String.valueOf(e.getMessage()), false);
                });
                return;
            }
            Platform.runLater(() -> {
                run.end();
                PkSubmissionDialog dialog = new PkSubmissionDialog(report, folder, checks);
                PkSubmissionDialog.Choice choice = submissionAnswer != null ? submissionAnswer.apply(dialog)
                        : dialog.showAndWait().orElse(null);
                if (choice != null) {
                    writeSubmission(clean, choice);
                } else {
                    PanelStatus.info(status, "");
                }
            });
        });
    }

    /** @return the lines of the dialog's CHECKS: grid formulas removed, schema, FMA rules */
    static String submissionChecks(String original, String clean, Path schema, PkReport report) {
        return submissionChecks(original, clean, schema, report, org.fxt.freexmltoolkit.service.progress.Progress.NONE);
    }

    /** The submission's steps: clean, schema, FMA rules, ZIP. */
    static final int SUBMISSION_STEPS = 4;

    /** Like {@link #submissionChecks(String, String, Path, PkReport)}, reporting steps 2 and 3 of the submission. */
    static String submissionChecks(String original, String clean, Path schema, PkReport report,
                                   org.fxt.freexmltoolkit.service.progress.Progress progress) {
        progress.checkCancelled();
        progress.progress(1, SUBMISSION_STEPS);
        progress.message(I18n.t("PkMeldewesenPanel.submission-step-schema", 2, SUBMISSION_STEPS));
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (PkGridSetup.hasAnnotations(original)) {
            lines.add(I18n.t("PkSubmissionDialog.grid-removed"));
        }
        var result = org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner.runWithReport(clean,
                schema == null ? null : schema.toFile(), null, "report.xml");
        long schemaErrors = result.problems().stream()
                .filter(p -> !"PK-Meldewesen".equals(p.source()) && "error".equals(p.severity())).count();
        lines.add(schemaErrors == 0 ? I18n.t("PkSubmissionDialog.schema-ok") : I18n.t("PkSubmissionDialog.schema-errors", schemaErrors));
        progress.checkCancelled();
        progress.progress(2, SUBMISSION_STEPS);
        progress.message(I18n.t("PkMeldewesenPanel.submission-step-rules", 3, SUBMISSION_STEPS));
        if (PkRuleCheck.supports(report.module())) {
            long errors = 0;
            long warnings = 0;
            boolean ranInValidation = org.fxt.freexmltoolkit.plugin.PluginManager.getInstance().validators().stream()
                    .anyMatch(v -> v instanceof PkRuleValidator && v.accepts(clean, "report.xml"));
            try {
                if (ranInValidation) {
                    // the validator's findings: code first, the additional checks start with their id INT_…
                    for (var p : result.problems()) {
                        if ("PK-Meldewesen".equals(p.source()) && !p.message().startsWith(PkRuleCatalog.ADDITIONAL_PREFIX)) {
                            if ("error".equals(p.severity())) {
                                errors++;
                            } else {
                                warnings++;
                            }
                        }
                    }
                } else {
                    for (PkRuleCheck.Finding f : PkRuleCheck.run(clean, "report.xml", PkSettings.ruleOptions(), progress)) {
                        if (f.rule().additional()) {
                            continue;
                        }
                        if ("E".equals(f.status())) {
                            errors++;
                        } else {
                            warnings++;
                        }
                    }
                }
                lines.add(I18n.t("PkSubmissionDialog.rules-result", errors, warnings));
            } catch (CancelledException e) {
                throw e;
            } catch (Exception e) {
                lines.add(I18n.t("PkSubmissionDialog.not-run", String.valueOf(e.getMessage())));
            }
            if (errors > 0 || schemaErrors > 0) {
                lines.add(I18n.t("PkSubmissionDialog.errors-note"));
            }
        } else if (schemaErrors > 0) {
            lines.add(I18n.t("PkSubmissionDialog.errors-note"));
        }
        return String.join("\n", lines);
    }

    private void writeSubmission(String clean, PkSubmissionDialog.Choice choice) {
        Run run = start("submission", "#pk-submission", I18n.t("PkSubmissionDialog.title"), false);
        if (run == null) {
            return;
        }
        run.job().progress(3, SUBMISSION_STEPS);
        run.job().message(I18n.t("PkMeldewesenPanel.submission-step-zip", 4, SUBMISSION_STEPS));
        FxtGui.executorService.submit(() -> {
            try {
                Path zip = PkSubmission.writeZip(clean, choice.folder(), choice.xmlName());
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.submission-done", zip.toString()));
                    org.fxt.freexmltoolkit.controls.shell.editor.ExportResult.saved(ui.window(),
                            I18n.t("PkMeldewesenPanel.submission-what"), zip);
                });
            } catch (java.io.IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.failure(status, I18n.t("PkSubmissionDialog.title"), I18n.t("PkMeldewesenPanel.submission-failed"),
                            I18n.t("PkMeldewesenPanel.submission-remedy"), String.valueOf(e.getMessage()), false);
                });
            }
        });
    }

    /** Adds grid formulas and saved filters to a copy of the active report and opens it in the grid. */
    @FXML
    public void onGridAdd() {
        transformCopy(xml -> PkGridSetup.add(xml, true, true), "-grid.xml", ViewMode.GRAPHIC,
                I18n.t("PkMeldewesenPanel.grid-added"), "#pk-grid-add", I18n.t("PkMeldewesenPanel.grid-adding"));
    }

    /** Removes the grid formulas and filters: a copy of the active report ready for submission. */
    @FXML
    public void onGridRemove() {
        String xml = ui.editorHost().getActiveText().orElse(null);
        if (xml != null && !PkGridSetup.hasAnnotations(xml)) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.grid-none"));
            return;
        }
        transformCopy(PkGridSetup::remove, "-clean.xml", ViewMode.TEXT, I18n.t("PkMeldewesenPanel.grid-removed"),
                "#pk-grid-remove", I18n.t("PkMeldewesenPanel.grid-removing"));
    }

    /** A transformation of the active quarterly report, opened as a new document. */
    private interface ReportTransform {
        String apply(String xml) throws java.io.IOException;
    }

    private void transformCopy(ReportTransform transform, String suffix, ViewMode mode, String done, String selector,
                               String working) {
        PkReport report = activeReport();
        if (report == null || report.module() != PkModule.PFQ) {
            PanelStatus.precondition(status, I18n.t("PkMeldewesenPanel.pfq-only"));
            return;
        }
        String xml = ui.editorHost().getActiveText().orElse(null);
        if (xml == null) {
            return;
        }
        String name = ui.editorHost().getActiveDocument().map(doc -> doc.getDisplayName()).orElse("report.xml")
                .replaceFirst("(?i)(-grid)?\\.xml$", "");
        Run run = start("grid", selector, I18n.t("PkMeldewesenPanel.grid.title"), false);
        if (run == null) {
            return;
        }
        run.job().message(working);
        FxtGui.executorService.submit(() -> {
            try {
                String result = transform.apply(xml);
                Platform.runLater(() -> {
                    run.end();
                    ui.editorHost().openGeneratedDocument(result, EditorFileType.XML, name + suffix);
                    ui.editorHost().setActiveViewMode(mode);
                    PanelStatus.success(status, done);
                });
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    run.end();
                    PanelStatus.failure(status, I18n.t("PkMeldewesenPanel.grid.title"),
                            I18n.t("PkMeldewesenPanel.grid-failed"), I18n.t("PkMeldewesenPanel.report-remedy"),
                            String.valueOf(e.getMessage()), false);
                });
            }
        });
    }

    /** A running job of the panel: its progress and the busy button. */
    private record Run(org.fxt.freexmltoolkit.controls.shell.editor.PanelJobs.Job job, Runnable unbusy) {
        /** Ends the job (FX thread). */
        void end() {
            job.close();
            unbusy.run();
        }
    }

    /**
     * Starts a job in the panel's progress block and shows its button busy.
     *
     * @param selector the button's id selector, or {@code null}
     * @return the job, or {@code null} when it runs already (the click is ignored with a status line)
     */
    private Run start(String id, String selector, String label, boolean cancellable) {
        org.fxt.freexmltoolkit.controls.shell.editor.PanelJobs.Job job = jobs.start(id, label, cancellable);
        if (job == null) {
            PanelStatus.info(status, I18n.t("IndustryPanel.already-running", label));
            return null;
        }
        return new Run(job, selector == null ? () -> { } : busy(selector));
    }

    /** Shows the button with that id busy (a spinner as icon, "Running: …") until the returned action runs. */
    private Runnable busy(String selector) {
        if (!(lookup(selector) instanceof javafx.scene.control.ButtonBase button)) {
            return () -> { };
        }
        javafx.scene.Node icon = button.getGraphic();
        String text = button.getText();
        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setPrefSize(15, 15);
        spinner.setMaxSize(15, 15);
        button.setGraphic(spinner);
        button.setText(I18n.t("IndustryPanel.running", text));
        return () -> {
            button.setGraphic(icon);
            button.setText(text);
        };
    }

    /** The bundled example report (a fictitious pension fund with a few rule violations). */
    static final String EXAMPLE = "/plugins/pkmeldewesen/examples/01.pfq.20260331.00001.test.xml";

    /** Opens the example quarterly report as a new document. */
    @FXML
    public void onOpenExample() {
        try (var in = PkMeldewesenPanel.class.getResourceAsStream(EXAMPLE)) {
            String xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            ui.editorHost().openGeneratedDocument(xml, EditorFileType.XML, "01.pfq.20260331.00001.test.xml");
        } catch (java.io.IOException | RuntimeException e) {
            PanelStatus.info(status, String.valueOf(e.getMessage()));
        }
    }

    /** Generates the HTML documentation of the active report's schema (once per version) and opens it. */
    @FXML
    public void onSchemaDocumentation() {
        PkReport report = activeReport();
        PkModule module = report != null ? report.module() : PkModule.PFQ;
        String version = taxonomy.activeVersion();
        Path schema = taxonomy.schema(version, module);
        if (schema == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-schema"));
            return;
        }
        java.io.File out = taxonomy.dir(version).resolve("schema-doc-" + module.code()).toFile();
        java.io.File index = new java.io.File(out, "index.html");
        if (index.isFile()) {
            DesktopActions.open(index, e -> PanelStatus.info(status, String.valueOf(e.getMessage())));
            return;
        }
        Run run = start("schema-doc", "#pk-schema-doc", I18n.t("PkMeldewesenPanel.pk-schema-doc.text"), false);
        if (run == null) {
            return; // already running
        }
        PanelStatus.info(status, I18n.t("PkMeldewesenPanel.schema-doc-running"));
        FxtGui.executorService.submit(() -> {
            String result = org.fxt.freexmltoolkit.controls.shell.editor.DocumentationRunner.exportHtml(schema.toFile(), out,
                    run.job());
            Platform.runLater(() -> {
                run.end();
                if (result.startsWith("OK:")) {
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.schema-doc-done"));
                    DesktopActions.open(index, e -> PanelStatus.info(status, String.valueOf(e.getMessage())));
                } else {
                    PanelStatus.failure(status, I18n.t("PkMeldewesenPanel.pk-schema-doc.text"),
                            I18n.t("PkMeldewesenPanel.schema-doc-failed"), I18n.t("PkMeldewesenPanel.report-remedy"), result, false);
                }
            });
        });
    }

    @FXML
    public void onOpenSchema() {
        PkReport report = activeReport();
        PkModule module = report != null ? report.module() : PkModule.PFQ;
        Path schema = taxonomy.schema(taxonomy.activeVersion(), module);
        if (schema == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-schema"));
            return;
        }
        ui.openFile(schema);
    }

    @FXML
    public void onOpenDocumentation() {
        Path pdf = taxonomy.documentation(taxonomy.activeVersion());
        if (pdf == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-documentation"));
            return;
        }
        DesktopActions.open(pdf.toFile(), e -> PanelStatus.info(status, String.valueOf(e.getMessage())));
    }

    @FXML
    public void onOpenFolder() {
        DesktopActions.open(taxonomy.dir(taxonomy.activeVersion()).toFile(),
                e -> PanelStatus.info(status, String.valueOf(e.getMessage())));
    }
}
