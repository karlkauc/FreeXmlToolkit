package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;

/** The FundsXML activity side panel: manage versions, validate the active document, docs/resources. */
public class FundsXmlPanel extends VBox {

    private final EditorHost editorHost;
    @FXML private ComboBox<String> versionCombo;
    @FXML private Label status;
    @FXML private PanelProgress progress;
    private PanelJobs jobs;
    /** The job showing a background download, or {@code null}. */
    private PanelJobs.Job downloadJob;
    @FXML private PanelActionButton onlineButton;
    @FXML private org.fxt.freexmltoolkit.plugins.IdentifierChecker identifierChecker;
    private final FundsXmlDownloadCoordinator.Listener downloadListener = new FundsXmlDownloadCoordinator.Listener() {
        @Override
        public void onStarted() {
            showDownload(-1, null);
            PanelStatus.info(status, I18n.t("FundsXmlPanel.downloading"));
        }

        @Override
        public void onProgress(String stage, String message, double fraction) {
            showDownload(fraction, stage + " — " + message);
            PanelStatus.info(status, stage + " — " + message);
        }

        @Override
        public void onFinished(FundsXmlExtensionService.DownloadResult result) {
            endDownload();
            if (result.isSuccess()) {
                PanelStatus.success(status, result.schemaVersion() != null
                        ? "Download complete — schema version " + result.schemaVersion() + "."
                        : "Download complete.");
            } else if (result.cause() != null) {
                PanelStatus.failure(status, I18n.t("FundsXmlPanel.download-failed"),
                        I18n.t("FundsXmlPanel.download-failed-2", (result.error() == null ? "unknown error" : result.error())),
                        I18n.t("FundsXmlPanel.check-your-internet-connection-and"), result.cause());
            } else {
                PanelStatus.failure(status, I18n.t("FundsXmlPanel.download-failed"),
                        I18n.t("FundsXmlPanel.download-failed-2", (result.error() == null ? "unknown error" : result.error())));
            }
            refreshVersions();
        }
    };

    /** Loads the layout from {@code /pages/panels/FundsXmlPanel.fxml} and fills the versions. */
    public FundsXmlPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/FundsXmlPanel.fxml");
        jobs = new PanelJobs(progress);
        boolean online = org.fxt.freexmltoolkit.service.finance.IdentifierCheck.anyAllowed();
        onlineButton.setVisible(online);
        onlineButton.setManaged(online);
        identifierChecker.setKinds(java.util.List.of(org.fxt.freexmltoolkit.plugins.IdentifierKind.ISIN,
                org.fxt.freexmltoolkit.plugins.IdentifierKind.LEI, org.fxt.freexmltoolkit.plugins.IdentifierKind.BIC,
                org.fxt.freexmltoolkit.plugins.IdentifierKind.IBAN));
        // Initial fill: showing the active version must not count as the user picking one.
        var onVersionSelected = versionCombo.getOnAction();
        versionCombo.setOnAction(null);
        refreshVersions();
        versionCombo.setOnAction(onVersionSelected);

        // Observe background downloads (startup sync, settings toggle, this panel's button).
        FundsXmlDownloadCoordinator coordinator = FundsXmlDownloadCoordinator.getInstance();
        coordinator.addListener(downloadListener);
        if (coordinator.isRunning()) {
            showDownload(-1, null);
            PanelStatus.info(status, I18n.t("FundsXmlPanel.downloading"));
        }
    }

    @FXML
    public void onVersionSelected() {
        String v = versionCombo.getValue();
        if (v != null && FundsXmlRunner.setActiveVersion(v)) {
            PanelStatus.info(status, I18n.t("FundsXmlPanel.active-schema-version", v));
        }
    }

    @FXML
    public void onOpenExamplesFolder() {
        openFolder(FundsXmlRunner.examplesDir());
    }

    @FXML
    public void onOpenSchemaFolder() {
        openFolder(FundsXmlRunner.schemaDir());
    }

    @FXML
    public void onOpenSchematronFolder() {
        openFolder(FundsXmlRunner.schematronDir());
    }

    @FXML
    public void onOpenOnlineDocs() {
        openUrl(org.fxt.freexmltoolkit.util.ProjectLinks.FUNDSXML_SITE_URL);
    }

    @FXML
    public void onDownload() {
        if (!FundsXmlDownloadCoordinator.getInstance().startBackgroundDownload("panel")) {
            PanelStatus.info(status, I18n.t("FundsXmlPanel.download-already-in-progress"));
        }
    }

    @FXML
    public void onOpenSchema() {
        java.nio.file.Path schemaFile = FundsXmlRunner.activeSchemaFile();
        if (schemaFile == null) {
            PanelStatus.info(status, I18n.t("FundsXmlPanel.no-active-schema-download-content"));
            return;
        }
        editorHost.openFile(schemaFile);
    }

    private void refreshVersions() {
        versionCombo.getItems().setAll(FundsXmlRunner.installedVersions());
        String active = FundsXmlRunner.activeVersion();
        if (active != null) {
            versionCombo.getSelectionModel().select(active);
        }
    }

    /** Shows a background download in the progress block ({@code fraction < 0}: not known yet). */
    private void showDownload(double fraction, String message) {
        if (downloadJob == null) {
            downloadJob = jobs.start("download", I18n.t("FundsXmlPanel.downloading"), false);
        }
        if (downloadJob != null) {
            downloadJob.progress(fraction < 0 ? 0 : Math.round(fraction * 1000), fraction < 0 ? 0 : 1000);
            if (message != null) {
                downloadJob.message(message);
            }
        }
    }

    private void endDownload() {
        if (downloadJob != null) {
            downloadJob.finish();
            downloadJob = null;
        }
    }

    /** Starts a job of the panel, or says it is running already. @return the job or {@code null} */
    private PanelJobs.Job start(String id, String label, boolean cancellable) {
        PanelJobs.Job job = jobs.start(id, label, cancellable);
        if (job == null) {
            PanelStatus.info(status, I18n.t("IndustryPanel.already-running", label));
        }
        return job;
    }

    @FXML
    public void onValidate() {
        String xml = editorHost.getActiveText().orElse(null);
        if (xml == null) {
            PanelStatus.precondition(status, I18n.t("IndustryPanel.no-document"));
            return;
        }
        PanelJobs.Job job = start("validate", I18n.t("FundsXmlPanel.validating"), false);
        if (job == null) {
            return;
        }
        job.message(I18n.t("FundsXmlPanel.validating-schema"));
        PanelStatus.info(status, I18n.t("FundsXmlPanel.validating"));
        FxtGui.executorService.submit(() -> {
            String summary;
            try {
                summary = FundsXmlRunner.validateSummary(xml);
            } finally {
                job.finish();
            }
            Platform.runLater(() -> PanelStatus.info(status, summary));
        });
    }

    /** Runs the data-quality checks on the active document and opens the report. */
    @FXML
    public void onDataQuality() {
        String xml = editorHost.getActiveText().orElse(null);
        if (xml == null || !org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.isFundsXml(xml)) {
            PanelStatus.precondition(status, I18n.t("FundsXmlPanel.notFundsXml"));
            return;
        }
        String name = editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse("document");
        PanelJobs.Job job = start("dq", I18n.t("FundsXmlPanel.checking"), true);
        if (job == null) {
            return;
        }
        PanelStatus.info(status, I18n.t("FundsXmlPanel.checking"));
        FxtGui.executorService.submit(() -> {
            java.util.List<org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.Result> results;
            try {
                results = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.run(xml, job);
            } catch (org.fxt.freexmltoolkit.service.progress.CancelledException e) {
                job.finish();
                Platform.runLater(() -> PanelStatus.info(status, I18n.t("IndustryPanel.cancelled", I18n.t("FundsXmlPanel.checking"))));
                return;
            } catch (RuntimeException | OutOfMemoryError e) {
                job.finish();
                Platform.runLater(() -> PanelStatus.failure(status, I18n.t("FundsXmlPanel.checking"),
                        String.valueOf(e.getMessage()), false));
                return;
            }
            job.message(I18n.t("FundsXmlPanel.writing-report"));
            String html = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.htmlReport(results, name);
            long failed = results.stream().filter(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.Result::failed).count();
            long hints = results.stream().filter(r -> !r.passed() && !r.failed()).count();
            job.finish();
            Platform.runLater(() -> {
                editorHost.openGeneratedDocument(html, EditorFileType.HTML, name.replaceFirst("\\.xml$", "") + "-dq.html");
                editorHost.setActiveViewMode(ViewMode.PREVIEW);
                if (failed == 0 && hints > 0) {
                    PanelStatus.success(status, I18n.t("FundsXmlPanel.dqPassedWithHints", results.size(), hints));
                } else if (failed == 0) {
                    PanelStatus.success(status, I18n.t("FundsXmlPanel.dqPassed", results.size()));
                } else {
                    PanelStatus.info(status, I18n.t("FundsXmlPanel.dqFindings", failed, results.size()));
                }
            });
        });
    }

    /** Checks the LEIs and ISINs of the active FundsXML document online; findings go to the PROBLEMS panel. */
    @FXML
    public void onIdentifiersOnline() {
        String xml = editorHost.getActiveText().orElse(null);
        if (xml == null || !org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.isFundsXml(xml)) {
            PanelStatus.precondition(status, I18n.t("FundsXmlPanel.notFundsXml"));
            return;
        }
        PanelJobs.Job job = start("identifiers", I18n.t("IdentifierDocumentCheck.title"), true);
        if (job == null) {
            return;
        }
        PanelStatus.info(status, I18n.t("IndustryPanel.working", I18n.t("IdentifierDocumentCheck.title")));
        IdentifierDocumentCheck.runOnActive(editorHost, job, outcome -> {
            job.finish();
            outcome.showIn(status);
        });
    }

    /** Adds the grid formulas and filters to a copy of the active document and opens it in the grid. */
    @FXML
    public void onGridSetup() {
        String xml = editorHost.getActiveText().orElse(null);
        if (xml == null || !org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.isFundsXml(xml)) {
            PanelStatus.precondition(status, I18n.t("FundsXmlPanel.notFundsXml"));
            return;
        }
        if (org.fxt.freexmltoolkit.service.fundsxml.FundsXmlGridSetup.isSigned(xml)
                && !org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("FundsXmlPanel.fundsxml-grid-setup.text"),
                        I18n.t("FundsXmlPanel.signedHeader"), I18n.t("FundsXmlPanel.signedContent"))) {
            return;
        }
        String name = editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse("document.xml");
        PanelJobs.Job job = start("grid", I18n.t("FundsXmlPanel.fundsxml-grid-setup.text"), false);
        if (job == null) {
            return;
        }
        job.message(I18n.t("FundsXmlPanel.grid-adding"));
        FxtGui.executorService.submit(() -> {
            try {
                String result = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlGridSetup.apply(xml, true);
                job.finish();
                Platform.runLater(() -> {
                    editorHost.openGeneratedDocument(result, EditorFileType.XML, name.replaceFirst("\\.xml$", "") + "-grid.xml");
                    editorHost.setActiveViewMode(ViewMode.GRAPHIC);
                    PanelStatus.success(status, I18n.t("FundsXmlPanel.gridSetupDone"));
                });
            } catch (java.io.IOException | RuntimeException e) {
                job.finish();
                Platform.runLater(() -> PanelStatus.failure(status, I18n.t("FundsXmlPanel.gridSetupFailed"), e.getMessage(), false));
            }
        });
    }

    /** Test hook: answers the confirmation of {@link #onPercentages} (gets header and details) instead of a dialog. */
    static java.util.function.Predicate<String> percentagesConfirmer;
    /** Lines of changes and skipped positions the confirmation lists before "… and N more". */
    private static final int LISTED = 30;

    /**
     * Sets the TotalPercentage of every portfolio position of the active document from its value and the net
     * asset value: plans in the background, asks once, applies the edits as one undo step - if the document did
     * not change meanwhile.
     */
    @FXML
    public void onPercentages() {
        String xml = editorHost.getActiveText().orElse(null);
        EditorHost.ValidationTarget target = editorHost.validationTarget();
        if (xml == null || target == null || !org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.isFundsXml(xml)) {
            PanelStatus.precondition(status, I18n.t("FundsXmlPanel.notFundsXml"));
            return;
        }
        String title = I18n.t("FundsXmlPanel.fundsxml-percentages.text");
        if (org.fxt.freexmltoolkit.service.fundsxml.FundsXmlGridSetup.isSigned(xml) && percentagesConfirmer == null
                && !org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(title,
                        I18n.t("FundsXmlPanel.signedHeader"), I18n.t("FundsXmlPanel.percentages-signed"))) {
            return;
        }
        PanelJobs.Job job = start("percentages", title, false);
        if (job == null) {
            return;
        }
        job.message(I18n.t("FundsXmlPanel.percentages-computing"));
        FxtGui.executorService.submit(() -> {
            try {
                var plan = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPercentages.plan(xml);
                job.finish();
                Platform.runLater(() -> confirmAndApply(plan, target, title));
            } catch (java.io.IOException | RuntimeException e) {
                job.finish();
                Platform.runLater(() -> PanelStatus.failure(status, I18n.t("FundsXmlPanel.percentages-failed"),
                        e.getMessage(), false));
            }
        });
    }

    private void confirmAndApply(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPercentages.Plan plan,
                                 EditorHost.ValidationTarget target, String title) {
        if (plan.edits().isEmpty()) {
            if (plan.skipped().isEmpty()) {
                PanelStatus.success(status, I18n.t("FundsXmlPanel.percentages-all-right", plan.unchanged()));
            } else {
                PanelStatus.info(status, I18n.t("FundsXmlPanel.percentages-right-skipped", plan.unchanged(),
                        plan.skipped().size(), plan.skipped().getFirst().reason()));
            }
            return;
        }
        String header = I18n.t("FundsXmlPanel.percentages-header", plan.inserted(), plan.corrected(),
                plan.unchanged(), plan.skipped().size());
        StringBuilder detail = new StringBuilder();
        int shown = 0;
        for (var c : plan.changes()) {
            if (shown++ == LISTED) {
                detail.append(I18n.t("FundsXmlPanel.percentages-more", plan.changes().size() - LISTED)).append('\n');
                break;
            }
            detail.append(c.position()).append(" (").append(c.owner()).append(", ").append(c.navDate()).append("): ")
                    .append(c.inserted() ? "+ " + c.to() : c.from() + " → " + c.to()).append('\n');
        }
        if (!plan.skipped().isEmpty()) {
            detail.append('\n').append(I18n.t("FundsXmlPanel.percentages-skipped")).append('\n');
            plan.skipped().stream().limit(LISTED / 3).forEach(s -> detail.append(s.position()).append(" (")
                    .append(s.owner()).append(", ").append(s.navDate()).append("): ").append(s.reason()).append('\n'));
            if (plan.skipped().size() > LISTED / 3) {
                detail.append(I18n.t("FundsXmlPanel.percentages-more", plan.skipped().size() - LISTED / 3)).append('\n');
            }
        }
        boolean yes = percentagesConfirmer != null ? percentagesConfirmer.test(header + "\n" + detail)
                : org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(title, header,
                        detail + "\n" + I18n.t("FundsXmlPanel.percentages-note"));
        if (!yes) {
            return;
        }
        EditorHost.ValidationTarget now = editorHost.validationTarget();
        if (now == null || now.tab() != target.tab() || now.revision() != target.revision()) {
            PanelStatus.info(status, I18n.t("FundsXmlPanel.percentages-changed"));
            return;
        }
        if (editorHost.replaceActiveTextEdits(plan.edits())) {
            PanelStatus.success(status, I18n.t("FundsXmlPanel.percentages-done", plan.inserted(), plan.corrected()));
        }
    }

    @FXML
    public void onGenerateDocs() {
        PanelJobs.Job job = start("docs", I18n.t("FundsXmlPanel.generating-documentation"), false);
        if (job == null) {
            return;
        }
        PanelStatus.info(status, I18n.t("FundsXmlPanel.generating-documentation"));
        FxtGui.executorService.submit(() -> {
            String msg;
            boolean ok = true;
            try {
                var dir = FundsXmlRunner.generateDocumentation(job);
                msg = "Documentation written to: " + dir;
            } catch (Throwable t) {
                ok = false;
                msg = "Documentation failed: " + t.getMessage();
            } finally {
                job.finish();
            }
            String finalMsg = msg;
            boolean finalOk = ok;
            Platform.runLater(() -> {
                if (finalOk) {
                    PanelStatus.success(status, finalMsg);
                } else {
                    // schema_doc already carries the failure
                    PanelStatus.failure(status, I18n.t("FundsXmlPanel.documentation-failed"), finalMsg, false);
                }
            });
        });
    }

    private void openFolder(java.nio.file.Path dir) {
        if (dir == null) {
            return;
        }
        org.fxt.freexmltoolkit.util.DesktopActions.open(dir.toFile(), e ->
                PanelStatus.failure(status, I18n.t("FundsXmlPanel.could-not-open-folder"), I18n.t("FundsXmlPanel.could-not-open-folder-2", e.getMessage()),
                        I18n.t("FundsXmlPanel.open-the-folder-in-your"), e));
    }

    private void openUrl(String url) {
        java.util.function.Consumer<Exception> failed = e -> PanelStatus.failure(status, I18n.t("FundsXmlPanel.could-not-open-browser"),
                I18n.t("FundsXmlPanel.could-not-open-browser-2", e.getMessage()), I18n.t("FundsXmlPanel.copy-the-address-into-your"), e);
        try {
            org.fxt.freexmltoolkit.util.DesktopActions.browse(new java.net.URI(url), failed);
        } catch (java.net.URISyntaxException e) {
            failed.accept(e);
        }
    }
}
