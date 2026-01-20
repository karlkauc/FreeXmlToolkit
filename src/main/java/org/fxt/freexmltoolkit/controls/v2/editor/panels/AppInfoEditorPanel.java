package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeAppinfoCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Objects;

/**
 * Structured editor panel for XSD AppInfo annotations (XsdDoc).
 * Provides form-based editing for @since, @version, @author, @see, and @deprecated tags.
 * Supports {@code {@link}} autocomplete for element references.
 *
 * @since 2.0
 */
public class AppInfoEditorPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(AppInfoEditorPanel.class);

    private final XsdEditorContext editorContext;

    // Version Info fields
    private TextField sinceField;
    private TextField versionField;
    private TextField authorField;

    // See References
    private ListView<String> seeListView;
    private ObservableList<String> seeItems;
    private Button addSeeButton;
    private Button removeSeeButton;

    // Deprecation
    private CheckBox deprecatedCheckBox;
    private TextArea deprecatedTextArea;

    // Current state
    private XsdNode currentNode;
    private boolean updating = false;

    // Autocomplete support
    private XsdElementPathExtractor pathExtractor;
    private LinkAutocompletePopup autocompletePopup;

    /**
     * Creates a new AppInfo editor panel.
     *
     * @param editorContext the editor context
     */
    public AppInfoEditorPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        initializeUI();
        setupListeners();
        initializeAutocomplete();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Version Info TitledPane
        TitledPane versionPane = createVersionInfoPane();
        versionPane.setExpanded(false);

        // See References TitledPane
        TitledPane seePane = createSeeReferencesPane();
        seePane.setExpanded(false);

        // Deprecation TitledPane
        TitledPane deprecationPane = createDeprecationPane();
        deprecationPane.setExpanded(false);

        getChildren().addAll(versionPane, seePane, deprecationPane);
    }

    /**
     * Creates the Version Info pane with @since, @version, @author fields.
     */
    private TitledPane createVersionInfoPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        // @since
        Label sinceLabel = new Label("@since:");
        sinceLabel.setStyle("-fx-font-weight: bold;");
        sinceField = new TextField();
        sinceField.setPromptText("e.g., 4.0.0");
        sinceField.setPrefWidth(200);
        grid.add(sinceLabel, 0, 0);
        grid.add(sinceField, 1, 0);

        // @version
        Label versionLabel = new Label("@version:");
        versionLabel.setStyle("-fx-font-weight: bold;");
        versionField = new TextField();
        versionField.setPromptText("e.g., 1.0");
        versionField.setPrefWidth(200);
        grid.add(versionLabel, 0, 1);
        grid.add(versionField, 1, 1);

        // @author
        Label authorLabel = new Label("@author:");
        authorLabel.setStyle("-fx-font-weight: bold;");
        authorField = new TextField();
        authorField.setPromptText("e.g., John Doe");
        authorField.setPrefWidth(200);
        grid.add(authorLabel, 0, 2);
        grid.add(authorField, 1, 2);

        // Make text fields grow
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.NEVER);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        TitledPane pane = new TitledPane("Version Info", grid);
        pane.setGraphic(new FontIcon("bi-info-circle"));
        return pane;
    }

    /**
     * Creates the See References pane with a list of @see entries.
     */
    private TitledPane createSeeReferencesPane() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        // Description
        Label descLabel = new Label("Add references to related elements. Use {@link /XPath} for links.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // List view
        seeItems = FXCollections.observableArrayList();
        seeListView = new ListView<>(seeItems);
        seeListView.setPrefHeight(100);
        seeListView.setPlaceholder(new Label("No @see references defined"));
        seeListView.setCellFactory(lv -> new SeeReferenceCell());
        VBox.setVgrow(seeListView, Priority.ALWAYS);

        // Buttons
        addSeeButton = new Button("Add");
        addSeeButton.setGraphic(new FontIcon("bi-plus-circle"));
        addSeeButton.setOnAction(e -> handleAddSeeReference());

        removeSeeButton = new Button("Remove");
        removeSeeButton.setGraphic(new FontIcon("bi-dash-circle"));
        removeSeeButton.setOnAction(e -> handleRemoveSeeReference());
        removeSeeButton.setDisable(true);

        HBox buttonBox = new HBox(8, addSeeButton, removeSeeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        // Enable/disable remove button based on selection
        seeListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> removeSeeButton.setDisable(newVal == null));

        content.getChildren().addAll(descLabel, seeListView, buttonBox);

        TitledPane pane = new TitledPane("See Also (@see)", content);
        pane.setGraphic(new FontIcon("bi-link-45deg"));
        return pane;
    }

    /**
     * Creates the Deprecation pane.
     */
    private TitledPane createDeprecationPane() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        // Checkbox
        deprecatedCheckBox = new CheckBox("Mark as deprecated");
        deprecatedCheckBox.setStyle("-fx-font-weight: bold;");

        // Description
        Label descLabel = new Label("Provide a deprecation message. Use {@link /XPath} for replacement references.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // Text area
        deprecatedTextArea = new TextArea();
        deprecatedTextArea.setPromptText("e.g., Use {@link /NewElement} instead");
        deprecatedTextArea.setPrefRowCount(2);
        deprecatedTextArea.setWrapText(true);
        deprecatedTextArea.setDisable(true);
        VBox.setVgrow(deprecatedTextArea, Priority.ALWAYS);

        // Enable/disable text area based on checkbox
        deprecatedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            deprecatedTextArea.setDisable(!newVal);
            if (!updating && !newVal) {
                // Clear deprecation when unchecked
                handleDeprecationChange();
            }
        });

        content.getChildren().addAll(deprecatedCheckBox, descLabel, deprecatedTextArea);

        TitledPane pane = new TitledPane("Deprecation (@deprecated)", content);
        pane.setGraphic(new FontIcon("bi-exclamation-triangle"));

        // Style the pane header when deprecated
        deprecatedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                pane.setStyle("-fx-color: #fff3cd;");
            } else {
                pane.setStyle("");
            }
        });

        return pane;
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
        if (updating) return;  // Prevent recursive updates during command execution
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
        if (updating || currentNode == null) return;

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
            if (newVal != null && LinkAutocompletePopup.isLinkTrigger(newVal)) {
                if (autocompletePopup != null) {
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
        if (updating || currentNode == null) return;

        XsdAppInfo newAppInfo = createAppInfoFromUI();
        executeChangeCommand(newAppInfo);
    }

    /**
     * Handles changes to deprecation.
     */
    private void handleDeprecationChange() {
        if (updating || currentNode == null) return;

        XsdAppInfo newAppInfo = createAppInfoFromUI();
        executeChangeCommand(newAppInfo);
    }

    /**
     * Executes a change command with the new appinfo.
     */
    private void executeChangeCommand(XsdAppInfo newAppInfo) {
        if (editorContext == null || currentNode == null) return;
        if (updating) return;  // Prevent recursive calls during PropertyChangeEvent handling

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

            // Create and execute command
            String newAppInfoString = hasData ? newAppInfo.toDisplayString() : "";
            ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, currentNode, newAppInfoString);
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
        private final FontIcon icon;
        private final Label textLabel;

        public SeeReferenceCell() {
            content = new HBox(8);
            content.setAlignment(Pos.CENTER_LEFT);

            icon = new FontIcon("bi-link-45deg");
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
