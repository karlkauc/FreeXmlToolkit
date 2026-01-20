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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for providing XSD schema information to the XML editor.
 *
 * <p>This abstraction allows the XML editor to be schema-aware, enabling features like:</p>
 * <ul>
 *   <li>Context-sensitive element suggestions</li>
 *   <li>Type-aware value editing (enumerations, booleans, dates, etc.)</li>
 *   <li>Validation feedback</li>
 *   <li>Documentation tooltips</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public interface XmlSchemaProvider {

    /**
     * Checks if a schema is available.
     *
     * @return true if schema is loaded and available
     */
    boolean hasSchema();

    /**
     * Gets the list of valid child element names for a given parent element.
     *
     * @param parentXPath the XPath of the parent element
     * @return list of valid child element names (empty if none or no schema)
     */
    List<String> getValidChildElements(String parentXPath);

    /**
     * Gets the list of valid attribute names for an element.
     *
     * @param elementXPath the XPath of the element
     * @return list of valid attribute names
     */
    List<String> getValidAttributes(String elementXPath);

    /**
     * Gets detailed information about an element.
     *
     * @param elementXPath the XPath of the element
     * @return element info, or empty if not found
     */
    Optional<ElementTypeInfo> getElementTypeInfo(String elementXPath);

    /**
     * Gets detailed information about an attribute.
     *
     * @param elementXPath  the XPath of the element
     * @param attributeName the attribute name
     * @return attribute info, or empty if not found
     */
    Optional<AttributeTypeInfo> getAttributeTypeInfo(String elementXPath, String attributeName);

    /**
     * Gets documentation for an element.
     *
     * @param elementXPath the XPath of the element
     * @return documentation string, or empty if none
     */
    Optional<String> getElementDocumentation(String elementXPath);

    /**
     * Gets documentation for an attribute.
     *
     * @param elementXPath  the XPath of the element
     * @param attributeName the attribute name
     * @return documentation string, or empty if none
     */
    Optional<String> getAttributeDocumentation(String elementXPath, String attributeName);

    /**
     * Validates a value against the type constraints.
     *
     * @param elementXPath the XPath of the element
     * @param value        the value to validate
     * @return validation result
     */
    ValidationResult validateElementValue(String elementXPath, String value);

    /**
     * Validates an attribute value against the type constraints.
     *
     * @param elementXPath  the XPath of the element
     * @param attributeName the attribute name
     * @param value         the value to validate
     * @return validation result
     */
    ValidationResult validateAttributeValue(String elementXPath, String attributeName, String value);

    // ==================== Inner Classes ====================

    /**
     * Information about an element's type.
     * @param name The element name
     * @param typeName The name of the type
     * @param xsdType The XSD type category
     * @param isMandatory Whether the element is mandatory
     * @param minOccurs The minimum occurrences
     * @param maxOccurs The maximum occurrences
     * @param enumerationValues List of allowed values
     * @param facets Map of other facets
     * @param defaultValue The default value
     * @param fixedValue The fixed value
     * @param documentation The documentation text
     */
    record ElementTypeInfo(
            String name,
            String typeName,
            XsdType xsdType,
            boolean isMandatory,
            String minOccurs,
            String maxOccurs,
            List<String> enumerationValues,
            Map<String, String> facets,
            String defaultValue,
            String fixedValue,
            String documentation
    ) {
        /**
         * Checks if this element has enumeration values.
         */
        public boolean hasEnumeration() {
            return enumerationValues != null && !enumerationValues.isEmpty();
        }

        /**
         * Checks if this element has a fixed value.
         */
        public boolean isFixed() {
            return fixedValue != null && !fixedValue.isEmpty();
        }

        /**
         * Gets a specific facet value.
         */
        public Optional<String> getFacet(String facetName) {
            return Optional.ofNullable(facets != null ? facets.get(facetName) : null);
        }
    }

    /**
     * Information about an attribute's type.
     * @param name The attribute name
     * @param typeName The name of the type
     * @param xsdType The XSD type category
     * @param isRequired Whether the attribute is required
     * @param enumerationValues List of allowed values
     * @param facets Map of other facets
     * @param defaultValue The default value
     * @param fixedValue The fixed value
     * @param documentation The documentation text
     */
    record AttributeTypeInfo(
            String name,
            String typeName,
            XsdType xsdType,
            boolean isRequired,
            List<String> enumerationValues,
            Map<String, String> facets,
            String defaultValue,
            String fixedValue,
            String documentation
    ) {
        /**
         * Checks if this attribute has enumeration values.
         */
        public boolean hasEnumeration() {
            return enumerationValues != null && !enumerationValues.isEmpty();
        }

        /**
         * Checks if this attribute has a fixed value.
         */
        public boolean isFixed() {
            return fixedValue != null && !fixedValue.isEmpty();
        }

        /**
         * Gets a specific facet value.
         */
        public Optional<String> getFacet(String facetName) {
            return Optional.ofNullable(facets != null ? facets.get(facetName) : null);
        }
    }

    /**
     * Result of a validation operation.
     * @param isValid Whether the value is valid
     * @param errorMessage The error message (if any)
     * @param severity The severity of the validation result
     */
    record ValidationResult(
            boolean isValid,
            String errorMessage,
            ValidationSeverity severity
    ) {
        /**
         * Creates a valid result.
         */
        public static ValidationResult valid() {
            return new ValidationResult(true, null, ValidationSeverity.INFO);
        }

        /**
         * Creates a warning result.
         */
        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, ValidationSeverity.WARNING);
        }

        /**
         * Creates an error result.
         */
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, ValidationSeverity.ERROR);
        }
    }

    /**
     * Severity levels for validation.
     */
    enum ValidationSeverity {
        INFO,
        WARNING,
        ERROR
    }

    /**
     * XSD built-in types categorized for UI purposes.
     */
    enum XsdType {
        // String types
        STRING,
        NORMALIZED_STRING,
        TOKEN,

        // Boolean
        BOOLEAN,

        // Numeric types
        INTEGER,
        DECIMAL,
        FLOAT,
        DOUBLE,

        // Date/Time types
        DATE,
        TIME,
        DATE_TIME,
        DURATION,
        G_YEAR,
        G_YEAR_MONTH,
        G_MONTH,
        G_MONTH_DAY,
        G_DAY,

        // URI
        ANY_URI,

        // Other
        QNAME,
        HEX_BINARY,
        BASE64_BINARY,

        // Special
        ENUMERATION,  // When type has enumeration facet
        COMPLEX,      // Complex type
        UNKNOWN;      // Unknown or custom type

        /**
         * Determines the XsdType from a type name string.
         */
        public static XsdType fromTypeName(String typeName) {
            if (typeName == null || typeName.isEmpty()) {
                return UNKNOWN;
            }

            // Remove namespace prefix if present
            String localName = typeName.contains(":")
                    ? typeName.substring(typeName.indexOf(':') + 1)
                    : typeName;

            return switch (localName.toLowerCase()) {
                case "string" -> STRING;
                case "normalizedstring" -> NORMALIZED_STRING;
                case "token", "nmtoken", "nmtokens", "name", "ncname", "id", "idref", "idrefs", "entity", "entities",
                     "language" -> TOKEN;
                case "boolean" -> BOOLEAN;
                case "integer", "int", "long", "short", "byte",
                     "nonpositiveinteger", "negativeinteger", "nonnegativeinteger", "positiveinteger",
                     "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte" -> INTEGER;
                case "decimal" -> DECIMAL;
                case "float" -> FLOAT;
                case "double" -> DOUBLE;
                case "date" -> DATE;
                case "time" -> TIME;
                case "datetime", "datetimestamp" -> DATE_TIME;
                case "duration", "yearmonthduration", "daytimeduration" -> DURATION;
                case "gyear" -> G_YEAR;
                case "gyearmonth" -> G_YEAR_MONTH;
                case "gmonth" -> G_MONTH;
                case "gmonthday" -> G_MONTH_DAY;
                case "gday" -> G_DAY;
                case "anyuri" -> ANY_URI;
                case "qname", "notation" -> QNAME;
                case "hexbinary" -> HEX_BINARY;
                case "base64binary" -> BASE64_BINARY;
                default -> UNKNOWN;
            };
        }

        /**
         * Checks if this type is a date/time type.
         */
        public boolean isDateTimeType() {
            return this == DATE || this == TIME || this == DATE_TIME || this == DURATION
                    || this == G_YEAR || this == G_YEAR_MONTH || this == G_MONTH
                    || this == G_MONTH_DAY || this == G_DAY;
        }

        /**
         * Checks if this type is numeric.
         */
        public boolean isNumericType() {
            return this == INTEGER || this == DECIMAL || this == FLOAT || this == DOUBLE;
        }

        /**
         * Checks if this type is a string type.
         */
        public boolean isStringType() {
            return this == STRING || this == NORMALIZED_STRING || this == TOKEN;
        }
    }
}
