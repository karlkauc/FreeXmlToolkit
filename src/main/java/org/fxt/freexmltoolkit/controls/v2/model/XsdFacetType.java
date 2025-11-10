package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Enumeration of all XSD facet types used in simple type restrictions.
 *
 * @since 2.0
 */
public enum XsdFacetType {
    /**
     * Defines the minimum length of a string (xs:minLength).
     */
    MIN_LENGTH("minLength"),

    /**
     * Defines the maximum length of a string (xs:maxLength).
     */
    MAX_LENGTH("maxLength"),

    /**
     * Defines the exact length of a string (xs:length).
     */
    LENGTH("length"),

    /**
     * Defines a regular expression pattern (xs:pattern).
     */
    PATTERN("pattern"),

    /**
     * Defines an enumeration value (xs:enumeration).
     */
    ENUMERATION("enumeration"),

    /**
     * Defines whitespace handling (xs:whiteSpace).
     */
    WHITE_SPACE("whiteSpace"),

    /**
     * Defines the maximum inclusive value (xs:maxInclusive).
     */
    MAX_INCLUSIVE("maxInclusive"),

    /**
     * Defines the maximum exclusive value (xs:maxExclusive).
     */
    MAX_EXCLUSIVE("maxExclusive"),

    /**
     * Defines the minimum inclusive value (xs:minInclusive).
     */
    MIN_INCLUSIVE("minInclusive"),

    /**
     * Defines the minimum exclusive value (xs:minExclusive).
     */
    MIN_EXCLUSIVE("minExclusive"),

    /**
     * Defines the total number of digits (xs:totalDigits).
     */
    TOTAL_DIGITS("totalDigits"),

    /**
     * Defines the number of fraction digits (xs:fractionDigits).
     */
    FRACTION_DIGITS("fractionDigits"),

    /**
     * XSD 1.1: Defines an assertion constraint (xs:assertion).
     */
    ASSERTION("assertion"),

    /**
     * XSD 1.1: Defines an explicit timezone (xs:explicitTimezone).
     */
    EXPLICIT_TIMEZONE("explicitTimezone");

    private final String xmlName;

    XsdFacetType(String xmlName) {
        this.xmlName = xmlName;
    }

    /**
     * Gets the XML name for this facet type.
     *
     * @return the XML name (e.g., "minLength")
     */
    public String getXmlName() {
        return xmlName;
    }

    /**
     * Finds a facet type by XML name.
     *
     * @param xmlName the XML name
     * @return the facet type, or null if not found
     */
    public static XsdFacetType fromXmlName(String xmlName) {
        for (XsdFacetType type : values()) {
            if (type.xmlName.equals(xmlName)) {
                return type;
            }
        }
        return null;
    }
}
