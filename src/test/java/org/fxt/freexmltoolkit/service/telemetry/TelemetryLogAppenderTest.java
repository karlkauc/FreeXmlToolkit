package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every logged exception reaches the error telemetry: ERROR and WARN events with a throwable
 * from the application's loggers, ERROR only from libraries, nothing from the telemetry
 * package itself, and nothing twice.
 */
class TelemetryLogAppenderTest {

    private RecordingTelemetryService telemetry;

    @BeforeEach
    void setUp() {
        Telemetry.reset();
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        TelemetryLogAppender.install();
        TelemetryLogAppender.install(); // idempotent
    }

    @AfterEach
    void tearDown() {
        TelemetryLogAppender.uninstall();
        Telemetry.reset();
    }

    @Test
    void loggedErrorsAndWarningsWithAThrowableAreReported() {
        Logger logger = LogManager.getLogger("org.fxt.freexmltoolkit.service.XmlServiceImpl");

        logger.error("Could not parse", new IOException("bad file"));
        logger.warn("Could not load schema", new IllegalStateException("x"));

        assertEquals(List.of("log.XmlServiceImpl", "logwarn.XmlServiceImpl"), telemetry.errorReports());
    }

    @Test
    void eventsWithoutAThrowableOrBelowWarnAreIgnored() {
        Logger logger = LogManager.getLogger("org.fxt.freexmltoolkit.service.XmlServiceImpl");

        logger.error("just a message");
        logger.warn("just a warning");
        logger.info("info with throwable", new IOException("x"));
        logger.debug("debug with throwable", new IOException("x"));

        assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());
    }

    @Test
    void librariesCountOnlyTheirErrors() {
        Logger fop = LogManager.getLogger("org.apache.fop.fonts.FontCache");

        fop.warn("font substituted", new IOException("expected"));
        fop.error("render failed", new IOException("unexpected"));

        assertEquals(List.of("log.FontCache"), telemetry.errorReports());
    }

    @Test
    void theTelemetryPackageNeverReportsItself() {
        LogManager.getLogger(TelemetryServiceImpl.class).error("send failed", new IOException("offline"));
        LogManager.getLogger(ErrorReportingThreadPoolExecutor.class).warn("task failed", new IOException("x"));

        assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());
    }

    @Test
    void anExplicitlyReportedThrowableIsNotCountedAgainWhenLogged() {
        Logger logger = LogManager.getLogger("org.fxt.freexmltoolkit.FxtGui");
        RuntimeException uncaught = new RuntimeException("boom");

        Telemetry.reportError(uncaught, "uncaught");
        logger.error("Uncaught exception in thread 'x'", uncaught);

        assertEquals(List.of("uncaught"), telemetry.errorReports());
    }

    @Test
    void simpleNameOfALogger() {
        assertEquals("XmlServiceImpl", TelemetryLogAppender.simpleName("org.fxt.freexmltoolkit.service.XmlServiceImpl"));
        assertEquals("Plain", TelemetryLogAppender.simpleName("Plain"));
        assertEquals("unknown", TelemetryLogAppender.simpleName(null));
        assertEquals("unknown", TelemetryLogAppender.simpleName(""));
    }
}
