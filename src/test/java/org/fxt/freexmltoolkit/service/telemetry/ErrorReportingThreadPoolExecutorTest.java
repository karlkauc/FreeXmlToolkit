package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tasks that fail on the reporting executors reach the error telemetry — also on the scheduled
 * variant, where every task (even an {@code execute()}d one) is wrapped in a future that would
 * otherwise swallow the exception.
 */
class ErrorReportingThreadPoolExecutorTest {

    private RecordingTelemetryService telemetry;

    @BeforeEach
    void setUp() {
        Telemetry.reset();
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
    }

    @AfterEach
    void tearDown() {
        Telemetry.reset();
    }

    private static void await(ScheduledThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void submittedTaskFailureIsReported() throws Exception {
        ErrorReportingThreadPoolExecutor executor = ErrorReportingThreadPoolExecutor.fixed(1, Thread::new, "test.fixed"); // NOPMD - shut down below
        executor.submit(() -> {
            throw new IllegalStateException("submitted");
        });
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(List.of("test.fixed"), telemetry.errorReports());
    }

    @Test
    void scheduledAndExecutedTasksOnTheScheduledPoolAreReported() throws Exception {
        ScheduledThreadPoolExecutor executor = ErrorReportingThreadPoolExecutor.scheduled(1, Thread::new, "test.scheduled"); // NOPMD - shut down by await()
        executor.schedule(() -> {
            throw new IllegalStateException("scheduled");
        }, 1, TimeUnit.MILLISECONDS);
        executor.execute(() -> {
            throw new IllegalArgumentException("executed");
        });
        executor.submit(() -> "fine");
        await(executor);

        assertEquals(List.of("test.scheduled", "test.scheduled"), telemetry.errorReports());
    }

    @Test
    void aPeriodicTaskThatDiesIsReportedOnce() throws Exception {
        ScheduledThreadPoolExecutor executor = ErrorReportingThreadPoolExecutor.scheduled(1, Thread::new, "test.periodic"); // NOPMD - shut down by await()
        CountDownLatch ran = new CountDownLatch(1);
        executor.scheduleAtFixedRate(() -> {
            ran.countDown();
            throw new IllegalStateException("tick");
        }, 1, 1, TimeUnit.MILLISECONDS);
        assertTrue(ran.await(5, TimeUnit.SECONDS));
        Thread.sleep(50);
        await(executor);

        assertEquals(List.of("test.periodic"), telemetry.errorReports(), "the schedule ended with the first failure");
    }

    @Test
    void cancelledTasksAreNotReported() throws Exception {
        ScheduledThreadPoolExecutor executor = ErrorReportingThreadPoolExecutor.scheduled(1, Thread::new, "test.cancel"); // NOPMD - shut down by await()
        var future = executor.schedule(() -> {
            throw new IllegalStateException("never runs");
        }, 1, TimeUnit.HOURS);
        future.cancel(false);
        await(executor);

        assertTrue(telemetry.errorReports().isEmpty());
    }
}
