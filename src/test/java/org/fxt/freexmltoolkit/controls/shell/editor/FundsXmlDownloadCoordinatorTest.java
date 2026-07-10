/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.GitHubReleaseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for {@link FundsXmlDownloadCoordinator} — runs without a JavaFX
 * toolkit (the coordinator's FX hop falls back to direct execution), using a
 * manual executor so the "in flight" state is observable deterministically.
 */
@DisplayName("FundsXmlDownloadCoordinator Tests")
class FundsXmlDownloadCoordinatorTest {

    @TempDir
    Path tempDir;

    private StubService service;
    private ManualExecutor executor;
    private FundsXmlDownloadCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        FundsXmlCache cache = ctor.newInstance(tempDir.resolve("fundsxml"));
        service = new StubService(cache);
        executor = new ManualExecutor();
        coordinator = new FundsXmlDownloadCoordinator(service, executor);
    }

    @Test
    @DisplayName("second start while running is rejected; state resets after the run")
    void doubleStartRejected() {
        assertTrue(coordinator.startBackgroundDownload("test"));
        assertTrue(coordinator.isRunning());
        assertFalse(coordinator.startBackgroundDownload("test-again"),
                "a second start while running must be rejected");
        assertEquals(1, executor.tasks.size(), "the rejected start must not submit work");

        executor.runAll();

        assertFalse(coordinator.isRunning());
        assertTrue(coordinator.startBackgroundDownload("after"),
                "a new run must be possible once the previous one finished");
    }

    @Test
    @DisplayName("listeners observe started and finished with the service result")
    void listenerReceivesLifecycle() {
        RecordingListener listener = new RecordingListener();
        coordinator.addListener(listener);

        coordinator.startBackgroundDownload("test");
        executor.runAll();

        assertEquals(1, listener.started.get());
        assertNotNull(listener.lastResult);
        assertFalse(listener.lastResult.isSuccess(), "stub download yields an error result");
    }

    @Test
    @DisplayName("state resets and listeners fire even when the service throws")
    void serviceExceptionHandled() {
        service.throwOnDownload = true;
        RecordingListener listener = new RecordingListener();
        coordinator.addListener(listener);

        coordinator.startBackgroundDownload("test");
        executor.runAll();

        assertFalse(coordinator.isRunning());
        assertNotNull(listener.lastResult);
        assertFalse(listener.lastResult.isSuccess());
        assertTrue(listener.lastResult.error().contains("boom"), listener.lastResult.error());
    }

    @Test
    @DisplayName("runRegisterOnly delegates to reRegisterFromCache off-thread")
    void registerOnlyDelegates() {
        coordinator.runRegisterOnly();
        assertEquals(0, service.reRegisterCalls, "work must run on the executor, not inline");

        executor.runAll();

        assertEquals(1, service.reRegisterCalls);
    }

    @Test
    @DisplayName("removed listeners no longer receive events")
    void removedListenerSilent() {
        RecordingListener listener = new RecordingListener();
        coordinator.addListener(listener);
        coordinator.removeListener(listener);

        coordinator.startBackgroundDownload("test");
        executor.runAll();

        assertEquals(0, listener.started.get());
    }

    // -----------------------------------------------------------------
    // Doubles
    // -----------------------------------------------------------------

    private static class RecordingListener implements FundsXmlDownloadCoordinator.Listener {
        final AtomicInteger started = new AtomicInteger();
        volatile FundsXmlExtensionService.DownloadResult lastResult;

        @Override
        public void onStarted() {
            started.incrementAndGet();
        }

        @Override
        public void onFinished(FundsXmlExtensionService.DownloadResult result) {
            lastResult = result;
        }
    }

    /** Collects submitted tasks; the test runs them explicitly. */
    private static class ManualExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    private static class StubService extends FundsXmlExtensionService {
        boolean throwOnDownload;
        int reRegisterCalls;

        StubService(FundsXmlCache cache) {
            super(cache, new GitHubReleaseClient(uri -> {
                throw new java.io.IOException("Network disabled in tests");
            }));
        }

        @Override
        public DownloadResult downloadOrUpdate(
                org.fxt.freexmltoolkit.service.fundsxml.DownloadProgressCallback callback) {
            if (throwOnDownload) {
                throw new IllegalStateException("boom");
            }
            return DownloadResult.builder().error("no network in tests").build();
        }

        @Override
        public void reRegisterFromCache() {
            reRegisterCalls++;
        }
    }
}
