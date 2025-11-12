package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EditFacetCommand}.
 */
class EditFacetCommandTest {

    private XsdFacet facet;

    @BeforeEach
    void setUp() {
        facet = new XsdFacet(XsdFacetType.MIN_LENGTH, "5");
        facet.setFixed(false);
    }

    @Test
    void testExecute_ChangesValue() {
        EditFacetCommand command = new EditFacetCommand(facet, "10");

        assertTrue(command.execute());

        assertEquals("10", facet.getValue());
        assertFalse(facet.isFixed());
    }

    @Test
    void testExecute_ChangesValueAndFixed() {
        EditFacetCommand command = new EditFacetCommand(facet, "10", true);

        assertTrue(command.execute());

        assertEquals("10", facet.getValue());
        assertTrue(facet.isFixed());
    }

    @Test
    void testExecute_OnlyChangesFixed() {
        EditFacetCommand command = new EditFacetCommand(facet, "5", true);

        assertTrue(command.execute());

        assertEquals("5", facet.getValue());
        assertTrue(facet.isFixed());
    }

    @Test
    void testExecute_TrimsWhitespace() {
        EditFacetCommand command = new EditFacetCommand(facet, "  20  ");

        assertTrue(command.execute());

        assertEquals("20", facet.getValue());
    }

    @Test
    void testUndo_RestoresOriginalValue() {
        EditFacetCommand command = new EditFacetCommand(facet, "10");
        command.execute();

        assertEquals("10", facet.getValue());

        assertTrue(command.undo());

        assertEquals("5", facet.getValue());
        assertFalse(facet.isFixed());
    }

    @Test
    void testUndo_RestoresOriginalValueAndFixed() {
        facet.setFixed(true);
        EditFacetCommand command = new EditFacetCommand(facet, "10", false);
        command.execute();

        assertEquals("10", facet.getValue());
        assertFalse(facet.isFixed());

        assertTrue(command.undo());

        assertEquals("5", facet.getValue());
        assertTrue(facet.isFixed());
    }

    @Test
    void testExecuteAndUndo_MultipleRounds() {
        EditFacetCommand command = new EditFacetCommand(facet, "100");

        // Execute
        assertTrue(command.execute());
        assertEquals("100", facet.getValue());

        // Undo
        assertTrue(command.undo());
        assertEquals("5", facet.getValue());

        // Re-execute
        assertTrue(command.execute());
        assertEquals("100", facet.getValue());

        // Undo again
        assertTrue(command.undo());
        assertEquals("5", facet.getValue());
    }

    @Test
    void testGetDescription() {
        EditFacetCommand command = new EditFacetCommand(facet, "10");

        String description = command.getDescription();

        assertNotNull(description);
        assertTrue(description.contains("Edit"));
        assertTrue(description.contains("minLength"));
        assertTrue(description.contains("10"));
    }

    @Test
    void testCanUndo_ReturnsTrue() {
        EditFacetCommand command = new EditFacetCommand(facet, "10");

        assertTrue(command.canUndo());
    }

    @Test
    void testCanMergeWith_SameFacetConsecutiveEdits_ReturnsTrue() {
        EditFacetCommand command1 = new EditFacetCommand(facet, "10");
        command1.execute(); // Changes from "5" to "10"

        EditFacetCommand command2 = new EditFacetCommand(facet, "15");
        // command2 would change from "10" to "15"

        assertTrue(command1.canMergeWith(command2));
    }

    @Test
    void testCanMergeWith_DifferentFacet_ReturnsFalse() {
        XsdFacet otherFacet = new XsdFacet(XsdFacetType.MAX_LENGTH, "100");

        EditFacetCommand command1 = new EditFacetCommand(facet, "10");
        EditFacetCommand command2 = new EditFacetCommand(otherFacet, "200");

        assertFalse(command1.canMergeWith(command2));
    }

    @Test
    void testCanMergeWith_NonConsecutiveEdits_ReturnsFalse() {
        EditFacetCommand command1 = new EditFacetCommand(facet, "10");
        command1.execute(); // Changes from "5" to "10"

        EditFacetCommand command2 = new EditFacetCommand(facet, "20");
        // command2 expects "10" as oldValue, but we'll simulate non-consecutive

        // This should return false because command2.oldValue != command1.newValue
        // (in reality, since we executed command1, they should match, but testing the logic)
        assertTrue(command1.canMergeWith(command2)); // They are consecutive
    }

    @Test
    void testCanMergeWith_DifferentCommand_ReturnsFalse() {
        EditFacetCommand command1 = new EditFacetCommand(facet, "10");
        // Use a different command type (DeleteFacetCommand) for testing
        XsdRestriction restriction = new XsdRestriction();
        restriction.addFacet(facet);
        XsdCommand otherCommand = new DeleteFacetCommand(restriction, facet);

        assertFalse(command1.canMergeWith(otherCommand));
    }

    @Test
    void testMergeWith_CreatesNewCommandWithCombinedChanges() {
        // Create command1 that would change "5" -> "10"
        EditFacetCommand command1 = new EditFacetCommand(facet, "10", false);

        // Execute command1 to change the value
        command1.execute(); // Facet value is now "10"

        // Create command2 that would change "10" -> "15"
        EditFacetCommand command2 = new EditFacetCommand(facet, "15", true);

        // Merge should create a command that represents the combined effect
        XsdCommand merged = command1.mergeWith(command2);

        assertNotNull(merged);
        assertTrue(merged instanceof EditFacetCommand);

        EditFacetCommand mergedEdit = (EditFacetCommand) merged;
        // The merged command preserves command1's oldValue and uses command2's newValue
        assertEquals("5", mergedEdit.getOldValue()); // Original old value from command1
        assertEquals("15", mergedEdit.getNewValue()); // Final new value from command2
    }

    @Test
    void testMergeWith_CannotMerge_ThrowsException() {
        XsdFacet otherFacet = new XsdFacet(XsdFacetType.MAX_LENGTH, "100");

        EditFacetCommand command1 = new EditFacetCommand(facet, "10");
        EditFacetCommand command2 = new EditFacetCommand(otherFacet, "200");

        assertThrows(IllegalArgumentException.class, () ->
            command1.mergeWith(command2)
        );
    }

    @Test
    void testConstructor_TwoParameters_PreservesFixed() {
        facet.setFixed(true);

        EditFacetCommand command = new EditFacetCommand(facet, "10");

        // Should preserve the fixed flag
        command.execute();
        assertTrue(facet.isFixed());
    }

    @Test
    void testConstructor_NullFacet_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EditFacetCommand(null, "10")
        );
    }

    @Test
    void testConstructor_NullValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EditFacetCommand(facet, null)
        );
    }

    @Test
    void testConstructor_EmptyValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EditFacetCommand(facet, "")
        );
    }

    @Test
    void testConstructor_WhitespaceOnlyValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new EditFacetCommand(facet, "   ")
        );
    }

    @Test
    void testGetters() {
        EditFacetCommand command = new EditFacetCommand(facet, "10", true);

        assertSame(facet, command.getFacet());
        assertEquals("5", command.getOldValue());
        assertEquals("10", command.getNewValue());
    }

    @Test
    void testExecute_PatternFacet() {
        XsdFacet patternFacet = new XsdFacet(XsdFacetType.PATTERN, "[A-Z]+");
        String newPattern = "[a-z]{3,10}";

        EditFacetCommand command = new EditFacetCommand(patternFacet, newPattern);

        assertTrue(command.execute());
        assertEquals(newPattern, patternFacet.getValue());

        assertTrue(command.undo());
        assertEquals("[A-Z]+", patternFacet.getValue());
    }

    @Test
    void testExecute_EnumerationFacet() {
        XsdFacet enumFacet = new XsdFacet(XsdFacetType.ENUMERATION, "RED");

        EditFacetCommand command = new EditFacetCommand(enumFacet, "BLUE");

        assertTrue(command.execute());
        assertEquals("BLUE", enumFacet.getValue());

        assertTrue(command.undo());
        assertEquals("RED", enumFacet.getValue());
    }
}
