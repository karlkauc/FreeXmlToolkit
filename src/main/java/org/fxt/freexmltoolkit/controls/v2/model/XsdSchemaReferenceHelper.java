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
 * Helper class for parsing XSD schema-level references.
 *
 * <p>Handles parsing of Import, Include, Redefine, and Override elements.</p>
 *
 * @since 2.0
 */
public class XsdSchemaReferenceHelper {
    private static final Logger logger = LogManager.getLogger(XsdSchemaReferenceHelper.class);

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /**
     * Checks if an element is an Import element.
     *
     * @param element the element to check
     * @return true if element is an Import
     */
    public boolean isImport(Element element) {
        return element != null && "import".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an Include element.
     *
     * @param element the element to check
     * @return true if element is an Include
     */
    public boolean isInclude(Element element) {
        return element != null && "include".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is a Redefine element.
     *
     * @param element the element to check
     * @return true if element is a Redefine
     */
    public boolean isRedefine(Element element) {
        return element != null && "redefine".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an Override element (XSD 1.1).
     *
     * @param element the element to check
     * @return true if element is an Override
     */
    public boolean isOverride(Element element) {
        return element != null && "override".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Checks if an element is an Annotation element.
     *
     * @param element the element to check
     * @return true if element is an Annotation
     */
    public boolean isAnnotation(Element element) {
        return element != null && "annotation".equals(element.getLocalName()) &&
                XSD_NAMESPACE.equals(element.getNamespaceURI());
    }

    /**
     * Gets the schema location attribute.
     *
     * @param element the element to check
     * @return the schemaLocation attribute value
     */
    public String getSchemaLocation(Element element) {
        return element.getAttribute("schemaLocation");
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
     * Gets all Import elements from a schema.
     *
     * @param schemaElement the schema root element
     * @return list of Import elements
     */
    public java.util.List<Element> getImports(Element schemaElement) {
        java.util.List<Element> imports = new java.util.ArrayList<>();
        NodeList importNodes = schemaElement.getElementsByTagNameNS(XSD_NAMESPACE, "import");
        for (int i = 0; i < importNodes.getLength(); i++) {
            imports.add((Element) importNodes.item(i));
        }
        return imports;
    }

    /**
     * Gets all Include elements from a schema.
     *
     * @param schemaElement the schema root element
     * @return list of Include elements
     */
    public java.util.List<Element> getIncludes(Element schemaElement) {
        java.util.List<Element> includes = new java.util.ArrayList<>();
        NodeList includeNodes = schemaElement.getElementsByTagNameNS(XSD_NAMESPACE, "include");
        for (int i = 0; i < includeNodes.getLength(); i++) {
            includes.add((Element) includeNodes.item(i));
        }
        return includes;
    }

    /**
     * Gets all Redefine elements from a schema.
     *
     * @param schemaElement the schema root element
     * @return list of Redefine elements
     */
    public java.util.List<Element> getRedefines(Element schemaElement) {
        java.util.List<Element> redefines = new java.util.ArrayList<>();
        NodeList redefineNodes = schemaElement.getElementsByTagNameNS(XSD_NAMESPACE, "redefine");
        for (int i = 0; i < redefineNodes.getLength(); i++) {
            redefines.add((Element) redefineNodes.item(i));
        }
        return redefines;
    }

    /**
     * Gets all Override elements from a schema (XSD 1.1).
     *
     * @param schemaElement the schema root element
     * @return list of Override elements
     */
    public java.util.List<Element> getOverrides(Element schemaElement) {
        java.util.List<Element> overrides = new java.util.ArrayList<>();
        NodeList overrideNodes = schemaElement.getElementsByTagNameNS(XSD_NAMESPACE, "override");
        for (int i = 0; i < overrideNodes.getLength(); i++) {
            overrides.add((Element) overrideNodes.item(i));
        }
        return overrides;
    }

    /**
     * Gets the first Annotation element.
     *
     * @param parentElement the parent element
     * @return the first Annotation element or null
     */
    public Element getAnnotationChild(Element parentElement) {
        NodeList annotations = parentElement.getElementsByTagNameNS(XSD_NAMESPACE, "annotation");
        return annotations.getLength() > 0 ? (Element) annotations.item(0) : null;
    }

    /**
     * Validates a schema reference (import/include).
     *
     * @param referenceType the reference type (Import, Include, etc.)
     * @param schemaLocation the schema location
     * @return true if valid reference
     */
    public boolean isValidSchemaReference(String referenceType, String schemaLocation) {
        if (schemaLocation == null || schemaLocation.trim().isEmpty()) {
            logger.warn("{} element has no schemaLocation attribute", referenceType);
            return false;
        }
        return true;
    }

    /**
     * Checks if a location is a URL (vs. relative path).
     *
     * @param location the location string
     * @return true if location appears to be a URL
     */
    public boolean isUrl(String location) {
        return location != null && (location.startsWith("http://") || location.startsWith("https://") ||
                location.startsWith("file://"));
    }

    /**
     * Logs schema reference parsing information.
     *
     * @param referenceType the reference type (Import, Include, etc.)
     * @param location the schema location
     * @param message additional message
     */
    public void logReferenceInfo(String referenceType, String location, String message) {
        logger.debug("{} '{}': {}", referenceType, location, message);
    }
}
