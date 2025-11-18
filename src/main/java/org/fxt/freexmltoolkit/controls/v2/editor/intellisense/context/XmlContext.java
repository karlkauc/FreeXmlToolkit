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

    public int getCaretPosition() {
        return caretPosition;
    }

    public String getTextBeforeCaret() {
        return textBeforeCaret;
    }

    public ContextType getType() {
        return type;
    }

    public String getParentElement() {
        return parentElement;
    }

    public String getCurrentElement() {
        return currentElement;
    }

    public String getCurrentAttribute() {
        return currentAttribute;
    }

    public XPathContext getXPathContext() {
        return xpathContext;
    }

    public boolean isInComment() {
        return inComment;
    }

    public boolean isInCData() {
        return inCData;
    }

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

        public Builder caretPosition(int caretPosition) {
            this.caretPosition = caretPosition;
            return this;
        }

        public Builder textBeforeCaret(String textBeforeCaret) {
            this.textBeforeCaret = textBeforeCaret;
            return this;
        }

        public Builder type(ContextType type) {
            this.type = type;
            return this;
        }

        public Builder parentElement(String parentElement) {
            this.parentElement = parentElement;
            return this;
        }

        public Builder currentElement(String currentElement) {
            this.currentElement = currentElement;
            return this;
        }

        public Builder currentAttribute(String currentAttribute) {
            this.currentAttribute = currentAttribute;
            return this;
        }

        public Builder xpathContext(XPathContext xpathContext) {
            this.xpathContext = xpathContext;
            return this;
        }

        public Builder inComment(boolean inComment) {
            this.inComment = inComment;
            return this;
        }

        public Builder inCData(boolean inCData) {
            this.inCData = inCData;
            return this;
        }

        public Builder completionStartPosition(int completionStartPosition) {
            this.completionStartPosition = completionStartPosition;
            return this;
        }

        public XmlContext build() {
            return new XmlContext(this);
        }
    }
}
