package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;

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

    private XmlCodeEditor xmlCodeEditor;

    @FXML
    public void initialize() {
        // Synchronize the find fields
        findFieldReplace.textProperty().bindBidirectional(findFieldSearch.textProperty());
    }

    public void setXmlCodeEditor(XmlCodeEditor xmlCodeEditor) {
        this.xmlCodeEditor = xmlCodeEditor;
    }

    @FXML
    private void findNext() {
        if (xmlCodeEditor != null) {
            xmlCodeEditor.find(findFieldSearch.getText(), true);
        }
    }

    @FXML
    private void findPrevious() {
        if (xmlCodeEditor != null) {
            xmlCodeEditor.find(findFieldSearch.getText(), false);
        }
    }

    @FXML
    private void replace() {
        if (xmlCodeEditor != null) {
            xmlCodeEditor.replace(findFieldReplace.getText(), replaceField.getText());
        }
    }

    @FXML
    private void replaceAll() {
        if (xmlCodeEditor != null) {
            xmlCodeEditor.replaceAll(findFieldReplace.getText(), replaceField.getText());
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