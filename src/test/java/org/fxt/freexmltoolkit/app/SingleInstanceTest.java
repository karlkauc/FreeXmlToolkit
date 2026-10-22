package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceTest {

    @TempDir
    Path dir;

    @Test
    void secondClaimFailsWhileTheFirstHoldsTheLock() {
        try (SingleInstance primary = SingleInstance.claim(dir, files -> { })) {
            assertNotNull(primary);
            assertNull(SingleInstance.claim(dir, files -> { }));
        }
        // released on close
        SingleInstance again = SingleInstance.claim(dir, files -> { });
        assertNotNull(again);
        again.close();
    }

    @Test
    void forwardedFilesReachThePrimaryAndActivateIt() throws Exception {
        List<Path> received = new CopyOnWriteArrayList<>();
        CountDownLatch activated = new CountDownLatch(1);
        try (SingleInstance primary = SingleInstance.claim(dir, received::addAll)) {
            assertNotNull(primary);
            primary.setActivationHandler(activated::countDown);
            Path file = Files.writeString(dir.resolve("a.xml"), "<root/>");

            assertTrue(SingleInstance.forward(dir, List.of(file)));

            assertTrue(activated.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(file.toAbsolutePath()), received);
        }
    }

    @Test
    void forwardWithoutFilesOnlyActivates() throws Exception {
        List<Path> received = new CopyOnWriteArrayList<>();
        CountDownLatch activated = new CountDownLatch(1);
        try (SingleInstance primary = SingleInstance.claim(dir, received::addAll)) {
            primary.setActivationHandler(activated::countDown);

            assertTrue(SingleInstance.forward(dir, List.of()));

            assertTrue(activated.await(5, TimeUnit.SECONDS));
            assertTrue(received.isEmpty());
        }
    }

    @Test
    void wrongTokenIsRejected() throws IOException {
        List<Path> received = new CopyOnWriteArrayList<>();
        try (SingleInstance primary = SingleInstance.claim(dir, received::addAll)) {
            Path portFile = dir.resolve(SingleInstance.PORT_FILE);
            String port = Files.readAllLines(portFile, StandardCharsets.UTF_8).get(0);
            Files.writeString(portFile, port + "\nnot-the-token\n");

            assertFalse(SingleInstance.forward(dir, List.of(dir.resolve("x.xml"))));
            assertTrue(received.isEmpty());
        }
    }

    @Test
    void forwardFailsWithoutARunningInstance() throws IOException {
        assertFalse(SingleInstance.forward(dir, List.of()));

        // stale port file of a crashed instance: nobody listens there
        Files.writeString(dir.resolve(SingleInstance.PORT_FILE), "1\ntoken\n");
        assertFalse(SingleInstance.forward(dir, List.of()));
    }

    @Test
    void closeRemovesThePortFile() {
        SingleInstance primary = SingleInstance.claim(dir, files -> { });
        assertTrue(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));
        primary.close();
        assertFalse(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));
    }

    @Test
    void fileArgumentsKeepsOnlyExistingFilesAsAbsolutePaths() throws IOException {
        Path file = Files.writeString(dir.resolve("b.xml"), "<root/>");

        List<Path> files = SingleInstance.fileArguments(
                List.of(file.toString(), "--some-flag", dir.resolve("missing.xml").toString(), dir.toString()));

        assertEquals(List.of(file.toAbsolutePath()), files);
    }

    @Test
    void startForwardsToTheRunningInstance() throws Exception {
        List<Path> received = new CopyOnWriteArrayList<>();
        CountDownLatch activated = new CountDownLatch(1);
        try (SingleInstance primary = SingleInstance.claim(dir, received::addAll)) {
            primary.setActivationHandler(activated::countDown);
            Path file = Files.writeString(dir.resolve("c.xml"), "<root/>");

            SingleInstance.Startup startup = SingleInstance.start(dir, List.of(file.toString()), files -> { });

            assertTrue(startup.forwarded());
            assertNull(startup.primary());
            assertTrue(activated.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(file.toAbsolutePath()), received);
        }
    }

    @Test
    void startBecomesPrimaryWhenNoneIsRunning() {
        SingleInstance.Startup startup = SingleInstance.start(dir, List.of(), files -> { });
        assertFalse(startup.forwarded());
        assertNotNull(startup.primary());
        startup.primary().close();
    }
}
