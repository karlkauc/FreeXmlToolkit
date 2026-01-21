package org.fxt.freexmltoolkit.domain;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Flexible template parameter system for XML templates.
 * Supports various parameter types, validation, and dynamic value generation.
 *
 * <p>This class provides a comprehensive parameter definition system that can be used
 * to define template placeholders with type information, validation rules, default values,
 * and UI configuration for rendering input forms.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Multiple parameter types (string, integer, decimal, boolean, date, email, URL, enum)</li>
 *   <li>Validation with regex patterns, length constraints, and value ranges</li>
 *   <li>Dynamic value generation based on other parameters</li>
 *   <li>Conditional default values</li>
 *   <li>Multi-value support with configurable separators</li>
 *   <li>UI configuration for rendering appropriate input controls</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * TemplateParameter param = TemplateParameter.stringParam("userName")
 *     .displayName("User Name")
 *     .required(true)
 *     .length(3, 50)
 *     .pattern("[a-zA-Z0-9_]+");
 * }</pre>
 */
public class TemplateParameter {

    // Core parameter information
    private String name;
    private String displayName;
    private String description;
    private ParameterType type = ParameterType.STRING;
    private String defaultValue;
    private boolean required = false;

    // Validation and constraints
    private List<String> allowedValues = new ArrayList<>();
    private String validationPattern; // Regex pattern
    private Pattern compiledPattern;
    private Integer minLength;
    private Integer maxLength;
    private Double minValue;
    private Double maxValue;
    private List<ParameterConstraint> constraints = new ArrayList<>();

    // UI configuration
    private InputType inputType = InputType.TEXT_FIELD;
    private String placeholder;
    private String helpText;
    private boolean sensitive = false; // For passwords, etc.
    private int displayOrder = 0;
    private String category = "General";

    // Dynamic value generation
    private ValueGenerator valueGenerator;
    private List<String> dependsOn = new ArrayList<>(); // Other parameters this depends on
    private Map<String, String> conditionalDefaults = new HashMap<>();

    // Advanced features
    private boolean multiValue = false;
    private String separator = ",";
    private String valuePrefix = "";
    private String valueSuffix = "";
    private boolean autoGenerate = false;

    // Metadata
    private LocalDateTime created = LocalDateTime.now();
    private LocalDateTime lastModified = LocalDateTime.now();
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Default constructor that creates an empty template parameter.
     * The parameter will have default type STRING and will not be required.
     */
    public TemplateParameter() {
        // Default constructor
    }

    /**
     * Creates a template parameter with the specified name and type.
     * The display name is automatically generated from the parameter name
     * by converting camelCase or snake_case to a human-readable format.
     *
     * @param name the unique identifier for this parameter
     * @param type the data type of the parameter
     */
    public TemplateParameter(String name, ParameterType type) {
        this.name = name;
        this.type = type;
        this.displayName = formatDisplayName(name);
    }

    /**
     * Creates a template parameter with the specified name, type, and default value.
     *
     * @param name         the unique identifier for this parameter
     * @param type         the data type of the parameter
     * @param defaultValue the default value to use when no value is provided
     */
    public TemplateParameter(String name, ParameterType type, String defaultValue) {
        this(name, type);
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a template parameter with the specified name, type, and required flag.
     *
     * @param name     the unique identifier for this parameter
     * @param type     the data type of the parameter
     * @param required whether a value must be provided for this parameter
     */
    public TemplateParameter(String name, ParameterType type, boolean required) {
        this(name, type);
        this.required = required;
    }

    // ========== Validation Methods ==========

    /**
     * Validates the given value against all configured constraints.
     * This method performs type-specific validation, length checks, pattern matching,
     * range validation, and custom constraint validation.
     *
     * @param value the value to validate, may be null or empty
     * @return a list of validation error messages; empty list if validation passes
     */
    public List<String> validateValue(String value) {
        List<String> errors = new ArrayList<>();

        if (value == null || value.isEmpty()) {
            if (required) {
                errors.add("Parameter '" + displayName + "' is required");
            }
            return errors;
        }

        // Type-specific validation
        switch (type) {
            case INTEGER:
                errors.addAll(validateInteger(value));
                break;
            case DECIMAL:
                errors.addAll(validateDecimal(value));
                break;
            case BOOLEAN:
                errors.addAll(validateBoolean(value));
                break;
            case DATE:
                errors.addAll(validateDate(value));
                break;
            case EMAIL:
                errors.addAll(validateEmail(value));
                break;
            case URL:
                errors.addAll(validateUrl(value));
                break;
            case ENUM:
                errors.addAll(validateEnum(value));
                break;
            case STRING:
            default:
                errors.addAll(validateString(value));
                break;
        }

        // Length validation
        errors.addAll(validateLength(value));

        // Pattern validation
        errors.addAll(validatePattern(value));

        // Value range validation
        errors.addAll(validateRange(value));

        // Custom constraints
        for (ParameterConstraint constraint : constraints) {
            if (!constraint.validate(value)) {
                errors.add(constraint.getErrorMessage());
            }
        }

        return errors;
    }

    private List<String> validateString(String value) {
        List<String> errors = new ArrayList<>();
        // String-specific validation can be added here
        return errors;
    }

    private List<String> validateInteger(String value) {
        List<String> errors = new ArrayList<>();
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            errors.add("Parameter '" + displayName + "' must be a valid integer");
        }
        return errors;
    }

    private List<String> validateDecimal(String value) {
        List<String> errors = new ArrayList<>();
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            errors.add("Parameter '" + displayName + "' must be a valid decimal number");
        }
        return errors;
    }

    private List<String> validateBoolean(String value) {
        List<String> errors = new ArrayList<>();
        String lowerValue = value.toLowerCase().trim();
        if (!Arrays.asList("true", "false", "1", "0", "yes", "no", "on", "off").contains(lowerValue)) {
            errors.add("Parameter '" + displayName + "' must be a valid boolean value");
        }
        return errors;
    }

    private List<String> validateDate(String value) {
        List<String> errors = new ArrayList<>();
        try {
            java.time.LocalDate.parse(value);
        } catch (Exception e) {
            errors.add("Parameter '" + displayName + "' must be a valid date (YYYY-MM-DD)");
        }
        return errors;
    }

    private List<String> validateEmail(String value) {
        List<String> errors = new ArrayList<>();
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        if (!value.matches(emailRegex)) {
            errors.add("Parameter '" + displayName + "' must be a valid email address");
        }
        return errors;
    }

    private List<String> validateUrl(String value) {
        List<String> errors = new ArrayList<>();
        try {
            new java.net.URI(value).toURL();
        } catch (Exception e) {
            errors.add("Parameter '" + displayName + "' must be a valid URL");
        }
        return errors;
    }

    private List<String> validateEnum(String value) {
        List<String> errors = new ArrayList<>();
        if (!allowedValues.isEmpty() && !allowedValues.contains(value)) {
            errors.add("Parameter '" + displayName + "' must be one of: " + String.join(", ", allowedValues));
        }
        return errors;
    }

    private List<String> validateLength(String value) {
        List<String> errors = new ArrayList<>();

        if (minLength != null && value.length() < minLength) {
            errors.add("Parameter '" + displayName + "' must be at least " + minLength + " characters long");
        }

        if (maxLength != null && value.length() > maxLength) {
            errors.add("Parameter '" + displayName + "' must be at most " + maxLength + " characters long");
        }

        return errors;
    }

    private List<String> validatePattern(String value) {
        List<String> errors = new ArrayList<>();

        if (validationPattern != null && !validationPattern.isEmpty()) {
            if (compiledPattern == null) {
                try {
                    compiledPattern = Pattern.compile(validationPattern);
                } catch (Exception e) {
                    errors.add("Invalid validation pattern for parameter '" + displayName + "'");
                    return errors;
                }
            }

            if (!compiledPattern.matcher(value).matches()) {
                errors.add("Parameter '" + displayName + "' does not match the required pattern");
            }
        }

        return errors;
    }

    private List<String> validateRange(String value) {
        List<String> errors = new ArrayList<>();

        if (type == ParameterType.INTEGER || type == ParameterType.DECIMAL) {
            try {
                double numValue = Double.parseDouble(value);

                if (minValue != null && numValue < minValue) {
                    errors.add("Parameter '" + displayName + "' must be at least " + minValue);
                }

                if (maxValue != null && numValue > maxValue) {
                    errors.add("Parameter '" + displayName + "' must be at most " + maxValue);
                }
            } catch (NumberFormatException e) {
                // Already handled in type validation
            }
        }

        return errors;
    }

    // ========== Value Processing Methods ==========

    /**
     * Processes and formats a parameter value by applying configured transformations.
     * This includes applying value generators, prefixes, suffixes, and handling multi-value parameters.
     *
     * @param rawValue      the raw input value to process
     * @param allParameters a map of all parameter values for dependency resolution
     * @return the processed value, or the effective default value if rawValue is null or empty
     */
    public String processValue(String rawValue, Map<String, String> allParameters) {
        if (rawValue == null || rawValue.isEmpty()) {
            return getEffectiveDefaultValue(allParameters);
        }

        String processedValue = rawValue.trim();

        // Apply value generator if available
        if (valueGenerator != null) {
            processedValue = valueGenerator.generateValue(processedValue, allParameters);
        }

        // Apply prefix and suffix
        if (!valuePrefix.isEmpty()) {
            processedValue = valuePrefix + processedValue;
        }
        if (!valueSuffix.isEmpty()) {
            processedValue = processedValue + valueSuffix;
        }

        // Handle multi-value parameters
        if (multiValue && processedValue.contains(separator)) {
            String[] values = processedValue.split(Pattern.quote(separator));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) result.append(separator);
                result.append(values[i].trim());
            }
            processedValue = result.toString();
        }

        return processedValue;
    }

    /**
     * Gets the effective default value considering conditional defaults and auto-generation.
     * This method first checks conditional defaults based on other parameter values,
     * then falls back to auto-generation if configured, and finally returns the static default value.
     *
     * @param allParameters a map of all parameter values for evaluating conditions
     * @return the effective default value, or null if no default is configured
     */
    public String getEffectiveDefaultValue(Map<String, String> allParameters) {
        // Check conditional defaults first
        for (Map.Entry<String, String> conditional : conditionalDefaults.entrySet()) {
            String condition = conditional.getKey();
            String conditionalDefault = conditional.getValue();

            if (evaluateCondition(condition, allParameters)) {
                return conditionalDefault;
            }
        }

        // Auto-generate value if configured
        if (autoGenerate && valueGenerator != null) {
            return valueGenerator.generateValue(null, allParameters);
        }

        return defaultValue;
    }

    private boolean evaluateCondition(String condition, Map<String, String> parameters) {
        // Simple condition evaluation: "paramName=value" or "paramName!=value"
        if (condition.contains("=")) {
            String[] parts = condition.split("=", 2);
            if (parts.length == 2) {
                String paramName = parts[0].trim();
                String expectedValue = parts[1].trim();
                String actualValue = parameters.get(paramName);

                if (condition.contains("!=")) {
                    return !Objects.equals(actualValue, expectedValue);
                } else {
                    return Objects.equals(actualValue, expectedValue);
                }
            }
        }

        return false;
    }

    // ========== Utility Methods ==========

    /**
     * Creates a deep copy of this parameter with all properties duplicated.
     * The copy will have new timestamp values for created and lastModified.
     * Reference types like valueGenerator are copied by reference.
     *
     * @return a new TemplateParameter instance with copied values
     */
    public TemplateParameter copy() {
        TemplateParameter copy = new TemplateParameter();
        copy.name = this.name;
        copy.displayName = this.displayName;
        copy.description = this.description;
        copy.type = this.type;
        copy.defaultValue = this.defaultValue;
        copy.required = this.required;

        copy.allowedValues = new ArrayList<>(this.allowedValues);
        copy.validationPattern = this.validationPattern;
        copy.minLength = this.minLength;
        copy.maxLength = this.maxLength;
        copy.minValue = this.minValue;
        copy.maxValue = this.maxValue;
        copy.constraints = new ArrayList<>(this.constraints);

        copy.inputType = this.inputType;
        copy.placeholder = this.placeholder;
        copy.helpText = this.helpText;
        copy.sensitive = this.sensitive;
        copy.displayOrder = this.displayOrder;
        copy.category = this.category;

        copy.valueGenerator = this.valueGenerator; // Reference copy
        copy.dependsOn = new ArrayList<>(this.dependsOn);
        copy.conditionalDefaults = new HashMap<>(this.conditionalDefaults);

        copy.multiValue = this.multiValue;
        copy.separator = this.separator;
        copy.valuePrefix = this.valuePrefix;
        copy.valueSuffix = this.valueSuffix;
        copy.autoGenerate = this.autoGenerate;

        copy.created = LocalDateTime.now();
        copy.lastModified = LocalDateTime.now();
        copy.metadata = new HashMap<>(this.metadata);

        return copy;
    }

    private String formatDisplayName(String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return "Parameter";
        }

        // Convert camelCase or snake_case to Display Name
        String formatted = paramName.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("_", " ");

        // Capitalize first letter of each word
        String[] words = formatted.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }
        }

        return result.toString();
    }

    /**
     * Creates a string parameter with the specified name.
     *
     * @param name the parameter name
     * @return a new TemplateParameter of type STRING
     */
    public static TemplateParameter stringParam(String name) {
        return new TemplateParameter(name, ParameterType.STRING);
    }

    /**
     * Creates a string parameter with the specified name and default value.
     *
     * @param name         the parameter name
     * @param defaultValue the default value
     * @return a new TemplateParameter of type STRING with the specified default
     */
    public static TemplateParameter stringParam(String name, String defaultValue) {
        return new TemplateParameter(name, ParameterType.STRING, defaultValue);
    }

    /**
     * Creates a required string parameter with the specified name.
     *
     * @param name the parameter name
     * @return a new required TemplateParameter of type STRING
     */
    public static TemplateParameter requiredString(String name) {
        return new TemplateParameter(name, ParameterType.STRING, true);
    }

    /**
     * Creates an integer parameter with the specified name and default value.
     *
     * @param name         the parameter name
     * @param defaultValue the default integer value
     * @return a new TemplateParameter of type INTEGER with the specified default
     */
    public static TemplateParameter intParam(String name, int defaultValue) {
        TemplateParameter param = new TemplateParameter(name, ParameterType.INTEGER);
        param.setDefaultValue(String.valueOf(defaultValue));
        return param;
    }

    /**
     * Creates a boolean parameter with the specified name and default value.
     * The input type is automatically set to CHECKBOX.
     *
     * @param name         the parameter name
     * @param defaultValue the default boolean value
     * @return a new TemplateParameter of type BOOLEAN with CHECKBOX input
     */
    public static TemplateParameter boolParam(String name, boolean defaultValue) {
        TemplateParameter param = new TemplateParameter(name, ParameterType.BOOLEAN);
        param.setDefaultValue(String.valueOf(defaultValue));
        param.setInputType(InputType.CHECKBOX);
        return param;
    }

    /**
     * Creates an enumeration parameter with the specified name and allowed values.
     * The input type is automatically set to DROPDOWN, and the first allowed value
     * becomes the default.
     *
     * @param name          the parameter name
     * @param allowedValues the allowed values for the enumeration
     * @return a new TemplateParameter of type ENUM with DROPDOWN input
     */
    public static TemplateParameter enumParam(String name, String... allowedValues) {
        TemplateParameter param = new TemplateParameter(name, ParameterType.ENUM);
        param.setAllowedValues(Arrays.asList(allowedValues));
        param.setInputType(InputType.DROPDOWN);
        if (allowedValues.length > 0) {
            param.setDefaultValue(allowedValues[0]);
        }
        return param;
    }

    // ========== Builder Pattern Support ==========

    /**
     * Sets the display name and returns this parameter for method chaining.
     *
     * @param displayName the human-readable display name
     * @return this parameter instance
     */
    public TemplateParameter displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Sets the description and returns this parameter for method chaining.
     *
     * @param description the detailed description of the parameter
     * @return this parameter instance
     */
    public TemplateParameter description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the placeholder text and returns this parameter for method chaining.
     *
     * @param placeholder the placeholder text shown in empty input fields
     * @return this parameter instance
     */
    public TemplateParameter placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * Sets the help text and returns this parameter for method chaining.
     *
     * @param helpText the help text shown to users
     * @return this parameter instance
     */
    public TemplateParameter helpText(String helpText) {
        this.helpText = helpText;
        return this;
    }

    /**
     * Sets the category and returns this parameter for method chaining.
     *
     * @param category the category for grouping parameters
     * @return this parameter instance
     */
    public TemplateParameter category(String category) {
        this.category = category;
        return this;
    }

    /**
     * Sets the display order and returns this parameter for method chaining.
     *
     * @param displayOrder the order in which to display this parameter
     * @return this parameter instance
     */
    public TemplateParameter order(int displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    /**
     * Sets whether this parameter is required and returns this parameter for method chaining.
     *
     * @param required true if a value must be provided
     * @return this parameter instance
     */
    public TemplateParameter required(boolean required) {
        this.required = required;
        return this;
    }

    /**
     * Sets the validation pattern and returns this parameter for method chaining.
     *
     * @param pattern the regex pattern for validation
     * @return this parameter instance
     */
    public TemplateParameter pattern(String pattern) {
        this.validationPattern = pattern;
        return this;
    }

    /**
     * Sets the minimum and maximum length constraints and returns this parameter for method chaining.
     *
     * @param min the minimum length
     * @param max the maximum length
     * @return this parameter instance
     */
    public TemplateParameter length(int min, int max) {
        this.minLength = min;
        this.maxLength = max;
        return this;
    }

    /**
     * Sets the minimum and maximum value constraints and returns this parameter for method chaining.
     * Applies to numeric parameter types.
     *
     * @param min the minimum value
     * @param max the maximum value
     * @return this parameter instance
     */
    public TemplateParameter range(double min, double max) {
        this.minValue = min;
        this.maxValue = max;
        return this;
    }

    /**
     * Sets the parameter type and returns this parameter for method chaining.
     *
     * @param type the parameter type
     * @return this parameter instance
     */
    public TemplateParameter withType(ParameterType type) {
        this.type = type;
        return this;
    }

    /**
     * Sets the value generator and returns this parameter for method chaining.
     *
     * @param generator the value generator for dynamic value creation
     * @return this parameter instance
     */
    public TemplateParameter withValueGenerator(ValueGenerator generator) {
        this.valueGenerator = generator;
        return this;
    }

    /**
     * Sets the auto-generate flag and returns this parameter for method chaining.
     *
     * @param autoGenerate true to auto-generate values using the value generator
     * @return this parameter instance
     */
    public TemplateParameter withAutoGenerate(boolean autoGenerate) {
        this.autoGenerate = autoGenerate;
        return this;
    }

    // ========== Enums ==========

    /**
     * Defines the supported parameter data types.
     * Each type determines validation rules and may affect UI rendering.
     */
    public enum ParameterType {
        /** Plain text string value. */
        STRING,
        /** Whole number value. */
        INTEGER,
        /** Floating-point decimal value. */
        DECIMAL,
        /** True or false value. */
        BOOLEAN,
        /** Date value in ISO format (YYYY-MM-DD). */
        DATE,
        /** Email address value. */
        EMAIL,
        /** URL value. */
        URL,
        /** Value from a predefined set of allowed values. */
        ENUM,
        /** File system path value. */
        FILE_PATH,
        /** Color value (hex or named). */
        COLOR,
        /** XML namespace URI. */
        NAMESPACE,
        /** XML element name. */
        ELEMENT_NAME,
        /** XML attribute name. */
        ATTRIBUTE_NAME
    }

    /**
     * Defines the UI input control types for rendering parameter input fields.
     */
    public enum InputType {
        /** Single-line text input field. */
        TEXT_FIELD,
        /** Multi-line text input area. */
        TEXT_AREA,
        /** Password input field with masked characters. */
        PASSWORD,
        /** Dropdown selection list. */
        DROPDOWN,
        /** Boolean checkbox. */
        CHECKBOX,
        /** Radio button group for exclusive selection. */
        RADIO_GROUP,
        /** Numeric input field with validation. */
        NUMBER_FIELD,
        /** Calendar-based date picker. */
        DATE_PICKER,
        /** Visual color picker. */
        COLOR_PICKER,
        /** File selection dialog. */
        FILE_CHOOSER,
        /** Numeric slider for range selection. */
        SLIDER
    }

    // ========== Inner Interfaces ==========

    /**
     * Functional interface for generating parameter values dynamically.
     * Implementations can compute values based on the current value and other parameters.
     */
    @FunctionalInterface
    public interface ValueGenerator {
        /**
         * Generates a value based on the current value and all parameter values.
         *
         * @param currentValue  the current value, may be null
         * @param allParameters a map of all parameter names to their values
         * @return the generated value
         */
        String generateValue(String currentValue, Map<String, String> allParameters);
    }

    /**
     * Functional interface for custom parameter validation constraints.
     * Implementations define custom validation logic beyond the built-in type validation.
     */
    @FunctionalInterface
    public interface ParameterConstraint {
        /**
         * Validates the given value against this constraint.
         *
         * @param value the value to validate
         * @return true if the value is valid, false otherwise
         */
        boolean validate(String value);

        /**
         * Gets the error message to display when validation fails.
         *
         * @return the error message
         */
        default String getErrorMessage() {
            return "Parameter constraint validation failed";
        }
    }

    // ========== Getters and Setters ==========

    /**
     * Gets the unique identifier name of this parameter.
     *
     * @return the parameter name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the unique identifier name of this parameter.
     * If display name is not set, it will be automatically generated from the name.
     *
     * @param name the parameter name
     */
    public void setName(String name) {
        this.name = name;
        if (displayName == null || displayName.isEmpty()) {
            this.displayName = formatDisplayName(name);
        }
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Gets the human-readable display name of this parameter.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the human-readable display name of this parameter.
     *
     * @param displayName the display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Gets the detailed description of this parameter.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the detailed description of this parameter.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the data type of this parameter.
     *
     * @return the parameter type
     */
    public ParameterType getType() {
        return type;
    }

    /**
     * Sets the data type of this parameter.
     *
     * @param type the parameter type
     */
    public void setType(ParameterType type) {
        this.type = type;
    }

    /**
     * Gets the default value for this parameter.
     *
     * @return the default value, or null if not set
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value for this parameter.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Checks whether this parameter requires a value.
     *
     * @return true if a value is required, false otherwise
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Sets whether this parameter requires a value.
     *
     * @param required true if a value is required
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Gets the list of allowed values for ENUM type parameters.
     *
     * @return the list of allowed values
     */
    public List<String> getAllowedValues() {
        return allowedValues;
    }

    /**
     * Sets the list of allowed values for ENUM type parameters.
     *
     * @param allowedValues the list of allowed values
     */
    public void setAllowedValues(List<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    /**
     * Gets the regex validation pattern.
     *
     * @return the validation pattern, or null if not set
     */
    public String getValidationPattern() {
        return validationPattern;
    }

    /**
     * Sets the regex validation pattern.
     * The compiled pattern cache is reset when this is changed.
     *
     * @param validationPattern the validation pattern
     */
    public void setValidationPattern(String validationPattern) {
        this.validationPattern = validationPattern;
        this.compiledPattern = null; // Reset compiled pattern
    }

    /**
     * Gets the minimum length constraint.
     *
     * @return the minimum length, or null if not set
     */
    public Integer getMinLength() {
        return minLength;
    }

    /**
     * Sets the minimum length constraint.
     *
     * @param minLength the minimum length
     */
    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    /**
     * Gets the maximum length constraint.
     *
     * @return the maximum length, or null if not set
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * Sets the maximum length constraint.
     *
     * @param maxLength the maximum length
     */
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Gets the minimum value constraint for numeric types.
     *
     * @return the minimum value, or null if not set
     */
    public Double getMinValue() {
        return minValue;
    }

    /**
     * Sets the minimum value constraint for numeric types.
     *
     * @param minValue the minimum value
     */
    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    /**
     * Gets the maximum value constraint for numeric types.
     *
     * @return the maximum value, or null if not set
     */
    public Double getMaxValue() {
        return maxValue;
    }

    /**
     * Sets the maximum value constraint for numeric types.
     *
     * @param maxValue the maximum value
     */
    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    /**
     * Gets the list of custom validation constraints.
     *
     * @return the list of constraints
     */
    public List<ParameterConstraint> getConstraints() {
        return constraints;
    }

    /**
     * Sets the list of custom validation constraints.
     *
     * @param constraints the list of constraints
     */
    public void setConstraints(List<ParameterConstraint> constraints) {
        this.constraints = constraints;
    }

    /**
     * Gets the UI input type for rendering.
     *
     * @return the input type
     */
    public InputType getInputType() {
        return inputType;
    }

    /**
     * Sets the UI input type for rendering.
     *
     * @param inputType the input type
     */
    public void setInputType(InputType inputType) {
        this.inputType = inputType;
    }

    /**
     * Gets the placeholder text for empty input fields.
     *
     * @return the placeholder text, or null if not set
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Sets the placeholder text for empty input fields.
     *
     * @param placeholder the placeholder text
     */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    /**
     * Gets the help text displayed to users.
     *
     * @return the help text, or null if not set
     */
    public String getHelpText() {
        return helpText;
    }

    /**
     * Sets the help text displayed to users.
     *
     * @param helpText the help text
     */
    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    /**
     * Checks whether this parameter contains sensitive data.
     *
     * @return true if the parameter is sensitive (e.g., password), false otherwise
     */
    public boolean isSensitive() {
        return sensitive;
    }

    /**
     * Sets whether this parameter contains sensitive data.
     *
     * @param sensitive true if the parameter is sensitive
     */
    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    /**
     * Gets the display order for this parameter.
     *
     * @return the display order
     */
    public int getDisplayOrder() {
        return displayOrder;
    }

    /**
     * Sets the display order for this parameter.
     *
     * @param displayOrder the display order
     */
    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /**
     * Gets the category for grouping parameters.
     *
     * @return the category name
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category for grouping parameters.
     *
     * @param category the category name
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Gets the value generator for dynamic value creation.
     *
     * @return the value generator, or null if not set
     */
    public ValueGenerator getValueGenerator() {
        return valueGenerator;
    }

    /**
     * Sets the value generator for dynamic value creation.
     *
     * @param valueGenerator the value generator
     */
    public void setValueGenerator(ValueGenerator valueGenerator) {
        this.valueGenerator = valueGenerator;
    }

    /**
     * Gets the list of parameter names this parameter depends on.
     *
     * @return the list of dependency names
     */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * Sets the list of parameter names this parameter depends on.
     *
     * @param dependsOn the list of dependency names
     */
    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    /**
     * Gets the map of conditional default values.
     * Keys are condition expressions, values are default values when conditions are met.
     *
     * @return the conditional defaults map
     */
    public Map<String, String> getConditionalDefaults() {
        return conditionalDefaults;
    }

    /**
     * Sets the map of conditional default values.
     *
     * @param conditionalDefaults the conditional defaults map
     */
    public void setConditionalDefaults(Map<String, String> conditionalDefaults) {
        this.conditionalDefaults = conditionalDefaults;
    }

    /**
     * Checks whether this parameter accepts multiple values.
     *
     * @return true if multi-value is enabled, false otherwise
     */
    public boolean isMultiValue() {
        return multiValue;
    }

    /**
     * Sets whether this parameter accepts multiple values.
     *
     * @param multiValue true to enable multi-value
     */
    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
    }

    /**
     * Gets the separator used for multi-value parameters.
     *
     * @return the separator string
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Sets the separator used for multi-value parameters.
     *
     * @param separator the separator string
     */
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    /**
     * Gets the prefix prepended to processed values.
     *
     * @return the value prefix
     */
    public String getValuePrefix() {
        return valuePrefix;
    }

    /**
     * Sets the prefix prepended to processed values.
     *
     * @param valuePrefix the value prefix
     */
    public void setValuePrefix(String valuePrefix) {
        this.valuePrefix = valuePrefix;
    }

    /**
     * Gets the suffix appended to processed values.
     *
     * @return the value suffix
     */
    public String getValueSuffix() {
        return valueSuffix;
    }

    /**
     * Sets the suffix appended to processed values.
     *
     * @param valueSuffix the value suffix
     */
    public void setValueSuffix(String valueSuffix) {
        this.valueSuffix = valueSuffix;
    }

    /**
     * Checks whether values should be auto-generated.
     *
     * @return true if auto-generation is enabled, false otherwise
     */
    public boolean isAutoGenerate() {
        return autoGenerate;
    }

    /**
     * Sets whether values should be auto-generated.
     *
     * @param autoGenerate true to enable auto-generation
     */
    public void setAutoGenerate(boolean autoGenerate) {
        this.autoGenerate = autoGenerate;
    }

    /**
     * Gets the creation timestamp of this parameter.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreated() {
        return created;
    }

    /**
     * Sets the creation timestamp of this parameter.
     *
     * @param created the creation timestamp
     */
    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    /**
     * Gets the last modification timestamp of this parameter.
     *
     * @return the last modification timestamp
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    /**
     * Sets the last modification timestamp of this parameter.
     *
     * @param lastModified the last modification timestamp
     */
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * Gets the metadata map for storing additional custom properties.
     *
     * @return the metadata map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata map for storing additional custom properties.
     *
     * @param metadata the metadata map
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Checks equality based on the parameter name.
     *
     * @param obj the object to compare
     * @return true if the names are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TemplateParameter that = (TemplateParameter) obj;
        return Objects.equals(name, that.name);
    }

    /**
     * Computes the hash code based on the parameter name.
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
     * @return a string containing name, type, and required status
     */
    @Override
    public String toString() {
        return String.format("TemplateParameter{name='%s', type=%s, required=%s}",
                name, type, required);
    }
}
