package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
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
                      XsdDocumentationService.MarkdownMode markdown,
                      boolean includeTypeDefs, boolean showDocInSvg,
                      boolean svgOverview, boolean addMetadata, boolean deduplicateDataDictionaryByType,
                      String imageFormat,
                      Set<String> languages, String fallbackLanguage, boolean openAfter,
                      File favicon, FormatOptions formatOptions) {
    }

    /**
     * The PDF/Word detail options (display names; the {@code colorScheme}/
     * {@code watermark}/{@code pageNumbers}/{@code bookmarks} parts apply to PDF only).
     */
    record FormatOptions(String pageSize, boolean landscape, boolean coverPage, boolean toc,
                         boolean dataDictionary, boolean schemaDiagram, boolean elementDiagrams,
                         String colorScheme, String watermark, boolean pageNumbers, boolean bookmarks) {

        static FormatOptions defaults() {
            return new FormatOptions("A4", false, false, true, true, true, false,
                    "Blue", "None", true, true);
        }
    }

    private final EditorHost editorHost;
    @FXML private Label xsdName;
    @FXML private Label outputName;
    @FXML private ToggleButton html;
    @FXML private ToggleButton pdf;
    @FXML private ToggleButton word;
    /** The three labels of the Markdown rendering choice. */
    static final String MARKDOWN_ALL = "All documentation";
    static final String MARKDOWN_PER_NODE = "Per node (@markdown)";
    static final String MARKDOWN_OFF = "Off";

    @FXML private ComboBox<String> markdownMode;
    @FXML private CheckBox includeTypeDefs;
    @FXML private CheckBox showDocInSvg;
    @FXML private CheckBox svgOverview;
    @FXML private CheckBox addMetadata;
    @FXML private CheckBox dedupDataDictionary;
    @FXML private ComboBox<String> imageFormat;
    @FXML private FlowPane languagesPane;
    @FXML private ComboBox<String> fallbackLanguage;
    @FXML private Label languagesStatus;
    @FXML private Label faviconName;
    // PDF/Word detail options (the section shows only for those formats).
    @FXML private Label formatOptionsLabel;
    @FXML private VBox formatOptionsBox;
    @FXML private VBox pdfOnlyBox;
    @FXML private ComboBox<String> docPageSize;
    @FXML private ToggleButton portrait;
    @FXML private ToggleButton landscape;
    @FXML private CheckBox coverPage;
    @FXML private CheckBox tableOfContents;
    @FXML private CheckBox dataDictionary;
    @FXML private CheckBox schemaDiagram;
    @FXML private CheckBox elementDiagrams;
    @FXML private ComboBox<String> pdfColorScheme;
    @FXML private ComboBox<String> pdfWatermark;
    @FXML private CheckBox pageNumbers;
    @FXML private CheckBox pdfBookmarks;
    @FXML private CheckBox openAfter;
    @FXML private Button generate;
    @FXML private Button cancel;
    @FXML private ToggleGroup formatGroup;
    @FXML private ToggleGroup orientationGroup;
    private final ObservableList<StepRow> progressItems = FXCollections.observableArrayList();
    @FXML private ListView<StepRow> progressList;
    /** Maps a task name to its row index in {@link #progressItems} so a step's row is updated in place. */
    private final java.util.Map<String, Integer> stepIndex = new java.util.HashMap<>();
    @FXML private Label status;
    /** Spinner shown in the PROGRESS header while a generation is running. */
    @FXML private ProgressIndicator spinner;
    /** Live elapsed-time label shown next to the spinner while running. */
    @FXML private Label elapsedLabel;
    /** Drives {@link #elapsedLabel} (~5 Hz) while a generation is running. */
    private Timeline elapsedTimer;
    private File xsdFile;
    private File outputTarget;
    private File faviconFile;
    private Future<?> running;

    /** One row in the PROGRESS list: a pipeline step with its current status and (when done) its duration. */
    private record StepRow(String name, TaskProgressListener.ProgressUpdate.Status status, long durationMillis) {
    }

    /** Loads the layout from {@code /pages/views/DocumentationView.fxml} and fills the choices. */
    public DocumentationView(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/views/DocumentationView.fxml");

        formatGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                formatGroup.selectToggle(oldV);
            } else {
                outputTarget = null; // folder vs. file depends on the format
                refreshNames();
                refreshFormatOptions();
            }
        });
        orientationGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                orientationGroup.selectToggle(oldV);
            }
        });

        for (PdfDocumentationConfig.ColorScheme scheme : PdfDocumentationConfig.ColorScheme.values()) {
            pdfColorScheme.getItems().add(scheme.getDisplayName());
        }
        pdfColorScheme.getSelectionModel().selectFirst();
        for (PdfDocumentationConfig.Watermark mark : PdfDocumentationConfig.Watermark.values()) {
            pdfWatermark.getItems().add(mark.getDisplayName());
        }
        pdfWatermark.getSelectionModel().selectFirst();
        refreshFormatOptions();

        markdownMode.getItems().addAll(MARKDOWN_ALL, MARKDOWN_PER_NODE, MARKDOWN_OFF);
        markdownMode.getSelectionModel().selectFirst();
        imageFormat.getSelectionModel().selectFirst();

        progressList.setItems(progressItems);
        progressList.setCellFactory(lv -> new StepCell());

        prefillFromActiveDocument();
    }

    @FXML
    public void onClearFavicon() {
        setFavicon(null);
    }

    @FXML
    public void onScanLanguages() {
        scanLanguages();
    }

    @FXML
    public void onGenerate() {
        // The generator reads the XSD from disk: offer to save editor changes first.
        if (xsdFile != null && !editorHost.ensureSavedBeforeTool(xsdFile.toPath(), "Generate Documentation")) {
            return;
        }
        generate(currentOptions());
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
        FormatOptions format = new FormatOptions(
                docPageSize.getValue() != null ? docPageSize.getValue() : "A4",
                landscape.isSelected(), coverPage.isSelected(), tableOfContents.isSelected(),
                dataDictionary.isSelected(), schemaDiagram.isSelected(), elementDiagrams.isSelected(),
                pdfColorScheme.getValue(), pdfWatermark.getValue(),
                pageNumbers.isSelected(), pdfBookmarks.isSelected());
        return new DocOptions(xsdFile, outputTarget, selectedFormat(),
                selectedMarkdownMode(), includeTypeDefs.isSelected(), showDocInSvg.isSelected(),
                svgOverview.isSelected(), addMetadata.isSelected(), dedupDataDictionary.isSelected(),
                imageFormat.getValue(),
                languages, fallbackLanguage.getValue(), openAfter.isSelected(), faviconFile, format);
    }

    /** @return the Markdown mode currently selected in the form. */
    XsdDocumentationService.MarkdownMode selectedMarkdownMode() {
        return switch (markdownMode.getValue() == null ? MARKDOWN_ALL : markdownMode.getValue()) {
            case MARKDOWN_PER_NODE -> XsdDocumentationService.MarkdownMode.PER_NODE;
            case MARKDOWN_OFF -> XsdDocumentationService.MarkdownMode.OFF;
            default -> XsdDocumentationService.MarkdownMode.ALL;
        };
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
        return progressItems.stream().map(r -> {
            String line = "[" + r.status() + "] " + r.name();
            if (r.status() == TaskProgressListener.ProgressUpdate.Status.FINISHED
                    || r.status() == TaskProgressListener.ProgressUpdate.Status.FAILED) {
                line += " (" + r.durationMillis() + " ms)";
            }
            return line;
        }).toList();
    }

    // ----- progress rendering ---------------------------------------------------------

    /**
     * Records or updates one pipeline step. A step's {@code RUNNING}/{@code STARTED} update creates
     * its row; the matching {@code FINISHED}/{@code FAILED} update replaces that same row in place so
     * each step appears exactly once, transitioning from "running" to "done" with its duration.
     */
    private void recordProgress(TaskProgressListener.ProgressUpdate update) {
        StepRow row = new StepRow(update.taskName(), update.status(), update.durationMillis());
        Integer idx = stepIndex.get(update.taskName());
        if (idx == null) {
            stepIndex.put(update.taskName(), progressItems.size());
            progressItems.add(row);
        } else {
            progressItems.set(idx, row);
        }
        progressList.scrollTo(progressItems.size() - 1);
    }

    /** Shows the spinner and starts a ~5 Hz timer updating the elapsed-time label. */
    private void startElapsedTimer(long startMillis) {
        spinner.setVisible(true);
        spinner.setManaged(true);
        elapsedLabel.setVisible(true);
        elapsedLabel.setManaged(true);
        elapsedLabel.setText("0.0 s");
        if (elapsedTimer != null) {
            elapsedTimer.stop();
        }
        elapsedTimer = new Timeline(new KeyFrame(Duration.millis(200),
                e -> elapsedLabel.setText(humanDuration(System.currentTimeMillis() - startMillis))));
        elapsedTimer.setCycleCount(Animation.INDEFINITE);
        elapsedTimer.play();
    }

    /** Stops the elapsed timer and hides the spinner. */
    private void stopElapsedTimer() {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
        spinner.setVisible(false);
        spinner.setManaged(false);
        elapsedLabel.setVisible(false);
        elapsedLabel.setManaged(false);
    }

    /** Formats a millisecond duration in a compact, human-readable form (e.g. {@code "2 min 14 s"}). */
    private static String humanDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        long totalSeconds = ms / 1000;
        if (totalSeconds < 60) {
            return String.format(java.util.Locale.ROOT, "%.1f s", ms / 1000.0);
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + " min " + seconds + " s";
    }

    /** A PROGRESS list cell: status icon + step name on the left, duration right-aligned. */
    private static final class StepCell extends ListCell<StepRow> {
        @Override
        protected void updateItem(StepRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            IconifyIcon glyph;
            String time;
            switch (item.status()) {
                case FINISHED -> {
                    glyph = org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                            icon("bi-check-circle-fill", 13), DesignTokens.ColorToken.SUCCESS);
                    time = String.format(java.util.Locale.ROOT, "%,d ms", item.durationMillis());
                }
                case FAILED -> {
                    glyph = org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                            icon("bi-x-circle-fill", 13), DesignTokens.ColorToken.DANGER);
                    time = "failed";
                }
                default -> {
                    glyph = org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                            icon("bi-hourglass-split", 13), DesignTokens.ColorToken.INFO);
                    time = "…";
                }
            }
            Label name = new Label(item.name());
            name.getStyleClass().add("fxt-docgen-step-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label duration = new Label(time);
            duration.getStyleClass().add("fxt-docgen-step-time");
            HBox box = new HBox(8, glyph, name, spacer, duration);
            box.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(box);
        }
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
        stepIndex.clear();
        status.setText("Generating " + options.format() + "…");
        generate.setDisable(true);
        cancel.setDisable(false);
        long start = System.currentTimeMillis();
        startElapsedTimer(start);
        running = FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            String result;
            var usageStatus = org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.OK;
            try {
                runGeneration(options);
                result = "Generated " + options.format() + " in " + humanDuration(System.currentTimeMillis() - start)
                        + " — " + options.output().getAbsolutePath();
            } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                result = "Cancelled.";
                usageStatus = org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.CANCELLED;
            } catch (Throwable t) {
                result = "ERROR: " + (t.getMessage() != null ? t.getMessage() : t.toString());
                usageStatus = org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.ERROR;
            }
            org.fxt.freexmltoolkit.service.telemetry.UsageEvents.schemaDocGenerated(
                    options.format(), t0, usageStatus);
            String finalResult = result;
            Platform.runLater(() -> {
                stopElapsedTimer();
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
        service.setMarkdownMode(options.markdown());
        service.setIncludeTypeDefinitionsInSourceCode(options.includeTypeDefs());
        service.setShowDocumentationInSvg(options.showDocInSvg());
        service.setGenerateSvgOverviewPage(options.svgOverview());
        service.setAddMetadataInOutput(options.addMetadata());
        service.setDeduplicateDataDictionaryByType(options.deduplicateDataDictionaryByType());
        if (options.languages() != null && !options.languages().isEmpty()) {
            service.setIncludedLanguages(options.languages());
        }
        if (options.fallbackLanguage() != null && !options.fallbackLanguage().isBlank()) {
            service.setFallbackLanguage(options.fallbackLanguage());
        }
        if (options.favicon() != null && options.favicon().isFile()) {
            service.setFaviconPath(options.favicon().getAbsolutePath());
        }
        service.setMethod(switch (options.imageFormat() != null ? options.imageFormat() : "SVG") {
            case "PNG" -> XsdDocumentationService.ImageOutputMethod.PNG;
            case "JPG" -> XsdDocumentationService.ImageOutputMethod.JPG;
            default -> XsdDocumentationService.ImageOutputMethod.SVG;
        });
        TaskProgressListener listener = update -> Platform.runLater(() -> recordProgress(update));
        service.setProgressListener(listener);

        switch (options.format()) {
            case "PDF" -> {
                service.processXsd(options.markdown());
                XsdDocumentationPdfService pdfService = new XsdDocumentationPdfService();
                pdfService.setProgressListener(listener);
                if (options.languages() != null && !options.languages().isEmpty()) {
                    pdfService.setIncludedLanguages(options.languages());
                }
                pdfService.setConfig(pdfConfig(options.formatOptions()));
                XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                        service.xsdDocumentationData.getExtendedXsdElementMap());
                imageService.setShowDocumentation(options.showDocInSvg());
                pdfService.setImageService(imageService);
                pdfService.generatePdfDocumentation(options.output(), service.xsdDocumentationData);
            }
            case "Word" -> {
                service.processXsd(options.markdown());
                XsdDocumentationWordService wordService = new XsdDocumentationWordService();
                wordService.setProgressListener(listener);
                if (options.languages() != null && !options.languages().isEmpty()) {
                    wordService.setIncludedLanguages(options.languages());
                }
                wordService.setConfig(wordConfig(options.formatOptions()));
                XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                        service.xsdDocumentationData.getExtendedXsdElementMap());
                imageService.setShowDocumentation(options.showDocInSvg());
                wordService.setImageService(imageService);
                wordService.generateWordDocumentation(options.output(), service.xsdDocumentationData);
            }
            default -> service.generateXsdDocumentation(options.output());
        }
    }

    /** Builds the PDF config from the captured options ({@code null} = defaults). */
    private static PdfDocumentationConfig pdfConfig(FormatOptions options) {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        if (options == null) {
            return config;
        }
        config.setPageSize(switch (options.pageSize() != null ? options.pageSize() : "A4") {
            case "Letter" -> PdfDocumentationConfig.PageSize.LETTER;
            case "Legal" -> PdfDocumentationConfig.PageSize.LEGAL;
            case "A3" -> PdfDocumentationConfig.PageSize.A3;
            default -> PdfDocumentationConfig.PageSize.A4;
        });
        config.setOrientation(options.landscape()
                ? PdfDocumentationConfig.Orientation.LANDSCAPE
                : PdfDocumentationConfig.Orientation.PORTRAIT);
        config.setIncludeCoverPage(options.coverPage());
        config.setIncludeToc(options.toc());
        config.setIncludeDataDictionary(options.dataDictionary());
        config.setIncludeSchemaDiagram(options.schemaDiagram());
        config.setIncludeElementDiagrams(options.elementDiagrams());
        for (PdfDocumentationConfig.ColorScheme scheme : PdfDocumentationConfig.ColorScheme.values()) {
            if (scheme.getDisplayName().equals(options.colorScheme())) {
                config.setColorScheme(scheme);
            }
        }
        for (PdfDocumentationConfig.Watermark mark : PdfDocumentationConfig.Watermark.values()) {
            if (mark.getDisplayName().equals(options.watermark())) {
                config.setWatermark(mark);
            }
        }
        config.setIncludePageNumbers(options.pageNumbers());
        config.setGenerateBookmarks(options.bookmarks());
        return config;
    }

    /** Builds the Word config from the captured options ({@code null} = defaults). */
    private static WordDocumentationConfig wordConfig(FormatOptions options) {
        WordDocumentationConfig config = new WordDocumentationConfig();
        if (options == null) {
            return config;
        }
        config.setPageSize(switch (options.pageSize() != null ? options.pageSize() : "A4") {
            case "Letter" -> WordDocumentationConfig.PageSize.LETTER;
            case "Legal" -> WordDocumentationConfig.PageSize.LEGAL;
            default -> WordDocumentationConfig.PageSize.A4;
        });
        config.setOrientation(options.landscape()
                ? WordDocumentationConfig.Orientation.LANDSCAPE
                : WordDocumentationConfig.Orientation.PORTRAIT);
        config.setIncludeCoverPage(options.coverPage());
        config.setIncludeToc(options.toc());
        config.setIncludeDataDictionary(options.dataDictionary());
        config.setIncludeSchemaDiagram(options.schemaDiagram());
        config.setIncludeElementDiagrams(options.elementDiagrams());
        return config;
    }

    /** Selects a format programmatically (also used by tests). */
    void selectFormat(String format) {
        switch (format) {
            case "PDF" -> pdf.setSelected(true);
            case "Word" -> word.setSelected(true);
            default -> html.setSelected(true);
        }
    }

    /** @return the PDF/Word options section (visibility checks in tests). */
    VBox formatOptionsBoxForTests() {
        return formatOptionsBox;
    }

    /** @return the page-size choices currently offered (for tests). */
    java.util.List<String> pageSizeChoices() {
        return java.util.List.copyOf(docPageSize.getItems());
    }

    @FXML
    public void onCancel() {
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

    /** Sets the HTML favicon file (or {@code null} to clear; also used by tests). */
    void setFavicon(File file) {
        this.faviconFile = file;
        setSourceName(faviconName, file != null ? file.getName() : null);
    }

    @FXML
    public void onChooseFavicon() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select favicon");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Favicon", "*.ico", "*.png", "*.svg", "*.gif"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setFavicon(file);
        }
    }

    @FXML
    public void onChooseXsd() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSD schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD", "*.xsd"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            xsdFile = file;
            refreshNames();
        }
    }

    @FXML
    public void onChooseOutput() {
        var window = getScene() != null ? getScene().getWindow() : null;
        String baseName = xsdFile != null ? xsdFile.getName().replaceFirst("\\.[^.]+$", "") : "documentation";
        switch (selectedFormat()) {
            case "PDF" -> outputTarget = chooseFile(window, baseName + ".pdf", "PDF", "*.pdf");
            case "Word" -> outputTarget = chooseFile(window, baseName + ".docx", "Word", "*.docx");
            default -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Choose output directory for HTML documentation");
                File dir = org.fxt.freexmltoolkit.util.FileChooserHelper.showDialog(chooser, window);
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
        return org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, window);
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

    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "Not selected");
        label.getStyleClass().remove("fxt-vp-source-none");
        if (name == null) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }

    /**
     * Shows/hides the PDF/WORD detail-options section for the selected format and
     * swaps the page-size choices (Word has no A3). The selection survives the
     * swap when the size exists in both sets.
     */
    private void refreshFormatOptions() {
        String format = selectedFormat();
        boolean show = !"HTML".equals(format);
        formatOptionsBox.setVisible(show);
        formatOptionsBox.setManaged(show);
        if (!show) {
            return;
        }
        formatOptionsLabel.setText("PDF".equals(format) ? "PDF OPTIONS" : "WORD OPTIONS");
        pdfOnlyBox.setVisible("PDF".equals(format));
        pdfOnlyBox.setManaged("PDF".equals(format));
        String keep = docPageSize.getValue();
        docPageSize.getItems().clear();
        if ("PDF".equals(format)) {
            for (PdfDocumentationConfig.PageSize size : PdfDocumentationConfig.PageSize.values()) {
                docPageSize.getItems().add(size.getDisplayName());
            }
        } else {
            for (WordDocumentationConfig.PageSize size : WordDocumentationConfig.PageSize.values()) {
                docPageSize.getItems().add(size.getDisplayName());
            }
        }
        docPageSize.getSelectionModel().select(
                keep != null && docPageSize.getItems().contains(keep) ? keep : "A4");
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
