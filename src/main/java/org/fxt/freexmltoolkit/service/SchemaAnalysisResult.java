package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive results from XML schema analysis.
 * Contains discovered elements, types, patterns, and optimization recommendations.
 *
 * <p>This class aggregates all information gathered during the analysis of one or more
 * XML documents for the purpose of generating an XSD schema. It includes:</p>
 * <ul>
 *   <li>Basic analysis metadata (timing, document count, analysis ID)</li>
 *   <li>Schema structure information (elements, attributes, hierarchy)</li>
 *   <li>Namespace analysis results</li>
 *   <li>Type inference results with confidence scores</li>
 *   <li>Pattern detection across elements</li>
 *   <li>Occurrence analysis for cardinality constraints</li>
 *   <li>Constraint and restriction information (enumerations, patterns, length/value bounds)</li>
 *   <li>Quality metrics and recommendations</li>
 * </ul>
 *
 * <p>The analysis workflow typically involves:</p>
 * <ol>
 *   <li>Creating a new SchemaAnalysisResult instance</li>
 *   <li>Adding elements and attributes via {@link #addElement(ElementInfo)} and {@link #addAttribute(AttributeInfo)}</li>
 *   <li>Calling {@link #analyzePatterns()} to detect patterns across elements</li>
 *   <li>Calling {@link #analyzeConstraints()} to collect constraint information</li>
 *   <li>Calling {@link #calculateQualityMetrics()} to compute quality scores and generate recommendations</li>
 *   <li>Using {@link #getAnalysisSummary()} to get a human-readable summary</li>
 * </ol>
 *
 * @see ElementInfo
 * @see AttributeInfo
 * @see SchemaGenerationOptions
 */
public class SchemaAnalysisResult {

    // Basic analysis information
    /**
     * Unique identifier for this analysis session, generated as a random UUID.
     */
    private String analysisId = UUID.randomUUID().toString();

    /**
     * Timestamp when the analysis was performed.
     */
    private LocalDateTime analyzedAt = LocalDateTime.now();

    /**
     * Duration of the analysis in milliseconds.
     */
    private long analysisTimeMs = 0;

    /**
     * Number of XML documents that were analyzed.
     */
    private int documentsAnalyzed = 0;

    /**
     * List of file paths or identifiers for the source documents that were analyzed.
     */
    private List<String> sourceDocuments = new ArrayList<>();

    // Schema structure
    /**
     * Information about the root element discovered in the analyzed documents.
     */
    private ElementInfo rootElement;

    /**
     * Map of all discovered elements, keyed by their qualified name (namespace + local name).
     */
    private Map<String, ElementInfo> allElements = new LinkedHashMap<>();

    /**
     * Map grouping elements by their local name, where multiple elements can share the same name
     * but appear in different contexts.
     */
    private Map<String, Set<ElementInfo>> elementsByName = new HashMap<>();

    /**
     * Map of all discovered attributes, keyed by their qualified name.
     */
    private Map<String, AttributeInfo> allAttributes = new LinkedHashMap<>();

    /**
     * Map representing the parent-child relationships between elements.
     * Keys are parent element names, values are sets of child element names.
     */
    private Map<String, Set<String>> elementHierarchy = new HashMap<>();

    // Namespace analysis
    /**
     * Set of all namespaces discovered in the analyzed documents, preserving insertion order.
     */
    private Set<String> discoveredNamespaces = new LinkedHashSet<>();

    /**
     * Map of namespace URIs to their preferred prefixes.
     */
    private Map<String, String> namespacePrefixes = new HashMap<>();

    /**
     * Recommended target namespace for the generated schema, if determined.
     */
    private String recommendedTargetNamespace;

    /**
     * Flag indicating whether a default (unprefixed) namespace was found.
     */
    private boolean hasDefaultNamespace = false;

    /**
     * Map tracking how many times each namespace is used across all elements and attributes.
     */
    private Map<String, Integer> namespaceUsage = new HashMap<>();

    // Type analysis
    /**
     * Map of element/attribute qualified names to their inferred XSD types.
     */
    private Map<String, String> inferredTypes = new HashMap<>();

    /**
     * Map of element/attribute qualified names to confidence scores (0.0 to 1.0)
     * for the type inference.
     */
    private Map<String, Double> typeConfidences = new HashMap<>();

    /**
     * Set of qualified names for elements identified as complex types (having child elements or attributes).
     */
    private Set<String> complexTypes = new HashSet<>();

    /**
     * Set of qualified names for elements identified as simple types (text-only content).
     */
    private Set<String> simpleTypes = new HashSet<>();

    /**
     * Map grouping elements by their structural type signature for potential type reuse.
     * Keys are generated type names, values are lists of elements sharing that structure.
     */
    private Map<String, List<ElementInfo>> typeGroups = new HashMap<>();

    // Pattern analysis
    /**
     * Map of detected pattern names to their occurrence counts across all elements.
     */
    private Map<String, Integer> detectedPatterns = new HashMap<>();

    /**
     * List of the most commonly occurring patterns, sorted by frequency.
     */
    private List<String> commonPatterns = new ArrayList<>();

    /**
     * Map of pattern names to their confidence scores (0.0 to 1.0).
     */
    private Map<String, Double> patternConfidences = new HashMap<>();

    /**
     * Set of qualified names for elements that can repeat (maxOccurs greater than 1 or unbounded).
     */
    private Set<String> repeatingElements = new HashSet<>();

    /**
     * Set of qualified names for elements that appear to be optional based on occurrence analysis.
     */
    private Set<String> optionalElements = new HashSet<>();

    // Occurrence analysis
    /**
     * Map of element qualified names to their occurrence statistics across all analyzed documents.
     */
    private Map<String, OccurrenceInfo> occurrencePatterns = new HashMap<>();

    /**
     * Set of qualified names for elements with unbounded maxOccurs.
     */
    private Set<String> unboundedElements = new HashSet<>();

    /**
     * Map of element qualified names to their minOccurs/maxOccurs constraints.
     */
    private Map<String, MinMaxInfo> occurrenceConstraints = new HashMap<>();

    // Constraints and restrictions
    /**
     * Map of element/attribute qualified names to their detected enumeration values.
     */
    private Map<String, List<String>> enumerationValues = new HashMap<>();

    /**
     * Map of element/attribute qualified names to their detected regex pattern restrictions.
     */
    private Map<String, String> patternRestrictions = new HashMap<>();

    /**
     * Map of element/attribute qualified names to their detected length constraints.
     */
    private Map<String, LengthConstraints> lengthConstraints = new HashMap<>();

    /**
     * Map of element/attribute qualified names to their detected value range constraints.
     */
    private Map<String, ValueConstraints> valueConstraints = new HashMap<>();

    // Quality analysis
    /**
     * Overall confidence score for the analysis (0.0 to 1.0), averaged across all type inferences.
     */
    private double overallConfidence = 0.0;

    /**
     * List of warning messages generated during analysis.
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * List of recommendations for improving the generated schema.
     */
    private List<String> recommendations = new ArrayList<>();

    /**
     * List of issues or problems detected during analysis.
     */
    private List<String> issues = new ArrayList<>();

    /**
     * Map of quality metric names to their computed values.
     */
    private Map<String, Double> qualityMetrics = new HashMap<>();

    // Statistics
    /**
     * General statistics map for custom metric storage.
     */
    private Map<String, Integer> statisticsMap = new HashMap<>();

    /**
     * Total count of elements discovered across all documents.
     */
    private int totalElements = 0;

    /**
     * Total count of attributes discovered across all documents.
     */
    private int totalAttributes = 0;

    /**
     * Maximum nesting depth found in the document structure.
     */
    private int maxDepth = 0;

    /**
     * Average nesting depth across all elements.
     */
    private int averageDepth = 0;

    /**
     * Computed complexity score based on structure, types, and patterns.
     */
    private double complexityScore = 0.0;

    // Schema generation metadata
    /**
     * Options for schema generation, if provided.
     */
    private SchemaGenerationOptions generationOptions;

    /**
     * Additional metadata from the analysis process.
     */
    private Map<String, Object> analysisMetadata = new HashMap<>();

    /**
     * Ordered list of processing steps performed during analysis.
     */
    private List<String> processingSteps = new ArrayList<>();

    /**
     * Constructs a new SchemaAnalysisResult with default values.
     * The analysis ID is generated as a random UUID and the timestamp is set to the current time.
     */
    public SchemaAnalysisResult() {
        // Initialize with current timestamp
        this.analyzedAt = LocalDateTime.now();
    }

    // ========== Element Management ==========

    /**
     * Adds an element to the analysis result and updates related statistics.
     *
     * <p>This method performs the following operations:</p>
     * <ul>
     *   <li>Stores the element in the allElements map by qualified name</li>
     *   <li>Groups the element by name in elementsByName</li>
     *   <li>Updates element count and max depth statistics</li>
     *   <li>Tracks namespace usage</li>
     *   <li>Adds all attributes from the element</li>
     *   <li>Classifies the element as complex or simple type</li>
     *   <li>Records type inference information</li>
     * </ul>
     *
     * @param element the element information to add; if null, no action is taken
     */
    public void addElement(ElementInfo element) {
        if (element == null) return;

        String qualifiedName = element.getQualifiedName();
        allElements.put(qualifiedName, element);

        // Group elements by name
        elementsByName.computeIfAbsent(element.getName(), k -> new HashSet<>()).add(element);

        // Update statistics
        totalElements++;
        if (element.getDepth() > maxDepth) {
            maxDepth = element.getDepth();
        }

        // Add to namespace tracking
        if (element.getNamespace() != null && !element.getNamespace().isEmpty()) {
            discoveredNamespaces.add(element.getNamespace());
            namespaceUsage.put(element.getNamespace(),
                    namespaceUsage.getOrDefault(element.getNamespace(), 0) + 1);
        }

        // Add attributes
        for (AttributeInfo attr : element.getAttributes().values()) {
            addAttribute(attr);
        }

        // Track type information
        if (element.isComplexType()) {
            complexTypes.add(qualifiedName);
        } else {
            simpleTypes.add(qualifiedName);
        }

        inferredTypes.put(qualifiedName, element.getInferredType());
        typeConfidences.put(qualifiedName, element.getTypeConfidence());
    }

    /**
     * Adds an attribute to the analysis result and updates related statistics.
     *
     * <p>This method stores the attribute in the allAttributes map, updates the
     * attribute count, tracks namespace usage, and records type inference information.</p>
     *
     * @param attribute the attribute information to add; if null, no action is taken
     */
    public void addAttribute(AttributeInfo attribute) {
        if (attribute == null) return;

        String qualifiedName = attribute.getQualifiedName();
        allAttributes.put(qualifiedName, attribute);
        totalAttributes++;

        // Track namespace usage
        if (attribute.getNamespace() != null && !attribute.getNamespace().isEmpty()) {
            discoveredNamespaces.add(attribute.getNamespace());
            namespaceUsage.put(attribute.getNamespace(),
                    namespaceUsage.getOrDefault(attribute.getNamespace(), 0) + 1);
        }

        // Track type information
        inferredTypes.put(qualifiedName, attribute.getInferredType());
        typeConfidences.put(qualifiedName, attribute.getTypeConfidence());
    }

    // ========== Pattern Analysis Methods ==========

    /**
     * Analyzes patterns across all elements in the result.
     *
     * <p>This method performs comprehensive pattern analysis including:</p>
     * <ul>
     *   <li>Collecting patterns from individual elements</li>
     *   <li>Analyzing element occurrence patterns</li>
     *   <li>Finding commonly occurring patterns</li>
     *   <li>Detecting cross-element patterns for type reuse</li>
     *   <li>Calculating confidence scores for detected patterns</li>
     * </ul>
     *
     * <p>Call this method after all elements have been added to perform pattern analysis.</p>
     */
    public void analyzePatterns() {
        // Reset pattern data
        detectedPatterns.clear();
        commonPatterns.clear();
        patternConfidences.clear();

        // Analyze element patterns
        for (ElementInfo element : allElements.values()) {
            element.analyzePatterns();

            // Collect patterns
            for (String pattern : element.getDetectedPatterns()) {
                detectedPatterns.put(pattern, detectedPatterns.getOrDefault(pattern, 0) + 1);
            }

            // Track occurrence patterns
            analyzeElementOccurrences(element);
        }

        // Find common patterns
        findCommonPatterns();

        // Analyze cross-element patterns
        analyzeCrossElementPatterns();

        // Calculate pattern confidences
        calculatePatternConfidences();
    }

    /**
     * Analyzes occurrence patterns for a single element and updates tracking collections.
     *
     * @param element the element to analyze for occurrence patterns
     */
    private void analyzeElementOccurrences(ElementInfo element) {
        String name = element.getQualifiedName();

        // Track occurrence information
        OccurrenceInfo occInfo = occurrencePatterns.get(name);
        if (occInfo == null) {
            occInfo = new OccurrenceInfo();
            occurrencePatterns.put(name, occInfo);
        }

        occInfo.totalOccurrences += element.getTotalOccurrences();
        occInfo.documentOccurrences += element.getDocumentOccurrences();
        occInfo.minOccurs = Math.min(occInfo.minOccurs, element.getMinOccurs());
        occInfo.maxOccurs = Math.max(occInfo.maxOccurs, element.getMaxOccurs());

        if (element.isUnbounded()) {
            unboundedElements.add(name);
        }

        // Determine if element is optional
        if (element.getOccurrenceConfidence() < 0.8) {
            optionalElements.add(name);
        }

        // Check for repeating elements
        if (element.getMaxOccurs() > 1 || element.isUnbounded()) {
            repeatingElements.add(name);
        }

        // Store min/max constraints
        occurrenceConstraints.put(name, new MinMaxInfo(element.getMinOccurs(),
                element.isUnbounded() ? -1 : element.getMaxOccurs()));
    }

    /**
     * Finds the most common patterns from the detected patterns map.
     * Patterns are sorted by frequency and the top 10 (that appear more than once) are selected.
     */
    private void findCommonPatterns() {
        // Sort patterns by frequency
        List<Map.Entry<String, Integer>> sortedPatterns = new ArrayList<>(detectedPatterns.entrySet());
        sortedPatterns.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Take top patterns
        int maxPatterns = Math.min(10, sortedPatterns.size());
        for (int i = 0; i < maxPatterns; i++) {
            Map.Entry<String, Integer> entry = sortedPatterns.get(i);
            if (entry.getValue() > 1) { // Pattern appears in multiple elements
                commonPatterns.add(entry.getKey());
            }
        }
    }

    /**
     * Analyzes patterns across multiple elements to identify potential type reuse opportunities.
     * Elements with similar structural signatures are grouped together.
     */
    private void analyzeCrossElementPatterns() {
        // Group elements by structural similarity
        Map<String, List<ElementInfo>> structuralGroups = new HashMap<>();

        for (ElementInfo element : allElements.values()) {
            String signature = createStructuralSignature(element);
            structuralGroups.computeIfAbsent(signature, k -> new ArrayList<>()).add(element);
        }

        // Find groups with multiple elements (potential type reuse)
        for (Map.Entry<String, List<ElementInfo>> entry : structuralGroups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String typeName = entry.getKey() + "Type";
                typeGroups.put(typeName, entry.getValue());

                // Add as a detected pattern
                detectedPatterns.put("reusable_type_" + typeName, entry.getValue().size());
            }
        }
    }

    /**
     * Creates a structural signature for an element based on its content type, child elements, and attributes.
     *
     * @param element the element to create a signature for
     * @return a string representing the structural signature of the element
     */
    private String createStructuralSignature(ElementInfo element) {
        StringBuilder signature = new StringBuilder();

        // Add content type
        signature.append(element.getContentType()).append("_");

        // Add child elements (sorted)
        List<String> childNames = new ArrayList<>(element.getChildElements().keySet());
        Collections.sort(childNames);
        signature.append("children[").append(String.join(",", childNames)).append("]_");

        // Add attributes (sorted)
        List<String> attrNames = new ArrayList<>(element.getAttributes().keySet());
        Collections.sort(attrNames);
        signature.append("attrs[").append(String.join(",", attrNames)).append("]");

        return signature.toString();
    }

    /**
     * Calculates confidence scores for each detected pattern based on its prevalence across all elements.
     */
    private void calculatePatternConfidences() {
        int totalElements = allElements.size();

        for (Map.Entry<String, Integer> entry : detectedPatterns.entrySet()) {
            double confidence = (double) entry.getValue() / totalElements;
            patternConfidences.put(entry.getKey(), confidence);
        }
    }

    // ========== Constraint Analysis ==========

    /**
     * Analyzes and collects constraints and restrictions from all elements and attributes.
     *
     * <p>This method extracts the following constraint types:</p>
     * <ul>
     *   <li>Enumeration values - possible values for the element/attribute</li>
     *   <li>Pattern restrictions - regex patterns the values must match</li>
     *   <li>Length constraints - minimum and maximum string lengths</li>
     *   <li>Value constraints - minimum and maximum numeric values</li>
     * </ul>
     *
     * <p>Call this method after all elements have been added to collect constraint information.</p>
     */
    public void analyzeConstraints() {
        for (ElementInfo element : allElements.values()) {
            String name = element.getQualifiedName();

            // Enumeration values
            if (!element.getEnumerationValues().isEmpty()) {
                enumerationValues.put(name, new ArrayList<>(element.getEnumerationValues()));
            }

            // Pattern restrictions
            if (element.getRestrictionPattern() != null) {
                patternRestrictions.put(name, element.getRestrictionPattern());
            }

            // Length constraints
            if (element.getMinLength() != null || element.getMaxLength() != null) {
                lengthConstraints.put(name, new LengthConstraints(
                        element.getMinLength(), element.getMaxLength()));
            }

            // Value constraints
            if (element.getMinValue() != null || element.getMaxValue() != null) {
                valueConstraints.put(name, new ValueConstraints(
                        element.getMinValue(), element.getMaxValue()));
            }
        }

        // Analyze attribute constraints
        for (AttributeInfo attribute : allAttributes.values()) {
            String name = attribute.getQualifiedName();

            // Enumeration values
            if (!attribute.getEnumerationValues().isEmpty()) {
                enumerationValues.put(name, new ArrayList<>(attribute.getEnumerationValues()));
            }

            // Pattern restrictions
            if (attribute.getRestrictionPattern() != null) {
                patternRestrictions.put(name, attribute.getRestrictionPattern());
            }

            // Length constraints
            if (attribute.getMinLength() != null || attribute.getMaxLength() != null) {
                lengthConstraints.put(name, new LengthConstraints(
                        attribute.getMinLength(), attribute.getMaxLength()));
            }

            // Value constraints
            if (attribute.getMinValue() != null || attribute.getMaxValue() != null) {
                valueConstraints.put(name, new ValueConstraints(
                        attribute.getMinValue(), attribute.getMaxValue()));
            }
        }
    }

    // ========== Quality Analysis ==========

    /**
     * Calculates overall quality metrics and confidence for the analysis.
     *
     * <p>This method performs the following:</p>
     * <ul>
     *   <li>Computes average confidence across all type inferences</li>
     *   <li>Calculates the complexity score</li>
     *   <li>Generates warnings for potential issues</li>
     *   <li>Generates recommendations for schema improvement</li>
     *   <li>Stores various quality metrics (namespace consistency, type reusability, structural consistency)</li>
     * </ul>
     *
     * <p>Call this method after pattern and constraint analysis to compute quality metrics.</p>
     */
    public void calculateQualityMetrics() {
        // Calculate average confidence
        double totalConfidence = 0.0;
        int confidenceCount = 0;

        for (double confidence : typeConfidences.values()) {
            totalConfidence += confidence;
            confidenceCount++;
        }

        overallConfidence = confidenceCount > 0 ? totalConfidence / confidenceCount : 0.0;

        // Calculate complexity score
        calculateComplexityScore();

        // Generate warnings and recommendations
        generateWarnings();
        generateRecommendations();

        // Store quality metrics
        qualityMetrics.put("overallConfidence", overallConfidence);
        qualityMetrics.put("complexityScore", complexityScore);
        qualityMetrics.put("namespaceConsistency", calculateNamespaceConsistency());
        qualityMetrics.put("typeReusability", calculateTypeReusability());
        qualityMetrics.put("structuralConsistency", calculateStructuralConsistency());
    }

    /**
     * Calculates the average complexity score across all elements.
     */
    private void calculateComplexityScore() {
        complexityScore = 0.0;

        for (ElementInfo element : allElements.values()) {
            complexityScore += element.getComplexityScore();
        }

        // Average complexity per element
        if (totalElements > 0) {
            complexityScore /= totalElements;
        }
    }

    /**
     * Calculates namespace consistency score based on how consistently namespaces are used.
     *
     * @return a score from 0.0 to 1.0 indicating namespace consistency
     */
    private double calculateNamespaceConsistency() {
        if (discoveredNamespaces.size() <= 1) return 1.0;

        // Check if namespaces are used consistently
        int consistentUsage = 0;
        for (String namespace : discoveredNamespaces) {
            int usage = namespaceUsage.getOrDefault(namespace, 0);
            if (usage > 1) consistentUsage++;
        }

        return (double) consistentUsage / discoveredNamespaces.size();
    }

    /**
     * Calculates type reusability score based on how many type groups have multiple elements.
     *
     * @return a score from 0.0 to 1.0 indicating type reusability potential
     */
    private double calculateTypeReusability() {
        if (typeGroups.isEmpty()) return 0.0;

        int reusableTypes = 0;
        for (List<ElementInfo> group : typeGroups.values()) {
            if (group.size() > 1) reusableTypes++;
        }

        return (double) reusableTypes / Math.max(1, complexTypes.size());
    }

    /**
     * Calculates structural consistency by checking if elements with the same name have consistent structures.
     *
     * @return a score from 0.0 to 1.0 indicating structural consistency
     */
    private double calculateStructuralConsistency() {
        // Analyze if similar elements have similar structures
        Map<String, Set<String>> elementStructures = new HashMap<>();

        for (ElementInfo element : allElements.values()) {
            String baseName = element.getName();
            String structure = createStructuralSignature(element);
            elementStructures.computeIfAbsent(baseName, k -> new HashSet<>()).add(structure);
        }

        int consistentElements = 0;
        for (Set<String> structures : elementStructures.values()) {
            if (structures.size() == 1) consistentElements++;
        }

        return totalElements > 0 ? (double) consistentElements / totalElements : 1.0;
    }

    /**
     * Generates warning messages based on the analysis results.
     * Warnings include low confidence types, excessive complexity, many namespaces, and low overall confidence.
     */
    private void generateWarnings() {
        warnings.clear();

        // Check for low confidence types
        for (Map.Entry<String, Double> entry : typeConfidences.entrySet()) {
            if (entry.getValue() < 0.5) {
                warnings.add("Low confidence type inference for '" + entry.getKey() +
                        "': " + String.format("%.2f", entry.getValue()));
            }
        }

        // Check for excessive complexity
        if (complexityScore > 10.0) {
            warnings.add("High schema complexity detected: " + String.format("%.2f", complexityScore));
        }

        // Check for inconsistent namespaces
        if (discoveredNamespaces.size() > 5) {
            warnings.add("Many namespaces detected (" + discoveredNamespaces.size() +
                    ") - consider consolidation");
        }

        // Check for potential data quality issues
        if (overallConfidence < 0.7) {
            warnings.add("Low overall analysis confidence: " + String.format("%.2f", overallConfidence));
        }
    }

    /**
     * Generates recommendations for improving the generated schema based on analysis results.
     * Recommendations include type reuse, namespace organization, and occurrence constraints.
     */
    private void generateRecommendations() {
        recommendations.clear();

        // Type reuse recommendations
        for (Map.Entry<String, List<ElementInfo>> entry : typeGroups.entrySet()) {
            if (entry.getValue().size() > 2) {
                recommendations.add("Consider creating reusable complex type '" + entry.getKey() +
                        "' for " + entry.getValue().size() + " similar elements");
            }
        }

        // Namespace recommendations
        if (discoveredNamespaces.size() > 1 && recommendedTargetNamespace == null) {
            recommendations.add("Consider defining a target namespace for better schema organization");
        }

        // Occurrence pattern recommendations
        if (unboundedElements.size() > totalElements * 0.3) {
            recommendations.add("Many unbounded elements detected - consider adding maxOccurs constraints for better validation");
        }

        // Documentation recommendations
        if (generationOptions != null && generationOptions.isGenerateDocumentation()) {
            recommendations.add("Generate comprehensive documentation for better schema maintainability");
        }
    }

    // ========== Summary Methods ==========

    /**
     * Generates a human-readable summary of the analysis results.
     *
     * <p>The summary includes:</p>
     * <ul>
     *   <li>Structure overview (document count, element/attribute counts, depth, namespaces)</li>
     *   <li>Type analysis (complex/simple type counts, confidence, complexity)</li>
     *   <li>Pattern analysis (detected patterns, common patterns, optional/repeating elements)</li>
     *   <li>Quality metrics (warnings, recommendations, analysis time)</li>
     * </ul>
     *
     * @return a formatted string containing the analysis summary
     */
    public String getAnalysisSummary() {

        String summary = "XML Schema Analysis Results\n" +
                "============================\n\n" +

                // Basic statistics
                "Structure Overview:\n" +
                String.format("  Documents Analyzed: %d\n", documentsAnalyzed) +
                String.format("  Total Elements: %d\n", totalElements) +
                String.format("  Total Attributes: %d\n", totalAttributes) +
                String.format("  Max Depth: %d\n", maxDepth) +
                String.format("  Namespaces: %d\n", discoveredNamespaces.size()) +

                // Type analysis
                "\nType Analysis:\n" +
                String.format("  Complex Types: %d\n", complexTypes.size()) +
                String.format("  Simple Types: %d\n", simpleTypes.size()) +
                String.format("  Overall Confidence: %.2f\n", overallConfidence) +
                String.format("  Complexity Score: %.2f\n", complexityScore) +

                // Pattern analysis
                "\nPattern Analysis:\n" +
                String.format("  Detected Patterns: %d\n", detectedPatterns.size()) +
                String.format("  Common Patterns: %d\n", commonPatterns.size()) +
                String.format("  Optional Elements: %d\n", optionalElements.size()) +
                String.format("  Repeating Elements: %d\n", repeatingElements.size()) +

                // Quality metrics
                "\nQuality Metrics:\n" +
                String.format("  Warnings: %d\n", warnings.size()) +
                String.format("  Recommendations: %d\n", recommendations.size()) +
                String.format("  Analysis Time: %d ms\n", analysisTimeMs);

        return summary;
    }

    // ========== Inner Classes ==========

    /**
     * Holds occurrence statistics for an element across analyzed documents.
     *
     * <p>This class tracks how many times an element occurs in total,
     * in how many documents it appears, and the observed min/max occurrence counts.</p>
     */
    public static class OccurrenceInfo {
        /**
         * Creates a new OccurrenceInfo instance with default values.
         *
         * <p>Initializes all counters to zero except minOccurs which is set to
         * Integer.MAX_VALUE so the first real value will be smaller.</p>
         */
        public OccurrenceInfo() {
            // Default constructor with field initializers
        }

        /**
         * Total number of occurrences across all documents.
         */
        public int totalOccurrences = 0;

        /**
         * Number of documents in which this element appeared.
         */
        public int documentOccurrences = 0;

        /**
         * Minimum number of occurrences observed in any single parent context.
         * Initialized to MAX_VALUE so the first real value will be smaller.
         */
        public int minOccurs = Integer.MAX_VALUE;

        /**
         * Maximum number of occurrences observed in any single parent context.
         */
        public int maxOccurs = 0;
    }

    /**
     * Record holding minimum and maximum occurrence constraints for an element.
     *
     * @param min the minimum number of occurrences (minOccurs constraint)
     * @param max the maximum number of occurrences (maxOccurs constraint), or -1 for unbounded
     */
    public record MinMaxInfo(int min, int max) {
    }

    /**
     * Record holding length constraints for string values.
     *
     * @param minLength the minimum string length, or null if no minimum
     * @param maxLength the maximum string length, or null if no maximum
     */
    public record LengthConstraints(Integer minLength, Integer maxLength) {
    }

    /**
     * Record holding value constraints for numeric or comparable values.
     *
     * @param minValue the minimum value as a string representation, or null if no minimum
     * @param maxValue the maximum value as a string representation, or null if no maximum
     */
    public record ValueConstraints(String minValue, String maxValue) {
    }

    // ========== Getters and Setters ==========

    /**
     * Returns the unique identifier for this analysis session.
     *
     * @return the analysis ID as a UUID string
     */
    public String getAnalysisId() {
        return analysisId;
    }

    /**
     * Sets the unique identifier for this analysis session.
     *
     * @param analysisId the analysis ID to set
     */
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    /**
     * Returns the timestamp when the analysis was performed.
     *
     * @return the analysis timestamp
     */
    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    /**
     * Sets the timestamp when the analysis was performed.
     *
     * @param analyzedAt the timestamp to set
     */
    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    /**
     * Returns the duration of the analysis in milliseconds.
     *
     * @return the analysis duration in milliseconds
     */
    public long getAnalysisTimeMs() {
        return analysisTimeMs;
    }

    /**
     * Sets the duration of the analysis in milliseconds.
     *
     * @param analysisTimeMs the analysis duration to set
     */
    public void setAnalysisTimeMs(long analysisTimeMs) {
        this.analysisTimeMs = analysisTimeMs;
    }

    /**
     * Returns the number of XML documents that were analyzed.
     *
     * @return the count of analyzed documents
     */
    public int getDocumentsAnalyzed() {
        return documentsAnalyzed;
    }

    /**
     * Sets the number of XML documents that were analyzed.
     *
     * @param documentsAnalyzed the document count to set
     */
    public void setDocumentsAnalyzed(int documentsAnalyzed) {
        this.documentsAnalyzed = documentsAnalyzed;
    }

    /**
     * Returns the list of source document paths or identifiers.
     *
     * @return the list of source documents
     */
    public List<String> getSourceDocuments() {
        return sourceDocuments;
    }

    /**
     * Sets the list of source document paths or identifiers.
     *
     * @param sourceDocuments the source documents list to set
     */
    public void setSourceDocuments(List<String> sourceDocuments) {
        this.sourceDocuments = sourceDocuments;
    }

    /**
     * Returns information about the root element.
     *
     * @return the root element information, or null if not set
     */
    public ElementInfo getRootElement() {
        return rootElement;
    }

    /**
     * Sets the root element information.
     *
     * @param rootElement the root element to set
     */
    public void setRootElement(ElementInfo rootElement) {
        this.rootElement = rootElement;
    }

    /**
     * Returns the map of all discovered elements keyed by qualified name.
     *
     * @return the map of all elements
     */
    public Map<String, ElementInfo> getAllElements() {
        return allElements;
    }

    /**
     * Sets the map of all discovered elements.
     *
     * @param allElements the elements map to set
     */
    public void setAllElements(Map<String, ElementInfo> allElements) {
        this.allElements = allElements;
    }

    /**
     * Returns the map of elements grouped by their local name.
     *
     * @return the map of elements by name
     */
    public Map<String, Set<ElementInfo>> getElementsByName() {
        return elementsByName;
    }

    /**
     * Sets the map of elements grouped by their local name.
     *
     * @param elementsByName the elements by name map to set
     */
    public void setElementsByName(Map<String, Set<ElementInfo>> elementsByName) {
        this.elementsByName = elementsByName;
    }

    /**
     * Returns the map of all discovered attributes keyed by qualified name.
     *
     * @return the map of all attributes
     */
    public Map<String, AttributeInfo> getAllAttributes() {
        return allAttributes;
    }

    /**
     * Sets the map of all discovered attributes.
     *
     * @param allAttributes the attributes map to set
     */
    public void setAllAttributes(Map<String, AttributeInfo> allAttributes) {
        this.allAttributes = allAttributes;
    }

    /**
     * Returns the map representing parent-child element relationships.
     *
     * @return the element hierarchy map
     */
    public Map<String, Set<String>> getElementHierarchy() {
        return elementHierarchy;
    }

    /**
     * Sets the map representing parent-child element relationships.
     *
     * @param elementHierarchy the hierarchy map to set
     */
    public void setElementHierarchy(Map<String, Set<String>> elementHierarchy) {
        this.elementHierarchy = elementHierarchy;
    }

    /**
     * Returns the set of discovered namespace URIs.
     *
     * @return the set of namespaces
     */
    public Set<String> getDiscoveredNamespaces() {
        return discoveredNamespaces;
    }

    /**
     * Sets the set of discovered namespace URIs.
     *
     * @param discoveredNamespaces the namespaces set to set
     */
    public void setDiscoveredNamespaces(Set<String> discoveredNamespaces) {
        this.discoveredNamespaces = discoveredNamespaces;
    }

    /**
     * Returns the map of namespace URIs to their preferred prefixes.
     *
     * @return the namespace prefixes map
     */
    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    /**
     * Sets the map of namespace URIs to their preferred prefixes.
     *
     * @param namespacePrefixes the prefixes map to set
     */
    public void setNamespacePrefixes(Map<String, String> namespacePrefixes) {
        this.namespacePrefixes = namespacePrefixes;
    }

    /**
     * Returns the recommended target namespace for the generated schema.
     *
     * @return the recommended target namespace, or null if not determined
     */
    public String getRecommendedTargetNamespace() {
        return recommendedTargetNamespace;
    }

    /**
     * Sets the recommended target namespace for the generated schema.
     *
     * @param recommendedTargetNamespace the namespace to set
     */
    public void setRecommendedTargetNamespace(String recommendedTargetNamespace) {
        this.recommendedTargetNamespace = recommendedTargetNamespace;
    }

    /**
     * Returns whether a default (unprefixed) namespace was found.
     *
     * @return true if a default namespace exists, false otherwise
     */
    public boolean isHasDefaultNamespace() {
        return hasDefaultNamespace;
    }

    /**
     * Sets whether a default (unprefixed) namespace was found.
     *
     * @param hasDefaultNamespace the flag to set
     */
    public void setHasDefaultNamespace(boolean hasDefaultNamespace) {
        this.hasDefaultNamespace = hasDefaultNamespace;
    }

    /**
     * Returns the map of namespace usage counts.
     *
     * @return the namespace usage map
     */
    public Map<String, Integer> getNamespaceUsage() {
        return namespaceUsage;
    }

    /**
     * Sets the map of namespace usage counts.
     *
     * @param namespaceUsage the usage map to set
     */
    public void setNamespaceUsage(Map<String, Integer> namespaceUsage) {
        this.namespaceUsage = namespaceUsage;
    }

    /**
     * Returns the map of inferred XSD types for elements and attributes.
     *
     * @return the inferred types map
     */
    public Map<String, String> getInferredTypes() {
        return inferredTypes;
    }

    /**
     * Sets the map of inferred XSD types.
     *
     * @param inferredTypes the types map to set
     */
    public void setInferredTypes(Map<String, String> inferredTypes) {
        this.inferredTypes = inferredTypes;
    }

    /**
     * Returns the map of type inference confidence scores.
     *
     * @return the type confidences map
     */
    public Map<String, Double> getTypeConfidences() {
        return typeConfidences;
    }

    /**
     * Sets the map of type inference confidence scores.
     *
     * @param typeConfidences the confidences map to set
     */
    public void setTypeConfidences(Map<String, Double> typeConfidences) {
        this.typeConfidences = typeConfidences;
    }

    /**
     * Returns the set of elements identified as complex types.
     *
     * @return the complex types set
     */
    public Set<String> getComplexTypes() {
        return complexTypes;
    }

    /**
     * Sets the set of elements identified as complex types.
     *
     * @param complexTypes the complex types set to set
     */
    public void setComplexTypes(Set<String> complexTypes) {
        this.complexTypes = complexTypes;
    }

    /**
     * Returns the set of elements identified as simple types.
     *
     * @return the simple types set
     */
    public Set<String> getSimpleTypes() {
        return simpleTypes;
    }

    /**
     * Sets the set of elements identified as simple types.
     *
     * @param simpleTypes the simple types set to set
     */
    public void setSimpleTypes(Set<String> simpleTypes) {
        this.simpleTypes = simpleTypes;
    }

    /**
     * Returns the map of type groups for potential type reuse.
     *
     * @return the type groups map
     */
    public Map<String, List<ElementInfo>> getTypeGroups() {
        return typeGroups;
    }

    /**
     * Sets the map of type groups for potential type reuse.
     *
     * @param typeGroups the type groups map to set
     */
    public void setTypeGroups(Map<String, List<ElementInfo>> typeGroups) {
        this.typeGroups = typeGroups;
    }

    /**
     * Returns the map of detected patterns and their occurrence counts.
     *
     * @return the detected patterns map
     */
    public Map<String, Integer> getDetectedPatterns() {
        return detectedPatterns;
    }

    /**
     * Sets the map of detected patterns and their occurrence counts.
     *
     * @param detectedPatterns the patterns map to set
     */
    public void setDetectedPatterns(Map<String, Integer> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    /**
     * Returns the list of commonly occurring patterns.
     *
     * @return the common patterns list
     */
    public List<String> getCommonPatterns() {
        return commonPatterns;
    }

    /**
     * Sets the list of commonly occurring patterns.
     *
     * @param commonPatterns the patterns list to set
     */
    public void setCommonPatterns(List<String> commonPatterns) {
        this.commonPatterns = commonPatterns;
    }

    /**
     * Returns the map of pattern confidence scores.
     *
     * @return the pattern confidences map
     */
    public Map<String, Double> getPatternConfidences() {
        return patternConfidences;
    }

    /**
     * Sets the map of pattern confidence scores.
     *
     * @param patternConfidences the confidences map to set
     */
    public void setPatternConfidences(Map<String, Double> patternConfidences) {
        this.patternConfidences = patternConfidences;
    }

    /**
     * Returns the set of elements that can repeat.
     *
     * @return the repeating elements set
     */
    public Set<String> getRepeatingElements() {
        return repeatingElements;
    }

    /**
     * Sets the set of elements that can repeat.
     *
     * @param repeatingElements the repeating elements set to set
     */
    public void setRepeatingElements(Set<String> repeatingElements) {
        this.repeatingElements = repeatingElements;
    }

    /**
     * Returns the set of optional elements.
     *
     * @return the optional elements set
     */
    public Set<String> getOptionalElements() {
        return optionalElements;
    }

    /**
     * Sets the set of optional elements.
     *
     * @param optionalElements the optional elements set to set
     */
    public void setOptionalElements(Set<String> optionalElements) {
        this.optionalElements = optionalElements;
    }

    /**
     * Returns the map of occurrence patterns for elements.
     *
     * @return the occurrence patterns map
     */
    public Map<String, OccurrenceInfo> getOccurrencePatterns() {
        return occurrencePatterns;
    }

    /**
     * Sets the map of occurrence patterns for elements.
     *
     * @param occurrencePatterns the occurrence patterns map to set
     */
    public void setOccurrencePatterns(Map<String, OccurrenceInfo> occurrencePatterns) {
        this.occurrencePatterns = occurrencePatterns;
    }

    /**
     * Returns the set of elements with unbounded maxOccurs.
     *
     * @return the unbounded elements set
     */
    public Set<String> getUnboundedElements() {
        return unboundedElements;
    }

    /**
     * Sets the set of elements with unbounded maxOccurs.
     *
     * @param unboundedElements the unbounded elements set to set
     */
    public void setUnboundedElements(Set<String> unboundedElements) {
        this.unboundedElements = unboundedElements;
    }

    /**
     * Returns the map of occurrence constraints (minOccurs/maxOccurs).
     *
     * @return the occurrence constraints map
     */
    public Map<String, MinMaxInfo> getOccurrenceConstraints() {
        return occurrenceConstraints;
    }

    /**
     * Sets the map of occurrence constraints (minOccurs/maxOccurs).
     *
     * @param occurrenceConstraints the constraints map to set
     */
    public void setOccurrenceConstraints(Map<String, MinMaxInfo> occurrenceConstraints) {
        this.occurrenceConstraints = occurrenceConstraints;
    }

    /**
     * Returns the map of enumeration values for elements and attributes.
     *
     * @return the enumeration values map
     */
    public Map<String, List<String>> getEnumerationValues() {
        return enumerationValues;
    }

    /**
     * Sets the map of enumeration values for elements and attributes.
     *
     * @param enumerationValues the enumeration values map to set
     */
    public void setEnumerationValues(Map<String, List<String>> enumerationValues) {
        this.enumerationValues = enumerationValues;
    }

    /**
     * Returns the map of pattern restrictions (regex patterns).
     *
     * @return the pattern restrictions map
     */
    public Map<String, String> getPatternRestrictions() {
        return patternRestrictions;
    }

    /**
     * Sets the map of pattern restrictions (regex patterns).
     *
     * @param patternRestrictions the restrictions map to set
     */
    public void setPatternRestrictions(Map<String, String> patternRestrictions) {
        this.patternRestrictions = patternRestrictions;
    }

    /**
     * Returns the map of length constraints.
     *
     * @return the length constraints map
     */
    public Map<String, LengthConstraints> getLengthConstraints() {
        return lengthConstraints;
    }

    /**
     * Sets the map of length constraints.
     *
     * @param lengthConstraints the constraints map to set
     */
    public void setLengthConstraints(Map<String, LengthConstraints> lengthConstraints) {
        this.lengthConstraints = lengthConstraints;
    }

    /**
     * Returns the map of value constraints.
     *
     * @return the value constraints map
     */
    public Map<String, ValueConstraints> getValueConstraints() {
        return valueConstraints;
    }

    /**
     * Sets the map of value constraints.
     *
     * @param valueConstraints the constraints map to set
     */
    public void setValueConstraints(Map<String, ValueConstraints> valueConstraints) {
        this.valueConstraints = valueConstraints;
    }

    /**
     * Returns the overall confidence score for the analysis.
     *
     * @return the overall confidence score (0.0 to 1.0)
     */
    public double getOverallConfidence() {
        return overallConfidence;
    }

    /**
     * Sets the overall confidence score for the analysis.
     *
     * @param overallConfidence the confidence score to set
     */
    public void setOverallConfidence(double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    /**
     * Returns the list of warning messages.
     *
     * @return the warnings list
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Sets the list of warning messages.
     *
     * @param warnings the warnings list to set
     */
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    /**
     * Returns the list of recommendations.
     *
     * @return the recommendations list
     */
    public List<String> getRecommendations() {
        return recommendations;
    }

    /**
     * Sets the list of recommendations.
     *
     * @param recommendations the recommendations list to set
     */
    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Returns the list of issues detected during analysis.
     *
     * @return the issues list
     */
    public List<String> getIssues() {
        return issues;
    }

    /**
     * Sets the list of issues detected during analysis.
     *
     * @param issues the issues list to set
     */
    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    /**
     * Returns the map of quality metrics.
     *
     * @return the quality metrics map
     */
    public Map<String, Double> getQualityMetrics() {
        return qualityMetrics;
    }

    /**
     * Sets the map of quality metrics.
     *
     * @param qualityMetrics the metrics map to set
     */
    public void setQualityMetrics(Map<String, Double> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    /**
     * Returns the general statistics map.
     *
     * @return the statistics map
     */
    public Map<String, Integer> getStatisticsMap() {
        return statisticsMap;
    }

    /**
     * Sets the general statistics map.
     *
     * @param statisticsMap the statistics map to set
     */
    public void setStatisticsMap(Map<String, Integer> statisticsMap) {
        this.statisticsMap = statisticsMap;
    }

    /**
     * Returns the total count of elements discovered.
     *
     * @return the total element count
     */
    public int getTotalElements() {
        return totalElements;
    }

    /**
     * Sets the total count of elements discovered.
     *
     * @param totalElements the element count to set
     */
    public void setTotalElements(int totalElements) {
        this.totalElements = totalElements;
    }

    /**
     * Returns the total count of attributes discovered.
     *
     * @return the total attribute count
     */
    public int getTotalAttributes() {
        return totalAttributes;
    }

    /**
     * Sets the total count of attributes discovered.
     *
     * @param totalAttributes the attribute count to set
     */
    public void setTotalAttributes(int totalAttributes) {
        this.totalAttributes = totalAttributes;
    }

    /**
     * Returns the maximum nesting depth found.
     *
     * @return the maximum depth
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Sets the maximum nesting depth.
     *
     * @param maxDepth the depth to set
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * Returns the average nesting depth.
     *
     * @return the average depth
     */
    public int getAverageDepth() {
        return averageDepth;
    }

    /**
     * Sets the average nesting depth.
     *
     * @param averageDepth the depth to set
     */
    public void setAverageDepth(int averageDepth) {
        this.averageDepth = averageDepth;
    }

    /**
     * Returns the computed complexity score.
     *
     * @return the complexity score
     */
    public double getComplexityScore() {
        return complexityScore;
    }

    /**
     * Sets the computed complexity score.
     *
     * @param complexityScore the score to set
     */
    public void setComplexityScore(double complexityScore) {
        this.complexityScore = complexityScore;
    }

    /**
     * Returns the schema generation options.
     *
     * @return the generation options, or null if not set
     */
    public SchemaGenerationOptions getGenerationOptions() {
        return generationOptions;
    }

    /**
     * Sets the schema generation options.
     *
     * @param generationOptions the options to set
     */
    public void setGenerationOptions(SchemaGenerationOptions generationOptions) {
        this.generationOptions = generationOptions;
    }

    /**
     * Returns the analysis metadata map.
     *
     * @return the metadata map
     */
    public Map<String, Object> getAnalysisMetadata() {
        return analysisMetadata;
    }

    /**
     * Sets the analysis metadata map.
     *
     * @param analysisMetadata the metadata map to set
     */
    public void setAnalysisMetadata(Map<String, Object> analysisMetadata) {
        this.analysisMetadata = analysisMetadata;
    }

    /**
     * Returns the list of processing steps performed during analysis.
     *
     * @return the processing steps list
     */
    public List<String> getProcessingSteps() {
        return processingSteps;
    }

    /**
     * Sets the list of processing steps performed during analysis.
     *
     * @param processingSteps the steps list to set
     */
    public void setProcessingSteps(List<String> processingSteps) {
        this.processingSteps = processingSteps;
    }
}
