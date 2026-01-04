package org.fxt.freexmltoolkit.controls.shared.utilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapter for integrating XSD schema information with IntelliSense.
 * Provides context-aware element and attribute suggestions based on XSD schema.
 */
public class XsdIntegrationAdapter {

    private static final Logger logger = LogManager.getLogger(XsdIntegrationAdapter.class);

    private XsdDocumentationData xsdDocumentationData;
    private Schema schema;
    private Document schemaDocument;
    private final Map<String, ElementInfo> elementInfoCache = new HashMap<>();
    private final Map<String, List<AttributeInfo>> attributeCache = new HashMap<>();
    private final Map<String, List<String>> childElementsCache = new HashMap<>();

    /**
     * Set the XSD documentation data for IntelliSense
     */
    public void setXsdDocumentationData(XsdDocumentationData data) {
        this.xsdDocumentationData = data;
        rebuildCache();
    }

    /**
     * Load XSD schema from file
     */
    public void loadSchema(File schemaFile) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.schema = factory.newSchema(schemaFile);

            // Parse schema document for detailed information
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            this.schemaDocument = dBuilder.parse(schemaFile);

            analyzeSchema();
            logger.info("Schema loaded successfully from: {}", schemaFile.getPath());
        } catch (Exception e) {
            logger.error("Failed to load schema: {}", e.getMessage(), e);
        }
    }

    /**
     * Load XSD schema from string
     */
    public void loadSchemaFromString(String schemaContent) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Source schemaSource = new StreamSource(new StringReader(schemaContent));
            this.schema = factory.newSchema(schemaSource);

            // Parse schema document
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            this.schemaDocument = dBuilder.parse(new org.xml.sax.InputSource(new StringReader(schemaContent)));

            analyzeSchema();
            logger.info("Schema loaded successfully from string");
        } catch (Exception e) {
            logger.error("Failed to load schema from string: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if schema is available
     */
    public boolean hasSchema() {
        return schema != null || xsdDocumentationData != null;
    }

    /**
     * Get available elements for a given context
     */
    public List<String> getAvailableElements() {
        return getAvailableElements(null);
    }

    /**
     * Get available elements for a given parent context.
     * Accepts both element name (e.g., "Person") and full XPath (e.g., "/Root/Person").
     */
    public List<String> getAvailableElements(String parentElement) {
        List<String> elements = new ArrayList<>();

        // Try XsdDocumentationData first
        if (xsdDocumentationData != null) {
            elements.addAll(getElementsFromDocumentationData(parentElement));
        }

        // Try parsed schema
        if (schemaDocument != null) {
            String elementName = extractElementName(parentElement);
            elements.addAll(getElementsFromSchemaDocument(elementName));
        }

        // Use cache if available (cache uses element names, not full paths)
        String elementName = extractElementName(parentElement);
        if (elementName != null && childElementsCache.containsKey(elementName)) {
            elements.addAll(childElementsCache.get(elementName));
        }

        // Remove duplicates but preserve XSD order (do NOT sort alphabetically)
        return elements.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Extracts the element name from a string that could be either a simple name or an XPath.
     *
     * @param elementOrPath either element name (e.g., "Person") or XPath (e.g., "/Root/Person")
     * @return the element name without path or predicates
     */
    private String extractElementName(String elementOrPath) {
        if (elementOrPath == null || elementOrPath.isEmpty()) {
            return null;
        }

        // If it looks like an XPath (contains /), extract the last part
        if (elementOrPath.contains("/")) {
            String[] parts = elementOrPath.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i];
                if (part != null && !part.isEmpty()) {
                    // Remove predicates like [1]
                    if (part.contains("[")) {
                        part = part.substring(0, part.indexOf('['));
                    }
                    // Remove namespace prefix
                    if (part.contains(":")) {
                        part = part.substring(part.indexOf(':') + 1);
                    }
                    return part;
                }
            }
        }

        return elementOrPath;
    }

    /**
     * Get available attributes for an element
     */
    public List<AttributeInfo> getAvailableAttributes(String elementName) {
        List<AttributeInfo> attributes = new ArrayList<>();

        // Check cache first
        if (attributeCache.containsKey(elementName)) {
            return attributeCache.get(elementName);
        }

        // Try XsdDocumentationData
        if (xsdDocumentationData != null) {
            attributes.addAll(getAttributesFromDocumentationData(elementName));
        }

        // Try parsed schema
        if (schemaDocument != null) {
            attributes.addAll(getAttributesFromSchemaDocument(elementName));
        }

        // Cache results
        if (!attributes.isEmpty()) {
            attributeCache.put(elementName, attributes);
        }

        return attributes;
    }

    /**
     * Get enumeration values for an attribute
     */
    public List<String> getAttributeEnumerationValues(String elementName, String attributeName) {
        List<String> values = new ArrayList<>();

        // Try XsdDocumentationData first
        if (xsdDocumentationData != null) {
            String xpath = "/" + elementName + "/@" + attributeName;
            XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);

            if (element != null && element.getRestrictionInfo() != null) {
                Map<String, List<String>> facets = element.getRestrictionInfo().facets();
                if (facets != null && facets.containsKey("enumeration")) {
                    values.addAll(facets.get("enumeration"));
                }
            }
        }

        // Try schema document
        if (schemaDocument != null && values.isEmpty()) {
            values.addAll(getEnumerationFromSchemaDocument(elementName, attributeName));
        }

        return values;
    }

    /**
     * Get documentation for an element
     */
    public String getElementDocumentation(String elementName) {
        // Try XsdDocumentationData first
        if (xsdDocumentationData != null) {
            XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get("/" + elementName);
            if (element != null && element.getDocumentations() != null && !element.getDocumentations().isEmpty()) {
                return element.getDocumentations().get(0).content();
            }
        }

        // Try element info cache
        if (elementInfoCache.containsKey(elementName)) {
            return elementInfoCache.get(elementName).documentation;
        }

        return null;
    }

    /**
     * Get documentation for an attribute
     */
    public String getAttributeDocumentation(String elementName, String attributeName) {
        List<AttributeInfo> attributes = getAvailableAttributes(elementName);
        for (AttributeInfo attr : attributes) {
            if (attr.name.equals(attributeName)) {
                return attr.documentation;
            }
        }
        return null;
    }

    /**
     * Validate if an element is valid in a given context
     */
    public boolean isValidElement(String parentElement, String childElement) {
        List<String> validChildren = getAvailableElements(parentElement);
        return validChildren.contains(childElement);
    }

    /**
     * Validate if an attribute is valid for an element
     */
    public boolean isValidAttribute(String elementName, String attributeName) {
        List<AttributeInfo> validAttributes = getAvailableAttributes(elementName);
        return validAttributes.stream().anyMatch(attr -> attr.name.equals(attributeName));
    }

    // Private helper methods

    private void rebuildCache() {
        elementInfoCache.clear();
        attributeCache.clear();
        childElementsCache.clear();

        if (xsdDocumentationData != null) {
            // Build caches from XsdDocumentationData
            Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

            for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
                String xpath = entry.getKey();
                XsdExtendedElement element = entry.getValue();

                // Cache element info
                String elementName = element.getElementName();
                if (elementName != null && !elementName.startsWith("@")) {
                    ElementInfo info = new ElementInfo();
                    info.name = elementName;
                    if (element.getDocumentations() != null && !element.getDocumentations().isEmpty()) {
                        info.documentation = element.getDocumentations().get(0).content();
                    }
                    info.type = element.getElementType();
                    info.minOccurs = "";
                    info.maxOccurs = "";

                    elementInfoCache.put(elementName, info);
                }

                // Cache parent-child relationships
                String[] pathParts = xpath.split("/");
                if (pathParts.length >= 2) {
                    String parent = pathParts[pathParts.length - 2];
                    String child = pathParts[pathParts.length - 1];

                    if (!child.startsWith("@")) {
                        childElementsCache.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                    }
                }
            }
        }

        logger.debug("Cache rebuilt with {} elements", elementInfoCache.size());
    }

    private void analyzeSchema() {
        if (schemaDocument == null) return;

        try {
            // Find all element definitions
            NodeList elements = schemaDocument.getElementsByTagNameNS(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");

            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                String name = element.getAttribute("name");

                if (name != null && !name.isEmpty()) {
                    ElementInfo info = new ElementInfo();
                    info.name = name;
                    info.type = element.getAttribute("type");
                    info.minOccurs = element.getAttribute("minOccurs");
                    info.maxOccurs = element.getAttribute("maxOccurs");

                    // Extract documentation
                    NodeList annotations = element.getElementsByTagNameNS(
                            XMLConstants.W3C_XML_SCHEMA_NS_URI, "documentation");
                    if (annotations.getLength() > 0) {
                        info.documentation = annotations.item(0).getTextContent().trim();
                    }

                    elementInfoCache.put(name, info);

                    // Extract attributes
                    extractAttributes(element, name);

                    // Extract child elements
                    extractChildElements(element, name);
                }
            }

            logger.debug("Schema analysis complete: {} elements found", elementInfoCache.size());

        } catch (Exception e) {
            logger.error("Error analyzing schema: {}", e.getMessage(), e);
        }
    }

    private void extractAttributes(Element element, String elementName) {
        NodeList attributes = element.getElementsByTagNameNS(
                XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");

        List<AttributeInfo> attrList = new ArrayList<>();

        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            AttributeInfo info = new AttributeInfo();
            info.name = attr.getAttribute("name");
            info.type = attr.getAttribute("type");
            info.use = attr.getAttribute("use");
            info.defaultValue = attr.getAttribute("default");

            // Extract documentation
            NodeList docs = attr.getElementsByTagNameNS(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI, "documentation");
            if (docs.getLength() > 0) {
                info.documentation = docs.item(0).getTextContent().trim();
            }

            attrList.add(info);
        }

        if (!attrList.isEmpty()) {
            attributeCache.put(elementName, attrList);
        }
    }

    private void extractChildElements(Element element, String elementName) {
        NodeList sequences = element.getElementsByTagNameNS(
                XMLConstants.W3C_XML_SCHEMA_NS_URI, "sequence");

        List<String> children = new ArrayList<>();

        for (int i = 0; i < sequences.getLength(); i++) {
            NodeList childElements = sequences.item(i).getChildNodes();
            for (int j = 0; j < childElements.getLength(); j++) {
                Node child = childElements.item(j);
                if (child instanceof Element && "element".equals(child.getLocalName())) {
                    String childName = ((Element) child).getAttribute("ref");
                    if (childName == null || childName.isEmpty()) {
                        childName = ((Element) child).getAttribute("name");
                    }
                    if (childName != null && !childName.isEmpty()) {
                        children.add(childName);
                    }
                }
            }
        }

        if (!children.isEmpty()) {
            childElementsCache.put(elementName, children);
        }
    }

    private List<String> getElementsFromDocumentationData(String parentElement) {
        List<String> elements = new ArrayList<>();

        if (xsdDocumentationData == null) {
            logger.debug("XSD documentation data is null");
            return elements;
        }

        Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

        // Build the parent path for searching
        String parentPath;
        if (parentElement == null || parentElement.isEmpty()) {
            parentPath = "/";
        } else if (parentElement.startsWith("/")) {
            // Already a full XPath - use it directly, ensure it ends with /
            parentPath = parentElement.endsWith("/") ? parentElement : parentElement + "/";
        } else {
            // Simple element name - create path prefix
            parentPath = "/" + parentElement + "/";
        }

        logger.debug("Searching for children of '{}' using parent path '{}'", parentElement, parentPath);
        logger.debug("Available XPaths in elementMap: {}", elementMap.size() > 10 ? "[" + elementMap.size() + " total]" : elementMap.keySet());

        // First, try direct match with full parent path
        for (String xpath : elementMap.keySet()) {
            if (xpath.startsWith(parentPath) && !xpath.contains("@")) {
                String remaining = xpath.substring(parentPath.length());
                if (!remaining.contains("/")) {
                    if (!elements.contains(remaining)) {
                        elements.add(remaining);
                        logger.debug("Found direct child: '{}' from xpath '{}'", remaining, xpath);
                    }
                }
            }
        }

        // If no results found and it was a simple element name, try searching all paths ending with that element
        if (elements.isEmpty() && parentElement != null && !parentElement.startsWith("/")) {
            String searchSuffix = "/" + parentElement + "/";
            for (String xpath : elementMap.keySet()) {
                // Find paths that contain the element as a parent (e.g., "*/Person/*")
                int idx = xpath.lastIndexOf(searchSuffix);
                if (idx >= 0 && !xpath.contains("@")) {
                    String remaining = xpath.substring(idx + searchSuffix.length());
                    if (!remaining.contains("/") && !remaining.isEmpty()) {
                        if (!elements.contains(remaining)) {
                            elements.add(remaining);
                            logger.debug("Found child '{}' via suffix search from xpath '{}'", remaining, xpath);
                        }
                    }
                }
            }
        }

        logger.debug("Found {} direct children for parent '{}': {}", elements.size(), parentElement, elements);
        return elements;
    }

    private List<String> getElementsFromSchemaDocument(String parentElement) {
        if (parentElement == null || !childElementsCache.containsKey(parentElement)) {
            // Return all root elements
            return new ArrayList<>(elementInfoCache.keySet());
        }
        return childElementsCache.getOrDefault(parentElement, new ArrayList<>());
    }

    private List<AttributeInfo> getAttributesFromDocumentationData(String elementName) {
        List<AttributeInfo> attributes = new ArrayList<>();

        if (xsdDocumentationData == null) return attributes;

        Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();
        String elementPath = "/" + elementName + "/@";

        for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
            String xpath = entry.getKey();
            if (xpath.startsWith(elementPath)) {
                String attrName = xpath.substring(elementPath.length());
                XsdExtendedElement element = entry.getValue();

                AttributeInfo info = new AttributeInfo();
                info.name = attrName;
                info.type = element.getElementType();
                if (element.getDocumentations() != null && !element.getDocumentations().isEmpty()) {
                    info.documentation = element.getDocumentations().get(0).content();
                }
                info.use = "";

                attributes.add(info);
            }
        }

        return attributes;
    }

    private List<AttributeInfo> getAttributesFromSchemaDocument(String elementName) {
        return attributeCache.getOrDefault(elementName, new ArrayList<>());
    }

    private List<String> getEnumerationFromSchemaDocument(String elementName, String attributeName) {
        // This would require deeper schema parsing to extract enumeration values
        // For now, return empty list
        return new ArrayList<>();
    }

    /**
     * Get enumeration values for an element's text content.
     *
     * @deprecated Use {@link #getElementEnumerationValuesByXPath(String)} instead.
     *             This method only searches by element name which can return incorrect
     *             values when multiple elements share the same name but have different
     *             enumeration constraints (e.g., multiple "Version" elements in FundsXML).
     * @param elementName the element name (without XPath)
     * @return list of enumeration values (may include incorrect values from other elements)
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public List<String> getElementEnumerationValues(String elementName) {
        List<String> values = new ArrayList<>();
        logger.warn("⚠️ Using deprecated getElementEnumerationValues(). Use getElementEnumerationValuesByXPath() instead for accurate results.");
        logger.debug("🔍 Looking for enumeration values for element: '{}'", elementName);

        // Try XsdDocumentationData first with simple XPath patterns
        // NOTE: This approach is inherently flawed because it doesn't consider the full XPath context.
        // Multiple elements with the same name may exist at different paths with different constraints.
        if (xsdDocumentationData != null) {
            logger.debug("📊 XSD documentation data available, searching...");

            // Try simple XPath - direct match only
            String xpath = "/" + elementName;
            XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
            logger.debug("🔍 Trying xpath '{}': {}", xpath, element != null ? "found" : "not found");

            if (element != null && element.getRestrictionInfo() != null) {
                Map<String, List<String>> facets = element.getRestrictionInfo().facets();
                if (facets != null && facets.containsKey("enumeration")) {
                    List<String> enumValues = facets.get("enumeration");
                    for (String enumValue : enumValues) {
                        String trimmedValue = enumValue.trim();
                        if (!values.contains(trimmedValue)) {
                            values.add(trimmedValue);
                        }
                    }
                    logger.debug("✅ Found enumeration values for element '{}': {}", elementName, values);
                }
            }
            // NOTE: Removed the fallback that searched ALL elements by name.
            // This was causing incorrect results when multiple elements share the same name.
            // Callers should use getElementEnumerationValuesByXPath() for accurate results.
        } else {
            logger.debug("❌ No XSD documentation data available");
        }

        // Try schema document
        if (schemaDocument != null && values.isEmpty()) {
            logger.debug("🔍 Trying schema document fallback...");
            values.addAll(getElementEnumerationFromSchemaDocument(elementName));
        }

        logger.debug("🏁 Final enumeration values for '{}': {}", elementName, values);
        return values;
    }

    /**
     * Get enumeration values for an element by XPath
     */
    public List<String> getElementEnumerationValuesByXPath(String xpath) {
        List<String> values = new ArrayList<>();

        if (xsdDocumentationData != null) {
            XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);

            if (element != null && element.getRestrictionInfo() != null) {
                Map<String, List<String>> facets = element.getRestrictionInfo().facets();
                if (facets != null && facets.containsKey("enumeration")) {
                    values.addAll(facets.get("enumeration"));
                    logger.debug("Found enumeration values for xpath '{}': {}", xpath, values);
                }
            }
        }

        return values;
    }

    private List<String> getElementEnumerationFromSchemaDocument(String elementName) {
        // This would require deeper schema parsing to extract element enumeration values
        // For now, return empty list
        return new ArrayList<>();
    }

    // Inner classes

    public static class ElementInfo {
        public String name;
        public String type;
        public String documentation;
        public String minOccurs;
        public String maxOccurs;
    }

    public static class AttributeInfo {
        public String name;
        public String type;
        public String documentation;
        public String use;
        public String defaultValue;
        public List<String> enumerationValues;
    }
}