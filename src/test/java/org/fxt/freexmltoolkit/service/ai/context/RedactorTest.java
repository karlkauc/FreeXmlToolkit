package org.fxt.freexmltoolkit.service.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedactorTest {

    @Test
    void xercesMessagesKeepComponentNamesOnly() {
        assertEquals("cvc-datatype-valid.1.2.1: «value» is not a valid value for 'integer'.",
                Redactor.message("cvc-datatype-valid.1.2.1: 'abc' is not a valid value for 'integer'."));
        assertEquals("cvc-type.3.1.3: The value «value» of element 'n' is not valid.",
                Redactor.message("cvc-type.3.1.3: The value 'MARKER-7f3a' of element 'n' is not valid."));
        String enumeration = Redactor.message("cvc-enumeration-valid: Value 'SECRET' is not facet-valid with respect "
                + "to enumeration '[OPEN, CLOSED]'. It must be a value from the enumeration.");
        assertFalse(enumeration.contains("SECRET"), enumeration);
        assertEquals("cvc-complex-type.2.4.a: Invalid content was found starting with element 'foo'.",
                Redactor.message("cvc-complex-type.2.4.a: Invalid content was found starting with element 'foo'."));
        assertFalse(Redactor.message("Value \"MARKER-7f3a\" for type 'IdType'").contains("MARKER"));
    }

    @Test
    void fragmentsLoseTheirValues() {
        String redacted = Redactor.fragment("<a id=\"MARKER-1\"><b x='MARKER-2'>MARKER-3</b><!-- MARKER-4 --><c/></a>");
        assertFalse(redacted.contains("MARKER"), redacted);
        assertTrue(redacted.contains("<b x=\"{value}\">{value}</b>"), redacted);
        assertTrue(redacted.contains("<c/>"), redacted);
    }
}
