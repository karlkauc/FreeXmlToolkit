package org.fxt.freexmltoolkit.domain;

import java.util.Objects;

/**
 * Represents a parameterized snippet parameter with validation and type information.
 * Supports advanced parameter substitution in XPath/XQuery snippets.
 */
public class SnippetParameter {

    public enum ParameterType {
        STRING("String", "Text value", String.class),
        NUMBER("Number", "Numeric value", Number.class),
        BOOLEAN("Boolean", "True/false value", Boolean.class),
        XPATH("XPath", "XPath expression", String.class),
        ELEMENT_NAME("Element", "XML element name", String.class),
        ATTRIBUTE_NAME("Attribute", "XML attribute name", String.class),
        NAMESPACE_URI("Namespace", "Namespace URI", String.class),
        NODE_PATH("Node Path", "XML node path", String.class);

        private final String displayName;
        private final String description;
        private final Class<?> javaType;

        ParameterType(String displayName, String description, Class<?> javaType) {
            this.displayName = displayName;
            this.description = description;
            this.javaType = javaType;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public Class<?> getJavaType() {
            return javaType;
        }
    }

    private String name;
    private String description;
    private ParameterType type;
    private String defaultValue;
    private boolean required;
    private String validationPattern; // Regex pattern for validation
    private String[] possibleValues; // Enum-like values
    private String example;
    private int order; // Display order in UI

    public SnippetParameter() {
        this.type = ParameterType.STRING;
        this.required = true;
        this.order = 0;
    }

    public SnippetParameter(String name, ParameterType type, String defaultValue) {
        this();
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public SnippetParameter(String name, ParameterType type, String defaultValue, String description) {
        this(name, type, defaultValue);
        this.description = description;
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private final SnippetParameter parameter;

        public Builder(String name) {
            this.parameter = new SnippetParameter();
            this.parameter.name = name;
        }

        public Builder description(String description) {
            parameter.description = description;
            return this;
        }

        public Builder type(ParameterType type) {
            parameter.type = type;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            parameter.defaultValue = defaultValue;
            return this;
        }

        public Builder required(boolean required) {
            parameter.required = required;
            return this;
        }

        public Builder validationPattern(String pattern) {
            parameter.validationPattern = pattern;
            return this;
        }

        public Builder possibleValues(String... values) {
            parameter.possibleValues = values.clone();
            return this;
        }

        public Builder example(String example) {
            parameter.example = example;
            return this;
        }

        public Builder order(int order) {
            parameter.order = order;
            return this;
        }

        public SnippetParameter build() {
            if (parameter.name == null || parameter.name.trim().isEmpty()) {
                throw new IllegalStateException("Parameter name is required");
            }
            return parameter;
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ========== Validation Methods ==========

    /**
     * Validate parameter value
     */
    public ValidationResult validateValue(String value) {
        ValidationResult result = new ValidationResult();

        // Check required
        if (required && (value == null || value.trim().isEmpty())) {
            result.addError("Parameter '" + name + "' is required");
            return result;
        }

        // Skip validation if optional and empty
        if (!required && (value == null || value.trim().isEmpty())) {
            return result;
        }

        // Type-specific validation
        switch (type) {
            case NUMBER:
                if (!isValidNumber(value)) {
                    result.addError("Parameter '" + name + "' must be a valid number");
                }
                break;
            case BOOLEAN:
                if (!isValidBoolean(value)) {
                    result.addError("Parameter '" + name + "' must be true or false");
                }
                break;
            case ELEMENT_NAME:
            case ATTRIBUTE_NAME:
                if (!isValidXmlName(value)) {
                    result.addError("Parameter '" + name + "' must be a valid XML name");
                }
                break;
            case NAMESPACE_URI:
                if (!isValidUri(value)) {
                    result.addError("Parameter '" + name + "' must be a valid URI");
                }
                break;
        }

        // Pattern validation
        if (validationPattern != null && !value.matches(validationPattern)) {
            result.addError("Parameter '" + name + "' does not match required pattern");
        }

        // Possible values validation
        if (possibleValues != null && possibleValues.length > 0) {
            boolean found = false;
            for (String possibleValue : possibleValues) {
                if (possibleValue.equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addError("Parameter '" + name + "' must be one of: " +
                        String.join(", ", possibleValues));
            }
        }

        return result;
    }

    private boolean isValidNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ||
                "1".equals(value) || "0".equals(value);
    }

    private boolean isValidXmlName(String value) {
        if (value == null || value.isEmpty()) return false;

        // Simplified XML name validation
        char first = value.charAt(0);
        if (!Character.isLetter(first) && first != '_') return false;

        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '.' && c != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean isValidUri(String value) {
        try {
            java.net.URI.create(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get formatted parameter for display in UI
     */
    public String getFormattedName() {
        StringBuilder formatted = new StringBuilder(name);
        if (type != ParameterType.STRING) {
            formatted.append(" (").append(type.getDisplayName()).append(")");
        }
        if (required) {
            formatted.append(" *");
        }
        return formatted.toString();
    }

    /**
     * Get parameter tooltip text
     */
    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();

        if (description != null) {
            tooltip.append(description);
        } else {
            tooltip.append("Parameter: ").append(name);
        }

        tooltip.append("\nType: ").append(type.getDisplayName());

        if (defaultValue != null) {
            tooltip.append("\nDefault: ").append(defaultValue);
        }

        if (example != null) {
            tooltip.append("\nExample: ").append(example);
        }

        if (possibleValues != null && possibleValues.length > 0) {
            tooltip.append("\nPossible values: ").append(String.join(", ", possibleValues));
        }

        return tooltip.toString();
    }

    // ========== Getters and Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ParameterType getType() {
        return type;
    }

    public void setType(ParameterType type) {
        this.type = type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getValidationPattern() {
        return validationPattern;
    }

    public void setValidationPattern(String validationPattern) {
        this.validationPattern = validationPattern;
    }

    public String[] getPossibleValues() {
        return possibleValues;
    }

    public void setPossibleValues(String[] possibleValues) {
        this.possibleValues = possibleValues;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    // ========== Object Methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnippetParameter that = (SnippetParameter) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("SnippetParameter{name='%s', type=%s, required=%s}",
                name, type, required);
    }

    // ========== Validation Result Helper Class ==========

    public static class ValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }

        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }

        public String getErrorMessage() {
            return errors.isEmpty() ? "" : String.join("; ", errors);
        }
    }
}