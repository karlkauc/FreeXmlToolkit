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
            }
            // TODO: Add support for xs:group, xs:attributeGroup, xs:import, xs:include
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
                // TODO: Handle inline simple type
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
     */
    private XsdCompositorModel parseAllCompositor(Element allElement) {
        String compositorId = generateId("all");
        XsdCompositorModel compositor = new XsdCompositorModel.XsdAllModel(compositorId);

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
            }
            // TODO: Add support for xs:list, xs:union
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
        }
        return null;
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
