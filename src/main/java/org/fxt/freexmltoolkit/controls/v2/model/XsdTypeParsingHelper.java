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
 * Helper class for parsing XSD type definitions.
 *
 * <p>Handles parsing of ComplexType, SimpleType, Restriction, List, and Union elements.</p>
 *
 * @since 2.0
 */
public class XsdTypeParsingHelper {
    private static final Logger logger = LogManager.getLogger(XsdTypeParsingHelper.class);

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /**
     * Checks if an element is a ComplexType definition.
     *
     * @param element the element to check
     * @return true if element is a ComplexType
     */
    public boolean isComplexType(Element element) {
        return element != null && "complexType".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a SimpleType definition.
     *
     * @param element the element to check
     * @return true if element is a SimpleType
     */
    public boolean isSimpleType(Element element) {
        return element != null && "simpleType".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Restriction.
     *
     * @param element the element to check
     * @return true if element is a Restriction
     */
    public boolean isRestriction(Element element) {
        return element != null && "restriction".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an Extension.
     *
     * @param element the element to check
     * @return true if element is an Extension
     */
    public boolean isExtension(Element element) {
        return element != null && "extension".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a List.
     *
     * @param element the element to check
     * @return true if element is a List
     */
    public boolean isList(Element element) {
        return element != null && "list".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Union.
     *
     * @param element the element to check
     * @return true if element is a Union
     */
    public boolean isUnion(Element element) {
        return element != null && "union".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Gets the first child ComplexType element.
     *
     * @param parentElement the parent element
     * @return the ComplexType element or null
     */
    public Element getComplexTypeChild(Element parentElement) {
        NodeList complexTypes = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "complexType");
        return complexTypes.getLength() > 0 ? (Element) complexTypes.item(0) : null;
    }

    /**
     * Gets the first child SimpleType element.
     *
     * @param parentElement the parent element
     * @return the SimpleType element or null
     */
    public Element getSimpleTypeChild(Element parentElement) {
        NodeList simpleTypes = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "simpleType");
        return simpleTypes.getLength() > 0 ? (Element) simpleTypes.item(0) : null;
    }

    /**
     * Gets the first child Restriction element.
     *
     * @param parentElement the parent element
     * @return the Restriction element or null
     */
    public Element getRestrictionChild(Element parentElement) {
        NodeList restrictions = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "restriction");
        return restrictions.getLength() > 0 ? (Element) restrictions.item(0) : null;
    }

    /**
     * Gets the first child Extension element.
     *
     * @param parentElement the parent element
     * @return the Extension element or null
     */
    public Element getExtensionChild(Element parentElement) {
        NodeList extensions = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "extension");
        return extensions.getLength() > 0 ? (Element) extensions.item(0) : null;
    }

    /**
     * Gets the first child List element.
     *
     * @param parentElement the parent element
     * @return the List element or null
     */
    public Element getListChild(Element parentElement) {
        NodeList lists = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "list");
        return lists.getLength() > 0 ? (Element) lists.item(0) : null;
    }

    /**
     * Gets the first child Union element.
     *
     * @param parentElement the parent element
     * @return the Union element or null
     */
    public Element getUnionChild(Element parentElement) {
        NodeList unions = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "union");
        return unions.getLength() > 0 ? (Element) unions.item(0) : null;
    }

    /**
     * Gets the base type name from a restriction or extension element.
     *
     * @param element the restriction or extension element
     * @return the base type name or null
     */
    public String getBaseTypeName(Element element) {
        String baseAttr = element.getAttribute("base");
        if (baseAttr == null || baseAttr.isEmpty()) {
            return null;
        }

        // Handle namespace prefixes
        if (baseAttr.contains(":")) {
            String prefix = baseAttr.substring(0, baseAttr.indexOf(":"));
            String localName = baseAttr.substring(baseAttr.indexOf(":") + 1);

            // Get the namespace URI from the prefix
            String namespaceURI = element.lookupNamespaceURI(prefix);
            if (XSD_NAMESPACE.equals(namespaceURI)) {
                return "xs:" + localName;
            } else {
                return baseAttr;
            }
        }

        return baseAttr;
    }

    /**
     * Gets the item type name from a list element.
     *
     * @param listElement the list element
     * @return the item type name or null
     */
    public String getItemTypeName(Element listElement) {
        return listElement.getAttribute("itemType");
    }

    /**
     * Gets the member types from a union element.
     *
     * @param unionElement the union element
     * @return space-separated list of member types or null
     */
    public String getMemberTypes(Element unionElement) {
        return unionElement.getAttribute("memberTypes");
    }

    /**
     * Checks if a type is a built-in XSD type.
     *
     * @param typeName the type name
     * @return true if built-in type
     */
    public boolean isBuiltinType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }

        return typeName.startsWith("xs:") || typeName.startsWith("xsd:");
    }

    /**
     * Normalizes a type reference (adds xs: prefix if missing for builtin types).
     *
     * @param typeReference the type reference
     * @return normalized type reference
     */
    public String normalizeTypeReference(String typeReference) {
        if (typeReference == null || typeReference.isEmpty()) {
            return typeReference;
        }

        // Already has a prefix
        if (typeReference.contains(":")) {
            return typeReference;
        }

        // Check if it's a known built-in type (this is a simple check)
        String lowerCase = typeReference.toLowerCase();
        if (lowerCase.matches("(string|int|integer|boolean|double|float|decimal|date|time|.*type)")) {
            return "xs:" + typeReference;
        }

        return typeReference;
    }

    /**
     * Logs type parsing information.
     *
     * @param typeName the type name
     * @param baseType the base type (if any)
     * @param message additional message
     */
    public void logTypeInfo(String typeName, String baseType, String message) {
        if (baseType != null && !baseType.isEmpty()) {
            logger.debug("Type '{}' (extends {}): {}", typeName, baseType, message);
        } else {
            logger.debug("Type '{}': {}", typeName, message);
        }
    }
}
