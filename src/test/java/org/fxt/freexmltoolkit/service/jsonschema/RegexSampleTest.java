package org.fxt.freexmltoolkit.service.jsonschema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** Tests {@link RegexSample}. */
class RegexSampleTest {

    private static void assertMatches(String regex) {
        String sample = RegexSample.of(regex);
        assertNotNull(sample, regex);
        assertTrue(Pattern.compile(regex).matcher(sample).find(), regex + " → " + sample);
    }

    @Test
    void commonSchemaPatterns() {
        assertEquals("AA-1", RegexSample.of("^[A-Z]{2}-\\d+$"));
        assertMatches("^\\w+@\\w+\\.com$");
        assertMatches("^(DE|AT)\\d{2}[A-Z0-9]{4,}$");
        assertMatches("^[^@\\s]+@[^@\\s]+$");
        assertMatches("^(?:\\+\\d{1,3})?\\d{6,10}$");
        assertMatches("^[a-z][a-z0-9_-]*$");
        assertMatches("v\\d+\\.\\d+");
    }

    @Test
    void unknownConstructsGiveNull() {
        assertNull(RegexSample.of("^(?=.*\\d).{8,}$"), "lookahead");
        assertNull(RegexSample.of("[unclosed"));
        assertNull(RegexSample.of(null));
    }
}
