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
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.SchemaGenerationEngine;
import org.fxt.freexmltoolkit.service.SchemaGenerationOptions;
import org.fxt.freexmltoolkit.service.SchemaGenerationResult;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Intelligent Schema Generator - Revolutionary Feature #3
 * Auto-generates XSD schemas with advanced type inference and optimization
 */
public class SchemaGeneratorController {
    private static final Logger logger = LogManager.getLogger(SchemaGeneratorController.class);

    // Revolutionary Services
    private final SchemaGenerationEngine schemaEngine = SchemaGenerationEngine.getInstance();

    // Background processing
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("SchemaGenerator-Thread");
        return t;
    });

    // UI Components - Configuration Options
    @FXML
    private CheckBox enableSmartTypeInference;
    @FXML
    private CheckBox inferComplexTypes;
    @FXML
    private CheckBox strictTypeInference;
    @FXML
    private CheckBox analyzeDataPatterns;
    @FXML
    private CheckBox generateComplexTypes;
    @FXML
    private CheckBox inlineSimpleTypes;
    @FXML
    private CheckBox groupSimilarElements;
    @FXML
    private CheckBox generateGroups;
    @FXML
    private CheckBox optimizeSchema;
    @FXML
    private CheckBox eliminateDuplicates;
    @FXML
    private CheckBox mergeCompatibleTypes;
    @FXML
    private CheckBox preserveNamespaces;
    @FXML
    private CheckBox generateTargetNamespace;
    @FXML
    private TextField targetNamespaceField;

    // UI Components - Input/Output
    @FXML
    private TextArea xmlInputArea;
    @FXML
    private Button loadXmlBtn;
    @FXML
    private Button pasteXmlBtn;
    @FXML
    private Button clearXmlBtn;
    @FXML
    private Button batchProcessBtn;
    @FXML
    private Button generateSchemaBtn;
    @FXML
    private ProgressBar generationProgressBar;

    // UI Components - Results
    @FXML
    private TabPane schemaResultsTabPane;
    @FXML
    private TextArea xsdOutputArea;
    @FXML
    private CheckBox formatXsdOutput;
    @FXML
    private CheckBox addComments;
    @FXML
    private Button copyXsdBtn;
    @FXML
    private Button exportSchemaBtn;
    @FXML
    private Label generationStatsLabel;

    // UI Components - Analysis Results
    @FXML
    private Label complexTypesLabel;
    @FXML
    private Label simpleTypesLabel;
    @FXML
    private Label elementsLabel;
    @FXML
    private Label attributesLabel;
    @FXML
    private Label namespacesLabel;
    @FXML
    private Label generationTimeLabel;
    @FXML
    private Label xmlSizeLabel;
    @FXML
    private Label xsdSizeLabel;
    @FXML
    private Label compressionLabel;
    @FXML
    private TextArea analysisReportArea;

    // UI Components - Type Definitions
    @FXML
    private ComboBox<String> typeFilterCombo;
    @FXML
    private TextField typeSearchField;
    @FXML
    private TableView<TypeDefinition> typeDefinitionsTable;
    @FXML
    private TableColumn<TypeDefinition, String> typeNameColumn;
    @FXML
    private TableColumn<TypeDefinition, String> typeKindColumn;
    @FXML
    private TableColumn<TypeDefinition, String> typeBaseColumn;
    @FXML
    private TableColumn<TypeDefinition, Integer> typeUsageColumn;
    @FXML
    private TableColumn<TypeDefinition, String> typeDescriptionColumn;

    // State Management
    private SchemaGenerationResult lastResult;

    @FXML
    private void initialize() {
        logger.info("Initializing Intelligent Schema Generator Controller - Revolutionary Feature #3");

        initializeUI();
        setupEventHandlers();
        setDefaultOptions();

        logger.info("Schema Generator Controller initialized successfully");
    }

    private void initializeUI() {
        // Initialize type definitions table
        if (typeDefinitionsTable != null) {
            typeNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
            typeKindColumn.setCellValueFactory(new PropertyValueFactory<>("kind"));
            typeBaseColumn.setCellValueFactory(new PropertyValueFactory<>("baseType"));
            typeUsageColumn.setCellValueFactory(new PropertyValueFactory<>("usageCount"));
            typeDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        }

        // Initialize type filter combo
        if (typeFilterCombo != null) {
            typeFilterCombo.setItems(FXCollections.observableArrayList(
                    "All Types", "Complex Types", "Simple Types", "Elements", "Attributes"
            ));
            typeFilterCombo.setValue("All Types");
        }

        // Set initial button states
        if (exportSchemaBtn != null) exportSchemaBtn.setDisable(true);
        if (generationProgressBar != null) generationProgressBar.setVisible(false);
    }

    private void setupEventHandlers() {
        // Type filter change
        if (typeFilterCombo != null) {
            typeFilterCombo.setOnAction(e -> filterTypeDefinitions());
        }

        // Type search field
        if (typeSearchField != null) {
            typeSearchField.textProperty().addListener((obs, oldText, newText) -> filterTypeDefinitions());
        }
    }

    private void setDefaultOptions() {
        // Set recommended default values
        if (enableSmartTypeInference != null) enableSmartTypeInference.setSelected(true);
        if (inferComplexTypes != null) inferComplexTypes.setSelected(true);
        if (analyzeDataPatterns != null) analyzeDataPatterns.setSelected(true);
        if (generateComplexTypes != null) generateComplexTypes.setSelected(true);
        if (groupSimilarElements != null) groupSimilarElements.setSelected(true);
        if (optimizeSchema != null) optimizeSchema.setSelected(true);
        if (eliminateDuplicates != null) eliminateDuplicates.setSelected(true);
        if (mergeCompatibleTypes != null) mergeCompatibleTypes.setSelected(true);
        if (preserveNamespaces != null) preserveNamespaces.setSelected(true);
        if (generateTargetNamespace != null) generateTargetNamespace.setSelected(true);
        if (formatXsdOutput != null) formatXsdOutput.setSelected(true);
        if (addComments != null) addComments.setSelected(true);
    }

    @FXML
    private void generateSchema() {
        String xmlContent = xmlInputArea != null ? xmlInputArea.getText().trim() : "";

        if (xmlContent.isEmpty()) {
            showAlert("Input Required", "Please enter XML content or load an XML file.");
            return;
        }

        // Show progress indicator
        if (generationProgressBar != null) generationProgressBar.setVisible(true);
        if (generateSchemaBtn != null) generateSchemaBtn.setDisable(true);

        Task<SchemaGenerationResult> generationTask = new Task<>() {
            @Override
            protected SchemaGenerationResult call() throws Exception {
                SchemaGenerationOptions options = buildGenerationOptions();
                return schemaEngine.generateSchema(xmlContent, options);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    lastResult = getValue();
                    displayGenerationResults(lastResult);

                    // Hide progress indicator
                    if (generationProgressBar != null) generationProgressBar.setVisible(false);
                    if (generateSchemaBtn != null) generateSchemaBtn.setDisable(false);
                    if (exportSchemaBtn != null) exportSchemaBtn.setDisable(false);

                    logger.info("Schema generation completed successfully");
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    // Hide progress indicator
                    if (generationProgressBar != null) generationProgressBar.setVisible(false);
                    if (generateSchemaBtn != null) generateSchemaBtn.setDisable(false);

                    logger.error("Schema generation failed", getException());
                    showAlert("Generation Error", "Failed to generate schema: " + getException().getMessage());
                });
            }
        };

        executorService.submit(generationTask);
    }

    private SchemaGenerationOptions buildGenerationOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        // Type inference options
        if (enableSmartTypeInference != null)
            options.setEnableSmartTypeInference(enableSmartTypeInference.isSelected());
        if (inferComplexTypes != null)
            options.setInferComplexTypes(inferComplexTypes.isSelected());
        if (strictTypeInference != null)
            options.setStrictTypeInference(strictTypeInference.isSelected());
        if (analyzeDataPatterns != null)
            options.setAnalyzeDataPatterns(analyzeDataPatterns.isSelected());

        // Structure options
        if (generateComplexTypes != null)
            options.setGenerateComplexTypes(generateComplexTypes.isSelected());
        if (inlineSimpleTypes != null)
            options.setInlineSimpleTypes(inlineSimpleTypes.isSelected());
        if (groupSimilarElements != null)
            options.setGroupSimilarElements(groupSimilarElements.isSelected());
        if (generateGroups != null)
            options.setGenerateGroups(generateGroups.isSelected());

        // Optimization options
        if (optimizeSchema != null)
            options.setOptimizeSchema(optimizeSchema.isSelected());
        if (eliminateDuplicates != null)
            options.setEliminateDuplicateTypes(eliminateDuplicates.isSelected());
        if (mergeCompatibleTypes != null)
            options.setMergeCompatibleTypes(mergeCompatibleTypes.isSelected());

        // Namespace handling
        if (preserveNamespaces != null)
            options.setPreserveNamespaces(preserveNamespaces.isSelected());
        if (generateTargetNamespace != null)
            options.setGenerateTargetNamespace(generateTargetNamespace.isSelected());
        if (targetNamespaceField != null && !targetNamespaceField.getText().trim().isEmpty())
            options.setTargetNamespaceUri(targetNamespaceField.getText().trim());

        return options;
    }

    private void displayGenerationResults(SchemaGenerationResult result) {
        // Display generated XSD
        if (xsdOutputArea != null) {
            if (formatXsdOutput != null && formatXsdOutput.isSelected()) {
                xsdOutputArea.setText(result.getFormattedXsdContent());
            } else {
                xsdOutputArea.setText(result.getXsdContent());
            }
        }

        // Update statistics
        updateStatistics(result);

        // Display analysis report
        if (analysisReportArea != null) {
            analysisReportArea.setText(result.getDetailedReport());
        }

        // Update generation stats label
        if (generationStatsLabel != null) {
            generationStatsLabel.setText(String.format(
                    "Generated in %dms | %d complex types | %d simple types",
                    result.getGenerationTimeMs(),
                    result.getTotalComplexTypesGenerated(),
                    result.getTotalSimpleTypesGenerated()
            ));
        }
    }

    private void updateStatistics(SchemaGenerationResult result) {
        if (complexTypesLabel != null)
            complexTypesLabel.setText(String.valueOf(result.getTotalComplexTypesGenerated()));
        if (simpleTypesLabel != null)
            simpleTypesLabel.setText(String.valueOf(result.getTotalSimpleTypesGenerated()));
        if (elementsLabel != null)
            elementsLabel.setText(String.valueOf(result.getTotalElementsGenerated()));
        if (attributesLabel != null)
            attributesLabel.setText(String.valueOf(result.getTotalAttributesGenerated()));
        if (namespacesLabel != null)
            namespacesLabel.setText("N/A"); // Namespace count not available in current API

        if (generationTimeLabel != null)
            generationTimeLabel.setText(result.getGenerationTimeMs() + "ms");
        if (xmlSizeLabel != null)
            xmlSizeLabel.setText("N/A"); // Input size not tracked in current API
        if (xsdSizeLabel != null)
            xsdSizeLabel.setText(formatBytes(result.getGeneratedContentLength()));
        if (compressionLabel != null) {
            double compression = 0; // Compression calculation not available without input size
            compressionLabel.setText(String.format("%.1f%%", Math.max(0, compression)));
        }
    }

    @FXML
    private void exportSchema() {
        if (lastResult == null || xsdOutputArea == null) {
            showAlert("No Schema", "Please generate a schema first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export XSD Schema");
        fileChooser.setInitialFileName("generated_schema.xsd");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

        File file = fileChooser.showSaveDialog(exportSchemaBtn.getScene().getWindow());
        if (file != null) {
            try {
                Files.write(file.toPath(), xsdOutputArea.getText().getBytes(StandardCharsets.UTF_8));
                showInfo("Export Successful", "Schema exported to: " + file.getAbsolutePath());
                logger.info("Schema exported to: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to export schema", e);
                showAlert("Export Error", "Failed to export schema: " + e.getMessage());
            }
        }
    }

    @FXML
    private void copyXsd() {
        if (xsdOutputArea != null && !xsdOutputArea.getText().isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(xsdOutputArea.getText()), null);
            showInfo("Copied", "XSD content copied to clipboard.");
        }
    }

    @FXML
    private void loadXml() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load XML File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(loadXmlBtn.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (xmlInputArea != null) {
                    xmlInputArea.setText(content);
                }
                logger.debug("Loaded XML file: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to load XML file", e);
                showAlert("Load Error", "Failed to load XML file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void pasteXml() {
        try {
            String clipboardContent = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            if (xmlInputArea != null && clipboardContent != null) {
                xmlInputArea.setText(clipboardContent);
            }
        } catch (Exception e) {
            showAlert("Paste Error", "Failed to paste from clipboard: " + e.getMessage());
        }
    }

    @FXML
    private void clearXml() {
        if (xmlInputArea != null) {
            xmlInputArea.clear();
        }
    }

    @FXML
    private void batchProcess() {
        // TODO: Implement batch processing functionality
        showInfo("Batch Process", "Batch processing feature coming soon!");
    }

    private void filterTypeDefinitions() {
        // TODO: Implement type definitions filtering
        logger.debug("Type definitions filtering requested");
    }

    // Utility Methods
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for Type Definitions
    public static class TypeDefinition {
        private String name;
        private String kind;
        private String baseType;
        private int usageCount;
        private String description;

        public TypeDefinition(String name, String kind, String baseType, int usageCount, String description) {
            this.name = name;
            this.kind = kind;
            this.baseType = baseType;
            this.usageCount = usageCount;
            this.description = description;
        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getBaseType() {
            return baseType;
        }

        public void setBaseType(String baseType) {
            this.baseType = baseType;
        }

        public int getUsageCount() {
            return usageCount;
        }

        public void setUsageCount(int usageCount) {
            this.usageCount = usageCount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    // Lifecycle
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("Schema Generator Controller shutdown completed");
        }
    }
}