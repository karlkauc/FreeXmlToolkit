package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SetPrimitiveValueCommand}.
 */
class SetPrimitiveValueCommandTest {

    private static JsonPrimitive property(JsonDocument doc, String key) {
        return (JsonPrimitive) ((JsonObject) doc.getRootValue()).getProperty(key);
    }

    @Test
    void executeSetsValueAndUndoRestoresIt() {
        JsonPrimitive target = new JsonPrimitive("old");
        SetPrimitiveValueCommand cmd = new SetPrimitiveValueCommand(target, "new");

        assertTrue(cmd.execute());
        assertEquals("new", target.getValue());

        assertTrue(cmd.undo());
        assertEquals("old", target.getValue());

        assertTrue(cmd.execute());
        assertEquals("new", target.getValue());
    }

    @Test
    void undoRestoresNullType() {
        JsonPrimitive target = JsonPrimitive.nullValue();
        SetPrimitiveValueCommand cmd = new SetPrimitiveValueCommand(target, "text");

        cmd.execute();
        assertEquals(JsonNodeType.STRING, target.getNodeType());

        cmd.undo();
        assertTrue(target.isNull());
        assertNull(target.getValue());
        assertEquals(JsonNodeType.NULL, target.getNodeType());
    }

    @Test
    void undoRestoresExactNumberObjectAndSerialization() {
        String text = "{\"i\": 42, \"l\": 12345678901234, \"d\": 1.5}";
        JsonDocument doc = JsonNodeFactory.parse(text);
        String original = doc.serialize(2, 0);

        JsonPrimitive i = property(doc, "i");
        JsonPrimitive l = property(doc, "l");
        JsonPrimitive d = property(doc, "d");
        Object originalI = i.getValue();
        Object originalD = d.getValue();

        SetPrimitiveValueCommand c1 = new SetPrimitiveValueCommand(i, new BigDecimal("7.25"));
        SetPrimitiveValueCommand c2 = new SetPrimitiveValueCommand(l, "text");
        SetPrimitiveValueCommand c3 = new SetPrimitiveValueCommand(d, null);
        c1.execute();
        c2.execute();
        c3.execute();
        assertNotEquals(original, doc.serialize(2, 0));

        c3.undo();
        c2.undo();
        c1.undo();

        assertSame(originalI, i.getValue());
        assertSame(originalD, d.getValue());
        assertEquals(JsonNodeType.NUMBER, l.getNodeType());
        assertEquals(original, doc.serialize(2, 0));
    }

    @Test
    void descriptionIsHumanReadable() {
        SetPrimitiveValueCommand cmd = new SetPrimitiveValueCommand(new JsonPrimitive("a"), "b");
        assertNotNull(cmd.getDescription());
        assertFalse(cmd.getDescription().isBlank());
        assertTrue(cmd.canUndo());
    }

    @Test
    void mergeRequiresSameTargetAndContinuation() {
        JsonPrimitive target = new JsonPrimitive("a");
        JsonPrimitive other = new JsonPrimitive("x");
        SetPrimitiveValueCommand first = new SetPrimitiveValueCommand(target, "ab");
        first.execute();

        SetPrimitiveValueCommand plain = new SetPrimitiveValueCommand(target, "abc");
        assertFalse(first.canMergeWith(plain));

        SetPrimitiveValueCommand continuation = new SetPrimitiveValueCommand(target, "abc").asContinuation();
        assertTrue(continuation.isContinuation());
        assertTrue(first.canMergeWith(continuation));

        SetPrimitiveValueCommand otherTarget = new SetPrimitiveValueCommand(other, "abc").asContinuation();
        assertFalse(first.canMergeWith(otherTarget));

        assertFalse(first.canMergeWith(new RenameKeyCommand(target, "k")));
    }

    @Test
    void mergedCommandKeepsFirstOldValueAndLastNewValue() {
        JsonPrimitive target = new JsonPrimitive("a");
        SetPrimitiveValueCommand first = new SetPrimitiveValueCommand(target, "ab");
        first.execute();
        SetPrimitiveValueCommand second = new SetPrimitiveValueCommand(target, "abc").asContinuation();
        second.execute();

        JsonCommand merged = first.mergeWith(second);

        assertEquals("abc", target.getValue());
        assertTrue(merged.undo());
        assertEquals("a", target.getValue());
        assertTrue(merged.execute());
        assertEquals("abc", target.getValue());
    }

    @Test
    void commandManagerMergesContinuationsIntoOneUndoStep() {
        JsonPrimitive target = new JsonPrimitive("");
        JsonCommandManager manager = new JsonCommandManager();

        manager.executeCommand(new SetPrimitiveValueCommand(target, "h"));
        manager.executeCommand(new SetPrimitiveValueCommand(target, "he").asContinuation());
        manager.executeCommand(new SetPrimitiveValueCommand(target, "hel").asContinuation());

        assertEquals(1, manager.getUndoStackSize());
        assertEquals("hel", target.getValue());

        manager.undo();
        assertEquals("", target.getValue());

        manager.redo();
        assertEquals("hel", target.getValue());
    }

    @Test
    void mergeWithIncompatibleCommandThrows() {
        JsonPrimitive target = new JsonPrimitive("a");
        SetPrimitiveValueCommand first = new SetPrimitiveValueCommand(target, "b");
        SetPrimitiveValueCommand plain = new SetPrimitiveValueCommand(target, "c");

        assertThrows(IllegalArgumentException.class, () -> first.mergeWith(plain));
    }

    @Test
    void nullTargetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SetPrimitiveValueCommand(null, "x"));
    }
}
