package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AddPropertyCommand}.
 */
class AddPropertyCommandTest {

    private JsonDocument doc;
    private JsonObject root;

    @BeforeEach
    void setUp() {
        doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2}");
        root = (JsonObject) doc.getRootValue();
    }

    @Test
    void appendWhenIndexIsMinusOne() {
        JsonPrimitive value = new JsonPrimitive("v");
        AddPropertyCommand cmd = new AddPropertyCommand(root, "c", value, -1);

        assertTrue(cmd.execute());

        assertEquals(3, root.getChildCount());
        assertSame(value, root.getChild(2));
        assertEquals("c", value.getKey());
        assertSame(root, value.getParent());
    }

    @Test
    void insertAtIndex() {
        JsonPrimitive value = new JsonPrimitive(true);
        AddPropertyCommand cmd = new AddPropertyCommand(root, "c", value, 0);

        assertTrue(cmd.execute());

        assertSame(value, root.getChild(0));
        assertEquals("a", root.getChild(1).getKey());
    }

    @Test
    void undoRemovesAndRedoReinsertsAtSameIndex() {
        JsonPrimitive value = new JsonPrimitive("v");
        AddPropertyCommand cmd = new AddPropertyCommand(root, "c", value, 1);
        cmd.execute();

        assertTrue(cmd.undo());
        assertEquals(2, root.getChildCount());
        assertFalse(root.hasProperty("c"));
        assertNull(value.getParent());

        assertTrue(cmd.execute());
        assertSame(value, root.getChild(1));
        assertEquals(3, root.getChildCount());
    }

    @Test
    void duplicateKeyIsRejected() {
        JsonPrimitive value = new JsonPrimitive("v");
        AddPropertyCommand cmd = new AddPropertyCommand(root, "a", value, -1);

        assertFalse(cmd.execute());
        assertEquals(2, root.getChildCount());
        assertNull(value.getParent());
    }

    @Test
    void outOfRangeIndexIsRejected() {
        assertFalse(new AddPropertyCommand(root, "c", new JsonPrimitive("v"), 5).execute());
        assertFalse(new AddPropertyCommand(root, "c", new JsonPrimitive("v"), -2).execute());
        assertEquals(2, root.getChildCount());
    }

    @Test
    void serializationReflectsAddedProperty() {
        new AddPropertyCommand(root, "c", new JsonPrimitive(3), -1).execute();
        assertEquals("{\n  \"a\": 1,\n  \"b\": 2,\n  \"c\": 3\n}", doc.serialize(2, 0));
    }

    @Test
    void invalidConstructorArgumentsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new AddPropertyCommand(null, "c", new JsonPrimitive(1), -1));
        assertThrows(IllegalArgumentException.class, () -> new AddPropertyCommand(root, null, new JsonPrimitive(1), -1));
        assertThrows(IllegalArgumentException.class, () -> new AddPropertyCommand(root, "c", null, -1));
    }

    @Test
    void descriptionMentionsKey() {
        assertTrue(new AddPropertyCommand(root, "newKey", new JsonPrimitive(1), -1).getDescription().contains("newKey"));
    }
}
