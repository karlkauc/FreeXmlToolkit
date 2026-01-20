package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced template processing engine with parameter substitution,
 * context awareness, and intelligent template suggestion.
 */
public class TemplateEngine {

    private static final Logger logger = LogManager.getLogger(TemplateEngine.class);

    // Singleton instance
    private static TemplateEngine instance;

    // Template repository
    private final TemplateRepository templateRepository;

    // Processing cache
    private final Map<String, TemplateProcessingResult> processingCache = new ConcurrentHashMap<>();

    // Template suggestion engine
    private final TemplateSuggestionEngine suggestionEngine;

    // Context analyzer
    private final TemplateContextAnalyzer contextAnalyzer;

    // Performance monitoring
    private final Map<String, ProcessingStats> performanceStats = new ConcurrentHashMap<>();

    private TemplateEngine() {
        this.templateRepository = TemplateRepository.getInstance();
        this.suggestionEngine = new TemplateSuggestionEngine();
        this.contextAnalyzer = new TemplateContextAnalyzer();

        logger.info("Template Engine initialized");
    }

    public static synchronized TemplateEngine getInstance() {
        if (instance == null) {
            instance = new TemplateEngine();
        }
        return instance;
    }

    // ========== Template Processing ==========

    /**
     * Process template with parameters
     */
    public TemplateProcessingResult processTemplate(String templateId, Map<String, String> parameters) {
        long startTime = System.currentTimeMillis();

        try {
            XmlTemplate template = templateRepository.getTemplate(templateId);
            if (template == null) {
                return TemplateProcessingResult.error("Template not found: " + templateId);
            }

            // Validate parameters
            List<String> validationErrors = template.validateParameters(parameters);
            if (!validationErrors.isEmpty()) {
                return TemplateProcessingResult.validationError(validationErrors);
            }

            // Process template
            String processedContent = template.processTemplate(parameters);

            // Record usage
            templateRepository.recordUsage(templateId);

            // Update performance stats
            long processingTime = System.currentTimeMillis() - startTime;
            updatePerformanceStats(templateId, processingTime, true);

            TemplateProcessingResult result = TemplateProcessingResult.success(
                    processedContent, template, parameters);
            result.setProcessingTimeMs(processingTime);

            return result;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            updatePerformanceStats(templateId, processingTime, false);

            logger.error("Template processing failed for template: " + templateId, e);
            return TemplateProcessingResult.error("Template processing failed: " + e.getMessage());
        }
    }

    /**
     * Process template with context-aware parameter filling
     */
    public TemplateProcessingResult processTemplateWithContext(String templateId,
                                                               Map<String, String> parameters,
                                                               TemplateContext context) {
        // Enhance parameters with context-derived values
        Map<String, String> enhancedParameters = new HashMap<>(parameters);

        XmlTemplate template = templateRepository.getTemplate(templateId);
        if (template != null) {
            // Auto-fill parameters based on context
            Map<String, String> contextParameters = contextAnalyzer.deriveParameters(template, context);

            // Add context parameters if not already provided
            for (Map.Entry<String, String> entry : contextParameters.entrySet()) {
                enhancedParameters.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        return processTemplate(templateId, enhancedParameters);
    }

    /**
     * Batch process multiple templates
     */
    public List<TemplateProcessingResult> processTemplates(List<TemplateBatchItem> batchItems) {
        List<TemplateProcessingResult> results = new ArrayList<>();

        for (TemplateBatchItem item : batchItems) {
            TemplateProcessingResult result = processTemplate(item.templateId(), item.parameters());
            results.add(result);
        }

        return results;
    }

    // ========== Template Suggestions ==========

    /**
     * Get template suggestions based on context
     */
    public List<TemplateSuggestion> getTemplateSuggestions(TemplateContext context) {
        return suggestionEngine.getSuggestions(context);
    }

    /**
     * Get template suggestions for current cursor position
     */
    public List<TemplateSuggestion> getContextualSuggestions(String xmlContent,
                                                             int cursorPosition,
                                                             Set<String> availableNamespaces) {
        TemplateContext context = contextAnalyzer.analyzeContext(xmlContent, cursorPosition);

        List<XmlTemplate> contextualTemplates = templateRepository.getContextualTemplates(
                context.getTemplateContext(), context.getCurrentElement(), availableNamespaces);

        return contextualTemplates.stream()
                .map(template -> new TemplateSuggestion(template, context))
                .sorted((s1, s2) -> Double.compare(s2.getRelevanceScore(), s1.getRelevanceScore()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Get smart parameter suggestions
     */
    public Map<String, List<String>> getParameterSuggestions(String templateId, TemplateContext context) {
        XmlTemplate template = templateRepository.getTemplate(templateId);
        if (template == null) {
            return new HashMap<>();
        }

        Map<String, List<String>> suggestions = new HashMap<>();

        for (TemplateParameter param : template.getParameters()) {
            List<String> paramSuggestions = suggestionEngine.getParameterSuggestions(param, context);
            if (!paramSuggestions.isEmpty()) {
                suggestions.put(param.getName(), paramSuggestions);
            }
        }

        return suggestions;
    }

    // ========== Template Validation ==========

    /**
     * Validate template before processing
     */
    public TemplateValidationResult validateTemplate(String templateId, Map<String, String> parameters) {
        XmlTemplate template = templateRepository.getTemplate(templateId);
        if (template == null) {
            return new TemplateValidationResult(false, List.of("Template not found: " + templateId));
        }

        List<String> errors = template.validateParameters(parameters);
        List<String> warnings = new ArrayList<>();

        // Check for missing optional parameters with defaults
        for (TemplateParameter param : template.getParameters()) {
            if (!param.isRequired() && !parameters.containsKey(param.getName()) &&
                    param.getDefaultValue() != null) {
                warnings.add("Optional parameter '" + param.getDisplayName() +
                        "' will use default value: " + param.getDefaultValue());
            }
        }

        // Check for unused parameters
        Set<String> templateParamNames = template.getParameters().stream()
                .collect(HashSet::new, (set, param) -> set.add(param.getName()), HashSet::addAll);

        for (String providedParam : parameters.keySet()) {
            if (!templateParamNames.contains(providedParam)) {
                warnings.add("Parameter '" + providedParam + "' is not used by this template");
            }
        }

        boolean isValid = errors.isEmpty();
        return new TemplateValidationResult(isValid, errors, warnings);
    }

    /**
     * Get template preview without full processing
     */
    public String getTemplatePreview(String templateId, Map<String, String> parameters) {
        XmlTemplate template = templateRepository.getTemplate(templateId);
        if (template == null) {
            return "Template not found";
        }

        return template.getPreview(parameters);
    }

    // ========== Performance Monitoring ==========

    private void updatePerformanceStats(String templateId, long processingTime, boolean success) {
        ProcessingStats stats = performanceStats.computeIfAbsent(templateId, k -> new ProcessingStats());
        stats.recordProcessing(processingTime, success);
    }

    /**
     * Get performance statistics
     */
    public Map<String, ProcessingStats> getPerformanceStats() {
        return new HashMap<>(performanceStats);
    }

    /**
     * Get slowest templates
     */
    public List<TemplatePerformanceInfo> getSlowestTemplates(int limit) {
        return performanceStats.entrySet().stream()
                .map(entry -> new TemplatePerformanceInfo(entry.getKey(), entry.getValue()))
                .sorted((p1, p2) -> Double.compare(p2.getAverageProcessingTime(), p1.getAverageProcessingTime()))
                .limit(limit)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    // ========== Cache Management ==========

    /**
     * Clear processing cache
     */
    public void clearCache() {
        processingCache.clear();
        logger.debug("Template processing cache cleared");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", processingCache.size());
        stats.put("cacheHitRate", calculateCacheHitRate());
        return stats;
    }

    private double calculateCacheHitRate() {
        // Simplified cache hit rate calculation
        return processingCache.size() > 0 ? 0.75 : 0.0; // Placeholder
    }

    // ========== Inner Classes ==========

    /**
     * Template processing result
     */
    public static class TemplateProcessingResult {
        private final boolean success;
        private final String content;
        private final List<String> errors;
        private final List<String> warnings;
        private final XmlTemplate template;
        private final Map<String, String> parameters;
        private long processingTimeMs;

        private TemplateProcessingResult(boolean success, String content,
                                         List<String> errors, List<String> warnings,
                                         XmlTemplate template, Map<String, String> parameters) {
            this.success = success;
            this.content = content;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.template = template;
            this.parameters = parameters != null ? parameters : new HashMap<>();
        }

        public static TemplateProcessingResult success(String content, XmlTemplate template,
                                                       Map<String, String> parameters) {
            return new TemplateProcessingResult(true, content, null, null, template, parameters);
        }

        public static TemplateProcessingResult error(String error) {
            return new TemplateProcessingResult(false, null, Collections.singletonList(error), null, null, null);
        }

        public static TemplateProcessingResult validationError(List<String> errors) {
            return new TemplateProcessingResult(false, null, errors, null, null, null);
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public XmlTemplate getTemplate() {
            return template;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }
    }

    /**
     * Template validation result
     * @param valid Whether the template is valid
     * @param errors List of error messages
     * @param warnings List of warning messages
     */
    public record TemplateValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            public TemplateValidationResult(boolean valid, List<String> errors) {
                this(valid, errors, new ArrayList<>());
            }

            public TemplateValidationResult(boolean valid, List<String> errors, List<String> warnings) {
                this.valid = valid;
                this.errors = errors != null ? errors : new ArrayList<>();
                this.warnings = warnings != null ? warnings : new ArrayList<>();
            }
        }

    /**
     * Template suggestion
     */
    public static class TemplateSuggestion {
        private final XmlTemplate template;
        private final TemplateContext context;
        private final double relevanceScore;
        private final String description;

        public TemplateSuggestion(XmlTemplate template, TemplateContext context) {
            this.template = template;
            this.context = context;
            this.relevanceScore = template.calculateRelevanceScore(
                    context.getTemplateContext(), context.getCurrentElement(), context.getAvailableNamespaces());
            this.description = generateDescription();
        }

        private String generateDescription() {
            StringBuilder desc = new StringBuilder();
            desc.append(template.getName());

            if (template.getDescription() != null) {
                desc.append(" - ").append(template.getDescription());
            }

            if (template.getParameters().size() > 0) {
                desc.append(" (").append(template.getParameters().size()).append(" parameters)");
            }

            return desc.toString();
        }

        public XmlTemplate getTemplate() {
            return template;
        }

        public TemplateContext getContext() {
            return context;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Batch processing item
     * @param templateId The template ID
     * @param parameters The parameters for the template
     */
    public record TemplateBatchItem(String templateId, Map<String, String> parameters) {
    }

    /**
     * Template context information
     */
    public static class TemplateContext {
        private XmlTemplate.TemplateContext templateContext;
        private String currentElement;
        private Set<String> availableNamespaces = new HashSet<>();
        private String xmlContent;
        private int cursorPosition;
        private Map<String, String> contextVariables = new HashMap<>();

        public XmlTemplate.TemplateContext getTemplateContext() {
            return templateContext;
        }

        public void setTemplateContext(XmlTemplate.TemplateContext templateContext) {
            this.templateContext = templateContext;
        }

        public String getCurrentElement() {
            return currentElement;
        }

        public void setCurrentElement(String currentElement) {
            this.currentElement = currentElement;
        }

        public Set<String> getAvailableNamespaces() {
            return availableNamespaces;
        }

        public void setAvailableNamespaces(Set<String> availableNamespaces) {
            this.availableNamespaces = availableNamespaces;
        }

        public String getXmlContent() {
            return xmlContent;
        }

        public void setXmlContent(String xmlContent) {
            this.xmlContent = xmlContent;
        }

        public int getCursorPosition() {
            return cursorPosition;
        }

        public void setCursorPosition(int cursorPosition) {
            this.cursorPosition = cursorPosition;
        }

        public Map<String, String> getContextVariables() {
            return contextVariables;
        }

        public void setContextVariables(Map<String, String> contextVariables) {
            this.contextVariables = contextVariables;
        }
    }

    /**
     * Processing statistics
     */
    public static class ProcessingStats {
        private int totalProcessings = 0;
        private int successfulProcessings = 0;
        private long totalProcessingTime = 0;
        private long minProcessingTime = Long.MAX_VALUE;
        private long maxProcessingTime = 0;
        private LocalDateTime firstProcessing;
        private LocalDateTime lastProcessing;

        public void recordProcessing(long processingTime, boolean success) {
            totalProcessings++;
            totalProcessingTime += processingTime;

            if (success) {
                successfulProcessings++;
            }

            minProcessingTime = Math.min(minProcessingTime, processingTime);
            maxProcessingTime = Math.max(maxProcessingTime, processingTime);

            LocalDateTime now = LocalDateTime.now();
            if (firstProcessing == null) {
                firstProcessing = now;
            }
            lastProcessing = now;
        }

        public double getAverageProcessingTime() {
            return totalProcessings > 0 ? (double) totalProcessingTime / totalProcessings : 0.0;
        }

        public double getSuccessRate() {
            return totalProcessings > 0 ? (double) successfulProcessings / totalProcessings : 0.0;
        }

        // Getters
        public int getTotalProcessings() {
            return totalProcessings;
        }

        public int getSuccessfulProcessings() {
            return successfulProcessings;
        }

        public long getTotalProcessingTime() {
            return totalProcessingTime;
        }

        public long getMinProcessingTime() {
            return minProcessingTime == Long.MAX_VALUE ? 0 : minProcessingTime;
        }

        public long getMaxProcessingTime() {
            return maxProcessingTime;
        }

        public LocalDateTime getFirstProcessing() {
            return firstProcessing;
        }

        public LocalDateTime getLastProcessing() {
            return lastProcessing;
        }
    }

    /**
     * Template performance information
     * @param templateId The template ID
     * @param stats The processing statistics
     */
    public record TemplatePerformanceInfo(String templateId, ProcessingStats stats) {

        public double getAverageProcessingTime() {
                return stats.getAverageProcessingTime();
            }

            public double getSuccessRate() {
                return stats.getSuccessRate();
            }

            public int getTotalProcessings() {
                return stats.getTotalProcessings();
            }
        }

    // ========== Helper Classes ==========

    /**
     * Template suggestion engine
     */
    private static class TemplateSuggestionEngine {

        public List<TemplateSuggestion> getSuggestions(TemplateContext context) {
            // Implement intelligent template suggestions based on context
            return new ArrayList<>();
        }

        public List<String> getParameterSuggestions(TemplateParameter parameter, TemplateContext context) {
            List<String> suggestions = new ArrayList<>();

            // Add default value if available
            if (parameter.getDefaultValue() != null) {
                suggestions.add(parameter.getDefaultValue());
            }

            // Add allowed values for enum parameters
            if (parameter.getType() == TemplateParameter.ParameterType.ENUM) {
                suggestions.addAll(parameter.getAllowedValues());
            }

            // Add context-based suggestions
            switch (parameter.getType()) {
                case ELEMENT_NAME:
                    suggestions.addAll(getElementNameSuggestions(context));
                    break;
                case NAMESPACE:
                    suggestions.addAll(context.getAvailableNamespaces());
                    break;
                case DATE:
                    suggestions.add(java.time.LocalDate.now().toString());
                    break;
            }

            return suggestions;
        }

        private List<String> getElementNameSuggestions(TemplateContext context) {
            // Extract element names from context
            if (context.getCurrentElement() != null) {
                return Arrays.asList(context.getCurrentElement(), "item", "element", "data");
            }
            return Arrays.asList("item", "element", "data", "value");
        }
    }

    /**
     * Template context analyzer
     */
    private static class TemplateContextAnalyzer {

        public TemplateContext analyzeContext(String xmlContent, int cursorPosition) {
            TemplateContext context = new TemplateContext();
            context.setXmlContent(xmlContent);
            context.setCursorPosition(cursorPosition);

            // Analyze context at cursor position
            if (xmlContent != null && cursorPosition >= 0 && cursorPosition < xmlContent.length()) {
                // Determine if we're inside an element, attribute, etc.
                String beforeCursor = xmlContent.substring(0, cursorPosition);

                if (beforeCursor.lastIndexOf('<') > beforeCursor.lastIndexOf('>')) {
                    context.setTemplateContext(XmlTemplate.TemplateContext.CHILD_ELEMENT);
                } else {
                    context.setTemplateContext(XmlTemplate.TemplateContext.TEXT_CONTENT);
                }

                // Extract current element name
                String currentElement = extractCurrentElement(beforeCursor);
                context.setCurrentElement(currentElement);

                // Extract available namespaces
                Set<String> namespaces = extractNamespaces(xmlContent);
                context.setAvailableNamespaces(namespaces);
            } else {
                context.setTemplateContext(XmlTemplate.TemplateContext.GENERAL);
            }

            return context;
        }

        private String extractCurrentElement(String xmlContent) {
            // Simple extraction of current element name
            Pattern pattern = Pattern.compile("<(\\w+)[^>]*>?$");
            Matcher matcher = pattern.matcher(xmlContent);

            if (matcher.find()) {
                return matcher.group(1);
            }

            return null;
        }

        private Set<String> extractNamespaces(String xmlContent) {
            Set<String> namespaces = new HashSet<>();

            Pattern pattern = Pattern.compile("xmlns:?(\\w*)\\s*=\\s*[\"']([^\"']+)[\"']");
            Matcher matcher = pattern.matcher(xmlContent);

            while (matcher.find()) {
                String prefix = matcher.group(1);
                String uri = matcher.group(2);

                if (!prefix.isEmpty()) {
                    namespaces.add(uri);
                }
            }

            return namespaces;
        }

        public Map<String, String> deriveParameters(XmlTemplate template, TemplateContext context) {
            Map<String, String> derivedParams = new HashMap<>();

            // Auto-fill timestamp parameters
            for (TemplateParameter param : template.getParameters()) {
                if (param.getName().toLowerCase().contains("timestamp") ||
                        param.getName().toLowerCase().contains("date")) {
                    if (param.getType() == TemplateParameter.ParameterType.DATE) {
                        derivedParams.put(param.getName(), java.time.LocalDate.now().toString());
                    } else {
                        derivedParams.put(param.getName(), java.time.Instant.now().toString());
                    }
                }

                // Auto-fill element names from context
                if (param.getName().toLowerCase().contains("element") &&
                        context.getCurrentElement() != null) {
                    derivedParams.put(param.getName(), context.getCurrentElement());
                }
            }

            return derivedParams;
        }
    }
}