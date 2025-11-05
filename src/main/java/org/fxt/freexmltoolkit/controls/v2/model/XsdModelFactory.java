package org.fxt.freexmltoolkit.controls.v2.model;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Factory for creating XSD model instances from XSD files or strings.
 * This class parses XSD schemas using DOM and creates the internal model representation.
 *
 * @since 2.0
 */
public class XsdModelFactory {

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
    private int elementCounter = 0;
    private int groupCounter = 0;
    private int attributeGroupCounter = 0;

    /**
     * Creates an XSD model from a file.
     *
     * @param xsdFile the XSD file to parse
     * @return the parsed XSD model
     * @throws Exception if parsing fails
     */
    public XsdSchemaModel fromFile(File xsdFile) throws Exception {
        String content = Files.readString(xsdFile.toPath());
        return fromString(content);
    }

    /**
     * Creates an XSD model from a string.
     *
     * @param xsdContent the XSD content as string
     * @return the parsed XSD model
     * @throws Exception if parsing fails
     */
    public XsdSchemaModel fromString(String xsdContent) throws Exception {
        elementCounter = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xsdContent)));

        Element schemaElement = document.getDocumentElement();
        if (!isXsdElement(schemaElement, "schema")) {
            throw new IllegalArgumentException("Root element must be xs:schema");
        }

        return parseSchema(schemaElement);
    }

    /**
     * Parses the xs:schema root element.
     */
    private XsdSchemaModel parseSchema(Element schemaElement) {
        String schemaId = generateId("schema");
        XsdSchemaModel schema = new XsdSchemaModel(schemaId);

        // Parse schema attributes
        if (schemaElement.hasAttribute("version")) {
            schema.setVersion(schemaElement.getAttribute("version"));
        }

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

        // Parse child elements
        NodeList children = schemaElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElementModel element = parseElement(childElement);
                schema.addGlobalElement(element);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexTypeModel complexType = parseComplexType(childElement);
                schema.addGlobalComplexType(complexType.getName(), complexType);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleTypeModel simpleType = parseSimpleType(childElement);
                schema.addGlobalSimpleType(simpleType.getName(), simpleType);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroupModel group = parseGroup(childElement);
                schema.addGlobalGroup(group.getName(), group);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroupModel attributeGroup = parseAttributeGroup(childElement);
                schema.addGlobalAttributeGroup(attributeGroup.getName(), attributeGroup);
            } else if (isXsdElement(childElement, "import")) {
                parseImport(childElement, schema);
            } else if (isXsdElement(childElement, "include")) {
                parseInclude(childElement, schema);
            } else if (isXsdElement(childElement, "override")) {
                // XSD 1.1 override support
                parseOverride(childElement, schema);
            }
        }

        return schema;
    }

    /**
     * Parses an xs:element.
     */
    private XsdElementModel parseElement(Element elementNode) {
        String elementId = generateId("element");
        String name = elementNode.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "element_" + elementCounter++;
        }

        XsdElementModel element = new XsdElementModel(elementId, name);

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
                element.setMaxOccurs(Integer.MAX_VALUE);
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

        if (elementNode.hasAttribute("default")) {
            element.setDefaultValue(elementNode.getAttribute("default"));
        }

        if (elementNode.hasAttribute("fixed")) {
            element.setFixedValue(elementNode.getAttribute("fixed"));
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
                parseInlineComplexType(childElement, element);
            } else if (isXsdElement(childElement, "simpleType")) {
                parseInlineSimpleType(childElement, element);
            } else if (isXsdElement(childElement, "alternative")) {
                // XSD 1.1 alternative support (Conditional Type Assignment)
                XsdAlternativeModel alternative = parseAlternative(childElement);
                if (alternative != null) {
                    element.addAlternative(alternative);
                }
            }
        }

        return element;
    }

    /**
     * Parses inline complex type within an element.
     */
    private void parseInlineComplexType(Element complexTypeElement, XsdElementModel parentElement) {
        NodeList children = complexTypeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "sequence")) {
                XsdCompositorModel compositor = parseSequenceCompositor(childElement);
                parentElement.addCompositor(compositor);
            } else if (isXsdElement(childElement, "choice")) {
                XsdCompositorModel compositor = parseChoiceCompositor(childElement);
                parentElement.addCompositor(compositor);
            } else if (isXsdElement(childElement, "all")) {
                XsdCompositorModel compositor = parseAllCompositor(childElement);
                parentElement.addCompositor(compositor);
            } else if (isXsdElement(childElement, "attribute")) {
                XsdAttributeModel attribute = parseAttribute(childElement);
                parentElement.addAttribute(attribute);
            }
        }
    }

    /**
     * Parses inline simple type within an element.
     */
    private void parseInlineSimpleType(Element simpleTypeElement, XsdElementModel parentElement) {
        String typeId = generateId("simpleType");
        String name = "anonymousSimpleType_" + elementCounter++;

        XsdSimpleTypeModel simpleType = new XsdSimpleTypeModel(typeId, name);

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
                parseRestriction(childElement, simpleType);
            } else if (isXsdElement(childElement, "list")) {
                parseList(childElement, simpleType);
            } else if (isXsdElement(childElement, "union")) {
                parseUnion(childElement, simpleType);
            }
        }

        parentElement.setInlineSimpleType(simpleType);
    }

    /**
     * Parses xs:sequence compositor.
     */
    private XsdCompositorModel parseSequenceCompositor(Element sequenceElement) {
        String compositorId = generateId("sequence");
        XsdCompositorModel compositor = new XsdCompositorModel.XsdSequenceModel(compositorId);

        // Parse min/maxOccurs attributes
        if (sequenceElement.hasAttribute("minOccurs")) {
            try {
                compositor.setMinOccurs(Integer.parseInt(sequenceElement.getAttribute("minOccurs")));
            } catch (NumberFormatException ignored) {
            }
        }
        if (sequenceElement.hasAttribute("maxOccurs")) {
            String maxOccurs = sequenceElement.getAttribute("maxOccurs");
            if ("unbounded".equals(maxOccurs)) {
                compositor.setMaxOccurs(Integer.MAX_VALUE);
            } else {
                try {
                    compositor.setMaxOccurs(Integer.parseInt(maxOccurs));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Parse child elements
        NodeList children = sequenceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElementModel element = parseElement(childElement);
                compositor.addElement(element);
            } else if (isXsdElement(childElement, "choice")) {
                XsdCompositorModel nestedCompositor = parseChoiceCompositor(childElement);
                compositor.addCompositor(nestedCompositor);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdCompositorModel nestedCompositor = parseSequenceCompositor(childElement);
                compositor.addCompositor(nestedCompositor);
            }
        }

        return compositor;
    }

    /**
     * Parses xs:choice compositor.
     */
    private XsdCompositorModel parseChoiceCompositor(Element choiceElement) {
        String compositorId = generateId("choice");
        XsdCompositorModel compositor = new XsdCompositorModel.XsdChoiceModel(compositorId);

        // Parse min/maxOccurs attributes
        if (choiceElement.hasAttribute("minOccurs")) {
            try {
                compositor.setMinOccurs(Integer.parseInt(choiceElement.getAttribute("minOccurs")));
            } catch (NumberFormatException ignored) {
            }
        }
        if (choiceElement.hasAttribute("maxOccurs")) {
            String maxOccurs = choiceElement.getAttribute("maxOccurs");
            if ("unbounded".equals(maxOccurs)) {
                compositor.setMaxOccurs(Integer.MAX_VALUE);
            } else {
                try {
                    compositor.setMaxOccurs(Integer.parseInt(maxOccurs));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Parse child elements
        NodeList children = choiceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElementModel element = parseElement(childElement);
                compositor.addElement(element);
            } else if (isXsdElement(childElement, "choice")) {
                XsdCompositorModel nestedCompositor = parseChoiceCompositor(childElement);
                compositor.addCompositor(nestedCompositor);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdCompositorModel nestedCompositor = parseSequenceCompositor(childElement);
                compositor.addCompositor(nestedCompositor);
            }
        }

        return compositor;
    }

    /**
     * Parses xs:all compositor.
     * <p>
     * XSD 1.1 enhancement: xs:all can now have minOccurs and maxOccurs attributes.
     * In XSD 1.0, these were restricted to 0 or 1. In XSD 1.1, they can have any value.
     * </p>
     */
    private XsdCompositorModel parseAllCompositor(Element allElement) {
        String compositorId = generateId("all");
        XsdCompositorModel compositor = new XsdCompositorModel.XsdAllModel(compositorId);

        // Parse min/maxOccurs attributes (XSD 1.1 enhancement)
        if (allElement.hasAttribute("minOccurs")) {
            try {
                compositor.setMinOccurs(Integer.parseInt(allElement.getAttribute("minOccurs")));
            } catch (NumberFormatException e) {
                // Keep default value of 1
            }
        }

        if (allElement.hasAttribute("maxOccurs")) {
            String maxOccursValue = allElement.getAttribute("maxOccurs");
            if ("unbounded".equals(maxOccursValue)) {
                compositor.setMaxOccurs(Integer.MAX_VALUE);
            } else {
                try {
                    compositor.setMaxOccurs(Integer.parseInt(maxOccursValue));
                } catch (NumberFormatException e) {
                    // Keep default value of 1
                }
            }
        }

        // Parse child elements
        NodeList children = allElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "element")) {
                XsdElementModel element = parseElement(childElement);
                compositor.addElement(element);
            }
            // xs:all cannot contain other compositors
        }

        return compositor;
    }

    /**
     * Parses xs:attribute.
     */
    private XsdAttributeModel parseAttribute(Element attributeElement) {
        String attributeId = generateId("attribute");
        String name = attributeElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "attribute_" + elementCounter++;
        }

        XsdAttributeModel attribute = new XsdAttributeModel(attributeId, name);

        if (attributeElement.hasAttribute("type")) {
            attribute.setType(attributeElement.getAttribute("type"));
        }

        if (attributeElement.hasAttribute("use")) {
            String use = attributeElement.getAttribute("use");
            attribute.setRequired("required".equals(use));
        }

        if (attributeElement.hasAttribute("default")) {
            attribute.setDefaultValue(attributeElement.getAttribute("default"));
        }

        if (attributeElement.hasAttribute("fixed")) {
            attribute.setFixedValue(attributeElement.getAttribute("fixed"));
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
    private XsdComplexTypeModel parseComplexType(Element complexTypeElement) {
        String typeId = generateId("complexType");
        String name = complexTypeElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "complexType_" + elementCounter++;
        }

        XsdComplexTypeModel complexType = new XsdComplexTypeModel(typeId, name);

        if (complexTypeElement.hasAttribute("mixed")) {
            complexType.setMixedContent(Boolean.parseBoolean(complexTypeElement.getAttribute("mixed")));
        }

        if (complexTypeElement.hasAttribute("abstract")) {
            complexType.setAbstractType(Boolean.parseBoolean(complexTypeElement.getAttribute("abstract")));
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
            } else if (isXsdElement(childElement, "openContent")) {
                // XSD 1.1 open content support
                XsdOpenContentModel openContent = parseOpenContent(childElement);
                complexType.setOpenContent(openContent);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdCompositorModel compositor = parseSequenceCompositor(childElement);
                complexType.addCompositor(compositor);
            } else if (isXsdElement(childElement, "choice")) {
                XsdCompositorModel compositor = parseChoiceCompositor(childElement);
                complexType.addCompositor(compositor);
            } else if (isXsdElement(childElement, "all")) {
                XsdCompositorModel compositor = parseAllCompositor(childElement);
                complexType.addCompositor(compositor);
            } else if (isXsdElement(childElement, "attribute")) {
                XsdAttributeModel attribute = parseAttribute(childElement);
                complexType.addAttribute(attribute);
            } else if (isXsdElement(childElement, "assert")) {
                // XSD 1.1 assertion support
                XsdAssertModel assertion = parseAssertion(childElement);
                if (assertion != null) {
                    complexType.addAssertion(assertion);
                }
            }
        }

        return complexType;
    }


    /**
     * Parses xs:simpleType.
     */
    private XsdSimpleTypeModel parseSimpleType(Element simpleTypeElement) {
        String typeId = generateId("simpleType");
        String name = simpleTypeElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "simpleType_" + elementCounter++;
        }

        XsdSimpleTypeModel simpleType = new XsdSimpleTypeModel(typeId, name);

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
                parseRestriction(childElement, simpleType);
            } else if (isXsdElement(childElement, "list")) {
                parseList(childElement, simpleType);
            } else if (isXsdElement(childElement, "union")) {
                parseUnion(childElement, simpleType);
            }
        }

        return simpleType;
    }

    /**
     * Parses xs:restriction.
     */
    private void parseRestriction(Element restrictionElement, XsdSimpleTypeModel simpleType) {
        if (restrictionElement.hasAttribute("base")) {
            simpleType.setBaseType(restrictionElement.getAttribute("base"));
        }

        NodeList children = restrictionElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            String localName = childElement.getLocalName();

            if ("enumeration".equals(localName)) {
                if (childElement.hasAttribute("value")) {
                    simpleType.addEnumeration(childElement.getAttribute("value"));
                }
            } else if (isXsdElement(childElement, "assertion")) {
                // XSD 1.1 assertion support for simple types
                XsdAssertModel assertion = parseAssertion(childElement);
                if (assertion != null) {
                    simpleType.addAssertion(assertion);
                }
            } else if (childElement.hasAttribute("value")) {
                // Other facets: minLength, maxLength, pattern, etc.
                simpleType.addFacet(localName, childElement.getAttribute("value"));
            }
        }
    }

    /**
     * Parses xs:annotation and extracts documentation and XsdDocInfo.
     */
    private void parseAnnotation(Element annotationElement, Object target) {
        NodeList children = annotationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "documentation")) {
                String documentation = childElement.getTextContent();
                if (target instanceof XsdElementModel) {
                    ((XsdElementModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdAttributeModel) {
                    ((XsdAttributeModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdComplexTypeModel) {
                    ((XsdComplexTypeModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdSimpleTypeModel) {
                    ((XsdSimpleTypeModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdGroupModel) {
                    ((XsdGroupModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdAttributeGroupModel) {
                    ((XsdAttributeGroupModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdAssertModel) {
                    ((XsdAssertModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdAlternativeModel) {
                    ((XsdAlternativeModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdOpenContentModel) {
                    ((XsdOpenContentModel) target).setDocumentation(documentation);
                } else if (target instanceof XsdOverrideModel) {
                    ((XsdOverrideModel) target).setDocumentation(documentation);
                }
            } else if (isXsdElement(childElement, "appinfo")) {
                parseAppInfo(childElement, target);
            }
        }
    }

    /**
     * Parses xs:appinfo and extracts XsdDocInfo annotations.
     */
    private void parseAppInfo(Element appInfoElement, Object target) {
        XsdDocInfo docInfo = getDocInfo(target);
        if (docInfo == null) {
            return;
        }

        String source = appInfoElement.getAttribute("source");
        if (source != null && !source.isEmpty()) {
            if (source.startsWith("@since ")) {
                docInfo.setSinceVersion(source.substring(7).trim());
            } else if (source.startsWith("@see ")) {
                docInfo.addSeeReference(source.substring(5).trim());
            } else if (source.startsWith("@deprecated ")) {
                docInfo.setDeprecated(source.substring(12).trim());
            }
        }
    }

    /**
     * Gets XsdDocInfo from target object.
     */
    private XsdDocInfo getDocInfo(Object target) {
        if (target instanceof XsdElementModel) {
            return ((XsdElementModel) target).getDocInfo();
        } else if (target instanceof XsdAttributeModel) {
            return ((XsdAttributeModel) target).getDocInfo();
        } else if (target instanceof XsdComplexTypeModel) {
            return ((XsdComplexTypeModel) target).getDocInfo();
        } else if (target instanceof XsdSimpleTypeModel) {
            return ((XsdSimpleTypeModel) target).getDocInfo();
        } else if (target instanceof XsdAssertModel) {
            return ((XsdAssertModel) target).getDocInfo();
        }
        return null;
    }

    /**
     * Parses XSD 1.1 xs:assert or xs:assertion element.
     * Both elements use the same structure with a required "test" attribute.
     *
     * @param assertElement the xs:assert or xs:assertion element
     * @return the parsed assertion model
     */
    private XsdAssertModel parseAssertion(Element assertElement) {
        String assertId = generateId("assert");
        String test = assertElement.getAttribute("test");
        if (test == null || test.isEmpty()) {
            // test attribute is required
            return null;
        }

        XsdAssertModel assertion = new XsdAssertModel(assertId, test);

        // Parse optional xpathDefaultNamespace attribute
        String xpathDefaultNs = assertElement.getAttribute("xpathDefaultNamespace");
        if (xpathDefaultNs != null && !xpathDefaultNs.isEmpty()) {
            assertion.setXpathDefaultNamespace(xpathDefaultNs);
        }

        // Parse optional annotation child element
        NodeList children = assertElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, assertion);
            }
        }

        return assertion;
    }

    /**
     * Parses XSD 1.1 xs:alternative element.
     * Alternatives provide conditional type assignment based on XPath expressions.
     *
     * @param alternativeElement the xs:alternative element
     * @return the parsed alternative model
     */
    private XsdAlternativeModel parseAlternative(Element alternativeElement) {
        String alternativeId = generateId("alternative");
        XsdAlternativeModel alternative = new XsdAlternativeModel(alternativeId);

        // Parse optional test attribute (if missing, this is the default alternative)
        String test = alternativeElement.getAttribute("test");
        if (test != null && !test.isEmpty()) {
            alternative.setTest(test);
        }

        // Parse optional type attribute
        String type = alternativeElement.getAttribute("type");
        if (type != null && !type.isEmpty()) {
            alternative.setType(type);
        }

        // Parse optional xpathDefaultNamespace attribute
        String xpathDefaultNs = alternativeElement.getAttribute("xpathDefaultNamespace");
        if (xpathDefaultNs != null && !xpathDefaultNs.isEmpty()) {
            alternative.setXpathDefaultNamespace(xpathDefaultNs);
        }

        // Parse child elements (annotation, inline simpleType or complexType)
        NodeList children = alternativeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, alternative);
            } else if (isXsdElement(childElement, "simpleType")) {
                // Inline simple type definition
                XsdSimpleTypeModel simpleType = parseSimpleType(childElement);
                alternative.setInlineSimpleType(simpleType);
            } else if (isXsdElement(childElement, "complexType")) {
                // Inline complex type definition
                XsdComplexTypeModel complexType = parseComplexType(childElement);
                alternative.setInlineComplexType(complexType);
            }
        }

        return alternative;
    }

    /**
     * Parses xs:list element.
     *
     * @param listElement      the xs:list element
     * @param parentSimpleType the parent simple type model to configure
     */
    private void parseList(Element listElement, XsdSimpleTypeModel parentSimpleType) {
        parentSimpleType.setDerivationMethod(XsdSimpleTypeModel.DerivationMethod.LIST);

        // Parse itemType attribute (type reference)
        String itemType = listElement.getAttribute("itemType");
        if (itemType != null && !itemType.isEmpty()) {
            parentSimpleType.setListItemType(itemType);
        }

        // Parse inline simpleType child
        NodeList children = listElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleTypeModel inlineType = parseSimpleType(childElement);
                parentSimpleType.setInlineListItemType(inlineType);
                break; // Only one inline type allowed
            }
        }
    }

    /**
     * Parses xs:union element.
     *
     * @param unionElement     the xs:union element
     * @param parentSimpleType the parent simple type model to configure
     */
    private void parseUnion(Element unionElement, XsdSimpleTypeModel parentSimpleType) {
        parentSimpleType.setDerivationMethod(XsdSimpleTypeModel.DerivationMethod.UNION);

        // Parse memberTypes attribute (space-separated list of type references)
        String memberTypes = unionElement.getAttribute("memberTypes");
        if (memberTypes != null && !memberTypes.isEmpty()) {
            // Split by whitespace and add each type
            String[] types = memberTypes.trim().split("\\s+");
            for (String type : types) {
                if (!type.isEmpty()) {
                    parentSimpleType.addUnionMemberType(type);
                }
            }
        }

        // Parse inline simpleType children
        NodeList children = unionElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleTypeModel inlineType = parseSimpleType(childElement);
                parentSimpleType.addInlineUnionMemberType(inlineType);
            }
        }
    }

    /**
     * Parses xs:openContent element (XSD 1.1).
     *
     * @param openContentElement the xs:openContent element
     * @return the open content model
     */
    private XsdOpenContentModel parseOpenContent(Element openContentElement) {
        String openContentId = generateId("openContent");
        XsdOpenContentModel openContent = new XsdOpenContentModel(openContentId);

        // Parse mode attribute (default: interleave)
        String mode = openContentElement.getAttribute("mode");
        if (mode != null && !mode.isEmpty()) {
            if ("suffix".equals(mode)) {
                openContent.setMode(XsdOpenContentModel.Mode.SUFFIX);
            } else {
                openContent.setMode(XsdOpenContentModel.Mode.INTERLEAVE);
            }
        }

        // Parse child elements (annotation and xs:any wildcard)
        NodeList children = openContentElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, openContent);
            } else if (isXsdElement(childElement, "any")) {
                // Parse xs:any wildcard attributes
                String namespace = childElement.getAttribute("namespace");
                if (namespace != null && !namespace.isEmpty()) {
                    openContent.setWildcardNamespace(namespace);
                }

                String processContents = childElement.getAttribute("processContents");
                if (processContents != null && !processContents.isEmpty()) {
                    switch (processContents) {
                        case "lax":
                            openContent.setProcessContents(XsdOpenContentModel.ProcessContents.LAX);
                            break;
                        case "skip":
                            openContent.setProcessContents(XsdOpenContentModel.ProcessContents.SKIP);
                            break;
                        default:
                            openContent.setProcessContents(XsdOpenContentModel.ProcessContents.STRICT);
                    }
                }

                // XSD 1.1 notNamespace and notQName attributes
                String notNamespace = childElement.getAttribute("notNamespace");
                if (notNamespace != null && !notNamespace.isEmpty()) {
                    openContent.setNotNamespace(notNamespace);
                }

                String notQName = childElement.getAttribute("notQName");
                if (notQName != null && !notQName.isEmpty()) {
                    openContent.setNotQName(notQName);
                }
            }
        }

        return openContent;
    }

    /**
     * Parses xs:group.
     */
    private XsdGroupModel parseGroup(Element groupElement) {
        String groupId = generateId("group");
        String name = groupElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "group_" + groupCounter++;
        }

        XsdGroupModel group = new XsdGroupModel(groupId, name);

        // Parse documentation
        NodeList children = groupElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, group);
            } else if (isXsdElement(childElement, "sequence")) {
                XsdCompositorModel compositor = parseSequenceCompositor(childElement);
                group.setCompositor(compositor);
            } else if (isXsdElement(childElement, "choice")) {
                XsdCompositorModel compositor = parseChoiceCompositor(childElement);
                group.setCompositor(compositor);
            } else if (isXsdElement(childElement, "all")) {
                XsdCompositorModel compositor = parseAllCompositor(childElement);
                group.setCompositor(compositor);
            }
        }

        return group;
    }

    /**
     * Parses xs:attributeGroup.
     */
    private XsdAttributeGroupModel parseAttributeGroup(Element attributeGroupElement) {
        String groupId = generateId("attributeGroup");
        String name = attributeGroupElement.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "attributeGroup_" + attributeGroupCounter++;
        }

        XsdAttributeGroupModel attributeGroup = new XsdAttributeGroupModel(groupId, name);

        // Parse documentation and attributes
        NodeList children = attributeGroupElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, attributeGroup);
            } else if (isXsdElement(childElement, "attribute")) {
                XsdAttributeModel attribute = parseAttribute(childElement);
                attributeGroup.addAttribute(attribute);
            }
        }

        return attributeGroup;
    }

    /**
     * Parses xs:import.
     */
    private void parseImport(Element importElement, XsdSchemaModel schema) {
        String namespace = importElement.getAttribute("namespace");
        if (namespace != null && !namespace.isEmpty()) {
            schema.addImport(namespace);
        }
    }

    /**
     * Parses xs:include.
     */
    private void parseInclude(Element includeElement, XsdSchemaModel schema) {
        String schemaLocation = includeElement.getAttribute("schemaLocation");
        if (schemaLocation != null && !schemaLocation.isEmpty()) {
            schema.addInclude(schemaLocation);
        }
    }

    /**
     * Parses xs:override (XSD 1.1).
     * <p>
     * xs:override allows including another schema while overriding specific components.
     * It can contain complexType, simpleType, group, and attributeGroup definitions
     * that override components from the included schema.
     * </p>
     */
    private void parseOverride(Element overrideElement, XsdSchemaModel schema) {
        String schemaLocation = overrideElement.getAttribute("schemaLocation");
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            return; // schemaLocation is required
        }

        String overrideId = generateId("override");
        XsdOverrideModel override = new XsdOverrideModel(overrideId, schemaLocation);

        // Parse child elements (annotation, complexType, simpleType, group, attributeGroup)
        NodeList children = overrideElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            Element childElement = (Element) child;

            if (isXsdElement(childElement, "annotation")) {
                parseAnnotation(childElement, override);
            } else if (isXsdElement(childElement, "complexType")) {
                XsdComplexTypeModel complexType = parseComplexType(childElement);
                override.addOverriddenComplexType(complexType.getName(), complexType);
            } else if (isXsdElement(childElement, "simpleType")) {
                XsdSimpleTypeModel simpleType = parseSimpleType(childElement);
                override.addOverriddenSimpleType(simpleType.getName(), simpleType);
            } else if (isXsdElement(childElement, "group")) {
                XsdGroupModel group = parseGroup(childElement);
                override.addOverriddenGroup(group.getName(), group);
            } else if (isXsdElement(childElement, "attributeGroup")) {
                XsdAttributeGroupModel attributeGroup = parseAttributeGroup(childElement);
                override.addOverriddenAttributeGroup(attributeGroup.getName(), attributeGroup);
            }
        }

        schema.addOverride(override);
    }

    /**
     * Checks if an element is an XSD element with given local name.
     */
    private boolean isXsdElement(Element element, String localName) {
        return XSD_NAMESPACE.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    /**
     * Generates a unique ID for a model object.
     */
    private String generateId(String type) {
        return type + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
