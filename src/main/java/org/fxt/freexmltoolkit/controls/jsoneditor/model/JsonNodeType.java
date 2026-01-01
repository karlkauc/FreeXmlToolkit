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

/**
 * Enumeration of JSON node types.
 */
public enum JsonNodeType {
    /**
     * The root document node.
     */
    DOCUMENT("bi-file-earmark-code", "#343a40"),

    /**
     * An object node (key-value pairs).
     */
    OBJECT("bi-braces", "#007bff"),

    /**
     * An array node (ordered list of values).
     */
    ARRAY("bi-list-ol", "#28a745"),

    /**
     * A string value.
     */
    STRING("bi-chat-quote", "#6c757d"),

    /**
     * A number value (integer or floating point).
     */
    NUMBER("bi-123", "#fd7e14"),

    /**
     * A boolean value (true or false).
     */
    BOOLEAN("bi-toggle-on", "#17a2b8"),

    /**
     * A null value.
     */
    NULL("bi-dash-circle", "#6c757d"),

    /**
     * A comment (JSONC/JSON5 only).
     */
    COMMENT("bi-chat-left-text", "#198754");

    private final String icon;
    private final String color;

    JsonNodeType(String icon, String color) {
        this.icon = icon;
        this.color = color;
    }

    /**
     * Gets the icon literal for this node type.
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Gets the color for this node type.
     */
    public String getColor() {
        return color;
    }
}
