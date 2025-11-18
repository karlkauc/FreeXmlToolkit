package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model;

import java.util.Objects;

/**
 * Represents a completion item for IntelliSense.
 * Immutable value object.
 */
public class CompletionItem {

    private final String label;
    private final String insertText;
    private final CompletionItemType type;
    private final String description;
    private final String dataType;
    private final boolean required;
    private final int relevanceScore;

    private CompletionItem(Builder builder) {
        this.label = builder.label;
        this.insertText = builder.insertText;
        this.type = builder.type;
        this.description = builder.description;
        this.dataType = builder.dataType;
        this.required = builder.required;
        this.relevanceScore = builder.relevanceScore;
    }

    public String getLabel() {
        return label;
    }

    public String getInsertText() {
        return insertText;
    }

    public CompletionItemType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getDataType() {
        return dataType;
    }

    public boolean isRequired() {
        return required;
    }

    public int getRelevanceScore() {
        return relevanceScore;
    }

    @Override
    public String toString() {
        return "CompletionItem{" +
                "label='" + label + '\'' +
                ", type=" + type +
                ", required=" + required +
                ", score=" + relevanceScore +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompletionItem that = (CompletionItem) o;
        return Objects.equals(label, that.label) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, type);
    }

    /**
     * Builder for CompletionItem.
     */
    public static class Builder {
        private final String label;
        private String insertText;
        private final CompletionItemType type;
        private String description = "";
        private String dataType = "";
        private boolean required = false;
        private int relevanceScore = 100;

        public Builder(String label, String insertText, CompletionItemType type) {
            this.label = Objects.requireNonNull(label, "Label cannot be null");
            this.insertText = insertText != null ? insertText : label;
            this.type = Objects.requireNonNull(type, "Type cannot be null");
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder dataType(String dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder relevanceScore(int score) {
            this.relevanceScore = score;
            return this;
        }

        public CompletionItem build() {
            return new CompletionItem(this);
        }
    }
}
