package org.fxt.freexmltoolkit.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFileOpenerTest {

    @TempDir
    Path tempDir;

    private final List<Path> opened = new ArrayList<>();

    @BeforeEach
    void setUp() {
        StartupFileOpener.reset();
        // deliver synchronously instead of Platform.runLater (no FX toolkit in unit tests)
        StartupFileOpener.uiDispatcher = Runnable::run;
    }

    @AfterEach
    void tearDown() {
        StartupFileOpener.reset();
    }

    private Path createFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "<root/>");
        return file;
    }

    @Test
    void filesEnqueuedBeforeConsumerAreDeliveredOnRegistration() throws IOException {
        Path file = createFile("a.xml");
        StartupFileOpener.enqueue(List.of(file));
        assertTrue(opened.isEmpty());

        StartupFileOpener.setConsumer(opened::add);
        assertEquals(List.of(file.toAbsolutePath()), opened);
    }

    @Test
    void filesEnqueuedAfterConsumerAreDeliveredImmediately() throws IOException {
        StartupFileOpener.setConsumer(opened::add);
        Path file = createFile("b.xml");
        StartupFileOpener.enqueue(List.of(file));
        assertEquals(List.of(file.toAbsolutePath()), opened);
    }

    @Test
    void nonExistingPathsAreFiltered() throws IOException {
        StartupFileOpener.setConsumer(opened::add);
        Path file = createFile("c.xml");
        StartupFileOpener.enqueue(List.of(tempDir.resolve("missing.xml"), file, tempDir));
        assertEquals(List.of(file.toAbsolutePath()), opened);
    }

    @Test
    void rawArgsIgnoreUnparseableAndNonFileArguments() throws IOException {
        StartupFileOpener.setConsumer(opened::add);
        Path file = createFile("d.xml");
        StartupFileOpener.enqueueRawArgs(List.of("--enable-preview", "\0bad", file.toString()));
        assertEquals(List.of(file.toAbsolutePath()), opened);
    }

    @Test
    void queueIsDrainedOnlyOnce() throws IOException {
        Path file = createFile("e.xml");
        StartupFileOpener.enqueue(List.of(file));
        StartupFileOpener.setConsumer(opened::add);
        StartupFileOpener.setConsumer(opened::add);
        assertEquals(1, opened.size());
    }
}
