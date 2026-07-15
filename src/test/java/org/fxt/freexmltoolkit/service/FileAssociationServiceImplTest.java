package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.fileassoc.CommandRunner;
import org.fxt.freexmltoolkit.service.fileassoc.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAssociationServiceImplTest {

    @TempDir
    Path userHome;

    private final List<List<String>> executedCommands = new ArrayList<>();
    private final Map<String, String> storedValues = new HashMap<>();

    private final CommandRunner fakeRunner = command -> {
        executedCommands.add(command);
        return new CommandRunner.CommandResult(0, "", "");
    };

    private final KeyValueStore fakeStore = new KeyValueStore() {
        @Override
        public String get(String key) {
            return storedValues.get(key);
        }

        @Override
        public void set(String key, String value) {
            storedValues.put(key, value);
        }
    };

    private FileAssociationServiceImpl serviceWithLauncher(Path launcher) {
        return new FileAssociationServiceImpl(fakeRunner, launcher, userHome, fakeStore);
    }

    private Path createExecutableLauncher() throws IOException {
        Path launcher = userHome.resolve("app/bin/FreeXmlToolkit");
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, "#!/bin/sh\n");
        Files.setPosixFilePermissions(launcher, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return launcher;
    }

    @Test
    void notSupportedWithoutInstalledLauncher() {
        FileAssociationServiceImpl service = serviceWithLauncher(userHome.resolve("missing/FreeXmlToolkit"));
        assertFalse(service.isSupported());
        assertFalse(service.getUnsupportedReason().isBlank());

        FileAssociationResult result = service.register(Set.of(UnifiedEditorFileType.XML));
        assertFalse(result.success());
        assertTrue(executedCommands.isEmpty());
    }

    @Test
    void extensionMapCoversAllSevenSpecExtensions() {
        FileAssociationServiceImpl service = serviceWithLauncher(userHome.resolve("missing"));
        assertEquals(Set.of("xml"), service.extensionsFor(UnifiedEditorFileType.XML));
        assertEquals(Set.of("xsd"), service.extensionsFor(UnifiedEditorFileType.XSD));
        assertEquals(Set.of("xsl", "xslt"), service.extensionsFor(UnifiedEditorFileType.XSLT));
        assertEquals(Set.of("sch", "schematron"), service.extensionsFor(UnifiedEditorFileType.SCHEMATRON));
        assertEquals(Set.of("json"), service.extensionsFor(UnifiedEditorFileType.JSON));
    }

    @Test
    void registerRejectsEmptySelection() throws IOException {
        FileAssociationServiceImpl service = serviceWithLauncher(createExecutableLauncher());
        FileAssociationResult result = service.register(Set.of());
        assertFalse(result.success());
        assertTrue(executedCommands.isEmpty());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void registerDispatchesOnlySelectedTypes() throws IOException {
        FileAssociationServiceImpl service = serviceWithLauncher(createExecutableLauncher());
        FileAssociationResult result = service.register(Set.of(UnifiedEditorFileType.XML));
        assertTrue(result.success());

        List<String> flat = executedCommands.stream().map(c -> String.join(" ", c)).toList();
        assertTrue(flat.contains("xdg-mime default freexmltoolkit.desktop application/xml"));
        assertFalse(flat.stream().anyMatch(c -> c.contains("application/json")
                && c.contains("default")));
        assertTrue(Files.exists(userHome.resolve(".local/share/applications/freexmltoolkit.desktop")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void unregisterAllTypesRemovesDesktopEntry() throws IOException {
        FileAssociationServiceImpl service = serviceWithLauncher(createExecutableLauncher());
        service.register(Set.of(UnifiedEditorFileType.XML, UnifiedEditorFileType.XSD,
                UnifiedEditorFileType.XSLT, UnifiedEditorFileType.SCHEMATRON, UnifiedEditorFileType.JSON));
        Path desktopFile = userHome.resolve(".local/share/applications/freexmltoolkit.desktop");
        assertTrue(Files.exists(desktopFile));

        FileAssociationResult result = service.unregister(Set.of(UnifiedEditorFileType.XML,
                UnifiedEditorFileType.XSD, UnifiedEditorFileType.XSLT,
                UnifiedEditorFileType.SCHEMATRON, UnifiedEditorFileType.JSON));
        assertTrue(result.success());
        assertFalse(Files.exists(desktopFile));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linuxDoesNotOpenSystemSettingsPage() throws IOException {
        FileAssociationServiceImpl service = serviceWithLauncher(createExecutableLauncher());
        assertFalse(service.opensSystemSettingsPage());
    }
}
