package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ChangeValueTypeCommand}.
 */
class ChangeValueTypeCommandTest {

    @Test
    void stringToIntegerNumberBecomesBigInteger() {
        JsonPrimitive target = new JsonPrimitive("42");
        ChangeValueTypeCommand cmd = new ChangeValueTypeCommand(target, JsonNodeType.NUMBER);

        assertTrue(cmd.execute());

        assertTrue(target.isNumber());
        assertEquals(new BigInteger("42"), target.getValue());
        assertEquals("42", target.serialize(2, 0));
    }

    @Test
    void stringToDecimalNumberBecomesBigDecimal() {
        JsonPrimitive target = new JsonPrimitive(" 3.5 ");
        assertTrue(new ChangeValueTypeCommand(target, JsonNodeType.NUMBER).execute());
        assertEquals(new BigDecimal("3.5"), target.getValue());

        JsonPrimitive exp = new JsonPrimitive("1e3");
        assertTrue(new ChangeValueTypeCommand(exp, JsonNodeType.NUMBER).execute());
        assertInstanceOf(BigDecimal.class, exp.getValue());
    }

    @Test
    void unparsableStringToNumberBecomesZero() {
        JsonPrimitive target = new JsonPrimitive("hello");
        assertTrue(new ChangeValueTypeCommand(target, JsonNodeType.NUMBER).execute());

        assertTrue(target.isNumber());
        assertEquals("0", target.serialize(2, 0));
    }

    @Test
    void numberToStringUsesTextForm() {
        JsonPrimitive target = new JsonPrimitive(42);
        assertTrue(new ChangeValueTypeCommand(target, JsonNodeType.STRING).execute());

        assertTrue(target.isString());
        assertEquals("42", target.getValue());
    }

    @Test
    void booleanToStringUsesTextForm() {
        JsonPrimitive target = new JsonPrimitive(Boolean.TRUE);
        assertTrue(new ChangeValueTypeCommand(target, JsonNodeType.STRING).execute());

        assertEquals("true", target.getValue());
    }

    @Test
    void nullToStringBecomesEmptyString() {
        JsonPrimitive target = JsonPrimitive.nullValue();
        assertTrue(new ChangeValueTypeCommand(target, JsonNodeType.STRING).execute());

        assertTrue(target.isString());
        assertEquals("", target.getValue());
    }

    @Test
    void stringToBooleanParses() {
        JsonPrimitive yes = new JsonPrimitive("TRUE");
        assertTrue(new ChangeValueTypeCommand(yes, JsonNodeType.BOOLEAN).execute());
        assertEquals(Boolean.TRUE, yes.getValue());

        JsonPrimitive no = new JsonPrimitive("anything");
        assertTrue(new ChangeValueTypeCommand(no, JsonNodeType.BOOLEAN).execute());
        assertEquals(Boolean.FALSE, no.getValue());
    }

    @Test
    void anythingToNull() {
        JsonPrimitive s = new JsonPrimitive("x");
        JsonPrimitive n = new JsonPrimitive(1);
        JsonPrimitive b = new JsonPrimitive(Boolean.TRUE);

        assertTrue(new ChangeValueTypeCommand(s, JsonNodeType.NULL).execute());
        assertTrue(new ChangeValueTypeCommand(n, JsonNodeType.NULL).execute());
        assertTrue(new ChangeValueTypeCommand(b, JsonNodeType.NULL).execute());

        assertTrue(s.isNull());
        assertTrue(n.isNull());
        assertTrue(b.isNull());
    }

    @Test
    void sameTypeIsNoOp() {
        JsonPrimitive target = new JsonPrimitive("x");
        assertFalse(new ChangeValueTypeCommand(target, JsonNodeType.STRING).execute());
        assertEquals("x", target.getValue());
    }

    @Test
    void undoRestoresOriginalObjectAndSerialization() {
        JsonDocument doc = JsonNodeFactory.parse("{\"n\": 12345678901234, \"s\": \"7\", \"z\": null}");
        JsonObject root = (JsonObject) doc.getRootValue();
        JsonPrimitive n = (JsonPrimitive) root.getProperty("n");
        JsonPrimitive s = (JsonPrimitive) root.getProperty("s");
        JsonPrimitive z = (JsonPrimitive) root.getProperty("z");
        Object originalN = n.getValue();
        String original = doc.serialize(2, 0);

        ChangeValueTypeCommand c1 = new ChangeValueTypeCommand(n, JsonNodeType.STRING);
        ChangeValueTypeCommand c2 = new ChangeValueTypeCommand(s, JsonNodeType.NUMBER);
        ChangeValueTypeCommand c3 = new ChangeValueTypeCommand(z, JsonNodeType.BOOLEAN);
        assertTrue(c1.execute());
        assertTrue(c2.execute());
        assertTrue(c3.execute());
        assertTrue(n.isString());
        assertTrue(s.isNumber());
        assertTrue(z.isBoolean());

        assertTrue(c3.undo());
        assertTrue(c2.undo());
        assertTrue(c1.undo());

        assertSame(originalN, n.getValue());
        assertEquals("7", s.getValue());
        assertTrue(z.isNull());
        assertEquals(original, doc.serialize(2, 0));

        assertTrue(c1.execute());
        assertEquals("12345678901234", n.getValue());
    }

    @Test
    void containerTypesAreRejected() {
        JsonPrimitive target = new JsonPrimitive("x");
        assertThrows(IllegalArgumentException.class, () -> new ChangeValueTypeCommand(target, JsonNodeType.OBJECT));
        assertThrows(IllegalArgumentException.class, () -> new ChangeValueTypeCommand(target, JsonNodeType.ARRAY));
        assertThrows(IllegalArgumentException.class, () -> new ChangeValueTypeCommand(target, null));
        assertThrows(IllegalArgumentException.class, () -> new ChangeValueTypeCommand(null, JsonNodeType.STRING));
    }

    @Test
    void descriptionMentionsTargetType() {
        assertTrue(new ChangeValueTypeCommand(new JsonPrimitive("x"), JsonNodeType.NUMBER)
                .getDescription().toLowerCase().contains("number"));
    }
}
