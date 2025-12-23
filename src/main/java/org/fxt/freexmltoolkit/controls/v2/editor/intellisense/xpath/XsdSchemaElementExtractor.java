package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Extracts XSD-specific schema constructs for IntelliSense completions.
 * <p>
 * This extractor focuses on extracting named schema components:
 * <ul>
 *   <li>Element declarations (xs:element/@name)</li>
 *   <li>Complex type definitions (xs:complexType/@name)</li>
 *   <li>Simple type definitions (xs:simpleType/@name)</li>
 *   <li>Attribute declarations (xs:attribute/@name)</li>
 *   <li>Attribute groups (xs:attributeGroup/@name)</li>
 *   <li>Model groups (xs:group/@name)</li>
 * </ul>
 * <p>
 * Additionally provides common XSD attribute names for predicate completions.
 */
public class XsdSchemaElementExtractor {

    private static final Logger logger = LogManager.getLogger(XsdSchemaElementExtractor.class);

    // XSD namespace URI
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    // Extracted schema component names
    private final Set<String> elementNames = new LinkedHashSet<>();
    private final Set<String> complexTypeNames = new LinkedHashSet<>();
    private final Set<String> simpleTypeNames = new LinkedHashSet<>();
    private final Set<String> attributeNames = new LinkedHashSet<>();
    private final Set<String> attributeGroupNames = new LinkedHashSet<>();
    private final Set<String> groupNames = new LinkedHashSet<>();

    // Common XSD attributes for predicate completions
    private static final Set<String> XSD_COMMON_ATTRIBUTES = Set.of(
            "name", "type", "ref", "base",
            "minOccurs", "maxOccurs", "use", "default", "fixed",
            "abstract", "final", "block", "nillable",
            "substitutionGroup", "targetNamespace", "elementFormDefault",
            "attributeFormDefault", "id", "mixed", "processContents"
    );

    // XSD built-in types for type attribute suggestions
    private static final Set<String> XSD_BUILTIN_TYPES = Set.of(
            "xs:string", "xs:boolean", "xs:decimal", "xs:integer",
            "xs:date", "xs:time", "xs:dateTime", "xs:duration",
            "xs:float", "xs:double", "xs:int", "xs:long", "xs:short", "xs:byte",
            "xs:positiveInteger", "xs:negativeInteger", "xs:nonPositiveInteger", "xs:nonNegativeInteger",
            "xs:unsignedInt", "xs:unsignedLong", "xs:unsignedShort", "xs:unsignedByte",
            "xs:normalizedString", "xs:token", "xs:language", "xs:NMTOKEN", "xs:Name", "xs:NCName",
            "xs:ID", "xs:IDREF", "xs:IDREFS", "xs:ENTITY", "xs:ENTITIES",
            "xs:anyURI", "xs:QName", "xs:NOTATION",
            "xs:hexBinary", "xs:base64Binary",
            "xs:gYear", "xs:gMonth", "xs:gDay", "xs:gYearMonth", "xs:gMonthDay",
            "xs:anyType", "xs:anySimpleType"
    );

    // Cache management
    private String lastXsdHash = null;
    private boolean cacheValid = false;

    /**
     * Extracts schema construct names from XSD content.
     * Results are cached; subsequent calls with the same content return immediately.
     *
     * @param xsdContent the XSD content to parse
     */
    public void extractFromXsd(String xsdContent) {
        if (xsdContent == null || xsdContent.isBlank()) {
            clear();
            return;
        }

        // Check if cache is still valid
        String currentHash = String.valueOf(xsdContent.hashCode());
        if (cacheValid && Objects.equals(currentHash, lastXsdHash)) {
            logger.debug("Using cached XSD schema construct names");
            return;
        }

        // Clear and re-extract
        clear();

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAXParser parser = factory.newSAXParser();

            parser.parse(new InputSource(new StringReader(xsdContent)), new XsdSchemaHandler());

            lastXsdHash = currentHash;
            cacheValid = true;

            logger.debug("Extracted {} elements, {} complexTypes, {} simpleTypes, {} attributes from XSD",
                    elementNames.size(), complexTypeNames.size(), simpleTypeNames.size(), attributeNames.size());

        } catch (Exception e) {
            logger.warn("Failed to parse XSD for schema extraction: {}", e.getMessage());
            // Keep any partial results
        }
    }

    /**
     * Gets schema element completions matching the given prefix.
     *
     * @param prefix    the prefix to filter by (can be null)
     * @param baseScore the base relevance score
     * @return list of completion items for schema elements
     */
    public List<CompletionItem> getSchemaElementCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (String element : elementNames) {
            if (lowerPrefix.isEmpty() || element.toLowerCase().startsWith(lowerPrefix)) {
                int score = baseScore;
                if (!lowerPrefix.isEmpty() && element.toLowerCase().startsWith(lowerPrefix)) {
                    score += 10;
                }

                items.add(new CompletionItem.Builder(
                        element,
                        element,
                        CompletionItemType.ELEMENT
                )
                        .description("XSD Element")
                        .relevanceScore(score)
                        .build());
            }
        }

        return items;
    }

    /**
     * Gets schema type completions (both complex and simple types) matching the given prefix.
     *
     * @param prefix    the prefix to filter by (can be null)
     * @param baseScore the base relevance score
     * @return list of completion items for schema types
     */
    public List<CompletionItem> getSchemaTypeCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        // Add complex types
        for (String typeName : complexTypeNames) {
            if (lowerPrefix.isEmpty() || typeName.toLowerCase().startsWith(lowerPrefix)) {
                int score = baseScore;
                if (!lowerPrefix.isEmpty() && typeName.toLowerCase().startsWith(lowerPrefix)) {
                    score += 10;
                }

                items.add(new CompletionItem.Builder(
                        typeName,
                        typeName,
                        CompletionItemType.TYPE
                )
                        .description("XSD ComplexType")
                        .relevanceScore(score)
                        .build());
            }
        }

        // Add simple types
        for (String typeName : simpleTypeNames) {
            if (lowerPrefix.isEmpty() || typeName.toLowerCase().startsWith(lowerPrefix)) {
                int score = baseScore - 5; // Slightly lower than complex types
                if (!lowerPrefix.isEmpty() && typeName.toLowerCase().startsWith(lowerPrefix)) {
                    score += 10;
                }

                items.add(new CompletionItem.Builder(
                        typeName,
                        typeName,
                        CompletionItemType.TYPE
                )
                        .description("XSD SimpleType")
                        .relevanceScore(score)
                        .build());
            }
        }

        return items;
    }

    /**
     * Gets XSD-specific attribute completions for predicates.
     *
     * @param prefix    the prefix to filter by (can be null)
     * @param baseScore the base relevance score
     * @return list of completion items for XSD attributes
     */
    public List<CompletionItem> getXsdAttributeCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        // Add common XSD attributes
        for (String attr : XSD_COMMON_ATTRIBUTES) {
            if (lowerPrefix.isEmpty() || attr.toLowerCase().startsWith(lowerPrefix)) {
                int score = baseScore;
                if (!lowerPrefix.isEmpty() && attr.toLowerCase().startsWith(lowerPrefix)) {
                    score += 10;
                }

                // Higher priority for most common attributes
                if (attr.equals("name") || attr.equals("type") || attr.equals("ref")) {
                    score += 15;
                }

                items.add(new CompletionItem.Builder(
                        "@" + attr,
                        attr,
                        CompletionItemType.ATTRIBUTE
                )
                        .description("XSD Attribute")
                        .relevanceScore(score)
                        .build());
            }
        }

        // Add declared attribute names from the schema
        for (String attr : attributeNames) {
            if (lowerPrefix.isEmpty() || attr.toLowerCase().startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        "@" + attr,
                        attr,
                        CompletionItemType.ATTRIBUTE
                )
                        .description("Declared Attribute")
                        .relevanceScore(baseScore - 5)
                        .build());
            }
        }

        return items;
    }

    /**
     * Gets XSD built-in type completions for type attribute suggestions.
     *
     * @param prefix    the prefix to filter by (can be null)
     * @param baseScore the base relevance score
     * @return list of completion items for XSD built-in types
     */
    public List<CompletionItem> getBuiltinTypeCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (String typeName : XSD_BUILTIN_TYPES) {
            if (lowerPrefix.isEmpty() || typeName.toLowerCase().contains(lowerPrefix)) {
                int score = baseScore;

                // Boost common types
                if (typeName.equals("xs:string") || typeName.equals("xs:integer") ||
                        typeName.equals("xs:boolean") || typeName.equals("xs:date")) {
                    score += 10;
                }

                items.add(new CompletionItem.Builder(
                        typeName,
                        typeName,
                        CompletionItemType.TYPE
                )
                        .description("XSD Built-in Type")
                        .relevanceScore(score)
                        .build());
            }
        }

        return items;
    }

    /**
     * Gets group name completions.
     *
     * @param prefix    the prefix to filter by (can be null)
     * @param baseScore the base relevance score
     * @return list of completion items for model groups
     */
    public List<CompletionItem> getGroupCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (String group : groupNames) {
            if (lowerPrefix.isEmpty() || group.toLowerCase().startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        group,
                        group,
                        CompletionItemType.ELEMENT
                )
                        .description("XSD Group")
                        .relevanceScore(baseScore)
                        .build());
            }
        }

        for (String attrGroup : attributeGroupNames) {
            if (lowerPrefix.isEmpty() || attrGroup.toLowerCase().startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        attrGroup,
                        attrGroup,
                        CompletionItemType.ELEMENT
                )
                        .description("XSD AttributeGroup")
                        .relevanceScore(baseScore - 5)
                        .build());
            }
        }

        return items;
    }

    /**
     * Gets all schema element names.
     */
    public Set<String> getAllElementNames() {
        return Collections.unmodifiableSet(elementNames);
    }

    /**
     * Gets all complex type names.
     */
    public Set<String> getAllComplexTypeNames() {
        return Collections.unmodifiableSet(complexTypeNames);
    }

    /**
     * Gets all simple type names.
     */
    public Set<String> getAllSimpleTypeNames() {
        return Collections.unmodifiableSet(simpleTypeNames);
    }

    /**
     * Gets all attribute names declared in the schema.
     */
    public Set<String> getAllAttributeNames() {
        return Collections.unmodifiableSet(attributeNames);
    }

    /**
     * Clears all cached data.
     */
    public void clear() {
        elementNames.clear();
        complexTypeNames.clear();
        simpleTypeNames.clear();
        attributeNames.clear();
        attributeGroupNames.clear();
        groupNames.clear();
        lastXsdHash = null;
        cacheValid = false;
    }

    /**
     * Invalidates the cache, forcing re-extraction on next call.
     */
    public void invalidateCache() {
        cacheValid = false;
    }

    /**
     * Returns true if the cache is valid.
     */
    public boolean isCacheValid() {
        return cacheValid;
    }

    /**
     * Returns the total count of extracted schema constructs.
     */
    public int getTotalCount() {
        return elementNames.size() + complexTypeNames.size() + simpleTypeNames.size() +
                attributeNames.size() + groupNames.size() + attributeGroupNames.size();
    }

    /**
     * SAX handler that extracts named XSD schema constructs.
     */
    private class XsdSchemaHandler extends DefaultHandler {

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {

            // Only process XSD elements (check namespace or prefix)
            boolean isXsdElement = XSD_NS.equals(uri) ||
                    qName.startsWith("xs:") ||
                    qName.startsWith("xsd:");

            if (!isXsdElement && uri != null && !uri.isEmpty()) {
                return;
            }

            // Get the name attribute value if present
            String nameAttr = attributes.getValue("name");
            if (nameAttr == null || nameAttr.isEmpty()) {
                return; // Only extract named constructs
            }

            // Determine element type and categorize
            String elementType = localName != null && !localName.isEmpty() ? localName : qName;
            if (elementType.contains(":")) {
                elementType = elementType.substring(elementType.indexOf(':') + 1);
            }

            switch (elementType) {
                case "element" -> elementNames.add(nameAttr);
                case "complexType" -> complexTypeNames.add(nameAttr);
                case "simpleType" -> simpleTypeNames.add(nameAttr);
                case "attribute" -> attributeNames.add(nameAttr);
                case "attributeGroup" -> attributeGroupNames.add(nameAttr);
                case "group" -> groupNames.add(nameAttr);
                default -> {
                    // Ignore other constructs
                }
            }
        }
    }
}
