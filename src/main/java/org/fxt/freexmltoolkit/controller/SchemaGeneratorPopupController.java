/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.SchemaGenerationEngine;
import org.fxt.freexmltoolkit.service.SchemaGenerationOptions;
import org.fxt.freexmltoolkit.service.SchemaGenerationResult;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Schema Generator Popup
 */
public class SchemaGeneratorPopupController implements Initializable {
    private static final Logger logger = LogManager.getLogger(SchemaGeneratorPopupController.class);

    // Services
    private final XmlContentProvider contentProvider;
    private final SchemaGenerationEngine schemaEngine;

    // Background processing
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("SchemaGenerator-Thread");
        return t;
    });

    // UI Components - Source Selection
    @FXML
    private RadioButton useCurrentEditorRadio;
    @FXML
    private RadioButton useFileRadio;
    @FXML
    private RadioButton useMultipleFilesRadio;
    @FXML
    private ToggleGroup sourceGroup;
    @FXML
    private TextField sourceFileField;
    @FXML
    private Button browseSourceButton;
    @FXML
    private Button addMoreFilesButton;
    @FXML
    private ListView<String> sourceFilesList;

    // UI Components - Generation Options
    @FXML
    private CheckBox inferTypesCheckbox;
    @FXML
    private CheckBox flattenSchemaCheckbox;
    @FXML
    private CheckBox generateDocumentationCheckbox;
    @FXML
    private CheckBox enforceOrderCheckbox;
    @FXML
    private CheckBox includeAttributesCheckbox;
    @FXML
    private CheckBox makeOptionalCheckbox;
    @FXML
    private CheckBox generateExamplesCheckbox;
    @FXML
    private CheckBox includeNamespacesCheckbox;

    // UI Components - Advanced Options
    @FXML
    private TextField targetNamespaceField;
    @FXML
    private TextField schemaPrefixField;
    @FXML
    private TextField rootElementField;
    @FXML
    private Spinner<Integer> maxOccursSpinner;
    @FXML
    private CheckBox unboundedMaxOccursCheckbox;

    // UI Components - Results
    @FXML
    private ProgressIndicator generationProgress;
    @FXML
    private Label generationStatusLabel;
    @FXML
    private Button generateSchemaButton;
    @FXML
    private TextArea schemaPreviewArea;
    @FXML
    private VBox reportContainer;
    @FXML
    private Label elementCountLabel;
    @FXML
    private Label attributeCountLabel;
    @FXML
    private Label complexTypeCountLabel;
    @FXML
    private Label simpleTypeCountLabel;
    @FXML
    private Label namespaceCountLabel;

    // UI Components - Actions
    @FXML
    private Button validateSchemaButton;
    @FXML
    private Button saveSchemaButton;
    @FXML
    private Button exportSchemaButton;
    @FXML
    private Button cancelButton;

    // State
    private String generatedSchemaContent = "";
    private SchemaGenerationResult lastResult;
    private final List<File> selectedFiles = new ArrayList<>();

    /**
     * Creates a new SchemaGeneratorPopupController with the specified content provider and schema engine.
     *
     * @param contentProvider the provider for accessing current XML editor content
     * @param schemaEngine the engine used to generate XSD schemas from XML documents
     */
    public SchemaGeneratorPopupController(XmlContentProvider contentProvider,
                                          SchemaGenerationEngine schemaEngine) {
        this.contentProvider = contentProvider;
        this.schemaEngine = schemaEngine;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing Schema Generator Popup");
        initializeUI();
        setupEventHandlers();
        resetStatistics();
    }

    private void initializeUI() {
        // Initialize spinners
        maxOccursSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 10));

        // Set default values
        schemaPrefixField.setText("xs");
        targetNamespaceField.setText("http://example.com/schema");

        // Initialize file selection area
        updateFileSelectionVisibility();
    }

    private void setupEventHandlers() {
        cancelButton.setOnAction(e -> closePopup());

        // Source selection radio buttons
        sourceGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            updateFileSelectionVisibility();
        });

        // File selection
        browseSourceButton.setOnAction(e -> browseForFiles());
        addMoreFilesButton.setOnAction(e -> addMoreFiles());

        // Generation button
        generateSchemaButton.setOnAction(e -> generateSchema());

        // Action buttons
        validateSchemaButton.setOnAction(e -> validateSchema());
        saveSchemaButton.setOnAction(e -> saveSchema());
        exportSchemaButton.setOnAction(e -> exportSchema());

        // Unbounded max occurs checkbox
        unboundedMaxOccursCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            maxOccursSpinner.setDisable(newVal);
        });
    }

    @FXML
    private void generateSchema() {
        logger.info("Generating XML Schema");

        // Prepare options
        SchemaGenerationOptions options = buildGenerationOptions();

        // Get XML content
        List<String> xmlContents = getXmlContents();
        if (xmlContents.isEmpty()) {
            showError("No XML Content", "Please provide XML content to generate schema from.");
            return;
        }

        // Show progress
        generationProgress.setVisible(true);
        generationStatusLabel.setText("Generating schema...");
        generateSchemaButton.setDisable(true);

        Task<SchemaGenerationResult> schemaTask = new Task<>() {
            @Override
            protected SchemaGenerationResult call() throws Exception {
                if (xmlContents.size() == 1) {
                    return schemaEngine.generateSchema(xmlContents.get(0), options);
                } else {
                    return schemaEngine.generateSchemaFromMultipleDocuments(xmlContents, options);
                }
            }
        };

        schemaTask.setOnSucceeded(e -> {
            lastResult = schemaTask.getValue();
            generatedSchemaContent = lastResult.getXsdContent();

            Platform.runLater(() -> {
                schemaPreviewArea.setText(generatedSchemaContent);
                updateGenerationReport(lastResult);
                updateStatistics(lastResult);

                generationProgress.setVisible(false);
                generationStatusLabel.setText("Schema generated successfully");
                generateSchemaButton.setDisable(false);

                // Enable action buttons
                validateSchemaButton.setDisable(false);
                saveSchemaButton.setDisable(false);
                exportSchemaButton.setDisable(false);
            });
        });

        schemaTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = schemaTask.getException();
                showError("Schema Generation Error", "Could not generate schema: " + exception.getMessage());
                logger.error("Failed to generate schema", exception);

                generationProgress.setVisible(false);
                generationStatusLabel.setText("Generation failed");
                generateSchemaButton.setDisable(false);
            });
        });

        executorService.submit(schemaTask);
    }

    @FXML
    private void validateSchema() {
        if (generatedSchemaContent.isEmpty()) {
            showError("No Schema", "Please generate a schema first.");
            return;
        }

        try {
            // Basic XSD validation
            // This would typically use a more sophisticated validator
            if (generatedSchemaContent.contains("xs:schema") || generatedSchemaContent.contains("xsd:schema")) {
                showInfo("Schema Valid", "The generated schema appears to be valid XSD.");
            } else {
                showError("Schema Invalid", "The generated content does not appear to be valid XSD.");
            }
        } catch (Exception e) {
            showError("Validation Error", "Could not validate schema: " + e.getMessage());
            logger.error("Failed to validate schema", e);
        }
    }

    @FXML
    private void saveSchema() {
        if (generatedSchemaContent.isEmpty()) {
            showError("No Schema", "Please generate a schema first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save XSD Schema");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XSD Files", "*.xsd")
        );

        File file = fileChooser.showSaveDialog(cancelButton.getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), generatedSchemaContent);
                showInfo("Schema Saved", "Schema saved to: " + file.getAbsolutePath());
            } catch (IOException e) {
                showError("Save Error", "Could not save schema: " + e.getMessage());
                logger.error("Failed to save schema", e);
            }
        }
    }

    @FXML
    private void exportSchema() {
        saveSchema(); // Save the schema first
        closePopup(); // Then close the popup
    }

    private void browseForFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select XML File(s)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        if (useMultipleFilesRadio.isSelected()) {
            List<File> files = fileChooser.showOpenMultipleDialog(cancelButton.getScene().getWindow());
            if (files != null && !files.isEmpty()) {
                selectedFiles.addAll(files);
                updateFilesList();
            }
        } else {
            File file = fileChooser.showOpenDialog(cancelButton.getScene().getWindow());
            if (file != null) {
                selectedFiles.clear();
                selectedFiles.add(file);
                sourceFileField.setText(file.getAbsolutePath());
            }
        }
    }

    private void addMoreFiles() {
        browseForFiles();
    }

    private void updateFileSelectionVisibility() {
        boolean useFile = useFileRadio.isSelected() || useMultipleFilesRadio.isSelected();
        sourceFileField.getParent().setDisable(!useFile);
        addMoreFilesButton.setDisable(!useMultipleFilesRadio.isSelected());

        if (useMultipleFilesRadio.isSelected()) {
            sourceFilesList.setVisible(true);
            sourceFilesList.setManaged(true);
            sourceFileField.setPromptText("Multiple files selected...");
        } else {
            sourceFilesList.setVisible(false);
            sourceFilesList.setManaged(false);
            sourceFileField.setPromptText("Select XML file...");
        }
    }

    private void updateFilesList() {
        List<String> fileNames = selectedFiles.stream()
                .map(File::getName)
                .toList();
        sourceFilesList.setItems(FXCollections.observableArrayList(fileNames));
    }

    private SchemaGenerationOptions buildGenerationOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        options.setEnableSmartTypeInference(inferTypesCheckbox.isSelected());
        options.setFlattenUnnecessaryStructure(flattenSchemaCheckbox.isSelected());
        options.setGenerateDocumentation(generateDocumentationCheckbox.isSelected());
        // Map UI options to available SchemaGenerationOptions methods
        options.setPreserveElementOrder(enforceOrderCheckbox.isSelected());
        // Include attributes is handled by existing methods
        options.setDetectOptionalElements(makeOptionalCheckbox.isSelected());
        options.setIncludeExampleValues(generateExamplesCheckbox.isSelected());
        options.setPreserveNamespaces(includeNamespacesCheckbox.isSelected());

        // Advanced options - use available methods
        if (!targetNamespaceField.getText().trim().isEmpty()) {
            options.setTargetNamespaceUri(targetNamespaceField.getText().trim());
        }
        if (!schemaPrefixField.getText().trim().isEmpty()) {
            options.setTargetNamespacePrefix(schemaPrefixField.getText().trim());
        }
        // Root element name - use element renames map
        if (!rootElementField.getText().trim().isEmpty()) {
            // This could be handled differently based on available options
        }

        if (unboundedMaxOccursCheckbox.isSelected()) {
            options.setUnboundedForHighOccurrence(true);
            options.setMaxOccursThreshold(1); // Set low threshold for unbounded
        } else {
            options.setMaxOccursThreshold(maxOccursSpinner.getValue());
        }

        return options;
    }

    private List<String> getXmlContents() {
        List<String> contents = new ArrayList<>();

        if (useCurrentEditorRadio.isSelected()) {
            String currentContent = contentProvider.getCurrentXmlContent();
            if (!currentContent.trim().isEmpty()) {
                contents.add(currentContent);
            }
        } else if (!selectedFiles.isEmpty()) {
            for (File file : selectedFiles) {
                try {
                    contents.add(Files.readString(file.toPath()));
                } catch (IOException e) {
                    logger.error("Failed to read file: " + file.getAbsolutePath(), e);
                    showError("File Read Error", "Could not read file: " + file.getName());
                }
            }
        }

        return contents;
    }

    private void updateGenerationReport(SchemaGenerationResult result) {
        reportContainer.getChildren().clear();

        // Add generation report entries - use available methods
        Label reportLabel = new Label(result.getDetailedReport());
        reportLabel.setWrapText(true);
        reportContainer.getChildren().add(reportLabel);
    }

    private void updateStatistics(SchemaGenerationResult result) {
        elementCountLabel.setText(String.valueOf(result.getTotalElementsGenerated()));
        attributeCountLabel.setText(String.valueOf(result.getTotalAttributesGenerated()));
        complexTypeCountLabel.setText(String.valueOf(result.getTotalComplexTypesGenerated()));
        simpleTypeCountLabel.setText(String.valueOf(result.getTotalSimpleTypesGenerated()));

        // For namespace count, we'll check if analysis result is available
        int namespaceCount = 0;
        if (result.getAnalysisResult() != null) {
            namespaceCount = result.getAnalysisResult().getDiscoveredNamespaces().size();
        }
        namespaceCountLabel.setText(String.valueOf(namespaceCount));
    }

    private void resetStatistics() {
        elementCountLabel.setText("0");
        attributeCountLabel.setText("0");
        complexTypeCountLabel.setText("0");
        simpleTypeCountLabel.setText("0");
        namespaceCountLabel.setText("0");
    }

    private void closePopup() {
        executorService.shutdown();
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("Schema Generator");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Schema Generator Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}