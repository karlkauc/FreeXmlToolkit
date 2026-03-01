package org.fxt.freexmltoolkit.controller;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.CheckComboBox;
import org.fxt.freexmltoolkit.domain.DocumentationOutputFormat;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        if (htmlSettingsContainer != null) { htmlSettingsContainer.setVisible(html); htmlSettingsContainer.setManaged(html); }
        if (wordSettingsContainer != null) { wordSettingsContainer.setVisible(word); wordSettingsContainer.setManaged(word); }
        if (pdfSettingsContainer != null) { pdfSettingsContainer.setVisible(pdf); pdfSettingsContainer.setManaged(pdf); }
    }

    @FXML
    private void generateDocumentation() {
        // Validation and setup logic...
        String xsdPath = xsdFilePath.getText();
        if (xsdPath == null || xsdPath.isBlank()) return;

        File xsdFile = new File(xsdPath);
        DocumentationOutputFormat format = getSelectedOutputFormat();
        File outputTarget = new File(documentationOutputDirPath.getText());

        startDocumentationTimer();
        Task<Void> generationTask = getGenerationTask(xsdFile, outputTarget, format);
        currentDocumentationTask = generationTask;
        parentController.executeBackgroundTask(generationTask);
    }

    private DocumentationOutputFormat getSelectedOutputFormat() {
        if (outputFormatWord != null && outputFormatWord.isSelected()) return DocumentationOutputFormat.WORD;
        if (outputFormatPdf != null && outputFormatPdf.isSelected()) return DocumentationOutputFormat.PDF;
        return DocumentationOutputFormat.HTML;
    }

    private @NotNull Task<Void> getGenerationTask(File xsdFile, File outputTarget, DocumentationOutputFormat format) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Background generation work...
                return null;
            }
        };
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
        if (documentationTimer != null) documentationTimer.stop();
    }

    @FXML
    public void scanForLanguages() {
        // Extracted scanning logic...
    }

    @FXML
    private void openXsdFileChooser() {
        parentController.openXsdFileChooser();
    }

    @FXML
    private void openOutputFolderDialog() {
        // Directory choosing logic...
    }

    @FXML
    public void openGeneratedDocFolder() {
        if (lastGeneratedDocFolder != null && lastGeneratedDocFolder.exists()) {
            parentController.openFolderInExplorer(lastGeneratedDocFolder);
        }
    }

    @FXML
    public void cancelDocumentationGeneration() {
        if (currentDocumentationTask != null) currentDocumentationTask.cancel(true);
        stopDocumentationTimer();
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
