package org.fxt.freexmltoolkit.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Validation rules for XPath/XQuery snippets to ensure quality and correctness.
 * Provides an extensible validation framework for snippet content and structure.
 *
 * <p>This class supports both pattern-based and custom validator-based validation,
 * allowing for flexible rule definitions. Rules can be created using the builder
 * pattern for fluent configuration.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * SnippetValidationRule rule = SnippetValidationRule.builder("My Rule")
 *     .type(RuleType.SYNTAX)
 *     .severity(Severity.ERROR)
 *     .pattern("//")
 *     .errorMessage("Found double slash")
 *     .build();
 * ValidationResult result = rule.validate(xpathQuery);
 * }</pre>
 *
 * @see ValidationResult
 * @see RuleType
 * @see Severity
 */
public class SnippetValidationRule {

    /**
     * Enumeration of validation rule types for categorizing snippet validation rules.
     * Each type represents a different aspect of validation that can be applied
     * to XPath/XQuery snippets.
     */
    public enum RuleType {
        /**
         * Syntax validation rule type.
         * Used for validating XPath/XQuery syntax correctness.
         */
        SYNTAX("Syntax Validation", "Validates XPath/XQuery syntax"),

        /**
         * Performance check rule type.
         * Used for identifying potential performance issues in queries.
         */
        PERFORMANCE("Performance Check", "Checks for performance issues"),

        /**
         * Security check rule type.
         * Used for validating security considerations and identifying risky patterns.
         */
        SECURITY("Security Check", "Validates security considerations"),

        /**
         * Best practice rule type.
         * Used for enforcing coding best practices and conventions.
         */
        BEST_PRACTICE("Best Practice", "Enforces coding best practices"),

        /**
         * Custom rule type.
         * Used for user-defined validation rules that do not fit other categories.
         */
        CUSTOM("Custom Rule", "User-defined validation rule");

        private final String displayName;
        private final String description;

        /**
         * Constructs a RuleType with the specified display name and description.
         *
         * @param displayName the human-readable name for display in UI
         * @param description a brief description of what this rule type validates
         */
        RuleType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        /**
         * Returns the human-readable display name of this rule type.
         *
         * @return the display name for UI presentation
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the description of what this rule type validates.
         *
         * @return the description text
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * Enumeration of severity levels for validation rule violations.
     * Each severity level has an associated display name, description, and color
     * for visual representation in the UI.
     */
    public enum Severity {
        /**
         * Error severity level.
         * Indicates a critical issue that must be fixed before the snippet can be used.
         * Displayed with red color (#dc3545).
         */
        ERROR("Error", "Must be fixed", "#dc3545"),

        /**
         * Warning severity level.
         * Indicates an issue that should be reviewed but may not prevent usage.
         * Displayed with yellow color (#ffc107).
         */
        WARNING("Warning", "Should be reviewed", "#ffc107"),

        /**
         * Informational severity level.
         * Provides informational feedback that does not require action.
         * Displayed with cyan color (#17a2b8).
         */
        INFO("Info", "Informational", "#17a2b8"),

        /**
         * Suggestion severity level.
         * Offers optional improvements to consider for better code quality.
         * Displayed with green color (#28a745).
         */
        SUGGESTION("Suggestion", "Consider improvement", "#28a745");

        private final String displayName;
        private final String description;
        private final String color;

        /**
         * Constructs a Severity with the specified display name, description, and color.
         *
         * @param displayName the human-readable name for display in UI
         * @param description a brief description of what this severity level means
         * @param color       the hex color code for visual representation (e.g., "#dc3545")
         */
        Severity(String displayName, String description, String color) {
            this.displayName = displayName;
            this.description = description;
            this.color = color;
        }

        /**
         * Returns the human-readable display name of this severity level.
         *
         * @return the display name for UI presentation
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the description of what this severity level means.
         *
         * @return the description text
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns the hex color code associated with this severity level.
         * The color can be used for visual distinction in the UI.
         *
         * @return the hex color code (e.g., "#dc3545" for red)
         */
        public String getColor() {
            return color;
        }
    }

    private String id;
    private String name;
    private String description;
    private RuleType type;
    private Severity severity;
    private boolean enabled;
    private Pattern pattern; // For regex-based validation
    private Function<String, ValidationResult> customValidator; // For complex validation
    private String errorMessage;
    private String suggestion;
    private String documentationUrl;

    /**
     * Constructs a new SnippetValidationRule with default settings.
     * The rule is enabled by default with WARNING severity.
     */
    public SnippetValidationRule() {
        this.enabled = true;
        this.severity = Severity.WARNING;
    }

    /**
     * Constructs a new SnippetValidationRule with the specified parameters.
     * The rule is enabled by default.
     *
     * @param name        the name of the validation rule
     * @param type        the type of validation rule
     * @param severity    the severity level for violations
     * @param description a description of what this rule validates
     */
    public SnippetValidationRule(String name, RuleType type, Severity severity, String description) {
        this();
        this.name = name;
        this.type = type;
        this.severity = severity;
        this.description = description;
    }

    // ========== Builder Pattern ==========

    /**
     * Builder class for constructing SnippetValidationRule instances.
     * Provides a fluent API for configuring validation rules with various options.
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * SnippetValidationRule rule = new Builder("My Rule")
     *     .type(RuleType.SYNTAX)
     *     .severity(Severity.ERROR)
     *     .pattern("//")
     *     .errorMessage("Found double slash")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private final SnippetValidationRule rule;

        /**
         * Constructs a new Builder with the specified rule name.
         *
         * @param name the name of the validation rule to build
         */
        public Builder(String name) {
            this.rule = new SnippetValidationRule();
            this.rule.name = name;
        }

        /**
         * Sets the description for the validation rule.
         *
         * @param description a description of what this rule validates
         * @return this builder for method chaining
         */
        public Builder description(String description) {
            rule.description = description;
            return this;
        }

        /**
         * Sets the type of the validation rule.
         *
         * @param type the rule type (e.g., SYNTAX, PERFORMANCE, SECURITY)
         * @return this builder for method chaining
         */
        public Builder type(RuleType type) {
            rule.type = type;
            return this;
        }

        /**
         * Sets the severity level for rule violations.
         *
         * @param severity the severity level (e.g., ERROR, WARNING, INFO)
         * @return this builder for method chaining
         */
        public Builder severity(Severity severity) {
            rule.severity = severity;
            return this;
        }

        /**
         * Sets whether the rule is enabled.
         *
         * @param enabled true to enable the rule, false to disable it
         * @return this builder for method chaining
         */
        public Builder enabled(boolean enabled) {
            rule.enabled = enabled;
            return this;
        }

        /**
         * Sets the regex pattern for pattern-based validation.
         * The pattern string will be compiled into a Pattern object.
         *
         * @param pattern the regex pattern string to match against snippets
         * @return this builder for method chaining
         */
        public Builder pattern(String pattern) {
            rule.pattern = Pattern.compile(pattern);
            return this;
        }

        /**
         * Sets the pre-compiled Pattern for pattern-based validation.
         *
         * @param pattern the compiled Pattern object to match against snippets
         * @return this builder for method chaining
         */
        public Builder pattern(Pattern pattern) {
            rule.pattern = pattern;
            return this;
        }

        /**
         * Sets a custom validator function for complex validation logic.
         * The function receives the query string and returns a ValidationResult.
         *
         * @param validator the custom validation function
         * @return this builder for method chaining
         */
        public Builder validator(Function<String, ValidationResult> validator) {
            rule.customValidator = validator;
            return this;
        }

        /**
         * Sets the error message to display when validation fails.
         *
         * @param errorMessage the error message text
         * @return this builder for method chaining
         */
        public Builder errorMessage(String errorMessage) {
            rule.errorMessage = errorMessage;
            return this;
        }

        /**
         * Sets a suggestion for how to fix the validation issue.
         *
         * @param suggestion the suggestion text
         * @return this builder for method chaining
         */
        public Builder suggestion(String suggestion) {
            rule.suggestion = suggestion;
            return this;
        }

        /**
         * Sets the documentation URL for more information about this rule.
         *
         * @param url the URL to documentation
         * @return this builder for method chaining
         */
        public Builder documentationUrl(String url) {
            rule.documentationUrl = url;
            return this;
        }

        /**
         * Builds and returns the configured SnippetValidationRule.
         *
         * @return the constructed SnippetValidationRule instance
         * @throws IllegalStateException if the rule name is null or empty,
         *                               or if neither pattern nor custom validator is set
         */
        public SnippetValidationRule build() {
            if (rule.name == null || rule.name.trim().isEmpty()) {
                throw new IllegalStateException("Rule name is required");
            }
            if (rule.pattern == null && rule.customValidator == null) {
                throw new IllegalStateException("Either pattern or custom validator is required");
            }
            return rule;
        }
    }

    /**
     * Creates a new Builder instance for constructing a SnippetValidationRule.
     *
     * @param name the name of the validation rule to build
     * @return a new Builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ========== Validation Methods ==========

    /**
     * Validates the given snippet query against this rule.
     * Returns a ValidationResult containing any errors, warnings, info, or suggestions.
     *
     * <p>If the rule is disabled or the query is null, an empty ValidationResult is returned.
     * The validation uses either the custom validator (if set) or the pattern (if set).</p>
     *
     * @param query the XPath/XQuery snippet to validate
     * @return a ValidationResult containing the validation outcome
     */
    public ValidationResult validate(String query) {
        if (!enabled || query == null) {
            return new ValidationResult();
        }

        ValidationResult result;

        if (customValidator != null) {
            result = customValidator.apply(query);
        } else if (pattern != null) {
            result = validateWithPattern(query);
        } else {
            result = new ValidationResult();
        }

        return result;
    }

    /**
     * Validates the query using the configured regex pattern.
     *
     * @param query the query string to validate
     * @return the validation result based on pattern matching
     */
    private ValidationResult validateWithPattern(String query) {
        ValidationResult result = new ValidationResult();

        boolean matches = pattern.matcher(query).find();

        if (!matches && severity == Severity.ERROR) {
            result.addError(getFormattedErrorMessage());
        } else if (!matches && severity == Severity.WARNING) {
            result.addWarning(getFormattedErrorMessage());
        } else if (!matches && severity == Severity.INFO) {
            result.addInfo(getFormattedErrorMessage());
        } else if (!matches && severity == Severity.SUGGESTION) {
            result.addSuggestion(getFormattedErrorMessage());
        }

        return result;
    }

    /**
     * Returns the formatted error message for validation failures.
     * Uses the custom error message if set, otherwise generates a default message.
     *
     * @return the formatted error message
     */
    private String getFormattedErrorMessage() {
        if (errorMessage != null) {
            return errorMessage;
        }
        return String.format("%s validation failed: %s", name, description != null ? description : "");
    }

    // ========== Predefined Rules Factory ==========

    /**
     * Factory class providing common predefined validation rules for XPath/XQuery snippets.
     * These rules cover common validation scenarios including syntax checking,
     * performance optimization, and security considerations.
     */
    public static class CommonRules {

        /**
         * Private constructor to prevent instantiation.
         * This class contains only static factory methods.
         */
        private CommonRules() {
            // Utility class - not meant to be instantiated
        }

        /**
         * Creates a rule that warns about double slash usage in XPath expressions.
         * Double slash can cause performance issues on large XML documents.
         *
         * @return a validation rule for detecting double slash usage
         */
        public static SnippetValidationRule noDoubleSlash() {
            return builder("Avoid Double Slash")
                    .type(RuleType.PERFORMANCE)
                    .severity(Severity.WARNING)
                    .description("Double slash (//) can be slow on large documents")
                    .pattern("//")
                    .errorMessage("Consider using more specific paths instead of '//' for better performance")
                    .suggestion("Use specific element paths like '/root/element' instead of '//element'")
                    .build();
        }

        /**
         * Creates a rule that suggests minimizing wildcard usage in XPath expressions.
         * Wildcards can impact query performance.
         *
         * @return a validation rule for detecting wildcard usage
         */
        public static SnippetValidationRule noWildcards() {
            return builder("Minimize Wildcards")
                    .type(RuleType.PERFORMANCE)
                    .severity(Severity.SUGGESTION)
                    .description("Wildcards (*) can impact performance")
                    .pattern("\\*")
                    .errorMessage("Wildcard usage detected - consider more specific selectors")
                    .suggestion("Use specific element names when possible")
                    .build();
        }

        /**
         * Creates a rule that encourages namespace prefix usage for clarity.
         * Using namespaces improves code readability and avoids ambiguity.
         *
         * @return a validation rule for encouraging namespace usage
         */
        public static SnippetValidationRule requireNamespaces() {
            return builder("Namespace Usage")
                    .type(RuleType.BEST_PRACTICE)
                    .severity(Severity.INFO)
                    .description("Consider using namespace prefixes for better clarity")
                    .pattern("^[^:]+$") // No colons (no namespaces)
                    .errorMessage("Consider using namespace prefixes for elements")
                    .suggestion("Use namespace prefixes like 'ns:element' for clarity")
                    .build();
        }

        /**
         * Creates a rule that performs basic XPath syntax validation.
         * Checks for balanced brackets and parentheses, and empty predicates.
         *
         * @return a validation rule for basic syntax checking
         */
        public static SnippetValidationRule syntaxCheck() {
            return builder("Basic Syntax Check")
                    .type(RuleType.SYNTAX)
                    .severity(Severity.ERROR)
                    .description("Basic XPath syntax validation")
                    .validator(query -> {
                        ValidationResult result = new ValidationResult();

                        // Check for balanced brackets
                        int openBrackets = 0;
                        int openParens = 0;

                        for (char c : query.toCharArray()) {
                            if (c == '[') openBrackets++;
                            else if (c == ']') openBrackets--;
                            else if (c == '(') openParens++;
                            else if (c == ')') openParens--;
                        }

                        if (openBrackets != 0) {
                            result.addError("Unbalanced square brackets in XPath expression");
                        }
                        if (openParens != 0) {
                            result.addError("Unbalanced parentheses in XPath expression");
                        }

                        // Check for empty predicates
                        if (query.contains("[]")) {
                            result.addWarning("Empty predicate found - consider removing or adding condition");
                        }

                        return result;
                    })
                    .build();
        }

        /**
         * Creates a rule that checks for potentially unsafe XPath constructs.
         * Detects external document access and system function usage.
         *
         * @return a validation rule for security checking
         */
        public static SnippetValidationRule securityCheck() {
            return builder("Security Check")
                    .type(RuleType.SECURITY)
                    .severity(Severity.WARNING)
                    .description("Checks for potentially unsafe XPath constructs")
                    .validator(query -> {
                        ValidationResult result = new ValidationResult();

                        // Check for external document access
                        if (query.contains("document(") || query.contains("doc(")) {
                            result.addWarning("External document access detected - ensure input is trusted");
                        }

                        // Check for system functions
                        if (query.contains("system-property") || query.contains("environment-variable")) {
                            result.addWarning("System function usage detected - review for security implications");
                        }

                        return result;
                    })
                    .suggestion("Review external references and system function calls")
                    .build();
        }

        /**
         * Returns a list containing all common validation rules.
         * Includes syntax check, performance rules, namespace usage, and security check.
         *
         * @return a list of all predefined common validation rules
         */
        public static List<SnippetValidationRule> getAllRules() {
            List<SnippetValidationRule> rules = new ArrayList<>();
            rules.add(syntaxCheck());
            rules.add(noDoubleSlash());
            rules.add(noWildcards());
            rules.add(requireNamespaces());
            rules.add(securityCheck());
            return rules;
        }
    }

    // ========== Getters and Setters ==========

    /**
     * Returns the unique identifier of this rule.
     *
     * @return the rule ID, or null if not set
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this rule.
     *
     * @param id the unique identifier to set
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
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the description of what this rule validates.
     *
     * @return the description text, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of what this rule validates.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the type of this validation rule.
     *
     * @return the rule type
     */
    public RuleType getType() {
        return type;
    }

    /**
     * Sets the type of this validation rule.
     *
     * @param type the rule type to set
     */
    public void setType(RuleType type) {
        this.type = type;
    }

    /**
     * Returns the severity level for violations of this rule.
     *
     * @return the severity level
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Sets the severity level for violations of this rule.
     *
     * @param severity the severity level to set
     */
    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    /**
     * Returns whether this rule is enabled.
     *
     * @return true if the rule is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this rule is enabled.
     *
     * @param enabled true to enable the rule, false to disable it
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the regex pattern used for pattern-based validation.
     *
     * @return the compiled Pattern, or null if not set
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * Sets the regex pattern for pattern-based validation.
     *
     * @param pattern the compiled Pattern to set
     */
    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    /**
     * Returns the custom validator function for complex validation.
     *
     * @return the custom validator function, or null if not set
     */
    public Function<String, ValidationResult> getCustomValidator() {
        return customValidator;
    }

    /**
     * Sets the custom validator function for complex validation.
     *
     * @param customValidator the custom validation function to set
     */
    public void setCustomValidator(Function<String, ValidationResult> customValidator) {
        this.customValidator = customValidator;
    }

    /**
     * Returns the error message displayed when validation fails.
     *
     * @return the error message, or null if not set
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message to display when validation fails.
     *
     * @param errorMessage the error message to set
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the suggestion for fixing validation issues.
     *
     * @return the suggestion text, or null if not set
     */
    public String getSuggestion() {
        return suggestion;
    }

    /**
     * Sets the suggestion for fixing validation issues.
     *
     * @param suggestion the suggestion text to set
     */
    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    /**
     * Returns the documentation URL for more information about this rule.
     *
     * @return the documentation URL, or null if not set
     */
    public String getDocumentationUrl() {
        return documentationUrl;
    }

    /**
     * Sets the documentation URL for more information about this rule.
     *
     * @param documentationUrl the URL to set
     */
    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }

    /**
     * Returns a string representation of this validation rule.
     * Includes the rule name, type, and severity.
     *
     * @return a string representation of this rule
     */
    @Override
    public String toString() {
        return String.format("SnippetValidationRule{name='%s', type=%s, severity=%s}",
                name, type, severity);
    }

    // ========== Validation Result Class ==========

    /**
     * Container class for validation results from snippet validation.
     * Stores categorized messages including errors, warnings, informational messages,
     * and suggestions.
     *
     * <p>Errors indicate critical issues that must be fixed. Warnings indicate
     * issues that should be reviewed. Info provides informational feedback,
     * and suggestions offer optional improvements.</p>
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();
        private final List<String> suggestions = new ArrayList<>();

        /**
         * Creates a new empty ValidationResult.
         * All message lists (errors, warnings, info, suggestions) are initialized as empty.
         */
        public ValidationResult() {
            // Default constructor - all lists are initialized inline
        }

        /**
         * Adds an error message to the validation result.
         * Errors indicate critical issues that must be fixed.
         *
         * @param error the error message to add
         */
        public void addError(String error) {
            errors.add(error);
        }

        /**
         * Adds a warning message to the validation result.
         * Warnings indicate issues that should be reviewed.
         *
         * @param warning the warning message to add
         */
        public void addWarning(String warning) {
            warnings.add(warning);
        }

        /**
         * Adds an informational message to the validation result.
         * Info messages provide informational feedback that does not require action.
         *
         * @param info the informational message to add
         */
        public void addInfo(String info) {
            this.info.add(info);
        }

        /**
         * Adds a suggestion message to the validation result.
         * Suggestions offer optional improvements to consider.
         *
         * @param suggestion the suggestion message to add
         */
        public void addSuggestion(String suggestion) {
            suggestions.add(suggestion);
        }

        /**
         * Checks whether the validation passed without errors.
         * A result is considered valid if there are no error messages.
         *
         * @return true if there are no errors, false otherwise
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Checks whether the validation result contains warnings.
         *
         * @return true if there are warning messages, false otherwise
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        /**
         * Checks whether the validation result contains informational messages.
         *
         * @return true if there are informational messages, false otherwise
         */
        public boolean hasInfo() {
            return !info.isEmpty();
        }

        /**
         * Checks whether the validation result contains suggestions.
         *
         * @return true if there are suggestion messages, false otherwise
         */
        public boolean hasSuggestions() {
            return !suggestions.isEmpty();
        }

        /**
         * Returns a copy of the error messages list.
         *
         * @return a new list containing all error messages
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Returns a copy of the warning messages list.
         *
         * @return a new list containing all warning messages
         */
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        /**
         * Returns a copy of the informational messages list.
         *
         * @return a new list containing all informational messages
         */
        public List<String> getInfo() {
            return new ArrayList<>(info);
        }

        /**
         * Returns a copy of the suggestion messages list.
         *
         * @return a new list containing all suggestion messages
         */
        public List<String> getSuggestions() {
            return new ArrayList<>(suggestions);
        }

        /**
         * Checks whether the validation result contains any issues at all.
         * This includes errors, warnings, informational messages, and suggestions.
         *
         * @return true if there are any messages of any type, false otherwise
         */
        public boolean hasAnyIssues() {
            return !errors.isEmpty() || !warnings.isEmpty() || !info.isEmpty() || !suggestions.isEmpty();
        }
    }
}
