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
 * Used by TemplateManagerPopupController to work with the Unified Editor.
 */
public class UnifiedEditorTemplateAdapter implements XmlContentProvider {

    private final XmlUnifiedTab xmlTab;

    /**
     * Creates a new adapter for the given XmlUnifiedTab.
     *
     * @param xmlTab the XML tab to adapt
     */
    public UnifiedEditorTemplateAdapter(XmlUnifiedTab xmlTab) {
        this.xmlTab = xmlTab;
    }

    @Override
    public String getCurrentXmlContent() {
        return xmlTab.getEditorContent();
    }

    @Override
    public void setCurrentXmlContent(String content) {
        xmlTab.setEditorContent(content);
    }

    @Override
    public void insertXmlContent(String content) {
        xmlTab.insertAtCursor(content);
    }
}
