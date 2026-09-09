package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AddArrayItemCommand}.
 */
class AddArrayItemCommandTest {

    private JsonDocument doc;
    private JsonArray array;

    @BeforeEach
    void setUp() {
        doc = JsonNodeFactory.parse("[1, 2]");
        array = (JsonArray) doc.getRootValue();
    }

    @Test
    void appendWhenIndexIsMinusOne() {
        JsonPrimitive value = new JsonPrimitive(3);
        AddArrayItemCommand cmd = new AddArrayItemCommand(array, -1, value);

        assertTrue(cmd.execute());

        assertEquals(3, array.size());
        assertSame(value, array.get(2));
        assertSame(array, value.getParent());
        assertEquals("[1, 2, 3]", doc.serialize(2, 0));
    }

    @Test
    void insertAtIndex() {
        JsonPrimitive value = new JsonPrimitive(0);
        AddArrayItemCommand cmd = new AddArrayItemCommand(array, 0, value);

        assertTrue(cmd.execute());

        assertSame(value, array.get(0));
        assertEquals("[0, 1, 2]", doc.serialize(2, 0));
    }

    @Test
    void undoRemovesAndRedoReinsertsAtSameIndex() {
        JsonPrimitive value = new JsonPrimitive("x");
        AddArrayItemCommand cmd = new AddArrayItemCommand(array, 1, value);
        cmd.execute();

        assertTrue(cmd.undo());
        assertEquals(2, array.size());
        assertEquals("[1, 2]", doc.serialize(2, 0));
        assertNull(value.getParent());

        assertTrue(cmd.execute());
        assertSame(value, array.get(1));
        assertEquals(3, array.size());
    }

    @Test
    void outOfRangeIndexIsRejected() {
        assertFalse(new AddArrayItemCommand(array, 3, new JsonPrimitive(9)).execute());
        assertFalse(new AddArrayItemCommand(array, -2, new JsonPrimitive(9)).execute());
        assertEquals(2, array.size());
    }

    @Test
    void invalidConstructorArgumentsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new AddArrayItemCommand(null, -1, new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> new AddArrayItemCommand(array, -1, null));
    }

    @Test
    void descriptionIsPresent() {
        assertFalse(new AddArrayItemCommand(array, -1, new JsonPrimitive(1)).getDescription().isBlank());
    }
}
