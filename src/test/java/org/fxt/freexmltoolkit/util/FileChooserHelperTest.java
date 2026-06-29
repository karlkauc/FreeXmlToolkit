package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.stage.FileChooser;

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
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
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
