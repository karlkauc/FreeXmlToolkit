package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.domain.ValidationError;
import org.fxt.freexmltoolkit.service.SchematronService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XmlEditorSidebarController {

    private static final Logger logger = LogManager.getLogger(XmlEditorSidebarController.class);

    @FXML
    private TextField xsdPathField;

    @FXML
    private Button changeXsdButton;

    @FXML
    private Label validationStatusLabel;

    @FXML
    private CheckBox continuousValidationCheckBox;

    @FXML
    private TextField xpathField;

    @FXML
    private TextField elementNameField;

    @FXML
    private TextField elementTypeField;

    @FXML
    private TextArea documentationTextArea;

    @FXML
    private ListView<String> exampleValuesListView;

    @FXML
    private ListView<String> childElementsListView;

    @FXML
    private TitledPane validationErrorsPane;

    @FXML
    private Label validationErrorsCountLabel;

    @FXML
    private ListView<ValidationError> validationErrorsListView;

    @FXML
    private TextField schematronPathField;

    @FXML
    private Button changeSchematronButton;

    @FXML
    private Label schematronValidationStatusLabel;

    @FXML
    private CheckBox continuousSchematronValidationCheckBox;

    @FXML
    private Button schematronDetailsButton;

    @FXML
    private Button toggleSidebarButton;

    @FXML
    private VBox sidebarContent;

    private XmlEditor xmlEditor;

    private String xsdPath;

    private boolean sidebarVisible = true;

    private org.fxt.freexmltoolkit.controller.MainController mainController;

    private VBox sidebarContainer; // Reference to the main container
    private double expandedWidth = 300; // Store the expanded width
    private final List<SchematronService.SchematronValidationError> currentSchematronErrors = new ArrayList<>();
    private final List<ValidationError> currentValidationErrors = new ArrayList<>();

    public void setXmlEditor(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
    }

    public void setMainController(org.fxt.freexmltoolkit.controller.MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void initialize() {
        changeXsdButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select XSD Schema");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

            // Set initial directory to the same as the XML file if available
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().getParentFile() != null) {
                fileChooser.setInitialDirectory(xmlEditor.getXmlFile().getParentFile());
            }
            
            File selectedFile = fileChooser.showOpenDialog(changeXsdButton.getScene().getWindow());
            if (selectedFile != null) {
                xsdPathField.setText(selectedFile.getAbsolutePath());
                if (xmlEditor != null) {
                    xmlEditor.setXsdFile(selectedFile);
                }
            }
        });

        changeSchematronButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Schematron Rules");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Schematron Files", "*.sch", "*.xslt", "*.xsl"));

            // Set initial directory to the same as the XML file if available
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().getParentFile() != null) {
                fileChooser.setInitialDirectory(xmlEditor.getXmlFile().getParentFile());
            }

            File selectedFile = fileChooser.showOpenDialog(changeSchematronButton.getScene().getWindow());
            if (selectedFile != null) {
                schematronPathField.setText(selectedFile.getAbsolutePath());
                if (xmlEditor != null) {
                    xmlEditor.setSchematronFile(selectedFile);
                }
            }
        });

        continuousValidationCheckBox.setOnAction(event -> {
            if (continuousValidationCheckBox.isSelected() && xmlEditor != null) {
                xmlEditor.validateXml();
            }
        });

        continuousSchematronValidationCheckBox.setOnAction(event -> {
            if (continuousSchematronValidationCheckBox.isSelected() && xmlEditor != null) {
                xmlEditor.validateSchematron();
            }
        });

        // Setup validation errors ListView with custom cell factory
        if (validationErrorsListView != null) {
            validationErrorsListView.setCellFactory(listView -> new ListCell<ValidationError>() {
                @Override
                protected void updateItem(ValidationError error, boolean empty) {
                    super.updateItem(error, empty);
                    if (empty || error == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(error.getDisplayText());
                        // Style based on severity
                        String style = "-fx-text-fill: ";
                        switch (error.severity().toUpperCase()) {
                            case "ERROR" -> style += "red";
                            case "WARNING" -> style += "orange";
                            case "INFO" -> style += "blue";
                            default -> style += "black";
                        }
                        setStyle(style + "; -fx-padding: 2 5 2 5;");
                    }
                }
            });

            // Add click handler for navigation to error position
            validationErrorsListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { // Double-click
                    ValidationError selectedError = validationErrorsListView.getSelectionModel().getSelectedItem();
                    if (selectedError != null && xmlEditor != null) {
                        navigateToError(selectedError);
                    }
                }
            });
        }
    }

    @FXML
    private void toggleSidebar() {
        // Use the global sidebar toggle functionality instead of local minimize/maximize
        if (mainController != null) {
            // Get the current state from the main controller's menu
            boolean currentlyVisible = mainController.isXmlEditorSidebarVisible();

            // Toggle the state through the MainController - this will:
            // 1. Update the menu CheckMenuItem
            // 2. Save the preference 
            // 3. Apply to all XML Editor tabs
            // 4. Completely hide/show the sidebar (not just minimize)
            mainController.toggleXmlEditorSidebarFromSidebar(!currentlyVisible);
        } else {
            // Alternative approach: Try to find the MainController through the Stage
            System.out.println("Warning: MainController not available directly - trying alternative approach");

            try {
                // Get the current stage and find MainController through the window
                javafx.stage.Window window = toggleSidebarButton.getScene().getWindow();
                if (window instanceof javafx.stage.Stage stage) {
                    Object userData = stage.getUserData();
                    // This is a fallback - in practice, we should ensure MainController is available
                    // For now, just use local toggle as fallback
                    toggleSidebarLocal();
                }
            } catch (Exception e) {
                System.out.println("Fallback failed - using local sidebar toggle: " + e.getMessage());
                toggleSidebarLocal();
            }
        }
    }

    /**
     * Fallback method for local sidebar toggle (original behavior)
     */
    private void toggleSidebarLocal() {
        sidebarVisible = !sidebarVisible;

        if (sidebarVisible) {
            // Expand sidebar
            sidebarContent.setVisible(true);
            sidebarContent.setManaged(true);

            if (sidebarContainer != null) {
                sidebarContainer.setPrefWidth(expandedWidth);
                sidebarContainer.setMinWidth(250);
                sidebarContainer.setMaxWidth(400);
            }

            // Reset toggle button width when expanded
            toggleSidebarButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            toggleSidebarButton.setMinWidth(Region.USE_COMPUTED_SIZE);

            toggleSidebarButton.setText("◀");
        } else {
            // Collapse sidebar - minimize to just the toggle button width
            sidebarContent.setVisible(false);
            sidebarContent.setManaged(false);

            if (sidebarContainer != null) {
                sidebarContainer.setPrefWidth(32); // Minimal width for just the toggle button
                sidebarContainer.setMinWidth(32);
                sidebarContainer.setMaxWidth(32);
            }

            // Make toggle button take minimal space when collapsed
            toggleSidebarButton.setPrefWidth(30);
            toggleSidebarButton.setMinWidth(30);

            toggleSidebarButton.setText("▶");
        }
    }

    public void setValidationStatus(String status) {
        validationStatusLabel.setText("Validation status: " + status);
    }

    public void setXPath(String xpath) {
        xpathField.setText(xpath);
    }

    public void setPossibleChildElements(java.util.List<String> childElements) {
        childElementsListView.getItems().setAll(childElements);
    }

    public boolean isContinuousValidationSelected() {
        return continuousValidationCheckBox.isSelected();
    }

    public void setXsdPath(String absolutePath) {
        this.xsdPath = absolutePath;
        if (xsdPathField != null) {
            xsdPathField.setText(absolutePath != null ? absolutePath : "No XSD schema selected");
        }
    }

    // Additional methods for better integration
    public void updateValidationStatus(String status, String color) {
        validationStatusLabel.setText("Validation status: " + status);
        validationStatusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    public void updateSchematronValidationStatus(String status, String color) {
        if (schematronValidationStatusLabel != null) {
            schematronValidationStatusLabel.setText("Schematron validation status: " + status);
            schematronValidationStatusLabel.setStyle("-fx-text-fill: " + color + ";");
        }
    }

    /**
     * Updates the Schematron validation status and stores the error details for the details button.
     *
     * @param status The status message to display
     * @param color  The color for the status text
     * @param errors List of detailed Schematron errors (can be null or empty)
     */
    public void updateSchematronValidationStatus(String status, String color, List<SchematronService.SchematronValidationError> errors) {
        updateSchematronValidationStatus(status, color);

        // Store the errors for the details view
        this.currentSchematronErrors.clear();
        if (errors != null) {
            this.currentSchematronErrors.addAll(errors);
        }

        // Show/hide the details button based on whether there are errors
        if (schematronDetailsButton != null) {
            boolean hasErrors = errors != null && !errors.isEmpty();
            schematronDetailsButton.setVisible(hasErrors);
        }
    }

    public void setXsdPathField(String path) {
        if (xsdPathField != null) {
            xsdPathField.setText(path != null ? path : "No XSD schema selected");
        }
    }

    public void setSchematronPathField(String path) {
        if (schematronPathField != null) {
            schematronPathField.setText(path != null ? path : "No Schematron rules selected");
        }
    }

    public void setContinuousValidation(boolean selected) {
        if (continuousValidationCheckBox != null) {
            continuousValidationCheckBox.setSelected(selected);
        }
    }

    public void setContinuousSchematronValidation(boolean selected) {
        if (continuousSchematronValidationCheckBox != null) {
            continuousSchematronValidationCheckBox.setSelected(selected);
        }
    }

    // Method to set the sidebar container reference from XmlEditor
    public void setSidebarContainer(VBox container) {
        this.sidebarContainer = container;
        if (container != null) {
            this.expandedWidth = container.getPrefWidth();
        }
    }

    public void setElementName(String elementName) {
        if (elementNameField != null) {
            elementNameField.setText(elementName != null ? elementName : "");
        }
    }

    public void setElementType(String elementType) {
        if (elementTypeField != null) {
            elementTypeField.setText(elementType != null ? elementType : "");
        }
    }

    public void setDocumentation(String documentation) {
        if (documentationTextArea != null) {
            documentationTextArea.setText(documentation != null ? documentation : "");
        }
    }

    public void setExampleValues(java.util.List<String> exampleValues) {
        if (exampleValuesListView != null) {
            if (exampleValues != null && !exampleValues.isEmpty()) {
                exampleValuesListView.getItems().setAll(exampleValues);
            } else {
                exampleValuesListView.getItems().setAll("No example values available");
            }
        }
    }

    public boolean isContinuousSchematronValidationSelected() {
        return continuousSchematronValidationCheckBox != null && continuousSchematronValidationCheckBox.isSelected();
    }

    @FXML
    private void showSchematronDetails() {
        if (currentSchematronErrors.isEmpty()) {
            return;
        }

        // Create a new stage for the detailed error view
        Stage detailStage = new Stage();
        detailStage.setTitle("Schematron Validation Errors");
        detailStage.setWidth(600);
        detailStage.setHeight(400);

        // Create the content
        VBox content = new VBox();
        content.setSpacing(10);
        content.setPadding(new Insets(10));

        Label titleLabel = new Label("Schematron Validation Errors (" + currentSchematronErrors.size() + " error(s)):");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Create a list view for the errors
        ListView<String> errorList = new ListView<>();
        for (int i = 0; i < currentSchematronErrors.size(); i++) {
            SchematronService.SchematronValidationError error = currentSchematronErrors.get(i);
            StringBuilder errorText = new StringBuilder();

            errorText.append((i + 1)).append(". ");

            if (error.lineNumber() > 0) {
                errorText.append("Line ").append(error.lineNumber());
                if (error.columnNumber() > 0) {
                    errorText.append(", Col ").append(error.columnNumber());
                }
                errorText.append(": ");
            }

            errorText.append(error.message());

            if (error.ruleId() != null && !error.ruleId().isEmpty()) {
                errorText.append("\n   Rule: ").append(error.ruleId());
            }

            if (error.context() != null && !error.context().isEmpty()) {
                errorText.append("\n   Context: ").append(error.context());
            }

            errorList.getItems().add(errorText.toString());
        }

        // Close button
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> detailStage.close());

        content.getChildren().addAll(titleLabel, errorList, closeButton);
        VBox.setVgrow(errorList, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(content);
        detailStage.setScene(scene);

        // Make it modal to the main window
        if (schematronDetailsButton.getScene() != null && schematronDetailsButton.getScene().getWindow() != null) {
            detailStage.initOwner(schematronDetailsButton.getScene().getWindow());
        }

        detailStage.show();
    }

    /**
     * Navigates to the position of a validation error in the XmlCodeEditor
     */
    private void navigateToError(ValidationError error) {
        if (xmlEditor == null || error.lineNumber() <= 0) {
            return;
        }

        // Get the XmlCodeEditor from the XmlEditor
        org.fxt.freexmltoolkit.controls.XmlCodeEditor xmlCodeEditor = xmlEditor.getXmlCodeEditor();
        if (xmlCodeEditor == null) {
            return;
        }

        org.fxmisc.richtext.CodeArea codeArea = xmlCodeEditor.getCodeArea();
        if (codeArea == null) {
            return;
        }

        try {
            // Convert line/column to position (lines are 1-based in ValidationError, but 0-based in CodeArea)
            int targetLine = Math.max(0, error.lineNumber() - 1);
            int targetColumn = Math.max(0, error.columnNumber() - 1);

            // Get the paragraph (line) from the CodeArea
            if (targetLine < codeArea.getParagraphs().size()) {
                int lineStartPosition = codeArea.getAbsolutePosition(targetLine, 0);
                int lineLength = codeArea.getParagraph(targetLine).length();
                int targetPosition = lineStartPosition + Math.min(targetColumn, lineLength);

                // Move cursor to the error position
                codeArea.moveTo(targetPosition);

                // Select the position or a small range for visibility
                if (targetColumn < lineLength) {
                    // Try to select a word or character at the error position
                    int selectionEnd = Math.min(targetPosition + 10, lineStartPosition + lineLength);
                    codeArea.selectRange(targetPosition, selectionEnd);
                } else {
                    codeArea.selectRange(targetPosition, targetPosition);
                }

                // Request focus and scroll to make the position visible
                codeArea.requestFocus();
                codeArea.requestFollowCaret();
            }
        } catch (Exception e) {
            logger.error("Error navigating to validation error: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the validation errors display in the sidebar
     */
    public void updateValidationErrors(List<ValidationError> errors) {
        currentValidationErrors.clear();
        if (errors != null) {
            currentValidationErrors.addAll(errors);
        }

        // Update the ListView
        if (validationErrorsListView != null) {
            validationErrorsListView.getItems().setAll(currentValidationErrors);
        }

        // Update the count label
        if (validationErrorsCountLabel != null) {
            int errorCount = currentValidationErrors.size();
            validationErrorsCountLabel.setText("Validation Errors (" + errorCount + ")");
        }

        // Show/hide the validation errors pane based on whether there are errors
        if (validationErrorsPane != null) {
            boolean hasErrors = !currentValidationErrors.isEmpty();
            validationErrorsPane.setVisible(hasErrors);
            validationErrorsPane.setManaged(hasErrors);
        }
    }

    /**
     * Enhanced validation status update that also accepts validation errors
     */
    public void updateValidationStatus(String status, String color, List<ValidationError> errors) {
        updateValidationStatus(status, color);
        updateValidationErrors(errors);
    }
}
