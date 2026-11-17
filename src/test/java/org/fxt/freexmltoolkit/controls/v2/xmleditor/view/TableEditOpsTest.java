package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.CompositeXmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Whole-row and whole-column operations on a repeating group (table display). */
class TableEditOpsTest {

    private static final String XML = """
            <items>
              <item id="1"><name>a</name><qty>1</qty></item>
              <item id="2"><name>b</name></item>
              <item id="3"><name>c</name><qty>3</qty></item>
            </items>
            """;

    private XmlEditorContext context;
    private XmlElement items;
    private RepeatingElementsTable table;

    @BeforeEach
    void setUp() {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        items = context.getDocument().getRootElement();
        table = new RepeatingElementsTable("item", items.getChildElements("item"), 1, () -> { });
    }

    @Test
    void insertRowCreatesAMemberWithEveryColumnEmpty() {
        XmlCommand cmd = TableEditOps.insertRow(table, 1, TableEditOps.Placement.AFTER);
        assertNotNull(cmd);
        assertTrue(context.executeCommand(cmd));
        List<XmlElement> members = items.getChildElements("item");
        assertEquals(4, members.size());
        XmlElement fresh = members.get(2);
        assertEquals("", fresh.getAttribute("id"), "attribute column present but empty");
        assertNotNull(fresh.getChildElement("name"));
        assertNotNull(fresh.getChildElement("qty"));
        assertEquals("", fresh.getChildElement("name").getTextContent());

        context.undo();
        assertEquals(3, items.getChildElements("item").size(), "one undo step removes the row");
    }

    @Test
    void insertRowBeforeAndAppendPlaceTheRowCorrectly() {
        context.executeCommand(TableEditOps.insertRow(table, 0, TableEditOps.Placement.BEFORE));
        assertEquals("", items.getChildElements("item").get(0).getAttribute("id"), "inserted before the first");
        context.executeCommand(TableEditOps.insertRow(table, 0, TableEditOps.Placement.APPEND));
        List<XmlElement> members = items.getChildElements("item");
        assertEquals(5, members.size());
        assertEquals("", members.get(4).getAttribute("id"), "appended after the last");
        assertEquals("3", members.get(3).getAttribute("id"));
    }

    @Test
    void insertColumnAddsAnElementToEveryMemberNextToTheReference() {
        CompositeXmlCommand cmd = TableEditOps.insertColumn(table, "price",
                RepeatingElementsTable.ColumnType.CHILD_ELEMENT, "name", TableEditOps.Placement.AFTER);
        assertEquals(3, cmd.getParts().size());
        assertTrue(context.executeCommand(cmd));
        for (XmlElement member : items.getChildElements("item")) {
            XmlElement price = member.getChildElement("price");
            assertNotNull(price, member.getAttribute("id"));
            assertEquals(member.indexOf(member.getChildElement("name")) + 1, member.indexOf(price),
                    "placed right after name");
        }
        context.undo();
        assertNull(items.getChildElements("item").get(0).getChildElement("price"), "one undo step");
    }

    @Test
    void insertAttributeColumnSkipsMembersThatHaveIt() {
        items.getChildElements("item").get(1).setAttribute("flag", "x");
        CompositeXmlCommand cmd = TableEditOps.insertColumn(table, "flag",
                RepeatingElementsTable.ColumnType.ATTRIBUTE, null, TableEditOps.Placement.AFTER);
        assertEquals(2, cmd.getParts().size());
        assertTrue(context.executeCommand(cmd));
        assertEquals("", items.getChildElements("item").get(0).getAttribute("flag"));
        assertEquals("x", items.getChildElements("item").get(1).getAttribute("flag"), "existing value kept");
    }

    @Test
    void renameColumnRenamesTheNodeInEveryMember() {
        CompositeXmlCommand cmd = TableEditOps.renameColumn(table, "name", "title");
        assertEquals(3, cmd.getParts().size());
        assertTrue(context.executeCommand(cmd));
        for (XmlElement member : items.getChildElements("item")) {
            assertNull(member.getChildElement("name"));
            assertNotNull(member.getChildElement("title"));
        }
        CompositeXmlCommand attr = TableEditOps.renameColumn(table, "id", "key");
        assertTrue(context.executeCommand(attr));
        assertEquals("2", items.getChildElements("item").get(1).getAttribute("key"));
        assertNull(items.getChildElements("item").get(1).getAttribute("id"));

        assertTrue(TableEditOps.renameColumn(table, "id", "id").isEmpty(), "same name = nothing to do");
        assertTrue(TableEditOps.renameColumn(table, "nope", "x").isEmpty(), "unknown column = nothing to do");
    }

    @Test
    void cellEditCreatesTheMissingChildElement() {
        XmlGridAdapter adapter = new XmlGridAdapter(context);
        assertTrue(adapter.commitCellEdit(table, 1, "qty", "7"));
        XmlElement second = items.getChildElements("item").get(1);
        assertNotNull(second.getChildElement("qty"), "the member lacked qty: created");
        assertEquals("7", second.getChildElement("qty").getTextContent());
        assertTrue(adapter.commitCellEdit(table, 0, "qty", "9"));
        assertEquals("9", items.getChildElements("item").get(0).getChildElement("qty").getTextContent());
    }

    @Test
    void missingChildElementsGoToTheirColumnPositionNotTheEnd() {
        context.loadDocumentFromString("""
                <items>
                  <item><a>1</a><b>1</b><c>1</c><d>1</d></item>
                  <item><a>2</a><d>2</d></item>
                </items>
                """);
        items = context.getDocument().getRootElement();
        RepeatingElementsTable.clearAllCaches();
        table = new RepeatingElementsTable("item", items.getChildElements("item"), 1, () -> { });
        XmlElement second = items.getChildElements("item").get(1);

        assertTrue(new XmlGridAdapter(context).commitCellEdit(table, 1, "c", "x"));
        assertEquals(List.of("a", "c", "d"), names(second), "typed value: before d, not after it");
        context.undo();

        // One paste adding two missing elements to the same record
        int bColumn = table.getColumns().indexOf(table.getColumn("b"));
        CompositeXmlCommand paste = TableEditOps.pasteCells(table, 1, bColumn, List.of(List.of("p", "q")));
        assertTrue(context.executeCommand(paste));
        assertEquals(List.of("a", "b", "c", "d"), names(second));
        assertEquals("p", second.getChildElement("b").getTextContent());
        context.undo();
        assertEquals(List.of("a", "d"), names(second), "one undo step");
    }

    private static List<String> names(XmlElement element) {
        return element.getChildElements().stream().map(XmlElement::getName).toList();
    }

    private int col(String name) {
        List<RepeatingElementsTable.TableColumn> columns = table.getColumns();
        for (int c = 0; c < columns.size(); c++) {
            if (columns.get(c).getName().equals(name)) {
                return c;
            }
        }
        throw new AssertionError("no column " + name);
    }

    @Test
    void deletingAnElementCellRemovesTheElementCompletely() {
        int qty = col("qty");
        CompositeXmlCommand cmd = TableEditOps.deleteCells(table, 0, qty, 0, qty, false);
        assertTrue(context.executeCommand(cmd));
        XmlElement first = items.getChildElements("item").get(0);
        assertNull(first.getChildElement("qty"), "the element is gone, not left empty");
        assertNotNull(first.getChildElement("name"), "the other cells stay");
        assertEquals(3, items.getChildElements("item").size(), "the row stays");

        context.undo();
        assertEquals("1", items.getChildElements("item").get(0).getChildElement("qty").getTextContent());
    }

    @Test
    void deletingAnAttributeCellRemovesTheAttribute() {
        int id = col("id");
        assertTrue(context.executeCommand(TableEditOps.deleteCells(table, 1, id, 1, id, false)));
        assertTrue(!items.getChildElements("item").get(1).hasAttribute("id"));
        assertTrue(items.getChildElements("item").get(0).hasAttribute("id"));
    }

    @Test
    void deletingACellRangeIsOneUndoStepAndSkipsMissingCells() {
        int name = col("name");
        int qty = col("qty");
        CompositeXmlCommand cmd = TableEditOps.deleteCells(table, 2, qty, 0, name, false);
        assertTrue(context.executeCommand(cmd));
        for (XmlElement item : items.getChildElements("item")) {
            assertNull(item.getChildElement("name"));
            assertNull(item.getChildElement("qty"));
            assertTrue(item.hasAttribute("id"), "columns outside the range stay");
        }
        context.undo();
        assertEquals("b", items.getChildElements("item").get(1).getChildElement("name").getTextContent());
        assertEquals("3", items.getChildElements("item").get(2).getChildElement("qty").getTextContent());
    }

    @Test
    void deletingACellRemovesEverySameNamedChild() {
        context.loadDocumentFromString("<items><item><v>1</v><v>2</v><w>x</w></item><item><w>y</w></item></items>");
        items = context.getDocument().getRootElement();
        table = new RepeatingElementsTable("item", items.getChildElements("item"), 1, () -> { });
        int v = col("v");
        assertTrue(context.executeCommand(TableEditOps.deleteCells(table, 0, v, 0, v, false)));
        assertTrue(items.getChildElements("item").get(0).getChildElements("v").isEmpty());
        assertNotNull(items.getChildElements("item").get(0).getChildElement("w"));
    }

    @Test
    void wholeRowsLoseTheirMemberAndEmptyCellsGiveNothingToDo() {
        assertTrue(TableEditOps.deleteCells(table, 1, col("qty"), 1, col("qty"), false).isEmpty(),
                "item 2 has no qty");
        assertTrue(context.executeCommand(TableEditOps.deleteCells(table, 0, 0, 1, 0, true)));
        List<XmlElement> members = items.getChildElements("item");
        assertEquals(1, members.size());
        assertEquals("3", members.get(0).getAttribute("id"));
        context.undo();
        assertEquals(3, items.getChildElements("item").size());
    }
}
