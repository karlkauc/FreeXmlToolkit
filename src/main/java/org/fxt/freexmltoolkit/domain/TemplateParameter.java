package org.fxt.freexmltoolkit.domain;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Flexible template parameter system for XML templates.
 * Supports various parameter types, validation, and dynamic value generation.
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

    public TemplateParameter() {
        // Default constructor
    }

    public TemplateParameter(String name, ParameterType type) {
        this.name = name;
        this.type = type;
        this.displayName = formatDisplayName(name);
    }

    public TemplateParameter(String name, ParameterType type, String defaultValue) {
        this(name, type);
        this.defaultValue = defaultValue;
    }

    public TemplateParameter(String name, ParameterType type, boolean required) {
        this(name, type);
        this.required = required;
    }

    // ========== Validation Methods ==========

    /**
     * Validate parameter value
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
     * Process and format parameter value
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
     * Get effective default value considering dependencies
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
     * Create a copy of this parameter
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
     * Create quick parameter builders
     */
    public static TemplateParameter stringParam(String name) {
        return new TemplateParameter(name, ParameterType.STRING);
    }

    public static TemplateParameter stringParam(String name, String defaultValue) {
        return new TemplateParameter(name, ParameterType.STRING, defaultValue);
    }

    public static TemplateParameter requiredString(String name) {
        return new TemplateParameter(name, ParameterType.STRING, true);
    }

    public static TemplateParameter intParam(String name, int defaultValue) {
        TemplateParameter param = new TemplateParameter(name, ParameterType.INTEGER);
        param.setDefaultValue(String.valueOf(defaultValue));
        return param;
    }

    public static TemplateParameter boolParam(String name, boolean defaultValue) {
        TemplateParameter param = new TemplateParameter(name, ParameterType.BOOLEAN);
        param.setDefaultValue(String.valueOf(defaultValue));
        param.setInputType(InputType.CHECKBOX);
        return param;
    }

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

    public TemplateParameter displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public TemplateParameter description(String description) {
        this.description = description;
        return this;
    }

    public TemplateParameter placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public TemplateParameter helpText(String helpText) {
        this.helpText = helpText;
        return this;
    }

    public TemplateParameter category(String category) {
        this.category = category;
        return this;
    }

    public TemplateParameter order(int displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    public TemplateParameter required(boolean required) {
        this.required = required;
        return this;
    }

    public TemplateParameter pattern(String pattern) {
        this.validationPattern = pattern;
        return this;
    }

    public TemplateParameter length(int min, int max) {
        this.minLength = min;
        this.maxLength = max;
        return this;
    }

    public TemplateParameter range(double min, double max) {
        this.minValue = min;
        this.maxValue = max;
        return this;
    }

    public TemplateParameter withType(ParameterType type) {
        this.type = type;
        return this;
    }

    public TemplateParameter withValueGenerator(ValueGenerator generator) {
        this.valueGenerator = generator;
        return this;
    }

    public TemplateParameter withAutoGenerate(boolean autoGenerate) {
        this.autoGenerate = autoGenerate;
        return this;
    }

    // ========== Enums ==========

    public enum ParameterType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        DATE,
        EMAIL,
        URL,
        ENUM,
        FILE_PATH,
        COLOR,
        NAMESPACE,
        ELEMENT_NAME,
        ATTRIBUTE_NAME
    }

    public enum InputType {
        TEXT_FIELD,
        TEXT_AREA,
        PASSWORD,
        DROPDOWN,
        CHECKBOX,
        RADIO_GROUP,
        NUMBER_FIELD,
        DATE_PICKER,
        COLOR_PICKER,
        FILE_CHOOSER,
        SLIDER
    }

    // ========== Inner Interfaces ==========

    @FunctionalInterface
    public interface ValueGenerator {
        String generateValue(String currentValue, Map<String, String> allParameters);
    }

    @FunctionalInterface
    public interface ParameterConstraint {
        boolean validate(String value);

        default String getErrorMessage() {
            return "Parameter constraint validation failed";
        }
    }

    // ========== Getters and Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (displayName == null || displayName.isEmpty()) {
            this.displayName = formatDisplayName(name);
        }
        this.lastModified = LocalDateTime.now();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.lastModified = LocalDateTime.now();
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

    public List<String> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public String getValidationPattern() {
        return validationPattern;
    }

    public void setValidationPattern(String validationPattern) {
        this.validationPattern = validationPattern;
        this.compiledPattern = null; // Reset compiled pattern
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    public List<ParameterConstraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<ParameterConstraint> constraints) {
        this.constraints = constraints;
    }

    public InputType getInputType() {
        return inputType;
    }

    public void setInputType(InputType inputType) {
        this.inputType = inputType;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ValueGenerator getValueGenerator() {
        return valueGenerator;
    }

    public void setValueGenerator(ValueGenerator valueGenerator) {
        this.valueGenerator = valueGenerator;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public Map<String, String> getConditionalDefaults() {
        return conditionalDefaults;
    }

    public void setConditionalDefaults(Map<String, String> conditionalDefaults) {
        this.conditionalDefaults = conditionalDefaults;
    }

    public boolean isMultiValue() {
        return multiValue;
    }

    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public String getValuePrefix() {
        return valuePrefix;
    }

    public void setValuePrefix(String valuePrefix) {
        this.valuePrefix = valuePrefix;
    }

    public String getValueSuffix() {
        return valueSuffix;
    }

    public void setValueSuffix(String valueSuffix) {
        this.valueSuffix = valueSuffix;
    }

    public boolean isAutoGenerate() {
        return autoGenerate;
    }

    public void setAutoGenerate(boolean autoGenerate) {
        this.autoGenerate = autoGenerate;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TemplateParameter that = (TemplateParameter) obj;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("TemplateParameter{name='%s', type=%s, required=%s}",
                name, type, required);
    }
}