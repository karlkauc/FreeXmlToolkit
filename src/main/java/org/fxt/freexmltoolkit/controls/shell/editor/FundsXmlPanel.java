package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
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
            PanelStatus.info(status, "Downloading…");
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
            } else {
                PanelStatus.failure(status, "Download failed",
                        "Download failed: " + (result.error() == null ? "unknown error" : result.error()));
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
            PanelStatus.info(status, "Downloading…");
        }
    }

    @FXML
    public void onVersionSelected() {
        String v = versionCombo.getValue();
        if (v != null && FundsXmlRunner.setActiveVersion(v)) {
            PanelStatus.info(status, "Active schema version: " + v);
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
        openUrl("https://fundsxml.org/");
    }

    @FXML
    public void onDownload() {
        if (!FundsXmlDownloadCoordinator.getInstance().startBackgroundDownload("panel")) {
            PanelStatus.info(status, "Download already in progress…");
        }
    }

    @FXML
    public void onOpenSchema() {
        java.nio.file.Path schemaFile = FundsXmlRunner.activeSchemaFile();
        if (schemaFile == null) {
            PanelStatus.info(status, "No active schema — download content first.");
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
        PanelStatus.info(status, "Validating…");
        FxtGui.executorService.submit(() -> {
            String summary = FundsXmlRunner.validateSummary(xml);
            Platform.runLater(() -> PanelStatus.info(status, summary));
        });
    }

    @FXML
    public void onGenerateDocs() {
        PanelStatus.info(status, "Generating documentation…");
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
                    PanelStatus.failure(status, "Documentation failed", finalMsg);
                }
            });
        });
    }

    private void openFolder(java.nio.file.Path dir) {
        try {
            if (dir != null && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            PanelStatus.failure(status, "Could not open folder", "Could not open folder: " + e.getMessage(),
                    "Open the folder in your file manager instead.", e);
        }
    }

    private void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            PanelStatus.failure(status, "Could not open browser", "Could not open browser: " + e.getMessage(),
                    "Copy the address into your browser instead.", e);
        }
    }
}
