package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.junit.jupiter.api.Test;

class CopyNodeCommandTest {

    @Test
    void insertsADeepCopyAtTheIndexAndRedoReusesIt() {
        XsdSequence sequence = new XsdSequence();
        XsdElement a = new XsdElement("a");
        XsdElement b = new XsdElement("b");
        sequence.addChild(a);
        sequence.addChild(b);
        CommandManager manager = new CommandManager();

        CopyNodeCommand command = new CopyNodeCommand(a, sequence, 1);
        assertTrue(manager.executeCommand(command));
        XsdElement copy = (XsdElement) command.getCopy();
        assertNotSame(a, copy);
        assertEquals("a_copy", copy.getName(), "a sibling already has the name");
        assertSame(copy, sequence.getChildren().get(1));

        manager.undo();
        assertEquals(2, sequence.getChildren().size());
        manager.redo();
        assertSame(copy, sequence.getChildren().get(1), "redo inserts the same node (stable id)");
    }

    @Test
    void refusesContentUnderASimpleTypedElement() {
        XsdElement simple = new XsdElement("s");
        simple.setType("xs:string");
        CopyNodeCommand command = new CopyNodeCommand(new XsdElement("x"), simple, -1);

        assertFalse(command.execute());
        assertTrue(simple.getChildren().isEmpty());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> new CopyNodeCommand(null, new XsdSequence(), 0));
        assertThrows(IllegalArgumentException.class, () -> new CopyNodeCommand(new XsdElement("a"), null, 0));
    }
}
