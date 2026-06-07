package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/** The FundsXML activity side panel: manage versions, validate the active document, docs/resources. */
public class FundsXmlPanel extends VBox {

    private final EditorHost editorHost;
    private final ComboBox<String> versionCombo = new ComboBox<>();
    private final Label status = new Label();

    public FundsXmlPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("FUNDSXML");
        title.getStyleClass().add("fxt-side-panel-title");
        status.getStyleClass().add("fxt-placeholder-text");
        status.setWrapText(true);

        // --- Management ---
        Label mgmt = sectionTitle("MANAGEMENT");
        versionCombo.getItems().setAll(FundsXmlRunner.installedVersions());
        String active = FundsXmlRunner.activeVersion();
        if (active != null) {
            versionCombo.getSelectionModel().select(active);
        }
        versionCombo.setOnAction(e -> {
            String v = versionCombo.getValue();
            if (v != null && FundsXmlRunner.setActiveVersion(v)) {
                status.setText("Active schema version: " + v);
            }
        });
        Button download = button("Download / Update Content", "bi-cloud-arrow-down", this::download);

        // --- Action ---
        Label action = sectionTitle("VALIDATE");
        Button validate = button("Validate active document", "bi-check2-circle", this::validate);

        // --- Docs & resources ---
        Label docs = sectionTitle("DOCS & RESOURCES");
        Button genDocs = button("Generate Schema Documentation", "bi-file-earmark-text", this::generateDocs);
        Button examples = button("Open Examples Folder", "bi-folder2-open",
                () -> openFolder(FundsXmlRunner.examplesDir()));
        Button schema = button("Open Schema Folder", "bi-folder2-open",
                () -> openFolder(FundsXmlRunner.schemaDir()));
        Button schematron = button("Open Schematron Folder", "bi-folder2-open",
                () -> openFolder(FundsXmlRunner.schematronDir()));
        Button online = button("Open Online Docs", "bi-globe",
                () -> openUrl("https://fundsxml.org/"));

        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(title,
                mgmt, new Label("Active version"), versionCombo, SidePanelLayout.fill(download),
                action, SidePanelLayout.fill(validate),
                docs, SidePanelLayout.fill(genDocs), SidePanelLayout.fill(examples),
                SidePanelLayout.fill(schema), SidePanelLayout.fill(schematron),
                SidePanelLayout.fill(online),
                spacer, status);
    }

    private void download() {
        status.setText("Downloading…");
        FxtGui.executorService.submit(() -> {
            String msg;
            try {
                org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService.getInstance()
                        .downloadOrUpdate(
                                org.fxt.freexmltoolkit.service.fundsxml.DownloadProgressCallback.NO_OP);
                msg = "Download complete.";
            } catch (Throwable t) {
                msg = "Download failed: " + t.getMessage();
            }
            String finalMsg = msg;
            Platform.runLater(() -> {
                status.setText(finalMsg);
                versionCombo.getItems().setAll(FundsXmlRunner.installedVersions());
                String active = FundsXmlRunner.activeVersion();
                if (active != null) {
                    versionCombo.getSelectionModel().select(active);
                }
            });
        });
    }

    private void validate() {
        String xml = editorHost.getActiveText().orElse(null);
        status.setText("Validating…");
        FxtGui.executorService.submit(() -> {
            String summary = FundsXmlRunner.validateSummary(xml);
            Platform.runLater(() -> status.setText(summary));
        });
    }

    private void generateDocs() {
        status.setText("Generating documentation…");
        FxtGui.executorService.submit(() -> {
            String msg;
            try {
                var dir = FundsXmlRunner.generateDocumentation();
                msg = "Documentation written to: " + dir;
            } catch (Throwable t) {
                msg = "Documentation failed: " + t.getMessage();
            }
            String finalMsg = msg;
            Platform.runLater(() -> status.setText(finalMsg));
        });
    }

    private void openFolder(java.nio.file.Path dir) {
        try {
            if (dir != null && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            status.setText("Could not open folder: " + e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            status.setText("Could not open browser: " + e.getMessage());
        }
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("fxt-side-panel-title");
        return l;
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
