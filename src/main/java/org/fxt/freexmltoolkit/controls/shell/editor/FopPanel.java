package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The PDF/FOP activity side panel: renders the active XML to PDF using a chosen
 * XSL(-FO) stylesheet via the reused {@link org.fxt.freexmltoolkit.service.FOPService}
 * (through {@link FopRunner}). Generation runs off the UI thread (fixing the old
 * synchronous, UI-blocking behaviour); the generated PDF can be opened.
 */
public class FopPanel extends VBox {

    private final EditorHost editorHost;
    private final Label xslStatus = new Label("XSL: none");
    private final Label status = new Label("No PDF generated");
    private final Button openButton;
    private final Button previewButton;
    private File xslFile;
    private File lastPdf;

    public FopPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("PDF / FOP");
        title.getStyleClass().add("fxt-side-panel-title");

        Button setXsl = button("Set XSL-FO…", "bi-file-earmark-code", this::chooseXsl);
        Button generate = button("Generate PDF…", "bi-file-earmark-pdf", this::chooseTargetAndGenerate);
        previewButton = button("Preview", "bi-eye", this::previewPdf);
        previewButton.setDisable(true);
        openButton = button("Open PDF", "bi-box-arrow-up-right", this::openPdf);
        openButton.setDisable(true);

        xslStatus.getStyleClass().add("fxt-placeholder-text");
        status.getStyleClass().add("fxt-placeholder-text");

        getChildren().addAll(title, new HBox(6, setXsl, generate), xslStatus, status,
                new HBox(6, previewButton, openButton));
    }

    /** Sets the XSL(-FO) stylesheet (also from the file chooser). */
    public void setXslFile(File file) {
        this.xslFile = file;
        xslStatus.setText(file != null ? "XSL: " + file.getName() : "XSL: none");
    }

    /** Generates a PDF from the active XML + the selected XSL into {@code pdfOutput} (async). */
    public void generateTo(File pdfOutput) {
        if (xslFile == null) {
            status.setText("Select an XSL-FO stylesheet first.");
            return;
        }
        File xmlFile = activeXmlFile();
        if (xmlFile == null) {
            status.setText("No document open.");
            return;
        }
        File xsl = xslFile;
        status.setText("Generating…");
        openButton.setDisable(true);
        FxtGui.executorService.submit(() -> {
            String result = FopRunner.generate(xmlFile, xsl, pdfOutput);
            Platform.runLater(() -> {
                boolean ok = result.startsWith("OK:");
                status.setText(ok ? "Generated: " + pdfOutput.getName() : result);
                if (ok) {
                    lastPdf = pdfOutput;
                    openButton.setDisable(false);
                    previewButton.setDisable(false);
                    previewPdf(); // show the result in-app immediately
                }
            });
        });
    }

    /** @return the current status text (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    /** @return the active XML file, writing untitled content to a temp file if needed. */
    private File activeXmlFile() {
        var doc = editorHost.getActiveDocument();
        if (doc.isEmpty()) {
            return null;
        }
        if (doc.get().getPath() != null) {
            return doc.get().getPath().toFile();
        }
        try {
            File temp = File.createTempFile("fxt-fop-", ".xml");
            temp.deleteOnExit();
            Files.writeString(temp.toPath(), editorHost.getActiveText().orElse(""), StandardCharsets.UTF_8);
            return temp;
        } catch (Exception e) {
            return null;
        }
    }

    private void chooseXsl() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSL-FO Stylesheet");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSL", "*.xsl", "*.xslt"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setXslFile(file);
        }
    }

    private void chooseTargetAndGenerate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            generateTo(file);
        }
    }

    /** Opens the last generated PDF in an in-app preview tab. */
    public void previewPdf() {
        if (lastPdf != null && lastPdf.exists()) {
            editorHost.openPdfPreview(lastPdf);
        }
    }

    private void openPdf() {
        if (lastPdf == null || !lastPdf.exists()) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(lastPdf);
        } catch (Exception ignored) {
            status.setText("Could not open PDF (no desktop handler).");
        }
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
