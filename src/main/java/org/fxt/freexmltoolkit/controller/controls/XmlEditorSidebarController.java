package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.SchematronService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XmlEditorSidebarController {

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
}
