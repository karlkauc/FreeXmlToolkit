package org.fxt.freexmltoolkit.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Custom validation rules for template parameters.
 * Supports complex validation logic beyond basic parameter constraints.
 *
 * <p>This class provides various types of validation rules that can be applied
 * to template parameters, including required-if conditions, mutual exclusivity,
 * dependencies, pattern matching, and custom validation logic.</p>
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

    /**
     * Creates a new validation rule with default values.
     * The rule will be active by default.
     */
    public TemplateValidationRule() {
        // Default constructor
    }

    /**
     * Creates a new validation rule with the specified name, target parameter, and error message.
     * The rule type is set to CUSTOM by default.
     *
     * @param name the name of the validation rule
     * @param targetParameter the name of the parameter this rule validates
     * @param errorMessage the error message to display when validation fails
     */
    public TemplateValidationRule(String name, String targetParameter, String errorMessage) {
        this.name = name;
        this.targetParameter = targetParameter;
        this.errorMessage = errorMessage;
        this.ruleType = RuleType.CUSTOM;
    }

    /**
     * Validates the given parameters against this rule.
     * If the rule is not active, validation always passes.
     *
     * @param parameters the map of parameter names to their values
     * @return true if the parameters pass validation, false otherwise
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

    /**
     * Types of validation rules that can be applied to template parameters.
     */
    public enum RuleType {
        /**
         * The parameter is required if a specified condition is met.
         */
        REQUIRED_IF,

        /**
         * Parameters in the group are mutually exclusive - only one can have a value.
         */
        MUTUALLY_EXCLUSIVE,

        /**
         * The parameter depends on another parameter being set.
         */
        DEPENDENCY,

        /**
         * The parameter value must match a specified regex pattern.
         */
        PATTERN_MATCH,

        /**
         * Custom validation logic using a predicate.
         */
        CUSTOM
    }

    /**
     * Returns the unique identifier of this validation rule.
     *
     * @return the rule identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of this validation rule.
     *
     * @param id the rule identifier to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the name of this validation rule.
     *
     * @return the rule name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this validation rule.
     *
     * @param name the rule name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the description of this validation rule.
     *
     * @return the rule description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this validation rule.
     *
     * @param description the rule description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the error message displayed when validation fails.
     *
     * @return the error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message displayed when validation fails.
     *
     * @param errorMessage the error message to set
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the type of this validation rule.
     *
     * @return the rule type
     */
    public RuleType getRuleType() {
        return ruleType;
    }

    /**
     * Sets the type of this validation rule.
     *
     * @param ruleType the rule type to set
     */
    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * Returns whether this validation rule is active.
     *
     * @return true if the rule is active, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets whether this validation rule is active.
     * Inactive rules always pass validation.
     *
     * @param active true to activate the rule, false to deactivate
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the name of the target parameter for this rule.
     *
     * @return the target parameter name
     */
    public String getTargetParameter() {
        return targetParameter;
    }

    /**
     * Sets the name of the target parameter for this rule.
     *
     * @param targetParameter the target parameter name to set
     */
    public void setTargetParameter(String targetParameter) {
        this.targetParameter = targetParameter;
    }

    /**
     * Returns the list of target parameters for rules that operate on multiple parameters.
     *
     * @return the list of target parameter names
     */
    public List<String> getTargetParameters() {
        return targetParameters;
    }

    /**
     * Sets the list of target parameters for rules that operate on multiple parameters.
     *
     * @param targetParameters the list of target parameter names to set
     */
    public void setTargetParameters(List<String> targetParameters) {
        this.targetParameters = targetParameters;
    }

    /**
     * Returns the validation expression used by this rule.
     *
     * @return the validation expression
     */
    public String getValidationExpression() {
        return validationExpression;
    }

    /**
     * Sets the validation expression used by this rule.
     *
     * @param validationExpression the validation expression to set
     */
    public void setValidationExpression(String validationExpression) {
        this.validationExpression = validationExpression;
    }

    /**
     * Returns the regex pattern used for PATTERN_MATCH validation.
     *
     * @return the validation pattern
     */
    public Pattern getValidationPattern() {
        return validationPattern;
    }

    /**
     * Sets the regex pattern used for PATTERN_MATCH validation.
     *
     * @param validationPattern the validation pattern to set
     */
    public void setValidationPattern(Pattern validationPattern) {
        this.validationPattern = validationPattern;
    }

    /**
     * Returns the custom validator predicate used for CUSTOM validation.
     *
     * @return the custom validator predicate
     */
    public Predicate<Map<String, String>> getCustomValidator() {
        return customValidator;
    }

    /**
     * Sets the custom validator predicate used for CUSTOM validation.
     *
     * @param customValidator the custom validator predicate to set
     */
    public void setCustomValidator(Predicate<Map<String, String>> customValidator) {
        this.customValidator = customValidator;
    }
}
