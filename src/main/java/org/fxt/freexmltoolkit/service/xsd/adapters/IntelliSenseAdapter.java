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
     * Contains schema information for IntelliSense/autocomplete features.
     *
     * <p>This class aggregates all relevant schema data needed for providing
     * code completion suggestions in XML editors, including elements, attributes,
     * types, and imported schemas.</p>
     */
    public static class SchemaInfo {
        /**
         * The target namespace of the schema, or {@code null} if not specified.
         */
        public String targetNamespace;

        /**
         * Map of namespace prefixes to namespace URIs declared in the schema.
         */
        public final Map<String, String> namespaces = new LinkedHashMap<>();

        /**
         * List of global elements declared in the schema.
         */
        public final List<ElementInfo> elements = new ArrayList<>();

        /**
         * List of global attributes declared in the schema.
         */
        public final List<AttributeInfo> attributes = new ArrayList<>();

        /**
         * Map of complex type names to their information.
         */
        public final Map<String, ComplexTypeInfo> complexTypes = new LinkedHashMap<>();

        /**
         * Map of simple type names to their information.
         */
        public final Map<String, SimpleTypeInfo> simpleTypes = new LinkedHashMap<>();

        /**
         * Map of imported schema identifiers (namespace or location) to their SchemaInfo.
         */
        public final Map<String, SchemaInfo> importedSchemas = new LinkedHashMap<>();

        /**
         * Creates a new empty SchemaInfo instance.
         */
        public SchemaInfo() {
            // Default constructor
        }

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
     * Contains information about an XSD element for IntelliSense completion.
     *
     * <p>This class holds all relevant element metadata including name, type,
     * occurrence constraints, and documentation that can be displayed in
     * autocomplete suggestions.</p>
     */
    public static class ElementInfo {
        /**
         * The name of the element, or the reference name if this is a reference.
         */
        public String name;

        /**
         * The type of the element (e.g., "xs:string", "PersonType"), or empty if anonymous.
         */
        public String type;

        /**
         * Documentation text extracted from xs:documentation, or {@code null} if not present.
         */
        public String documentation;

        /**
         * Minimum number of occurrences (default is 1).
         */
        public int minOccurs = 1;

        /**
         * Maximum number of occurrences (default is 1, -1 means unbounded).
         */
        public int maxOccurs = 1;

        /**
         * Whether this element is required (minOccurs greater than 0).
         */
        public boolean required;

        /**
         * Whether this element can have a nil value (xsi:nil="true").
         */
        public boolean nillable;

        /**
         * Whether this is a reference to another element (uses ref attribute).
         */
        public boolean isRef;

        /**
         * The default value for this element, or empty if not specified.
         */
        public String defaultValue;

        /**
         * The fixed value for this element, or empty if not specified.
         */
        public String fixedValue;

        /**
         * The substitution group this element belongs to, or empty if none.
         */
        public String substitutionGroup;

        /**
         * Creates a new empty ElementInfo instance.
         */
        public ElementInfo() {
            // Default constructor
        }
    }

    /**
     * Contains information about an XSD attribute for IntelliSense completion.
     *
     * <p>This class holds all relevant attribute metadata including name, type,
     * use constraints, and documentation that can be displayed in
     * autocomplete suggestions.</p>
     */
    public static class AttributeInfo {
        /**
         * The name of the attribute, or the reference name if this is a reference.
         */
        public String name;

        /**
         * The type of the attribute (e.g., "xs:string", "StatusType"), or empty if not specified.
         */
        public String type;

        /**
         * Documentation text extracted from xs:documentation, or {@code null} if not present.
         */
        public String documentation;

        /**
         * Whether this attribute is required (use="required").
         */
        public boolean required;

        /**
         * Whether this attribute is prohibited (use="prohibited").
         */
        public boolean prohibited;

        /**
         * Whether this is a reference to another attribute (uses ref attribute).
         */
        public boolean isRef;

        /**
         * The default value for this attribute, or empty if not specified.
         */
        public String defaultValue;

        /**
         * The fixed value for this attribute, or empty if not specified.
         */
        public String fixedValue;

        /**
         * Creates a new empty AttributeInfo instance.
         */
        public AttributeInfo() {
            // Default constructor
        }
    }

    /**
     * Contains information about an XSD complex type for IntelliSense.
     *
     * <p>This class holds metadata about complex types including their
     * child elements, attributes, and inheritance information that is
     * used to provide context-aware completion suggestions.</p>
     */
    public static class ComplexTypeInfo {
        /**
         * The name of the complex type.
         */
        public String name;

        /**
         * Documentation text extracted from xs:documentation, or {@code null} if not present.
         */
        public String documentation;

        /**
         * Whether this type is abstract and cannot be instantiated directly.
         */
        public boolean isAbstract;

        /**
         * Whether this type allows mixed content (text and elements).
         */
        public boolean isMixed;

        /**
         * The base type this complex type extends or restricts, or {@code null} if none.
         */
        public String baseType;

        /**
         * List of child elements allowed in this complex type.
         */
        public final List<ElementInfo> childElements = new ArrayList<>();

        /**
         * List of attributes defined for this complex type.
         */
        public final List<AttributeInfo> attributes = new ArrayList<>();

        /**
         * Creates a new empty ComplexTypeInfo instance.
         */
        public ComplexTypeInfo() {
            // Default constructor
        }
    }

    /**
     * Contains information about an XSD simple type for IntelliSense.
     *
     * <p>This class holds metadata about simple types including their
     * restriction base, facets, enumerations, and list/union characteristics
     * that is used to provide value completion suggestions.</p>
     */
    public static class SimpleTypeInfo {
        /**
         * The name of the simple type.
         */
        public String name;

        /**
         * Documentation text extracted from xs:documentation, or {@code null} if not present.
         */
        public String documentation;

        /**
         * The base type this simple type restricts (e.g., "xs:string").
         */
        public String baseType;

        /**
         * Whether this is a list type (xs:list).
         */
        public boolean isList;

        /**
         * Whether this is a union type (xs:union).
         */
        public boolean isUnion;

        /**
         * List of member type names if this is a union type, or {@code null} if not a union.
         */
        public List<String> unionMemberTypes;

        /**
         * List of enumeration values if this type has enumeration facets.
         */
        public final List<EnumerationValue> enumerationValues = new ArrayList<>();

        /**
         * Map of facet names to their values (e.g., "minLength" to "1").
         */
        public final Map<String, String> facets = new LinkedHashMap<>();

        /**
         * Creates a new empty SimpleTypeInfo instance.
         */
        public SimpleTypeInfo() {
            // Default constructor
        }
    }

    /**
     * Represents an enumeration value from an XSD simple type restriction.
     *
     * <p>This class holds both the value itself and any associated documentation
     * that can be displayed in autocomplete suggestions.</p>
     */
    public static class EnumerationValue {
        /**
         * The enumeration value string.
         */
        public String value;

        /**
         * Documentation text for this enumeration value, or {@code null} if not present.
         */
        public String documentation;

        /**
         * Creates a new empty EnumerationValue instance.
         */
        public EnumerationValue() {
            // Default constructor
        }
    }
}
