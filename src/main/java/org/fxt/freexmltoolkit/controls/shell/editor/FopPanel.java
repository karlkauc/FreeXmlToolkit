package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * The PDF/FOP activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — PDF · FOP" (node 49:2): an INPUT section (XML following
 * the active editor or a fixed override, plus the XSL-FO stylesheet), a METADATA
 * section (PDF title/author/subject), an OPTIONS section (PDF/A-1b conformance,
 * page size passed to the stylesheet), and a primary "Generate PDF" button.
 * Generation runs off the UI thread via {@link FopRunner}; the generated PDF
 * opens in the in-app preview.
 */
public class FopPanel extends VBox {

    private final EditorHost editorHost;
    @FXML private ContextMenu xmlMenu;
    @FXML private SourceRow xmlRow;
    @FXML private SourceRow xslRow;
    @FXML private Label xmlName;
    @FXML private Label xslName;
    @FXML private TextField titleField;
    @FXML private TextField authorField;
    @FXML private TextField subjectField;
    @FXML private CheckBox pdfACompliant;
    @FXML private ComboBox<String> pageSize;
    @FXML private Label status;
    @FXML private PanelProgress progress;
    @FXML private Button generateButton;
    @FXML private Button openButton;
    @FXML private Button previewButton;
    private final javafx.scene.control.MenuButton xmlFavoritesMenu;
    private final javafx.scene.control.MenuButton xslFavoritesMenu;
    private File xmlOverride;
    private File xslFile;
    private File lastPdf;

    /** Loads the layout from {@code /pages/panels/FopPanel.fxml} and wires the data-driven parts. */
    public FopPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/FopPanel.fxml");

        xmlFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XML,
                "XML favorites", this::setXmlOverride);
        xmlRow.addExtras(xmlFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xmlRow,
                org.fxt.freexmltoolkit.service.DragDropService.XML_EXTENSIONS, this::setXmlOverride);
        refreshXmlName();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshXmlName());

        xslFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSLT,
                "XSLT favorites", this::setXslFile);
        xslRow.addExtras(xslFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xslRow,
                org.fxt.freexmltoolkit.service.DragDropService.XSLT_EXTENSIONS, this::setXslFile);

        prefillAuthor();
        pageSize.getSelectionModel().selectFirst();
    }

    @FXML
    public void onChangeXml() {
        xmlMenu.show(xmlName, Side.BOTTOM, 0, 0);
    }

    @FXML
    public void onSelectXmlFile() {
        chooseXmlOverride();
    }

    @FXML
    public void onUseActiveEditor() {
        setXmlOverride(null);
    }

    @FXML
    public void onChooseXsl() {
        chooseXsl();
    }

    @FXML
    public void onGenerate() {
        chooseTargetAndGenerate();
    }

    @FXML
    public void onPreviewPdf() {
        previewPdf();
    }

    @FXML
    public void onOpenPdf() {
        openPdf();
    }

    /** Sets the XSL(-FO) stylesheet (also from the file chooser). */
    public void setXslFile(File file) {
        this.xslFile = file;
        setSourceName(xslName, file != null ? file.getName() : null);
    }

    /** Fixes the XML input to a file, or {@code null} to follow the active editor again. */
    public void setXmlOverride(File file) {
        this.xmlOverride = file;
        refreshXmlName();
    }

    /** Generates a PDF from the XML input + the selected XSL into {@code pdfOutput} (async). */
    public void generateTo(File pdfOutput) {
        if (xslFile == null) {
            PanelStatus.precondition(status, "Select an XSL-FO stylesheet first.");
            return;
        }
        File xmlFile = inputXmlFile();
        if (xmlFile == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        File xsl = xslFile;
        FopRunner.PdfOptions options = currentOptions();
        PanelStatus.info(status, "Generating…");
        openButton.setDisable(true);
        generateButton.setDisable(true);
        // PDF rendering is a single opaque call, so cancellation frees the UI and
        // abandons the result (best-effort interrupt); it may not stop FOP mid-render.
        java.util.concurrent.atomic.AtomicBoolean abandoned = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.Future<?>[] task = new java.util.concurrent.Future<?>[1];
        progress.beginIndeterminate(() -> {
            abandoned.set(true);
            if (task[0] != null) {
                task[0].cancel(true);
            }
            progress.finish();
            generateButton.setDisable(false);
            PanelStatus.info(status, "Cancelled");
        });
        task[0] = FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.FOP_PDF, pdfOutput.getName());
            String result = FopRunner.generate(xmlFile, xsl, pdfOutput, options);
            boolean generated = result.startsWith("OK:");
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.pdfGenerated(t0, generated);
            long elapsedMs = probe.finish(xmlFile.length(), generated ? pdfOutput.length() : -1, generated,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            boolean showStats = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().isEnabled();
            Platform.runLater(() -> {
                if (abandoned.get()) {
                    return; // user cancelled — ignore the (possibly still-produced) result
                }
                progress.finish();
                generateButton.setDisable(false);
                boolean ok = result.startsWith("OK:");
                if (ok) {
                    PanelStatus.success(status, "Generated: " + pdfOutput.getName()
                            + (showStats ? " · " + elapsedMs + " ms" : ""));
                    lastPdf = pdfOutput;
                    openButton.setDisable(false);
                    previewButton.setDisable(false);
                    previewPdf(); // show the result in-app immediately
                } else {
                    PanelStatus.failure(status, "PDF generation failed",
                            "PDF generation failed.",
                            "Check that the XML and the XSL-FO stylesheet are valid and the target file is writable, then try again.",
                            PanelStatus.strip(result));
                }
            });
        });
    }

    /** @return the metadata + options currently configured in the panel. */
    FopRunner.PdfOptions currentOptions() {
        String size = pageSize.getValue() != null ? pageSize.getValue() : "";
        String[] parts = size.split(" · ", 2);
        return new FopRunner.PdfOptions(titleField.getText(), authorField.getText(), subjectField.getText(),
                pdfACompliant.isSelected(), parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : "");
    }

    /** Sets the PDF metadata fields (for tests/observers). */
    public void setMetadata(String title, String author, String subject) {
        titleField.setText(title);
        authorField.setText(author);
        subjectField.setText(subject);
    }

    /** @return the current status text (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    /** @return the XML input: the override file, or the active document (temp-saved if untitled). */
    private File inputXmlFile() {
        if (xmlOverride != null) {
            return xmlOverride;
        }
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

    /** Pre-fills the author from the configured user name (best-effort). */
    private void prefillAuthor() {
        try {
            var metadata = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.ExportMetadataService.class);
            String userName = metadata.getUserName();
            if (userName != null && !userName.isBlank()) {
                authorField.setText(userName);
            }
        } catch (Throwable ignored) {
            // no registry (tests) or no configured user - leave empty
        }
    }

    /** Updates the INPUT row label: the override file, or the active document (live). */
    private void refreshXmlName() {
        String name = xmlOverride != null
                ? xmlOverride.getName()
                : editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse(null);
        setSourceName(xmlName, name);
    }

    private void chooseXsl() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSL-FO Stylesheet");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSL", "*.xsl", "*.xslt"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setXslFile(file);
        }
    }

    private void chooseXmlOverride() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XML file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setXmlOverride(file);
        }
    }

    /** @return the favorite-XSLT names currently in the stylesheet star menu (for tests). */
    public java.util.List<String> xslFavoriteNames() {
        FavoritesMenu.populate(xslFavoritesMenu,
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSLT, this::setXslFile);
        return FavoritesMenu.leafNames(xslFavoritesMenu);
    }

    /** @return the favorite-XML names currently in the input star menu (for tests). */
    public java.util.List<String> xmlFavoriteNames() {
        FavoritesMenu.populate(xmlFavoritesMenu,
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XML, this::setXmlOverride);
        return FavoritesMenu.leafNames(xmlFavoritesMenu);
    }

    private void chooseTargetAndGenerate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
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
        } catch (Exception e) {
            PanelStatus.failure(status, "Could not open PDF", "Could not open PDF (no desktop handler).");
        }
    }

    // ----- shared mockup-language helpers ------------------------------------

    /** Sets a source-row name, toggling the muted "none" style. */
    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "none");
        label.getStyleClass().remove("fxt-vp-source-none");
        if (name == null) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }
}
