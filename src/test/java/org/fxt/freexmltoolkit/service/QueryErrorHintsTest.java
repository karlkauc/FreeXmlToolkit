package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent;
import org.junit.jupiter.api.Test;

/**
 * {@link QueryErrorHints}: which codes get a hint, how it is appended, and that real XPath / XQuery runs
 * through {@link TransformRunner} show it while their telemetry classification stays the same.
 */
class QueryErrorHintsTest {

    private static final String XML = "<funds><Fund><Name>A</Name><Ccy>EUR</Ccy><Amount>5</Amount></Fund>"
            + "<Fund><Name>B</Name><Ccy>USD</Ccy><Amount>x</Amount></Fund></funds>";

    @Test
    void everyCodeHasAHintAndPrefixesAreIgnored() {
        for (String code : QueryErrorHints.CODES) {
            assertNotNull(QueryErrorHints.hintFor(code), code);
        }
        assertNotNull(QueryErrorHints.hintFor("err:XPTY0019"));
        assertNotNull(QueryErrorHints.hintFor("Q{http://www.w3.org/2005/xqt-errors}XPTY0019"));
        assertNull(QueryErrorHints.hintFor("XPST0003"), "syntax errors get the processor's message only");
        assertNull(QueryErrorHints.hintFor(null));
        assertNull(QueryErrorHints.hintFor(" "));
    }

    @Test
    void theHintIsAppendedOnceAndIndented() {
        String text = QueryErrorHints.withHint("ERROR: boom", "XPTY0019");
        assertTrue(text.startsWith("ERROR: boom\nHint: A path step"), text);
        for (String line : text.split("\n")) {
            assertTrue(line.startsWith("ERROR:") || line.startsWith("Hint: ") || line.startsWith("      "), line);
        }
        assertEquals(text, QueryErrorHints.withHint(text, "XPTY0019"), "no second hint");
        assertEquals("ERROR: boom", QueryErrorHints.withHint("ERROR: boom", "XPST0003"));
        assertEquals("ERROR: boom", QueryErrorHints.withHint("ERROR: boom", null));
    }

    @Test
    void anXPathStepOnAValueShowsTheHint() {
        TransformRunner.QueryRun run = TransformRunner.xpath(XML, "distinct-values(//Ccy)/../Name");
        assertTrue(run.text().startsWith("ERROR:"), run.text());
        assertEquals("XPTY0019", run.failure().errorCode(), run.text());
        assertEquals(TelemetryEvent.Status.ERROR, run.failure().status(), "classification unchanged");
        assertTrue(run.text().contains("\nHint: A path step \"/\" was applied to a value"), run.text());
    }

    @Test
    void anAxisStepInAPredicateOnValuesShowsTheHint() {
        TransformRunner.QueryRun run = TransformRunner.xpath(XML, "distinct-values(//Ccy)[../Name]");
        assertEquals("XPTY0020", run.failure().errorCode(), run.text());
        assertTrue(run.text().contains("\nHint: An axis step"), run.text());
    }

    @Test
    void xqueryErrorsShowTheHintToo() {
        TransformRunner.QueryRun cast = TransformRunner.xquery(XML, "sum(//Amount ! xs:decimal(.))", Map.of(),
                OutputFormat.XML);
        assertEquals("FORG0001", cast.failure().errorCode(), cast.text());
        assertTrue(cast.text().contains("\nHint: A value could not be converted"), cast.text());

        TransformRunner.QueryRun type = TransformRunner.xquery(XML, "string(//Fund[1]/Amount) + 1", Map.of(),
                OutputFormat.XML);
        assertEquals("XPTY0004", type.failure().errorCode(), type.text());
        assertTrue(type.text().contains("\nHint: A value has the wrong type"), type.text());
    }

    @Test
    void syntaxErrorsGetNoHint() {
        TransformRunner.QueryRun run = TransformRunner.xpath(XML, "//Fund[");
        assertTrue(run.text().startsWith("ERROR:"), run.text());
        assertEquals(TelemetryEvent.Status.INVALID_INPUT, run.failure().status());
        assertFalse(run.text().contains("Hint:"), run.text());
    }
}
