package org.fxt.freexmltoolkit.service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive information about an XML element discovered during schema analysis.
 * Contains structure, type inference, occurrence patterns, and constraints.
 */
public class ElementInfo {

    // Basic element information
    private String name;
    private String namespace;
    private String qualifiedName;
    private int depth = 0;
    private String xpath;

    // Content model
    private String contentType = "mixed"; // empty, simple, complex, mixed
    private String textContent;
    private boolean hasAttributes = false;
    private boolean hasChildElements = false;
    private boolean hasTextContent = false;
    private boolean hasMixedContent = false;

    // Type information
    private String inferredType = "xs:string";
    private String originalType;
    private double typeConfidence = 0.0;
    private Set<String> possibleTypes = new HashSet<>();
    private boolean isComplexType = false;
    private String complexTypeName;

    // Child elements and attributes
    private Map<String, List<ElementInfo>> childElements = new LinkedHashMap<>();
    private Map<String, AttributeInfo> attributes = new LinkedHashMap<>();
    private List<ElementInfo> allChildren = new ArrayList<>();

    // Occurrence patterns
    private int minOccurs = 1;
    private int maxOccurs = 1;
    private boolean unbounded = false;
    private int totalOccurrences = 0;
    private int documentOccurrences = 0;
    private double occurrenceConfidence = 0.0;

    // Value analysis (for simple content)
    private Set<String> observedValues = new LinkedHashSet<>();
    private List<String> sampleValues = new ArrayList<>();
    private Map<String, Integer> valueFrequency = new HashMap<>();

    // Pattern detection
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
    private boolean nillable = false;

    // Statistical information
    private Map<String, Object> statistics = new HashMap<>();
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private int averageChildCount = 0;
    private int maxChildCount = 0;
    private Map<String, Double> childFrequencies = new HashMap<>();

    // Documentation and metadata
    private String documentation;
    private List<String> comments = new ArrayList<>();
    private Map<String, String> annotations = new HashMap<>();
    private Set<String> sourceDocuments = new HashSet<>();

    // Analysis metadata
    private int analysisDepth = 0;
    private boolean analyzed = false;
    private LocalDateTime analyzedAt;
    private Map<String, Object> analysisContext = new HashMap<>();

    public ElementInfo() {
        this.firstSeen = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    public ElementInfo(String name) {
        this();
        this.name = name;
        this.qualifiedName = name;
    }

    public ElementInfo(String name, String namespace) {
        this(name);
        this.namespace = namespace;
        this.qualifiedName = (namespace != null && !namespace.isEmpty())
                ? namespace + ":" + name : name;
    }

    // ========== Content Analysis Methods ==========

    /**
     * Add text content for analysis
     */
    public void addTextContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        this.hasTextContent = true;
        this.textContent = text;

        // Add to observed values for simple content analysis
        if (!hasChildElements) {
            addObservedValue(text.trim());
        } else {
            this.hasMixedContent = true;
            this.contentType = "mixed";
        }

        // Update content type
        updateContentType();

        // Infer type from content
        inferTypeFromContent(text.trim());

        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Add a child element
     */
    public void addChildElement(ElementInfo child) {
        if (child == null) return;

        this.hasChildElements = true;
        child.depth = this.depth + 1;

        // Add to child elements map
        String childName = child.getQualifiedName();
        childElements.computeIfAbsent(childName, k -> new ArrayList<>()).add(child);

        // Add to all children list
        allChildren.add(child);

        // Update statistics
        updateChildStatistics();

        // Update content type
        updateContentType();

        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Add an attribute
     */
    public void addAttribute(AttributeInfo attribute) {
        if (attribute == null) return;

        this.hasAttributes = true;
        attributes.put(attribute.getQualifiedName(), attribute);

        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Update content type based on current state
     */
    private void updateContentType() {
        if (!hasChildElements && !hasTextContent && !hasAttributes) {
            contentType = "empty";
        } else if (!hasChildElements && hasTextContent && hasAttributes) {
            contentType = "simple";
            isComplexType = false;
        } else if (hasChildElements && !hasTextContent) {
            contentType = "complex";
            isComplexType = true;
        } else if (hasChildElements && hasTextContent) {
            contentType = "mixed";
            isComplexType = true;
            hasMixedContent = true;
        } else if (!hasChildElements && !hasTextContent && hasAttributes) {
            contentType = "empty";
            isComplexType = true; // Attributes require complex type
        }
    }

    private void updateChildStatistics() {
        int currentChildCount = allChildren.size();
        if (currentChildCount > maxChildCount) {
            maxChildCount = currentChildCount;
        }

        // Update child frequencies
        for (Map.Entry<String, List<ElementInfo>> entry : childElements.entrySet()) {
            String childName = entry.getKey();
            int occurrences = entry.getValue().size();
            childFrequencies.put(childName, (double) occurrences / documentOccurrences);
        }
    }

    // ========== Value and Type Analysis ==========

    /**
     * Add an observed value for simple content analysis
     */
    private void addObservedValue(String value) {
        if (value == null || value.isEmpty()) return;

        observedValues.add(value);
        valueFrequency.put(value, valueFrequency.getOrDefault(value, 0) + 1);

        // Keep sample values (limited size)
        if (sampleValues.size() < 10 && !sampleValues.contains(value)) {
            sampleValues.add(value);
        }

        // Update constraints
        updateConstraintsFromValue(value);
    }

    /**
     * Infer type from content
     */
    private void inferTypeFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        // Check various patterns
        if (isInteger(content)) {
            possibleTypes.add("xs:integer");
            possibleTypes.add("xs:int");
            possibleTypes.add("xs:long");
        }

        if (isDecimal(content)) {
            possibleTypes.add("xs:decimal");
            possibleTypes.add("xs:double");
            possibleTypes.add("xs:float");
        }

        if (isBoolean(content)) {
            possibleTypes.add("xs:boolean");
        }

        if (isDate(content)) {
            possibleTypes.add("xs:date");
        }

        if (isDateTime(content)) {
            possibleTypes.add("xs:dateTime");
        }

        if (isTime(content)) {
            possibleTypes.add("xs:time");
        }

        if (isEmail(content)) {
            possibleTypes.add("emailType");
            detectedPatterns.add("email");
        }

        if (isUrl(content)) {
            possibleTypes.add("xs:anyURI");
            detectedPatterns.add("url");
        }

        // Always possible as string
        possibleTypes.add("xs:string");

        // Update inferred type
        updateInferredType();
    }

    /**
     * Update the inferred type based on all observed values
     */
    private void updateInferredType() {
        if (isComplexType) {
            inferredType = complexTypeName != null ? complexTypeName : name + "Type";
            typeConfidence = 1.0;
            return;
        }

        if (possibleTypes.isEmpty()) {
            inferredType = "xs:string";
            typeConfidence = 1.0;
            return;
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
        if (observedValues.size() < 3) {
            typeConfidence *= 0.5;
        } else if (observedValues.size() < 10) {
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

    // ========== Occurrence Analysis ==========

    /**
     * Record an occurrence of this element
     */
    public void recordOccurrence(String documentId) {
        totalOccurrences++;
        if (documentId != null) {
            sourceDocuments.add(documentId);
        }
        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Analyze occurrence patterns
     */
    public void analyzeOccurrences(int totalDocuments) {
        documentOccurrences = sourceDocuments.size();

        // Calculate occurrence confidence
        if (totalDocuments > 0) {
            occurrenceConfidence = (double) documentOccurrences / totalDocuments;
        }

        // Infer minOccurs/maxOccurs patterns
        if (occurrenceConfidence < 0.5) {
            minOccurs = 0; // Optional
        } else {
            minOccurs = 1; // Required
        }

        // Analyze child element occurrences
        for (Map.Entry<String, List<ElementInfo>> entry : childElements.entrySet()) {
            List<ElementInfo> occurrences = entry.getValue();

            // Calculate min/max for child elements
            int childMin = occurrences.stream().mapToInt(e -> 1).min().orElse(0);
            int childMax = occurrences.size();

            // Update child occurrence info
            if (!occurrences.isEmpty()) {
                ElementInfo representative = occurrences.get(0);
                representative.minOccurs = childMin;
                representative.maxOccurs = childMax;

                // Check if unbounded
                if (childMax > 10) {
                    representative.unbounded = true;
                    representative.maxOccurs = -1; // Represents unbounded
                }
            }
        }

        calculateAverageChildCount();
    }

    private void calculateAverageChildCount() {
        if (allChildren.isEmpty()) {
            averageChildCount = 0;
            return;
        }

        averageChildCount = allChildren.size();
    }

    // ========== Pattern Analysis ==========

    /**
     * Analyze patterns in element structure and content
     */
    public void analyzePatterns() {
        // Analyze content patterns
        if (!observedValues.isEmpty()) {
            analyzeContentPatterns();
        }

        // Analyze structural patterns
        analyzeStructuralPatterns();

        // Generate enumeration if appropriate
        if (shouldGenerateEnumeration()) {
            enumerationValues = new HashSet<>(observedValues);
        }

        this.analyzed = true;
        this.analyzedAt = LocalDateTime.now();
    }

    private void analyzeContentPatterns() {
        Map<String, Integer> patternFreq = new HashMap<>();

        for (String value : observedValues) {
            if (value == null || value.isEmpty()) continue;

            String pattern = detectValuePattern(value);
            patternFreq.put(pattern, patternFreq.getOrDefault(pattern, 0) + 1);
        }

        if (!patternFreq.isEmpty()) {
            mostCommonPattern = Collections.max(patternFreq.entrySet(),
                    Map.Entry.comparingByValue()).getKey();
            patternCounts = patternFreq;

            // Calculate pattern confidence
            int maxCount = Collections.max(patternFreq.values());
            patternConfidence = (double) maxCount / observedValues.size();
        }
    }

    private void analyzeStructuralPatterns() {
        // Analyze child element patterns
        detectedPatterns.add("element_count_" + childElements.size());
        detectedPatterns.add("attribute_count_" + attributes.size());
        detectedPatterns.add("content_type_" + contentType);

        if (hasMixedContent) {
            detectedPatterns.add("mixed_content");
        }

        if (hasTextContent && hasChildElements) {
            detectedPatterns.add("text_and_elements");
        }
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

    private boolean shouldGenerateEnumeration() {
        // Generate enumeration if simple content with limited distinct values
        if (isComplexType || hasChildElements) return false;

        int distinctValues = observedValues.size();
        if (distinctValues > 20 || distinctValues < 2) return false;

        // Check if values look like enumeration
        boolean looksLikeEnum = true;
        for (String value : observedValues) {
            if (value == null) continue;
            if (value.length() > 50 || (value.contains(" ") && value.split(" ").length > 3)) {
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
     * Get element complexity score
     */
    public double getComplexityScore() {
        double score = 0.0;

        // Base complexity from content type
        switch (contentType) {
            case "empty":
                score += 1.0;
                break;
            case "simple":
                score += 2.0;
                break;
            case "complex":
                score += 4.0;
                break;
            case "mixed":
                score += 6.0;
                break;
        }

        // Add complexity for child elements
        score += childElements.size() * 2.0;

        // Add complexity for attributes
        score += attributes.size() * 1.5;

        // Add complexity for depth
        score += depth * 0.5;

        // Add complexity for occurrence patterns
        if (unbounded || maxOccurs > 5) {
            score += 2.0;
        }

        return score;
    }

    /**
     * Get usage statistics
     */
    public Map<String, Object> getUsageStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOccurrences", totalOccurrences);
        stats.put("documentOccurrences", documentOccurrences);
        stats.put("occurrenceConfidence", occurrenceConfidence);
        stats.put("typeConfidence", typeConfidence);
        stats.put("patternConfidence", patternConfidence);
        stats.put("complexityScore", getComplexityScore());
        stats.put("childElementCount", childElements.size());
        stats.put("attributeCount", attributes.size());
        stats.put("averageChildCount", averageChildCount);
        stats.put("maxChildCount", maxChildCount);
        stats.put("distinctValues", observedValues.size());
        stats.put("contentType", contentType);
        return stats;
    }

    /**
     * Check if element structure matches another element (for type reuse)
     */
    public boolean isStructurallyCompatible(ElementInfo other) {
        if (other == null) return false;

        // Check content type compatibility
        if (!contentType.equals(other.contentType)) return false;

        // Check child elements
        if (childElements.size() != other.childElements.size()) return false;

        for (String childName : childElements.keySet()) {
            if (!other.childElements.containsKey(childName)) return false;
        }

        // Check attributes
        if (attributes.size() != other.attributes.size()) return false;

        for (String attrName : attributes.keySet()) {
            if (!other.attributes.containsKey(attrName)) return false;

            AttributeInfo thisAttr = attributes.get(attrName);
            AttributeInfo otherAttr = other.attributes.get(attrName);

            if (!thisAttr.getInferredType().equals(otherAttr.getInferredType())) {
                return false;
            }
        }

        return true;
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

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getXpath() {
        return xpath;
    }

    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public boolean isHasAttributes() {
        return hasAttributes;
    }

    public void setHasAttributes(boolean hasAttributes) {
        this.hasAttributes = hasAttributes;
    }

    public boolean isHasChildElements() {
        return hasChildElements;
    }

    public void setHasChildElements(boolean hasChildElements) {
        this.hasChildElements = hasChildElements;
    }

    public boolean isHasTextContent() {
        return hasTextContent;
    }

    public void setHasTextContent(boolean hasTextContent) {
        this.hasTextContent = hasTextContent;
    }

    public boolean isHasMixedContent() {
        return hasMixedContent;
    }

    public void setHasMixedContent(boolean hasMixedContent) {
        this.hasMixedContent = hasMixedContent;
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

    public boolean isComplexType() {
        return isComplexType;
    }

    public void setComplexType(boolean complexType) {
        isComplexType = complexType;
    }

    public String getComplexTypeName() {
        return complexTypeName;
    }

    public void setComplexTypeName(String complexTypeName) {
        this.complexTypeName = complexTypeName;
    }

    public Map<String, List<ElementInfo>> getChildElements() {
        return childElements;
    }

    public void setChildElements(Map<String, List<ElementInfo>> childElements) {
        this.childElements = childElements;
    }

    public Map<String, AttributeInfo> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, AttributeInfo> attributes) {
        this.attributes = attributes;
    }

    public List<ElementInfo> getAllChildren() {
        return allChildren;
    }

    public void setAllChildren(List<ElementInfo> allChildren) {
        this.allChildren = allChildren;
    }

    public int getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    public int getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(int maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public boolean isUnbounded() {
        return unbounded;
    }

    public void setUnbounded(boolean unbounded) {
        this.unbounded = unbounded;
    }

    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    public void setTotalOccurrences(int totalOccurrences) {
        this.totalOccurrences = totalOccurrences;
    }

    public int getDocumentOccurrences() {
        return documentOccurrences;
    }

    public void setDocumentOccurrences(int documentOccurrences) {
        this.documentOccurrences = documentOccurrences;
    }

    public double getOccurrenceConfidence() {
        return occurrenceConfidence;
    }

    public void setOccurrenceConfidence(double occurrenceConfidence) {
        this.occurrenceConfidence = occurrenceConfidence;
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

    public Map<String, Integer> getValueFrequency() {
        return valueFrequency;
    }

    public void setValueFrequency(Map<String, Integer> valueFrequency) {
        this.valueFrequency = valueFrequency;
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

    public boolean isNillable() {
        return nillable;
    }

    public void setNillable(boolean nillable) {
        this.nillable = nillable;
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

    public int getAverageChildCount() {
        return averageChildCount;
    }

    public void setAverageChildCount(int averageChildCount) {
        this.averageChildCount = averageChildCount;
    }

    public int getMaxChildCount() {
        return maxChildCount;
    }

    public void setMaxChildCount(int maxChildCount) {
        this.maxChildCount = maxChildCount;
    }

    public Map<String, Double> getChildFrequencies() {
        return childFrequencies;
    }

    public void setChildFrequencies(Map<String, Double> childFrequencies) {
        this.childFrequencies = childFrequencies;
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

    public Set<String> getSourceDocuments() {
        return sourceDocuments;
    }

    public void setSourceDocuments(Set<String> sourceDocuments) {
        this.sourceDocuments = sourceDocuments;
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

    public Map<String, Object> getAnalysisContext() {
        return analysisContext;
    }

    public void setAnalysisContext(Map<String, Object> analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Override
    public String toString() {
        return String.format("ElementInfo{name='%s', type='%s', confidence=%.2f, occurrences=%d, children=%d}",
                qualifiedName, inferredType, typeConfidence, totalOccurrences, childElements.size());
    }
}