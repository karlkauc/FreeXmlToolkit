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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing XPath expressions and creating XML nodes
 * Supports reconstruction of XML structure from XPath-based data
 */
public class XPathParser {
    private static final Logger logger = LogManager.getLogger(XPathParser.class);

    // Pattern for matching element names with optional position predicates
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("([^\\[]+)(\\[(\\d+)\\])?");

    /**
     * Represents a parsed XPath component
     */
    public static class XPathComponent {
        private String elementName;
        private int position = 1;
        private boolean isAttribute = false;
        private boolean isText = false;
        private boolean isComment = false;
        private boolean isCData = false;
        private String attributeName;

        public XPathComponent(String elementName) {
            this.elementName = elementName;
        }

        // Getters and setters
        public String getElementName() {
            return elementName;
        }

        public void setElementName(String elementName) {
            this.elementName = elementName;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public boolean isAttribute() {
            return isAttribute;
        }

        public void setIsAttribute(boolean isAttribute) {
            this.isAttribute = isAttribute;
        }

        public boolean isText() {
            return isText;
        }

        public void setIsText(boolean isText) {
            this.isText = isText;
        }

        public boolean isComment() {
            return isComment;
        }

        public void setIsComment(boolean isComment) {
            this.isComment = isComment;
        }

        public boolean isCData() {
            return isCData;
        }

        public void setIsCData(boolean isCData) {
            this.isCData = isCData;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }

        @Override
        public String toString() {
            return String.format("XPathComponent{name='%s', pos=%d, attr=%b, text=%b}",
                    elementName, position, isAttribute, isText);
        }
    }

    /**
     * Parses an XPath expression into components
     */
    public List<XPathComponent> parseXPath(String xpath) {
        List<XPathComponent> components = new ArrayList<>();

        if (xpath == null || xpath.trim().isEmpty()) {
            return components;
        }

        // Remove leading slash
        String cleanXPath = xpath.startsWith("/") ? xpath.substring(1) : xpath;

        // Split by slashes, but preserve them in special functions
        String[] parts = cleanXPath.split("/");

        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }

            XPathComponent component = parseXPathComponent(part.trim());
            components.add(component);
        }

        return components;
    }

    /**
     * Parses a single XPath component
     */
    private XPathComponent parseXPathComponent(String part) {
        // Handle special functions
        if (part.equals("text()")) {
            XPathComponent component = new XPathComponent("text");
            component.setIsText(true);
            return component;
        }

        if (part.equals("comment()")) {
            XPathComponent component = new XPathComponent("comment");
            component.setIsComment(true);
            return component;
        }

        if (part.equals("cdata()")) {
            XPathComponent component = new XPathComponent("cdata");
            component.setIsCData(true);
            return component;
        }

        // Handle attributes
        if (part.startsWith("@")) {
            XPathComponent component = new XPathComponent("");
            component.setIsAttribute(true);
            component.setAttributeName(part.substring(1));
            return component;
        }

        // Handle regular elements with optional position
        Matcher matcher = ELEMENT_PATTERN.matcher(part);
        if (matcher.matches()) {
            String elementName = matcher.group(1);
            String positionStr = matcher.group(3);

            XPathComponent component = new XPathComponent(elementName);
            if (positionStr != null) {
                try {
                    component.setPosition(Integer.parseInt(positionStr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid position in XPath component: {}", part);
                }
            }
            return component;
        }

        // Fallback - treat as simple element name
        return new XPathComponent(part);
    }

    /**
     * Creates a node in the document based on XPath
     */
    public void createNodeFromXPath(Document doc, String xpath, String value, String nodeType) {
        if (doc == null || xpath == null || xpath.trim().isEmpty()) {
            return;
        }

        try {
            List<XPathComponent> components = parseXPath(xpath);
            if (components.isEmpty()) {
                return;
            }

            Element currentElement = doc.getDocumentElement();

            // Find the root element name from the first element component
            String rootElementName = null;
            for (XPathComponent comp : components) {
                if (!comp.isAttribute() && !comp.isText() && !comp.isComment() && !comp.isCData()) {
                    rootElementName = comp.getElementName();
                    break;
                }
            }

            if (rootElementName == null) {
                logger.warn("Cannot determine root element from XPath: {}", xpath);
                return;
            }

            // If root element doesn't exist, create it
            if (currentElement == null) {
                currentElement = doc.createElement(rootElementName);
                doc.appendChild(currentElement);
            } else {
                // Verify it's the same root element
                if (!currentElement.getNodeName().equals(rootElementName)) {
                    logger.warn("XPath refers to different root element: {} vs existing {}",
                            rootElementName, currentElement.getNodeName());
                    return;
                }
            }

            // Handle simple root element with value (only if it's just the root)
            if (components.size() == 1 && value != null && !value.trim().isEmpty()) {
                currentElement.setTextContent(value);
                return;
            }

            // Remove the root component since we've processed it
            // This applies whether the root was just created or already existed
            if (components.size() > 1) {
                components = components.subList(1, components.size());
            } else {
                // If we only have the root component, we're done
                return;
            }

            // Navigate through the path, creating elements as needed
            currentElement = navigateToElement(doc, currentElement, components, value, nodeType);

        } catch (Exception e) {
            logger.error("Error creating node from XPath '{}': {}", xpath, e.getMessage(), e);
        }
    }

    /**
     * Navigates to the target element, creating intermediate elements as needed
     */
    private Element navigateToElement(Document doc, Element startElement, List<XPathComponent> components,
                                      String value, String nodeType) {
        Element currentElement = startElement;

        for (int i = 0; i < components.size(); i++) {
            XPathComponent component = components.get(i);
            boolean isLastComponent = (i == components.size() - 1);

            if (component.isAttribute()) {
                if (isLastComponent) {
                    currentElement.setAttribute(component.getAttributeName(), value != null ? value : "");
                }
                // Attributes don't change the current element
                return currentElement;

            } else if (component.isText()) {
                if (isLastComponent && value != null && !value.trim().isEmpty()) {
                    // Add text content to current element
                    Text textNode = doc.createTextNode(value);
                    currentElement.appendChild(textNode);
                }
                return currentElement;

            } else if (component.isComment()) {
                if (isLastComponent && value != null) {
                    Comment commentNode = doc.createComment(value);
                    currentElement.appendChild(commentNode);
                }
                return currentElement;

            } else if (component.isCData()) {
                if (isLastComponent && value != null) {
                    CDATASection cdataNode = doc.createCDATASection(value);
                    currentElement.appendChild(cdataNode);
                }
                return currentElement;

            } else {
                // Regular element
                Element targetElement = findOrCreateChildElement(doc, currentElement,
                        component.getElementName(), component.getPosition());

                if (isLastComponent) {
                    // Set value if this is a simple element
                    if (value != null && !value.trim().isEmpty() &&
                            !hasElementChildren(targetElement)) {
                        targetElement.setTextContent(value);
                    }
                }

                currentElement = targetElement;
            }
        }

        return currentElement;
    }

    /**
     * Finds an existing child element or creates a new one
     */
    private Element findOrCreateChildElement(Document doc, Element parent, String elementName, int position) {
        // Find all child elements with the given name
        List<Element> matchingChildren = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    child.getNodeName().equals(elementName)) {
                matchingChildren.add((Element) child);
            }
        }

        // If we need a specific position and it exists, return it
        if (position <= matchingChildren.size()) {
            return matchingChildren.get(position - 1);
        }

        // Create new elements until we reach the desired position
        while (matchingChildren.size() < position) {
            Element newElement = doc.createElement(elementName);
            parent.appendChild(newElement);
            matchingChildren.add(newElement);
        }

        return matchingChildren.get(position - 1);
    }

    /**
     * Checks if an element has child elements (not just text)
     */
    private boolean hasElementChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }
}