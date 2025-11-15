package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.*;

/**
 * Defines applicable facets for each XSD 1.1 datatype.
 * This class provides mapping between datatypes and their allowed facets,
 * including information about fixed facet values.
 *
 * @since 2.0
 */
public class XsdDatatypeFacets {

    /**
     * Gets the applicable facets for a given XSD datatype.
     *
     * @param datatype the datatype (e.g., "xs:string", "string", "xs:decimal")
     * @return set of applicable facet types
     */
    public static Set<XsdFacetType> getApplicableFacets(String datatype) {
        if (datatype == null || datatype.isEmpty()) {
            return Collections.emptySet();
        }

        // Remove namespace prefix if present
        String typeName = removeNamespacePrefix(datatype);

        return switch (typeName) {
            // String types
            case "string" -> STRING_FACETS;
            case "normalizedString", "token", "language", "Name", "NCName",
                 "ID", "IDREF", "ENTITY", "NMTOKEN" -> STRING_FACETS;

            // Decimal and integer types
            case "decimal" -> DECIMAL_FACETS;
            case "integer", "positiveInteger", "negativeInteger",
                 "nonPositiveInteger", "nonNegativeInteger",
                 "long", "int", "short", "byte",
                 "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte" -> INTEGER_FACETS;

            // Float types
            case "float", "double" -> FLOAT_FACETS;

            // Date/time types
            case "dateTime", "dateTimeStamp" -> DATETIME_FACETS;
            case "date", "time", "gYear", "gYearMonth", "gMonth", "gMonthDay", "gDay" -> DATETIME_FACETS;

            // Duration types
            case "duration", "yearMonthDuration", "dayTimeDuration" -> DURATION_FACETS;

            // Binary types
            case "hexBinary", "base64Binary" -> BINARY_FACETS;

            // Boolean
            case "boolean" -> BOOLEAN_FACETS;

            // URI
            case "anyURI" -> URI_FACETS;

            // QName and NOTATION
            case "QName", "NOTATION" -> QNAME_FACETS;

            default -> Collections.emptySet();
        };
    }

    /**
     * Checks if a facet has a fixed value for a given datatype.
     * Fixed facets should be displayed as disabled/read-only.
     *
     * @param datatype the datatype
     * @param facetType the facet type
     * @return true if this facet is fixed for this datatype
     */
    public static boolean isFacetFixed(String datatype, XsdFacetType facetType) {
        if (datatype == null || datatype.isEmpty()) {
            return false;
        }

        String typeName = removeNamespacePrefix(datatype);

        // Check for fractionDigits fixed to 0 (integer types)
        if (facetType == XsdFacetType.FRACTION_DIGITS) {
            return switch (typeName) {
                case "integer", "positiveInteger", "negativeInteger",
                     "nonPositiveInteger", "nonNegativeInteger",
                     "long", "int", "short", "byte",
                     "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte" -> true;
                default -> false;
            };
        }

        // Check for whiteSpace fixed values
        if (facetType == XsdFacetType.WHITE_SPACE) {
            return switch (typeName) {
                // normalizedString: fixed to "replace"
                case "normalizedString" -> true;
                // token and derivatives: fixed to "collapse"
                case "token", "language", "Name", "NCName", "ID", "IDREF", "ENTITY", "NMTOKEN",
                     "decimal", "integer", "positiveInteger", "negativeInteger",
                     "nonPositiveInteger", "nonNegativeInteger",
                     "long", "int", "short", "byte",
                     "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                     "float", "double", "boolean",
                     "dateTime", "dateTimeStamp", "date", "time", "duration",
                     "yearMonthDuration", "dayTimeDuration",
                     "gYear", "gYearMonth", "gMonth", "gMonthDay", "gDay",
                     "hexBinary", "base64Binary", "anyURI", "QName", "NOTATION" -> true;
                default -> false;
            };
        }

        // Check for explicitTimezone fixed to "required" (dateTimeStamp)
        if (facetType == XsdFacetType.EXPLICIT_TIMEZONE) {
            return "dateTimeStamp".equals(typeName);
        }

        // Check for fixed minInclusive/maxInclusive values for built-in numeric types
        if (facetType == XsdFacetType.MIN_INCLUSIVE) {
            return switch (typeName) {
                case "long", "int", "short", "byte",
                     "nonNegativeInteger", "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                     "positiveInteger" -> true;
                default -> false;
            };
        }

        if (facetType == XsdFacetType.MAX_INCLUSIVE) {
            return switch (typeName) {
                case "nonPositiveInteger", "negativeInteger",
                     "long", "int", "short", "byte",
                     "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte" -> true;
                default -> false;
            };
        }

        return false;
    }

    /**
     * Gets the fixed value for a facet on a given datatype.
     *
     * @param datatype the datatype
     * @param facetType the facet type
     * @return the fixed value, or null if not fixed
     */
    public static String getFixedFacetValue(String datatype, XsdFacetType facetType) {
        if (!isFacetFixed(datatype, facetType)) {
            return null;
        }

        String typeName = removeNamespacePrefix(datatype);

        if (facetType == XsdFacetType.WHITE_SPACE) {
            return switch (typeName) {
                case "normalizedString" -> "replace";
                default -> "collapse";
            };
        }

        if (facetType == XsdFacetType.FRACTION_DIGITS) {
            return "0";
        }

        if (facetType == XsdFacetType.EXPLICIT_TIMEZONE) {
            return "required";
        }

        // Fixed minInclusive values for built-in numeric types
        if (facetType == XsdFacetType.MIN_INCLUSIVE) {
            return switch (typeName) {
                case "long" -> "-9223372036854775808";
                case "int" -> "-2147483648";
                case "short" -> "-32768";
                case "byte" -> "-128";
                case "nonNegativeInteger", "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte" -> "0";
                case "positiveInteger" -> "1";
                default -> null;
            };
        }

        // Fixed maxInclusive values for built-in numeric types
        if (facetType == XsdFacetType.MAX_INCLUSIVE) {
            return switch (typeName) {
                case "nonPositiveInteger" -> "0";
                case "negativeInteger" -> "-1";
                case "long" -> "9223372036854775807";
                case "int" -> "2147483647";
                case "short" -> "32767";
                case "byte" -> "127";
                case "unsignedLong" -> "18446744073709551615";
                case "unsignedInt" -> "4294967295";
                case "unsignedShort" -> "65535";
                case "unsignedByte" -> "255";
                default -> null;
            };
        }

        return null;
    }

    /**
     * Removes the namespace prefix from a datatype name.
     *
     * @param datatype the datatype (e.g., "xs:string")
     * @return the datatype without prefix (e.g., "string")
     */
    private static String removeNamespacePrefix(String datatype) {
        if (datatype.contains(":")) {
            return datatype.substring(datatype.indexOf(":") + 1);
        }
        return datatype;
    }

    // Facet sets for different datatype categories

    private static final Set<XsdFacetType> STRING_FACETS = Set.of(
            XsdFacetType.LENGTH,
            XsdFacetType.MIN_LENGTH,
            XsdFacetType.MAX_LENGTH,
            XsdFacetType.PATTERN,
            XsdFacetType.ENUMERATION,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> DECIMAL_FACETS = Set.of(
            XsdFacetType.TOTAL_DIGITS,
            XsdFacetType.FRACTION_DIGITS,
            XsdFacetType.PATTERN,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ENUMERATION,
            XsdFacetType.MAX_INCLUSIVE,
            XsdFacetType.MAX_EXCLUSIVE,
            XsdFacetType.MIN_INCLUSIVE,
            XsdFacetType.MIN_EXCLUSIVE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> INTEGER_FACETS = Set.of(
            XsdFacetType.TOTAL_DIGITS,
            XsdFacetType.FRACTION_DIGITS, // fixed to 0
            XsdFacetType.PATTERN,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ENUMERATION,
            XsdFacetType.MAX_INCLUSIVE,
            XsdFacetType.MAX_EXCLUSIVE,
            XsdFacetType.MIN_INCLUSIVE,
            XsdFacetType.MIN_EXCLUSIVE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> FLOAT_FACETS = Set.of(
            XsdFacetType.PATTERN,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ENUMERATION,
            XsdFacetType.MAX_INCLUSIVE,
            XsdFacetType.MAX_EXCLUSIVE,
            XsdFacetType.MIN_INCLUSIVE,
            XsdFacetType.MIN_EXCLUSIVE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> DATETIME_FACETS = Set.of(
            XsdFacetType.PATTERN,
            XsdFacetType.ENUMERATION,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.MAX_INCLUSIVE,
            XsdFacetType.MAX_EXCLUSIVE,
            XsdFacetType.MIN_INCLUSIVE,
            XsdFacetType.MIN_EXCLUSIVE,
            XsdFacetType.EXPLICIT_TIMEZONE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> DURATION_FACETS = Set.of(
            XsdFacetType.PATTERN,
            XsdFacetType.ENUMERATION,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.MAX_INCLUSIVE,
            XsdFacetType.MAX_EXCLUSIVE,
            XsdFacetType.MIN_INCLUSIVE,
            XsdFacetType.MIN_EXCLUSIVE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> BINARY_FACETS = Set.of(
            XsdFacetType.LENGTH,
            XsdFacetType.MIN_LENGTH,
            XsdFacetType.MAX_LENGTH,
            XsdFacetType.PATTERN,
            XsdFacetType.ENUMERATION,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> BOOLEAN_FACETS = Set.of(
            XsdFacetType.PATTERN,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> URI_FACETS = Set.of(
            XsdFacetType.LENGTH,
            XsdFacetType.MIN_LENGTH,
            XsdFacetType.MAX_LENGTH,
            XsdFacetType.PATTERN,
            XsdFacetType.ENUMERATION,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ASSERTION
    );

    private static final Set<XsdFacetType> QNAME_FACETS = Set.of(
            XsdFacetType.LENGTH,
            XsdFacetType.MIN_LENGTH,
            XsdFacetType.MAX_LENGTH,
            XsdFacetType.PATTERN,
            XsdFacetType.ENUMERATION,
            XsdFacetType.WHITE_SPACE,
            XsdFacetType.ASSERTION
    );
}
