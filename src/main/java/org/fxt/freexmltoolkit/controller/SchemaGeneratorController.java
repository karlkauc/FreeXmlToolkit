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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Controller for the Intelligent Schema Generator - Revolutionary Feature #3.
 * Auto-generates XSD schemas with advanced type inference and optimization.
 *
 * <p>This controller manages the schema generation workflow including:
 * <ul>
 *   <li>Loading XML files for analysis</li>
 *   <li>Configuring type inference and optimization options</li>
 *   <li>Generating XSD schemas from XML input</li>
 *   <li>Exporting generated schemas to files</li>
 * </ul>
 *
 * @see SchemaGenerationEngine
 * @see SchemaGenerationOptions
 */
public class SchemaGeneratorController implements FavoritesParentController {

    /**
     * Default constructor for SchemaGeneratorController.
     * Initializes the controller with default settings for schema generation.
     */
    public SchemaGeneratorController() {
        // Default constructor - initialization done in initialize() method
    }
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
    private List<TypeDefinition> allTypeDefinitions = new ArrayList<>();

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

    /**
     * Loads a file from the favorites list into the XML input area.
     *
     * <p>This method is called when a user selects a file from the favorites panel.
     * The file content is loaded into the input text area for schema generation.
     *
     * @param file the file to load from favorites
     */
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

    /**
     * Returns the currently loaded XML file.
     *
     * @return the current XML file, or null if no file is loaded
     */
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

    /**
     * Generates an XSD schema from the XML content in the input area.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Validates that XML content is present in the input area</li>
     *   <li>Builds generation options from the current UI settings</li>
     *   <li>Executes schema generation asynchronously in a background thread</li>
     *   <li>Displays results in the output area upon completion</li>
     * </ol>
     *
     * <p>The generation progress is shown via a progress bar, and the generate button
     * is disabled during processing to prevent concurrent operations.
     */
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

        // Populate type definitions table
        populateTypeDefinitions(result);
    }

    private void populateTypeDefinitions(SchemaGenerationResult result) {
        allTypeDefinitions.clear();

        if (result == null || result.getAnalysisResult() == null) {
            if (typeDefinitionsTable != null) {
                typeDefinitionsTable.setItems(FXCollections.observableArrayList());
            }
            return;
        }

        var analysisResult = result.getAnalysisResult();

        // Add complex types
        Set<String> complexTypes = analysisResult.getComplexTypes();
        for (String typeName : complexTypes) {
            allTypeDefinitions.add(new TypeDefinition(
                    typeName,
                    "Complex Type",
                    "xs:complexType",
                    0, // Usage count not tracked
                    "Complex type with child elements"
            ));
        }

        // Add simple types
        Set<String> simpleTypes = analysisResult.getSimpleTypes();
        for (String typeName : simpleTypes) {
            String inferredType = analysisResult.getInferredTypes().getOrDefault(typeName, "xs:string");
            allTypeDefinitions.add(new TypeDefinition(
                    typeName,
                    "Simple Type",
                    inferredType,
                    0,
                    "Simple type with text content"
            ));
        }

        // Add elements from allElements
        var allElements = analysisResult.getAllElements();
        for (var entry : allElements.entrySet()) {
            var elementInfo = entry.getValue();
            // Only add if not already in complex/simple types
            if (!complexTypes.contains(entry.getKey()) && !simpleTypes.contains(entry.getKey())) {
                allTypeDefinitions.add(new TypeDefinition(
                        elementInfo.getName(),
                        "Element",
                        elementInfo.getInferredType() != null ? elementInfo.getInferredType() : "xs:string",
                        elementInfo.getTotalOccurrences(),
                        elementInfo.isComplexType() ? "Element with children" : "Leaf element"
                ));
            }
        }

        // Add attributes
        var allAttributes = analysisResult.getAllAttributes();
        for (var entry : allAttributes.entrySet()) {
            var attrInfo = entry.getValue();
            allTypeDefinitions.add(new TypeDefinition(
                    attrInfo.getName(),
                    "Attribute",
                    attrInfo.getInferredType() != null ? attrInfo.getInferredType() : "xs:string",
                    attrInfo.getTotalOccurrences(),
                    attrInfo.isRequired() ? "Required attribute" : "Optional attribute"
            ));
        }

        // Apply current filter
        filterTypeDefinitions();
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
        if (typeDefinitionsTable == null) {
            return;
        }

        String selectedFilter = typeFilterCombo != null ? typeFilterCombo.getValue() : "All Types";
        String searchText = typeSearchField != null ? typeSearchField.getText().toLowerCase().trim() : "";

        List<TypeDefinition> filteredList = allTypeDefinitions.stream()
                .filter(typeDef -> {
                    // Apply type filter
                    if (selectedFilter != null && !selectedFilter.equals("All Types")) {
                        String kind = typeDef.getKind();
                        boolean matchesFilter = switch (selectedFilter) {
                            case "Complex Types" -> "Complex Type".equals(kind);
                            case "Simple Types" -> "Simple Type".equals(kind);
                            case "Elements" -> "Element".equals(kind);
                            case "Attributes" -> "Attribute".equals(kind);
                            default -> true;
                        };
                        if (!matchesFilter) {
                            return false;
                        }
                    }

                    // Apply search text filter
                    if (!searchText.isEmpty()) {
                        return typeDef.getName().toLowerCase().contains(searchText) ||
                               (typeDef.getBaseType() != null && typeDef.getBaseType().toLowerCase().contains(searchText)) ||
                               (typeDef.getDescription() != null && typeDef.getDescription().toLowerCase().contains(searchText));
                    }

                    return true;
                })
                .collect(Collectors.toList());

        typeDefinitionsTable.setItems(FXCollections.observableArrayList(filteredList));
        logger.debug("Type definitions filtered: {} of {} shown", filteredList.size(), allTypeDefinitions.size());
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

    /**
     * Represents a type definition entry for display in the type definitions table.
     *
     * <p>This class encapsulates information about XSD types (complex or simple)
     * that are generated during schema creation, including their name, kind,
     * base type, usage count, and description.
     */
    public static class TypeDefinition {

        /** The name of the type definition. */
        private String name;

        /** The kind of type (e.g., "complexType", "simpleType"). */
        private String kind;

        /** The base type from which this type is derived, if any. */
        private String baseType;

        /** The number of times this type is referenced in the schema. */
        private int usageCount;

        /** A human-readable description of the type. */
        private String description;

        /**
         * Constructs a new TypeDefinition with the specified properties.
         *
         * @param name        the name of the type definition
         * @param kind        the kind of type (e.g., "complexType", "simpleType")
         * @param baseType    the base type from which this type is derived, or null if none
         * @param usageCount  the number of times this type is referenced in the schema
         * @param description a human-readable description of the type
         */
        public TypeDefinition(String name, String kind, String baseType, int usageCount, String description) {
            this.name = name;
            this.kind = kind;
            this.baseType = baseType;
            this.usageCount = usageCount;
            this.description = description;
        }

        /**
         * Returns the name of this type definition.
         *
         * @return the type name
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the name of this type definition.
         *
         * @param name the new type name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Returns the kind of this type (e.g., "complexType", "simpleType").
         *
         * @return the type kind
         */
        public String getKind() {
            return kind;
        }

        /**
         * Sets the kind of this type.
         *
         * @param kind the new type kind
         */
        public void setKind(String kind) {
            this.kind = kind;
        }

        /**
         * Returns the base type from which this type is derived.
         *
         * @return the base type name, or null if this type has no base type
         */
        public String getBaseType() {
            return baseType;
        }

        /**
         * Sets the base type from which this type is derived.
         *
         * @param baseType the new base type name
         */
        public void setBaseType(String baseType) {
            this.baseType = baseType;
        }

        /**
         * Returns the number of times this type is referenced in the schema.
         *
         * @return the usage count
         */
        public int getUsageCount() {
            return usageCount;
        }

        /**
         * Sets the number of times this type is referenced in the schema.
         *
         * @param usageCount the new usage count
         */
        public void setUsageCount(int usageCount) {
            this.usageCount = usageCount;
        }

        /**
         * Returns the human-readable description of this type.
         *
         * @return the type description
         */
        public String getDescription() {
            return description;
        }

        /**
         * Sets the human-readable description of this type.
         *
         * @param description the new type description
         */
        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * Shuts down the controller and releases resources.
     *
     * <p>This method should be called when the controller is no longer needed,
     * typically during application shutdown. It gracefully terminates the
     * background executor service used for schema generation tasks.
     */
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
