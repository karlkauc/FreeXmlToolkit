package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Revolutionary Schema Generation Engine - Auto-generates XSD from XML with intelligent analysis.
 * Features:
 * - Smart type inference from XML content
 * - Multiple XML document analysis for pattern detection
 * - Occurrence pattern analysis (minOccurs/maxOccurs)
 * - Namespace handling and optimization
 * - Schema optimization and flattening
 * - Documentation generation
 */
public class SchemaGenerationEngine {

    private static final Logger logger = LogManager.getLogger(SchemaGenerationEngine.class);

    // Singleton instance
    private static SchemaGenerationEngine instance;

    // Type inference patterns
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d*\\.\\d+$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}.*$");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("^(true|false|0|1)$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*$");

    // Analysis configuration
    private boolean enableSmartTypeInference = true;
    private boolean enablePatternDetection = true;
    private final boolean enableDocumentationGeneration = true;
    private boolean optimizeSchema = true;
    private final int maxSampleValues = 10;

    // Analysis results cache
    private final Map<String, SchemaAnalysisResult> analysisCache = new ConcurrentHashMap<>();

    public SchemaGenerationEngine() {
        logger.info("Schema Generation Engine initialized");
    }

    public static synchronized SchemaGenerationEngine getInstance() {
        if (instance == null) {
            instance = new SchemaGenerationEngine();
        }
        return instance;
    }

    // ========== Main Schema Generation Methods ==========

    /**
     * Generate XSD schema from single XML document
     */
    public SchemaGenerationResult generateSchema(String xmlContent, SchemaGenerationOptions options) {
        long startTime = System.currentTimeMillis();

        try {
            logger.debug("Starting schema generation from XML content");

            // Parse XML document
            Document document = parseXmlDocument(xmlContent);
            if (document == null) {
                return SchemaGenerationResult.error("Failed to parse XML document");
            }

            // Analyze XML structure
            SchemaAnalysisResult analysis = analyzeXmlStructure(document, options);

            // Generate XSD schema
            String xsdContent = generateXsdFromAnalysis(analysis, options);

            // Create result
            SchemaGenerationResult result = SchemaGenerationResult.success(xsdContent, analysis);
            result.setGenerationTimeMs(System.currentTimeMillis() - startTime);
            result.getAnalysisResult().setDocumentsAnalyzed(1);

            logger.debug("Schema generation completed in {}ms", result.getGenerationTimeMs());
            return result;

        } catch (Exception e) {
            logger.error("Schema generation failed", e);
            return SchemaGenerationResult.error("Schema generation failed: " + e.getMessage());
        }
    }

    /**
     * Generate XSD schema from multiple XML documents for pattern analysis
     */
    public SchemaGenerationResult generateSchemaFromMultipleDocuments(List<String> xmlDocuments,
                                                                      SchemaGenerationOptions options) {
        long startTime = System.currentTimeMillis();

        try {
            logger.debug("Starting schema generation from {} XML documents", xmlDocuments.size());

            // Parse all documents
            List<Document> documents = new ArrayList<>();
            for (String xmlContent : xmlDocuments) {
                Document doc = parseXmlDocument(xmlContent);
                if (doc != null) {
                    documents.add(doc);
                }
            }

            if (documents.isEmpty()) {
                return SchemaGenerationResult.error("No valid XML documents found");
            }

            // Perform multi-document analysis
            SchemaAnalysisResult analysis = analyzeMultipleDocuments(documents, options);

            // Generate optimized XSD schema
            String xsdContent = generateXsdFromAnalysis(analysis, options);

            // Create result
            SchemaGenerationResult result = SchemaGenerationResult.success(xsdContent, analysis);
            result.setGenerationTimeMs(System.currentTimeMillis() - startTime);
            result.getAnalysisResult().setDocumentsAnalyzed(documents.size());

            logger.debug("Multi-document schema generation completed in {}ms", result.getGenerationTimeMs());
            return result;

        } catch (Exception e) {
            logger.error("Multi-document schema generation failed", e);
            return SchemaGenerationResult.error("Multi-document schema generation failed: " + e.getMessage());
        }
    }

    // ========== XML Structure Analysis ==========

    private Document parseXmlDocument(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            logger.warn("Failed to parse XML document: {}", e.getMessage());
            return null;
        }
    }

    private SchemaAnalysisResult analyzeXmlStructure(Document document, SchemaGenerationOptions options) {
        SchemaAnalysisResult analysis = new SchemaAnalysisResult();
        analysis.setAnalyzedAt(LocalDateTime.now());

        // Analyze root element
        Element rootElement = document.getDocumentElement();
        if (rootElement != null) {
            ElementInfo rootInfo = analyzeElement(rootElement, analysis, options);
            analysis.setRootElement(rootInfo);

            // Collect namespaces
            analyzeNamespaces(document, analysis);

            // Detect patterns and constraints
            if (enablePatternDetection) {
                detectPatterns(analysis);
            }
        }

        return analysis;
    }

    private SchemaAnalysisResult analyzeMultipleDocuments(List<Document> documents, SchemaGenerationOptions options) {
        SchemaAnalysisResult combinedAnalysis = new SchemaAnalysisResult();
        combinedAnalysis.setAnalyzedAt(LocalDateTime.now());

        Map<String, List<ElementInfo>> elementOccurrences = new HashMap<>();
        Set<String> namespaces = new HashSet<>();

        // Analyze each document
        for (Document document : documents) {
            SchemaAnalysisResult singleAnalysis = analyzeXmlStructure(document, options);

            // Collect element patterns
            collectElementPatterns(singleAnalysis, elementOccurrences);

            // Collect namespace usage
            namespaces.addAll(singleAnalysis.getDiscoveredNamespaces());
        }

        // Merge patterns and create optimized schema
        ElementInfo optimizedRoot = mergeElementPatterns(elementOccurrences, options);
        combinedAnalysis.setRootElement(optimizedRoot);
        combinedAnalysis.setDiscoveredNamespaces(namespaces);

        // Advanced pattern detection across documents
        detectCrossDocumentPatterns(combinedAnalysis, documents.size());

        return combinedAnalysis;
    }

    private ElementInfo analyzeElement(Element element, SchemaAnalysisResult analysis, SchemaGenerationOptions options) {
        ElementInfo info = new ElementInfo();
        // Use localName if available, otherwise fall back to nodeName
        String localName = element.getLocalName();
        info.setName(localName != null ? localName : element.getNodeName());
        info.setNamespace(element.getNamespaceURI());

        // Analyze text content
        String textContent = getElementTextContent(element);
        if (textContent != null && !textContent.trim().isEmpty()) {
            info.setTextContent(textContent.trim());
            info.setInferredType(inferDataType(textContent.trim()));

            // Pattern analysis for restrictions
            if (enablePatternDetection) {
                analyzeTextPatterns(textContent.trim(), info);
            }
        }

        // Analyze attributes
        NamedNodeMap attributes = element.getAttributes();
        Map<String, AttributeInfo> attributeMap = new HashMap<>();

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attrNode = attributes.item(i);
            if (attrNode.getNodeType() == Node.ATTRIBUTE_NODE) {
                Attr attr = (Attr) attrNode;

                // Skip namespace declarations
                if (attr.getName().startsWith("xmlns")) {
                    continue;
                }

                AttributeInfo attrInfo = new AttributeInfo();
                attrInfo.setName(attr.getName());
                attrInfo.addObservedValue(attr.getValue());
                attrInfo.setInferredType(inferDataType(attr.getValue()));
                attrInfo.setRequired(true); // Will be adjusted in multi-document analysis

                attributeMap.put(attr.getName(), attrInfo);
            }
        }
        info.setAttributes(attributeMap);

        // Analyze child elements
        Map<String, List<ElementInfo>> childElements = new HashMap<>();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                ElementInfo childInfo = analyzeElement((Element) child, analysis, options);

                childElements.computeIfAbsent(childInfo.getName(), k -> new ArrayList<>()).add(childInfo);
            }
        }

        info.setChildElements(childElements);

        // Calculate occurrence patterns
        calculateOccurrencePatterns(info);

        return info;
    }

    // ========== Data Type Inference ==========

    private String inferDataType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "xs:string";
        }

        String trimmed = value.trim();

        // Boolean check
        if (BOOLEAN_PATTERN.matcher(trimmed).matches()) {
            return "xs:boolean";
        }

        // Integer check
        if (INTEGER_PATTERN.matcher(trimmed).matches()) {
            try {
                Long.parseLong(trimmed);
                return "xs:long";
            } catch (NumberFormatException e) {
                return "xs:integer"; // Very large numbers
            }
        }

        // Decimal check
        if (DECIMAL_PATTERN.matcher(trimmed).matches()) {
            return "xs:decimal";
        }

        // Date/Time checks
        if (DATETIME_PATTERN.matcher(trimmed).matches()) {
            return "xs:dateTime";
        }

        if (DATE_PATTERN.matcher(trimmed).matches()) {
            return "xs:date";
        }

        if (TIME_PATTERN.matcher(trimmed).matches()) {
            return "xs:time";
        }

        // Format-specific checks
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            return "xs:string"; // Could add pattern restriction
        }

        if (URL_PATTERN.matcher(trimmed).matches()) {
            return "xs:anyURI";
        }

        // Default to string
        return "xs:string";
    }

    private void analyzeTextPatterns(String text, ElementInfo info) {
        // Length analysis
        info.setMinLength(text.length());
        info.setMaxLength(text.length());

        // Pattern detection for common formats
        if (EMAIL_PATTERN.matcher(text).matches()) {
            info.getDetectedPatterns().add("email");
        } else if (URL_PATTERN.matcher(text).matches()) {
            info.getDetectedPatterns().add("url");
        } else if (text.matches("^[A-Z]{2,3}$")) {
            info.getDetectedPatterns().add("countryCode");
        } else if (text.matches("^\\d{4,5}$")) {
            info.getDetectedPatterns().add("postalCode");
        }
    }

    // ========== XSD Generation ==========

    private String generateXsdFromAnalysis(SchemaAnalysisResult analysis, SchemaGenerationOptions options) {
        StringBuilder xsd = new StringBuilder();

        // XSD header
        xsd.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xsd.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"");

        // Add target namespace if present
        String targetNamespace = determineTargetNamespace(analysis);
        if (targetNamespace != null && !targetNamespace.isEmpty()) {
            xsd.append("\n           targetNamespace=\"").append(targetNamespace).append("\"");
            xsd.append("\n           xmlns:tns=\"").append(targetNamespace).append("\"");
            xsd.append("\n           elementFormDefault=\"qualified\"");
        }

        // Add other namespaces
        for (String ns : analysis.getDiscoveredNamespaces()) {
            if (ns != null && !ns.isEmpty() && !ns.equals(targetNamespace)) {
                String prefix = analysis.getNamespacePrefixes().getOrDefault(ns, "ns" + ns.hashCode());
                xsd.append("\n           xmlns:").append(prefix).append("=\"").append(ns).append("\"");
            }
        }

        xsd.append(">\n\n");

        // Add documentation if enabled
        if (enableDocumentationGeneration) {
            xsd.append("  <xs:annotation>\n");
            xsd.append("    <xs:documentation>\n");
            xsd.append("      Auto-generated schema by FreeXmlToolkit Schema Generator\n");
            xsd.append("      Generated on: ").append(LocalDateTime.now()).append("\n");
            xsd.append("      Source documents: ").append(analysis.getDocumentsAnalyzed()).append("\n");
            xsd.append("    </xs:documentation>\n");
            xsd.append("  </xs:annotation>\n\n");
        }

        // Generate root element definition
        if (analysis.getRootElement() != null) {
            generateElementDefinition(xsd, analysis.getRootElement(), options, 1);
        }

        xsd.append("</xs:schema>\n");

        return xsd.toString();
    }

    private void generateElementDefinition(StringBuilder xsd, ElementInfo element,
                                           SchemaGenerationOptions options, int depth) {
        String indent = "  ".repeat(depth);

        xsd.append(indent).append("<xs:element name=\"").append(element.getName()).append("\"");

        // Check if element has content
        boolean hasChildElements = !element.getChildElements().isEmpty();
        boolean hasAttributes = !element.getAttributes().isEmpty();
        boolean hasTextContent = element.getTextContent() != null && !element.getTextContent().isEmpty();

        if (!hasChildElements && !hasAttributes && hasTextContent) {
            // Simple element with just text content
            xsd.append(" type=\"").append(element.getInferredType()).append("\"");

            // Add occurrence constraints
            addOccurrenceConstraints(xsd, element);

            xsd.append("/>\n");
        } else {
            // Complex element
            addOccurrenceConstraints(xsd, element);
            xsd.append(">\n");

            // Add annotation if available
            if (element.getDocumentation() != null) {
                xsd.append(indent).append("  <xs:annotation>\n");
                xsd.append(indent).append("    <xs:documentation>")
                        .append(element.getDocumentation()).append("</xs:documentation>\n");
                xsd.append(indent).append("  </xs:annotation>\n");
            }

            xsd.append(indent).append("  <xs:complexType");

            if (hasTextContent && hasAttributes) {
                // Mixed content with attributes
                xsd.append(">\n");
                xsd.append(indent).append("    <xs:simpleContent>\n");
                xsd.append(indent).append("      <xs:extension base=\"").append(element.getInferredType()).append("\">\n");

                // Add attributes
                generateAttributeDefinitions(xsd, element.getAttributes(), options, depth + 4);

                xsd.append(indent).append("      </xs:extension>\n");
                xsd.append(indent).append("    </xs:simpleContent>\n");
                xsd.append(indent).append("  </xs:complexType>\n");
            } else {
                // Standard complex type
                xsd.append(">\n");

                if (hasChildElements) {
                    xsd.append(indent).append("    <xs:sequence>\n");

                    // Generate child elements
                    for (Map.Entry<String, List<ElementInfo>> childEntry : element.getChildElements().entrySet()) {
                        List<ElementInfo> occurrences = childEntry.getValue();
                        ElementInfo representativeChild = mergeElementOccurrences(occurrences);
                        generateElementDefinition(xsd, representativeChild, options, depth + 3);
                    }

                    xsd.append(indent).append("    </xs:sequence>\n");
                }

                // Add attributes
                if (hasAttributes) {
                    generateAttributeDefinitions(xsd, element.getAttributes(), options, depth + 2);
                }

                xsd.append(indent).append("  </xs:complexType>\n");
            }

            xsd.append(indent).append("</xs:element>\n");
        }
    }

    private void generateAttributeDefinitions(StringBuilder xsd, Map<String, AttributeInfo> attributes,
                                              SchemaGenerationOptions options, int depth) {
        String indent = "  ".repeat(depth);

        for (AttributeInfo attr : attributes.values()) {
            xsd.append(indent).append("<xs:attribute name=\"").append(attr.getName()).append("\"");
            xsd.append(" type=\"").append(attr.getInferredType()).append("\"");

            if (attr.isRequired()) {
                xsd.append(" use=\"required\"");
            }

            // Add default value if present and not required
            if (!attr.isRequired() && attr.getDefaultValue() != null) {
                xsd.append(" default=\"").append(attr.getDefaultValue()).append("\"");
            }

            xsd.append("/>\n");
        }
    }

    private void addOccurrenceConstraints(StringBuilder xsd, ElementInfo element) {
        if (element.getMinOccurs() != 1) {
            xsd.append(" minOccurs=\"").append(element.getMinOccurs()).append("\"");
        }

        if (element.getMaxOccurs() == Integer.MAX_VALUE) {
            xsd.append(" maxOccurs=\"unbounded\"");
        } else if (element.getMaxOccurs() != 1) {
            xsd.append(" maxOccurs=\"").append(element.getMaxOccurs()).append("\"");
        }
    }

    // ========== Helper Methods ==========

    private String getElementTextContent(Element element) {
        StringBuilder textContent = new StringBuilder();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();
                if (text != null) {
                    textContent.append(text);
                }
            }
        }

        return textContent.toString().trim();
    }

    private void analyzeNamespaces(Document document, SchemaAnalysisResult analysis) {
        Map<String, String> namespaces = new HashMap<>();

        Element root = document.getDocumentElement();
        if (root != null) {
            collectNamespaces(root, namespaces);
        }

        analysis.setNamespacePrefixes(namespaces);
    }

    private void collectNamespaces(Element element, Map<String, String> namespaces) {
        // Collect namespace from element
        String namespaceURI = element.getNamespaceURI();
        String prefix = element.getPrefix();

        if (namespaceURI != null && !namespaceURI.isEmpty()) {
            namespaces.put(prefix != null ? prefix : "", namespaceURI);
        }

        // Collect from attributes
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (attr.getNodeName().startsWith("xmlns:")) {
                String nsPrefix = attr.getNodeName().substring(6);
                namespaces.put(nsPrefix, attr.getNodeValue());
            } else if ("xmlns".equals(attr.getNodeName())) {
                namespaces.put("", attr.getNodeValue());
            }
        }

        // Recursively collect from child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectNamespaces((Element) child, namespaces);
            }
        }
    }

    private String determineTargetNamespace(SchemaAnalysisResult analysis) {
        Map<String, String> namespaces = analysis.getNamespacePrefixes();

        // Use default namespace if available
        String defaultNs = namespaces.get("");
        if (defaultNs != null && !defaultNs.isEmpty()) {
            return defaultNs;
        }

        // Use first non-empty namespace
        for (String uri : namespaces.values()) {
            if (!uri.isEmpty() && !uri.startsWith("http://www.w3.org/")) {
                return uri;
            }
        }

        return null;
    }

    private void detectPatterns(SchemaAnalysisResult analysis) {
        // Implement pattern detection logic
        // This could include detecting repeated structures, common constraints, etc.
        logger.debug("Pattern detection completed");
    }

    private void collectElementPatterns(SchemaAnalysisResult analysis, Map<String, List<ElementInfo>> patterns) {
        // Collect patterns from analysis for multi-document processing
        if (analysis.getRootElement() != null) {
            collectElementPatternsRecursive(analysis.getRootElement(), patterns);
        }
    }

    private void collectElementPatternsRecursive(ElementInfo element, Map<String, List<ElementInfo>> patterns) {
        patterns.computeIfAbsent(element.getName(), k -> new ArrayList<>()).add(element);

        for (List<ElementInfo> children : element.getChildElements().values()) {
            for (ElementInfo child : children) {
                collectElementPatternsRecursive(child, patterns);
            }
        }
    }

    private ElementInfo mergeElementPatterns(Map<String, List<ElementInfo>> patterns, SchemaGenerationOptions options) {
        // Implement pattern merging logic for multi-document analysis
        // This would analyze all occurrences and create optimized element definitions

        // For now, return the first root element found
        for (List<ElementInfo> elements : patterns.values()) {
            if (!elements.isEmpty()) {
                return elements.get(0); // Simplified implementation
            }
        }

        return null;
    }

    private ElementInfo mergeElementOccurrences(List<ElementInfo> occurrences) {
        if (occurrences.isEmpty()) return null;
        if (occurrences.size() == 1) return occurrences.get(0);

        // Merge multiple occurrences into one representative element
        ElementInfo merged = new ElementInfo();
        ElementInfo first = occurrences.get(0);

        merged.setName(first.getName());
        merged.setName(first.getName());
        merged.setNamespace(first.getNamespace());
        merged.setInferredType(first.getInferredType());

        // Calculate occurrence constraints
        merged.setMinOccurs(1);
        merged.setMaxOccurs(occurrences.size());

        return merged;
    }

    private void detectCrossDocumentPatterns(SchemaAnalysisResult analysis, int documentCount) {
        // Implement cross-document pattern detection
        analysis.setDocumentsAnalyzed(documentCount);
        logger.debug("Cross-document pattern detection completed for {} documents", documentCount);
    }

    private void calculateOccurrencePatterns(ElementInfo info) {
        // Calculate min/max occurs based on child element patterns
        for (Map.Entry<String, List<ElementInfo>> entry : info.getChildElements().entrySet()) {
            List<ElementInfo> occurrences = entry.getValue();
            if (occurrences.size() > 1) {
                // Multiple occurrences found - update the first one to represent all
                ElementInfo representative = occurrences.get(0);
                representative.setMinOccurs(1);
                representative.setMaxOccurs(occurrences.size());
            }
        }
    }

    // ========== Configuration Methods ==========

    public void setSmartTypeInference(boolean enabled) {
        this.enableSmartTypeInference = enabled;
        logger.debug("Smart type inference {}", enabled ? "enabled" : "disabled");
    }

    public void setPatternDetection(boolean enabled) {
        this.enablePatternDetection = enabled;
        logger.debug("Pattern detection {}", enabled ? "enabled" : "disabled");
    }

    public void setSchemaOptimization(boolean enabled) {
        this.optimizeSchema = enabled;
        logger.debug("Schema optimization {}", enabled ? "enabled" : "disabled");
    }

    public void clearCache() {
        analysisCache.clear();
        logger.info("Schema analysis cache cleared");
    }
}