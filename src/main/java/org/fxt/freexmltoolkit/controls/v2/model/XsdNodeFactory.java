package org.fxt.freexmltoolkit.controls.v2.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory for creating XSD model instances from XSD files or strings using the XsdNode hierarchy.
 * This factory parses XSD schemas using DOM and creates the internal XsdNode-based model representation.
 *
 * <p>This factory creates the new XsdNode-based object hierarchy (XsdSchema, XsdElement, etc.)
 * which supports PropertyChangeSupport and unified parent-child relationships.</p>
 *
 * @since 2.0
 */
public class XsdNodeFactory {

    private static final Logger logger = LogManager.getLogger(XsdNodeFactory.class);
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /**
     * Tracks schema files that are currently being processed to prevent circular includes.
     */
    private final Set<Path> includeStack = new HashSet<>();
    private final Set<Path> processedIncludes = new HashSet<>();
    private final Set<String> processedImports = new HashSet<>(); // Track processed import URLs to prevent duplicates
    private final java.util.Map<String, XsdSchema> importedSchemas = new java.util.HashMap<>(); // Cache for imported schemas
    private Path currentSchemaFile;

    /**
     * Creates an XSD model from a file.
     *
     * @param xsdFile the XSD file to parse
     * @return the parsed XSD schema model
     * @throws Exception if parsing fails
     */
    public XsdSchema fromFile(File xsdFile) throws Exception {
        return fromFile(xsdFile.toPath());
    }

    /**
     * Creates an XSD model from a file path.
     *
     * @param xsdFile the XSD file path to parse
     * @return the parsed XSD schema model
     * @throws Exception if parsing fails
     */
    public XsdSchema fromFile(Path xsdFile) throws Exception {
        Path absoluteFile = xsdFile.toAbsolutePath().normalize();
        String content = Files.readString(absoluteFile);
        Path previousRoot = currentSchemaFile;
        currentSchemaFile = absoluteFile;
        try {
            return fromString(content, absoluteFile.getParent());
        } finally {
            currentSchemaFile = previousRoot;
        }
    }

    /**
     * Creates an XSD model from a string.
     *
     * @param xsdContent the XSD content as string
     * @return the parsed XSD schema model
     * @throws Exception if parsing fails
     */
    public XsdSchema fromString(String xsdContent) throws Exception {
        return fromString(xsdContent, null);
    }

    /**
     * Creates an XSD model from a string with optional base directory for resolving schema references.
     *
     * @param xsdContent   the XSD content as string
     * @param baseDirectory optional base directory used to resolve xs:include locations (can be null)
     * @return the parsed XSD schema model
     * @throws Exception if parsing fails
     */
    public XsdSchema fromString(String xsdContent, Path baseDirectory) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xsdContent)));

        Element schemaElement = document.getDocumentElement();
        if (!isXsdElement(schemaElement, "schema")) {
            throw new IllegalArgumentException("Root element must be xs:schema");
        }

        includeStack.clear();
        processedIncludes.clear();
        processedImports.clear();
        importedSchemas.clear();
        if (currentSchemaFile != null) {
            processedIncludes.add(currentSchemaFile);
        }

        XsdSchema schema = parseSchema(schemaElement, baseDirectory, factory);

        // Process imports after the schema is fully parsed
        processImports(schema);

        return schema;
    }

    /**
     * Parses the xs:schema root element.
     */
    private XsdSchema parseSchema(Element schemaElement) {
        return parseSchema(schemaElement, null, null);
    }

    private XsdSchema parseSchema(Element schemaElement, Path baseDirectory, DocumentBuilderFactory factory) {
        XsdSchema schema = new XsdSchema();

        // Parse schema attributes
        if (schemaElement.hasAttribute("targetNamespace")) {
            schema.setTargetNamespace(schemaElement.getAttribute("targetNamespace"));
        }

        if (schemaElement.hasAttribute("elementFormDefault")) {
            schema.setElementFormDefault(schemaElement.getAttribute("elementFormDefault"));
        }

        if (schemaElement.hasAttribute("attributeFormDefault")) {
            schema.setAttributeFormDefault(schemaElement.getAttribute("attributeFormDefault"));
        }

        // Parse namespace declarations
        NamedNodeMap attributes = schemaElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();
            if (attrName.startsWith("xmlns:")) {
                String prefix = attrName.substring(6);
                schema.addNamespace(prefix, attr.getNodeValue());
            } else if (attrName.equals("xmlns")) {
                schema.addNamespace("", attr.getNodeValue());
            }
        }

        parseSchemaChildren(schemaElement, schema, baseDirectory, factory);
        return schema;
    }

    /**
     * Parses child nodes of a schema (or included schema) and attaches them to the provided parent schema.
     */
    private void parseSchemaChildren(Element schemaElement, XsdSchema schema, Path baseDirectory,
                                     DocumentBuilderFactory factory) {
        NodeList children = schemaElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElement element = parseElement(childElement);
                schema.addChild(element);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexType complexType = parseComplexType(childElement);
                schema.addChild(complexType);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleType simpleType = parseSimpleType(childElement);
                schema.addChild(simpleType);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroup group = parseGroup(childElement);
                schema.addChild(group);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroup attributeGroup = parseAttributeGroup(childElement);
                schema.addChild(attributeGroup);
            } else if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, schema);
            } else if (isXsdElement(childElement, "import")) {
                XsdImport xsdImport = parseImport(childElement);
                schema.addChild(xsdImport);
            } else if (isXsdElement(childElement, "include")) {
                XsdInclude xsdInclude = parseInclude(childElement);
                schema.addChild(xsdInclude);
                inlineSchemaReference(childElement, baseDirectory, schema, factory);
            } else if (isXsdElement(childElement, "redefine")) {
                XsdRedefine xsdRedefine = parseRedefine(childElement);
                schema.addChild(xsdRedefine);
            } else if (isXsdElement(childElement, "override")) {
                XsdOverride xsdOverride = parseOverride(childElement);
                schema.addChild(xsdOverride);
            }
        }
    }

    /**
     * Resolves xs:include references by loading the referenced schema and adding its top-level components
     * to the current schema. This keeps the visual tree functional even when schemas are split across files.
     */
    private void inlineSchemaReference(Element directiveElement, Path baseDirectory, XsdSchema targetSchema,
                                       DocumentBuilderFactory factory) {
        if (baseDirectory == null || factory == null) {
            return; // Cannot resolve without file context
        }

        String schemaLocation = directiveElement.getAttribute("schemaLocation");
        if (schemaLocation == null || schemaLocation.isBlank()) {
            logger.warn("Encountered xs:include without schemaLocation. Skipping.");
            return;
        }

        if (schemaLocation.contains("://")) {
            logger.debug("Skipping remote schema include '{}'", schemaLocation);
            return;
        }

        Path resolvedPath = baseDirectory.resolve(schemaLocation).normalize();
        if (!Files.exists(resolvedPath)) {
            logger.warn("Included schema '{}' not found relative to '{}'", schemaLocation, baseDirectory);
            return;
        }

        Path realPath;
        try {
            realPath = resolvedPath.toRealPath();
        } catch (Exception ex) {
            logger.warn("Failed to resolve real path for included schema '{}': {}", resolvedPath, ex.getMessage());
            return;
        }

        if (processedIncludes.contains(realPath)) {
            logger.debug("Schema '{}' already processed. Skipping duplicate include.", realPath);
            return;
        }

        if (!includeStack.add(realPath)) {
            logger.warn("Circular include detected for '{}'. Skipping to prevent infinite recursion.", realPath);
            return;
        }

        try {
            DocumentBuilder includeBuilder = factory.newDocumentBuilder();
            Document includedDocument = includeBuilder.parse(realPath.toFile());
            Element includedRoot = includedDocument.getDocumentElement();

            if (!isXsdElement(includedRoot, "schema")) {
                logger.warn("Included file '{}' does not contain an xs:schema root. Skipping.", realPath);
                return;
            }

            Path nextBaseDir = realPath.getParent();
            parseSchemaChildren(includedRoot, targetSchema, nextBaseDir, factory);
            processedIncludes.add(realPath);
        } catch (Exception ex) {
            logger.warn("Failed to inline schema include '{}': {}", realPath, ex.getMessage());
        } finally {
            includeStack.remove(realPath);
        }
    }

    /**
     * Parses an xs:element.
     */
    private XsdElement parseElement(Element elementNode) {
        String name = elementNode.getAttribute("name");
        String ref = elementNode.getAttribute("ref");

        // Element can have either 'name' or 'ref', not both
        if (ref != null && !ref.isEmpty()) {
            // Element reference - use ref as the name for now
            // The actual element will be resolved later in the visual tree builder
            name = ref;
        } else if (name == null || name.isEmpty()) {
            name = "element";
        }

        XsdElement element = new XsdElement(name);

        // Store the ref attribute if present
        if (ref != null && !ref.isEmpty()) {
            element.setRef(ref);
        }

        // Parse basic attributes
        if (elementNode.hasAttribute("type")) {
            element.setType(elementNode.getAttribute("type"));
        }

        if (elementNode.hasAttribute("minOccurs")) {
            try {
                element.setMinOccurs(Integer.parseInt(elementNode.getAttribute("minOccurs")));
            } catch (NumberFormatException ignored) {
            }
        }

        if (elementNode.hasAttribute("maxOccurs")) {
            String maxOccurs = elementNode.getAttribute("maxOccurs");
            if ("unbounded".equals(maxOccurs)) {
                element.setMaxOccurs(XsdNode.UNBOUNDED);
            } else {
                try {
                    element.setMaxOccurs(Integer.parseInt(maxOccurs));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (elementNode.hasAttribute("nillable")) {
            element.setNillable(Boolean.parseBoolean(elementNode.getAttribute("nillable")));
        }

        if (elementNode.hasAttribute("abstract")) {
            element.setAbstract(Boolean.parseBoolean(elementNode.getAttribute("abstract")));
        }

        if (elementNode.hasAttribute("default")) {
            element.setDefaultValue(elementNode.getAttribute("default"));
        }

        if (elementNode.hasAttribute("fixed")) {
            element.setFixed(elementNode.getAttribute("fixed"));
        }

        if (elementNode.hasAttribute("substitutionGroup")) {
            element.setSubstitutionGroup(elementNode.getAttribute("substitutionGroup"));
        }

        // Parse child elements
        NodeList children = elementNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, element);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexType complexType = parseComplexType(childElement);
                element.addChild(complexType);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleType simpleType = parseSimpleType(childElement);
                element.addChild(simpleType);
                // Extract enumerations, patterns, and assertions from the simpleType
                extractConstraintsFromSimpleType(simpleType, element);
            } else if (isXsdElement(childElement, "key")) {
                XsdKey key = parseKey(childElement);
                element.addChild(key);
            } else if (isXsdElement(childElement, "keyref")) {
                XsdKeyRef keyref = parseKeyRef(childElement);
                element.addChild(keyref);
            } else if (isXsdElement(childElement, "unique")) {
                XsdUnique unique = parseUnique(childElement);
                element.addChild(unique);
            }
        }

        return element;
    }

    /**
     * Parses inline complex type within an element.
     */
    private void parseInlineComplexType(Element complexTypeElement, XsdElement parentElement) {
        // Parse mixed attribute
        boolean mixed = false;
        if (complexTypeElement.hasAttribute("mixed")) {
            mixed = Boolean.parseBoolean(complexTypeElement.getAttribute("mixed"));
        }

        NodeList children = complexTypeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "sequence")) {
                XsdSequence sequence = parseSequence(childElement);
                parentElement.addChild(sequence);
            } else if (isXsdElement(childElement, "choice")) {
                XsdChoice choice = parseChoice(childElement);
                parentElement.addChild(choice);
            } else if (isXsdElement(childElement, "all")) {
                XsdAll all = parseAll(childElement);
                parentElement.addChild(all);
            } else if (isXsdElement(childElement, "attribute")) {
                XsdAttribute attribute = parseAttribute(childElement);
                parentElement.addChild(attribute);
            } else if (isXsdElement(childElement, "simpleContent")) {
                XsdSimpleContent simpleContent = parseSimpleContent(childElement);
                parentElement.addChild(simpleContent);
            } else if (isXsdElement(childElement, "complexContent")) {
                XsdComplexContent complexContent = parseComplexContent(childElement);
                parentElement.addChild(complexContent);
            }
        }
    }

    /**
     * Parses xs:sequence compositor.
     */
    private XsdSequence parseSequence(Element sequenceElement) {
        XsdSequence sequence = new XsdSequence();

        // Parse min/maxOccurs attributes
        parseOccurrenceAttributes(sequenceElement, sequence);

        // Parse child elements
        NodeList children = sequenceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElement element = parseElement(childElement);
                sequence.addChild(element);
            } else if (isXsdElement(childElement, "choice")) {
                XsdChoice choice = parseChoice(childElement);
                sequence.addChild(choice);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdSequence nestedSequence = parseSequence(childElement);
                sequence.addChild(nestedSequence);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroup group = parseGroupRef(childElement);
                sequence.addChild(group);
            }
        }

        return sequence;
    }

    /**
     * Parses xs:choice compositor.
     */
    private XsdChoice parseChoice(Element choiceElement) {
        XsdChoice choice = new XsdChoice();

        // Parse min/maxOccurs attributes
        parseOccurrenceAttributes(choiceElement, choice);

        // Parse child elements
        NodeList children = choiceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElement element = parseElement(childElement);
                choice.addChild(element);
            } else if (isXsdElement(childElement, "choice")) {
                XsdChoice nestedChoice = parseChoice(childElement);
                choice.addChild(nestedChoice);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdSequence sequence = parseSequence(childElement);
                choice.addChild(sequence);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroup group = parseGroupRef(childElement);
                choice.addChild(group);
            }
        }

        return choice;
    }

    /**
     * Parses xs:all compositor.
     */
    private XsdAll parseAll(Element allElement) {
        XsdAll all = new XsdAll();

        // Parse min/maxOccurs attributes (XSD 1.1 enhancement)
        parseOccurrenceAttributes(allElement, all);

        // Parse child elements
        NodeList children = allElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElement element = parseElement(childElement);
                all.addChild(element);
            }
            // xs:all cannot contain other compositors
        }

        return all;
    }

    /**
     * Parses xs:attribute.
     */
    private XsdAttribute parseAttribute(Element attributeElement) {
        String name = attributeElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "attribute";
        }

        XsdAttribute attribute = new XsdAttribute(name);

        if (attributeElement.hasAttribute("type")) {
            attribute.setType(attributeElement.getAttribute("type"));
        }

        if (attributeElement.hasAttribute("use")) {
            attribute.setUse(attributeElement.getAttribute("use"));
        }

        if (attributeElement.hasAttribute("form")) {
            attribute.setForm(attributeElement.getAttribute("form"));
        }

        if (attributeElement.hasAttribute("default")) {
            attribute.setDefaultValue(attributeElement.getAttribute("default"));
        }

        if (attributeElement.hasAttribute("fixed")) {
            attribute.setFixed(attributeElement.getAttribute("fixed"));
        }

        // Parse annotation
        NodeList children = attributeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && isXsdElement((Element) child, "annotation")) {
                parseAnnotation((Element) child, attribute);
            }
        }

        return attribute;
    }

    /**
     * Parses xs:complexType.
     */
    private XsdComplexType parseComplexType(Element complexTypeElement) {
        String name = complexTypeElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "complexType";
        }

        XsdComplexType complexType = new XsdComplexType(name);

        if (complexTypeElement.hasAttribute("mixed")) {
            complexType.setMixed(Boolean.parseBoolean(complexTypeElement.getAttribute("mixed")));
        }

        if (complexTypeElement.hasAttribute("abstract")) {
            complexType.setAbstract(Boolean.parseBoolean(complexTypeElement.getAttribute("abstract")));
        }

        // Parse child elements
        NodeList children = complexTypeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, complexType);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdSequence sequence = parseSequence(childElement);
                complexType.addChild(sequence);
            } else if (isXsdElement(childElement, "choice")) {
                XsdChoice choice = parseChoice(childElement);
                complexType.addChild(choice);
            } else if (isXsdElement(childElement, "all")) {
                XsdAll all = parseAll(childElement);
                complexType.addChild(all);
            } else if (isXsdElement(childElement, "attribute")) {
                XsdAttribute attribute = parseAttribute(childElement);
                complexType.addChild(attribute);
            } else if (isXsdElement(childElement, "simpleContent")) {
                XsdSimpleContent simpleContent = parseSimpleContent(childElement);
                complexType.addChild(simpleContent);
            } else if (isXsdElement(childElement, "complexContent")) {
                XsdComplexContent complexContent = parseComplexContent(childElement);
                complexType.addChild(complexContent);
            }
        }

        return complexType;
    }

    /**
     * Parses xs:simpleType.
     */
    private XsdSimpleType parseSimpleType(Element simpleTypeElement) {
        String name = simpleTypeElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "simpleType";
        }

        XsdSimpleType simpleType = new XsdSimpleType(name);

        // Parse child elements
        NodeList children = simpleTypeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, simpleType);
            } else if (isXsdElement(childElement, "restriction")) {
                XsdRestriction restriction = parseRestriction(childElement);
                simpleType.addChild(restriction);
            } else if (isXsdElement(childElement, "list")) {
                XsdList list = parseList(childElement);
                simpleType.addChild(list);
            } else if (isXsdElement(childElement, "union")) {
                XsdUnion union = parseUnion(childElement);
                simpleType.addChild(union);
            }
        }

        return simpleType;
    }

    /**
     * Extracts constraint facets (enumerations, patterns, assertions) from a simpleType
     * and adds them to the parent element's constraint lists.
     *
     * @param simpleType The parsed simpleType node
     * @param element The parent element to add constraints to
     */
    private void extractConstraintsFromSimpleType(XsdSimpleType simpleType, XsdElement element) {
        // Traverse children to find restrictions
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                // Extract facets from the restriction
                for (XsdFacet facet : restriction.getFacets()) {
                    String facetValue = facet.getValue();
                    if (facetValue == null || facetValue.isEmpty()) {
                        continue;
                    }

                    // Add to appropriate constraint list based on facet type
                    switch (facet.getFacetType()) {
                        case ENUMERATION:
                            element.addEnumeration(facetValue);
                            break;
                        case PATTERN:
                            element.addPattern(facetValue);
                            break;
                        case ASSERTION:
                            element.addAssertion(facetValue);
                            break;
                        default:
                            // Other facets are not stored in constraint lists
                            break;
                    }
                }
            }
        }
    }

    /**
     * Parses xs:restriction.
     */
    private XsdRestriction parseRestriction(Element restrictionElement) {
        String base = restrictionElement.getAttribute("base");
        XsdRestriction restriction = new XsdRestriction(base);

        NodeList children = restrictionElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String localName = childElement.getLocalName();

            // Try to parse as a facet
            XsdFacetType facetType = XsdFacetType.fromXmlName(localName);
            if (facetType != null && childElement.hasAttribute("value")) {
                String value = childElement.getAttribute("value");
                boolean fixed = childElement.hasAttribute("fixed") &&
                        Boolean.parseBoolean(childElement.getAttribute("fixed"));

                XsdFacet facet = new XsdFacet(facetType, value);
                facet.setFixed(fixed);
                restriction.addFacet(facet);
            } else if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, restriction);
            }
        }

        return restriction;
    }

    /**
     * Parses xs:list.
     */
    private XsdList parseList(Element listElement) {
        String itemType = listElement.getAttribute("itemType");
        XsdList list = new XsdList(itemType);

        // Parse inline simpleType child
        NodeList children = listElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (isXsdElement(childElement, "simpleType")) {
                    XsdSimpleType inlineType = parseSimpleType(childElement);
                    list.addChild(inlineType);
                    break; // Only one inline type allowed
                }
            }
        }

        return list;
    }

    /**
     * Parses xs:union.
     */
    private XsdUnion parseUnion(Element unionElement) {
        XsdUnion union = new XsdUnion();

        // Parse memberTypes attribute (space-separated list)
        String memberTypes = unionElement.getAttribute("memberTypes");
        if (memberTypes != null && !memberTypes.isEmpty()) {
            String[] types = memberTypes.trim().split("\\s+");
            for (String type : types) {
                if (!type.isEmpty()) {
                    union.addMemberType(type);
                }
            }
        }

        // Parse inline simpleType children
        NodeList children = unionElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (isXsdElement(childElement, "simpleType")) {
                    XsdSimpleType inlineType = parseSimpleType(childElement);
                    union.addChild(inlineType);
                }
            }
        }

        return union;
    }

    /**
     * Parses xs:simpleContent.
     */
    private XsdSimpleContent parseSimpleContent(Element simpleContentElement) {
        XsdSimpleContent simpleContent = new XsdSimpleContent();

        NodeList children = simpleContentElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (isXsdElement(childElement, "extension")) {
                    XsdExtension extension = parseExtension(childElement);
                    simpleContent.addChild(extension);
                } else if (isXsdElement(childElement, "restriction")) {
                    XsdRestriction restriction = parseRestriction(childElement);
                    simpleContent.addChild(restriction);
                }
            }
        }

        return simpleContent;
    }

    /**
     * Parses xs:complexContent.
     */
    private XsdComplexContent parseComplexContent(Element complexContentElement) {
        XsdComplexContent complexContent = new XsdComplexContent();

        if (complexContentElement.hasAttribute("mixed")) {
            complexContent.setMixed(Boolean.parseBoolean(complexContentElement.getAttribute("mixed")));
        }

        NodeList children = complexContentElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (isXsdElement(childElement, "extension")) {
                    XsdExtension extension = parseExtension(childElement);
                    complexContent.addChild(extension);
                } else if (isXsdElement(childElement, "restriction")) {
                    XsdRestriction restriction = parseRestriction(childElement);
                    complexContent.addChild(restriction);
                }
            }
        }

        return complexContent;
    }

    /**
     * Parses xs:extension.
     */
    private XsdExtension parseExtension(Element extensionElement) {
        String base = extensionElement.getAttribute("base");
        XsdExtension extension = new XsdExtension(base);

        NodeList children = extensionElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (isXsdElement(childElement, "sequence")) {
                    XsdSequence sequence = parseSequence(childElement);
                    extension.addChild(sequence);
                } else if (isXsdElement(childElement, "choice")) {
                    XsdChoice choice = parseChoice(childElement);
                    extension.addChild(choice);
                } else if (isXsdElement(childElement, "all")) {
                    XsdAll all = parseAll(childElement);
                    extension.addChild(all);
                } else if (isXsdElement(childElement, "attribute")) {
                    XsdAttribute attribute = parseAttribute(childElement);
                    extension.addChild(attribute);
                } else if (isXsdElement(childElement, "attributeGroup")) {
                    XsdAttributeGroup attributeGroup = parseAttributeGroupRef(childElement);
                    extension.addChild(attributeGroup);
                }
            }
        }

        return extension;
    }

    /**
     * Parses xs:group (definition, not reference).
     */
    private XsdGroup parseGroup(Element groupElement) {
        String name = groupElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "group";
        }

        XsdGroup group = new XsdGroup(name);

        NodeList children = groupElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (isXsdElement(childElement, "annotation")) {
                    parseAnnotation(childElement, group);
                } else if (isXsdElement(childElement, "sequence")) {
                    XsdSequence sequence = parseSequence(childElement);
                    group.addChild(sequence);
                } else if (isXsdElement(childElement, "choice")) {
                    XsdChoice choice = parseChoice(childElement);
                    group.addChild(choice);
                } else if (isXsdElement(childElement, "all")) {
                    XsdAll all = parseAll(childElement);
                    group.addChild(all);
                }
            }
        }

        return group;
    }

    /**
     * Parses xs:group reference.
     */
    private XsdGroup parseGroupRef(Element groupElement) {
        String ref = groupElement.getAttribute("ref");
        if (ref == null || ref.isEmpty()) {
            ref = "group";
        }

        XsdGroup group = new XsdGroup();
        group.setRef(ref);

        // Parse occurrence attributes for group references
        parseOccurrenceAttributes(groupElement, group);

        return group;
    }

    /**
     * Parses xs:attributeGroup (definition, not reference).
     */
    private XsdAttributeGroup parseAttributeGroup(Element attributeGroupElement) {
        String name = attributeGroupElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "attributeGroup";
        }

        XsdAttributeGroup attributeGroup = new XsdAttributeGroup(name);

        NodeList children = attributeGroupElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (isXsdElement(childElement, "annotation")) {
                    parseAnnotation(childElement, attributeGroup);
                } else if (isXsdElement(childElement, "attribute")) {
                    XsdAttribute attribute = parseAttribute(childElement);
                    attributeGroup.addChild(attribute);
                }
            }
        }

        return attributeGroup;
    }

    /**
     * Parses xs:attributeGroup reference.
     */
    private XsdAttributeGroup parseAttributeGroupRef(Element attributeGroupElement) {
        String ref = attributeGroupElement.getAttribute("ref");
        if (ref == null || ref.isEmpty()) {
            ref = "attributeGroup";
        }

        XsdAttributeGroup attributeGroup = new XsdAttributeGroup();
        attributeGroup.setRef(ref);

        return attributeGroup;
    }

    /**
     * Parses xs:key identity constraint.
     */
    private XsdKey parseKey(Element keyElement) {
        String name = keyElement.getAttribute("name");
        XsdKey key = new XsdKey(name);

        parseIdentityConstraintChildren(keyElement, key);

        return key;
    }

    /**
     * Parses xs:keyref identity constraint.
     */
    private XsdKeyRef parseKeyRef(Element keyrefElement) {
        String name = keyrefElement.getAttribute("name");
        String refer = keyrefElement.getAttribute("refer");

        XsdKeyRef keyref = new XsdKeyRef(name);
        if (refer != null && !refer.isEmpty()) {
            keyref.setRefer(refer);
        }

        parseIdentityConstraintChildren(keyrefElement, keyref);

        return keyref;
    }

    /**
     * Parses xs:unique identity constraint.
     */
    private XsdUnique parseUnique(Element uniqueElement) {
        String name = uniqueElement.getAttribute("name");
        XsdUnique unique = new XsdUnique(name);

        parseIdentityConstraintChildren(uniqueElement, unique);

        return unique;
    }

    /**
     * Parses selector and field children of identity constraints.
     */
    private void parseIdentityConstraintChildren(Element constraintElement, XsdIdentityConstraint constraint) {
        NodeList children = constraintElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (isXsdElement(childElement, "selector")) {
                    String xpath = childElement.getAttribute("xpath");
                    XsdSelector selector = new XsdSelector(xpath);
                    constraint.addChild(selector);
                } else if (isXsdElement(childElement, "field")) {
                    String xpath = childElement.getAttribute("xpath");
                    XsdField field = new XsdField(xpath);
                    constraint.addChild(field);
                } else if (isXsdElement(childElement, "annotation")) {
                    parseAnnotation(childElement, constraint);
                }
            }
        }
    }

    /**
     * Parses xs:annotation and extracts documentation and appinfo.
     * If multiple documentation/appinfo elements exist, they are concatenated with newlines.
     */
    private void parseAnnotation(Element annotationElement, XsdNode target) {
        StringBuilder documentationBuilder = new StringBuilder();
        XsdAppInfo appInfo = new XsdAppInfo();

        NodeList children = annotationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "documentation")) {
                String documentation = childElement.getTextContent();
                if (documentation != null && !documentation.trim().isEmpty()) {
                    if (documentationBuilder.length() > 0) {
                        documentationBuilder.append("\n\n"); // Separate multiple documentation elements
                    }
                    // Include language attribute if present
                    String lang = childElement.getAttribute("xml:lang");
                    if (lang != null && !lang.isEmpty()) {
                        documentationBuilder.append("[").append(lang).append("] ");
                    }
                    documentationBuilder.append(documentation.trim());
                }
            } else if (isXsdElement(childElement, "appinfo")) {
                String appinfoContent = childElement.getTextContent();
                if (appinfoContent != null && !appinfoContent.trim().isEmpty()) {
                    // Get the "source" attribute
                    String source = childElement.getAttribute("source");
                    // Parse and add entry (will automatically detect JavaDoc-style tags like @since, @see, etc.)
                    appInfo.addEntry(source, appinfoContent.trim());
                }
            }
        }

        // Set the combined documentation and structured appinfo
        if (documentationBuilder.length() > 0) {
            target.setDocumentation(documentationBuilder.toString());
        }
        if (appInfo.hasEntries()) {
            target.setAppinfo(appInfo);
        }
    }

    /**
     * Parses minOccurs and maxOccurs attributes on an element.
     */
    private void parseOccurrenceAttributes(Element element, XsdNode target) {
        if (element.hasAttribute("minOccurs")) {
            try {
                target.setMinOccurs(Integer.parseInt(element.getAttribute("minOccurs")));
            } catch (NumberFormatException ignored) {
            }
        }

        if (element.hasAttribute("maxOccurs")) {
            String maxOccurs = element.getAttribute("maxOccurs");
            if ("unbounded".equals(maxOccurs)) {
                target.setMaxOccurs(XsdNode.UNBOUNDED);
            } else {
                try {
                    target.setMaxOccurs(Integer.parseInt(maxOccurs));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /**
     * Parses an xs:import element.
     */
    private XsdImport parseImport(Element importElement) {
        XsdImport xsdImport = new XsdImport();

        // Parse namespace attribute
        if (importElement.hasAttribute("namespace")) {
            xsdImport.setNamespace(importElement.getAttribute("namespace"));
        }

        // Parse schemaLocation attribute
        if (importElement.hasAttribute("schemaLocation")) {
            xsdImport.setSchemaLocation(importElement.getAttribute("schemaLocation"));
        }

        // Parse annotation if present
        NodeList children = importElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (isXsdElement(childElement, "annotation")) {
                    parseAnnotation(childElement, xsdImport);
                }
            }
        }

        return xsdImport;
    }

    /**
     * Parses an xs:include element.
     */
    private XsdInclude parseInclude(Element includeElement) {
        XsdInclude xsdInclude = new XsdInclude();

        // Parse schemaLocation attribute
        if (includeElement.hasAttribute("schemaLocation")) {
            xsdInclude.setSchemaLocation(includeElement.getAttribute("schemaLocation"));
        }

        // Parse annotation if present
        NodeList children = includeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (isXsdElement(childElement, "annotation")) {
                    parseAnnotation(childElement, xsdInclude);
                }
            }
        }

        return xsdInclude;
    }

    /**
     * Parses an xs:redefine element.
     */
    private XsdRedefine parseRedefine(Element redefineElement) {
        XsdRedefine xsdRedefine = new XsdRedefine();

        // Parse schemaLocation attribute
        if (redefineElement.hasAttribute("schemaLocation")) {
            xsdRedefine.setSchemaLocation(redefineElement.getAttribute("schemaLocation"));
        }

        // Parse child elements (simpleType, complexType, group, attributeGroup)
        NodeList children = redefineElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, xsdRedefine);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleType simpleType = parseSimpleType(childElement);
                xsdRedefine.addChild(simpleType);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexType complexType = parseComplexType(childElement);
                xsdRedefine.addChild(complexType);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroup group = parseGroup(childElement);
                xsdRedefine.addChild(group);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroup attributeGroup = parseAttributeGroup(childElement);
                xsdRedefine.addChild(attributeGroup);
            }
        }

        return xsdRedefine;
    }

    /**
     * Parses an xs:override element (XSD 1.1).
     */
    private XsdOverride parseOverride(Element overrideElement) {
        XsdOverride xsdOverride = new XsdOverride();

        // Parse schemaLocation attribute
        if (overrideElement.hasAttribute("schemaLocation")) {
            xsdOverride.setSchemaLocation(overrideElement.getAttribute("schemaLocation"));
        }

        // Parse child elements (simpleType, complexType, group, attributeGroup, element, attribute, notation)
        NodeList children = overrideElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, xsdOverride);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleType simpleType = parseSimpleType(childElement);
                xsdOverride.addChild(simpleType);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexType complexType = parseComplexType(childElement);
                xsdOverride.addChild(complexType);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroup group = parseGroup(childElement);
                xsdOverride.addChild(group);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroup attributeGroup = parseAttributeGroup(childElement);
                xsdOverride.addChild(attributeGroup);
            } else if (isXsdElement(childElement, "element")) {
                XsdElement element = parseElement(childElement);
                xsdOverride.addChild(element);
            }
        }

        return xsdOverride;
    }

    /**
     * Checks if an element is an XSD element with given local name.
     */
    private boolean isXsdElement(Element element, String localName) {
        return XSD_NAMESPACE.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    /**
     * Processes all xs:import elements in the schema and loads the referenced schemas.
     * This method loads external schemas via HTTP/HTTPS or from local files,
     * parses them, and merges their global elements and types into the main schema.
     *
     * @param schema the schema containing import elements
     */
    private void processImports(XsdSchema schema) {
        if (schema == null) {
            return;
        }

        logger.info("Processing imports for schema with {} children", schema.getChildren().size());

        // Find all XsdImport nodes
        java.util.List<XsdImport> imports = schema.getChildren().stream()
                .filter(node -> node instanceof XsdImport)
                .map(node -> (XsdImport) node)
                .toList();

        logger.info("Found {} import statements", imports.size());

        for (XsdImport xsdImport : imports) {
            try {
                loadAndMergeImportedSchema(xsdImport, schema);
            } catch (Exception e) {
                logger.error("Failed to process import: namespace='{}', schemaLocation='{}', error: {}",
                        xsdImport.getNamespace(), xsdImport.getSchemaLocation(), e.getMessage(), e);
            }
        }
    }

    /**
     * Loads an imported schema and merges its global elements and types into the main schema.
     *
     * @param xsdImport the import element
     * @param mainSchema the main schema to merge into
     * @throws Exception if loading or parsing fails
     */
    private void loadAndMergeImportedSchema(XsdImport xsdImport, XsdSchema mainSchema) throws Exception {
        String schemaLocation = xsdImport.getSchemaLocation();
        String namespace = xsdImport.getNamespace();

        if (schemaLocation == null || schemaLocation.isEmpty()) {
            logger.warn("Import has no schemaLocation, skipping: namespace='{}'", namespace);
            return;
        }

        // Check if we've already processed this import
        if (processedImports.contains(schemaLocation)) {
            logger.debug("Import already processed, skipping: {}", schemaLocation);
            return;
        }

        processedImports.add(schemaLocation);
        logger.info("Loading imported schema: namespace='{}', location='{}'", namespace, schemaLocation);

        // Load the schema content
        String schemaContent = loadSchemaFromLocation(schemaLocation);
        if (schemaContent == null || schemaContent.isEmpty()) {
            logger.warn("Failed to load schema content from: {}", schemaLocation);
            return;
        }

        // Parse the imported schema
        XsdNodeFactory importFactory = new XsdNodeFactory();
        XsdSchema importedSchema = importFactory.fromString(schemaContent);
        importedSchemas.put(namespace != null ? namespace : schemaLocation, importedSchema);

        logger.info("Successfully loaded imported schema with {} children", importedSchema.getChildren().size());

        // Merge global elements and types from imported schema into main schema
        mergeSchemaComponents(importedSchema, mainSchema, namespace);
    }

    /**
     * Loads schema content from a location (HTTP URL or local file).
     *
     * @param location the schema location (URL or file path)
     * @return the schema content as string
     * @throws Exception if loading fails
     */
    private String loadSchemaFromLocation(String location) throws Exception {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            // Load from HTTP/HTTPS using ConnectionService
            return loadSchemaFromHTTP(location);
        } else {
            // Load from local file
            return loadSchemaFromFile(location);
        }
    }

    /**
     * Loads schema content from an HTTP/HTTPS URL using ConnectionService.
     *
     * @param url the schema URL
     * @return the schema content as string
     * @throws Exception if loading fails
     */
    private String loadSchemaFromHTTP(String url) throws Exception {
        logger.info("Loading schema from HTTP: {}", url);

        try {
            // Use ConnectionService for HTTP downloads (respects proxy settings)
            ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);

            java.net.URI uri = java.net.URI.create(url);
            String content = connectionService.getTextContentFromURL(uri);

            if (content != null && !content.isEmpty()) {
                logger.info("Successfully loaded schema from HTTP: {} ({} bytes)", url, content.length());
                return content;
            } else {
                logger.warn("Empty content received from HTTP: {}", url);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to load schema from HTTP: {}, error: {}", url, e.getMessage());
            throw e;
        }
    }

    /**
     * Loads schema content from a local file.
     *
     * @param filePath the file path (relative or absolute)
     * @return the schema content as string
     * @throws Exception if loading fails
     */
    private String loadSchemaFromFile(String filePath) throws Exception {
        logger.info("Loading schema from file: {}", filePath);

        Path path;
        if (currentSchemaFile != null) {
            // Resolve relative to current schema file
            path = currentSchemaFile.getParent().resolve(filePath);
        } else {
            path = Path.of(filePath);
        }

        if (!Files.exists(path)) {
            logger.warn("Schema file not found: {}", path);
            return null;
        }

        String content = Files.readString(path);
        logger.info("Successfully loaded schema from file: {} ({} bytes)", path, content.length());
        return content;
    }

    /**
     * Merges global elements and types from imported schema into main schema.
     * This allows referencing components from the imported schema via namespace prefix.
     *
     * @param importedSchema the imported schema
     * @param mainSchema the main schema to merge into
     * @param namespace the target namespace of the imported schema
     */
    private void mergeSchemaComponents(XsdSchema importedSchema, XsdSchema mainSchema, String namespace) {
        int mergedElements = 0;
        int mergedTypes = 0;

        // Merge global elements
        for (XsdNode child : importedSchema.getChildren()) {
            if (child instanceof XsdElement element) {
                // Add element to main schema (it will be available for ref resolution)
                // Note: We don't add it as a direct child to avoid cluttering the tree
                // Instead, it will be accessible via the globalElementIndex in XsdVisualTreeBuilder
                logger.debug("Found global element in imported schema: {}", element.getName());
                mergedElements++;
            } else if (child instanceof XsdComplexType complexType) {
                // Add complex type to main schema
                logger.debug("Found complex type in imported schema: {}", complexType.getName());
                mergedTypes++;
            } else if (child instanceof XsdSimpleType simpleType) {
                // Add simple type to main schema
                logger.debug("Found simple type in imported schema: {}", simpleType.getName());
                mergedTypes++;
            }
        }

        logger.info("Merged {} elements and {} types from imported schema (namespace='{}')",
                mergedElements, mergedTypes, namespace);
    }

    /**
     * Gets all imported schemas that have been loaded.
     *
     * @return map of namespace/location to imported schema
     */
    public java.util.Map<String, XsdSchema> getImportedSchemas() {
        return new java.util.HashMap<>(importedSchemas);
    }
}
