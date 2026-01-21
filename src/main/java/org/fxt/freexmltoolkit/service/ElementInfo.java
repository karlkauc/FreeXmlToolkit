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

    /**
     * Creates a new ElementInfo with default values.
     */
    public ElementInfo() {
        this.firstSeen = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Creates a new ElementInfo with the specified name.
     *
     * @param name the element name
     */
    public ElementInfo(String name) {
        this();
        this.name = name;
        this.qualifiedName = name;
    }

    /**
     * Creates a new ElementInfo with the specified name and namespace.
     *
     * @param name the element name
     * @param namespace the namespace URI, may be null
     */
    public ElementInfo(String name, String namespace) {
        this(name);
        this.namespace = namespace;
        this.qualifiedName = (namespace != null && !namespace.isEmpty())
                ? namespace + ":" + name : name;
    }

    // ========== Content Analysis Methods ==========

    /**
     * Adds text content for analysis. Updates content type and infers type from the content.
     *
     * @param text the text content to add
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
     * Adds a child element to this element.
     *
     * @param child the child element to add
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
     * Adds an attribute to this element.
     *
     * @param attribute the attribute to add
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
     * Records an occurrence of this element in a document.
     *
     * @param documentId the identifier of the document where this element occurred
     */
    public void recordOccurrence(String documentId) {
        totalOccurrences++;
        if (documentId != null) {
            sourceDocuments.add(documentId);
        }
        this.lastSeen = LocalDateTime.now();
    }

    /**
     * Analyzes occurrence patterns across all documents.
     *
     * @param totalDocuments the total number of documents analyzed
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
     * Calculates the complexity score of this element based on content type,
     * child elements, attributes, depth, and occurrence patterns.
     *
     * @return the complexity score as a double value
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
     * Returns usage statistics including occurrence counts, confidence levels,
     * and structural information.
     *
     * @return a map containing various usage statistics
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
     * Checks if this element's structure is compatible with another element for type reuse.
     * Elements are compatible if they have the same content type, child elements, and attributes.
     *
     * @param other the other element to compare with
     * @return true if the structures are compatible, false otherwise
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

    /**
     * Returns the element name.
     *
     * @return the element name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the element name.
     *
     * @param name the element name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the namespace URI.
     *
     * @return the namespace URI, may be null
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace URI.
     *
     * @param namespace the namespace URI
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Returns the qualified name (namespace:name).
     *
     * @return the qualified name
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * Sets the qualified name.
     *
     * @param qualifiedName the qualified name
     */
    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    /**
     * Returns the nesting depth of this element.
     *
     * @return the depth level (0 for root elements)
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the nesting depth.
     *
     * @param depth the depth level
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Returns the XPath expression to this element.
     *
     * @return the XPath expression
     */
    public String getXpath() {
        return xpath;
    }

    /**
     * Sets the XPath expression.
     *
     * @param xpath the XPath expression
     */
    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    /**
     * Returns the content type (empty, simple, complex, or mixed).
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the content type.
     *
     * @param contentType the content type
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Returns the text content of this element.
     *
     * @return the text content, may be null
     */
    public String getTextContent() {
        return textContent;
    }

    /**
     * Sets the text content.
     *
     * @param textContent the text content
     */
    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    /**
     * Checks if element has attributes.
     * @return true if element has attributes
     */
    public boolean isHasAttributes() {
        return hasAttributes;
    }

    /**
     * Sets whether element has attributes.
     * @param hasAttributes whether element has attributes
     */
    public void setHasAttributes(boolean hasAttributes) {
        this.hasAttributes = hasAttributes;
    }

    /**
     * Checks if element has child elements.
     * @return true if element has child elements
     */
    public boolean isHasChildElements() {
        return hasChildElements;
    }

    /**
     * Sets whether element has child elements.
     * @param hasChildElements whether element has child elements
     */
    public void setHasChildElements(boolean hasChildElements) {
        this.hasChildElements = hasChildElements;
    }

    /**
     * Checks if element has text content.
     * @return true if element has text content
     */
    public boolean isHasTextContent() {
        return hasTextContent;
    }

    /**
     * Sets whether element has text content.
     * @param hasTextContent whether element has text content
     */
    public void setHasTextContent(boolean hasTextContent) {
        this.hasTextContent = hasTextContent;
    }

    /**
     * Checks if element has mixed content.
     * @return true if element has mixed content
     */
    public boolean isHasMixedContent() {
        return hasMixedContent;
    }

    /**
     * Sets whether element has mixed content.
     * @param hasMixedContent whether element has mixed content
     */
    public void setHasMixedContent(boolean hasMixedContent) {
        this.hasMixedContent = hasMixedContent;
    }

    /**
     * Returns the inferred XSD type.
     * @return the inferred XSD type
     */
    public String getInferredType() {
        return inferredType;
    }

    /**
     * Sets the inferred XSD type.
     * @param inferredType the inferred XSD type
     */
    public void setInferredType(String inferredType) {
        this.inferredType = inferredType;
    }

    /**
     * Returns the original type from schema.
     * @return the original type from schema
     */
    public String getOriginalType() {
        return originalType;
    }

    /**
     * Sets the original type from schema.
     * @param originalType the original type from schema
     */
    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    /**
     * Returns the confidence level for type inference.
     * @return the confidence level (0.0-1.0)
     */
    public double getTypeConfidence() {
        return typeConfidence;
    }

    /**
     * Sets the confidence level for type inference.
     * @param typeConfidence the confidence level
     */
    public void setTypeConfidence(double typeConfidence) {
        this.typeConfidence = typeConfidence;
    }

    /**
     * Returns the set of possible XSD types.
     * @return set of possible XSD types
     */
    public Set<String> getPossibleTypes() {
        return possibleTypes;
    }

    /**
     * Sets the set of possible XSD types.
     * @param possibleTypes set of possible XSD types
     */
    public void setPossibleTypes(Set<String> possibleTypes) {
        this.possibleTypes = possibleTypes;
    }

    /**
     * Checks if element has complex type.
     * @return true if element has complex type
     */
    public boolean isComplexType() {
        return isComplexType;
    }

    /**
     * Sets whether element has complex type.
     * @param complexType whether element has complex type
     */
    public void setComplexType(boolean complexType) {
        isComplexType = complexType;
    }

    /**
     * Returns the complex type name.
     * @return the complex type name
     */
    public String getComplexTypeName() {
        return complexTypeName;
    }

    /**
     * Sets the complex type name.
     * @param complexTypeName the complex type name
     */
    public void setComplexTypeName(String complexTypeName) {
        this.complexTypeName = complexTypeName;
    }

    /**
     * Returns the map of child element names to their occurrences.
     *
     * @return map of child element names to their occurrences
     */
    public Map<String, List<ElementInfo>> getChildElements() {
        return childElements;
    }

    /**
     * Sets the map of child element names to their occurrences.
     *
     * @param childElements map of child element names to their occurrences
     */
    public void setChildElements(Map<String, List<ElementInfo>> childElements) {
        this.childElements = childElements;
    }

    /**
     * Returns the map of attribute names to their info.
     *
     * @return map of attribute names to their info
     */
    public Map<String, AttributeInfo> getAttributes() {
        return attributes;
    }

    /**
     * Sets the map of attribute names to their info.
     *
     * @param attributes map of attribute names to their info
     */
    public void setAttributes(Map<String, AttributeInfo> attributes) {
        this.attributes = attributes;
    }

    /**
     * Returns the list of all child elements.
     *
     * @return list of all child elements
     */
    public List<ElementInfo> getAllChildren() {
        return allChildren;
    }

    /**
     * Sets the list of all child elements.
     *
     * @param allChildren list of all child elements
     */
    public void setAllChildren(List<ElementInfo> allChildren) {
        this.allChildren = allChildren;
    }

    /**
     * Returns the minimum occurrences.
     *
     * @return minimum occurrences
     */
    public int getMinOccurs() {
        return minOccurs;
    }

    /**
     * Sets the minimum occurrences.
     *
     * @param minOccurs minimum occurrences
     */
    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    /**
     * Returns the maximum occurrences (-1 for unbounded).
     *
     * @return maximum occurrences (-1 for unbounded)
     */
    public int getMaxOccurs() {
        return maxOccurs;
    }

    /**
     * Sets the maximum occurrences.
     *
     * @param maxOccurs maximum occurrences
     */
    public void setMaxOccurs(int maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    /**
     * Checks if occurrences are unbounded.
     *
     * @return true if occurrences are unbounded
     */
    public boolean isUnbounded() {
        return unbounded;
    }

    /**
     * Sets whether occurrences are unbounded.
     *
     * @param unbounded whether occurrences are unbounded
     */
    public void setUnbounded(boolean unbounded) {
        this.unbounded = unbounded;
    }

    /**
     * Returns the total number of occurrences across all documents.
     *
     * @return total number of occurrences across all documents
     */
    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    /**
     * Sets the total number of occurrences.
     *
     * @param totalOccurrences total number of occurrences
     */
    public void setTotalOccurrences(int totalOccurrences) {
        this.totalOccurrences = totalOccurrences;
    }

    /**
     * Returns the number of documents containing this element.
     *
     * @return number of documents containing this element
     */
    public int getDocumentOccurrences() {
        return documentOccurrences;
    }

    /**
     * Sets the number of documents containing this element.
     *
     * @param documentOccurrences number of documents containing this element
     */
    public void setDocumentOccurrences(int documentOccurrences) {
        this.documentOccurrences = documentOccurrences;
    }

    /**
     * Returns the confidence level for occurrence patterns (0.0-1.0).
     *
     * @return confidence level for occurrence patterns (0.0-1.0)
     */
    public double getOccurrenceConfidence() {
        return occurrenceConfidence;
    }

    /**
     * Sets the confidence level for occurrence patterns.
     *
     * @param occurrenceConfidence confidence level for occurrence patterns
     */
    public void setOccurrenceConfidence(double occurrenceConfidence) {
        this.occurrenceConfidence = occurrenceConfidence;
    }

    /**
     * Returns the set of observed values for this element.
     *
     * @return set of observed values for this element
     */
    public Set<String> getObservedValues() {
        return observedValues;
    }

    /**
     * Sets the set of observed values.
     *
     * @param observedValues set of observed values
     */
    public void setObservedValues(Set<String> observedValues) {
        this.observedValues = observedValues;
    }

    /**
     * Returns the list of sample values (limited size).
     *
     * @return list of sample values (limited size)
     */
    public List<String> getSampleValues() {
        return sampleValues;
    }

    /**
     * Sets the list of sample values.
     *
     * @param sampleValues list of sample values
     */
    public void setSampleValues(List<String> sampleValues) {
        this.sampleValues = sampleValues;
    }

    /**
     * Returns the map of values to their frequency counts.
     *
     * @return map of values to their frequency counts
     */
    public Map<String, Integer> getValueFrequency() {
        return valueFrequency;
    }

    /**
     * Sets the map of values to their frequency counts.
     *
     * @param valueFrequency map of values to their frequency counts
     */
    public void setValueFrequency(Map<String, Integer> valueFrequency) {
        this.valueFrequency = valueFrequency;
    }

    /**
     * Returns the set of detected patterns.
     *
     * @return set of detected patterns
     */
    public Set<String> getDetectedPatterns() {
        return detectedPatterns;
    }

    /**
     * Sets the set of detected patterns.
     *
     * @param detectedPatterns set of detected patterns
     */
    public void setDetectedPatterns(Set<String> detectedPatterns) {
        this.detectedPatterns = detectedPatterns;
    }

    /**
     * Returns the map of pattern names to their counts.
     *
     * @return map of pattern names to their counts
     */
    public Map<String, Integer> getPatternCounts() {
        return patternCounts;
    }

    /**
     * Sets the map of pattern names to their counts.
     *
     * @param patternCounts map of pattern names to their counts
     */
    public void setPatternCounts(Map<String, Integer> patternCounts) {
        this.patternCounts = patternCounts;
    }

    /**
     * Returns the most commonly detected pattern.
     *
     * @return the most commonly detected pattern
     */
    public String getMostCommonPattern() {
        return mostCommonPattern;
    }

    /**
     * Sets the most commonly detected pattern.
     *
     * @param mostCommonPattern the most commonly detected pattern
     */
    public void setMostCommonPattern(String mostCommonPattern) {
        this.mostCommonPattern = mostCommonPattern;
    }

    /**
     * Returns the confidence level for pattern detection (0.0-1.0).
     *
     * @return confidence level for pattern detection (0.0-1.0)
     */
    public double getPatternConfidence() {
        return patternConfidence;
    }

    /**
     * Sets the confidence level for pattern detection.
     *
     * @param patternConfidence confidence level for pattern detection
     */
    public void setPatternConfidence(double patternConfidence) {
        this.patternConfidence = patternConfidence;
    }

    /**
     * Returns the minimum length constraint.
     *
     * @return minimum length constraint
     */
    public Integer getMinLength() {
        return minLength;
    }

    /**
     * Sets the minimum length constraint.
     *
     * @param minLength minimum length constraint
     */
    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    /**
     * Returns the maximum length constraint.
     *
     * @return maximum length constraint
     */
    public Integer getMaxLength() {
        return maxLength;
    }

    /**
     * Sets the maximum length constraint.
     *
     * @param maxLength maximum length constraint
     */
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Returns the minimum value constraint.
     *
     * @return minimum value constraint
     */
    public String getMinValue() {
        return minValue;
    }

    /**
     * Sets the minimum value constraint.
     *
     * @param minValue minimum value constraint
     */
    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    /**
     * Returns the maximum value constraint.
     *
     * @return maximum value constraint
     */
    public String getMaxValue() {
        return maxValue;
    }

    /**
     * Sets the maximum value constraint.
     *
     * @param maxValue maximum value constraint
     */
    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    /**
     * Returns the set of enumeration values.
     *
     * @return set of enumeration values
     */
    public Set<String> getEnumerationValues() {
        return enumerationValues;
    }

    /**
     * Sets the set of enumeration values.
     *
     * @param enumerationValues set of enumeration values
     */
    public void setEnumerationValues(Set<String> enumerationValues) {
        this.enumerationValues = enumerationValues;
    }

    /**
     * Returns the regex restriction pattern.
     *
     * @return the regex restriction pattern
     */
    public String getRestrictionPattern() {
        return restrictionPattern;
    }

    /**
     * Sets the regex restriction pattern.
     *
     * @param restrictionPattern the regex restriction pattern
     */
    public void setRestrictionPattern(String restrictionPattern) {
        this.restrictionPattern = restrictionPattern;
    }

    /**
     * Checks if element is nillable.
     *
     * @return true if element is nillable
     */
    public boolean isNillable() {
        return nillable;
    }

    /**
     * Sets whether element is nillable.
     *
     * @param nillable whether element is nillable
     */
    public void setNillable(boolean nillable) {
        this.nillable = nillable;
    }

    /**
     * Returns the map of statistics.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        return statistics;
    }

    /**
     * Sets the map of statistics.
     *
     * @param statistics map of statistics
     */
    public void setStatistics(Map<String, Object> statistics) {
        this.statistics = statistics;
    }

    /**
     * Returns the timestamp when element was first seen.
     *
     * @return timestamp when element was first seen
     */
    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    /**
     * Sets the timestamp when element was first seen.
     *
     * @param firstSeen timestamp when element was first seen
     */
    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    /**
     * Returns the timestamp when element was last seen.
     *
     * @return timestamp when element was last seen
     */
    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    /**
     * Sets the timestamp when element was last seen.
     *
     * @param lastSeen timestamp when element was last seen
     */
    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * Returns the average number of child elements.
     *
     * @return average number of child elements
     */
    public int getAverageChildCount() {
        return averageChildCount;
    }

    /**
     * Sets the average number of child elements.
     *
     * @param averageChildCount average number of child elements
     */
    public void setAverageChildCount(int averageChildCount) {
        this.averageChildCount = averageChildCount;
    }

    /**
     * Returns the maximum number of child elements observed.
     *
     * @return maximum number of child elements observed
     */
    public int getMaxChildCount() {
        return maxChildCount;
    }

    /**
     * Sets the maximum number of child elements.
     *
     * @param maxChildCount maximum number of child elements
     */
    public void setMaxChildCount(int maxChildCount) {
        this.maxChildCount = maxChildCount;
    }

    /**
     * Returns the map of child names to their occurrence frequencies.
     *
     * @return map of child names to their occurrence frequencies
     */
    public Map<String, Double> getChildFrequencies() {
        return childFrequencies;
    }

    /**
     * Sets the map of child names to their occurrence frequencies.
     *
     * @param childFrequencies map of child names to their occurrence frequencies
     */
    public void setChildFrequencies(Map<String, Double> childFrequencies) {
        this.childFrequencies = childFrequencies;
    }

    /**
     * Returns the documentation string.
     *
     * @return the documentation string
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation string.
     *
     * @param documentation the documentation string
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Returns the list of comments.
     *
     * @return list of comments
     */
    public List<String> getComments() {
        return comments;
    }

    /**
     * Sets the list of comments.
     *
     * @param comments list of comments
     */
    public void setComments(List<String> comments) {
        this.comments = comments;
    }

    /**
     * Returns the map of annotations.
     *
     * @return map of annotations
     */
    public Map<String, String> getAnnotations() {
        return annotations;
    }

    /**
     * Sets the map of annotations.
     *
     * @param annotations map of annotations
     */
    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    /**
     * Returns the set of source document identifiers.
     *
     * @return set of source document identifiers
     */
    public Set<String> getSourceDocuments() {
        return sourceDocuments;
    }

    /**
     * Sets the set of source document identifiers.
     *
     * @param sourceDocuments set of source document identifiers
     */
    public void setSourceDocuments(Set<String> sourceDocuments) {
        this.sourceDocuments = sourceDocuments;
    }

    /**
     * Returns the analysis depth level.
     *
     * @return the analysis depth level
     */
    public int getAnalysisDepth() {
        return analysisDepth;
    }

    /**
     * Sets the analysis depth level.
     *
     * @param analysisDepth the analysis depth level
     */
    public void setAnalysisDepth(int analysisDepth) {
        this.analysisDepth = analysisDepth;
    }

    /**
     * Checks if element has been analyzed.
     *
     * @return true if element has been analyzed
     */
    public boolean isAnalyzed() {
        return analyzed;
    }

    /**
     * Sets whether element has been analyzed.
     *
     * @param analyzed whether element has been analyzed
     */
    public void setAnalyzed(boolean analyzed) {
        this.analyzed = analyzed;
    }

    /**
     * Returns the timestamp when analysis was performed.
     *
     * @return timestamp when analysis was performed
     */
    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    /**
     * Sets the timestamp when analysis was performed.
     *
     * @param analyzedAt timestamp when analysis was performed
     */
    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    /**
     * Returns the map of analysis context data.
     *
     * @return map of analysis context data
     */
    public Map<String, Object> getAnalysisContext() {
        return analysisContext;
    }

    /**
     * Sets the map of analysis context data.
     *
     * @param analysisContext map of analysis context data
     */
    public void setAnalysisContext(Map<String, Object> analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Override
    public String toString() {
        return String.format("ElementInfo{name='%s', type='%s', confidence=%.2f, occurrences=%d, children=%d}",
                qualifiedName, inferredType, typeConfidence, totalOccurrences, childElements.size());
    }
}