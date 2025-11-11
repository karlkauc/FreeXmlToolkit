package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdFacet.
 *
 * @since 2.0
 */
class XsdFacetTest {

    private XsdFacet facet;

    @BeforeEach
    void setUp() {
        facet = new XsdFacet();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdFacet f = new XsdFacet();
        assertEquals("facet", f.getName());
        assertNull(f.getFacetType());
        assertNull(f.getValue());
        assertFalse(f.isFixed());
    }

    @Test
    @DisplayName("constructor with type and value should set properties")
    void testConstructorWithTypeAndValue() {
        XsdFacet f = new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}");

        assertEquals("pattern", f.getName());
        assertEquals(XsdFacetType.PATTERN, f.getFacetType());
        assertEquals("[0-9]{5}", f.getValue());
        assertFalse(f.isFixed());
    }

    @Test
    @DisplayName("constructor should set name from facet type XML name")
    void testConstructorSetsNameFromType() {
        XsdFacet minLength = new XsdFacet(XsdFacetType.MIN_LENGTH, "5");
        assertEquals("minLength", minLength.getName());

        XsdFacet enumeration = new XsdFacet(XsdFacetType.ENUMERATION, "Red");
        assertEquals("enumeration", enumeration.getName());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return FACET")
    void testGetNodeType() {
        assertEquals(XsdNodeType.FACET, facet.getNodeType());
    }

    // ========== FacetType Tests ==========

    @Test
    @DisplayName("facetType should be null by default")
    void testFacetTypeDefaultValue() {
        assertNull(facet.getFacetType());
    }

    @Test
    @DisplayName("setFacetType() should set facet type")
    void testSetFacetType() {
        facet.setFacetType(XsdFacetType.LENGTH);
        assertEquals(XsdFacetType.LENGTH, facet.getFacetType());
    }

    @Test
    @DisplayName("setFacetType() should update name to XML name")
    void testSetFacetTypeUpdatesName() {
        facet.setFacetType(XsdFacetType.MAX_INCLUSIVE);
        assertEquals("maxInclusive", facet.getName());

        facet.setFacetType(XsdFacetType.TOTAL_DIGITS);
        assertEquals("totalDigits", facet.getName());
    }

    @Test
    @DisplayName("setFacetType() should fire PropertyChangeEvent")
    void testSetFacetTypeFiresPropertyChange() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            // setFacetType() fires both "facetType" and "name" events
            if ("facetType".equals(evt.getPropertyName())) {
                assertNull(evt.getOldValue());
                assertEquals(XsdFacetType.PATTERN, evt.getNewValue());
                eventCount[0]++;
            } else if ("name".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        facet.addPropertyChangeListener(listener);
        facet.setFacetType(XsdFacetType.PATTERN);

        assertTrue(eventCount[0] > 0, "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setFacetType() should allow null value")
    void testSetFacetTypeAllowsNull() {
        facet.setFacetType(XsdFacetType.PATTERN);
        facet.setFacetType(null);
        assertNull(facet.getFacetType());
    }

    // ========== Value Tests ==========

    @Test
    @DisplayName("value should be null by default")
    void testValueDefaultValue() {
        assertNull(facet.getValue());
    }

    @Test
    @DisplayName("setValue() should set value")
    void testSetValue() {
        facet.setValue("test value");
        assertEquals("test value", facet.getValue());
    }

    @Test
    @DisplayName("setValue() should fire PropertyChangeEvent")
    void testSetValueFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("value", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("100", evt.getNewValue());
            eventFired.set(true);
        };

        facet.addPropertyChangeListener(listener);
        facet.setValue("100");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setValue() should allow null value")
    void testSetValueAllowsNull() {
        facet.setValue("test");
        facet.setValue(null);
        assertNull(facet.getValue());
    }

    @Test
    @DisplayName("setValue() should accept various string formats")
    void testSetValueVariousFormats() {
        facet.setValue("[0-9]+");  // Pattern
        assertEquals("[0-9]+", facet.getValue());

        facet.setValue("5");  // Length
        assertEquals("5", facet.getValue());

        facet.setValue("preserve");  // WhiteSpace
        assertEquals("preserve", facet.getValue());
    }

    // ========== Fixed Flag Tests ==========

    @Test
    @DisplayName("isFixed() should be false by default")
    void testIsFixedDefaultValue() {
        assertFalse(facet.isFixed());
    }

    @Test
    @DisplayName("setFixed() should set fixed flag")
    void testSetFixed() {
        facet.setFixed(true);
        assertTrue(facet.isFixed());
    }

    @Test
    @DisplayName("setFixed() should fire PropertyChangeEvent")
    void testSetFixedFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("fixed", evt.getPropertyName());
            assertEquals(false, evt.getOldValue());
            assertEquals(true, evt.getNewValue());
            eventFired.set(true);
        };

        facet.addPropertyChangeListener(listener);
        facet.setFixed(true);

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        facet.setFacetType(XsdFacetType.PATTERN);
        facet.setValue("[A-Z]+");
        facet.setFixed(true);

        XsdFacet copy = (XsdFacet) facet.deepCopy(null);

        assertNotNull(copy);
        assertEquals(facet.getFacetType(), copy.getFacetType());
        assertEquals(facet.getValue(), copy.getValue());
        assertEquals(facet.isFixed(), copy.isFixed());
        assertNotSame(facet, copy);
        assertNotEquals(facet.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change facet name")
    void testDeepCopyWithSuffix() {
        facet.setFacetType(XsdFacetType.LENGTH);
        facet.setValue("10");

        XsdFacet copy = (XsdFacet) facet.deepCopy("_Copy");

        // Facet name comes from facet type, suffix should not be applied
        assertEquals(facet.getName(), copy.getName());
        assertEquals(facet.getFacetType(), copy.getFacetType());
    }

    @Test
    @DisplayName("deepCopy() should handle null properties")
    void testDeepCopyWithNullProperties() {
        // facet has all null/default values
        XsdFacet copy = (XsdFacet) facet.deepCopy(null);

        assertNotNull(copy);
        assertNull(copy.getFacetType());
        assertNull(copy.getValue());
        assertFalse(copy.isFixed());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("facet should work in restriction context")
    void testFacetInRestriction() {
        XsdRestriction restriction = new XsdRestriction("xs:string");

        XsdFacet patternFacet = new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{3}");
        restriction.addFacet(patternFacet);

        assertEquals(restriction, patternFacet.getParent());
        assertEquals(1, restriction.getFacets().size());
        assertEquals(patternFacet, restriction.getFacets().get(0));
    }

    @Test
    @DisplayName("multiple facets of same type should be allowed")
    void testMultipleFacetsOfSameType() {
        // Common in XSD: multiple enumeration facets
        XsdFacet enum1 = new XsdFacet(XsdFacetType.ENUMERATION, "Red");
        XsdFacet enum2 = new XsdFacet(XsdFacetType.ENUMERATION, "Green");
        XsdFacet enum3 = new XsdFacet(XsdFacetType.ENUMERATION, "Blue");

        assertEquals(XsdFacetType.ENUMERATION, enum1.getFacetType());
        assertEquals(XsdFacetType.ENUMERATION, enum2.getFacetType());
        assertEquals(XsdFacetType.ENUMERATION, enum3.getFacetType());

        assertEquals("Red", enum1.getValue());
        assertEquals("Green", enum2.getValue());
        assertEquals("Blue", enum3.getValue());
    }

    @Test
    @DisplayName("facet with all properties set")
    void testFacetWithAllProperties() {
        XsdFacet f = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0");
        f.setFixed(true);

        assertEquals(XsdFacetType.MIN_INCLUSIVE, f.getFacetType());
        assertEquals("0", f.getValue());
        assertTrue(f.isFixed());
        assertEquals("minInclusive", f.getName());
        assertEquals(XsdNodeType.FACET, f.getNodeType());
    }

    @Test
    @DisplayName("changing facet type should update name")
    void testChangingFacetTypeUpdatesName() {
        facet.setFacetType(XsdFacetType.LENGTH);
        assertEquals("length", facet.getName());

        facet.setFacetType(XsdFacetType.PATTERN);
        assertEquals("pattern", facet.getName());

        facet.setFacetType(XsdFacetType.MAX_EXCLUSIVE);
        assertEquals("maxExclusive", facet.getName());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> eventCount[0]++;

        facet.addPropertyChangeListener(listener);
        facet.setFacetType(XsdFacetType.PATTERN);
        facet.setValue("[0-9]+");
        facet.setFixed(true);

        assertTrue(eventCount[0] >= 3, "Should have fired at least 3 events");
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        facet.setFacetType(XsdFacetType.PATTERN);
        facet.setValue("[A-Z]+");
        String toString = facet.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    // ========== Realistic XSD Facet Examples ==========

    @Test
    @DisplayName("create pattern facet for zip code")
    void testPatternFacetZipCode() {
        XsdFacet zipPattern = new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}");

        assertEquals(XsdFacetType.PATTERN, zipPattern.getFacetType());
        assertEquals("[0-9]{5}", zipPattern.getValue());
        assertEquals("pattern", zipPattern.getName());
    }

    @Test
    @DisplayName("create length facet for fixed-length string")
    void testLengthFacet() {
        XsdFacet length = new XsdFacet(XsdFacetType.LENGTH, "10");
        length.setFixed(true);

        assertEquals(XsdFacetType.LENGTH, length.getFacetType());
        assertEquals("10", length.getValue());
        assertTrue(length.isFixed());
    }

    @Test
    @DisplayName("create min/max inclusive facets for range")
    void testRangeFacets() {
        XsdFacet min = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0");
        XsdFacet max = new XsdFacet(XsdFacetType.MAX_INCLUSIVE, "100");

        assertEquals("0", min.getValue());
        assertEquals("100", max.getValue());
        assertEquals("minInclusive", min.getName());
        assertEquals("maxInclusive", max.getName());
    }

    @Test
    @DisplayName("create totalDigits and fractionDigits facets")
    void testDigitsFacets() {
        XsdFacet totalDigits = new XsdFacet(XsdFacetType.TOTAL_DIGITS, "5");
        XsdFacet fractionDigits = new XsdFacet(XsdFacetType.FRACTION_DIGITS, "2");

        assertEquals("5", totalDigits.getValue());
        assertEquals("2", fractionDigits.getValue());
    }

    @Test
    @DisplayName("create whiteSpace facet")
    void testWhiteSpaceFacet() {
        XsdFacet whiteSpace = new XsdFacet(XsdFacetType.WHITE_SPACE, "collapse");

        assertEquals(XsdFacetType.WHITE_SPACE, whiteSpace.getFacetType());
        assertEquals("collapse", whiteSpace.getValue());
    }
}
