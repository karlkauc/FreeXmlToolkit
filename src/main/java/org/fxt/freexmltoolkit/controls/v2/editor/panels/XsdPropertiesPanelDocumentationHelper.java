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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

import java.util.List;
import java.util.Optional;

/**
 * Helper class for documentation-related functionality in XsdPropertiesPanel.
 *
 * <p>Manages documentation card creation, editing, and deletion.</p>
 *
 * @since 2.0
 */
public class XsdPropertiesPanelDocumentationHelper {
    private static final Logger logger = LogManager.getLogger(XsdPropertiesPanelDocumentationHelper.class);

    private final XsdEditorContext editorContext;
    private XsdNode currentNode;
    private boolean isEditMode;

    /**
     * Creates a new documentation helper.
     *
     * @param editorContext the editor context
     */
    public XsdPropertiesPanelDocumentationHelper(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        this.currentNode = null;
        this.isEditMode = false;
    }

    /**
     * Sets the current node and edit mode.
     *
     * @param node the current node
     * @param editMode whether in edit mode
     */
    public void setContext(XsdNode node, boolean editMode) {
        this.currentNode = node;
        this.isEditMode = editMode;
    }

    /**
     * Creates a documentation card for a single documentation entry.
     *
     * @param doc the documentation object
     * @param onEdit callback for edit action
     * @param onDelete callback for delete action
     * @return the card VBox
     */
    public VBox createDocumentationCard(XsdDocumentation doc, Runnable onEdit, Runnable onDelete) {
        VBox card = new VBox(5);
        card.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-padding: 8;");
        card.setPrefWidth(180);
        card.setMaxWidth(200);

        // Language label
        Label langLabel = new Label("Language: " + (doc.getLang() != null && !doc.getLang().isEmpty() ? doc.getLang() : "(default)"));
        langLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");

        // Content preview (truncated)
        TextArea contentArea = new TextArea();
        contentArea.setText(doc.getText());
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(3);
        contentArea.setEditable(isEditMode);
        contentArea.setStyle("-fx-font-size: 10px;");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        // Action buttons
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER);

        if (isEditMode) {
            Button editBtn = new Button("Edit");
            editBtn.setGraphic(new FontIcon("bi-pencil"));
            editBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 8px;");
            editBtn.setOnAction(e -> onEdit.run());

            Button deleteBtn = new Button("Delete");
            deleteBtn.setGraphic(new FontIcon("bi-trash"));
            deleteBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 8px; -fx-text-fill: #dc3545;");
            deleteBtn.setOnAction(e -> onDelete.run());

            buttonBox.getChildren().addAll(editBtn, deleteBtn);
        }

        card.getChildren().addAll(langLabel, contentArea, buttonBox);
        return card;
    }

    /**
     * Creates a documentation dialog for adding or editing documentation.
     *
     * @param title dialog title
     * @param existingDoc existing documentation (null for new)
     * @return optional containing the result
     */
    public Optional<XsdDocumentation> showDocumentationDialog(String title, XsdDocumentation existingDoc) {
        Dialog<XsdDocumentation> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Edit documentation entry");

        // Language field
        Label langLabel = new Label("Language:");
        TextField langField = new TextField();
        langField.setPromptText("en, fr, de, etc. (leave empty for default)");
        if (existingDoc != null) {
            langField.setText(existingDoc.getLang() != null ? existingDoc.getLang() : "");
        }

        // Content field
        Label contentLabel = new Label("Content:");
        TextArea contentArea = new TextArea();
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(8);
        if (existingDoc != null) {
            contentArea.setText(existingDoc.getText());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(langLabel, 0, 0);
        grid.add(langField, 1, 0);
        grid.add(contentLabel, 0, 1);
        grid.add(contentArea, 0, 2, 2, 1);
        GridPane.setVgrow(contentArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String lang = langField.getText().trim().isEmpty() ? null : langField.getText().trim();
                String content = contentArea.getText();
                return new XsdDocumentation(lang, content);
            }
            return null;
        });

        return dialog.showAndWait();
    }

    /**
     * Gets the effective language code from documentation list.
     *
     * @param docs list of documentations
     * @param defaultLang default language
     * @return effective language
     */
    public String getEffectiveLanguage(List<XsdDocumentation> docs, String defaultLang) {
        if (docs == null || docs.isEmpty()) {
            return defaultLang;
        }

        // Try to find the documentation matching the UI language
        for (XsdDocumentation doc : docs) {
            if (defaultLang.equals(doc.getLang())) {
                return defaultLang;
            }
        }

        // Default to the first one
        return docs.get(0).getLang();
    }

    /**
     * Validates documentation content before saving.
     *
     * @param content the content to validate
     * @return true if valid
     */
    public boolean isValidDocumentationContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Empty documentation content");
            return false;
        }
        return true;
    }
}
