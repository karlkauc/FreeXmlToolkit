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
 * Tests for {@link ReplaceNodeCommand}.
 */
class ReplaceNodeCommandTest {

    @Test
    void replaceObjectPropertyCopiesKeyAndKeepsIndex() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2, \"c\": 3}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonNode b = root.getProperty("b");
        JsonArray replacement = new JsonArray();
        replacement.add(new JsonPrimitive(9));

        ReplaceNodeCommand cmd = new ReplaceNodeCommand(b, replacement);
        assertTrue(cmd.execute());

        assertSame(replacement, root.getChild(1));
        assertEquals("b", replacement.getKey());
        assertSame(root, replacement.getParent());
        assertNull(b.getParent());
        assertEquals(3, root.getChildCount());
        assertEquals("{\n  \"a\": 1,\n  \"b\": [9],\n  \"c\": 3\n}", doc.serialize(2, 0));
    }

    @Test
    void undoSwapsBackAndRedoSwapsAgain() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonNode b = root.getProperty("b");
        String original = doc.serialize(2, 0);
        JsonPrimitive replacement = new JsonPrimitive("text");

        ReplaceNodeCommand cmd = new ReplaceNodeCommand(b, replacement);
        cmd.execute();

        assertTrue(cmd.undo());
        assertSame(b, root.getChild(1));
        assertSame(root, b.getParent());
        assertNull(replacement.getParent());
        assertEquals(original, doc.serialize(2, 0));

        assertTrue(cmd.execute());
        assertSame(replacement, root.getChild(1));
    }

    @Test
    void replaceArrayItem() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2, 3]");
        JsonArray array = (JsonArray) doc.getRootValue();

        ReplaceNodeCommand cmd = new ReplaceNodeCommand(array.get(1), new JsonPrimitive(false));
        assertTrue(cmd.execute());
        assertEquals("[1, false, 3]", doc.serialize(2, 0));

        assertTrue(cmd.undo());
        assertEquals("[1, 2, 3]", doc.serialize(2, 0));
    }

    @Test
    void replaceRootValue() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1}");
        JsonNode oldRoot = doc.getRootValue();
        JsonArray replacement = new JsonArray();

        ReplaceNodeCommand cmd = new ReplaceNodeCommand(oldRoot, replacement);
        assertTrue(cmd.execute());
        assertSame(replacement, doc.getRootValue());
        assertEquals("[]", doc.serialize(2, 0));

        assertTrue(cmd.undo());
        assertSame(oldRoot, doc.getRootValue());
    }

    @Test
    void detachedNodeIsRejected() {
        assertFalse(new ReplaceNodeCommand(new JsonPrimitive(1), new JsonPrimitive(2)).execute());
    }

    @Test
    void invalidConstructorArgumentsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ReplaceNodeCommand(null, new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> new ReplaceNodeCommand(new JsonPrimitive(1), null));
        JsonPrimitive same = new JsonPrimitive(1);
        assertThrows(IllegalArgumentException.class, () -> new ReplaceNodeCommand(same, same));
    }

    @Test
    void descriptionIsPresent() {
        assertFalse(new ReplaceNodeCommand(new JsonPrimitive(1), new JsonPrimitive(2)).getDescription().isBlank());
    }
}
