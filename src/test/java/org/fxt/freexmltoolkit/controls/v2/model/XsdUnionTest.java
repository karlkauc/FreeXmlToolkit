package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdUnion.
 *
 * @since 2.0
 */
class XsdUnionTest {

    private XsdUnion union;

    @BeforeEach
    void setUp() {
        union = new XsdUnion();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdUnion u = new XsdUnion();
        assertEquals("union", u.getName());
        assertNotNull(u.getMemberTypes());
        assertTrue(u.getMemberTypes().isEmpty());
    }

    @Test
    @DisplayName("constructor with member types should set member types")
    void testConstructorWithMemberTypes() {
        XsdUnion u = new XsdUnion("xs:integer", "xs:string");
        assertEquals("union", u.getName());
        assertEquals(2, u.getMemberTypes().size());
        assertTrue(u.getMemberTypes().contains("xs:integer"));
        assertTrue(u.getMemberTypes().contains("xs:string"));
    }

    @Test
    @DisplayName("constructor with single member type")
    void testConstructorWithSingleMemberType() {
        XsdUnion u = new XsdUnion("xs:decimal");
        assertEquals(1, u.getMemberTypes().size());
        assertEquals("xs:decimal", u.getMemberTypes().get(0));
    }

    @Test
    @DisplayName("constructor with multiple member types")
    void testConstructorWithMultipleMemberTypes() {
        XsdUnion u = new XsdUnion("xs:integer", "xs:string", "xs:decimal", "CustomType");
        assertEquals(4, u.getMemberTypes().size());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return UNION")
    void testGetNodeType() {
        assertEquals(XsdNodeType.UNION, union.getNodeType());
    }

    // ========== MemberTypes Tests ==========

    @Test
    @DisplayName("getMemberTypes() should return empty list by default")
    void testGetMemberTypesDefaultValue() {
        List<String> memberTypes = union.getMemberTypes();
        assertNotNull(memberTypes);
        assertTrue(memberTypes.isEmpty());
    }

    @Test
    @DisplayName("getMemberTypes() should return copy of list")
    void testGetMemberTypesReturnsCopy() {
        union.addMemberType("xs:integer");

        List<String> memberTypes1 = union.getMemberTypes();
        List<String> memberTypes2 = union.getMemberTypes();

        assertNotSame(memberTypes1, memberTypes2, "Should return different list instances");
        assertEquals(memberTypes1.size(), memberTypes2.size());
    }

    @Test
    @DisplayName("addMemberType() should add member type to list")
    void testAddMemberType() {
        union.addMemberType("xs:integer");
        assertEquals(1, union.getMemberTypes().size());
        assertEquals("xs:integer", union.getMemberTypes().get(0));
    }

    @Test
    @DisplayName("addMemberType() should fire PropertyChangeEvent")
    void testAddMemberTypeFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("memberTypes", evt.getPropertyName());
            eventFired.set(true);
        };

        union.addPropertyChangeListener(listener);
        union.addMemberType("xs:string");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("addMemberType() should allow multiple member types")
    void testAddMultipleMemberTypes() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        union.addMemberType("xs:decimal");

        assertEquals(3, union.getMemberTypes().size());
        assertTrue(union.getMemberTypes().contains("xs:integer"));
        assertTrue(union.getMemberTypes().contains("xs:string"));
        assertTrue(union.getMemberTypes().contains("xs:decimal"));
    }

    @Test
    @DisplayName("removeMemberType() should remove member type from list")
    void testRemoveMemberType() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");

        union.removeMemberType("xs:integer");

        assertEquals(1, union.getMemberTypes().size());
        assertEquals("xs:string", union.getMemberTypes().get(0));
    }

    @Test
    @DisplayName("removeMemberType() should fire PropertyChangeEvent")
    void testRemoveMemberTypeFiresPropertyChange() {
        union.addMemberType("xs:decimal");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("memberTypes", evt.getPropertyName());
            eventFired.set(true);
        };

        union.addPropertyChangeListener(listener);
        union.removeMemberType("xs:decimal");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("removeMemberType() on non-existent member should be safe")
    void testRemoveNonExistentMemberType() {
        union.addMemberType("xs:integer");

        assertDoesNotThrow(() -> union.removeMemberType("xs:string"));

        // xs:integer should still be there
        assertEquals(1, union.getMemberTypes().size());
        assertEquals("xs:integer", union.getMemberTypes().get(0));
    }

    @Test
    @DisplayName("setMemberTypes() should replace all member types")
    void testSetMemberTypes() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");

        List<String> newTypes = List.of("xs:decimal", "xs:boolean", "CustomType");
        union.setMemberTypes(newTypes);

        assertEquals(3, union.getMemberTypes().size());
        assertTrue(union.getMemberTypes().contains("xs:decimal"));
        assertTrue(union.getMemberTypes().contains("xs:boolean"));
        assertTrue(union.getMemberTypes().contains("CustomType"));
        assertFalse(union.getMemberTypes().contains("xs:integer"));
        assertFalse(union.getMemberTypes().contains("xs:string"));
    }

    @Test
    @DisplayName("setMemberTypes() should fire PropertyChangeEvent")
    void testSetMemberTypesFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("memberTypes", evt.getPropertyName());
            eventFired.set(true);
        };

        union.addPropertyChangeListener(listener);
        union.setMemberTypes(List.of("xs:string", "xs:integer"));

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setMemberTypes() with empty list should clear member types")
    void testSetMemberTypesEmpty() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");

        union.setMemberTypes(List.of());

        assertTrue(union.getMemberTypes().isEmpty());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");

        XsdUnion copy = (XsdUnion) union.deepCopy(null);

        assertNotNull(copy);
        assertEquals(union.getMemberTypes().size(), copy.getMemberTypes().size());
        assertTrue(copy.getMemberTypes().contains("xs:integer"));
        assertTrue(copy.getMemberTypes().contains("xs:string"));
        assertNotSame(union, copy);
        assertNotEquals(union.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change union name")
    void testDeepCopyWithSuffix() {
        union.addMemberType("xs:decimal");

        XsdUnion copy = (XsdUnion) union.deepCopy("_Copy");

        // Union name is always "union", suffix should not be applied
        assertEquals("union", copy.getName());
        assertEquals(union.getMemberTypes().size(), copy.getMemberTypes().size());
    }

    @Test
    @DisplayName("deepCopy() should handle empty member types")
    void testDeepCopyWithEmptyMemberTypes() {
        // union has no member types
        XsdUnion copy = (XsdUnion) union.deepCopy(null);

        assertNotNull(copy);
        assertTrue(copy.getMemberTypes().isEmpty());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        union.addMemberType("xs:integer");

        // Union can have inline simpleType as child
        XsdSimpleType inlineType = new XsdSimpleType("InlineType");
        union.addChild(inlineType);

        XsdUnion copy = (XsdUnion) union.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdSimpleType);
        assertNotSame(inlineType, copy.getChildren().get(0));
    }

    @Test
    @DisplayName("deepCopy() should create independent member types list")
    void testDeepCopyIndependentList() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");

        XsdUnion copy = (XsdUnion) union.deepCopy(null);

        // Modify original
        union.addMemberType("xs:decimal");

        // Copy should not be affected
        assertEquals(2, copy.getMemberTypes().size());
        assertEquals(3, union.getMemberTypes().size());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("union should work in simpleType context")
    void testUnionInSimpleType() {
        XsdSimpleType simpleType = new XsdSimpleType("NumberOrStringType");

        XsdUnion unionDef = new XsdUnion("xs:integer", "xs:string");
        unionDef.setParent(simpleType);
        simpleType.addChild(unionDef);

        assertEquals(simpleType, unionDef.getParent());
        assertEquals(1, simpleType.getChildren().size());
        assertEquals(unionDef, simpleType.getChildren().get(0));
    }

    @Test
    @DisplayName("union with inline simpleTypes")
    void testUnionWithInlineSimpleTypes() {
        XsdUnion unionDef = new XsdUnion();

        // Instead of member type references, define inline simpleTypes
        XsdSimpleType inlineType1 = new XsdSimpleType();
        XsdRestriction restriction1 = new XsdRestriction("xs:integer");
        restriction1.addFacet(new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0"));
        inlineType1.addChild(restriction1);

        XsdSimpleType inlineType2 = new XsdSimpleType();
        XsdRestriction restriction2 = new XsdRestriction("xs:string");
        restriction2.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]+"));
        inlineType2.addChild(restriction2);

        unionDef.addChild(inlineType1);
        unionDef.addChild(inlineType2);

        assertEquals(2, unionDef.getChildren().size());
        assertTrue(unionDef.getChildren().get(0) instanceof XsdSimpleType);
        assertTrue(unionDef.getChildren().get(1) instanceof XsdSimpleType);
    }

    // ========== Realistic XSD Union Examples ==========

    @Test
    @DisplayName("create union of integers and strings")
    void testUnionOfIntegersAndStrings() {
        XsdUnion intOrString = new XsdUnion("xs:integer", "xs:string");

        assertEquals(2, intOrString.getMemberTypes().size());
        assertTrue(intOrString.getMemberTypes().contains("xs:integer"));
        assertTrue(intOrString.getMemberTypes().contains("xs:string"));
        assertEquals("union", intOrString.getName());
        assertEquals(XsdNodeType.UNION, intOrString.getNodeType());
    }

    @Test
    @DisplayName("create union of numeric types")
    void testUnionOfNumericTypes() {
        XsdUnion numericUnion = new XsdUnion("xs:integer", "xs:decimal", "xs:double");

        assertEquals(3, numericUnion.getMemberTypes().size());
        assertTrue(numericUnion.getMemberTypes().contains("xs:integer"));
        assertTrue(numericUnion.getMemberTypes().contains("xs:decimal"));
        assertTrue(numericUnion.getMemberTypes().contains("xs:double"));
    }

    @Test
    @DisplayName("create union with custom types")
    void testUnionWithCustomTypes() {
        // Union of custom simple types (e.g., PhoneNumber or Email)
        XsdUnion contactUnion = new XsdUnion("PhoneNumberType", "EmailType");

        assertEquals(2, contactUnion.getMemberTypes().size());
        assertTrue(contactUnion.getMemberTypes().contains("PhoneNumberType"));
        assertTrue(contactUnion.getMemberTypes().contains("EmailType"));
    }

    @Test
    @DisplayName("union represents multiple allowed types")
    void testUnionSemantics() {
        // Example: <value>123</value> OR <value>abc</value>
        // Where value is defined as: <xs:union memberTypes="xs:integer xs:string"/>
        XsdUnion valueUnion = new XsdUnion("xs:integer", "xs:string");

        assertEquals(2, valueUnion.getMemberTypes().size());
        // In actual XML, value could be "123" (validates as integer)
        // or "abc" (validates as string)
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("memberTypes".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        union.addPropertyChangeListener(listener);
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        union.removeMemberType("xs:integer");

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("addMemberType() multiple times should accumulate")
    void testAddMemberTypeAccumulates() {
        union.addMemberType("xs:integer");
        assertEquals(1, union.getMemberTypes().size());

        union.addMemberType("xs:string");
        assertEquals(2, union.getMemberTypes().size());

        union.addMemberType("xs:decimal");
        assertEquals(3, union.getMemberTypes().size());
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        String toString = union.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("union name should always be 'union'")
    void testUnionNameAlwaysUnion() {
        assertEquals("union", union.getName());

        union.addMemberType("xs:string");
        assertEquals("union", union.getName());

        // Even after changing properties, name stays "union"
        XsdUnion anotherUnion = new XsdUnion("xs:integer", "xs:decimal");
        assertEquals("union", anotherUnion.getName());
    }

    @Test
    @DisplayName("union with namespace prefix in member types")
    void testUnionWithNamespacePrefix() {
        union.addMemberType("tns:CustomType1");
        union.addMemberType("tns:CustomType2");
        union.addMemberType("xs:string");

        assertEquals(3, union.getMemberTypes().size());
        assertTrue(union.getMemberTypes().contains("tns:CustomType1"));
        assertTrue(union.getMemberTypes().contains("tns:CustomType2"));
        assertTrue(union.getMemberTypes().contains("xs:string"));
    }

    @Test
    @DisplayName("union can have duplicate member types")
    void testUnionWithDuplicates() {
        // XSD allows duplicate member types (though not recommended)
        union.addMemberType("xs:integer");
        union.addMemberType("xs:integer");

        assertEquals(2, union.getMemberTypes().size());
        assertEquals("xs:integer", union.getMemberTypes().get(0));
        assertEquals("xs:integer", union.getMemberTypes().get(1));
    }

    @Test
    @DisplayName("removeMemberType() should remove first occurrence only")
    void testRemoveMemberTypeFirstOccurrence() {
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        union.addMemberType("xs:integer");

        union.removeMemberType("xs:integer");

        // Should have 2 items: xs:string and xs:integer (the second one)
        assertEquals(2, union.getMemberTypes().size());
        assertEquals("xs:string", union.getMemberTypes().get(0));
        assertEquals("xs:integer", union.getMemberTypes().get(1));
    }
}
