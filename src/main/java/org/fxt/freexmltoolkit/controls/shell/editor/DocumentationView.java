package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.service.TaskProgressListener;
import org.fxt.freexmltoolkit.service.XsdDocumentationImageService;
import org.fxt.freexmltoolkit.service.XsdDocumentationPdfService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.fxt.freexmltoolkit.service.XsdDocumentationWordService;

/**
 * The XSD documentation generator as a main-area tool tab ("the big editing
 * happens in the editor area"): the full option set of the legacy Documentation
 * tab — source/output, HTML/PDF/Word format, the HTML rendering options
 * (Markdown, type definitions, SVG documentation/overview, metadata, image
 * format), documentation languages (scan + filter + fallback), open-after —
 * plus a live PROGRESS log fed by the pipeline's {@link TaskProgressListener}.
 * Generation runs off the UI thread and can be cancelled.
 */
public class DocumentationView extends BorderPane {

    /** All generation options (captured from the form; also built directly by tests). */
    record DocOptions(File xsd, File output, String format,
                      boolean useMarkdown, boolean includeTypeDefs, boolean showDocInSvg,
                      boolean svgOverview, boolean addMetadata, String imageFormat,
                      Set<String> languages, String fallbackLanguage, boolean openAfter) {
    }

    private final EditorHost editorHost;
    private final Label xsdName = new Label("none");
    private final Label outputName = new Label("none");
    private final ToggleButton html = segment("HTML");
    private final ToggleButton pdf = segment("PDF");
    private final ToggleButton word = segment("Word");
    private final CheckBox useMarkdown = new CheckBox("Use Markdown renderer");
    private final CheckBox includeTypeDefs = new CheckBox("Include type definitions in source code");
    private final CheckBox showDocInSvg = new CheckBox("Show documentation in diagrams");
    private final CheckBox svgOverview = new CheckBox("Generate SVG overview page");
    private final CheckBox addMetadata = new CheckBox("Add metadata in output");
    private final ComboBox<String> imageFormat = new ComboBox<>();
    private final FlowPane languagesPane = new FlowPane(10, 6);
    private final ComboBox<String> fallbackLanguage = new ComboBox<>();
    private final Label languagesStatus = new Label("Not scanned - all languages are included.");
    private final CheckBox openAfter = new CheckBox("Open the generated documentation after creation");
    private final Button generate = new Button("Generate Documentation");
    private final Button cancel = new Button("Cancel");
    private final ObservableList<String> progressItems = FXCollections.observableArrayList();
    private final ListView<String> progressList = new ListView<>(progressItems);
    private final Label status = new Label("Ready.");
    private File xsdFile;
    private File outputTarget;
    private Future<?> running;

    public DocumentationView(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-docgen");

        // --- header ---------------------------------------------------------------
        IconifyIcon headIcon = new IconifyIcon("bi-file-earmark-text");
        headIcon.setIconSize(20);
        StackPane iconTile = new StackPane(headIcon);
        iconTile.getStyleClass().add("fxt-sign-icon-tile");
        Label title = new Label("Generate Documentation");
        title.getStyleClass().add("fxt-favmgr-title");
        Label subtitle = new Label("HTML, PDF, or Word documentation for an XSD schema.");
        subtitle.getStyleClass().add("fxt-favmgr-subtitle");
        HBox header = new HBox(14, iconTile, new VBox(2, title, subtitle));
        header.setAlignment(Pos.CENTER_LEFT);

        // --- SOURCE & OUTPUT ---------------------------------------------------------
        xsdName.setId("docgen-xsd-name");
        xsdName.getStyleClass().addAll("fxt-vp-source-name", "fxt-vp-source-none");
        outputName.setId("docgen-output-name");
        outputName.getStyleClass().addAll("fxt-vp-source-name", "fxt-vp-source-none");
        HBox xsdRow = sourceRow("bi-diagram-3", xsdName, "Browse", this::chooseXsd);
        HBox outputRow = sourceRow("bi-folder2-open", outputName, "Browse", this::chooseOutput);

        // --- FORMAT ---------------------------------------------------------------------
        ToggleGroup formatGroup = new ToggleGroup();
        for (ToggleButton segment : new ToggleButton[]{html, pdf, word}) {
            segment.setToggleGroup(formatGroup);
        }
        html.setSelected(true);
        formatGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                formatGroup.selectToggle(oldV);
            } else {
                outputTarget = null; // folder vs. file depends on the format
                refreshNames();
            }
        });
        HBox formatSeg = new HBox(2, html, pdf, word);
        formatSeg.getStyleClass().add("fxt-seg-group");

        // --- OPTIONS ----------------------------------------------------------------------
        useMarkdown.setSelected(true);
        showDocInSvg.setSelected(true);
        imageFormat.getItems().addAll("SVG", "PNG", "JPG");
        imageFormat.getSelectionModel().selectFirst();
        Label imageLabel = new Label("Diagram image format");
        imageLabel.getStyleClass().add("fxt-sig-field-label");
        VBox optionsBox = new VBox(8, useMarkdown, includeTypeDefs, showDocInSvg, svgOverview,
                addMetadata, imageLabel, imageFormat);

        // --- LANGUAGES -----------------------------------------------------------------------
        Hyperlink scan = new Hyperlink("Scan languages");
        scan.setId("docgen-scan-languages");
        scan.getStyleClass().add("fxt-vp-change");
        scan.setOnAction(e -> scanLanguages());
        languagesStatus.getStyleClass().add("fxt-favmgr-subtitle");
        Label fallbackLabel = new Label("Fallback language");
        fallbackLabel.getStyleClass().add("fxt-sig-field-label");
        fallbackLanguage.setPromptText("(none)");
        VBox languagesBox = new VBox(8, new HBox(10, scan, languagesStatus),
                languagesPane, fallbackLabel, fallbackLanguage);

        // --- generate + progress ------------------------------------------------------------
        openAfter.setSelected(true);
        generate.setId("docgen-generate");
        generate.setGraphic(icon("bi-play-fill", 14));
        generate.getStyleClass().add("fxt-primary-button");
        generate.setMaxWidth(Double.MAX_VALUE);
        generate.setOnAction(e -> generate(currentOptions()));
        cancel.setId("docgen-cancel");
        cancel.getStyleClass().add("fxt-tool-button");
        cancel.setDisable(true);
        cancel.setOnAction(e -> cancelRunning());
        HBox runRow = new HBox(8, generate, cancel);
        HBox.setHgrow(generate, Priority.ALWAYS);

        status.setId("docgen-status");
        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);
        progressList.setId("docgen-progress");
        progressList.getStyleClass().add("fxt-docgen-progress");
        progressList.setPlaceholder(new Label("Progress messages appear here during generation."));
        progressList.setFocusTraversable(false);
        VBox.setVgrow(progressList, Priority.ALWAYS);

        VBox form = new VBox(10,
                sectionLabel("SOURCE & OUTPUT"), xsdRow, outputRow,
                sectionLabel("FORMAT"), formatSeg,
                sectionLabel("OPTIONS"), optionsBox,
                sectionLabel("LANGUAGES"), languagesBox,
                openAfter, runRow, status);
        form.setPrefWidth(430);
        form.setMinWidth(380);
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.getStyleClass().add("edge-to-edge");

        VBox progressBox = new VBox(8, sectionLabel("PROGRESS"), progressList);
        progressBox.setPadding(new Insets(0, 0, 0, 20));

        BorderPane card = new BorderPane(progressBox);
        card.setTop(new VBox(16, header, new Region()));
        card.setLeft(formScroll);
        card.getStyleClass().add("fxt-favmgr-card");

        setCenter(card);
        BorderPane.setMargin(card, new Insets(24));

        prefillFromActiveDocument();
    }

    // ----- option capture --------------------------------------------------------

    /** @return the options currently configured in the form. */
    DocOptions currentOptions() {
        Set<String> languages = new LinkedHashSet<>();
        for (var node : languagesPane.getChildren()) {
            if (node instanceof CheckBox box && box.isSelected()) {
                languages.add(box.getText());
            }
        }
        return new DocOptions(xsdFile, outputTarget, selectedFormat(),
                useMarkdown.isSelected(), includeTypeDefs.isSelected(), showDocInSvg.isSelected(),
                svgOverview.isSelected(), addMetadata.isSelected(), imageFormat.getValue(),
                languages, fallbackLanguage.getValue(), openAfter.isSelected());
    }

    String selectedFormat() {
        if (pdf.isSelected()) {
            return "PDF";
        }
        if (word.isSelected()) {
            return "Word";
        }
        return "HTML";
    }

    /** Sets source + output programmatically (also used by tests). */
    void setFiles(File xsd, File output) {
        this.xsdFile = xsd;
        this.outputTarget = output;
        refreshNames();
    }

    /** @return the PROGRESS log lines (for tests/observers). */
    java.util.List<String> progressMessages() {
        return java.util.List.copyOf(progressItems);
    }

    /** @return the status line (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    // ----- generation ---------------------------------------------------------------

    /** Runs the generation with {@code options} off the UI thread (cancellable). */
    void generate(DocOptions options) {
        if (options.xsd() == null || !options.xsd().isFile()) {
            status.setText("Select an XSD schema first.");
            return;
        }
        if (options.output() == null) {
            status.setText("Choose the output " + ("HTML".equals(options.format()) ? "folder" : "file") + " first.");
            return;
        }
        progressItems.clear();
        status.setText("Generating " + options.format() + "…");
        generate.setDisable(true);
        cancel.setDisable(false);
        long start = System.currentTimeMillis();
        running = FxtGui.executorService.submit(() -> {
            String result;
            try {
                runGeneration(options);
                result = "Generated in " + (System.currentTimeMillis() - start) + " ms: "
                        + options.output().getAbsolutePath();
            } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                result = "Cancelled.";
            } catch (Throwable t) {
                result = "ERROR: " + (t.getMessage() != null ? t.getMessage() : t.toString());
            }
            String finalResult = result;
            Platform.runLater(() -> {
                status.setText(finalResult);
                generate.setDisable(false);
                cancel.setDisable(true);
                running = null;
                if (finalResult.startsWith("Generated") && options.openAfter()) {
                    File target = options.output().isDirectory()
                            ? new File(options.output(), "index.html") : options.output();
                    openInDesktop(target);
                }
            });
        });
    }

    /** The pipeline (mirrors the legacy Documentation tab's generation task). */
    private void runGeneration(DocOptions options) throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(options.xsd().getAbsolutePath());
        service.setUseMarkdownRenderer(options.useMarkdown());
        service.setIncludeTypeDefinitionsInSourceCode(options.includeTypeDefs());
        service.setShowDocumentationInSvg(options.showDocInSvg());
        service.setGenerateSvgOverviewPage(options.svgOverview());
        service.setAddMetadataInOutput(options.addMetadata());
        if (options.languages() != null && !options.languages().isEmpty()) {
            service.setIncludedLanguages(options.languages());
        }
        if (options.fallbackLanguage() != null && !options.fallbackLanguage().isBlank()) {
            service.setFallbackLanguage(options.fallbackLanguage());
        }
        service.setMethod(switch (options.imageFormat() != null ? options.imageFormat() : "SVG") {
            case "PNG" -> XsdDocumentationService.ImageOutputMethod.PNG;
            case "JPG" -> XsdDocumentationService.ImageOutputMethod.JPG;
            default -> XsdDocumentationService.ImageOutputMethod.SVG;
        });
        TaskProgressListener listener = update -> Platform.runLater(() -> {
            String message = "[" + update.status() + "] " + update.taskName();
            if (update.status() == TaskProgressListener.ProgressUpdate.Status.FINISHED) {
                message += " (" + update.durationMillis() + " ms)";
            }
            progressItems.add(message);
            progressList.scrollTo(progressItems.size() - 1);
        });
        service.setProgressListener(listener);

        switch (options.format()) {
            case "PDF" -> {
                service.processXsd(options.useMarkdown());
                XsdDocumentationPdfService pdfService = new XsdDocumentationPdfService();
                pdfService.setProgressListener(listener);
                if (options.languages() != null && !options.languages().isEmpty()) {
                    pdfService.setIncludedLanguages(options.languages());
                }
                pdfService.setConfig(new PdfDocumentationConfig());
                XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                        service.xsdDocumentationData.getExtendedXsdElementMap());
                imageService.setShowDocumentation(options.showDocInSvg());
                pdfService.setImageService(imageService);
                pdfService.generatePdfDocumentation(options.output(), service.xsdDocumentationData);
            }
            case "Word" -> {
                service.processXsd(options.useMarkdown());
                XsdDocumentationWordService wordService = new XsdDocumentationWordService();
                wordService.setProgressListener(listener);
                if (options.languages() != null && !options.languages().isEmpty()) {
                    wordService.setIncludedLanguages(options.languages());
                }
                wordService.setConfig(new WordDocumentationConfig());
                XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                        service.xsdDocumentationData.getExtendedXsdElementMap());
                imageService.setShowDocumentation(options.showDocInSvg());
                wordService.setImageService(imageService);
                wordService.generateWordDocumentation(options.output(), service.xsdDocumentationData);
            }
            default -> service.generateXsdDocumentation(options.output());
        }
    }

    private void cancelRunning() {
        Future<?> current = running;
        if (current != null) {
            current.cancel(true);
            status.setText("Cancelling…");
        }
    }

    // ----- languages ------------------------------------------------------------------

    /** Scans the XSD's documentation languages off the UI thread and shows them as checkboxes. */
    void scanLanguages() {
        if (xsdFile == null || !xsdFile.isFile()) {
            status.setText("Select an XSD schema first.");
            return;
        }
        languagesStatus.setText("Scanning…");
        File xsd = xsdFile;
        FxtGui.executorService.submit(() -> {
            Set<String> found;
            try {
                XsdDocumentationService scanService = new XsdDocumentationService();
                scanService.setXsdFilePath(xsd.getAbsolutePath());
                scanService.processXsd(false);
                found = new LinkedHashSet<>(scanService.getDiscoveredLanguages());
            } catch (Exception e) {
                found = Set.of();
            }
            Set<String> languages = found;
            Platform.runLater(() -> {
                languagesPane.getChildren().clear();
                fallbackLanguage.getItems().clear();
                if (languages.isEmpty()) {
                    languagesStatus.setText("No languages found - all documentation is included.");
                    return;
                }
                languagesStatus.setText(languages.size() + " language(s) found:");
                for (String language : languages) {
                    CheckBox box = new CheckBox(language);
                    box.setSelected(true);
                    languagesPane.getChildren().add(box);
                }
                fallbackLanguage.getItems().addAll(languages);
                fallbackLanguage.getSelectionModel().selectFirst();
            });
        });
    }

    // ----- helpers --------------------------------------------------------------------

    private void prefillFromActiveDocument() {
        editorHost.getActiveDocument()
                .filter(doc -> doc.getFileType() == EditorFileType.XSD && doc.getPath() != null)
                .ifPresent(doc -> {
                    xsdFile = doc.getPath().toFile();
                    refreshNames();
                });
    }

    private void chooseXsd() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSD schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD", "*.xsd"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            xsdFile = file;
            refreshNames();
        }
    }

    private void chooseOutput() {
        var window = getScene() != null ? getScene().getWindow() : null;
        String baseName = xsdFile != null ? xsdFile.getName().replaceFirst("\\.[^.]+$", "") : "documentation";
        switch (selectedFormat()) {
            case "PDF" -> outputTarget = chooseFile(window, baseName + ".pdf", "PDF", "*.pdf");
            case "Word" -> outputTarget = chooseFile(window, baseName + ".docx", "Word", "*.docx");
            default -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Choose output directory for HTML documentation");
                File dir = chooser.showDialog(window);
                if (dir != null) {
                    outputTarget = dir;
                }
            }
        }
        refreshNames();
    }

    private File chooseFile(javafx.stage.Window window, String initialName, String label, String glob) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save documentation as " + label);
        chooser.setInitialFileName(initialName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, glob));
        return chooser.showSaveDialog(window);
    }

    private void refreshNames() {
        setSourceName(xsdName, xsdFile != null ? xsdFile.getName() : null);
        setSourceName(outputName, outputTarget != null ? outputTarget.getName() : null);
    }

    private static void openInDesktop(File file) {
        try {
            if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
            }
        } catch (Throwable ignored) {
            // best-effort preview; never fail the action because the OS can't open it
        }
    }

    private HBox sourceRow(String iconLiteral, Label nameLabel, String linkText, Runnable action) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Hyperlink link = new Hyperlink(linkText);
        link.getStyleClass().add("fxt-vp-change");
        link.setOnAction(e -> action.run());
        HBox row = new HBox(8, icon(iconLiteral, 15), nameLabel, spacer, link);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "none");
        label.getStyleClass().remove("fxt-vp-source-none");
        if (name == null) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-sign-section-label");
        return label;
    }

    private static ToggleButton segment(String text) {
        ToggleButton toggle = new ToggleButton(text);
        toggle.getStyleClass().add("fxt-seg");
        toggle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(toggle, Priority.ALWAYS);
        return toggle;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
