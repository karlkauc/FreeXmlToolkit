package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration options for schema generation from XML documents.
 * Controls various aspects of analysis, optimization, and output formatting.
 *
 * <p>This class provides a comprehensive set of options to customize how XSD schemas
 * are generated from XML documents. It supports multiple preset configurations through
 * factory methods for different use cases (simple schemas, enterprise schemas, prototyping).
 *
 * <p>Options are organized into the following categories:
 * <ul>
 *   <li>Analysis Options - Control how XML documents are analyzed</li>
 *   <li>Type Inference Options - Configure type detection behavior</li>
 *   <li>Occurrence Pattern Detection - Settings for minOccurs/maxOccurs inference</li>
 *   <li>Namespace Handling - Control namespace generation and qualification</li>
 *   <li>Schema Structure Options - Configure generated schema structure</li>
 *   <li>Optimization Options - Schema optimization settings</li>
 *   <li>Documentation Options - Control documentation generation</li>
 *   <li>Validation Options - Schema validation settings</li>
 *   <li>Output Format Options - Control output formatting</li>
 *   <li>Advanced Options - Custom mappings and element handling</li>
 *   <li>Performance Options - Resource limits and caching</li>
 * </ul>
 *
 * @see #forSimpleSchema()
 * @see #forEnterpriseSchema()
 * @see #forPrototyping()
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

    /**
     * Creates a new SchemaGenerationOptions instance with default settings.
     *
     * <p>Default settings are optimized for general-purpose schema generation
     * with smart type inference enabled, pattern detection active, and
     * documentation generation turned on.
     */
    public SchemaGenerationOptions() {
        // Initialize with sensible defaults
    }

    /**
     * Creates options optimized for simple schemas.
     *
     * <p>This preset disables complex type inference and optimization,
     * inlines simple types, and disables documentation generation.
     * Suitable for small XML documents with straightforward structures.
     *
     * @return a new SchemaGenerationOptions instance configured for simple schemas
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
     * Creates options optimized for complex, enterprise schemas.
     *
     * <p>This preset enables all features including complex type inference,
     * group generation, attribute groups, strict type inference, and
     * full documentation. Suitable for large, complex XML documents
     * requiring comprehensive schema generation.
     *
     * @return a new SchemaGenerationOptions instance configured for enterprise schemas
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
     * Creates options for quick prototyping.
     *
     * <p>This preset uses relaxed settings with lower confidence thresholds,
     * disables optimization and documentation, and allows any type.
     * Suitable for rapid schema generation during development.
     *
     * @return a new SchemaGenerationOptions instance configured for prototyping
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
     * Validates the current configuration options.
     *
     * <p>Checks all configurable values for validity, including:
     * <ul>
     *   <li>Confidence thresholds must be between 0.0 and 1.0</li>
     *   <li>Minimum sample size must be at least 1</li>
     *   <li>Max occurs threshold must be at least 1</li>
     *   <li>Max example values cannot be negative</li>
     *   <li>Max inline depth cannot be negative</li>
     *   <li>Max type variations must be at least 1</li>
     *   <li>Indentation cannot be null or empty</li>
     * </ul>
     *
     * @return a list of validation error messages; empty if all options are valid
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

    /**
     * Returns whether smart type inference is enabled.
     *
     * <p>When enabled, the generator uses heuristics to determine
     * the most appropriate XSD type for element content.
     *
     * @return {@code true} if smart type inference is enabled, {@code false} otherwise
     */
    public boolean isEnableSmartTypeInference() {
        return enableSmartTypeInference;
    }

    /**
     * Sets whether smart type inference is enabled.
     *
     * @param enableSmartTypeInference {@code true} to enable smart type inference,
     *                                  {@code false} to disable it
     */
    public void setEnableSmartTypeInference(boolean enableSmartTypeInference) {
        this.enableSmartTypeInference = enableSmartTypeInference;
    }

    /**
     * Returns whether pattern detection is enabled.
     *
     * <p>When enabled, the generator analyzes element content for patterns
     * such as email addresses, dates, or phone numbers.
     *
     * @return {@code true} if pattern detection is enabled, {@code false} otherwise
     */
    public boolean isEnablePatternDetection() {
        return enablePatternDetection;
    }

    /**
     * Sets whether pattern detection is enabled.
     *
     * @param enablePatternDetection {@code true} to enable pattern detection,
     *                                {@code false} to disable it
     */
    public void setEnablePatternDetection(boolean enablePatternDetection) {
        this.enablePatternDetection = enablePatternDetection;
    }

    /**
     * Returns whether cross-document analysis is enabled.
     *
     * <p>When enabled, the generator analyzes multiple XML documents
     * to improve type inference accuracy.
     *
     * @return {@code true} if cross-document analysis is enabled, {@code false} otherwise
     */
    public boolean isEnableCrossDocumentAnalysis() {
        return enableCrossDocumentAnalysis;
    }

    /**
     * Sets whether cross-document analysis is enabled.
     *
     * @param enableCrossDocumentAnalysis {@code true} to enable cross-document analysis,
     *                                     {@code false} to disable it
     */
    public void setEnableCrossDocumentAnalysis(boolean enableCrossDocumentAnalysis) {
        this.enableCrossDocumentAnalysis = enableCrossDocumentAnalysis;
    }

    /**
     * Returns whether optional element detection is enabled.
     *
     * <p>When enabled, the generator identifies elements that appear
     * inconsistently across documents and marks them as optional (minOccurs="0").
     *
     * @return {@code true} if optional element detection is enabled, {@code false} otherwise
     */
    public boolean isDetectOptionalElements() {
        return detectOptionalElements;
    }

    /**
     * Sets whether optional element detection is enabled.
     *
     * @param detectOptionalElements {@code true} to enable optional element detection,
     *                                {@code false} to disable it
     */
    public void setDetectOptionalElements(boolean detectOptionalElements) {
        this.detectOptionalElements = detectOptionalElements;
    }

    /**
     * Returns whether complex type inference is enabled.
     *
     * <p>When enabled, the generator creates named complex types
     * for recurring element structures.
     *
     * @return {@code true} if complex type inference is enabled, {@code false} otherwise
     */
    public boolean isInferComplexTypes() {
        return inferComplexTypes;
    }

    /**
     * Sets whether complex type inference is enabled.
     *
     * @param inferComplexTypes {@code true} to enable complex type inference,
     *                           {@code false} to disable it
     */
    public void setInferComplexTypes(boolean inferComplexTypes) {
        this.inferComplexTypes = inferComplexTypes;
    }

    /**
     * Returns whether data pattern analysis is enabled.
     *
     * <p>When enabled, the generator analyzes actual data values
     * to determine appropriate type constraints.
     *
     * @return {@code true} if data pattern analysis is enabled, {@code false} otherwise
     */
    public boolean isAnalyzeDataPatterns() {
        return analyzeDataPatterns;
    }

    /**
     * Sets whether data pattern analysis is enabled.
     *
     * @param analyzeDataPatterns {@code true} to enable data pattern analysis,
     *                             {@code false} to disable it
     */
    public void setAnalyzeDataPatterns(boolean analyzeDataPatterns) {
        this.analyzeDataPatterns = analyzeDataPatterns;
    }

    /**
     * Returns whether strict type inference is enabled.
     *
     * <p>When enabled, the generator uses more conservative type inference,
     * requiring higher confidence levels for type decisions.
     *
     * @return {@code true} if strict type inference is enabled, {@code false} otherwise
     */
    public boolean isStrictTypeInference() {
        return strictTypeInference;
    }

    /**
     * Sets whether strict type inference is enabled.
     *
     * @param strictTypeInference {@code true} to enable strict type inference,
     *                             {@code false} to disable it
     */
    public void setStrictTypeInference(boolean strictTypeInference) {
        this.strictTypeInference = strictTypeInference;
    }

    /**
     * Returns whether restrictive types are preferred.
     *
     * <p>When enabled, the generator prefers more specific types
     * (e.g., xs:integer over xs:decimal) when possible.
     *
     * @return {@code true} if restrictive types are preferred, {@code false} otherwise
     */
    public boolean isPreferRestrictiveTypes() {
        return preferRestrictiveTypes;
    }

    /**
     * Sets whether restrictive types are preferred.
     *
     * @param preferRestrictiveTypes {@code true} to prefer restrictive types,
     *                                {@code false} to prefer general types
     */
    public void setPreferRestrictiveTypes(boolean preferRestrictiveTypes) {
        this.preferRestrictiveTypes = preferRestrictiveTypes;
    }

    /**
     * Returns the minimum sample size required for type inference.
     *
     * <p>The generator requires at least this many samples before
     * making type inference decisions.
     *
     * @return the minimum sample size for type inference
     */
    public int getMinSampleSizeForInference() {
        return minSampleSizeForInference;
    }

    /**
     * Sets the minimum sample size required for type inference.
     *
     * @param minSampleSizeForInference the minimum sample size; must be at least 1
     */
    public void setMinSampleSizeForInference(int minSampleSizeForInference) {
        this.minSampleSizeForInference = minSampleSizeForInference;
    }

    /**
     * Returns the confidence threshold for type inference.
     *
     * <p>Type inference decisions are only made when confidence
     * exceeds this threshold (0.0 to 1.0).
     *
     * @return the confidence threshold
     */
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    /**
     * Sets the confidence threshold for type inference.
     *
     * @param confidenceThreshold the confidence threshold; must be between 0.0 and 1.0
     */
    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Returns the list of custom type patterns.
     *
     * <p>Custom patterns are regular expressions that map to specific XSD types.
     *
     * @return the list of custom type patterns
     */
    public List<String> getCustomTypePatterns() {
        return customTypePatterns;
    }

    /**
     * Sets the list of custom type patterns.
     *
     * @param customTypePatterns the list of custom type patterns
     */
    public void setCustomTypePatterns(List<String> customTypePatterns) {
        this.customTypePatterns = customTypePatterns;
    }

    /**
     * Returns whether minOccurs detection is enabled.
     *
     * <p>When enabled, the generator analyzes element occurrence patterns
     * to determine appropriate minOccurs values.
     *
     * @return {@code true} if minOccurs detection is enabled, {@code false} otherwise
     */
    public boolean isDetectMinOccurs() {
        return detectMinOccurs;
    }

    /**
     * Sets whether minOccurs detection is enabled.
     *
     * @param detectMinOccurs {@code true} to enable minOccurs detection,
     *                         {@code false} to disable it
     */
    public void setDetectMinOccurs(boolean detectMinOccurs) {
        this.detectMinOccurs = detectMinOccurs;
    }

    /**
     * Returns whether maxOccurs detection is enabled.
     *
     * <p>When enabled, the generator analyzes element occurrence patterns
     * to determine appropriate maxOccurs values.
     *
     * @return {@code true} if maxOccurs detection is enabled, {@code false} otherwise
     */
    public boolean isDetectMaxOccurs() {
        return detectMaxOccurs;
    }

    /**
     * Sets whether maxOccurs detection is enabled.
     *
     * @param detectMaxOccurs {@code true} to enable maxOccurs detection,
     *                         {@code false} to disable it
     */
    public void setDetectMaxOccurs(boolean detectMaxOccurs) {
        this.detectMaxOccurs = detectMaxOccurs;
    }

    /**
     * Returns the threshold for maxOccurs detection.
     *
     * <p>Elements with occurrence counts exceeding this threshold
     * may be marked as unbounded.
     *
     * @return the maxOccurs threshold
     */
    public int getMaxOccursThreshold() {
        return maxOccursThreshold;
    }

    /**
     * Sets the threshold for maxOccurs detection.
     *
     * @param maxOccursThreshold the threshold; must be at least 1
     */
    public void setMaxOccursThreshold(int maxOccursThreshold) {
        this.maxOccursThreshold = maxOccursThreshold;
    }

    /**
     * Returns whether high occurrence counts should be treated as unbounded.
     *
     * <p>When enabled, elements with occurrence counts exceeding the threshold
     * are marked as maxOccurs="unbounded".
     *
     * @return {@code true} if high occurrences are treated as unbounded, {@code false} otherwise
     */
    public boolean isUnboundedForHighOccurrence() {
        return unboundedForHighOccurrence;
    }

    /**
     * Sets whether high occurrence counts should be treated as unbounded.
     *
     * @param unboundedForHighOccurrence {@code true} to treat high occurrences as unbounded,
     *                                    {@code false} to use actual counts
     */
    public void setUnboundedForHighOccurrence(boolean unboundedForHighOccurrence) {
        this.unboundedForHighOccurrence = unboundedForHighOccurrence;
    }

    /**
     * Returns the confidence level for occurrence pattern detection.
     *
     * <p>Occurrence pattern decisions are only made when confidence
     * exceeds this threshold (0.0 to 1.0).
     *
     * @return the occurrence confidence threshold
     */
    public double getOccurrenceConfidence() {
        return occurrenceConfidence;
    }

    /**
     * Sets the confidence level for occurrence pattern detection.
     *
     * @param occurrenceConfidence the confidence threshold; must be between 0.0 and 1.0
     */
    public void setOccurrenceConfidence(double occurrenceConfidence) {
        this.occurrenceConfidence = occurrenceConfidence;
    }

    /**
     * Returns whether namespaces should be preserved from source documents.
     *
     * <p>When enabled, namespaces found in the source XML are preserved
     * in the generated schema.
     *
     * @return {@code true} if namespaces are preserved, {@code false} otherwise
     */
    public boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    /**
     * Sets whether namespaces should be preserved from source documents.
     *
     * @param preserveNamespaces {@code true} to preserve namespaces,
     *                            {@code false} to ignore them
     */
    public void setPreserveNamespaces(boolean preserveNamespaces) {
        this.preserveNamespaces = preserveNamespaces;
    }

    /**
     * Returns whether a target namespace should be generated.
     *
     * <p>When enabled, the generated schema includes a targetNamespace attribute.
     *
     * @return {@code true} if target namespace is generated, {@code false} otherwise
     */
    public boolean isGenerateTargetNamespace() {
        return generateTargetNamespace;
    }

    /**
     * Sets whether a target namespace should be generated.
     *
     * @param generateTargetNamespace {@code true} to generate a target namespace,
     *                                 {@code false} to omit it
     */
    public void setGenerateTargetNamespace(boolean generateTargetNamespace) {
        this.generateTargetNamespace = generateTargetNamespace;
    }

    /**
     * Returns the prefix to use for the target namespace.
     *
     * @return the target namespace prefix
     */
    public String getTargetNamespacePrefix() {
        return targetNamespacePrefix;
    }

    /**
     * Sets the prefix to use for the target namespace.
     *
     * @param targetNamespacePrefix the target namespace prefix
     */
    public void setTargetNamespacePrefix(String targetNamespacePrefix) {
        this.targetNamespacePrefix = targetNamespacePrefix;
    }

    /**
     * Returns the URI to use for the target namespace.
     *
     * <p>If {@code null}, the URI is auto-generated based on the document structure.
     *
     * @return the target namespace URI, or {@code null} if auto-generated
     */
    public String getTargetNamespaceUri() {
        return targetNamespaceUri;
    }

    /**
     * Sets the URI to use for the target namespace.
     *
     * @param targetNamespaceUri the target namespace URI, or {@code null} for auto-generation
     */
    public void setTargetNamespaceUri(String targetNamespaceUri) {
        this.targetNamespaceUri = targetNamespaceUri;
    }

    /**
     * Returns whether elements should be qualified with the target namespace.
     *
     * <p>When enabled, elementFormDefault="qualified" is set in the schema.
     *
     * @return {@code true} if elements are qualified, {@code false} otherwise
     */
    public boolean isQualifyElements() {
        return qualifyElements;
    }

    /**
     * Sets whether elements should be qualified with the target namespace.
     *
     * @param qualifyElements {@code true} to qualify elements, {@code false} otherwise
     */
    public void setQualifyElements(boolean qualifyElements) {
        this.qualifyElements = qualifyElements;
    }

    /**
     * Returns whether attributes should be qualified with the target namespace.
     *
     * <p>When enabled, attributeFormDefault="qualified" is set in the schema.
     *
     * @return {@code true} if attributes are qualified, {@code false} otherwise
     */
    public boolean isQualifyAttributes() {
        return qualifyAttributes;
    }

    /**
     * Sets whether attributes should be qualified with the target namespace.
     *
     * @param qualifyAttributes {@code true} to qualify attributes, {@code false} otherwise
     */
    public void setQualifyAttributes(boolean qualifyAttributes) {
        this.qualifyAttributes = qualifyAttributes;
    }

    /**
     * Returns whether complex types should be generated.
     *
     * <p>When enabled, named complex types are created for element structures.
     *
     * @return {@code true} if complex types are generated, {@code false} otherwise
     */
    public boolean isGenerateComplexTypes() {
        return generateComplexTypes;
    }

    /**
     * Sets whether complex types should be generated.
     *
     * @param generateComplexTypes {@code true} to generate complex types,
     *                              {@code false} to use anonymous types
     */
    public void setGenerateComplexTypes(boolean generateComplexTypes) {
        this.generateComplexTypes = generateComplexTypes;
    }

    /**
     * Returns whether simple types should be generated.
     *
     * <p>When enabled, named simple types are created for constrained data.
     *
     * @return {@code true} if simple types are generated, {@code false} otherwise
     */
    public boolean isGenerateSimpleTypes() {
        return generateSimpleTypes;
    }

    /**
     * Sets whether simple types should be generated.
     *
     * @param generateSimpleTypes {@code true} to generate simple types,
     *                             {@code false} to use built-in types
     */
    public void setGenerateSimpleTypes(boolean generateSimpleTypes) {
        this.generateSimpleTypes = generateSimpleTypes;
    }

    /**
     * Returns whether simple types should be inlined.
     *
     * <p>When enabled, simple type definitions are embedded directly
     * in element declarations rather than referenced.
     *
     * @return {@code true} if simple types are inlined, {@code false} otherwise
     */
    public boolean isInlineSimpleTypes() {
        return inlineSimpleTypes;
    }

    /**
     * Sets whether simple types should be inlined.
     *
     * @param inlineSimpleTypes {@code true} to inline simple types,
     *                           {@code false} to use named references
     */
    public void setInlineSimpleTypes(boolean inlineSimpleTypes) {
        this.inlineSimpleTypes = inlineSimpleTypes;
    }

    /**
     * Returns whether similar elements should be grouped.
     *
     * <p>When enabled, elements with similar structures are grouped
     * into shared type definitions.
     *
     * @return {@code true} if similar elements are grouped, {@code false} otherwise
     */
    public boolean isGroupSimilarElements() {
        return groupSimilarElements;
    }

    /**
     * Sets whether similar elements should be grouped.
     *
     * @param groupSimilarElements {@code true} to group similar elements,
     *                              {@code false} to keep them separate
     */
    public void setGroupSimilarElements(boolean groupSimilarElements) {
        this.groupSimilarElements = groupSimilarElements;
    }

    /**
     * Returns the maximum depth for inline type definitions.
     *
     * <p>Nested types beyond this depth are extracted into named definitions.
     *
     * @return the maximum inline depth
     */
    public int getMaxInlineDepth() {
        return maxInlineDepth;
    }

    /**
     * Sets the maximum depth for inline type definitions.
     *
     * @param maxInlineDepth the maximum inline depth; must not be negative
     */
    public void setMaxInlineDepth(int maxInlineDepth) {
        this.maxInlineDepth = maxInlineDepth;
    }

    /**
     * Returns whether xs:group definitions should be generated.
     *
     * <p>When enabled, reusable element groups are created for
     * recurring element sequences.
     *
     * @return {@code true} if groups are generated, {@code false} otherwise
     */
    public boolean isGenerateGroups() {
        return generateGroups;
    }

    /**
     * Sets whether xs:group definitions should be generated.
     *
     * @param generateGroups {@code true} to generate groups, {@code false} otherwise
     */
    public void setGenerateGroups(boolean generateGroups) {
        this.generateGroups = generateGroups;
    }

    /**
     * Returns whether xs:attributeGroup definitions should be generated.
     *
     * <p>When enabled, reusable attribute groups are created for
     * recurring attribute sets.
     *
     * @return {@code true} if attribute groups are generated, {@code false} otherwise
     */
    public boolean isGenerateAttributeGroups() {
        return generateAttributeGroups;
    }

    /**
     * Sets whether xs:attributeGroup definitions should be generated.
     *
     * @param generateAttributeGroups {@code true} to generate attribute groups,
     *                                 {@code false} otherwise
     */
    public void setGenerateAttributeGroups(boolean generateAttributeGroups) {
        this.generateAttributeGroups = generateAttributeGroups;
    }

    /**
     * Returns whether schema optimization is enabled.
     *
     * <p>When enabled, the generator applies various optimizations
     * to reduce schema size and complexity.
     *
     * @return {@code true} if schema optimization is enabled, {@code false} otherwise
     */
    public boolean isOptimizeSchema() {
        return optimizeSchema;
    }

    /**
     * Sets whether schema optimization is enabled.
     *
     * @param optimizeSchema {@code true} to enable optimization,
     *                        {@code false} to disable it
     */
    public void setOptimizeSchema(boolean optimizeSchema) {
        this.optimizeSchema = optimizeSchema;
    }

    /**
     * Returns whether duplicate type elimination is enabled.
     *
     * <p>When enabled, identical type definitions are merged into single definitions.
     *
     * @return {@code true} if duplicate elimination is enabled, {@code false} otherwise
     */
    public boolean isEliminateDuplicateTypes() {
        return eliminateDuplicateTypes;
    }

    /**
     * Sets whether duplicate type elimination is enabled.
     *
     * @param eliminateDuplicateTypes {@code true} to eliminate duplicates,
     *                                 {@code false} to keep them
     */
    public void setEliminateDuplicateTypes(boolean eliminateDuplicateTypes) {
        this.eliminateDuplicateTypes = eliminateDuplicateTypes;
    }

    /**
     * Returns whether compatible type merging is enabled.
     *
     * <p>When enabled, structurally compatible types are merged
     * into unified definitions.
     *
     * @return {@code true} if compatible type merging is enabled, {@code false} otherwise
     */
    public boolean isMergeCompatibleTypes() {
        return mergeCompatibleTypes;
    }

    /**
     * Sets whether compatible type merging is enabled.
     *
     * @param mergeCompatibleTypes {@code true} to merge compatible types,
     *                              {@code false} to keep them separate
     */
    public void setMergeCompatibleTypes(boolean mergeCompatibleTypes) {
        this.mergeCompatibleTypes = mergeCompatibleTypes;
    }

    /**
     * Returns whether unnecessary structure flattening is enabled.
     *
     * <p>When enabled, intermediate wrapper types are removed
     * when they add no value.
     *
     * @return {@code true} if structure flattening is enabled, {@code false} otherwise
     */
    public boolean isFlattenUnnecessaryStructure() {
        return flattenUnnecessaryStructure;
    }

    /**
     * Sets whether unnecessary structure flattening is enabled.
     *
     * @param flattenUnnecessaryStructure {@code true} to flatten structures,
     *                                     {@code false} to preserve them
     */
    public void setFlattenUnnecessaryStructure(boolean flattenUnnecessaryStructure) {
        this.flattenUnnecessaryStructure = flattenUnnecessaryStructure;
    }

    /**
     * Returns whether occurrence constraint optimization is enabled.
     *
     * <p>When enabled, redundant occurrence constraints are simplified.
     *
     * @return {@code true} if occurrence optimization is enabled, {@code false} otherwise
     */
    public boolean isOptimizeOccurrenceConstraints() {
        return optimizeOccurrenceConstraints;
    }

    /**
     * Sets whether occurrence constraint optimization is enabled.
     *
     * @param optimizeOccurrenceConstraints {@code true} to optimize occurrence constraints,
     *                                       {@code false} to preserve them
     */
    public void setOptimizeOccurrenceConstraints(boolean optimizeOccurrenceConstraints) {
        this.optimizeOccurrenceConstraints = optimizeOccurrenceConstraints;
    }

    /**
     * Returns the maximum number of type variations allowed.
     *
     * <p>When element variations exceed this limit, a more general type is used.
     *
     * @return the maximum number of type variations
     */
    public int getMaxTypeVariations() {
        return maxTypeVariations;
    }

    /**
     * Sets the maximum number of type variations allowed.
     *
     * @param maxTypeVariations the maximum variations; must be at least 1
     */
    public void setMaxTypeVariations(int maxTypeVariations) {
        this.maxTypeVariations = maxTypeVariations;
    }

    /**
     * Returns whether documentation generation is enabled.
     *
     * <p>When enabled, xs:documentation elements are generated
     * with descriptions of schema components.
     *
     * @return {@code true} if documentation is generated, {@code false} otherwise
     */
    public boolean isGenerateDocumentation() {
        return generateDocumentation;
    }

    /**
     * Sets whether documentation generation is enabled.
     *
     * @param generateDocumentation {@code true} to generate documentation,
     *                               {@code false} to omit it
     */
    public void setGenerateDocumentation(boolean generateDocumentation) {
        this.generateDocumentation = generateDocumentation;
    }

    /**
     * Returns whether element documentation should be included.
     *
     * <p>When enabled, documentation is generated for element declarations.
     *
     * @return {@code true} if element documentation is included, {@code false} otherwise
     */
    public boolean isIncludeElementDocumentation() {
        return includeElementDocumentation;
    }

    /**
     * Sets whether element documentation should be included.
     *
     * @param includeElementDocumentation {@code true} to include element documentation,
     *                                     {@code false} to omit it
     */
    public void setIncludeElementDocumentation(boolean includeElementDocumentation) {
        this.includeElementDocumentation = includeElementDocumentation;
    }

    /**
     * Returns whether attribute documentation should be included.
     *
     * <p>When enabled, documentation is generated for attribute declarations.
     *
     * @return {@code true} if attribute documentation is included, {@code false} otherwise
     */
    public boolean isIncludeAttributeDocumentation() {
        return includeAttributeDocumentation;
    }

    /**
     * Sets whether attribute documentation should be included.
     *
     * @param includeAttributeDocumentation {@code true} to include attribute documentation,
     *                                       {@code false} to omit it
     */
    public void setIncludeAttributeDocumentation(boolean includeAttributeDocumentation) {
        this.includeAttributeDocumentation = includeAttributeDocumentation;
    }

    /**
     * Returns whether type documentation should be included.
     *
     * <p>When enabled, documentation is generated for type definitions.
     *
     * @return {@code true} if type documentation is included, {@code false} otherwise
     */
    public boolean isIncludeTypeDocumentation() {
        return includeTypeDocumentation;
    }

    /**
     * Sets whether type documentation should be included.
     *
     * @param includeTypeDocumentation {@code true} to include type documentation,
     *                                  {@code false} to omit it
     */
    public void setIncludeTypeDocumentation(boolean includeTypeDocumentation) {
        this.includeTypeDocumentation = includeTypeDocumentation;
    }

    /**
     * Returns whether example values should be included in documentation.
     *
     * <p>When enabled, sample values from the source XML are included
     * in documentation comments.
     *
     * @return {@code true} if example values are included, {@code false} otherwise
     */
    public boolean isIncludeExampleValues() {
        return includeExampleValues;
    }

    /**
     * Sets whether example values should be included in documentation.
     *
     * @param includeExampleValues {@code true} to include example values,
     *                              {@code false} to omit them
     */
    public void setIncludeExampleValues(boolean includeExampleValues) {
        this.includeExampleValues = includeExampleValues;
    }

    /**
     * Returns the maximum number of example values to include.
     *
     * @return the maximum number of example values
     */
    public int getMaxExampleValues() {
        return maxExampleValues;
    }

    /**
     * Sets the maximum number of example values to include.
     *
     * @param maxExampleValues the maximum number; must not be negative
     */
    public void setMaxExampleValues(int maxExampleValues) {
        this.maxExampleValues = maxExampleValues;
    }

    /**
     * Returns whether human-readable names should be generated.
     *
     * <p>When enabled, type and group names are formatted
     * for readability (e.g., "CustomerAddressType" instead of "customeraddress").
     *
     * @return {@code true} if human-readable names are generated, {@code false} otherwise
     */
    public boolean isGenerateHumanReadableNames() {
        return generateHumanReadableNames;
    }

    /**
     * Sets whether human-readable names should be generated.
     *
     * @param generateHumanReadableNames {@code true} to generate readable names,
     *                                    {@code false} to use simple names
     */
    public void setGenerateHumanReadableNames(boolean generateHumanReadableNames) {
        this.generateHumanReadableNames = generateHumanReadableNames;
    }

    /**
     * Returns whether schema validation attributes should be added.
     *
     * <p>When enabled, validation-related attributes are included in the schema.
     *
     * @return {@code true} if schema validation is added, {@code false} otherwise
     */
    public boolean isAddSchemaValidation() {
        return addSchemaValidation;
    }

    /**
     * Sets whether schema validation attributes should be added.
     *
     * @param addSchemaValidation {@code true} to add validation attributes,
     *                             {@code false} to omit them
     */
    public void setAddSchemaValidation(boolean addSchemaValidation) {
        this.addSchemaValidation = addSchemaValidation;
    }

    /**
     * Returns the XSD schema version to generate.
     *
     * @return the schema version (e.g., "1.0" or "1.1")
     */
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Sets the XSD schema version to generate.
     *
     * @param schemaVersion the schema version (e.g., "1.0" or "1.1")
     */
    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /**
     * Returns whether a schema location hint should be added.
     *
     * <p>When enabled, schemaLocation attributes are added to facilitate validation.
     *
     * @return {@code true} if schema location is added, {@code false} otherwise
     */
    public boolean isAddSchemaLocation() {
        return addSchemaLocation;
    }

    /**
     * Sets whether a schema location hint should be added.
     *
     * @param addSchemaLocation {@code true} to add schema location,
     *                           {@code false} to omit it
     */
    public void setAddSchemaLocation(boolean addSchemaLocation) {
        this.addSchemaLocation = addSchemaLocation;
    }

    /**
     * Returns the URI to use for schema location hints.
     *
     * @return the schema location URI, or {@code null} if not specified
     */
    public String getSchemaLocationUri() {
        return schemaLocationUri;
    }

    /**
     * Sets the URI to use for schema location hints.
     *
     * @param schemaLocationUri the schema location URI, or {@code null}
     */
    public void setSchemaLocationUri(String schemaLocationUri) {
        this.schemaLocationUri = schemaLocationUri;
    }

    /**
     * Returns whether the output should be formatted.
     *
     * <p>When enabled, the generated schema is pretty-printed with indentation.
     *
     * @return {@code true} if output is formatted, {@code false} otherwise
     */
    public boolean isFormatOutput() {
        return formatOutput;
    }

    /**
     * Sets whether the output should be formatted.
     *
     * @param formatOutput {@code true} to format output, {@code false} for compact output
     */
    public void setFormatOutput(boolean formatOutput) {
        this.formatOutput = formatOutput;
    }

    /**
     * Returns the indentation string used for formatting.
     *
     * @return the indentation string (e.g., "  " or "\t")
     */
    public String getIndentation() {
        return indentation;
    }

    /**
     * Sets the indentation string used for formatting.
     *
     * @param indentation the indentation string; must not be null or empty
     */
    public void setIndentation(String indentation) {
        this.indentation = indentation;
    }

    /**
     * Returns whether comments should be added to the output.
     *
     * <p>When enabled, helpful XML comments are included in the generated schema.
     *
     * @return {@code true} if comments are added, {@code false} otherwise
     */
    public boolean isAddComments() {
        return addComments;
    }

    /**
     * Sets whether comments should be added to the output.
     *
     * @param addComments {@code true} to add comments, {@code false} to omit them
     */
    public void setAddComments(boolean addComments) {
        this.addComments = addComments;
    }

    /**
     * Returns whether a timestamp should be added to the output.
     *
     * <p>When enabled, a generation timestamp is included in the schema header.
     *
     * @return {@code true} if timestamp is added, {@code false} otherwise
     */
    public boolean isAddTimestamp() {
        return addTimestamp;
    }

    /**
     * Sets whether a timestamp should be added to the output.
     *
     * @param addTimestamp {@code true} to add timestamp, {@code false} to omit it
     */
    public void setAddTimestamp(boolean addTimestamp) {
        this.addTimestamp = addTimestamp;
    }

    /**
     * Returns whether generator information should be included.
     *
     * <p>When enabled, information about the schema generator is included
     * in the output comments.
     *
     * @return {@code true} if generator info is included, {@code false} otherwise
     */
    public boolean isIncludeGeneratorInfo() {
        return includeGeneratorInfo;
    }

    /**
     * Sets whether generator information should be included.
     *
     * @param includeGeneratorInfo {@code true} to include generator info,
     *                              {@code false} to omit it
     */
    public void setIncludeGeneratorInfo(boolean includeGeneratorInfo) {
        this.includeGeneratorInfo = includeGeneratorInfo;
    }

    /**
     * Returns whether elements should be sorted alphabetically.
     *
     * <p>When enabled, element declarations are sorted by name.
     *
     * @return {@code true} if elements are sorted, {@code false} otherwise
     */
    public boolean isSortElements() {
        return sortElements;
    }

    /**
     * Sets whether elements should be sorted alphabetically.
     *
     * @param sortElements {@code true} to sort elements, {@code false} to preserve order
     */
    public void setSortElements(boolean sortElements) {
        this.sortElements = sortElements;
    }

    /**
     * Returns whether attributes should be sorted alphabetically.
     *
     * <p>When enabled, attribute declarations are sorted by name.
     *
     * @return {@code true} if attributes are sorted, {@code false} otherwise
     */
    public boolean isSortAttributes() {
        return sortAttributes;
    }

    /**
     * Sets whether attributes should be sorted alphabetically.
     *
     * @param sortAttributes {@code true} to sort attributes, {@code false} to preserve order
     */
    public void setSortAttributes(boolean sortAttributes) {
        this.sortAttributes = sortAttributes;
    }

    /**
     * Returns the custom type mapping from patterns to XSD types.
     *
     * <p>Keys are regex patterns, values are XSD type names.
     *
     * @return the custom type mapping
     */
    public Map<String, String> getCustomTypeMapping() {
        return customTypeMapping;
    }

    /**
     * Sets the custom type mapping from patterns to XSD types.
     *
     * @param customTypeMapping the custom type mapping
     */
    public void setCustomTypeMapping(Map<String, String> customTypeMapping) {
        this.customTypeMapping = customTypeMapping;
    }

    /**
     * Returns the list of element names to ignore during generation.
     *
     * <p>Elements matching these names are excluded from the schema.
     *
     * @return the list of ignored element names
     */
    public List<String> getIgnoredElements() {
        return ignoredElements;
    }

    /**
     * Sets the list of element names to ignore during generation.
     *
     * @param ignoredElements the list of ignored element names
     */
    public void setIgnoredElements(List<String> ignoredElements) {
        this.ignoredElements = ignoredElements;
    }

    /**
     * Returns the list of attribute names to ignore during generation.
     *
     * <p>Attributes matching these names are excluded from the schema.
     *
     * @return the list of ignored attribute names
     */
    public List<String> getIgnoredAttributes() {
        return ignoredAttributes;
    }

    /**
     * Sets the list of attribute names to ignore during generation.
     *
     * @param ignoredAttributes the list of ignored attribute names
     */
    public void setIgnoredAttributes(List<String> ignoredAttributes) {
        this.ignoredAttributes = ignoredAttributes;
    }

    /**
     * Returns the element rename mapping.
     *
     * <p>Keys are original element names, values are new names to use in the schema.
     *
     * @return the element rename mapping
     */
    public Map<String, String> getElementRenames() {
        return elementRenames;
    }

    /**
     * Sets the element rename mapping.
     *
     * @param elementRenames the element rename mapping
     */
    public void setElementRenames(Map<String, String> elementRenames) {
        this.elementRenames = elementRenames;
    }

    /**
     * Returns the attribute rename mapping.
     *
     * <p>Keys are original attribute names, values are new names to use in the schema.
     *
     * @return the attribute rename mapping
     */
    public Map<String, String> getAttributeRenames() {
        return attributeRenames;
    }

    /**
     * Sets the attribute rename mapping.
     *
     * @param attributeRenames the attribute rename mapping
     */
    public void setAttributeRenames(Map<String, String> attributeRenames) {
        this.attributeRenames = attributeRenames;
    }

    /**
     * Returns whether element order should be preserved.
     *
     * <p>When enabled, element order from the source XML is maintained in sequences.
     *
     * @return {@code true} if element order is preserved, {@code false} otherwise
     */
    public boolean isPreserveElementOrder() {
        return preserveElementOrder;
    }

    /**
     * Sets whether element order should be preserved.
     *
     * @param preserveElementOrder {@code true} to preserve order, {@code false} otherwise
     */
    public void setPreserveElementOrder(boolean preserveElementOrder) {
        this.preserveElementOrder = preserveElementOrder;
    }

    /**
     * Returns whether xs:anyType is allowed for uncertain elements.
     *
     * <p>When enabled, elements with unclear types use xs:anyType.
     *
     * @return {@code true} if anyType is allowed, {@code false} otherwise
     */
    public boolean isAllowAnyType() {
        return allowAnyType;
    }

    /**
     * Sets whether xs:anyType is allowed for uncertain elements.
     *
     * @param allowAnyType {@code true} to allow anyType, {@code false} to require specific types
     */
    public void setAllowAnyType(boolean allowAnyType) {
        this.allowAnyType = allowAnyType;
    }

    /**
     * Returns whether abstract types should be generated.
     *
     * <p>When enabled, base types for inheritance hierarchies are marked abstract.
     *
     * @return {@code true} if abstract types are generated, {@code false} otherwise
     */
    public boolean isGenerateAbstractTypes() {
        return generateAbstractTypes;
    }

    /**
     * Sets whether abstract types should be generated.
     *
     * @param generateAbstractTypes {@code true} to generate abstract types,
     *                               {@code false} otherwise
     */
    public void setGenerateAbstractTypes(boolean generateAbstractTypes) {
        this.generateAbstractTypes = generateAbstractTypes;
    }

    /**
     * Returns whether type extensions are enabled.
     *
     * <p>When enabled, the generator creates type hierarchies using extension.
     *
     * @return {@code true} if extensions are enabled, {@code false} otherwise
     */
    public boolean isEnableExtensions() {
        return enableExtensions;
    }

    /**
     * Sets whether type extensions are enabled.
     *
     * @param enableExtensions {@code true} to enable extensions, {@code false} to disable them
     */
    public void setEnableExtensions(boolean enableExtensions) {
        this.enableExtensions = enableExtensions;
    }

    /**
     * Returns the maximum number of documents to analyze.
     *
     * <p>For cross-document analysis, no more than this many documents are processed.
     *
     * @return the maximum number of documents to analyze
     */
    public int getMaxDocumentsToAnalyze() {
        return maxDocumentsToAnalyze;
    }

    /**
     * Sets the maximum number of documents to analyze.
     *
     * @param maxDocumentsToAnalyze the maximum number of documents
     */
    public void setMaxDocumentsToAnalyze(int maxDocumentsToAnalyze) {
        this.maxDocumentsToAnalyze = maxDocumentsToAnalyze;
    }

    /**
     * Returns the maximum number of elements to process per document.
     *
     * <p>Documents with more elements than this limit are truncated during analysis.
     *
     * @return the maximum elements per document
     */
    public int getMaxElementsPerDocument() {
        return maxElementsPerDocument;
    }

    /**
     * Sets the maximum number of elements to process per document.
     *
     * @param maxElementsPerDocument the maximum elements per document
     */
    public void setMaxElementsPerDocument(int maxElementsPerDocument) {
        this.maxElementsPerDocument = maxElementsPerDocument;
    }

    /**
     * Returns the maximum depth for element analysis.
     *
     * <p>Elements nested deeper than this limit are not fully analyzed.
     *
     * @return the maximum analysis depth
     */
    public int getMaxDepthAnalysis() {
        return maxDepthAnalysis;
    }

    /**
     * Sets the maximum depth for element analysis.
     *
     * @param maxDepthAnalysis the maximum analysis depth
     */
    public void setMaxDepthAnalysis(int maxDepthAnalysis) {
        this.maxDepthAnalysis = maxDepthAnalysis;
    }

    /**
     * Returns whether caching is enabled.
     *
     * <p>When enabled, intermediate analysis results are cached for performance.
     *
     * @return {@code true} if caching is enabled, {@code false} otherwise
     */
    public boolean isEnableCaching() {
        return enableCaching;
    }

    /**
     * Sets whether caching is enabled.
     *
     * @param enableCaching {@code true} to enable caching, {@code false} to disable it
     */
    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }

    /**
     * Returns the cache timeout in milliseconds.
     *
     * <p>Cached data older than this value is considered stale.
     *
     * @return the cache timeout in milliseconds
     */
    public long getCacheTimeoutMillis() {
        return cacheTimeoutMillis;
    }

    /**
     * Sets the cache timeout in milliseconds.
     *
     * @param cacheTimeoutMillis the cache timeout in milliseconds
     */
    public void setCacheTimeoutMillis(long cacheTimeoutMillis) {
        this.cacheTimeoutMillis = cacheTimeoutMillis;
    }
}
