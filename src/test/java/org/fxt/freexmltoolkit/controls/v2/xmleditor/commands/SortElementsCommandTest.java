package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SortElementsCommand.
 */
@DisplayName("SortElementsCommand Tests")
class SortElementsCommandTest {

    private XmlElement parentElement;
    private List<XmlElement> testElements;

    @BeforeEach
    void setUp() {
        parentElement = new XmlElement("items");

        // Create test elements with different attribute values
        testElements = new ArrayList<>();

        XmlElement elem1 = new XmlElement("item");
        elem1.setAttribute("name", "Charlie");
        elem1.setAttribute("price", "30.00");
        parentElement.addChild(elem1);
        testElements.add(elem1);

        XmlElement elem2 = new XmlElement("item");
        elem2.setAttribute("name", "Alice");
        elem2.setAttribute("price", "10.00");
        parentElement.addChild(elem2);
        testElements.add(elem2);

        XmlElement elem3 = new XmlElement("item");
        elem3.setAttribute("name", "Bob");
        elem3.setAttribute("price", "20.00");
        parentElement.addChild(elem3);
        testElements.add(elem3);
    }

    @Nested
    @DisplayName("Sortability Detection")
    class SortabilityTests {

        @Test
        @DisplayName("Attribute columns should be sortable")
        void attributeColumnsShouldBeSortable() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            assertTrue(table.isColumnSortable("name"), "Attribute 'name' should be sortable");
            assertTrue(table.isColumnSortable("price"), "Attribute 'price' should be sortable");
        }

        @Test
        @DisplayName("Non-existent columns should not be sortable")
        void nonExistentColumnsShouldNotBeSortable() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            assertFalse(table.isColumnSortable("nonexistent"), "Non-existent column should not be sortable");
        }
    }

    @Nested
    @DisplayName("Data Type Detection")
    class DataTypeDetectionTests {

        @Test
        @DisplayName("Should detect numeric columns")
        void shouldDetectNumericColumns() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            assertEquals(RepeatingElementsTable.ColumnDataType.NUMERIC,
                    table.detectColumnDataType("price"),
                    "Price column should be detected as NUMERIC");
        }

        @Test
        @DisplayName("Should detect string columns")
        void shouldDetectStringColumns() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            assertEquals(RepeatingElementsTable.ColumnDataType.STRING,
                    table.detectColumnDataType("name"),
                    "Name column should be detected as STRING");
        }

        @Test
        @DisplayName("Should detect date columns")
        void shouldDetectDateColumns() {
            // Create elements with date values
            XmlElement parent = new XmlElement("events");
            List<XmlElement> dateElements = new ArrayList<>();

            XmlElement e1 = new XmlElement("event");
            e1.setAttribute("date", "2024-01-15");
            parent.addChild(e1);
            dateElements.add(e1);

            XmlElement e2 = new XmlElement("event");
            e2.setAttribute("date", "2024-03-20");
            parent.addChild(e2);
            dateElements.add(e2);

            RepeatingElementsTable table = new RepeatingElementsTable("event", dateElements, null, 0, () -> {});

            assertEquals(RepeatingElementsTable.ColumnDataType.DATE,
                    table.detectColumnDataType("date"),
                    "Date column should be detected as DATE");
        }
    }

    @Nested
    @DisplayName("Sorting Execution")
    class SortingExecutionTests {

        @Test
        @DisplayName("Should sort by string column ascending")
        void shouldSortByStringColumnAscending() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            SortElementsCommand cmd = new SortElementsCommand(table, "name", true);
            boolean result = cmd.execute();

            assertTrue(result, "Command should execute successfully");

            // Check order: Alice, Bob, Charlie
            List<?> children = parentElement.getChildren();
            assertEquals("Alice", ((XmlElement) children.get(0)).getAttribute("name"));
            assertEquals("Bob", ((XmlElement) children.get(1)).getAttribute("name"));
            assertEquals("Charlie", ((XmlElement) children.get(2)).getAttribute("name"));
        }

        @Test
        @DisplayName("Should sort by string column descending")
        void shouldSortByStringColumnDescending() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            SortElementsCommand cmd = new SortElementsCommand(table, "name", false);
            boolean result = cmd.execute();

            assertTrue(result, "Command should execute successfully");

            // Check order: Charlie, Bob, Alice
            List<?> children = parentElement.getChildren();
            assertEquals("Charlie", ((XmlElement) children.get(0)).getAttribute("name"));
            assertEquals("Bob", ((XmlElement) children.get(1)).getAttribute("name"));
            assertEquals("Alice", ((XmlElement) children.get(2)).getAttribute("name"));
        }

        @Test
        @DisplayName("Should sort by numeric column ascending")
        void shouldSortByNumericColumnAscending() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            SortElementsCommand cmd = new SortElementsCommand(table, "price", true);
            boolean result = cmd.execute();

            assertTrue(result, "Command should execute successfully");

            // Check order: 10.00 (Alice), 20.00 (Bob), 30.00 (Charlie)
            List<?> children = parentElement.getChildren();
            assertEquals("10.00", ((XmlElement) children.get(0)).getAttribute("price"));
            assertEquals("20.00", ((XmlElement) children.get(1)).getAttribute("price"));
            assertEquals("30.00", ((XmlElement) children.get(2)).getAttribute("price"));
        }
    }

    @Nested
    @DisplayName("Undo/Redo")
    class UndoRedoTests {

        @Test
        @DisplayName("Should undo sorting and restore original order")
        void shouldUndoSorting() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            // Remember original order
            String originalFirst = ((XmlElement) parentElement.getChildren().get(0)).getAttribute("name");
            String originalSecond = ((XmlElement) parentElement.getChildren().get(1)).getAttribute("name");
            String originalThird = ((XmlElement) parentElement.getChildren().get(2)).getAttribute("name");

            // Sort
            SortElementsCommand cmd = new SortElementsCommand(table, "name", true);
            cmd.execute();

            // Undo
            boolean undoResult = cmd.undo();
            assertTrue(undoResult, "Undo should succeed");

            // Check original order is restored
            List<?> children = parentElement.getChildren();
            assertEquals(originalFirst, ((XmlElement) children.get(0)).getAttribute("name"));
            assertEquals(originalSecond, ((XmlElement) children.get(1)).getAttribute("name"));
            assertEquals(originalThird, ((XmlElement) children.get(2)).getAttribute("name"));
        }

        @Test
        @DisplayName("Should not undo if not executed")
        void shouldNotUndoIfNotExecuted() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            SortElementsCommand cmd = new SortElementsCommand(table, "name", true);
            // Don't execute

            boolean undoResult = cmd.undo();
            assertFalse(undoResult, "Undo should fail if not executed");
        }
    }

    @Nested
    @DisplayName("Description")
    class DescriptionTests {

        @Test
        @DisplayName("Should return correct description for ascending sort")
        void shouldReturnAscendingDescription() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            SortElementsCommand cmd = new SortElementsCommand(table, "name", true);

            assertTrue(cmd.getDescription().contains("ascending"));
            assertTrue(cmd.getDescription().contains("name"));
        }

        @Test
        @DisplayName("Should return correct description for descending sort")
        void shouldReturnDescendingDescription() {
            RepeatingElementsTable table = new RepeatingElementsTable("item", testElements, null, 0, () -> {});

            SortElementsCommand cmd = new SortElementsCommand(table, "price", false);

            assertTrue(cmd.getDescription().contains("descending"));
            assertTrue(cmd.getDescription().contains("price"));
        }
    }
}
