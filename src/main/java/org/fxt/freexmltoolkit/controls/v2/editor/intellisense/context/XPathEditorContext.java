package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable context object representing the cursor position within an XPath/XQuery expression.
 * Used by the completion provider to determine appropriate suggestions.
 */
public class XPathEditorContext {

    private final int caretPosition;
    private final String fullExpression;
    private final String textBeforeCaret;
    private final XPathContextType contextType;
    private final String currentToken;
    private final String precedingToken;
    private final int tokenStartPosition;
    private final boolean isXQuery;
    private final int predicateDepth;
    private final int functionDepth;

    // Path tracking for context-aware element suggestions
    private final List<String> xpathPath;
    private final String lastElementInPath;
    private final boolean isAbsolutePath;
    private final boolean isDescendantPath;

    private XPathEditorContext(Builder builder) {
        this.caretPosition = builder.caretPosition;
        this.fullExpression = builder.fullExpression;
        this.textBeforeCaret = builder.textBeforeCaret;
        this.contextType = builder.contextType;
        this.currentToken = builder.currentToken;
        this.precedingToken = builder.precedingToken;
        this.tokenStartPosition = builder.tokenStartPosition;
        this.isXQuery = builder.isXQuery;
        this.predicateDepth = builder.predicateDepth;
        this.functionDepth = builder.functionDepth;
        this.xpathPath = Collections.unmodifiableList(new ArrayList<>(builder.xpathPath));
        this.lastElementInPath = builder.lastElementInPath;
        this.isAbsolutePath = builder.isAbsolutePath;
        this.isDescendantPath = builder.isDescendantPath;
    }

    /**
     * Gets the caret position in the expression.
     */
    public int getCaretPosition() {
        return caretPosition;
    }

    /**
     * Gets the full expression text.
     */
    public String getFullExpression() {
        return fullExpression;
    }

    /**
     * Gets the text before the caret position.
     */
    public String getTextBeforeCaret() {
        return textBeforeCaret;
    }

    /**
     * Gets the detected context type.
     */
    public XPathContextType getContextType() {
        return contextType;
    }

    /**
     * Gets the current token being typed (partial text for filtering).
     */
    public String getCurrentToken() {
        return currentToken;
    }

    /**
     * Gets the token preceding the current token.
     */
    public String getPrecedingToken() {
        return precedingToken;
    }

    /**
     * Gets the start position of the current token (for replacement).
     */
    public int getTokenStartPosition() {
        return tokenStartPosition;
    }

    /**
     * Returns true if in XQuery mode (supports FLWOR expressions).
     */
    public boolean isXQuery() {
        return isXQuery;
    }

    /**
     * Gets the current predicate nesting depth (number of unclosed '[').
     */
    public int getPredicateDepth() {
        return predicateDepth;
    }

    /**
     * Gets the current function nesting depth (number of unclosed '(').
     */
    public int getFunctionDepth() {
        return functionDepth;
    }

    /**
     * Returns true if currently inside a predicate.
     */
    public boolean isInPredicate() {
        return predicateDepth > 0;
    }

    /**
     * Returns true if currently inside function arguments.
     */
    public boolean isInFunctionArgs() {
        return functionDepth > 0;
    }

    /**
     * Returns the length of the current token for replacement calculation.
     */
    public int getCurrentTokenLength() {
        return currentToken != null ? currentToken.length() : 0;
    }

    /**
     * Gets the XPath path elements before the cursor position.
     * For "/root/child/", this returns ["root", "child"].
     *
     * @return unmodifiable list of path element names
     */
    public List<String> getXpathPath() {
        return xpathPath;
    }

    /**
     * Gets the last element in the XPath path.
     * For "/root/child/", this returns "child".
     *
     * @return the last element name, or null if path is empty
     */
    public String getLastElementInPath() {
        return lastElementInPath;
    }

    /**
     * Returns true if this is an absolute path (starts with /).
     */
    public boolean isAbsolutePath() {
        return isAbsolutePath;
    }

    /**
     * Returns true if this is a descendant-or-self path (starts with //).
     */
    public boolean isDescendantPath() {
        return isDescendantPath;
    }

    /**
     * Returns true if the path is at the root level (just "/" with no elements yet).
     */
    public boolean isAtRoot() {
        return isAbsolutePath && xpathPath.isEmpty();
    }

    @Override
    public String toString() {
        return "XPathEditorContext{" +
                "caretPosition=" + caretPosition +
                ", contextType=" + contextType +
                ", currentToken='" + currentToken + '\'' +
                ", isXQuery=" + isXQuery +
                ", predicateDepth=" + predicateDepth +
                ", functionDepth=" + functionDepth +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XPathEditorContext that = (XPathEditorContext) o;
        return caretPosition == that.caretPosition &&
                isXQuery == that.isXQuery &&
                contextType == that.contextType &&
                Objects.equals(currentToken, that.currentToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caretPosition, contextType, currentToken, isXQuery);
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for XPathEditorContext.
     */
    public static class Builder {
        private int caretPosition;
        private String fullExpression = "";
        private String textBeforeCaret = "";
        private XPathContextType contextType = XPathContextType.UNKNOWN;
        private String currentToken = "";
        private String precedingToken = "";
        private int tokenStartPosition;
        private boolean isXQuery = false;
        private int predicateDepth = 0;
        private int functionDepth = 0;
        private List<String> xpathPath = new ArrayList<>();
        private String lastElementInPath = null;
        private boolean isAbsolutePath = false;
        private boolean isDescendantPath = false;

        public Builder caretPosition(int caretPosition) {
            this.caretPosition = caretPosition;
            return this;
        }

        public Builder fullExpression(String fullExpression) {
            this.fullExpression = fullExpression != null ? fullExpression : "";
            return this;
        }

        public Builder textBeforeCaret(String textBeforeCaret) {
            this.textBeforeCaret = textBeforeCaret != null ? textBeforeCaret : "";
            return this;
        }

        public Builder contextType(XPathContextType contextType) {
            this.contextType = contextType != null ? contextType : XPathContextType.UNKNOWN;
            return this;
        }

        public Builder currentToken(String currentToken) {
            this.currentToken = currentToken != null ? currentToken : "";
            return this;
        }

        public Builder precedingToken(String precedingToken) {
            this.precedingToken = precedingToken != null ? precedingToken : "";
            return this;
        }

        public Builder tokenStartPosition(int tokenStartPosition) {
            this.tokenStartPosition = tokenStartPosition;
            return this;
        }

        public Builder isXQuery(boolean isXQuery) {
            this.isXQuery = isXQuery;
            return this;
        }

        public Builder predicateDepth(int predicateDepth) {
            this.predicateDepth = Math.max(0, predicateDepth);
            return this;
        }

        public Builder functionDepth(int functionDepth) {
            this.functionDepth = Math.max(0, functionDepth);
            return this;
        }

        public Builder xpathPath(List<String> xpathPath) {
            this.xpathPath = xpathPath != null ? new ArrayList<>(xpathPath) : new ArrayList<>();
            this.lastElementInPath = this.xpathPath.isEmpty() ? null : this.xpathPath.get(this.xpathPath.size() - 1);
            return this;
        }

        public Builder lastElementInPath(String lastElementInPath) {
            this.lastElementInPath = lastElementInPath;
            return this;
        }

        public Builder isAbsolutePath(boolean isAbsolutePath) {
            this.isAbsolutePath = isAbsolutePath;
            return this;
        }

        public Builder isDescendantPath(boolean isDescendantPath) {
            this.isDescendantPath = isDescendantPath;
            return this;
        }

        public XPathEditorContext build() {
            return new XPathEditorContext(this);
        }
    }
}
