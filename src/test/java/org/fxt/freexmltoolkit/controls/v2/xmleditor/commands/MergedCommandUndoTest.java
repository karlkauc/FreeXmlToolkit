package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.junit.jupiter.api.Test;

/**
 * Two edits in a row on the same node merge into one undo step; that step must undo to the state
 * before the first edit and redo to the state after the last one (telemetry: {@code undo.failed}
 * for a merged {@link SetElementTextCommand}).
 */
class MergedCommandUndoTest {

    @Test
    void mergedElementTextEditsUndoAndRedo() {
        XmlElement element = new XmlElement("Country");
        element.addChild(new XmlText("AT"));
        CommandManager manager = new CommandManager();

        assertTrue(manager.executeCommand(new SetElementTextCommand(element, "DE")));
        assertTrue(manager.executeCommand(new SetElementTextCommand(element, "FR")));
        assertEquals("FR", element.getTextContent());

        assertTrue(manager.undo(), "the merged step can be undone");
        assertEquals("AT", element.getTextContent());
        assertEquals(1, element.getChildCount(), "no text node of an edit is left behind");
        assertTrue(manager.redo());
        assertEquals("FR", element.getTextContent());
        assertEquals(1, element.getChildCount());
    }

    @Test
    void mergedTextNodeEditsUndoToTheFirstOldText() {
        XmlText text = new XmlText("one");
        CommandManager manager = new CommandManager();

        assertTrue(manager.executeCommand(new SetTextCommand(text, "two")));
        assertTrue(manager.executeCommand(new SetTextCommand(text, "three")));

        assertTrue(manager.undo());
        assertEquals("one", text.getText(), "undo restores the text before the first edit");
        assertTrue(manager.redo());
        assertEquals("three", text.getText());
    }

    @Test
    void mergedRenamesUndoAndRedo() {
        XmlElement element = new XmlElement("a");
        CommandManager manager = new CommandManager();

        assertTrue(manager.executeCommand(new RenameNodeCommand(element, "b")));
        assertTrue(manager.executeCommand(new RenameNodeCommand(element, "c")));

        assertTrue(manager.undo());
        assertEquals("a", element.getName());
        assertTrue(manager.redo());
        assertEquals("c", element.getName());
    }
}
