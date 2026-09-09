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
 * Tests for {@link DuplicateNodeCommand}.
 */
class DuplicateNodeCommandTest {

    @Test
    void duplicateObjectPropertyGetsCopySuffixAndFollowsOriginal() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": {\"x\": true}, \"c\": 3}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonNode b = root.getProperty("b");

        DuplicateNodeCommand cmd = new DuplicateNodeCommand(b);
        assertTrue(cmd.execute());

        JsonNode duplicate = cmd.getDuplicate();
        assertNotNull(duplicate);
        assertNotSame(b, duplicate);
        assertEquals("b_copy", duplicate.getKey());
        assertEquals(4, root.getChildCount());
        assertSame(duplicate, root.getChild(2));
        assertSame(root, duplicate.getParent());
        assertEquals(b.serialize(2, 0), duplicate.serialize(2, 0));
    }

    @Test
    void duplicateIsDeepCopy() {
        JsonDocument doc = JsonNodeFactory.parse("{\"b\": {\"x\": true}}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonObject b = (JsonObject) root.getProperty("b");

        DuplicateNodeCommand cmd = new DuplicateNodeCommand(b);
        cmd.execute();
        JsonObject copy = (JsonObject) cmd.getDuplicate();

        ((JsonPrimitive) copy.getProperty("x")).setValue(false);
        assertEquals(Boolean.TRUE, ((JsonPrimitive) b.getProperty("x")).getValue());
    }

    @Test
    void copySuffixIsNumberedUntilUnique() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"a_copy\": 2, \"a_copy2\": 3}");
        JsonObject root = (JsonObject) doc.getRootValue();

        DuplicateNodeCommand cmd = new DuplicateNodeCommand(root.getProperty("a"));
        assertTrue(cmd.execute());

        assertEquals("a_copy3", cmd.getDuplicate().getKey());
        assertSame(cmd.getDuplicate(), root.getChild(1));
    }

    @Test
    void duplicateArrayItemHasNoKey() {
        JsonDocument doc = JsonNodeFactory.parse("[1, 2, 3]");
        JsonArray array = (JsonArray) doc.getRootValue();

        DuplicateNodeCommand cmd = new DuplicateNodeCommand(array.get(1));
        assertTrue(cmd.execute());

        assertNull(cmd.getDuplicate().getKey());
        assertEquals("[1, 2, 2, 3]", doc.serialize(2, 0));
    }

    @Test
    void undoRemovesDuplicateAndRedoReinsertsSameInstance() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2}");
        JsonObject root = (JsonObject) doc.getRootValue();
        String original = doc.serialize(2, 0);

        DuplicateNodeCommand cmd = new DuplicateNodeCommand(root.getProperty("a"));
        cmd.execute();
        JsonNode duplicate = cmd.getDuplicate();

        assertTrue(cmd.undo());
        assertEquals(original, doc.serialize(2, 0));
        assertNull(duplicate.getParent());

        assertTrue(cmd.execute());
        assertSame(duplicate, cmd.getDuplicate());
        assertSame(duplicate, root.getChild(1));
        assertEquals("a_copy", duplicate.getKey());
    }

    @Test
    void duplicatingRootOrDetachedNodeIsRefused() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1}");
        assertFalse(new DuplicateNodeCommand(doc.getRootValue()).execute());
        assertFalse(new DuplicateNodeCommand(new JsonPrimitive(1)).execute());
    }

    @Test
    void nullNodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DuplicateNodeCommand(null));
    }

    @Test
    void descriptionIsPresent() {
        assertFalse(new DuplicateNodeCommand(new JsonPrimitive(1)).getDescription().isBlank());
    }
}
