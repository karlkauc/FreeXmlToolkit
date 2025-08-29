package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for manipulating XSD DOM structure.
 * Provides CRUD operations for XSD elements while maintaining schema validity.
 */
public class XsdDomManipulator {

    private static final Logger logger = LogManager.getLogger(XsdDomManipulator.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String DEFAULT_XSD_PREFIX = "xs";

    private Document document;
    private Element schemaRoot;
    private String xsdPrefix = DEFAULT_XSD_PREFIX;

    /**
     * Initialize the manipulator with XSD content
     */
    public void loadXsd(String xsdContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.parse(new InputSource(new StringReader(xsdContent)));

        // Find schema root
        NodeList schemaNodes = document.getElementsByTagNameNS(XSD_NS, "schema");
        if (schemaNodes.getLength() == 0) {
            throw new IllegalArgumentException("No schema element found in XSD");
        }
        schemaRoot = (Element) schemaNodes.item(0);

        // Determine the prefix used by the schema for the XSD namespace (fallback to "xs")
        String detectedPrefix = schemaRoot.getPrefix();
        if (detectedPrefix != null && !detectedPrefix.isBlank()) {
            xsdPrefix = detectedPrefix;
        } else {
            xsdPrefix = DEFAULT_XSD_PREFIX;
        }
    }

    /**
     * Add a new element to the schema
     */
    public Element createElement(String parentXPath, String elementName, String elementType,
                                 String minOccurs, String maxOccurs) {
        try {
            logger.info("Creating element '{}' with type '{}' in parent '{}'", elementName, elementType, parentXPath);

            Element parent = findElementByXPath(parentXPath);
            if (parent == null) {
                logger.error("Parent element not found: {}", parentXPath);
                return null;
            }

            logger.info("Parent element found: {}", parent.getTagName());

            Element newElement = document.createElementNS(XSD_NS, xsdPrefix + ":element");
            newElement.setAttribute("name", elementName);

            if (elementType != null && !elementType.isEmpty()) {
                newElement.setAttribute("type", elementType);
            }

            if (minOccurs != null && !minOccurs.equals("1")) {
                newElement.setAttribute("minOccurs", minOccurs);
            }

            if (maxOccurs != null && !maxOccurs.equals("1")) {
                newElement.setAttribute("maxOccurs", maxOccurs);
            }

            // Find appropriate insertion point
            // Early validation: Check if we can add children to this element
            if (!canElementHaveChildren(parent)) {
                logger.error("Cannot add child element to '{}' - parent has simpleType or built-in type",
                        parent.getAttribute("name"));
                return null;
            }

            Element insertionPoint = findInsertionPoint(parent);
            if (insertionPoint != null) {
                // If the insertion point is a complexType, ensure a sequence exists
                if ("complexType".equals(insertionPoint.getLocalName())) {
                    Element sequence = ensureSequence(insertionPoint);
                    sequence.appendChild(newElement);
                } else {
                    insertionPoint.appendChild(newElement);
                }
            } else {
                // No insertion point found, prepare valid content model
                if ("element".equals(parent.getLocalName())) {
                    // Create inline complexType and a sequence container
                    Element inlineComplexType = document.createElementNS(XSD_NS, xsdPrefix + ":complexType");
                    parent.appendChild(inlineComplexType);
                    Element sequence = document.createElementNS(XSD_NS, xsdPrefix + ":sequence");
                    inlineComplexType.appendChild(sequence);
                    sequence.appendChild(newElement);
                } else if ("complexType".equals(parent.getLocalName())) {
                    Element sequence = ensureSequence(parent);
                    sequence.appendChild(newElement);
                } else {
                    // As a fallback, append to parent (covers when parent is already a sequence/choice)
                    parent.appendChild(newElement);
                }
            }

            logger.info("Created element: {} in parent: {}", elementName, parentXPath);
            return newElement;

        } catch (Exception e) {
            logger.error("Error creating element", e);
            return null;
        }
    }

    /**
     * Add a new attribute to an element
     */
    public Element createAttribute(String parentXPath, String attributeName, String attributeType,
                                   String use, String defaultValue) {
        try {
            Element parent = findElementByXPath(parentXPath);
            if (parent == null) {
                logger.error("Parent element not found: {}", parentXPath);
                return null;
            }

            // Early validation: Check if we can add attributes to this element
            if (!canElementHaveChildren(parent)) {
                logger.error("Cannot add attribute to element '{}' - element has simple/built-in type and cannot have attributes",
                        parent.getAttribute("name"));
                return null;
            }

            Element newAttribute = document.createElementNS(XSD_NS, xsdPrefix + ":attribute");
            newAttribute.setAttribute("name", attributeName);

            if (attributeType != null && !attributeType.isEmpty()) {
                newAttribute.setAttribute("type", attributeType);
            }

            if (use != null && !use.equals("optional")) {
                newAttribute.setAttribute("use", use);
            }

            if (defaultValue != null && !defaultValue.isEmpty()) {
                newAttribute.setAttribute("default", defaultValue);
            }

            // Determine correct insertion parent (complexType), create inline complexType if needed
            Element insertionParent = findAttributeInsertionParent(parent);
            if (insertionParent == null) {
                logger.error("Could not determine insertion parent for attribute '{}' under '{}'.", attributeName, parentXPath);
                return null;
            }
            // Attributes should be added after content model, before other attributes
            insertAttributeInOrder(insertionParent, newAttribute);

            logger.info("Created attribute: {} in parent: {}", attributeName, parentXPath);
            return newAttribute;

        } catch (Exception e) {
            logger.error("Error creating attribute", e);
            return null;
        }
    }

    /**
     * Delete an element from the schema
     */
    public boolean deleteElement(String xpath) {
        try {
            Element element = findElementByXPath(xpath);
            if (element == null) {
                logger.error("Element not found: {}", xpath);
                return false;
            }

            Node parent = element.getParentNode();
            if (parent != null) {
                parent.removeChild(element);
                logger.info("Deleted element at: {}", xpath);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error deleting element", e);
            return false;
        }
    }

    /**
     * Rename an element or attribute
     */
    public boolean renameElement(String xpath, String newName) {
        try {
            Element element = findElementByXPath(xpath);
            if (element == null) {
                logger.error("Element not found: {}", xpath);
                return false;
            }

            // Check if it's a type definition or a named element/attribute
            if (element.hasAttribute("name")) {
                String oldName = element.getAttribute("name");
                element.setAttribute("name", newName);

                // Update references if it's a global type
                if (isGlobalDefinition(element)) {
                    updateReferences(oldName, newName, element.getLocalName());
                }

                logger.info("Renamed element from {} to {}", oldName, newName);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error renaming element", e);
            return false;
        }
    }

    /**
     * Update element properties
     */
    public boolean updateElementProperties(String xpath, String property, String value) {
        try {
            Element element = findElementByXPath(xpath);
            if (element == null) {
                logger.error("Element not found: {}", xpath);
                return false;
            }

            if (value == null || value.isEmpty()) {
                element.removeAttribute(property);
            } else {
                element.setAttribute(property, value);
            }

            logger.info("Updated property {} to {} for element at {}", property, value, xpath);
            return true;

        } catch (Exception e) {
            logger.error("Error updating element properties", e);
            return false;
        }
    }

    /**
     * Move an element to a new parent
     */
    public boolean moveElement(String elementXPath, String newParentXPath) {
        try {
            Element element = findElementByXPath(elementXPath);
            Element newParent = findElementByXPath(newParentXPath);

            if (element == null || newParent == null) {
                logger.error("Element or parent not found");
                return false;
            }

            // Check if move is valid
            if (!isValidMove(element, newParent)) {
                logger.error("Invalid move operation");
                return false;
            }

            Node oldParent = element.getParentNode();
            oldParent.removeChild(element);

            Element insertionPoint = findInsertionPoint(newParent);
            if (insertionPoint != null) {
                insertionPoint.appendChild(element);
            } else {
                newParent.appendChild(element);
            }

            logger.info("Moved element from {} to {}", elementXPath, newParentXPath);
            return true;

        } catch (Exception e) {
            logger.error("Error moving element", e);
            return false;
        }
    }

    /**
     * Add a sequence container
     */
    public Element createSequence(String parentXPath, String minOccurs, String maxOccurs) {
        return createContainer(parentXPath, "sequence", minOccurs, maxOccurs);
    }

    /**
     * Add a choice container
     */
    public Element createChoice(String parentXPath, String minOccurs, String maxOccurs) {
        return createContainer(parentXPath, "choice", minOccurs, maxOccurs);
    }

    /**
     * Create a complex type
     */
    public Element createComplexType(String parentXPath, String typeName, boolean global) {
        try {
            Element complexType = document.createElementNS(XSD_NS, xsdPrefix + ":complexType");

            if (typeName != null && !typeName.isEmpty()) {
                complexType.setAttribute("name", typeName);
            }

            if (global) {
                schemaRoot.appendChild(complexType);
            } else {
                Element parent = findElementByXPath(parentXPath);
                if (parent != null) {
                    parent.appendChild(complexType);
                }
            }

            logger.info("Created complex type: {}", typeName);
            return complexType;

        } catch (Exception e) {
            logger.error("Error creating complex type", e);
            return null;
        }
    }

    /**
     * Get the current XSD as string
     */
    public String getXsdAsString() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            return writer.toString();

        } catch (Exception e) {
            logger.error("Error converting DOM to string", e);
            return null;
        }
    }

    /**
     * Validate the current XSD structure
     */
    public List<String> validateStructure() {
        List<String> errors = new ArrayList<>();

        // Check for duplicate element names at same level
        checkDuplicateNames(schemaRoot, errors);

        // Check for circular references
        checkCircularReferences(errors);

        // Check required attributes
        checkRequiredAttributes(schemaRoot, errors);

        return errors;
    }

    // Helper methods

    public Element findElementByXPath(String xpath) {
        logger.debug("Finding element by XPath: {}", xpath);

        // For now, use simple path resolution since XSD structure is predictable
        return findElementBySimplePath(xpath);
    }


    private Element findElementBySimplePath(String xpath) {
        logger.debug("Finding element by simple path: {}", xpath);

        // Handle root element case
        if (xpath.equals("/") || xpath.isEmpty()) {
            return schemaRoot;
        }

        String[] parts = xpath.split("/");
        if (parts.length < 2) {
            logger.warn("Invalid XPath format: {}", xpath);
            return null;
        }

        // Start with the root element (first part after /)
        String rootName = parts[1];
        Element current = findRootElement(rootName);

        // If not found as root element, try to find as global complexType or simpleType
        if (current == null) {
            current = findComplexTypeByName(rootName);
            if (current == null) {
                current = findSimpleTypeByName(rootName);
            }
        }

        if (current == null) {
            logger.warn("Element, complexType, or simpleType '{}' not found", rootName);
            return null;
        }

        // Navigate through the rest of the path
        for (int i = 2; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            Element child = findDirectChildElement(current, part);
            if (child == null) {
                logger.warn("Element '{}' not found in path '{}' at parent '{}'",
                        part, xpath, current.getAttribute("name"));
                return null;
            }
            current = child;
        }

        logger.debug("Found element: {} (tagName: {})", current.getAttribute("name"), current.getTagName());
        return current;
    }

    /**
     * Find the root element in the schema by name
     */
    private Element findRootElement(String name) {
        NodeList elements = schemaRoot.getElementsByTagNameNS(XSD_NS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (name.equals(element.getAttribute("name")) && element.getParentNode() == schemaRoot) {
                return element;
            }
        }
        return null;
    }

    /**
     * Find a global element by name (anywhere in the schema)
     */
    private Element findGlobalElementByName(String name) {
        NodeList elements = schemaRoot.getElementsByTagNameNS(XSD_NS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (name.equals(element.getAttribute("name"))) {
                return element;
            }
        }
        return null;
    }

    /**
     * Find a direct child element by name within an element's content model
     */
    private Element findDirectChildElement(Element parent, String childName) {
        logger.debug("Looking for child element '{}' in parent '{}'", childName, parent.getAttribute("name"));

        // Debug: Print parent structure
        debugPrintElement(parent, 1);

        // Special handling for structural elements (sequence, choice, all)
        if ("sequence".equals(childName) || "choice".equals(childName) || "all".equals(childName)) {
            logger.debug("Looking for structural element: {}", childName);
            // Look for direct structural children first
            NodeList directChildren = parent.getChildNodes();
            for (int i = 0; i < directChildren.getLength(); i++) {
                Node child = directChildren.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (childName.equals(childElement.getLocalName())) {
                        logger.debug("Found direct structural child: {}", childName);
                        return childElement;
                    }
                }
            }

            // Also check in complexType children if not found directly
            NodeList complexTypes = parent.getElementsByTagNameNS(XSD_NS, "complexType");
            for (int i = 0; i < complexTypes.getLength(); i++) {
                Element complexType = (Element) complexTypes.item(i);
                NodeList structuralChildren = complexType.getChildNodes();
                for (int j = 0; j < structuralChildren.getLength(); j++) {
                    Node child = structuralChildren.item(j);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element childElement = (Element) child;
                        if (childName.equals(childElement.getLocalName())) {
                            logger.debug("Found structural child in complexType: {}", childName);
                            return childElement;
                        }
                    }
                }
            }
        }

        // First check if this element has a 'type' attribute that references a complexType
        String typeAttr = parent.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            logger.debug("Element '{}' has type reference: {}", parent.getAttribute("name"), typeAttr);
            Element typeElement = findComplexTypeByName(typeAttr);
            if (typeElement != null) {
                logger.debug("Found referenced complexType: {}", typeAttr);
                Element found = findElementInComplexType(typeElement, childName);
                if (found != null) return found;
            } else {
                // Check if it's a simpleType - simpleTypes cannot have child elements
                Element simpleTypeElement = findSimpleTypeByName(typeAttr);
                if (simpleTypeElement != null) {
                    logger.debug("Element '{}' references simpleType '{}' - cannot have child elements",
                            parent.getAttribute("name"), typeAttr);
                    return null;
                }
                // Check if it's a built-in type like xs:string
                if (typeAttr.startsWith("xs:") || typeAttr.startsWith(xsdPrefix + ":")) {
                    logger.debug("Element '{}' has built-in type '{}' - cannot have child elements",
                            parent.getAttribute("name"), typeAttr);
                    return null;
                }
            }
        }

        // Look in sequences, choices, all groups, and complexType content within the element itself
        NodeList sequences = parent.getElementsByTagNameNS(XSD_NS, "sequence");
        logger.debug("Found {} sequence(s)", sequences.getLength());
        for (int i = 0; i < sequences.getLength(); i++) {
            Element found = findElementInContainer((Element) sequences.item(i), childName);
            if (found != null) return found;
        }

        NodeList choices = parent.getElementsByTagNameNS(XSD_NS, "choice");
        logger.debug("Found {} choice(s)", choices.getLength());
        for (int i = 0; i < choices.getLength(); i++) {
            Element found = findElementInContainer((Element) choices.item(i), childName);
            if (found != null) return found;
        }

        NodeList alls = parent.getElementsByTagNameNS(XSD_NS, "all");
        logger.debug("Found {} all(s)", alls.getLength());
        for (int i = 0; i < alls.getLength(); i++) {
            Element found = findElementInContainer((Element) alls.item(i), childName);
            if (found != null) return found;
        }

        // Look in complexType directly
        NodeList complexTypes = parent.getElementsByTagNameNS(XSD_NS, "complexType");
        logger.debug("Found {} complexType(s)", complexTypes.getLength());
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element found = findElementInContainer((Element) complexTypes.item(i), childName);
            if (found != null) return found;
        }

        // If not found in content model, check if it's a global element reference
        // This handles cases where the XPath refers to a global element that's referenced via ref
        Element globalElement = findGlobalElementByName(childName);
        if (globalElement != null) {
            logger.debug("Found global element reference: {}", childName);
            return globalElement;
        }

        logger.debug("Child element '{}' not found in parent '{}'", childName, parent.getAttribute("name"));
        return null;
    }

    /**
     * Debug helper to print element structure
     */
    private void debugPrintElement(Element element, int depth) {
        String indent = "  ".repeat(depth);
        logger.debug("{}Element: {} (name: {}, localName: {})",
                indent, element.getTagName(), element.getAttribute("name"), element.getLocalName());

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && depth < 3) { // Limit depth
                debugPrintElement((Element) child, depth + 1);
            }
        }
    }

    /**
     * Find an element by name within a container (sequence, choice, all, complexType)
     */
    private Element findElementInContainer(Element container, String elementName) {
        logger.debug("Searching in container: {} for element: {}", container.getLocalName(), elementName);

        String targetName = elementName;
        String targetNameStripped = stripPrefix(elementName);

        // First check direct children
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                logger.debug("  Child: {} (name: {})", childElement.getLocalName(), childElement.getAttribute("name"));

                if ("element".equals(childElement.getLocalName())) {
                    String childName = childElement.getAttribute("name");
                    String childRef = childElement.getAttribute("ref");
                    if (!childName.isEmpty()) {
                        if (targetName.equals(childName) || targetNameStripped.equals(childName)) {
                            logger.debug("  Found element by name: {}", childName);
                            return childElement;
                        }
                    } else if (!childRef.isEmpty()) {
                        String childRefStripped = stripPrefix(childRef);
                        if (targetName.equals(childRef) || targetNameStripped.equals(childRefStripped)) {
                            logger.debug("  Found element by ref: {}", childRef);
                            return childElement;
                        }
                    }
                }
            }
        }

        // If not found in direct children, check nested sequences/choices
        NodeList nestedSequences = container.getElementsByTagNameNS(XSD_NS, "sequence");
        for (int i = 0; i < nestedSequences.getLength(); i++) {
            Element found = findElementInContainer((Element) nestedSequences.item(i), elementName);
            if (found != null) return found;
        }

        NodeList nestedChoices = container.getElementsByTagNameNS(XSD_NS, "choice");
        for (int i = 0; i < nestedChoices.getLength(); i++) {
            Element found = findElementInContainer((Element) nestedChoices.item(i), elementName);
            if (found != null) return found;
        }

        logger.debug("Element '{}' not found in container: {}", elementName, container.getLocalName());
        return null;
    }

    private String stripPrefix(String value) {
        if (value == null) return "";
        int idx = value.indexOf(":");
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    /**
     * Find a complexType by name in the schema
     */
    private Element findComplexTypeByName(String typeName) {
        logger.debug("Searching for complexType: {}", typeName);

        // Remove namespace prefix if present (e.g., "DocumentType" from "ns:DocumentType")
        String localName = typeName;
        if (typeName.contains(":")) {
            localName = typeName.substring(typeName.indexOf(":") + 1);
        }

        NodeList complexTypes = document.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            String name = complexType.getAttribute("name");
            if (localName.equals(name)) {
                logger.debug("Found complexType: {}", name);
                return complexType;
            }
        }

        logger.debug("ComplexType '{}' not found", typeName);
        return null;
    }

    /**
     * Find a simpleType by name in the schema
     */
    private Element findSimpleTypeByName(String typeName) {
        logger.debug("Searching for simpleType: {}", typeName);

        // Remove namespace prefix if present (e.g., "DataOperationType" from "ns:DataOperationType")
        String localName = typeName;
        if (typeName.contains(":")) {
            localName = typeName.substring(typeName.indexOf(":") + 1);
        }

        NodeList simpleTypes = document.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            String name = simpleType.getAttribute("name");
            if (localName.equals(name)) {
                logger.debug("Found simpleType: {}", name);
                return simpleType;
            }
        }

        logger.debug("SimpleType '{}' not found", typeName);
        return null;
    }

    /**
     * Find an element within a complexType definition
     */
    private Element findElementInComplexType(Element complexType, String elementName) {
        logger.debug("Searching in complexType '{}' for element: {}",
                complexType.getAttribute("name"), elementName);

        // Look in sequences within the complexType
        NodeList sequences = complexType.getElementsByTagNameNS(XSD_NS, "sequence");
        for (int i = 0; i < sequences.getLength(); i++) {
            Element found = findElementInContainer((Element) sequences.item(i), elementName);
            if (found != null) return found;
        }

        // Look in choices within the complexType  
        NodeList choices = complexType.getElementsByTagNameNS(XSD_NS, "choice");
        for (int i = 0; i < choices.getLength(); i++) {
            Element found = findElementInContainer((Element) choices.item(i), elementName);
            if (found != null) return found;
        }

        // Look in all groups within the complexType
        NodeList alls = complexType.getElementsByTagNameNS(XSD_NS, "all");
        for (int i = 0; i < alls.getLength(); i++) {
            Element found = findElementInContainer((Element) alls.item(i), elementName);
            if (found != null) return found;
        }

        logger.debug("Element '{}' not found in complexType '{}'", elementName,
                complexType.getAttribute("name"));
        return null;
    }

    private Element findInsertionPoint(Element parent) {
        logger.debug("Finding insertion point for parent: {} (tag: {})",
                parent.getAttribute("name"), parent.getTagName());

        // First check if this element has a 'type' attribute that references a complexType
        String typeAttr = parent.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            logger.debug("Parent element '{}' has type reference: {}", parent.getAttribute("name"), typeAttr);
            Element typeElement = findComplexTypeByName(typeAttr);
            if (typeElement != null) {
                logger.debug("Found referenced complexType: {}", typeAttr);

                // Look for sequence/choice/all in the referenced complexType
                NodeList sequences = typeElement.getElementsByTagNameNS(XSD_NS, "sequence");
                if (sequences.getLength() > 0) {
                    logger.debug("Found sequence in referenced complexType");
                    return (Element) sequences.item(0);
                }

                NodeList choices = typeElement.getElementsByTagNameNS(XSD_NS, "choice");
                if (choices.getLength() > 0) {
                    logger.debug("Found choice in referenced complexType");
                    return (Element) choices.item(0);
                }

                NodeList alls = typeElement.getElementsByTagNameNS(XSD_NS, "all");
                if (alls.getLength() > 0) {
                    logger.debug("Found all in referenced complexType");
                    return (Element) alls.item(0);
                }
            } else {
                // Check if it's a simpleType or built-in type - these cannot have child elements
                Element simpleTypeElement = findSimpleTypeByName(typeAttr);
                if (simpleTypeElement != null || typeAttr.startsWith("xs:")) {
                    logger.debug("Element '{}' references simpleType or built-in type '{}' - cannot add child elements",
                            parent.getAttribute("name"), typeAttr);
                    return null;  // No insertion point for simpleTypes
                }
            }
        }

        // Find sequence or choice container within parent element itself
        NodeList sequences = parent.getElementsByTagNameNS(XSD_NS, "sequence");
        if (sequences.getLength() > 0) {
            logger.debug("Found sequence in parent element");
            return (Element) sequences.item(0);
        }

        NodeList choices = parent.getElementsByTagNameNS(XSD_NS, "choice");
        if (choices.getLength() > 0) {
            logger.debug("Found choice in parent element");
            return (Element) choices.item(0);
        }

        NodeList alls = parent.getElementsByTagNameNS(XSD_NS, "all");
        if (alls.getLength() > 0) {
            logger.debug("Found all in parent element");
            return (Element) alls.item(0);
        }

        // Check if parent is complexType
        NodeList complexTypes = parent.getElementsByTagNameNS(XSD_NS, "complexType");
        if (complexTypes.getLength() > 0) {
            logger.debug("Found complexType in parent element");
            return (Element) complexTypes.item(0);
        }

        logger.debug("No insertion point found for parent: {}", parent.getAttribute("name"));
        return null;
    }

    private void insertAttributeInOrder(Element parent, Element attribute) {
        // Find the right position for attribute (after content model, before other attributes)
        NodeList children = parent.getChildNodes();
        Node insertBefore = null;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("attribute".equals(childElement.getLocalName()) ||
                        "attributeGroup".equals(childElement.getLocalName()) ||
                        "anyAttribute".equals(childElement.getLocalName())) {
                    insertBefore = child;
                    break;
                }
            }
        }

        if (insertBefore != null) {
            parent.insertBefore(attribute, insertBefore);
        } else {
            parent.appendChild(attribute);
        }
    }

    private boolean isGlobalDefinition(Element element) {
        return element.getParentNode() == schemaRoot;
    }

    private void updateReferences(String oldName, String newName, String elementType) {
        // Update all references to the renamed type/element
        NodeList allElements = document.getElementsByTagNameNS(XSD_NS, "*");

        for (int i = 0; i < allElements.getLength(); i++) {
            Element elem = (Element) allElements.item(i);

            // Update type references
            if (elem.hasAttribute("type") && elem.getAttribute("type").equals(oldName)) {
                elem.setAttribute("type", newName);
            }

            // Update ref references
            if (elem.hasAttribute("ref") && elem.getAttribute("ref").equals(oldName)) {
                elem.setAttribute("ref", newName);
            }

            // Update base references
            if (elem.hasAttribute("base") && elem.getAttribute("base").equals(oldName)) {
                elem.setAttribute("base", newName);
            }
        }
    }

    private boolean isValidMove(Element element, Element newParent) {
        // Check if element would become its own ancestor
        Node current = newParent;
        while (current != null) {
            if (current == element) {
                return false;
            }
            current = current.getParentNode();
        }
        return true;
    }

    private Element createContainer(String parentXPath, String containerType,
                                    String minOccurs, String maxOccurs) {
        try {
            Element parent = findElementByXPath(parentXPath);
            if (parent == null) {
                logger.error("Parent element not found: {}", parentXPath);
                return null;
            }

            // Early validation: Check if we can add containers to this element
            if (!canElementHaveChildren(parent)) {
                logger.error("Cannot add '{}' to element '{}' - element has simple/built-in type and cannot have children",
                        containerType, parent.getAttribute("name"));
                return null;
            }

            Element container = document.createElementNS(XSD_NS, xsdPrefix + ":" + containerType);

            if (minOccurs != null && !minOccurs.equals("1")) {
                container.setAttribute("minOccurs", minOccurs);
            }

            if (maxOccurs != null && !maxOccurs.equals("1")) {
                container.setAttribute("maxOccurs", maxOccurs);
            }

            // Find appropriate insertion point (handle type references)
            Element insertionPoint = findContainerInsertionPoint(parent, containerType);
            if (insertionPoint == null) {
                // If parent is an element without inline complexType, create one
                if ("element".equals(parent.getLocalName())) {
                    // Create inline complexType if missing
                    NodeList existing = parent.getElementsByTagNameNS(XSD_NS, "complexType");
                    if (existing.getLength() > 0) {
                        insertionPoint = (Element) existing.item(0);
                    } else {
                        Element inlineComplexType = document.createElementNS(XSD_NS, xsdPrefix + ":complexType");
                        parent.appendChild(inlineComplexType);
                        insertionPoint = inlineComplexType;
                    }
                } else if ("complexType".equals(parent.getLocalName())) {
                    insertionPoint = parent;
                }
            }

            if (insertionPoint == null) {
                logger.error("No valid insertion point found to create '{}' under '{}'.", containerType, parentXPath);
                return null;
            }

            insertionPoint.appendChild(container);
            logger.info("Created {} container under {}", containerType, parentXPath);

            return container;

        } catch (Exception e) {
            logger.error("Error creating container", e);
            return null;
        }
    }

    /**
     * Check if an element can have child elements based on its type
     */
    private boolean canElementHaveChildren(Element element) {
        String typeAttr = element.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            // Check if it's a simpleType - simpleTypes cannot have child elements
            Element simpleTypeElement = findSimpleTypeByName(typeAttr);
            if (simpleTypeElement != null) {
                logger.debug("Element '{}' has simpleType '{}' - cannot have children",
                        element.getAttribute("name"), typeAttr);
                return false;
            }
            // Check if it's a built-in type like xs:string or with the actual schema prefix
            if (typeAttr.startsWith("xs:") || typeAttr.startsWith(xsdPrefix + ":")) {
                logger.debug("Element '{}' has built-in type '{}' - cannot have children",
                        element.getAttribute("name"), typeAttr);
                return false;
            }
            // If it's a complexType reference, it can have children
            Element complexTypeElement = findComplexTypeByName(typeAttr);
            if (complexTypeElement != null) {
                logger.debug("Element '{}' has complexType '{}' - can have children",
                        element.getAttribute("name"), typeAttr);
                return true;
            }
        }

        // If no type attribute, check for inline complexType
        NodeList complexTypes = element.getElementsByTagNameNS(XSD_NS, "complexType");
        if (complexTypes.getLength() > 0) {
            logger.debug("Element '{}' has inline complexType - can have children", element.getAttribute("name"));
            return true;
        }

        // If no type info found, assume it can have children (conservative approach)
        logger.debug("Element '{}' type unknown - assuming can have children", element.getAttribute("name"));
        return true;
    }

    /**
     * Find insertion point for containers (sequence, choice, all)
     * This is different from element insertion as containers go directly into complexTypes
     */
    private Element findContainerInsertionPoint(Element parent, String containerType) {
        logger.debug("Finding container insertion point for {}: {} (tag: {})",
                containerType, parent.getAttribute("name"), parent.getTagName());

        // Check if this element has a 'type' attribute that references a complexType
        String typeAttr = parent.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            logger.debug("Parent element '{}' has type reference: {}", parent.getAttribute("name"), typeAttr);
            Element typeElement = findComplexTypeByName(typeAttr);
            if (typeElement != null) {
                logger.debug("Found referenced complexType for container: {}", typeAttr);
                return typeElement;  // Containers go directly into complexType
            }
        }

        // If parent is already a complexType, return it
        if ("complexType".equals(parent.getLocalName())) {
            logger.debug("Parent is already a complexType");
            return parent;
        }

        // Look for complexType within parent element
        NodeList complexTypes = parent.getElementsByTagNameNS(XSD_NS, "complexType");
        if (complexTypes.getLength() > 0) {
            logger.debug("Found complexType in parent element");
            return (Element) complexTypes.item(0);
        }

        logger.debug("No container insertion point found, will use parent");
        return null;  // Will use parent directly
    }

    private Element ensureSequence(Element complexType) {
        NodeList sequences = complexType.getElementsByTagNameNS(XSD_NS, "sequence");
        if (sequences.getLength() > 0) {
            return (Element) sequences.item(0);
        }
        Element sequence = document.createElementNS(XSD_NS, xsdPrefix + ":sequence");
        complexType.appendChild(sequence);
        return sequence;
    }

    /**
     * Determines the correct parent node to insert attributes into.
     * If the target element references a global complexType, the attribute is added there.
     * If it has an inline complexType, it is used. If neither exists, an inline complexType is created.
     * If the element references a simpleType or built-in type, attributes cannot be added and null is returned.
     */
    private Element findAttributeInsertionParent(Element parent) {
        if (parent == null) {
            return null;
        }

        // If the parent itself is a complexType, add directly into it
        if ("complexType".equals(parent.getLocalName())) {
            return parent;
        }

        // If element references a type, try to resolve complexType
        String typeAttr = parent.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            Element typeElement = findComplexTypeByName(typeAttr);
            if (typeElement != null) {
                return typeElement;
            }
            // Cannot add attributes to elements that reference simpleType or built-in types
            Element simpleTypeElement = findSimpleTypeByName(typeAttr);
            if (simpleTypeElement != null || typeAttr.contains(":")) {
                return null;
            }
        }

        // Try to find an inline complexType
        NodeList complexTypes = parent.getElementsByTagNameNS(XSD_NS, "complexType");
        if (complexTypes.getLength() > 0) {
            return (Element) complexTypes.item(0);
        }

        // Create an inline complexType and return it
        Element inline = document.createElementNS(XSD_NS, xsdPrefix + ":complexType");
        parent.appendChild(inline);
        return inline;
    }

    private void checkDuplicateNames(Element parent, List<String> errors) {
        // Implementation for checking duplicate names
    }

    private void checkCircularReferences(List<String> errors) {
        // Implementation for checking circular references
    }

    private void checkRequiredAttributes(Element element, List<String> errors) {
        // Implementation for checking required attributes
    }

    /**
     * Returns the current DOM document for validation purposes
     */
    public Document getDocument() {
        return document;
    }

    /**
     * Get the current XSD content as XML string
     */
    public String getXmlContent() {
        if (document == null) {
            return null;
        }

        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.error("Error converting DOM to XML string", e);
            return null;
        }
    }

    /**
     * Find a DOM node by XPath for undo/redo operations
     */
    public Node findNodeByPath(String xpath) {
        return findElementByXPath(xpath);
    }

    /**
     * Get all global type definitions (simple and complex types) in the schema
     */
    public List<org.fxt.freexmltoolkit.domain.TypeInfo> getAllGlobalTypes() {
        List<org.fxt.freexmltoolkit.domain.TypeInfo> types = new ArrayList<>();

        if (document == null || schemaRoot == null) {
            return types;
        }

        // Get all global simple types
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            String name = simpleType.getAttribute("name");

            // Only include global types (those with a name attribute directly under schema)
            if (name != null && !name.isEmpty() && simpleType.getParentNode() == schemaRoot) {
                org.fxt.freexmltoolkit.domain.TypeInfo typeInfo = analyzeSimpleType(simpleType, name);
                types.add(typeInfo);
            }
        }

        // Get all global complex types
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            String name = complexType.getAttribute("name");

            // Only include global types (those with a name attribute directly under schema)
            if (name != null && !name.isEmpty() && complexType.getParentNode() == schemaRoot) {
                org.fxt.freexmltoolkit.domain.TypeInfo typeInfo = analyzeComplexType(complexType, name);
                types.add(typeInfo);
            }
        }

        return types;
    }

    /**
     * Analyze a simple type element and create TypeInfo
     */
    private org.fxt.freexmltoolkit.domain.TypeInfo analyzeSimpleType(Element simpleType, String name) {
        String baseType = extractSimpleTypeBase(simpleType);
        String documentation = extractDocumentation(simpleType);
        int usageCount = countTypeUsages(name);
        String xpath = generateXPathForElement(simpleType);

        return org.fxt.freexmltoolkit.domain.TypeInfo.simpleType(
                name, baseType, usageCount, documentation, xpath);
    }

    /**
     * Analyze a complex type element and create TypeInfo
     */
    private org.fxt.freexmltoolkit.domain.TypeInfo analyzeComplexType(Element complexType, String name) {
        String baseType = extractComplexTypeBase(complexType);
        String documentation = extractDocumentation(complexType);
        int usageCount = countTypeUsages(name);
        String xpath = generateXPathForElement(complexType);

        boolean isAbstract = "true".equals(complexType.getAttribute("abstract"));
        boolean isMixed = "true".equals(complexType.getAttribute("mixed"));

        String derivationType = extractDerivationType(complexType);
        String contentModel = extractContentModel(complexType);

        return org.fxt.freexmltoolkit.domain.TypeInfo.complexType(
                name, baseType, usageCount, documentation, xpath,
                isAbstract, isMixed, derivationType, contentModel);
    }

    /**
     * Extract the base type for a simple type
     */
    private String extractSimpleTypeBase(Element simpleType) {
        // Check for restriction
        NodeList restrictions = simpleType.getElementsByTagNameNS(XSD_NS, "restriction");
        if (restrictions.getLength() > 0) {
            Element restriction = (Element) restrictions.item(0);
            String base = restriction.getAttribute("base");
            if (base != null && !base.isEmpty()) {
                return base;
            }
        }

        // Check for list
        NodeList lists = simpleType.getElementsByTagNameNS(XSD_NS, "list");
        if (lists.getLength() > 0) {
            Element list = (Element) lists.item(0);
            String itemType = list.getAttribute("itemType");
            if (itemType != null && !itemType.isEmpty()) {
                return itemType + " (list)";
            }
        }

        // Check for union
        NodeList unions = simpleType.getElementsByTagNameNS(XSD_NS, "union");
        if (unions.getLength() > 0) {
            return "union";
        }

        return null;
    }

    /**
     * Extract the base type for a complex type
     */
    private String extractComplexTypeBase(Element complexType) {
        // Check for extension or restriction in complexContent
        NodeList complexContent = complexType.getElementsByTagNameNS(XSD_NS, "complexContent");
        if (complexContent.getLength() > 0) {
            Element content = (Element) complexContent.item(0);

            NodeList extensions = content.getElementsByTagNameNS(XSD_NS, "extension");
            if (extensions.getLength() > 0) {
                Element extension = (Element) extensions.item(0);
                return extension.getAttribute("base");
            }

            NodeList restrictions = content.getElementsByTagNameNS(XSD_NS, "restriction");
            if (restrictions.getLength() > 0) {
                Element restriction = (Element) restrictions.item(0);
                return restriction.getAttribute("base");
            }
        }

        // Check for extension or restriction in simpleContent
        NodeList simpleContent = complexType.getElementsByTagNameNS(XSD_NS, "simpleContent");
        if (simpleContent.getLength() > 0) {
            Element content = (Element) simpleContent.item(0);

            NodeList extensions = content.getElementsByTagNameNS(XSD_NS, "extension");
            if (extensions.getLength() > 0) {
                Element extension = (Element) extensions.item(0);
                return extension.getAttribute("base");
            }

            NodeList restrictions = content.getElementsByTagNameNS(XSD_NS, "restriction");
            if (restrictions.getLength() > 0) {
                Element restriction = (Element) restrictions.item(0);
                return restriction.getAttribute("base");
            }
        }

        return null;
    }

    /**
     * Extract derivation type (extension, restriction, or null for no derivation)
     */
    private String extractDerivationType(Element complexType) {
        NodeList complexContent = complexType.getElementsByTagNameNS(XSD_NS, "complexContent");
        if (complexContent.getLength() > 0) {
            Element content = (Element) complexContent.item(0);

            if (content.getElementsByTagNameNS(XSD_NS, "extension").getLength() > 0) {
                return "extension";
            }
            if (content.getElementsByTagNameNS(XSD_NS, "restriction").getLength() > 0) {
                return "restriction";
            }
        }

        NodeList simpleContent = complexType.getElementsByTagNameNS(XSD_NS, "simpleContent");
        if (simpleContent.getLength() > 0) {
            Element content = (Element) simpleContent.item(0);

            if (content.getElementsByTagNameNS(XSD_NS, "extension").getLength() > 0) {
                return "extension";
            }
            if (content.getElementsByTagNameNS(XSD_NS, "restriction").getLength() > 0) {
                return "restriction";
            }
        }

        return null;
    }

    /**
     * Extract content model type (sequence, choice, all, empty, simple)
     */
    private String extractContentModel(Element complexType) {
        // Check for simple content
        NodeList simpleContent = complexType.getElementsByTagNameNS(XSD_NS, "simpleContent");
        if (simpleContent.getLength() > 0) {
            return "simple";
        }

        // Check for sequence
        NodeList sequences = complexType.getElementsByTagNameNS(XSD_NS, "sequence");
        if (sequences.getLength() > 0) {
            return "sequence";
        }

        // Check for choice
        NodeList choices = complexType.getElementsByTagNameNS(XSD_NS, "choice");
        if (choices.getLength() > 0) {
            return "choice";
        }

        // Check for all
        NodeList alls = complexType.getElementsByTagNameNS(XSD_NS, "all");
        if (alls.getLength() > 0) {
            return "all";
        }

        // Check if it has any child elements at all
        NodeList elements = complexType.getElementsByTagNameNS(XSD_NS, "element");
        NodeList attributes = complexType.getElementsByTagNameNS(XSD_NS, "attribute");

        if (elements.getLength() == 0 && attributes.getLength() == 0) {
            return "empty";
        }

        return "mixed";
    }

    /**
     * Extract documentation from xs:annotation/xs:documentation
     */
    private String extractDocumentation(Element element) {
        NodeList annotations = element.getElementsByTagNameNS(XSD_NS, "annotation");
        if (annotations.getLength() > 0) {
            Element annotation = (Element) annotations.item(0);
            NodeList docs = annotation.getElementsByTagNameNS(XSD_NS, "documentation");
            if (docs.getLength() > 0) {
                Element doc = (Element) docs.item(0);
                return doc.getTextContent();
            }
        }
        return null;
    }

    /**
     * Count how many times a type is used in the schema
     */
    private int countTypeUsages(String typeName) {
        if (document == null) return 0;

        int count = 0;
        String[] typeAttributes = {"type", "base", "itemType", "memberTypes"};

        for (String attrName : typeAttributes) {
            // Find all elements with the type attribute
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String attrValue = element.getAttribute(attrName);

                if (attrValue != null && !attrValue.isEmpty()) {
                    // Handle namespaced types (remove prefix for comparison)
                    String localType = attrValue.contains(":") ?
                            attrValue.substring(attrValue.indexOf(":") + 1) : attrValue;

                    if (typeName.equals(localType)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Generate XPath for an element (simplified version)
     */
    private String generateXPathForElement(Element element) {
        StringBuilder xpath = new StringBuilder();
        Node current = element;

        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element elem = (Element) current;
            String name = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

            if (elem == schemaRoot) {
                xpath.insert(0, "/" + name);
                break;
            } else {
                String nameAttr = elem.getAttribute("name");
                if (nameAttr != null && !nameAttr.isEmpty()) {
                    xpath.insert(0, "/" + name + "[@name='" + nameAttr + "']");
                } else {
                    xpath.insert(0, "/" + name);
                }
            }

            current = current.getParentNode();
        }

        return xpath.toString();
    }

    /**
     * Find all inline types that could be extracted to global types
     */
    public List<Element> findInlineTypes() {
        List<Element> inlineTypes = new ArrayList<>();

        if (document == null) return inlineTypes;

        // Find unnamed simple types
        NodeList simpleTypes = document.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            String name = simpleType.getAttribute("name");

            // Include types without names (inline types)
            if (name == null || name.isEmpty()) {
                inlineTypes.add(simpleType);
            }
        }

        // Find unnamed complex types
        NodeList complexTypes = document.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            String name = complexType.getAttribute("name");

            // Include types without names (inline types)
            if (name == null || name.isEmpty()) {
                inlineTypes.add(complexType);
            }
        }

        return inlineTypes;
    }

    /**
     * Check if a type can be safely deleted (no references exist)
     */
    public boolean canDeleteType(String typeName) {
        return countTypeUsages(typeName) == 0;
    }

    /**
     * Find all references to a specific type
     */
    public List<Element> findTypeReferences(String typeName) {
        List<Element> references = new ArrayList<>();

        if (document == null) return references;

        String[] typeAttributes = {"type", "base", "itemType", "memberTypes"};

        for (String attrName : typeAttributes) {
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String attrValue = element.getAttribute(attrName);

                if (attrValue != null && !attrValue.isEmpty()) {
                    String localType = attrValue.contains(":") ?
                            attrValue.substring(attrValue.indexOf(":") + 1) : attrValue;

                    if (typeName.equals(localType)) {
                        references.add(element);
                    }
                }
            }
        }

        return references;
    }

    /**
     * Delete a global type from the schema
     */
    public boolean deleteGlobalType(String typeName) {
        if (document == null || schemaRoot == null) return false;

        // Find and remove simple types
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (typeName.equals(simpleType.getAttribute("name")) &&
                    simpleType.getParentNode() == schemaRoot) {
                schemaRoot.removeChild(simpleType);
                logger.info("Deleted simple type: {}", typeName);
                return true;
            }
        }

        // Find and remove complex types
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (typeName.equals(complexType.getAttribute("name")) &&
                    complexType.getParentNode() == schemaRoot) {
                schemaRoot.removeChild(complexType);
                logger.info("Deleted complex type: {}", typeName);
                return true;
            }
        }

        return false;
    }

    /**
     * Find a type definition by name
     */
    public Element findTypeDefinition(Document document, String typeName) {
        Element root = document.getDocumentElement();

        // Find simple types
        NodeList simpleTypes = root.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (typeName.equals(simpleType.getAttribute("name")) &&
                    simpleType.getParentNode() == root) {
                return simpleType;
            }
        }

        // Find complex types
        NodeList complexTypes = root.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (typeName.equals(complexType.getAttribute("name")) &&
                    complexType.getParentNode() == root) {
                return complexType;
            }
        }

        return null;
    }

    /**
     * Find usages of a type in the document
     */
    public List<Element> findTypeUsages(Document document, String typeName) {
        List<Element> usages = new ArrayList<>();
        String[] typeAttributes = {"type", "base", "itemType", "memberTypes"};

        for (String attrName : typeAttributes) {
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String attrValue = element.getAttribute(attrName);

                if (attrValue != null && !attrValue.isEmpty()) {
                    String localType = attrValue.contains(":") ?
                            attrValue.substring(attrValue.indexOf(":") + 1) : attrValue;

                    if (typeName.equals(localType)) {
                        usages.add(element);
                    }
                }
            }
        }

        return usages;
    }

    /**
     * Inline a type definition into an element
     */
    public void inlineTypeDefinition(Element usage, Element typeDefinition) {
        // Remove the type attribute
        usage.removeAttribute("type");

        // Clone the type definition content and add it as inline
        Element inlineType = (Element) typeDefinition.cloneNode(true);
        inlineType.removeAttribute("name"); // Remove name attribute for inline type
        usage.appendChild(inlineType);
    }

    /**
     * Find elements with inlined content (for undo operations)
     */
    public List<Element> findElementsWithInlinedContent(Document document, String originalTypeName) {
        List<Element> elements = new ArrayList<>();

        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);

            // Check if element has inline type definitions
            NodeList complexTypes = element.getElementsByTagNameNS(XSD_NS, "complexType");
            NodeList simpleTypes = element.getElementsByTagNameNS(XSD_NS, "simpleType");

            if ((complexTypes.getLength() > 0 || simpleTypes.getLength() > 0) &&
                    !element.hasAttribute("type")) {
                elements.add(element);
            }
        }

        return elements;
    }

    /**
     * Restore type reference (for undo operations)
     */
    public void restoreTypeReference(Element usage, String typeName) {
        // Remove inline type definitions
        NodeList complexTypes = usage.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = complexTypes.getLength() - 1; i >= 0; i--) {
            Element child = (Element) complexTypes.item(i);
            if (child.getParentNode() == usage) {
                usage.removeChild(child);
            }
        }

        NodeList simpleTypes = usage.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = simpleTypes.getLength() - 1; i >= 0; i--) {
            Element child = (Element) simpleTypes.item(i);
            if (child.getParentNode() == usage) {
                usage.removeChild(child);
            }
        }

        // Restore type attribute
        usage.setAttribute("type", typeName);
    }

    /**
     * Get all type definitions (for remove unused types)
     */
    public List<Element> getAllTypeDefinitions(Document document) {
        List<Element> types = new ArrayList<>();
        Element root = document.getDocumentElement();

        // Get simple types
        NodeList simpleTypes = root.getElementsByTagNameNS(XSD_NS, "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (simpleType.getParentNode() == root && simpleType.hasAttribute("name")) {
                types.add(simpleType);
            }
        }

        // Get complex types
        NodeList complexTypes = root.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == root && complexType.hasAttribute("name")) {
                types.add(complexType);
            }
        }

        return types;
    }

    /**
     * Get type definition as XML string for export
     */
    public String getTypeDefinitionAsString(Element typeElement) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(typeElement), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            logger.error("Error converting type definition to string", e);
            return null;
        }
    }
}