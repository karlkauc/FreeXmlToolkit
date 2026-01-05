/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.v2.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Helper class for parsing XSD elements and attributes.
 *
 * <p>Handles parsing of Element and Attribute declarations, including inline type definitions.</p>
 *
 * @since 2.0
 */
public class XsdElementParsingHelper {
    private static final Logger logger = LogManager.getLogger(XsdElementParsingHelper.class);

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /**
     * Checks if an element is an Element declaration.
     *
     * @param element the element to check
     * @return true if element is an Element declaration
     */
    public boolean isElement(Element element) {
        return element != null && "element".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an Attribute declaration.
     *
     * @param element the element to check
     * @return true if element is an Attribute declaration
     */
    public boolean isAttribute(Element element) {
        return element != null && "attribute".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a reference (has ref attribute).
     *
     * @param element the element to check
     * @return true if element is a reference
     */
    public boolean isReference(Element element) {
        return element != null && element.hasAttribute("ref");
    }

    /**
     * Gets the element/attribute name.
     *
     * @param element the element to check
     * @return the name attribute value
     */
    public String getElementName(Element element) {
        return element.getAttribute("name");
    }

    /**
     * Gets the element/attribute type.
     *
     * @param element the element to check
     * @return the type attribute value
     */
    public String getElementType(Element element) {
        return element.getAttribute("type");
    }

    /**
     * Gets the reference name (from ref attribute).
     *
     * @param element the element to check
     * @return the ref attribute value
     */
    public String getReferenceName(Element element) {
        return element.getAttribute("ref");
    }

    /**
     * Gets the min occurs value.
     *
     * @param element the element to check
     * @return the minOccurs value or "1" (default)
     */
    public String getMinOccurs(Element element) {
        String minOccurs = element.getAttribute("minOccurs");
        return minOccurs.isEmpty() ? "1" : minOccurs;
    }

    /**
     * Gets the max occurs value.
     *
     * @param element the element to check
     * @return the maxOccurs value or "1" (default)
     */
    public String getMaxOccurs(Element element) {
        String maxOccurs = element.getAttribute("maxOccurs");
        return maxOccurs.isEmpty() ? "1" : maxOccurs;
    }

    /**
     * Checks if an element has an inline type definition.
     *
     * @param elementNode the element to check
     * @return true if element has inline complexType or simpleType
     */
    public boolean hasInlineType(Element elementNode) {
        NodeList complexTypes = elementNode.getElementsByTagNameNS(XSD_NAMESPACE, "complexType");
        NodeList simpleTypes = elementNode.getElementsByTagNameNS(XSD_NAMESPACE, "simpleType");
        return complexTypes.getLength() > 0 || simpleTypes.getLength() > 0;
    }

    /**
     * Gets the inline ComplexType element.
     *
     * @param elementNode the element to check
     * @return the complexType element or null
     */
    public Element getInlineComplexType(Element elementNode) {
        NodeList complexTypes = elementNode.getElementsByTagNameNS(XSD_NAMESPACE, "complexType");
        return complexTypes.getLength() > 0 ? (Element) complexTypes.item(0) : null;
    }

    /**
     * Gets the inline SimpleType element.
     *
     * @param elementNode the element to check
     * @return the simpleType element or null
     */
    public Element getInlineSimpleType(Element elementNode) {
        NodeList simpleTypes = elementNode.getElementsByTagNameNS(XSD_NAMESPACE, "simpleType");
        return simpleTypes.getLength() > 0 ? (Element) simpleTypes.item(0) : null;
    }

    /**
     * Gets the nillable attribute.
     *
     * @param element the element to check
     * @return the nillable attribute value
     */
    public String getNillable(Element element) {
        return element.getAttribute("nillable");
    }

    /**
     * Gets the fixed attribute.
     *
     * @param element the element to check
     * @return the fixed attribute value
     */
    public String getFixed(Element element) {
        return element.getAttribute("fixed");
    }

    /**
     * Gets the default attribute.
     *
     * @param element the element to check
     * @return the default attribute value
     */
    public String getDefault(Element element) {
        return element.getAttribute("default");
    }

    /**
     * Gets the form attribute.
     *
     * @param element the element to check
     * @return the form attribute value
     */
    public String getForm(Element element) {
        return element.getAttribute("form");
    }

    /**
     * Gets the use attribute (for attributes).
     *
     * @param element the element to check
     * @return the use attribute value
     */
    public String getUse(Element element) {
        return element.getAttribute("use");
    }

    /**
     * Gets the abstract attribute.
     *
     * @param element the element to check
     * @return the abstract attribute value
     */
    public String getAbstract(Element element) {
        return element.getAttribute("abstract");
    }

    /**
     * Gets the substitutionGroup attribute.
     *
     * @param element the element to check
     * @return the substitutionGroup attribute value
     */
    public String getSubstitutionGroup(Element element) {
        return element.getAttribute("substitutionGroup");
    }

    /**
     * Validates element properties.
     *
     * @param elementName the element name
     * @param typeName the type name
     * @return true if valid
     */
    public boolean isValidElement(String elementName, String typeName) {
        if (elementName == null || elementName.isEmpty()) {
            logger.warn("Element has no name attribute");
            return false;
        }

        if (typeName == null || typeName.isEmpty()) {
            logger.debug("Element '{}' has no type (inline type expected)", elementName);
            return true; // Can be valid with inline type
        }

        return true;
    }

    /**
     * Logs element parsing information.
     *
     * @param elementName the element name
     * @param typeName the type name
     * @param message additional message
     */
    public void logElementInfo(String elementName, String typeName, String message) {
        logger.debug("Element '{}' (type: {}): {}", elementName, typeName != null ? typeName : "(inline)", message);
    }
}
