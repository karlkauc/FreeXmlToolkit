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

package org.fxt.freexmltoolkit.controls.jsoneditor.model;

import java.io.File;

/**
 * Represents the root JSON document.
 * Contains the root value (object, array, or primitive).
 */
public class JsonDocument extends JsonNode {

    private File sourceFile;
    private String format = "json"; // json, jsonc, json5

    /**
     * Creates a new empty JSON document.
     */
    public JsonDocument() {
    }

    /**
     * Creates a new JSON document with the specified root value.
     *
     * @param rootValue the root JSON node (object, array, or primitive)
     */
    public JsonDocument(JsonNode rootValue) {
        if (rootValue != null) {
            addChild(rootValue);
        }
    }

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.DOCUMENT;
    }

    /**
     * Gets the root value of the document.
     * The root value is the first child node of this document, which can be
     * an object, array, or primitive JSON value.
     *
     * @return the root JSON node, or null if the document is empty
     */
    public JsonNode getRootValue() {
        return hasChildren() ? getChild(0) : null;
    }

    /**
     * Sets the root value of this JSON document.
     * Replaces any existing root value with the specified node.
     * Fires a property change event for the "rootValue" property.
     *
     * @param rootValue the JSON node to set as the root value, or null to clear
     */
    public void setRootValue(JsonNode rootValue) {
        children.clear();
        if (rootValue != null) {
            addChild(rootValue);
        }
        pcs.firePropertyChange("rootValue", null, rootValue);
    }

    /**
     * Gets the source file from which this document was loaded.
     * Returns null if the document was created programmatically or not yet saved.
     *
     * @return the source file, or null if not associated with a file
     */
    public File getSourceFile() {
        return sourceFile;
    }

    /**
     * Sets the source file associated with this document.
     * Fires a property change event for the "sourceFile" property.
     *
     * @param sourceFile the file to associate with this document, or null to clear
     */
    public void setSourceFile(File sourceFile) {
        File oldFile = this.sourceFile;
        this.sourceFile = sourceFile;
        pcs.firePropertyChange("sourceFile", oldFile, sourceFile);
    }

    /**
     * Gets the detected JSON format of this document.
     * Possible values are "json" (standard JSON), "jsonc" (JSON with comments),
     * or "json5" (JSON5 extended format).
     *
     * @return the format string, defaults to "json"
     */
    public String getFormat() {
        return format;
    }

    /**
     * Sets the JSON format of this document.
     * Fires a property change event for the "format" property.
     *
     * @param format the format string ("json", "jsonc", or "json5")
     */
    public void setFormat(String format) {
        String oldFormat = this.format;
        this.format = format;
        pcs.firePropertyChange("format", oldFormat, format);
    }

    @Override
    public JsonNode deepCopy() {
        JsonDocument copy = new JsonDocument();
        copy.setFormat(format);
        JsonNode root = getRootValue();
        if (root != null) {
            copy.setRootValue(root.deepCopy());
        }
        return copy;
    }

    @Override
    public String serialize(int indent, int currentIndent) {
        JsonNode root = getRootValue();
        if (root != null) {
            return root.serialize(indent, currentIndent);
        }
        return "";
    }

    @Override
    public String getDisplayLabel() {
        if (sourceFile != null) {
            return sourceFile.getName();
        }
        return "Document";
    }

    @Override
    public String getValueAsString() {
        return format.toUpperCase() + " Document";
    }
}
