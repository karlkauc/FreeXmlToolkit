package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdList.
 *
 * @since 2.0
 */
class XsdListTest {

    private XsdList list;

    @BeforeEach
    void setUp() {
        list = new XsdList();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdList l = new XsdList();
        assertEquals("list", l.getName());
        assertNull(l.getItemType());
    }

    @Test
    @DisplayName("constructor with itemType should set item type")
    void testConstructorWithItemType() {
        XsdList l = new XsdList("xs:integer");
        assertEquals("list", l.getName());
        assertEquals("xs:integer", l.getItemType());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return LIST")
    void testGetNodeType() {
        assertEquals(XsdNodeType.LIST, list.getNodeType());
    }

    // ========== ItemType Tests ==========

    @Test
    @DisplayName("itemType should be null by default")
    void testItemTypeDefaultValue() {
        assertNull(list.getItemType());
    }

    @Test
    @DisplayName("setItemType() should set item type")
    void testSetItemType() {
        list.setItemType("xs:string");
        assertEquals("xs:string", list.getItemType());
    }

    @Test
    @DisplayName("setItemType() should fire PropertyChangeEvent")
    void testSetItemTypeFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("itemType", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("xs:decimal", evt.getNewValue());
            eventFired.set(true);
        };

        list.addPropertyChangeListener(listener);
        list.setItemType("xs:decimal");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setItemType() should allow null value")
    void testSetItemTypeAllowsNull() {
        list.setItemType("xs:string");
        list.setItemType(null);
        assertNull(list.getItemType());
    }

    @Test
    @DisplayName("setItemType() should accept built-in types")
    void testSetItemTypeBuiltInTypes() {
        list.setItemType("xs:integer");
        assertEquals("xs:integer", list.getItemType());

        list.setItemType("xs:string");
        assertEquals("xs:string", list.getItemType());

        list.setItemType("xs:decimal");
        assertEquals("xs:decimal", list.getItemType());
    }

    @Test
    @DisplayName("setItemType() should accept custom type references")
    void testSetItemTypeCustomTypes() {
        list.setItemType("MyCustomType");
        assertEquals("MyCustomType", list.getItemType());

        list.setItemType("ns:CustomType");
        assertEquals("ns:CustomType", list.getItemType());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        list.setItemType("xs:integer");

        XsdList copy = (XsdList) list.deepCopy(null);

        assertNotNull(copy);
        assertEquals(list.getItemType(), copy.getItemType());
        assertEquals(list.getName(), copy.getName());
        assertNotSame(list, copy);
        assertNotEquals(list.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change list name")
    void testDeepCopyWithSuffix() {
        list.setItemType("xs:string");

        XsdList copy = (XsdList) list.deepCopy("_Copy");

        // List name is always "list", suffix should not be applied
        assertEquals("list", copy.getName());
        assertEquals(list.getItemType(), copy.getItemType());
    }

    @Test
    @DisplayName("deepCopy() should handle null itemType")
    void testDeepCopyWithNullItemType() {
        // list has null itemType
        XsdList copy = (XsdList) list.deepCopy(null);

        assertNotNull(copy);
        assertNull(copy.getItemType());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        list.setItemType("xs:integer");

        // List can have inline simpleType as child
        XsdSimpleType inlineType = new XsdSimpleType("InlineType");
        list.addChild(inlineType);

        XsdList copy = (XsdList) list.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdSimpleType);
        assertNotSame(inlineType, copy.getChildren().get(0));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("list should work in simpleType context")
    void testListInSimpleType() {
        XsdSimpleType simpleType = new XsdSimpleType("IntegerListType");

        XsdList listDef = new XsdList("xs:integer");
        listDef.setParent(simpleType);
        simpleType.addChild(listDef);

        assertEquals(simpleType, listDef.getParent());
        assertEquals(1, simpleType.getChildren().size());
        assertEquals(listDef, simpleType.getChildren().get(0));
    }

    @Test
    @DisplayName("list with inline simpleType")
    void testListWithInlineSimpleType() {
        XsdList listDef = new XsdList();

        // Instead of itemType reference, define inline simpleType
        XsdSimpleType inlineType = new XsdSimpleType();
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{2}"));

        inlineType.addChild(restriction);
        listDef.addChild(inlineType);

        assertEquals(1, listDef.getChildren().size());
        assertEquals(inlineType, listDef.getChildren().get(0));
        assertEquals(listDef, inlineType.getParent());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("list should support parent-child relationships")
    void testParentChildRelationships() {
        XsdSimpleType parent = new XsdSimpleType("MyListType");
        list.setParent(parent);
        parent.addChild(list);

        assertEquals(parent, list.getParent());
        assertTrue(parent.getChildren().contains(list));
    }

    // ========== Realistic XSD List Examples ==========

    @Test
    @DisplayName("create list of integers")
    void testListOfIntegers() {
        XsdList integerList = new XsdList("xs:integer");

        assertEquals("xs:integer", integerList.getItemType());
        assertEquals("list", integerList.getName());
        assertEquals(XsdNodeType.LIST, integerList.getNodeType());
    }

    @Test
    @DisplayName("create list of strings")
    void testListOfStrings() {
        XsdList stringList = new XsdList("xs:string");

        assertEquals("xs:string", stringList.getItemType());
    }

    @Test
    @DisplayName("create list with custom type")
    void testListWithCustomType() {
        // List of custom simple type (e.g., list of zip codes)
        XsdList zipCodeList = new XsdList("ZipCodeType");

        assertEquals("ZipCodeType", zipCodeList.getItemType());
    }

    @Test
    @DisplayName("list represents whitespace-separated values")
    void testListSemantics() {
        // Example: <sizes>small medium large</sizes>
        // Where sizes is defined as: <xs:list itemType="SizeType"/>
        XsdList sizeList = new XsdList("SizeType");

        assertEquals("SizeType", sizeList.getItemType());
        // In actual XML, values would be: "small medium large" (whitespace-separated)
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("itemType".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        list.addPropertyChangeListener(listener);
        list.setItemType("xs:string");
        list.setItemType("xs:integer");
        list.setItemType("xs:decimal");

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("setItemType() with different value should fire event")
    void testSetItemTypeDifferentValue() {
        list.setItemType("xs:string");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("itemType", evt.getPropertyName());
            assertEquals("xs:string", evt.getOldValue());
            assertEquals("xs:integer", evt.getNewValue());
            eventFired.set(true);
        };

        list.addPropertyChangeListener(listener);
        list.setItemType("xs:integer");

        assertTrue(eventFired.get(), "Event should fire when value changes");
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        list.setItemType("xs:integer");
        String toString = list.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("list name should always be 'list'")
    void testListNameAlwaysList() {
        assertEquals("list", list.getName());

        list.setItemType("xs:string");
        assertEquals("list", list.getName());

        // Even after changing properties, name stays "list"
        XsdList anotherList = new XsdList("xs:integer");
        assertEquals("list", anotherList.getName());
    }

    @Test
    @DisplayName("list with namespace prefix in itemType")
    void testListWithNamespacePrefix() {
        list.setItemType("tns:CustomSimpleType");
        assertEquals("tns:CustomSimpleType", list.getItemType());

        list.setItemType("xs:token");
        assertEquals("xs:token", list.getItemType());
    }
}
