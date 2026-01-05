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

package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacets;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class for facets and patterns functionality in XsdPropertiesPanel.
 *
 * <p>Manages facet extraction, validation, and display.</p>
 *
 * @since 2.0
 */
public class XsdPropertiesPanelFacetsHelper {
    private static final Logger logger = LogManager.getLogger(XsdPropertiesPanelFacetsHelper.class);

    /**
     * Gets applicable facets for a given datatype.
     *
     * @param datatype the datatype string
     * @return set of applicable facet types
     */
    public java.util.Set<XsdFacetType> getApplicableFacets(String datatype) {
        if (datatype == null || datatype.isEmpty()) {
            return java.util.Collections.emptySet();
        }

        try {
            return XsdDatatypeFacets.getApplicableFacets(datatype);
        } catch (Exception e) {
            logger.warn("Could not get applicable facets for datatype: {}", datatype, e);
            return java.util.Collections.emptySet();
        }
    }

    /**
     * Extracts patterns from a restriction.
     *
     * @param restriction the restriction object
     * @return list of pattern strings
     */
    public List<String> extractPatterns(XsdRestriction restriction) {
        return extractFacetValues(restriction, XsdFacetType.PATTERN, "patterns");
    }

    /**
     * Extracts enumerations from a restriction.
     *
     * @param restriction the restriction object
     * @return list of enumeration strings
     */
    public List<String> extractEnumerations(XsdRestriction restriction) {
        return extractFacetValues(restriction, XsdFacetType.ENUMERATION, "enumerations");
    }

    /**
     * Extracts assertions from a restriction.
     *
     * @param restriction the restriction object
     * @return list of assertion strings
     */
    public List<String> extractAssertions(XsdRestriction restriction) {
        return extractFacetValues(restriction, XsdFacetType.ASSERTION, "assertions");
    }

    /**
     * Extracts facet values of a specific type from a restriction.
     * Optimized to reduce collection allocations.
     *
     * @param restriction the restriction object
     * @param facetType the type of facet to extract
     * @param facetName the name for logging
     * @return list of facet values, or empty list if none found
     */
    private List<String> extractFacetValues(XsdRestriction restriction, XsdFacetType facetType, String facetName) {
        if (restriction == null || restriction.getChildren() == null) {
            return java.util.Collections.emptyList();
        }

        java.util.List<String> values = new ArrayList<>();
        for (var child : restriction.getChildren()) {
            if (child instanceof XsdFacet facet && facet.getFacetType() == facetType) {
                String value = facet.getValue();
                if (value != null && !value.isEmpty()) {
                    values.add(value);
                }
            }
        }

        logger.debug("Extracted {} {} from restriction", values.size(), facetName);
        return values.isEmpty() ? java.util.Collections.emptyList() : values;
    }

    /**
     * Gets the restriction from an element or simpleType.
     *
     * @param modelObject the model object
     * @return the restriction or null
     */
    public XsdRestriction getRestriction(Object modelObject) {
        // Note: XsdElement.getType() returns a String (type name), not the actual type object
        // This method would need more context (like access to the schema root) to resolve the type
        // For now, return null as the restriction would need to be resolved through the schema tree
        return null;
    }

    /**
     * Validates a pattern regex.
     *
     * @param pattern the pattern to validate
     * @return true if valid regex
     */
    public boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            logger.warn("Pattern is empty");
            return false;
        }

        try {
            java.util.regex.Pattern.compile(pattern);
            return true;
        } catch (java.util.regex.PatternSyntaxException e) {
            logger.warn("Invalid pattern: {}", pattern, e);
            return false;
        }
    }

    /**
     * Validates an enumeration value.
     *
     * @param enumValue the enumeration value
     * @return true if valid
     */
    public boolean isValidEnumeration(String enumValue) {
        if (enumValue == null || enumValue.trim().isEmpty()) {
            logger.warn("Enumeration value is empty");
            return false;
        }
        return true;
    }

    /**
     * Gets facet label for display.
     *
     * @param facetType the facet type
     * @return display label
     */
    public String getFacetLabel(XsdFacetType facetType) {
        if (facetType == null) {
            return "Unknown";
        }

        return switch (facetType) {
            case LENGTH -> "Length";
            case MIN_LENGTH -> "Min Length";
            case MAX_LENGTH -> "Max Length";
            case PATTERN -> "Pattern";
            case ENUMERATION -> "Enumeration";
            case WHITE_SPACE -> "White Space";
            case MIN_INCLUSIVE -> "Min Inclusive";
            case MAX_INCLUSIVE -> "Max Inclusive";
            case MIN_EXCLUSIVE -> "Min Exclusive";
            case MAX_EXCLUSIVE -> "Max Exclusive";
            case TOTAL_DIGITS -> "Total Digits";
            case FRACTION_DIGITS -> "Fraction Digits";
            case ASSERTION -> "Assertion";
            case EXPLICIT_TIMEZONE -> "Explicit Timezone";
        };
    }
}
