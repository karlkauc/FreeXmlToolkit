package org.fxt.freexmltoolkit.service.telemetry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ThreadPoolExecutor} that reports exceptions thrown by its tasks to
 * {@link Telemetry#reportError(Throwable, String)} and logs them.
 *
 * <p>Exceptions of {@code submit()}ted tasks are normally captured in the returned
 * {@link Future} and silently lost when nobody calls {@code get()}. {@link #afterExecute}
 * unwraps them from the completed {@code FutureTask} (without changing what callers see —
 * a later {@code get()} still throws the same {@link ExecutionException}). Cancellation and
 * interruption are not reported. Tasks run through {@code CompletableFuture.*Async} capture
 * their exception inside the future and are therefore not seen here.
 */
public class ErrorReportingThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger logger = LogManager.getLogger(ErrorReportingThreadPoolExecutor.class);

    private final String where;

    /**
     * @param corePoolSize    core threads
     * @param maximumPoolSize max threads
     * @param keepAlive       keep-alive time
     * @param unit            keep-alive unit
     * @param workQueue       task queue
     * @param threadFactory   thread factory
     * @param where           telemetry context (e.g. {@code "executor"})
     */
    public ErrorReportingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAlive, TimeUnit unit,
                                            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                                            String where) {
        super(corePoolSize, maximumPoolSize, keepAlive, unit, workQueue, threadFactory);
        this.where = where;
    }

    /** Drop-in replacement for {@code Executors.newFixedThreadPool(n, factory)}. */
    public static ErrorReportingThreadPoolExecutor fixed(int threads, ThreadFactory factory, String where) {
        return new ErrorReportingThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), factory, where);
    }

    /**
     * Drop-in replacement for {@code Executors.newScheduledThreadPool(n, factory)} /
     * {@code newSingleThreadScheduledExecutor(factory)}. A scheduled pool wraps every task — also
     * one given to {@code execute()} — in a {@code ScheduledFutureTask}, which swallows the
     * exception and, for a periodic task, silently ends its schedule. This executor reports and
     * logs such failures.
     */
    public static ScheduledThreadPoolExecutor scheduled(int threads, ThreadFactory factory, String where) {
        return new Scheduled(threads, factory, where);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        reportFailedTask(r, t, where);
    }

    /**
     * Reports the failure of a finished task: a plain {@code execute()}d Runnable that threw
     * ({@code t != null}) terminates the worker thread and reaches the default
     * uncaught-exception handler, which reports it — not counted here. A {@code submit()}ted
     * task keeps its exception in the {@link Future}; it is unwrapped from the completed task
     * (a later {@code get()} still throws the same {@link ExecutionException}).
     */
    static void reportFailedTask(Runnable r, Throwable t, String where) {
        if (t != null) {
            return;
        }
        Throwable failure = null;
        if (r instanceof Future<?> future && future.isDone() && !future.isCancelled()) {
            try {
                future.get();
            } catch (CancellationException ignored) {
                // cancelled — not an error
            } catch (ExecutionException e) {
                failure = e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure != null && !(failure instanceof InterruptedException)) {
            Telemetry.reportError(failure, where);
            logger.warn("Unhandled exception in background task ({})", where, failure);
        }
    }

    /** The scheduled variant; see {@link #scheduled(int, ThreadFactory, String)}. */
    static final class Scheduled extends ScheduledThreadPoolExecutor {
        private final String where;

        Scheduled(int threads, ThreadFactory factory, String where) {
            super(threads, factory);
            this.where = where;
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            reportFailedTask(r, t, where);
        }
    }
}
