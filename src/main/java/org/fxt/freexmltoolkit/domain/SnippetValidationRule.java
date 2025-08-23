package org.fxt.freexmltoolkit.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Validation rules for XPath/XQuery snippets to ensure quality and correctness.
 * Provides extensible validation framework for snippet content and structure.
 */
public class SnippetValidationRule {

    public enum RuleType {
        SYNTAX("Syntax Validation", "Validates XPath/XQuery syntax"),
        PERFORMANCE("Performance Check", "Checks for performance issues"),
        SECURITY("Security Check", "Validates security considerations"),
        BEST_PRACTICE("Best Practice", "Enforces coding best practices"),
        CUSTOM("Custom Rule", "User-defined validation rule");

        private final String displayName;
        private final String description;

        RuleType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Severity {
        ERROR("Error", "Must be fixed", "#dc3545"),
        WARNING("Warning", "Should be reviewed", "#ffc107"),
        INFO("Info", "Informational", "#17a2b8"),
        SUGGESTION("Suggestion", "Consider improvement", "#28a745");

        private final String displayName;
        private final String description;
        private final String color;

        Severity(String displayName, String description, String color) {
            this.displayName = displayName;
            this.description = description;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

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

    // Constructors
    public SnippetValidationRule() {
        this.enabled = true;
        this.severity = Severity.WARNING;
    }

    public SnippetValidationRule(String name, RuleType type, Severity severity, String description) {
        this();
        this.name = name;
        this.type = type;
        this.severity = severity;
        this.description = description;
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private final SnippetValidationRule rule;

        public Builder(String name) {
            this.rule = new SnippetValidationRule();
            this.rule.name = name;
        }

        public Builder description(String description) {
            rule.description = description;
            return this;
        }

        public Builder type(RuleType type) {
            rule.type = type;
            return this;
        }

        public Builder severity(Severity severity) {
            rule.severity = severity;
            return this;
        }

        public Builder enabled(boolean enabled) {
            rule.enabled = enabled;
            return this;
        }

        public Builder pattern(String pattern) {
            rule.pattern = Pattern.compile(pattern);
            return this;
        }

        public Builder pattern(Pattern pattern) {
            rule.pattern = pattern;
            return this;
        }

        public Builder validator(Function<String, ValidationResult> validator) {
            rule.customValidator = validator;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            rule.errorMessage = errorMessage;
            return this;
        }

        public Builder suggestion(String suggestion) {
            rule.suggestion = suggestion;
            return this;
        }

        public Builder documentationUrl(String url) {
            rule.documentationUrl = url;
            return this;
        }

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

    public static Builder builder(String name) {
        return new Builder(name);
    }

    // ========== Validation Methods ==========

    /**
     * Validate snippet query against this rule
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
     * Validate using regex pattern
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
     * Get formatted error message
     */
    private String getFormattedErrorMessage() {
        if (errorMessage != null) {
            return errorMessage;
        }
        return String.format("%s validation failed: %s", name, description != null ? description : "");
    }

    // ========== Predefined Rules Factory ==========

    /**
     * Create common validation rules for XPath/XQuery snippets
     */
    public static class CommonRules {

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
         * Get all common validation rules
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

    public RuleType getType() {
        return type;
    }

    public void setType(RuleType type) {
        this.type = type;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public Function<String, ValidationResult> getCustomValidator() {
        return customValidator;
    }

    public void setCustomValidator(Function<String, ValidationResult> customValidator) {
        this.customValidator = customValidator;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getDocumentationUrl() {
        return documentationUrl;
    }

    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }

    @Override
    public String toString() {
        return String.format("SnippetValidationRule{name='%s', type=%s, severity=%s}",
                name, type, severity);
    }

    // ========== Validation Result Class ==========

    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();
        private final List<String> suggestions = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public void addInfo(String info) {
            this.info.add(info);
        }

        public void addSuggestion(String suggestion) {
            suggestions.add(suggestion);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasInfo() {
            return !info.isEmpty();
        }

        public boolean hasSuggestions() {
            return !suggestions.isEmpty();
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        public List<String> getInfo() {
            return new ArrayList<>(info);
        }

        public List<String> getSuggestions() {
            return new ArrayList<>(suggestions);
        }

        public boolean hasAnyIssues() {
            return !errors.isEmpty() || !warnings.isEmpty() || !info.isEmpty() || !suggestions.isEmpty();
        }
    }
}