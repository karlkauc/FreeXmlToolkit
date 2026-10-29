package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@link Telemetry} facade: errors reported before the real service exists are kept and
 * replayed, the same throwable counts once, and the async helpers report what futures and
 * schedulers would otherwise swallow.
 */
class TelemetryTest {

    private RecordingTelemetryService telemetry;

    @BeforeEach
    void setUp() {
        Telemetry.reset();
        telemetry = new RecordingTelemetryService();
    }

    @AfterEach
    void tearDown() {
        Telemetry.reset();
    }

    @Test
    void errorsReportedBeforeInstallAreReplayedOnInstall() {
        Telemetry.reportError(new IllegalStateException("early"), "main");
        Telemetry.reportError("ui.error_dialog", "dialog.Startup");
        assertEquals(2, Telemetry.pendingCount());

        Telemetry.install(telemetry);

        assertEquals(List.of("main", "dialog.Startup"), telemetry.errorReports());
        assertEquals(0, Telemetry.pendingCount());
        Telemetry.reportError(new IllegalStateException("late"), "later");
        assertEquals(List.of("main", "dialog.Startup", "later"), telemetry.errorReports(), "after install: direct");
    }

    @Test
    void theBufferIsBounded() {
        for (int i = 0; i < Telemetry.MAX_PENDING + 5; i++) {
            Telemetry.reportError(new RuntimeException("e" + i), "w" + i);
        }
        assertEquals(Telemetry.MAX_PENDING, Telemetry.pendingCount());
        Telemetry.install(telemetry);
        assertEquals(Telemetry.MAX_PENDING, telemetry.errorReports().size());
    }

    @Test
    void theSameThrowableIsReportedOnceAndTheFirstWhereWins() {
        Telemetry.install(telemetry);
        RuntimeException failure = new RuntimeException("boom");

        Telemetry.reportError(failure, "uncaught");
        Telemetry.reportError(failure, "dialog.Later");
        Telemetry.reportLogged(failure, "log.SomeClass");

        assertEquals(List.of("uncaught"), telemetry.errorReports());
    }

    @Test
    void loggedThrowablesAreReportedUnlessAlreadyKnownOrShutdownNoise() {
        Telemetry.install(telemetry);
        Telemetry.reportLogged(new IllegalArgumentException("x"), "log.XmlServiceImpl");
        assertEquals(List.of("log.XmlServiceImpl"), telemetry.errorReports());

        NullPointerException traceless = new NullPointerException();
        traceless.setStackTrace(new StackTraceElement[0]);
        Telemetry.markShuttingDown();
        Telemetry.reportLogged(traceless, "log.Glass");
        assertEquals(1, telemetry.errorReports().size(), "a stack-less NPE during shutdown is noise");
        assertTrue(Telemetry.isShutdownNoise(traceless));
        assertFalse(Telemetry.isShutdownNoise(new NullPointerException("real")));
    }

    @Test
    void reportUnexpectedUnwrapsFuturesAndIgnoresCancellation() {
        Telemetry.install(telemetry);
        IllegalStateException cause = new IllegalStateException("inner");

        assertSame(cause, Telemetry.reportUnexpected(new CompletionException(cause), "async"));
        assertNull(Telemetry.reportUnexpected(new CancellationException(), "async"));
        assertNull(Telemetry.reportUnexpected(new CompletionException(new InterruptedException()), "async"));
        assertNull(Telemetry.reportUnexpected(null, "async"));

        assertEquals(List.of("async"), telemetry.errorReports());
    }

    @Test
    void whenCompleteReportsFailedFuturesOnly() {
        Telemetry.install(telemetry);

        CompletableFuture.failedFuture(new RuntimeException("failed")).whenComplete(Telemetry.whenComplete("future"));
        CompletableFuture.completedFuture("ok").whenComplete(Telemetry.whenComplete("future"));

        assertEquals(List.of("future"), telemetry.errorReports());
    }

    @Test
    void parserFallbacksReportOnlyWhatIsNotTheUsersInput() {
        Telemetry.install(telemetry);

        Telemetry.reportUnlessUserInput(new RuntimeException(new org.xml.sax.SAXParseException("bad xml", null)), "editor.parse_xml");
        Telemetry.reportUnlessUserInput(new java.nio.file.NoSuchFileException("gone.xsd"), "editor.parse_xsd");
        Telemetry.reportUnlessUserInput(new com.google.gson.JsonSyntaxException("bad json"), "editor.parse_json");
        Telemetry.reportUnlessUserInput(new NullPointerException("parser bug"), "editor.parse_xml");
        Telemetry.reportUnlessUserInput(null, "editor.parse_xml");

        assertEquals(List.of("editor.parse_xml"), telemetry.errorReports(), "only the parser bug counts");
    }

    @Test
    void guardReportsAndSwallowsSoASchedulerKeepsRunning() {
        Telemetry.install(telemetry);
        int[] runs = new int[1];
        Runnable guarded = Telemetry.guard("periodic", () -> {
            runs[0]++;
            throw new IllegalStateException("tick failed");
        });

        assertDoesNotThrow(guarded::run);
        assertDoesNotThrow(guarded::run);

        assertEquals(2, runs[0]);
        assertEquals(List.of("periodic", "periodic"), telemetry.errorReports(), "different throwables, both reported");
    }
}
