package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AddFacetCommand}.
 */
class AddFacetCommandTest {

    private XsdRestriction restriction;

    @BeforeEach
    void setUp() {
        restriction = new XsdRestriction();
        restriction.setBase("xs:string");
    }

    @Test
    void testExecute_AddsSimpleFacet() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");

        assertTrue(command.execute());

        assertEquals(1, restriction.getFacets().size());
        XsdFacet facet = restriction.getFacets().get(0);
        assertEquals(XsdFacetType.MIN_LENGTH, facet.getFacetType());
        assertEquals("5", facet.getValue());
        assertFalse(facet.isFixed());
    }

    @Test
    void testExecute_AddsFixedFacet() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MAX_LENGTH, "100", true);

        assertTrue(command.execute());

        assertEquals(1, restriction.getFacets().size());
        XsdFacet facet = restriction.getFacets().get(0);
        assertEquals(XsdFacetType.MAX_LENGTH, facet.getFacetType());
        assertEquals("100", facet.getValue());
        assertTrue(facet.isFixed());
    }

    @Test
    void testExecute_AddsPatternFacet() {
        String pattern = "[A-Z]{3}[0-9]{3}";
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.PATTERN, pattern);

        assertTrue(command.execute());

        assertEquals(1, restriction.getFacets().size());
        XsdFacet facet = restriction.getFacets().get(0);
        assertEquals(XsdFacetType.PATTERN, facet.getFacetType());
        assertEquals(pattern, facet.getValue());
    }

    @Test
    void testExecute_AddsEnumerationFacet() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.ENUMERATION, "RED");

        assertTrue(command.execute());

        assertEquals(1, restriction.getFacets().size());
        XsdFacet facet = restriction.getFacets().get(0);
        assertEquals(XsdFacetType.ENUMERATION, facet.getFacetType());
        assertEquals("RED", facet.getValue());
    }

    @Test
    void testExecute_AddsMultipleFacets() {
        AddFacetCommand cmd1 = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");
        AddFacetCommand cmd2 = new AddFacetCommand(restriction, XsdFacetType.MAX_LENGTH, "10");
        AddFacetCommand cmd3 = new AddFacetCommand(restriction, XsdFacetType.PATTERN, "[a-z]+");

        assertTrue(cmd1.execute());
        assertTrue(cmd2.execute());
        assertTrue(cmd3.execute());

        assertEquals(3, restriction.getFacets().size());
        assertEquals(XsdFacetType.MIN_LENGTH, restriction.getFacets().get(0).getFacetType());
        assertEquals(XsdFacetType.MAX_LENGTH, restriction.getFacets().get(1).getFacetType());
        assertEquals(XsdFacetType.PATTERN, restriction.getFacets().get(2).getFacetType());
    }

    @Test
    void testExecute_TrimsWhitespace() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "  5  ");

        assertTrue(command.execute());

        assertEquals(1, restriction.getFacets().size());
        assertEquals("5", restriction.getFacets().get(0).getValue());
    }

    @Test
    void testUndo_RemovesFacet() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");
        command.execute();

        assertEquals(1, restriction.getFacets().size());

        assertTrue(command.undo());

        assertEquals(0, restriction.getFacets().size());
    }

    @Test
    void testUndo_BeforeExecute_ReturnsFalse() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");

        assertFalse(command.undo());
    }

    @Test
    void testExecuteAndUndo_MultipleRounds() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MAX_LENGTH, "100");

        // Execute
        assertTrue(command.execute());
        assertEquals(1, restriction.getFacets().size());

        // Undo
        assertTrue(command.undo());
        assertEquals(0, restriction.getFacets().size());

        // Re-execute
        assertTrue(command.execute());
        assertEquals(1, restriction.getFacets().size());

        // Undo again
        assertTrue(command.undo());
        assertEquals(0, restriction.getFacets().size());
    }

    @Test
    void testGetDescription() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");

        String description = command.getDescription();

        assertNotNull(description);
        assertTrue(description.contains("Add"));
        assertTrue(description.contains("minLength"));
        assertTrue(description.contains("5"));
    }

    @Test
    void testCanUndo_ReturnsTrue() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");

        assertTrue(command.canUndo());
    }

    @Test
    void testCanMergeWith_ReturnsFalse() {
        AddFacetCommand command1 = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");
        AddFacetCommand command2 = new AddFacetCommand(restriction, XsdFacetType.MAX_LENGTH, "10");

        assertFalse(command1.canMergeWith(command2));
    }

    @Test
    void testGetCreatedFacet_BeforeExecute_ReturnsNull() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");

        assertNull(command.getCreatedFacet());
    }

    @Test
    void testGetCreatedFacet_AfterExecute_ReturnsCreatedFacet() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5");
        command.execute();

        XsdFacet createdFacet = command.getCreatedFacet();

        assertNotNull(createdFacet);
        assertEquals(XsdFacetType.MIN_LENGTH, createdFacet.getFacetType());
        assertEquals("5", createdFacet.getValue());
    }

    @Test
    void testConstructor_NullRestriction_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AddFacetCommand(null, XsdFacetType.MIN_LENGTH, "5")
        );
    }

    @Test
    void testConstructor_NullFacetType_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AddFacetCommand(restriction, null, "5")
        );
    }

    @Test
    void testConstructor_NullValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, null)
        );
    }

    @Test
    void testConstructor_EmptyValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "")
        );
    }

    @Test
    void testConstructor_WhitespaceOnlyValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "   ")
        );
    }

    @Test
    void testGetters() {
        AddFacetCommand command = new AddFacetCommand(restriction, XsdFacetType.MIN_LENGTH, "5", true);

        assertSame(restriction, command.getRestriction());
        assertEquals(XsdFacetType.MIN_LENGTH, command.getFacetType());
        assertEquals("5", command.getValue());
    }
}
