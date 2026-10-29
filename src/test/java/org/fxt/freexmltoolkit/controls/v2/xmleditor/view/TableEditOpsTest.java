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

/** Whole-row and whole-column operations on a repeating group (XMLSpy table display). */
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
}
