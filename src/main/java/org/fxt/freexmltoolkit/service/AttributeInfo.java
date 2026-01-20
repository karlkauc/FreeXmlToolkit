package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Information about an XML attribute discovered during schema analysis.
 * Contains type inference, occurrence patterns, and validation constraints.
 */
public class AttributeInfo {

    // Basic attribute information
    private String name;
    private String namespace;
    private String qualifiedName;
    private boolean required = false;
    private String defaultValue;
    private String fixedValue;

    // Type information
    private String inferredType = "xs:string";
    private String originalType; // If specified in source
    private double typeConfidence = 0.0;
    private Set<String> possibleTypes = new HashSet<>();

    // Value analysis
    private Set<String> observedValues = new LinkedHashSet<>();
    private List<String> sampleValues = new ArrayList<>();
    private int totalOccurrences = 0;
    private int nullOccurrences = 0;
    private int emptyOccurrences = 0;

    // Pattern analysis
    private Set<String> detectedPatterns = new HashSet<>();
    private Map<String, Integer> patternCounts = new HashMap<>();
    private String mostCommonPattern;
    private double patternConfidence = 0.0;

    // Constraints
    private Integer minLength;
    private Integer maxLength;
    private String minValue;
    private String maxValue;
    private Set<String> enumerationValues = new HashSet<>();
    private String restrictionPattern; // Regular expression

    // Statistical information
    private Map<String, Object> statistics = new HashMap<>();
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private Map<String, Integer> valueFrequency = new HashMap<>();

    // Documentation
    private String documentation;
    private List<String> comments = new ArrayList<>();
    private Map<String, String> annotations = new HashMap<>();

    // Analysis metadata
    private int analysisDepth = 0;
    private boolean analyzed = false;
    private LocalDateTime analyzedAt;
    private String sourceDocument;

    /**
     * Default constructor. Initializes timestamps.
     */
    public AttributeInfo() {
        this.firstSeen = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Constructor with name.
     *
     * @param name The name of the attribute.
     */
    public AttributeInfo(String name) {
        this();
        this.name = name;
        this.qualifiedName = name;
    }

    /**
     * Constructor with name and namespace.
     *
     * @param name      The name of the attribute.
     * @param namespace The namespace of the attribute.
     */
    public AttributeInfo(String name, String namespace) {
        this(name);
        this.namespace = namespace;
        this.qualifiedName = (namespace != null && !namespace.isEmpty())
                ? namespace + ":" + name : name;
    }

    // ========== Value Analysis Methods ==========

    /**
     * Add an observed value for analysis
     * @param value The observed attribute value
     */
    public void addObservedValue(String value) {
        if (value == null) {
            nullOccurrences++;
            return;
        }

        if (value.isEmpty()) {
            emptyOccurrences++;
        }

        observedValues.add(value);
        totalOccurrences++;

        // Update value frequency
        valueFrequency.put(value, valueFrequency.getOrDefault(value, 0) + 1);

        // Keep sample values (limited size)
        if (sampleValues.size() < 10 && !sampleValues.contains(value)) {
            sampleValues.add(value);
        }

        // Update timestamps
        this.lastSeen = LocalDateTime.now();

        // Trigger type inference
        inferTypeFromValue(value);
    }

    /**
     * Infer type from observed value
     */
    private void inferTypeFromValue(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        // Check various patterns
        if (isInteger(value)) {
            possibleTypes.add("xs:integer");
            possibleTypes.add("xs:int");
            possibleTypes.add("xs:long");
        }

        if (isDecimal(value)) {
            possibleTypes.add("xs:decimal");
            possibleTypes.add("xs:double");
            possibleTypes.add("xs:float");
        }

        if (isBoolean(value)) {
            possibleTypes.add("xs:boolean");
        }

        if (isDate(value)) {
            possibleTypes.add("xs:date");
        }

        if (isDateTime(value)) {
            possibleTypes.add("xs:dateTime");
        }

        if (isTime(value)) {
            possibleTypes.add("xs:time");
        }

        if (isEmail(value)) {
            possibleTypes.add("emailType");
            detectedPatterns.add("email");
        }

        if (isUrl(value)) {
            possibleTypes.add("xs:anyURI");
            detectedPatterns.add("url");
        }

        // Always possible as string
        possibleTypes.add("xs:string");

        // Update inferred type based on most restrictive common type
        updateInferredType();
    }

    /**
     * Update the inferred type based on all observed values
     */
    private void updateInferredType() {
        if (possibleTypes.isEmpty()) {
            inferredType = "xs:string";
            typeConfidence = 1.0;
            return;
        }

        // Count occurrences of each possible type
        Map<String, Integer> typeCounts = new HashMap<>();
        for (String type : possibleTypes) {
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }

        // Find most restrictive type that applies to all values
        if (allValuesMatch("xs:boolean")) {
            inferredType = "xs:boolean";
            typeConfidence = 1.0;
        } else if (allValuesMatch("xs:integer")) {
            inferredType = "xs:integer";
            typeConfidence = 1.0;
        } else if (allValuesMatch("xs:decimal")) {
            inferredType = "xs:decimal";
            typeConfidence = 0.9;
        } else if (allValuesMatch("xs:date")) {
            inferredType = "xs:date";
            typeConfidence = 1.0;
        } else if (allValuesMatch("xs:dateTime")) {
            inferredType = "xs:dateTime";
            typeConfidence = 1.0;
        } else if (allValuesMatch("xs:time")) {
            inferredType = "xs:time";
            typeConfidence = 1.0;
        } else if (allValuesMatch("xs:anyURI")) {
            inferredType = "xs:anyURI";
            typeConfidence = 0.8;
        } else {
            inferredType = "xs:string";
            typeConfidence = 0.7;
        }

        // Adjust confidence based on sample size
        if (totalOccurrences < 3) {
            typeConfidence *= 0.5;
        } else if (totalOccurrences < 10) {
            typeConfidence *= 0.8;
        }
    }

    private boolean allValuesMatch(String type) {
        for (String value : observedValues) {
            if (value == null || value.isEmpty()) continue;

            switch (type) {
                case "xs:boolean":
                    if (!isBoolean(value)) return false;
                    break;
                case "xs:integer":
                    if (!isInteger(value)) return false;
                    break;
                case "xs:decimal":
                    if (!isDecimal(value)) return false;
                    break;
                case "xs:date":
                    if (!isDate(value)) return false;
                    break;
                case "xs:dateTime":
                    if (!isDateTime(value)) return false;
                    break;
                case "xs:time":
                    if (!isTime(value)) return false;
                    break;
                case "xs:anyURI":
                    if (!isUrl(value)) return false;
                    break;
            }
        }
        return true;
    }

    // ========== Pattern Detection Methods ==========

    /**
     * Analyze patterns in attribute values
     */
    public void analyzePatterns() {
        if (observedValues.isEmpty()) return;

        Map<String, Integer> patternFreq = new HashMap<>();

        for (String value : observedValues) {
            if (value == null || value.isEmpty()) continue;

            // Detect various patterns
            String pattern = detectValuePattern(value);
            patternFreq.put(pattern, patternFreq.getOrDefault(pattern, 0) + 1);

            // Update constraints
            updateConstraintsFromValue(value);
        }

        // Find most common pattern
        if (!patternFreq.isEmpty()) {
            mostCommonPattern = Collections.max(patternFreq.entrySet(),
                    Map.Entry.comparingByValue()).getKey();
            patternCounts = patternFreq;

            // Calculate pattern confidence
            int maxCount = Collections.max(patternFreq.values());
            patternConfidence = (double) maxCount / totalOccurrences;
        }

        // Generate enumeration if appropriate
        if (shouldGenerateEnumeration()) {
            enumerationValues = new HashSet<>(observedValues);
        }

        this.analyzed = true;
        this.analyzedAt = LocalDateTime.now();
    }

    private String detectValuePattern(String value) {
        if (isInteger(value)) return "integer";
        if (isDecimal(value)) return "decimal";
        if (isBoolean(value)) return "boolean";
        if (isDate(value)) return "date";
        if (isDateTime(value)) return "dateTime";
        if (isTime(value)) return "time";
        if (isEmail(value)) return "email";
        if (isUrl(value)) return "url";

        // Length-based patterns
        int length = value.length();
        if (length <= 10) return "short_string";
        if (length <= 50) return "medium_string";
        return "long_string";
    }

    private void updateConstraintsFromValue(String value) {
        if (value == null) return;

        // Update length constraints
        int length = value.length();
        if (minLength == null || length < minLength) {
            minLength = length;
        }
        if (maxLength == null || length > maxLength) {
            maxLength = length;
        }

        // Update value constraints for numeric types
        if (isNumeric(value)) {
            try {
                double numValue = Double.parseDouble(value);
                if (minValue == null || numValue < Double.parseDouble(minValue)) {
                    minValue = value;
                }
                if (maxValue == null || numValue > Double.parseDouble(maxValue)) {
                    maxValue = value;
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
    }

    private boolean shouldGenerateEnumeration() {
        // Generate enumeration if:
        // - Small number of distinct values
        // - High repetition of values
        // - Not obviously a free-form field

        int distinctValues = observedValues.size();
        if (distinctValues > 20) return false;
        if (distinctValues < 2) return false;

        // Check if values look like enumeration
        boolean looksLikeEnum = true;
        for (String value : observedValues) {
            if (value == null) continue;
            // If any value is very long or looks like free text, probably not enum
            if (value.length() > 50 || value.contains(" ") && value.split(" ").length > 3) {
                looksLikeEnum = false;
                break;
            }
        }

        return looksLikeEnum && (distinctValues <= 10 || patternConfidence > 0.8);
    }

    // ========== Utility Methods ==========

    private boolean isInteger(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDecimal(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isNumeric(String value) {
        return isInteger(value) || isDecimal(value);
    }

    private boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ||
                "1".equals(value) || "0".equals(value);
    }

    private boolean isDate(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private boolean isDateTime(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    }

    private boolean isTime(String value) {
        return value.matches("\\d{2}:\\d{2}:\\d{2}.*");
    }

    private boolean isEmail(String value) {
        return value.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    }

    private boolean isUrl(String value) {
        return value.matches("https?://.*");
    }

    /**
     * Get usage statistics.
     *
     * @return A map containing usage statistics.
     */
    public Map<String, Object> getUsageStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOccurrences", totalOccurrences);
        stats.put("distinctValues", observedValues.size());
        stats.put("nullOccurrences", nullOccurrences);
        stats.put("emptyOccurrences", emptyOccurrences);
        stats.put("typeConfidence", typeConfidence);
        stats.put("patternConfidence", patternConfidence);
        stats.put("mostFrequentValue", getMostFrequentValue());
        stats.put("averageLength", getAverageLength());
        return stats;
    }

    private String getMostFrequentValue() {
        if (valueFrequency.isEmpty()) return null;
        return Collections.max(valueFrequency.entrySet(),
                Map.Entry.comparingByValue()).getKey();
    }

    private double getAverageLength() {
        if (observedValues.isEmpty()) return 0.0;
        return observedValues.stream()
                .filter(Objects::nonNull)
                .mapToInt(String::length)
                .average()
                .orElse(0.0);
    }

    /**
     * Check if attribute should be marked as required.
     *
     * @param requiredThreshold The threshold ratio (0.0 to 1.0) for considering an attribute required.
     * @return True if the attribute presence ratio meets or exceeds the threshold.
     */
    public boolean shouldBeRequired(double requiredThreshold) {
        if (totalOccurrences == 0) return false;
        double presenceRatio = (double) (totalOccurrences - nullOccurrences) / totalOccurrences;
        return presenceRatio >= requiredThreshold;
    }

    // ========== Getters and Setters ==========

    /**
     * Gets the name of the attribute.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the attribute.
     *
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the namespace of the attribute.
     *
     * @return The namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace of the attribute.
     *
     * @param namespace The namespace to set.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the qualified name of the attribute.
     *
     * @return The qualified name.
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * Sets the qualified name of the attribute.
     *
     * @param qualifiedName The qualified name to set.
     */
    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    /**
     * Checks if the attribute is required.
     *
     * @return True if required, false otherwise.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Sets whether the attribute is required.
     *
     * @param required True if required.
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Gets the default value of the attribute.
     *
     * @return The default value.
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value of the attribute.
     *
     * @param defaultValue The default value to set.
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the fixed value of the attribute.
     *
     * @return The fixed value.
     */
    public String getFixedValue() {
        return fixedValue;
    }

    /**
     * Sets the fixed value of the attribute.
     *
     * @param fixedValue The fixed value to set.
     */
    public void setFixedValue(String fixedValue) {
        this.fixedValue = fixedValue;
    }

    /**
     * Gets the inferred type of the attribute.
     *
     * @return The inferred type (e.g., "xs:string").
     */
    public String getInferredType() {
        return inferredType;
    }

    /**
     * Sets the inferred type of the attribute.
     *
     * @param inferredType The inferred type to set.
     */
    public void setInferredType(String inferredType) {
        this.inferredType = inferredType;
    }

    /**
     * Gets the original type specified in the source.
     *
     * @return The original type.
     */
    public String getOriginalType() {
        return originalType;
    }

    /**
     * Sets the original type specified in the source.
     *
     * @param originalType The original type to set.
     */
    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    /**
     * Gets the confidence level of the inferred type.
     *
     * @return The confidence level (0.0 to 1.0).
     */
    public double getTypeConfidence() {
        return typeConfidence;
    }

    /**
     * Sets the confidence level of the inferred type.
     *
     * @param typeConfidence The confidence level to set.
     */
    public void setTypeConfidence(double typeConfidence) {
        this.typeConfidence = typeConfidence;
    }

    /**
     * Gets the set of possible types for the attribute.
     *
     * @return The set of possible types.
     */
    public Set<String> getPossibleTypes() {
        return possibleTypes;
    }

    /**
     * Sets the set of possible types for the attribute.
     *
     * @param possibleTypes The set of possible types to set.
     */
    public void setPossibleTypes(Set<String> possibleTypes) {
        this.possibleTypes = possibleTypes;
    }

    /**
     * Gets the set of observed values.
     *
     * @return The set of observed values.
     */
    public Set<String> getObservedValues() {
        return observedValues;
    }

    /**
     * Sets the set of observed values.
     *
     * @param observedValues The set of observed values to set.
     */
    public void setObservedValues(Set<String> observedValues) {
        this.observedValues = observedValues;
    }

    /**
     * Gets the list of sample values.
     *
     * @return The list of sample values.
     */
    public List<String> getSampleValues() {
        return sampleValues;
    }

    /**
     * Sets the list of sample values.
     *
     * @param sampleValues The list of sample values to set.
     */
    public void setSampleValues(List<String> sampleValues) {
        this.sampleValues = sampleValues;
    }

    /**
     * Gets the total number of occurrences of the attribute.
     *
     * @return The total occurrences.
     */
    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    /**
     * Sets the total number of occurrences of the attribute.
     *
     * @param totalOccurrences The total occurrences to set.
     */
    public void setTotalOccurrences(int totalOccurrences) {
        this.totalOccurrences = totalOccurrences;
    }

    /**
     * Gets the number of null occurrences.
     *
     * @return The number of null occurrences.
     */
    public int getNullOccurrences() {
        return nullOccurrences;
    }

    /**
     * Sets the number of null occurrences.
     *
     * @param nullOccurrences The number of null occurrences to set.
     */
    public void setNullOccurrences(int nullOccurrences) {
        this.nullOccurrences = nullOccurrences;
    }

    /**
     * Gets the number of empty occurrences.
     *
     * @return The number of empty occurrences.
     */
    public int getEmptyOccurrences() {
        return emptyOccurrences;
    }

    /**
     * Sets the number of empty occurrences.
     *
     * @param emptyOccurrences The number of empty occurrences to set.
     */
    public void setEmptyOccurrences(int emptyOccurrences) {
        this.emptyOccurrences = emptyOccurrences;
    }

    /**
     * Gets the set of detected patterns.
     *
     * @return The set of detected patterns.
     */
    public Set<String> getDetectedPatterns() {
        return detectedPatterns;
    }

    /**
     * Sets the set of detected patterns.
     *
     * @param detectedPatterns The set of detected patterns to set.
     */
    public void setDetectedPatterns(Set<String> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    /**
     * Gets the counts of detected patterns.
     *
     * @return The pattern counts.
     */
    public Map<String, Integer> getPatternCounts() {
        return patternCounts;
    }

    /**
     * Sets the counts of detected patterns.
     *
     * @param patternCounts The pattern counts to set.
     */
    public void setPatternCounts(Map<String, Integer> patternCounts) {
        this.patternCounts = patternCounts;
    }

    /**
     * Gets the most common pattern detected.
     *
     * @return The most common pattern.
     */
    public String getMostCommonPattern() {
        return mostCommonPattern;
    }

    /**
     * Sets the most common pattern detected.
     *
     * @param mostCommonPattern The most common pattern to set.
     */
    public void setMostCommonPattern(String mostCommonPattern) {
        this.mostCommonPattern = mostCommonPattern;
    }

    /**
     * Gets the confidence level of the detected pattern.
     *
     * @return The confidence level (0.0 to 1.0).
     */
    public double getPatternConfidence() {
        return patternConfidence;
    }

    /**
     * Sets the confidence level of the detected pattern.
     *
     * @param patternConfidence The confidence level to set.
     */
    public void setPatternConfidence(double patternConfidence) {
        this.patternConfidence = patternConfidence;
    }

    /**
     * Gets the minimum length of the attribute value.
     *
     * @return The minimum length.
     */
    public Integer getMinLength() {
        return minLength;
    }

    /**
     * Sets the minimum length of the attribute value.
     *
     * @param minLength The minimum length to set.
     */
    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    /**
     * Gets the maximum length of the attribute value.
     *
     * @return The maximum length.
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * Sets the maximum length of the attribute value.
     *
     * @param maxLength The maximum length to set.
     */
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Gets the minimum value observed (for numeric types).
     *
     * @return The minimum value.
     */
    public String getMinValue() {
        return minValue;
    }

    /**
     * Sets the minimum value observed.
     *
     * @param minValue The minimum value to set.
     */
    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    /**
     * Gets the maximum value observed (for numeric types).
     *
     * @return The maximum value.
     */
    public String getMaxValue() {
        return maxValue;
    }

    /**
     * Sets the maximum value observed.
     *
     * @param maxValue The maximum value to set.
     */
    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    /**
     * Gets the set of enumeration values (if detected).
     *
     * @return The set of enumeration values.
     */
    public Set<String> getEnumerationValues() {
        return enumerationValues;
    }

    /**
     * Sets the set of enumeration values.
     *
     * @param enumerationValues The set of enumeration values to set.
     */
    public void setEnumerationValues(Set<String> enumerationValues) {
        this.enumerationValues = enumerationValues;
    }

    /**
     * Gets the restriction pattern (regex).
     *
     * @return The restriction pattern.
     */
    public String getRestrictionPattern() {
        return restrictionPattern;
    }

    /**
     * Sets the restriction pattern.
     *
     * @param restrictionPattern The restriction pattern to set.
     */
    public void setRestrictionPattern(String restrictionPattern) {
        this.restrictionPattern = restrictionPattern;
    }

    /**
     * Gets the statistical information.
     *
     * @return The statistics map.
     */
    public Map<String, Object> getStatistics() {
        return statistics;
    }

    /**
     * Sets the statistical information.
     *
     * @param statistics The statistics map to set.
     */
    public void setStatistics(Map<String, Object> statistics) {
        this.statistics = statistics;
    }

    /**
     * Gets the timestamp when the attribute was first seen.
     *
     * @return The first seen timestamp.
     */
    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    /**
     * Sets the timestamp when the attribute was first seen.
     *
     * @param firstSeen The first seen timestamp to set.
     */
    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    /**
     * Gets the timestamp when the attribute was last seen.
     *
     * @return The last seen timestamp.
     */
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    /**
     * Sets the timestamp when the attribute was last seen.
     *
     * @param lastSeen The last seen timestamp to set.
     */
    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * Gets the frequency of each observed value.
     *
     * @return The value frequency map.
     */
    public Map<String, Integer> getValueFrequency() {
        return valueFrequency;
    }

    /**
     * Sets the frequency of each observed value.
     *
     * @param valueFrequency The value frequency map to set.
     */
    public void setValueFrequency(Map<String, Integer> valueFrequency) {
        this.valueFrequency = valueFrequency;
    }

    /**
     * Gets the documentation for the attribute.
     *
     * @return The documentation string.
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation for the attribute.
     *
     * @param documentation The documentation string to set.
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets the list of comments associated with the attribute.
     *
     * @return The list of comments.
     */
    public List<String> getComments() {
        return comments;
    }

    /**
     * Sets the list of comments associated with the attribute.
     *
     * @param comments The list of comments to set.
     */
    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    /**
     * Gets the annotations associated with the attribute.
     *
     * @return The annotations map.
     */
    public Map<String, String> getAnnotations() {
        return annotations;
    }

    /**
     * Sets the annotations associated with the attribute.
     *
     * @param annotations The annotations map to set.
     */
    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    /**
     * Gets the depth of analysis performed.
     *
     * @return The analysis depth.
     */
    public int getAnalysisDepth() {
        return analysisDepth;
    }

    /**
     * Sets the depth of analysis performed.
     *
     * @param analysisDepth The analysis depth to set.
     */
    public void setAnalysisDepth(int analysisDepth) {
        this.analysisDepth = analysisDepth;
    }

    /**
     * Checks if the attribute has been analyzed.
     *
     * @return True if analyzed, false otherwise.
     */
    public boolean isAnalyzed() {
        return analyzed;
    }

    /**
     * Sets whether the attribute has been analyzed.
     *
     * @param analyzed True if analyzed.
     */
    public void setAnalyzed(boolean analyzed) {
        this.analyzed = analyzed;
    }

    /**
     * Gets the timestamp when analysis was performed.
     *
     * @return The analysis timestamp.
     */
    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    /**
     * Sets the timestamp when analysis was performed.
     *
     * @param analyzedAt The analysis timestamp to set.
     */
    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    /**
     * Gets the source document identifier.
     *
     * @return The source document identifier.
     */
    public String getSourceDocument() {
        return sourceDocument;
    }

    /**
     * Sets the source document identifier.
     *
     * @param sourceDocument The source document identifier to set.
     */
    public void setSourceDocument(String sourceDocument) {
        this.sourceDocument = sourceDocument;
    }

    @Override
    public String toString() {
        return String.format("AttributeInfo{name='%s', type='%s', confidence=%.2f, occurrences=%d}",
                qualifiedName, inferredType, typeConfidence, totalOccurrences);
    }
}