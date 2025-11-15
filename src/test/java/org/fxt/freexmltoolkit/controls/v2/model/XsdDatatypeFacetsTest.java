package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdDatatypeFacets.
 */
class XsdDatatypeFacetsTest {

    @Test
    void stringTypesShouldSupportAllStringFacets() {
        String[] stringTypes = {"string", "xs:string", "normalizedString", "token",
                               "language", "Name", "NCName", "ID", "IDREF", "ENTITY", "NMTOKEN"};

        for (String type : stringTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.LENGTH),
                      type + " should support LENGTH");
            assertTrue(facets.contains(XsdFacetType.MIN_LENGTH),
                      type + " should support MIN_LENGTH");
            assertTrue(facets.contains(XsdFacetType.MAX_LENGTH),
                      type + " should support MAX_LENGTH");
            assertTrue(facets.contains(XsdFacetType.PATTERN),
                      type + " should support PATTERN");
            assertTrue(facets.contains(XsdFacetType.ENUMERATION),
                      type + " should support ENUMERATION");
            assertTrue(facets.contains(XsdFacetType.WHITE_SPACE),
                      type + " should support WHITE_SPACE");
            assertTrue(facets.contains(XsdFacetType.ASSERTION),
                      type + " should support ASSERTION (XSD 1.1)");
        }
    }

    @Test
    void numericTypesShouldSupportAssertions() {
        String[] numericTypes = {"decimal", "integer", "long", "int", "short", "byte",
                                "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                                "positiveInteger", "negativeInteger", "nonPositiveInteger",
                                "nonNegativeInteger", "float", "double"};

        for (String type : numericTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.ASSERTION),
                      type + " should support ASSERTION (XSD 1.1)");
        }
    }

    @Test
    void dateTimeTypesShouldSupportAssertionsAndExplicitTimezone() {
        String[] dateTimeTypes = {"dateTime", "dateTimeStamp", "date", "time",
                                 "gYear", "gYearMonth", "gMonth", "gMonthDay", "gDay"};

        for (String type : dateTimeTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.ASSERTION),
                      type + " should support ASSERTION (XSD 1.1)");
            assertTrue(facets.contains(XsdFacetType.EXPLICIT_TIMEZONE),
                      type + " should support EXPLICIT_TIMEZONE (XSD 1.1)");
        }
    }

    @Test
    void durationTypesShouldSupportAssertions() {
        String[] durationTypes = {"duration", "yearMonthDuration", "dayTimeDuration"};

        for (String type : durationTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.ASSERTION),
                      type + " should support ASSERTION (XSD 1.1)");
        }
    }

    @Test
    void binaryTypesShouldSupportAssertions() {
        String[] binaryTypes = {"hexBinary", "base64Binary"};

        for (String type : binaryTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.ASSERTION),
                      type + " should support ASSERTION (XSD 1.1)");
        }
    }

    @Test
    void booleanShouldSupportAssertions() {
        Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets("boolean");
        assertTrue(facets.contains(XsdFacetType.ASSERTION),
                  "boolean should support ASSERTION (XSD 1.1)");
        assertTrue(facets.contains(XsdFacetType.PATTERN),
                  "boolean should support PATTERN");
        assertTrue(facets.contains(XsdFacetType.WHITE_SPACE),
                  "boolean should support WHITE_SPACE");
    }

    @Test
    void uriTypesShouldSupportAssertions() {
        Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets("anyURI");
        assertTrue(facets.contains(XsdFacetType.ASSERTION),
                  "anyURI should support ASSERTION (XSD 1.1)");
    }

    @Test
    void qnameAndNotationTypesShouldSupportAssertions() {
        String[] types = {"QName", "NOTATION"};

        for (String type : types) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.ASSERTION),
                      type + " should support ASSERTION (XSD 1.1)");
        }
    }

    @Test
    void integerTypesShouldHaveFractionDigitsFixed() {
        String[] integerTypes = {"integer", "long", "int", "short", "byte",
                                "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                                "positiveInteger", "negativeInteger",
                                "nonPositiveInteger", "nonNegativeInteger"};

        for (String type : integerTypes) {
            assertTrue(XsdDatatypeFacets.isFacetFixed(type, XsdFacetType.FRACTION_DIGITS),
                      type + " should have fractionDigits fixed to 0");
            assertEquals("0", XsdDatatypeFacets.getFixedFacetValue(type, XsdFacetType.FRACTION_DIGITS),
                        type + " fractionDigits should be fixed to '0'");
        }
    }

    @Test
    void whiteSpaceShouldBeFixedForMostTypes() {
        // normalizedString: fixed to "replace"
        assertTrue(XsdDatatypeFacets.isFacetFixed("normalizedString", XsdFacetType.WHITE_SPACE));
        assertEquals("replace", XsdDatatypeFacets.getFixedFacetValue("normalizedString", XsdFacetType.WHITE_SPACE));

        // token and derivatives: fixed to "collapse"
        String[] collapseTypes = {"token", "language", "Name", "NCName", "decimal",
                                 "integer", "float", "double", "boolean", "dateTime",
                                 "date", "time", "duration", "hexBinary", "base64Binary",
                                 "anyURI", "QName", "NOTATION"};

        for (String type : collapseTypes) {
            assertTrue(XsdDatatypeFacets.isFacetFixed(type, XsdFacetType.WHITE_SPACE),
                      type + " should have whiteSpace fixed");
            assertEquals("collapse", XsdDatatypeFacets.getFixedFacetValue(type, XsdFacetType.WHITE_SPACE),
                        type + " whiteSpace should be fixed to 'collapse'");
        }
    }

    @Test
    void dateTimeStampShouldHaveExplicitTimezoneFixed() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("dateTimeStamp", XsdFacetType.EXPLICIT_TIMEZONE));
        assertEquals("required", XsdDatatypeFacets.getFixedFacetValue("dateTimeStamp", XsdFacetType.EXPLICIT_TIMEZONE));
    }

    @Test
    void unknownTypeShouldReturnEmptySet() {
        Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets("unknownType");
        assertTrue(facets.isEmpty());
    }

    @Test
    void nullOrEmptyTypeShouldReturnEmptySet() {
        assertTrue(XsdDatatypeFacets.getApplicableFacets(null).isEmpty());
        assertTrue(XsdDatatypeFacets.getApplicableFacets("").isEmpty());
    }

    @Test
    void namespacePrefixShouldBeRemoved() {
        Set<XsdFacetType> withPrefix = XsdDatatypeFacets.getApplicableFacets("xs:string");
        Set<XsdFacetType> withoutPrefix = XsdDatatypeFacets.getApplicableFacets("string");

        assertEquals(withoutPrefix, withPrefix,
                    "Facets should be the same regardless of namespace prefix");
    }

    @Test
    void decimalTypesShouldSupportTotalAndFractionDigits() {
        String[] decimalTypes = {"decimal", "integer", "long", "int", "short", "byte",
                                "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte",
                                "positiveInteger", "negativeInteger",
                                "nonPositiveInteger", "nonNegativeInteger"};

        for (String type : decimalTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertTrue(facets.contains(XsdFacetType.TOTAL_DIGITS),
                      type + " should support TOTAL_DIGITS");
            assertTrue(facets.contains(XsdFacetType.FRACTION_DIGITS),
                      type + " should support FRACTION_DIGITS");
        }
    }

    @Test
    void floatAndDoubleShouldNotSupportTotalDigits() {
        String[] floatTypes = {"float", "double"};

        for (String type : floatTypes) {
            Set<XsdFacetType> facets = XsdDatatypeFacets.getApplicableFacets(type);
            assertFalse(facets.contains(XsdFacetType.TOTAL_DIGITS),
                       type + " should NOT support TOTAL_DIGITS");
            assertFalse(facets.contains(XsdFacetType.FRACTION_DIGITS),
                       type + " should NOT support FRACTION_DIGITS");
        }
    }

    @Test
    void longShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("long", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("long", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("-9223372036854775808", XsdDatatypeFacets.getFixedFacetValue("long", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("9223372036854775807", XsdDatatypeFacets.getFixedFacetValue("long", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void intShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("int", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("int", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("-2147483648", XsdDatatypeFacets.getFixedFacetValue("int", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("2147483647", XsdDatatypeFacets.getFixedFacetValue("int", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void shortShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("short", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("short", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("-32768", XsdDatatypeFacets.getFixedFacetValue("short", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("32767", XsdDatatypeFacets.getFixedFacetValue("short", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void byteShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("byte", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("byte", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("-128", XsdDatatypeFacets.getFixedFacetValue("byte", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("127", XsdDatatypeFacets.getFixedFacetValue("byte", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void unsignedLongShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedLong", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedLong", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("0", XsdDatatypeFacets.getFixedFacetValue("unsignedLong", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("18446744073709551615", XsdDatatypeFacets.getFixedFacetValue("unsignedLong", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void unsignedIntShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedInt", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedInt", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("0", XsdDatatypeFacets.getFixedFacetValue("unsignedInt", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("4294967295", XsdDatatypeFacets.getFixedFacetValue("unsignedInt", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void unsignedShortShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedShort", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedShort", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("0", XsdDatatypeFacets.getFixedFacetValue("unsignedShort", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("65535", XsdDatatypeFacets.getFixedFacetValue("unsignedShort", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void unsignedByteShouldHaveFixedMinMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedByte", XsdFacetType.MIN_INCLUSIVE));
        assertTrue(XsdDatatypeFacets.isFacetFixed("unsignedByte", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("0", XsdDatatypeFacets.getFixedFacetValue("unsignedByte", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("255", XsdDatatypeFacets.getFixedFacetValue("unsignedByte", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void positiveIntegerShouldHaveFixedMinInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("positiveInteger", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("1", XsdDatatypeFacets.getFixedFacetValue("positiveInteger", XsdFacetType.MIN_INCLUSIVE));
        assertFalse(XsdDatatypeFacets.isFacetFixed("positiveInteger", XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void negativeIntegerShouldHaveFixedMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("negativeInteger", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("-1", XsdDatatypeFacets.getFixedFacetValue("negativeInteger", XsdFacetType.MAX_INCLUSIVE));
        assertFalse(XsdDatatypeFacets.isFacetFixed("negativeInteger", XsdFacetType.MIN_INCLUSIVE));
    }

    @Test
    void nonPositiveIntegerShouldHaveFixedMaxInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("nonPositiveInteger", XsdFacetType.MAX_INCLUSIVE));
        assertEquals("0", XsdDatatypeFacets.getFixedFacetValue("nonPositiveInteger", XsdFacetType.MAX_INCLUSIVE));
        assertFalse(XsdDatatypeFacets.isFacetFixed("nonPositiveInteger", XsdFacetType.MIN_INCLUSIVE));
    }

    @Test
    void nonNegativeIntegerShouldHaveFixedMinInclusive() {
        assertTrue(XsdDatatypeFacets.isFacetFixed("nonNegativeInteger", XsdFacetType.MIN_INCLUSIVE));
        assertEquals("0", XsdDatatypeFacets.getFixedFacetValue("nonNegativeInteger", XsdFacetType.MIN_INCLUSIVE));
        assertFalse(XsdDatatypeFacets.isFacetFixed("nonNegativeInteger", XsdFacetType.MAX_INCLUSIVE));
    }
}
