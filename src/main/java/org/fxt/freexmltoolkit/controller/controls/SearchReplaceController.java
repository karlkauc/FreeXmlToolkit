package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;

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

    // V1 XmlCodeEditor is deprecated and removed
    // SearchReplaceController is no longer used with V2 editor
    // Search/Replace functionality is now handled by XmlCodeEditorV2

    @FXML
    public void initialize() {
        // Synchronize the find fields
        findFieldReplace.textProperty().bindBidirectional(findFieldSearch.textProperty());
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public void setXmlCodeEditor(Object xmlCodeEditor) {
        // V1 editor deprecated - this method is no longer used
    }

    @FXML
    private void findNext() {
        // V1 Search functionality deprecated - search is now handled by XmlCodeEditorV2
    }

    @FXML
    private void findPrevious() {
        // V1 Search functionality deprecated - search is now handled by XmlCodeEditorV2
    }

    @FXML
    private void replace() {
        // V1 Replace functionality deprecated - replace is now handled by XmlCodeEditorV2
    }

    @FXML
    private void replaceAll() {
        // V1 Replace functionality deprecated - replace is now handled by XmlCodeEditorV2
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