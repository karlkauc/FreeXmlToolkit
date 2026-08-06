package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlDownloadCoordinator;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlCache;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlExtensionService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlStartupSync;
import org.fxt.freexmltoolkit.service.fundsxml.GitHubReleaseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellBootstrapTest {

    @Test
    void scheduleAndShutdownAreSafe() {
        ShellBootstrap boot = ShellBootstrap.getInstance();
        // Scheduling startup tasks must not throw (the live network check runs on a background
        // thread and is independent of this call returning).
        assertDoesNotThrow(boot::scheduleStartupTasks);
        // Shutdown must be idempotent and not throw.
        assertDoesNotThrow(boot::shutdown);
        assertDoesNotThrow(boot::shutdown);
    }

    @Test
    void startupSyncDispatchMapsActionsToCoordinatorWork(@TempDir Path tempDir) throws Exception {
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        FundsXmlCache cache = ctor.newInstance(tempDir.resolve("fundsxml"));
        RecordingService service = new RecordingService(cache);
        List<Runnable> tasks = new ArrayList<>();
        FundsXmlDownloadCoordinator coordinator =
                new FundsXmlDownloadCoordinator(service, tasks::add);

        ShellBootstrap.dispatchFundsXmlAction(FundsXmlStartupSync.Action.NONE, coordinator);
        assertEquals(1, tasks.size(), "NONE must still schedule the stale-entry cleanup");
        tasks.remove(0).run();
        assertEquals(1, service.cleanupCalls);
        assertEquals(0, service.reRegisterCalls);
        assertEquals(0, service.downloadCalls);

        ShellBootstrap.dispatchFundsXmlAction(FundsXmlStartupSync.Action.REGISTER_ONLY, coordinator);
        assertEquals(1, tasks.size());
        tasks.remove(0).run();
        assertEquals(1, service.reRegisterCalls);
        assertEquals(0, service.downloadCalls);

        ShellBootstrap.dispatchFundsXmlAction(FundsXmlStartupSync.Action.DOWNLOAD_INITIAL, coordinator);
        assertEquals(1, tasks.size());
        tasks.remove(0).run();
        assertEquals(1, service.downloadCalls);

        ShellBootstrap.dispatchFundsXmlAction(FundsXmlStartupSync.Action.DOWNLOAD_UPDATE, coordinator);
        assertEquals(1, tasks.size());
        tasks.remove(0).run();
        assertEquals(2, service.downloadCalls);
    }

    private static class RecordingService extends FundsXmlExtensionService {
        int downloadCalls;
        int reRegisterCalls;
        int cleanupCalls;

        RecordingService(FundsXmlCache cache) {
            super(cache, new GitHubReleaseClient(uri -> {
                throw new java.io.IOException("Network disabled in tests");
            }));
        }

        @Override
        public DownloadResult downloadOrUpdate(
                org.fxt.freexmltoolkit.service.fundsxml.DownloadProgressCallback callback) {
            downloadCalls++;
            return DownloadResult.builder().error("no network in tests").build();
        }

        @Override
        public void reRegisterFromCache() {
            reRegisterCalls++;
        }

        @Override
        public void cleanupStaleEntries() {
            cleanupCalls++;
        }
    }
}
