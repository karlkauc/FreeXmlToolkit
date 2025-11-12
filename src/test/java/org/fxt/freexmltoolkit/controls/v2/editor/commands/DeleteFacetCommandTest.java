package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DeleteFacetCommand}.
 */
class DeleteFacetCommandTest {

    private XsdRestriction restriction;
    private XsdFacet facet1;
    private XsdFacet facet2;
    private XsdFacet facet3;

    @BeforeEach
    void setUp() {
        restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        facet1 = new XsdFacet(XsdFacetType.MIN_LENGTH, "5");
        facet2 = new XsdFacet(XsdFacetType.MAX_LENGTH, "100");
        facet3 = new XsdFacet(XsdFacetType.PATTERN, "[A-Z]+");

        restriction.addFacet(facet1);
        restriction.addFacet(facet2);
        restriction.addFacet(facet3);
    }

    @Test
    void testExecute_DeletesFirstFacet() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet1);

        assertTrue(command.execute());

        assertEquals(2, restriction.getFacets().size());
        assertFalse(restriction.getFacets().contains(facet1));
        assertTrue(restriction.getFacets().contains(facet2));
        assertTrue(restriction.getFacets().contains(facet3));
    }

    @Test
    void testExecute_DeletesMiddleFacet() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet2);

        assertTrue(command.execute());

        assertEquals(2, restriction.getFacets().size());
        assertTrue(restriction.getFacets().contains(facet1));
        assertFalse(restriction.getFacets().contains(facet2));
        assertTrue(restriction.getFacets().contains(facet3));
    }

    @Test
    void testExecute_DeletesLastFacet() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet3);

        assertTrue(command.execute());

        assertEquals(2, restriction.getFacets().size());
        assertTrue(restriction.getFacets().contains(facet1));
        assertTrue(restriction.getFacets().contains(facet2));
        assertFalse(restriction.getFacets().contains(facet3));
    }

    @Test
    void testExecute_DeletesOnlyFacet() {
        XsdRestriction singleRestriction = new XsdRestriction();
        XsdFacet singleFacet = new XsdFacet(XsdFacetType.MIN_LENGTH, "1");
        singleRestriction.addFacet(singleFacet);

        DeleteFacetCommand command = new DeleteFacetCommand(singleRestriction, singleFacet);

        assertTrue(command.execute());

        assertEquals(0, singleRestriction.getFacets().size());
    }

    @Test
    void testExecute_FacetNotInRestriction_ReturnsFalse() {
        XsdFacet unrelatedFacet = new XsdFacet(XsdFacetType.ENUMERATION, "VALUE");

        DeleteFacetCommand command = new DeleteFacetCommand(restriction, unrelatedFacet);

        assertFalse(command.execute());

        // Restriction unchanged
        assertEquals(3, restriction.getFacets().size());
    }

    @Test
    void testExecute_RecordsOriginalPosition() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet2);

        assertEquals(-1, command.getOriginalPosition());

        assertTrue(command.execute());

        assertEquals(1, command.getOriginalPosition());
    }

    @Test
    void testUndo_RestoresFacet() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet2);
        command.execute();

        assertEquals(2, restriction.getFacets().size());

        assertTrue(command.undo());

        assertEquals(3, restriction.getFacets().size());
        assertTrue(restriction.getFacets().contains(facet2));
    }

    @Test
    void testUndo_BeforeExecute_ReturnsFalse() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet1);

        assertFalse(command.undo());
    }

    @Test
    void testUndo_AfterFailedExecute_ReturnsFalse() {
        XsdFacet unrelatedFacet = new XsdFacet(XsdFacetType.ENUMERATION, "VALUE");
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, unrelatedFacet);

        assertFalse(command.execute());
        assertFalse(command.undo());
    }

    @Test
    void testExecuteAndUndo_MultipleRounds() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet2);

        // Execute
        assertTrue(command.execute());
        assertEquals(2, restriction.getFacets().size());
        assertFalse(restriction.getFacets().contains(facet2));

        // Undo
        assertTrue(command.undo());
        assertEquals(3, restriction.getFacets().size());
        assertTrue(restriction.getFacets().contains(facet2));

        // Re-execute
        assertTrue(command.execute());
        assertEquals(2, restriction.getFacets().size());
        assertFalse(restriction.getFacets().contains(facet2));

        // Undo again
        assertTrue(command.undo());
        assertEquals(3, restriction.getFacets().size());
        assertTrue(restriction.getFacets().contains(facet2));
    }

    @Test
    void testGetDescription() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet1);

        String description = command.getDescription();

        assertNotNull(description);
        assertTrue(description.contains("Delete"));
        assertTrue(description.contains("minLength"));
        assertTrue(description.contains("5"));
    }

    @Test
    void testCanUndo_ReturnsTrue() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet1);

        assertTrue(command.canUndo());
    }

    @Test
    void testCanMergeWith_ReturnsFalse() {
        DeleteFacetCommand command1 = new DeleteFacetCommand(restriction, facet1);
        DeleteFacetCommand command2 = new DeleteFacetCommand(restriction, facet2);

        assertFalse(command1.canMergeWith(command2));
    }

    @Test
    void testConstructor_NullRestriction_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new DeleteFacetCommand(null, facet1)
        );
    }

    @Test
    void testConstructor_NullFacet_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new DeleteFacetCommand(restriction, null)
        );
    }

    @Test
    void testGetters() {
        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet1);

        assertSame(restriction, command.getRestriction());
        assertSame(facet1, command.getFacet());
        assertEquals(-1, command.getOriginalPosition());

        command.execute();

        assertEquals(0, command.getOriginalPosition());
    }

    @Test
    void testExecute_DeleteAllFacetsSequentially() {
        DeleteFacetCommand cmd1 = new DeleteFacetCommand(restriction, facet1);
        DeleteFacetCommand cmd2 = new DeleteFacetCommand(restriction, facet2);
        DeleteFacetCommand cmd3 = new DeleteFacetCommand(restriction, facet3);

        assertTrue(cmd1.execute());
        assertEquals(2, restriction.getFacets().size());

        assertTrue(cmd2.execute());
        assertEquals(1, restriction.getFacets().size());

        assertTrue(cmd3.execute());
        assertEquals(0, restriction.getFacets().size());

        // Undo in reverse order
        assertTrue(cmd3.undo());
        assertEquals(1, restriction.getFacets().size());

        assertTrue(cmd2.undo());
        assertEquals(2, restriction.getFacets().size());

        assertTrue(cmd1.undo());
        assertEquals(3, restriction.getFacets().size());
    }

    @Test
    void testExecute_WithFixedFacet() {
        facet1.setFixed(true);

        DeleteFacetCommand command = new DeleteFacetCommand(restriction, facet1);

        assertTrue(command.execute());
        assertEquals(2, restriction.getFacets().size());

        assertTrue(command.undo());
        assertEquals(3, restriction.getFacets().size());

        // Verify the fixed flag is preserved
        XsdFacet restoredFacet = restriction.getFacets().stream()
                .filter(f -> f.getFacetType() == XsdFacetType.MIN_LENGTH)
                .findFirst()
                .orElse(null);

        assertNotNull(restoredFacet);
        assertTrue(restoredFacet.isFixed());
    }

    @Test
    void testExecute_WithPatternFacet() {
        XsdFacet patternFacet = new XsdFacet(XsdFacetType.PATTERN, "[0-9]{3}-[0-9]{4}");
        restriction.addFacet(patternFacet);

        assertEquals(4, restriction.getFacets().size());

        DeleteFacetCommand command = new DeleteFacetCommand(restriction, patternFacet);

        assertTrue(command.execute());
        assertEquals(3, restriction.getFacets().size());
        assertFalse(restriction.getFacets().contains(patternFacet));

        assertTrue(command.undo());
        assertEquals(4, restriction.getFacets().size());
        assertTrue(restriction.getFacets().contains(patternFacet));
    }

    @Test
    void testExecute_WithEnumerationFacet() {
        XsdFacet enumFacet = new XsdFacet(XsdFacetType.ENUMERATION, "RED");
        restriction.addFacet(enumFacet);

        DeleteFacetCommand command = new DeleteFacetCommand(restriction, enumFacet);

        assertTrue(command.execute());
        assertFalse(restriction.getFacets().contains(enumFacet));

        assertTrue(command.undo());
        assertTrue(restriction.getFacets().contains(enumFacet));
        assertEquals("RED", enumFacet.getValue());
    }
}
