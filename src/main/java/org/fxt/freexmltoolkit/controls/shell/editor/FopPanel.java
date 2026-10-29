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
    @FXML private Button showFoButton;
    @FXML private Button messagesButton;
    @FXML private Button renderFoButton;
    private final javafx.scene.control.MenuButton xmlFavoritesMenu;
    private final javafx.scene.control.MenuButton xslFavoritesMenu;
    private File xmlOverride;
    private File xslFile;
    private File lastPdf;
    /** Outcome of the latest generation (intermediate FO, messages, FOP events). */
    private FopRunner.FopResult lastResult;
    /** The editor tab showing the latest intermediate FO, if still open. */
    private OpenDocument foDocument;

    /** Loads the layout from {@code /pages/panels/FopPanel.fxml} and wires the data-driven parts. */
    public FopPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/FopPanel.fxml");

        xmlFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XML,
                "XML favorites", this::pickXml);
        xmlRow.addExtras(xmlFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xmlRow,
                org.fxt.freexmltoolkit.service.DragDropService.XML_EXTENSIONS, this::pickXml);
        refreshXmlName();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshXmlName());

        xslFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSLT,
                "XSLT favorites", this::pickXsl);
        xslRow.addExtras(xslFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xslRow,
                org.fxt.freexmltoolkit.service.DragDropService.XSLT_EXTENSIONS, this::pickXsl);

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

    /** Opens the intermediate XSL-FO of the latest generation as an editor tab. */
    @FXML
    public void onShowFo() {
        showIntermediateFo();
    }

    /** Shows the stylesheet's messages and FOP's events of the latest generation in OUTPUT. */
    @FXML
    public void onShowMessages() {
        showMessages();
    }

    /** Starts the XSLT debugger on the XSL-FO stylesheet (the FO result lands in OUTPUT). */
    @FXML
    public void onDebug() {
        startDebug();
    }

    /** @return {@code true} once an XSL-FO stylesheet is selected. */
    public boolean hasStylesheet() {
        return xslFile != null;
    }

    /**
     * Debugs the XSL-FO stylesheet against the XML input with the shared XSLT debugger:
     * breakpoints, stepping and variables in the stylesheet; the produced FO is shown in the
     * OUTPUT panel when the run ends. The page-size / orientation parameters are passed on.
     */
    public void startDebug() {
        if (xslFile == null) {
            PanelStatus.precondition(status, "Select an XSL-FO stylesheet first.");
            return;
        }
        File xmlFile = inputXmlFile();
        if (xmlFile == null) {
            PanelStatus.precondition(status, "No document open.");
            return;
        }
        String xml;
        try {
            xml = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            PanelStatus.failure(status, "Could not read XML", "Could not read the XML input: " + e.getMessage());
            return;
        }
        java.util.Map<String, Object> parameters = new java.util.HashMap<>(currentOptions().stylesheetParameters());
        editorHost.setDebugInputFile(xmlFile.toPath()); // the paused context node is shown in it
        editorHost.startXsltDebug(xslFile, xml, parameters,
                org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML, this::onDebugRunFinished);
    }

    /** A debugged run produced its FO: keep it for "Show intermediate FO" / "Render PDF from FO…". */
    private void onDebugRunFinished(org.fxt.freexmltoolkit.service.XsltTransformationResult result) {
        if (result == null || !result.isSuccess()) {
            return;
        }
        lastResult = new FopRunner.FopResult(false, null, result.getOutputContent(), result.getMessages(),
                java.util.List.of(), null);
        showFoButton.setDisable(false);
        messagesButton.setDisable(false);
        messagesButton.setText(lastResult.hasNotes()
                ? "Messages & events (" + lastResult.messages().size() + ")" : "Messages & events");
        renderFoButton.setDisable(false);
        PanelStatus.info(status, "Debug run finished · FO in OUTPUT · " + result.getExecutionTime() + " ms");
    }

    /** Renders the FO of the latest (debug) run to a PDF chosen by the user. */
    @FXML
    public void onRenderFo() {
        if (lastResult == null || lastResult.foXml() == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            renderFoTo(file);
        }
    }

    /** Renders the FO of the latest run into {@code pdfOutput} (async); also used by tests. */
    public void renderFoTo(File pdfOutput) {
        if (lastResult == null || lastResult.foXml() == null) {
            PanelStatus.precondition(status, "Debug or generate first — there is no FO to render yet.");
            return;
        }
        String fo = lastResult.foXml();
        FopRunner.PdfOptions options = currentOptions();
        PanelStatus.info(status, "Rendering…");
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            FopRunner.FopResult outcome = FopRunner.renderFo(fo, pdfOutput, options);
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.pdfGenerated(t0, outcome.stage(), outcome.failure());
            Platform.runLater(() -> {
                lastResult = new FopRunner.FopResult(outcome.ok(), outcome.pdf(), fo,
                        lastResult != null ? lastResult.messages() : java.util.List.of(), outcome.events(), outcome.error(),
                        outcome.stage(), outcome.failure());
                messagesButton.setDisable(false);
                if (outcome.ok()) {
                    PanelStatus.success(status, "Generated: " + pdfOutput.getName());
                    lastPdf = pdfOutput;
                    openButton.setDisable(false);
                    previewButton.setDisable(false);
                    previewPdf();
                } else {
                    // fop_pdf already carries the failure; the dialog must not report it a second time.
                    PanelStatus.failure(status, "PDF generation failed", "PDF generation failed.",
                            "FOP rejected the FO document.", PanelStatus.strip(outcome.asText()), false);
                    editorHost.transformOutputPanel().showMessages("PDF / FOP · rendering failed",
                            PanelStatus.strip(outcome.asText()) + "\n\n" + lastResult.notesText());
                }
            });
        });
    }

    private void showIntermediateFo() {
        if (lastResult == null || lastResult.foXml() == null) {
            return;
        }
        String name = (xslFile != null ? stripExtension(xslFile.getName()) : "document") + ".fo";
        boolean sameTab = foDocument != null && name.equals(foDocument.getDisplayName());
        if (!sameTab || !editorHost.updateGeneratedDocument(foDocument, lastResult.foXml())) {
            foDocument = editorHost.openGeneratedDocument(lastResult.foXml(), EditorFileType.XML, name);
        }
    }

    private void showMessages() {
        if (lastResult == null) {
            return;
        }
        String title = "PDF / FOP · " + lastResult.messages().size() + " message"
                + (lastResult.messages().size() == 1 ? "" : "s") + " · " + lastResult.events().size()
                + " FOP event" + (lastResult.events().size() == 1 ? "" : "s");
        editorHost.transformOutputPanel().showMessages(title, lastResult.notesText());
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** @return the outcome of the latest generation (for tests/observers), or {@code null}. */
    public FopRunner.FopResult getLastResult() {
        return lastResult;
    }

    /** Sets the XSL(-FO) stylesheet (also from the file chooser). */
    public void setXslFile(File file) {
        this.xslFile = file;
        xslRow.showFile(file);
    }

    /** An XML input picked from the favorites or dropped on the row: set it and show it in the editor (it is the input either way). */
    void pickXml(File file) {
        setXmlOverride(file);
        editorHost.openFile(file.toPath(), true);
    }

    /**
     * A stylesheet picked from the favorites or dropped on the row: set it and load it into the
     * editor. Its tab only comes to the front while the XML input is a fixed file — otherwise the
     * input follows the active document.
     */
    void pickXsl(File file) {
        editorHost.openFile(file.toPath(), xmlOverride != null);
        setXslFile(file);
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
            FopRunner.FopResult outcome = FopRunner.generateResult(xmlFile, xsl, pdfOutput, options);
            String result = outcome.asText();
            boolean generated = outcome.ok();
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.pdfGenerated(t0, outcome.stage(), outcome.failure());
            long elapsedMs = probe.finish(xmlFile.length(), generated ? pdfOutput.length() : -1, generated,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            boolean showStats = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().isEnabled();
            Platform.runLater(() -> {
                if (abandoned.get()) {
                    return; // user cancelled — ignore the (possibly still-produced) result
                }
                progress.finish();
                generateButton.setDisable(false);
                lastResult = outcome;
                showFoButton.setDisable(outcome.foXml() == null);
                renderFoButton.setDisable(outcome.foXml() == null);
                messagesButton.setDisable(false);
                messagesButton.setText(outcome.hasNotes()
                        ? "Messages & events (" + (outcome.messages().size() + outcome.events().size()) + ")"
                        : "Messages & events");
                if (outcome.ok()) {
                    long warnings = outcome.warningCount();
                    PanelStatus.success(status, "Generated: " + pdfOutput.getName()
                            + (showStats ? " · " + elapsedMs + " ms" : "")
                            + (warnings > 0 ? " · " + warnings + " FOP warning" + (warnings == 1 ? "" : "s") : ""));
                    lastPdf = pdfOutput;
                    openButton.setDisable(false);
                    previewButton.setDisable(false);
                    previewPdf(); // show the result in-app immediately
                } else {
                    // fop_pdf already carries the failure; the dialog must not report it a second time.
                    PanelStatus.failure(status, "PDF generation failed",
                            "PDF generation failed.",
                            "Check that the XML and the XSL-FO stylesheet are valid and the target file is writable, then try again.",
                            PanelStatus.strip(result), false);
                    // The details (stylesheet error with its location, FOP's reason) go to OUTPUT.
                    editorHost.transformOutputPanel().showMessages("PDF / FOP · generation failed",
                            PanelStatus.strip(result) + "\n\n" + outcome.notesText());
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
            // the panel then says "No document open"; the failure itself is counted via the log bridge
            org.apache.logging.log4j.LogManager.getLogger(FopPanel.class)
                    .warn("Could not write the unsaved document to a temp file for FOP", e);
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
        if (xmlOverride != null) {
            xmlRow.showFile(xmlOverride);
            return;
        }
        var active = editorHost.getActiveDocument();
        xmlRow.showName(active.map(OpenDocument::getDisplayName).orElse(null),
                active.map(d -> d.getPath() != null ? d.getPath().toString() + " (active document)" : "Active document (unsaved)")
                        .orElse(null));
    }

    private void chooseXsl() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSL-FO Stylesheet");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSL", "*.xsl", "*.xslt"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            pickXsl(file);
        }
    }

    private void chooseXmlOverride() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XML file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            pickXml(file);
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
        org.fxt.freexmltoolkit.util.DesktopActions.open(lastPdf, e ->
                PanelStatus.failure(status, "Could not open PDF", "Could not open PDF (no desktop handler)."));
    }

    // ----- shared mockup-language helpers ------------------------------------

}
