package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import org.fxt.freexmltoolkit.controls.XmlEditor;

public class SearchReplaceController {

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab searchTab;
    @FXML
    private Tab replaceTab;
    @FXML
    private TextField findFieldSearch;
    @FXML
    private TextField findFieldReplace;
    @FXML
    private TextField replaceField;
    @FXML
    private Label searchStatusLabel;
    @FXML
    private Label replaceStatusLabel;

    private XmlEditor xmlEditor;

    @FXML
    public void initialize() {
        // Synchronize the find fields
        findFieldReplace.textProperty().bindBidirectional(findFieldSearch.textProperty());
    }

    public void setXmlEditor(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
    }

    @FXML
    private void findNext() {
        if (xmlEditor != null) {
            xmlEditor.find(findFieldSearch.getText(), true);
        }
    }

    @FXML
    private void findPrevious() {
        if (xmlEditor != null) {
            xmlEditor.find(findFieldSearch.getText(), false);
        }
    }

    @FXML
    private void replace() {
        if (xmlEditor != null) {
            xmlEditor.replace(findFieldReplace.getText(), replaceField.getText());
        }
    }

    @FXML
    private void replaceAll() {
        if (xmlEditor != null) {
            xmlEditor.replaceAll(findFieldReplace.getText(), replaceField.getText());
        }
    }

    public void focusFindField() {
        findFieldSearch.requestFocus();
        findFieldSearch.selectAll();
    }

    public void selectTab(Tab tab) {
        tabPane.getSelectionModel().select(tab);
    }

    public Tab getSearchTab() {
        return searchTab;
    }

    public Tab getReplaceTab() {
        return replaceTab;
    }
}