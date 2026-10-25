package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChangeFixedValueCommand}: the Inspector's "fixed" field on an attribute
 * used to end in an {@code IllegalArgumentException} from {@link ChangeConstraintsCommand}.
 */
class ChangeFixedValueCommandTest {

    private XsdEditorContext editorContext;
    private XsdAttribute attribute;

    @BeforeEach
    void setUp() {
        editorContext = new XsdEditorContext(new XsdSchema());
        attribute = new XsdAttribute("version");
    }

    @Test
    void setsAndUndoesTheFixedValueOfAnAttribute() {
        attribute.setFixed("1.0");
        var command = new ChangeFixedValueCommand(editorContext, attribute, "  2.0 ");

        assertTrue(command.execute());
        assertEquals("2.0", attribute.getFixed());

        assertTrue(command.undo());
        assertEquals("1.0", attribute.getFixed());
    }

    @Test
    void blankClearsTheFixedValue() {
        attribute.setFixed("1.0");
        assertTrue(new ChangeFixedValueCommand(editorContext, attribute, "  ").execute());
        assertNull(attribute.getFixed());
    }

    @Test
    void consecutiveEditsMergeIntoOneUndoStep() {
        var first = new ChangeFixedValueCommand(editorContext, attribute, "a");
        first.execute();
        var second = new ChangeFixedValueCommand(editorContext, attribute, "ab");
        second.execute();

        assertTrue(first.canMergeWith(second));
        XsdCommand merged = first.mergeWith(second);
        assertTrue(merged.undo());
        assertNull(attribute.getFixed());
    }

    @Test
    void rejectsNodesOtherThanAttributes() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeFixedValueCommand(editorContext, new XsdElement("e"), "x"));
    }

    @Test
    void normalizeTrimsAndMapsBlankToNull() {
        assertNull(ChangeFixedValueCommand.normalize(null));
        assertNull(ChangeFixedValueCommand.normalize(" "));
        assertEquals("x", ChangeFixedValueCommand.normalize(" x "));
    }
}
