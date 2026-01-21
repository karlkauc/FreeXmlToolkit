package org.fxt.freexmltoolkit.service.xsd.adapters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParseException;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Adapter for converting ParsedSchema to XsdSchema (V2 model).
 *
 * <p>This adapter provides a bridge between the unified parsing infrastructure
 * and the existing XSD visual editor model.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Full XSD 1.0/1.1 element support</li>
 *   <li>Source file tracking for multi-file schemas</li>
 *   <li>Include/import preservation based on options</li>
 *   <li>Namespace handling</li>
 * </ul>
 */
public class XsdModelAdapter {

    private static final Logger logger = LogManager.getLogger(XsdModelAdapter.class);

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdParseOptions options;
    private IncludeTracker includeTracker;

    /**
     * Creates a new adapter with default options.
     */
    public XsdModelAdapter() {
        this(XsdParseOptions.defaults());
    }

    /**
     * Creates a new adapter with the specified options.
     *
     * @param options parsing options
     */
    public XsdModelAdapter(XsdParseOptions options) {
        this.options = options != null ? options : XsdParseOptions.defaults();
    }

    /**
     * Converts a ParsedSchema to XsdSchema model.
     *
     * @param parsedSchema the parsed schema
     * @return the XsdSchema model
     * @throws XsdParseException if conversion fails
     */
    public XsdSchema toXsdModel(ParsedSchema parsedSchema) throws XsdParseException {
        if (parsedSchema == null) {
            throw new XsdParseException("ParsedSchema cannot be null");
        }

        try {
            XsdSchema schema = new XsdSchema();
            Element schemaElement = parsedSchema.getSchemaElement();

            // Set basic schema properties
            String targetNamespace = schemaElement.getAttribute("targetNamespace");
            if (targetNamespace != null && !targetNamespace.isEmpty()) {
                schema.setTargetNamespace(targetNamespace);
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

            // Set namespace declarations
            for (Map.Entry<String, String> entry : parsedSchema.getNamespaceDeclarations().entrySet()) {
                schema.addNamespace(entry.getKey(), entry.getValue());
            }

            // Set source file if available
            parsedSchema.getSourceFile().ifPresent(schema::setMainSchemaPath);

            // Initialize include tracker if preserving structure
            if (options.getIncludeMode() == XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE) {
                Path mainPath = parsedSchema.getSourceFile().orElse(null);
                if (mainPath != null) {
                    includeTracker = new IncludeTracker(mainPath);
                }
            }

            // Parse schema children
            parseSchemaChildren(schemaElement, schema);

            // Handle includes based on mode
            handleIncludes(parsedSchema, schema);

            // Handle imports
            handleImports(parsedSchema, schema);

            logger.info("Converted ParsedSchema to XsdSchema with {} children", schema.getChildren().size());

            return schema;

        } catch (Exception e) {
            throw new XsdParseException("Failed to convert to XsdSchema: " + e.getMessage(), e);
        }
    }

    /**
     * Parses children of the schema element.
     */
    private void parseSchemaChildren(Element schemaElement, XsdSchema schema) {
        NodeList children = schemaElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);

            if (!(node instanceof Element element)) {
                continue;
            }

            // Skip include/import/redefine - handled separately
            String localName = element.getLocalName();
            if ("include".equals(localName) || "import".equals(localName) ||
                    "redefine".equals(localName) || "override".equals(localName)) {
                continue;
            }

            XsdNode xsdNode = parseElement(element);
            if (xsdNode != null) {
                schema.addChild(xsdNode);

                // Tag with source info if tracking
                if (includeTracker != null) {
                    includeTracker.tagNodeRecursively(xsdNode);
                }
            }
        }
    }

    /**
     * Parses an XSD element and returns the appropriate XsdNode.
     */
    private XsdNode parseElement(Element element) {
        if (!XSD_NS.equals(element.getNamespaceURI())) {
            return null;
        }

        String localName = element.getLocalName();

        return switch (localName) {
            case "element" -> parseXsdElement(element);
            case "complexType" -> parseComplexType(element);
            case "simpleType" -> parseSimpleType(element);
            case "group" -> parseGroup(element);
            case "attributeGroup" -> parseAttributeGroup(element);
            case "attribute" -> parseAttribute(element);
            case "annotation" -> null; // Handled separately
            default -> {
                logger.debug("Unhandled XSD element type: {}", localName);
                yield null;
            }
        };
    }

    /**
     * Parses an xs:element.
     */
    private XsdElement parseXsdElement(Element element) {
        String name = element.getAttribute("name");
        String ref = element.getAttribute("ref");

        if (ref != null && !ref.isEmpty()) {
            name = ref;
        } else if (name == null || name.isEmpty()) {
            name = "element";
        }

        XsdElement xsdElement = new XsdElement(name);

        if (ref != null && !ref.isEmpty()) {
            xsdElement.setRef(ref);
        }

        if (element.hasAttribute("type")) {
            xsdElement.setType(element.getAttribute("type"));
        }

        parseOccurs(element, xsdElement);

        if (element.hasAttribute("nillable")) {
            xsdElement.setNillable(Boolean.parseBoolean(element.getAttribute("nillable")));
        }

        if (element.hasAttribute("abstract")) {
            xsdElement.setAbstract(Boolean.parseBoolean(element.getAttribute("abstract")));
        }

        if (element.hasAttribute("default")) {
            xsdElement.setDefaultValue(element.getAttribute("default"));
        }

        if (element.hasAttribute("fixed")) {
            xsdElement.setFixed(element.getAttribute("fixed"));
        }

        if (element.hasAttribute("substitutionGroup")) {
            xsdElement.setSubstitutionGroup(element.getAttribute("substitutionGroup"));
        }

        // Parse inline type
        parseInlineContent(element, xsdElement);

        // Parse annotation
        parseAnnotation(element, xsdElement);

        return xsdElement;
    }

    /**
     * Parses an xs:complexType.
     * Anonymous (inline) complexTypes have no name attribute.
     */
    private XsdComplexType parseComplexType(Element element) {
        String name = element.getAttribute("name");
        // Anonymous/inline complexTypes should have null name (no name attribute)
        if (name != null && name.isEmpty()) {
            name = null;
        }

        XsdComplexType complexType = new XsdComplexType(name);

        if (element.hasAttribute("abstract")) {
            complexType.setAbstract(Boolean.parseBoolean(element.getAttribute("abstract")));
        }

        if (element.hasAttribute("mixed")) {
            complexType.setMixed(Boolean.parseBoolean(element.getAttribute("mixed")));
        }

        // Parse content (sequence, choice, all, complexContent, simpleContent)
        parseComplexTypeContent(element, complexType);

        // Parse annotation
        parseAnnotation(element, complexType);

        return complexType;
    }

    /**
     * Parses an xs:simpleType.
     * Anonymous (inline) simpleTypes have no name attribute.
     */
    private XsdSimpleType parseSimpleType(Element element) {
        String name = element.getAttribute("name");
        // Anonymous/inline simpleTypes should have null name (no name attribute)
        if (name != null && name.isEmpty()) {
            name = null;
        }

        XsdSimpleType simpleType = new XsdSimpleType(name);

        // Parse restriction, list, or union
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();
                if ("restriction".equals(localName)) {
                    XsdRestriction restriction = parseRestriction(child);
                    simpleType.addChild(restriction);
                } else if ("list".equals(localName)) {
                    XsdList list = parseList(child);
                    simpleType.addChild(list);
                } else if ("union".equals(localName)) {
                    XsdUnion union = parseUnion(child);
                    simpleType.addChild(union);
                }
            }
        }

        parseAnnotation(element, simpleType);

        return simpleType;
    }

    /**
     * Parses an xs:group.
     */
    private XsdGroup parseGroup(Element element) {
        String name = element.getAttribute("name");
        String ref = element.getAttribute("ref");

        if (ref != null && !ref.isEmpty()) {
            XsdGroup group = new XsdGroup(ref);
            group.setRef(ref);
            parseOccurs(element, group);
            return group;
        }

        XsdGroup group = new XsdGroup(name != null && !name.isEmpty() ? name : "group");

        // Parse content (sequence, choice, all)
        parseGroupContent(element, group);
        parseAnnotation(element, group);

        return group;
    }

    /**
     * Parses an xs:attributeGroup.
     */
    private XsdAttributeGroup parseAttributeGroup(Element element) {
        String name = element.getAttribute("name");
        String ref = element.getAttribute("ref");

        if (ref != null && !ref.isEmpty()) {
            XsdAttributeGroup attrGroup = new XsdAttributeGroup(ref);
            attrGroup.setRef(ref);
            return attrGroup;
        }

        XsdAttributeGroup attrGroup = new XsdAttributeGroup(
                name != null && !name.isEmpty() ? name : "attributeGroup");

        // Parse attributes
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                if ("attribute".equals(child.getLocalName())) {
                    XsdAttribute attr = parseAttribute(child);
                    attrGroup.addChild(attr);
                }
            }
        }

        parseAnnotation(element, attrGroup);

        return attrGroup;
    }

    /**
     * Parses an xs:attribute.
     */
    private XsdAttribute parseAttribute(Element element) {
        String name = element.getAttribute("name");
        String ref = element.getAttribute("ref");

        if (ref != null && !ref.isEmpty()) {
            XsdAttribute attr = new XsdAttribute(ref);
            attr.setRef(ref);
            return attr;
        }

        XsdAttribute attr = new XsdAttribute(name != null && !name.isEmpty() ? name : "attribute");

        if (element.hasAttribute("type")) {
            attr.setType(element.getAttribute("type"));
        }

        if (element.hasAttribute("use")) {
            attr.setUse(element.getAttribute("use"));
        }

        if (element.hasAttribute("default")) {
            attr.setDefaultValue(element.getAttribute("default"));
        }

        if (element.hasAttribute("fixed")) {
            attr.setFixed(element.getAttribute("fixed"));
        }

        parseAnnotation(element, attr);

        return attr;
    }

    /**
     * Parses xs:restriction.
     */
    private XsdRestriction parseRestriction(Element element) {
        XsdRestriction restriction = new XsdRestriction();

        if (element.hasAttribute("base")) {
            restriction.setBase(element.getAttribute("base"));
        }

        // Parse facets
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();
                if (isFacetElement(localName)) {
                    XsdFacet facet = parseFacet(child, localName);
                    restriction.addChild(facet);
                }
            }
        }

        return restriction;
    }

    /**
     * Parses xs:list.
     */
    private XsdList parseList(Element element) {
        XsdList list = new XsdList();

        if (element.hasAttribute("itemType")) {
            list.setItemType(element.getAttribute("itemType"));
        }

        return list;
    }

    /**
     * Parses xs:union.
     */
    private XsdUnion parseUnion(Element element) {
        XsdUnion union = new XsdUnion();

        if (element.hasAttribute("memberTypes")) {
            String memberTypes = element.getAttribute("memberTypes");
            if (!memberTypes.isBlank()) {
                union.setMemberTypes(java.util.Arrays.asList(memberTypes.split("\\s+")));
            }
        }

        return union;
    }

    /**
     * Parses a facet element.
     */
    private XsdFacet parseFacet(Element element, String facetName) {
        XsdFacetType type = XsdFacetType.fromXmlName(facetName);
        String value = element.getAttribute("value");

        if (type == null) {
            return null;
        }

        XsdFacet facet = new XsdFacet(type, value);

        if (element.hasAttribute("fixed")) {
            facet.setFixed(Boolean.parseBoolean(element.getAttribute("fixed")));
        }

        return facet;
    }

    /**
     * Checks if an element name is a facet.
     */
    private boolean isFacetElement(String localName) {
        return "minInclusive".equals(localName) || "maxInclusive".equals(localName) ||
                "minExclusive".equals(localName) || "maxExclusive".equals(localName) ||
                "minLength".equals(localName) || "maxLength".equals(localName) ||
                "length".equals(localName) || "pattern".equals(localName) ||
                "enumeration".equals(localName) || "whiteSpace".equals(localName) ||
                "totalDigits".equals(localName) || "fractionDigits".equals(localName) ||
                "assertion".equals(localName) || "explicitTimezone".equals(localName);
    }

    /**
     * Parses min/maxOccurs attributes.
     */
    private void parseOccurs(Element element, XsdNode node) {
        if (element.hasAttribute("minOccurs")) {
            try {
                node.setMinOccurs(Integer.parseInt(element.getAttribute("minOccurs")));
            } catch (NumberFormatException ignored) {
            }
        }

        if (element.hasAttribute("maxOccurs")) {
            String maxOccurs = element.getAttribute("maxOccurs");
            if ("unbounded".equals(maxOccurs)) {
                node.setMaxOccurs(XsdNode.UNBOUNDED);
            } else {
                try {
                    node.setMaxOccurs(Integer.parseInt(maxOccurs));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /**
     * Parses inline content (complexType/simpleType) within an element.
     */
    private void parseInlineContent(Element element, XsdElement xsdElement) {
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();
                if ("complexType".equals(localName)) {
                    XsdComplexType complexType = parseComplexType(child);
                    xsdElement.addChild(complexType);
                } else if ("simpleType".equals(localName)) {
                    XsdSimpleType simpleType = parseSimpleType(child);
                    xsdElement.addChild(simpleType);
                }
            }
        }
    }

    /**
     * Parses complexType content.
     */
    private void parseComplexTypeContent(Element element, XsdComplexType complexType) {
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();

                switch (localName) {
                    case "sequence" -> {
                        XsdSequence sequence = parseSequence(child);
                        complexType.addChild(sequence);
                    }
                    case "choice" -> {
                        XsdChoice choice = parseChoice(child);
                        complexType.addChild(choice);
                    }
                    case "all" -> {
                        XsdAll all = parseAll(child);
                        complexType.addChild(all);
                    }
                    case "complexContent" -> {
                        XsdComplexContent complexContent = parseComplexContent(child);
                        complexType.addChild(complexContent);
                    }
                    case "simpleContent" -> {
                        XsdSimpleContent simpleContent = parseSimpleContent(child);
                        complexType.addChild(simpleContent);
                    }
                    case "attribute" -> {
                        XsdAttribute attr = parseAttribute(child);
                        complexType.addChild(attr);
                    }
                    case "attributeGroup" -> {
                        XsdAttributeGroup attrGroup = parseAttributeGroup(child);
                        complexType.addChild(attrGroup);
                    }
                }
            }
        }
    }

    /**
     * Parses group content.
     */
    private void parseGroupContent(Element element, XsdGroup group) {
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();

                switch (localName) {
                    case "sequence" -> {
                        XsdSequence sequence = parseSequence(child);
                        group.addChild(sequence);
                    }
                    case "choice" -> {
                        XsdChoice choice = parseChoice(child);
                        group.addChild(choice);
                    }
                    case "all" -> {
                        XsdAll all = parseAll(child);
                        group.addChild(all);
                    }
                }
            }
        }
    }

    /**
     * Parses xs:sequence.
     */
    private XsdSequence parseSequence(Element element) {
        XsdSequence sequence = new XsdSequence();
        parseOccurs(element, sequence);
        parseCompositorContent(element, sequence);
        return sequence;
    }

    /**
     * Parses xs:choice.
     */
    private XsdChoice parseChoice(Element element) {
        XsdChoice choice = new XsdChoice();
        parseOccurs(element, choice);
        parseCompositorContent(element, choice);
        return choice;
    }

    /**
     * Parses xs:all.
     */
    private XsdAll parseAll(Element element) {
        XsdAll all = new XsdAll();
        parseOccurs(element, all);
        parseCompositorContent(element, all);
        return all;
    }

    /**
     * Parses compositor (sequence/choice/all) content.
     */
    private void parseCompositorContent(Element element, XsdNode compositor) {
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();

                switch (localName) {
                    case "element" -> {
                        XsdElement xsdElement = parseXsdElement(child);
                        compositor.addChild(xsdElement);
                    }
                    case "sequence" -> {
                        XsdSequence sequence = parseSequence(child);
                        compositor.addChild(sequence);
                    }
                    case "choice" -> {
                        XsdChoice choice = parseChoice(child);
                        compositor.addChild(choice);
                    }
                    case "group" -> {
                        XsdGroup group = parseGroup(child);
                        compositor.addChild(group);
                    }
                    case "any" -> {
                        XsdAny any = parseAny(child);
                        compositor.addChild(any);
                    }
                }
            }
        }
    }

    /**
     * Parses xs:complexContent.
     */
    private XsdComplexContent parseComplexContent(Element element) {
        XsdComplexContent content = new XsdComplexContent();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                if ("extension".equals(child.getLocalName())) {
                    XsdExtension extension = parseExtension(child);
                    content.addChild(extension);
                } else if ("restriction".equals(child.getLocalName())) {
                    XsdRestriction restriction = parseRestriction(child);
                    content.addChild(restriction);
                }
            }
        }

        return content;
    }

    /**
     * Parses xs:simpleContent.
     */
    private XsdSimpleContent parseSimpleContent(Element element) {
        XsdSimpleContent content = new XsdSimpleContent();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                if ("extension".equals(child.getLocalName())) {
                    XsdExtension extension = parseExtension(child);
                    content.addChild(extension);
                } else if ("restriction".equals(child.getLocalName())) {
                    XsdRestriction restriction = parseRestriction(child);
                    content.addChild(restriction);
                }
            }
        }

        return content;
    }

    /**
     * Parses xs:extension.
     */
    private XsdExtension parseExtension(Element element) {
        XsdExtension extension = new XsdExtension();

        if (element.hasAttribute("base")) {
            extension.setBase(element.getAttribute("base"));
        }

        // Parse content
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child && XSD_NS.equals(child.getNamespaceURI())) {
                String localName = child.getLocalName();

                switch (localName) {
                    case "sequence" -> extension.addChild(parseSequence(child));
                    case "choice" -> extension.addChild(parseChoice(child));
                    case "all" -> extension.addChild(parseAll(child));
                    case "attribute" -> extension.addChild(parseAttribute(child));
                    case "attributeGroup" -> extension.addChild(parseAttributeGroup(child));
                }
            }
        }

        return extension;
    }

    /**
     * Parses xs:any.
     */
    private XsdAny parseAny(Element element) {
        XsdAny any = new XsdAny();
        parseOccurs(element, any);

        if (element.hasAttribute("namespace")) {
            any.setNamespace(element.getAttribute("namespace"));
        }

        if (element.hasAttribute("processContents")) {
            String pc = element.getAttribute("processContents");
            XsdAny.ProcessContents processContents = switch (pc) {
                case "strict" -> XsdAny.ProcessContents.STRICT;
                case "lax" -> XsdAny.ProcessContents.LAX;
                case "skip" -> XsdAny.ProcessContents.SKIP;
                default -> XsdAny.ProcessContents.STRICT;
            };
            any.setProcessContents(processContents);
        }

        return any;
    }

    /**
     * Parses annotation and sets documentation with multi-language support.
     * Supports multiple xs:documentation elements with xml:lang attributes.
     */
    private void parseAnnotation(Element element, XsdNode node) {
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element annotationEl &&
                    XSD_NS.equals(annotationEl.getNamespaceURI()) &&
                    "annotation".equals(annotationEl.getLocalName())) {

                NodeList annotationChildren = annotationEl.getChildNodes();
                for (int j = 0; j < annotationChildren.getLength(); j++) {
                    Node annotationChild = annotationChildren.item(j);
                    if (annotationChild instanceof Element docEl &&
                            XSD_NS.equals(docEl.getNamespaceURI()) &&
                            "documentation".equals(docEl.getLocalName())) {
                        String doc = docEl.getTextContent();
                        if (doc != null && !doc.isBlank()) {
                            // Use getAttributeNS for namespace-qualified attributes like xml:lang
                            String lang = docEl.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");
                            String source = docEl.getAttribute("source");

                            // Create XsdDocumentation object with language support
                            XsdDocumentation documentation = new XsdDocumentation(
                                doc.trim(),
                                (lang != null && !lang.isEmpty()) ? lang : null,
                                (source != null && !source.isEmpty()) ? source : null
                            );
                            node.addDocumentation(documentation);
                        }
                    }
                }
                break; // Only process first annotation
            }
        }
    }

    /**
     * Handles include processing based on options.
     *
     * <p>In FLATTEN mode, the DOM document has already been flattened by SchemaResolver,
     * so we do NOT process includes again here - parseSchemaChildren() has already parsed
     * all the flattened content. We only process includes in PRESERVE_STRUCTURE mode
     * where we need to create XsdInclude reference nodes.</p>
     */
    private void handleIncludes(ParsedSchema parsedSchema, XsdSchema schema) {
        // In FLATTEN mode, the DOM has already been flattened by SchemaResolver.flattenIncludes()
        // and parseSchemaChildren() has already processed all the inlined content.
        // We must NOT process includes again here, as that would cause duplicate types.
        if (options.getIncludeMode() == XsdParseOptions.IncludeMode.FLATTEN) {
            logger.debug("Skipping handleIncludes in FLATTEN mode - DOM already flattened");
            return;
        }

        // PRESERVE_STRUCTURE mode: create XsdInclude reference nodes
        for (ParsedSchema.ResolvedInclude include : parsedSchema.getResolvedIncludes()) {
            // Create XsdInclude node
            XsdInclude xsdInclude = new XsdInclude();
            xsdInclude.setSchemaLocation(include.schemaLocation());

            if (include.resolvedPath() != null) {
                xsdInclude.setResolvedPath(include.resolvedPath());
            }

            if (include.isResolved()) {
                // Create reference to included schema and add to schema
                XsdSchema includedRef = new XsdSchema();
                includedRef.setMainSchemaPath(include.resolvedPath());
                xsdInclude.setIncludedSchema(includedRef);
                schema.addChild(xsdInclude);
            } else if (include.error() != null) {
                // Resolution failed - add include with error marker
                xsdInclude.markResolutionFailed(include.error());
                schema.addChild(xsdInclude);
            }
        }
    }

    /**
     * Handles import processing.
     *
     * <p>If resolveImports option was enabled, the imports have been resolved and their
     * content can be inlined. If not, we still need to preserve the xs:import statements
     * by parsing them directly from the DOM.</p>
     */
    private void handleImports(ParsedSchema parsedSchema, XsdSchema schema) {
        List<ParsedSchema.ResolvedImport> resolvedImports = parsedSchema.getResolvedImports();

        if (!resolvedImports.isEmpty()) {
            // Imports were resolved - process them (may include inlined content)
            for (ParsedSchema.ResolvedImport imp : resolvedImports) {
                XsdImport xsdImport = new XsdImport();
                xsdImport.setNamespace(imp.namespace());
                xsdImport.setSchemaLocation(imp.schemaLocation());

                if (imp.resolvedPath() != null) {
                    xsdImport.setResolvedPath(imp.resolvedPath());
                }

                if (imp.isLoaded() && imp.parsedSchema() != null) {
                    // Create reference to imported schema
                    XsdSchema importedSchema = toXsdModelInternal(imp.parsedSchema());
                    xsdImport.setImportedSchema(importedSchema);

                    // Register in schema's imported schemas map
                    String key = imp.namespace() != null ? imp.namespace() : imp.schemaLocation();
                    schema.addImportedSchema(key, importedSchema);
                } else if (imp.error() != null) {
                    xsdImport.markResolutionFailed(imp.error());
                }

                schema.addChild(xsdImport);
            }
        } else {
            // Imports were NOT resolved - parse xs:import elements directly from DOM
            // to preserve them in the output
            parseImportsFromDom(parsedSchema.getSchemaElement(), schema);
        }
    }

    /**
     * Parses xs:import elements directly from the DOM when imports are not being resolved.
     * This preserves the import statements in the flattened output.
     */
    private void parseImportsFromDom(Element schemaElement, XsdSchema schema) {
        NodeList children = schemaElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }

            if (XSD_NS.equals(element.getNamespaceURI()) && "import".equals(element.getLocalName())) {
                XsdImport xsdImport = new XsdImport();

                String namespace = element.getAttribute("namespace");
                if (namespace != null && !namespace.isEmpty()) {
                    xsdImport.setNamespace(namespace);
                }

                String schemaLocation = element.getAttribute("schemaLocation");
                if (schemaLocation != null && !schemaLocation.isEmpty()) {
                    xsdImport.setSchemaLocation(schemaLocation);
                }

                schema.addChild(xsdImport);
                logger.debug("Preserved xs:import from DOM: namespace={}, schemaLocation={}",
                        namespace, schemaLocation);
            }
        }
    }

    /**
     * Internal method for converting imported schemas (no include tracking).
     */
    private XsdSchema toXsdModelInternal(ParsedSchema parsedSchema) {
        XsdSchema schema = new XsdSchema();
        Element schemaElement = parsedSchema.getSchemaElement();

        String targetNamespace = schemaElement.getAttribute("targetNamespace");
        if (targetNamespace != null && !targetNamespace.isEmpty()) {
            schema.setTargetNamespace(targetNamespace);
        }

        parsedSchema.getSourceFile().ifPresent(schema::setMainSchemaPath);

        parseSchemaChildren(schemaElement, schema);

        return schema;
    }
}
