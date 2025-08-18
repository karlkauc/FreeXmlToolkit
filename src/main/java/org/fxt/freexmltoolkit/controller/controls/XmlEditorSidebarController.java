package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.XmlEditor;

import java.io.File;

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
    private Button toggleSidebarButton;

    @FXML
    private VBox sidebarContent;

    private XmlEditor xmlEditor;

    private String xsdPath;

    private boolean sidebarVisible = true;

    private VBox sidebarContainer; // Reference to the main container
    private double expandedWidth = 300; // Store the expanded width

    public void setXmlEditor(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
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
}
