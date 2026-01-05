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
 * Helper class for parsing XSD structure elements.
 *
 * <p>Handles parsing of Sequence, Choice, All, Group, and AttributeGroup elements.</p>
 *
 * @since 2.0
 */
public class XsdStructureParsingHelper {
    private static final Logger logger = LogManager.getLogger(XsdStructureParsingHelper.class);

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /**
     * Checks if an element is a Sequence compositor.
     *
     * @param element the element to check
     * @return true if element is a Sequence
     */
    public boolean isSequence(Element element) {
        return element != null && "sequence".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Choice compositor.
     *
     * @param element the element to check
     * @return true if element is a Choice
     */
    public boolean isChoice(Element element) {
        return element != null && "choice".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an All compositor.
     *
     * @param element the element to check
     * @return true if element is an All
     */
    public boolean isAll(Element element) {
        return element != null && "all".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Group.
     *
     * @param element the element to check
     * @return true if element is a Group
     */
    public boolean isGroup(Element element) {
        return element != null && "group".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an AttributeGroup.
     *
     * @param element the element to check
     * @return true if element is an AttributeGroup
     */
    public boolean isAttributeGroup(Element element) {
        return element != null && "attributeGroup".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an Any wildcard.
     *
     * @param element the element to check
     * @return true if element is an Any
     */
    public boolean isAny(Element element) {
        return element != null && "any".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an AnyAttribute wildcard.
     *
     * @param element the element to check
     * @return true if element is an AnyAttribute
     */
    public boolean isAnyAttribute(Element element) {
        return element != null && "anyAttribute".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Gets the first child Sequence element.
     *
     * @param parentElement the parent element
     * @return the Sequence element or null
     */
    public Element getSequenceChild(Element parentElement) {
        NodeList sequences = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "sequence");
        return sequences.getLength() > 0 ? (Element) sequences.item(0) : null;
    }

    /**
     * Gets the first child Choice element.
     *
     * @param parentElement the parent element
     * @return the Choice element or null
     */
    public Element getChoiceChild(Element parentElement) {
        NodeList choices = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "choice");
        return choices.getLength() > 0 ? (Element) choices.item(0) : null;
    }

    /**
     * Gets the first child All element.
     *
     * @param parentElement the parent element
     * @return the All element or null
     */
    public Element getAllChild(Element parentElement) {
        NodeList alls = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "all");
        return alls.getLength() > 0 ? (Element) alls.item(0) : null;
    }

    /**
     * Gets the group reference name.
     *
     * @param groupElement the group element
     * @return the group name or ref attribute
     */
    public String getGroupName(Element groupElement) {
        String name = groupElement.getAttribute("name");
        return !name.isEmpty() ? name : groupElement.getAttribute("ref");
    }

    /**
     * Gets the attribute group reference name.
     *
     * @param attrGroupElement the attributeGroup element
     * @return the attributeGroup name or ref attribute
     */
    public String getAttributeGroupName(Element attrGroupElement) {
        String name = attrGroupElement.getAttribute("name");
        return !name.isEmpty() ? name : attrGroupElement.getAttribute("ref");
    }

    /**
     * Gets the namespace attribute.
     *
     * @param element the element to check
     * @return the namespace attribute value
     */
    public String getNamespace(Element element) {
        return element.getAttribute("namespace");
    }

    /**
     * Gets the processContents attribute.
     *
     * @param element the element to check
     * @return the processContents attribute value
     */
    public String getProcessContents(Element element) {
        return element.getAttribute("processContents");
    }

    /**
     * Gets the minOccurs attribute.
     *
     * @param element the element to check
     * @return the minOccurs attribute value
     */
    public String getMinOccurs(Element element) {
        String minOccurs = element.getAttribute("minOccurs");
        return minOccurs.isEmpty() ? "1" : minOccurs;
    }

    /**
     * Gets the maxOccurs attribute.
     *
     * @param element the element to check
     * @return the maxOccurs attribute value
     */
    public String getMaxOccurs(Element element) {
        String maxOccurs = element.getAttribute("maxOccurs");
        return maxOccurs.isEmpty() ? "1" : maxOccurs;
    }

    /**
     * Counts the direct child elements (for cardinality).
     *
     * @param parentElement the parent element
     * @return the number of direct child elements (excluding text/comment nodes)
     */
    public int countDirectChildren(Element parentElement) {
        int count = 0;
        for (int i = 0; i < parentElement.getChildNodes().getLength(); i++) {
            if (parentElement.getChildNodes().item(i) instanceof Element) {
                count++;
            }
        }
        return count;
    }

    /**
     * Logs structure parsing information.
     *
     * @param structureName the structure type name (Sequence, Choice, etc.)
     * @param parentName the parent element name
     * @param message additional message
     */
    public void logStructureInfo(String structureName, String parentName, String message) {
        logger.debug("{} in '{}': {}", structureName, parentName, message);
    }
}
