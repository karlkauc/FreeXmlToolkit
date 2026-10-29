package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;

/** One undo step for several commands: ordered execution, reverse undo, rollback on failure. */
class CompositeXmlCommandTest {

    @Test
    void executesPartsInOrderAndUndoesThemAsOneStep() {
        XmlElement a = new XmlElement("a");
        XmlElement b = new XmlElement("b");
        CompositeXmlCommand composite = new CompositeXmlCommand("set both", List.of(
                new SetAttributeCommand(a, "x", "1"),
                new SetAttributeCommand(b, "y", "2")));

        assertTrue(composite.execute());
        assertEquals("1", a.getAttribute("x"));
        assertEquals("2", b.getAttribute("y"));

        assertTrue(composite.undo());
        assertNull(a.getAttribute("x"));
        assertNull(b.getAttribute("y"));
        assertEquals("set both", composite.getDescription());
        assertEquals(2, composite.getParts().size());
    }

    @Test
    void rollsBackTheExecutedPartsWhenOneFails() {
        XmlElement parent = new XmlElement("p");
        XmlElement withText = new XmlElement("t");
        withText.setTextContent("text"); // AddElementCommand refuses mixed content
        CompositeXmlCommand composite = new CompositeXmlCommand("partially failing", List.of(
                new SetAttributeCommand(parent, "x", "1"),
                new AddElementCommand(withText, new XmlElement("child")),
                new SetAttributeCommand(parent, "y", "2")));

        assertFalse(composite.execute());
        assertNull(parent.getAttribute("x"), "the part before the failure is rolled back");
        assertNull(parent.getAttribute("y"), "the part after the failure never ran");
    }

    @Test
    void runsThroughTheCommandManagerAsASingleUndoStep() {
        CommandManager manager = new CommandManager();
        XmlElement a = new XmlElement("a");
        manager.executeCommand(new CompositeXmlCommand("two attributes", List.of(
                new SetAttributeCommand(a, "x", "1"),
                new SetAttributeCommand(a, "y", "2"))));
        assertEquals(1, manager.getUndoStackSize());
        manager.undo();
        assertNull(a.getAttribute("x"));
        assertNull(a.getAttribute("y"));
        assertTrue(new CompositeXmlCommand("empty", List.of()).isEmpty());
    }
}
