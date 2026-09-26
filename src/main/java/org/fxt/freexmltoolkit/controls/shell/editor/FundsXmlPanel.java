package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;

/** The FundsXML activity side panel: manage versions, validate the active document, docs/resources. */
public class FundsXmlPanel extends VBox {

    private final EditorHost editorHost;
    private final ComboBox<String> versionCombo = new ComboBox<>();
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar();
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

    public FundsXmlPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("FUNDSXML");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-panel-title");
        status.getStyleClass().add("fxt-placeholder-text");
        status.setWrapText(true);
        progress.setMaxWidth(Double.MAX_VALUE);
        hideProgress();

        // --- Management ---
        versionCombo.getItems().setAll(FundsXmlRunner.installedVersions());
        String active = FundsXmlRunner.activeVersion();
        if (active != null) {
            versionCombo.getSelectionModel().select(active);
        }
        versionCombo.setOnAction(e -> {
            String v = versionCombo.getValue();
            if (v != null && FundsXmlRunner.setActiveVersion(v)) {
                PanelStatus.info(status, "Active schema version: " + v);
            }
        });
        VBox versionBox = new VBox(4, new Label("Active version"), versionCombo);
        versionBox.getStyleClass().add("fxt-tp-section-body");
        PanelActionList management = new PanelActionList(
                PanelAction.of("fundsxml-download", "bi-cloud-arrow-down", "Download / Update Content", this::download));
        HBox managementHeader = SidePanelLayout.sectionHeader(new Label("MANAGEMENT"), versionBox, management);

        // --- Action ---
        PanelActionList validation = new PanelActionList(
                PanelAction.of("fundsxml-validate", "bi-check2-circle", "Validate active document", this::validate));

        // --- Docs & resources ---
        PanelActionList docs = new PanelActionList(
                PanelAction.of("fundsxml-open-schema", "bi-file-earmark-code", "Open Schema in Editor",
                        this::openSchemaInEditor),
                PanelAction.of("fundsxml-generate-docs", "bi-file-earmark-text", "Generate Schema Documentation",
                        this::generateDocs),
                PanelAction.of("fundsxml-examples-folder", "bi-folder2-open", "Open Examples Folder",
                        () -> openFolder(FundsXmlRunner.examplesDir())),
                PanelAction.of("fundsxml-schema-folder", "bi-folder2-open", "Open Schema Folder",
                        () -> openFolder(FundsXmlRunner.schemaDir())),
                PanelAction.of("fundsxml-schematron-folder", "bi-folder2-open", "Open Schematron Folder",
                        () -> openFolder(FundsXmlRunner.schematronDir())),
                PanelAction.of("fundsxml-online-docs", "bi-globe", "Open Online Docs",
                        () -> openUrl("https://fundsxml.org/")));

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(title,
                managementHeader, versionBox, management,
                PanelActionList.section("VALIDATE", false, validation),
                PanelActionList.section("DOCS & RESOURCES", false, docs),
                spacer, progress, status);

        // Observe background downloads (startup sync, settings toggle, this panel's button).
        FundsXmlDownloadCoordinator coordinator = FundsXmlDownloadCoordinator.getInstance();
        coordinator.addListener(downloadListener);
        if (coordinator.isRunning()) {
            showProgress(-1);
            PanelStatus.info(status, "Downloading…");
        }
    }

    private void download() {
        if (!FundsXmlDownloadCoordinator.getInstance().startBackgroundDownload("panel")) {
            PanelStatus.info(status, "Download already in progress…");
        }
    }

    private void openSchemaInEditor() {
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

    private void validate() {
        String xml = editorHost.getActiveText().orElse(null);
        PanelStatus.info(status, "Validating…");
        FxtGui.executorService.submit(() -> {
            String summary = FundsXmlRunner.validateSummary(xml);
            Platform.runLater(() -> PanelStatus.info(status, summary));
        });
    }

    private void generateDocs() {
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
            PanelStatus.failure(status, "Could not open folder", "Could not open folder: " + e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            PanelStatus.failure(status, "Could not open browser", "Could not open browser: " + e.getMessage());
        }
    }

}
