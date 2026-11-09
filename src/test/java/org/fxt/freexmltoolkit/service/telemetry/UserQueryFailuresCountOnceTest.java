package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A run-time error in the user's XQuery (a failed cast, a {@code doc()} that finds nothing) is
 * counted once, by the action event with its code — not again by the log bridge as an
 * application error (telemetry 09.11.2026: {@code error log.xslttransformationengine} for
 * {@code FORG0001} / {@code FODC0002} from the Query Console).
 */
class UserQueryFailuresCountOnceTest {

    private RecordingTelemetryService telemetry;

    @BeforeEach
    void setUp() {
        Telemetry.reset();
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        TelemetryLogAppender.install();
    }

    @AfterEach
    void tearDown() {
        TelemetryLogAppender.uninstall();
        Telemetry.reset();
    }

    @Test
    void failedCastIsNotReportedByTheLogBridge() {
        XsltTransformationResult result = run("xs:integer('abc')");

        assertFalse(result.isSuccess());
        assertEquals("FORG0001", result.getErrorCode(), "the code still reaches the action event");
        assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());
    }

    @Test
    void missingDocumentIsNotReportedByTheLogBridge() {
        String missing = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "no-such-file-" + System.nanoTime() + ".xml").toUri().toString();
        XsltTransformationResult result = run("doc('" + missing + "')");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorCode() != null && result.getErrorCode().startsWith("FODC"), result.getErrorCode());
        assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());
    }

    private static XsltTransformationResult run(String query) {
        return XsltTransformationEngine.getInstance().transformXQuery("<root/>", query, Map.of(),
                XsltTransformationEngine.OutputFormat.XML);
    }
}
