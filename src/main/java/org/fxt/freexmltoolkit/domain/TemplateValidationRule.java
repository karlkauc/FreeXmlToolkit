package org.fxt.freexmltoolkit.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Custom validation rules for template parameters.
 * Supports complex validation logic beyond basic parameter constraints.
 */
public class TemplateValidationRule {

    private String id;
    private String name;
    private String description;
    private String errorMessage;
    private RuleType ruleType;
    private boolean active = true;

    // Rule configuration
    private String targetParameter;
    private List<String> targetParameters;
    private String validationExpression;
    private Pattern validationPattern;
    private Predicate<Map<String, String>> customValidator;

    public TemplateValidationRule() {
        // Default constructor
    }

    public TemplateValidationRule(String name, String targetParameter, String errorMessage) {
        this.name = name;
        this.targetParameter = targetParameter;
        this.errorMessage = errorMessage;
        this.ruleType = RuleType.CUSTOM;
    }

    /**
     * Validate parameters against this rule
     */
    public boolean validate(Map<String, String> parameters) {
        if (!active) {
            return true;
        }

        switch (ruleType) {
            case REQUIRED_IF:
                return validateRequiredIf(parameters);
            case MUTUALLY_EXCLUSIVE:
                return validateMutuallyExclusive(parameters);
            case DEPENDENCY:
                return validateDependency(parameters);
            case PATTERN_MATCH:
                return validatePattern(parameters);
            case CUSTOM:
                return validateCustom(parameters);
            default:
                return true;
        }
    }

    private boolean validateRequiredIf(Map<String, String> parameters) {
        // Implementation for required-if logic
        return true;
    }

    private boolean validateMutuallyExclusive(Map<String, String> parameters) {
        // Implementation for mutually exclusive parameters
        return true;
    }

    private boolean validateDependency(Map<String, String> parameters) {
        // Implementation for parameter dependencies
        return true;
    }

    private boolean validatePattern(Map<String, String> parameters) {
        if (validationPattern != null && targetParameter != null) {
            String value = parameters.get(targetParameter);
            return value == null || validationPattern.matcher(value).matches();
        }
        return true;
    }

    private boolean validateCustom(Map<String, String> parameters) {
        if (customValidator != null) {
            return customValidator.test(parameters);
        }
        return true;
    }

    public enum RuleType {
        REQUIRED_IF,
        MUTUALLY_EXCLUSIVE,
        DEPENDENCY,
        PATTERN_MATCH,
        CUSTOM
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getTargetParameter() {
        return targetParameter;
    }

    public void setTargetParameter(String targetParameter) {
        this.targetParameter = targetParameter;
    }

    public List<String> getTargetParameters() {
        return targetParameters;
    }

    public void setTargetParameters(List<String> targetParameters) {
        this.targetParameters = targetParameters;
    }

    public String getValidationExpression() {
        return validationExpression;
    }

    public void setValidationExpression(String validationExpression) {
        this.validationExpression = validationExpression;
    }

    public Pattern getValidationPattern() {
        return validationPattern;
    }

    public void setValidationPattern(Pattern validationPattern) {
        this.validationPattern = validationPattern;
    }

    public Predicate<Map<String, String>> getCustomValidator() {
        return customValidator;
    }

    public void setCustomValidator(Predicate<Map<String, String>> customValidator) {
        this.customValidator = customValidator;
    }
}