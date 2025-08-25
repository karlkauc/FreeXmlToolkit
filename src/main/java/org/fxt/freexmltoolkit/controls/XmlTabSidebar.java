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

package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab-specific sidebar for XML editors.
 * Each XML tab has its own instance with file-specific information.
 */
public class XmlTabSidebar extends ScrollPane {
    private static final Logger logger = LogManager.getLogger(XmlTabSidebar.class);
    
    // Services
    private final XmlService xmlService = XmlServiceImpl.getInstance();
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("XmlTabSidebar-Thread");
        return t;
    });
    
    // Parent XML Editor reference
    private XmlEditor parentEditor;
    private File currentXmlFile;
    
    // UI Components
    private VBox mainContainer;
    
    // Document Tree
    private TitledPane documentTreePane;
    private TreeView<String> documentTreeView;
    
    // XSD Schema Section
    private TitledPane xsdSchemaPane;
    private TextField xsdPathField;
    private Button changeXsdButton;
    private Label validationStatusLabel;
    private CheckBox continuousValidationCheckBox;
    
    // Validation Errors
    private TitledPane validationErrorsPane;
    private Label validationErrorsCountLabel;
    private ListView<String> validationErrorsListView;
    
    // Properties
    private TitledPane propertiesPane;
    private TableView<PropertyEntry> propertiesTable;
    private TableColumn<PropertyEntry, String> propertyNameColumn;
    private TableColumn<PropertyEntry, String> propertyValueColumn;
    
    // Namespaces
    private TitledPane namespacesPane;
    private ListView<String> namespacesList;
    
    public XmlTabSidebar(XmlEditor parentEditor) {
        this.parentEditor = parentEditor;
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }
    
    private void initializeComponents() {
        mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(10));
        
        // Document Tree
        documentTreePane = new TitledPane("Document Structure", null);
        documentTreePane.setExpanded(true);
        documentTreePane.setGraphic(new FontIcon("bi-diagram-3"));
        documentTreeView = new TreeView<>();
        documentTreePane.setContent(documentTreeView);
        
        // XSD Schema Section
        xsdSchemaPane = new TitledPane("XSD Schema", null);
        xsdSchemaPane.setExpanded(true);
        xsdSchemaPane.setGraphic(new FontIcon("bi-file-earmark-check"));
        
        VBox xsdContent = new VBox(5);
        HBox xsdPathBox = new HBox(5);
        xsdPathField = new TextField();
        xsdPathField.setPromptText("XSD Schema");
        xsdPathField.setEditable(false);
        HBox.setHgrow(xsdPathField, javafx.scene.layout.Priority.ALWAYS);
        
        changeXsdButton = new Button("...");
        changeXsdButton.setOnAction(e -> changeXsd());
        
        xsdPathBox.getChildren().addAll(xsdPathField, changeXsdButton);
        
        validationStatusLabel = new Label("Validation status: Unknown");
        validationStatusLabel.setWrapText(true);
        
        continuousValidationCheckBox = new CheckBox("Continuous validation");
        continuousValidationCheckBox.setOnAction(e -> onContinuousValidationChanged());
        
        xsdContent.getChildren().addAll(xsdPathBox, validationStatusLabel, continuousValidationCheckBox);
        xsdSchemaPane.setContent(xsdContent);
        
        // Validation Errors
        validationErrorsPane = new TitledPane("Validation Errors", null);
        validationErrorsPane.setExpanded(true);
        validationErrorsPane.setGraphic(new FontIcon("bi-exclamation-triangle"));
        
        VBox validationContent = new VBox(5);
        validationErrorsCountLabel = new Label("Errors: 0");
        validationErrorsListView = new ListView<>();
        validationErrorsListView.setPrefHeight(100);
        
        validationContent.getChildren().addAll(validationErrorsCountLabel, validationErrorsListView);
        validationErrorsPane.setContent(validationContent);
        
        // Properties
        propertiesPane = new TitledPane("Properties", null);
        propertiesPane.setExpanded(false);
        propertiesPane.setGraphic(new FontIcon("bi-list-ul"));
        
        propertiesTable = new TableView<>();
        propertyNameColumn = new TableColumn<>("Property");
        propertyValueColumn = new TableColumn<>("Value");
        
        propertyNameColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));
        propertyValueColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getValue()));
            
        propertiesTable.getColumns().addAll(propertyNameColumn, propertyValueColumn);
        propertiesPane.setContent(propertiesTable);
        
        // Namespaces
        namespacesPane = new TitledPane("Namespaces", null);
        namespacesPane.setExpanded(false);
        namespacesPane.setGraphic(new FontIcon("bi-tags"));
        
        namespacesList = new ListView<>();
        namespacesList.setPrefHeight(80);
        namespacesPane.setContent(namespacesList);
    }
    
    private void setupLayout() {
        mainContainer.getChildren().addAll(
            documentTreePane,
            xsdSchemaPane,
            validationErrorsPane,
            propertiesPane,
            namespacesPane
        );
        
        this.setContent(mainContainer);
        this.setFitToWidth(true);
        this.setPrefWidth(300);
        this.setMinWidth(250);
    }
    
    private void setupEventHandlers() {
        // Setup text change listener on parent editor's codeArea
        if (parentEditor != null && parentEditor.codeArea != null) {
            parentEditor.codeArea.textProperty().addListener((obs, oldText, newText) -> {
                if (continuousValidationCheckBox.isSelected()) {
                    validateCurrentXml(newText);
                }
                updateDocumentTree(newText);
            });
        }
    }
    
    /**
     * Update this sidebar with information for the specified XML file
     */
    public void updateForFile(File xmlFile, String xmlContent) {
        this.currentXmlFile = xmlFile;
        
        // Auto-detect XSD schema
        autoDetectAndLoadXsdSchema();
        
        // Update document tree
        updateDocumentTree(xmlContent);
        
        // Validate XML
        validateCurrentXml(xmlContent);
        
        // Update properties
        updateProperties();
        
        logger.info("Sidebar updated for file: {}", xmlFile != null ? xmlFile.getName() : "Untitled");
    }
    
    /**
     * Auto-detect XSD schema from XML content
     */
    private void autoDetectAndLoadXsdSchema() {
        if (currentXmlFile == null) return;
        
        Task<Optional<String>> schemaDetectionTask = new Task<>() {
            @Override
            protected Optional<String> call() throws Exception {
                xmlService.setCurrentXmlFile(currentXmlFile);
                return xmlService.getSchemaNameFromCurrentXMLFile();
            }
        };
        
        schemaDetectionTask.setOnSucceeded(e -> {
            Optional<String> schemaLocation = schemaDetectionTask.getValue();
            if (schemaLocation.isPresent()) {
                String schemaPath = schemaLocation.get();
                logger.info("Auto-detected XSD schema: {}", schemaPath);
                
                // Convert file:// URLs to local paths
                if (schemaPath.startsWith("file://")) {
                    schemaPath = schemaPath.substring(7);
                }
                
                final String finalSchemaPath = schemaPath;
                Platform.runLater(() -> {
                    xsdPathField.setText(finalSchemaPath);
                    
                    File xsdFile = new File(finalSchemaPath);
                    if (xsdFile.exists()) {
                        validationStatusLabel.setText("XSD loaded: " + xsdFile.getName());
                        validationStatusLabel.setStyle("-fx-text-fill: green;");
                    } else {
                        validationStatusLabel.setText("XSD not found: " + xsdFile.getName());
                        validationStatusLabel.setStyle("-fx-text-fill: red;");
                    }
                });
            } else {
                Platform.runLater(() -> {
                    xsdPathField.setText("");
                    validationStatusLabel.setText("No XSD schema detected");
                    validationStatusLabel.setStyle("-fx-text-fill: orange;");
                });
            }
        });
        
        schemaDetectionTask.setOnFailed(e -> {
            logger.error("Schema detection failed", schemaDetectionTask.getException());
            Platform.runLater(() -> {
                validationStatusLabel.setText("Schema detection failed");
                validationStatusLabel.setStyle("-fx-text-fill: red;");
            });
        });
        
        executorService.submit(schemaDetectionTask);
    }
    
    /**
     * Validate current XML content
     */
    private void validateCurrentXml(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) return;
        
        Task<List<String>> validationTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> errors = new ArrayList<>();
                
                String currentXsdPath = xsdPathField.getText();
                if (currentXsdPath != null && !currentXsdPath.isEmpty()) {
                    File xsdFile = new File(currentXsdPath);
                    if (xsdFile.exists()) {
                        List<SAXParseException> validationResult = xmlService.validateText(xmlContent, xsdFile);
                        if (validationResult.isEmpty()) {
                            Platform.runLater(() -> updateValidationStatus(true, "XSD validation successful"));
                        } else {
                            for (var exception : validationResult) {
                                String errorMsg = "Line " + exception.getLineNumber() + ": " + exception.getMessage();
                                errors.add(errorMsg);
                            }
                            Platform.runLater(() -> updateValidationStatus(false, validationResult.size() + " XSD validation errors"));
                        }
                    }
                }
                
                return errors;
            }
        };
        
        validationTask.setOnSucceeded(e -> {
            List<String> errors = validationTask.getValue();
            Platform.runLater(() -> updateValidationErrors(errors));
        });
        
        validationTask.setOnFailed(e -> {
            logger.error("Validation task failed", validationTask.getException());
        });
        
        executorService.submit(validationTask);
    }
    
    /**
     * Update document tree
     */
    private void updateDocumentTree(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            documentTreeView.setRoot(null);
            return;
        }
        
        Task<TreeItem<String>> treeTask = new Task<>() {
            @Override
            protected TreeItem<String> call() throws Exception {
                // Simple XML tree parsing
                TreeItem<String> root = new TreeItem<>("Document");
                root.setExpanded(true);
                
                // Basic tree structure - can be enhanced
                if (xmlContent.contains("<")) {
                    TreeItem<String> xmlNode = new TreeItem<>("XML Content");
                    root.getChildren().add(xmlNode);
                }
                
                return root;
            }
        };
        
        treeTask.setOnSucceeded(e -> {
            Platform.runLater(() -> documentTreeView.setRoot(treeTask.getValue()));
        });
        
        executorService.submit(treeTask);
    }
    
    /**
     * Update validation status
     */
    private void updateValidationStatus(boolean isValid, String message) {
        validationStatusLabel.setText(message);
        validationStatusLabel.setStyle(isValid ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
    }
    
    /**
     * Update validation errors list
     */
    private void updateValidationErrors(List<String> errors) {
        validationErrorsListView.setItems(FXCollections.observableArrayList(errors));
        validationErrorsCountLabel.setText("Errors: " + errors.size());
    }
    
    /**
     * Update properties table
     */
    private void updateProperties() {
        List<PropertyEntry> properties = new ArrayList<>();
        
        if (currentXmlFile != null) {
            properties.add(new PropertyEntry("File Name", currentXmlFile.getName()));
            properties.add(new PropertyEntry("File Path", currentXmlFile.getAbsolutePath()));
            properties.add(new PropertyEntry("File Size", String.valueOf(currentXmlFile.length()) + " bytes"));
        }
        
        propertiesTable.setItems(FXCollections.observableArrayList(properties));
    }
    
    /**
     * Handle manual XSD file selection
     */
    private void changeXsd() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select XSD Schema File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSD Schema Files", "*.xsd"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (file != null && file.exists()) {
            xsdPathField.setText(file.getAbsolutePath());
            
            validationStatusLabel.setText("XSD loaded: " + file.getName());
            validationStatusLabel.setStyle("-fx-text-fill: green;");
            
            // Re-validate with the new schema
            if (parentEditor != null && parentEditor.codeArea != null) {
                validateCurrentXml(parentEditor.codeArea.getText());
            }
        }
    }
    
    /**
     * Handle continuous validation toggle
     */
    private void onContinuousValidationChanged() {
        boolean enabled = continuousValidationCheckBox.isSelected();
        logger.info("Continuous validation {} for tab {}", 
                   enabled ? "enabled" : "disabled", 
                   parentEditor != null ? parentEditor.getText() : "unknown");
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    
    /**
     * Property entry for the properties table
     */
    public static class PropertyEntry {
        private final String name;
        private final String value;
        
        public PropertyEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public String getValue() { return value; }
    }
}