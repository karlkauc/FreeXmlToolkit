package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.CompositeXmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spreadsheet exchange of an embedded table: TSV formatting, parsing and the paste plan. */
class TsvClipboardTest {

    private static final String XML = """
            <items>
              <item id="1"><name>a</name><qty>1</qty></item>
              <item id="2"><name>b	x</name></item>
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
    void formatsARangeAndTheWholeTable() {
        assertEquals("a\t1\n", TsvClipboard.format(table, 0, 1, 0, 2, false));
        assertEquals("1\ta\n2\tb x\n", TsvClipboard.format(table, 1, 1, 0, 0, false),
                "any corner order; a tab inside a value becomes a space");
        assertEquals("@id\tname\tqty\n1\ta\t1\n2\tb x\t\n", TsvClipboard.formatTable(table));
    }

    @Test
    void parsesSpreadsheetText() {
        List<List<String>> rows = TsvClipboard.parse("x\ty\r\nz\t\n");
        assertEquals(List.of(List.of("x", "y"), List.of("z", "")), rows);
        assertTrue(TsvClipboard.parse("").isEmpty());
        assertTrue(TsvClipboard.isBlock("a\tb"));
        assertTrue(TsvClipboard.isBlock("a\nb"));
        assertFalse(TsvClipboard.isBlock("single\n"), "one value with a trailing newline is no block");
        assertFalse(TsvClipboard.isBlock(null));
    }

    @Test
    void pasteOverwritesCellsCreatesMissingChildrenAndAppendsRows() {
        List<List<String>> values = TsvClipboard.parse("10\tA\n20\tB\t5\n30\tC\t6\textra\n");
        CompositeXmlCommand cmd = TableEditOps.pasteCells(table, 0, 0, values);
        assertTrue(context.executeCommand(cmd));

        List<XmlElement> members = items.getChildElements("item");
        assertEquals(3, members.size(), "the third pasted row became a new member");
        assertEquals("10", members.get(0).getAttribute("id"));
        assertEquals("A", members.get(0).getChildElement("name").getTextContent());
        assertEquals("1", members.get(0).getChildElement("qty").getTextContent(), "not pasted, kept");
        assertNotNull(members.get(1).getChildElement("qty"), "missing child created");
        assertEquals("5", members.get(1).getChildElement("qty").getTextContent());
        assertEquals("30", members.get(2).getAttribute("id"));
        assertEquals("6", members.get(2).getChildElement("qty").getTextContent());

        context.undo();
        assertEquals(2, items.getChildElements("item").size(), "one undo step reverts the whole paste");
        assertEquals("1", items.getChildElements("item").get(0).getAttribute("id"));
    }
}
