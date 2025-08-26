package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a code snippet template for XML IntelliSense.
 * Supports parameterized templates with placeholders and transformations.
 */
public class SnippetTemplate {

    // Pattern for template variables: ${variable:default_value}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))\\}");

    // Pattern for cursor position: $0
    private static final Pattern CURSOR_PATTERN = Pattern.compile("\\$0");

    // Pattern for tab stops: $1, $2, etc.
    private static final Pattern TABSTOP_PATTERN = Pattern.compile("\\$(\\d+)");

    private final String id;
    private final String name;
    private final String description;
    private final String content;
    private final TemplateCategory category;
    private final Set<String> tags;
    private final Map<String, String> defaultValues;
    private final List<String> requiredVariables;
    private final int priority;
    private final boolean contextSensitive;

    // Template metadata
    private final String author;
    private final String version;
    private final long createdAt;

    public SnippetTemplate(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.content = builder.content;
        this.category = builder.category;
        this.tags = new HashSet<>(builder.tags);
        this.defaultValues = new HashMap<>(builder.defaultValues);
        this.requiredVariables = new ArrayList<>(builder.requiredVariables);
        this.priority = builder.priority;
        this.contextSensitive = builder.contextSensitive;
        this.author = builder.author;
        this.version = builder.version;
        this.createdAt = builder.createdAt;
    }

    /**
     * Expand template with provided variable values
     */
    public TemplateExpansion expand(Map<String, String> variables) {
        return expand(variables, new ExpansionOptions());
    }

    /**
     * Expand template with options
     */
    public TemplateExpansion expand(Map<String, String> variables, ExpansionOptions options) {
        String expandedContent = content;
        Map<String, String> usedVariables = new HashMap<>();
        List<TabStop> tabStops = new ArrayList<>();
        int cursorPosition = -1;

        // Expand variables
        Matcher matcher = VARIABLE_PATTERN.matcher(expandedContent);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String value = resolveVariableValue(variableName, defaultValue, variables, options);
            usedVariables.put(variableName, value);

            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        expandedContent = sb.toString();

        // Find tab stops
        matcher = TABSTOP_PATTERN.matcher(expandedContent);
        sb = new StringBuffer();

        while (matcher.find()) {
            int tabNumber = Integer.parseInt(matcher.group(1));
            int position = matcher.start();

            if (tabNumber == 0) {
                cursorPosition = position;
                matcher.appendReplacement(sb, "");
            } else {
                tabStops.add(new TabStop(tabNumber, position, ""));
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        expandedContent = sb.toString();

        // Sort tab stops by number
        tabStops.sort(Comparator.comparingInt(ts -> ts.number));

        return new TemplateExpansion(
                expandedContent,
                usedVariables,
                tabStops,
                cursorPosition >= 0 ? cursorPosition : expandedContent.length()
        );
    }

    /**
     * Resolve variable value with fallback chain
     */
    private String resolveVariableValue(String variableName, String defaultValue,
                                        Map<String, String> variables, ExpansionOptions options) {

        // 1. Use provided variable value
        if (variables.containsKey(variableName)) {
            String value = variables.get(variableName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 2. Use context-aware values
        if (options.context != null) {
            String contextValue = resolveContextValue(variableName, options.context);
            if (contextValue != null) {
                return contextValue;
            }
        }

        // 3. Use template default values
        if (defaultValues.containsKey(variableName)) {
            return defaultValues.get(variableName);
        }

        // 4. Use parameter default value
        if (defaultValue != null && !defaultValue.isEmpty()) {
            return defaultValue;
        }

        // 5. Use global default value
        if (options.globalDefaults.containsKey(variableName)) {
            return options.globalDefaults.get(variableName);
        }

        // 6. Use system values
        return resolveSystemValue(variableName);
    }

    /**
     * Resolve context-aware variable values
     */
    private String resolveContextValue(String variableName, TemplateContext context) {
        return switch (variableName.toLowerCase()) {
            case "namespace" -> context.currentNamespace;
            case "element" -> context.currentElement;
            case "prefix" -> context.namespacePrefix;
            case "schema" -> context.schemaName;
            case "date" -> java.time.LocalDate.now().toString();
            case "time" -> java.time.LocalTime.now().toString();
            case "datetime" -> java.time.LocalDateTime.now().toString();
            case "user" -> System.getProperty("user.name", "");
            case "filename" -> context.fileName != null ? context.fileName : "";
            default -> null;
        };
    }

    /**
     * Resolve system variable values
     */
    private String resolveSystemValue(String variableName) {
        return switch (variableName.toLowerCase()) {
            case "date" -> java.time.LocalDate.now().toString();
            case "time" -> java.time.LocalTime.now().toString();
            case "datetime" -> java.time.LocalDateTime.now().toString();
            case "user" -> System.getProperty("user.name", "");
            case "uuid" -> UUID.randomUUID().toString();
            case "year" -> String.valueOf(java.time.Year.now().getValue());
            case "month" -> String.valueOf(java.time.MonthDay.now().getMonthValue());
            case "day" -> String.valueOf(java.time.MonthDay.now().getDayOfMonth());
            default -> "${" + variableName + "}"; // Keep placeholder if unresolved
        };
    }

    /**
     * Extract all variables from template content
     */
    public Set<String> extractVariables() {
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }

    /**
     * Validate template content
     */
    public ValidationResult validate() {
        List<String> issues = new ArrayList<>();

        // Check for required fields
        if (id == null || id.trim().isEmpty()) {
            issues.add("Template ID is required");
        }

        if (name == null || name.trim().isEmpty()) {
            issues.add("Template name is required");
        }

        if (content == null || content.trim().isEmpty()) {
            issues.add("Template content is required");
        }

        // Validate variable syntax
        try {
            Matcher matcher = VARIABLE_PATTERN.matcher(content);
            while (matcher.find()) {
                String variableName = matcher.group(1);
                if (variableName.trim().isEmpty()) {
                    issues.add("Empty variable name found");
                }
            }
        } catch (Exception e) {
            issues.add("Invalid variable syntax: " + e.getMessage());
        }

        // Check for required variables
        Set<String> templateVars = extractVariables();
        for (String required : requiredVariables) {
            if (!templateVars.contains(required)) {
                issues.add("Required variable '" + required + "' not found in template");
            }
        }

        boolean valid = issues.isEmpty();
        String message = valid ? "Template is valid" : "Template has validation issues";

        return new ValidationResult(valid, message, issues);
    }

    /**
     * Check if template matches context
     */
    public boolean matchesContext(TemplateContext context) {
        if (!contextSensitive) {
            return true;
        }

        // Check namespace compatibility
        if (context.currentNamespace != null && tags.contains("namespace:" + context.currentNamespace)) {
            return true;
        }

        // Check element compatibility
        if (context.currentElement != null && tags.contains("element:" + context.currentElement)) {
            return true;
        }

        // Check category compatibility
        if (context.expectedCategory != null && category == context.expectedCategory) {
            return true;
        }

        // Default: template matches if no specific context requirements
        return tags.stream().noneMatch(tag -> tag.startsWith("namespace:") || tag.startsWith("element:"));
    }

    /**
     * Get template as completion item
     */
    public CompletionItem toCompletionItem() {
        return new CompletionItem.Builder(name, content, CompletionItemType.SNIPPET)
                .description(description)
                .dataType("template")
                .relevanceScore(priority)
                .shortcut("snippet")
                .build();
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContent() {
        return content;
    }

    public TemplateCategory getCategory() {
        return category;
    }

    public Set<String> getTags() {
        return new HashSet<>(tags);
    }

    public Map<String, String> getDefaultValues() {
        return new HashMap<>(defaultValues);
    }

    public List<String> getRequiredVariables() {
        return new ArrayList<>(requiredVariables);
    }

    public int getPriority() {
        return priority;
    }

    public boolean isContextSensitive() {
        return contextSensitive;
    }

    public String getAuthor() {
        return author;
    }

    public String getVersion() {
        return version;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return String.format("SnippetTemplate{id='%s', name='%s', category=%s}", id, name, category);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnippetTemplate that = (SnippetTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // Builder pattern
    public static class Builder {
        private final String id;
        private final String name;
        private String description = "";
        private final String content;
        private TemplateCategory category = TemplateCategory.GENERAL;
        private final Set<String> tags = new HashSet<>();
        private final Map<String, String> defaultValues = new HashMap<>();
        private final List<String> requiredVariables = new ArrayList<>();
        private int priority = 100;
        private boolean contextSensitive = false;
        private String author = "";
        private String version = "1.0";
        private final long createdAt = System.currentTimeMillis();

        public Builder(String id, String name, String content) {
            this.id = id;
            this.name = name;
            this.content = content;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(TemplateCategory category) {
            this.category = category;
            return this;
        }

        public Builder tags(String... tags) {
            Collections.addAll(this.tags, tags);
            return this;
        }

        public Builder tags(Collection<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        public Builder defaultValue(String variable, String value) {
            this.defaultValues.put(variable, value);
            return this;
        }

        public Builder defaultValues(Map<String, String> values) {
            this.defaultValues.putAll(values);
            return this;
        }

        public Builder requiredVariable(String variable) {
            this.requiredVariables.add(variable);
            return this;
        }

        public Builder requiredVariables(String... variables) {
            Collections.addAll(this.requiredVariables, variables);
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder contextSensitive(boolean contextSensitive) {
            this.contextSensitive = contextSensitive;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public SnippetTemplate build() {
            return new SnippetTemplate(this);
        }
    }

    // Supporting classes
    public enum TemplateCategory {
        GENERAL,
        XML_STRUCTURE,
        XML_NAMESPACE,
        XML_SCHEMA,
        WEB_SERVICES,
        CONFIGURATION,
        DATA_BINDING,
        DOCUMENTATION,
        CUSTOM
    }

    public record TemplateExpansion(String expandedContent, Map<String, String> usedVariables, List<TabStop> tabStops,
                                    int cursorPosition) {
            public TemplateExpansion(String expandedContent, Map<String, String> usedVariables,
                                     List<TabStop> tabStops, int cursorPosition) {
                this.expandedContent = expandedContent;
                this.usedVariables = new HashMap<>(usedVariables);
                this.tabStops = new ArrayList<>(tabStops);
                this.cursorPosition = cursorPosition;
            }
        }

    public record TabStop(int number, int position, String placeholder) {

        @Override
            public String toString() {
                return String.format("TabStop{number=%d, position=%d, placeholder='%s'}",
                        number, position, placeholder);
            }
        }

    public static class TemplateContext {
        public String currentNamespace;
        public String namespacePrefix;
        public String currentElement;
        public String schemaName;
        public String fileName;
        public TemplateCategory expectedCategory;

        public TemplateContext() {
        }

        public TemplateContext(String currentNamespace, String currentElement) {
            this.currentNamespace = currentNamespace;
            this.currentElement = currentElement;
        }
    }

    public static class ExpansionOptions {
        public TemplateContext context;
        public Map<String, String> globalDefaults = new HashMap<>();
        public boolean preserveWhitespace = true;
        public boolean autoIndent = true;

        public ExpansionOptions() {
        }

        public ExpansionOptions context(TemplateContext context) {
            this.context = context;
            return this;
        }

        public ExpansionOptions globalDefault(String variable, String value) {
            this.globalDefaults.put(variable, value);
            return this;
        }
    }

    public record ValidationResult(boolean isValid, String message, List<String> issues) {
            public ValidationResult(boolean isValid, String message, List<String> issues) {
                this.isValid = isValid;
                this.message = message;
                this.issues = new ArrayList<>(issues);
            }

            @Override
            public String toString() {
                return String.format("ValidationResult{valid=%s, message='%s', issues=%d}",
                        isValid, message, issues.size());
            }
        }
}