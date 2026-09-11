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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

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

    /** Per KEY or UNIQUE constraint: the value keyrefs used before the key had one; the key's next value. */
    private final Map<String, String> reservedKeyValues = new HashMap<>();

    /** Elements a KEY selector selects that cannot carry one of its fields, see {@link #isKeylessSelection}. */
    private final Set<String> keylessSelections = new HashSet<>();

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
                List<String> selectedXpaths = resolvePaths(elementXpath, selector, elementMap);
                if (selectedXpaths.isEmpty()) {
                    logger.debug("Could not resolve selector '{}' for constraint '{}' from '{}'",
                            selector, constraint.getName(), elementXpath);
                    continue;
                }

                // Resolve each field XPath relative to every selected element
                for (String selectedXpath : selectedXpaths) {
                    for (String field : fields) {
                        List<String> fieldXpaths = resolvePaths(selectedXpath, field, elementMap);
                        if (fieldXpaths.isEmpty()) {
                            logger.debug("Could not resolve field '{}' for constraint '{}' from selector '{}'",
                                    field, constraint.getName(), selectedXpath);
                            if (constraint.getType() == IdentityConstraint.Type.KEY) {
                                keylessSelections.add(selectedXpath);
                            }
                        }
                        for (String fieldXpath : fieldXpaths) {
                            constrainedFields.put(fieldXpath, new ConstraintFieldInfo(
                                    constraint.getName(),
                                    constraint.getType(),
                                    constraint.getRefer()
                            ));
                            logger.debug("Mapped constraint field: {} -> {} ({})",
                                    fieldXpath, constraint.getName(), constraint.getType());
                        }
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
    /**
     * Whether a KEY constraint selects the element but none of its declarations holds one of the key's fields: every
     * instance of it violates the key. XTCE {@code messageNameKey} selects {@code MessageSet/*}, which includes the
     * optional {@code LongDescription} without {@code @name}; a sample leaves such an optional element out.
     */
    public boolean isKeylessSelection(String elementXpath) {
        return keylessSelections.contains(elementXpath);
    }

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
        // No key value yet (Garmin lists CourseNameRef in Folders before the Courses): every such keyref shares one
        // reserved value, which the referenced key takes as its first value
        if (referredConstraint != null && baseSampleData != null && !baseSampleData.isEmpty()) {
            return reservedKeyValues.computeIfAbsent(referredConstraint, name -> baseSampleData);
        }
        return generateUniqueConstraintValue(info.constraintName, baseSampleData, null);
    }

    private String generateUniqueConstraintValue(String constraintName, String baseSampleData, XsdExtendedElement element) {
        String reserved = reservedKeyValues.remove(constraintName);
        if (reserved != null) {
            counters.merge(constraintName, 1, Integer::sum);
            generatedValues.computeIfAbsent(constraintName, k -> new ArrayList<>()).add(reserved);
            return reserved;
        }
        int counter = counters.getOrDefault(constraintName, 0) + 1;
        counters.put(constraintName, counter);

        String uniqueValue;

        // Check if the element has enumeration values - cycle through them instead of appending suffixes
        List<String> enumerations = getEnumerations(element);
        if (enumerations != null && !enumerations.isEmpty()) {
            // Cycle through enumeration values for uniqueness
            uniqueValue = enumerations.get((counter - 1) % enumerations.size());
        } else if (hasPattern(element)) {
            // A suffix would violate the pattern: a typed value is incremented if it still matches, a string value is
            // sampled again (XTCE NameType names every container); the base value stays when nothing distinct fits
            String base = (baseSampleData != null && !baseSampleData.isEmpty())
                    ? baseSampleData : constraintName + "_" + counter;
            uniqueValue = counter == 1 ? base : distinctPatternValue(constraintName, base, counter, element);
        } else if (baseSampleData == null || baseSampleData.isEmpty()) {
            uniqueValue = constraintName + "_" + counter;
        } else if (incrementedTypedValue(baseSampleData, counter - 1) != null) {
            // Date, time, date-time and decimal keys keep their type (Garmin Activity Id is an xsd:dateTime)
            uniqueValue = incrementedTypedValue(baseSampleData, counter - 1);
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
        if ((enumerations == null || enumerations.isEmpty()) && !hasPattern(element)
                && element != null && element.getRestrictionInfo() != null) {
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
    /**
     * Resolves a selector or field XPath of the restricted identity-constraint syntax against the element map:
     * alternatives ({@code a|b}), a leading {@code ./} or {@code .//} (descendants), name tests with or without a
     * prefix (the map is keyed by local names, element references by their QName), {@code *} and {@code @attribute}.
     * Structural containers (SEQUENCE, CHOICE, ALL) in the map are skipped.
     *
     * @return the XPaths of the element map the path selects, in map order; empty if none
     */
    private List<String> resolvePaths(String baseXpath, String path, Map<String, XsdExtendedElement> elementMap) {
        Set<String> result = new LinkedHashSet<>();
        if (baseXpath == null || path == null) {
            return List.of();
        }
        for (String alternative : path.split("\\|")) {
            String steps = alternative.trim();
            boolean descendants = steps.startsWith(".//");
            if (descendants) {
                steps = steps.substring(3);
            } else if (steps.startsWith("./")) {
                steps = steps.substring(2);
            }
            if (steps.isEmpty() || ".".equals(steps)) {
                if (elementMap.containsKey(baseXpath)) {
                    result.add(baseXpath);
                }
                continue;
            }
            String[] segments = steps.split("/");
            if (descendants) {
                String prefix = baseXpath + "/";
                for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
                    if (entry.getKey().startsWith(prefix) && nameMatches(entry.getValue(), segments[0])) {
                        resolveSegments(entry.getKey(), segments, 1, elementMap, result);
                    }
                }
            } else {
                resolveSegments(baseXpath, segments, 0, elementMap, result);
            }
        }
        return new ArrayList<>(result);
    }

    private void resolveSegments(String currentXpath, String[] segments, int index,
                                 Map<String, XsdExtendedElement> elementMap, Set<String> result) {
        if (index >= segments.length) {
            if (elementMap.containsKey(currentXpath)) {
                result.add(currentXpath);
            }
            return;
        }
        String segment = segments[index].trim();
        // Direct concatenation, for maps whose parents do not list their children
        String direct = currentXpath + "/" + localName(segment);
        if (!"*".equals(segment) && elementMap.containsKey(direct)) {
            resolveSegments(direct, segments, index + 1, elementMap, result);
        }
        XsdExtendedElement current = elementMap.get(currentXpath);
        if (current == null || current.getChildren() == null) {
            return;
        }
        for (String childXpath : current.getChildren()) {
            XsdExtendedElement child = elementMap.get(childXpath);
            if (child == null || child.getElementName() == null || childXpath.equals(direct)) {
                continue;
            }
            if (isContainer(child)) {
                resolveSegments(childXpath, segments, index, elementMap, result);
            } else if (nameMatches(child, segment)) {
                resolveSegments(childXpath, segments, index + 1, elementMap, result);
            }
        }
    }

    /** Whether a name test ({@code name}, {@code p:name}, {@code *}, {@code @name}, {@code @p:name}) matches. */
    private static boolean nameMatches(XsdExtendedElement element, String segment) {
        String name = element.getElementName();
        if (name == null || isContainer(element)) {
            return false;
        }
        boolean attributeTest = segment.startsWith("@");
        if (attributeTest != name.startsWith("@")) {
            return false;
        }
        String test = localName(attributeTest ? segment.substring(1) : segment);
        String actual = localName(attributeTest ? name.substring(1) : name);
        return "*".equals(test) || test.equals(actual);
    }

    private static boolean isContainer(XsdExtendedElement element) {
        String name = element.getElementName();
        return name != null && (name.startsWith("SEQUENCE") || name.startsWith("CHOICE") || name.startsWith("ALL"));
    }

    /** {@code p:name} → {@code name}; an attribute test keeps its {@code @}. */
    private static String localName(String step) {
        if (step.startsWith("@")) {
            return "@" + localName(step.substring(1));
        }
        return step.substring(step.indexOf(':') + 1);
    }

    /** Attempts to sample a pattern value that the constraint has not used yet. */
    private static final int DISTINCT_PATTERN_ATTEMPTS = 20;

    /**
     * A value for the {@code counter}-th occurrence of a pattern-restricted key field that the constraint has not used
     * yet: the typed base incremented, or a new sample of a string pattern within the length facets. Falls back to
     * {@code base}.
     */
    private String distinctPatternValue(String constraintName, String base, int counter, XsdExtendedElement element) {
        List<String> used = generatedValues.getOrDefault(constraintName, List.of());
        List<java.util.regex.Pattern> patterns = new ArrayList<>();
        for (String pattern : element.getRestrictionInfo().facets().get("pattern")) {
            try {
                patterns.add(java.util.regex.Pattern.compile(pattern));
            } catch (RuntimeException e) {
                return base; // not a Java regex: the value cannot be checked
            }
        }
        java.util.function.Predicate<String> fits = value -> value != null && !used.contains(value)
                && patterns.stream().anyMatch(p -> p.matcher(value).matches());
        String incremented = incrementedTypedValue(base, counter - 1);
        if (incremented != null) {
            return fits.test(incremented) ? incremented : base;
        }
        if (!isStringBase(element.getRestrictionInfo().base())) {
            return base;
        }
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        int length = intFacet(facets, "length", -1);
        int minLength = length >= 0 ? length : intFacet(facets, "minLength", 1);
        int maxLength = length >= 0 ? length : intFacet(facets, "maxLength", Math.max(minLength, 1) + 16);
        try {
            BoundedPatternSampler sampler = new BoundedPatternSampler(
                    element.getRestrictionInfo().facets().get("pattern").getFirst(),
                    java.util.concurrent.ThreadLocalRandom.current());
            for (int attempt = 0; attempt < DISTINCT_PATTERN_ATTEMPTS; attempt++) {
                String candidate = sampler.sample(minLength, maxLength);
                if (fits.test(candidate)) {
                    return candidate;
                }
            }
        } catch (RuntimeException e) {
            // an unsupported pattern: keep the base value
        }
        return base;
    }

    /** Whether a restriction base is a string type, whose pattern values may be sampled freely. */
    private static boolean isStringBase(String base) {
        String local = base == null ? "" : base.substring(base.indexOf(':') + 1);
        return List.of("string", "normalizedString", "token", "Name", "NCName", "NMTOKEN", "ID", "IDREF", "language",
                "anyURI").contains(local);
    }

    private static int intFacet(Map<String, List<String>> facets, String name, int fallback) {
        List<String> values = facets.get(name);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(values.getFirst().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * {@code value} advanced by {@code steps} in its own lexical space, or {@code null} if it is no date-time
     * ({@code +steps} seconds), time (seconds), date (days) or decimal with a fraction ({@code +steps}); integers keep
     * the numeric branch.
     */
    static String incrementedTypedValue(String value, int steps) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.matches("-?\\d{4,}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?")) {
                int zone = zoneStart(trimmed, trimmed.indexOf('T'));
                String local = trimmed.substring(0, zone);
                return java.time.LocalDateTime.parse(local).plusSeconds(steps).format(DATE_TIME)
                        + trimmed.substring(zone);
            }
            if (trimmed.matches("\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?")) {
                int zone = zoneStart(trimmed, 0);
                return java.time.LocalTime.parse(trimmed.substring(0, zone)).plusSeconds(steps).format(TIME)
                        + trimmed.substring(zone);
            }
            if (trimmed.matches("-?\\d{4,}-\\d{2}-\\d{2}(Z|[+-]\\d{2}:\\d{2})?")) {
                int zone = trimmed.length() > 10 ? 10 : trimmed.length();
                return java.time.LocalDate.parse(trimmed.substring(0, zone)).plusDays(steps) + trimmed.substring(zone);
            }
            if (trimmed.matches("[+-]?\\d*\\.\\d+")) {
                return new java.math.BigDecimal(trimmed).add(java.math.BigDecimal.valueOf(steps)).toPlainString();
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** Lexical date-time and time with seconds: {@code LocalDateTime.toString()} drops {@code :00} seconds. */
    private static final java.time.format.DateTimeFormatter DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final java.time.format.DateTimeFormatter TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Index where the time zone of a lexical time or date-time starts (its length if there is none). */
    private static int zoneStart(String value, int from) {
        int z = value.indexOf('Z', from);
        if (z >= 0) {
            return z;
        }
        int plus = value.indexOf('+', from);
        int minus = value.indexOf('-', value.indexOf(':', from));
        int zone = plus >= 0 ? plus : minus;
        return zone >= 0 ? zone : value.length();
    }

    private static boolean hasPattern(XsdExtendedElement element) {
        if (element == null || element.getRestrictionInfo() == null) {
            return false;
        }
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        if (facets == null) {
            return false;
        }
        List<String> patterns = facets.get("pattern");
        return patterns != null && !patterns.isEmpty();
    }

    private static List<String> getEnumerations(XsdExtendedElement element) {
        if (element == null || element.getRestrictionInfo() == null) {
            return null;
        }
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        if (facets == null) {
            return null;
        }
        List<String> enums = facets.get("enumeration");
        return (enums != null && !enums.isEmpty()) ? enums : null;
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int getMaxLength(XsdExtendedElement.RestrictionInfo restrictionInfo) {
        if (restrictionInfo == null || restrictionInfo.facets() == null) {
            return -1;
        }

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
