package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drag and drop in the XML grid: moving and copying nodes, dropping external text and XML fragments. */
class XmlGridAdapterDragDropTest {

    private XmlEditorContext context;
    private XmlGridAdapter adapter;
    private XmlElement root;

    @BeforeEach
    void setUp() {
        context = new XmlEditorContext();
        context.loadDocumentFromString("<root><a>1</a><b>2</b><box><x>3</x></box></root>");
        adapter = new XmlGridAdapter(context);
        root = context.getDocument().getRootElement();
    }

    private FlatRow row(String label) {
        return adapter.flatten().stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    private List<String> childNames(XmlElement el) {
        return el.getChildElements().stream().map(XmlElement::getName).toList();
    }

    @Test
    void moveAfterBeforeAndIntoAnotherElement() {
        assertTrue(adapter.dropRow(row("a"), row("b"), DropPosition.AFTER, false));
        assertEquals(List.of("b", "a", "box"), childNames(root));
        assertTrue(adapter.dropRow(row("a"), row("b"), DropPosition.BEFORE, false));
        assertEquals(List.of("a", "b", "box"), childNames(root));
        assertTrue(adapter.dropRow(row("a"), row("box"), DropPosition.INTO, false));
        assertEquals(List.of("b", "box"), childNames(root));
        assertEquals(List.of("x", "a"), childNames(root.getChildElement("box")));

        context.undo();
        assertEquals(List.of("a", "b", "box"), childNames(root), "each move is one undo step");
    }

    @Test
    void copyLeavesTheOriginal() {
        XmlElement a = root.getChildElement("a");
        assertTrue(adapter.dropRow(row("a"), row("box"), DropPosition.INTO, true));
        XmlElement copy = root.getChildElement("box").getChildElement("a");
        assertNotSame(a, copy);
        assertSame(a, root.getChildElement("a"));
        assertEquals("1", copy.getTextContent());
    }

    @Test
    void neverIntoItselfOrADescendantNorOntoTheRootLevel() {
        assertFalse(adapter.canDrop(row("box"), row("x"), DropPosition.AFTER), "into its own subtree");
        assertFalse(adapter.canDrop(row("box"), row("box"), DropPosition.INTO));
        assertFalse(adapter.canDrop(row("a"), row("root"), DropPosition.BEFORE), "no second root element");
        assertFalse(adapter.canDrag(row("root")), "the root element stays");
        assertFalse(adapter.canDrop(row("a"), row("a"), DropPosition.INTO), "a has text content");
        assertFalse(adapter.dropRow(row("a"), row("a"), DropPosition.BEFORE, false), "own place: nothing to do");
    }

    @Test
    void externalTextReplacesAValueOrBecomesNodes() {
        assertTrue(adapter.dropText(row("a"), DropPosition.VALUE, "  dropped  "));
        assertEquals("dropped", root.getChildElement("a").getTextContent());

        assertTrue(adapter.dropText(row("box"), DropPosition.INTO, "<n1>v</n1>\n<n2/>"));
        assertEquals(List.of("x", "n1", "n2"), childNames(root.getChildElement("box")));
        context.undo();
        assertEquals(List.of("x"), childNames(root.getChildElement("box")), "the fragment drop is one undo step");

        assertTrue(adapter.dropText(row("b"), DropPosition.AFTER, "plain words"));
        XmlNode text = root.getChild(root.indexOf(root.getChildElement("b")) + 1);
        assertInstanceOf(XmlText.class, text);
        assertEquals("plain words", ((XmlText) text).getText());
    }

    @Test
    void dragTextIsTheSerializedNode() {
        assertTrue(adapter.dragText(row("a")).contains("<a>1</a>"));
    }
}
