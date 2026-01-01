/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.controls.jsoneditor.view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/**
 * A TreeView-based component for displaying and navigating JSON documents.
 * Provides a hierarchical view of the JSON structure.
 */
public class JsonTreeView extends VBox implements PropertyChangeListener {

    private static final Logger logger = LogManager.getLogger(JsonTreeView.class);

    private final TreeView<JsonNode> treeView;
    private final TextField searchField;
    private final Label statusLabel;

    private JsonDocument document;
    private final Map<String, TreeItem<JsonNode>> nodeIdToTreeItem = new HashMap<>();

    // Selection callback
    private java.util.function.Consumer<JsonNode> onSelectionChanged;

    public JsonTreeView() {
        super(8);
        setPadding(new Insets(8));

        // Create search toolbar
        searchField = new TextField();
        searchField.setPromptText("Search JSON...");
        searchField.setOnAction(e -> search(searchField.getText()));

        Button searchButton = new Button();
        searchButton.setGraphic(new FontIcon("bi-search"));
        searchButton.setOnAction(e -> search(searchField.getText()));

        HBox searchBar = new HBox(8, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        // Create tree view
        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        treeView.setCellFactory(tv -> new JsonTreeCell());
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // Selection listener
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null && onSelectionChanged != null) {
                onSelectionChanged.accept(newItem.getValue());
            }
        });

        // Status label
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        getChildren().addAll(searchBar, treeView, statusLabel);

        logger.info("JsonTreeView created");
    }

    /**
     * Sets the JSON document to display.
     */
    public void setDocument(JsonDocument document) {
        // Remove old listener
        if (this.document != null) {
            this.document.removePropertyChangeListener(this);
        }

        this.document = document;
        nodeIdToTreeItem.clear();

        if (document != null) {
            document.addPropertyChangeListener(this);
            rebuildTree();
        } else {
            treeView.setRoot(null);
        }
    }

    /**
     * Gets the current document.
     */
    public JsonDocument getDocument() {
        return document;
    }

    /**
     * Rebuilds the tree from the current document.
     */
    public void rebuildTree() {
        if (document == null) {
            treeView.setRoot(null);
            updateStatus("No document");
            return;
        }

        nodeIdToTreeItem.clear();

        JsonNode rootValue = document.getRootValue();
        if (rootValue == null) {
            treeView.setRoot(null);
            updateStatus("Empty document");
            return;
        }

        TreeItem<JsonNode> rootItem = createTreeItem(rootValue);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

        updateStatus(countNodes(rootValue) + " nodes");
    }

    /**
     * Creates a TreeItem for a JsonNode and its children.
     */
    private TreeItem<JsonNode> createTreeItem(JsonNode node) {
        TreeItem<JsonNode> item = new TreeItem<>(node);
        nodeIdToTreeItem.put(node.getId(), item);

        // Add children
        for (JsonNode child : node.getChildren()) {
            item.getChildren().add(createTreeItem(child));
        }

        // Expand objects and arrays by default if they have few children
        if ((node instanceof JsonObject || node instanceof JsonArray) &&
                node.getChildCount() <= 10) {
            item.setExpanded(true);
        }

        return item;
    }

    /**
     * Counts total nodes in a subtree.
     */
    private int countNodes(JsonNode node) {
        int count = 1;
        for (JsonNode child : node.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }

    /**
     * Searches for nodes matching the query.
     */
    private void search(String query) {
        if (query == null || query.isBlank()) {
            return;
        }

        String lowerQuery = query.toLowerCase();
        TreeItem<JsonNode> match = findNode(treeView.getRoot(), lowerQuery);

        if (match != null) {
            // Expand parents
            TreeItem<JsonNode> parent = match.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }

            // Select and scroll to match
            treeView.getSelectionModel().select(match);
            treeView.scrollTo(treeView.getRow(match));
            updateStatus("Found: " + match.getValue().getPath());
        } else {
            updateStatus("Not found: " + query);
        }
    }

    /**
     * Finds a node matching the query.
     */
    private TreeItem<JsonNode> findNode(TreeItem<JsonNode> item, String query) {
        if (item == null) {
            return null;
        }

        JsonNode node = item.getValue();
        if (node != null) {
            // Check key
            if (node.getKey() != null && node.getKey().toLowerCase().contains(query)) {
                return item;
            }
            // Check value for primitives
            if (node instanceof JsonPrimitive primitive) {
                String valueStr = primitive.getValueAsString().toLowerCase();
                if (valueStr.contains(query)) {
                    return item;
                }
            }
        }

        // Search children
        for (TreeItem<JsonNode> child : item.getChildren()) {
            TreeItem<JsonNode> found = findNode(child, query);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Selects a node in the tree.
     */
    public void selectNode(JsonNode node) {
        if (node == null) {
            return;
        }

        TreeItem<JsonNode> item = nodeIdToTreeItem.get(node.getId());
        if (item != null) {
            // Expand parents
            TreeItem<JsonNode> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }

            treeView.getSelectionModel().select(item);
            treeView.scrollTo(treeView.getRow(item));
        }
    }

    /**
     * Gets the currently selected node.
     */
    public JsonNode getSelectedNode() {
        TreeItem<JsonNode> selected = treeView.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getValue() : null;
    }

    /**
     * Expands all nodes.
     */
    public void expandAll() {
        expandAll(treeView.getRoot());
    }

    private void expandAll(TreeItem<JsonNode> item) {
        if (item != null) {
            item.setExpanded(true);
            for (TreeItem<JsonNode> child : item.getChildren()) {
                expandAll(child);
            }
        }
    }

    /**
     * Collapses all nodes.
     */
    public void collapseAll() {
        collapseAll(treeView.getRoot());
    }

    private void collapseAll(TreeItem<JsonNode> item) {
        if (item != null) {
            for (TreeItem<JsonNode> child : item.getChildren()) {
                collapseAll(child);
            }
            if (item != treeView.getRoot()) {
                item.setExpanded(false);
            }
        }
    }

    /**
     * Sets the selection change callback.
     */
    public void setOnSelectionChanged(java.util.function.Consumer<JsonNode> callback) {
        this.onSelectionChanged = callback;
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Rebuild tree when document changes
        Platform.runLater(this::rebuildTree);
    }

    // ==================== Tree Cell ====================

    /**
     * Custom tree cell for rendering JSON nodes.
     */
    private static class JsonTreeCell extends TreeCell<JsonNode> {

        @Override
        protected void updateItem(JsonNode node, boolean empty) {
            super.updateItem(node, empty);

            if (empty || node == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }

            // Create icon
            FontIcon icon = new FontIcon(node.getNodeType().getIcon());
            icon.setIconSize(14);
            icon.setIconColor(Color.web(node.getNodeType().getColor()));
            setGraphic(icon);

            // Set text
            setText(node.getDisplayLabel());

            // Set tooltip with path
            Tooltip tooltip = new Tooltip(node.getPath());
            setTooltip(tooltip);

            // Style based on node type
            switch (node.getNodeType()) {
                case OBJECT -> setStyle("-fx-font-weight: bold;");
                case ARRAY -> setStyle("-fx-font-weight: bold;");
                case STRING -> setStyle("-fx-text-fill: #28a745;");
                case NUMBER -> setStyle("-fx-text-fill: #fd7e14;");
                case BOOLEAN -> setStyle("-fx-text-fill: #17a2b8;");
                case NULL -> setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");
                case COMMENT -> setStyle("-fx-text-fill: #198754; -fx-font-style: italic;");
                default -> setStyle(null);
            }
        }
    }
}
