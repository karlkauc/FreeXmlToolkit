package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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
    @FXML private ProgressBar progress;
    private final FundsXmlDownloadCoordinator.Listener downloadListener = new FundsXmlDownloadCoordinator.Listener() {
        @Override
        public void onStarted() {
            showProgress(-1);
            PanelStatus.info(status, I18n.t("FundsXmlPanel.downloading"));
        }

        @Override
        public void onProgress(String stage, String message, double fraction) {
            showProgress(fraction);
            PanelStatus.info(status, stage + " — " + message);
        }

        @Override
        public void onFinished(FundsXmlExtensionService.DownloadResult result) {
            hideProgress();
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
        hideProgress();
        // Initial fill: showing the active version must not count as the user picking one.
        var onVersionSelected = versionCombo.getOnAction();
        versionCombo.setOnAction(null);
        refreshVersions();
        versionCombo.setOnAction(onVersionSelected);

        // Observe background downloads (startup sync, settings toggle, this panel's button).
        FundsXmlDownloadCoordinator coordinator = FundsXmlDownloadCoordinator.getInstance();
        coordinator.addListener(downloadListener);
        if (coordinator.isRunning()) {
            showProgress(-1);
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

    private void showProgress(double fraction) {
        progress.setProgress(fraction < 0 ? ProgressBar.INDETERMINATE_PROGRESS : fraction);
        progress.setVisible(true);
        progress.setManaged(true);
    }

    private void hideProgress() {
        progress.setVisible(false);
        progress.setManaged(false);
    }

    @FXML
    public void onValidate() {
        String xml = editorHost.getActiveText().orElse(null);
        PanelStatus.info(status, I18n.t("FundsXmlPanel.validating"));
        FxtGui.executorService.submit(() -> {
            String summary = FundsXmlRunner.validateSummary(xml);
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
        PanelStatus.info(status, I18n.t("FundsXmlPanel.checking"));
        FxtGui.executorService.submit(() -> {
            var results = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.run(xml);
            String html = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.htmlReport(results, name);
            long failed = results.stream().filter(r -> !r.passed()).count();
            Platform.runLater(() -> {
                editorHost.openGeneratedDocument(html, EditorFileType.HTML, name.replaceFirst("\\.xml$", "") + "-dq.html");
                editorHost.setActiveViewMode(ViewMode.PREVIEW);
                if (failed == 0) {
                    PanelStatus.success(status, I18n.t("FundsXmlPanel.dqPassed", results.size()));
                } else {
                    PanelStatus.info(status, I18n.t("FundsXmlPanel.dqFindings", failed, results.size()));
                }
            });
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
        FxtGui.executorService.submit(() -> {
            try {
                String result = org.fxt.freexmltoolkit.service.fundsxml.FundsXmlGridSetup.apply(xml, true);
                Platform.runLater(() -> {
                    editorHost.openGeneratedDocument(result, EditorFileType.XML, name.replaceFirst("\\.xml$", "") + "-grid.xml");
                    editorHost.setActiveViewMode(ViewMode.GRAPHIC);
                    PanelStatus.success(status, I18n.t("FundsXmlPanel.gridSetupDone"));
                });
            } catch (java.io.IOException e) {
                Platform.runLater(() -> PanelStatus.failure(status, I18n.t("FundsXmlPanel.gridSetupFailed"), e.getMessage(), false));
            }
        });
    }

    @FXML
    public void onGenerateDocs() {
        PanelStatus.info(status, I18n.t("FundsXmlPanel.generating-documentation"));
        FxtGui.executorService.submit(() -> {
            String msg;
            boolean ok = true;
            try {
                var dir = FundsXmlRunner.generateDocumentation();
                msg = "Documentation written to: " + dir;
            } catch (Throwable t) {
                ok = false;
                msg = "Documentation failed: " + t.getMessage();
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
