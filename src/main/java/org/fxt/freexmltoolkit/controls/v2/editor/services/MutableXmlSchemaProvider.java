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

package org.fxt.freexmltoolkit.controls.v2.editor.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;

/**
 * Mutable implementation of XmlSchemaProvider that can be updated at runtime.
 * This is useful for editors that need to load/change XSD schemas dynamically,
 * such as the Unified Editor's XmlUnifiedTab.
 *
 * @since 2.0
 */
public class MutableXmlSchemaProvider implements XmlSchemaProvider {

    private static final Logger logger = LogManager.getLogger(MutableXmlSchemaProvider.class);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final XsdDocumentationService xsdDocumentationService;

    private File xsdFile;
    private XsdDocumentationData xsdDocumentationData;

    /**
     * Creates a new mutable schema provider without any schema loaded.
     */
    public MutableXmlSchemaProvider() {
        this.xsdDocumentationService = new XsdDocumentationService();
        logger.debug("MutableXmlSchemaProvider created");
    }

    /**
     * Loads an XSD schema from a file.
     *
     * @param xsdFile the XSD file to load
     * @return true if the schema was loaded successfully, false otherwise
     */
    public boolean loadSchema(File xsdFile) {
        if (xsdFile == null || !xsdFile.exists()) {
            logger.warn("Cannot load XSD: file is null or does not exist");
            return false;
        }

        try {
            File oldFile = this.xsdFile;
            XsdDocumentationData oldData = this.xsdDocumentationData;

            xsdDocumentationService.setXsdFilePath(xsdFile.getAbsolutePath());
            xsdDocumentationService.processXsd(true);

            this.xsdFile = xsdFile;
            this.xsdDocumentationData = xsdDocumentationService.xsdDocumentationData;

            logger.info("Loaded XSD schema: {}", xsdFile.getName());

            // Fire property change events
            pcs.firePropertyChange("xsdFile", oldFile, xsdFile);
            pcs.firePropertyChange("xsdDocumentationData", oldData, xsdDocumentationData);
            pcs.firePropertyChange("hasSchema", oldFile != null, true);

            return true;
        } catch (Exception e) {
            logger.error("Failed to load XSD schema: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clears the current schema.
     */
    public void clearSchema() {
        File oldFile = this.xsdFile;
        XsdDocumentationData oldData = this.xsdDocumentationData;

        this.xsdFile = null;
        this.xsdDocumentationData = null;

        pcs.firePropertyChange("xsdFile", oldFile, null);
        pcs.firePropertyChange("xsdDocumentationData", oldData, null);
        pcs.firePropertyChange("hasSchema", oldFile != null, false);

        logger.debug("Schema cleared");
    }

    /**
     * Gets the current XSD file.
     *
     * @return the XSD file, or null if no schema is loaded
     */
    public File getXsdFile() {
        return xsdFile;
    }

    @Override
    public boolean hasSchema() {
        return xsdFile != null && xsdDocumentationData != null;
    }

    @Override
    public XsdDocumentationData getXsdDocumentationData() {
        return xsdDocumentationData;
    }

    @Override
    public String getXsdFilePath() {
        return xsdFile != null ? xsdFile.getAbsolutePath() : null;
    }

    @Override
    public XsdExtendedElement findBestMatchingElement(String xpath) {
        if (xsdDocumentationData == null || xpath == null) {
            return null;
        }

        var elementMap = xsdDocumentationData.getExtendedXsdElementMap();
        if (elementMap == null || elementMap.isEmpty()) {
            return null;
        }

        // Try exact match first
        if (elementMap.containsKey(xpath)) {
            return elementMap.get(xpath);
        }

        // Extract element segments from the input XPath (filtering out empty segments)
        String[] inputSegments = xpath.split("/");
        java.util.List<String> cleanSegments = new java.util.ArrayList<>();
        for (String seg : inputSegments) {
            String cleaned = seg.replaceAll("\\[.*\\]", "").trim(); // Remove predicates
            if (!cleaned.isEmpty()) {
                cleanSegments.add(cleaned);
            }
        }

        if (cleanSegments.isEmpty()) {
            return null;
        }

        // Get the target element name (last segment)
        String targetElement = cleanSegments.get(cleanSegments.size() - 1);

        // Find all keys that end with the target element name
        // XSD paths include compositor elements like SEQUENCE_X, CHOICE_X which we need to ignore
        java.util.List<String> matchingKeys = new java.util.ArrayList<>();
        for (String key : elementMap.keySet()) {
            if (key.endsWith("/" + targetElement) || key.equals(targetElement)) {
                matchingKeys.add(key);
            }
        }

        if (matchingKeys.isEmpty()) {
            logger.debug("No matching keys found for element: {}", targetElement);
            return null;
        }

        // If only one match, return it
        if (matchingKeys.size() == 1) {
            logger.debug("Single match found for '{}': {}", xpath, matchingKeys.get(0));
            return elementMap.get(matchingKeys.get(0));
        }

        // Multiple matches - find the best one by matching parent path segments
        // Score each key based on how many parent segments match (ignoring SEQUENCE_X, CHOICE_X, etc.)
        String bestKey = null;
        int bestScore = -1;

        for (String key : matchingKeys) {
            int score = calculatePathMatchScore(cleanSegments, key);
            if (score > bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }

        if (bestKey != null) {
            logger.debug("Best match for '{}': {} (score: {})", xpath, bestKey, bestScore);
            return elementMap.get(bestKey);
        }

        // Fallback: return first match
        logger.debug("Fallback match for '{}': {}", xpath, matchingKeys.get(0));
        return elementMap.get(matchingKeys.get(0));
    }

    /**
     * Calculates a match score between clean path segments and an XSD map key.
     * Higher score means better match.
     */
    private int calculatePathMatchScore(java.util.List<String> cleanSegments, String mapKey) {
        // Extract real element names from the map key (filter out SEQUENCE_X, CHOICE_X, etc.)
        String[] keyParts = mapKey.split("/");
        java.util.List<String> keyElements = new java.util.ArrayList<>();
        for (String part : keyParts) {
            if (!part.isEmpty() && !isCompositorElement(part)) {
                keyElements.add(part);
            }
        }

        // Count how many segments from cleanSegments appear in keyElements in order
        int score = 0;
        int keyIndex = 0;
        for (String segment : cleanSegments) {
            for (int i = keyIndex; i < keyElements.size(); i++) {
                if (keyElements.get(i).equals(segment)) {
                    score++;
                    keyIndex = i + 1;
                    break;
                }
            }
        }

        return score;
    }

    /**
     * Checks if an element name is an XSD compositor (SEQUENCE_X, CHOICE_X, ALL_X, etc.)
     */
    private boolean isCompositorElement(String name) {
        return name.startsWith("SEQUENCE_") ||
               name.startsWith("CHOICE_") ||
               name.startsWith("ALL_") ||
               name.startsWith("GROUP_");
    }

    /**
     * Extracts the element name from an XPath.
     */
    private String extractElementName(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return null;
        }

        // Get the last segment
        int lastSlash = xpath.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? xpath.substring(lastSlash + 1) : xpath;

        // Remove predicates
        int bracketIndex = lastSegment.indexOf('[');
        if (bracketIndex >= 0) {
            lastSegment = lastSegment.substring(0, bracketIndex);
        }

        return lastSegment.isEmpty() ? null : lastSegment;
    }

    /**
     * Adds a property change listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
