package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

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
    private final Label xmlName = new Label("none");
    private final Label xslName = new Label("none");
    private final TextField titleField = new TextField();
    private final TextField authorField = new TextField();
    private final TextField subjectField = new TextField();
    private final CheckBox pdfACompliant = new CheckBox("PDF/A-1b compliant");
    private final ComboBox<String> pageSize = new ComboBox<>();
    private final Label status = new Label("No PDF generated");
    private final Button openButton;
    private final Button previewButton;
    private File xmlOverride;
    private File xslFile;
    private File lastPdf;

    public FopPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-fop-panel");

        // --- header: PDF / FOP -------------------------------------------------
        Label title = new Label("PDF / FOP");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(title, headerSpacer);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- INPUT: XML (active editor or override) + XSL-FO stylesheet --------
        xmlName.setId("fop-xml-name");
        xmlName.getStyleClass().add("fxt-vp-source-name");
        ContextMenu xmlMenu = new ContextMenu();
        MenuItem pickXml = new MenuItem("Select XML file…");
        pickXml.setOnAction(e -> chooseXmlOverride());
        MenuItem useActive = new MenuItem("Use active editor");
        useActive.setOnAction(e -> setXmlOverride(null));
        xmlMenu.getItems().addAll(pickXml, useActive);
        HBox xmlRow = sourceRow("bi-code-slash", xmlName, () ->
                xmlMenu.show(xmlName, Side.BOTTOM, 0, 0));
        refreshXmlName();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshXmlName());

        xslName.setId("fop-xsl-name");
        xslName.getStyleClass().addAll("fxt-vp-source-name", "fxt-vp-source-none");
        HBox xslRow = sourceRow("bi-file-earmark-code", xslName, this::chooseXsl);
        HBox inputHeader = SidePanelLayout.sectionHeader(new Label("INPUT"), xmlRow, xslRow);

        // --- METADATA ------------------------------------------------------------
        titleField.setId("fop-meta-title");
        titleField.setPromptText("Document title");
        authorField.setId("fop-meta-author");
        authorField.setPromptText("Author");
        subjectField.setId("fop-meta-subject");
        subjectField.setPromptText("Subject");
        prefillAuthor();
        VBox metadataBox = new VBox(4,
                fieldLabel("Title"), titleField,
                fieldLabel("Author"), authorField,
                fieldLabel("Subject"), subjectField);
        metadataBox.getStyleClass().add("fxt-tp-section-body");
        HBox metadataHeader = SidePanelLayout.sectionHeader(new Label("METADATA"), metadataBox);

        // --- OPTIONS ---------------------------------------------------------------
        pdfACompliant.setId("fop-pdfa");
        pdfACompliant.setTooltip(new javafx.scene.control.Tooltip(
                "Requires embeddable fonts: the stylesheet must use system fonts, not the PDF base-14 set"));
        pageSize.setId("fop-page-size");
        pageSize.getItems().addAll("A4 · Portrait", "A4 · Landscape", "Letter · Portrait", "Letter · Landscape");
        pageSize.getSelectionModel().selectFirst();
        pageSize.setMaxWidth(Double.MAX_VALUE);
        VBox optionsBox = new VBox(6, pdfACompliant, fieldLabel("Page size"), pageSize);
        optionsBox.getStyleClass().add("fxt-tp-section-body");
        HBox optionsHeader = SidePanelLayout.sectionHeader(new Label("OPTIONS"), optionsBox);

        // --- Generate + result actions ----------------------------------------------
        Button generate = new Button("Generate PDF", icon("bi-file-earmark-pdf", 14));
        generate.setId("fop-generate");
        generate.getStyleClass().add("fxt-primary-button");
        generate.setMaxWidth(Double.MAX_VALUE);
        generate.setOnAction(e -> chooseTargetAndGenerate());
        VBox runBox = new VBox(generate);
        runBox.getStyleClass().add("fxt-vp-run-box");

        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);

        previewButton = toolButton("Preview", "bi-eye", this::previewPdf);
        previewButton.setDisable(true);
        openButton = toolButton("Open PDF", "bi-box-arrow-up-right", this::openPdf);
        openButton.setDisable(true);
        VBox resultBox = new VBox(8, SidePanelLayout.fill(previewButton), SidePanelLayout.fill(openButton));
        resultBox.getStyleClass().add("fxt-vp-run-box");

        VBox content = new VBox(
                inputHeader, xmlRow, xslRow,
                metadataHeader, metadataBox,
                optionsHeader, optionsBox,
                runBox, status, resultBox);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);
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
            status.setText("Select an XSL-FO stylesheet first.");
            return;
        }
        File xmlFile = inputXmlFile();
        if (xmlFile == null) {
            status.setText("No document open.");
            return;
        }
        File xsl = xslFile;
        FopRunner.PdfOptions options = currentOptions();
        status.setText("Generating…");
        openButton.setDisable(true);
        FxtGui.executorService.submit(() -> {
            String result = FopRunner.generate(xmlFile, xsl, pdfOutput, options);
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
        } catch (Exception ignored) {
            status.setText("Could not open PDF (no desktop handler).");
        }
    }

    // ----- shared mockup-language helpers ------------------------------------

    /** A source row: file-type icon · name · "Change" link (shared mockup style). */
    private HBox sourceRow(String iconLiteral, Label nameLabel, Runnable changeAction) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Hyperlink change = new Hyperlink("Change");
        change.getStyleClass().add("fxt-vp-change");
        change.setOnAction(e -> changeAction.run());
        HBox row = new HBox(8, icon(iconLiteral, 15), nameLabel, spacer, change);
        row.getStyleClass().add("fxt-vp-source-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Sets a source-row name, toggling the muted "none" style. */
    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "none");
        label.getStyleClass().remove("fxt-vp-source-none");
        if (name == null) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }

    private Button toolButton(String text, String iconLiteral, Runnable action) {
        Button button = new Button(text, icon(iconLiteral, 16));
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-sig-field-label");
        return label;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
