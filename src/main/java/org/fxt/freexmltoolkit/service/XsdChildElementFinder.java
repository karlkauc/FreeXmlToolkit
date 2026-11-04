/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.service;

import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getAttributeValue;
import static org.fxt.freexmltoolkit.service.XsdDomHelpers.getDirectChildElements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService.MandatoryChildInfo;
import org.w3c.dom.Node;

/**
 * Answers which child elements (mandatory or all) an element of the schema of an {@link XsdDocumentationService} has:
 * from the processed element map when available, otherwise from the global declarations' content models.
 */
final class XsdChildElementFinder {

    private static final Logger logger = LogManager.getLogger(XsdChildElementFinder.class);

    private final XsdDocumentationService service;

    XsdChildElementFinder(XsdDocumentationService service) {
        this.service = service;
    }

    /**
     * Gets mandatory child elements for a given element name based on XSD schema analysis.
     *
     * @param elementName The name of the parent element
     * @return List of mandatory child element information
     */
    List<MandatoryChildInfo> getMandatoryChildElements(String elementName) {
        return getMandatoryChildElements(elementName, null);
    }

    /**
     * Gets mandatory child elements for a given element name with XPath context for disambiguation.
     *
     * @param elementName The name of the parent element
     * @param contextPath Optional XPath context to help find the correct element (e.g., "ControlData/DataSupplier")
     * @return List of mandatory child element information
     */
    List<MandatoryChildInfo> getMandatoryChildElements(String elementName, String contextPath) {
        List<MandatoryChildInfo> mandatoryChildren = new ArrayList<>();

        try {
            // Normalize element name (strip namespace prefix if present)
            String cleanElementName = elementName;
            int colonIdx = cleanElementName.indexOf(':');
            if (colonIdx > -1 && colonIdx < cleanElementName.length() - 1) {
                cleanElementName = cleanElementName.substring(colonIdx + 1);
            }

            // First approach: Use the already processed XsdDocumentationData with XPath context
            if (service.xsdDocumentationData != null && service.xsdDocumentationData.getExtendedXsdElementMap() != null) {
                // Look for the element in the processed data, prioritizing specific XPath matches
                XsdExtendedElement targetElement = findElementByNameAndContext(cleanElementName, contextPath);

                if (targetElement != null && targetElement.hasChildren()) {
                    // Found the element, now extract its mandatory children using the correct XPath context
                    // Flatten any SEQUENCE/CHOICE/ALL containers to get actual child elements
                    List<XsdExtendedElement> flattenedChildren = flattenContainerChildren(targetElement);

                    for (XsdExtendedElement childElement : flattenedChildren) {
                        if (childElement != null && childElement.isMandatory()) {
                            // Skip attributes (elements starting with @)
                            if (childElement.getElementName().startsWith("@")) {
                                logger.debug("Skipping attribute: {}", childElement.getElementName());
                                continue;
                            }

                            // Skip container elements - they are structural, not actual XML elements
                            String childName = childElement.getElementName();
                            if (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL")) {
                                logger.debug("Skipping container element: {}", childName);
                                continue;
                            }

                            // For mandatory children, only create empty elements without recursive nesting
                            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                                    childElement.getElementName(),
                                    1, // Default minOccurs for mandatory elements
                                    1, // Default maxOccurs
                                    new ArrayList<>() // No nested children - just create empty elements
                            );
                            mandatoryChildren.add(childInfo);
                            logger.debug("Added mandatory child from processed data: {} (isAttribute=false)",
                                    childElement.getElementName());
                        }
                    }
                    if (!mandatoryChildren.isEmpty()) {
                        logger.debug("Found {} mandatory children for element '{}' using processed data with XPath context",
                                mandatoryChildren.size(), cleanElementName);
                        return mandatoryChildren;
                    }
                }
            }

            // Second approach: Try to find in global element map (legacy behavior)
            Node elementNode = service.elementMap.get(cleanElementName);
            if (elementNode == null) {
                // Fallback: try to find a global complexType with the same name and use its content model
                Node complexTypeNode = service.complexTypeMap.get(cleanElementName);
                if (complexTypeNode != null) {
                    Node contentModel = service.findContentModel(complexTypeNode, null);
                    if (contentModel != null) {
                        extractMandatoryChildren(contentModel, mandatoryChildren);
                        logger.debug("Found {} mandatory children for complexType '{}' (no global element)", mandatoryChildren.size(), cleanElementName);
                        return mandatoryChildren;
                    }
                }
                logger.debug("Element '{}' not found in schema (after normalization)", cleanElementName);
                return mandatoryChildren;
            }

            // Get the type definition for the element
            Node typeDefinition = service.findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = service.findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractMandatoryChildren(contentModel, mandatoryChildren);
            }

            logger.debug("Found {} mandatory children for element '{}'", mandatoryChildren.size(), cleanElementName);

        } catch (Exception e) {
            logger.error("Error getting mandatory children for element '{}': {}", elementName, e.getMessage(), e);
        }

        return mandatoryChildren;
    }

    /**
     * Flattens container children (SEQUENCE, CHOICE, ALL) to get actual element children.
     * Container elements are structural and should not appear as actual XML elements.
     *
     * @param parentElement The parent element whose children should be flattened
     * @return List of actual child elements (not containers)
     */
    private List<XsdExtendedElement> flattenContainerChildren(XsdExtendedElement parentElement) {
        List<XsdExtendedElement> result = new ArrayList<>();

        if (parentElement == null || !parentElement.hasChildren()) {
            return result;
        }

        for (String childXPath : parentElement.getChildren()) {
            XsdExtendedElement childElement = service.xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
            if (childElement == null) {
                continue;
            }

            String childName = childElement.getElementName();
            if (childName == null) {
                continue;
            }

            // Check if this is a container element (SEQUENCE, CHOICE, ALL)
            if (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL")) {
                // Recursively flatten container children
                result.addAll(flattenContainerChildren(childElement));
            } else {
                // Regular element - add it directly
                result.add(childElement);
            }
        }

        return result;
    }

    /**
     * Finds an element by name and optional context path, prioritizing exact XPath matches.
     * This helps distinguish between multiple elements with the same name in different contexts.
     *
     * @param elementName The name of the element to find
     * @param contextPath Optional context path to help disambiguate (e.g. "ControlData/DataSupplier")
     * @return The best matching XsdExtendedElement or null if not found
     */
    private XsdExtendedElement findElementByNameAndContext(String elementName, String contextPath) {
        if (service.xsdDocumentationData == null || service.xsdDocumentationData.getExtendedXsdElementMap() == null) {
            return null;
        }

        XsdExtendedElement bestMatch = null;
        int bestScore = -1;

        for (Map.Entry<String, XsdExtendedElement> entry : service.xsdDocumentationData.getExtendedXsdElementMap().entrySet()) {
            String xpath = entry.getKey();
            XsdExtendedElement element = entry.getValue();

            if (elementName.equals(element.getElementName())) {
                int score = calculateContextScore(xpath, contextPath);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = element;
                }
            }
        }

        if (bestMatch != null) {
            logger.debug("Found best match for element '{}': xpath='{}' (score={})",
                    elementName, bestMatch.getCurrentXpath(), bestScore);
        }

        return bestMatch;
    }

    /**
     * Calculates a context score for XPath matching.
     * Higher scores indicate better matches based on context.
     */
    private int calculateContextScore(String xpath, String contextPath) {
        if (xpath == null) {
            return 0;
        }

        // Base score - prefer shorter, more specific paths
        int score = 100 - (xpath.split("/").length * 5);

        // Boost score for root-level elements
        if (xpath.startsWith("/") && xpath.indexOf('/', 1) == -1) {
            score += 50;
        }

        // If context is provided, significantly boost score for exact matches
        if (contextPath != null && !contextPath.isEmpty()) {
            // Exact context match gets highest score
            if (xpath.contains(contextPath)) {
                score += 200; // Strong boost for context match

                // Extra bonus if the context path is at the end (more specific)
                if (xpath.endsWith("/" + contextPath.substring(contextPath.lastIndexOf('/') + 1))) {
                    score += 100;
                }
            }

            // Penalty for paths that don't match the expected context
            // This helps avoid picking wrong elements with same name
            String[] contextParts = contextPath.split("/");
            String[] xpathParts = xpath.split("/");

            boolean hasContextMismatch = false;
            for (String contextPart : contextParts) {
                boolean found = false;
                for (String xpathPart : xpathParts) {
                    if (contextPart.equals(xpathPart)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    hasContextMismatch = true;
                    break;
                }
            }

            if (hasContextMismatch) {
                score -= 150; // Strong penalty for context mismatch
            }
        }

        return Math.max(0, score);
    }

    /**
     * Gets mandatory child elements for a specific XPath, ensuring we use the correct context.
     * This method looks up children directly by their parent's XPath.
     *
     * @param parentXPath The XPath of the parent element
     * @return List of mandatory child element information
     */
    /**
     * Gets all child elements (mandatory and optional) for a given element name.
     * This method is similar to getMandatoryChildElements but returns all children regardless of minOccurs.
     *
     * @param elementName The name of the parent element
     * @return List of all child element information
     */
    List<MandatoryChildInfo> getAllChildElements(String elementName) {
        List<MandatoryChildInfo> allChildren = new ArrayList<>();

        try {
            // Normalize element name (strip namespace prefix if present)
            String cleanElementName = elementName;
            int colonIdx = cleanElementName.indexOf(':');
            if (colonIdx > -1 && colonIdx < cleanElementName.length() - 1) {
                cleanElementName = cleanElementName.substring(colonIdx + 1);
            }

            // First approach: Use the already processed XsdDocumentationData
            if (service.xsdDocumentationData != null && service.xsdDocumentationData.getExtendedXsdElementMap() != null) {
                // Look for the element in the processed data
                for (Map.Entry<String, XsdExtendedElement> entry : service.xsdDocumentationData.getExtendedXsdElementMap().entrySet()) {
                    XsdExtendedElement element = entry.getValue();
                    if (cleanElementName.equals(element.getElementName()) && element.hasChildren()) {
                        // Found the element, now extract all its children (mandatory and optional)
                        // Flatten any SEQUENCE/CHOICE/ALL containers to get actual child elements
                        List<XsdExtendedElement> flattenedChildren = flattenContainerChildren(element);

                        for (XsdExtendedElement childElement : flattenedChildren) {
                            if (childElement == null) {
                                continue;
                            }

                            String childName = childElement.getElementName();
                            // Skip container elements - they are structural, not actual XML elements
                            if (childName != null && (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL"))) {
                                continue;
                            }

                            // Check if this child has its own children
                            List<MandatoryChildInfo> nestedChildren = getAllChildElements(childElement.getElementName());

                            // Parse minOccurs from current node if available
                            int minOccurs = 1; // Default
                            if (childElement.getCurrentNode() != null) {
                                String minOccursStr = getAttributeValue(childElement.getCurrentNode(), "minOccurs", "1");
                                try {
                                    minOccurs = Integer.parseInt(minOccursStr);
                                } catch (NumberFormatException e) {
                                    minOccurs = 1;
                                }
                            }

                            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                                    childElement.getElementName().startsWith("@")
                                            ? childElement.getElementName().substring(1) // Remove @ for attributes
                                            : childElement.getElementName(),
                                    minOccurs,
                                    1, // Default maxOccurs
                                    nestedChildren
                            );
                            allChildren.add(childInfo);
                            logger.debug("Added child from processed data: {} (minOccurs={}, hasChildren={})",
                                    childElement.getElementName(), minOccurs, !nestedChildren.isEmpty());
                        }
                        if (!allChildren.isEmpty()) {
                            logger.debug("Found {} child elements for element '{}' using processed data",
                                    allChildren.size(), cleanElementName);
                            return allChildren;
                        }
                    }
                }
            }

            // Second approach: Try to find in global element map (legacy behavior)
            Node elementNode = service.elementMap.get(cleanElementName);
            if (elementNode == null) {
                // Fallback: try to find a global complexType with the same name and use its content model
                Node complexTypeNode = service.complexTypeMap.get(cleanElementName);
                if (complexTypeNode != null) {
                    Node contentModel = service.findContentModel(complexTypeNode, null);
                    if (contentModel != null) {
                        extractAllChildren(contentModel, allChildren);
                        logger.debug("Found {} child elements for complexType '{}' (no global element)", allChildren.size(), cleanElementName);
                        return allChildren;
                    }
                }
                logger.debug("Element '{}' not found in schema (after normalization)", cleanElementName);
                return allChildren;
            }

            // Get the type definition for the element
            Node typeDefinition = service.findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = service.findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractAllChildren(contentModel, allChildren);
            }

            logger.debug("Found {} child elements for element '{}'", allChildren.size(), cleanElementName);

        } catch (Exception e) {
            logger.error("Error getting all child elements for element '{}': {}", elementName, e.getMessage(), e);
        }

        return allChildren;
    }

    /**
     * Recursively extracts mandatory child elements from XSD content model (sequence, choice, etc.).
     */
    private void extractMandatoryChildren(Node contentNode, List<MandatoryChildInfo> mandatoryChildren) {
        if (contentNode == null) {
            return;
        }

        String localName = contentNode.getLocalName();

        if ("sequence".equals(localName) || "all".equals(localName)) {
            // For sequence and all, process all child elements
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    processMandatoryElement(child, mandatoryChildren);
                } else if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    // Recursively process nested compositors
                    extractMandatoryChildren(child, mandatoryChildren);
                }
            }
        } else if ("choice".equals(localName)) {
            // For choice, we need to handle differently - usually only one child is mandatory
            // For now, we'll add the first mandatory child from the choice
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName()) && isMandatoryElement(child)) {
                    processMandatoryElement(child, mandatoryChildren);
                    break; // Only take first mandatory element from choice
                }
            }
        } else if ("extension".equals(localName)) {
            // Handle extension - process base type and then current content
            String baseType = getAttributeValue(contentNode, "base");
            if (baseType != null) {
                Node baseTypeNode = service.findGlobalType(baseType, contentNode);
                if (baseTypeNode != null && !service.derivesFromItself(baseTypeNode)) {
                    Node baseContentModel = service.findContentModel(baseTypeNode, null);
                    if (baseContentModel != null) {
                        extractMandatoryChildren(baseContentModel, mandatoryChildren);
                    }
                }
            }

            // Process current extension content
            for (Node child : getDirectChildElements(contentNode)) {
                if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    extractMandatoryChildren(child, mandatoryChildren);
                }
            }
        }
    }

    /**
     * Recursively extracts all child elements (mandatory and optional) from XSD content model.
     * This is similar to extractMandatoryChildren but includes all elements regardless of minOccurs.
     */
    private void extractAllChildren(Node contentNode, List<MandatoryChildInfo> allChildren) {
        if (contentNode == null) {
            return;
        }

        String localName = contentNode.getLocalName();

        if ("sequence".equals(localName) || "all".equals(localName)) {
            // For sequence and all, process all child elements
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    processAnyElement(child, allChildren);  // Process all elements, not just mandatory
                } else if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    // Recursively process nested compositors
                    extractAllChildren(child, allChildren);
                }
            }
        } else if ("choice".equals(localName)) {
            // For choice, include all options (not just first mandatory)
            for (Node child : getDirectChildElements(contentNode)) {
                if ("element".equals(child.getLocalName())) {
                    processAnyElement(child, allChildren);
                }
            }
        } else if ("extension".equals(localName)) {
            // Handle extension - process base type and then current content
            String baseType = getAttributeValue(contentNode, "base");
            if (baseType != null) {
                Node baseTypeNode = service.findGlobalType(baseType, contentNode);
                if (baseTypeNode != null && !service.derivesFromItself(baseTypeNode)) {
                    Node baseContentModel = service.findContentModel(baseTypeNode, null);
                    if (baseContentModel != null) {
                        extractAllChildren(baseContentModel, allChildren);
                    }
                }
            }

            // Process current extension content
            for (Node child : getDirectChildElements(contentNode)) {
                if ("sequence".equals(child.getLocalName()) || "choice".equals(child.getLocalName()) || "all".equals(child.getLocalName())) {
                    extractAllChildren(child, allChildren);
                }
            }
        }
    }

    /**
     * Processes a single element to determine if it's mandatory and extract its info.
     */
    private void processMandatoryElement(Node elementNode, List<MandatoryChildInfo> mandatoryChildren) {
        if (!isMandatoryElement(elementNode)) {
            return;
        }

        String childName = getAttributeValue(elementNode, "name");
        if (childName == null) {
            // Handle element reference
            childName = getAttributeValue(elementNode, "ref");
            if (childName != null && childName.contains(":")) {
                // Remove namespace prefix for simplicity
                childName = childName.substring(childName.lastIndexOf(":") + 1);
            }
        }

        if (childName != null) {
            // Get minOccurs and maxOccurs
            String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
            String maxOccurs = getAttributeValue(elementNode, "maxOccurs", "1");

            // Check if this element has mandatory children recursively
            List<MandatoryChildInfo> nestedChildren = new ArrayList<>();
            Node typeDefinition = service.findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = service.findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractMandatoryChildren(contentModel, nestedChildren);
            }

            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                    childName,
                    Integer.parseInt(minOccurs),
                    maxOccurs.equals("unbounded") ? Integer.MAX_VALUE : Integer.parseInt(maxOccurs),
                    nestedChildren
            );

            mandatoryChildren.add(childInfo);
            logger.debug("Added mandatory child element: {} (minOccurs={}, maxOccurs={}, hasChildren={})",
                    childName, minOccurs, maxOccurs, !nestedChildren.isEmpty());
        }
    }

    /**
     * Processes a single element (mandatory or optional) and extracts its info.
     * This is similar to processMandatoryElement but processes all elements regardless of minOccurs.
     */
    private void processAnyElement(Node elementNode, List<MandatoryChildInfo> allChildren) {
        String childName = getAttributeValue(elementNode, "name");
        if (childName == null) {
            // Handle element reference
            childName = getAttributeValue(elementNode, "ref");
            if (childName != null && childName.contains(":")) {
                // Remove namespace prefix for simplicity
                childName = childName.substring(childName.lastIndexOf(":") + 1);
            }
        }

        if (childName != null) {
            // Get minOccurs and maxOccurs
            String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
            String maxOccurs = getAttributeValue(elementNode, "maxOccurs", "1");

            // Check if this element has children recursively (use getAllChildElements for consistency)
            List<MandatoryChildInfo> nestedChildren = new ArrayList<>();
            Node typeDefinition = service.findTypeDefinition(elementNode, getAttributeValue(elementNode, "type"));
            Node contentModel = service.findContentModel(elementNode, typeDefinition);

            if (contentModel != null) {
                extractAllChildren(contentModel, nestedChildren);
            }

            MandatoryChildInfo childInfo = new MandatoryChildInfo(
                    childName,
                    Integer.parseInt(minOccurs),
                    maxOccurs.equals("unbounded") ? Integer.MAX_VALUE : Integer.parseInt(maxOccurs),
                    nestedChildren
            );

            allChildren.add(childInfo);
            logger.debug("Added child element: {} (minOccurs={}, maxOccurs={}, hasChildren={})",
                    childName, minOccurs, maxOccurs, !nestedChildren.isEmpty());
        }
    }

    /**
     * Checks if an element is mandatory based on its minOccurs attribute.
     */
    private boolean isMandatoryElement(Node elementNode) {
        String minOccurs = getAttributeValue(elementNode, "minOccurs", "1");
        return Integer.parseInt(minOccurs) >= 1;
    }
}
