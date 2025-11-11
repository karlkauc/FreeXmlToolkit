package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdFacetType enum.
 *
 * @since 2.0
 */
class XsdFacetTypeTest {

    // ========== Enum Values Tests ==========

    @Test
    @DisplayName("enum should have all 14 facet types")
    void testEnumHasAllTypes() {
        XsdFacetType[] values = XsdFacetType.values();
        assertEquals(14, values.length, "Should have 14 facet types");
    }

    @Test
    @DisplayName("enum should contain MIN_LENGTH")
    void testEnumContainsMinLength() {
        assertNotNull(XsdFacetType.MIN_LENGTH);
        assertEquals("minLength", XsdFacetType.MIN_LENGTH.getXmlName());
    }

    @Test
    @DisplayName("enum should contain MAX_LENGTH")
    void testEnumContainsMaxLength() {
        assertNotNull(XsdFacetType.MAX_LENGTH);
        assertEquals("maxLength", XsdFacetType.MAX_LENGTH.getXmlName());
    }

    @Test
    @DisplayName("enum should contain LENGTH")
    void testEnumContainsLength() {
        assertNotNull(XsdFacetType.LENGTH);
        assertEquals("length", XsdFacetType.LENGTH.getXmlName());
    }

    @Test
    @DisplayName("enum should contain PATTERN")
    void testEnumContainsPattern() {
        assertNotNull(XsdFacetType.PATTERN);
        assertEquals("pattern", XsdFacetType.PATTERN.getXmlName());
    }

    @Test
    @DisplayName("enum should contain ENUMERATION")
    void testEnumContainsEnumeration() {
        assertNotNull(XsdFacetType.ENUMERATION);
        assertEquals("enumeration", XsdFacetType.ENUMERATION.getXmlName());
    }

    @Test
    @DisplayName("enum should contain WHITE_SPACE")
    void testEnumContainsWhiteSpace() {
        assertNotNull(XsdFacetType.WHITE_SPACE);
        assertEquals("whiteSpace", XsdFacetType.WHITE_SPACE.getXmlName());
    }

    @Test
    @DisplayName("enum should contain MAX_INCLUSIVE")
    void testEnumContainsMaxInclusive() {
        assertNotNull(XsdFacetType.MAX_INCLUSIVE);
        assertEquals("maxInclusive", XsdFacetType.MAX_INCLUSIVE.getXmlName());
    }

    @Test
    @DisplayName("enum should contain MAX_EXCLUSIVE")
    void testEnumContainsMaxExclusive() {
        assertNotNull(XsdFacetType.MAX_EXCLUSIVE);
        assertEquals("maxExclusive", XsdFacetType.MAX_EXCLUSIVE.getXmlName());
    }

    @Test
    @DisplayName("enum should contain MIN_INCLUSIVE")
    void testEnumContainsMinInclusive() {
        assertNotNull(XsdFacetType.MIN_INCLUSIVE);
        assertEquals("minInclusive", XsdFacetType.MIN_INCLUSIVE.getXmlName());
    }

    @Test
    @DisplayName("enum should contain MIN_EXCLUSIVE")
    void testEnumContainsMinExclusive() {
        assertNotNull(XsdFacetType.MIN_EXCLUSIVE);
        assertEquals("minExclusive", XsdFacetType.MIN_EXCLUSIVE.getXmlName());
    }

    @Test
    @DisplayName("enum should contain TOTAL_DIGITS")
    void testEnumContainsTotalDigits() {
        assertNotNull(XsdFacetType.TOTAL_DIGITS);
        assertEquals("totalDigits", XsdFacetType.TOTAL_DIGITS.getXmlName());
    }

    @Test
    @DisplayName("enum should contain FRACTION_DIGITS")
    void testEnumContainsFractionDigits() {
        assertNotNull(XsdFacetType.FRACTION_DIGITS);
        assertEquals("fractionDigits", XsdFacetType.FRACTION_DIGITS.getXmlName());
    }

    @Test
    @DisplayName("enum should contain ASSERTION (XSD 1.1)")
    void testEnumContainsAssertion() {
        assertNotNull(XsdFacetType.ASSERTION);
        assertEquals("assertion", XsdFacetType.ASSERTION.getXmlName());
    }

    @Test
    @DisplayName("enum should contain EXPLICIT_TIMEZONE (XSD 1.1)")
    void testEnumContainsExplicitTimezone() {
        assertNotNull(XsdFacetType.EXPLICIT_TIMEZONE);
        assertEquals("explicitTimezone", XsdFacetType.EXPLICIT_TIMEZONE.getXmlName());
    }

    // ========== getXmlName() Tests ==========

    @Test
    @DisplayName("getXmlName() should return correct XML name")
    void testGetXmlName() {
        assertEquals("pattern", XsdFacetType.PATTERN.getXmlName());
        assertEquals("enumeration", XsdFacetType.ENUMERATION.getXmlName());
        assertEquals("maxInclusive", XsdFacetType.MAX_INCLUSIVE.getXmlName());
    }

    @Test
    @DisplayName("getXmlName() should return camelCase names")
    void testGetXmlNameCamelCase() {
        // Verify camelCase format
        assertTrue(XsdFacetType.MIN_LENGTH.getXmlName().matches("^[a-z][a-zA-Z]*$"));
        assertTrue(XsdFacetType.MAX_EXCLUSIVE.getXmlName().matches("^[a-z][a-zA-Z]*$"));
        assertTrue(XsdFacetType.TOTAL_DIGITS.getXmlName().matches("^[a-z][a-zA-Z]*$"));
    }

    // ========== fromXmlName() Tests ==========

    @Test
    @DisplayName("fromXmlName() should find facet by XML name")
    void testFromXmlName() {
        assertEquals(XsdFacetType.PATTERN, XsdFacetType.fromXmlName("pattern"));
        assertEquals(XsdFacetType.LENGTH, XsdFacetType.fromXmlName("length"));
        assertEquals(XsdFacetType.ENUMERATION, XsdFacetType.fromXmlName("enumeration"));
    }

    @Test
    @DisplayName("fromXmlName() should be case-sensitive")
    void testFromXmlNameCaseSensitive() {
        assertEquals(XsdFacetType.MIN_LENGTH, XsdFacetType.fromXmlName("minLength"));
        assertNull(XsdFacetType.fromXmlName("MinLength"));
        assertNull(XsdFacetType.fromXmlName("MINLENGTH"));
    }

    @Test
    @DisplayName("fromXmlName() should return null for invalid name")
    void testFromXmlNameInvalid() {
        assertNull(XsdFacetType.fromXmlName("invalidFacet"));
        assertNull(XsdFacetType.fromXmlName(""));
        assertNull(XsdFacetType.fromXmlName(null));
    }

    @Test
    @DisplayName("fromXmlName() should work for all facet types")
    void testFromXmlNameForAllTypes() {
        for (XsdFacetType type : XsdFacetType.values()) {
            assertEquals(type, XsdFacetType.fromXmlName(type.getXmlName()),
                    "fromXmlName should find " + type);
        }
    }

    // ========== Enum Properties Tests ==========

    @Test
    @DisplayName("enum values should be unique")
    void testEnumValuesUnique() {
        XsdFacetType[] values = XsdFacetType.values();
        java.util.Set<String> xmlNames = new java.util.HashSet<>();

        for (XsdFacetType type : values) {
            assertTrue(xmlNames.add(type.getXmlName()),
                    "XML name should be unique: " + type.getXmlName());
        }
    }

    @Test
    @DisplayName("valueOf() should find enum by name")
    void testValueOf() {
        assertEquals(XsdFacetType.PATTERN, XsdFacetType.valueOf("PATTERN"));
        assertEquals(XsdFacetType.MIN_INCLUSIVE, XsdFacetType.valueOf("MIN_INCLUSIVE"));
        assertEquals(XsdFacetType.FRACTION_DIGITS, XsdFacetType.valueOf("FRACTION_DIGITS"));
    }

    @Test
    @DisplayName("valueOf() should throw exception for invalid name")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> XsdFacetType.valueOf("INVALID_TYPE"));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("enum should work in switch statements")
    void testEnumInSwitch() {
        XsdFacetType type = XsdFacetType.PATTERN;
        String result = switch (type) {
            case PATTERN -> "pattern";
            case LENGTH -> "length";
            case MIN_LENGTH -> "minLength";
            case MAX_LENGTH -> "maxLength";
            default -> "other";
        };
        assertEquals("pattern", result);
    }

    @Test
    @DisplayName("enum should support all XSD 1.0 facets")
    void testXsd10FacetsPresent() {
        // XSD 1.0 standard facets (12 types)
        assertNotNull(XsdFacetType.MIN_LENGTH);
        assertNotNull(XsdFacetType.MAX_LENGTH);
        assertNotNull(XsdFacetType.LENGTH);
        assertNotNull(XsdFacetType.PATTERN);
        assertNotNull(XsdFacetType.ENUMERATION);
        assertNotNull(XsdFacetType.WHITE_SPACE);
        assertNotNull(XsdFacetType.MAX_INCLUSIVE);
        assertNotNull(XsdFacetType.MAX_EXCLUSIVE);
        assertNotNull(XsdFacetType.MIN_INCLUSIVE);
        assertNotNull(XsdFacetType.MIN_EXCLUSIVE);
        assertNotNull(XsdFacetType.TOTAL_DIGITS);
        assertNotNull(XsdFacetType.FRACTION_DIGITS);
    }

    @Test
    @DisplayName("enum should support XSD 1.1 facets")
    void testXsd11FacetsPresent() {
        // XSD 1.1 additional facets
        assertNotNull(XsdFacetType.ASSERTION);
        assertNotNull(XsdFacetType.EXPLICIT_TIMEZONE);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("enum should handle ordinal() correctly")
    void testOrdinal() {
        XsdFacetType first = XsdFacetType.values()[0];
        assertEquals(0, first.ordinal());

        XsdFacetType last = XsdFacetType.values()[XsdFacetType.values().length - 1];
        assertEquals(13, last.ordinal());
    }

    @Test
    @DisplayName("enum name() should return constant name")
    void testName() {
        assertEquals("MIN_LENGTH", XsdFacetType.MIN_LENGTH.name());
        assertEquals("PATTERN", XsdFacetType.PATTERN.name());
        assertEquals("EXPLICIT_TIMEZONE", XsdFacetType.EXPLICIT_TIMEZONE.name());
    }

    @Test
    @DisplayName("toString() should return same as name()")
    void testToString() {
        assertEquals(XsdFacetType.PATTERN.name(), XsdFacetType.PATTERN.toString());
        assertEquals(XsdFacetType.ENUMERATION.name(), XsdFacetType.ENUMERATION.toString());
    }
}
