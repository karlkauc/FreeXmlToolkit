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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.util.DesktopActions;

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
    @FXML private ProgressBar progress;
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
        getStyleClass().add("fxt-wf-pension");
        refreshDelay.setOnFinished(e -> refresh());
        loadOptions();
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
        ui.editorHost().runValidation();
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
        PanelStatus.info(status, I18n.t("PkMeldewesenPanel.checking"));
        showProgress(true);
        FxtGui.executorService.submit(() -> {
            try {
                String html = PkRuleCheck.htmlReport(xml, name, options);
                Platform.runLater(() -> {
                    showProgress(false);
                    ui.editorHost().openGeneratedDocument(html, EditorFileType.HTML,
                            name.replaceFirst("(?i)\\.xml$", "") + "-pruefbericht.html");
                    ui.editorHost().setActiveViewMode(ViewMode.PREVIEW);
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.report-done"));
                });
            } catch (Exception | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    showProgress(false);
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
        PanelStatus.info(status, I18n.t("PkMeldewesenPanel.submission-checking"));
        showProgress(true);
        FxtGui.executorService.submit(() -> {
            String clean;
            String checks;
            try {
                clean = PkSubmission.clean(xml);
                checks = submissionChecks(xml, clean, schema, report);
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    showProgress(false);
                    PanelStatus.failure(status, I18n.t("PkSubmissionDialog.title"), I18n.t("PkMeldewesenPanel.submission-failed"),
                            I18n.t("PkMeldewesenPanel.report-remedy"), String.valueOf(e.getMessage()), false);
                });
                return;
            }
            Platform.runLater(() -> {
                showProgress(false);
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
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (PkGridSetup.hasAnnotations(original)) {
            lines.add(I18n.t("PkSubmissionDialog.grid-removed"));
        }
        var result = org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner.runWithReport(clean,
                schema == null ? null : schema.toFile(), null, "report.xml");
        long schemaErrors = result.problems().stream()
                .filter(p -> !"PK-Meldewesen".equals(p.source()) && "error".equals(p.severity())).count();
        lines.add(schemaErrors == 0 ? I18n.t("PkSubmissionDialog.schema-ok") : I18n.t("PkSubmissionDialog.schema-errors", schemaErrors));
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
                    for (PkRuleCheck.Finding f : PkRuleCheck.run(clean, "report.xml", PkSettings.ruleOptions())) {
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
        showProgress(true);
        FxtGui.executorService.submit(() -> {
            try {
                Path zip = PkSubmission.writeZip(clean, choice.folder(), choice.xmlName());
                Platform.runLater(() -> {
                    showProgress(false);
                    PanelStatus.success(status, I18n.t("PkMeldewesenPanel.submission-done", zip.toString()));
                });
            } catch (java.io.IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    showProgress(false);
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
                I18n.t("PkMeldewesenPanel.grid-added"));
    }

    /** Removes the grid formulas and filters: a copy of the active report ready for submission. */
    @FXML
    public void onGridRemove() {
        String xml = ui.editorHost().getActiveText().orElse(null);
        if (xml != null && !PkGridSetup.hasAnnotations(xml)) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.grid-none"));
            return;
        }
        transformCopy(PkGridSetup::remove, "-clean.xml", ViewMode.TEXT, I18n.t("PkMeldewesenPanel.grid-removed"));
    }

    /** A transformation of the active quarterly report, opened as a new document. */
    private interface ReportTransform {
        String apply(String xml) throws java.io.IOException;
    }

    private void transformCopy(ReportTransform transform, String suffix, ViewMode mode, String done) {
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
        showProgress(true);
        FxtGui.executorService.submit(() -> {
            try {
                String result = transform.apply(xml);
                Platform.runLater(() -> {
                    showProgress(false);
                    ui.editorHost().openGeneratedDocument(result, EditorFileType.XML, name + suffix);
                    ui.editorHost().setActiveViewMode(mode);
                    PanelStatus.success(status, done);
                });
            } catch (java.io.IOException | RuntimeException | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    showProgress(false);
                    PanelStatus.failure(status, I18n.t("PkMeldewesenPanel.grid.title"),
                            I18n.t("PkMeldewesenPanel.grid-failed"), I18n.t("PkMeldewesenPanel.report-remedy"),
                            String.valueOf(e.getMessage()), false);
                });
            }
        });
    }

    private void showProgress(boolean on) {
        progress.setProgress(javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS);
        progress.setVisible(on);
        progress.setManaged(on);
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
