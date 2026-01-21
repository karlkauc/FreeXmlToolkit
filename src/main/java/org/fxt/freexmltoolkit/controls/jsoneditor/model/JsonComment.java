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

    /**
     * Creates a new empty line comment.
     * The comment text will be empty and it will default to a line comment style.
     */
    public JsonComment() {
        this.text = "";
        this.isLineComment = true;
    }

    /**
     * Creates a new comment with the specified text and style.
     *
     * @param text the comment text without delimiters, or null for empty text
     * @param isLineComment true for line comment (//), false for block comment
     */
    public JsonComment(String text, boolean isLineComment) {
        this.text = text != null ? text : "";
        this.isLineComment = isLineComment;
    }

    /**
     * Creates a line comment (//) with the specified text.
     *
     * @param text the comment text without the // delimiter
     * @return a new JsonComment configured as a line comment
     */
    public static JsonComment lineComment(String text) {
        return new JsonComment(text, true);
    }

    /**
     * Creates a block comment with the specified text.
     * Block comments can span multiple lines.
     *
     * @param text the comment text without the delimiters
     * @return a new JsonComment configured as a block comment
     */
    public static JsonComment blockComment(String text) {
        return new JsonComment(text, false);
    }

    /**
     * Returns the node type for this comment node.
     *
     * @return {@link JsonNodeType#COMMENT}
     */
    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.COMMENT;
    }

    /**
     * Gets the comment text without the delimiters.
     * For a line comment "// hello", this returns "hello".
     * For a block comment, this also returns just the text content.
     *
     * @return the comment text without delimiters, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the comment text.
     * The text should not include the comment delimiters.
     * Fires a property change event for "text".
     *
     * @param text the new comment text, or null to set empty text
     */
    public void setText(String text) {
        String oldText = this.text;
        this.text = text != null ? text : "";
        pcs.firePropertyChange("text", oldText, this.text);
    }

    /**
     * Checks if this is a line comment (//).
     * Line comments extend from the // delimiter to the end of the line.
     *
     * @return true if this is a line comment, false if it is a block comment
     */
    public boolean isLineComment() {
        return isLineComment;
    }

    /**
     * Sets whether this is a line comment or block comment.
     * Fires a property change event for "lineComment".
     *
     * @param lineComment true for line comment (//), false for block comment
     */
    public void setLineComment(boolean lineComment) {
        boolean old = this.isLineComment;
        this.isLineComment = lineComment;
        pcs.firePropertyChange("lineComment", old, lineComment);
    }

    /**
     * Checks if this is a block comment.
     * Block comments can span multiple lines.
     *
     * @return true if this is a block comment, false if it is a line comment
     */
    public boolean isBlockComment() {
        return !isLineComment;
    }

    /**
     * Creates a deep copy of this comment node.
     * The copy includes the text, comment style, and key.
     *
     * @return a new JsonComment instance with the same properties
     */
    @Override
    public JsonNode deepCopy() {
        JsonComment copy = new JsonComment(text, isLineComment);
        copy.setKey(getKey());
        return copy;
    }

    /**
     * Serializes this comment to its JSON/JSONC string representation.
     * Line comments are serialized with // prefix and block comments with delimiters.
     *
     * @param indent the number of spaces per indentation level (unused for comments)
     * @param currentIndent the current indentation level (unused for comments)
     * @return the serialized comment string with appropriate delimiters
     */
    @Override
    public String serialize(int indent, int currentIndent) {
        if (isLineComment) {
            return "// " + text;
        } else {
            return "/* " + text + " */";
        }
    }

    /**
     * Gets a display label for this comment suitable for UI presentation.
     * Long comments are truncated to 50 characters with an ellipsis.
     *
     * @return the display label including the comment delimiter prefix
     */
    @Override
    public String getDisplayLabel() {
        if (text.length() > 50) {
            return (isLineComment ? "// " : "/* ") + text.substring(0, 47) + "...";
        }
        return (isLineComment ? "// " : "/* ") + text;
    }

    /**
     * Gets the comment text as a string value.
     * This returns the raw comment text without delimiters.
     *
     * @return the comment text, never null
     */
    @Override
    public String getValueAsString() {
        return text;
    }
}
