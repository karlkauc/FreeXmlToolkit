package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Failed XPath / XQuery / JSONPath / XProc runs must reach telemetry with a fixed error code,
 * and a broken expression or document must count as {@code invalid_input}, not as an app error.
 */
class QueryFailureTest {

    private RecordingTelemetryService telemetry;

    @BeforeEach
    void setUp() {
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        UsageEvents.resetSession();
    }

    @AfterEach
    void tearDown() {
        Telemetry.reset();
        UsageEvents.resetSession();
    }

    @Test
    void staticCodesAreInvalidInput() {
        for (String code : new String[] {"XPST0003", "XQST0031", "XTSE0010", "SXXP0003", "err:XS0001",
                "Q{http://www.w3.org/2005/xqt-errors}XPST0017"}) {
            assertEquals(TelemetryEvent.Status.INVALID_INPUT, QueryFailure.ofCode(code).status(), code);
        }
        assertEquals("XS0001", QueryFailure.ofCode("err:XS0001").errorCode());
        assertEquals("XPST0017", QueryFailure.ofCode("Q{http://www.w3.org/2005/xqt-errors}XPST0017").errorCode());
    }

    @Test
    void dynamicCodesStayErrorsButKeepTheCode() {
        QueryFailure f = QueryFailure.ofCode("FOAR0001");
        assertEquals(TelemetryEvent.Status.ERROR, f.status());
        assertEquals("FOAR0001", f.errorCode());
        assertNull(f.signature());
    }

    @Test
    void uncodedThrowableKeepsItsStackSignature() {
        QueryFailure f = QueryFailure.of(new IllegalStateException("secret message"));
        assertEquals(TelemetryEvent.Status.ERROR, f.status());
        assertNotNull(f.signature());
        assertNotNull(f.signature().hash());
        assertFalse(f.signature().detail().contains("secret message"), "message text must never be sent");
    }

    @Test
    void missingInformationIsUnknown() {
        assertSame(QueryFailure.UNKNOWN, QueryFailure.of(null));
        assertSame(QueryFailure.UNKNOWN, QueryFailure.ofCode(null));
    }

    @Test
    void brokenXPathExpressionIsReportedAsInvalidInputWithSaxonCode() {
        TransformRunner.QueryRun run = TransformRunner.xpath("<a><b/></a>", "//b[");
        assertTrue(run.text().startsWith("ERROR"));
        assertNotNull(run.failure());

        UsageEvents.xpathExecuted(0, run.failure());
        TelemetryEvent e = telemetry.events("xpath").get(0);
        assertEquals(TelemetryEvent.Status.INVALID_INPUT, e.status());
        assertEquals("XPST0003", e.errorCode());
    }

    @Test
    void successfulXPathHasNoFailure() {
        TransformRunner.QueryRun run = TransformRunner.xpath("<a><b>1</b></a>", "string(//b)");
        assertNull(run.failure());
        assertEquals("1", run.text().strip());

        UsageEvents.xpathExecuted(0, run.failure());
        TelemetryEvent e = telemetry.events("xpath").get(0);
        assertEquals(TelemetryEvent.Status.OK, e.status());
        assertNull(e.errorCode());
    }

    @Test
    void brokenXQueryCarriesItsCode() {
        TransformRunner.QueryRun run = TransformRunner.xquery("<a/>", "for $x in", java.util.Map.of(),
                XsltTransformationEngine.OutputFormat.XML);
        assertNotNull(run.failure());

        UsageEvents.xqueryExecuted(0, run.failure());
        TelemetryEvent e = telemetry.events("xquery").get(0);
        assertEquals(TelemetryEvent.Status.INVALID_INPUT, e.status());
        assertEquals("XPST0003", e.errorCode());
    }

    @Test
    void brokenJsonPathIsInvalidInput() {
        TransformRunner.QueryRun run = TransformRunner.jsonPath("{\"a\":1}", "$[");
        assertNotNull(run.failure(), "result was: " + run.text());
        assertEquals(TelemetryEvent.Status.INVALID_INPUT, run.failure().status());
    }

    @Test
    void booleanOverloadsKeepReportingPlainErrors() {
        UsageEvents.xprocExecuted(0, false);
        UsageEvents.jsonPathExecuted(0, true);
        assertEquals(TelemetryEvent.Status.ERROR, telemetry.events("xproc").get(0).status());
        assertEquals(TelemetryEvent.Status.OK, telemetry.events("jsonpath").get(0).status());
    }
}
