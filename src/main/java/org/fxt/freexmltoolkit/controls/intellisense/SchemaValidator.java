package org.fxt.freexmltoolkit.controls.intellisense;

import org.fxt.freexmltoolkit.service.xsd.adapters.IntelliSenseAdapter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Multi-schema validator for IntelliSense with namespace support.
 * Validates XML content against multiple schemas simultaneously.
 */
public class SchemaValidator {

    // Validation cache
    private final Map<String, ValidationResult> validationCache = new ConcurrentHashMap<>();

    // Multi-schema manager
    private final MultiSchemaManager schemaManager;

    // Namespace resolver
    private final NamespaceResolver namespaceResolver;

    // Validation rules cache
    private final Map<String, List<ValidationRule>> validationRulesCache = new ConcurrentHashMap<>();

    // Common XML patterns
    private static final Pattern XML_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][\\w.-]*$");
    private static final Pattern XML_ELEMENT_PATTERN = Pattern.compile("<(\\w+(?::\\w+)?)(?:\\s[^>]*)?>.*?</\\1>", Pattern.DOTALL);
    private static final Pattern XML_SELF_CLOSING_PATTERN = Pattern.compile("<(\\w+(?::\\w+)?)(?:\\s[^>]*)?/>");

    public SchemaValidator(MultiSchemaManager schemaManager, NamespaceResolver namespaceResolver) {
        this.schemaManager = schemaManager;
        this.namespaceResolver = namespaceResolver;
    }

    /**
     * Validate XML content against all registered schemas
     */
    public ValidationResult validateContent(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new ValidationResult(true, "Empty content", Collections.emptyList());
        }

        // Check cache first
        String cacheKey = generateCacheKey(xmlContent);
        ValidationResult cached = validationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try (AutoCloseable timer =
                     PerformanceProfiler.getInstance().startOperation("schema-validation")) {

            ValidationResult result = performValidation(xmlContent);

            // Cache result
            validationCache.put(cacheKey, result);

            return result;
        } catch (Exception e) {
            // Handle profiler exceptions gracefully
            try {
                ValidationResult result = performValidation(xmlContent);
                validationCache.put(cacheKey, result);
                return result;
            } catch (Exception validationException) {
                return new ValidationResult(false, "Validation error: " + validationException.getMessage(),
                        Collections.singletonList(new ValidationIssue(
                                ValidationSeverity.ERROR, 0, 0, "Validation failed", validationException.getMessage())));
            }
        }
    }

    /**
     * Perform actual validation
     */
    private ValidationResult performValidation(String xmlContent) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Parse namespace declarations
        namespaceResolver.parseNamespaceDeclarations(xmlContent);

        // Basic XML well-formedness check
        issues.addAll(validateWellFormedness(xmlContent));

        // Validate against schemas
        Map<String, MultiSchemaManager.SchemaInfo> schemas = schemaManager.getAllSchemas();
        for (MultiSchemaManager.SchemaInfo schema : schemas.values()) {
            issues.addAll(validateAgainstSchema(xmlContent, schema));
        }

        // Namespace validation
        issues.addAll(validateNamespaces(xmlContent));

        // Remove duplicate issues
        issues = removeDuplicateIssues(issues);

        // Sort by severity and position
        issues.sort((a, b) -> {
            int severityComp = b.severity.ordinal() - a.severity.ordinal();
            if (severityComp != 0) return severityComp;

            int lineComp = Integer.compare(a.line, b.line);
            if (lineComp != 0) return lineComp;

            return Integer.compare(a.column, b.column);
        });

        boolean isValid = issues.stream().noneMatch(issue -> issue.severity == ValidationSeverity.ERROR);
        String message = isValid ? "Valid" : "Validation failed";

        return new ValidationResult(isValid, message, issues);
    }

    /**
     * Validate XML well-formedness
     */
    private List<ValidationIssue> validateWellFormedness(String xmlContent) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check for basic XML structure
        if (!xmlContent.trim().startsWith("<")) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, 1, 1,
                    "Invalid XML", "XML content must start with an element"));
            return issues;
        }

        // Check for balanced tags (simplified)
        issues.addAll(validateTagBalance(xmlContent));

        // Check for proper XML names
        issues.addAll(validateXmlNames(xmlContent));

        return issues;
    }

    /**
     * Validate tag balance
     */
    private List<ValidationIssue> validateTagBalance(String xmlContent) {
        List<ValidationIssue> issues = new ArrayList<>();
        Stack<String> tagStack = new Stack<>();

        // Simple tag parsing (this is a simplified version)
        String[] lines = xmlContent.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            // Find opening and closing tags in the line
            // This is a simplified implementation - a full parser would be more robust
            if (line.contains("</")) {
                // Closing tag
                int closeStart = line.indexOf("</");
                int closeEnd = line.indexOf(">", closeStart);
                if (closeEnd > closeStart) {
                    String closingTag = line.substring(closeStart + 2, closeEnd);
                    if (tagStack.isEmpty() || !tagStack.peek().equals(closingTag)) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR, lineNumber, closeStart + 1,
                                "Unmatched closing tag", "Closing tag '" + closingTag + "' has no matching opening tag"));
                    } else {
                        tagStack.pop();
                    }
                }
            }

            if (line.contains("<") && !line.contains("</") && !line.contains("/>")) {
                // Opening tag
                int openStart = line.indexOf("<");
                int openEnd = line.indexOf(">", openStart);
                if (openEnd > openStart) {
                    String openingTag = line.substring(openStart + 1, openEnd);
                    // Remove attributes
                    if (openingTag.contains(" ")) {
                        openingTag = openingTag.substring(0, openingTag.indexOf(" "));
                    }
                    tagStack.push(openingTag);
                }
            }
        }

        // Check for unmatched opening tags
        while (!tagStack.isEmpty()) {
            String unmatchedTag = tagStack.pop();
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, lines.length, 1,
                    "Unmatched opening tag", "Opening tag '" + unmatchedTag + "' has no matching closing tag"));
        }

        return issues;
    }

    /**
     * Validate XML names
     */
    private List<ValidationIssue> validateXmlNames(String xmlContent) {
        List<ValidationIssue> issues = new ArrayList<>();

        // This would typically use a proper XML parser
        // For now, we'll do basic validation
        String[] lines = xmlContent.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            // Check element names
            Pattern elementPattern = Pattern.compile("<(\\w+(?::\\w+)?)");
            java.util.regex.Matcher matcher = elementPattern.matcher(line);
            while (matcher.find()) {
                String elementName = matcher.group(1);
                if (!XML_NAME_PATTERN.matcher(elementName).matches()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, lineNumber, matcher.start() + 1,
                            "Invalid element name", "Element name '" + elementName + "' is not a valid XML name"));
                }
            }
        }

        return issues;
    }

    /**
     * Validate against specific schema
     */
    private List<ValidationIssue> validateAgainstSchema(String xmlContent, MultiSchemaManager.SchemaInfo schema) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Get validation rules for this schema
        List<ValidationRule> rules = getValidationRules(schema);

        // Apply validation rules
        for (ValidationRule rule : rules) {
            issues.addAll(rule.validate(xmlContent));
        }

        return issues;
    }

    /**
     * Get validation rules for schema
     */
    private List<ValidationRule> getValidationRules(MultiSchemaManager.SchemaInfo schema) {
        return validationRulesCache.computeIfAbsent(schema.schemaId, id -> {
            List<ValidationRule> rules = new ArrayList<>();

            // Generate rules from schema elements
            for (IntelliSenseAdapter.ElementInfo element : schema.extractedInfo.elements) {
                rules.add(new ElementValidationRule(element));
            }

            // Generate rules from schema attributes
            for (IntelliSenseAdapter.AttributeInfo attribute : schema.extractedInfo.attributes) {
                rules.add(new AttributeValidationRule(attribute));
            }

            return rules;
        });
    }

    /**
     * Validate namespaces
     */
    private List<ValidationIssue> validateNamespaces(String xmlContent) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check for undefined namespace prefixes
        Pattern prefixPattern = Pattern.compile("<(\\w+):(\\w+)");
        java.util.regex.Matcher matcher = prefixPattern.matcher(xmlContent);

        String[] lines = xmlContent.split("\n");
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String namespace = namespaceResolver.getNamespaceForPrefix(prefix);

            if (namespace == null) {
                // Find line number
                int position = matcher.start();
                int lineNumber = getLineNumber(xmlContent, position);
                int columnNumber = getColumnNumber(xmlContent, position, lineNumber);

                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, lineNumber, columnNumber,
                        "Undefined namespace prefix", "Namespace prefix '" + prefix + "' is not declared"));
            }
        }

        return issues;
    }

    /**
     * Remove duplicate issues
     */
    private List<ValidationIssue> removeDuplicateIssues(List<ValidationIssue> issues) {
        Set<String> seen = new HashSet<>();
        return issues.stream()
                .filter(issue -> {
                    String key = issue.line + ":" + issue.column + ":" + issue.message;
                    return seen.add(key);
                })
                .toList();
    }

    /**
     * Get line number from position
     */
    private int getLineNumber(String content, int position) {
        int lineNumber = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }

    /**
     * Get column number from position and line number
     */
    private int getColumnNumber(String content, int position, int lineNumber) {
        String[] lines = content.split("\n");
        if (lineNumber <= lines.length) {
            int lineStart = 0;
            for (int i = 1; i < lineNumber; i++) {
                lineStart += lines[i - 1].length() + 1; // +1 for newline
            }
            return position - lineStart + 1;
        }
        return 1;
    }

    /**
     * Generate cache key for validation result
     */
    private String generateCacheKey(String xmlContent) {
        return Integer.toString(xmlContent.hashCode());
    }

    /**
     * Clear validation cache
     */
    public void clearCache() {
        validationCache.clear();
        validationRulesCache.clear();
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(validationCache.size(), validationRulesCache.size());
    }

    // Validation result classes
        public record ValidationResult(boolean isValid, String message, List<ValidationIssue> issues) {
            public ValidationResult(boolean isValid, String message, List<ValidationIssue> issues) {
                this.isValid = isValid;
                this.message = message;
                this.issues = new ArrayList<>(issues);
            }

            public List<ValidationIssue> getErrorsOnly() {
                return issues.stream()
                        .filter(issue -> issue.severity == ValidationSeverity.ERROR)
                        .toList();
            }

            public List<ValidationIssue> getWarningsOnly() {
                return issues.stream()
                        .filter(issue -> issue.severity == ValidationSeverity.WARNING)
                        .toList();
            }

            @Override
            public String toString() {
                return String.format("ValidationResult{valid=%s, issues=%d, message='%s'}",
                        isValid, issues.size(), message);
            }
        }

    public record ValidationIssue(ValidationSeverity severity, int line, int column, String title, String message) {

        @Override
            public String toString() {
                return String.format("%s at %d:%d - %s: %s",
                        severity, line, column, title, message);
            }
        }

    public enum ValidationSeverity {
        ERROR, WARNING, INFO
    }

    // Validation rule interface
    private interface ValidationRule {
        List<ValidationIssue> validate(String xmlContent);
    }

    // Element validation rule
    private record ElementValidationRule(IntelliSenseAdapter.ElementInfo elementInfo) implements ValidationRule {

        @Override
        public List<ValidationIssue> validate(String xmlContent) {
            List<ValidationIssue> issues = new ArrayList<>();

            // Check if required element is present
            if (elementInfo.required) {
                if (!xmlContent.contains("<" + elementInfo.name)) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, 1, 1,
                            "Missing required element",
                            "Required element '" + elementInfo.name + "' is missing"));
                }
            }

            return issues;
        }
    }

    // Attribute validation rule
    private record AttributeValidationRule(
            IntelliSenseAdapter.AttributeInfo attributeInfo) implements ValidationRule {

        @Override
        public List<ValidationIssue> validate(String xmlContent) {
            List<ValidationIssue> issues = new ArrayList<>();

            // Check if required attribute is present
            if (attributeInfo.required) {
                if (!xmlContent.contains(attributeInfo.name + "=")) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING, 1, 1,
                            "Missing required attribute",
                            "Required attribute '" + attributeInfo.name + "' may be missing"));
                }
            }

            return issues;
        }
    }

    // Cache statistics
        public record CacheStatistics(int validationCacheSize, int rulesCacheSize) {

        @Override
            public String toString() {
                return String.format("CacheStatistics{validation=%d, rules=%d}",
                        validationCacheSize, rulesCacheSize);
            }
        }
}