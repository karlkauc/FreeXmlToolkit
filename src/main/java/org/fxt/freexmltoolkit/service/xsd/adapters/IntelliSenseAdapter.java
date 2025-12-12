package org.fxt.freexmltoolkit.service.xsd.adapters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParseException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Adapter for converting ParsedSchema to IntelliSense data structures.
 *
 * <p>This adapter extracts completion information from XSD schemas
 * for use in XML editors with IntelliSense/autocomplete features.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Element and attribute completion data</li>
 *   <li>Documentation extraction</li>
 *   <li>Type constraint information</li>
 *   <li>Enumeration values</li>
 *   <li>Namespace handling</li>
 * </ul>
 */
public class IntelliSenseAdapter {

    private static final Logger logger = LogManager.getLogger(IntelliSenseAdapter.class);

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    // Type caches
    private final Map<String, ComplexTypeInfo> complexTypes = new HashMap<>();
    private final Map<String, SimpleTypeInfo> simpleTypes = new HashMap<>();

    /**
     * Creates a new IntelliSenseAdapter.
     */
    public IntelliSenseAdapter() {
    }

    /**
     * Converts a ParsedSchema to SchemaInfo for IntelliSense.
     *
     * @param parsedSchema the parsed schema
     * @return the schema info for IntelliSense
     * @throws XsdParseException if conversion fails
     */
    public SchemaInfo toSchemaInfo(ParsedSchema parsedSchema) throws XsdParseException {
        if (parsedSchema == null) {
            throw new XsdParseException("ParsedSchema cannot be null");
        }

        // Clear caches
        complexTypes.clear();
        simpleTypes.clear();

        SchemaInfo schemaInfo = new SchemaInfo();
        Element schemaElement = parsedSchema.getSchemaElement();

        try {
            // Extract target namespace
            schemaInfo.targetNamespace = parsedSchema.getTargetNamespace();

            // Extract namespace declarations
            schemaInfo.namespaces.putAll(parsedSchema.getNamespaceDeclarations());

            // First pass: analyze types
            analyzeTypes(schemaElement);

            // Process includes (they may contain additional types)
            for (ParsedSchema.ResolvedInclude include : parsedSchema.getResolvedIncludes()) {
                if (include.isResolved() && include.parsedSchema() != null) {
                    analyzeTypes(include.parsedSchema().getSchemaElement());
                }
            }

            // Process imports
            for (ParsedSchema.ResolvedImport imp : parsedSchema.getResolvedImports()) {
                if (imp.isLoaded() && imp.parsedSchema() != null) {
                    // Add imported schema info
                    SchemaInfo importedInfo = toSchemaInfoInternal(imp.parsedSchema());
                    schemaInfo.importedSchemas.put(
                            imp.namespace() != null ? imp.namespace() : imp.schemaLocation(),
                            importedInfo
                    );
                }
            }

            // Second pass: extract elements and attributes
            extractGlobalElements(schemaElement, schemaInfo);
            extractGlobalAttributes(schemaElement, schemaInfo);

            // Process include content
            for (ParsedSchema.ResolvedInclude include : parsedSchema.getResolvedIncludes()) {
                if (include.isResolved() && include.parsedSchema() != null) {
                    extractGlobalElements(include.parsedSchema().getSchemaElement(), schemaInfo);
                    extractGlobalAttributes(include.parsedSchema().getSchemaElement(), schemaInfo);
                }
            }

            // Extract type info for completion
            schemaInfo.complexTypes.putAll(complexTypes);
            schemaInfo.simpleTypes.putAll(simpleTypes);

            logger.debug("Extracted IntelliSense info: {} elements, {} attributes, {} complex types, {} simple types",
                    schemaInfo.elements.size(), schemaInfo.attributes.size(),
                    schemaInfo.complexTypes.size(), schemaInfo.simpleTypes.size());

            return schemaInfo;

        } catch (Exception e) {
            throw new XsdParseException("Failed to extract IntelliSense info: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method for extracting schema info (no include processing).
     */
    private SchemaInfo toSchemaInfoInternal(ParsedSchema parsedSchema) {
        SchemaInfo schemaInfo = new SchemaInfo();
        Element schemaElement = parsedSchema.getSchemaElement();

        schemaInfo.targetNamespace = parsedSchema.getTargetNamespace();
        schemaInfo.namespaces.putAll(parsedSchema.getNamespaceDeclarations());

        analyzeTypes(schemaElement);
        extractGlobalElements(schemaElement, schemaInfo);
        extractGlobalAttributes(schemaElement, schemaInfo);

        schemaInfo.complexTypes.putAll(complexTypes);
        schemaInfo.simpleTypes.putAll(simpleTypes);

        return schemaInfo;
    }

    /**
     * Analyzes complex and simple types in the schema.
     */
    private void analyzeTypes(Element schemaElement) {
        NodeList children = schemaElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            if (!XSD_NS.equals(element.getNamespaceURI())) {
                continue;
            }

            String localName = element.getLocalName();
            if ("complexType".equals(localName)) {
                ComplexTypeInfo typeInfo = analyzeComplexType(element);
                if (typeInfo != null && typeInfo.name != null) {
                    complexTypes.put(typeInfo.name, typeInfo);
                }
            } else if ("simpleType".equals(localName)) {
                SimpleTypeInfo typeInfo = analyzeSimpleType(element);
                if (typeInfo != null && typeInfo.name != null) {
                    simpleTypes.put(typeInfo.name, typeInfo);
                }
            }
        }
    }

    /**
     * Analyzes a complex type element.
     */
    private ComplexTypeInfo analyzeComplexType(Element element) {
        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return null;
        }

        ComplexTypeInfo typeInfo = new ComplexTypeInfo();
        typeInfo.name = name;
        typeInfo.documentation = extractDocumentation(element);
        typeInfo.isAbstract = Boolean.parseBoolean(element.getAttribute("abstract"));
        typeInfo.isMixed = Boolean.parseBoolean(element.getAttribute("mixed"));

        // Extract attributes
        extractAttributes(element, typeInfo.attributes);

        // Extract child elements
        extractChildElements(element, typeInfo.childElements);

        return typeInfo;
    }

    /**
     * Analyzes a simple type element.
     */
    private SimpleTypeInfo analyzeSimpleType(Element element) {
        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return null;
        }

        SimpleTypeInfo typeInfo = new SimpleTypeInfo();
        typeInfo.name = name;
        typeInfo.documentation = extractDocumentation(element);

        // Look for restriction
        Element restriction = findChildElement(element, "restriction");
        if (restriction != null) {
            typeInfo.baseType = restriction.getAttribute("base");

            // Extract enumeration values
            extractEnumerations(restriction, typeInfo.enumerationValues);

            // Extract facets
            extractFacets(restriction, typeInfo.facets);
        }

        // Look for list
        Element list = findChildElement(element, "list");
        if (list != null) {
            typeInfo.isList = true;
            typeInfo.baseType = list.getAttribute("itemType");
        }

        // Look for union
        Element union = findChildElement(element, "union");
        if (union != null) {
            typeInfo.isUnion = true;
            String memberTypes = union.getAttribute("memberTypes");
            if (!memberTypes.isEmpty()) {
                typeInfo.unionMemberTypes = Arrays.asList(memberTypes.split("\\s+"));
            }
        }

        return typeInfo;
    }

    /**
     * Extracts attributes from a complex type.
     */
    private void extractAttributes(Element typeElement, List<AttributeInfo> attributes) {
        // Direct attributes
        NodeList children = typeElement.getElementsByTagNameNS(XSD_NS, "attribute");
        for (int i = 0; i < children.getLength(); i++) {
            Element attrElement = (Element) children.item(i);
            AttributeInfo attrInfo = extractAttributeInfo(attrElement);
            if (attrInfo != null) {
                attributes.add(attrInfo);
            }
        }
    }

    /**
     * Extracts child elements from sequence/choice/all.
     */
    private void extractChildElements(Element typeElement, List<ElementInfo> elements) {
        // Find compositor (sequence, choice, all)
        Element compositor = findChildElement(typeElement, "sequence");
        if (compositor == null) {
            compositor = findChildElement(typeElement, "choice");
        }
        if (compositor == null) {
            compositor = findChildElement(typeElement, "all");
        }

        // Also check complexContent/simpleContent extensions
        Element complexContent = findChildElement(typeElement, "complexContent");
        if (complexContent != null) {
            Element extension = findChildElement(complexContent, "extension");
            if (extension != null) {
                Element extCompositor = findChildElement(extension, "sequence");
                if (extCompositor == null) extCompositor = findChildElement(extension, "choice");
                if (extCompositor == null) extCompositor = findChildElement(extension, "all");
                if (extCompositor != null) {
                    compositor = extCompositor;
                }
            }
        }

        if (compositor == null) {
            return;
        }

        NodeList children = compositor.getElementsByTagNameNS(XSD_NS, "element");
        for (int i = 0; i < children.getLength(); i++) {
            Element elemElement = (Element) children.item(i);
            ElementInfo elemInfo = extractElementInfo(elemElement);
            if (elemInfo != null) {
                elements.add(elemInfo);
            }
        }
    }

    /**
     * Extracts global elements from schema.
     */
    private void extractGlobalElements(Element schemaElement, SchemaInfo schemaInfo) {
        NodeList children = schemaElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            if (XSD_NS.equals(element.getNamespaceURI()) && "element".equals(element.getLocalName())) {
                ElementInfo elemInfo = extractElementInfo(element);
                if (elemInfo != null) {
                    schemaInfo.elements.add(elemInfo);
                }
            }
        }
    }

    /**
     * Extracts global attributes from schema.
     */
    private void extractGlobalAttributes(Element schemaElement, SchemaInfo schemaInfo) {
        NodeList children = schemaElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            if (XSD_NS.equals(element.getNamespaceURI()) && "attribute".equals(element.getLocalName())) {
                AttributeInfo attrInfo = extractAttributeInfo(element);
                if (attrInfo != null) {
                    schemaInfo.attributes.add(attrInfo);
                }
            }
        }
    }

    /**
     * Extracts element information.
     */
    private ElementInfo extractElementInfo(Element element) {
        String name = element.getAttribute("name");
        String ref = element.getAttribute("ref");

        if (name.isEmpty() && ref.isEmpty()) {
            return null;
        }

        ElementInfo info = new ElementInfo();
        info.name = name.isEmpty() ? ref : name;
        info.isRef = !ref.isEmpty();
        info.type = element.getAttribute("type");
        info.documentation = extractDocumentation(element);

        // Occurrence
        String minOccurs = element.getAttribute("minOccurs");
        String maxOccurs = element.getAttribute("maxOccurs");
        info.minOccurs = minOccurs.isEmpty() ? 1 : Integer.parseInt(minOccurs);
        info.maxOccurs = "unbounded".equals(maxOccurs) ? -1 :
                (maxOccurs.isEmpty() ? 1 : Integer.parseInt(maxOccurs));
        info.required = info.minOccurs > 0;

        // Other attributes
        info.nillable = Boolean.parseBoolean(element.getAttribute("nillable"));
        info.defaultValue = element.getAttribute("default");
        info.fixedValue = element.getAttribute("fixed");
        info.substitutionGroup = element.getAttribute("substitutionGroup");

        return info;
    }

    /**
     * Extracts attribute information.
     */
    private AttributeInfo extractAttributeInfo(Element element) {
        String name = element.getAttribute("name");
        String ref = element.getAttribute("ref");

        if (name.isEmpty() && ref.isEmpty()) {
            return null;
        }

        AttributeInfo info = new AttributeInfo();
        info.name = name.isEmpty() ? ref : name;
        info.isRef = !ref.isEmpty();
        info.type = element.getAttribute("type");
        info.documentation = extractDocumentation(element);

        // Use
        String use = element.getAttribute("use");
        info.required = "required".equals(use);
        info.prohibited = "prohibited".equals(use);

        // Default/fixed
        info.defaultValue = element.getAttribute("default");
        info.fixedValue = element.getAttribute("fixed");

        return info;
    }

    /**
     * Extracts enumeration values.
     */
    private void extractEnumerations(Element restriction, List<EnumerationValue> values) {
        NodeList enums = restriction.getElementsByTagNameNS(XSD_NS, "enumeration");
        for (int i = 0; i < enums.getLength(); i++) {
            Element enumElement = (Element) enums.item(i);
            EnumerationValue value = new EnumerationValue();
            value.value = enumElement.getAttribute("value");
            value.documentation = extractDocumentation(enumElement);
            values.add(value);
        }
    }

    /**
     * Extracts facet constraints.
     */
    private void extractFacets(Element restriction, Map<String, String> facets) {
        String[] facetNames = {
                "minInclusive", "maxInclusive", "minExclusive", "maxExclusive",
                "minLength", "maxLength", "length", "pattern",
                "totalDigits", "fractionDigits", "whiteSpace"
        };

        for (String facetName : facetNames) {
            Element facet = findChildElement(restriction, facetName);
            if (facet != null) {
                facets.put(facetName, facet.getAttribute("value"));
            }
        }
    }

    /**
     * Extracts documentation from an element.
     */
    private String extractDocumentation(Element element) {
        Element annotation = findChildElement(element, "annotation");
        if (annotation == null) {
            return null;
        }

        Element documentation = findChildElement(annotation, "documentation");
        if (documentation == null) {
            return null;
        }

        String text = documentation.getTextContent();
        return text != null ? text.trim() : null;
    }

    /**
     * Finds a child element by local name.
     */
    private Element findChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child &&
                    XSD_NS.equals(child.getNamespaceURI()) &&
                    localName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    // ========== Data Classes ==========

    /**
     * Schema information for IntelliSense.
     */
    public static class SchemaInfo {
        public String targetNamespace;
        public final Map<String, String> namespaces = new LinkedHashMap<>();
        public final List<ElementInfo> elements = new ArrayList<>();
        public final List<AttributeInfo> attributes = new ArrayList<>();
        public final Map<String, ComplexTypeInfo> complexTypes = new LinkedHashMap<>();
        public final Map<String, SimpleTypeInfo> simpleTypes = new LinkedHashMap<>();
        public final Map<String, SchemaInfo> importedSchemas = new LinkedHashMap<>();

        /**
         * Gets completion items for a given context.
         *
         * @param parentType the type of the parent element
         * @return list of possible child elements
         */
        public List<ElementInfo> getChildElements(String parentType) {
            if (parentType == null || parentType.isEmpty()) {
                return elements; // Root elements
            }

            ComplexTypeInfo typeInfo = complexTypes.get(stripPrefix(parentType));
            if (typeInfo != null) {
                return typeInfo.childElements;
            }

            return Collections.emptyList();
        }

        /**
         * Gets attributes for a given element type.
         *
         * @param elementType the type of the element
         * @return list of possible attributes
         */
        public List<AttributeInfo> getAttributes(String elementType) {
            if (elementType == null || elementType.isEmpty()) {
                return attributes;
            }

            ComplexTypeInfo typeInfo = complexTypes.get(stripPrefix(elementType));
            if (typeInfo != null) {
                return typeInfo.attributes;
            }

            return Collections.emptyList();
        }

        /**
         * Gets enumeration values for a simple type.
         *
         * @param typeName the name of the simple type
         * @return list of enumeration values
         */
        public List<EnumerationValue> getEnumerationValues(String typeName) {
            SimpleTypeInfo typeInfo = simpleTypes.get(stripPrefix(typeName));
            if (typeInfo != null) {
                return typeInfo.enumerationValues;
            }
            return Collections.emptyList();
        }

        private String stripPrefix(String name) {
            int colonIndex = name.indexOf(':');
            return colonIndex >= 0 ? name.substring(colonIndex + 1) : name;
        }
    }

    /**
     * Element information for completion.
     */
    public static class ElementInfo {
        public String name;
        public String type;
        public String documentation;
        public int minOccurs = 1;
        public int maxOccurs = 1;
        public boolean required;
        public boolean nillable;
        public boolean isRef;
        public String defaultValue;
        public String fixedValue;
        public String substitutionGroup;
    }

    /**
     * Attribute information for completion.
     */
    public static class AttributeInfo {
        public String name;
        public String type;
        public String documentation;
        public boolean required;
        public boolean prohibited;
        public boolean isRef;
        public String defaultValue;
        public String fixedValue;
    }

    /**
     * Complex type information.
     */
    public static class ComplexTypeInfo {
        public String name;
        public String documentation;
        public boolean isAbstract;
        public boolean isMixed;
        public String baseType;
        public final List<ElementInfo> childElements = new ArrayList<>();
        public final List<AttributeInfo> attributes = new ArrayList<>();
    }

    /**
     * Simple type information.
     */
    public static class SimpleTypeInfo {
        public String name;
        public String documentation;
        public String baseType;
        public boolean isList;
        public boolean isUnion;
        public List<String> unionMemberTypes;
        public final List<EnumerationValue> enumerationValues = new ArrayList<>();
        public final Map<String, String> facets = new LinkedHashMap<>();
    }

    /**
     * Enumeration value.
     */
    public static class EnumerationValue {
        public String value;
        public String documentation;
    }
}
