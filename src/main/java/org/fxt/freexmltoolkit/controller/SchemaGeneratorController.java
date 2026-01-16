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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.controls.FavoritesPanelController;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;

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
public class SchemaGeneratorController implements FavoritesParentController {
    private static final Logger logger = LogManager.getLogger(SchemaGeneratorController.class);

    // Revolutionary Services
    private final SchemaGenerationEngine schemaEngine = SchemaGenerationEngine.getInstance();
    private final PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

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

    // UI Components - Favorites (unified FavoritesPanel)
    @FXML
    private Button addToFavoritesBtn;
    @FXML
    private Button toggleFavoritesButton;
    @FXML
    private SplitPane rightSplitPane;
    @FXML
    private VBox favoritesPanel;
    @FXML
    private FavoritesPanelController favoritesPanelController;

    // UI Components - Empty State
    @FXML
    private VBox emptyStatePane;
    @FXML
    private VBox contentPane;
    @FXML
    private Button emptyStateOpenXmlButton;
    @FXML
    private Button emptyStatePasteButton;
    @FXML
    private Button helpBtn;

    // State Management
    private SchemaGenerationResult lastResult;
    private File currentXmlFile;

    @FXML
    private void initialize() {
        logger.info("Initializing Intelligent Schema Generator Controller - Revolutionary Feature #3");

        initializeUI();
        setupEventHandlers();
        setDefaultOptions();
        setupFavorites();
        initializeEmptyState();
        setupDragAndDrop();
        applySmallIconsSetting();

        logger.info("Schema Generator Controller initialized successfully");
    }

    /**
     * Set up drag and drop functionality for the Schema Generator controller.
     * Accepts XML files only (used as input for schema generation).
     */
    private void setupDragAndDrop() {
        if (contentPane == null && emptyStatePane == null) {
            logger.warn("Cannot setup drag and drop: no valid container available");
            return;
        }

        // Set up drag and drop on both the empty state pane and content pane
        if (emptyStatePane != null) {
            DragDropService.setupDragDrop(emptyStatePane, DragDropService.XML_EXTENSIONS, this::handleDroppedFiles);
        }
        if (contentPane != null) {
            DragDropService.setupDragDrop(contentPane, DragDropService.XML_EXTENSIONS, this::handleDroppedFiles);
        }
        logger.debug("Drag and drop initialized for Schema Generator controller");
    }

    /**
     * Handle files dropped on the Schema Generator controller.
     * Loads XML files as input for schema generation.
     *
     * @param files the dropped files
     */
    private void handleDroppedFiles(java.util.List<File> files) {
        logger.info("XML files dropped on Schema Generator: {} file(s)", files.size());

        // Load the first XML file
        if (!files.isEmpty()) {
            File file = files.get(0);
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (xmlInputArea != null) {
                    xmlInputArea.setText(content);
                }
                currentXmlFile = file;
                showContent();
                logger.debug("Loaded dropped XML file: {}", file.getName());
            } catch (IOException e) {
                logger.error("Failed to load dropped XML file", e);
                showAlert("Load Error", "Failed to load XML file: " + e.getMessage());
            }
        }
    }

    private void setupFavorites() {
        // Setup unified FavoritesPanel
        if (favoritesPanelController != null) {
            favoritesPanelController.setParentController(this);
        }

        // Setup add to favorites button
        if (addToFavoritesBtn != null) {
            addToFavoritesBtn.setOnAction(e -> addCurrentToFavorites());
        }

        // Setup toggle favorites button
        if (toggleFavoritesButton != null) {
            toggleFavoritesButton.setOnAction(e -> toggleFavoritesPanel());
        }

        // Initially hide favorites panel
        if (favoritesPanel != null && rightSplitPane != null) {
            rightSplitPane.getItems().remove(favoritesPanel);
        }

        logger.debug("Favorites panel setup completed");
    }

    /**
     * Adds the current file to favorites using a dialog.
     */
    private void addCurrentToFavorites() {
        File currentFile = getCurrentFile();
        if (currentFile != null) {
            // Show the favorites panel if hidden
            if (rightSplitPane != null && !rightSplitPane.getItems().contains(favoritesPanel)) {
                toggleFavoritesPanel();
            }
            // Use a simple dialog approach
            TextInputDialog dialog = new TextInputDialog(currentFile.getName());
            dialog.setTitle("Add to Favorites");
            dialog.setHeaderText("Add " + currentFile.getName() + " to favorites");
            dialog.setContentText("Enter alias (optional):");

            dialog.showAndWait().ifPresent(alias -> {
                org.fxt.freexmltoolkit.domain.FileFavorite favorite =
                    new org.fxt.freexmltoolkit.domain.FileFavorite(
                        alias.isEmpty() ? currentFile.getName() : alias,
                        currentFile.getAbsolutePath(),
                        "XML Documents"
                    );
                org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.FavoritesService.class).addFavorite(favorite);
                showInfo("Added to Favorites", currentFile.getName() + " has been added to favorites.");
            });
        } else {
            showAlert("No File Loaded", "Please load an XML file before adding to favorites.");
        }
    }

    /**
     * Toggles the visibility of the favorites panel using SplitPane.
     */
    private void toggleFavoritesPanel() {
        toggleFavoritesPanelInternal();
    }

    private void toggleFavoritesPanelInternal() {
        if (favoritesPanel == null || rightSplitPane == null) {
            return;
        }

        boolean isCurrentlyShown = rightSplitPane.getItems().contains(favoritesPanel);

        if (isCurrentlyShown) {
            rightSplitPane.getItems().remove(favoritesPanel);
            logger.debug("Favorites panel hidden");
        } else {
            rightSplitPane.getItems().add(favoritesPanel);
            rightSplitPane.setDividerPositions(0.75);
            logger.debug("Favorites panel shown");
        }
    }

    // ==================== PUBLIC KEYBOARD SHORTCUT METHODS ====================

    /**
     * Public wrapper for keyboard shortcut access to toggle favorites panel.
     */
    public void toggleFavoritesPanelPublic() {
        toggleFavoritesPanelInternal();
    }

    /**
     * Public wrapper for keyboard shortcut access to add current file to favorites.
     */
    public void addCurrentToFavoritesPublic() {
        addCurrentToFavorites();
    }

    // FavoritesParentController interface implementation

    @Override
    public void loadFileToNewTab(File file) {
        if (file == null || !file.exists()) {
            showAlert("File Not Found", "The selected file does not exist.");
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (xmlInputArea != null) {
                xmlInputArea.setText(content);
            }
            currentXmlFile = file;
            showContent();
            logger.info("Loaded file from favorites: {}", file.getName());
        } catch (IOException e) {
            logger.error("Failed to load file from favorites", e);
            showAlert("Load Error", "Failed to load file: " + e.getMessage());
        }
    }

    @Override
    public File getCurrentFile() {
        return currentXmlFile;
    }

    /**
     * Initializes the empty state UI and wires up button actions.
     */
    private void initializeEmptyState() {
        // Wire up empty state buttons to trigger main actions
        if (emptyStateOpenXmlButton != null) {
            emptyStateOpenXmlButton.setOnAction(e -> loadXmlBtn.fire());
        }

        if (emptyStatePasteButton != null) {
            emptyStatePasteButton.setOnAction(e -> pasteXmlBtn.fire());
        }
    }

    /**
     * Shows the main content and hides the empty state placeholder.
     * Called when files are loaded or content is pasted.
     */
    private void showContent() {
        if (emptyStatePane != null && contentPane != null) {
            emptyStatePane.setVisible(false);
            emptyStatePane.setManaged(false);
            contentPane.setVisible(true);
            contentPane.setManaged(true);
            logger.debug("Switched from empty state to content view");
        }
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

        // Disable batch processing - feature not yet implemented
        if (batchProcessBtn != null) {
            batchProcessBtn.setDisable(true);
            batchProcessBtn.setTooltip(new javafx.scene.control.Tooltip("Batch processing is not yet available"));
        }
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
    public void generateSchema() {
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
                // Add metadata before saving
                ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
                String xsdContent = xsdOutputArea.getText();
                String contentWithMetadata = metadataService.addOrUpdateXmlMetadata(xsdContent);

                Files.write(file.toPath(), contentWithMetadata.getBytes(StandardCharsets.UTF_8));
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
                currentXmlFile = file;
                showContent();  // Show content when XML file is loaded
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
                showContent();  // Show content when XML is pasted
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
        // Feature not yet implemented - button is disabled in initializeUI()
        logger.debug("Batch processing requested but feature is not yet implemented");
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

    /**
     * Shows help dialog.
     */
    @FXML
    private void showHelp() {
        var features = java.util.List.of(
                new String[]{"bi-diagram-3", "Smart Type Inference", "Automatically detects and infers XSD types from your XML data"},
                new String[]{"bi-box-seam", "Complex Type Generation", "Generates complex types with proper structure and constraints"},
                new String[]{"bi-speedometer2", "Schema Optimization", "Optimizes generated schemas for readability and efficiency"},
                new String[]{"bi-file-earmark-bar-graph", "Analysis Reports", "Detailed reports about the generated schema structure"}
        );

        var shortcuts = java.util.List.of(
                new String[]{"F5", "Generate schema from XML"},
                new String[]{"Ctrl+D", "Add current file to favorites"},
                new String[]{"Ctrl+Shift+D", "Toggle favorites panel"},
                new String[]{"F1", "Show this help dialog"}
        );

        var helpDialog = DialogHelper.createHelpDialog(
                "Schema Generator - Help",
                "Schema Generator",
                "Generate XSD schemas from XML documents automatically",
                "bi-diagram-3",
                DialogHelper.HeaderTheme.SUCCESS,
                features,
                shortcuts
        );

        helpDialog.showAndWait();
    }

    /**
     * Applies the small icons setting from user preferences.
     * When enabled, toolbar buttons display in compact mode with smaller icons (14px) and no text labels.
     * When disabled, buttons show both icon and text (TOP display) with normal icon size (20px).
     */
    private void applySmallIconsSetting() {
        boolean useSmallIcons = propertiesService.isUseSmallIcons();
        logger.debug("Applying small icons setting to Schema Generator toolbar: {}", useSmallIcons);

        // Determine display mode and icon size
        javafx.scene.control.ContentDisplay displayMode = useSmallIcons
                ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                : javafx.scene.control.ContentDisplay.TOP;

        // Icon sizes: small = 14px, normal = 20px
        int iconSize = useSmallIcons ? 14 : 20;

        // Button style: compact padding for small icons
        String buttonStyle = useSmallIcons
                ? "-fx-padding: 4px;"
                : "";

        // Apply to all toolbar buttons
        applyButtonSettings(loadXmlBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(pasteXmlBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(clearXmlBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(batchProcessBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(generateSchemaBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(copyXsdBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(exportSchemaBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(addToFavoritesBtn, displayMode, iconSize, buttonStyle);
        applyButtonSettings(toggleFavoritesButton, displayMode, iconSize, buttonStyle);
        applyButtonSettings(helpBtn, displayMode, iconSize, buttonStyle);

        logger.info("Small icons setting applied to Schema Generator toolbar (size: {}px)", iconSize);
    }

    /**
     * Helper method to apply display mode, icon size, and style to a button.
     */
    private void applyButtonSettings(javafx.scene.control.ButtonBase button,
                                     javafx.scene.control.ContentDisplay displayMode,
                                     int iconSize,
                                     String style) {
        if (button == null) return;

        // Set content display mode
        button.setContentDisplay(displayMode);

        // Apply compact style
        button.setStyle(style);

        // Update icon size if the button has a FontIcon graphic
        if (button.getGraphic() instanceof org.kordamp.ikonli.javafx.FontIcon fontIcon) {
            fontIcon.setIconSize(iconSize);
        }
    }

    /**
     * Public method to refresh toolbar icons.
     * Can be called from Settings or MainController when icon size preference changes.
     */
    public void refreshToolbarIcons() {
        applySmallIconsSetting();
    }

}
