package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive results from XML schema analysis.
 * Contains discovered elements, types, patterns, and optimization recommendations.
 */
public class SchemaAnalysisResult {

    // Basic analysis information
    private String analysisId = UUID.randomUUID().toString();
    private LocalDateTime analyzedAt = LocalDateTime.now();
    private long analysisTimeMs = 0;
    private int documentsAnalyzed = 0;
    private List<String> sourceDocuments = new ArrayList<>();

    // Schema structure
    private ElementInfo rootElement;
    private Map<String, ElementInfo> allElements = new LinkedHashMap<>();
    private Map<String, Set<ElementInfo>> elementsByName = new HashMap<>();
    private Map<String, AttributeInfo> allAttributes = new LinkedHashMap<>();
    private Map<String, Set<String>> elementHierarchy = new HashMap<>();

    // Namespace analysis
    private Set<String> discoveredNamespaces = new LinkedHashSet<>();
    private Map<String, String> namespacePrefixes = new HashMap<>();
    private String recommendedTargetNamespace;
    private boolean hasDefaultNamespace = false;
    private Map<String, Integer> namespaceUsage = new HashMap<>();

    // Type analysis
    private Map<String, String> inferredTypes = new HashMap<>();
    private Map<String, Double> typeConfidences = new HashMap<>();
    private Set<String> complexTypes = new HashSet<>();
    private Set<String> simpleTypes = new HashSet<>();
    private Map<String, List<ElementInfo>> typeGroups = new HashMap<>();

    // Pattern analysis
    private Map<String, Integer> detectedPatterns = new HashMap<>();
    private List<String> commonPatterns = new ArrayList<>();
    private Map<String, Double> patternConfidences = new HashMap<>();
    private Set<String> repeatingElements = new HashSet<>();
    private Set<String> optionalElements = new HashSet<>();

    // Occurrence analysis
    private Map<String, OccurrenceInfo> occurrencePatterns = new HashMap<>();
    private Set<String> unboundedElements = new HashSet<>();
    private Map<String, MinMaxInfo> occurrenceConstraints = new HashMap<>();

    // Constraints and restrictions
    private Map<String, List<String>> enumerationValues = new HashMap<>();
    private Map<String, String> patternRestrictions = new HashMap<>();
    private Map<String, LengthConstraints> lengthConstraints = new HashMap<>();
    private Map<String, ValueConstraints> valueConstraints = new HashMap<>();

    // Quality analysis
    private double overallConfidence = 0.0;
    private List<String> warnings = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private List<String> issues = new ArrayList<>();
    private Map<String, Double> qualityMetrics = new HashMap<>();

    // Statistics
    private Map<String, Integer> statisticsMap = new HashMap<>();
    private int totalElements = 0;
    private int totalAttributes = 0;
    private int maxDepth = 0;
    private int averageDepth = 0;
    private double complexityScore = 0.0;

    // Schema generation metadata
    private SchemaGenerationOptions generationOptions;
    private Map<String, Object> analysisMetadata = new HashMap<>();
    private List<String> processingSteps = new ArrayList<>();

    public SchemaAnalysisResult() {
        // Initialize with current timestamp
        this.analyzedAt = LocalDateTime.now();
    }

    // ========== Element Management ==========

    /**
     * Add an element to the analysis result
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
     * Add an attribute to the analysis result
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
     * Analyze patterns across all elements
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

    private void calculatePatternConfidences() {
        int totalElements = allElements.size();

        for (Map.Entry<String, Integer> entry : detectedPatterns.entrySet()) {
            double confidence = (double) entry.getValue() / totalElements;
            patternConfidences.put(entry.getKey(), confidence);
        }
    }

    // ========== Constraint Analysis ==========

    /**
     * Analyze constraints and restrictions
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
     * Calculate overall quality metrics and confidence
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

    private double calculateTypeReusability() {
        if (typeGroups.isEmpty()) return 0.0;

        int reusableTypes = 0;
        for (List<ElementInfo> group : typeGroups.values()) {
            if (group.size() > 1) reusableTypes++;
        }

        return (double) reusableTypes / Math.max(1, complexTypes.size());
    }

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
     * Get analysis summary
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

    public static class OccurrenceInfo {
        public int totalOccurrences = 0;
        public int documentOccurrences = 0;
        public int minOccurs = Integer.MAX_VALUE;
        public int maxOccurs = 0;
    }

    public record MinMaxInfo(int min, int max) {
    }

    public record LengthConstraints(Integer minLength, Integer maxLength) {
    }

    public record ValueConstraints(String minValue, String maxValue) {
    }

    // ========== Getters and Setters ==========

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public long getAnalysisTimeMs() {
        return analysisTimeMs;
    }

    public void setAnalysisTimeMs(long analysisTimeMs) {
        this.analysisTimeMs = analysisTimeMs;
    }

    public int getDocumentsAnalyzed() {
        return documentsAnalyzed;
    }

    public void setDocumentsAnalyzed(int documentsAnalyzed) {
        this.documentsAnalyzed = documentsAnalyzed;
    }

    public List<String> getSourceDocuments() {
        return sourceDocuments;
    }

    public void setSourceDocuments(List<String> sourceDocuments) {
        this.sourceDocuments = sourceDocuments;
    }

    public ElementInfo getRootElement() {
        return rootElement;
    }

    public void setRootElement(ElementInfo rootElement) {
        this.rootElement = rootElement;
    }

    public Map<String, ElementInfo> getAllElements() {
        return allElements;
    }

    public void setAllElements(Map<String, ElementInfo> allElements) {
        this.allElements = allElements;
    }

    public Map<String, Set<ElementInfo>> getElementsByName() {
        return elementsByName;
    }

    public void setElementsByName(Map<String, Set<ElementInfo>> elementsByName) {
        this.elementsByName = elementsByName;
    }

    public Map<String, AttributeInfo> getAllAttributes() {
        return allAttributes;
    }

    public void setAllAttributes(Map<String, AttributeInfo> allAttributes) {
        this.allAttributes = allAttributes;
    }

    public Map<String, Set<String>> getElementHierarchy() {
        return elementHierarchy;
    }

    public void setElementHierarchy(Map<String, Set<String>> elementHierarchy) {
        this.elementHierarchy = elementHierarchy;
    }

    public Set<String> getDiscoveredNamespaces() {
        return discoveredNamespaces;
    }

    public void setDiscoveredNamespaces(Set<String> discoveredNamespaces) {
        this.discoveredNamespaces = discoveredNamespaces;
    }

    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    public void setNamespacePrefixes(Map<String, String> namespacePrefixes) {
        this.namespacePrefixes = namespacePrefixes;
    }

    public String getRecommendedTargetNamespace() {
        return recommendedTargetNamespace;
    }

    public void setRecommendedTargetNamespace(String recommendedTargetNamespace) {
        this.recommendedTargetNamespace = recommendedTargetNamespace;
    }

    public boolean isHasDefaultNamespace() {
        return hasDefaultNamespace;
    }

    public void setHasDefaultNamespace(boolean hasDefaultNamespace) {
        this.hasDefaultNamespace = hasDefaultNamespace;
    }

    public Map<String, Integer> getNamespaceUsage() {
        return namespaceUsage;
    }

    public void setNamespaceUsage(Map<String, Integer> namespaceUsage) {
        this.namespaceUsage = namespaceUsage;
    }

    public Map<String, String> getInferredTypes() {
        return inferredTypes;
    }

    public void setInferredTypes(Map<String, String> inferredTypes) {
        this.inferredTypes = inferredTypes;
    }

    public Map<String, Double> getTypeConfidences() {
        return typeConfidences;
    }

    public void setTypeConfidences(Map<String, Double> typeConfidences) {
        this.typeConfidences = typeConfidences;
    }

    public Set<String> getComplexTypes() {
        return complexTypes;
    }

    public void setComplexTypes(Set<String> complexTypes) {
        this.complexTypes = complexTypes;
    }

    public Set<String> getSimpleTypes() {
        return simpleTypes;
    }

    public void setSimpleTypes(Set<String> simpleTypes) {
        this.simpleTypes = simpleTypes;
    }

    public Map<String, List<ElementInfo>> getTypeGroups() {
        return typeGroups;
    }

    public void setTypeGroups(Map<String, List<ElementInfo>> typeGroups) {
        this.typeGroups = typeGroups;
    }

    public Map<String, Integer> getDetectedPatterns() {
        return detectedPatterns;
    }

    public void setDetectedPatterns(Map<String, Integer> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    public List<String> getCommonPatterns() {
        return commonPatterns;
    }

    public void setCommonPatterns(List<String> commonPatterns) {
        this.commonPatterns = commonPatterns;
    }

    public Map<String, Double> getPatternConfidences() {
        return patternConfidences;
    }

    public void setPatternConfidences(Map<String, Double> patternConfidences) {
        this.patternConfidences = patternConfidences;
    }

    public Set<String> getRepeatingElements() {
        return repeatingElements;
    }

    public void setRepeatingElements(Set<String> repeatingElements) {
        this.repeatingElements = repeatingElements;
    }

    public Set<String> getOptionalElements() {
        return optionalElements;
    }

    public void setOptionalElements(Set<String> optionalElements) {
        this.optionalElements = optionalElements;
    }

    public Map<String, OccurrenceInfo> getOccurrencePatterns() {
        return occurrencePatterns;
    }

    public void setOccurrencePatterns(Map<String, OccurrenceInfo> occurrencePatterns) {
        this.occurrencePatterns = occurrencePatterns;
    }

    public Set<String> getUnboundedElements() {
        return unboundedElements;
    }

    public void setUnboundedElements(Set<String> unboundedElements) {
        this.unboundedElements = unboundedElements;
    }

    public Map<String, MinMaxInfo> getOccurrenceConstraints() {
        return occurrenceConstraints;
    }

    public void setOccurrenceConstraints(Map<String, MinMaxInfo> occurrenceConstraints) {
        this.occurrenceConstraints = occurrenceConstraints;
    }

    public Map<String, List<String>> getEnumerationValues() {
        return enumerationValues;
    }

    public void setEnumerationValues(Map<String, List<String>> enumerationValues) {
        this.enumerationValues = enumerationValues;
    }

    public Map<String, String> getPatternRestrictions() {
        return patternRestrictions;
    }

    public void setPatternRestrictions(Map<String, String> patternRestrictions) {
        this.patternRestrictions = patternRestrictions;
    }

    public Map<String, LengthConstraints> getLengthConstraints() {
        return lengthConstraints;
    }

    public void setLengthConstraints(Map<String, LengthConstraints> lengthConstraints) {
        this.lengthConstraints = lengthConstraints;
    }

    public Map<String, ValueConstraints> getValueConstraints() {
        return valueConstraints;
    }

    public void setValueConstraints(Map<String, ValueConstraints> valueConstraints) {
        this.valueConstraints = valueConstraints;
    }

    public double getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    public Map<String, Double> getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(Map<String, Double> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    public Map<String, Integer> getStatisticsMap() {
        return statisticsMap;
    }

    public void setStatisticsMap(Map<String, Integer> statisticsMap) {
        this.statisticsMap = statisticsMap;
    }

    public int getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(int totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalAttributes() {
        return totalAttributes;
    }

    public void setTotalAttributes(int totalAttributes) {
        this.totalAttributes = totalAttributes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getAverageDepth() {
        return averageDepth;
    }

    public void setAverageDepth(int averageDepth) {
        this.averageDepth = averageDepth;
    }

    public double getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(double complexityScore) {
        this.complexityScore = complexityScore;
    }

    public SchemaGenerationOptions getGenerationOptions() {
        return generationOptions;
    }

    public void setGenerationOptions(SchemaGenerationOptions generationOptions) {
        this.generationOptions = generationOptions;
    }

    public Map<String, Object> getAnalysisMetadata() {
        return analysisMetadata;
    }

    public void setAnalysisMetadata(Map<String, Object> analysisMetadata) {
        this.analysisMetadata = analysisMetadata;
    }

    public List<String> getProcessingSteps() {
        return processingSteps;
    }

    public void setProcessingSteps(List<String> processingSteps) {
        this.processingSteps = processingSteps;
    }
}