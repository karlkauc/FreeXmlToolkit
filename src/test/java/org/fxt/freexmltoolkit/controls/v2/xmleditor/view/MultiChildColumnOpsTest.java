package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.MoveTableColumnCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Column operations act on every child of the column, not only the first one. */
class MultiChildColumnOpsTest {

    private XmlEditorContext context;
    private XmlElement first;
    private RepeatingElementsTable table;

    @BeforeEach
    void setUp() {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString("<items><item id=\"1\"><tag>x</tag><name>a</name><tag>y</tag></item>"
                + "<item id=\"2\"><tag>z</tag><name>b</name></item></items>");
        XmlElement items = context.getDocument().getRootElement();
        first = items.getChildElements("item").getFirst();
        table = new RepeatingElementsTable("item", items.getChildElements("item"), 1, () -> { });
    }

    private List<String> childNames(XmlElement element) {
        return element.getChildElements().stream().map(XmlElement::getName).toList();
    }

    @Test
    void renamingAColumnRenamesEveryChildOfIt() {
        assertTrue(context.executeCommand(TableEditOps.renameColumn(table, "tag", "label")));

        assertEquals(List.of("label", "name", "label"), childNames(first));
        context.undo();
        assertEquals(List.of("tag", "name", "tag"), childNames(first));
    }

    @Test
    void movingAColumnMovesEveryChildOfItInOrder() {
        int tag = table.getColumns().indexOf(table.getColumn("tag"));
        int name = table.getColumns().indexOf(table.getColumn("name"));
        MoveTableColumnCommand move = new MoveTableColumnCommand(table, tag, name, false);

        assertTrue(context.executeCommand(move));
        assertEquals(List.of("name", "tag", "tag"), childNames(first));
        assertEquals(List.of("x", "y"), first.getChildElements("tag").stream().map(XmlElement::getTextContent).toList());

        context.undo();
        assertEquals(List.of("tag", "name", "tag"), childNames(first));
    }
}
