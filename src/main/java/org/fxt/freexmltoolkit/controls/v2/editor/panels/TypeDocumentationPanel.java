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

import javafx.fxml.FXML;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

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

    private XsdNode currentNode;

    @FXML private TitledPane documentationPane;
    @FXML private VBox appInfoContent;

    // Documentation UI
    private final InlineDocumentationEditor documentationEditor;

    // AppInfo UI
    private final AppInfoEditorPanel appInfoPanel;

    /**
     * Creates a new TypeDocumentationPanel. The layout is loaded from
     * {@code /pages/views/TypeDocumentationPanel.fxml}; the two editors are added to its placeholders.
     *
     * @param editorContext the editor context for command execution
     */
    public TypeDocumentationPanel(XsdEditorContext editorContext) {
        FxmlViews.load(this, "/pages/views/TypeDocumentationPanel.fxml");

        documentationEditor = new InlineDocumentationEditor(editorContext);
        documentationPane.setContent(documentationEditor);

        appInfoPanel = new AppInfoEditorPanel(editorContext);
        appInfoContent.getChildren().add(appInfoPanel);
        VBox.setVgrow(appInfoPanel, Priority.ALWAYS);
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
