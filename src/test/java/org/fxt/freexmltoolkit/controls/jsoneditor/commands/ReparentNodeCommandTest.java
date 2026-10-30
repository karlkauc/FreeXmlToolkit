package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Moving JSON nodes between containers, and the composite step around several commands. */
class ReparentNodeCommandTest {

    private JsonObject root;
    private JsonArray list;
    private JsonObject meta;

    @BeforeEach
    void setUp() {
        JsonDocument doc = JsonNodeFactory.parse("{\"a\": 1, \"list\": [10, 20], \"meta\": {\"b\": 2}}");
        root = (JsonObject) doc.getRootValue();
        list = (JsonArray) root.getProperty("list");
        meta = (JsonObject) root.getProperty("meta");
    }

    @Test
    void movesAPropertyIntoAnArrayAndBack() {
        JsonPrimitive a = (JsonPrimitive) root.getProperty("a");
        ReparentNodeCommand cmd = new ReparentNodeCommand(a, list, 1, "ignored");
        assertTrue(cmd.execute());
        assertSame(a, list.get(1));
        assertNull(a.getKey(), "array items have no key");
        assertFalse(root.hasProperty("a"));

        assertTrue(cmd.undo());
        assertSame(a, root.getChild(0));
        assertEquals("a", a.getKey());
        assertEquals(2, list.size());
    }

    @Test
    void movesAnItemIntoAnObjectUnderTheGivenKey() {
        JsonPrimitive ten = (JsonPrimitive) list.get(0);
        assertTrue(new ReparentNodeCommand(ten, meta, -1, "ten").execute());
        assertSame(ten, meta.getProperty("ten"));
        assertEquals(1, meta.indexOf(ten), "appended");
    }

    @Test
    void rejectsTakenKeysAndMovesIntoItsOwnSubtree() {
        assertFalse(new ReparentNodeCommand(root.getProperty("a"), meta, -1, "b").execute(), "key taken");
        assertFalse(new ReparentNodeCommand(meta, meta, -1, "x").execute(), "into itself");
        assertTrue(root.hasProperty("a"));
    }

    @Test
    void compositeRollsBackWhenAPartFails() {
        CompositeJsonCommand cmd = new CompositeJsonCommand("two", List.of(
                new RenameKeyCommand(root.getProperty("a"), "z"),
                new RenameKeyCommand(root.getProperty("list"), "meta"))); // fails: key taken
        assertFalse(cmd.execute());
        assertTrue(root.hasProperty("a"), "the first rename is undone again");

        CompositeJsonCommand ok = new CompositeJsonCommand("two", List.of(
                new RenameKeyCommand(root.getProperty("a"), "z"),
                new RenameKeyCommand(root.getProperty("list"), "items")));
        assertTrue(ok.execute());
        assertTrue(root.hasProperty("z") && root.hasProperty("items"));
        assertTrue(ok.undo());
        assertTrue(root.hasProperty("a") && root.hasProperty("list"));
    }
}
