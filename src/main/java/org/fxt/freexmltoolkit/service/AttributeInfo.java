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

    public AttributeInfo() {
        this.firstSeen = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    public AttributeInfo(String name) {
        this();
        this.name = name;
        this.qualifiedName = name;
    }

    public AttributeInfo(String name, String namespace) {
        this(name);
        this.namespace = namespace;
        this.qualifiedName = (namespace != null && !namespace.isEmpty())
                ? namespace + ":" + name : name;
    }

    // ========== Value Analysis Methods ==========

    /**
     * Add an observed value for analysis
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
     * Get usage statistics
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
     * Check if attribute should be marked as required
     */
    public boolean shouldBeRequired(double requiredThreshold) {
        if (totalOccurrences == 0) return false;
        double presenceRatio = (double) (totalOccurrences - nullOccurrences) / totalOccurrences;
        return presenceRatio >= requiredThreshold;
    }

    // ========== Getters and Setters ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getFixedValue() {
        return fixedValue;
    }

    public void setFixedValue(String fixedValue) {
        this.fixedValue = fixedValue;
    }

    public String getInferredType() {
        return inferredType;
    }

    public void setInferredType(String inferredType) {
        this.inferredType = inferredType;
    }

    public String getOriginalType() {
        return originalType;
    }

    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    public double getTypeConfidence() {
        return typeConfidence;
    }

    public void setTypeConfidence(double typeConfidence) {
        this.typeConfidence = typeConfidence;
    }

    public Set<String> getPossibleTypes() {
        return possibleTypes;
    }

    public void setPossibleTypes(Set<String> possibleTypes) {
        this.possibleTypes = possibleTypes;
    }

    public Set<String> getObservedValues() {
        return observedValues;
    }

    public void setObservedValues(Set<String> observedValues) {
        this.observedValues = observedValues;
    }

    public List<String> getSampleValues() {
        return sampleValues;
    }

    public void setSampleValues(List<String> sampleValues) {
        this.sampleValues = sampleValues;
    }

    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    public void setTotalOccurrences(int totalOccurrences) {
        this.totalOccurrences = totalOccurrences;
    }

    public int getNullOccurrences() {
        return nullOccurrences;
    }

    public void setNullOccurrences(int nullOccurrences) {
        this.nullOccurrences = nullOccurrences;
    }

    public int getEmptyOccurrences() {
        return emptyOccurrences;
    }

    public void setEmptyOccurrences(int emptyOccurrences) {
        this.emptyOccurrences = emptyOccurrences;
    }

    public Set<String> getDetectedPatterns() {
        return detectedPatterns;
    }

    public void setDetectedPatterns(Set<String> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    public Map<String, Integer> getPatternCounts() {
        return patternCounts;
    }

    public void setPatternCounts(Map<String, Integer> patternCounts) {
        this.patternCounts = patternCounts;
    }

    public String getMostCommonPattern() {
        return mostCommonPattern;
    }

    public void setMostCommonPattern(String mostCommonPattern) {
        this.mostCommonPattern = mostCommonPattern;
    }

    public double getPatternConfidence() {
        return patternConfidence;
    }

    public void setPatternConfidence(double patternConfidence) {
        this.patternConfidence = patternConfidence;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getMinValue() {
        return minValue;
    }

    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    public Set<String> getEnumerationValues() {
        return enumerationValues;
    }

    public void setEnumerationValues(Set<String> enumerationValues) {
        this.enumerationValues = enumerationValues;
    }

    public String getRestrictionPattern() {
        return restrictionPattern;
    }

    public void setRestrictionPattern(String restrictionPattern) {
        this.restrictionPattern = restrictionPattern;
    }

    public Map<String, Object> getStatistics() {
        return statistics;
    }

    public void setStatistics(Map<String, Object> statistics) {
        this.statistics = statistics;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Map<String, Integer> getValueFrequency() {
        return valueFrequency;
    }

    public void setValueFrequency(Map<String, Integer> valueFrequency) {
        this.valueFrequency = valueFrequency;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public List<String> getComments() {
        return comments;
    }

    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public int getAnalysisDepth() {
        return analysisDepth;
    }

    public void setAnalysisDepth(int analysisDepth) {
        this.analysisDepth = analysisDepth;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public void setAnalyzed(boolean analyzed) {
        this.analyzed = analyzed;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getSourceDocument() {
        return sourceDocument;
    }

    public void setSourceDocument(String sourceDocument) {
        this.sourceDocument = sourceDocument;
    }

    @Override
    public String toString() {
        return String.format("AttributeInfo{name='%s', type='%s', confidence=%.2f, occurrences=%d}",
                qualifiedName, inferredType, typeConfidence, totalOccurrences);
    }
}