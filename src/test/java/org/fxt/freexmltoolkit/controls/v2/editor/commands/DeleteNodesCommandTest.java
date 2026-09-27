package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.junit.jupiter.api.Test;

class DeleteNodesCommandTest {

    private static List<String> names(XsdNode parent) {
        return parent.getChildren().stream().map(XsdNode::getName).toList();
    }

    @Test
    void deletesAdjacentSiblingsAndUndoRestoresOriginalOrder() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType a = new XsdComplexType("A");
        XsdComplexType b = new XsdComplexType("B");
        XsdSimpleType c = new XsdSimpleType("C");
        XsdComplexType d = new XsdComplexType("D");
        schema.addChild(a);
        schema.addChild(b);
        schema.addChild(c);
        schema.addChild(d);

        // Given in ascending order on purpose — the command must reorder internally.
        DeleteNodesCommand command = new DeleteNodesCommand(List.of(a, b, d), "Remove 3 unused components");
        assertEquals(3, command.size());
        assertTrue(command.execute());
        assertEquals(List.of("C"), names(schema));

        assertTrue(command.undo());
        assertEquals(List.of("A", "B", "C", "D"), names(schema), "sibling order restored exactly");

        assertTrue(command.execute(), "redo runs the same child commands again");
        assertEquals(List.of("C"), names(schema));
    }

    @Test
    void handlesNodesUnderDifferentParents() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType type = new XsdComplexType("T");
        XsdSequence sequence = new XsdSequence();
        XsdElement e1 = new XsdElement("e1");
        XsdElement e2 = new XsdElement("e2");
        sequence.addChild(e1);
        sequence.addChild(e2);
        type.addChild(sequence);
        XsdSimpleType orphan = new XsdSimpleType("Orphan");
        schema.addChild(type);
        schema.addChild(orphan);

        DeleteNodesCommand command = new DeleteNodesCommand(List.of(e1, orphan, e2), null);
        assertTrue(command.execute());
        assertEquals(List.of("T"), names(schema));
        assertTrue(sequence.getChildren().isEmpty());

        assertTrue(command.undo());
        assertEquals(List.of("T", "Orphan"), names(schema));
        assertEquals(List.of("e1", "e2"), names(sequence));
    }

    @Test
    void isOneUndoStepInTheCommandManager() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType a = new XsdComplexType("A");
        XsdComplexType b = new XsdComplexType("B");
        schema.addChild(a);
        schema.addChild(b);
        CommandManager manager = new CommandManager();

        assertTrue(manager.executeCommand(new DeleteNodesCommand(List.of(a, b), "Remove 2")));
        assertEquals(1, manager.getUndoStackSize());
        assertTrue(schema.getChildren().isEmpty());

        assertTrue(manager.undo());
        assertEquals(List.of("A", "B"), names(schema));
        assertEquals(0, manager.getUndoStackSize());
        assertTrue(manager.redo());
        assertTrue(schema.getChildren().isEmpty());
    }

    @Test
    void rejectsParentlessNodesAndIgnoresDuplicates() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType a = new XsdComplexType("A");
        schema.addChild(a);
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteNodesCommand(List.of(new XsdComplexType("Loose")), "x"));

        DeleteNodesCommand command = new DeleteNodesCommand(List.of(a, a), "dup");
        assertEquals(1, command.size());
        assertTrue(command.execute());
        assertTrue(schema.getChildren().isEmpty());
        assertFalse(command.canMergeWith(command));
        assertEquals("dup", command.getDescription());
    }
}
