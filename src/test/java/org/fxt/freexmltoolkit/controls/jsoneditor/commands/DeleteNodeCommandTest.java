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
 * Tests for {@link DeleteNodeCommand}.
 */
class DeleteNodeCommandTest {

    @Test
    void deleteObjectPropertyAndUndoReinsertsAtSameIndex() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2, \"c\": 3}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonNode b = root.getProperty("b");
        String original = doc.serialize(2, 0);

        DeleteNodeCommand cmd = new DeleteNodeCommand(b);
        assertTrue(cmd.execute());

        assertEquals(2, root.getChildCount());
        assertFalse(root.hasProperty("b"));
        assertNull(b.getParent());

        assertTrue(cmd.undo());
        assertEquals(3, root.getChildCount());
        assertSame(b, root.getChild(1));
        assertEquals("b", b.getKey());
        assertSame(root, b.getParent());
        assertEquals(original, doc.serialize(2, 0));

        assertTrue(cmd.execute());
        assertEquals(2, root.getChildCount());
    }

    @Test
    void deleteArrayItem() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2, 3]");
        JsonArray array = (JsonArray) doc.getRootValue();
        JsonNode second = array.get(1);

        DeleteNodeCommand cmd = new DeleteNodeCommand(second);
        assertTrue(cmd.execute());
        assertEquals("[1, 3]", doc.serialize(2, 0));

        assertTrue(cmd.undo());
        assertEquals("[1, 2, 3]", doc.serialize(2, 0));
    }

    @Test
    void deletingRootValueIsRefused() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1}");
        JsonNode root = doc.getRootValue();

        assertFalse(new DeleteNodeCommand(root).execute());
        assertSame(root, doc.getRootValue());
    }

    @Test
    void deletingDetachedNodeIsRefused() {
        assertFalse(new DeleteNodeCommand(new JsonPrimitive(1)).execute());
    }

    @Test
    void nullNodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteNodeCommand(null));
    }

    @Test
    void descriptionMentionsKey() {
        JsonDocument doc = JsonNodeFactory.parse("{\"myKey\": 1}");
        JsonNode node = ((JsonObject) doc.getRootValue()).getProperty("myKey");
        assertTrue(new DeleteNodeCommand(node).getDescription().contains("myKey"));
    }
}
