package org.fxt.freexmltoolkit.domain;

import java.util.Objects;

/**
 * Represents a parameterized snippet parameter with validation and type information.
 * Supports advanced parameter substitution in XPath/XQuery snippets.
 *
 * <p>This class provides a flexible way to define parameters for code snippets,
 * including type information, validation rules, and UI display metadata.
 *
 * <p>Example usage:
 * <pre>{@code
 * SnippetParameter param = SnippetParameter.builder("elementName")
 *     .type(ParameterType.ELEMENT_NAME)
 *     .description("Name of the XML element to search")
 *     .defaultValue("root")
 *     .required(true)
 *     .build();
 * }</pre>
 *
 * @see ParameterType
 * @see ValidationResult
 */
public class SnippetParameter {

    /**
     * Defines the type of a snippet parameter.
     * Each type has a display name, description, and corresponding Java type.
     */
    public enum ParameterType {
        /**
         * A plain text string value.
         */
        STRING("String", "Text value", String.class),

        /**
         * A numeric value (integer or decimal).
         */
        NUMBER("Number", "Numeric value", Number.class),

        /**
         * A boolean value (true or false).
         */
        BOOLEAN("Boolean", "True/false value", Boolean.class),

        /**
         * An XPath expression for querying XML documents.
         */
        XPATH("XPath", "XPath expression", String.class),

        /**
         * A valid XML element name.
         */
        ELEMENT_NAME("Element", "XML element name", String.class),

        /**
         * A valid XML attribute name.
         */
        ATTRIBUTE_NAME("Attribute", "XML attribute name", String.class),

        /**
         * A namespace URI.
         */
        NAMESPACE_URI("Namespace", "Namespace URI", String.class),

        /**
         * An XML node path (e.g., root/child/element).
         */
        NODE_PATH("Node Path", "XML node path", String.class);

        private final String displayName;
        private final String description;
        private final Class<?> javaType;

        /**
         * Creates a new parameter type with the specified display name, description, and Java type.
         *
         * @param displayName the human-readable name for display in the UI
         * @param description a brief description of the parameter type
         * @param javaType    the corresponding Java class for this parameter type
         */
        ParameterType(String displayName, String description, Class<?> javaType) {
            this.displayName = displayName;
            this.description = description;
            this.javaType = javaType;
        }

        /**
         * Returns the human-readable display name for this parameter type.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns a brief description of this parameter type.
         *
         * @return the description
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns the corresponding Java class for this parameter type.
         *
         * @return the Java class representing values of this type
         */
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

    /**
     * Creates a new snippet parameter with default settings.
     * The default type is STRING, required is true, and order is 0.
     */
    public SnippetParameter() {
        this.type = ParameterType.STRING;
        this.required = true;
        this.order = 0;
    }

    /**
     * Creates a new snippet parameter with the specified name, type, and default value.
     *
     * @param name         the parameter name
     * @param type         the parameter type
     * @param defaultValue the default value for the parameter
     */
    public SnippetParameter(String name, ParameterType type, String defaultValue) {
        this();
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a new snippet parameter with the specified name, type, default value, and description.
     *
     * @param name         the parameter name
     * @param type         the parameter type
     * @param defaultValue the default value for the parameter
     * @param description  a description of the parameter's purpose
     */
    public SnippetParameter(String name, ParameterType type, String defaultValue, String description) {
        this(name, type, defaultValue);
        this.description = description;
    }

    // ========== Builder Pattern ==========

    /**
     * Builder class for creating SnippetParameter instances with a fluent API.
     *
     * <p>Example usage:
     * <pre>{@code
     * SnippetParameter param = new Builder("paramName")
     *     .type(ParameterType.STRING)
     *     .description("A sample parameter")
     *     .defaultValue("default")
     *     .required(true)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private final SnippetParameter parameter;

        /**
         * Creates a new builder with the specified parameter name.
         *
         * @param name the name of the parameter to build
         */
        public Builder(String name) {
            this.parameter = new SnippetParameter();
            this.parameter.name = name;
        }

        /**
         * Sets the description for the parameter.
         *
         * @param description the parameter description
         * @return this builder for method chaining
         */
        public Builder description(String description) {
            parameter.description = description;
            return this;
        }

        /**
         * Sets the type for the parameter.
         *
         * @param type the parameter type
         * @return this builder for method chaining
         */
        public Builder type(ParameterType type) {
            parameter.type = type;
            return this;
        }

        /**
         * Sets the default value for the parameter.
         *
         * @param defaultValue the default value
         * @return this builder for method chaining
         */
        public Builder defaultValue(String defaultValue) {
            parameter.defaultValue = defaultValue;
            return this;
        }

        /**
         * Sets whether the parameter is required.
         *
         * @param required true if the parameter is required, false otherwise
         * @return this builder for method chaining
         */
        public Builder required(boolean required) {
            parameter.required = required;
            return this;
        }

        /**
         * Sets a regex validation pattern for the parameter value.
         *
         * @param pattern the regex pattern for validation
         * @return this builder for method chaining
         */
        public Builder validationPattern(String pattern) {
            parameter.validationPattern = pattern;
            return this;
        }

        /**
         * Sets the possible values for the parameter (enum-like constraint).
         *
         * @param values the allowed values for this parameter
         * @return this builder for method chaining
         */
        public Builder possibleValues(String... values) {
            parameter.possibleValues = values.clone();
            return this;
        }

        /**
         * Sets an example value for the parameter.
         *
         * @param example an example value to show in the UI
         * @return this builder for method chaining
         */
        public Builder example(String example) {
            parameter.example = example;
            return this;
        }

        /**
         * Sets the display order for the parameter in the UI.
         *
         * @param order the display order (lower values appear first)
         * @return this builder for method chaining
         */
        public Builder order(int order) {
            parameter.order = order;
            return this;
        }

        /**
         * Builds and returns the configured SnippetParameter.
         *
         * @return the built SnippetParameter instance
         * @throws IllegalStateException if the parameter name is null or empty
         */
        public SnippetParameter build() {
            if (parameter.name == null || parameter.name.trim().isEmpty()) {
                throw new IllegalStateException("Parameter name is required");
            }
            return parameter;
        }
    }

    /**
     * Creates a new builder for constructing a SnippetParameter with the specified name.
     *
     * @param name the name of the parameter to build
     * @return a new Builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ========== Validation Methods ==========

    /**
     * Validates the given parameter value against this parameter's constraints.
     *
     * <p>The validation includes:
     * <ul>
     *   <li>Required field check</li>
     *   <li>Type-specific validation (number, boolean, XML name, URI)</li>
     *   <li>Regex pattern matching (if configured)</li>
     *   <li>Possible values constraint (if configured)</li>
     * </ul>
     *
     * @param value the value to validate
     * @return a ValidationResult containing any errors or warnings
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

    /**
     * Checks if the given value is a valid number.
     *
     * @param value the value to check
     * @return true if the value can be parsed as a double, false otherwise
     */
    private boolean isValidNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks if the given value is a valid boolean.
     *
     * @param value the value to check
     * @return true if the value is "true", "false", "1", or "0" (case-insensitive)
     */
    private boolean isValidBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ||
                "1".equals(value) || "0".equals(value);
    }

    /**
     * Checks if the given value is a valid XML name.
     *
     * <p>XML names must start with a letter or underscore, and subsequent
     * characters can be letters, digits, hyphens, periods, or underscores.
     *
     * @param value the value to check
     * @return true if the value is a valid XML name, false otherwise
     */
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

    /**
     * Checks if the given value is a valid URI.
     *
     * @param value the value to check
     * @return true if the value can be parsed as a URI, false otherwise
     */
    private boolean isValidUri(String value) {
        try {
            java.net.URI.create(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns a formatted name for display in the UI.
     *
     * <p>The formatted name includes the parameter name, optionally followed by
     * the type in parentheses (if not STRING), and an asterisk if required.
     *
     * @return the formatted parameter name for UI display
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
     * Returns tooltip text for this parameter suitable for display in the UI.
     *
     * <p>The tooltip includes the description, type, default value (if set),
     * example (if set), and possible values (if configured).
     *
     * @return the tooltip text for this parameter
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

    /**
     * Returns the name of this parameter.
     *
     * @return the parameter name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this parameter.
     *
     * @param name the parameter name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the description of this parameter.
     *
     * @return the parameter description, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this parameter.
     *
     * @param description the parameter description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the type of this parameter.
     *
     * @return the parameter type
     */
    public ParameterType getType() {
        return type;
    }

    /**
     * Sets the type of this parameter.
     *
     * @param type the parameter type
     */
    public void setType(ParameterType type) {
        this.type = type;
    }

    /**
     * Returns the default value of this parameter.
     *
     * @return the default value, or null if not set
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value of this parameter.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Returns whether this parameter is required.
     *
     * @return true if the parameter is required, false otherwise
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Sets whether this parameter is required.
     *
     * @param required true if the parameter is required, false otherwise
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Returns the regex validation pattern for this parameter.
     *
     * @return the validation pattern, or null if not set
     */
    public String getValidationPattern() {
        return validationPattern;
    }

    /**
     * Sets the regex validation pattern for this parameter.
     *
     * @param validationPattern the regex pattern for validation
     */
    public void setValidationPattern(String validationPattern) {
        this.validationPattern = validationPattern;
    }

    /**
     * Returns the possible values for this parameter.
     *
     * @return an array of possible values, or null if not set
     */
    public String[] getPossibleValues() {
        return possibleValues;
    }

    /**
     * Sets the possible values for this parameter (enum-like constraint).
     *
     * @param possibleValues the allowed values for this parameter
     */
    public void setPossibleValues(String[] possibleValues) {
        this.possibleValues = possibleValues;
    }

    /**
     * Returns the example value for this parameter.
     *
     * @return the example value, or null if not set
     */
    public String getExample() {
        return example;
    }

    /**
     * Sets the example value for this parameter.
     *
     * @param example an example value to show in the UI
     */
    public void setExample(String example) {
        this.example = example;
    }

    /**
     * Returns the display order of this parameter in the UI.
     *
     * @return the display order (lower values appear first)
     */
    public int getOrder() {
        return order;
    }

    /**
     * Sets the display order of this parameter in the UI.
     *
     * @param order the display order (lower values appear first)
     */
    public void setOrder(int order) {
        this.order = order;
    }

    // ========== Object Methods ==========

    /**
     * Checks equality based on the parameter name.
     *
     * @param o the object to compare with
     * @return true if the other object is a SnippetParameter with the same name
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnippetParameter that = (SnippetParameter) o;
        return Objects.equals(name, that.name);
    }

    /**
     * Returns the hash code based on the parameter name.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Returns a string representation of this parameter.
     *
     * @return a string containing the name, type, and required status
     */
    @Override
    public String toString() {
        return String.format("SnippetParameter{name='%s', type=%s, required=%s}",
                name, type, required);
    }

    // ========== Validation Result Helper Class ==========

    /**
     * Represents the result of validating a parameter value.
     * Contains lists of errors and warnings that occurred during validation.
     */
    public static class ValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        /**
         * Creates a new empty ValidationResult.
         * Both error and warning lists are initialized as empty.
         */
        public ValidationResult() {
            // Default constructor - lists are initialized inline
        }

        /**
         * Adds an error message to the validation result.
         *
         * @param error the error message to add
         */
        public void addError(String error) {
            errors.add(error);
        }

        /**
         * Adds a warning message to the validation result.
         *
         * @param warning the warning message to add
         */
        public void addWarning(String warning) {
            warnings.add(warning);
        }

        /**
         * Checks if the validation passed with no errors.
         *
         * @return true if there are no errors, false otherwise
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Checks if the validation produced any warnings.
         *
         * @return true if there are warnings, false otherwise
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        /**
         * Returns a copy of the error messages.
         *
         * @return a list of error messages
         */
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }

        /**
         * Returns a copy of the warning messages.
         *
         * @return a list of warning messages
         */
        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }

        /**
         * Returns all error messages concatenated with semicolons.
         *
         * @return a single string containing all errors, or an empty string if no errors
         */
        public String getErrorMessage() {
            return errors.isEmpty() ? "" : String.join("; ", errors);
        }
    }
}
