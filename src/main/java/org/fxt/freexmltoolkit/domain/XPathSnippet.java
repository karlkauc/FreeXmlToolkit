package org.fxt.freexmltoolkit.domain;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents an XPath/XQuery snippet with metadata, execution context, and performance metrics.
 * This is a core model for the revolutionary XPath/XQuery Snippet Manager system.
 */
public class XPathSnippet {

    public enum SnippetType {
        XPATH("XPath Expression", "xpath", "XPath"),
        XQUERY("XQuery Script", "xquery", "XQuery"),
        XPATH_FUNCTION("XPath Function", "xpath-func", "XPath Func"),
        XQUERY_MODULE("XQuery Module", "xquery-mod", "XQuery Mod"),
        FLWOR("FLWOR Expression", "flwor", "FLWOR"),
        TEMPLATE("Template Pattern", "template", "Template");

        private final String displayName;
        private final String cssClass;
        private final String shortName;

        SnippetType(String displayName, String cssClass, String shortName) {
            this.displayName = displayName;
            this.cssClass = cssClass;
            this.shortName = shortName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssClass() {
            return cssClass;
        }

        public String getShortName() {
            return shortName;
        }
    }

    public enum SnippetCategory {
        NAVIGATION("Navigation", "Navigate XML structure", "#007bff"),
        EXTRACTION("Data Extraction", "Extract specific data", "#28a745"),
        FILTERING("Filtering", "Filter XML content", "#ffc107"),
        TRANSFORMATION("Transformation", "Transform XML data", "#17a2b8"),
        VALIDATION("Validation", "Validate XML content", "#dc3545"),
        ANALYSIS("Analysis", "Analyze XML structure", "#6f42c1"),
        UTILITY("Utility", "General purpose utilities", "#6c757d"),
        CUSTOM("Custom", "User-defined snippets", "#fd7e14");

        private final String displayName;
        private final String description;
        private final String color;

        SnippetCategory(String displayName, String description, String color) {
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

    // Core properties
    private String id;
    private String name;
    private String description;
    private SnippetType type;
    private SnippetCategory category;
    private String query;
    private String expectedResult;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
    private LocalDateTime lastExecuted;
    private String author;
    private String version;
    private Set<String> tags;

    // Execution context
    private Map<String, String> variables; // Variable name -> default value
    private Map<String, String> namespaces; // Prefix -> URI
    private String contextPath; // Default context path
    private boolean requiresContext;

    // Performance metrics
    private long executionCount;
    private long totalExecutionTime; // in milliseconds
    private long averageExecutionTime;
    private long lastExecutionTime;
    private boolean isFavorite;

    // Advanced features
    private String documentationUrl;
    private String exampleXml; // Sample XML for testing
    private List<SnippetParameter> parameters; // Parameterized snippets
    private SnippetValidationRule validationRule;

    // Constructors
    public XPathSnippet() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.tags = new HashSet<>();
        this.variables = new HashMap<>();
        this.namespaces = new HashMap<>();
        this.parameters = new ArrayList<>();
        this.executionCount = 0;
        this.totalExecutionTime = 0;
        this.isFavorite = false;
        this.version = "1.0";
    }

    public XPathSnippet(String name, SnippetType type, SnippetCategory category, String query) {
        this();
        this.name = name;
        this.type = type;
        this.category = category;
        this.query = query;
    }

    // ========== Builder Pattern ==========

    public static class Builder {
        private final XPathSnippet snippet;

        public Builder() {
            this.snippet = new XPathSnippet();
        }

        public Builder name(String name) {
            snippet.name = name;
            return this;
        }

        public Builder description(String description) {
            snippet.description = description;
            return this;
        }

        public Builder type(SnippetType type) {
            snippet.type = type;
            return this;
        }

        public Builder category(SnippetCategory category) {
            snippet.category = category;
            return this;
        }

        public Builder query(String query) {
            snippet.query = query;
            return this;
        }

        public Builder expectedResult(String expectedResult) {
            snippet.expectedResult = expectedResult;
            return this;
        }

        public Builder author(String author) {
            snippet.author = author;
            return this;
        }

        public Builder version(String version) {
            snippet.version = version;
            return this;
        }

        public Builder tags(String... tags) {
            snippet.tags.addAll(Arrays.asList(tags));
            return this;
        }

        public Builder variable(String name, String defaultValue) {
            snippet.variables.put(name, defaultValue);
            return this;
        }

        public Builder namespace(String prefix, String uri) {
            snippet.namespaces.put(prefix, uri);
            return this;
        }

        public Builder contextPath(String contextPath) {
            snippet.contextPath = contextPath;
            return this;
        }

        public Builder requiresContext(boolean requiresContext) {
            snippet.requiresContext = requiresContext;
            return this;
        }

        public Builder favorite(boolean isFavorite) {
            snippet.isFavorite = isFavorite;
            return this;
        }

        public Builder documentationUrl(String url) {
            snippet.documentationUrl = url;
            return this;
        }

        public Builder exampleXml(String exampleXml) {
            snippet.exampleXml = exampleXml;
            return this;
        }

        public Builder parameter(SnippetParameter parameter) {
            snippet.parameters.add(parameter);
            return this;
        }

        public Builder validationRule(SnippetValidationRule rule) {
            snippet.validationRule = rule;
            return this;
        }

        public XPathSnippet build() {
            if (snippet.name == null || snippet.name.trim().isEmpty()) {
                throw new IllegalStateException("Snippet name is required");
            }
            if (snippet.query == null || snippet.query.trim().isEmpty()) {
                throw new IllegalStateException("Snippet query is required");
            }
            if (snippet.type == null) {
                snippet.type = SnippetType.XPATH;
            }
            if (snippet.category == null) {
                snippet.category = SnippetCategory.CUSTOM;
            }
            return snippet;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Execution Methods ==========

    /**
     * Record execution metrics
     */
    public void recordExecution(long executionTime) {
        this.lastExecuted = LocalDateTime.now();
        this.lastExecutionTime = executionTime;
        this.executionCount++;
        this.totalExecutionTime += executionTime;
        this.averageExecutionTime = this.totalExecutionTime / this.executionCount;
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Get formatted execution statistics
     */
    public String getExecutionStatistics() {
        if (executionCount == 0) {
            return "Never executed";
        }

        return String.format("Executed %d times, Avg: %dms, Last: %dms",
                executionCount, averageExecutionTime, lastExecutionTime);
    }

    /**
     * Check if snippet has parameters
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    /**
     * Check if snippet has variables
     */
    public boolean hasVariables() {
        return !variables.isEmpty();
    }

    /**
     * Check if snippet has namespaces
     */
    public boolean hasNamespaces() {
        return !namespaces.isEmpty();
    }

    /**
     * Get formatted query with parameter placeholders
     */
    public String getFormattedQuery() {
        String formattedQuery = query;

        for (SnippetParameter param : parameters) {
            String placeholder = "${" + param.getName() + "}";
            formattedQuery = formattedQuery.replace(placeholder, param.getDefaultValue());
        }

        return formattedQuery;
    }

    /**
     * Get query with variable substitutions
     */
    public String getQueryWithVariables(Map<String, String> variableValues) {
        String processedQuery = query;

        // Substitute variables
        for (Map.Entry<String, String> var : variables.entrySet()) {
            String value = variableValues.getOrDefault(var.getKey(), var.getValue());
            processedQuery = processedQuery.replace("$" + var.getKey(), value);
        }

        // Substitute parameters
        for (SnippetParameter param : parameters) {
            String value = variableValues.getOrDefault(param.getName(), param.getDefaultValue());
            processedQuery = processedQuery.replace("${" + param.getName() + "}", value);
        }

        return processedQuery;
    }

    /**
     * Validate snippet configuration
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            errors.add("Snippet name is required");
        }

        if (query == null || query.trim().isEmpty()) {
            errors.add("Query is required");
        }

        if (type == null) {
            errors.add("Snippet type is required");
        }

        // Validate parameters
        for (SnippetParameter param : parameters) {
            if (param.getName() == null || param.getName().trim().isEmpty()) {
                errors.add("Parameter name is required");
            }
        }

        // Check for undefined variables in query
        if (query != null) {
            // Simple validation - could be enhanced with proper XPath parsing
            for (String var : extractVariableNames(query)) {
                if (!variables.containsKey(var)) {
                    errors.add("Undefined variable: $" + var);
                }
            }
        }

        return errors;
    }

    /**
     * Extract variable names from query (simplified)
     */
    private Set<String> extractVariableNames(String query) {
        Set<String> variables = new HashSet<>();
        String[] parts = query.split("\\$");

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int end = 0;
            while (end < part.length() &&
                    (Character.isLetterOrDigit(part.charAt(end)) || part.charAt(end) == '_')) {
                end++;
            }
            if (end > 0) {
                variables.add(part.substring(0, end));
            }
        }

        return variables;
    }

    // ========== Utility Methods ==========

    /**
     * Clone this snippet with a new ID
     */
    public XPathSnippet clone() {
        XPathSnippet clone = new XPathSnippet();
        clone.name = this.name + " (Copy)";
        clone.description = this.description;
        clone.type = this.type;
        clone.category = this.category;
        clone.query = this.query;
        clone.expectedResult = this.expectedResult;
        clone.author = this.author;
        clone.version = this.version;
        clone.tags = new HashSet<>(this.tags);
        clone.variables = new HashMap<>(this.variables);
        clone.namespaces = new HashMap<>(this.namespaces);
        clone.contextPath = this.contextPath;
        clone.requiresContext = this.requiresContext;
        clone.documentationUrl = this.documentationUrl;
        clone.exampleXml = this.exampleXml;
        clone.parameters = new ArrayList<>(this.parameters);
        clone.validationRule = this.validationRule;
        return clone;
    }

    /**
     * Get snippet summary for display
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(name);
        if (type != null) {
            summary.append(" (").append(type.getShortName()).append(")");
        }
        if (executionCount > 0) {
            summary.append(" - ").append(executionCount).append(" executions");
        }
        return summary.toString();
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
        this.lastModified = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.lastModified = LocalDateTime.now();
    }

    public SnippetType getType() {
        return type;
    }

    public void setType(SnippetType type) {
        this.type = type;
        this.lastModified = LocalDateTime.now();
    }

    public SnippetCategory getCategory() {
        return category;
    }

    public void setCategory(SnippetCategory category) {
        this.category = category;
        this.lastModified = LocalDateTime.now();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
        this.lastModified = LocalDateTime.now();
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public LocalDateTime getLastExecuted() {
        return lastExecuted;
    }

    public void setLastExecuted(LocalDateTime lastExecuted) {
        this.lastExecuted = lastExecuted;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public boolean isRequiresContext() {
        return requiresContext;
    }

    public void setRequiresContext(boolean requiresContext) {
        this.requiresContext = requiresContext;
    }

    public long getExecutionCount() {
        return executionCount;
    }

    public int getUsageCount() {
        return (int) executionCount;
    }

    public void setExecutionCount(long executionCount) {
        this.executionCount = executionCount;
    }

    public long getTotalExecutionTime() {
        return totalExecutionTime;
    }

    public void setTotalExecutionTime(long totalExecutionTime) {
        this.totalExecutionTime = totalExecutionTime;
    }

    public long getAverageExecutionTime() {
        return averageExecutionTime;
    }

    public void setAverageExecutionTime(long averageExecutionTime) {
        this.averageExecutionTime = averageExecutionTime;
    }

    public long getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(long lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        this.isFavorite = favorite;
        this.lastModified = LocalDateTime.now();
    }

    public String getDocumentationUrl() {
        return documentationUrl;
    }

    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }

    public String getExampleXml() {
        return exampleXml;
    }

    public void setExampleXml(String exampleXml) {
        this.exampleXml = exampleXml;
    }

    public List<SnippetParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<SnippetParameter> parameters) {
        this.parameters = parameters;
    }

    public SnippetValidationRule getValidationRule() {
        return validationRule;
    }

    public void setValidationRule(SnippetValidationRule validationRule) {
        this.validationRule = validationRule;
    }

    // ========== Object Methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XPathSnippet that = (XPathSnippet) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("XPathSnippet{id='%s', name='%s', type=%s, category=%s}",
                id, name, type, category);
    }
}