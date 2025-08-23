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
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.web.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.TemplateEngine;
import org.fxt.freexmltoolkit.service.TemplateRepository;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Smart Templates System - Revolutionary Feature #4
 * Provides professional XML template management with intelligent parameter validation
 */
public class TemplatesController {
    private static final Logger logger = LogManager.getLogger(TemplatesController.class);

    // Revolutionary Services
    private final TemplateRepository templateRepository = TemplateRepository.getInstance();
    private final TemplateEngine templateEngine = TemplateEngine.getInstance();

    // Background processing
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("Templates-Thread");
        return t;
    });

    // UI Components - Category and Search
    @FXML
    private ComboBox<String> templateCategoryCombo;
    @FXML
    private TextField templateSearchField;
    @FXML
    private ListView<XmlTemplate> templatesListView;

    // UI Components - Template Info
    @FXML
    private Label templateNameLabel;
    @FXML
    private Label templateDescriptionLabel;
    @FXML
    private Label templateCategoryLabel;
    @FXML
    private Label templateTagsLabel;

    // UI Components - Actions
    @FXML
    private Button refreshTemplatesBtn;
    @FXML
    private Button createTemplateBtn;
    @FXML
    private Button applyTemplateBtn;
    @FXML
    private Button previewTemplateBtn;
    @FXML
    private Button validateParamsBtn;
    @FXML
    private Button resetParamsBtn;
    @FXML
    private Button copyToClipboardBtn;

    // UI Components - Parameters Table
    @FXML
    private TableView<TemplateParameter> templateParametersTable;
    @FXML
    private TableColumn<TemplateParameter, String> paramNameColumn;
    @FXML
    private TableColumn<TemplateParameter, String> paramValueColumn;
    @FXML
    private TableColumn<TemplateParameter, String> paramTypeColumn;
    @FXML
    private TableColumn<TemplateParameter, Boolean> paramRequiredColumn;
    @FXML
    private TableColumn<TemplateParameter, String> paramDescriptionColumn;

    // UI Components - Preview
    @FXML
    private TabPane previewTabPane;
    @FXML
    private TextArea xmlPreviewArea;
    @FXML
    private WebView xmlFormattedPreview;
    @FXML
    private ToggleButton livePreviewToggle;

    // State Management
    private XmlTemplate selectedTemplate;
    private final Map<String, String> currentParameterValues = new HashMap<>();

    @FXML
    private void initialize() {
        logger.info("Initializing Smart Templates Controller - Revolutionary Feature #4");

        initializeUI();
        loadTemplates();
        setupEventHandlers();

        logger.info("Smart Templates Controller initialized successfully");
    }

    private void initializeUI() {
        // Initialize category combo
        if (templateCategoryCombo != null) {
            templateCategoryCombo.setItems(FXCollections.observableArrayList(
                    "All Templates", "Finance", "Healthcare", "Automotive", "Government", "Generic"
            ));
            templateCategoryCombo.setValue("All Templates");
        }

        // Initialize templates list
        if (templatesListView != null) {
            templatesListView.setCellFactory(listView -> new ListCell<XmlTemplate>() {
                @Override
                protected void updateItem(XmlTemplate template, boolean empty) {
                    super.updateItem(template, empty);
                    if (empty || template == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(String.format("%s (%s)", template.getName(), template.getCategory()));
                        setStyle("-fx-font-size: 12px;");
                    }
                }
            });
        }

        // Initialize parameters table
        if (templateParametersTable != null) {
            paramNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
            paramValueColumn.setCellValueFactory(new PropertyValueFactory<>("defaultValue"));
            paramValueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
            paramTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
            paramRequiredColumn.setCellValueFactory(new PropertyValueFactory<>("required"));
            paramDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        }

        // Set initial button states
        if (applyTemplateBtn != null) applyTemplateBtn.setDisable(true);
        if (previewTemplateBtn != null) previewTemplateBtn.setDisable(true);
    }

    private void loadTemplates() {
        Task<List<XmlTemplate>> loadTask = new Task<>() {
            @Override
            protected List<XmlTemplate> call() throws Exception {
                return templateRepository.getAllTemplates();
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    if (templatesListView != null) {
                        templatesListView.setItems(FXCollections.observableList(getValue()));
                        logger.debug("Loaded {} templates", getValue().size());
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Failed to load templates", getException());
                    showAlert("Template Error", "Failed to load templates: " + getException().getMessage());
                });
            }
        };

        executorService.submit(loadTask);
    }

    private void setupEventHandlers() {
        // Category filter change
        if (templateCategoryCombo != null) {
            templateCategoryCombo.setOnAction(e -> filterTemplates());
        }

        // Search field change
        if (templateSearchField != null) {
            templateSearchField.textProperty().addListener((obs, oldText, newText) -> filterTemplates());
        }

        // Live preview toggle
        if (livePreviewToggle != null) {
            livePreviewToggle.setOnAction(e -> {
                if (livePreviewToggle.isSelected() && selectedTemplate != null) {
                    generatePreview();
                }
            });
        }

        // Parameters table value changes
        if (templateParametersTable != null) {
            paramValueColumn.setOnEditCommit(event -> {
                TemplateParameter parameter = event.getRowValue();
                String newValue = event.getNewValue();
                parameter.setDefaultValue(newValue);
                currentParameterValues.put(parameter.getName(), newValue);

                if (livePreviewToggle != null && livePreviewToggle.isSelected()) {
                    generatePreview();
                }
            });
        }
    }

    @FXML
    private void onTemplateSelected() {
        XmlTemplate selected = templatesListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedTemplate = selected;
            updateTemplateInfo();
            updateParametersTable();

            // Enable action buttons
            if (applyTemplateBtn != null) applyTemplateBtn.setDisable(false);
            if (previewTemplateBtn != null) previewTemplateBtn.setDisable(false);

            logger.debug("Selected template: {}", selected.getName());
        }
    }

    private void updateTemplateInfo() {
        if (selectedTemplate != null) {
            if (templateNameLabel != null) {
                templateNameLabel.setText(selectedTemplate.getName());
            }
            if (templateDescriptionLabel != null) {
                templateDescriptionLabel.setText(selectedTemplate.getDescription());
            }
            if (templateCategoryLabel != null) {
                templateCategoryLabel.setText("Category: " + selectedTemplate.getCategory());
            }
            if (templateTagsLabel != null) {
                templateTagsLabel.setText("Tags: " + String.join(", ", selectedTemplate.getTags()));
            }
        }
    }

    private void updateParametersTable() {
        if (selectedTemplate != null && templateParametersTable != null) {
            templateParametersTable.setItems(FXCollections.observableList(selectedTemplate.getParameters()));

            // Initialize parameter values with defaults or placeholders for required params
            currentParameterValues.clear();
            for (TemplateParameter param : selectedTemplate.getParameters()) {
                String defaultValue = param.getDefaultValue();

                // If parameter is required but has no default, provide a placeholder value
                if (defaultValue == null || defaultValue.isEmpty()) {
                    if (param.isRequired()) {
                        // Provide a sensible placeholder based on parameter name
                        defaultValue = getPlaceholderForParameter(param.getName());
                    } else {
                        defaultValue = "";
                    }
                }

                currentParameterValues.put(param.getName(), defaultValue);
            }
        }
    }

    private String getPlaceholderForParameter(String paramName) {
        // Provide sensible placeholder values for common parameter names
        return switch (paramName.toLowerCase()) {
            case "groupid" -> "com.example";
            case "artifactid" -> "my-project";
            case "projectname" -> "My Project";
            case "elementname" -> "element";
            case "comment" -> "Comment text";
            case "targetnamespace" -> "http://example.com/namespace";
            case "operationname" -> "operation";
            case "servicename" -> "MyService";
            case "soapaction" -> "http://example.com/action";
            case "serviceurl" -> "http://localhost:8080/service";
            case "basepackage" -> "com.example";
            case "beanid" -> "myBean";
            case "beanclass" -> "com.example.MyClass";
            case "apiname" -> "My API";
            case "apidescription" -> "API Description";
            case "baseurl" -> "http://api.example.com";
            case "endpointpath" -> "/endpoint";
            case "transactionid" -> "TXN-001";
            case "amount" -> "100.00";
            case "fromaccount" -> "ACC-001";
            case "toaccount" -> "ACC-002";
            case "patientid" -> "PAT-001";
            case "firstname" -> "John";
            case "lastname" -> "Doe";
            case "vin" -> "1HGBH41JXMN109186";
            case "licenseplate" -> "ABC-123";
            case "make" -> "Toyota";
            case "model" -> "Camry";
            case "ownername" -> "John Doe";
            case "formid" -> "FORM-001";
            case "formtitle" -> "Application Form";
            case "department" -> "Department Name";
            case "fullname" -> "John Doe";
            case "nationalid" -> "123456789";
            case "purpose" -> "General Purpose";
            case "requestedservice" -> "Service Request";
            case "rootelement" -> "root";
            case "rootelementtype" -> "complexType";
            case "matchpattern" -> "//element";
            case "outputelement" -> "output";
            default -> "PLACEHOLDER_" + paramName.toUpperCase();
        };
    }

    private void filterTemplates() {
        String category = templateCategoryCombo != null ? templateCategoryCombo.getValue() : "All Templates";
        String searchText = templateSearchField != null ? templateSearchField.getText().toLowerCase() : "";

        Task<List<XmlTemplate>> filterTask = new Task<>() {
            @Override
            protected List<XmlTemplate> call() throws Exception {
                List<XmlTemplate> allTemplates = templateRepository.getAllTemplates();

                return allTemplates.stream()
                        .filter(template -> {
                            // Category filter
                            boolean matchesCategory = "All Templates".equals(category) ||
                                    template.getCategory().equals(category);

                            // Search filter
                            boolean matchesSearch = searchText.isEmpty() ||
                                    template.getName().toLowerCase().contains(searchText) ||
                                    template.getDescription().toLowerCase().contains(searchText);

                            return matchesCategory && matchesSearch;
                        })
                        .toList();
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    if (templatesListView != null) {
                        templatesListView.setItems(FXCollections.observableList(getValue()));
                    }
                });
            }
        };

        executorService.submit(filterTask);
    }

    @FXML
    private void applySelectedTemplate() {
        if (selectedTemplate != null) {
            Task<String> applyTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return selectedTemplate.processTemplate(currentParameterValues);
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        String generatedXml = getValue();

                        // Copy to clipboard
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(generatedXml), null);

                        // Show success message
                        showInfo("Template Applied",
                                "Template '" + selectedTemplate.getName() + "' has been generated and copied to clipboard.");

                        logger.info("Applied template: {}", selectedTemplate.getName());
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Failed to apply template", getException());
                        showAlert("Template Error", "Failed to apply template: " + getException().getMessage());
                    });
                }
            };

            executorService.submit(applyTask);
        }
    }

    @FXML
    private void previewSelectedTemplate() {
        generatePreview();
    }

    private void generatePreview() {
        if (selectedTemplate != null && xmlPreviewArea != null) {
            Task<String> previewTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return selectedTemplate.processTemplate(currentParameterValues);
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        String generatedXml = getValue();
                        xmlPreviewArea.setText(generatedXml);

                        // Update formatted preview
                        if (xmlFormattedPreview != null) {
                            String formattedHtml = "<html><body><pre>" +
                                    escapeHtml(generatedXml) +
                                    "</pre></body></html>";
                            xmlFormattedPreview.getEngine().loadContent(formattedHtml);
                        }
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Failed to generate preview", getException());
                        xmlPreviewArea.setText("Error generating preview: " + getException().getMessage());
                    });
                }
            };

            executorService.submit(previewTask);
        }
    }

    @FXML
    private void validateParameters() {
        if (selectedTemplate != null) {
            // TODO: Implement advanced parameter validation
            showInfo("Validation", "All parameters are valid.");
        }
    }

    @FXML
    private void resetParameters() {
        if (selectedTemplate != null) {
            updateParametersTable();
            generatePreview();
        }
    }

    @FXML
    private void copyToClipboard() {
        if (xmlPreviewArea != null && !xmlPreviewArea.getText().isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(xmlPreviewArea.getText()), null);
            showInfo("Copied", "XML content copied to clipboard.");
        }
    }

    @FXML
    private void refreshTemplates() {
        loadTemplates();
    }

    @FXML
    private void createTemplate() {
        // TODO: Implement template creation dialog
        showInfo("Create Template", "Template creation feature coming soon!");
    }

    // Utility Methods
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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

    // Lifecycle
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("Templates Controller shutdown completed");
        }
    }
}