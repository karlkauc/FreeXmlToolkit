package org.fxt.freexmltoolkit.controls.v2.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
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
 * <p>Multi-file support: When parsing XSD schemas with xs:include statements, this factory
 * tracks which file each node comes from using {@link IncludeTracker} and {@link IncludeSourceInfo}.
 * This enables the serializer to write changes back to the correct source files.</p>
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
     * Tracks the include hierarchy and assigns source info to parsed nodes.
     * Initialized when loading from a file path.
     */
    private IncludeTracker includeTracker;

    /**
     * Flag to control whether include structure is preserved for multi-file serialization.
     * When true (default), nodes are tagged with their source file information.
     */
    private boolean preserveIncludeStructure = true;

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

        // Initialize include tracker for multi-file support
        if (preserveIncludeStructure) {
            includeTracker = new IncludeTracker(absoluteFile);
        }

        try {
            XsdSchema schema = fromString(content, absoluteFile.getParent());

            // Set the main schema path for multi-file tracking
            schema.setMainSchemaPath(absoluteFile);

            return schema;
        } finally {
            currentSchemaFile = previousRoot;
        }
    }

    /**
     * Sets whether include structure should be preserved for multi-file serialization.
     *
     * @param preserveIncludeStructure true to track source files, false to flatten
     */
    public void setPreserveIncludeStructure(boolean preserveIncludeStructure) {
        this.preserveIncludeStructure = preserveIncludeStructure;
    }

    /**
     * Checks if include structure preservation is enabled.
     *
     * @return true if source tracking is enabled
     */
    public boolean isPreserveIncludeStructure() {
        return preserveIncludeStructure;
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
        return fromStringWithSchemaFile(xsdContent, null, baseDirectory);
    }

    /**
     * Creates an XSD model from a string with support for multi-file include tracking.
     * <p>
     * This method is useful when the XSD content comes from an edited text area but we still
     * know the original file path. It enables proper source tracking for nodes from xs:include files.
     *
     * @param xsdContent      the XSD content as string
     * @param mainSchemaFile  the path to the main schema file (for include tracking, can be null)
     * @param baseDirectory   optional base directory used to resolve xs:include locations (can be null)
     * @return the parsed XSD schema model
     * @throws Exception if parsing fails
     */
    public XsdSchema fromStringWithSchemaFile(String xsdContent, Path mainSchemaFile, Path baseDirectory) throws Exception {
        // Initialize include tracker if we have a main schema file and tracking is enabled
        if (mainSchemaFile != null && preserveIncludeStructure) {
            currentSchemaFile = mainSchemaFile.toAbsolutePath().normalize();
            includeTracker = new IncludeTracker(currentSchemaFile);
            logger.debug("Include tracking enabled for schema: {}", currentSchemaFile);
        }
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

        // Capture leading comments (comments before the schema element)
        NodeList documentChildren = document.getChildNodes();
        for (int i = 0; i < documentChildren.getLength(); i++) {
            Node node = documentChildren.item(i);
            if (node.getNodeType() == Node.COMMENT_NODE) {
                schema.addLeadingComment(node.getNodeValue());
            } else if (node == schemaElement) {
                // Stop when we reach the schema element
                break;
            }
        }

        // Process imports after the schema is fully parsed
        processImports(schema);

        // Set main schema path if we have include tracking
        if (currentSchemaFile != null) {
            schema.setMainSchemaPath(currentSchemaFile);
        }

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

        if (schemaElement.hasAttribute("version")) {
            schema.setVersion(schemaElement.getAttribute("version"));
        }

        // Parse namespace declarations and additional attributes
        NamedNodeMap attributes = schemaElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();
            if (attrName.startsWith("xmlns:")) {
                String prefix = attrName.substring(6);
                schema.addNamespace(prefix, attr.getNodeValue());
            } else if (attrName.equals("xmlns")) {
                schema.addNamespace("", attr.getNodeValue());
            } else if (!isStandardSchemaAttribute(attrName)) {
                // Store additional attributes like vc:minVersion, finalDefault, blockDefault, etc.
                schema.setAdditionalAttribute(attrName, attr.getNodeValue());
            }
        }

        parseSchemaChildren(schemaElement, schema, baseDirectory, factory);
        return schema;
    }

    /**
     * Parses child nodes of a schema (or included schema) and attaches them to the provided parent schema.
     * When include tracking is enabled, each node is tagged with its source file information.
     */
    private void parseSchemaChildren(Element schemaElement, XsdSchema schema, Path baseDirectory,
                                     DocumentBuilderFactory factory) {
        NodeList children = schemaElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            // Handle comment nodes
            if (child.getNodeType() == Node.COMMENT_NODE) {
                XsdComment comment = new XsdComment(child.getNodeValue());
                tagNodeWithSourceInfo(comment);
                schema.addChild(comment);
                continue;
            }

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElement element = parseElement(childElement);
                tagNodeWithSourceInfo(element);
                schema.addChild(element);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexType complexType = parseComplexType(childElement);
                tagNodeWithSourceInfo(complexType);
                schema.addChild(complexType);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleType simpleType = parseSimpleType(childElement);
                tagNodeWithSourceInfo(simpleType);
                schema.addChild(simpleType);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroup group = parseGroup(childElement);
                tagNodeWithSourceInfo(group);
                schema.addChild(group);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroup attributeGroup = parseAttributeGroup(childElement);
                tagNodeWithSourceInfo(attributeGroup);
                schema.addChild(attributeGroup);
            } else if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, schema);
            } else if (isXsdElement(childElement, "import")) {
                XsdImport xsdImport = parseImport(childElement);
                tagNodeWithSourceInfo(xsdImport);
                schema.addChild(xsdImport);
            } else if (isXsdElement(childElement, "include")) {
                XsdInclude xsdInclude = parseInclude(childElement);
                tagNodeWithSourceInfo(xsdInclude);
                schema.addChild(xsdInclude);
                inlineSchemaReference(childElement, baseDirectory, schema, factory, xsdInclude);
            } else if (isXsdElement(childElement, "redefine")) {
                XsdRedefine xsdRedefine = parseRedefine(childElement);
                tagNodeWithSourceInfo(xsdRedefine);
                schema.addChild(xsdRedefine);
            } else if (isXsdElement(childElement, "override")) {
                XsdOverride xsdOverride = parseOverride(childElement);
                tagNodeWithSourceInfo(xsdOverride);
                schema.addChild(xsdOverride);
            }
        }
    }

    /**
     * Tags a node with the current source file information from the include tracker.
     * This method recursively tags all descendants with the same source info.
     *
     * @param node the node to tag
     */
    private void tagNodeWithSourceInfo(XsdNode node) {
        if (node == null || includeTracker == null || !preserveIncludeStructure) {
            return;
        }

        includeTracker.tagNodeRecursively(node);
    }

    /**
     * Resolves xs:include references by loading the referenced schema and adding its top-level components
     * to the current schema. This keeps the visual tree functional even when schemas are split across files.
     *
     * <p>When include tracking is enabled, nodes parsed from the included file are tagged with their
     * source information, enabling multi-file serialization.</p>
     *
     * @param directiveElement the xs:include DOM element
     * @param baseDirectory    the base directory for resolving relative paths
     * @param targetSchema     the schema to add components to
     * @param factory          the document builder factory
     * @param xsdInclude       the XsdInclude model node for tracking
     */
    private void inlineSchemaReference(Element directiveElement, Path baseDirectory, XsdSchema targetSchema,
                                       DocumentBuilderFactory factory, XsdInclude xsdInclude) {
        if (baseDirectory == null || factory == null) {
            return; // Cannot resolve without file context
        }

        String schemaLocation = directiveElement.getAttribute("schemaLocation");
        if (schemaLocation == null || schemaLocation.isBlank()) {
            logger.warn("Encountered xs:include without schemaLocation. Skipping.");
            if (xsdInclude != null) {
                xsdInclude.markResolutionFailed("No schemaLocation attribute");
            }
            return;
        }

        if (schemaLocation.contains("://")) {
            logger.debug("Skipping remote schema include '{}'", schemaLocation);
            if (xsdInclude != null) {
                xsdInclude.markResolutionFailed("Remote schemas not supported");
            }
            return;
        }

        Path resolvedPath = baseDirectory.resolve(schemaLocation).normalize();
        if (!Files.exists(resolvedPath)) {
            logger.warn("Included schema '{}' not found relative to '{}'", schemaLocation, baseDirectory);
            if (xsdInclude != null) {
                xsdInclude.markResolutionFailed("File not found: " + resolvedPath);
            }
            return;
        }

        Path realPath;
        try {
            realPath = resolvedPath.toRealPath();
        } catch (Exception ex) {
            logger.warn("Failed to resolve real path for included schema '{}': {}", resolvedPath, ex.getMessage());
            if (xsdInclude != null) {
                xsdInclude.markResolutionFailed("Path resolution failed: " + ex.getMessage());
            }
            return;
        }

        if (processedIncludes.contains(realPath)) {
            logger.debug("Schema '{}' already processed. Skipping duplicate include.", realPath);
            return;
        }

        if (!includeStack.add(realPath)) {
            logger.warn("Circular include detected for '{}'. Skipping to prevent infinite recursion.", realPath);
            if (xsdInclude != null) {
                xsdInclude.markResolutionFailed("Circular include detected");
            }
            return;
        }

        try {
            DocumentBuilder includeBuilder = factory.newDocumentBuilder();
            Document includedDocument = includeBuilder.parse(realPath.toFile());
            Element includedRoot = includedDocument.getDocumentElement();

            if (!isXsdElement(includedRoot, "schema")) {
                logger.warn("Included file '{}' does not contain an xs:schema root. Skipping.", realPath);
                if (xsdInclude != null) {
                    xsdInclude.markResolutionFailed("Not a valid XSD schema");
                }
                return;
            }

            // Set resolution info on the XsdInclude node
            if (xsdInclude != null) {
                xsdInclude.setResolvedPath(realPath);
            }

            // Push include context for source tracking
            if (includeTracker != null && preserveIncludeStructure && xsdInclude != null) {
                includeTracker.pushContext(xsdInclude, realPath);
            }

            try {
                Path nextBaseDir = realPath.getParent();

                // Track child count before parsing to register new nodes
                int childCountBefore = targetSchema.getChildren().size();

                parseSchemaChildren(includedRoot, targetSchema, nextBaseDir, factory);

                // Register newly added nodes with the schema's include tracking
                if (preserveIncludeStructure && xsdInclude != null) {
                    java.util.List<XsdNode> allChildren = targetSchema.getChildren();
                    for (int i = childCountBefore; i < allChildren.size(); i++) {
                        XsdNode addedNode = allChildren.get(i);
                        targetSchema.registerNodeForInclude(addedNode, xsdInclude);
                    }
                }

                processedIncludes.add(realPath);

                // Mark include as successfully resolved
                if (xsdInclude != null) {
                    // Create a minimal schema representation for the included content
                    XsdSchema includedSchemaRef = new XsdSchema();
                    includedSchemaRef.setTargetNamespace(targetSchema.getTargetNamespace());
                    includedSchemaRef.setMainSchemaPath(realPath);
                    xsdInclude.setIncludedSchema(includedSchemaRef);
                }

                logger.debug("Successfully inlined schema from '{}' with {} new components",
                        realPath, targetSchema.getChildren().size() - childCountBefore);

            } finally {
                // Pop include context
                if (includeTracker != null && preserveIncludeStructure && xsdInclude != null) {
                    includeTracker.popContext();
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to inline schema include '{}': {}", realPath, ex.getMessage());
            if (xsdInclude != null) {
                xsdInclude.markResolutionFailed("Parse error: " + ex.getMessage());
            }
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

        if (elementNode.hasAttribute("form")) {
            element.setForm(elementNode.getAttribute("form"));
        }

        if (elementNode.hasAttribute("block")) {
            element.setBlock(elementNode.getAttribute("block"));
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
            } else if (isXsdElement(childElement, "any")) {
                XsdAny any = parseAny(childElement);
                sequence.addChild(any);
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
            } else if (isXsdElement(childElement, "any")) {
                XsdAny any = parseAny(childElement);
                choice.addChild(any);
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
            } else if (isXsdElement(childElement, "any")) {
                // XSD 1.1 allows xs:any in xs:all
                XsdAny any = parseAny(childElement);
                all.addChild(any);
            }
        }

        return all;
    }

    /**
     * Parses xs:attribute.
     */
    private XsdAttribute parseAttribute(Element attributeElement) {
        String name = attributeElement.getAttribute("name");
        String ref = attributeElement.getAttribute("ref");

        // Attribute can have either 'name' or 'ref', not both
        if (ref != null && !ref.isEmpty()) {
            // Attribute reference - use ref as the name for now
            name = ref;
        } else if (name == null || name.isEmpty()) {
            name = "attribute";
        }

        XsdAttribute attribute = new XsdAttribute(name);

        // Store the ref attribute if present
        if (ref != null && !ref.isEmpty()) {
            attribute.setRef(ref);
        }

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
        // Get name attribute - null/empty for anonymous types
        String name = complexTypeElement.getAttribute("name");
        if (name != null && name.isEmpty()) {
            name = null; // Anonymous complexType - no name attribute in output
        }

        XsdComplexType complexType = new XsdComplexType(name);

        if (complexTypeElement.hasAttribute("mixed")) {
            complexType.setMixed(Boolean.parseBoolean(complexTypeElement.getAttribute("mixed")));
        }

        if (complexTypeElement.hasAttribute("abstract")) {
            complexType.setAbstract(Boolean.parseBoolean(complexTypeElement.getAttribute("abstract")));
        }

        if (complexTypeElement.hasAttribute("block")) {
            complexType.setBlock(complexTypeElement.getAttribute("block"));
        }

        if (complexTypeElement.hasAttribute("final")) {
            complexType.setFinal(complexTypeElement.getAttribute("final"));
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
            } else if (isXsdElement(childElement, "anyAttribute")) {
                XsdAnyAttribute anyAttr = parseAnyAttribute(childElement);
                complexType.addChild(anyAttr);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroup attributeGroup = parseAttributeGroupRef(childElement);
                complexType.addChild(attributeGroup);
            }
        }

        return complexType;
    }

    /**
     * Parses xs:simpleType.
     */
    private XsdSimpleType parseSimpleType(Element simpleTypeElement) {
        // Get name attribute - null/empty for anonymous types
        String name = simpleTypeElement.getAttribute("name");
        if (name != null && name.isEmpty()) {
            name = null; // Anonymous simpleType - no name attribute in output
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
                } else if (isXsdElement(childElement, "anyAttribute")) {
                    XsdAnyAttribute anyAttr = parseAnyAttribute(childElement);
                    extension.addChild(anyAttr);
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
     * Parses legacy documentation format with [lang] markers.
     * Example: "[en] English text [de] German text" -> two XsdDocumentation entries
     *
     * @param text the documentation text possibly containing [lang] markers
     * @return list of parsed XsdDocumentation entries, or null if not legacy format
     */
    private java.util.List<XsdDocumentation> parseLegacyDocumentation(String text) {
        // Pattern: [lang] followed by text until next [lang] or end
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[([a-z]{2,3})\\]\\s*([^\\[]+)");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        java.util.List<XsdDocumentation> docs = new java.util.ArrayList<>();
        while (matcher.find()) {
            String lang = matcher.group(1);
            String docText = matcher.group(2).trim();
            if (!docText.isEmpty()) {
                docs.add(new XsdDocumentation(docText, lang));
            }
        }

        // If we found at least one [lang] marker, return the parsed docs
        // Otherwise return null to indicate this is not legacy format
        return docs.isEmpty() ? null : docs;
    }

    /**
     * Parses xs:annotation and extracts documentation and appinfo.
     * Each documentation element is preserved separately with its xml:lang attribute.
     */
    private void parseAnnotation(Element annotationElement, XsdNode target) {
        XsdAppInfo appInfo = new XsdAppInfo();

        NodeList children = annotationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "documentation")) {
                String text = childElement.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    // Use getAttributeNS for namespace-qualified attributes like xml:lang
                    String lang = childElement.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");
                    String source = childElement.getAttribute("source");

                    // Check if this is a legacy format with [lang] markers
                    if (lang == null || lang.isEmpty()) {
                        java.util.List<XsdDocumentation> parsed = parseLegacyDocumentation(text.trim());
                        if (parsed != null && !parsed.isEmpty()) {
                            // Legacy format detected, add all parsed entries
                            for (XsdDocumentation doc : parsed) {
                                target.addDocumentation(doc);
                            }
                        } else {
                            // Not legacy format, add as-is
                            XsdDocumentation doc = new XsdDocumentation(text.trim(), null,
                                    (source != null && !source.isEmpty()) ? source : null);
                            target.addDocumentation(doc);
                        }
                    } else {
                        // Has xml:lang attribute, use it directly
                        XsdDocumentation doc = new XsdDocumentation(
                                text.trim(),
                                lang,
                                (source != null && !source.isEmpty()) ? source : null
                        );
                        target.addDocumentation(doc);
                    }
                }
            } else if (isXsdElement(childElement, "appinfo")) {
                // Get the "source" attribute
                String source = childElement.getAttribute("source");

                // Check if appinfo has child elements (complex XML content)
                boolean hasChildElements = false;
                NodeList appinfoChildren = childElement.getChildNodes();
                for (int j = 0; j < appinfoChildren.getLength(); j++) {
                    if (appinfoChildren.item(j).getNodeType() == Node.ELEMENT_NODE) {
                        hasChildElements = true;
                        break;
                    }
                }

                if (hasChildElements) {
                    // Serialize inner XML content
                    String rawXml = serializeInnerXml(childElement);
                    String textContent = childElement.getTextContent();
                    appInfo.addEntry(source, textContent != null ? textContent.trim() : "", rawXml);
                } else {
                    // Simple text content
                    String appinfoContent = childElement.getTextContent();
                    if (appinfoContent != null && !appinfoContent.trim().isEmpty()) {
                        appInfo.addEntry(source, appinfoContent.trim());
                    }
                }
            }
        }

        // Do NOT set legacy documentation string - let the serializer handle it
        // The legacy string is only used as fallback when documentations list is empty

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
     * Parses xs:any wildcard.
     */
    private XsdAny parseAny(Element anyElement) {
        XsdAny any = new XsdAny();

        // Parse namespace attribute
        if (anyElement.hasAttribute("namespace")) {
            any.setNamespace(anyElement.getAttribute("namespace"));
        }

        // Parse processContents attribute
        if (anyElement.hasAttribute("processContents")) {
            any.setProcessContents(XsdAny.ProcessContents.fromString(anyElement.getAttribute("processContents")));
        }

        // Parse occurrence attributes
        parseOccurrenceAttributes(anyElement, any);

        return any;
    }

    /**
     * Parses xs:anyAttribute wildcard.
     */
    private XsdAnyAttribute parseAnyAttribute(Element anyAttrElement) {
        XsdAnyAttribute anyAttr = new XsdAnyAttribute();

        // Parse namespace attribute
        if (anyAttrElement.hasAttribute("namespace")) {
            anyAttr.setNamespace(anyAttrElement.getAttribute("namespace"));
        }

        // Parse processContents attribute
        if (anyAttrElement.hasAttribute("processContents")) {
            anyAttr.setProcessContents(XsdAny.ProcessContents.fromString(anyAttrElement.getAttribute("processContents")));
        }

        return anyAttr;
    }

    /**
     * Serializes the inner XML content of an element (all child nodes) to a string.
     * Used for preserving complex XML content inside xs:appinfo elements.
     *
     * @param element the element whose inner content should be serialized
     * @return the serialized inner XML, or empty string if serialization fails
     */
    private String serializeInnerXml(Element element) {
        try {
            StringBuilder result = new StringBuilder();
            NodeList children = element.getChildNodes();

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    StringWriter writer = new StringWriter();
                    transformer.transform(new DOMSource(child), new StreamResult(writer));
                    result.append(writer.toString());
                } else if (child.getNodeType() == Node.TEXT_NODE) {
                    String text = child.getTextContent();
                    if (text != null && !text.trim().isEmpty()) {
                        result.append(text);
                    }
                }
            }

            return result.toString().trim();
        } catch (Exception e) {
            logger.warn("Failed to serialize inner XML: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Checks if an element is an XSD element with given local name.
     */
    private boolean isXsdElement(Element element, String localName) {
        return XSD_NAMESPACE.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    /**
     * Checks if an attribute name is a standard xs:schema attribute that is handled separately.
     * Additional attributes (like vc:minVersion) should be stored in additionalAttributes.
     */
    private boolean isStandardSchemaAttribute(String attrName) {
        return switch (attrName) {
            case "targetNamespace",
                 "elementFormDefault",
                 "attributeFormDefault",
                 "version",
                 "id",
                 "xmlns" -> true;
            default -> attrName.startsWith("xmlns:");
        };
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
            xsdImport.markResolutionFailed("No schemaLocation specified");
            return;
        }

        // Check if we've already processed this import
        if (processedImports.contains(schemaLocation)) {
            logger.debug("Import already processed, skipping: {}", schemaLocation);
            xsdImport.setResolutionError("Already processed (duplicate)");
            return;
        }

        processedImports.add(schemaLocation);
        logger.info("Loading imported schema: namespace='{}', location='{}'", namespace, schemaLocation);

        try {
            // Resolve the schema location to an absolute path
            Path resolvedPath = null;
            if (!schemaLocation.startsWith("http://") && !schemaLocation.startsWith("https://")) {
                if (currentSchemaFile != null) {
                    resolvedPath = currentSchemaFile.getParent().resolve(schemaLocation).normalize();
                } else {
                    resolvedPath = Path.of(schemaLocation).toAbsolutePath().normalize();
                }
            }

            // Load the schema content
            String schemaContent = loadSchemaFromLocation(schemaLocation);
            if (schemaContent == null || schemaContent.isEmpty()) {
                logger.warn("Failed to load schema content from: {}", schemaLocation);
                xsdImport.markResolutionFailed("Failed to load schema content");
                return;
            }

            // Parse the imported schema
            XsdNodeFactory importFactory = new XsdNodeFactory();
            XsdSchema importedSchema = importFactory.fromString(schemaContent);
            String importKey = namespace != null ? namespace : schemaLocation;
            importedSchemas.put(importKey, importedSchema);

            // Also register the imported schema on the main schema for analysis features
            mainSchema.addImportedSchema(importKey, importedSchema);

            // Update XsdImport with resolution info
            xsdImport.setImportedSchema(importedSchema);
            xsdImport.setResolvedPath(resolvedPath);

            logger.info("Successfully loaded imported schema with {} children", importedSchema.getChildren().size());

            // Tag imported nodes with source info (for schema analysis features)
            mergeSchemaComponents(importedSchema, mainSchema, xsdImport, resolvedPath);
        } catch (Exception e) {
            xsdImport.markResolutionFailed(e.getMessage());
            throw e;
        }
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
     * Tags imported schema components with source info for tracking.
     * IMPORTANT: Components are NOT added as direct children of the main schema.
     * Instead, they remain in the importedSchemas map and are accessed via:
     * - globalElementIndex in XsdVisualTreeBuilder (for visual tree and ref resolution)
     * - importedSchemas map (for schema analysis features)
     *
     * This preserves the original structure where refs like "ds:Signature" are resolved
     * through the index, not by looking at schema children.
     *
     * @param importedSchema the imported schema
     * @param mainSchema the main schema (unused, kept for signature compatibility)
     * @param xsdImport the XsdImport node that triggered this import
     * @param resolvedPath the resolved path of the imported schema file
     */
    private void mergeSchemaComponents(XsdSchema importedSchema, XsdSchema mainSchema,
                                        XsdImport xsdImport, Path resolvedPath) {
        int taggedElements = 0;
        int taggedTypes = 0;
        int taggedGroups = 0;
        String namespace = xsdImport.getNamespace();
        String schemaLocation = xsdImport.getSchemaLocation();

        // Create source info for imported nodes
        IncludeSourceInfo sourceInfo = IncludeSourceInfo.forImportedSchema(
                resolvedPath, schemaLocation, xsdImport);

        // Tag nodes in the imported schema with source info (but don't copy to main schema!)
        // This allows Schema Analysis to know where nodes come from, while keeping
        // the visual tree builder's ref resolution working correctly.
        for (XsdNode child : importedSchema.getChildren()) {
            // Skip import/include/redefine/override nodes
            if (child instanceof XsdImport || child instanceof XsdInclude ||
                child instanceof XsdRedefine || child instanceof XsdOverride) {
                continue;
            }

            // Tag the original node and its descendants with source info
            tagNodeWithSourceInfoRecursive(child, sourceInfo);

            // Track statistics
            if (child instanceof XsdElement) {
                taggedElements++;
                logger.debug("Tagged global element from import: {}", child.getName());
            } else if (child instanceof XsdComplexType) {
                taggedTypes++;
                logger.debug("Tagged complex type from import: {}", child.getName());
            } else if (child instanceof XsdSimpleType) {
                taggedTypes++;
                logger.debug("Tagged simple type from import: {}", child.getName());
            } else if (child instanceof XsdGroup || child instanceof XsdAttributeGroup) {
                taggedGroups++;
                logger.debug("Tagged group from import: {}", child.getName());
            }
        }

        logger.info("Tagged {} elements, {} types, {} groups in imported schema (namespace='{}', location='{}')",
                taggedElements, taggedTypes, taggedGroups, namespace, schemaLocation);
    }

    /**
     * Recursively tags a node and all its descendants with source info.
     *
     * @param node the node to tag
     * @param sourceInfo the source info to apply
     */
    private void tagNodeWithSourceInfoRecursive(XsdNode node, IncludeSourceInfo sourceInfo) {
        if (node == null) {
            return;
        }
        node.setSourceInfo(sourceInfo);
        for (XsdNode child : node.getChildren()) {
            tagNodeWithSourceInfoRecursive(child, sourceInfo);
        }
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
