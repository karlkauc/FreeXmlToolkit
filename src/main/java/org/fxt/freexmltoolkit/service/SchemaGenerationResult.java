package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Result object for XML schema generation operations.
 * Contains the generated XSD content, analysis results, and generation metadata.
 */
public class SchemaGenerationResult {

    // Generation status
    private boolean success;
    private String errorMessage;
    private List<String> warnings = new ArrayList<>();
    private List<String> informationalMessages = new ArrayList<>();

    // Generated content
    private String xsdContent;
    private String formattedXsdContent;
    private Map<String, String> additionalSchemas = new HashMap<>(); // For split schemas
    private long generatedContentLength = 0;

    // Analysis results
    private SchemaAnalysisResult analysisResult;
    private Map<String, Object> analysisMetadata = new HashMap<>();

    // Generation metadata
    private String generationId = UUID.randomUUID().toString();
    private LocalDateTime generatedAt = LocalDateTime.now();
    private long generationTimeMs = 0;
    private SchemaGenerationOptions usedOptions;
    private String generatorVersion = "1.0";

    // Quality metrics
    private double schemaQualityScore = 0.0;
    private Map<String, Double> qualityMetrics = new HashMap<>();
    private List<String> optimizationSuggestions = new ArrayList<>();
    private List<String> validationIssues = new ArrayList<>();

    // Statistics
    private Map<String, Integer> generationStatistics = new HashMap<>();
    private int totalElementsGenerated = 0;
    private int totalAttributesGenerated = 0;
    private int totalComplexTypesGenerated = 0;
    private int totalSimpleTypesGenerated = 0;
    private int totalLinesGenerated = 0;

    // Processing information
    private List<String> processingSteps = new ArrayList<>();
    private Map<String, Long> stepTimings = new HashMap<>();
    private List<String> appliedOptimizations = new ArrayList<>();

    private SchemaGenerationResult(boolean success) {
        this.success = success;
        this.generatedAt = LocalDateTime.now();
    }

    // ========== Factory Methods ==========

    /**
     * Creates a successful generation result with the given XSD content and analysis result.
     *
     * @param xsdContent     the generated XSD content as a string
     * @param analysisResult the schema analysis result containing element and type information
     * @return a new SchemaGenerationResult instance indicating success
     */
    public static SchemaGenerationResult success(String xsdContent, SchemaAnalysisResult analysisResult) {
        SchemaGenerationResult result = new SchemaGenerationResult(true);
        result.xsdContent = xsdContent;
        result.analysisResult = analysisResult;
        result.generatedContentLength = xsdContent != null ? xsdContent.length() : 0;

        // Initialize statistics from analysis
        if (analysisResult != null) {
            result.totalElementsGenerated = analysisResult.getTotalElements();
            result.totalAttributesGenerated = analysisResult.getTotalAttributes();
            result.totalComplexTypesGenerated = analysisResult.getComplexTypes().size();
            result.totalSimpleTypesGenerated = analysisResult.getSimpleTypes().size();
        }

        // Count lines
        if (xsdContent != null) {
            result.totalLinesGenerated = xsdContent.split("\n").length;
        }

        return result;
    }

    /**
     * Creates a successful generation result with formatted XSD content.
     *
     * @param xsdContent     the generated XSD content as a string
     * @param formattedXsd   the formatted (pretty-printed) version of the XSD content
     * @param analysisResult the schema analysis result containing element and type information
     * @return a new SchemaGenerationResult instance indicating success with formatted content
     */
    public static SchemaGenerationResult success(String xsdContent, String formattedXsd, SchemaAnalysisResult analysisResult) {
        SchemaGenerationResult result = success(xsdContent, analysisResult);
        result.formattedXsdContent = formattedXsd;
        return result;
    }

    /**
     * Creates an error result with the given error message.
     *
     * @param errorMessage the error message describing what went wrong
     * @return a new SchemaGenerationResult instance indicating failure
     */
    public static SchemaGenerationResult error(String errorMessage) {
        SchemaGenerationResult result = new SchemaGenerationResult(false);
        result.errorMessage = errorMessage;
        return result;
    }

    /**
     * Creates an error result with exception details for debugging purposes.
     *
     * @param errorMessage the error message describing what went wrong
     * @param throwable    the exception that caused the error, may be null
     * @return a new SchemaGenerationResult instance indicating failure with exception details
     */
    public static SchemaGenerationResult error(String errorMessage, Throwable throwable) {
        SchemaGenerationResult result = error(errorMessage);
        if (throwable != null) {
            result.analysisMetadata.put("exceptionType", throwable.getClass().getSimpleName());
            result.analysisMetadata.put("exceptionMessage", throwable.getMessage());

            // Add stack trace for debugging
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element : throwable.getStackTrace()) {
                stackTrace.append(element.toString()).append("\n");
                if (stackTrace.length() > 2000) break; // Limit stack trace length
            }
            result.analysisMetadata.put("stackTrace", stackTrace.toString());
        }
        return result;
    }

    // ========== Processing Tracking Methods ==========

    /**
     * Adds a processing step with timestamp to the processing history.
     *
     * @param step the description of the processing step
     */
    public void addProcessingStep(String step) {
        processingSteps.add(LocalDateTime.now() + ": " + step);
    }

    /**
     * Records the timing for a specific processing step.
     *
     * @param stepName the name of the processing step
     * @param timeMs   the time taken in milliseconds
     */
    public void recordStepTiming(String stepName, long timeMs) {
        stepTimings.put(stepName, timeMs);
    }

    /**
     * Adds an optimization that was applied during schema generation.
     *
     * @param optimization the description of the applied optimization
     */
    public void addAppliedOptimization(String optimization) {
        appliedOptimizations.add(optimization);
    }

    /**
     * Adds a warning message to the generation result.
     *
     * @param warning the warning message to add
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Adds an informational message to the generation result.
     *
     * @param info the informational message to add
     */
    public void addInfo(String info) {
        informationalMessages.add(info);
    }

    /**
     * Adds a validation issue found during schema validation.
     *
     * @param issue the validation issue description
     */
    public void addValidationIssue(String issue) {
        validationIssues.add(issue);
    }

    /**
     * Adds a suggestion for optimizing the generated schema.
     *
     * @param suggestion the optimization suggestion
     */
    public void addOptimizationSuggestion(String suggestion) {
        optimizationSuggestions.add(suggestion);
    }

    // ========== Quality Analysis Methods ==========

    /**
     * Calculates and sets the schema quality score based on various metrics.
     * The score is calculated from analysis confidence, complexity, type reuse,
     * warnings, validation issues, and applied optimizations.
     * The resulting score is normalized to a 0-100 scale.
     */
    public void calculateQualityScore() {
        if (analysisResult == null) {
            schemaQualityScore = success ? 0.5 : 0.0;
            return;
        }

        double score = 0.0;

        // Base score from analysis confidence
        score += analysisResult.getOverallConfidence() * 40.0;

        // Bonus for good structure
        if (analysisResult.getComplexityScore() < 5.0) {
            score += 20.0;
        } else if (analysisResult.getComplexityScore() < 10.0) {
            score += 10.0;
        }

        // Bonus for type reuse
        if (analysisResult.getTypeGroups().size() > 0) {
            score += Math.min(20.0, analysisResult.getTypeGroups().size() * 5.0);
        }

        // Penalty for warnings
        score -= warnings.size() * 2.0;

        // Penalty for validation issues
        score -= validationIssues.size() * 5.0;

        // Bonus for optimizations applied
        score += appliedOptimizations.size() * 3.0;

        // Ensure score is between 0 and 100
        schemaQualityScore = Math.max(0.0, Math.min(100.0, score));

        // Store detailed quality metrics
        storeDetailedQualityMetrics();
    }

    private void storeDetailedQualityMetrics() {
        if (analysisResult != null) {
            qualityMetrics.put("analysisConfidence", analysisResult.getOverallConfidence());
            qualityMetrics.put("complexityScore", analysisResult.getComplexityScore());
            qualityMetrics.put("typeReuseScore", calculateTypeReuseScore());
            qualityMetrics.put("namespaceConsistency", calculateNamespaceConsistency());
            qualityMetrics.put("structuralConsistency", calculateStructuralConsistency());
        }

        qualityMetrics.put("warningCount", (double) warnings.size());
        qualityMetrics.put("validationIssueCount", (double) validationIssues.size());
        qualityMetrics.put("optimizationCount", (double) appliedOptimizations.size());
        qualityMetrics.put("generationEfficiency", calculateGenerationEfficiency());
        qualityMetrics.put("overallQuality", schemaQualityScore);
    }

    private double calculateTypeReuseScore() {
        if (analysisResult == null) return 0.0;

        int reusableTypes = 0;
        for (List<ElementInfo> group : analysisResult.getTypeGroups().values()) {
            if (group.size() > 1) reusableTypes++;
        }

        int totalComplexTypes = analysisResult.getComplexTypes().size();
        return totalComplexTypes > 0 ? (double) reusableTypes / totalComplexTypes * 100.0 : 0.0;
    }

    private double calculateNamespaceConsistency() {
        if (analysisResult == null) return 100.0;

        Set<String> namespaces = analysisResult.getDiscoveredNamespaces();
        if (namespaces.size() <= 1) return 100.0;

        // Check consistency of namespace usage
        Map<String, Integer> usage = analysisResult.getNamespaceUsage();
        double consistency = 0.0;

        for (String namespace : namespaces) {
            int uses = usage.getOrDefault(namespace, 0);
            if (uses > 1) consistency += 1.0;
        }

        return (consistency / namespaces.size()) * 100.0;
    }

    private double calculateStructuralConsistency() {
        if (analysisResult == null) return 100.0;

        // Analyze if similar elements have similar structures
        Map<String, Set<String>> elementStructures = new HashMap<>();

        for (ElementInfo element : analysisResult.getAllElements().values()) {
            String baseName = element.getName();
            String structure = createStructuralSignature(element);
            elementStructures.computeIfAbsent(baseName, k -> new HashSet<>()).add(structure);
        }

        int consistentElements = 0;
        for (Set<String> structures : elementStructures.values()) {
            if (structures.size() == 1) consistentElements++;
        }

        int totalElements = analysisResult.getTotalElements();
        return totalElements > 0 ? (double) consistentElements / totalElements * 100.0 : 100.0;
    }

    private String createStructuralSignature(ElementInfo element) {
        String signature = element.getContentType() + "_" +
                "children:" + element.getChildElements().size() + "_" +
                "attrs:" + element.getAttributes().size();
        return signature;
    }

    private double calculateGenerationEfficiency() {
        if (generationTimeMs == 0) return 100.0;

        // Calculate elements processed per second
        double elementsPerSecond = (double) totalElementsGenerated / (generationTimeMs / 1000.0);

        // Normalize to 0-100 scale (assume 1000 elements/second is optimal)
        return Math.min(100.0, elementsPerSecond / 10.0);
    }

    // ========== Validation Methods ==========

    /**
     * Validates the generated XSD content for well-formedness and required elements.
     *
     * @return true if the generated schema is valid, false otherwise
     */
    public boolean validateGeneratedSchema() {
        if (!success || xsdContent == null || xsdContent.isEmpty()) {
            addValidationIssue("No XSD content to validate");
            return false;
        }

        boolean isValid = true;

        // Basic XML well-formedness check
        if (!isWellFormedXml()) {
            addValidationIssue("Generated XSD is not well-formed XML");
            isValid = false;
        }

        // Check for required XSD elements
        if (!hasRequiredXsdElements()) {
            addValidationIssue("Generated XSD missing required schema elements");
            isValid = false;
        }

        // Check namespace declarations
        if (!hasProperNamespaceDeclarations()) {
            addValidationIssue("Generated XSD has improper namespace declarations");
            isValid = false;
        }

        return isValid;
    }

    private boolean isWellFormedXml() {
        try {
            // Basic check for XML structure
            return xsdContent.contains("<?xml") &&
                    xsdContent.contains("<xs:schema") &&
                    xsdContent.contains("</xs:schema>");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasRequiredXsdElements() {
        return xsdContent.contains("xs:schema") || xsdContent.contains("xsd:schema");
    }

    private boolean hasProperNamespaceDeclarations() {
        return xsdContent.contains("xmlns:xs=") || xsdContent.contains("xmlns:xsd=");
    }

    // ========== Summary Methods ==========

    /**
     * Generates a human-readable summary of the schema generation process.
     *
     * @return a formatted string containing generation statistics and quality assessment
     */
    public String getGenerationSummary() {
        StringBuilder summary = new StringBuilder();

        summary.append("XML Schema Generation Summary\n");
        summary.append("==============================\n\n");

        // Status
        summary.append("Status: ").append(success ? "SUCCESS" : "FAILED").append("\n");
        if (!success && errorMessage != null) {
            summary.append("Error: ").append(errorMessage).append("\n");
        }
        summary.append("\n");

        // Statistics
        summary.append("Generation Statistics:\n");
        summary.append(String.format("  Generation Time: %d ms\n", generationTimeMs));
        summary.append(String.format("  Elements Generated: %d\n", totalElementsGenerated));
        summary.append(String.format("  Attributes Generated: %d\n", totalAttributesGenerated));
        summary.append(String.format("  Complex Types: %d\n", totalComplexTypesGenerated));
        summary.append(String.format("  Simple Types: %d\n", totalSimpleTypesGenerated));
        summary.append(String.format("  Total Lines: %d\n", totalLinesGenerated));
        summary.append(String.format("  Content Length: %d chars\n", generatedContentLength));
        summary.append("\n");

        // Quality metrics
        summary.append("Quality Assessment:\n");
        summary.append(String.format("  Overall Score: %.1f/100\n", schemaQualityScore));
        summary.append(String.format("  Warnings: %d\n", warnings.size()));
        summary.append(String.format("  Validation Issues: %d\n", validationIssues.size()));
        summary.append(String.format("  Optimizations Applied: %d\n", appliedOptimizations.size()));
        summary.append("\n");

        // Processing information
        if (!processingSteps.isEmpty()) {
            summary.append("Processing Steps:\n");
            for (String step : processingSteps) {
                summary.append("  ").append(step).append("\n");
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * Generates a detailed report including warnings, validation issues, and suggestions.
     *
     * @return a formatted string containing the full generation report
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();

        report.append(getGenerationSummary());

        // Warnings
        if (!warnings.isEmpty()) {
            report.append("Warnings:\n");
            for (String warning : warnings) {
                report.append("  - ").append(warning).append("\n");
            }
            report.append("\n");
        }

        // Validation issues
        if (!validationIssues.isEmpty()) {
            report.append("Validation Issues:\n");
            for (String issue : validationIssues) {
                report.append("  - ").append(issue).append("\n");
            }
            report.append("\n");
        }

        // Optimization suggestions
        if (!optimizationSuggestions.isEmpty()) {
            report.append("Optimization Suggestions:\n");
            for (String suggestion : optimizationSuggestions) {
                report.append("  - ").append(suggestion).append("\n");
            }
            report.append("\n");
        }

        // Applied optimizations
        if (!appliedOptimizations.isEmpty()) {
            report.append("Applied Optimizations:\n");
            for (String optimization : appliedOptimizations) {
                report.append("  - ").append(optimization).append("\n");
            }
            report.append("\n");
        }

        return report.toString();
    }

    // ========== Getters and Setters ==========

    /**
     * Returns whether the schema generation was successful.
     *
     * @return true if generation succeeded, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the success status of the generation.
     *
     * @param success true if generation succeeded, false otherwise
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Returns the error message if generation failed.
     *
     * @return the error message, or null if generation was successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message for a failed generation.
     *
     * @param errorMessage the error message describing what went wrong
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the list of warning messages generated during schema creation.
     *
     * @return the list of warning messages
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Sets the list of warning messages.
     *
     * @param warnings the list of warning messages
     */
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    /**
     * Returns the list of informational messages.
     *
     * @return the list of informational messages
     */
    public List<String> getInformationalMessages() {
        return informationalMessages;
    }

    /**
     * Sets the list of informational messages.
     *
     * @param informationalMessages the list of informational messages
     */
    public void setInformationalMessages(List<String> informationalMessages) {
        this.informationalMessages = informationalMessages;
    }

    /**
     * Returns the generated XSD content as a string.
     *
     * @return the XSD content
     */
    public String getXsdContent() {
        return xsdContent;
    }

    /**
     * Sets the generated XSD content and updates related statistics.
     *
     * @param xsdContent the XSD content to set
     */
    public void setXsdContent(String xsdContent) {
        this.xsdContent = xsdContent;
        this.generatedContentLength = xsdContent != null ? xsdContent.length() : 0;
        if (xsdContent != null) {
            this.totalLinesGenerated = xsdContent.split("\n").length;
        }
    }

    /**
     * Returns the formatted (pretty-printed) XSD content.
     * Falls back to the regular XSD content if formatted content is not available.
     *
     * @return the formatted XSD content, or regular XSD content if not available
     */
    public String getFormattedXsdContent() {
        // Fall back to xsdContent if formattedXsdContent is not set
        return formattedXsdContent != null ? formattedXsdContent : xsdContent;
    }

    /**
     * Sets the formatted (pretty-printed) XSD content.
     *
     * @param formattedXsdContent the formatted XSD content
     */
    public void setFormattedXsdContent(String formattedXsdContent) {
        this.formattedXsdContent = formattedXsdContent;
    }

    /**
     * Returns the map of additional schemas generated for split schema scenarios.
     *
     * @return the map of schema name to schema content
     */
    public Map<String, String> getAdditionalSchemas() {
        return additionalSchemas;
    }

    /**
     * Sets the map of additional schemas.
     *
     * @param additionalSchemas the map of schema name to schema content
     */
    public void setAdditionalSchemas(Map<String, String> additionalSchemas) {
        this.additionalSchemas = additionalSchemas;
    }

    /**
     * Returns the length of the generated XSD content in characters.
     *
     * @return the content length in characters
     */
    public long getGeneratedContentLength() {
        return generatedContentLength;
    }

    /**
     * Sets the length of the generated content.
     *
     * @param generatedContentLength the content length in characters
     */
    public void setGeneratedContentLength(long generatedContentLength) {
        this.generatedContentLength = generatedContentLength;
    }

    /**
     * Returns the schema analysis result containing element and type information.
     *
     * @return the schema analysis result
     */
    public SchemaAnalysisResult getAnalysisResult() {
        return analysisResult;
    }

    /**
     * Sets the schema analysis result.
     *
     * @param analysisResult the schema analysis result
     */
    public void setAnalysisResult(SchemaAnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
    }

    /**
     * Returns the analysis metadata map containing additional analysis information.
     *
     * @return the analysis metadata map
     */
    public Map<String, Object> getAnalysisMetadata() {
        return analysisMetadata;
    }

    /**
     * Sets the analysis metadata map.
     *
     * @param analysisMetadata the analysis metadata map
     */
    public void setAnalysisMetadata(Map<String, Object> analysisMetadata) {
        this.analysisMetadata = analysisMetadata;
    }

    /**
     * Returns the unique identifier for this generation operation.
     *
     * @return the generation ID (UUID)
     */
    public String getGenerationId() {
        return generationId;
    }

    /**
     * Sets the unique identifier for this generation.
     *
     * @param generationId the generation ID
     */
    public void setGenerationId(String generationId) {
        this.generationId = generationId;
    }

    /**
     * Returns the timestamp when the schema was generated.
     *
     * @return the generation timestamp
     */
    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Sets the timestamp when the schema was generated.
     *
     * @param generatedAt the generation timestamp
     */
    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * Returns the total time taken for schema generation in milliseconds.
     *
     * @return the generation time in milliseconds
     */
    public long getGenerationTimeMs() {
        return generationTimeMs;
    }

    /**
     * Sets the total generation time.
     *
     * @param generationTimeMs the generation time in milliseconds
     */
    public void setGenerationTimeMs(long generationTimeMs) {
        this.generationTimeMs = generationTimeMs;
    }

    /**
     * Returns the generation options that were used to create this schema.
     *
     * @return the generation options used
     */
    public SchemaGenerationOptions getUsedOptions() {
        return usedOptions;
    }

    /**
     * Sets the generation options that were used.
     *
     * @param usedOptions the generation options
     */
    public void setUsedOptions(SchemaGenerationOptions usedOptions) {
        this.usedOptions = usedOptions;
    }

    /**
     * Returns the version of the schema generator used.
     *
     * @return the generator version string
     */
    public String getGeneratorVersion() {
        return generatorVersion;
    }

    /**
     * Sets the generator version.
     *
     * @param generatorVersion the generator version string
     */
    public void setGeneratorVersion(String generatorVersion) {
        this.generatorVersion = generatorVersion;
    }

    /**
     * Returns the overall schema quality score (0-100 scale).
     *
     * @return the quality score
     */
    public double getSchemaQualityScore() {
        return schemaQualityScore;
    }

    /**
     * Sets the schema quality score.
     *
     * @param schemaQualityScore the quality score (0-100 scale)
     */
    public void setSchemaQualityScore(double schemaQualityScore) {
        this.schemaQualityScore = schemaQualityScore;
    }

    /**
     * Returns the map of detailed quality metrics.
     *
     * @return the quality metrics map (metric name to value)
     */
    public Map<String, Double> getQualityMetrics() {
        return qualityMetrics;
    }

    /**
     * Sets the quality metrics map.
     *
     * @param qualityMetrics the quality metrics map
     */
    public void setQualityMetrics(Map<String, Double> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    /**
     * Returns the list of optimization suggestions for the generated schema.
     *
     * @return the list of optimization suggestions
     */
    public List<String> getOptimizationSuggestions() {
        return optimizationSuggestions;
    }

    /**
     * Sets the list of optimization suggestions.
     *
     * @param optimizationSuggestions the list of optimization suggestions
     */
    public void setOptimizationSuggestions(List<String> optimizationSuggestions) {
        this.optimizationSuggestions = optimizationSuggestions;
    }

    /**
     * Returns the list of validation issues found in the generated schema.
     *
     * @return the list of validation issues
     */
    public List<String> getValidationIssues() {
        return validationIssues;
    }

    /**
     * Sets the list of validation issues.
     *
     * @param validationIssues the list of validation issues
     */
    public void setValidationIssues(List<String> validationIssues) {
        this.validationIssues = validationIssues;
    }

    /**
     * Returns the map of generation statistics.
     *
     * @return the generation statistics map (statistic name to count)
     */
    public Map<String, Integer> getGenerationStatistics() {
        return generationStatistics;
    }

    /**
     * Sets the generation statistics map.
     *
     * @param generationStatistics the generation statistics map
     */
    public void setGenerationStatistics(Map<String, Integer> generationStatistics) {
        this.generationStatistics = generationStatistics;
    }

    /**
     * Returns the total number of elements generated in the schema.
     *
     * @return the total element count
     */
    public int getTotalElementsGenerated() {
        return totalElementsGenerated;
    }

    /**
     * Sets the total number of elements generated.
     *
     * @param totalElementsGenerated the total element count
     */
    public void setTotalElementsGenerated(int totalElementsGenerated) {
        this.totalElementsGenerated = totalElementsGenerated;
    }

    /**
     * Returns the total number of attributes generated in the schema.
     *
     * @return the total attribute count
     */
    public int getTotalAttributesGenerated() {
        return totalAttributesGenerated;
    }

    /**
     * Sets the total number of attributes generated.
     *
     * @param totalAttributesGenerated the total attribute count
     */
    public void setTotalAttributesGenerated(int totalAttributesGenerated) {
        this.totalAttributesGenerated = totalAttributesGenerated;
    }

    /**
     * Returns the total number of complex types generated in the schema.
     *
     * @return the total complex type count
     */
    public int getTotalComplexTypesGenerated() {
        return totalComplexTypesGenerated;
    }

    /**
     * Sets the total number of complex types generated.
     *
     * @param totalComplexTypesGenerated the total complex type count
     */
    public void setTotalComplexTypesGenerated(int totalComplexTypesGenerated) {
        this.totalComplexTypesGenerated = totalComplexTypesGenerated;
    }

    /**
     * Returns the total number of simple types generated in the schema.
     *
     * @return the total simple type count
     */
    public int getTotalSimpleTypesGenerated() {
        return totalSimpleTypesGenerated;
    }

    /**
     * Sets the total number of simple types generated.
     *
     * @param totalSimpleTypesGenerated the total simple type count
     */
    public void setTotalSimpleTypesGenerated(int totalSimpleTypesGenerated) {
        this.totalSimpleTypesGenerated = totalSimpleTypesGenerated;
    }

    /**
     * Returns the total number of lines in the generated XSD.
     *
     * @return the total line count
     */
    public int getTotalLinesGenerated() {
        return totalLinesGenerated;
    }

    /**
     * Sets the total number of lines generated.
     *
     * @param totalLinesGenerated the total line count
     */
    public void setTotalLinesGenerated(int totalLinesGenerated) {
        this.totalLinesGenerated = totalLinesGenerated;
    }

    /**
     * Returns the list of processing steps executed during generation.
     *
     * @return the list of processing steps with timestamps
     */
    public List<String> getProcessingSteps() {
        return processingSteps;
    }

    /**
     * Sets the list of processing steps.
     *
     * @param processingSteps the list of processing steps
     */
    public void setProcessingSteps(List<String> processingSteps) {
        this.processingSteps = processingSteps;
    }

    /**
     * Returns the map of step timings recording how long each step took.
     *
     * @return the step timings map (step name to time in milliseconds)
     */
    public Map<String, Long> getStepTimings() {
        return stepTimings;
    }

    /**
     * Sets the step timings map.
     *
     * @param stepTimings the step timings map
     */
    public void setStepTimings(Map<String, Long> stepTimings) {
        this.stepTimings = stepTimings;
    }

    /**
     * Returns the list of optimizations that were applied during generation.
     *
     * @return the list of applied optimizations
     */
    public List<String> getAppliedOptimizations() {
        return appliedOptimizations;
    }

    /**
     * Sets the list of applied optimizations.
     *
     * @param appliedOptimizations the list of applied optimizations
     */
    public void setAppliedOptimizations(List<String> appliedOptimizations) {
        this.appliedOptimizations = appliedOptimizations;
    }
}
