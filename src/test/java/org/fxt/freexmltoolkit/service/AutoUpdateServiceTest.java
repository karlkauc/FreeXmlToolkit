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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for AutoUpdateService.
 * Tests platform detection, URL generation, and update progress handling.
 */
@DisplayName("AutoUpdateService Tests")
class AutoUpdateServiceTest {

    private AutoUpdateService autoUpdateService;

    @BeforeEach
    void setUp() {
        autoUpdateService = AutoUpdateServiceImpl.getInstance();
    }

    @Nested
    @DisplayName("Platform Detection Tests")
    class PlatformDetectionTests {

        @Test
        @DisplayName("getPlatformIdentifier should return valid platform string")
        void testGetPlatformIdentifier() {
            String platform = autoUpdateService.getPlatformIdentifier();

            assertNotNull(platform, "Platform identifier should not be null");
            assertTrue(
                    platform.equals("windows-x64") ||
                            platform.equals("macos-x64") ||
                            platform.equals("macos-arm64") ||
                            platform.equals("linux-x64"),
                    "Platform should be one of the supported platforms: " + platform
            );
        }

        @Test
        @DisplayName("Platform identifier should match current OS")
        void testPlatformMatchesOs() {
            String platform = autoUpdateService.getPlatformIdentifier();
            String osName = System.getProperty("os.name").toLowerCase();

            if (osName.contains("win")) {
                assertEquals("windows-x64", platform);
            } else if (osName.contains("mac")) {
                assertTrue(platform.equals("macos-x64") || platform.equals("macos-arm64"));
            } else {
                assertEquals("linux-x64", platform);
            }
        }
    }

    @Nested
    @DisplayName("Download URL Generation Tests")
    class DownloadUrlTests {

        @Test
        @DisplayName("getDownloadUrl should generate correct URL format")
        void testGetDownloadUrl() {
            UpdateInfo updateInfo = new UpdateInfo(
                    "1.0.0",
                    "2.0.0",
                    "Release 2.0.0",
                    "What's new",
                    "https://github.com/karlkauc/FreeXmlToolkit/releases/tag/v2.0.0",
                    "2024-12-01",
                    true
            );

            String url = autoUpdateService.getDownloadUrl(updateInfo);

            assertNotNull(url, "Download URL should not be null");
            assertTrue(url.startsWith("https://github.com/karlkauc/FreeXmlToolkit/releases/download/"),
                    "URL should start with GitHub releases download path");
            assertTrue(url.contains("app-image"), "URL should contain 'app-image'");
            assertTrue(url.contains("2.0.0"), "URL should contain the version number");
            assertTrue(url.endsWith(".zip"), "URL should end with .zip");
        }

        @Test
        @DisplayName("getDownloadUrl should strip 'v' prefix from version")
        void testGetDownloadUrlStripsVPrefix() {
            UpdateInfo updateInfo = new UpdateInfo(
                    "1.0.0",
                    "v2.0.0",  // Version with 'v' prefix
                    "Release 2.0.0",
                    "What's new",
                    "https://github.com/karlkauc/FreeXmlToolkit/releases/tag/v2.0.0",
                    "2024-12-01",
                    true
            );

            String url = autoUpdateService.getDownloadUrl(updateInfo);

            // URL should use version without v prefix in filename
            assertTrue(url.contains("/v2.0.0/"), "URL should have v prefix in tag path");
            assertTrue(url.contains("-2.0.0.zip"), "URL should not have v prefix in filename");
        }

        @Test
        @DisplayName("getDownloadUrl should include correct platform")
        void testGetDownloadUrlIncludesPlatform() {
            UpdateInfo updateInfo = new UpdateInfo(
                    "1.0.0",
                    "2.0.0",
                    "Release 2.0.0",
                    "What's new",
                    "https://github.com/karlkauc/FreeXmlToolkit/releases/tag/v2.0.0",
                    "2024-12-01",
                    true
            );

            String url = autoUpdateService.getDownloadUrl(updateInfo);
            String platform = autoUpdateService.getPlatformIdentifier();

            assertTrue(url.contains(platform), "URL should contain platform identifier: " + platform);
        }
    }

    @Nested
    @DisplayName("Update State Tests")
    class UpdateStateTests {

        @Test
        @DisplayName("isUpdateInProgress should return false initially")
        void testIsUpdateInProgressInitially() {
            assertFalse(autoUpdateService.isUpdateInProgress(),
                    "No update should be in progress initially");
        }

        @Test
        @DisplayName("cancelUpdate should not throw when no update in progress")
        void testCancelUpdateWhenNoUpdate() {
            assertDoesNotThrow(() -> autoUpdateService.cancelUpdate(),
                    "cancelUpdate should not throw when no update in progress");
        }
    }

    @Nested
    @DisplayName("UpdateProgress Record Tests")
    class UpdateProgressTests {

        @Test
        @DisplayName("UpdateProgress percentage calculation for downloading")
        void testProgressPercentageDownloading() {
            var progress = new AutoUpdateService.UpdateProgress(
                    AutoUpdateService.UpdateStage.DOWNLOADING,
                    50 * 1024 * 1024,  // 50 MB downloaded
                    100 * 1024 * 1024, // 100 MB total
                    "Downloading..."
            );

            assertEquals(0.5, progress.percentage(), 0.001,
                    "Percentage should be 50%");
        }

        @Test
        @DisplayName("UpdateProgress percentage returns -1 when total unknown")
        void testProgressPercentageUnknown() {
            var progress = new AutoUpdateService.UpdateProgress(
                    AutoUpdateService.UpdateStage.DOWNLOADING,
                    50 * 1024 * 1024,
                    -1,  // Unknown total
                    "Downloading..."
            );

            assertEquals(-1, progress.percentage(),
                    "Percentage should be -1 when total is unknown");
        }

        @Test
        @DisplayName("UpdateProgress percentage returns -1 for non-downloading stages")
        void testProgressPercentageNonDownloading() {
            var progress = new AutoUpdateService.UpdateProgress(
                    AutoUpdateService.UpdateStage.EXTRACTING,
                    0,
                    100,
                    "Extracting..."
            );

            assertEquals(-1, progress.percentage(),
                    "Percentage should be -1 for non-downloading stages");
        }
    }

    @Nested
    @DisplayName("UpdateResult Record Tests")
    class UpdateResultTests {

        @Test
        @DisplayName("UpdateResult.success should create successful result")
        void testUpdateResultSuccess() {
            var result = AutoUpdateService.UpdateResult.success(null);

            assertTrue(result.success(), "Result should be successful");
            assertNull(result.errorMessage(), "Error message should be null");
        }

        @Test
        @DisplayName("UpdateResult.failure should create failed result")
        void testUpdateResultFailure() {
            var result = AutoUpdateService.UpdateResult.failure("Download failed");

            assertFalse(result.success(), "Result should be failure");
            assertEquals("Download failed", result.errorMessage());
        }
    }

    @Nested
    @DisplayName("UpdateStage Enum Tests")
    class UpdateStageTests {

        @Test
        @DisplayName("All update stages should be defined")
        void testAllStagesDefined() {
            var stages = AutoUpdateService.UpdateStage.values();

            assertEquals(6, stages.length, "Should have 6 update stages");
            assertNotNull(AutoUpdateService.UpdateStage.PREPARING);
            assertNotNull(AutoUpdateService.UpdateStage.DOWNLOADING);
            assertNotNull(AutoUpdateService.UpdateStage.EXTRACTING);
            assertNotNull(AutoUpdateService.UpdateStage.LAUNCHING_UPDATER);
            assertNotNull(AutoUpdateService.UpdateStage.COMPLETED);
            assertNotNull(AutoUpdateService.UpdateStage.FAILED);
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    class ShutdownTests {

        @Test
        @DisplayName("shutdown should not throw exceptions")
        void testShutdownDoesNotThrow() {
            // Get a fresh instance for shutdown test
            AutoUpdateService service = AutoUpdateServiceImpl.getInstance();

            assertDoesNotThrow(service::shutdown,
                    "Shutdown should not throw exceptions");
        }
    }

    @Nested
    @DisplayName("App Directory Writability Tests")
    class AppDirectoryWritabilityTests {

        private AutoUpdateServiceImpl serviceImpl;

        @BeforeEach
        void setUp() {
            serviceImpl = (AutoUpdateServiceImpl) AutoUpdateServiceImpl.getInstance();
        }

        @Test
        @DisplayName("isAppDirectoryWritable should return true for writable temp directory")
        void testWritableTempDirectory(@TempDir Path tempDir) {
            assertTrue(serviceImpl.isAppDirectoryWritable(tempDir),
                    "Temp directory should be writable");
        }

        @Test
        @DisplayName("isAppDirectoryWritable should return false for non-existent directory")
        void testNonExistentDirectory() {
            Path nonExistent = Path.of("/this/path/should/not/exist/fxt-test-" + System.nanoTime());
            assertFalse(serviceImpl.isAppDirectoryWritable(nonExistent),
                    "Non-existent directory should not be writable");
        }
    }

    @Nested
    @DisplayName("PowerShell Argument Escaping Tests")
    class PowerShellEscapingTests {

        @Test
        @DisplayName("Doubles embedded single quotes so paths cannot break out of quoting")
        void escapesSingleQuotes() {
            // e.g. a Windows user named "O'Brien": C:\Users\O'Brien\AppData\Local\Temp\helper.exe
            String path = "C:\\Users\\O'Brien\\AppData\\Local\\Temp\\fxt-helper.exe";
            String escaped = AutoUpdateServiceImpl.escapePowerShellSingleQuoted(path);

            assertEquals("C:\\Users\\O''Brien\\AppData\\Local\\Temp\\fxt-helper.exe", escaped);
            // When wrapped in single quotes, no lone single quote may survive.
            String wrapped = "'" + escaped + "'";
            assertFalse(wrapped.contains("O'Brien"),
                    "A lone single quote must not survive: " + wrapped);
        }

        @Test
        @DisplayName("Leaves quote-free paths unchanged")
        void leavesNormalPathsUnchanged() {
            String path = "C:\\Users\\karl\\AppData\\Local\\Temp\\fxt-helper.exe";
            assertEquals(path, AutoUpdateServiceImpl.escapePowerShellSingleQuoted(path));
        }

        @Test
        @DisplayName("Handles null defensively")
        void handlesNull() {
            assertEquals("", AutoUpdateServiceImpl.escapePowerShellSingleQuoted(null));
        }
    }

    @Nested
    @DisplayName("Elevation Required (error 740) Detection Tests")
    class ElevationRequiredDetectionTests {

        @Test
        @DisplayName("Detects error=740 with localized German message")
        void detectsGermanMessage() {
            IOException e = new IOException(
                    "Cannot run program \"C:\\Users\\x\\AppData\\Local\\Temp\\fxt-helper-123.exe\": "
                            + "CreateProcess error=740, Der angeforderte Vorgang erfordert erhöhte Rechte");
            assertTrue(AutoUpdateServiceImpl.isElevationRequired(e));
        }

        @Test
        @DisplayName("Detects error=740 with localized English message")
        void detectsEnglishMessage() {
            IOException e = new IOException(
                    "Cannot run program \"C:\\tmp\\fxt-helper.exe\": "
                            + "CreateProcess error=740, The requested operation requires elevation");
            assertTrue(AutoUpdateServiceImpl.isElevationRequired(e));
        }

        @Test
        @DisplayName("Detects error=740 nested in the cause chain")
        void detectsInCauseChain() {
            IOException cause = new IOException("CreateProcess error=740, elevation required");
            IOException e = new IOException("Cannot run program", cause);
            assertTrue(AutoUpdateServiceImpl.isElevationRequired(e));
        }

        @Test
        @DisplayName("Ignores other CreateProcess error codes")
        void ignoresOtherErrorCodes() {
            IOException e = new IOException(
                    "Cannot run program: CreateProcess error=2, Das System kann die angegebene Datei nicht finden");
            assertFalse(AutoUpdateServiceImpl.isElevationRequired(e));
        }

        @Test
        @DisplayName("Does not match error=7400 (word boundary)")
        void doesNotMatchPrefixCodes() {
            IOException e = new IOException("CreateProcess error=7400, something else");
            assertFalse(AutoUpdateServiceImpl.isElevationRequired(e));
        }

        @Test
        @DisplayName("Handles null message and deep cause chains defensively")
        void handlesNullMessageAndDeepChains() {
            assertFalse(AutoUpdateServiceImpl.isElevationRequired(new IOException((String) null)));

            // Chain deeper than the traversal bound, without the token: must terminate and return false.
            Throwable deepest = new IOException("innermost");
            Throwable chain = deepest;
            for (int i = 0; i < 20; i++) {
                chain = new IOException("wrapper " + i, chain);
            }
            assertFalse(AutoUpdateServiceImpl.isElevationRequired(new IOException("outer", chain)));
        }
    }

    @Nested
    @DisplayName("Elevated Launch Command Tests")
    class ElevatedLaunchCommandTests {

        @Test
        @DisplayName("Builds a powershell Start-Process -Verb RunAs command with both paths")
        void buildsExpectedCommand() {
            Path helper = Path.of("C:\\Users\\karl\\AppData\\Local\\Temp\\fxt-helper-1.exe");
            Path config = Path.of("C:\\Users\\karl\\AppData\\Local\\Temp\\fxt-update-1\\extracted\\helper-config.toml");

            List<String> command = AutoUpdateServiceImpl.buildElevatedLaunchCommand(helper, config);

            assertEquals(4, command.size());
            assertEquals("powershell.exe", command.get(0));
            assertEquals("-NoProfile", command.get(1));
            assertEquals("-Command", command.get(2));
            String ps = command.get(3);
            assertTrue(ps.contains("-Verb RunAs"), ps);
            assertTrue(ps.contains("-FilePath '" + helper + "'"), ps);
            assertTrue(ps.contains("-ArgumentList '" + config + "'"), ps);
        }

        @Test
        @DisplayName("Escapes apostrophes in paths (O'Brien)")
        void escapesApostrophes() {
            Path helper = Path.of("C:\\Users\\O'Brien\\AppData\\Local\\Temp\\fxt-helper-1.exe");
            Path config = Path.of("C:\\Users\\O'Brien\\AppData\\Local\\Temp\\helper-config.toml");

            String ps = AutoUpdateServiceImpl.buildElevatedLaunchCommand(helper, config).get(3);

            assertTrue(ps.contains("O''Brien"), ps);
            assertFalse(ps.replace("O''Brien", "").contains("O'Brien"),
                    "No unescaped apostrophe may survive: " + ps);
        }
    }
}
