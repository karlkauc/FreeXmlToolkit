package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RenameKeyCommand}.
 */
class RenameKeyCommandTest {

    private JsonDocument doc;
    private JsonObject root;

    @BeforeEach
    void setUp() {
        doc = JsonNodeFactory.parse("{\"a\": 1, \"b\": 2, \"c\": 3}");
        root = (JsonObject) doc.getRootValue();
    }

    @Test
    void executeRenamesKeyAndKeepsPosition() {
        JsonNode b = root.getProperty("b");
        RenameKeyCommand cmd = new RenameKeyCommand(b, "renamed");

        assertTrue(cmd.execute());

        assertEquals("renamed", b.getKey());
        assertEquals(1, root.indexOf(b));
        assertTrue(root.hasProperty("renamed"));
        assertFalse(root.hasProperty("b"));
        assertEquals("{\n  \"a\": 1,\n  \"renamed\": 2,\n  \"c\": 3\n}", doc.serialize(2, 0));
    }

    @Test
    void undoRestoresOldKey() {
        JsonNode b = root.getProperty("b");
        RenameKeyCommand cmd = new RenameKeyCommand(b, "renamed");
        cmd.execute();

        assertTrue(cmd.undo());

        assertEquals("b", b.getKey());
        assertEquals(1, root.indexOf(b));

        assertTrue(cmd.execute());
        assertEquals("renamed", b.getKey());
    }

    @Test
    void renameToExistingKeyIsRejected() {
        JsonNode b = root.getProperty("b");
        RenameKeyCommand cmd = new RenameKeyCommand(b, "c");

        assertFalse(cmd.execute());

        assertEquals("b", b.getKey());
        assertEquals(3, root.getChildCount());
    }

    @Test
    void renameToSameKeyIsAllowed() {
        JsonNode b = root.getProperty("b");
        RenameKeyCommand cmd = new RenameKeyCommand(b, "b");

        assertTrue(cmd.execute());
        assertEquals("b", b.getKey());
    }

    @Test
    void renameOfArrayItemIsRejected() {
        JsonArray array = new JsonArray();
        JsonPrimitive item = new JsonPrimitive(1);
        array.add(item);

        assertFalse(new RenameKeyCommand(item, "x").execute());
        assertNull(item.getKey());
    }

    @Test
    void renameOfDetachedNodeIsRejected() {
        assertFalse(new RenameKeyCommand(new JsonPrimitive("x"), "y").execute());
    }

    @Test
    void nullOrInvalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RenameKeyCommand(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new RenameKeyCommand(root.getProperty("a"), null));
    }

    @Test
    void descriptionMentionsKeys() {
        RenameKeyCommand cmd = new RenameKeyCommand(root.getProperty("a"), "z");
        assertTrue(cmd.getDescription().contains("a"));
        assertTrue(cmd.getDescription().contains("z"));
    }
}
