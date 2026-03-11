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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Reusable panel for editing type documentation (multi-language) and AppInfo.
 * Can be used in both SimpleType and ComplexType editors.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Inline tab-based multi-language documentation editor</li>
 *   <li>Integrated AppInfoEditorPanel for structured metadata</li>
 *   <li>Command pattern support for undo/redo</li>
 * </ul>
 *
 * @since 2.0
 */
public class TypeDocumentationPanel extends BorderPane {

    private static final Logger logger = LogManager.getLogger(TypeDocumentationPanel.class);

    private final XsdEditorContext editorContext;
    private XsdNode currentNode;

    // Documentation UI
    private InlineDocumentationEditor documentationEditor;

    // AppInfo UI
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
        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));

        // Documentation TitledPane with inline editor
        documentationEditor = new InlineDocumentationEditor(editorContext);
        TitledPane documentationPane = new TitledPane("Documentation", documentationEditor);
        documentationPane.setExpanded(true);
        documentationPane.setGraphic(new FontIcon("bi-file-text"));

        // AppInfo TitledPane with embedded AppInfoEditorPanel
        TitledPane appInfoPane = createAppInfoPane();
        appInfoPane.setExpanded(false);

        contentBox.getChildren().addAll(documentationPane, appInfoPane);

        // Wrap in ScrollPane for scrollability
        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        setCenter(scrollPane);
    }

    /**
     * Creates the AppInfo pane with embedded AppInfoEditorPanel.
     *
     * @return the AppInfo TitledPane
     */
    private TitledPane createAppInfoPane() {
        VBox content = new VBox(5);
        content.setPadding(new Insets(5));

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
        this.currentNode = node;
        documentationEditor.setNode(node);
        appInfoPanel.setNode(node);
    }

    /**
     * Sets the edit mode.
     *
     * @param editMode true to enable editing, false for read-only
     */
    public void setEditMode(boolean editMode) {
        documentationEditor.setEditMode(editMode);
    }

    /**
     * Refreshes the panel from the model.
     */
    public void refresh() {
        documentationEditor.refresh();
        appInfoPanel.setNode(currentNode);
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
