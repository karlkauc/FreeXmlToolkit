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
package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

import java.util.*;

/**
 * Tracks XSD identity constraints (xs:key, xs:unique, xs:keyref) during sample XML generation
 * and provides unique values for constrained fields.
 *
 * <p>Identity constraints define uniqueness and referential integrity rules in XSD schemas.
 * When generating sample XML with repeated elements (maxOccurs > 1), all instances would
 * normally get the same sample value, causing validation errors. This tracker ensures
 * constrained fields get unique values.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IdentityConstraintTracker tracker = new IdentityConstraintTracker();
 * tracker.scanConstraints(elementMap);
 * // During XML generation:
 * if (tracker.isConstrainedField(elementXpath)) {
 *     String value = tracker.getUniqueValue(elementXpath, baseSampleData, element);
 * }
 * }</pre>
 */
public class IdentityConstraintTracker {

    private static final Logger logger = LogManager.getLogger(IdentityConstraintTracker.class);

    /**
     * Information about a resolved constraint field, linking a concrete element XPath
     * to its constraint definition.
     */
    record ConstraintFieldInfo(
            String constraintName,
            IdentityConstraint.Type constraintType,
            String refer // only for KEYREF: name of referenced KEY/UNIQUE
    ) {
    }

    /**
     * Maps resolved element XPaths to their constraint info.
     * An element XPath like "/FundsXML4/Funds/Fund/FundStaticData/Benchmarks/Benchmark/BenchmarkID"
     * would map to the constraint "benchmarkID" of type KEY.
     */
    private final Map<String, ConstraintFieldInfo> constrainedFields = new HashMap<>();

    /**
     * Tracks generated values per constraint name for uniqueness enforcement.
     * Key: constraint name, Value: list of generated values in order.
     */
    private final Map<String, List<String>> generatedValues = new HashMap<>();

    /**
     * Counter per constraint for generating unique suffixes.
     */
    private final Map<String, Integer> counters = new HashMap<>();

    /**
     * Maps constraint names to their constraint definitions for KEYREF resolution.
     */
    private final Map<String, IdentityConstraint> constraintsByName = new HashMap<>();

    /**
     * Scans all elements in the element map for identity constraints and builds
     * a reverse index from element XPaths to constraint names.
     *
     * <p>For each constraint found, the selector and field XPaths are resolved
     * to concrete element XPaths in the element map. For example, a constraint with
     * selector "FundStaticData/Benchmarks/Benchmark" and field "BenchmarkID" on
     * element "/FundsXML4/Funds/Fund" would resolve to
     * "/FundsXML4/Funds/Fund/FundStaticData/Benchmarks/Benchmark/BenchmarkID".</p>
     *
     * @param elementMap the map of element XPaths to their extended element definitions
     */
    public void scanConstraints(Map<String, XsdExtendedElement> elementMap) {
        if (elementMap == null || elementMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
            XsdExtendedElement element = entry.getValue();
            String elementXpath = entry.getKey();

            if (element.getIdentityConstraints() == null || element.getIdentityConstraints().isEmpty()) {
                continue;
            }

            for (IdentityConstraint constraint : element.getIdentityConstraints()) {
                constraintsByName.put(constraint.getName(), constraint);

                String selector = constraint.getSelector();
                List<String> fields = constraint.getFields();

                if (selector == null || fields == null || fields.isEmpty()) {
                    logger.debug("Skipping constraint {} with missing selector or fields", constraint.getName());
                    continue;
                }

                // Resolve selector XPath relative to the element that defines the constraint.
                // The selector is a relative XPath like "FundStaticData/Benchmarks/Benchmark"
                // which resolves relative to the constraint-defining element's XPath.
                String resolvedSelectorXpath = resolveRelativePath(elementXpath, selector, elementMap);

                if (resolvedSelectorXpath == null) {
                    logger.debug("Could not resolve selector '{}' for constraint '{}' from '{}'",
                            selector, constraint.getName(), elementXpath);
                    continue;
                }

                // Resolve each field XPath relative to the resolved selector
                for (String field : fields) {
                    String resolvedFieldXpath = resolveRelativePath(resolvedSelectorXpath, field, elementMap);

                    if (resolvedFieldXpath != null) {
                        constrainedFields.put(resolvedFieldXpath, new ConstraintFieldInfo(
                                constraint.getName(),
                                constraint.getType(),
                                constraint.getRefer()
                        ));
                        logger.debug("Mapped constraint field: {} -> {} ({})",
                                resolvedFieldXpath, constraint.getName(), constraint.getType());
                    } else {
                        logger.debug("Could not resolve field '{}' for constraint '{}' from selector '{}'",
                                field, constraint.getName(), resolvedSelectorXpath);
                    }
                }
            }
        }

        logger.info("Scanned identity constraints: {} constrained fields found", constrainedFields.size());
    }

    /**
     * Checks if the given element XPath is subject to a KEY or UNIQUE constraint.
     *
     * @param elementXpath the full XPath of the element
     * @return true if the element is constrained by KEY or UNIQUE
     */
    public boolean isConstrainedField(String elementXpath) {
        ConstraintFieldInfo info = constrainedFields.get(elementXpath);
        return info != null && (info.constraintType == IdentityConstraint.Type.KEY
                || info.constraintType == IdentityConstraint.Type.UNIQUE);
    }

    /**
     * Checks if the given element XPath is subject to a KEYREF constraint.
     *
     * @param elementXpath the full XPath of the element
     * @return true if the element is constrained by KEYREF
     */
    public boolean isKeyrefField(String elementXpath) {
        ConstraintFieldInfo info = constrainedFields.get(elementXpath);
        return info != null && info.constraintType == IdentityConstraint.Type.KEYREF;
    }

    /**
     * Returns a unique value for a constrained field, or a referencing value for a KEYREF field.
     *
     * <p>For KEY/UNIQUE constraints: appends incrementing suffixes (_1, _2, etc.) to the base
     * sample data to ensure uniqueness. For numeric base values, increments the number instead.
     * Respects maxLength facets from restrictions.</p>
     *
     * <p>For KEYREF constraints: cycles through the values generated for the referenced
     * KEY/UNIQUE constraint. If no referenced values exist yet, falls back to the base value
     * with a suffix.</p>
     *
     * @param elementXpath   the full XPath of the element
     * @param baseSampleData the base sample data to make unique
     * @param element        the XSD element (for facet information)
     * @return a unique or referencing value
     */
    public String getUniqueValue(String elementXpath, String baseSampleData, XsdExtendedElement element) {
        ConstraintFieldInfo info = constrainedFields.get(elementXpath);
        if (info == null) {
            return baseSampleData;
        }

        if (info.constraintType == IdentityConstraint.Type.KEYREF) {
            return getKeyrefValue(info, baseSampleData);
        }

        // KEY or UNIQUE: generate unique value
        return generateUniqueConstraintValue(info.constraintName, baseSampleData, element);
    }

    /**
     * Checks whether this tracker has any constrained fields registered.
     *
     * @return true if at least one constrained field was found
     */
    public boolean hasConstraints() {
        return !constrainedFields.isEmpty();
    }

    // --- Private implementation ---

    private String getKeyrefValue(ConstraintFieldInfo info, String baseSampleData) {
        String referredConstraint = info.refer;
        if (referredConstraint != null) {
            // Strip namespace prefix if present (e.g., "tns:benchmarkID" -> "benchmarkID")
            if (referredConstraint.contains(":")) {
                referredConstraint = referredConstraint.substring(referredConstraint.indexOf(':') + 1);
            }

            List<String> referredValues = generatedValues.get(referredConstraint);
            if (referredValues != null && !referredValues.isEmpty()) {
                // Cycle through referred values
                int counter = counters.getOrDefault(info.constraintName, 0);
                String value = referredValues.get(counter % referredValues.size());
                counters.put(info.constraintName, counter + 1);
                return value;
            }
        }
        // Fallback: if referenced values not yet generated, use base with suffix
        return generateUniqueConstraintValue(info.constraintName, baseSampleData, null);
    }

    private String generateUniqueConstraintValue(String constraintName, String baseSampleData, XsdExtendedElement element) {
        int counter = counters.getOrDefault(constraintName, 0) + 1;
        counters.put(constraintName, counter);

        String uniqueValue;

        // Check if the element has enumeration values - cycle through them instead of appending suffixes
        List<String> enumerations = getEnumerations(element);
        if (enumerations != null && !enumerations.isEmpty()) {
            // Cycle through enumeration values for uniqueness
            uniqueValue = enumerations.get((counter - 1) % enumerations.size());
        } else if (hasPattern(element)) {
            // Element has a pattern restriction - appending suffixes would violate it.
            // Use the base value as-is (pattern validity > uniqueness constraint).
            uniqueValue = (baseSampleData != null && !baseSampleData.isEmpty())
                    ? baseSampleData : constraintName + "_" + counter;
        } else if (baseSampleData == null || baseSampleData.isEmpty()) {
            uniqueValue = constraintName + "_" + counter;
        } else if (isNumeric(baseSampleData)) {
            // For numeric values, increment the number
            try {
                long numValue = Long.parseLong(baseSampleData.trim());
                uniqueValue = String.valueOf(numValue + counter - 1);
            } catch (NumberFormatException e) {
                uniqueValue = baseSampleData + "_" + counter;
            }
        } else {
            // For string values, append suffix
            uniqueValue = baseSampleData + "_" + counter;
        }

        // Respect maxLength if available (only for non-enumeration and non-pattern values)
        if ((enumerations == null || enumerations.isEmpty()) && !hasPattern(element)) {
            if (element != null && element.getRestrictionInfo() != null) {
                int maxLength = getMaxLength(element.getRestrictionInfo());
                if (maxLength > 0 && uniqueValue.length() > maxLength) {
                    // Truncate base and re-append suffix to fit
                    String suffix = "_" + counter;
                    int availableLength = maxLength - suffix.length();
                    if (availableLength > 0 && baseSampleData != null) {
                        uniqueValue = baseSampleData.substring(0, Math.min(baseSampleData.length(), availableLength)) + suffix;
                    } else {
                        // If even the suffix doesn't fit, just use the counter
                        uniqueValue = String.valueOf(counter);
                    }
                }
            }
        }

        // Track generated value for KEYREF resolution
        generatedValues.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(uniqueValue);

        return uniqueValue;
    }

    /**
     * Resolves a relative XPath against a base element XPath by finding the matching
     * element in the element map. Skips SEQUENCE/CHOICE/ALL container nodes that appear
     * in the element map but are not part of the XSD XPath selectors.
     *
     * @param baseXpath   the XPath of the element defining the constraint
     * @param relativePath the relative XPath from the selector/field
     * @param elementMap  the element map for validation
     * @return the resolved full XPath, or null if not found
     */
    private String resolveRelativePath(String baseXpath, String relativePath, Map<String, XsdExtendedElement> elementMap) {
        if (baseXpath == null || relativePath == null) {
            return null;
        }

        // Handle attribute fields (e.g., "@id")
        if (relativePath.startsWith("@")) {
            String candidateXpath = baseXpath + "/" + relativePath;
            if (elementMap.containsKey(candidateXpath)) {
                return candidateXpath;
            }
            return null;
        }

        // Simple case: direct concatenation works
        String directPath = baseXpath + "/" + relativePath;
        if (elementMap.containsKey(directPath)) {
            return directPath;
        }

        // The relative path might need to skip SEQUENCE/CHOICE/ALL containers.
        // Split relative path into segments and try to match step by step.
        String[] segments = relativePath.split("/");
        return resolvePathSegments(baseXpath, segments, 0, elementMap);
    }

    /**
     * Recursively resolves path segments against the element map, skipping structural
     * containers (SEQUENCE, CHOICE, ALL) that exist in the XPath map but not in XSD selectors.
     */
    private String resolvePathSegments(String currentXpath, String[] segments, int segmentIndex,
                                       Map<String, XsdExtendedElement> elementMap) {
        if (segmentIndex >= segments.length) {
            // All segments resolved
            return elementMap.containsKey(currentXpath) ? currentXpath : null;
        }

        String segment = segments[segmentIndex];

        // Try direct match: currentXpath/segment
        String directPath = currentXpath + "/" + segment;
        String result = resolvePathSegments(directPath, segments, segmentIndex + 1, elementMap);
        if (result != null) {
            return result;
        }

        // Try skipping through container nodes (SEQUENCE_*, CHOICE_*, ALL_*)
        XsdExtendedElement currentElement = elementMap.get(currentXpath);
        if (currentElement != null && currentElement.getChildren() != null) {
            for (String childXpath : currentElement.getChildren()) {
                XsdExtendedElement child = elementMap.get(childXpath);
                if (child == null) continue;

                String childName = child.getElementName();
                if (childName != null && (childName.startsWith("SEQUENCE") || childName.startsWith("CHOICE") || childName.startsWith("ALL"))) {
                    // Skip container: try resolving from the container's XPath
                    result = resolvePathSegments(childXpath, segments, segmentIndex, elementMap);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

    private static boolean hasPattern(XsdExtendedElement element) {
        if (element == null || element.getRestrictionInfo() == null) return false;
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        if (facets == null) return false;
        List<String> patterns = facets.get("pattern");
        return patterns != null && !patterns.isEmpty();
    }

    private static List<String> getEnumerations(XsdExtendedElement element) {
        if (element == null || element.getRestrictionInfo() == null) return null;
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        if (facets == null) return null;
        List<String> enums = facets.get("enumeration");
        return (enums != null && !enums.isEmpty()) ? enums : null;
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Long.parseLong(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int getMaxLength(XsdExtendedElement.RestrictionInfo restrictionInfo) {
        if (restrictionInfo == null || restrictionInfo.facets() == null) return -1;

        List<String> maxLengthValues = restrictionInfo.facets().get("maxLength");
        if (maxLengthValues != null && !maxLengthValues.isEmpty()) {
            try {
                return Integer.parseInt(maxLengthValues.getFirst());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
