package org.fxt.freexmltoolkit.controls.intellisense;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts comprehensive documentation and completion data from XSD schemas.
 * Enhanced to provide rich IntelliSense information including:
 * - Element descriptions from xs:documentation
 * - Type constraints and restrictions
 * - Enumeration values
 * - Required vs optional attributes
 * - Complex type hierarchies
 * - Namespace information
 */
public class XsdDocumentationExtractor {

    private static final Logger logger = LogManager.getLogger(XsdDocumentationExtractor.class);

    private DocumentBuilder documentBuilder;
    private Map<String, ComplexTypeInfo> complexTypes;
    private Map<String, SimpleTypeInfo> simpleTypes;
    private Map<String, String> namespaceMap;

    public XsdDocumentationExtractor() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            this.documentBuilder = factory.newDocumentBuilder();
            this.complexTypes = new HashMap<>();
            this.simpleTypes = new HashMap<>();
            this.namespaceMap = new HashMap<>();
        } catch (Exception e) {
            logger.error("Failed to initialize XSD documentation extractor", e);
        }
    }

    /**
     * Extracts completion items from XSD file for a given XPath context
     */
    public List<CompletionItem> extractCompletionItems(File xsdFile, String xpathContext) {
        List<CompletionItem> items = new ArrayList<>();

        try {
            Document xsdDoc = documentBuilder.parse(xsdFile);
            analyzeSchema(xsdDoc);

            // Find context element in schema
            XsdExtendedElement contextElement = findElementByXPath(xsdDoc, xpathContext);

            if (contextElement != null) {
                items.addAll(extractElementCompletions(contextElement, xsdDoc));
                items.addAll(extractAttributeCompletions(contextElement, xsdDoc));
            } else {
                // Root level completions
                items.addAll(extractRootElementCompletions(xsdDoc));
            }

            logger.debug("Extracted {} completion items for context: {}", items.size(), xpathContext);

        } catch (Exception e) {
            logger.error("Failed to extract completion items from XSD", e);
        }

        return items;
    }

    /**
     * Analyzes the entire schema and builds type maps
     */
    private void analyzeSchema(Document xsdDoc) {
        complexTypes.clear();
        simpleTypes.clear();
        namespaceMap.clear();

        Element root = xsdDoc.getDocumentElement();

        // Extract namespace declarations
        extractNamespaces(root);

        // Analyze complex types
        NodeList complexTypeNodes = root.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypeNodes.getLength(); i++) {
            Element complexTypeElement = (Element) complexTypeNodes.item(i);
            analyzeComplexType(complexTypeElement);
        }

        // Analyze simple types
        NodeList simpleTypeNodes = root.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        for (int i = 0; i < simpleTypeNodes.getLength(); i++) {
            Element simpleTypeElement = (Element) simpleTypeNodes.item(i);
            analyzeSimpleType(simpleTypeElement);
        }
    }

    private void extractNamespaces(Element root) {
        NamedNodeMap attributes = root.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith("xmlns:")) {
                String prefix = attr.getNodeName().substring(6);
                namespaceMap.put(prefix, attr.getNodeValue());
            } else if (attr.getNodeName().equals("xmlns")) {
                namespaceMap.put("", attr.getNodeValue());
            }
        }
    }

    private void analyzeComplexType(Element complexTypeElement) {
        String name = complexTypeElement.getAttribute("name");
        if (name.isEmpty()) return;

        ComplexTypeInfo typeInfo = new ComplexTypeInfo(name);

        // Extract documentation
        typeInfo.documentation = extractDocumentation(complexTypeElement);

        // Extract attributes
        NodeList attributes = complexTypeElement.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attrElement = (Element) attributes.item(i);
            AttributeInfo attrInfo = analyzeAttribute(attrElement);
            if (attrInfo != null) {
                typeInfo.attributes.add(attrInfo);
            }
        }

        // Extract child elements from sequence, choice, or all
        extractChildElements(complexTypeElement, typeInfo);

        complexTypes.put(name, typeInfo);
    }

    private void analyzeSimpleType(Element simpleTypeElement) {
        String name = simpleTypeElement.getAttribute("name");
        if (name.isEmpty()) return;

        SimpleTypeInfo typeInfo = new SimpleTypeInfo(name);
        typeInfo.documentation = extractDocumentation(simpleTypeElement);

        // Check for restrictions
        Element restriction = getChildElementByName(simpleTypeElement, "restriction");
        if (restriction != null) {
            typeInfo.baseType = restriction.getAttribute("base");

            // Extract enumeration values
            NodeList enumerations = restriction.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "enumeration");
            for (int i = 0; i < enumerations.getLength(); i++) {
                Element enumElement = (Element) enumerations.item(i);
                String value = enumElement.getAttribute("value");
                String doc = extractDocumentation(enumElement);
                typeInfo.enumerationValues.add(new EnumerationValue(value, doc));
            }

            // Extract other constraints (minLength, maxLength, pattern, etc.)
            extractConstraints(restriction, typeInfo);
        }

        simpleTypes.put(name, typeInfo);
    }

    private void extractChildElements(Element parent, ComplexTypeInfo typeInfo) {
        // Look for sequence, choice, or all
        Element sequence = getChildElementByName(parent, "sequence");
        Element choice = getChildElementByName(parent, "choice");
        Element all = getChildElementByName(parent, "all");

        Element container = sequence != null ? sequence : (choice != null ? choice : all);
        if (container == null) return;

        NodeList elements = container.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element elemElement = (Element) elements.item(i);
            ElementInfo elemInfo = analyzeElement(elemElement);
            if (elemInfo != null) {
                typeInfo.childElements.add(elemInfo);
            }
        }
    }

    private ElementInfo analyzeElement(Element elementNode) {
        String name = elementNode.getAttribute("name");
        String type = elementNode.getAttribute("type");
        String minOccurs = elementNode.getAttribute("minOccurs");
        String maxOccurs = elementNode.getAttribute("maxOccurs");

        if (name.isEmpty()) return null;

        ElementInfo info = new ElementInfo(name, type);
        info.documentation = extractDocumentation(elementNode);
        info.minOccurs = minOccurs.isEmpty() ? 1 : Integer.parseInt(minOccurs);
        info.maxOccurs = maxOccurs.isEmpty() ? 1 : (maxOccurs.equals("unbounded") ? Integer.MAX_VALUE : Integer.parseInt(maxOccurs));
        info.required = info.minOccurs > 0;

        return info;
    }

    private AttributeInfo analyzeAttribute(Element attributeNode) {
        String name = attributeNode.getAttribute("name");
        String type = attributeNode.getAttribute("type");
        String use = attributeNode.getAttribute("use");
        String defaultValue = attributeNode.getAttribute("default");
        String fixedValue = attributeNode.getAttribute("fixed");

        if (name.isEmpty()) return null;

        AttributeInfo info = new AttributeInfo(name, type);
        info.documentation = extractDocumentation(attributeNode);
        info.required = "required".equals(use);
        info.defaultValue = defaultValue.isEmpty() ? null : defaultValue;
        info.fixedValue = fixedValue.isEmpty() ? null : fixedValue;

        return info;
    }

    private void extractConstraints(Element restriction, SimpleTypeInfo typeInfo) {
        NodeList constraints = restriction.getChildNodes();
        for (int i = 0; i < constraints.getLength(); i++) {
            if (constraints.item(i).getNodeType() != Node.ELEMENT_NODE) continue;

            Element constraint = (Element) constraints.item(i);
            String constraintName = constraint.getLocalName();
            String value = constraint.getAttribute("value");

            switch (constraintName) {
                case "minLength":
                    typeInfo.constraints.put("minLength", value);
                    break;
                case "maxLength":
                    typeInfo.constraints.put("maxLength", value);
                    break;
                case "pattern":
                    typeInfo.constraints.put("pattern", value);
                    break;
                case "minInclusive":
                    typeInfo.constraints.put("minInclusive", value);
                    break;
                case "maxInclusive":
                    typeInfo.constraints.put("maxInclusive", value);
                    break;
                case "minExclusive":
                    typeInfo.constraints.put("minExclusive", value);
                    break;
                case "maxExclusive":
                    typeInfo.constraints.put("maxExclusive", value);
                    break;
            }
        }
    }

    private String extractDocumentation(Element element) {
        Element annotation = getChildElementByName(element, "annotation");
        if (annotation == null) return null;

        Element documentation = getChildElementByName(annotation, "documentation");
        if (documentation == null) return null;

        return documentation.getTextContent().trim();
    }

    private Element getChildElementByName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) children.item(i);
                if (localName.equals(child.getLocalName())) {
                    return child;
                }
            }
        }
        return null;
    }

    private XsdExtendedElement findElementByXPath(Document xsdDoc, String xpath) {
        // This would need to be implemented based on your existing XSD parsing logic
        // For now, return null to indicate root level
        return null;
    }

    private List<CompletionItem> extractRootElementCompletions(Document xsdDoc) {
        List<CompletionItem> items = new ArrayList<>();

        NodeList rootElements = xsdDoc.getDocumentElement().getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        for (int i = 0; i < rootElements.getLength(); i++) {
            Element element = (Element) rootElements.item(i);
            if (element.getParentNode().equals(xsdDoc.getDocumentElement())) {
                CompletionItem item = createElementCompletionItem(element);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    private List<CompletionItem> extractElementCompletions(XsdExtendedElement contextElement, Document xsdDoc) {
        List<CompletionItem> items = new ArrayList<>();

        // Get complex type info for context element
        ComplexTypeInfo typeInfo = complexTypes.get(contextElement.getElementType());
        if (typeInfo != null) {
            for (ElementInfo childElement : typeInfo.childElements) {
                CompletionItem item = new CompletionItem.Builder(
                        childElement.name,
                        "<" + childElement.name + "></" + childElement.name + ">",
                        CompletionItemType.ELEMENT
                )
                        .description(childElement.documentation)
                        .dataType(childElement.type)
                        .required(childElement.required)
                        .relevanceScore(childElement.required ? 200 : 100)
                        .build();

                items.add(item);
            }
        }

        return items;
    }

    private List<CompletionItem> extractAttributeCompletions(XsdExtendedElement contextElement, Document xsdDoc) {
        List<CompletionItem> items = new ArrayList<>();

        ComplexTypeInfo typeInfo = complexTypes.get(contextElement.getElementType());
        if (typeInfo != null) {
            for (AttributeInfo attribute : typeInfo.attributes) {
                String insertText = attribute.name + "=\"" +
                        (attribute.defaultValue != null ? attribute.defaultValue : "") + "\"";

                CompletionItem item = new CompletionItem.Builder(
                        attribute.name,
                        insertText,
                        CompletionItemType.ATTRIBUTE
                )
                        .description(attribute.documentation)
                        .dataType(attribute.type)
                        .defaultValue(attribute.defaultValue)
                        .required(attribute.required)
                        .relevanceScore(attribute.required ? 150 : 75)
                        .build();

                items.add(item);
            }
        }

        return items;
    }

    private CompletionItem createElementCompletionItem(Element element) {
        String name = element.getAttribute("name");
        String type = element.getAttribute("type");
        if (name.isEmpty()) return null;

        String documentation = extractDocumentation(element);
        String insertText = "<" + name + "></" + name + ">";

        return new CompletionItem.Builder(name, insertText, CompletionItemType.ELEMENT)
                .description(documentation)
                .dataType(type)
                .relevanceScore(100)
                .build();
    }

    /**
     * Gets enumeration values for a simple type
     */
    public List<CompletionItem> getEnumerationCompletions(String typeName) {
        List<CompletionItem> items = new ArrayList<>();

        SimpleTypeInfo typeInfo = simpleTypes.get(typeName);
        if (typeInfo != null) {
            for (EnumerationValue enumValue : typeInfo.enumerationValues) {
                CompletionItem item = new CompletionItem.Builder(
                        enumValue.value,
                        enumValue.value,
                        CompletionItemType.TEXT
                )
                        .description(enumValue.documentation)
                        .dataType(typeInfo.baseType)
                        .relevanceScore(100)
                        .build();

                items.add(item);
            }
        }

        return items;
    }

    // Inner classes for type information
    private static class ComplexTypeInfo {
        String name;
        String documentation;
        List<ElementInfo> childElements = new ArrayList<>();
        List<AttributeInfo> attributes = new ArrayList<>();

        ComplexTypeInfo(String name) {
            this.name = name;
        }
    }

    private static class SimpleTypeInfo {
        String name;
        String documentation;
        String baseType;
        List<EnumerationValue> enumerationValues = new ArrayList<>();
        Map<String, String> constraints = new HashMap<>();

        SimpleTypeInfo(String name) {
            this.name = name;
        }
    }

    public static class ElementInfo {
        public String name;
        public String type;
        public String documentation;
        public int minOccurs;
        public int maxOccurs;
        public boolean required;

        public ElementInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class AttributeInfo {
        public String name;
        public String type;
        public String documentation;
        public boolean required;
        public String defaultValue;
        public String fixedValue;

        public AttributeInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private static class EnumerationValue {
        String value;
        String documentation;

        EnumerationValue(String value, String documentation) {
            this.value = value;
            this.documentation = documentation;
        }
    }

    // Public schema info class for multi-schema support
    public static class SchemaInfo {
        public String targetNamespace;
        public List<ElementInfo> elements = new ArrayList<>();
        public List<AttributeInfo> attributes = new ArrayList<>();
        public Map<String, String> namespaces = new HashMap<>();

        public SchemaInfo() {
        }

        public SchemaInfo(String targetNamespace) {
            this.targetNamespace = targetNamespace;
        }
    }

    /**
     * Extract schema information from file
     */
    public SchemaInfo extractFromFile(File xsdFile) {
        try {
            Document xsdDoc = documentBuilder.parse(xsdFile);
            return extractSchemaInfo(xsdDoc);
        } catch (Exception e) {
            logger.error("Failed to extract schema info from file: " + xsdFile, e);
            return new SchemaInfo();
        }
    }

    /**
     * Extract schema information from URL
     */
    public SchemaInfo extractFromUrl(java.net.URL url) {
        try {
            Document xsdDoc = documentBuilder.parse(url.openStream());
            return extractSchemaInfo(xsdDoc);
        } catch (Exception e) {
            logger.error("Failed to extract schema info from URL: " + url, e);
            return new SchemaInfo();
        }
    }

    /**
     * Extract schema information from DOM document
     */
    private SchemaInfo extractSchemaInfo(Document xsdDoc) {
        SchemaInfo schemaInfo = new SchemaInfo();

        try {
            Element root = xsdDoc.getDocumentElement();

            // Extract target namespace
            schemaInfo.targetNamespace = root.getAttribute("targetNamespace");

            // Extract namespace declarations
            extractNamespaces(root);
            schemaInfo.namespaces.putAll(namespaceMap);

            // Analyze complex types and simple types first
            analyzeSchema(xsdDoc);

            // Extract elements
            NodeList elementNodes = root.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
            for (int i = 0; i < elementNodes.getLength(); i++) {
                Element elementNode = (Element) elementNodes.item(i);
                ElementInfo elementInfo = extractElementInfo(elementNode);
                if (elementInfo != null) {
                    schemaInfo.elements.add(elementInfo);
                }
            }

            // Extract attributes
            NodeList attributeNodes = root.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "attribute");
            for (int i = 0; i < attributeNodes.getLength(); i++) {
                Element attributeNode = (Element) attributeNodes.item(i);
                AttributeInfo attributeInfo = extractAttributeInfo(attributeNode);
                if (attributeInfo != null) {
                    schemaInfo.attributes.add(attributeInfo);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to extract schema information", e);
        }

        return schemaInfo;
    }

    /**
     * Extract element information from element node
     */
    private ElementInfo extractElementInfo(Element elementNode) {
        String name = elementNode.getAttribute("name");
        if (name.isEmpty()) {
            return null;
        }

        String type = elementNode.getAttribute("type");
        ElementInfo elementInfo = new ElementInfo(name, type);

        // Extract documentation
        elementInfo.documentation = extractDocumentation(elementNode);

        // Extract occurrence constraints
        String minOccurs = elementNode.getAttribute("minOccurs");
        String maxOccurs = elementNode.getAttribute("maxOccurs");

        elementInfo.minOccurs = minOccurs.isEmpty() ? 1 : Integer.parseInt(minOccurs);
        elementInfo.maxOccurs = maxOccurs.equals("unbounded") ? Integer.MAX_VALUE :
                (maxOccurs.isEmpty() ? 1 : Integer.parseInt(maxOccurs));
        elementInfo.required = elementInfo.minOccurs > 0;

        return elementInfo;
    }

    /**
     * Extract attribute information from attribute node
     */
    private AttributeInfo extractAttributeInfo(Element attributeNode) {
        String name = attributeNode.getAttribute("name");
        if (name.isEmpty()) {
            return null;
        }

        String type = attributeNode.getAttribute("type");
        AttributeInfo attributeInfo = new AttributeInfo(name, type);

        // Extract documentation
        attributeInfo.documentation = extractDocumentation(attributeNode);

        // Extract usage
        String use = attributeNode.getAttribute("use");
        attributeInfo.required = "required".equals(use);

        // Extract default and fixed values
        attributeInfo.defaultValue = attributeNode.getAttribute("default");
        attributeInfo.fixedValue = attributeNode.getAttribute("fixed");

        if (attributeInfo.defaultValue.isEmpty()) {
            attributeInfo.defaultValue = null;
        }
        if (attributeInfo.fixedValue.isEmpty()) {
            attributeInfo.fixedValue = null;
        }

        return attributeInfo;
    }
}