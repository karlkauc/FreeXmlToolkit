package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.fxt.freexmltoolkit.domain.IdentityConstraint;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void selectorsAndFieldsWithPrefixesDescendantsWildcardsAndUnionsAreResolved() {
        // SIRI .//siri:KeyValue and siri:Values/siri:*, Garmin tc2:Folder with @Name: the literal paths never matched
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();
        XsdExtendedElement box = node(elementMap, "/Root/Box", "Box");
        IdentityConstraint descendants = new IdentityConstraint(IdentityConstraint.Type.UNIQUE, "keys");
        descendants.setSelector(".//t:Item");
        descendants.addField("t:Code");
        box.setIdentityConstraints(List.of(descendants));
        node(elementMap, "/Root/Box/SEQUENCE_1", "SEQUENCE");
        node(elementMap, "/Root/Box/SEQUENCE_1/Group", "Group");
        node(elementMap, "/Root/Box/SEQUENCE_1/Group/Item", "Item");
        node(elementMap, "/Root/Box/SEQUENCE_1/Group/Item/Code", "Code");

        XsdExtendedElement folders = node(elementMap, "/Root/Folders", "Folders");
        IdentityConstraint names = new IdentityConstraint(IdentityConstraint.Type.UNIQUE, "names");
        names.setSelector("t:Folder");
        names.addField("@Name");
        folders.setIdentityConstraints(List.of(names));
        node(elementMap, "/Root/Folders/Folder", "Folder");
        node(elementMap, "/Root/Folders/Folder/@Name", "@Name");

        XsdExtendedElement values = node(elementMap, "/Root/Values", "Values");
        IdentityConstraint codes = new IdentityConstraint(IdentityConstraint.Type.UNIQUE, "codes");
        codes.setSelector("t:*|t:Size");
        codes.addField("t:TypeCode");
        values.setIdentityConstraints(List.of(codes));
        node(elementMap, "/Root/Values/Colour", "Colour");
        node(elementMap, "/Root/Values/Colour/TypeCode", "TypeCode");
        node(elementMap, "/Root/Values/Size", "Size");
        node(elementMap, "/Root/Values/Size/TypeCode", "TypeCode");

        tracker.scanConstraints(elementMap);

        assertTrue(tracker.isConstrainedField("/Root/Box/SEQUENCE_1/Group/Item/Code"));
        assertTrue(tracker.isConstrainedField("/Root/Folders/Folder/@Name"));
        assertTrue(tracker.isConstrainedField("/Root/Values/Colour/TypeCode"));
        assertTrue(tracker.isConstrainedField("/Root/Values/Size/TypeCode"));
    }

    /** Adds an element to the map and to its parent's children. */
    private static XsdExtendedElement node(Map<String, XsdExtendedElement> elementMap, String xpath, String name) {
        XsdExtendedElement element = new XsdExtendedElement();
        element.setCurrentXpath(xpath);
        element.setElementName(name);
        String parent = xpath.substring(0, xpath.lastIndexOf('/'));
        element.setParentXpath(parent.isEmpty() ? null : parent);
        if (elementMap.containsKey(parent)) {
            elementMap.get(parent).addChild(xpath);
        }
        elementMap.put(xpath, element);
        return element;
    }

    @Test
    void repeatedPatternKeyValuesAreDistinctAndMatchThePattern() {
        Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
        XsdExtendedElement field = elementMap.get("/Root/Items/Item/ItemID");
        // XTCE NameType: the same sampled name for every container broke containerNameKey
        field.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo("normalizedString",
                Map.of("pattern", List.of("[^./:\\[\\] ]+"))));
        tracker.scanConstraints(elementMap);

        Set<String> values = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            String value = tracker.getUniqueValue("/Root/Items/Item/ItemID", "Name1", field);
            assertTrue(value.matches("[^./:\\[\\] ]+"), "matches the pattern: " + value);
            values.add(value);
        }
        assertEquals(5, values.size(), "distinct key values: " + values);
    }

    @Test
    void repeatedTypedKeyValuesKeepTheirType() throws Exception {
        javax.xml.datatype.DatatypeFactory datatypes = javax.xml.datatype.DatatypeFactory.newInstance();
        // Garmin Activity Id is an xsd:dateTime key; a suffix "_1" made it invalid
        for (String base : List.of("2026-07-05T22:25:59", "2026-07-05T22:25:40Z", "22:25:59", "2026-07-05", "12.5")) {
            tracker = new IdentityConstraintTracker();
            Map<String, XsdExtendedElement> elementMap = buildSimpleKeyMap();
            XsdExtendedElement field = elementMap.get("/Root/Items/Item/ItemID");
            tracker.scanConstraints(elementMap);

            Set<String> values = new HashSet<>();
            for (int i = 0; i < 3; i++) {
                String value = tracker.getUniqueValue("/Root/Items/Item/ItemID", base, field);
                if (base.contains("-") || base.contains(":")) {
                    assertDoesNotThrow(() -> datatypes.newXMLGregorianCalendar(value), "a date or dateTime: " + value);
                } else {
                    assertDoesNotThrow(() -> new java.math.BigDecimal(value), "a decimal: " + value);
                }
                values.add(value);
            }
            assertEquals(3, values.size(), "distinct key values for " + base + ": " + values);
        }
    }

    @Test
    void anIncrementedDateTimeKeepsItsSeconds() {
        assertEquals("2026-07-05T22:26:00", IdentityConstraintTracker.incrementedTypedValue("2026-07-05T22:25:59", 1));
        assertEquals("2026-07-05T22:26:00+02:00",
                IdentityConstraintTracker.incrementedTypedValue("2026-07-05T22:25:59+02:00", 1));
        assertEquals("22:26:00", IdentityConstraintTracker.incrementedTypedValue("22:25:59", 1));
        assertEquals("2026-07-06", IdentityConstraintTracker.incrementedTypedValue("2026-07-05", 1));
    }

    @Test
    void aKeyrefBeforeItsKeyReservesTheKeysFirstValue() {
        // Garmin lists CourseNameRef in Folders before the Courses; a suffixed fallback matched no course name
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();
        XsdExtendedElement root = createElementWithChildren("/Root");
        IdentityConstraint key = new IdentityConstraint(IdentityConstraint.Type.KEY, "courseNameKey");
        key.setSelector("Courses/Course");
        key.addField("Name");
        IdentityConstraint keyref = new IdentityConstraint(IdentityConstraint.Type.KEYREF, "courseNameKeyRef");
        keyref.setSelector("Folders/CourseNameRef");
        keyref.addField("Id");
        keyref.setRefer("courseNameKey");
        root.setIdentityConstraints(List.of(key, keyref));
        elementMap.put("/Root", root);
        elementMap.put("/Root/Folders", createElementWithChildren("/Root/Folders"));
        elementMap.put("/Root/Folders/CourseNameRef", createElementWithChildren("/Root/Folders/CourseNameRef"));
        elementMap.put("/Root/Folders/CourseNameRef/Id",
                createLeafElement("/Root/Folders/CourseNameRef/Id", "xs:token"));
        elementMap.put("/Root/Courses", createElementWithChildren("/Root/Courses"));
        elementMap.put("/Root/Courses/Course", createElementWithChildren("/Root/Courses/Course"));
        elementMap.put("/Root/Courses/Course/Name", createLeafElement("/Root/Courses/Course/Name", "xs:token"));
        tracker.scanConstraints(elementMap);

        String firstRef = tracker.getUniqueValue("/Root/Folders/CourseNameRef/Id", "aaaa",
                elementMap.get("/Root/Folders/CourseNameRef/Id"));
        String secondRef = tracker.getUniqueValue("/Root/Folders/CourseNameRef/Id", "aaaa",
                elementMap.get("/Root/Folders/CourseNameRef/Id"));
        String firstName = tracker.getUniqueValue("/Root/Courses/Course/Name", "aaaa",
                elementMap.get("/Root/Courses/Course/Name"));
        String secondName = tracker.getUniqueValue("/Root/Courses/Course/Name", "aaaa",
                elementMap.get("/Root/Courses/Course/Name"));

        assertEquals(firstRef, secondRef, "keyrefs before the key share one reserved value");
        assertEquals(firstRef, firstName, "the key takes the reserved value first");
        assertNotEquals(firstName, secondName, "later key values stay unique");
    }

    @Test
    void numericValuesOfOneConstraintNeverRepeatAcrossFieldPaths() {
        // Garmin StepIdMustBeUnique selects .//* with the field StepId: Step/StepId and Child/StepId advance different
        // random bases with one counter, so 7 + 1 met 8 + 0
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();
        XsdExtendedElement workout = createElementWithChildren("/Root/Workout");
        IdentityConstraint unique = new IdentityConstraint(IdentityConstraint.Type.UNIQUE, "StepIdMustBeUnique");
        unique.setSelector("Step|Step/Child");
        unique.addField("StepId");
        workout.setIdentityConstraints(List.of(unique));
        elementMap.put("/Root/Workout", workout);
        elementMap.put("/Root/Workout/Step", createElementWithChildren("/Root/Workout/Step"));
        elementMap.put("/Root/Workout/Step/StepId",
                createLeafElement("/Root/Workout/Step/StepId", "xs:positiveInteger"));
        elementMap.put("/Root/Workout/Step/Child", createElementWithChildren("/Root/Workout/Step/Child"));
        elementMap.put("/Root/Workout/Step/Child/StepId",
                createLeafElement("/Root/Workout/Step/Child/StepId", "xs:positiveInteger"));
        tracker.scanConstraints(elementMap);

        List<String> values = new ArrayList<>();
        for (String[] call : new String[][]{{"Step", "7"}, {"Step/Child", "6"}, {"Step/Child", "6"}, {"Step", "7"}}) {
            String xpath = "/Root/Workout/" + call[0] + "/StepId";
            values.add(tracker.getUniqueValue(xpath, call[1], elementMap.get(xpath)));
        }
        assertEquals(values.size(), new HashSet<>(values).size(), "distinct values: " + values);
    }

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
