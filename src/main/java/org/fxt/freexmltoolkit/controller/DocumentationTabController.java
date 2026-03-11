package org.fxt.freexmltoolkit.controller;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.CheckComboBox;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.DocumentationOutputFormat;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.TaskProgressListener;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XsdDocumentationImageService;
import org.fxt.freexmltoolkit.service.XsdDocumentationPdfService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.fxt.freexmltoolkit.service.XsdDocumentationWordService;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FormattingUtils;
import org.jetbrains.annotations.NotNull;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

/**
 * Controller for the Documentation Tab.
 * Handles the generation of XSD documentation in various formats.
 */
public class DocumentationTabController {

    private static final Logger logger = LogManager.getLogger(DocumentationTabController.class);

    @FXML
    private Tab documentation;
    @FXML
    private TextField xsdFilePath;
    @FXML
    private TextField documentationOutputDirPath;
    @FXML
    private CheckBox useMarkdownRenderer;
    @FXML
    private CheckBox openFileAfterCreation;
    @FXML
    private CheckBox includeTypeDefinitionsInSourceCode;
    @FXML
    private CheckBox generateSvgOverviewPage;
    @FXML
    private CheckBox addMetadataInOutput;
    @FXML
    private ChoiceBox<String> grafikFormat;
    @FXML
    private TextField faviconPath;
    @FXML
    private Button browseFaviconButton;
    @FXML
    private CheckBox showDocumentationInSvg;
    @FXML
    private RadioButton outputFormatHtml;
    @FXML
    private RadioButton outputFormatWord;
    @FXML
    private RadioButton outputFormatPdf;
    @FXML
    private ToggleGroup outputFormatGroup;

    @FXML
    private VBox htmlSettingsContainer;
    @FXML
    private VBox wordSettingsContainer;
    @FXML
    private VBox pdfSettingsContainer;

    @FXML
    private ChoiceBox<String> wordPageSize;
    @FXML
    private ChoiceBox<String> pdfPageSize;

    @FXML
    private ScrollPane progressScrollPane;
    @FXML
    private VBox progressContainer;
    @FXML
    private Button openDocFolder;
    @FXML
    private HBox statusMessageContainer;
    @FXML
    private Label statusText;
    @FXML
    private Button cancelDocumentationButton;
    @FXML
    private Button generateDocumentationButton;

    @FXML
    public Button scanLanguagesButton;
    @FXML
    public Label languageScanStatus;
    @FXML
    public HBox languageSelectionContainer;
    @FXML
    public ComboBox<String> fallbackLanguageComboBox;

    @FXML
    private WebView docWebView;
    @FXML
    private Tab docPreviewTab;

    private CheckComboBox<String> languageCheckComboBox;
    private Set<String> discoveredLanguages = new LinkedHashSet<>();

    private XsdController parentController;
    private File lastGeneratedDocFolder;
    private Task<Void> currentDocumentationTask;
    private Timeline documentationTimer;
    private long documentationStartTime;
    private HttpServer docServer;

    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
    private final XmlService xmlService = ServiceRegistry.get(XmlService.class);

    private static final int DOC_SERVER_PORT = 8080;

    @FXML
    public void initialize() {
        if (grafikFormat != null) {
            grafikFormat.getItems().addAll("SVG", "PNG", "JPG");
            grafikFormat.setValue("SVG");
        }

        if (wordPageSize != null) {
            wordPageSize.getItems().addAll("A4", "Letter", "Legal");
            wordPageSize.setValue("A4");
        }

        if (pdfPageSize != null) {
            pdfPageSize.getItems().addAll("A4", "Letter", "Legal", "A3");
            pdfPageSize.setValue("A4");
        }

        initializeDocumentationFormatSettings();
    }

    private void initializeDocumentationFormatSettings() {
        if (outputFormatGroup != null) {
            outputFormatGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
                if (newToggle == outputFormatHtml) {
                    showSettings(true, false, false);
                } else if (newToggle == outputFormatWord) {
                    showSettings(false, true, false);
                } else if (newToggle == outputFormatPdf) {
                    showSettings(false, false, true);
                }
            });
        }
    }

    private void showSettings(boolean html, boolean word, boolean pdf) {
        if (htmlSettingsContainer != null) {
            htmlSettingsContainer.setVisible(html);
            htmlSettingsContainer.setManaged(html);
        }
        if (wordSettingsContainer != null) {
            wordSettingsContainer.setVisible(word);
            wordSettingsContainer.setManaged(word);
        }
        if (pdfSettingsContainer != null) {
            pdfSettingsContainer.setVisible(pdf);
            pdfSettingsContainer.setManaged(pdf);
        }
    }

    @FXML
    public void generateDocumentation() {
        // 1. Validate XSD input
        String xsdPath = xsdFilePath.getText();
        if (xsdPath == null || xsdPath.isBlank()) {
            DialogHelper.showError("Generate Documentation", "Missing XSD File",
                    "Please provide a source XSD file.");
            return;
        }

        File xsdFile = new File(xsdPath);
        if (!xsdFile.exists()) {
            DialogHelper.showError("Generate Documentation", "XSD File Not Found",
                    "The specified XSD file does not exist: " + xsdPath);
            return;
        }

        // 2. Determine output format
        DocumentationOutputFormat outputFormat = getSelectedOutputFormat();

        // 3. Validate output path based on format
        String outputPath = documentationOutputDirPath.getText();
        File outputTarget;

        if (outputFormat == DocumentationOutputFormat.HTML) {
            if (outputPath == null || outputPath.isBlank()) {
                DialogHelper.showError("Generate Documentation", "Missing Output Directory",
                        "Please select an output directory.");
                return;
            }
            outputTarget = new File(outputPath);
            if (!outputTarget.exists() && !outputTarget.mkdirs()) {
                DialogHelper.showError("Generate Documentation", "Cannot Create Directory",
                        "Could not create the output directory: " + outputPath);
                return;
            }
            if (!outputTarget.isDirectory()) {
                DialogHelper.showError("Generate Documentation", "Invalid Output Path",
                        "The specified output path is not a directory: " + outputPath);
                return;
            }
        } else {
            // Word/PDF need a file path
            String schemaName = xsdFile.getName().replace(".xsd", "");
            String expectedExtension = "." + outputFormat.getFileExtension();

            if (outputPath == null || outputPath.isBlank()) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save " + outputFormat.getDisplayName());
                fileChooser.setInitialFileName(schemaName + expectedExtension);
                addFormatExtensionFilter(fileChooser, outputFormat);

                String lastDirString = propertiesService.getLastOpenDirectory();
                if (lastDirString != null) {
                    File lastDir = new File(lastDirString);
                    if (lastDir.exists() && lastDir.isDirectory()) {
                        fileChooser.setInitialDirectory(lastDir);
                    }
                }

                File selectedFile = fileChooser.showSaveDialog(
                        generateDocumentationButton.getScene().getWindow());
                if (selectedFile == null) {
                    return;
                }
                outputTarget = selectedFile;
                documentationOutputDirPath.setText(selectedFile.getAbsolutePath());
            } else {
                File outputFile = new File(outputPath);
                if (outputFile.isDirectory()) {
                    outputTarget = new File(outputFile, schemaName + expectedExtension);
                    documentationOutputDirPath.setText(outputTarget.getAbsolutePath());
                } else if (outputPath.endsWith(expectedExtension)) {
                    outputTarget = outputFile;
                } else {
                    outputTarget = new File(outputPath + expectedExtension);
                    documentationOutputDirPath.setText(outputTarget.getAbsolutePath());
                }
            }

            // Ensure parent directory exists
            File parentDir = outputTarget.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                DialogHelper.showError("Generate Documentation", "Cannot Create Directory",
                        "Could not create the output directory: " + parentDir.getAbsolutePath());
                return;
            }
        }

        // 4. Prepare UI for background task
        progressScrollPane.setVisible(true);
        progressScrollPane.setManaged(true);
        progressContainer.getChildren().clear();

        if (statusMessageContainer != null) {
            statusMessageContainer.setVisible(false);
            statusMessageContainer.setManaged(false);
        }
        if (openDocFolder != null) {
            openDocFolder.setDisable(true);
        }
        statusText.setText("Generating " + outputFormat.getDisplayName() + "...");

        if (cancelDocumentationButton != null) {
            cancelDocumentationButton.setVisible(true);
            cancelDocumentationButton.setManaged(true);
        }
        if (generateDocumentationButton != null) {
            generateDocumentationButton.setDisable(true);
        }

        // 5. Start timer and execute task
        startDocumentationTimer();
        Task<Void> generationTask = getGenerationTask(xsdFile, outputTarget, outputFormat);
        currentDocumentationTask = generationTask;
        parentController.executeBackgroundTask(generationTask);
    }

    private void addFormatExtensionFilter(FileChooser fileChooser, DocumentationOutputFormat format) {
        if (format == DocumentationOutputFormat.WORD) {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Word Documents", "*.docx"));
        } else if (format == DocumentationOutputFormat.PDF) {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Documents", "*.pdf"));
        }
    }

    private DocumentationOutputFormat getSelectedOutputFormat() {
        if (outputFormatWord != null && outputFormatWord.isSelected()) {
            return DocumentationOutputFormat.WORD;
        }
        if (outputFormatPdf != null && outputFormatPdf.isSelected()) {
            return DocumentationOutputFormat.PDF;
        }
        return DocumentationOutputFormat.HTML;
    }

    private @NotNull Task<Void> getGenerationTask(File xsdFile, File outputTarget, DocumentationOutputFormat outputFormat) {
        // Capture UI values before entering background thread
        final Set<String> selectedLanguages = getSelectedLanguages();
        final String fallbackLanguage = getSelectedFallbackLanguage();
        final boolean useMarkdown = useMarkdownRenderer != null && useMarkdownRenderer.isSelected();
        final boolean includeTypeDefs = includeTypeDefinitionsInSourceCode != null && includeTypeDefinitionsInSourceCode.isSelected();
        final boolean showDocInSvg = showDocumentationInSvg != null && showDocumentationInSvg.isSelected();
        final boolean generateSvgOverview = generateSvgOverviewPage != null && generateSvgOverviewPage.isSelected();
        final boolean addMetadata = addMetadataInOutput != null && addMetadataInOutput.isSelected();
        final String imageFormat = grafikFormat != null ? grafikFormat.getValue() : "SVG";
        final String faviconFilePath = faviconPath != null ? faviconPath.getText() : null;

        final WordDocumentationConfig wordConfig = captureWordConfig();
        final PdfDocumentationConfig pdfConfig = capturePdfConfig();

        Task<Void> generationTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Generating " + outputFormat.getDisplayName() + "...");

                XsdDocumentationService docService = new XsdDocumentationService();

                docService.setXsdFilePath(xsdFile.getAbsolutePath());
                docService.setUseMarkdownRenderer(useMarkdown);
                docService.setIncludeTypeDefinitionsInSourceCode(includeTypeDefs);
                docService.setShowDocumentationInSvg(showDocInSvg);
                docService.setGenerateSvgOverviewPage(generateSvgOverview);
                docService.setAddMetadataInOutput(addMetadata);
                docService.setFaviconPath(faviconFilePath);
                docService.setIncludedLanguages(selectedLanguages);
                docService.setFallbackLanguage(fallbackLanguage);

                if ("PNG".equalsIgnoreCase(imageFormat)) {
                    docService.setMethod(XsdDocumentationService.ImageOutputMethod.PNG);
                } else if ("JPG".equalsIgnoreCase(imageFormat)) {
                    docService.setMethod(XsdDocumentationService.ImageOutputMethod.JPG);
                } else {
                    docService.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
                }

                TaskProgressListener progressListener = progressUpdate -> Platform.runLater(() -> {
                    String message = String.format("[%s] %s", progressUpdate.status(), progressUpdate.taskName());
                    if (progressUpdate.status() == TaskProgressListener.ProgressUpdate.Status.FINISHED) {
                        message += " (took " + progressUpdate.durationMillis() + "ms)";
                    }
                    progressContainer.getChildren().add(new Label(message));
                    progressScrollPane.setVvalue(1.0);
                });
                docService.setProgressListener(progressListener);

                switch (outputFormat) {
                    case HTML -> docService.generateXsdDocumentation(outputTarget);
                    case WORD -> {
                        docService.processXsd(useMarkdown);
                        XsdDocumentationWordService wordService = new XsdDocumentationWordService();
                        wordService.setProgressListener(progressListener);
                        wordService.setIncludedLanguages(selectedLanguages);
                        wordService.setConfig(wordConfig);
                        XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                                docService.xsdDocumentationData.getExtendedXsdElementMap());
                        imageService.setShowDocumentation(showDocInSvg);
                        wordService.setImageService(imageService);
                        wordService.generateWordDocumentation(outputTarget, docService.xsdDocumentationData);
                    }
                    case PDF -> {
                        docService.processXsd(useMarkdown);
                        XsdDocumentationPdfService pdfService = new XsdDocumentationPdfService();
                        pdfService.setProgressListener(progressListener);
                        pdfService.setIncludedLanguages(selectedLanguages);
                        pdfService.setConfig(pdfConfig);
                        XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                                docService.xsdDocumentationData.getExtendedXsdElementMap());
                        imageService.setShowDocumentation(showDocInSvg);
                        pdfService.setImageService(imageService);
                        pdfService.generatePdfDocumentation(outputTarget, docService.xsdDocumentationData);
                    }
                }
                return null;
            }
        };

        generationTask.setOnSucceeded(event -> handleDocumentationSuccess(outputTarget, outputFormat));
        generationTask.setOnFailed(event -> handleDocumentationFailure(generationTask.getException()));
        generationTask.setOnCancelled(event -> handleDocumentationCancelled());
        return generationTask;
    }

    private void handleDocumentationSuccess(File outputTarget, DocumentationOutputFormat format) {
        stopDocumentationTimer();
        currentDocumentationTask = null;
        resetDocumentationButtons();

        lastGeneratedDocFolder = format == DocumentationOutputFormat.HTML
                ? outputTarget : outputTarget.getParentFile();

        progressScrollPane.setVisible(false);
        progressScrollPane.setManaged(false);

        String elapsedTime = FormattingUtils.formatElapsedTime(System.currentTimeMillis() - documentationStartTime);
        statusText.setText(format.getDisplayName() + " generated successfully in " + elapsedTime
                + " - " + outputTarget.getAbsolutePath());

        if (statusMessageContainer != null) {
            statusMessageContainer.setStyle("-fx-background-color: #d4edda; -fx-background-radius: 8; "
                    + "-fx-padding: 15; -fx-border-radius: 8; -fx-border-color: #c3e6cb; -fx-border-width: 1;");
            statusMessageContainer.setVisible(true);
            statusMessageContainer.setManaged(true);
        }

        if (openDocFolder != null) {
            openDocFolder.setVisible(true);
            openDocFolder.setManaged(true);
            openDocFolder.setDisable(false);
            openDocFolder.setText(format == DocumentationOutputFormat.HTML ? "Open Folder" : "Open File");
        }

        if (openFileAfterCreation != null && openFileAfterCreation.isSelected()) {
            if (format == DocumentationOutputFormat.HTML) {
                startDocServerAndShowPreview(outputTarget);
            } else {
                openFileWithDefaultApplication(outputTarget);
            }
        }
    }

    private void handleDocumentationFailure(Throwable e) {
        stopDocumentationTimer();
        currentDocumentationTask = null;
        resetDocumentationButtons();

        progressScrollPane.setVisible(false);
        progressScrollPane.setManaged(false);
        logger.error("Failed to generate documentation.", e);

        if (statusMessageContainer != null) {
            statusText.setText("Error generating documentation: " + e.getMessage());
            statusMessageContainer.setStyle("-fx-background-color: #f8d7da; -fx-background-radius: 8; "
                    + "-fx-padding: 15; -fx-border-radius: 8; -fx-border-color: #f5c6cb; -fx-border-width: 1;");
            statusMessageContainer.setVisible(true);
            statusMessageContainer.setManaged(true);
        }

        if (openDocFolder != null) {
            openDocFolder.setVisible(false);
            openDocFolder.setManaged(false);
        }

        if (e instanceof Exception) {
            DialogHelper.showException("Generate Documentation", "Failed to Generate Documentation", (Exception) e);
        } else {
            DialogHelper.showError("Generate Documentation", "Error", e.getMessage());
        }
    }

    private void handleDocumentationCancelled() {
        stopDocumentationTimer();
        currentDocumentationTask = null;
        resetDocumentationButtons();

        progressScrollPane.setVisible(false);
        progressScrollPane.setManaged(false);

        if (statusMessageContainer != null) {
            statusText.setText("Documentation generation cancelled.");
            statusMessageContainer.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 8; "
                    + "-fx-padding: 15; -fx-border-radius: 8; -fx-border-color: #ffc107; -fx-border-width: 1;");
            statusMessageContainer.setVisible(true);
            statusMessageContainer.setManaged(true);
        }

        if (openDocFolder != null) {
            openDocFolder.setVisible(false);
            openDocFolder.setManaged(false);
        }

        logger.info("Documentation generation was cancelled.");
    }

    private void resetDocumentationButtons() {
        if (cancelDocumentationButton != null) {
            cancelDocumentationButton.setVisible(false);
            cancelDocumentationButton.setManaged(false);
        }
        if (generateDocumentationButton != null) {
            generateDocumentationButton.setDisable(false);
        }
    }

    private void startDocumentationTimer() {
        documentationStartTime = System.currentTimeMillis();
        documentationTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateTimerDisplay()));
        documentationTimer.setCycleCount(Animation.INDEFINITE);
        documentationTimer.play();
    }

    private void updateTimerDisplay() {
        long elapsed = System.currentTimeMillis() - documentationStartTime;
        String time = String.format("%02d:%02d", (elapsed / 60000), (elapsed % 60000) / 1000);
        parentController.updateBackgroundTaskTimer(time);
    }

    private void stopDocumentationTimer() {
        if (documentationTimer != null) {
            documentationTimer.stop();
        }
    }

    // ======================================================================
    // Language Configuration
    // ======================================================================

    @FXML
    public void scanForLanguages() {
        String xsdPath = xsdFilePath.getText();
        if (xsdPath == null || xsdPath.isBlank()) {
            DialogHelper.showWarning("Scan Languages", "No XSD File",
                    "Please select an XSD file first.");
            return;
        }

        File xsdFile = new File(xsdPath);
        if (!xsdFile.exists()) {
            DialogHelper.showError("Scan Languages", "File Not Found",
                    "The specified XSD file does not exist.");
            return;
        }

        if (languageScanStatus != null) {
            languageScanStatus.setText("Scanning...");
            languageScanStatus.setStyle("-fx-text-fill: #4a90d9;");
        }

        Task<Set<String>> scanTask = new Task<>() {
            @Override
            protected Set<String> call() throws Exception {
                XsdDocumentationService scanService = new XsdDocumentationService();
                scanService.setXsdFilePath(xsdFile.getAbsolutePath());
                scanService.processXsd(false);
                return scanService.getDiscoveredLanguages();
            }
        };

        scanTask.setOnSucceeded(event -> {
            discoveredLanguages = new LinkedHashSet<>(scanTask.getValue());
            updateLanguageUI(discoveredLanguages);
        });

        scanTask.setOnFailed(event -> {
            DialogHelper.showError("Scan Languages", "Scan Failed",
                    "Could not scan XSD file: " + scanTask.getException().getMessage());
            if (languageScanStatus != null) {
                languageScanStatus.setText("Scan failed");
                languageScanStatus.setStyle("-fx-text-fill: #dc3545;");
            }
        });

        parentController.executeBackgroundTask(scanTask);
    }

    private void updateLanguageUI(Set<String> languages) {
        if (languages == null || languages.isEmpty()) {
            if (languageScanStatus != null) {
                languageScanStatus.setText("No languages found in documentation");
                languageScanStatus.setStyle("-fx-text-fill: #6c757d;");
            }
            if (languageSelectionContainer != null) {
                languageSelectionContainer.setVisible(false);
                languageSelectionContainer.setManaged(false);
            }
            return;
        }

        if (languages.size() == 1) {
            String singleLang = languages.iterator().next();
            if (languageScanStatus != null) {
                languageScanStatus.setText("1 language detected: " + singleLang + " (no filtering needed)");
                languageScanStatus.setStyle("-fx-text-fill: #28a745;");
            }
            if (languageSelectionContainer != null) {
                languageSelectionContainer.setVisible(false);
                languageSelectionContainer.setManaged(false);
            }
            updateFallbackLanguageUI(languages);
            return;
        }

        if (languageScanStatus != null) {
            languageScanStatus.setText(languages.size() + " language(s) detected: " + String.join(", ", languages));
            languageScanStatus.setStyle("-fx-text-fill: #28a745;");
        }

        if (languageCheckComboBox == null) {
            languageCheckComboBox = new CheckComboBox<>();
            languageCheckComboBox.setTitle("Select languages...");
            languageCheckComboBox.setPrefWidth(250);
            if (languageSelectionContainer != null) {
                languageSelectionContainer.getChildren().add(languageCheckComboBox);
            }
        }

        languageCheckComboBox.getItems().clear();
        languageCheckComboBox.getItems().addAll(languages);
        languageCheckComboBox.getCheckModel().checkAll();

        if (languageSelectionContainer != null) {
            languageSelectionContainer.setVisible(true);
            languageSelectionContainer.setManaged(true);
        }

        updateFallbackLanguageUI(languages);
    }

    private void updateFallbackLanguageUI(Set<String> languages) {
        List<String> fallbackOptions = languages.stream()
                .filter(lang -> !"default".equalsIgnoreCase(lang))
                .sorted()
                .toList();

        if (fallbackLanguageComboBox == null) {
            return;
        }

        fallbackLanguageComboBox.getItems().clear();
        fallbackLanguageComboBox.getItems().add("(none)");
        fallbackLanguageComboBox.getItems().addAll(fallbackOptions);

        if (!fallbackOptions.isEmpty()) {
            fallbackLanguageComboBox.setValue(fallbackOptions.get(0));
        } else {
            fallbackLanguageComboBox.setValue("(none)");
        }
    }

    private Set<String> getSelectedLanguages() {
        if (languageCheckComboBox == null) {
            return null;
        }
        List<String> checkedItems = languageCheckComboBox.getCheckModel().getCheckedItems();
        if (checkedItems.isEmpty() || checkedItems.size() == languageCheckComboBox.getItems().size()) {
            return null;
        }
        return new LinkedHashSet<>(checkedItems);
    }

    private String getSelectedFallbackLanguage() {
        if (fallbackLanguageComboBox == null || fallbackLanguageComboBox.getValue() == null) {
            return null;
        }
        String selected = fallbackLanguageComboBox.getValue();
        return "(none)".equals(selected) ? null : selected;
    }

    // ======================================================================
    // Word/PDF Configuration Capture
    // ======================================================================

    private WordDocumentationConfig captureWordConfig() {
        WordDocumentationConfig config = new WordDocumentationConfig();
        if (wordPageSize != null && wordPageSize.getValue() != null) {
            config.setPageSize(switch (wordPageSize.getValue()) {
                case "Letter" -> WordDocumentationConfig.PageSize.LETTER;
                case "Legal" -> WordDocumentationConfig.PageSize.LEGAL;
                default -> WordDocumentationConfig.PageSize.A4;
            });
        }
        return config;
    }

    private PdfDocumentationConfig capturePdfConfig() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        if (pdfPageSize != null && pdfPageSize.getValue() != null) {
            config.setPageSize(switch (pdfPageSize.getValue()) {
                case "Letter" -> PdfDocumentationConfig.PageSize.LETTER;
                case "Legal" -> PdfDocumentationConfig.PageSize.LEGAL;
                case "A3" -> PdfDocumentationConfig.PageSize.A3;
                default -> PdfDocumentationConfig.PageSize.A4;
            });
        }
        return config;
    }

    // ======================================================================
    // File Choosers & Helpers
    // ======================================================================

    @FXML
    public void openXsdFileChooser() {
        File file = parentController.openXsdFileChooser();
        if (file != null && xsdFilePath != null) {
            xsdFilePath.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void openOutputFolderDialog() {
        javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
        dirChooser.setTitle("Select Output Directory");
        File dir = dirChooser.showDialog(documentationOutputDirPath.getScene().getWindow());
        if (dir != null && documentationOutputDirPath != null) {
            documentationOutputDirPath.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    public void openGeneratedDocFolder() {
        if (lastGeneratedDocFolder != null && lastGeneratedDocFolder.exists()) {
            parentController.openFolderInExplorer(lastGeneratedDocFolder);
        } else {
            DialogHelper.showWarning("Open Folder", "No Folder Available",
                    "No documentation has been generated yet, or the folder no longer exists.");
        }
    }

    @FXML
    public void cancelDocumentationGeneration() {
        if (currentDocumentationTask != null && currentDocumentationTask.isRunning()) {
            logger.info("Cancelling documentation generation...");
            currentDocumentationTask.cancel(true);
        }
        stopDocumentationTimer();
        currentDocumentationTask = null;
        resetDocumentationButtons();

        progressScrollPane.setVisible(false);
        progressScrollPane.setManaged(false);

        if (statusMessageContainer != null) {
            statusText.setText("Documentation generation cancelled.");
            statusMessageContainer.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 8; "
                    + "-fx-padding: 15; -fx-border-radius: 8; -fx-border-color: #ffc107; -fx-border-width: 1;");
            statusMessageContainer.setVisible(true);
            statusMessageContainer.setManaged(true);
        }

        if (openDocFolder != null) {
            openDocFolder.setVisible(false);
            openDocFolder.setManaged(false);
        }
    }

    private void openFileWithDefaultApplication(File file) {
        try {
            java.awt.Desktop.getDesktop().open(file);
        } catch (IOException e) {
            logger.error("Failed to open file: {}", file.getAbsolutePath(), e);
            DialogHelper.showError("Open File", "Cannot Open File",
                    "Failed to open the file with the default application: " + e.getMessage());
        }
    }

    private void startDocServerAndShowPreview(File outputDir) {
        stopDocServer();

        Path docRootPath = outputDir.toPath().toAbsolutePath().normalize();
        docServer = SimpleFileServer.createFileServer(
                new InetSocketAddress(DOC_SERVER_PORT),
                docRootPath,
                SimpleFileServer.OutputLevel.INFO);
        docServer.start();
        logger.info("Documentation server started on http://localhost:{}", DOC_SERVER_PORT);

        String url = "http://localhost:" + DOC_SERVER_PORT + "/index.html";
        Platform.runLater(() -> {
            docWebView.getEngine().load(url);
            if (docPreviewTab != null) {
                docPreviewTab.setDisable(false);
            }
        });
    }

    private void stopDocServer() {
        if (docServer != null) {
            docServer.stop(0);
            docServer = null;
        }
    }

    public void setParentController(XsdController parentController) {
        this.parentController = parentController;
    }
}
