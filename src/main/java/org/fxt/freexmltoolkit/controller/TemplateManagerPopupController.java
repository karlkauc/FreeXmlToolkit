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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.DefaultStringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.TemplateEngine;
import org.fxt.freexmltoolkit.service.TemplateRepository;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the Template Manager Popup
 */
public class TemplateManagerPopupController implements Initializable {
    private static final Logger logger = LogManager.getLogger(TemplateManagerPopupController.class);

    // Services
    private final XmlContentProvider contentProvider;
    private final TemplateEngine templateEngine;
    private final TemplateRepository templateRepository;

    // UI Components
    @FXML
    private ComboBox<String> templateCategoryCombo;
    @FXML
    private Button refreshTemplatesButton;
    @FXML
    private Button createNewTemplateButton;
    @FXML
    private ListView<XmlTemplate> templatesListView;
    @FXML
    private Label templateNameLabel;
    @FXML
    private Label templateDescriptionLabel;
    @FXML
    private Label templateCategoryLabel;
    @FXML
    private Label templateParameterCountLabel;
    @FXML
    private Button addParameterButton;
    @FXML
    private TableView<TemplateParameter> templateParametersTable;
    @FXML
    private TableColumn<TemplateParameter, String> parameterNameColumn;
    @FXML
    private TableColumn<TemplateParameter, String> parameterValueColumn;
    @FXML
    private TableColumn<TemplateParameter, String> parameterTypeColumn;
    @FXML
    private Button generatePreviewButton;
    @FXML
    private TextArea templatePreviewArea;
    @FXML
    private Button validateTemplateButton;
    @FXML
    private Button applyTemplateButton;
    @FXML
    private Button insertTemplateButton;
    @FXML
    private Button cancelButton;

    // State
    private XmlTemplate selectedTemplate;
    private final Map<String, String> currentTemplateParams = new HashMap<>();

    public TemplateManagerPopupController(XmlContentProvider contentProvider,
                                          TemplateEngine templateEngine,
                                          TemplateRepository templateRepository) {
        this.contentProvider = contentProvider;
        this.templateEngine = templateEngine;
        this.templateRepository = templateRepository;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing Template Manager Popup");
        initializeComboBoxes();
        initializeTables();
        initializeListView();
        loadTemplates();
        setupEventHandlers();
    }

    private void initializeComboBoxes() {
        templateCategoryCombo.setItems(FXCollections.observableArrayList(
                "All Templates", "Finance", "Healthcare", "Automotive",
                "Government", "Generic", "Web Services", "Configuration"
        ));
        templateCategoryCombo.setValue("All Templates");
    }

    private void initializeTables() {
        parameterNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        parameterValueColumn.setCellValueFactory(new PropertyValueFactory<>("defaultValue"));
        parameterTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));

        parameterValueColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
        parameterValueColumn.setOnEditCommit(event -> {
            TemplateParameter param = event.getRowValue();
            param.setDefaultValue(event.getNewValue());
            currentTemplateParams.put(param.getName(), event.getNewValue());
            generatePreview();
        });
    }

    private void initializeListView() {
        templatesListView.setCellFactory(lv -> new ListCell<XmlTemplate>() {
            @Override
            protected void updateItem(XmlTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " - " + item.getDescription());
            }
        });
    }

    private void setupEventHandlers() {
        cancelButton.setOnAction(e -> closePopup());

        templatesListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTemplate, newTemplate) -> {
                    if (newTemplate != null) {
                        selectTemplate(newTemplate);
                    }
                }
        );

        // Additional button event handlers
        createNewTemplateButton.setOnAction(e -> createNewTemplate());
        addParameterButton.setOnAction(e -> addParameter());
        generatePreviewButton.setOnAction(e -> generatePreview());
        validateTemplateButton.setOnAction(e -> validateTemplate());
        applyTemplateButton.setOnAction(e -> applyTemplate());
        insertTemplateButton.setOnAction(e -> insertTemplate());
    }

    @FXML
    private void refreshTemplates() {
        logger.info("Refreshing templates");
        loadTemplates();
    }

    @FXML
    private void onTemplateSelected() {
        XmlTemplate template = templatesListView.getSelectionModel().getSelectedItem();
        if (template != null) {
            selectTemplate(template);
        }
    }

    @FXML
    private void addParameter() {
        if (selectedTemplate != null) {
            TextInputDialog dialog = new TextInputDialog("newParam");
            dialog.setTitle("Add Parameter");
            dialog.setHeaderText("Add Template Parameter");
            dialog.setContentText("Parameter name:");

            dialog.showAndWait().ifPresent(name -> {
                TemplateParameter newParam = TemplateParameter.stringParam(name, "");
                selectedTemplate.addParameter(newParam);
                templateParametersTable.getItems().add(newParam);
                currentTemplateParams.put(name, "");
                updateTemplateInfo();
            });
        }
    }

    @FXML
    private void generatePreview() {
        if (selectedTemplate != null) {
            try {
                String generatedXml = selectedTemplate.processTemplate(currentTemplateParams);
                templatePreviewArea.setText(generatedXml);
            } catch (Exception e) {
                templatePreviewArea.setText("Error generating preview: " + e.getMessage());
                logger.error("Failed to generate template preview", e);
            }
        }
    }

    @FXML
    private void validateTemplate() {
        if (selectedTemplate != null) {
            try {
                // Validate template parameters
                boolean valid = true;
                StringBuilder errors = new StringBuilder();

                for (TemplateParameter param : selectedTemplate.getParameters()) {
                    String value = currentTemplateParams.get(param.getName());

                    if (param.isRequired() && (value == null || value.isEmpty())) {
                        valid = false;
                        errors.append("Parameter '").append(param.getName()).append("' is required\n");
                    }

                    if (param.getValidationPattern() != null && value != null &&
                            !value.matches(param.getValidationPattern())) {
                        valid = false;
                        errors.append("Parameter '").append(param.getName())
                                .append("' does not match pattern: ").append(param.getValidationPattern()).append("\n");
                    }
                }

                if (valid) {
                    showInfo("Validation Success", "All template parameters are valid");
                } else {
                    showError("Validation Failed", errors.toString());
                }
            } catch (Exception e) {
                showError("Validation Error", "Could not validate template: " + e.getMessage());
                logger.error("Failed to validate template", e);
            }
        }
    }

    @FXML
    private void applyTemplate() {
        if (selectedTemplate != null) {
            try {
                String generatedXml = selectedTemplate.processTemplate(currentTemplateParams);
                contentProvider.setCurrentXmlContent(generatedXml);
                showInfo("Template Applied", "Template has been applied to the current editor");
                closePopup();
            } catch (Exception e) {
                showError("Template Error", "Could not apply template: " + e.getMessage());
                logger.error("Failed to apply template", e);
            }
        }
    }

    @FXML
    private void insertTemplate() {
        if (selectedTemplate != null) {
            try {
                String generatedXml = selectedTemplate.processTemplate(currentTemplateParams);
                contentProvider.insertXmlContent("\n\n" + generatedXml);
                showInfo("Template Inserted", "Template has been inserted at cursor position");
                closePopup();
            } catch (Exception e) {
                showError("Template Error", "Could not insert template: " + e.getMessage());
                logger.error("Failed to insert template", e);
            }
        }
    }

    private void loadTemplates() {
        ObservableList<XmlTemplate> templates = FXCollections.observableArrayList(
                templateRepository.getAllTemplates()
        );
        templatesListView.setItems(templates);

        // Select first template if available
        if (!templates.isEmpty()) {
            templatesListView.getSelectionModel().selectFirst();
        }
    }

    private void selectTemplate(XmlTemplate template) {
        selectedTemplate = template;

        // Update template info
        updateTemplateInfo();

        // Load template parameters
        templateParametersTable.setItems(FXCollections.observableArrayList(template.getParameters()));

        // Initialize parameter values
        currentTemplateParams.clear();
        for (TemplateParameter param : template.getParameters()) {
            String defaultValue = param.getDefaultValue();
            if (defaultValue == null || defaultValue.isEmpty()) {
                defaultValue = param.isRequired() ? "REQUIRED_VALUE" : "";
            }
            currentTemplateParams.put(param.getName(), defaultValue);
        }

        // Generate initial preview
        generatePreview();
    }

    private void updateTemplateInfo() {
        if (selectedTemplate != null) {
            templateNameLabel.setText(selectedTemplate.getName());
            templateDescriptionLabel.setText(selectedTemplate.getDescription());
            templateCategoryLabel.setText(selectedTemplate.getCategory());
            templateParameterCountLabel.setText(String.valueOf(selectedTemplate.getParameters().size()));
        } else {
            templateNameLabel.setText("No template selected");
            templateDescriptionLabel.setText("");
            templateCategoryLabel.setText("");
            templateParameterCountLabel.setText("0");
        }
    }

    /**
     * Create new template functionality
     */
    @FXML
    private void createNewTemplate() {
        logger.info("Creating new template");

        // Create dialog for new template creation
        Dialog<XmlTemplate> dialog = new Dialog<>();
        dialog.setTitle("Create New Template");
        dialog.setHeaderText("Create a new XML template");

        // Set dialog pane
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Create form fields
        VBox formContent = new VBox(10);
        formContent.setPadding(new javafx.geometry.Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Template name");

        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Template description");

        ComboBox<String> categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().addAll("Finance", "Healthcare", "Automotive", "Government", "Generic", "Web Services", "Configuration");
        categoryComboBox.setPromptText("Select category");

        TextArea templateContentArea = new TextArea();
        templateContentArea.setPromptText("Enter XML template content...");
        templateContentArea.setPrefRowCount(10);

        formContent.getChildren().addAll(
                new Label("Template Name:"), nameField,
                new Label("Description:"), descriptionField,
                new Label("Category:"), categoryComboBox,
                new Label("Template Content:"), templateContentArea
        );

        dialogPane.setContent(formContent);

        // Convert result to template when OK is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String name = nameField.getText().trim();
                String description = descriptionField.getText().trim();
                String category = categoryComboBox.getValue();
                String content = templateContentArea.getText().trim();

                if (name.isEmpty() || content.isEmpty()) {
                    showError("Validation Error", "Template name and content are required");
                    return null;
                }

                try {
                    // Create new template using the correct constructor (name, content, category)
                    XmlTemplate newTemplate = new XmlTemplate(name, content, category != null ? category : "Generic");
                    if (description != null && !description.isEmpty()) {
                        newTemplate.setDescription(description);
                    }

                    // Add to repository
                    templateRepository.addTemplate(newTemplate);

                    return newTemplate;
                } catch (Exception e) {
                    showError("Template Creation Error", "Could not create template: " + e.getMessage());
                    logger.error("Failed to create template", e);
                    return null;
                }
            }
            return null;
        });

        // Show dialog and handle result
        dialog.showAndWait().ifPresent(newTemplate -> {
            loadTemplates(); // Refresh template list
            // Select the new template
            templatesListView.getSelectionModel().select(newTemplate);
            showInfo("Template Created", "Template '" + newTemplate.getName() + "' has been created successfully");
        });
    }

    private void closePopup() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("Template Manager");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Template Manager Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}