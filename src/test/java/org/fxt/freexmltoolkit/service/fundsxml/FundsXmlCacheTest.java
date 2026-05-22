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

package org.fxt.freexmltoolkit.service.fundsxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FundsXmlCache Tests")
class FundsXmlCacheTest {

    @TempDir
    Path tempDir;

    private FundsXmlCache cache;
    private String originalUserHome;

    @BeforeEach
    void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        FundsXmlCache.resetForTesting();
        // Use the package-private constructor to pin the base dir to the temp dir
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        cache = ctor.newInstance(tempDir.resolve("fundsxml"));
    }

    @AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
        FundsXmlCache.resetForTesting();
    }

    @Test
    @DisplayName("Creates required subdirectories on construction")
    void createsDirectoryLayout() {
        assertTrue(Files.isDirectory(cache.getBaseDir()));
        assertTrue(Files.isDirectory(cache.getSchemaDir()));
        assertTrue(Files.isDirectory(cache.getExamplesDir()));
        assertTrue(Files.isDirectory(cache.getSchematronDir()));
        assertTrue(Files.isDirectory(cache.getQueriesDir()));
    }

    @Test
    @DisplayName("Default singleton uses ~/.freeXmlToolkit/fundsxml")
    void defaultBaseDirHonorsUserHome() {
        FundsXmlCache.resetForTesting();
        FundsXmlCache defaultInstance = FundsXmlCache.getInstance();
        assertEquals(tempDir.resolve(".freeXmlToolkit").resolve("fundsxml"),
                defaultInstance.getBaseDir());
    }

    @Test
    @DisplayName("listInstalledVersions returns directory names sorted newest first")
    void listInstalledVersionsSorted() throws IOException {
        Files.createDirectories(cache.getSchemaDir().resolve("4.2.9"));
        Files.createDirectories(cache.getSchemaDir().resolve("4.2.10"));
        Files.createDirectories(cache.getSchemaDir().resolve("4.1.0"));

        List<String> versions = cache.listInstalledVersions();

        assertEquals(List.of("4.2.10", "4.2.9", "4.1.0"), versions);
    }

    @Test
    @DisplayName("listInstalledVersions ignores stray files")
    void listInstalledVersionsIgnoresFiles() throws IOException {
        Files.createDirectories(cache.getSchemaDir().resolve("4.2.10"));
        Files.writeString(cache.getSchemaDir().resolve("README.txt"), "stray");

        List<String> versions = cache.listInstalledVersions();

        assertEquals(List.of("4.2.10"), versions);
    }

    @Test
    @DisplayName("getSchemaFile returns the xsd when present, null otherwise")
    void getSchemaFileResolution() throws IOException {
        Path versionDir = cache.getSchemaVersionDir("4.2.10");
        Path xsd = versionDir.resolve("FundsXML4.xsd");
        Files.writeString(xsd, "<schema/>");

        assertEquals(xsd, cache.getSchemaFile("4.2.10"));
        assertNull(cache.getSchemaFile("9.9.9"));
        assertNull(cache.getSchemaFile(null));
        assertNull(cache.getSchemaFile(""));
    }

    @Test
    @DisplayName("getActiveSchemaFile follows metadata pointer")
    void getActiveSchemaFile() throws IOException {
        Path xsd = cache.getSchemaVersionDir("4.2.10").resolve("FundsXML4.xsd");
        Files.writeString(xsd, "<schema/>");

        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setActiveSchemaVersion("4.2.10");
        cache.saveMetadata(meta);

        assertEquals(xsd, cache.getActiveSchemaFile());
    }

    @Test
    @DisplayName("Metadata round-trip preserves values")
    void metadataRoundTrip() {
        FundsXmlMetadata original = new FundsXmlMetadata();
        original.setActiveSchemaVersion("4.2.10");
        original.setSchemaLastUpdated("2026-05-20T12:00:00Z");
        original.setExamplesLastUpdated("2026-05-20T12:05:00Z");
        original.setSchematronLastUpdated("2026-05-20T12:10:00Z");
        original.setQueriesLastUpdated("2026-05-20T12:15:00Z");
        original.setLastUpdateCheck("2026-05-20T12:20:00Z");
        original.setLatestKnownSchemaTag("v4.2.11");

        cache.saveMetadata(original);
        FundsXmlMetadata loaded = cache.loadMetadata();

        assertEquals("4.2.10", loaded.getActiveSchemaVersion());
        assertEquals("2026-05-20T12:00:00Z", loaded.getSchemaLastUpdated());
        assertEquals("2026-05-20T12:05:00Z", loaded.getExamplesLastUpdated());
        assertEquals("2026-05-20T12:10:00Z", loaded.getSchematronLastUpdated());
        assertEquals("2026-05-20T12:15:00Z", loaded.getQueriesLastUpdated());
        assertEquals("2026-05-20T12:20:00Z", loaded.getLastUpdateCheck());
        assertEquals("v4.2.11", loaded.getLatestKnownSchemaTag());
    }

    @Test
    @DisplayName("saveMetadata refreshes installed-versions list from disk")
    void saveMetadataReflectsDiskState() throws IOException {
        Files.createDirectories(cache.getSchemaDir().resolve("4.2.10"));
        Files.createDirectories(cache.getSchemaDir().resolve("4.2.9"));

        FundsXmlMetadata meta = new FundsXmlMetadata();
        cache.saveMetadata(meta);

        FundsXmlMetadata loaded = cache.loadMetadata();
        assertEquals(List.of("4.2.10", "4.2.9"), loaded.getInstalledSchemaVersions());
    }

    @Test
    @DisplayName("loadMetadata returns empty object when file is missing")
    void loadMetadataMissingFile() {
        FundsXmlMetadata meta = cache.loadMetadata();
        assertNotNull(meta);
        assertNull(meta.getActiveSchemaVersion());
        assertTrue(meta.getInstalledSchemaVersions().isEmpty());
    }

    @Test
    @DisplayName("removeVersion deletes directory and updates metadata")
    void removeVersionUpdatesMetadata() throws IOException {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));
        Files.writeString(cache.getSchemaVersionDir("4.2.10").resolve("FundsXML4.xsd"), "<schema/>");
        Files.createDirectories(cache.getSchemaVersionDir("4.2.9"));

        FundsXmlMetadata meta = new FundsXmlMetadata();
        meta.setActiveSchemaVersion("4.2.10");
        cache.saveMetadata(meta);

        assertTrue(cache.removeVersion("4.2.10"));

        assertFalse(Files.exists(cache.getSchemaDir().resolve("4.2.10")));
        FundsXmlMetadata after = cache.loadMetadata();
        // Active should fall back to the next available version
        assertEquals("4.2.9", after.getActiveSchemaVersion());
        assertEquals(List.of("4.2.9"), after.getInstalledSchemaVersions());
    }

    @Test
    @DisplayName("removeVersion clears active when no versions remain")
    void removeLastVersionClearsActive() throws IOException {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));
        FundsXmlMetadata meta = new FundsXmlMetadata();
        meta.setActiveSchemaVersion("4.2.10");
        cache.saveMetadata(meta);

        assertTrue(cache.removeVersion("4.2.10"));

        assertNull(cache.loadMetadata().getActiveSchemaVersion());
    }

    @Test
    @DisplayName("removeVersion is a no-op for unknown versions")
    void removeUnknownVersion() {
        assertFalse(cache.removeVersion("9.9.9"));
        assertFalse(cache.removeVersion(null));
        assertFalse(cache.removeVersion(""));
    }

    @Test
    @DisplayName("compareVersionsDesc handles mixed-width semver components")
    void compareVersionsDesc() {
        assertTrue(FundsXmlCache.compareVersionsDesc("4.2.10", "4.2.9") < 0); // 4.2.10 > 4.2.9 → comes first
        assertTrue(FundsXmlCache.compareVersionsDesc("4.2.9", "4.2.10") > 0);
        assertEquals(0, FundsXmlCache.compareVersionsDesc("4.2.10", "4.2.10"));
        assertTrue(FundsXmlCache.compareVersionsDesc("5.0.0", "4.99.99") < 0);
    }
}
