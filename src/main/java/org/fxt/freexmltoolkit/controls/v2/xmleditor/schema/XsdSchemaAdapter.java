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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.intellisense.XsdIntegrationAdapter;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Adapter that bridges XsdDocumentationData/XsdIntegrationAdapter to XmlSchemaProvider.
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
    private final XsdIntegrationAdapter integrationAdapter;

    /**
     * Creates a new XsdSchemaAdapter.
     */
    public XsdSchemaAdapter() {
        this.integrationAdapter = new XsdIntegrationAdapter();
    }

    /**
     * Sets the XSD documentation data.
     *
     * @param data the XSD documentation data
     */
    public void setXsdDocumentationData(XsdDocumentationData data) {
        this.xsdDocumentationData = data;
        if (data != null) {
            this.integrationAdapter.setXsdDocumentationData(data);
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
            integrationAdapter.loadSchema(schemaFile);
            logger.info("Schema loaded from file: {}", schemaFile.getPath());
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
        return xsdDocumentationData != null || integrationAdapter.hasSchema();
    }

    @Override
    public List<String> getValidChildElements(String parentXPath) {
        if (!hasSchema()) {
            logger.debug("getValidChildElements: No schema available");
            return Collections.emptyList();
        }

        logger.debug("getValidChildElements: parentXPath='{}'", parentXPath);

        // Pass the full XPath to the integration adapter - it now handles both full XPaths and element names
        List<String> elements = integrationAdapter.getAvailableElements(parentXPath);
        logger.debug("getValidChildElements: Found {} elements for '{}': {}", elements.size(), parentXPath, elements);

        // If no results with full path, also try with just the element name (fallback)
        if (elements.isEmpty()) {
            String elementName = extractElementName(parentXPath);
            if (elementName != null && !elementName.equals(parentXPath)) {
                logger.debug("getValidChildElements: Trying fallback with element name '{}'", elementName);
                elements = integrationAdapter.getAvailableElements(elementName);
                logger.debug("getValidChildElements: Fallback found {} elements", elements.size());
            }
        }

        return elements;
    }

    @Override
    public List<String> getValidAttributes(String elementXPath) {
        if (!hasSchema()) {
            return Collections.emptyList();
        }

        String elementName = extractElementName(elementXPath);
        return integrationAdapter.getAvailableAttributes(elementName).stream()
                .map(attr -> attr.name)
                .toList();
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

        String elementName = extractElementName(elementXPath);
        List<XsdIntegrationAdapter.AttributeInfo> attributes = integrationAdapter.getAvailableAttributes(elementName);

        for (XsdIntegrationAdapter.AttributeInfo attr : attributes) {
            if (attr.name.equals(attributeName)) {
                return Optional.of(createAttributeTypeInfo(attr, elementXPath, attributeName));
            }
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

        String elementName = extractElementName(elementXPath);
        String doc = integrationAdapter.getElementDocumentation(elementName);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<String> getAttributeDocumentation(String elementXPath, String attributeName) {
        if (!hasSchema()) {
            return Optional.empty();
        }

        String elementName = extractElementName(elementXPath);
        String doc = integrationAdapter.getAttributeDocumentation(elementName, attributeName);
        return Optional.ofNullable(doc);
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
     * Extracts the element name from an XPath.
     */
    private String extractElementName(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return null;
        }

        // Remove leading slash
        String path = xpath.startsWith("/") ? xpath.substring(1) : xpath;

        // Get last element in path
        String[] parts = path.split("/");
        if (parts.length == 0) {
            return null;
        }

        String lastPart = parts[parts.length - 1];

        // Remove namespace prefix if present
        if (lastPart.contains(":")) {
            lastPart = lastPart.substring(lastPart.indexOf(':') + 1);
        }

        // Remove predicates like [1]
        if (lastPart.contains("[")) {
            lastPart = lastPart.substring(0, lastPart.indexOf('['));
        }

        return lastPart;
    }

    /**
     * Finds an element in the XSD documentation data.
     */
    private XsdExtendedElement findElement(String xpath) {
        if (xsdDocumentationData == null || xsdDocumentationData.getExtendedXsdElementMap() == null) {
            return null;
        }

        Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

        // Try exact match first
        if (elementMap.containsKey(xpath)) {
            return elementMap.get(xpath);
        }

        // Try with leading slash
        String normalizedPath = xpath.startsWith("/") ? xpath : "/" + xpath;
        if (elementMap.containsKey(normalizedPath)) {
            return elementMap.get(normalizedPath);
        }

        // Try to find by element name (last part of path)
        String elementName = extractElementName(xpath);
        if (elementName != null) {
            // Find first match with this element name
            for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("/" + elementName) || key.equals("/" + elementName)) {
                    return entry.getValue();
                }
            }
        }

        return null;
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
     * Creates AttributeTypeInfo from XsdIntegrationAdapter.AttributeInfo.
     */
    private AttributeTypeInfo createAttributeTypeInfo(XsdIntegrationAdapter.AttributeInfo attr,
                                                       String elementXPath, String attributeName) {
        XsdType xsdType = XsdType.fromTypeName(attr.type);

        // Get enumeration values for attribute
        String elementName = extractElementName(elementXPath);
        List<String> enumerationValues = integrationAdapter.getAttributeEnumerationValues(elementName, attributeName);

        if (!enumerationValues.isEmpty()) {
            xsdType = XsdType.ENUMERATION;
        }

        // Try to get facets from XSD documentation data
        Map<String, String> facets = new HashMap<>();
        if (xsdDocumentationData != null) {
            String attrXpath = elementXPath + "/@" + attributeName;
            XsdExtendedElement attrElement = xsdDocumentationData.getExtendedXsdElementMap().get(attrXpath);
            if (attrElement != null) {
                facets = getFacetsMap(attrElement);
            }
        }

        return new AttributeTypeInfo(
                attr.name,
                attr.type,
                xsdType,
                "required".equals(attr.use),
                enumerationValues,
                facets,
                attr.defaultValue,
                null, // No fixed value in AttributeInfo
                attr.documentation
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
    private ValidationResult validateValue(String value, String typeName, XsdType xsdType,
                                           List<String> enumerationValues, Map<String, String> facets) {
        if (value == null) {
            value = "";
        }

        // Check enumeration
        if (enumerationValues != null && !enumerationValues.isEmpty()) {
            if (!enumerationValues.contains(value)) {
                return ValidationResult.warning("Value should be one of: " + String.join(", ", enumerationValues));
            }
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
        if (xsdType == XsdType.BOOLEAN && !value.isEmpty()) {
            if (!value.equals("true") && !value.equals("false") &&
                    !value.equals("1") && !value.equals("0")) {
                return ValidationResult.warning("Value must be 'true', 'false', '1', or '0'");
            }
        }

        return ValidationResult.valid();
    }
}
