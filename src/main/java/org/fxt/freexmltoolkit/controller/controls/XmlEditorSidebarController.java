package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.scene.control.*;
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
    private ListView<String> childElementsListView;

    private XmlEditor xmlEditor;

    public void setXmlEditor(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
    }

    @FXML
    private void initialize() {
        changeXsdButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select XSD Schema");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));
            File selectedFile = fileChooser.showOpenDialog(changeXsdButton.getScene().getWindow());
            if (selectedFile != null) {
                xsdPathField.setText(selectedFile.getAbsolutePath());
                xmlEditor.setXsdFile(selectedFile);
            }
        });

        continuousValidationCheckBox.setOnAction(event -> {
            if (continuousValidationCheckBox.isSelected()) {
                xmlEditor.validateXml();
            }
        });
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
}
