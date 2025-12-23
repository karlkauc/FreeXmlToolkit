package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RepeatingElementsTable class, especially column order merging.
 *
 * @author Claude Code
 * @since 2.0
 */
class RepeatingElementsTableTest {

    @BeforeEach
    void setUp() {
        // Clear caches before each test to ensure clean state
        RepeatingElementsTable.clearAllCaches();
    }

    // ==================== Column Order Tests ====================

    /**
     * Tests that columns are ordered correctly when different elements have different children.
     *
     * This is the main use case from the bug report:
     * - Element 1: UniqueID, Currency, Country, Name, AssetType, AssetDetails
     * - Element 2: UniqueID, Identifiers, Currency, Country, Name, AssetType, AssetDetails, Securitized
     *
     * Expected order: UniqueID, Identifiers, Currency, Country, Name, AssetType, AssetDetails, Securitized
     * (Identifiers should appear after UniqueID because that's where it appears in Element 2)
     */
    @Test
    void testColumnOrderWithDifferentChildren() {
        // Create first element: UniqueID, Currency, Country, Name, AssetType, AssetDetails
        XmlElement asset1 = new XmlElement("Asset");
        asset1.addChild(createElementWithText("UniqueID", "ID_24347801"));
        asset1.addChild(createElementWithText("Currency", "EUR"));
        asset1.addChild(createElementWithText("Country", "AT"));
        asset1.addChild(createElementWithText("Name", "EUR Account"));
        asset1.addChild(createElementWithText("AssetType", "AC"));
        asset1.addChild(createElementWithText("AssetDetails", "..."));

        // Create second element: UniqueID, Identifiers, Currency, Country, Name, AssetType, AssetDetails, Securitized
        XmlElement asset2 = new XmlElement("Asset");
        asset2.addChild(createElementWithText("UniqueID", "ID_44268701"));
        asset2.addChild(createElementWithText("Identifiers", "ISIN"));  // New element
        asset2.addChild(createElementWithText("Currency", "EUR"));
        asset2.addChild(createElementWithText("Country", "AT"));
        asset2.addChild(createElementWithText("Name", "Bond"));
        asset2.addChild(createElementWithText("AssetType", "BO"));
        asset2.addChild(createElementWithText("AssetDetails", "..."));
        asset2.addChild(createElementWithText("Securitized", "false"));  // New element at end

        List<XmlElement> elements = List.of(asset1, asset2);

        // Create the table
        RepeatingElementsTable table = new RepeatingElementsTable("Asset", elements, null, 0, () -> {});

        // Get column names in order
        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        // Verify "Identifiers" appears after "UniqueID" (position 1), not at the end
        int uniqueIdIndex = columnNames.indexOf("UniqueID");
        int identifiersIndex = columnNames.indexOf("Identifiers");
        int currencyIndex = columnNames.indexOf("Currency");
        int securitizedIndex = columnNames.indexOf("Securitized");

        assertEquals(0, uniqueIdIndex, "UniqueID should be first");
        assertEquals(1, identifiersIndex, "Identifiers should be after UniqueID");
        assertEquals(2, currencyIndex, "Currency should be after Identifiers");
        assertTrue(securitizedIndex > columnNames.indexOf("AssetDetails"),
                "Securitized should be after AssetDetails");
        assertEquals(columnNames.size() - 1, securitizedIndex,
                "Securitized should be last (it appears last in Element 2)");
    }

    /**
     * Tests column order when all elements have the same children (simple case).
     */
    @Test
    void testColumnOrderWithIdenticalChildren() {
        // Create elements with identical children
        XmlElement item1 = new XmlElement("Item");
        item1.addChild(createElementWithText("Name", "Item1"));
        item1.addChild(createElementWithText("Price", "10.00"));
        item1.addChild(createElementWithText("Quantity", "5"));

        XmlElement item2 = new XmlElement("Item");
        item2.addChild(createElementWithText("Name", "Item2"));
        item2.addChild(createElementWithText("Price", "20.00"));
        item2.addChild(createElementWithText("Quantity", "3"));

        List<XmlElement> elements = List.of(item1, item2);

        RepeatingElementsTable table = new RepeatingElementsTable("Item", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        assertEquals(List.of("Name", "Price", "Quantity"), columnNames);
    }

    /**
     * Tests column order with new columns interspersed throughout different elements.
     *
     * Element 1: A, C, E
     * Element 2: A, B, C, D, E, F
     *
     * Expected: A, B, C, D, E, F
     */
    @Test
    void testColumnOrderWithMultipleNewColumns() {
        XmlElement elem1 = new XmlElement("Test");
        elem1.addChild(createElementWithText("A", "1"));
        elem1.addChild(createElementWithText("C", "3"));
        elem1.addChild(createElementWithText("E", "5"));

        XmlElement elem2 = new XmlElement("Test");
        elem2.addChild(createElementWithText("A", "1"));
        elem2.addChild(createElementWithText("B", "2"));  // New
        elem2.addChild(createElementWithText("C", "3"));
        elem2.addChild(createElementWithText("D", "4"));  // New
        elem2.addChild(createElementWithText("E", "5"));
        elem2.addChild(createElementWithText("F", "6"));  // New

        List<XmlElement> elements = List.of(elem1, elem2);

        RepeatingElementsTable table = new RepeatingElementsTable("Test", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        assertEquals(List.of("A", "B", "C", "D", "E", "F"), columnNames);
    }

    /**
     * Tests column order when first element has more columns than subsequent elements.
     *
     * Element 1: A, B, C, D, E
     * Element 2: A, C, E
     *
     * Expected: A, B, C, D, E (first element defines the full order)
     */
    @Test
    void testColumnOrderFirstElementHasMore() {
        XmlElement elem1 = new XmlElement("Test");
        elem1.addChild(createElementWithText("A", "1"));
        elem1.addChild(createElementWithText("B", "2"));
        elem1.addChild(createElementWithText("C", "3"));
        elem1.addChild(createElementWithText("D", "4"));
        elem1.addChild(createElementWithText("E", "5"));

        XmlElement elem2 = new XmlElement("Test");
        elem2.addChild(createElementWithText("A", "1"));
        elem2.addChild(createElementWithText("C", "3"));
        elem2.addChild(createElementWithText("E", "5"));

        List<XmlElement> elements = List.of(elem1, elem2);

        RepeatingElementsTable table = new RepeatingElementsTable("Test", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        assertEquals(List.of("A", "B", "C", "D", "E"), columnNames);
    }

    /**
     * Tests column order with attributes and child elements mixed.
     * Attributes should come first (with @ prefix internally).
     */
    @Test
    void testColumnOrderWithAttributesAndChildren() {
        XmlElement elem1 = new XmlElement("Item");
        elem1.setAttribute("id", "1");
        elem1.setAttribute("type", "A");
        elem1.addChild(createElementWithText("Name", "Item1"));
        elem1.addChild(createElementWithText("Value", "100"));

        XmlElement elem2 = new XmlElement("Item");
        elem2.setAttribute("id", "2");
        elem2.setAttribute("status", "active");  // New attribute
        elem2.addChild(createElementWithText("Name", "Item2"));
        elem2.addChild(createElementWithText("Description", "Desc"));  // New child
        elem2.addChild(createElementWithText("Value", "200"));

        List<XmlElement> elements = List.of(elem1, elem2);

        RepeatingElementsTable table = new RepeatingElementsTable("Item", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        // Verify attribute columns come first
        int idIndex = columnNames.indexOf("id");
        int typeIndex = columnNames.indexOf("type");
        int statusIndex = columnNames.indexOf("status");
        int nameIndex = columnNames.indexOf("Name");

        assertTrue(idIndex >= 0, "id attribute should be present");
        assertTrue(typeIndex >= 0, "type attribute should be present");
        assertTrue(statusIndex >= 0, "status attribute should be present");
        assertTrue(nameIndex >= 0, "Name element should be present");

        // All attributes should come before child elements
        assertTrue(idIndex < nameIndex, "Attributes should come before child elements");
        assertTrue(typeIndex < nameIndex, "Attributes should come before child elements");
        assertTrue(statusIndex < nameIndex, "Attributes should come before child elements");
    }

    /**
     * Tests that the column order remains stable with caching.
     * After creating a table, creating another one should use the cached order.
     */
    @Test
    void testColumnOrderCaching() {
        // First batch
        XmlElement elem1 = new XmlElement("CachedItem");
        elem1.addChild(createElementWithText("A", "1"));
        elem1.addChild(createElementWithText("B", "2"));
        elem1.addChild(createElementWithText("C", "3"));

        RepeatingElementsTable table1 = new RepeatingElementsTable("CachedItem",
                List.of(elem1, elem1), null, 0, () -> {});

        List<String> firstOrder = table1.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        // Second batch with new column
        XmlElement elem2 = new XmlElement("CachedItem");
        elem2.addChild(createElementWithText("A", "1"));
        elem2.addChild(createElementWithText("D", "4"));  // New column between A and B in this element
        elem2.addChild(createElementWithText("B", "2"));
        elem2.addChild(createElementWithText("C", "3"));

        RepeatingElementsTable table2 = new RepeatingElementsTable("CachedItem",
                List.of(elem2, elem2), null, 0, () -> {});

        List<String> secondOrder = table2.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        // The original A, B, C should maintain their order
        // D should be inserted at the correct position (after A, before B)
        int aIndex = secondOrder.indexOf("A");
        int dIndex = secondOrder.indexOf("D");
        int bIndex = secondOrder.indexOf("B");

        assertEquals(0, aIndex, "A should remain first");
        assertTrue(dIndex > aIndex && dIndex < bIndex,
                "D should be inserted between A and B based on its position in the new element");
    }

    /**
     * Tests three elements with progressively more columns.
     *
     * Element 1: A, D
     * Element 2: A, B, D
     * Element 3: A, B, C, D
     *
     * Expected: A, B, C, D
     */
    @Test
    void testColumnOrderProgressiveAdditions() {
        XmlElement elem1 = new XmlElement("Progressive");
        elem1.addChild(createElementWithText("A", "1"));
        elem1.addChild(createElementWithText("D", "4"));

        XmlElement elem2 = new XmlElement("Progressive");
        elem2.addChild(createElementWithText("A", "1"));
        elem2.addChild(createElementWithText("B", "2"));
        elem2.addChild(createElementWithText("D", "4"));

        XmlElement elem3 = new XmlElement("Progressive");
        elem3.addChild(createElementWithText("A", "1"));
        elem3.addChild(createElementWithText("B", "2"));
        elem3.addChild(createElementWithText("C", "3"));
        elem3.addChild(createElementWithText("D", "4"));

        List<XmlElement> elements = List.of(elem1, elem2, elem3);

        RepeatingElementsTable table = new RepeatingElementsTable("Progressive", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        assertEquals(List.of("A", "B", "C", "D"), columnNames);
    }

    /**
     * Tests edge case with empty elements.
     */
    @Test
    void testColumnOrderWithEmptyElements() {
        XmlElement elem1 = new XmlElement("Empty");
        // No children

        XmlElement elem2 = new XmlElement("Empty");
        elem2.addChild(createElementWithText("A", "1"));
        elem2.addChild(createElementWithText("B", "2"));

        List<XmlElement> elements = List.of(elem1, elem2);

        RepeatingElementsTable table = new RepeatingElementsTable("Empty", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        assertEquals(List.of("A", "B"), columnNames);
    }

    /**
     * Tests the exact scenario from the bug report with AssetMasterData.
     */
    @Test
    void testAssetMasterDataScenario() {
        // First Asset: UniqueID, Currency, Country, Name, AssetType, AssetDetails
        XmlElement asset1 = new XmlElement("Asset");
        asset1.addChild(createElementWithText("UniqueID", "ID_24347801"));
        asset1.addChild(createElementWithText("Currency", "EUR"));
        asset1.addChild(createElementWithText("Country", "AT"));
        asset1.addChild(createElementWithText("Name", "EUR Account"));
        asset1.addChild(createElementWithText("AssetType", "AC"));
        XmlElement details1 = new XmlElement("AssetDetails");
        details1.addChild(createElementWithText("Account", "..."));
        asset1.addChild(details1);

        // Second Asset: UniqueID, Identifiers, Currency, Country, Name, AssetType, AssetDetails, Securitized
        XmlElement asset2 = new XmlElement("Asset");
        asset2.addChild(createElementWithText("UniqueID", "ID_44268701"));
        XmlElement identifiers = new XmlElement("Identifiers");
        identifiers.addChild(createElementWithText("ISIN", "AT0000A0X913"));
        asset2.addChild(identifiers);  // NEW - should be in position 2
        asset2.addChild(createElementWithText("Currency", "EUR"));
        asset2.addChild(createElementWithText("Country", "AT"));
        asset2.addChild(createElementWithText("Name", "KELAG-KAERNT.ELE.12-22MTN"));
        asset2.addChild(createElementWithText("AssetType", "BO"));
        XmlElement details2 = new XmlElement("AssetDetails");
        details2.addChild(createElementWithText("Bond", "..."));
        asset2.addChild(details2);
        asset2.addChild(createElementWithText("Securitized", "false"));  // NEW - should be at end

        List<XmlElement> elements = List.of(asset1, asset2);

        RepeatingElementsTable table = new RepeatingElementsTable("Asset", elements, null, 0, () -> {});

        List<String> columnNames = table.getColumns().stream()
                .map(RepeatingElementsTable.TableColumn::getName)
                .collect(Collectors.toList());

        // Print for debugging
        System.out.println("Column order: " + columnNames);

        // Verify the exact expected order
        List<String> expectedOrder = List.of(
                "UniqueID",
                "Identifiers",  // Should be in position 2, not at the end
                "Currency",
                "Country",
                "Name",
                "AssetType",
                "AssetDetails",
                "Securitized"   // Should be at the end
        );

        assertEquals(expectedOrder, columnNames,
                "Columns should be ordered according to their document order across all elements");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates an XmlElement with a text child.
     */
    private XmlElement createElementWithText(String name, String text) {
        XmlElement element = new XmlElement(name);
        element.addChild(new XmlText(text));
        return element;
    }
}
