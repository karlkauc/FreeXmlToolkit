package org.fxt.freexmltoolkit.domain;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive XML Template model for the Smart Templates System.
 * Supports parameterized templates, conditional content, and context-awareness.
 */
public class XmlTemplate {

    // Core template information
    private String id = UUID.randomUUID().toString();
    private String name;
    private String description;
    private String content;
    private String category;
    private String subcategory;

    // Template metadata
    private String version = "1.0";
    private String author;
    private LocalDateTime created = LocalDateTime.now();
    private LocalDateTime lastModified = LocalDateTime.now();
    private boolean isBuiltIn = false;
    private boolean isActive = true;

    // Template parameters
    private List<TemplateParameter> parameters = new ArrayList<>();
    private Map<String, String> defaultValues = new HashMap<>();
    private Set<String> requiredParameters = new HashSet<>();

    // Context and targeting
    private Set<TemplateContext> contexts = new HashSet<>();
    private Set<String> xmlNamespaces = new HashSet<>();
    private Set<String> targetElements = new HashSet<>();
    private Set<String> fileExtensions = new HashSet<>();

    // Usage and statistics
    private int usageCount = 0;
    private LocalDateTime lastUsed;
    private double userRating = 0.0;
    private List<String> tags = new ArrayList<>();

    // Template validation and rules
    private List<TemplateValidationRule> validationRules = new ArrayList<>();
    private Map<String, String> conditionalBlocks = new HashMap<>();
    private boolean requiresValidation = false;

    // Output configuration
    private boolean autoIndent = true;
    private String indentStyle = "  "; // 2 spaces default
    private boolean generateComments = false;
    private boolean includeNamespaceDeclarations = true;

    // Industry and domain specific
    private TemplateIndustry industry = TemplateIndustry.GENERAL;
    private TemplateComplexity complexity = TemplateComplexity.SIMPLE;
    private Set<String> relatedStandards = new HashSet<>();

    public XmlTemplate() {
        // Initialize with defaults
    }

    public XmlTemplate(String name, String content, String category) {
        this();
        this.name = name;
        this.content = content;
        this.category = category;
    }

    // ========== Template Processing Methods ==========

    /**
     * Process template with parameter substitution
     */
    public String processTemplate(Map<String, String> parameterValues) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        String processedContent = content;

        // Replace parameters
        for (TemplateParameter param : parameters) {
            String paramName = param.getName();
            String value = parameterValues.getOrDefault(paramName, getDefaultValue(paramName));

            if (value == null && requiredParameters.contains(paramName)) {
                throw new IllegalArgumentException("Required parameter '" + paramName + "' not provided");
            }

            if (value != null) {
                processedContent = processedContent.replace("${" + paramName + "}", value);
                processedContent = processedContent.replace("{{" + paramName + "}}", value);
            }
        }

        // Process conditional blocks
        processedContent = processConditionalBlocks(processedContent, parameterValues);

        // Apply formatting
        if (autoIndent) {
            processedContent = formatXmlContent(processedContent);
        }

        return processedContent;
    }

    private String processConditionalBlocks(String content, Map<String, String> parameterValues) {
        String processed = content;

        for (Map.Entry<String, String> condition : conditionalBlocks.entrySet()) {
            String conditionName = condition.getKey();
            String conditionContent = condition.getValue();

            // Simple condition evaluation: if parameter is present and not empty
            String conditionPattern = "\\{\\{#if\\s+" + conditionName + "\\}\\}([\\s\\S]*?)\\{\\{/if\\}\\}";

            if (parameterValues.containsKey(conditionName) &&
                    !parameterValues.get(conditionName).isEmpty()) {
                processed = processed.replaceAll(conditionPattern, "$1");
            } else {
                processed = processed.replaceAll(conditionPattern, "");
            }
        }

        return processed;
    }

    private String formatXmlContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        try {
            // Basic XML formatting - split by tags and add indentation
            String[] lines = content.split("\n");
            StringBuilder formatted = new StringBuilder();
            int indentLevel = 0;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Decrease indent for closing tags
                if (trimmed.startsWith("</")) {
                    indentLevel = Math.max(0, indentLevel - 1);
                }

                // Add indentation
                formatted.append(indentStyle.repeat(indentLevel)).append(trimmed).append("\n");

                // Increase indent for opening tags (but not self-closing)
                if (trimmed.startsWith("<") && !trimmed.startsWith("</") &&
                        !trimmed.endsWith("/>") && !trimmed.contains("</")) {
                    indentLevel++;
                }
            }

            return formatted.toString();
        } catch (Exception e) {
            // If formatting fails, return original content
            return content;
        }
    }

    // ========== Context Matching Methods ==========

    /**
     * Check if template is applicable in given context
     */
    public boolean isApplicableInContext(TemplateContext context, String currentElement,
                                         Set<String> availableNamespaces) {
        // Check context match
        if (!contexts.isEmpty() && !contexts.contains(context)) {
            return false;
        }

        // Check target element match
        if (!targetElements.isEmpty() && currentElement != null) {
            boolean elementMatch = targetElements.stream()
                    .anyMatch(target -> currentElement.equals(target) ||
                            currentElement.matches(target) ||
                            target.equals("*"));
            if (!elementMatch) {
                return false;
            }
        }

        // Check namespace compatibility
        if (!xmlNamespaces.isEmpty() && availableNamespaces != null) {
            boolean namespaceMatch = xmlNamespaces.stream()
                    .anyMatch(ns -> availableNamespaces.contains(ns) || ns.equals("*"));
            if (!namespaceMatch) {
                return false;
            }
        }

        return isActive;
    }

    /**
     * Calculate relevance score for context-based sorting
     */
    public double calculateRelevanceScore(TemplateContext context, String currentElement,
                                          Set<String> availableNamespaces) {
        if (!isApplicableInContext(context, currentElement, availableNamespaces)) {
            return 0.0;
        }

        double score = 1.0;

        // Boost score for exact context match
        if (contexts.contains(context)) {
            score += 2.0;
        }

        // Boost score for element match
        if (targetElements.contains(currentElement)) {
            score += 1.5;
        }

        // Boost score for namespace match
        if (xmlNamespaces.stream().anyMatch(availableNamespaces::contains)) {
            score += 1.0;
        }

        // Factor in usage statistics
        score += Math.log(usageCount + 1) * 0.1;
        score += userRating * 0.2;

        // Factor in recency
        if (lastUsed != null) {
            long daysSinceUsed = java.time.Duration.between(lastUsed, LocalDateTime.now()).toDays();
            score += Math.max(0, 1.0 - daysSinceUsed * 0.1);
        }

        return score;
    }

    // ========== Validation Methods ==========

    /**
     * Validate template parameters
     */
    public List<String> validateParameters(Map<String, String> parameterValues) {
        List<String> errors = new ArrayList<>();

        // Check required parameters
        for (String required : requiredParameters) {
            if (!parameterValues.containsKey(required) ||
                    parameterValues.get(required) == null ||
                    parameterValues.get(required).trim().isEmpty()) {
                errors.add("Required parameter '" + required + "' is missing or empty");
            }
        }

        // Validate parameter types and constraints
        for (TemplateParameter param : parameters) {
            String value = parameterValues.get(param.getName());
            if (value != null) {
                List<String> paramErrors = param.validateValue(value);
                errors.addAll(paramErrors);
            }
        }

        // Apply custom validation rules
        for (TemplateValidationRule rule : validationRules) {
            if (!rule.validate(parameterValues)) {
                errors.add(rule.getErrorMessage());
            }
        }

        return errors;
    }

    /**
     * Get template preview with current parameters
     */
    public String getPreview(Map<String, String> parameterValues) {
        try {
            String preview = processTemplate(parameterValues);

            // Truncate if too long
            if (preview.length() > 1000) {
                preview = preview.substring(0, 1000) + "\n... (truncated)";
            }

            return preview;
        } catch (Exception e) {
            return "Preview Error: " + e.getMessage();
        }
    }

    // ========== Usage Tracking Methods ==========

    /**
     * Record template usage
     */
    public void recordUsage() {
        usageCount++;
        lastUsed = LocalDateTime.now();
        lastModified = LocalDateTime.now();
    }

    /**
     * Update user rating
     */
    public void updateRating(double rating) {
        if (rating >= 0.0 && rating <= 5.0) {
            this.userRating = rating;
        }
    }

    // ========== Parameter Management ==========

    /**
     * Add parameter to template
     */
    public void addParameter(TemplateParameter parameter) {
        parameters.removeIf(p -> p.getName().equals(parameter.getName()));
        parameters.add(parameter);

        if (parameter.getDefaultValue() != null) {
            defaultValues.put(parameter.getName(), parameter.getDefaultValue());
        }

        if (parameter.isRequired()) {
            requiredParameters.add(parameter.getName());
        }
    }

    /**
     * Remove parameter from template
     */
    public void removeParameter(String parameterName) {
        parameters.removeIf(p -> p.getName().equals(parameterName));
        defaultValues.remove(parameterName);
        requiredParameters.remove(parameterName);
    }

    /**
     * Get parameter by name
     */
    public TemplateParameter getParameter(String name) {
        return parameters.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get default value for parameter
     */
    public String getDefaultValue(String parameterName) {
        return defaultValues.get(parameterName);
    }

    // ========== Utility Methods ==========

    /**
     * Create a copy of this template
     */
    public XmlTemplate copy() {
        XmlTemplate copy = new XmlTemplate();
        copy.id = UUID.randomUUID().toString(); // New ID for copy
        copy.name = this.name + " (Copy)";
        copy.description = this.description;
        copy.content = this.content;
        copy.category = this.category;
        copy.subcategory = this.subcategory;
        copy.version = this.version;
        copy.author = this.author;
        copy.created = LocalDateTime.now();
        copy.lastModified = LocalDateTime.now();
        copy.isBuiltIn = false; // Copies are never built-in
        copy.isActive = this.isActive;

        // Deep copy parameters
        for (TemplateParameter param : this.parameters) {
            copy.addParameter(param.copy());
        }

        // Copy collections
        copy.contexts = new HashSet<>(this.contexts);
        copy.xmlNamespaces = new HashSet<>(this.xmlNamespaces);
        copy.targetElements = new HashSet<>(this.targetElements);
        copy.fileExtensions = new HashSet<>(this.fileExtensions);
        copy.tags = new ArrayList<>(this.tags);
        copy.relatedStandards = new HashSet<>(this.relatedStandards);

        // Copy configuration
        copy.autoIndent = this.autoIndent;
        copy.indentStyle = this.indentStyle;
        copy.generateComments = this.generateComments;
        copy.includeNamespaceDeclarations = this.includeNamespaceDeclarations;
        copy.industry = this.industry;
        copy.complexity = this.complexity;
        copy.requiresValidation = this.requiresValidation;

        return copy;
    }

    /**
     * Get template summary for UI display
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(name);

        if (description != null && !description.isEmpty()) {
            summary.append(" - ").append(description);
        }

        if (!parameters.isEmpty()) {
            summary.append(" (").append(parameters.size()).append(" parameters)");
        }

        if (usageCount > 0) {
            summary.append(" [Used ").append(usageCount).append(" times]");
        }

        return summary.toString();
    }

    // ========== Enums ==========

    public enum TemplateContext {
        ROOT_ELEMENT,        // Template for root elements
        CHILD_ELEMENT,       // Template for child elements
        ATTRIBUTE,           // Template for attributes
        TEXT_CONTENT,        // Template for text content
        COMMENT,             // Template for comments
        PROCESSING_INSTRUCTION, // Template for PIs
        DOCUMENT_TYPE,       // Template for DOCTYPE
        NAMESPACE,           // Template for namespace declarations
        SCHEMA_DEFINITION,   // Template for schema definitions
        TRANSFORMATION,      // Template for XSLT transformations
        VALIDATION,          // Template for validation rules
        GENERAL             // General purpose template
    }

    public enum TemplateIndustry {
        GENERAL,
        FINANCE,
        HEALTHCARE,
        AUTOMOTIVE,
        AEROSPACE,
        TELECOMMUNICATIONS,
        MANUFACTURING,
        RETAIL,
        GOVERNMENT,
        EDUCATION,
        WEB_SERVICES,
        DATA_EXCHANGE,
        CONFIGURATION,
        DOCUMENTATION
    }

    public enum TemplateComplexity {
        SIMPLE,     // Basic templates with few parameters
        MODERATE,   // Templates with moderate complexity
        COMPLEX,    // Advanced templates with many parameters
        EXPERT      // Expert-level templates with advanced features
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.lastModified = LocalDateTime.now();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public void setBuiltIn(boolean builtIn) {
        isBuiltIn = builtIn;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<TemplateParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<TemplateParameter> parameters) {
        this.parameters = parameters;
    }

    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(Map<String, String> defaultValues) {
        this.defaultValues = defaultValues;
    }

    public Set<String> getRequiredParameters() {
        return requiredParameters;
    }

    public void setRequiredParameters(Set<String> requiredParameters) {
        this.requiredParameters = requiredParameters;
    }

    public Set<TemplateContext> getContexts() {
        return contexts;
    }

    public void setContexts(Set<TemplateContext> contexts) {
        this.contexts = contexts;
    }

    public Set<String> getXmlNamespaces() {
        return xmlNamespaces;
    }

    public void setXmlNamespaces(Set<String> xmlNamespaces) {
        this.xmlNamespaces = xmlNamespaces;
    }

    public Set<String> getTargetElements() {
        return targetElements;
    }

    public void setTargetElements(Set<String> targetElements) {
        this.targetElements = targetElements;
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    public void setFileExtensions(Set<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

    public double getUserRating() {
        return userRating;
    }

    public void setUserRating(double userRating) {
        this.userRating = userRating;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<TemplateValidationRule> getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(List<TemplateValidationRule> validationRules) {
        this.validationRules = validationRules;
    }

    public Map<String, String> getConditionalBlocks() {
        return conditionalBlocks;
    }

    public void setConditionalBlocks(Map<String, String> conditionalBlocks) {
        this.conditionalBlocks = conditionalBlocks;
    }

    public boolean isRequiresValidation() {
        return requiresValidation;
    }

    public void setRequiresValidation(boolean requiresValidation) {
        this.requiresValidation = requiresValidation;
    }

    public boolean isAutoIndent() {
        return autoIndent;
    }

    public void setAutoIndent(boolean autoIndent) {
        this.autoIndent = autoIndent;
    }

    public String getIndentStyle() {
        return indentStyle;
    }

    public void setIndentStyle(String indentStyle) {
        this.indentStyle = indentStyle;
    }

    public boolean isGenerateComments() {
        return generateComments;
    }

    public void setGenerateComments(boolean generateComments) {
        this.generateComments = generateComments;
    }

    public boolean isIncludeNamespaceDeclarations() {
        return includeNamespaceDeclarations;
    }

    public void setIncludeNamespaceDeclarations(boolean includeNamespaceDeclarations) {
        this.includeNamespaceDeclarations = includeNamespaceDeclarations;
    }

    public TemplateIndustry getIndustry() {
        return industry;
    }

    public void setIndustry(TemplateIndustry industry) {
        this.industry = industry;
    }

    public TemplateComplexity getComplexity() {
        return complexity;
    }

    public void setComplexity(TemplateComplexity complexity) {
        this.complexity = complexity;
    }

    public Set<String> getRelatedStandards() {
        return relatedStandards;
    }

    public void setRelatedStandards(Set<String> relatedStandards) {
        this.relatedStandards = relatedStandards;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        XmlTemplate that = (XmlTemplate) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("XmlTemplate{id='%s', name='%s', category='%s', parameters=%d}",
                id, name, category, parameters.size());
    }
}