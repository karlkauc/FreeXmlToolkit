package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Grid edits of comment, CDATA and PI rows, attribute renames and type changes through the XML adapter. */
class XmlGridAdapterNodeEditTest {

    private XmlEditorContext context;
    private XmlGridAdapter adapter;
    private XmlElement root;

    @BeforeEach
    void setUp() {
        context = new XmlEditorContext();
        context.loadDocumentFromString("<root id=\"1\"><a>x</a><!--c--><![CDATA[d]]><?pi data?><b><c/></b></root>");
        adapter = new XmlGridAdapter(context);
        root = context.getDocument().getRootElement();
    }

    private FlatRow row(FlatRow.RowType type) {
        return adapter.flatten().stream().filter(r -> r.getType() == type).findFirst().orElseThrow();
    }

    private FlatRow row(String label) {
        return adapter.flatten().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void commentCdataAndPiValuesAreSaved() {
        assertTrue(adapter.canEditValue(row(FlatRow.RowType.COMMENT)));
        adapter.commitRowEdit(row(FlatRow.RowType.COMMENT), false, "new comment");
        adapter.commitRowEdit(row(FlatRow.RowType.CDATA), false, "new cdata");
        adapter.commitRowEdit(row(FlatRow.RowType.PROCESSING_INSTRUCTION), false, "new data");
        adapter.commitRowEdit(row(FlatRow.RowType.PROCESSING_INSTRUCTION), true, "target2");

        assertEquals("new comment", ((XmlComment) root.getChild(1)).getText());
        assertEquals("new cdata", ((XmlCData) root.getChild(2)).getText());
        XmlProcessingInstruction pi = (XmlProcessingInstruction) root.getChild(3);
        assertEquals("target2", pi.getTarget());
        assertEquals("new data", pi.getData());

        context.undo();
        assertEquals("pi", ((XmlProcessingInstruction) root.getChild(3)).getTarget(), "each edit is undoable");
    }

    @Test
    void attributeRowsCanBeRenamed() {
        FlatRow id = row("id");
        assertTrue(adapter.canEditName(id));
        adapter.commitRowEdit(id, true, "key");
        assertEquals("1", root.getAttribute("key"));
        assertFalse(root.hasAttribute("id"));
    }

    @Test
    void containersHaveNoEditableValueAndNoTypeTargets() {
        FlatRow b = row("b");
        assertFalse(adapter.canEditValue(b));
        assertTrue(adapter.typeTargets(b).isEmpty());
        assertFalse(adapter.typeTargets(row("a")).isEmpty());
        assertTrue(adapter.typeTargets(row("id")).contains("Element"));
    }

    @Test
    void changeTypeConvertsThroughTheCommandStack() {
        assertTrue(adapter.changeType(row("a"), "Attribute"));
        assertEquals("x", root.getAttribute("a"));
        assertFalse(adapter.changeType(row("b"), "Text"), "not offered for containers");
        context.undo();
        assertFalse(root.hasAttribute("a"));
        assertEquals("x", root.getChildElement("a").getTextContent());
        assertEquals(List.of("id"), List.copyOf(root.getAttributes().keySet()));
    }
}
