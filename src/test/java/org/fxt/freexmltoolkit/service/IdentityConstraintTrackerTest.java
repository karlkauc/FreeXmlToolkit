package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdentityConstraintTracker.
 * Tests constraint scanning, unique value generation, and KEYREF resolution.
 */
class IdentityConstraintTrackerTest {

    private IdentityConstraintTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new IdentityConstraintTracker();
    }

    @Test
    void testEmptyElementMap() {
        tracker.scanConstraints(null);
        assertFalse(tracker.hasConstraints());

        tracker.scanConstraints(Map.of());
        assertFalse(tracker.hasConstraints());
    }

    @Test
    void testScanKeyConstraint() {
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();

        // Parent element with KEY constraint
        XsdExtendedElement parent = createElementWithChildren("/Root/Items");
        IdentityConstraint keyConstraint = new IdentityConstraint(IdentityConstraint.Type.KEY, "itemKey");
        keyConstraint.setSelector("Item");
        keyConstraint.addField("ItemID");
        parent.setIdentityConstraints(List.of(keyConstraint));
        elementMap.put("/Root/Items", parent);

        // Child element matching selector
        XsdExtendedElement item = createElementWithChildren("/Root/Items/Item");
        elementMap.put("/Root/Items/Item", item);

        // Field element
        XsdExtendedElement itemId = createLeafElement("/Root/Items/Item/ItemID", "xs:string");
        elementMap.put("/Root/Items/Item/ItemID", itemId);

        tracker.scanConstraints(elementMap);

        assertTrue(tracker.hasConstraints());
        assertTrue(tracker.isConstrainedField("/Root/Items/Item/ItemID"));
        assertFalse(tracker.isKeyrefField("/Root/Items/Item/ItemID"));
        assertFalse(tracker.isConstrainedField("/Root/Items/Item")); // not a field
    }

    @Test
    void testUniqueValuesForKeyConstraint() {
        Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
        tracker.scanConstraints(elementMap);

        XsdExtendedElement field = elementMap.get("/Root/Items/Item/ItemID");

        String val1 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "ABC", field);
        String val2 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "ABC", field);
        String val3 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "ABC", field);

        // All values should be unique
        Set<String> values = Set.of(val1, val2, val3);
        assertEquals(3, values.size(), "All generated values should be unique");

        // Each should contain the base value
        assertTrue(val1.startsWith("ABC"), "Should start with base value: " + val1);
    }

    @Test
    void testUniqueValuesForNumericData() {
        Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
        tracker.scanConstraints(elementMap);

        XsdExtendedElement field = elementMap.get("/Root/Items/Item/ItemID");

        String val1 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "100", field);
        String val2 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "100", field);
        String val3 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "100", field);

        // Values should be unique numeric strings
        Set<String> values = Set.of(val1, val2, val3);
        assertEquals(3, values.size(), "All values should be unique");

        // All should be parseable as numbers
        for (String v : values) {
            assertDoesNotThrow(() -> Long.parseLong(v), "Should be numeric: " + v);
        }
    }

    @Test
    void testKeyrefResolution() {
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();

        // Parent element with KEY and KEYREF constraints
        XsdExtendedElement parent = createElementWithChildren("/Root");

        IdentityConstraint keyConstraint = new IdentityConstraint(IdentityConstraint.Type.KEY, "itemKey");
        keyConstraint.setSelector("StaticData/Item");
        keyConstraint.addField("ID");

        IdentityConstraint keyrefConstraint = new IdentityConstraint(IdentityConstraint.Type.KEYREF, "itemRef");
        keyrefConstraint.setSelector("DynamicData/Item");
        keyrefConstraint.addField("ID");
        keyrefConstraint.setRefer("itemKey");

        parent.setIdentityConstraints(List.of(keyConstraint, keyrefConstraint));
        elementMap.put("/Root", parent);

        // Static path
        elementMap.put("/Root/StaticData", createElementWithChildren("/Root/StaticData"));
        elementMap.put("/Root/StaticData/Item", createElementWithChildren("/Root/StaticData/Item"));
        elementMap.put("/Root/StaticData/Item/ID", createLeafElement("/Root/StaticData/Item/ID", "xs:string"));

        // Dynamic path
        elementMap.put("/Root/DynamicData", createElementWithChildren("/Root/DynamicData"));
        elementMap.put("/Root/DynamicData/Item", createElementWithChildren("/Root/DynamicData/Item"));
        elementMap.put("/Root/DynamicData/Item/ID", createLeafElement("/Root/DynamicData/Item/ID", "xs:string"));

        tracker.scanConstraints(elementMap);

        assertTrue(tracker.isConstrainedField("/Root/StaticData/Item/ID"));
        assertTrue(tracker.isKeyrefField("/Root/DynamicData/Item/ID"));

        // Generate KEY values first
        String keyVal1 = tracker.getUniqueValue("/Root/StaticData/Item/ID", "X", elementMap.get("/Root/StaticData/Item/ID"));
        String keyVal2 = tracker.getUniqueValue("/Root/StaticData/Item/ID", "X", elementMap.get("/Root/StaticData/Item/ID"));

        // KEYREF values should reference KEY values
        String refVal1 = tracker.getUniqueValue("/Root/DynamicData/Item/ID", "X", elementMap.get("/Root/DynamicData/Item/ID"));
        String refVal2 = tracker.getUniqueValue("/Root/DynamicData/Item/ID", "X", elementMap.get("/Root/DynamicData/Item/ID"));

        Set<String> keyValues = Set.of(keyVal1, keyVal2);
        assertTrue(keyValues.contains(refVal1), "KEYREF value should reference a KEY value: " + refVal1);
        assertTrue(keyValues.contains(refVal2), "KEYREF value should reference a KEY value: " + refVal2);
    }

    @Test
    void testMaxLengthRespected() {
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();

        XsdExtendedElement parent = createElementWithChildren("/Root/Items");
        IdentityConstraint keyConstraint = new IdentityConstraint(IdentityConstraint.Type.KEY, "shortKey");
        keyConstraint.setSelector("Item");
        keyConstraint.addField("Code");
        parent.setIdentityConstraints(List.of(keyConstraint));
        elementMap.put("/Root/Items", parent);

        elementMap.put("/Root/Items/Item", createElementWithChildren("/Root/Items/Item"));

        // Element with maxLength restriction
        XsdExtendedElement code = createLeafElement("/Root/Items/Item/Code", "xs:string");
        code.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo("xs:string",
                Map.of("maxLength", List.of("10"))));
        elementMap.put("/Root/Items/Item/Code", code);

        tracker.scanConstraints(elementMap);

        // Generate values with a long base
        for (int i = 0; i < 5; i++) {
            String value = tracker.getUniqueValue("/Root/Items/Item/Code", "LongBaseValue", code);
            assertTrue(value.length() <= 10, "Value should respect maxLength=10: " + value + " (length=" + value.length() + ")");
        }
    }

    @Test
    void testUnconstrainedFieldReturnsBaseValue() {
        Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
        tracker.scanConstraints(elementMap);

        XsdExtendedElement element = createLeafElement("/Root/Other", "xs:string");
        String result = tracker.getUniqueValue("/Root/Other", "hello", element);
        assertEquals("hello", result, "Unconstrained fields should return base value unchanged");
    }

    @Test
    void testUniqueConstraint() {
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();

        XsdExtendedElement parent = createElementWithChildren("/Root/Items");
        IdentityConstraint uniqueConstraint = new IdentityConstraint(IdentityConstraint.Type.UNIQUE, "uniqueCode");
        uniqueConstraint.setSelector("Item");
        uniqueConstraint.addField("Code");
        parent.setIdentityConstraints(List.of(uniqueConstraint));
        elementMap.put("/Root/Items", parent);

        elementMap.put("/Root/Items/Item", createElementWithChildren("/Root/Items/Item"));
        elementMap.put("/Root/Items/Item/Code", createLeafElement("/Root/Items/Item/Code", "xs:string"));

        tracker.scanConstraints(elementMap);

        assertTrue(tracker.isConstrainedField("/Root/Items/Item/Code"));
        assertFalse(tracker.isKeyrefField("/Root/Items/Item/Code"));

        String val1 = tracker.getUniqueValue("/Root/Items/Item/Code", "X", null);
        String val2 = tracker.getUniqueValue("/Root/Items/Item/Code", "X", null);
        assertNotEquals(val1, val2, "UNIQUE constraint should produce unique values");
    }

    @Test
    void testPathResolutionSkipsContainers() {
        // Test that path resolution correctly skips SEQUENCE/CHOICE/ALL containers
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();

        XsdExtendedElement root = createElementWithChildren("/Root");
        IdentityConstraint keyConstraint = new IdentityConstraint(IdentityConstraint.Type.KEY, "testKey");
        keyConstraint.setSelector("Items/Item");
        keyConstraint.addField("ID");
        root.setIdentityConstraints(List.of(keyConstraint));
        root.setChildren(List.of("/Root/SEQUENCE_1"));
        elementMap.put("/Root", root);

        // Items is inside a SEQUENCE container
        XsdExtendedElement seq = createElementWithChildren("/Root/SEQUENCE_1");
        seq.setElementName("SEQUENCE_1");
        seq.setChildren(List.of("/Root/SEQUENCE_1/Items"));
        elementMap.put("/Root/SEQUENCE_1", seq);

        XsdExtendedElement items = createElementWithChildren("/Root/SEQUENCE_1/Items");
        items.setElementName("Items");
        items.setChildren(List.of("/Root/SEQUENCE_1/Items/Item"));
        elementMap.put("/Root/SEQUENCE_1/Items", items);

        XsdExtendedElement item = createElementWithChildren("/Root/SEQUENCE_1/Items/Item");
        item.setElementName("Item");
        item.setChildren(List.of("/Root/SEQUENCE_1/Items/Item/ID"));
        elementMap.put("/Root/SEQUENCE_1/Items/Item", item);

        XsdExtendedElement id = createLeafElement("/Root/SEQUENCE_1/Items/Item/ID", "xs:string");
        id.setElementName("ID");
        elementMap.put("/Root/SEQUENCE_1/Items/Item/ID", id);

        tracker.scanConstraints(elementMap);

        // The constraint should resolve through the SEQUENCE container
        assertTrue(tracker.hasConstraints(), "Should find constraints through SEQUENCE containers");
    }

    @Test
    void testEmptyBaseSampleData() {
        Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
        tracker.scanConstraints(elementMap);

        String val1 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "", null);
        String val2 = tracker.getUniqueValue("/Root/Items/Item/ItemID", "", null);

        assertNotEquals(val1, val2);
        assertFalse(val1.isEmpty());
        assertFalse(val2.isEmpty());
    }

    @Test
    void testNullBaseSampleData() {
        Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
        tracker.scanConstraints(elementMap);

        String val1 = tracker.getUniqueValue("/Root/Items/Item/ItemID", null, null);
        String val2 = tracker.getUniqueValue("/Root/Items/Item/ItemID", null, null);

        assertNotEquals(val1, val2);
    }

    // --- Helper methods ---

    private Map<String, XsdExtendedElement> buildSimpleKeyMap() {
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();

        XsdExtendedElement parent = createElementWithChildren("/Root/Items");
        IdentityConstraint keyConstraint = new IdentityConstraint(IdentityConstraint.Type.KEY, "itemKey");
        keyConstraint.setSelector("Item");
        keyConstraint.addField("ItemID");
        parent.setIdentityConstraints(List.of(keyConstraint));
        elementMap.put("/Root/Items", parent);

        elementMap.put("/Root/Items/Item", createElementWithChildren("/Root/Items/Item"));
        elementMap.put("/Root/Items/Item/ItemID", createLeafElement("/Root/Items/Item/ItemID", "xs:string"));

        return elementMap;
    }

    private XsdExtendedElement createElementWithChildren(String xpath) {
        XsdExtendedElement elem = new XsdExtendedElement();
        elem.setCurrentXpath(xpath);
        String name = xpath.contains("/") ? xpath.substring(xpath.lastIndexOf('/') + 1) : xpath;
        elem.setElementName(name);
        elem.setChildren(new ArrayList<>());
        return elem;
    }

    private XsdExtendedElement createLeafElement(String xpath, String type) {
        XsdExtendedElement elem = new XsdExtendedElement();
        elem.setCurrentXpath(xpath);
        String name = xpath.contains("/") ? xpath.substring(xpath.lastIndexOf('/') + 1) : xpath;
        elem.setElementName(name);
        elem.setElementType(type);
        elem.setChildren(new ArrayList<>());
        return elem;
    }
}
