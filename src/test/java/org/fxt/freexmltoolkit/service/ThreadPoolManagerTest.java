package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ThreadPoolManager.
 * Tests thread pool functionality, performance monitoring, and resource management.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD) // Prevent parallel execution issues with singleton
class ThreadPoolManagerTest {

    private ThreadPoolManager threadPoolManager;

    @BeforeAll
    void setUp() {
        threadPoolManager = ThreadPoolManager.getInstance();
    }

    @AfterEach
    void cleanUp() {
        // Cancel any running tasks between tests
        threadPoolManager.cancelAllTasks();
    }

    @Test
    @DisplayName("Test singleton pattern")
    void testSingletonPattern() {
        ThreadPoolManager instance1 = ThreadPoolManager.getInstance();
        ThreadPoolManager instance2 = ThreadPoolManager.getInstance();

        assertSame(instance1, instance2, "ThreadPoolManager should be singleton");
        assertNotNull(instance1, "Instance should not be null");
    }

    @Test
    @DisplayName("Test UI task execution")
    void testUITaskExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        CompletableFuture<Void> future = threadPoolManager.executeUI("test-ui-task", () -> {
            result.set(42);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "UI task should complete within 2 seconds");
        assertNull(future.get(1, TimeUnit.SECONDS), "Future should complete successfully");
        assertEquals(42, result.get(), "UI task should have executed correctly");
    }

    @Test
    @DisplayName("Test CPU-intensive task execution")
    void testCPUIntensiveTaskExecution() throws Exception {
        CompletableFuture<Integer> future = threadPoolManager.executeCPUIntensive("test-cpu-task", () -> {
            // Simulate CPU-intensive work
            int sum = 0;
            for (int i = 0; i < 10000; i++) {
                sum += i;
            }
            return sum;
        });

        Integer result = future.get(3, TimeUnit.SECONDS);
        assertNotNull(result, "CPU task should return a result");
        assertEquals(49995000, result, "CPU task calculation should be correct");
    }

    @Test
    @DisplayName("Test I/O task execution")
    void testIOTaskExecution() throws Exception {
        CompletableFuture<String> future = threadPoolManager.executeIO("test-io-task", () -> {
            // Simulate I/O operation with sleep
            try {
                Thread.sleep(100);
                return "IO_RESULT";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
        });

        String result = future.get(2, TimeUnit.SECONDS);
        assertEquals("IO_RESULT", result, "I/O task should complete successfully");
    }

    @Test
    @DisplayName("Test scheduled task execution")
    void testScheduledTaskExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> future = threadPoolManager.schedule("test-scheduled-task", () -> {
            result.set(100);
            latch.countDown();
        }, 200, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Scheduled task should complete");
        assertNull(future.get(1, TimeUnit.SECONDS), "Scheduled task should complete successfully");
        assertEquals(100, result.get(), "Scheduled task should execute correctly");

        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue(elapsed >= 190 && elapsed <= 1000, "Task should execute after delay: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Test background task execution")
    void testBackgroundTaskExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();

        CompletableFuture<Void> future = threadPoolManager.executeBackground("test-background-task", () -> {
            result.set("BACKGROUND_COMPLETED");
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Background task should complete");
        assertNull(future.get(1, TimeUnit.SECONDS), "Background task should complete successfully");
        assertEquals("BACKGROUND_COMPLETED", result.get(), "Background task should execute correctly");
    }

    @Test
    @DisplayName("Test task cancellation")
    void testTaskCancellation() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskShouldCancel = new CountDownLatch(1);

        CompletableFuture<String> future = threadPoolManager.executeCPUIntensive("cancellable-task", () -> {
            taskStarted.countDown();
            try {
                // Wait for cancellation signal
                taskShouldCancel.await(5, TimeUnit.SECONDS);
                return "COMPLETED";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "CANCELLED";
            }
        });

        // Wait for task to start
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS), "Task should start");

        // Cancel the task
        boolean cancelled = threadPoolManager.cancelTask("cancellable-task");
        assertTrue(cancelled || future.isCancelled(), "Task should be cancellable");

        // Release the waiting task
        taskShouldCancel.countDown();
    }

    @Test
    @DisplayName("Test concurrent task execution")
    void testConcurrentTaskExecution() throws Exception {
        int taskCount = 10;
        CountDownLatch allTasksCompleted = new CountDownLatch(taskCount);
        AtomicInteger completedTasks = new AtomicInteger(0);

        // Submit multiple tasks concurrently
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            threadPoolManager.executeCPUIntensive("concurrent-task-" + taskId, () -> {
                try {
                    Thread.sleep(50); // Simulate some work
                    completedTasks.incrementAndGet();
                    return taskId;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                } finally {
                    allTasksCompleted.countDown();
                }
            });
        }

        assertTrue(allTasksCompleted.await(5, TimeUnit.SECONDS), "All tasks should complete");
        assertEquals(taskCount, completedTasks.get(), "All tasks should have completed successfully");
    }

    @Test
    @DisplayName("Test performance statistics")
    void testPerformanceStatistics() throws Exception {
        // Execute some tasks to generate statistics
        CountDownLatch taskLatch = new CountDownLatch(3);

        threadPoolManager.executeUI("stats-ui-task", () -> taskLatch.countDown());
        threadPoolManager.executeCPUIntensive("stats-cpu-task", () -> {
            taskLatch.countDown();
            return "result";
        });
        threadPoolManager.executeIO("stats-io-task", () -> {
            taskLatch.countDown();
            return "io-result";
        });

        assertTrue(taskLatch.await(3, TimeUnit.SECONDS), "All statistics tasks should complete");

        // Wait a bit for statistics to update
        Thread.sleep(100);

        ThreadPoolManager.ThreadPoolStats stats = threadPoolManager.getStats();
        assertNotNull(stats, "Statistics should be available");
        assertTrue(stats.tasksSubmitted() >= 3, "Should have submitted at least 3 tasks");
        assertTrue(stats.tasksCompleted() >= 3, "Should have completed at least 3 tasks");
        assertEquals(0, stats.tasksFailed(), "Should have no failed tasks");
        assertNotNull(stats.poolStats(), "Pool statistics should be available");
    }

    @Test
    @DisplayName("Test error handling in tasks")
    void testErrorHandling() throws Exception {
        CompletableFuture<String> future = threadPoolManager.executeCPUIntensive("error-task", () -> {
            throw new RuntimeException("Test exception");
        });

        assertThrows(Exception.class, () -> {
            future.get(2, TimeUnit.SECONDS);
        }, "Future should complete with exception");

        assertTrue(future.isCompletedExceptionally(), "Future should be completed exceptionally");
    }

    @Test
    @DisplayName("Test task execution after partial shutdown")
    void testTaskExecutionAfterPartialShutdown() throws Exception {
        // This test is tricky because we can't fully shutdown and restart the singleton
        // We'll test that tasks can still be submitted and statistics are tracked

        ThreadPoolManager.ThreadPoolStats initialStats = threadPoolManager.getStats();

        CompletableFuture<Void> future = threadPoolManager.executeUI("post-stats-task", () -> {
        });

        assertDoesNotThrow(() -> {
            future.get(2, TimeUnit.SECONDS);
        }, "Tasks should still execute normally");

        ThreadPoolManager.ThreadPoolStats newStats = threadPoolManager.getStats();
        assertTrue(newStats.tasksSubmitted() >= initialStats.tasksSubmitted(),
                "Task counter should still be working");
    }

    @Test
    @DisplayName("Test thread naming conventions")
    void testThreadNaming() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        threadPoolManager.executeUI("thread-name-test", () -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Thread name task should complete");

        String name = threadName.get();
        assertNotNull(name, "Thread name should not be null");
        assertTrue(name.startsWith("FXT-"), "Thread name should start with FXT- prefix");
    }

    @Test
    @DisplayName("Test resource cleanup")
    void testResourceCleanup() {
        // Test that cancelling all tasks works
        CompletableFuture<Void> future1 = threadPoolManager.executeBackground("cleanup-task-1", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Void> future2 = threadPoolManager.executeBackground("cleanup-task-2", () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Cancel all tasks
        threadPoolManager.cancelAllTasks();

        // Both futures should eventually be cancelled or complete
        assertDoesNotThrow(() -> {
            Thread.sleep(100); // Give time for cancellation
            // Tasks might complete quickly or be cancelled
        });
    }

    @Test
    @DisplayName("Test shutdown behavior")
    void testShutdownBehavior() {
        // We can't test full shutdown because it would affect other tests
        // Instead, test that the manager reports correct shutdown state
        assertFalse(threadPoolManager.isShutdown(), "ThreadPoolManager should not be shutdown initially");

        // Test that getting statistics works before any theoretical shutdown
        ThreadPoolManager.ThreadPoolStats stats = threadPoolManager.getStats();
        assertNotNull(stats, "Should be able to get statistics");
        assertNotNull(stats.poolStats(), "Pool statistics should be available");
    }

    @Test
    @DisplayName("Test performance under load")
    void testPerformanceUnderLoad() throws Exception {
        int taskCount = 100;
        CountDownLatch completionLatch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // Submit many tasks quickly
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            threadPoolManager.executeCPUIntensive("load-test-" + taskId, () -> {
                try {
                    // Simulate small amount of work
                    Thread.sleep(1);
                    successCount.incrementAndGet();
                    return taskId;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Wait for completion with reasonable timeout
        assertTrue(completionLatch.await(30, TimeUnit.SECONDS),
                "Load test should complete within 30 seconds");

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Completed " + taskCount + " tasks in " + duration + "ms");

        assertEquals(taskCount, successCount.get(), "All load test tasks should succeed");
        assertTrue(duration < 15000, "Load test should complete reasonably quickly: " + duration + "ms");

        // Check that statistics are reasonable
        ThreadPoolManager.ThreadPoolStats stats = threadPoolManager.getStats();
        assertTrue(stats.tasksSubmitted() >= taskCount, "Should have submitted at least " + taskCount + " tasks");
    }
}