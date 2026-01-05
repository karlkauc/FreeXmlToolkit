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

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for parsing XSD constraint elements.
 *
 * <p>Handles parsing of Key, KeyRef, Unique, Selector, and Field elements.</p>
 *
 * @since 2.0
 */
public class XsdConstraintParsingHelper {
    private static final Logger logger = LogManager.getLogger(XsdConstraintParsingHelper.class);

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /**
     * Checks if an element is a Key constraint.
     *
     * @param element the element to check
     * @return true if element is a Key
     */
    public boolean isKey(Element element) {
        return element != null && "key".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a KeyRef constraint.
     *
     * @param element the element to check
     * @return true if element is a KeyRef
     */
    public boolean isKeyRef(Element element) {
        return element != null && "keyref".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Unique constraint.
     *
     * @param element the element to check
     * @return true if element is a Unique
     */
    public boolean isUnique(Element element) {
        return element != null && "unique".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Selector.
     *
     * @param element the element to check
     * @return true if element is a Selector
     */
    public boolean isSelector(Element element) {
        return element != null && "selector".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Field.
     *
     * @param element the element to check
     * @return true if element is a Field
     */
    public boolean isField(Element element) {
        return element != null && "field".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Gets the constraint name.
     *
     * @param constraintElement the constraint element
     * @return the name attribute value
     */
    public String getConstraintName(Element constraintElement) {
        return constraintElement.getAttribute("name");
    }

    /**
     * Gets the refer attribute (for KeyRef).
     *
     * @param keyRefElement the keyref element
     * @return the refer attribute value
     */
    public String getRefer(Element keyRefElement) {
        return keyRefElement.getAttribute("refer");
    }

    /**
     * Gets the xpath attribute.
     *
     * @param element the element to check
     * @return the xpath attribute value
     */
    public String getXPath(Element element) {
        return element.getAttribute("xpath");
    }

    /**
     * Gets all Selector elements from a constraint.
     *
     * @param constraintElement the constraint element
     * @return list of Selector elements
     */
    public List<Element> getSelectors(Element constraintElement) {
        List<Element> selectors = new ArrayList<>();
        NodeList selectorNodes = constraintElement.getElementsByTagNameNS(XSD_NAMESPACE, "selector");
        for (int i = 0; i < selectorNodes.getLength(); i++) {
            selectors.add((Element) selectorNodes.item(i));
        }
        return selectors;
    }

    /**
     * Gets all Field elements from a constraint.
     *
     * @param constraintElement the constraint element
     * @return list of Field elements
     */
    public List<Element> getFields(Element constraintElement) {
        List<Element> fields = new ArrayList<>();
        NodeList fieldNodes = constraintElement.getElementsByTagNameNS(XSD_NAMESPACE, "field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            fields.add((Element) fieldNodes.item(i));
        }
        return fields;
    }

    /**
     * Gets the first Selector element.
     *
     * @param constraintElement the constraint element
     * @return the first Selector element or null
     */
    public Element getFirstSelector(Element constraintElement) {
        List<Element> selectors = getSelectors(constraintElement);
        return selectors.isEmpty() ? null : selectors.get(0);
    }

    /**
     * Counts Field elements in a constraint.
     *
     * @param constraintElement the constraint element
     * @return the number of Field elements
     */
    public int countFields(Element constraintElement) {
        NodeList fields = constraintElement.getElementsByTagNameNS(XSD_NAMESPACE, "field");
        return fields.getLength();
    }

    /**
     * Validates a constraint definition.
     *
     * @param constraintName the constraint name
     * @param selectorCount the number of selectors
     * @param fieldCount the number of fields
     * @return true if valid constraint definition
     */
    public boolean isValidConstraint(String constraintName, int selectorCount, int fieldCount) {
        if (constraintName == null || constraintName.isEmpty()) {
            logger.warn("Constraint has no name attribute");
            return false;
        }

        if (selectorCount == 0) {
            logger.warn("Constraint '{}' has no selector", constraintName);
            return false;
        }

        if (fieldCount == 0) {
            logger.warn("Constraint '{}' has no fields", constraintName);
            return false;
        }

        return true;
    }

    /**
     * Logs constraint parsing information.
     *
     * @param constraintType the constraint type (Key, KeyRef, Unique)
     * @param constraintName the constraint name
     * @param message additional message
     */
    public void logConstraintInfo(String constraintType, String constraintName, String message) {
        logger.debug("{} '{}': {}", constraintType, constraintName, message);
    }
}
