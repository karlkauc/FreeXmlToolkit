package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The settings file lives in the per-user app folder, independent of the working directory
 * (which differs per launch path and is read-only for installed builds).
 */
class PropertiesFileLocationTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("default location is <appDir>/FreeXmlToolkit.properties and the folder is created")
    void defaultsToAppFolder() {
        Path appDir = tmp.resolve("home/.freeXmlToolkit");
        File file = PropertiesServiceImpl.resolvePropertiesFile(null, appDir, tmp.resolve("cwd"));

        assertEquals(appDir.resolve("FreeXmlToolkit.properties").toFile(), file);
        assertTrue(Files.isDirectory(appDir));
        assertFalse(file.exists(), "nothing to migrate: the service writes defaults itself");
    }

    @Test
    @DisplayName("a legacy file in the working directory is copied once and left in place")
    void migratesLegacyFileFromWorkingDirectory() throws Exception {
        Path cwd = Files.createDirectories(tmp.resolve("cwd"));
        Path legacy = Files.writeString(cwd.resolve("FreeXmlToolkit.properties"), "theme=dark\n");
        Path appDir = tmp.resolve("home/.freeXmlToolkit");

        File file = PropertiesServiceImpl.resolvePropertiesFile("", appDir, cwd);

        assertEquals("theme=dark\n", Files.readString(file.toPath()));
        assertTrue(Files.exists(legacy), "the legacy file is kept for downgrades");
    }

    @Test
    @DisplayName("an existing file in the app folder is never overwritten by a legacy file")
    void existingTargetWins() throws Exception {
        Path cwd = Files.createDirectories(tmp.resolve("cwd"));
        Files.writeString(cwd.resolve("FreeXmlToolkit.properties"), "theme=dark\n");
        Path appDir = Files.createDirectories(tmp.resolve("home/.freeXmlToolkit"));
        Files.writeString(appDir.resolve("FreeXmlToolkit.properties"), "theme=light\n");

        File file = PropertiesServiceImpl.resolvePropertiesFile(null, appDir, cwd);

        assertEquals("theme=light\n", Files.readString(file.toPath()));
    }

    @Test
    @DisplayName("the fxt.properties.file override wins and triggers no migration")
    void overrideWins() throws Exception {
        Path cwd = Files.createDirectories(tmp.resolve("cwd"));
        Files.writeString(cwd.resolve("FreeXmlToolkit.properties"), "theme=dark\n");
        Path appDir = tmp.resolve("home/.freeXmlToolkit");
        String override = tmp.resolve("custom.properties").toString();

        File file = PropertiesServiceImpl.resolvePropertiesFile(override, appDir, cwd);

        assertEquals(new File(override), file);
        assertFalse(Files.exists(appDir));
    }
}
