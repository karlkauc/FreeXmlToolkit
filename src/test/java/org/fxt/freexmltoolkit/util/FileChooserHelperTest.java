package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FileChooserHelper}'s core logic (initial-directory
 * pre-selection and last-directory persistence) without showing a real dialog.
 */
class FileChooserHelperTest {

    private PropertiesService props;

    @BeforeEach
    void setUp() {
        props = mock(PropertiesService.class);
        ServiceRegistry.register(PropertiesService.class, props);
        FileChooserHelper.setDirectoryProbe(null, null);
    }

    @AfterEach
    void tearDown() {
        FileChooserHelper.setDirectoryProbe(null, null);
        ServiceRegistry.reset();
    }

    /** A directory check that hangs until released, as a cloud folder or network share that does not answer. */
    private static Predicate<File> hangingProbe(CountDownLatch release) {
        return f -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return f.isDirectory();
        };
    }

    @Test
    void aSlowRememberedDirectoryDoesNotHoldUpTheDialog(@TempDir Path dir) {
        when(props.getLastOpenDirectory()).thenReturn(dir.toString());
        CountDownLatch release = new CountDownLatch(1);
        FileChooserHelper.setDirectoryProbe(hangingProbe(release), Duration.ofMillis(100));
        try {
            FileChooser chooser = new FileChooser();
            long t0 = System.nanoTime();
            FileChooserHelper.applyInitialDir(chooser);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            assertTrue(ms < 2000, "the check must give up after its time limit, took " + ms + " ms");
            assertNull(chooser.getInitialDirectory(), "an unanswered directory is not preset");
        } finally {
            release.countDown();
        }
    }

    @Test
    void aSlowDirectoryFoundToExistIsUsedNextTime(@TempDir Path dir) throws Exception {
        when(props.getLastOpenDirectory()).thenReturn(dir.toString());
        CountDownLatch first = new CountDownLatch(1);
        FileChooserHelper.setDirectoryProbe(hangingProbe(first), Duration.ofMillis(100));
        assertNull(FileChooserHelper.lastDirectory());
        first.countDown(); // the slow check finishes in the background
        Thread.sleep(300);

        CountDownLatch second = new CountDownLatch(1);
        // still slow, but known to exist a moment ago
        FileChooserHelper.setDirectoryProbe(hangingProbe(second), Duration.ofMillis(100));
        try {
            FileChooser chooser = new FileChooser();
            FileChooserHelper.applyInitialDir(chooser);
            assertEquals(dir.toFile(), chooser.getInitialDirectory());
        } finally {
            second.countDown();
        }
    }

    @Test
    void aChosenFileInASlowFolderIsRememberedInTheBackground(@TempDir Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("slow.xml"));
        CountDownLatch release = new CountDownLatch(1);
        FileChooserHelper.setDirectoryProbe(hangingProbe(release), Duration.ofMillis(100));

        long t0 = System.nanoTime();
        FileChooserHelper.remember(file.toFile());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 2000, "remembering must not wait for the file system, took " + ms + " ms");
        verify(props, never()).setLastOpenDirectory(org.mockito.ArgumentMatchers.anyString());

        release.countDown();
        verify(props, timeout(5000)).setLastOpenDirectory(dir.toFile().getAbsolutePath());
    }

    @Test
    void appliesRememberedDirectoryAsInitialDirectory(@TempDir Path dir) {
        when(props.getLastOpenDirectory()).thenReturn(dir.toString());

        FileChooser chooser = new FileChooser();
        FileChooserHelper.applyInitialDir(chooser);

        assertEquals(dir.toFile(), chooser.getInitialDirectory());
    }

    @Test
    void ignoresNonExistentRememberedDirectory(@TempDir Path dir) {
        when(props.getLastOpenDirectory()).thenReturn(dir.resolve("does-not-exist").toString());

        FileChooser chooser = new FileChooser();
        FileChooserHelper.applyInitialDir(chooser);

        assertNull(chooser.getInitialDirectory());
    }

    @Test
    void ignoresBlankRememberedDirectory() {
        when(props.getLastOpenDirectory()).thenReturn("   ");

        FileChooser chooser = new FileChooser();
        FileChooserHelper.applyInitialDir(chooser);

        assertNull(chooser.getInitialDirectory());
    }

    @Test
    void doesNotOverrideCallerSuppliedInitialDirectory(@TempDir Path remembered, @TempDir Path explicit) {
        when(props.getLastOpenDirectory()).thenReturn(remembered.toString());

        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(explicit.toFile());
        FileChooserHelper.applyInitialDir(chooser);

        assertEquals(explicit.toFile(), chooser.getInitialDirectory());
    }

    @Test
    void remembersParentDirectoryOfChosenFile(@TempDir Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("sample.xml"));

        FileChooserHelper.remember(file.toFile());

        verify(props).setLastOpenDirectory(dir.toFile().getAbsolutePath());
    }

    @Test
    void remembersChosenDirectoryItself(@TempDir Path dir) {
        FileChooserHelper.remember(dir.toFile());

        verify(props).setLastOpenDirectory(dir.toFile().getAbsolutePath());
    }

    @Test
    void doesNotRememberNull() {
        FileChooserHelper.remember(null);

        verify(props, never()).setLastOpenDirectory(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doesNotRememberFileWithoutExistingParent() {
        File orphan = new File("/no/such/parent/file.xml");

        FileChooserHelper.remember(orphan);

        verify(props, never()).setLastOpenDirectory(org.mockito.ArgumentMatchers.anyString());
    }
}
