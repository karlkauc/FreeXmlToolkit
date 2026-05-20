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

import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FundsXmlExtensionService Tests")
class FundsXmlExtensionServiceTest {

    @TempDir
    Path tempDir;

    private FundsXmlCache cache;
    private FakeGitHubClient fakeClient;
    private FundsXmlExtensionService service;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        cache = ctor.newInstance(tempDir.resolve("fundsxml"));
        fakeClient = new FakeGitHubClient();
        service = new FundsXmlExtensionService(cache, fakeClient);
    }

    @Test
    @DisplayName("downloadOrUpdate copies schema files into versioned directory")
    void downloadSchema() throws Exception {
        GitHubRelease schemaRel = release("4.2.10", "schema-zip-url");
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, schemaRel);
        fakeClient.zipballForUrl.put("schema-zip-url", buildSchemaZip());
        fakeClient.latestForRepo.put(FundsXmlExtensionService.EXAMPLES_REPO, null);
        fakeClient.fileForUri.put(
                "https://api.github.com/repos/fundsxml/examples/zipball/main",
                buildExamplesZip());

        FundsXmlExtensionService.DownloadResult result = service.downloadOrUpdate(DownloadProgressCallback.NO_OP);

        assertTrue(result.isSuccess(), result.error());
        assertEquals("4.2.10", result.schemaVersion());
        assertTrue(result.schemaDownloaded());
        assertTrue(Files.isRegularFile(cache.getSchemaVersionDir("4.2.10").resolve("FundsXML4.xsd")));
        assertTrue(Files.isRegularFile(cache.getSchemaVersionDir("4.2.10").resolve("include_files/A.xsd")));
    }

    @Test
    @DisplayName("downloadOrUpdate sorts example files into examples/, schematron/, queries/")
    void downloadExamplesSorted() throws Exception {
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, release("4.2.10", "schema-zip-url"));
        fakeClient.zipballForUrl.put("schema-zip-url", buildSchemaZip());
        fakeClient.latestForRepo.put(FundsXmlExtensionService.EXAMPLES_REPO, null);
        fakeClient.fileForUri.put(
                "https://api.github.com/repos/fundsxml/examples/zipball/main",
                buildExamplesZip());

        FundsXmlExtensionService.DownloadResult result = service.downloadOrUpdate(DownloadProgressCallback.NO_OP);

        assertTrue(result.isSuccess(), result.error());
        assertTrue(Files.isRegularFile(cache.getExamplesDir().resolve("sample1.xml")));
        assertTrue(Files.isRegularFile(cache.getSchematronDir().resolve("rules.sch")));
        assertTrue(Files.isRegularFile(cache.getQueriesDir().resolve("q1.xpath")));
        // README.md must NOT be copied
        assertFalse(Files.exists(cache.getExamplesDir().resolve("README.md")));
        assertEquals(1, result.examplesDownloaded());
        assertEquals(1, result.schematronDownloaded());
        assertEquals(1, result.queriesDownloaded());
    }

    @Test
    @DisplayName("downloadOrUpdate sets active version on first install")
    void firstInstallSetsActive() throws Exception {
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, release("4.2.10", "schema-zip-url"));
        fakeClient.zipballForUrl.put("schema-zip-url", buildSchemaZip());
        fakeClient.latestForRepo.put(FundsXmlExtensionService.EXAMPLES_REPO, null);
        fakeClient.fileForUri.put(
                "https://api.github.com/repos/fundsxml/examples/zipball/main",
                buildExamplesZip());

        service.downloadOrUpdate(DownloadProgressCallback.NO_OP);

        assertEquals("4.2.10", cache.loadMetadata().getActiveSchemaVersion());
    }

    @Test
    @DisplayName("downloadOrUpdate is idempotent — does not re-download installed version")
    void idempotent() throws Exception {
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, release("4.2.10", "schema-zip-url"));
        fakeClient.zipballForUrl.put("schema-zip-url", buildSchemaZip());
        fakeClient.latestForRepo.put(FundsXmlExtensionService.EXAMPLES_REPO, null);
        fakeClient.fileForUri.put(
                "https://api.github.com/repos/fundsxml/examples/zipball/main",
                buildExamplesZip());

        service.downloadOrUpdate(DownloadProgressCallback.NO_OP);
        int firstSchemaCalls = fakeClient.schemaZipDownloads;

        FundsXmlExtensionService.DownloadResult second = service.downloadOrUpdate(DownloadProgressCallback.NO_OP);

        assertTrue(second.isSuccess(), second.error());
        assertFalse(second.schemaDownloaded(), "second run must not redownload an installed version");
        assertEquals(firstSchemaCalls, fakeClient.schemaZipDownloads);
    }

    @Test
    @DisplayName("downloadOrUpdate fails gracefully when latest release cannot be fetched")
    void failsGracefullyOnMissingRelease() {
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, null);

        FundsXmlExtensionService.DownloadResult result = service.downloadOrUpdate(DownloadProgressCallback.NO_OP);

        assertFalse(result.isSuccess());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Could not fetch"));
    }

    @Test
    @DisplayName("setActiveVersion rejects unknown versions")
    void setActiveVersionRejectsUnknown() throws IOException {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));

        assertTrue(service.setActiveVersion("4.2.10"));
        assertFalse(service.setActiveVersion("9.9.9"));
        assertFalse(service.setActiveVersion(null));

        assertEquals("4.2.10", cache.loadMetadata().getActiveSchemaVersion());
    }

    @Test
    @DisplayName("checkForUpdates returns release when newer than installed")
    void checkForUpdatesDetectsNew() throws IOException {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.9"));
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, release("4.2.10", "ignored"));

        GitHubRelease found = service.checkForUpdates();

        assertNotNull(found);
        assertEquals("4.2.10", found.tagName());
        assertNotNull(cache.loadMetadata().getLastUpdateCheck());
        assertEquals("4.2.10", cache.loadMetadata().getLatestKnownSchemaTag());
    }

    @Test
    @DisplayName("checkForUpdates returns null when latest is already installed")
    void checkForUpdatesSilentWhenUpToDate() throws IOException {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, release("4.2.10", "ignored"));

        assertNull(service.checkForUpdates());
    }

    @Test
    @DisplayName("checkForUpdates handles GitHub failure gracefully")
    void checkForUpdatesOnFailure() {
        fakeClient.latestForRepo.put(FundsXmlExtensionService.SCHEMA_REPO, null);
        assertNull(service.checkForUpdates());
        FundsXmlMetadata meta = cache.loadMetadata();
        assertNotNull(meta.getLastUpdateCheck()); // timestamp still recorded
    }

    @Test
    @DisplayName("extractZip refuses zip-slip entries")
    void extractZipBlocksZipSlip() throws IOException {
        Path malicious = tempDir.resolve("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(malicious))) {
            ZipEntry e = new ZipEntry("../escape.txt");
            zos.putNextEntry(e);
            zos.write("haha".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path target = Files.createDirectory(tempDir.resolve("safe"));

        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> FundsXmlExtensionService.extractZip(malicious, target));
        assertTrue(ex.getMessage().contains("Invalid zip entry"));
    }

    @Test
    @DisplayName("singleRootDir collapses single-root zipballs")
    void singleRootDirCollapse() throws IOException {
        Path extract = Files.createDirectories(tempDir.resolve("ex"));
        Path root = Files.createDirectory(extract.resolve("fundsxml-schema-abcdef"));
        assertEquals(root, FundsXmlExtensionService.singleRootDir(extract));
    }

    @Test
    @DisplayName("singleRootDir falls back when multiple top-level entries exist")
    void singleRootDirFallback() throws IOException {
        Path extract = Files.createDirectories(tempDir.resolve("ex"));
        Files.createDirectory(extract.resolve("a"));
        Files.createDirectory(extract.resolve("b"));
        assertEquals(extract, FundsXmlExtensionService.singleRootDir(extract));
    }

    // -----------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------

    private static GitHubRelease release(String tag, String zipUrl) {
        return new GitHubRelease(tag, "FundsXML " + tag, "notes", "html-url",
                "tar-url", zipUrl, "2026-01-23T10:30:00Z");
    }

    /** Mimics a GitHub schema zipball: one root folder containing the XSD plus an include. */
    private static byte[] buildSchemaZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("fundsxml-schema-abc123/FundsXML4.xsd", "<schema/>");
        entries.put("fundsxml-schema-abc123/include_files/A.xsd", "<schema/>");
        entries.put("fundsxml-schema-abc123/README.md", "ignore me");
        return makeZip(entries);
    }

    /** Mimics the examples repo: one XML, one .sch, one .xpath, plus an unrelated README. */
    private static byte[] buildExamplesZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("fundsxml-examples-def456/sample1.xml", "<root/>");
        entries.put("fundsxml-examples-def456/rules.sch", "<sch:schema/>");
        entries.put("fundsxml-examples-def456/q1.xpath", "//Fund");
        entries.put("fundsxml-examples-def456/README.md", "should not be copied");
        return makeZip(entries);
    }

    private static byte[] makeZip(Map<String, String> entries) throws IOException {
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bout)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bout.toByteArray();
    }

    /** Test double for {@link GitHubReleaseClient}. Returns pre-baked release info and zip bytes. */
    private static class FakeGitHubClient extends GitHubReleaseClient {
        final Map<String, GitHubRelease> latestForRepo = new HashMap<>();
        final Map<String, List<GitHubRelease>> releasesForRepo = new HashMap<>();
        final Map<String, byte[]> zipballForUrl = new HashMap<>();
        final Map<String, byte[]> fileForUri = new HashMap<>();
        int schemaZipDownloads = 0;

        FakeGitHubClient() {
            super(uri -> { throw new IOException("Network disabled in tests"); });
        }

        @Override
        public List<GitHubRelease> listReleases(String repo) {
            return releasesForRepo.getOrDefault(repo, List.of());
        }

        @Override
        public GitHubRelease getLatestRelease(String repo) {
            return latestForRepo.get(repo);
        }

        @Override
        public void downloadZipball(GitHubRelease release, Path destination,
                                    DownloadProgressCallback callback) throws IOException {
            if (release == null || release.zipballUrl() == null) {
                throw new IOException("No zipball URL");
            }
            byte[] bytes = zipballForUrl.get(release.zipballUrl());
            if (bytes == null) {
                throw new IOException("No fixture for " + release.zipballUrl());
            }
            Files.createDirectories(destination.getParent());
            try (OutputStream out = Files.newOutputStream(destination)) {
                out.write(bytes);
            }
            schemaZipDownloads++;
        }

        @Override
        public void downloadFile(URI uri, Path destination,
                                 DownloadProgressCallback callback, String stage) throws IOException {
            byte[] bytes = fileForUri.get(uri.toString());
            if (bytes == null) {
                throw new IOException("No fixture for " + uri);
            }
            Files.createDirectories(destination.getParent());
            try (OutputStream out = Files.newOutputStream(destination)) {
                out.write(bytes);
            }
        }
    }
}
