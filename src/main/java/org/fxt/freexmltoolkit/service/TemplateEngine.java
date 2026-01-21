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
 *
 * <p>This engine provides comprehensive template management capabilities including:
 * <ul>
 *   <li>Template processing with parameter substitution</li>
 *   <li>Context-aware parameter filling</li>
 *   <li>Batch template processing</li>
 *   <li>Intelligent template suggestions based on XML context</li>
 *   <li>Template validation and preview</li>
 *   <li>Performance monitoring and statistics</li>
 *   <li>Processing cache management</li>
 * </ul>
 *
 * <p>The engine follows the singleton pattern and can be obtained via {@link #getInstance()}.
 *
 * @see XmlTemplate
 * @see TemplateRepository
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

    /**
     * Private constructor for singleton pattern.
     * Initializes the template repository, suggestion engine, and context analyzer.
     */
    private TemplateEngine() {
        this.templateRepository = TemplateRepository.getInstance();
        this.suggestionEngine = new TemplateSuggestionEngine();
        this.contextAnalyzer = new TemplateContextAnalyzer();

        logger.info("Template Engine initialized");
    }

    /**
     * Returns the singleton instance of the TemplateEngine.
     * Creates a new instance if one does not already exist.
     *
     * @return the singleton TemplateEngine instance
     */
    public static synchronized TemplateEngine getInstance() {
        if (instance == null) {
            instance = new TemplateEngine();
        }
        return instance;
    }

    // ========== Template Processing ==========

    /**
     * Processes a template with the given parameters.
     * Validates parameters, processes the template content, records usage statistics,
     * and returns the processing result.
     *
     * @param templateId the unique identifier of the template to process
     * @param parameters a map of parameter names to their values for substitution
     * @return the processing result containing the processed content or error information
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
     * Processes a template with context-aware parameter filling.
     * Enhances the provided parameters with values derived from the given context
     * before processing the template.
     *
     * @param templateId the unique identifier of the template to process
     * @param parameters a map of parameter names to their values for substitution
     * @param context the template context containing information for parameter derivation
     * @return the processing result containing the processed content or error information
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
     * Processes multiple templates in batch.
     * Each batch item contains a template ID and its corresponding parameters.
     *
     * @param batchItems the list of batch items containing template IDs and parameters
     * @return a list of processing results corresponding to each batch item
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
     * Returns template suggestions based on the given context.
     * Uses the internal suggestion engine to analyze the context and recommend
     * appropriate templates.
     *
     * @param context the template context for generating suggestions
     * @return a list of template suggestions sorted by relevance
     */
    public List<TemplateSuggestion> getTemplateSuggestions(TemplateContext context) {
        return suggestionEngine.getSuggestions(context);
    }

    /**
     * Returns contextual template suggestions based on the current cursor position in XML content.
     * Analyzes the XML structure at the cursor position and suggests templates
     * that are appropriate for that context.
     *
     * @param xmlContent the XML content being edited
     * @param cursorPosition the current cursor position within the XML content
     * @param availableNamespaces the set of namespaces available in the document
     * @return a list of template suggestions sorted by relevance score
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
     * Returns smart parameter suggestions for a specific template based on context.
     * Analyzes the template parameters and the current context to suggest
     * appropriate values for each parameter.
     *
     * @param templateId the unique identifier of the template
     * @param context the template context for generating parameter suggestions
     * @return a map of parameter names to lists of suggested values
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
     * Validates a template with the given parameters before processing.
     * Checks for missing required parameters, validates parameter values,
     * and identifies unused parameters.
     *
     * @param templateId the unique identifier of the template to validate
     * @param parameters a map of parameter names to their values
     * @return the validation result containing validity status, errors, and warnings
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
     * Returns a preview of the template with the given parameters without full processing.
     * Useful for displaying a quick preview to users before committing to full processing.
     *
     * @param templateId the unique identifier of the template
     * @param parameters a map of parameter names to their values
     * @return the template preview string, or an error message if the template is not found
     */
    public String getTemplatePreview(String templateId, Map<String, String> parameters) {
        XmlTemplate template = templateRepository.getTemplate(templateId);
        if (template == null) {
            return "Template not found";
        }

        return template.getPreview(parameters);
    }

    // ========== Performance Monitoring ==========

    /**
     * Updates performance statistics for a template processing operation.
     *
     * @param templateId the unique identifier of the template
     * @param processingTime the time taken to process the template in milliseconds
     * @param success whether the processing was successful
     */
    private void updatePerformanceStats(String templateId, long processingTime, boolean success) {
        ProcessingStats stats = performanceStats.computeIfAbsent(templateId, k -> new ProcessingStats());
        stats.recordProcessing(processingTime, success);
    }

    /**
     * Returns the performance statistics for all processed templates.
     *
     * @return a map of template IDs to their processing statistics
     */
    public Map<String, ProcessingStats> getPerformanceStats() {
        return new HashMap<>(performanceStats);
    }

    /**
     * Returns the slowest templates based on average processing time.
     *
     * @param limit the maximum number of templates to return
     * @return a list of template performance information sorted by processing time descending
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
     * Clears the template processing cache.
     * This removes all cached processing results, forcing fresh processing
     * on subsequent requests.
     */
    public void clearCache() {
        processingCache.clear();
        logger.debug("Template processing cache cleared");
    }

    /**
     * Returns cache statistics including size and hit rate.
     *
     * @return a map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", processingCache.size());
        stats.put("cacheHitRate", calculateCacheHitRate());
        return stats;
    }

    /**
     * Calculates the cache hit rate.
     *
     * @return the cache hit rate as a decimal between 0.0 and 1.0
     */
    private double calculateCacheHitRate() {
        // Simplified cache hit rate calculation
        return processingCache.size() > 0 ? 0.75 : 0.0; // Placeholder
    }

    // ========== Inner Classes ==========

    /**
     * Represents the result of a template processing operation.
     * Contains the processed content, any errors or warnings, and processing metadata.
     */
    public static class TemplateProcessingResult {
        private final boolean success;
        private final String content;
        private final List<String> errors;
        private final List<String> warnings;
        private final XmlTemplate template;
        private final Map<String, String> parameters;
        private long processingTimeMs;

        /**
         * Constructs a new TemplateProcessingResult.
         *
         * @param success whether the processing was successful
         * @param content the processed content, or null if processing failed
         * @param errors the list of error messages, or null if none
         * @param warnings the list of warning messages, or null if none
         * @param template the template that was processed, or null if not found
         * @param parameters the parameters used for processing, or null if none
         */
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

        /**
         * Creates a successful processing result.
         *
         * @param content the processed content
         * @param template the template that was processed
         * @param parameters the parameters used for processing
         * @return a successful TemplateProcessingResult
         */
        public static TemplateProcessingResult success(String content, XmlTemplate template,
                                                       Map<String, String> parameters) {
            return new TemplateProcessingResult(true, content, null, null, template, parameters);
        }

        /**
         * Creates an error processing result with a single error message.
         *
         * @param error the error message
         * @return an error TemplateProcessingResult
         */
        public static TemplateProcessingResult error(String error) {
            return new TemplateProcessingResult(false, null, Collections.singletonList(error), null, null, null);
        }

        /**
         * Creates a validation error processing result with multiple error messages.
         *
         * @param errors the list of validation error messages
         * @return a validation error TemplateProcessingResult
         */
        public static TemplateProcessingResult validationError(List<String> errors) {
            return new TemplateProcessingResult(false, null, errors, null, null, null);
        }

        /**
         * Returns whether the processing was successful.
         *
         * @return true if processing succeeded, false otherwise
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * Returns the processed content.
         *
         * @return the processed content, or null if processing failed
         */
        public String getContent() {
            return content;
        }

        /**
         * Returns the list of error messages.
         *
         * @return the list of error messages, empty if none
         */
        public List<String> getErrors() {
            return errors;
        }

        /**
         * Returns the list of warning messages.
         *
         * @return the list of warning messages, empty if none
         */
        public List<String> getWarnings() {
            return warnings;
        }

        /**
         * Returns the template that was processed.
         *
         * @return the template, or null if not found
         */
        public XmlTemplate getTemplate() {
            return template;
        }

        /**
         * Returns the parameters used for processing.
         *
         * @return the parameters map, empty if none
         */
        public Map<String, String> getParameters() {
            return parameters;
        }

        /**
         * Returns the processing time in milliseconds.
         *
         * @return the processing time in milliseconds
         */
        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        /**
         * Sets the processing time in milliseconds.
         *
         * @param processingTimeMs the processing time to set
         */
        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }
    }

    /**
     * Represents the result of template validation.
     * Contains validation status, error messages, and warning messages.
     *
     * @param valid whether the template is valid with the given parameters
     * @param errors the list of validation error messages
     * @param warnings the list of warning messages
     */
    public record TemplateValidationResult(boolean valid, List<String> errors, List<String> warnings) {

        /**
         * Constructs a TemplateValidationResult with only validity status and errors.
         *
         * @param valid whether the template is valid
         * @param errors the list of error messages
         */
        public TemplateValidationResult(boolean valid, List<String> errors) {
            this(valid, errors, new ArrayList<>());
        }

        /**
         * Constructs a TemplateValidationResult with all fields.
         * Null lists are converted to empty lists.
         *
         * @param valid whether the template is valid
         * @param errors the list of error messages, or null
         * @param warnings the list of warning messages, or null
         */
        public TemplateValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
    }

    /**
     * Represents a template suggestion with relevance information.
     * Contains the suggested template, the context it was suggested for,
     * and a relevance score indicating how appropriate the suggestion is.
     */
    public static class TemplateSuggestion {
        private final XmlTemplate template;
        private final TemplateContext context;
        private final double relevanceScore;
        private final String description;

        /**
         * Constructs a new TemplateSuggestion.
         * Calculates the relevance score based on the template and context.
         *
         * @param template the suggested template
         * @param context the context for which the template is suggested
         */
        public TemplateSuggestion(XmlTemplate template, TemplateContext context) {
            this.template = template;
            this.context = context;
            this.relevanceScore = template.calculateRelevanceScore(
                    context.getTemplateContext(), context.getCurrentElement(), context.getAvailableNamespaces());
            this.description = generateDescription();
        }

        /**
         * Generates a human-readable description of the suggestion.
         *
         * @return the generated description
         */
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

        /**
         * Returns the suggested template.
         *
         * @return the template
         */
        public XmlTemplate getTemplate() {
            return template;
        }

        /**
         * Returns the context for which this suggestion was generated.
         *
         * @return the template context
         */
        public TemplateContext getContext() {
            return context;
        }

        /**
         * Returns the relevance score of this suggestion.
         * Higher scores indicate more relevant suggestions.
         *
         * @return the relevance score
         */
        public double getRelevanceScore() {
            return relevanceScore;
        }

        /**
         * Returns the human-readable description of this suggestion.
         *
         * @return the description
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * Represents a batch processing item containing a template ID and its parameters.
     *
     * @param templateId the unique identifier of the template to process
     * @param parameters the map of parameter names to their values for substitution
     */
    public record TemplateBatchItem(String templateId, Map<String, String> parameters) {
    }

    /**
     * Represents template context information used for intelligent template suggestion
     * and context-aware parameter filling.
     */
    public static class TemplateContext {
        private XmlTemplate.TemplateContext templateContext;
        private String currentElement;
        private Set<String> availableNamespaces = new HashSet<>();
        private String xmlContent;
        private int cursorPosition;
        private Map<String, String> contextVariables = new HashMap<>();

        /**
         * Returns the template context type.
         *
         * @return the template context type
         */
        public XmlTemplate.TemplateContext getTemplateContext() {
            return templateContext;
        }

        /**
         * Sets the template context type.
         *
         * @param templateContext the template context type to set
         */
        public void setTemplateContext(XmlTemplate.TemplateContext templateContext) {
            this.templateContext = templateContext;
        }

        /**
         * Returns the name of the current element at the cursor position.
         *
         * @return the current element name, or null if not within an element
         */
        public String getCurrentElement() {
            return currentElement;
        }

        /**
         * Sets the name of the current element.
         *
         * @param currentElement the current element name to set
         */
        public void setCurrentElement(String currentElement) {
            this.currentElement = currentElement;
        }

        /**
         * Returns the set of available namespaces in the document.
         *
         * @return the set of namespace URIs
         */
        public Set<String> getAvailableNamespaces() {
            return availableNamespaces;
        }

        /**
         * Sets the available namespaces.
         *
         * @param availableNamespaces the set of namespace URIs to set
         */
        public void setAvailableNamespaces(Set<String> availableNamespaces) {
            this.availableNamespaces = availableNamespaces;
        }

        /**
         * Returns the XML content being edited.
         *
         * @return the XML content
         */
        public String getXmlContent() {
            return xmlContent;
        }

        /**
         * Sets the XML content.
         *
         * @param xmlContent the XML content to set
         */
        public void setXmlContent(String xmlContent) {
            this.xmlContent = xmlContent;
        }

        /**
         * Returns the current cursor position within the XML content.
         *
         * @return the cursor position
         */
        public int getCursorPosition() {
            return cursorPosition;
        }

        /**
         * Sets the cursor position.
         *
         * @param cursorPosition the cursor position to set
         */
        public void setCursorPosition(int cursorPosition) {
            this.cursorPosition = cursorPosition;
        }

        /**
         * Returns the context variables map.
         *
         * @return the map of context variable names to their values
         */
        public Map<String, String> getContextVariables() {
            return contextVariables;
        }

        /**
         * Sets the context variables.
         *
         * @param contextVariables the map of context variable names to their values
         */
        public void setContextVariables(Map<String, String> contextVariables) {
            this.contextVariables = contextVariables;
        }
    }

    /**
     * Represents processing statistics for a template.
     * Tracks processing counts, times, and success rates.
     */
    public static class ProcessingStats {
        private int totalProcessings = 0;
        private int successfulProcessings = 0;
        private long totalProcessingTime = 0;
        private long minProcessingTime = Long.MAX_VALUE;
        private long maxProcessingTime = 0;
        private LocalDateTime firstProcessing;
        private LocalDateTime lastProcessing;

        /**
         * Records a processing operation with its time and success status.
         *
         * @param processingTime the time taken for processing in milliseconds
         * @param success whether the processing was successful
         */
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

        /**
         * Calculates the average processing time.
         *
         * @return the average processing time in milliseconds, or 0.0 if no processings recorded
         */
        public double getAverageProcessingTime() {
            return totalProcessings > 0 ? (double) totalProcessingTime / totalProcessings : 0.0;
        }

        /**
         * Calculates the success rate of processings.
         *
         * @return the success rate as a decimal between 0.0 and 1.0, or 0.0 if no processings recorded
         */
        public double getSuccessRate() {
            return totalProcessings > 0 ? (double) successfulProcessings / totalProcessings : 0.0;
        }

        /**
         * Returns the total number of processing operations.
         *
         * @return the total processing count
         */
        public int getTotalProcessings() {
            return totalProcessings;
        }

        /**
         * Returns the number of successful processing operations.
         *
         * @return the successful processing count
         */
        public int getSuccessfulProcessings() {
            return successfulProcessings;
        }

        /**
         * Returns the total processing time for all operations.
         *
         * @return the total processing time in milliseconds
         */
        public long getTotalProcessingTime() {
            return totalProcessingTime;
        }

        /**
         * Returns the minimum processing time recorded.
         *
         * @return the minimum processing time in milliseconds, or 0 if no processings recorded
         */
        public long getMinProcessingTime() {
            return minProcessingTime == Long.MAX_VALUE ? 0 : minProcessingTime;
        }

        /**
         * Returns the maximum processing time recorded.
         *
         * @return the maximum processing time in milliseconds
         */
        public long getMaxProcessingTime() {
            return maxProcessingTime;
        }

        /**
         * Returns the timestamp of the first processing operation.
         *
         * @return the first processing timestamp, or null if no processings recorded
         */
        public LocalDateTime getFirstProcessing() {
            return firstProcessing;
        }

        /**
         * Returns the timestamp of the last processing operation.
         *
         * @return the last processing timestamp, or null if no processings recorded
         */
        public LocalDateTime getLastProcessing() {
            return lastProcessing;
        }
    }

    /**
     * Represents performance information for a template.
     * Combines the template ID with its processing statistics.
     *
     * @param templateId the unique identifier of the template
     * @param stats the processing statistics for the template
     */
    public record TemplatePerformanceInfo(String templateId, ProcessingStats stats) {

        /**
         * Returns the average processing time for this template.
         *
         * @return the average processing time in milliseconds
         */
        public double getAverageProcessingTime() {
            return stats.getAverageProcessingTime();
        }

        /**
         * Returns the success rate for this template.
         *
         * @return the success rate as a decimal between 0.0 and 1.0
         */
        public double getSuccessRate() {
            return stats.getSuccessRate();
        }

        /**
         * Returns the total number of processings for this template.
         *
         * @return the total processing count
         */
        public int getTotalProcessings() {
            return stats.getTotalProcessings();
        }
    }

    // ========== Helper Classes ==========

    /**
     * Internal engine for generating template suggestions based on context.
     * Analyzes the current editing context and recommends appropriate templates.
     */
    private static class TemplateSuggestionEngine {

        /**
         * Returns template suggestions based on the given context.
         *
         * @param context the template context for generating suggestions
         * @return a list of template suggestions
         */
        public List<TemplateSuggestion> getSuggestions(TemplateContext context) {
            // Implement intelligent template suggestions based on context
            return new ArrayList<>();
        }

        /**
         * Returns parameter value suggestions for a specific parameter based on context.
         *
         * @param parameter the template parameter to suggest values for
         * @param context the template context for generating suggestions
         * @return a list of suggested parameter values
         */
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

        /**
         * Returns element name suggestions based on context.
         *
         * @param context the template context
         * @return a list of suggested element names
         */
        private List<String> getElementNameSuggestions(TemplateContext context) {
            // Extract element names from context
            if (context.getCurrentElement() != null) {
                return Arrays.asList(context.getCurrentElement(), "item", "element", "data");
            }
            return Arrays.asList("item", "element", "data", "value");
        }
    }

    /**
     * Internal analyzer for understanding template context from XML content.
     * Analyzes cursor position, extracts element information, and derives parameter values.
     */
    private static class TemplateContextAnalyzer {

        /**
         * Analyzes the XML content at the given cursor position to determine context.
         *
         * @param xmlContent the XML content being edited
         * @param cursorPosition the current cursor position
         * @return the analyzed template context
         */
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

        /**
         * Extracts the current element name from XML content before the cursor.
         *
         * @param xmlContent the XML content before the cursor position
         * @return the current element name, or null if not found
         */
        private String extractCurrentElement(String xmlContent) {
            // Simple extraction of current element name
            Pattern pattern = Pattern.compile("<(\\w+)[^>]*>?$");
            Matcher matcher = pattern.matcher(xmlContent);

            if (matcher.find()) {
                return matcher.group(1);
            }

            return null;
        }

        /**
         * Extracts namespace URIs from XML content.
         *
         * @param xmlContent the full XML content
         * @return the set of namespace URIs found in the content
         */
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

        /**
         * Derives parameter values from the template and context.
         * Auto-fills timestamp and element name parameters based on context information.
         *
         * @param template the template whose parameters should be filled
         * @param context the template context for deriving values
         * @return a map of parameter names to their derived values
         */
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
