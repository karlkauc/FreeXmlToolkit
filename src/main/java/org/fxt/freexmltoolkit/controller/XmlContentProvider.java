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

/**
 * Interface for providing and manipulating XML content.
 * This allows popup controllers to work with different editor implementations.
 */
public interface XmlContentProvider {

    /**
     * Gets the current XML content from the editor.
     *
     * @return the current XML content
     */
    String getCurrentXmlContent();

    /**
     * Sets the current XML content in the editor.
     *
     * @param content the XML content to set
     */
    void setCurrentXmlContent(String content);

    /**
     * Inserts XML content at the current cursor position.
     *
     * @param content the XML content to insert
     */
    void insertXmlContent(String content);
}
