package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import java.util.Objects;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeAppinfoCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Structured editor panel for XSD AppInfo annotations (XsdDoc).
 * Provides form-based editing for @since, @version, @author, @see, and @deprecated tags.
 * Supports {@code {@link}} autocomplete for element references.
 *
 * @since 2.0
 */
public class AppInfoEditorPanel extends VBox {

    /** Tags this panel manages directly; every other entry is carried over untouched. */
    private static final Set<String> MANAGED_TAGS =
            Set.of("@since", "@version", "@author", "@see", "@deprecated");

    private static final Logger logger = LogManager.getLogger(AppInfoEditorPanel.class);

    private final XsdEditorContext editorContext;

    // Version Info fields
    @FXML private TextField sinceField;
    @FXML private TextField versionField;
    @FXML private TextField authorField;

    // See References
    @FXML private ListView<String> seeListView;
    private final ObservableList<String> seeItems = FXCollections.observableArrayList();
    @FXML private Button addSeeButton;
    @FXML private Button removeSeeButton;

    // Deprecation
    @FXML private TitledPane deprecationPane;
    @FXML private CheckBox deprecatedCheckBox;
    @FXML private TextArea deprecatedTextArea;

    // Current state
    private XsdNode currentNode;
    private boolean updating = false;

    // Autocomplete support
    private XsdElementPathExtractor pathExtractor;
    private LinkAutocompletePopup autocompletePopup;

    /**
     * Creates a new AppInfo editor panel. The layout is loaded from
     * {@code /pages/views/AppInfoEditorPanel.fxml}.
     *
     * @param editorContext the editor context
     */
    public AppInfoEditorPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        FxmlViews.load(this, "/pages/views/AppInfoEditorPanel.fxml");
        initializeUI();
        setupListeners();
        initializeAutocomplete();
    }

    /**
     * Wires the data-driven parts of the loaded layout.
     */
    private void initializeUI() {
        seeListView.setItems(seeItems);
        seeListView.setCellFactory(lv -> new SeeReferenceCell());

        // Enable/disable remove button based on selection
        seeListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> removeSeeButton.setDisable(newVal == null));

        // Enable/disable text area based on checkbox
        deprecatedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            deprecatedTextArea.setDisable(!newVal);
            if (!updating && !newVal) {
                // Clear deprecation when unchecked
                handleDeprecationChange();
            }
        });

        // Style the pane header when deprecated
        deprecatedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                deprecationPane.setStyle("-fx-color: #fff3cd;");
            } else {
                deprecationPane.setStyle("");
            }
        });
    }

    @FXML
    public void onAddSeeReference() {
        handleAddSeeReference();
    }

    @FXML
    public void onRemoveSeeReference() {
        handleRemoveSeeReference();
    }

    /**
     * Sets up event listeners for fields.
     */
    private void setupListeners() {
        // Version info fields - trigger command on focus lost
        sinceField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!updating && wasFocused && !isFocused) {
                handleVersionInfoChange();
            }
        });

        versionField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!updating && wasFocused && !isFocused) {
                handleVersionInfoChange();
            }
        });

        authorField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!updating && wasFocused && !isFocused) {
                handleVersionInfoChange();
            }
        });

        // Deprecation text area - trigger command on focus lost
        deprecatedTextArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!updating && wasFocused && !isFocused) {
                handleDeprecationChange();
            }
        });

        // Setup @link autocomplete for deprecation text area
        deprecatedTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updating && newVal != null && LinkAutocompletePopup.isLinkTrigger(newVal)) {
                showAutocomplete(deprecatedTextArea);
            }
        });
    }

    /**
     * Initializes the autocomplete support.
     */
    private void initializeAutocomplete() {
        if (editorContext != null && editorContext.getSchema() != null) {
            pathExtractor = new XsdElementPathExtractor(editorContext.getSchema());
            autocompletePopup = new LinkAutocompletePopup(pathExtractor);
        }
    }

    /**
     * Shows the autocomplete popup for the given text control.
     */
    private void showAutocomplete(TextInputControl control) {
        if (autocompletePopup == null) {
            initializeAutocomplete();
        }

        if (autocompletePopup != null && pathExtractor != null) {
            int triggerPos = LinkAutocompletePopup.findLinkTriggerPosition(control.getText());
            if (triggerPos >= 0) {
                autocompletePopup.showFor(control, triggerPos, linkText -> {
                    // Replace {@link with the complete link
                    String currentText = control.getText();
                    int pos = LinkAutocompletePopup.findLinkTriggerPosition(currentText);
                    if (pos >= 0) {
                        String newText = currentText.substring(0, pos) + linkText;
                        control.setText(newText);
                        control.positionCaret(newText.length());
                    }
                });
            }
        }
    }

    /**
     * Sets the node to edit.
     *
     * @param node the XSD node, or null to clear
     */
    public void setNode(XsdNode node) {
        if (updating) {
            return; // Prevent recursive updates during command execution
        }
        this.currentNode = node;
        updateFromModel();
    }

    /**
     * Updates the UI from the current node's appinfo.
     */
    private void updateFromModel() {
        updating = true;
        try {
            if (currentNode == null) {
                clearFields();
                setDisable(true);
                return;
            }

            setDisable(false);
            XsdAppInfo appInfo = currentNode.getAppinfo();

            if (appInfo == null) {
                clearFields();
                return;
            }

            // Version info
            sinceField.setText(appInfo.getSince() != null ? appInfo.getSince() : "");
            versionField.setText(appInfo.getVersion() != null ? appInfo.getVersion() : "");
            authorField.setText(appInfo.getAuthor() != null ? appInfo.getAuthor() : "");

            // See references
            seeItems.setAll(appInfo.getSeeReferences());

            // Deprecation
            boolean isDeprecated = appInfo.isDeprecated();
            deprecatedCheckBox.setSelected(isDeprecated);
            deprecatedTextArea.setDisable(!isDeprecated);
            deprecatedTextArea.setText(appInfo.getDeprecated() != null ? appInfo.getDeprecated() : "");

        } finally {
            updating = false;
        }
    }

    /**
     * Clears all fields.
     */
    private void clearFields() {
        sinceField.clear();
        versionField.clear();
        authorField.clear();
        seeItems.clear();
        deprecatedCheckBox.setSelected(false);
        deprecatedTextArea.clear();
        deprecatedTextArea.setDisable(true);
    }

    /**
     * Creates a new XsdAppInfo from the current UI state.
     */
    private XsdAppInfo createAppInfoFromUI() {
        XsdAppInfo appInfo = new XsdAppInfo();

        // Carry over everything this panel does not manage - raw XML blocks such as
        // fxt:exampleValues, the @sourceFile marker, @markdown and unknown tags. Without this the
        // panel would silently drop them on every edit.
        XsdAppInfo current = currentNode != null ? currentNode.getAppinfo() : null;
        if (current != null) {
            for (XsdAppInfo.AppInfoEntry entry : current.getEntries()) {
                if (entry.hasRawXml() || entry.getTag() == null || !MANAGED_TAGS.contains(entry.getTag())) {
                    appInfo.addEntry(entry);
                }
            }
        }

        // Version info
        String since = sinceField.getText();
        if (since != null && !since.trim().isEmpty()) {
            appInfo.setSince(since.trim());
        }

        String version = versionField.getText();
        if (version != null && !version.trim().isEmpty()) {
            appInfo.setVersion(version.trim());
        }

        String author = authorField.getText();
        if (author != null && !author.trim().isEmpty()) {
            appInfo.setAuthor(author.trim());
        }

        // See references
        for (String see : seeItems) {
            if (see != null && !see.trim().isEmpty()) {
                appInfo.addSeeReference(see.trim());
            }
        }

        // Deprecation
        if (deprecatedCheckBox.isSelected()) {
            String deprecatedMsg = deprecatedTextArea.getText();
            appInfo.setDeprecated(deprecatedMsg != null ? deprecatedMsg.trim() : "");
        }

        return appInfo;
    }

    /**
     * Handles changes to version info fields.
     */
    private void handleVersionInfoChange() {
        if (updating || currentNode == null) {
            return;
        }

        XsdAppInfo newAppInfo = createAppInfoFromUI();
        executeChangeCommand(newAppInfo);
    }

    /**
     * Handles adding a new @see reference.
     */
    private void handleAddSeeReference() {
        // Show dialog with autocomplete
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add @see Reference");
        dialog.setHeaderText("Enter a reference");
        dialog.setContentText("Reference:");
        dialog.getEditor().setPromptText("e.g., {@link /Root/Element} or plain text");

        // Add autocomplete to the dialog's text field
        TextField dialogField = dialog.getEditor();
        dialogField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && LinkAutocompletePopup.isLinkTrigger(newVal) && autocompletePopup != null) {
                int triggerPos = LinkAutocompletePopup.findLinkTriggerPosition(newVal);
                autocompletePopup.showFor(dialogField, triggerPos, linkText -> {
                    String currentText = dialogField.getText();
                    int pos = LinkAutocompletePopup.findLinkTriggerPosition(currentText);
                    if (pos >= 0) {
                        String finalText = currentText.substring(0, pos) + linkText;
                        dialogField.setText(finalText);
                        dialogField.positionCaret(finalText.length());
                    }
                });
            }
        });

        dialog.showAndWait().ifPresent(reference -> {
            if (!reference.trim().isEmpty()) {
                seeItems.add(reference.trim());
                handleSeeReferencesChange();
            }
        });
    }

    /**
     * Handles removing the selected @see reference.
     */
    private void handleRemoveSeeReference() {
        String selected = seeListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            seeItems.remove(selected);
            handleSeeReferencesChange();
        }
    }

    /**
     * Handles changes to @see references.
     */
    private void handleSeeReferencesChange() {
        if (updating || currentNode == null) {
            return;
        }

        XsdAppInfo newAppInfo = createAppInfoFromUI();
        executeChangeCommand(newAppInfo);
    }

    /**
     * Handles changes to deprecation.
     */
    private void handleDeprecationChange() {
        if (updating || currentNode == null) {
            return;
        }

        XsdAppInfo newAppInfo = createAppInfoFromUI();
        executeChangeCommand(newAppInfo);
    }

    /**
     * Executes a change command with the new appinfo.
     */
    private void executeChangeCommand(XsdAppInfo newAppInfo) {
        if (editorContext == null || currentNode == null) {
            return;
        }
        if (updating) {
            return; // Prevent recursive calls during PropertyChangeEvent handling
        }

        updating = true;
        try {
            XsdAppInfo currentAppInfo = currentNode.getAppinfo();

            // Check if there's actually a change
            boolean hasData = newAppInfo.hasEntries();
            boolean hadData = currentAppInfo != null && currentAppInfo.hasEntries();

            if (!hasData && !hadData) {
                return; // No change
            }

            if (hasData && hadData && Objects.equals(newAppInfo.toDisplayString(), currentAppInfo.toDisplayString())) {
                return; // No change
            }

            // Create and execute command. The structured overload preserves raw-XML entries -
            // the display-string form cannot round-trip them.
            ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, currentNode,
                    hasData ? newAppInfo : null);
            editorContext.getCommandManager().executeCommand(command);

            logger.debug("AppInfo changed for node: {}", currentNode.getName());
        } finally {
            updating = false;
        }
    }

    /**
     * Refreshes the path extractor cache (call when schema changes).
     */
    public void refreshPathExtractor() {
        if (pathExtractor != null) {
            pathExtractor.invalidateCache();
        }
    }

    /**
     * Custom cell for displaying @see references.
     */
    private static class SeeReferenceCell extends ListCell<String> {
        private final HBox content;
        private final IconifyIcon icon;
        private final Label textLabel;

        SeeReferenceCell() {
            content = new HBox(8);
            content.setAlignment(Pos.CENTER_LEFT);

            icon = new IconifyIcon("bi-link-45deg");
            icon.setIconSize(14);

            textLabel = new Label();
            textLabel.setStyle("-fx-font-family: monospace;");

            content.getChildren().addAll(icon, textLabel);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                textLabel.setText(item);

                // Color based on whether it's a link
                if (item.contains("{@link")) {
                    icon.setIconColor(javafx.scene.paint.Color.DODGERBLUE);
                } else {
                    icon.setIconColor(javafx.scene.paint.Color.GRAY);
                }

                setGraphic(content);
            }
        }
    }
}
