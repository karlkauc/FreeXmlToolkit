package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.List;

/**
 * Represents a single completion item in the IntelliSense popup.
 * Contains all information needed for rich display and insertion.
 */
public class CompletionItem {

    private String label;                    // Display text
    private String insertText;               // Text to insert
    private CompletionItemType type;         // Element, Attribute, Text, Snippet
    private String description;              // XSD documentation
    private String dataType;                 // xs:string, xs:int, etc.
    private String defaultValue;             // Default value from XSD
    private List<String> requiredAttributes; // Required attributes for elements
    private List<String> optionalAttributes; // Optional attributes for elements
    private String constraints;              // Constraints description
    private String shortcut;                 // Keyboard shortcut hint
    private boolean required;                // Is this item required?
    private int relevanceScore;              // For sorting by relevance
    private String xPath;                    // XPath context
    private String namespace;                // XML namespace
    private String prefix;                   // Namespace prefix

    // Constructor
    public CompletionItem(String label, String insertText, CompletionItemType type) {
        this.label = label;
        this.insertText = insertText;
        this.type = type;
        this.relevanceScore = 100; // Default score
        this.required = false;
    }

    // Builder pattern for fluent API
    public static class Builder {
        private final CompletionItem item;

        public Builder(String label, String insertText, CompletionItemType type) {
            item = new CompletionItem(label, insertText, type);
        }

        public Builder description(String description) {
            item.description = description;
            return this;
        }

        public Builder dataType(String dataType) {
            item.dataType = dataType;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            item.defaultValue = defaultValue;
            return this;
        }

        public Builder requiredAttributes(List<String> attributes) {
            item.requiredAttributes = attributes;
            return this;
        }

        public Builder optionalAttributes(List<String> attributes) {
            item.optionalAttributes = attributes;
            return this;
        }

        public Builder constraints(String constraints) {
            item.constraints = constraints;
            return this;
        }

        public Builder shortcut(String shortcut) {
            item.shortcut = shortcut;
            return this;
        }

        public Builder required(boolean required) {
            item.required = required;
            return this;
        }

        public Builder relevanceScore(int score) {
            item.relevanceScore = score;
            return this;
        }

        public Builder xPath(String xPath) {
            item.xPath = xPath;
            return this;
        }

        public Builder namespace(String namespace, String prefix) {
            item.namespace = namespace;
            item.prefix = prefix;
            return this;
        }

        public CompletionItem build() {
            return item;
        }
    }

    // Getters and setters
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getInsertText() {
        return insertText;
    }

    public void setInsertText(String insertText) {
        this.insertText = insertText;
    }

    public CompletionItemType getType() {
        return type;
    }

    public void setType(CompletionItemType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<String> getRequiredAttributes() {
        return requiredAttributes;
    }

    public void setRequiredAttributes(List<String> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
    }

    public List<String> getOptionalAttributes() {
        return optionalAttributes;
    }

    public void setOptionalAttributes(List<String> optionalAttributes) {
        this.optionalAttributes = optionalAttributes;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public String getShortcut() {
        return shortcut;
    }

    public void setShortcut(String shortcut) {
        this.shortcut = shortcut;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(int relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public String getXPath() {
        return xPath;
    }

    public void setXPath(String xPath) {
        this.xPath = xPath;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toString() {
        return label;
    }
}

// CompletionItemType moved to separate file