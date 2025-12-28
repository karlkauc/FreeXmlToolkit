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

package org.fxt.freexmltoolkit.controller;

import org.fxt.freexmltoolkit.controls.unified.XmlUnifiedTab;

/**
 * Adapter class that bridges XmlUnifiedTab to XmlContentProvider interface.
 * Used by SchemaGeneratorPopupController to work with the Unified Editor.
 */
public class UnifiedEditorGeneratorAdapter implements XmlContentProvider {

    private final XmlUnifiedTab xmlTab;
    private final UnifiedEditorController editorController;

    /**
     * Creates a new adapter for the given XmlUnifiedTab and controller.
     *
     * @param xmlTab           the XML tab to adapt
     * @param editorController the parent controller for opening new tabs
     */
    public UnifiedEditorGeneratorAdapter(XmlUnifiedTab xmlTab, UnifiedEditorController editorController) {
        this.xmlTab = xmlTab;
        this.editorController = editorController;
    }

    @Override
    public String getCurrentXmlContent() {
        return xmlTab.getEditorContent();
    }

    @Override
    public void setCurrentXmlContent(String content) {
        // The generator doesn't typically set content back to the XML tab,
        // but we implement it for interface compliance
        xmlTab.setEditorContent(content);
    }

    @Override
    public void insertXmlContent(String content) {
        // The generator doesn't typically insert content,
        // but we implement it for interface compliance
        xmlTab.insertAtCursor(content);
    }

    /**
     * Opens a new XSD tab with the generated schema content.
     *
     * @param xsdContent the generated XSD content
     */
    public void openGeneratedSchema(String xsdContent) {
        if (editorController != null && xsdContent != null && !xsdContent.isEmpty()) {
            editorController.openNewXsdTab(xsdContent);
        }
    }
}
