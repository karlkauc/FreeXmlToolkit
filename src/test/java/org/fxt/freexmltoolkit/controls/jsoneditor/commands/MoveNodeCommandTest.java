package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MoveNodeCommand}.
 */
class MoveNodeCommandTest {

    @Test
    void moveArrayItemDownAndUndo() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2, 3, 4]");
        JsonArray array = (JsonArray) doc.getRootValue();
        JsonNode first = array.get(0);

        MoveNodeCommand cmd = new MoveNodeCommand(first, 2);
        assertTrue(cmd.execute());
        assertEquals("[2, 3, 1, 4]", doc.serialize(2, 0));
        assertSame(first, array.get(2));

        assertTrue(cmd.undo());
        assertEquals("[1, 2, 3, 4]", doc.serialize(2, 0));

        assertTrue(cmd.execute());
        assertEquals("[2, 3, 1, 4]", doc.serialize(2, 0));
    }

    @Test
    void moveArrayItemUp() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2, 3, 4]");
        JsonArray array = (JsonArray) doc.getRootValue();

        MoveNodeCommand cmd = new MoveNodeCommand(array.get(3), 0);
        assertTrue(cmd.execute());
        assertEquals("[4, 1, 2, 3]", doc.serialize(2, 0));

        assertTrue(cmd.undo());
        assertEquals("[1, 2, 3, 4]", doc.serialize(2, 0));
    }

    @Test
    void moveObjectPropertyKeepsKey() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2, \"c\": 3}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonNode c = root.getProperty("c");

        assertTrue(new MoveNodeCommand(c, 0).execute());

        assertSame(c, root.getChild(0));
        assertEquals("c", c.getKey());
        assertEquals("{\n  \"c\": 3,\n  \"a\": 1,\n  \"b\": 2\n}", doc.serialize(2, 0));
    }

    @Test
    void moveToSameIndexIsNoOp() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2]");
        JsonArray array = (JsonArray) doc.getRootValue();

        assertFalse(new MoveNodeCommand(array.get(1), 1).execute());
        assertEquals("[1, 2]", doc.serialize(2, 0));
    }

    @Test
    void outOfRangeIndexIsRejected() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2]");
        JsonArray array = (JsonArray) doc.getRootValue();

        assertFalse(new MoveNodeCommand(array.get(0), 2).execute());
        assertFalse(new MoveNodeCommand(array.get(0), -1).execute());
        assertEquals("[1, 2]", doc.serialize(2, 0));
    }

    @Test
    void detachedNodeIsRejected() {
        assertFalse(new MoveNodeCommand(new JsonPrimitive(1), 0).execute());
    }

    @Test
    void nullNodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MoveNodeCommand(null, 0));
    }

    @Test
    void descriptionIsPresent() {
        assertFalse(new MoveNodeCommand(new JsonPrimitive(1), 0).getDescription().isBlank());
    }
}
