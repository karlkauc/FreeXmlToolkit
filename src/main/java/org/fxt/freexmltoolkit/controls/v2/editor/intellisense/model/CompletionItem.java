package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // Extended fields for enhanced IntelliSense display
    private final String cardinality;              // e.g., "1", "0..1", "1..*", "0..*"
    private final String defaultValue;             // Default value from XSD
    private final List<String> facetHints;         // e.g., ["pattern", "maxLength:100"]
    private final List<String> requiredAttributes; // Required attributes for ELEMENT type
    private final List<String> optionalAttributes; // Optional attributes for ELEMENT type
    private final String namespace;                // Namespace URI
    private final String prefix;                   // Namespace prefix
    private final List<String> examples;           // Example values

    private CompletionItem(Builder builder) {
        this.label = builder.label;
        this.insertText = builder.insertText;
        this.type = builder.type;
        this.description = builder.description;
        this.dataType = builder.dataType;
        this.required = builder.required;
        this.relevanceScore = builder.relevanceScore;

        // Extended fields (immutable lists)
        this.cardinality = builder.cardinality;
        this.defaultValue = builder.defaultValue;
        this.facetHints = Collections.unmodifiableList(new ArrayList<>(builder.facetHints));
        this.requiredAttributes = Collections.unmodifiableList(new ArrayList<>(builder.requiredAttributes));
        this.optionalAttributes = Collections.unmodifiableList(new ArrayList<>(builder.optionalAttributes));
        this.namespace = builder.namespace;
        this.prefix = builder.prefix;
        this.examples = Collections.unmodifiableList(new ArrayList<>(builder.examples));
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

    // Extended getters

    /**
     * Gets the cardinality string (e.g., "1", "0..1", "1..*", "0..*").
     */
    public String getCardinality() {
        return cardinality;
    }

    /**
     * Gets the default value from XSD.
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets facet hints (e.g., ["pattern", "maxLength:100"]).
     */
    public List<String> getFacetHints() {
        return facetHints;
    }

    /**
     * Gets required attributes for ELEMENT type items.
     */
    public List<String> getRequiredAttributes() {
        return requiredAttributes;
    }

    /**
     * Gets optional attributes for ELEMENT type items.
     */
    public List<String> getOptionalAttributes() {
        return optionalAttributes;
    }

    /**
     * Gets the namespace URI.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the namespace prefix.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Gets example values.
     */
    public List<String> getExamples() {
        return examples;
    }

    /**
     * Checks if this item has extended information to display.
     */
    public boolean hasExtendedInfo() {
        return (cardinality != null && !cardinality.isEmpty()) ||
               (defaultValue != null && !defaultValue.isEmpty()) ||
               !facetHints.isEmpty() ||
               !requiredAttributes.isEmpty() ||
               !examples.isEmpty();
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

        // Extended fields
        private String cardinality = "";
        private String defaultValue = "";
        private List<String> facetHints = new ArrayList<>();
        private List<String> requiredAttributes = new ArrayList<>();
        private List<String> optionalAttributes = new ArrayList<>();
        private String namespace = "";
        private String prefix = "";
        private List<String> examples = new ArrayList<>();

        public Builder(String label, String insertText, CompletionItemType type) {
            this.label = Objects.requireNonNull(label, "Label cannot be null");
            this.insertText = insertText != null ? insertText : label;
            this.type = Objects.requireNonNull(type, "Type cannot be null");
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder dataType(String dataType) {
            this.dataType = dataType != null ? dataType : "";
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

        // Extended builder methods

        /**
         * Sets the cardinality string (e.g., "1", "0..1", "1..*", "0..*").
         */
        public Builder cardinality(String cardinality) {
            this.cardinality = cardinality != null ? cardinality : "";
            return this;
        }

        /**
         * Sets the default value from XSD.
         */
        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue != null ? defaultValue : "";
            return this;
        }

        /**
         * Sets facet hints (e.g., ["pattern", "maxLength:100"]).
         */
        public Builder facetHints(List<String> facetHints) {
            this.facetHints = facetHints != null ? new ArrayList<>(facetHints) : new ArrayList<>();
            return this;
        }

        /**
         * Sets required attributes for ELEMENT type items.
         */
        public Builder requiredAttributes(List<String> requiredAttributes) {
            this.requiredAttributes = requiredAttributes != null ? new ArrayList<>(requiredAttributes) : new ArrayList<>();
            return this;
        }

        /**
         * Sets optional attributes for ELEMENT type items.
         */
        public Builder optionalAttributes(List<String> optionalAttributes) {
            this.optionalAttributes = optionalAttributes != null ? new ArrayList<>(optionalAttributes) : new ArrayList<>();
            return this;
        }

        /**
         * Sets the namespace URI.
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace != null ? namespace : "";
            return this;
        }

        /**
         * Sets the namespace prefix.
         */
        public Builder prefix(String prefix) {
            this.prefix = prefix != null ? prefix : "";
            return this;
        }

        /**
         * Sets example values.
         */
        public Builder examples(List<String> examples) {
            this.examples = examples != null ? new ArrayList<>(examples) : new ArrayList<>();
            return this;
        }

        public CompletionItem build() {
            return new CompletionItem(this);
        }
    }
}
