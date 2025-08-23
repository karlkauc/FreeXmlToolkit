package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration options for schema generation from XML documents.
 * Controls various aspects of analysis, optimization, and output formatting.
 */
public class SchemaGenerationOptions {

    // Analysis Options
    private boolean enableSmartTypeInference = true;
    private boolean enablePatternDetection = true;
    private boolean enableCrossDocumentAnalysis = true;
    private boolean detectOptionalElements = true;
    private boolean inferComplexTypes = true;
    private boolean analyzeDataPatterns = true;

    // Type Inference Options
    private boolean strictTypeInference = false;
    private boolean preferRestrictiveTypes = true;
    private int minSampleSizeForInference = 3;
    private double confidenceThreshold = 0.8;
    private List<String> customTypePatterns = new ArrayList<>();

    // Occurrence Pattern Detection
    private boolean detectMinOccurs = true;
    private boolean detectMaxOccurs = true;
    private int maxOccursThreshold = 10;
    private boolean unboundedForHighOccurrence = true;
    private double occurrenceConfidence = 0.7;

    // Namespace Handling
    private boolean preserveNamespaces = true;
    private boolean generateTargetNamespace = true;
    private String targetNamespacePrefix = "tns";
    private String targetNamespaceUri = null; // Auto-generated if null
    private boolean qualifyElements = true;
    private boolean qualifyAttributes = false;

    // Schema Structure Options
    private boolean generateComplexTypes = true;
    private boolean generateSimpleTypes = true;
    private boolean inlineSimpleTypes = false;
    private boolean groupSimilarElements = true;
    private int maxInlineDepth = 3;
    private boolean generateGroups = false;
    private boolean generateAttributeGroups = false;

    // Optimization Options
    private boolean optimizeSchema = true;
    private boolean eliminateDuplicateTypes = true;
    private boolean mergeCompatibleTypes = true;
    private boolean flattenUnnecessaryStructure = true;
    private boolean optimizeOccurrenceConstraints = true;
    private int maxTypeVariations = 5;

    // Documentation Options
    private boolean generateDocumentation = true;
    private boolean includeElementDocumentation = true;
    private boolean includeAttributeDocumentation = true;
    private boolean includeTypeDocumentation = true;
    private boolean includeExampleValues = true;
    private int maxExampleValues = 3;
    private boolean generateHumanReadableNames = true;

    // Validation Options
    private boolean addSchemaValidation = true;
    private String schemaVersion = "1.0";
    private boolean addSchemaLocation = false;
    private String schemaLocationUri = null;

    // Output Format Options
    private boolean formatOutput = true;
    private String indentation = "  ";
    private boolean addComments = true;
    private boolean addTimestamp = true;
    private boolean includeGeneratorInfo = true;
    private boolean sortElements = false;
    private boolean sortAttributes = true;

    // Advanced Options
    private Map<String, String> customTypeMapping = new HashMap<>();
    private List<String> ignoredElements = new ArrayList<>();
    private List<String> ignoredAttributes = new ArrayList<>();
    private Map<String, String> elementRenames = new HashMap<>();
    private Map<String, String> attributeRenames = new HashMap<>();
    private boolean preserveElementOrder = true;
    private boolean allowAnyType = false;
    private boolean generateAbstractTypes = false;
    private boolean enableExtensions = false;

    // Performance Options
    private int maxDocumentsToAnalyze = 100;
    private int maxElementsPerDocument = 10000;
    private int maxDepthAnalysis = 20;
    private boolean enableCaching = true;
    private long cacheTimeoutMillis = 300000; // 5 minutes

    public SchemaGenerationOptions() {
        // Initialize with sensible defaults
    }

    /**
     * Create options optimized for simple schemas
     */
    public static SchemaGenerationOptions forSimpleSchema() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();
        options.setInferComplexTypes(false);
        options.setGenerateComplexTypes(false);
        options.setInlineSimpleTypes(true);
        options.setOptimizeSchema(false);
        options.setGenerateDocumentation(false);
        return options;
    }

    /**
     * Create options optimized for complex, enterprise schemas
     */
    public static SchemaGenerationOptions forEnterpriseSchema() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();
        options.setInferComplexTypes(true);
        options.setGenerateComplexTypes(true);
        options.setGenerateGroups(true);
        options.setGenerateAttributeGroups(true);
        options.setOptimizeSchema(true);
        options.setGenerateDocumentation(true);
        options.setStrictTypeInference(true);
        options.setPreferRestrictiveTypes(true);
        return options;
    }

    /**
     * Create options for quick prototyping
     */
    public static SchemaGenerationOptions forPrototyping() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();
        options.setStrictTypeInference(false);
        options.setOptimizeSchema(false);
        options.setGenerateDocumentation(false);
        options.setAllowAnyType(true);
        options.setConfidenceThreshold(0.5);
        return options;
    }

    // ========== Validation Methods ==========

    /**
     * Validate configuration options
     */
    public List<String> validateOptions() {
        List<String> errors = new ArrayList<>();

        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            errors.add("Confidence threshold must be between 0.0 and 1.0");
        }

        if (occurrenceConfidence < 0.0 || occurrenceConfidence > 1.0) {
            errors.add("Occurrence confidence must be between 0.0 and 1.0");
        }

        if (minSampleSizeForInference < 1) {
            errors.add("Minimum sample size for inference must be at least 1");
        }

        if (maxOccursThreshold < 1) {
            errors.add("Max occurs threshold must be at least 1");
        }

        if (maxExampleValues < 0) {
            errors.add("Max example values cannot be negative");
        }

        if (maxInlineDepth < 0) {
            errors.add("Max inline depth cannot be negative");
        }

        if (maxTypeVariations < 1) {
            errors.add("Max type variations must be at least 1");
        }

        if (indentation == null || indentation.isEmpty()) {
            errors.add("Indentation cannot be null or empty");
        }

        return errors;
    }

    // ========== Getters and Setters ==========

    public boolean isEnableSmartTypeInference() {
        return enableSmartTypeInference;
    }

    public void setEnableSmartTypeInference(boolean enableSmartTypeInference) {
        this.enableSmartTypeInference = enableSmartTypeInference;
    }

    public boolean isEnablePatternDetection() {
        return enablePatternDetection;
    }

    public void setEnablePatternDetection(boolean enablePatternDetection) {
        this.enablePatternDetection = enablePatternDetection;
    }

    public boolean isEnableCrossDocumentAnalysis() {
        return enableCrossDocumentAnalysis;
    }

    public void setEnableCrossDocumentAnalysis(boolean enableCrossDocumentAnalysis) {
        this.enableCrossDocumentAnalysis = enableCrossDocumentAnalysis;
    }

    public boolean isDetectOptionalElements() {
        return detectOptionalElements;
    }

    public void setDetectOptionalElements(boolean detectOptionalElements) {
        this.detectOptionalElements = detectOptionalElements;
    }

    public boolean isInferComplexTypes() {
        return inferComplexTypes;
    }

    public void setInferComplexTypes(boolean inferComplexTypes) {
        this.inferComplexTypes = inferComplexTypes;
    }

    public boolean isAnalyzeDataPatterns() {
        return analyzeDataPatterns;
    }

    public void setAnalyzeDataPatterns(boolean analyzeDataPatterns) {
        this.analyzeDataPatterns = analyzeDataPatterns;
    }

    public boolean isStrictTypeInference() {
        return strictTypeInference;
    }

    public void setStrictTypeInference(boolean strictTypeInference) {
        this.strictTypeInference = strictTypeInference;
    }

    public boolean isPreferRestrictiveTypes() {
        return preferRestrictiveTypes;
    }

    public void setPreferRestrictiveTypes(boolean preferRestrictiveTypes) {
        this.preferRestrictiveTypes = preferRestrictiveTypes;
    }

    public int getMinSampleSizeForInference() {
        return minSampleSizeForInference;
    }

    public void setMinSampleSizeForInference(int minSampleSizeForInference) {
        this.minSampleSizeForInference = minSampleSizeForInference;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public List<String> getCustomTypePatterns() {
        return customTypePatterns;
    }

    public void setCustomTypePatterns(List<String> customTypePatterns) {
        this.customTypePatterns = customTypePatterns;
    }

    public boolean isDetectMinOccurs() {
        return detectMinOccurs;
    }

    public void setDetectMinOccurs(boolean detectMinOccurs) {
        this.detectMinOccurs = detectMinOccurs;
    }

    public boolean isDetectMaxOccurs() {
        return detectMaxOccurs;
    }

    public void setDetectMaxOccurs(boolean detectMaxOccurs) {
        this.detectMaxOccurs = detectMaxOccurs;
    }

    public int getMaxOccursThreshold() {
        return maxOccursThreshold;
    }

    public void setMaxOccursThreshold(int maxOccursThreshold) {
        this.maxOccursThreshold = maxOccursThreshold;
    }

    public boolean isUnboundedForHighOccurrence() {
        return unboundedForHighOccurrence;
    }

    public void setUnboundedForHighOccurrence(boolean unboundedForHighOccurrence) {
        this.unboundedForHighOccurrence = unboundedForHighOccurrence;
    }

    public double getOccurrenceConfidence() {
        return occurrenceConfidence;
    }

    public void setOccurrenceConfidence(double occurrenceConfidence) {
        this.occurrenceConfidence = occurrenceConfidence;
    }

    public boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    public void setPreserveNamespaces(boolean preserveNamespaces) {
        this.preserveNamespaces = preserveNamespaces;
    }

    public boolean isGenerateTargetNamespace() {
        return generateTargetNamespace;
    }

    public void setGenerateTargetNamespace(boolean generateTargetNamespace) {
        this.generateTargetNamespace = generateTargetNamespace;
    }

    public String getTargetNamespacePrefix() {
        return targetNamespacePrefix;
    }

    public void setTargetNamespacePrefix(String targetNamespacePrefix) {
        this.targetNamespacePrefix = targetNamespacePrefix;
    }

    public String getTargetNamespaceUri() {
        return targetNamespaceUri;
    }

    public void setTargetNamespaceUri(String targetNamespaceUri) {
        this.targetNamespaceUri = targetNamespaceUri;
    }

    public boolean isQualifyElements() {
        return qualifyElements;
    }

    public void setQualifyElements(boolean qualifyElements) {
        this.qualifyElements = qualifyElements;
    }

    public boolean isQualifyAttributes() {
        return qualifyAttributes;
    }

    public void setQualifyAttributes(boolean qualifyAttributes) {
        this.qualifyAttributes = qualifyAttributes;
    }

    public boolean isGenerateComplexTypes() {
        return generateComplexTypes;
    }

    public void setGenerateComplexTypes(boolean generateComplexTypes) {
        this.generateComplexTypes = generateComplexTypes;
    }

    public boolean isGenerateSimpleTypes() {
        return generateSimpleTypes;
    }

    public void setGenerateSimpleTypes(boolean generateSimpleTypes) {
        this.generateSimpleTypes = generateSimpleTypes;
    }

    public boolean isInlineSimpleTypes() {
        return inlineSimpleTypes;
    }

    public void setInlineSimpleTypes(boolean inlineSimpleTypes) {
        this.inlineSimpleTypes = inlineSimpleTypes;
    }

    public boolean isGroupSimilarElements() {
        return groupSimilarElements;
    }

    public void setGroupSimilarElements(boolean groupSimilarElements) {
        this.groupSimilarElements = groupSimilarElements;
    }

    public int getMaxInlineDepth() {
        return maxInlineDepth;
    }

    public void setMaxInlineDepth(int maxInlineDepth) {
        this.maxInlineDepth = maxInlineDepth;
    }

    public boolean isGenerateGroups() {
        return generateGroups;
    }

    public void setGenerateGroups(boolean generateGroups) {
        this.generateGroups = generateGroups;
    }

    public boolean isGenerateAttributeGroups() {
        return generateAttributeGroups;
    }

    public void setGenerateAttributeGroups(boolean generateAttributeGroups) {
        this.generateAttributeGroups = generateAttributeGroups;
    }

    public boolean isOptimizeSchema() {
        return optimizeSchema;
    }

    public void setOptimizeSchema(boolean optimizeSchema) {
        this.optimizeSchema = optimizeSchema;
    }

    public boolean isEliminateDuplicateTypes() {
        return eliminateDuplicateTypes;
    }

    public void setEliminateDuplicateTypes(boolean eliminateDuplicateTypes) {
        this.eliminateDuplicateTypes = eliminateDuplicateTypes;
    }

    public boolean isMergeCompatibleTypes() {
        return mergeCompatibleTypes;
    }

    public void setMergeCompatibleTypes(boolean mergeCompatibleTypes) {
        this.mergeCompatibleTypes = mergeCompatibleTypes;
    }

    public boolean isFlattenUnnecessaryStructure() {
        return flattenUnnecessaryStructure;
    }

    public void setFlattenUnnecessaryStructure(boolean flattenUnnecessaryStructure) {
        this.flattenUnnecessaryStructure = flattenUnnecessaryStructure;
    }

    public boolean isOptimizeOccurrenceConstraints() {
        return optimizeOccurrenceConstraints;
    }

    public void setOptimizeOccurrenceConstraints(boolean optimizeOccurrenceConstraints) {
        this.optimizeOccurrenceConstraints = optimizeOccurrenceConstraints;
    }

    public int getMaxTypeVariations() {
        return maxTypeVariations;
    }

    public void setMaxTypeVariations(int maxTypeVariations) {
        this.maxTypeVariations = maxTypeVariations;
    }

    public boolean isGenerateDocumentation() {
        return generateDocumentation;
    }

    public void setGenerateDocumentation(boolean generateDocumentation) {
        this.generateDocumentation = generateDocumentation;
    }

    public boolean isIncludeElementDocumentation() {
        return includeElementDocumentation;
    }

    public void setIncludeElementDocumentation(boolean includeElementDocumentation) {
        this.includeElementDocumentation = includeElementDocumentation;
    }

    public boolean isIncludeAttributeDocumentation() {
        return includeAttributeDocumentation;
    }

    public void setIncludeAttributeDocumentation(boolean includeAttributeDocumentation) {
        this.includeAttributeDocumentation = includeAttributeDocumentation;
    }

    public boolean isIncludeTypeDocumentation() {
        return includeTypeDocumentation;
    }

    public void setIncludeTypeDocumentation(boolean includeTypeDocumentation) {
        this.includeTypeDocumentation = includeTypeDocumentation;
    }

    public boolean isIncludeExampleValues() {
        return includeExampleValues;
    }

    public void setIncludeExampleValues(boolean includeExampleValues) {
        this.includeExampleValues = includeExampleValues;
    }

    public int getMaxExampleValues() {
        return maxExampleValues;
    }

    public void setMaxExampleValues(int maxExampleValues) {
        this.maxExampleValues = maxExampleValues;
    }

    public boolean isGenerateHumanReadableNames() {
        return generateHumanReadableNames;
    }

    public void setGenerateHumanReadableNames(boolean generateHumanReadableNames) {
        this.generateHumanReadableNames = generateHumanReadableNames;
    }

    public boolean isAddSchemaValidation() {
        return addSchemaValidation;
    }

    public void setAddSchemaValidation(boolean addSchemaValidation) {
        this.addSchemaValidation = addSchemaValidation;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isAddSchemaLocation() {
        return addSchemaLocation;
    }

    public void setAddSchemaLocation(boolean addSchemaLocation) {
        this.addSchemaLocation = addSchemaLocation;
    }

    public String getSchemaLocationUri() {
        return schemaLocationUri;
    }

    public void setSchemaLocationUri(String schemaLocationUri) {
        this.schemaLocationUri = schemaLocationUri;
    }

    public boolean isFormatOutput() {
        return formatOutput;
    }

    public void setFormatOutput(boolean formatOutput) {
        this.formatOutput = formatOutput;
    }

    public String getIndentation() {
        return indentation;
    }

    public void setIndentation(String indentation) {
        this.indentation = indentation;
    }

    public boolean isAddComments() {
        return addComments;
    }

    public void setAddComments(boolean addComments) {
        this.addComments = addComments;
    }

    public boolean isAddTimestamp() {
        return addTimestamp;
    }

    public void setAddTimestamp(boolean addTimestamp) {
        this.addTimestamp = addTimestamp;
    }

    public boolean isIncludeGeneratorInfo() {
        return includeGeneratorInfo;
    }

    public void setIncludeGeneratorInfo(boolean includeGeneratorInfo) {
        this.includeGeneratorInfo = includeGeneratorInfo;
    }

    public boolean isSortElements() {
        return sortElements;
    }

    public void setSortElements(boolean sortElements) {
        this.sortElements = sortElements;
    }

    public boolean isSortAttributes() {
        return sortAttributes;
    }

    public void setSortAttributes(boolean sortAttributes) {
        this.sortAttributes = sortAttributes;
    }

    public Map<String, String> getCustomTypeMapping() {
        return customTypeMapping;
    }

    public void setCustomTypeMapping(Map<String, String> customTypeMapping) {
        this.customTypeMapping = customTypeMapping;
    }

    public List<String> getIgnoredElements() {
        return ignoredElements;
    }

    public void setIgnoredElements(List<String> ignoredElements) {
        this.ignoredElements = ignoredElements;
    }

    public List<String> getIgnoredAttributes() {
        return ignoredAttributes;
    }

    public void setIgnoredAttributes(List<String> ignoredAttributes) {
        this.ignoredAttributes = ignoredAttributes;
    }

    public Map<String, String> getElementRenames() {
        return elementRenames;
    }

    public void setElementRenames(Map<String, String> elementRenames) {
        this.elementRenames = elementRenames;
    }

    public Map<String, String> getAttributeRenames() {
        return attributeRenames;
    }

    public void setAttributeRenames(Map<String, String> attributeRenames) {
        this.attributeRenames = attributeRenames;
    }

    public boolean isPreserveElementOrder() {
        return preserveElementOrder;
    }

    public void setPreserveElementOrder(boolean preserveElementOrder) {
        this.preserveElementOrder = preserveElementOrder;
    }

    public boolean isAllowAnyType() {
        return allowAnyType;
    }

    public void setAllowAnyType(boolean allowAnyType) {
        this.allowAnyType = allowAnyType;
    }

    public boolean isGenerateAbstractTypes() {
        return generateAbstractTypes;
    }

    public void setGenerateAbstractTypes(boolean generateAbstractTypes) {
        this.generateAbstractTypes = generateAbstractTypes;
    }

    public boolean isEnableExtensions() {
        return enableExtensions;
    }

    public void setEnableExtensions(boolean enableExtensions) {
        this.enableExtensions = enableExtensions;
    }

    public int getMaxDocumentsToAnalyze() {
        return maxDocumentsToAnalyze;
    }

    public void setMaxDocumentsToAnalyze(int maxDocumentsToAnalyze) {
        this.maxDocumentsToAnalyze = maxDocumentsToAnalyze;
    }

    public int getMaxElementsPerDocument() {
        return maxElementsPerDocument;
    }

    public void setMaxElementsPerDocument(int maxElementsPerDocument) {
        this.maxElementsPerDocument = maxElementsPerDocument;
    }

    public int getMaxDepthAnalysis() {
        return maxDepthAnalysis;
    }

    public void setMaxDepthAnalysis(int maxDepthAnalysis) {
        this.maxDepthAnalysis = maxDepthAnalysis;
    }

    public boolean isEnableCaching() {
        return enableCaching;
    }

    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }

    public long getCacheTimeoutMillis() {
        return cacheTimeoutMillis;
    }

    public void setCacheTimeoutMillis(long cacheTimeoutMillis) {
        this.cacheTimeoutMillis = cacheTimeoutMillis;
    }
}