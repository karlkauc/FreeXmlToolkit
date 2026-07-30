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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * Adapter that bridges XsdDocumentationData to XmlSchemaProvider.
 *
 * <p>This class provides a clean interface for the XML editor to access XSD schema
 * information without depending on the internal implementation details.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XsdSchemaAdapter implements XmlSchemaProvider {

    private static final Logger logger = LogManager.getLogger(XsdSchemaAdapter.class);

    private XsdDocumentationData xsdDocumentationData;

    /**
     * Creates a new XsdSchemaAdapter.
     */
    public XsdSchemaAdapter() {
    }

    /**
     * Sets the XSD documentation data.
     *
     * @param data the XSD documentation data
     */
    public void setXsdDocumentationData(XsdDocumentationData data) {
        this.xsdDocumentationData = data;
        if (data != null) {
            logger.info("Schema loaded with {} elements",
                    data.getExtendedXsdElementMap() != null ? data.getExtendedXsdElementMap().size() : 0);
        }
    }

    /**
     * Loads schema from a file.
     *
     * @param schemaFile the XSD file
     */
    public void loadSchema(File schemaFile) {
        if (schemaFile != null && schemaFile.exists()) {
            logger.info("Schema file specified: {}", schemaFile.getPath());
        }
    }

    /**
     * Gets the underlying XsdDocumentationData.
     *
     * @return the XSD documentation data, or null if not set
     */
    public XsdDocumentationData getXsdDocumentationData() {
        return xsdDocumentationData;
    }

    // ==================== XmlSchemaProvider Implementation ====================

    @Override
    public boolean hasSchema() {
        return xsdDocumentationData != null;
    }

    @Override
    public List<String> getValidChildElements(String parentXPath) {
        if (!hasSchema()) {
            logger.debug("getValidChildElements: No schema available");
            return Collections.emptyList();
        }

        logger.debug("getValidChildElements: parentXPath='{}' - returning empty (XsdIntegrationAdapter removed)", parentXPath);
        return Collections.emptyList();
    }

    @Override
    public List<String> getValidAttributes(String elementXPath) {
        if (!hasSchema()) {
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    @Override
    public Optional<ElementTypeInfo> getElementTypeInfo(String elementXPath) {
        if (!hasSchema() || xsdDocumentationData == null) {
            return Optional.empty();
        }

        // Try to find element in the XSD element map
        XsdExtendedElement element = findElement(elementXPath);
        if (element == null) {
            return Optional.empty();
        }

        return Optional.of(createElementTypeInfo(element));
    }

    @Override
    public Optional<AttributeTypeInfo> getAttributeTypeInfo(String elementXPath, String attributeName) {
        if (!hasSchema()) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public Optional<String> getElementDocumentation(String elementXPath) {
        if (!hasSchema()) {
            return Optional.empty();
        }

        XsdExtendedElement element = findElement(elementXPath);
        if (element != null) {
            String doc = element.getDocumentationAsHtml();
            if (doc != null && !doc.isEmpty()) {
                return Optional.of(doc);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<String> getAttributeDocumentation(String elementXPath, String attributeName) {
        if (!hasSchema()) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public ValidationResult validateElementValue(String elementXPath, String value) {
        if (!hasSchema()) {
            return ValidationResult.valid();
        }

        Optional<ElementTypeInfo> typeInfo = getElementTypeInfo(elementXPath);
        if (typeInfo.isEmpty()) {
            return ValidationResult.valid();
        }

        return validateValue(value, typeInfo.get().typeName(), typeInfo.get().xsdType(),
                typeInfo.get().enumerationValues(), typeInfo.get().facets());
    }

    @Override
    public ValidationResult validateAttributeValue(String elementXPath, String attributeName, String value) {
        if (!hasSchema()) {
            return ValidationResult.valid();
        }

        Optional<AttributeTypeInfo> typeInfo = getAttributeTypeInfo(elementXPath, attributeName);
        if (typeInfo.isEmpty()) {
            return ValidationResult.valid();
        }

        return validateValue(value, typeInfo.get().typeName(), typeInfo.get().xsdType(),
                typeInfo.get().enumerationValues(), typeInfo.get().facets());
    }

    // ==================== Helper Methods ====================

    /**
     * Finds an element in the XSD documentation data.
     */
    private XsdExtendedElement findElement(String xpath) {
        return resolveElement(xpath);
    }

    /**
     * Resolves an XML-instance XPath to its schema declaration using the FULL path — never a
     * name-only guess, which conflated distinct same-named elements (e.g. the many "Amount"
     * declarations of FundsXML) and showed the documentation of the wrong one.
     *
     * <p>The map is keyed by full, predicate-free XPaths. An instance XPath may carry positional
     * predicates ({@code [n]}) for repeated siblings — those are stripped. Map keys may
     * additionally contain synthetic compositor segments the documentation model inserts for
     * nested compositors ({@code …/TotalValue/CHOICE_123/Amount}) which the instance path does
     * not have; the step-wise walk descends through them transparently.</p>
     *
     * @param xpath a positional instance XPath (e.g. {@code /FundsXML4/Funds/Fund[2]/…/Amount})
     * @return the matching schema element, or {@code null} when the path cannot be resolved
     */
    public XsdExtendedElement resolveElement(String xpath) {
        if (xpath == null || xsdDocumentationData == null
                || xsdDocumentationData.getExtendedXsdElementMap() == null) {
            return null;
        }

        Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

        String strippedPath = xpath.replaceAll("\\[\\d+\\]", "");
        String normalizedPath = strippedPath.startsWith("/") ? strippedPath : "/" + strippedPath;

        // Exact match first (the common case: no compositor segments in the key).
        XsdExtendedElement exact = elementMap.get(normalizedPath);
        if (exact != null) {
            return exact;
        }

        // Step-wise walk from the root, descending through synthetic compositor
        // segments (SEQUENCE_n/CHOICE_n/ALL_n/GROUP_n) that only exist in map keys.
        String[] steps = normalizedPath.substring(1).split("/");
        if (steps.length == 0 || steps[0].isBlank()) {
            return null;
        }
        String rootKey = "/" + localName(steps[0]);
        XsdExtendedElement current = elementMap.get(rootKey);
        String currentKey = rootKey;
        for (int i = 1; current != null && i < steps.length; i++) {
            currentKey = findChildKey(current, elementMap, localName(steps[i]), new java.util.HashSet<>());
            current = currentKey == null ? null : elementMap.get(currentKey);
        }
        if (current != null) {
            return current;
        }

        // Last resort: a local-name match, but ONLY when it is unambiguous across the whole
        // schema. With several same-named declarations, guessing one showed the wrong
        // element's documentation — return null instead of guessing.
        String local = localName(steps[steps.length - 1]);
        XsdExtendedElement match = null;
        int count = 0;
        for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
            if (entry.getKey().endsWith("/" + local)) {
                match = entry.getValue();
                if (++count > 1) {
                    return null; // ambiguous → do not guess
                }
            }
        }
        return count == 1 ? match : null;
    }

    /**
     * Finds the map key of the child with the given local name, descending transparently
     * through synthetic compositor children (whose element names are SEQUENCE/CHOICE/ALL/GROUP).
     */
    private String findChildKey(XsdExtendedElement parent, Map<String, XsdExtendedElement> elementMap,
                                String childName, java.util.Set<String> visited) {
        if (parent == null || parent.getChildren() == null || childName == null) {
            return null;
        }
        for (String childKey : parent.getChildren()) {
            if (childKey == null || childKey.contains("@") || !visited.add(childKey)) {
                continue; // attributes / cycles
            }
            String childLocal = localName(childKey.substring(childKey.lastIndexOf('/') + 1));
            if (childName.equals(childLocal)) {
                return childKey;
            }
            if (isCompositorName(childLocal)) {
                String viaCompositor = findChildKey(elementMap.get(childKey), elementMap, childName, visited);
                if (viaCompositor != null) {
                    return viaCompositor;
                }
            }
        }
        return null;
    }

    /** @return true for the synthetic compositor node names the documentation model inserts. */
    private static boolean isCompositorName(String name) {
        return name != null && name.matches("(?i)(SEQUENCE|CHOICE|ALL|GROUP)(_\\d+)?");
    }

    /** @return {@code step} without a namespace prefix. */
    private static String localName(String step) {
        int colon = step.indexOf(':');
        return colon >= 0 ? step.substring(colon + 1) : step;
    }

    /**
     * Creates ElementTypeInfo from XsdExtendedElement.
     */
    private ElementTypeInfo createElementTypeInfo(XsdExtendedElement element) {
        String typeName = element.getElementType();
        XsdType xsdType = determineXsdType(element);

        // Get enumeration values
        List<String> enumerationValues = getEnumerationValues(element);

        // Get facets
        Map<String, String> facets = getFacetsMap(element);

        // Get cardinality - use cardinalityNode if available
        String minOccurs = "1";
        String maxOccurs = "1";
        if (element.getCardinalityNode() != null) {
            minOccurs = getAttributeValue(element.getCardinalityNode(), "minOccurs", "1");
            maxOccurs = getAttributeValue(element.getCardinalityNode(), "maxOccurs", "1");
        } else if (element.getCurrentNode() != null) {
            minOccurs = getAttributeValue(element.getCurrentNode(), "minOccurs", "1");
            maxOccurs = getAttributeValue(element.getCurrentNode(), "maxOccurs", "1");
        }

        // Get default and fixed values
        String defaultValue = element.getCurrentNode() != null
                ? getAttributeValue(element.getCurrentNode(), "default", null)
                : null;
        String fixedValue = element.getCurrentNode() != null
                ? getAttributeValue(element.getCurrentNode(), "fixed", null)
                : null;

        return new ElementTypeInfo(
                element.getElementName(),
                typeName,
                xsdType,
                element.isMandatory(),
                minOccurs,
                maxOccurs,
                enumerationValues,
                facets,
                defaultValue,
                fixedValue,
                element.getDocumentationAsHtml()
        );
    }

    /**
     * Determines the XsdType from an XsdExtendedElement.
     */
    private XsdType determineXsdType(XsdExtendedElement element) {
        // Check for enumeration first
        List<String> enumValues = getEnumerationValues(element);
        if (!enumValues.isEmpty()) {
            return XsdType.ENUMERATION;
        }

        // Check type name
        String typeName = element.getElementType();
        if (typeName != null) {
            XsdType type = XsdType.fromTypeName(typeName);
            if (type != XsdType.UNKNOWN) {
                return type;
            }
        }

        // Check base type in restriction
        if (element.getRestrictionInfo() != null && element.getRestrictionInfo().base() != null) {
            XsdType type = XsdType.fromTypeName(element.getRestrictionInfo().base());
            if (type != XsdType.UNKNOWN) {
                return type;
            }
        }

        return XsdType.STRING; // Default to string
    }

    /**
     * Gets enumeration values from an XsdExtendedElement.
     */
    private List<String> getEnumerationValues(XsdExtendedElement element) {
        if (element.getRestrictionInfo() != null && element.getRestrictionInfo().facets() != null) {
            List<String> values = element.getRestrictionInfo().facets().get("enumeration");
            if (values != null) {
                return new ArrayList<>(values);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Gets facets as a simple Map from XsdExtendedElement.
     */
    private Map<String, String> getFacetsMap(XsdExtendedElement element) {
        Map<String, String> result = new HashMap<>();

        if (element.getRestrictionInfo() != null && element.getRestrictionInfo().facets() != null) {
            Map<String, List<String>> facets = element.getRestrictionInfo().facets();

            for (Map.Entry<String, List<String>> entry : facets.entrySet()) {
                String facetName = entry.getKey();
                List<String> values = entry.getValue();

                // Skip enumeration as it's handled separately
                if ("enumeration".equals(facetName)) {
                    continue;
                }

                // For most facets, join values (usually just one)
                if (values != null && !values.isEmpty()) {
                    result.put(facetName, String.join(", ", values));
                }
            }
        }

        return result;
    }

    /**
     * Gets an attribute value from a DOM node.
     */
    private String getAttributeValue(org.w3c.dom.Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) {
            return defaultValue;
        }
        org.w3c.dom.Node attrNode = node.getAttributes().getNamedItem(attrName);
        return attrNode != null ? attrNode.getNodeValue() : defaultValue;
    }

    /**
     * Validates a value against type constraints.
     */
    private ValidationResult validateValue(String value, String _typeName, XsdType xsdType,
                                           List<String> enumerationValues, Map<String, String> facets) {
        if (value == null) {
            value = "";
        }

        // Check enumeration
        if (enumerationValues != null && !enumerationValues.isEmpty() && !enumerationValues.contains(value)) {
            return ValidationResult.warning("Value should be one of: " + String.join(", ", enumerationValues));
        }

        // Check pattern
        if (facets != null && facets.containsKey("pattern")) {
            String pattern = facets.get("pattern");
            try {
                if (!Pattern.matches(pattern, value)) {
                    return ValidationResult.warning("Value does not match pattern: " + pattern);
                }
            } catch (PatternSyntaxException e) {
                logger.warn("Invalid pattern in schema: {}", pattern);
            }
        }

        // Check length constraints
        if (facets != null) {
            // minLength
            if (facets.containsKey("minLength")) {
                try {
                    int minLength = Integer.parseInt(facets.get("minLength"));
                    if (value.length() < minLength) {
                        return ValidationResult.warning("Value must be at least " + minLength + " characters");
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // maxLength
            if (facets.containsKey("maxLength")) {
                try {
                    int maxLength = Integer.parseInt(facets.get("maxLength"));
                    if (value.length() > maxLength) {
                        return ValidationResult.warning("Value must be at most " + maxLength + " characters");
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // length
            if (facets.containsKey("length")) {
                try {
                    int length = Integer.parseInt(facets.get("length"));
                    if (value.length() != length) {
                        return ValidationResult.warning("Value must be exactly " + length + " characters");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Type-specific validation
        if (xsdType.isNumericType() && !value.isEmpty()) {
            try {
                double numValue = Double.parseDouble(value);

                // Check numeric bounds
                if (facets != null) {
                    if (facets.containsKey("minInclusive")) {
                        double min = Double.parseDouble(facets.get("minInclusive"));
                        if (numValue < min) {
                            return ValidationResult.warning("Value must be >= " + min);
                        }
                    }
                    if (facets.containsKey("maxInclusive")) {
                        double max = Double.parseDouble(facets.get("maxInclusive"));
                        if (numValue > max) {
                            return ValidationResult.warning("Value must be <= " + max);
                        }
                    }
                    if (facets.containsKey("minExclusive")) {
                        double min = Double.parseDouble(facets.get("minExclusive"));
                        if (numValue <= min) {
                            return ValidationResult.warning("Value must be > " + min);
                        }
                    }
                    if (facets.containsKey("maxExclusive")) {
                        double max = Double.parseDouble(facets.get("maxExclusive"));
                        if (numValue >= max) {
                            return ValidationResult.warning("Value must be < " + max);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                return ValidationResult.warning("Value must be a valid number");
            }
        }

        // Boolean validation
        if (xsdType == XsdType.BOOLEAN && !value.isEmpty()
                && !value.equals("true") && !value.equals("false")
                && !value.equals("1") && !value.equals("0")) {
            return ValidationResult.warning("Value must be 'true', 'false', '1', or '0'");
        }

        return ValidationResult.valid();
    }
}
