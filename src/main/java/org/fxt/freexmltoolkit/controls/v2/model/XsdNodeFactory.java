package org.fxt.freexmltoolkit.controls.v2.model;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

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
        String content = Files.readString(xsdFile);
        return fromString(content);
    }

    /**
     * Creates an XSD model from a string.
     *
     * @param xsdContent the XSD content as string
     * @return the parsed XSD schema model
     * @throws Exception if parsing fails
     */
    public XsdSchema fromString(String xsdContent) throws Exception {
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
    private XsdSchema parseSchema(Element schemaElement) {
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

        // Parse child elements
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
            }
            // TODO: import, include, redefine, override support
        }

        return schema;
    }

    /**
     * Parses an xs:element.
     */
    private XsdElement parseElement(Element elementNode) {
        String name = elementNode.getAttribute("name");
        if (name == null || name.isEmpty()) {
            name = "element";
        }

        XsdElement element = new XsdElement(name);

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
     * Checks if an element is an XSD element with given local name.
     */
    private boolean isXsdElement(Element element, String localName) {
        return XSD_NAMESPACE.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }
}
