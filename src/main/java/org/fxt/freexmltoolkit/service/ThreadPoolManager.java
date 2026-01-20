package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Centralized thread pool management for the FreeXmlToolkit application.
 * <p>
 * This singleton provides optimized thread pools for different types of operations:
 * - UI-bound operations (short-running, high priority)
 * - CPU-intensive operations (medium-running, parallel processing)
 * - I/O-bound operations (long-running, many concurrent tasks)
 * - Background operations (low priority, cleanup tasks)
 * <p>
 * Features:
 * - Automatic pool sizing based on available CPU cores
 * - Performance monitoring and statistics
 * - Graceful shutdown handling
 * - Task cancellation support
 * - Memory-efficient with daemon threads
 */
public class ThreadPoolManager {

    private static final Logger logger = LogManager.getLogger(ThreadPoolManager.class);
    private static volatile ThreadPoolManager instance;
    private static final Object lock = new Object();

    // Thread pool configurations
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final int UI_POOL_SIZE = Math.max(2, CPU_CORES / 2);
    private static final int CPU_POOL_SIZE = CPU_CORES;
    private static final int IO_POOL_SIZE = Math.max(4, CPU_CORES * 2);
    private static final int BACKGROUND_POOL_SIZE = 2;

    // Thread pools for different operation types
    private final ExecutorService uiExecutor;           // Fast UI operations
    private final ForkJoinPool cpuIntensiveExecutor;    // CPU-bound tasks like syntax highlighting
    private final ExecutorService ioExecutor;          // I/O operations like file loading
    private final ScheduledExecutorService scheduledExecutor;  // Scheduled/delayed tasks
    private final ExecutorService backgroundExecutor;   // Low-priority background tasks

    // Performance monitoring
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    // Task tracking for cancellation
    private final ConcurrentHashMap<String, CompletableFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private volatile boolean isShutdown = false;

    /**
     * Private constructor for singleton pattern.
     */
    private ThreadPoolManager() {
        logger.info("Initializing ThreadPoolManager with {} CPU cores", CPU_CORES);

        // UI Executor - High priority, fast completion
        this.uiExecutor = Executors.newFixedThreadPool(UI_POOL_SIZE, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FXT-UI-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY - 1);
                return t;
            }
        });

        // CPU Intensive Executor - Work-stealing pool for parallel processing
        this.cpuIntensiveExecutor = new ForkJoinPool(
                CPU_POOL_SIZE,
                pool -> {
                    ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    worker.setName("FXT-CPU-" + worker.getPoolIndex());
                    worker.setDaemon(true);
                    return worker;
                },
                null,
                false
        );

        // I/O Executor - Many threads for concurrent I/O operations
        this.ioExecutor = Executors.newFixedThreadPool(IO_POOL_SIZE, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FXT-IO-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        });

        // Scheduled Executor - For timed/delayed operations
        this.scheduledExecutor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FXT-Scheduled-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        });

        // Background Executor - Low priority cleanup and maintenance
        this.backgroundExecutor = Executors.newFixedThreadPool(BACKGROUND_POOL_SIZE, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FXT-Background-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY + 1);
                return t;
            }
        });

        // Start performance monitoring
        startPerformanceMonitoring();

        logger.info("ThreadPoolManager initialized - UI: {}, CPU: {}, I/O: {}, Background: {}",
                UI_POOL_SIZE, CPU_POOL_SIZE, IO_POOL_SIZE, BACKGROUND_POOL_SIZE);
    }

    /**
     * Get singleton instance with double-checked locking.
     */
    public static ThreadPoolManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ThreadPoolManager();
                }
            }
        }
        return instance;
    }

    /**
     * Execute UI-bound task with high priority.
     * For fast operations that update the UI.
     */
    public CompletableFuture<Void> executeUI(Runnable task) {
        return executeUI("unnamed-ui-task", task);
    }

    public CompletableFuture<Void> executeUI(String taskId, Runnable task) {
        if (isShutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("ThreadPoolManager is shutdown"));
        }

        totalTasksSubmitted.incrementAndGet();
        Instant startTime = Instant.now();

        CompletableFuture<Void> future = CompletableFuture
                .runAsync(task, uiExecutor)
                .whenComplete((result, throwable) -> {
                    runningTasks.remove(taskId);
                    totalTasksCompleted.incrementAndGet();

                    if (throwable != null) {
                        totalTasksFailed.incrementAndGet();
                        logger.debug("UI task '{}' failed", taskId, throwable);
                    } else {
                        long executionTime = Duration.between(startTime, Instant.now()).toMillis();
                        totalExecutionTime.addAndGet(executionTime);

                        if (executionTime > 100) {
                            logger.debug("UI task '{}' took {}ms (slow)", taskId, executionTime);
                        }
                    }
                });

        runningTasks.put(taskId, future);
        return future;
    }

    /**
     * Execute CPU-intensive task with work-stealing pool.
     * For operations like syntax highlighting, parsing, validation.
     */
    public <T> CompletableFuture<T> executeCPUIntensive(Supplier<T> task) {
        return executeCPUIntensive("unnamed-cpu-task", task);
    }

    public <T> CompletableFuture<T> executeCPUIntensive(String taskId, Supplier<T> task) {
        if (isShutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("ThreadPoolManager is shutdown"));
        }

        totalTasksSubmitted.incrementAndGet();
        Instant startTime = Instant.now();

        CompletableFuture<T> future = CompletableFuture
                .supplyAsync(task, cpuIntensiveExecutor)
                .whenComplete((result, throwable) -> {
                    runningTasks.remove(taskId);
                    totalTasksCompleted.incrementAndGet();

                    if (throwable != null) {
                        totalTasksFailed.incrementAndGet();
                        logger.debug("CPU task '{}' failed", taskId, throwable);
                    } else {
                        long executionTime = Duration.between(startTime, Instant.now()).toMillis();
                        totalExecutionTime.addAndGet(executionTime);

                        if (executionTime > 500) {
                            logger.debug("CPU task '{}' took {}ms (slow)", taskId, executionTime);
                        }
                    }
                });

        runningTasks.put(taskId, future);
        return future;
    }

    /**
     * Execute I/O bound task.
     * For file operations, network requests, database queries.
     */
    public <T> CompletableFuture<T> executeIO(Supplier<T> task) {
        return executeIO("unnamed-io-task", task);
    }

    public <T> CompletableFuture<T> executeIO(String taskId, Supplier<T> task) {
        if (isShutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("ThreadPoolManager is shutdown"));
        }

        totalTasksSubmitted.incrementAndGet();
        Instant startTime = Instant.now();

        CompletableFuture<T> future = CompletableFuture
                .supplyAsync(task, ioExecutor)
                .whenComplete((result, throwable) -> {
                    runningTasks.remove(taskId);
                    totalTasksCompleted.incrementAndGet();

                    if (throwable != null) {
                        totalTasksFailed.incrementAndGet();
                        logger.debug("I/O task '{}' failed", taskId, throwable);
                    } else {
                        long executionTime = Duration.between(startTime, Instant.now()).toMillis();
                        totalExecutionTime.addAndGet(executionTime);
                    }
                });

        runningTasks.put(taskId, future);
        return future;
    }

    /**
     * Schedule task for delayed execution.
     */
    public CompletableFuture<Void> schedule(Runnable task, long delay, TimeUnit timeUnit) {
        return schedule("unnamed-scheduled-task", task, delay, timeUnit);
    }

    public CompletableFuture<Void> schedule(String taskId, Runnable task, long delay, TimeUnit timeUnit) {
        if (isShutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("ThreadPoolManager is shutdown"));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        scheduledExecutor.schedule(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                runningTasks.remove(taskId);
            }
        }, delay, timeUnit);

        runningTasks.put(taskId, future);
        return future;
    }

    /**
     * Execute background task with low priority.
     */
    public CompletableFuture<Void> executeBackground(Runnable task) {
        return executeBackground("unnamed-background-task", task);
    }

    public CompletableFuture<Void> executeBackground(String taskId, Runnable task) {
        if (isShutdown) {
            return CompletableFuture.failedFuture(new IllegalStateException("ThreadPoolManager is shutdown"));
        }

        CompletableFuture<Void> future = CompletableFuture
                .runAsync(task, backgroundExecutor)
                .whenComplete((result, throwable) -> {
                    runningTasks.remove(taskId);
                    if (throwable != null) {
                        logger.debug("Background task '{}' failed", taskId, throwable);
                    }
                });

        runningTasks.put(taskId, future);
        return future;
    }

    /**
     * Cancel a specific task by ID.
     */
    public boolean cancelTask(String taskId) {
        CompletableFuture<?> task = runningTasks.remove(taskId);
        if (task != null) {
            boolean cancelled = task.cancel(true);
            logger.debug("Task '{}' cancellation: {}", taskId, cancelled);
            return cancelled;
        }
        return false;
    }

    /**
     * Cancel all running tasks.
     */
    public void cancelAllTasks() {
        logger.info("Cancelling {} running tasks", runningTasks.size());
        runningTasks.values().forEach(future -> future.cancel(true));
        runningTasks.clear();
    }

    /**
     * Get performance statistics.
     */
    public ThreadPoolStats getStats() {
        return new ThreadPoolStats(
                totalTasksSubmitted.get(),
                totalTasksCompleted.get(),
                totalTasksFailed.get(),
                runningTasks.size(),
                totalTasksCompleted.get() > 0 ? totalExecutionTime.get() / totalTasksCompleted.get() : 0,
                getPoolStats()
        );
    }

    private PoolStats getPoolStats() {
        return new PoolStats(
                getActiveThreadCount(uiExecutor),
                cpuIntensiveExecutor.getActiveThreadCount(),
                getActiveThreadCount(ioExecutor),
                getActiveThreadCount(backgroundExecutor),
                cpuIntensiveExecutor.getQueuedTaskCount()
        );
    }

    private int getActiveThreadCount(ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return -1; // Unknown
    }

    /**
     * Start performance monitoring task.
     */
    private void startPerformanceMonitoring() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            if (logger.isDebugEnabled()) {
                ThreadPoolStats stats = getStats();
                logger.debug("Thread pool stats: Submitted={}, Completed={}, Failed={}, Running={}, AvgTime={}ms",
                        stats.tasksSubmitted, stats.tasksCompleted, stats.tasksFailed,
                        stats.runningTasks, stats.averageExecutionTimeMs);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Shutdown all thread pools gracefully.
     */
    public void shutdown() {
        if (isShutdown) {
            return;
        }

        logger.info("Shutting down ThreadPoolManager...");
        isShutdown = true;

        // Cancel all running tasks
        cancelAllTasks();

        // Shutdown pools
        shutdownExecutor("UI", uiExecutor, 1000);
        shutdownExecutor("CPU", cpuIntensiveExecutor, 2000);
        shutdownExecutor("I/O", ioExecutor, 1000);
        shutdownExecutor("Background", backgroundExecutor, 500);
        shutdownExecutor("Scheduled", scheduledExecutor, 500);

        logger.info("ThreadPoolManager shutdown completed");
    }

    private void shutdownExecutor(String name, ExecutorService executor, long timeoutMs) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                logger.warn("{} executor did not terminate within {}ms, forcing shutdown", name, timeoutMs);
                executor.shutdownNow();
                if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    logger.error("{} executor did not terminate after forced shutdown", name);
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted for {} executor", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if the thread pool manager is shutdown.
     */
    public boolean isShutdown() {
        return isShutdown;
    }

    /**
     * Performance statistics record.
     * @param tasksSubmitted Total tasks submitted
     * @param tasksCompleted Total tasks completed
     * @param tasksFailed Total tasks failed
     * @param runningTasks Currently running tasks
     * @param averageExecutionTimeMs Average execution time in milliseconds
     * @param poolStats Statistics for individual pools
     */
    public record ThreadPoolStats(
            long tasksSubmitted,
            long tasksCompleted,
            long tasksFailed,
            int runningTasks,
            long averageExecutionTimeMs,
            PoolStats poolStats
    ) {
    }

    /**
     * Individual pool statistics.
     * @param uiActiveThreads Active threads in UI pool
     * @param cpuActiveThreads Active threads in CPU pool
     * @param ioActiveThreads Active threads in I/O pool
     * @param backgroundActiveThreads Active threads in Background pool
     * @param cpuQueuedTasks Tasks queued in CPU pool
     */
    public record PoolStats(
            int uiActiveThreads,
            int cpuActiveThreads,
            int ioActiveThreads,
            int backgroundActiveThreads,
            long cpuQueuedTasks
    ) {
    }
}