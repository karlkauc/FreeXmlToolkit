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
     * Create successful generation result
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
     * Create successful generation result with formatted content
     */
    public static SchemaGenerationResult success(String xsdContent, String formattedXsd, SchemaAnalysisResult analysisResult) {
        SchemaGenerationResult result = success(xsdContent, analysisResult);
        result.formattedXsdContent = formattedXsd;
        return result;
    }

    /**
     * Create error result
     */
    public static SchemaGenerationResult error(String errorMessage) {
        SchemaGenerationResult result = new SchemaGenerationResult(false);
        result.errorMessage = errorMessage;
        return result;
    }

    /**
     * Create error result with exception details
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
     * Add processing step
     */
    public void addProcessingStep(String step) {
        processingSteps.add(LocalDateTime.now() + ": " + step);
    }

    /**
     * Record step timing
     */
    public void recordStepTiming(String stepName, long timeMs) {
        stepTimings.put(stepName, timeMs);
    }

    /**
     * Add applied optimization
     */
    public void addAppliedOptimization(String optimization) {
        appliedOptimizations.add(optimization);
    }

    /**
     * Add warning message
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Add informational message
     */
    public void addInfo(String info) {
        informationalMessages.add(info);
    }

    /**
     * Add validation issue
     */
    public void addValidationIssue(String issue) {
        validationIssues.add(issue);
    }

    /**
     * Add optimization suggestion
     */
    public void addOptimizationSuggestion(String suggestion) {
        optimizationSuggestions.add(suggestion);
    }

    // ========== Quality Analysis Methods ==========

    /**
     * Calculate schema quality score
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
     * Validate generated XSD content
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
     * Get generation summary
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
     * Get detailed report including warnings and suggestions
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getInformationalMessages() {
        return informationalMessages;
    }

    public void setInformationalMessages(List<String> informationalMessages) {
        this.informationalMessages = informationalMessages;
    }

    public String getXsdContent() {
        return xsdContent;
    }

    public void setXsdContent(String xsdContent) {
        this.xsdContent = xsdContent;
        this.generatedContentLength = xsdContent != null ? xsdContent.length() : 0;
        if (xsdContent != null) {
            this.totalLinesGenerated = xsdContent.split("\n").length;
        }
    }

    public String getFormattedXsdContent() {
        return formattedXsdContent;
    }

    public void setFormattedXsdContent(String formattedXsdContent) {
        this.formattedXsdContent = formattedXsdContent;
    }

    public Map<String, String> getAdditionalSchemas() {
        return additionalSchemas;
    }

    public void setAdditionalSchemas(Map<String, String> additionalSchemas) {
        this.additionalSchemas = additionalSchemas;
    }

    public long getGeneratedContentLength() {
        return generatedContentLength;
    }

    public void setGeneratedContentLength(long generatedContentLength) {
        this.generatedContentLength = generatedContentLength;
    }

    public SchemaAnalysisResult getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(SchemaAnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
    }

    public Map<String, Object> getAnalysisMetadata() {
        return analysisMetadata;
    }

    public void setAnalysisMetadata(Map<String, Object> analysisMetadata) {
        this.analysisMetadata = analysisMetadata;
    }

    public String getGenerationId() {
        return generationId;
    }

    public void setGenerationId(String generationId) {
        this.generationId = generationId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public long getGenerationTimeMs() {
        return generationTimeMs;
    }

    public void setGenerationTimeMs(long generationTimeMs) {
        this.generationTimeMs = generationTimeMs;
    }

    public SchemaGenerationOptions getUsedOptions() {
        return usedOptions;
    }

    public void setUsedOptions(SchemaGenerationOptions usedOptions) {
        this.usedOptions = usedOptions;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    public void setGeneratorVersion(String generatorVersion) {
        this.generatorVersion = generatorVersion;
    }

    public double getSchemaQualityScore() {
        return schemaQualityScore;
    }

    public void setSchemaQualityScore(double schemaQualityScore) {
        this.schemaQualityScore = schemaQualityScore;
    }

    public Map<String, Double> getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(Map<String, Double> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    public List<String> getOptimizationSuggestions() {
        return optimizationSuggestions;
    }

    public void setOptimizationSuggestions(List<String> optimizationSuggestions) {
        this.optimizationSuggestions = optimizationSuggestions;
    }

    public List<String> getValidationIssues() {
        return validationIssues;
    }

    public void setValidationIssues(List<String> validationIssues) {
        this.validationIssues = validationIssues;
    }

    public Map<String, Integer> getGenerationStatistics() {
        return generationStatistics;
    }

    public void setGenerationStatistics(Map<String, Integer> generationStatistics) {
        this.generationStatistics = generationStatistics;
    }

    public int getTotalElementsGenerated() {
        return totalElementsGenerated;
    }

    public void setTotalElementsGenerated(int totalElementsGenerated) {
        this.totalElementsGenerated = totalElementsGenerated;
    }

    public int getTotalAttributesGenerated() {
        return totalAttributesGenerated;
    }

    public void setTotalAttributesGenerated(int totalAttributesGenerated) {
        this.totalAttributesGenerated = totalAttributesGenerated;
    }

    public int getTotalComplexTypesGenerated() {
        return totalComplexTypesGenerated;
    }

    public void setTotalComplexTypesGenerated(int totalComplexTypesGenerated) {
        this.totalComplexTypesGenerated = totalComplexTypesGenerated;
    }

    public int getTotalSimpleTypesGenerated() {
        return totalSimpleTypesGenerated;
    }

    public void setTotalSimpleTypesGenerated(int totalSimpleTypesGenerated) {
        this.totalSimpleTypesGenerated = totalSimpleTypesGenerated;
    }

    public int getTotalLinesGenerated() {
        return totalLinesGenerated;
    }

    public void setTotalLinesGenerated(int totalLinesGenerated) {
        this.totalLinesGenerated = totalLinesGenerated;
    }

    public List<String> getProcessingSteps() {
        return processingSteps;
    }

    public void setProcessingSteps(List<String> processingSteps) {
        this.processingSteps = processingSteps;
    }

    public Map<String, Long> getStepTimings() {
        return stepTimings;
    }

    public void setStepTimings(Map<String, Long> stepTimings) {
        this.stepTimings = stepTimings;
    }

    public List<String> getAppliedOptimizations() {
        return appliedOptimizations;
    }

    public void setAppliedOptimizations(List<String> appliedOptimizations) {
        this.appliedOptimizations = appliedOptimizations;
    }
}