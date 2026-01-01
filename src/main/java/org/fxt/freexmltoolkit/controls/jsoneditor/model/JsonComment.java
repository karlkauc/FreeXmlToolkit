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
 * Represents a comment in JSONC or JSON5.
 * Standard JSON does not support comments, but they are preserved for editing.
 */
public class JsonComment extends JsonNode {

    private String text;
    private boolean isLineComment; // true for //, false for /* */

    public JsonComment() {
        this.text = "";
        this.isLineComment = true;
    }

    public JsonComment(String text, boolean isLineComment) {
        this.text = text != null ? text : "";
        this.isLineComment = isLineComment;
    }

    /**
     * Creates a line comment (//).
     */
    public static JsonComment lineComment(String text) {
        return new JsonComment(text, true);
    }

    /**
     * Creates a block comment (multi-line).
     */
    public static JsonComment blockComment(String text) {
        return new JsonComment(text, false);
    }

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.COMMENT;
    }

    /**
     * Gets the comment text (without the delimiters).
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the comment text.
     */
    public void setText(String text) {
        String oldText = this.text;
        this.text = text != null ? text : "";
        pcs.firePropertyChange("text", oldText, this.text);
    }

    /**
     * Checks if this is a line comment (//).
     */
    public boolean isLineComment() {
        return isLineComment;
    }

    /**
     * Sets whether this is a line comment.
     */
    public void setLineComment(boolean lineComment) {
        boolean old = this.isLineComment;
        this.isLineComment = lineComment;
        pcs.firePropertyChange("lineComment", old, lineComment);
    }

    /**
     * Checks if this is a block comment.
     */
    public boolean isBlockComment() {
        return !isLineComment;
    }

    @Override
    public JsonNode deepCopy() {
        JsonComment copy = new JsonComment(text, isLineComment);
        copy.setKey(getKey());
        return copy;
    }

    @Override
    public String serialize(int indent, int currentIndent) {
        if (isLineComment) {
            return "// " + text;
        } else {
            return "/* " + text + " */";
        }
    }

    @Override
    public String getDisplayLabel() {
        if (text.length() > 50) {
            return (isLineComment ? "// " : "/* ") + text.substring(0, 47) + "...";
        }
        return (isLineComment ? "// " : "/* ") + text;
    }

    @Override
    public String getValueAsString() {
        return text;
    }
}
