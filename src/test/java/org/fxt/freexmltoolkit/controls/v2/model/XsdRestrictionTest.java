package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdRestriction.
 *
 * @since 2.0
 */
class XsdRestrictionTest {

    private XsdRestriction restriction;

    @BeforeEach
    void setUp() {
        restriction = new XsdRestriction();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdRestriction rest = new XsdRestriction();
        assertEquals("restriction", rest.getName());
    }

    @Test
    @DisplayName("constructor with base should set base type")
    void testConstructorWithBase() {
        XsdRestriction rest = new XsdRestriction("xs:string");
        assertEquals("xs:string", rest.getBase());
        assertEquals("restriction", rest.getName());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return RESTRICTION")
    void testGetNodeType() {
        assertEquals(XsdNodeType.RESTRICTION, restriction.getNodeType());
    }

    // ========== Base Type Tests ==========

    @Test
    @DisplayName("base should be null by default")
    void testBaseDefaultValue() {
        assertNull(restriction.getBase());
    }

    @Test
    @DisplayName("setBase() should set base type")
    void testSetBase() {
        restriction.setBase("xs:integer");
        assertEquals("xs:integer", restriction.getBase());
    }

    @Test
    @DisplayName("setBase() should fire PropertyChangeEvent")
    void testSetBaseFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("base", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("xs:decimal", evt.getNewValue());
            eventFired.set(true);
        };

        restriction.addPropertyChangeListener(listener);
        restriction.setBase("xs:decimal");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setBase() should allow null value")
    void testSetBaseAllowsNull() {
        restriction.setBase("xs:string");
        restriction.setBase(null);
        assertNull(restriction.getBase());
    }

    // ========== Facets List Tests ==========

    @Test
    @DisplayName("getFacets() should return empty list by default")
    void testGetFacetsDefaultValue() {
        List<XsdFacet> facets = restriction.getFacets();
        assertNotNull(facets);
        assertTrue(facets.isEmpty());
    }

    @Test
    @DisplayName("getFacets() should return copy of list")
    void testGetFacetsReturnsCopy() {
        XsdFacet facet = new XsdFacet(XsdFacetType.MIN_LENGTH, "5");
        restriction.addFacet(facet);

        List<XsdFacet> facets1 = restriction.getFacets();
        List<XsdFacet> facets2 = restriction.getFacets();

        assertNotSame(facets1, facets2, "Should return different list instances");
        assertEquals(facets1.size(), facets2.size());
    }

    // ========== addFacet() Tests ==========

    @Test
    @DisplayName("addFacet() should add facet to list")
    void testAddFacet() {
        XsdFacet facet = new XsdFacet(XsdFacetType.MAX_LENGTH, "10");
        restriction.addFacet(facet);

        assertEquals(1, restriction.getFacets().size());
        assertEquals(facet, restriction.getFacets().get(0));
    }

    @Test
    @DisplayName("addFacet() should add facet as child")
    void testAddFacetAddsChild() {
        XsdFacet facet = new XsdFacet(XsdFacetType.PATTERN, "[A-Z]+");
        restriction.addFacet(facet);

        assertEquals(1, restriction.getChildren().size());
        assertEquals(facet, restriction.getChildren().get(0));
        assertEquals(restriction, facet.getParent());
    }

    @Test
    @DisplayName("addFacet() should fire PropertyChangeEvent")
    void testAddFacetFiresPropertyChange() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("facets".equals(evt.getPropertyName()) || "children".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        restriction.addPropertyChangeListener(listener);
        XsdFacet facet = new XsdFacet(XsdFacetType.ENUMERATION, "Red");
        restriction.addFacet(facet);

        assertTrue(eventCount[0] > 0, "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("addFacet() should allow multiple facets")
    void testAddMultipleFacets() {
        XsdFacet facet1 = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0");
        XsdFacet facet2 = new XsdFacet(XsdFacetType.MAX_INCLUSIVE, "100");
        XsdFacet facet3 = new XsdFacet(XsdFacetType.TOTAL_DIGITS, "3");

        restriction.addFacet(facet1);
        restriction.addFacet(facet2);
        restriction.addFacet(facet3);

        assertEquals(3, restriction.getFacets().size());
        assertEquals(3, restriction.getChildren().size());
    }

    // ========== removeFacet() Tests ==========

    @Test
    @DisplayName("removeFacet() should remove facet from list")
    void testRemoveFacet() {
        XsdFacet facet = new XsdFacet(XsdFacetType.LENGTH, "5");
        restriction.addFacet(facet);
        restriction.removeFacet(facet);

        assertTrue(restriction.getFacets().isEmpty());
    }

    @Test
    @DisplayName("removeFacet() should remove facet as child")
    void testRemoveFacetRemovesChild() {
        XsdFacet facet = new XsdFacet(XsdFacetType.WHITE_SPACE, "collapse");
        restriction.addFacet(facet);
        restriction.removeFacet(facet);

        assertTrue(restriction.getChildren().isEmpty());
        assertNull(facet.getParent());
    }

    @Test
    @DisplayName("removeFacet() should fire PropertyChangeEvent")
    void testRemoveFacetFiresPropertyChange() {
        XsdFacet facet = new XsdFacet(XsdFacetType.FRACTION_DIGITS, "2");
        restriction.addFacet(facet);

        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("facets".equals(evt.getPropertyName()) || "children".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        restriction.addPropertyChangeListener(listener);
        restriction.removeFacet(facet);

        assertTrue(eventCount[0] > 0, "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("removeFacet() on non-existent facet should be safe")
    void testRemoveNonExistentFacet() {
        XsdFacet facet1 = new XsdFacet(XsdFacetType.MIN_EXCLUSIVE, "0");
        XsdFacet facet2 = new XsdFacet(XsdFacetType.MAX_EXCLUSIVE, "100");

        restriction.addFacet(facet1);

        // Remove facet2 which was never added
        assertDoesNotThrow(() -> restriction.removeFacet(facet2));

        // facet1 should still be there
        assertEquals(1, restriction.getFacets().size());
        assertEquals(facet1, restriction.getFacets().get(0));
    }

    // ========== getFacetByType() Tests ==========

    @Test
    @DisplayName("getFacetByType() should return facet if exists")
    void testGetFacetByType() {
        XsdFacet patternFacet = new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}");
        XsdFacet lengthFacet = new XsdFacet(XsdFacetType.LENGTH, "5");

        restriction.addFacet(patternFacet);
        restriction.addFacet(lengthFacet);

        assertEquals(patternFacet, restriction.getFacetByType(XsdFacetType.PATTERN));
        assertEquals(lengthFacet, restriction.getFacetByType(XsdFacetType.LENGTH));
    }

    @Test
    @DisplayName("getFacetByType() should return null if not exists")
    void testGetFacetByTypeNotFound() {
        XsdFacet facet = new XsdFacet(XsdFacetType.MIN_LENGTH, "1");
        restriction.addFacet(facet);

        assertNull(restriction.getFacetByType(XsdFacetType.MAX_LENGTH));
        assertNull(restriction.getFacetByType(XsdFacetType.PATTERN));
    }

    @Test
    @DisplayName("getFacetByType() should return first facet if multiple of same type")
    void testGetFacetByTypeMultiple() {
        // Note: In real XSD, multiple enumerations are common
        XsdFacet enum1 = new XsdFacet(XsdFacetType.ENUMERATION, "Red");
        XsdFacet enum2 = new XsdFacet(XsdFacetType.ENUMERATION, "Green");
        XsdFacet enum3 = new XsdFacet(XsdFacetType.ENUMERATION, "Blue");

        restriction.addFacet(enum1);
        restriction.addFacet(enum2);
        restriction.addFacet(enum3);

        // Should return first one
        XsdFacet found = restriction.getFacetByType(XsdFacetType.ENUMERATION);
        assertEquals(enum1, found);
        assertEquals("Red", found.getValue());
    }

    // ========== hasFacet() Tests ==========

    @Test
    @DisplayName("hasFacet() should return true if facet exists")
    void testHasFacet() {
        XsdFacet facet = new XsdFacet(XsdFacetType.TOTAL_DIGITS, "10");
        restriction.addFacet(facet);

        assertTrue(restriction.hasFacet(XsdFacetType.TOTAL_DIGITS));
    }

    @Test
    @DisplayName("hasFacet() should return false if facet does not exist")
    void testHasFacetNotFound() {
        assertFalse(restriction.hasFacet(XsdFacetType.PATTERN));
        assertFalse(restriction.hasFacet(XsdFacetType.LENGTH));
    }

    @Test
    @DisplayName("hasFacet() should return false after removing facet")
    void testHasFacetAfterRemove() {
        XsdFacet facet = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "1");
        restriction.addFacet(facet);
        assertTrue(restriction.hasFacet(XsdFacetType.MIN_INCLUSIVE));

        restriction.removeFacet(facet);
        assertFalse(restriction.hasFacet(XsdFacetType.MIN_INCLUSIVE));
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        restriction.setBase("xs:string");
        XsdFacet facet1 = new XsdFacet(XsdFacetType.MIN_LENGTH, "5");
        XsdFacet facet2 = new XsdFacet(XsdFacetType.MAX_LENGTH, "50");
        restriction.addFacet(facet1);
        restriction.addFacet(facet2);

        XsdRestriction copy = (XsdRestriction) restriction.deepCopy(null);

        assertNotNull(copy);
        assertEquals(restriction.getBase(), copy.getBase());
        // Note: Facets are children and will be copied via copyBasicPropertiesTo()
        assertEquals(restriction.getChildren().size(), copy.getChildren().size());
        assertNotSame(restriction, copy);
        assertNotEquals(restriction.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() should copy all children including facets")
    void testDeepCopyCopiesFacets() {
        restriction.setBase("xs:integer");
        XsdFacet minFacet = new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0");
        XsdFacet maxFacet = new XsdFacet(XsdFacetType.MAX_INCLUSIVE, "100");
        restriction.addFacet(minFacet);
        restriction.addFacet(maxFacet);

        XsdRestriction copy = (XsdRestriction) restriction.deepCopy(null);

        // Children (including facets) should be copied
        assertEquals(2, copy.getChildren().size());
        // Children should be different instances
        assertNotSame(restriction.getChildren().get(0), copy.getChildren().get(0));
        assertNotSame(restriction.getChildren().get(1), copy.getChildren().get(1));
        // Children should be XsdFacet instances
        assertTrue(copy.getChildren().get(0) instanceof XsdFacet);
        assertTrue(copy.getChildren().get(1) instanceof XsdFacet);
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change restriction name")
    void testDeepCopyWithSuffix() {
        restriction.setBase("xs:decimal");

        XsdRestriction copy = (XsdRestriction) restriction.deepCopy("_Copy");

        // Restriction name is always "restriction", suffix should not be applied
        assertEquals("restriction", copy.getName());
        assertEquals(restriction.getBase(), copy.getBase());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("restriction should work in simpleType context")
    void testRestrictionInSimpleType() {
        XsdSimpleType simpleType = new XsdSimpleType("ZipCodeType");

        XsdRestriction rest = new XsdRestriction("xs:string");
        rest.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}"));

        rest.setParent(simpleType);
        simpleType.addChild(rest);

        assertEquals(simpleType, rest.getParent());
        assertEquals(1, simpleType.getChildren().size());
        assertEquals(rest, simpleType.getChildren().get(0));
    }

    @Test
    @DisplayName("restriction with multiple enumeration facets")
    void testRestrictionWithEnumerations() {
        restriction.setBase("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Red"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Green"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Blue"));

        assertEquals(3, restriction.getFacets().size());
        assertTrue(restriction.hasFacet(XsdFacetType.ENUMERATION));

        // All should be enumeration facets
        for (XsdFacet facet : restriction.getFacets()) {
            assertEquals(XsdFacetType.ENUMERATION, facet.getFacetType());
        }
    }

    @Test
    @DisplayName("restriction with numeric range facets")
    void testRestrictionWithNumericRange() {
        restriction.setBase("xs:integer");
        restriction.addFacet(new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0"));
        restriction.addFacet(new XsdFacet(XsdFacetType.MAX_INCLUSIVE, "100"));
        restriction.addFacet(new XsdFacet(XsdFacetType.TOTAL_DIGITS, "3"));

        assertEquals(3, restriction.getFacets().size());
        assertTrue(restriction.hasFacet(XsdFacetType.MIN_INCLUSIVE));
        assertTrue(restriction.hasFacet(XsdFacetType.MAX_INCLUSIVE));
        assertTrue(restriction.hasFacet(XsdFacetType.TOTAL_DIGITS));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> eventCount[0]++;

        restriction.addPropertyChangeListener(listener);
        restriction.setBase("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.LENGTH, "5"));
        restriction.setBase("xs:integer");

        assertTrue(eventCount[0] >= 3, "Should have fired at least 3 events");
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        restriction.setBase("xs:string");
        String toString = restriction.toString();
        assertNotNull(toString);
        // Should contain some identifying information
        assertTrue(toString.length() > 0);
    }
}
