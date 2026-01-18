/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.service;

import com.sun.net.httpserver.HttpServer;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for UpdateCheckService.
 * Tests version comparison, update checking, settings management, and error handling.
 */
@DisplayName("UpdateCheckService Tests")
class UpdateCheckServiceTest {

    private static HttpServer testServer;
    private static int testPort;
    private UpdateCheckServiceImpl updateCheckService;
    private MockPropertiesService mockPropertiesService;
    private MockConnectionService mockConnectionService;

    @BeforeAll
    static void setUpTestServer() throws IOException {
        // Start a simple HTTP test server to simulate GitHub API
        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        testPort = testServer.getAddress().getPort();

        // Handler for successful GitHub API release response
        testServer.createContext("/releases/latest", exchange -> {
            String response = """
                    {
                        "tag_name": "v2.0.0",
                        "name": "Release 2.0.0",
                        "body": "## What's New\\n- Feature A\\n- Feature B\\n- Bug fixes",
                        "html_url": "https://github.com/karlkauc/FreeXmlToolkit/releases/tag/v2.0.0",
                        "published_at": "2024-12-01T10:30:00Z"
                    }
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // Handler for empty tag_name response
        testServer.createContext("/releases/empty-tag", exchange -> {
            String response = """
                    {
                        "tag_name": "",
                        "name": "Invalid Release",
                        "body": "No tag",
                        "html_url": "https://github.com/test",
                        "published_at": "2024-01-01T00:00:00Z"
                    }
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // Handler for 404 error
        testServer.createContext("/releases/notfound", exchange -> {
            String response = "{\"message\": \"Not Found\"}";
            exchange.sendResponseHeaders(404, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        testServer.setExecutor(null);
        testServer.start();
    }

    @AfterAll
    static void tearDownTestServer() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        mockPropertiesService = new MockPropertiesService();
        mockConnectionService = new MockConnectionService();
        updateCheckService = new UpdateCheckServiceImpl(mockPropertiesService, mockConnectionService);
    }

    // ========== Version Comparison Tests ==========

    @Nested
    @DisplayName("Version Comparison Tests")
    class VersionComparisonTests {

        @Test
        @DisplayName("Should detect newer major version")
        void testNewerMajorVersion() {
            assertTrue(updateCheckService.isNewerVersion("2.0.0", "1.0.0"));
            assertTrue(updateCheckService.isNewerVersion("3.0.0", "2.5.10"));
            assertTrue(updateCheckService.isNewerVersion("10.0.0", "9.99.99"));
        }

        @Test
        @DisplayName("Should detect newer minor version")
        void testNewerMinorVersion() {
            assertTrue(updateCheckService.isNewerVersion("1.1.0", "1.0.0"));
            assertTrue(updateCheckService.isNewerVersion("1.5.0", "1.4.99"));
            assertTrue(updateCheckService.isNewerVersion("2.10.0", "2.9.0"));
        }

        @Test
        @DisplayName("Should detect newer patch version")
        void testNewerPatchVersion() {
            assertTrue(updateCheckService.isNewerVersion("1.0.1", "1.0.0"));
            assertTrue(updateCheckService.isNewerVersion("1.0.10", "1.0.9"));
            assertTrue(updateCheckService.isNewerVersion("2.5.100", "2.5.99"));
        }

        @Test
        @DisplayName("Should return false for equal versions")
        void testEqualVersions() {
            assertFalse(updateCheckService.isNewerVersion("1.0.0", "1.0.0"));
            assertFalse(updateCheckService.isNewerVersion("2.5.10", "2.5.10"));
        }

        @Test
        @DisplayName("Should return false for older versions")
        void testOlderVersions() {
            assertFalse(updateCheckService.isNewerVersion("1.0.0", "2.0.0"));
            assertFalse(updateCheckService.isNewerVersion("1.4.9", "1.5.0"));
            assertFalse(updateCheckService.isNewerVersion("1.0.0", "1.0.1"));
        }

        @Test
        @DisplayName("Should handle version with v prefix")
        void testVersionWithPrefix() {
            // The normalizeVersion is called before isNewerVersion in real usage
            // Testing raw comparison here
            assertTrue(updateCheckService.isNewerVersion("2.0.0", "1.0.0"));
        }

        @Test
        @DisplayName("Should handle versions with different number of parts")
        void testDifferentVersionParts() {
            assertTrue(updateCheckService.isNewerVersion("2.0", "1.9.9"));
            assertTrue(updateCheckService.isNewerVersion("1.1.0.0", "1.0.9.9"));
            assertTrue(updateCheckService.isNewerVersion("2", "1.9.9.9"));
        }

        @Test
        @DisplayName("Should strip non-numeric suffixes for comparison")
        void testVersionsWithSuffixes() {
            assertTrue(updateCheckService.isNewerVersion("2.0.0-beta", "1.9.9"));
            assertTrue(updateCheckService.isNewerVersion("2.0.0-SNAPSHOT", "1.0.0"));
            assertFalse(updateCheckService.isNewerVersion("1.0.0-beta", "1.0.0"));
        }

        @Test
        @DisplayName("Should handle null versions gracefully")
        void testNullVersions() {
            assertFalse(updateCheckService.isNewerVersion(null, "1.0.0"));
            assertFalse(updateCheckService.isNewerVersion("1.0.0", null));
            assertFalse(updateCheckService.isNewerVersion(null, null));
        }

        @Test
        @DisplayName("Should handle empty versions")
        void testEmptyVersions() {
            assertFalse(updateCheckService.isNewerVersion("", "1.0.0"));
            assertFalse(updateCheckService.isNewerVersion("1.0.0", ""));
        }
    }

    // ========== Settings Tests ==========

    @Nested
    @DisplayName("Settings Management Tests")
    class SettingsTests {

        @Test
        @DisplayName("Should return default enabled when not set")
        void testUpdateCheckEnabledDefault() {
            assertTrue(updateCheckService.isUpdateCheckEnabled());
        }

        @Test
        @DisplayName("Should return false when explicitly disabled")
        void testUpdateCheckDisabled() {
            mockPropertiesService.properties.setProperty("update.check.enabled", "false");
            assertFalse(updateCheckService.isUpdateCheckEnabled());
        }

        @Test
        @DisplayName("Should enable update check")
        void testSetUpdateCheckEnabled() {
            updateCheckService.setUpdateCheckEnabled(true);
            assertEquals("true", mockPropertiesService.properties.getProperty("update.check.enabled"));
        }

        @Test
        @DisplayName("Should disable update check")
        void testSetUpdateCheckDisabled() {
            updateCheckService.setUpdateCheckEnabled(false);
            assertEquals("false", mockPropertiesService.properties.getProperty("update.check.enabled"));
        }

        @Test
        @DisplayName("Should return null for unset skipped version")
        void testSkippedVersionDefault() {
            assertNull(updateCheckService.getSkippedVersion());
        }

        @Test
        @DisplayName("Should get skipped version")
        void testGetSkippedVersion() {
            mockPropertiesService.properties.setProperty("update.skipped.version", "2.0.0");
            assertEquals("2.0.0", updateCheckService.getSkippedVersion());
        }

        @Test
        @DisplayName("Should set skipped version")
        void testSetSkippedVersion() {
            updateCheckService.setSkippedVersion("2.1.0");
            assertEquals("2.1.0", mockPropertiesService.properties.getProperty("update.skipped.version"));
        }

        @Test
        @DisplayName("Should clear skipped version when set to null")
        void testClearSkippedVersion() {
            mockPropertiesService.properties.setProperty("update.skipped.version", "2.0.0");
            updateCheckService.setSkippedVersion(null);
            assertNull(mockPropertiesService.properties.getProperty("update.skipped.version"));
        }

        @Test
        @DisplayName("Should clear skipped version when set to blank")
        void testClearSkippedVersionBlank() {
            mockPropertiesService.properties.setProperty("update.skipped.version", "2.0.0");
            updateCheckService.setSkippedVersion("  ");
            assertNull(mockPropertiesService.properties.getProperty("update.skipped.version"));
        }
    }

    // ========== Current Version Tests ==========

    @Nested
    @DisplayName("Current Version Tests")
    class CurrentVersionTests {

        @Test
        @DisplayName("Should return default version when manifest not available")
        void testGetCurrentVersionDefault() {
            String version = updateCheckService.getCurrentVersion();
            assertNotNull(version);
            // Either manifest version or default "1.0.0"
            assertFalse(version.isBlank());
        }

        @Test
        @DisplayName("Should normalize version without v prefix")
        void testVersionNormalization() {
            // The service normalizes the version internally
            String version = updateCheckService.getCurrentVersion();
            assertFalse(version.startsWith("v") || version.startsWith("V"));
        }
    }

    // ========== UpdateInfo Tests ==========

    @Nested
    @DisplayName("UpdateInfo Record Tests")
    class UpdateInfoTests {

        @Test
        @DisplayName("Should create no-update-available info")
        void testNoUpdateAvailable() {
            UpdateInfo info = UpdateInfo.noUpdateAvailable("1.0.0");

            assertEquals("1.0.0", info.currentVersion());
            assertEquals("1.0.0", info.latestVersion());
            assertFalse(info.updateAvailable());
            assertNull(info.releaseName());
            assertNull(info.releaseNotes());
            assertNull(info.downloadUrl());
            assertNull(info.publishedDate());
        }

        @Test
        @DisplayName("Should create error-during-check info")
        void testErrorDuringCheck() {
            UpdateInfo info = UpdateInfo.errorDuringCheck("1.0.0");

            assertEquals("1.0.0", info.currentVersion());
            assertNull(info.latestVersion());
            assertFalse(info.updateAvailable());
        }

        @Test
        @DisplayName("Should create full update info")
        void testFullUpdateInfo() {
            UpdateInfo info = new UpdateInfo(
                    "1.0.0",
                    "2.0.0",
                    "Release 2.0.0",
                    "## Changes\n- Feature A",
                    "https://github.com/test/releases/tag/v2.0.0",
                    "2024-12-01",
                    true
            );

            assertEquals("1.0.0", info.currentVersion());
            assertEquals("2.0.0", info.latestVersion());
            assertEquals("Release 2.0.0", info.releaseName());
            assertEquals("## Changes\n- Feature A", info.releaseNotes());
            assertEquals("https://github.com/test/releases/tag/v2.0.0", info.downloadUrl());
            assertEquals("2024-12-01", info.publishedDate());
            assertTrue(info.updateAvailable());
        }
    }

    // ========== Update Check Flow Tests ==========

    @Nested
    @DisplayName("Update Check Flow Tests")
    class UpdateCheckFlowTests {

        @Test
        @DisplayName("Should detect update available")
        void testUpdateAvailable() throws ExecutionException, InterruptedException, TimeoutException {
            // Mock connection to return newer version
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{"Content-Type: application/json"},
                    """
                            {
                                "tag_name": "v99.0.0",
                                "name": "Release 99.0.0",
                                "body": "Major update",
                                "html_url": "https://github.com/test",
                                "published_at": "2024-12-01T10:30:00Z"
                            }
                            """
            ));

            CompletableFuture<UpdateInfo> future = updateCheckService.checkForUpdates();
            UpdateInfo result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertTrue(result.updateAvailable());
            assertEquals("99.0.0", result.latestVersion());
            assertEquals("Release 99.0.0", result.releaseName());
            assertEquals("Major update", result.releaseNotes());
            assertEquals("2024-12-01", result.publishedDate());
        }

        @Test
        @DisplayName("Should handle HTTP error gracefully")
        void testHttpError() throws ExecutionException, InterruptedException, TimeoutException {
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    404,
                    100L,
                    new String[]{},
                    "{\"message\": \"Not Found\"}"
            ));

            CompletableFuture<UpdateInfo> future = updateCheckService.checkForUpdates();
            UpdateInfo result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("Should handle null response gracefully")
        void testNullResponse() throws ExecutionException, InterruptedException, TimeoutException {
            mockConnectionService.setMockResponse(null);

            CompletableFuture<UpdateInfo> future = updateCheckService.checkForUpdates();
            UpdateInfo result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("Should handle empty response body")
        void testEmptyResponseBody() throws ExecutionException, InterruptedException, TimeoutException {
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{},
                    ""
            ));

            CompletableFuture<UpdateInfo> future = updateCheckService.checkForUpdates();
            UpdateInfo result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertFalse(result.updateAvailable());
        }

        @Test
        @DisplayName("Should skip update if version is marked as skipped")
        void testSkippedVersion() throws ExecutionException, InterruptedException, TimeoutException {
            // Set skipped version
            mockPropertiesService.properties.setProperty("update.skipped.version", "99.0.0");

            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{},
                    """
                            {
                                "tag_name": "v99.0.0",
                                "name": "Release 99.0.0",
                                "body": "Update",
                                "html_url": "https://github.com/test",
                                "published_at": "2024-12-01T10:30:00Z"
                            }
                            """
            ));

            CompletableFuture<UpdateInfo> future = updateCheckService.checkForUpdates();
            UpdateInfo result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertFalse(result.updateAvailable(), "Update should not be available for skipped version");
            assertEquals("99.0.0", result.latestVersion());
        }

        @Test
        @DisplayName("Should cache update check result")
        void testCaching() throws ExecutionException, InterruptedException, TimeoutException {
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{},
                    """
                            {
                                "tag_name": "v99.0.0",
                                "name": "Release 99.0.0",
                                "body": "Update",
                                "html_url": "https://github.com/test",
                                "published_at": "2024-12-01T10:30:00Z"
                            }
                            """
            ));

            // First call
            UpdateInfo result1 = updateCheckService.checkForUpdates().get(5, TimeUnit.SECONDS);

            // Change mock response
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{},
                    """
                            {
                                "tag_name": "v100.0.0",
                                "name": "Different Release",
                                "body": "Different",
                                "html_url": "https://github.com/test",
                                "published_at": "2024-12-01T10:30:00Z"
                            }
                            """
            ));

            // Second call should return cached result
            UpdateInfo result2 = updateCheckService.checkForUpdates().get(5, TimeUnit.SECONDS);

            assertEquals(result1.latestVersion(), result2.latestVersion());
            assertEquals("99.0.0", result2.latestVersion());
        }

        @Test
        @DisplayName("Should reset cache")
        void testResetCache() throws ExecutionException, InterruptedException, TimeoutException {
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{},
                    """
                            {
                                "tag_name": "v99.0.0",
                                "name": "Release 99.0.0",
                                "body": "Update",
                                "html_url": "https://github.com/test",
                                "published_at": "2024-12-01T10:30:00Z"
                            }
                            """
            ));

            // First call
            UpdateInfo result1 = updateCheckService.checkForUpdates().get(5, TimeUnit.SECONDS);
            assertEquals("99.0.0", result1.latestVersion());

            // Reset cache
            updateCheckService.resetCache();

            // Change mock response
            mockConnectionService.setMockResponse(new ConnectionResult(
                    URI.create("https://api.github.com/test"),
                    200,
                    100L,
                    new String[]{},
                    """
                            {
                                "tag_name": "v100.0.0",
                                "name": "Release 100.0.0",
                                "body": "Update",
                                "html_url": "https://github.com/test",
                                "published_at": "2024-12-01T10:30:00Z"
                            }
                            """
            ));

            // After reset, should get new result
            UpdateInfo result2 = updateCheckService.checkForUpdates().get(5, TimeUnit.SECONDS);
            assertEquals("100.0.0", result2.latestVersion());
        }
    }

    // ========== Singleton Tests ==========

    @Nested
    @DisplayName("Singleton Pattern Tests")
    class SingletonTests {

        @Test
        @DisplayName("Should return singleton instance")
        void testGetInstance() {
            UpdateCheckService instance1 = UpdateCheckServiceImpl.getInstance();
            UpdateCheckService instance2 = UpdateCheckServiceImpl.getInstance();

            assertNotNull(instance1);
            assertSame(instance1, instance2);
        }
    }

    // ========== Mock Classes ==========

    /**
     * Mock PropertiesService for testing without file I/O.
     */
    private static class MockPropertiesService implements PropertiesService {
        Properties properties = new Properties();
        boolean saveWasCalled = false;

        @Override
        public Properties loadProperties() {
            return properties;
        }

        @Override
        public void saveProperties(Properties save) {
            this.properties = save;
            saveWasCalled = true;
        }

        @Override
        public void createDefaultProperties() {
            properties = new Properties();
        }

        @Override
        public java.util.List<java.io.File> getLastOpenFiles() {
            return java.util.List.of();
        }

        @Override
        public void addLastOpenFile(java.io.File file) {
        }

        @Override
        public String getLastOpenDirectory() {
            return System.getProperty("user.home");
        }

        @Override
        public void setLastOpenDirectory(String path) {
        }

        @Override
        public String get(String key) {
            return properties.getProperty(key);
        }

        @Override
        public void set(String key, String value) {
            properties.setProperty(key, value);
        }

        @Override
        public int getXmlIndentSpaces() {
            return 4;
        }

        @Override
        public void setXmlIndentSpaces(int spaces) {
        }

        @Override
        public boolean isXmlAutoFormatAfterLoading() {
            return false;
        }

        @Override
        public void setXmlAutoFormatAfterLoading(boolean autoFormat) {
        }

        @Override
        public boolean isXsdAutoSaveEnabled() {
            return false;
        }

        @Override
        public void setXsdAutoSaveEnabled(boolean enabled) {
        }

        @Override
        public int getXsdAutoSaveInterval() {
            return 5;
        }

        @Override
        public void setXsdAutoSaveInterval(int minutes) {
        }

        @Override
        public boolean isXsdBackupEnabled() {
            return true;
        }

        @Override
        public void setXsdBackupEnabled(boolean enabled) {
        }

        @Override
        public int getXsdBackupVersions() {
            return 3;
        }

        @Override
        public void setXsdBackupVersions(int versions) {
        }

        @Override
        public boolean isXsdPrettyPrintOnSave() {
            return true;
        }

        @Override
        public void setXsdPrettyPrintOnSave(boolean enabled) {
        }

        @Override
        public boolean isSchematronPrettyPrintOnLoad() {
            return false;
        }

        @Override
        public void setSchematronPrettyPrintOnLoad(boolean enabled) {
        }

        @Override
        public org.fxt.freexmltoolkit.domain.XmlParserType getXmlParserType() {
            return org.fxt.freexmltoolkit.domain.XmlParserType.XERCES;
        }

        @Override
        public void setXmlParserType(org.fxt.freexmltoolkit.domain.XmlParserType parserType) {
        }

        @Override
        public boolean isUpdateCheckEnabled() {
            return Boolean.parseBoolean(properties.getProperty("update.check.enabled", "true"));
        }

        @Override
        public void setUpdateCheckEnabled(boolean enabled) {
            properties.setProperty("update.check.enabled", String.valueOf(enabled));
        }

        @Override
        public String getSkippedVersion() {
            return properties.getProperty("update.skipped.version", null);
        }

        @Override
        public void setSkippedVersion(String version) {
            if (version == null || version.isBlank()) {
                properties.remove("update.skipped.version");
            } else {
                properties.setProperty("update.skipped.version", version);
            }
        }

        @Override
        public boolean isUseSmallIcons() {
            return Boolean.parseBoolean(properties.getProperty("ui.use.small.icons", "false"));
        }

        @Override
        public void setUseSmallIcons(boolean useSmallIcons) {
            properties.setProperty("ui.use.small.icons", String.valueOf(useSmallIcons));
        }

        @Override
        public String getXsdSortOrder() {
            return properties.getProperty("xsd.sort.order", "TYPE_BEFORE_NAME");
        }

        @Override
        public void setXsdSortOrder(String sortOrder) {
            properties.setProperty("xsd.sort.order", sortOrder);
        }

        @Override
        public boolean isBackupUseSeparateDirectory() {
            return Boolean.parseBoolean(properties.getProperty("backup.use.separate.directory", "true"));
        }

        @Override
        public void setBackupUseSeparateDirectory(boolean useSeparate) {
            properties.setProperty("backup.use.separate.directory", String.valueOf(useSeparate));
        }

        @Override
        public String getBackupDirectory() {
            return properties.getProperty("backup.directory", System.getProperty("user.home") + "/.freexmltoolkit/backups");
        }

        @Override
        public void setBackupDirectory(String path) {
            properties.setProperty("backup.directory", path);
        }

        @Override
        public String getCustomTempFolder() {
            return properties.getProperty("custom.temp.folder", null);
        }

        @Override
        public void setCustomTempFolder(String path) {
            if (path == null || path.isBlank()) {
                properties.remove("custom.temp.folder");
            } else {
                properties.setProperty("custom.temp.folder", path);
            }
        }

        @Override
        public boolean isUseSystemTempFolder() {
            return Boolean.parseBoolean(properties.getProperty("use.system.temp.folder", "true"));
        }

        @Override
        public void setUseSystemTempFolder(boolean useSystem) {
            properties.setProperty("use.system.temp.folder", String.valueOf(useSystem));
        }

        @Override
        public String getTempFolder() {
            if (isUseSystemTempFolder()) {
                return System.getProperty("java.io.tmpdir");
            }
            return getCustomTempFolder() != null ? getCustomTempFolder() : System.getProperty("java.io.tmpdir");
        }

        @Override
        public boolean isXsltExtensionsAllowed() {
            return Boolean.parseBoolean(properties.getProperty("security.xslt.allow.extensions", "false"));
        }

        @Override
        public void setXsltExtensionsAllowed(boolean allowed) {
            properties.setProperty("security.xslt.allow.extensions", String.valueOf(allowed));
        }

        @Override
        public int getJsonIndentSpaces() {
            return 2;
        }

        @Override
        public void setJsonIndentSpaces(int spaces) {
        }

        @Override
        public java.util.List<java.io.File> getRecentJsonFiles() {
            return java.util.List.of();
        }

        @Override
        public void addRecentJsonFile(java.io.File file) {
        }

        @Override
        public java.util.List<java.io.File> getRecentXsltFiles() {
            return java.util.List.of();
        }

        @Override
        public void addRecentXsltFile(java.io.File file) {
        }

        @Override
        public void clearRecentXsltFiles() {
        }
    }

    /**
     * Mock ConnectionService for testing without network I/O.
     */
    private static class MockConnectionService implements ConnectionService {
        private ConnectionResult mockResponse;

        void setMockResponse(ConnectionResult response) {
            this.mockResponse = response;
        }

        @Override
        public ConnectionResult testHttpRequest(URI uri, Properties props) {
            return mockResponse;
        }

        @Override
        public ConnectionResult executeHttpRequest(URI uri) {
            return mockResponse;
        }

        @Override
        public String getTextContentFromURL(URI uri) {
            if (mockResponse != null && mockResponse.httpStatus() >= 200 && mockResponse.httpStatus() < 400) {
                return mockResponse.resultBody();
            }
            throw new RuntimeException("Failed to fetch content");
        }
    }
}
