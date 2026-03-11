/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeDocumentationsCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Inline tab-based editor for multi-language XSD documentation.
 * Replaces the old GridPane + modal dialog approach with a tab bar + shared TextArea.
 *
 * <p>Layout:</p>
 * <pre>
 *  [+] [en] [de] [fr] [(default)]       &lt;- pill toggle buttons
 * +----------------------------------------------+
 * |                                              |
 * |  TextArea (inline editable, wraps text)      |
 * |                                              |
 * +----------------------------------------------+
 *  Lang: [en  v]                    [Delete]
 * </pre>
 *
 * @since 2.0
 */
public class InlineDocumentationEditor extends VBox implements PropertyChangeListener {

    private static final Logger logger = LogManager.getLogger(InlineDocumentationEditor.class);

    private static final List<String> COMMON_LANGUAGES = List.of(
            "(none)", "en", "de", "fr", "es", "it", "ja", "zh", "ko", "pt", "ru", "ar"
    );

    private final XsdEditorContext editorContext;

    // UI components
    private final HBox tabBar;
    private final ToggleGroup tabGroup;
    private final Button addButton;
    private final TextArea docTextArea;
    private final HBox bottomBar;
    private final ComboBox<String> langComboBox;
    private final Button deleteButton;
    private final VBox emptyState;

    // State
    private XsdNode currentNode;
    private List<XsdDocumentation> documentations = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean editMode = false;
    private boolean updating = false;

    /**
     * Creates a new InlineDocumentationEditor.
     *
     * @param editorContext the editor context for command execution
     */
    public InlineDocumentationEditor(XsdEditorContext editorContext) {
        this.editorContext = editorContext;

        setSpacing(6);
        setPadding(new Insets(8));

        // Tab bar
        tabGroup = new ToggleGroup();
        tabBar = new HBox(4);
        tabBar.setAlignment(Pos.CENTER_LEFT);
        tabBar.getStyleClass().add("doc-tab-bar");

        addButton = new Button();
        FontIcon addIcon = new FontIcon("bi-plus-circle");
        addIcon.setIconSize(14);
        addButton.setGraphic(addIcon);
        addButton.setTooltip(new Tooltip("Add documentation entry"));
        addButton.getStyleClass().add("doc-tab-pill");
        addButton.setOnAction(e -> handleAdd());

        tabBar.getChildren().add(addButton);

        // TextArea
        docTextArea = new TextArea();
        docTextArea.setWrapText(true);
        docTextArea.setPrefRowCount(5);
        docTextArea.setPromptText("Enter documentation text...");
        VBox.setVgrow(docTextArea, Priority.ALWAYS);

        // Auto-save on focus lost
        docTextArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!updating && wasFocused && !isFocused) {
                commitCurrentText();
            }
        });

        // Bottom bar
        bottomBar = new HBox(8);
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        Label langLabel = new Label("Lang:");
        langComboBox = new ComboBox<>(FXCollections.observableArrayList(COMMON_LANGUAGES));
        langComboBox.setEditable(true);
        langComboBox.setPrefWidth(100);
        langComboBox.setTooltip(new Tooltip("Select or type a language code"));
        langComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updating && newVal != null) {
                handleLangChange(newVal);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        deleteButton = new Button("Delete");
        FontIcon deleteIcon = new FontIcon("bi-trash");
        deleteIcon.setIconSize(14);
        deleteButton.setGraphic(deleteIcon);
        deleteButton.getStyleClass().add("btn-danger");
        deleteButton.setTooltip(new Tooltip("Delete this documentation entry"));
        deleteButton.setOnAction(e -> handleDelete());

        bottomBar.getChildren().addAll(langLabel, langComboBox, spacer, deleteButton);

        // Empty state
        emptyState = new VBox(8);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(20));
        emptyState.getStyleClass().add("doc-empty-state");

        FontIcon emptyIcon = new FontIcon("bi-journal-text");
        emptyIcon.setIconSize(48);
        emptyIcon.setIconColor(javafx.scene.paint.Color.web("#adb5bd"));

        Label emptyLabel = new Label("No documentation. Click + to add.");
        emptyLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");

        emptyState.getChildren().addAll(emptyIcon, emptyLabel);
        VBox.setVgrow(emptyState, Priority.ALWAYS);

        // Initial layout (empty state)
        getChildren().addAll(tabBar, emptyState);
        bottomBar.setVisible(false);
        docTextArea.setVisible(false);

        setEditMode(false);
    }

    /**
     * Sets the node whose documentation to edit.
     *
     * @param node the XSD node, or null to clear
     */
    public void setNode(XsdNode node) {
        // Remove listener from old node
        if (currentNode != null) {
            currentNode.removePropertyChangeListener(this);
        }

        this.currentNode = node;

        // Add listener to new node
        if (currentNode != null) {
            currentNode.addPropertyChangeListener(this);
        }

        loadFromModel();
    }

    /**
     * Sets whether editing is enabled.
     *
     * @param edit true to enable editing
     */
    public void setEditMode(boolean edit) {
        this.editMode = edit;
        addButton.setDisable(!edit);
        docTextArea.setEditable(edit);
        langComboBox.setDisable(!edit);
        deleteButton.setDisable(!edit);
    }

    /**
     * Reloads documentation from the current node's model.
     */
    public void refresh() {
        loadFromModel();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("documentations".equals(evt.getPropertyName()) || "documentation".equals(evt.getPropertyName())) {
            if (!updating) {
                loadFromModel();
            }
        }
    }

    // ---- Internal ----

    private void loadFromModel() {
        updating = true;
        try {
            documentations.clear();
            selectedIndex = -1;

            if (currentNode == null) {
                setDisable(true);
                showEmptyState();
                return;
            }

            setDisable(false);

            // Load documentations
            List<XsdDocumentation> docs = currentNode.getDocumentations();
            if (docs != null) {
                documentations.addAll(docs);
            }

            // Check legacy documentation field
            if (documentations.isEmpty() && currentNode.getDocumentation() != null
                    && !currentNode.getDocumentation().trim().isEmpty()) {
                documentations.add(new XsdDocumentation(currentNode.getDocumentation()));
            }

            rebuildTabs();

            if (!documentations.isEmpty()) {
                selectTab(0);
            } else {
                showEmptyState();
            }
        } finally {
            updating = false;
        }
    }

    private void rebuildTabs() {
        // Keep only the add button
        tabBar.getChildren().clear();
        tabBar.getChildren().add(addButton);
        tabGroup.getToggles().clear();

        for (int i = 0; i < documentations.size(); i++) {
            XsdDocumentation doc = documentations.get(i);
            String lang = doc.getLang();
            boolean isDefault = lang == null || lang.isEmpty();
            String label = isDefault ? "(default)" : lang;

            ToggleButton pill = new ToggleButton(label);
            pill.setToggleGroup(tabGroup);
            if (isDefault) {
                pill.getStyleClass().addAll("doc-tab-pill", "doc-tab-pill-default");
            } else {
                pill.getStyleClass().add("doc-tab-pill");
            }

            final int index = i;
            pill.setOnAction(e -> {
                if (pill.isSelected()) {
                    commitCurrentText();
                    selectTab(index);
                } else {
                    // Don't allow deselection - reselect
                    pill.setSelected(true);
                }
            });

            tabBar.getChildren().add(pill);
        }
    }

    private void selectTab(int index) {
        if (index < 0 || index >= documentations.size()) {
            showEmptyState();
            return;
        }

        updating = true;
        try {
            selectedIndex = index;
            XsdDocumentation doc = documentations.get(index);

            // Update TextArea
            docTextArea.setText(doc.getText() != null ? doc.getText() : "");

            // Update language ComboBox
            String lang = doc.getLang();
            if (lang == null || lang.isEmpty()) {
                langComboBox.setValue("(none)");
            } else {
                langComboBox.setValue(lang);
            }

            // Select the right toggle
            // toggles are in positions 0..N-1 in tabGroup, pills are at indices 1..N in tabBar
            if (index < tabGroup.getToggles().size()) {
                tabGroup.getToggles().get(index).setSelected(true);
            }

            showEditor();
        } finally {
            updating = false;
        }
    }

    private void showEditor() {
        getChildren().clear();
        getChildren().addAll(tabBar, docTextArea, bottomBar);
        docTextArea.setVisible(true);
        docTextArea.setManaged(true);
        bottomBar.setVisible(true);
        bottomBar.setManaged(true);
    }

    private void showEmptyState() {
        getChildren().clear();
        getChildren().addAll(tabBar, emptyState);
        emptyState.setVisible(true);
        emptyState.setManaged(true);
    }

    private void commitCurrentText() {
        if (selectedIndex < 0 || selectedIndex >= documentations.size()) {
            return;
        }

        String newText = docTextArea.getText();
        XsdDocumentation doc = documentations.get(selectedIndex);
        String oldText = doc.getText() != null ? doc.getText() : "";

        if (!newText.equals(oldText)) {
            doc.setText(newText);
            executeCommand();
        }
    }

    private void handleAdd() {
        if (currentNode == null) {
            return;
        }

        // Commit any pending edits first
        commitCurrentText();

        XsdDocumentation newDoc = new XsdDocumentation("");
        documentations.add(newDoc);

        rebuildTabs();
        selectTab(documentations.size() - 1);
        executeCommand();

        // Focus the text area for immediate editing
        docTextArea.requestFocus();
    }

    private void handleDelete() {
        if (selectedIndex < 0 || selectedIndex >= documentations.size()) {
            return;
        }

        documentations.remove(selectedIndex);

        rebuildTabs();

        if (!documentations.isEmpty()) {
            int newIndex = Math.min(selectedIndex, documentations.size() - 1);
            selectTab(newIndex);
        } else {
            selectedIndex = -1;
            showEmptyState();
        }

        executeCommand();
    }

    private void handleLangChange(String newLang) {
        if (selectedIndex < 0 || selectedIndex >= documentations.size()) {
            return;
        }

        String effectiveLang = "(none)".equals(newLang) || newLang.trim().isEmpty() ? null : newLang.trim();
        XsdDocumentation doc = documentations.get(selectedIndex);
        String oldLang = doc.getLang();

        // Check if actually changed
        if ((effectiveLang == null && oldLang == null) ||
            (effectiveLang != null && effectiveLang.equals(oldLang))) {
            return;
        }

        doc.setLang(effectiveLang);

        // Update the tab label
        rebuildTabs();
        // Re-select without reloading text (we're already showing it)
        if (selectedIndex < tabGroup.getToggles().size()) {
            updating = true;
            tabGroup.getToggles().get(selectedIndex).setSelected(true);
            updating = false;
        }

        executeCommand();
    }

    private void executeCommand() {
        if (editorContext == null || currentNode == null) {
            logger.warn("Cannot execute command: editorContext or currentNode is null");
            return;
        }

        // Create deep copies for the command
        List<XsdDocumentation> newDocs = new ArrayList<>();
        for (XsdDocumentation doc : documentations) {
            newDocs.add(doc.deepCopy());
        }

        // Set updating flag to prevent propertyChange from reloading
        // while we execute the command (which fires a PropertyChangeEvent)
        updating = true;
        try {
            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, currentNode, newDocs);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Documentation changed for node: {}", currentNode.getName());
        } finally {
            updating = false;
        }
    }

    /**
     * Gets the current list of documentations (for testing).
     *
     * @return unmodifiable copy of the current documentations
     */
    List<XsdDocumentation> getDocumentations() {
        return List.copyOf(documentations);
    }

    /**
     * Gets the currently selected index (for testing).
     *
     * @return the selected tab index, or -1 if none selected
     */
    int getSelectedIndex() {
        return selectedIndex;
    }
}
