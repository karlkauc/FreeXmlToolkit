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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

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

    private volatile File xsdFile;
    private volatile XsdDocumentationData xsdDocumentationData;

    /** Serializes parsing: the Xerces deferred DOM inside the schema pipeline is not thread-safe. */
    private final Object parseLock = new Object();
    /** Guards publishing and clearing the loaded schema; only ever held briefly. */
    private final Object stateLock = new Object();
    /** Bumped by {@link #clearSchema()}: a load that started earlier must not publish over the clear. */
    private long generation;

    /**
     * Creates a new mutable schema provider without any schema loaded.
     */
    public MutableXmlSchemaProvider() {
        logger.debug("MutableXmlSchemaProvider created");
    }

    /**
     * Loads an XSD schema from a file. Loads are serialized: the Xerces deferred DOM inside
     * {@link XsdDocumentationService} is not thread-safe — concurrent loads corrupted
     * it (IndexOutOfBoundsException from DeferredDocumentImpl). Can take long for large
     * schemas, so call it on a worker thread.
     *
     * <p>A fresh service instance per load is essential: {@code processXsd} mutates the
     * service's single {@link XsdDocumentationData} in place, and reusing it would
     * modify the very element map an FX-thread reader (tooltips, IntelliSense) may be
     * iterating — a re-load of a changed schema reference then threw
     * {@link java.util.ConcurrentModificationException}. Building into a fresh object
     * and publishing it atomically leaves readers on a consistent old snapshot.
     *
     * @param xsdFile the XSD file to load
     * @return true if the schema was loaded (and not cleared meanwhile), false otherwise
     */
    public boolean loadSchema(File xsdFile) {
        if (xsdFile == null || !xsdFile.exists()) {
            logger.warn("Cannot load XSD: file is null or does not exist");
            return false;
        }

        synchronized (parseLock) {
            long startGeneration;
            synchronized (stateLock) {
                startGeneration = generation;
            }
            try {
                XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();
                xsdDocumentationService.setXsdFilePath(xsdFile.getAbsolutePath());
                // IntelliSense shows a sample only for the few elements a completion lists: generating
                // all of them up front took minutes (and memory) on large, pattern-heavy schemas.
                xsdDocumentationService.setEagerSampleData(false);
                xsdDocumentationService.processXsd(true);

                File oldFile;
                XsdDocumentationData oldData;
                XsdDocumentationData newData = xsdDocumentationService.xsdDocumentationData;
                synchronized (stateLock) {
                    if (generation != startGeneration) {
                        logger.debug("Schema {} was cleared while loading; not published", xsdFile.getName());
                        return false;
                    }
                    oldFile = this.xsdFile;
                    oldData = this.xsdDocumentationData;
                    this.xsdFile = xsdFile;
                    this.xsdDocumentationData = newData;
                }

                logger.info("Loaded XSD schema: {}", xsdFile.getName());

                // Fire property change events
                pcs.firePropertyChange("xsdFile", oldFile, xsdFile);
                pcs.firePropertyChange("xsdDocumentationData", oldData, newData);
                pcs.firePropertyChange("hasSchema", oldFile != null, true);

                return true;
            } catch (Exception e) {
                logger.error("Failed to load XSD schema: {}", e.getMessage(), e);
                return false;
            }
        }
    }

    /**
     * Clears the current schema. Never waits for a load that is still parsing (it is called on
     * the FX thread); such a load then does not publish its result.
     */
    public void clearSchema() {
        File oldFile;
        XsdDocumentationData oldData;
        synchronized (stateLock) {
            generation++;
            oldFile = this.xsdFile;
            oldData = this.xsdDocumentationData;
            this.xsdFile = null;
            this.xsdDocumentationData = null;
        }

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

    /**
     * The loaded model if {@code xsd} is the schema this provider holds, read together with the file
     * under the state lock so a concurrent reload never pairs one schema's file with another's model.
     *
     * @return the model of {@code xsd}, or {@code null} if a different (or no) schema is loaded
     */
    public XsdDocumentationData getXsdDocumentationDataFor(File xsd) {
        synchronized (stateLock) {
            return xsdFile != null && xsdFile.equals(xsd) ? xsdDocumentationData : null;
        }
    }

    @Override
    public XsdDocumentationData getXsdDocumentationData() {
        return xsdDocumentationData;
    }

    @Override
    public String getXsdFilePath() {
        return xsdFile != null ? xsdFile.getAbsolutePath() : null;
    }

    /** Keys of an element map by their last path segment, for {@link #findBestMatchingElement}. */
    private record KeyIndex(XsdDocumentationData data, java.util.Map<String, java.util.List<String>> byLocalName) {
    }

    private volatile KeyIndex localNameIndex;

    /** @return the keys of {@code data}'s element map grouped by last segment, in the map's iteration order */
    private java.util.Map<String, java.util.List<String>> keysByLocalName(XsdDocumentationData data) {
        KeyIndex index = localNameIndex;
        if (index == null || index.data() != data) {
            java.util.Map<String, java.util.List<String>> byLocalName = new java.util.HashMap<>();
            for (String key : data.getExtendedXsdElementMap().keySet()) {
                String last = key.substring(key.lastIndexOf('/') + 1);
                byLocalName.computeIfAbsent(last, k -> new java.util.ArrayList<>()).add(key);
            }
            index = new KeyIndex(data, byLocalName);
            localNameIndex = index;
        }
        return index.byLocalName();
    }

    /** {@code segment.replaceAll("\\[.*\\]", "")}: drops everything from the first '[' to the last ']'. */
    private static String withoutPredicate(String segment) {
        int open = segment.indexOf('[');
        int close = segment.lastIndexOf(']');
        if (open < 0 || close < open) {
            return segment;
        }
        if (segment.indexOf('\n') >= 0 || segment.indexOf('\r') >= 0) {
            return segment.replaceAll("\\[.*\\]", ""); // '.' stops at line breaks: keep the regex semantics
        }
        return segment.substring(0, open) + segment.substring(close + 1);
    }

    @Override
    public XsdExtendedElement findBestMatchingElement(String xpath) {
        // Snapshot the volatile once: a concurrent re-load may swap the data object,
        // and all lookups below must stay on one consistent snapshot.
        XsdDocumentationData data = this.xsdDocumentationData;
        if (data == null || xpath == null) {
            return null;
        }

        var elementMap = data.getExtendedXsdElementMap();
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
            String cleaned = withoutPredicate(seg).trim();     // Remove predicates
            int colon = cleaned.lastIndexOf(':');                 // Map keys use local names
            if (colon >= 0) {
                cleaned = cleaned.substring(colon + 1);
            }
            if (!cleaned.isEmpty()) {
                cleanSegments.add(cleaned);
            }
        }

        if (cleanSegments.isEmpty()) {
            return null;
        }

        // Get the target element name (last segment)
        String targetElement = cleanSegments.get(cleanSegments.size() - 1);

        // All keys that end with the target element name, from an index built once per loaded schema:
        // scanning every key (tens of thousands for large schemas) ran on each hover, completion and
        // inspector refresh. XSD paths include compositor elements like SEQUENCE_X, CHOICE_X which we
        // need to ignore.
        java.util.List<String> matchingKeys = keysByLocalName(data).getOrDefault(targetElement, java.util.List.of());

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
     *
     * @param cleanSegments the cleaned path segments to match
     * @param mapKey        the XSD map key to compare against
     * @return the match score (higher is better)
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
     * Checks if an element name is an XSD compositor (SEQUENCE_X, CHOICE_X, ALL_X, etc.).
     *
     * @param name the element name to check
     * @return true if the name represents a compositor element
     */
    private boolean isCompositorElement(String name) {
        return name.startsWith("SEQUENCE_") ||
               name.startsWith("CHOICE_") ||
               name.startsWith("ALL_") ||
               name.startsWith("GROUP_");
    }

    /**
     * Adds a property change listener for schema changes.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
