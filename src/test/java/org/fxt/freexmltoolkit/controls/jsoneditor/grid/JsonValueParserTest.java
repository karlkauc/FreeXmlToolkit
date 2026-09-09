package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JsonValueParser}.
 */
class JsonValueParserTest {

    // ==================== NUMBER ====================

    @Test
    void numberWithoutFractionBecomesBigInteger() {
        Object value = JsonValueParser.parse(" 42 ", JsonNodeType.NUMBER);
        assertEquals(new BigInteger("42"), value);
        assertInstanceOf(BigInteger.class, value);
    }

    @Test
    void hugeIntegerStaysExact() {
        Object value = JsonValueParser.parse("123456789012345678901234567890", JsonNodeType.NUMBER);
        assertEquals(new BigInteger("123456789012345678901234567890"), value);
    }

    @Test
    void numberWithFractionBecomesBigDecimal() {
        Object value = JsonValueParser.parse("3.14", JsonNodeType.NUMBER);
        assertEquals(new BigDecimal("3.14"), value);
        assertInstanceOf(BigDecimal.class, value);
    }

    @Test
    void numberWithExponentBecomesBigDecimal() {
        assertInstanceOf(BigDecimal.class, JsonValueParser.parse("1e5", JsonNodeType.NUMBER));
        assertInstanceOf(BigDecimal.class, JsonValueParser.parse("2E-3", JsonNodeType.NUMBER));
    }

    @Test
    void negativeNumbersParse() {
        assertEquals(new BigInteger("-7"), JsonValueParser.parse("-7", JsonNodeType.NUMBER));
        assertEquals(new BigDecimal("-0.5"), JsonValueParser.parse("-0.5", JsonNodeType.NUMBER));
    }

    @Test
    void unparsableNumberThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("abc", JsonNodeType.NUMBER));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("", JsonNodeType.NUMBER));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("1.2.3", JsonNodeType.NUMBER));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse(null, JsonNodeType.NUMBER));
    }

    // ==================== BOOLEAN ====================

    @Test
    void booleanAcceptsTrueAndFalseCaseInsensitively() {
        assertEquals(Boolean.TRUE, JsonValueParser.parse("true", JsonNodeType.BOOLEAN));
        assertEquals(Boolean.TRUE, JsonValueParser.parse(" TRUE ", JsonNodeType.BOOLEAN));
        assertEquals(Boolean.FALSE, JsonValueParser.parse("False", JsonNodeType.BOOLEAN));
    }

    @Test
    void booleanRejectsOtherText() {
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("yes", JsonNodeType.BOOLEAN));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("1", JsonNodeType.BOOLEAN));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse(null, JsonNodeType.BOOLEAN));
    }

    // ==================== NULL ====================

    @Test
    void nullTypeMapsNullTextToNull() {
        assertNull(JsonValueParser.parse("null", JsonNodeType.NULL));
        assertNull(JsonValueParser.parse(" null ", JsonNodeType.NULL));
        assertNull(JsonValueParser.parse(null, JsonNodeType.NULL));
    }

    @Test
    void nullTypeKeepsOtherTextAsString() {
        assertEquals("hello", JsonValueParser.parse("hello", JsonNodeType.NULL));
    }

    // ==================== STRING ====================

    @Test
    void stringIsTakenAsIs() {
        assertEquals("  42 ", JsonValueParser.parse("  42 ", JsonNodeType.STRING));
        assertEquals("null", JsonValueParser.parse("null", JsonNodeType.STRING));
        assertEquals("", JsonValueParser.parse(null, JsonNodeType.STRING));
    }

    @Test
    void containerTypesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("x", JsonNodeType.OBJECT));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("x", JsonNodeType.ARRAY));
        assertThrows(IllegalArgumentException.class, () -> JsonValueParser.parse("x", null));
    }

    // ==================== display ====================

    @Test
    void displayReturnsNullForNullPrimitive() {
        assertNull(JsonValueParser.display(JsonPrimitive.nullValue()));
        assertNull(JsonValueParser.display(null));
    }

    @Test
    void displayReturnsRawTextForOtherPrimitives() {
        assertEquals("hello", JsonValueParser.display(new JsonPrimitive("hello")));
        assertEquals("42", JsonValueParser.display(new JsonPrimitive(42)));
        assertEquals("true", JsonValueParser.display(new JsonPrimitive(Boolean.TRUE)));
        assertEquals("3.14", JsonValueParser.display(new JsonPrimitive(new BigDecimal("3.14"))));
    }
}
