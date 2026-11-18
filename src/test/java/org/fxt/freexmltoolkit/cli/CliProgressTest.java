package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

/** The progress line of long commands on stderr. */
class CliProgressTest {

    @Test
    void oneLineRewrittenInPlaceAndClearedAtTheEnd() {
        StringWriter out = new StringWriter();
        CliProgress progress = new CliProgress(new PrintWriter(out));
        progress.message("ESMA FIRDS: request 1 of 87");
        progress.progress(1, 87);
        assertEquals("ESMA FIRDS: request 1 of 87  [1/87]", progress.line());
        progress.message("x".repeat(300));
        assertEquals(100, progress.line().length(), "long messages are cut to the line width");
        progress.clear();
        String written = out.toString();
        assertTrue(written.startsWith("\rESMA FIRDS: request 1 of 87"), written);
        assertTrue(written.endsWith("\r"), "the line is cleared");
        assertTrue(!written.contains("\n"), "never a new line: stdout stays clean and stderr shows one line");
    }
}
