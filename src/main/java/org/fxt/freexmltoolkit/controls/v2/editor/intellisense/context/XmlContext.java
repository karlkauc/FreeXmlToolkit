package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import java.util.Objects;

/**
 * Represents the XML context at a specific cursor position.
 * Provides all information needed for context-sensitive IntelliSense.
 *
 * <p>This is an immutable value object created by {@link ContextAnalyzer}.</p>
 */
public class XmlContext {

    private final int caretPosition;
    private final String textBeforeCaret;
    private final ContextType type;
    private final String parentElement;
    private final String currentElement;
    private final String currentAttribute;
    private final XPathContext xpathContext;
    private final boolean inComment;
    private final boolean inCData;
    private final int completionStartPosition;

    /**
     * Creates a new XML context.
     *
     * @param builder the builder
     */
    private XmlContext(Builder builder) {
        this.caretPosition = builder.caretPosition;
        this.textBeforeCaret = builder.textBeforeCaret;
        this.type = builder.type;
        this.parentElement = builder.parentElement;
        this.currentElement = builder.currentElement;
        this.currentAttribute = builder.currentAttribute;
        this.xpathContext = builder.xpathContext;
        this.inComment = builder.inComment;
        this.inCData = builder.inCData;
        this.completionStartPosition = builder.completionStartPosition;
    }

    // Getters

    /**
     * Gets the caret position where this context was analyzed.
     *
     * @return the caret position
     */
    public int getCaretPosition() {
        return caretPosition;
    }

    /**
     * Gets the text before the caret position.
     *
     * @return the text before caret
     */
    public String getTextBeforeCaret() {
        return textBeforeCaret;
    }

    /**
     * Gets the context type (e.g., element, attribute, value).
     *
     * @return the context type
     */
    public ContextType getType() {
        return type;
    }

    /**
     * Gets the parent element name at this position.
     *
     * @return the parent element name, or null if not in an element
     */
    public String getParentElement() {
        return parentElement;
    }

    /**
     * Gets the current element name at this position.
     *
     * @return the current element name, or null if not in an element
     */
    public String getCurrentElement() {
        return currentElement;
    }

    /**
     * Gets the current attribute name at this position.
     *
     * @return the current attribute name, or null if not in an attribute
     */
    public String getCurrentAttribute() {
        return currentAttribute;
    }

    /**
     * Gets the XPath context for this position.
     *
     * @return the XPath context, or null if not available
     */
    public XPathContext getXPathContext() {
        return xpathContext;
    }

    /**
     * Checks if the caret is inside a comment.
     *
     * @return true if inside a comment
     */
    public boolean isInComment() {
        return inComment;
    }

    /**
     * Checks if the caret is inside a CDATA section.
     *
     * @return true if inside a CDATA section
     */
    public boolean isInCData() {
        return inCData;
    }

    /**
     * Gets the start position for completion text replacement.
     *
     * @return the completion start position
     */
    public int getCompletionStartPosition() {
        return completionStartPosition;
    }

    // Convenience methods

    /**
     * Checks if IntelliSense should be disabled in this context.
     *
     * @return true if IntelliSense is not applicable
     */
    public boolean shouldDisableIntelliSense() {
        return type.shouldDisableIntelliSense();
    }

    /**
     * Gets the XPath as a string.
     *
     * @return the XPath string, or "/" if no XPath context
     */
    public String getXPath() {
        return xpathContext != null ? xpathContext.getXPath() : "/";
    }

    @Override
    public String toString() {
        return "XmlContext{" +
                "caretPosition=" + caretPosition +
                ", type=" + type +
                ", parentElement='" + parentElement + '\'' +
                ", currentElement='" + currentElement + '\'' +
                ", xpath='" + getXPath() + '\'' +
                ", inComment=" + inComment +
                ", inCData=" + inCData +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlContext that = (XmlContext) o;
        return caretPosition == that.caretPosition &&
                type == that.type &&
                Objects.equals(parentElement, that.parentElement) &&
                Objects.equals(currentElement, that.currentElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caretPosition, type, parentElement, currentElement);
    }

    /**
     * Builder for XmlContext.
     */
    public static class Builder {
        private int caretPosition;
        private String textBeforeCaret = "";
        private ContextType type = ContextType.UNKNOWN;
        private String parentElement;
        private String currentElement;
        private String currentAttribute;
        private XPathContext xpathContext;
        private boolean inComment;
        private boolean inCData;
        private int completionStartPosition;

        /**
         * Sets the caret position.
         *
         * @param caretPosition the caret position
         * @return this builder for chaining
         */
        public Builder caretPosition(int caretPosition) {
            this.caretPosition = caretPosition;
            return this;
        }

        /**
         * Sets the text before the caret.
         *
         * @param textBeforeCaret the text before caret
         * @return this builder for chaining
         */
        public Builder textBeforeCaret(String textBeforeCaret) {
            this.textBeforeCaret = textBeforeCaret;
            return this;
        }

        /**
         * Sets the context type.
         *
         * @param type the context type
         * @return this builder for chaining
         */
        public Builder type(ContextType type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the parent element name.
         *
         * @param parentElement the parent element name
         * @return this builder for chaining
         */
        public Builder parentElement(String parentElement) {
            this.parentElement = parentElement;
            return this;
        }

        /**
         * Sets the current element name.
         *
         * @param currentElement the current element name
         * @return this builder for chaining
         */
        public Builder currentElement(String currentElement) {
            this.currentElement = currentElement;
            return this;
        }

        /**
         * Sets the current attribute name.
         *
         * @param currentAttribute the current attribute name
         * @return this builder for chaining
         */
        public Builder currentAttribute(String currentAttribute) {
            this.currentAttribute = currentAttribute;
            return this;
        }

        /**
         * Sets the XPath context.
         *
         * @param xpathContext the XPath context
         * @return this builder for chaining
         */
        public Builder xpathContext(XPathContext xpathContext) {
            this.xpathContext = xpathContext;
            return this;
        }

        /**
         * Sets whether the caret is inside a comment.
         *
         * @param inComment true if inside a comment
         * @return this builder for chaining
         */
        public Builder inComment(boolean inComment) {
            this.inComment = inComment;
            return this;
        }

        /**
         * Sets whether the caret is inside a CDATA section.
         *
         * @param inCData true if inside a CDATA section
         * @return this builder for chaining
         */
        public Builder inCData(boolean inCData) {
            this.inCData = inCData;
            return this;
        }

        /**
         * Sets the completion start position.
         *
         * @param completionStartPosition the completion start position
         * @return this builder for chaining
         */
        public Builder completionStartPosition(int completionStartPosition) {
            this.completionStartPosition = completionStartPosition;
            return this;
        }

        /**
         * Builds the XmlContext instance.
         *
         * @return the built XmlContext
         */
        public XmlContext build() {
            return new XmlContext(this);
        }
    }
}
