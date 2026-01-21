package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;

/**
 * Controller for the Search and Replace panel.
 *
 * <p>This controller manages the search and replace functionality for XML editors.
 * It provides separate tabs for search-only and search-and-replace operations,
 * with synchronized search fields between both tabs.</p>
 *
 * <p><strong>Note:</strong> This controller is deprecated as of version 2.0.
 * Search and replace functionality is now handled directly by {@code XmlCodeEditorV2}.
 * This class is retained only for backward compatibility with legacy components.</p>
 *
 * @deprecated Since 2.0, search/replace is handled by XmlCodeEditorV2
 */
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

    /**
     * Creates a new SearchReplaceController instance.
     *
     * <p>This default constructor is required for FXML instantiation.
     * The controller is fully initialized after the {@link #initialize()} method is called.</p>
     *
     * @deprecated Since 2.0, search/replace is handled by XmlCodeEditorV2
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public SearchReplaceController() {
        // Default constructor for FXML instantiation
    }

    /**
     * Initializes the controller after FXML injection.
     * Sets up bidirectional binding between search fields.
     */
    @FXML
    public void initialize() {
        // Synchronize the find fields
        findFieldReplace.textProperty().bindBidirectional(findFieldSearch.textProperty());
    }

    /**
     * Sets the XML code editor instance for search and replace operations.
     *
     * <p>This method was used to connect the search/replace controller to a V1 XmlCodeEditor.
     * As of version 2.0, this method is no longer functional and will be removed in a future release.</p>
     *
     * @param xmlCodeEditor the XML code editor instance (ignored in current implementation)
     * @deprecated Since 2.0, search/replace is handled directly by XmlCodeEditorV2
     */
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

    /**
     * Focuses the search field and selects all its text.
     *
     * <p>This method is typically called when the search panel becomes visible
     * to allow immediate keyboard input for the search term.</p>
     */
    public void focusFindField() {
        findFieldSearch.requestFocus();
        findFieldSearch.selectAll();
    }

    /**
     * Selects the specified tab in the tab pane.
     *
     * <p>Use this method to programmatically switch between the search tab
     * and the replace tab.</p>
     *
     * @param tab the tab to select (should be either the search tab or replace tab)
     */
    public void selectTab(Tab tab) {
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Returns the search-only tab.
     *
     * <p>The search tab provides basic find functionality without replace options.</p>
     *
     * @return the search tab instance
     */
    public Tab getSearchTab() {
        return searchTab;
    }

    /**
     * Returns the search and replace tab.
     *
     * <p>The replace tab provides both find and replace functionality,
     * including replace single and replace all operations.</p>
     *
     * @return the replace tab instance
     */
    public Tab getReplaceTab() {
        return replaceTab;
    }
}