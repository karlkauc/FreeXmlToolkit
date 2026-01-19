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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeDocumentationsCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.kordamp.ikonli.javafx.FontIcon;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reusable panel for editing type documentation (multi-language) and AppInfo.
 * Can be used in both SimpleType and ComplexType editors.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Multi-language documentation cards with Add/Edit/Delete</li>
 *   <li>Integrated AppInfoEditorPanel for structured metadata</li>
 *   <li>Command pattern support for undo/redo</li>
 * </ul>
 *
 * @since 2.0
 */
public class TypeDocumentationPanel extends VBox implements PropertyChangeListener {

    private static final Logger logger = LogManager.getLogger(TypeDocumentationPanel.class);

    private final XsdEditorContext editorContext;
    private XsdNode currentNode;
    private boolean editMode = true;
    private boolean updating = false;

    // Documentation UI
    private TitledPane documentationPane;
    private FlowPane documentationCards;
    private Button addDocButton;
    private List<XsdDocumentation> currentDocumentations = new ArrayList<>();

    // AppInfo UI
    private TitledPane appInfoPane;
    private AppInfoEditorPanel appInfoPanel;

    /**
     * Creates a new TypeDocumentationPanel.
     *
     * @param editorContext the editor context for command execution
     */
    public TypeDocumentationPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        initializeUI();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setSpacing(10);
        setPadding(new Insets(10));

        // Documentation TitledPane
        documentationPane = createDocumentationPane();
        documentationPane.setExpanded(true);

        // AppInfo TitledPane with embedded AppInfoEditorPanel
        appInfoPane = createAppInfoPane();
        appInfoPane.setExpanded(false);

        getChildren().addAll(documentationPane, appInfoPane);
    }

    /**
     * Creates the documentation pane with multi-language cards.
     *
     * @return the documentation TitledPane
     */
    private TitledPane createDocumentationPane() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Description
        Label descLabel = new Label("Add documentation in multiple languages. Each language has its own entry.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Cards container
        documentationCards = new FlowPane();
        documentationCards.setHgap(10);
        documentationCards.setVgap(10);
        documentationCards.setPadding(new Insets(5));

        // Placeholder when no documentation
        Label placeholder = new Label("No documentation defined. Click 'Add Documentation' to create one.");
        placeholder.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
        documentationCards.getChildren().add(placeholder);

        // Add button
        addDocButton = new Button("Add Documentation");
        addDocButton.setGraphic(new FontIcon("bi-plus-circle"));
        addDocButton.setOnAction(e -> handleAddDocumentation());

        HBox buttonBox = new HBox(addDocButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(descLabel, documentationCards, buttonBox);

        TitledPane pane = new TitledPane("Documentation", content);
        pane.setGraphic(new FontIcon("bi-file-text"));
        return pane;
    }

    /**
     * Creates the AppInfo pane with embedded AppInfoEditorPanel.
     *
     * @return the AppInfo TitledPane
     */
    private TitledPane createAppInfoPane() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

        // Create AppInfoEditorPanel
        appInfoPanel = new AppInfoEditorPanel(editorContext);

        content.getChildren().add(appInfoPanel);
        VBox.setVgrow(appInfoPanel, Priority.ALWAYS);

        TitledPane pane = new TitledPane("Application Info (AppInfo)", content);
        pane.setGraphic(new FontIcon("bi-gear"));
        return pane;
    }

    /**
     * Sets the node to edit.
     *
     * @param node the XSD node (SimpleType or ComplexType)
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

        // Update UI
        updateFromModel();

        // Update AppInfo panel
        appInfoPanel.setNode(node);
    }

    /**
     * Sets the edit mode.
     *
     * @param editMode true to enable editing, false for read-only
     */
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        addDocButton.setDisable(!editMode);
        updateDocumentationCards();
    }

    /**
     * Refreshes the panel from the model.
     */
    public void refresh() {
        updateFromModel();
        appInfoPanel.setNode(currentNode);
    }

    /**
     * Updates the UI from the current node's model.
     */
    private void updateFromModel() {
        if (updating) return;
        updating = true;

        try {
            if (currentNode == null) {
                currentDocumentations.clear();
                setDisable(true);
                return;
            }

            setDisable(false);

            // Load documentations from model
            List<XsdDocumentation> docs = currentNode.getDocumentations();
            currentDocumentations = docs != null ? new ArrayList<>(docs) : new ArrayList<>();

            // If no multi-lang docs, check legacy documentation field
            if (currentDocumentations.isEmpty() && currentNode.getDocumentation() != null
                    && !currentNode.getDocumentation().trim().isEmpty()) {
                currentDocumentations.add(new XsdDocumentation(null, currentNode.getDocumentation()));
            }

            updateDocumentationCards();

        } finally {
            updating = false;
        }
    }

    /**
     * Updates the documentation cards display.
     */
    private void updateDocumentationCards() {
        documentationCards.getChildren().clear();

        if (currentDocumentations.isEmpty()) {
            Label placeholder = new Label("No documentation defined. Click 'Add Documentation' to create one.");
            placeholder.setStyle("-fx-text-fill: #999999; -fx-font-style: italic;");
            documentationCards.getChildren().add(placeholder);
            return;
        }

        for (int i = 0; i < currentDocumentations.size(); i++) {
            XsdDocumentation doc = currentDocumentations.get(i);
            VBox card = createDocumentationCard(doc, i);
            documentationCards.getChildren().add(card);
        }
    }

    /**
     * Creates a card for a single documentation entry.
     *
     * @param doc the documentation
     * @param index the index in the list
     * @return the card VBox
     */
    private VBox createDocumentationCard(XsdDocumentation doc, int index) {
        VBox card = new VBox(5);
        card.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 8; -fx-background-color: #ffffff; -fx-background-radius: 4;");
        card.setPrefWidth(220);
        card.setMaxWidth(250);

        // Language badge
        String lang = doc.getLang() != null && !doc.getLang().isEmpty() ? doc.getLang() : "(default)";
        Label langLabel = new Label(lang);
        if (doc.getLang() != null && !doc.getLang().isEmpty()) {
            langLabel.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10px;");
        } else {
            langLabel.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10px;");
        }

        // Content preview
        TextArea contentArea = new TextArea();
        contentArea.setText(doc.getText() != null ? doc.getText() : "");
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(3);
        contentArea.setEditable(false);
        contentArea.setStyle("-fx-font-size: 11px;");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        // Action buttons
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        if (editMode) {
            Button editBtn = new Button();
            editBtn.setGraphic(new FontIcon("bi-pencil"));
            editBtn.setTooltip(new Tooltip("Edit documentation"));
            editBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 6px;");
            editBtn.setOnAction(e -> handleEditDocumentation(index));

            Button deleteBtn = new Button();
            deleteBtn.setGraphic(new FontIcon("bi-trash"));
            deleteBtn.setTooltip(new Tooltip("Delete documentation"));
            deleteBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 6px;");
            deleteBtn.setOnAction(e -> handleDeleteDocumentation(index));

            buttonBox.getChildren().addAll(editBtn, deleteBtn);
        }

        card.getChildren().addAll(langLabel, contentArea, buttonBox);
        return card;
    }

    /**
     * Handles adding a new documentation entry.
     */
    private void handleAddDocumentation() {
        Optional<XsdDocumentation> result = showDocumentationDialog("Add Documentation", null);
        result.ifPresent(doc -> {
            List<XsdDocumentation> newDocs = new ArrayList<>(currentDocumentations);
            newDocs.add(doc);
            executeDocumentationsCommand(newDocs);
        });
    }

    /**
     * Handles editing a documentation entry.
     *
     * @param index the index of the documentation to edit
     */
    private void handleEditDocumentation(int index) {
        if (index < 0 || index >= currentDocumentations.size()) return;

        XsdDocumentation existing = currentDocumentations.get(index);
        Optional<XsdDocumentation> result = showDocumentationDialog("Edit Documentation", existing);
        result.ifPresent(doc -> {
            List<XsdDocumentation> newDocs = new ArrayList<>(currentDocumentations);
            newDocs.set(index, doc);
            executeDocumentationsCommand(newDocs);
        });
    }

    /**
     * Handles deleting a documentation entry.
     *
     * @param index the index of the documentation to delete
     */
    private void handleDeleteDocumentation(int index) {
        if (index < 0 || index >= currentDocumentations.size()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Documentation");
        confirm.setHeaderText("Delete this documentation entry?");
        confirm.setContentText("This action can be undone with Ctrl+Z.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                List<XsdDocumentation> newDocs = new ArrayList<>(currentDocumentations);
                newDocs.remove(index);
                executeDocumentationsCommand(newDocs);
            }
        });
    }

    /**
     * Shows the documentation dialog for adding or editing.
     *
     * @param title the dialog title
     * @param existing existing documentation (null for new)
     * @return optional containing the result
     */
    private Optional<XsdDocumentation> showDocumentationDialog(String title, XsdDocumentation existing) {
        Dialog<XsdDocumentation> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Enter documentation content");
        dialog.setResizable(true);

        // Default language checkbox
        CheckBox defaultLangCheck = new CheckBox("No language (default)");
        defaultLangCheck.setSelected(existing == null || existing.getLang() == null || existing.getLang().isEmpty());

        // Language field
        Label langLabel = new Label("Language:");
        TextField langField = new TextField();
        langField.setPromptText("en, de, fr, etc.");
        langField.setDisable(defaultLangCheck.isSelected());
        if (existing != null && existing.getLang() != null) {
            langField.setText(existing.getLang());
        }

        defaultLangCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            langField.setDisable(newVal);
            if (newVal) {
                langField.clear();
            }
        });

        // Content field
        Label contentLabel = new Label("Documentation:");
        TextArea contentArea = new TextArea();
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(8);
        contentArea.setPrefWidth(400);
        if (existing != null) {
            contentArea.setText(existing.getText());
        }

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        grid.add(defaultLangCheck, 0, 0, 2, 1);
        grid.add(langLabel, 0, 1);
        grid.add(langField, 1, 1);
        grid.add(contentLabel, 0, 2);
        grid.add(contentArea, 0, 3, 2, 1);

        GridPane.setHgrow(langField, Priority.ALWAYS);
        GridPane.setHgrow(contentArea, Priority.ALWAYS);
        GridPane.setVgrow(contentArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(500);

        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (contentArea.getText().trim().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Validation Error");
                alert.setHeaderText(null);
                alert.setContentText("Documentation content cannot be empty.");
                alert.showAndWait();
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String lang = defaultLangCheck.isSelected() ? null : langField.getText().trim();
                if (lang != null && lang.isEmpty()) lang = null;
                String content = contentArea.getText();
                return new XsdDocumentation(lang, content);
            }
            return null;
        });

        return dialog.showAndWait();
    }

    /**
     * Executes a command to change the documentations.
     *
     * @param newDocumentations the new list of documentations
     */
    private void executeDocumentationsCommand(List<XsdDocumentation> newDocumentations) {
        if (editorContext == null || currentNode == null) {
            logger.warn("Cannot execute command: editorContext or currentNode is null");
            return;
        }

        ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                editorContext, currentNode, newDocumentations);
        editorContext.getCommandManager().executeCommand(command);

        logger.debug("Documentation changed for node: {}", currentNode.getName());
    }

    /**
     * PropertyChangeListener implementation to react to model changes.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("documentations".equals(evt.getPropertyName()) || "documentation".equals(evt.getPropertyName())) {
            updateFromModel();
        }
    }

    /**
     * Gets the current node being edited.
     *
     * @return the current node
     */
    public XsdNode getCurrentNode() {
        return currentNode;
    }

    /**
     * Gets the AppInfoEditorPanel for external access if needed.
     *
     * @return the AppInfo panel
     */
    public AppInfoEditorPanel getAppInfoPanel() {
        return appInfoPanel;
    }
}
