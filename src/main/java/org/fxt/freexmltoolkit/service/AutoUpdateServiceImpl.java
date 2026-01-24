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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.fxt.freexmltoolkit.util.PathValidator;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Implementation of AutoUpdateService that downloads and applies updates from GitHub Releases.
 *
 * <p>This service downloads app-image ZIP files from GitHub Releases, extracts them,
 * and launches a platform-specific updater script to replace the application files
 * while the application is not running.
 *
 * @since 2.0
 */
public class AutoUpdateServiceImpl implements AutoUpdateService {

    private static final Logger logger = LogManager.getLogger(AutoUpdateServiceImpl.class);

    /**
     * GitHub repository owner
     */
    private static final String GITHUB_OWNER = "karlkauc";

    /**
     * GitHub repository name
     */
    private static final String GITHUB_REPO = "FreeXmlToolkit";

    /**
     * Buffer size for file operations
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * Connection timeout in milliseconds
     */
    private static final int CONNECT_TIMEOUT = 30000;

    /**
     * Read timeout in milliseconds
     */
    private static final int READ_TIMEOUT = 300000; // 5 minutes for large downloads

    private final ExecutorService executorService;
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile HttpURLConnection currentConnection;

    /**
     * Holder class for lazy singleton initialization.
     */
    private static class InstanceHolder {
        private static final AutoUpdateService INSTANCE = new AutoUpdateServiceImpl();
    }

    /**
     * Returns the singleton instance of AutoUpdateServiceImpl.
     *
     * @return the singleton instance
     */
    public static AutoUpdateService getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Private constructor for singleton pattern.
     */
    private AutoUpdateServiceImpl() {
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AutoUpdateService-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public CompletableFuture<UpdateResult> downloadAndApplyUpdate(
            UpdateInfo updateInfo,
            Consumer<UpdateProgress> progressCallback) {

        if (!updateInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(
                    UpdateResult.failure("An update is already in progress"));
        }

        cancelRequested.set(false);

        return CompletableFuture.supplyAsync(() -> {
            Path tempDir = null;
            Path zipFile = null;

            try {
                // Stage 1: Preparing
                reportProgress(progressCallback, UpdateStage.PREPARING, 0, -1,
                        "Determining download URL...");

                String downloadUrl = getDownloadUrl(updateInfo);
                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    return UpdateResult.failure("Could not determine download URL for this platform");
                }

                logger.info("Download URL: {}", downloadUrl);

                // Create temp directory for update
                tempDir = Files.createTempDirectory("fxt-update-");
                zipFile = tempDir.resolve("update.zip");

                // Stage 2: Downloading
                reportProgress(progressCallback, UpdateStage.DOWNLOADING, 0, -1,
                        "Starting download...");

                if (cancelRequested.get()) {
                    return UpdateResult.failure("Update cancelled by user");
                }

                downloadFile(downloadUrl, zipFile, progressCallback);

                if (cancelRequested.get()) {
                    return UpdateResult.failure("Update cancelled by user");
                }

                // Stage 3: Extracting
                reportProgress(progressCallback, UpdateStage.EXTRACTING, 0, -1,
                        "Extracting update...");

                Path extractedDir = tempDir.resolve("extracted");
                Files.createDirectories(extractedDir);
                extractZip(zipFile, extractedDir);

                // Delete the zip file after extraction to save space
                Files.deleteIfExists(zipFile);

                if (cancelRequested.get()) {
                    return UpdateResult.failure("Update cancelled by user");
                }

                // Stage 4: Launching updater
                reportProgress(progressCallback, UpdateStage.LAUNCHING_UPDATER, 0, -1,
                        "Preparing to install update...");

                boolean updaterLaunched = launchUpdater(extractedDir);

                if (!updaterLaunched) {
                    return UpdateResult.failure("Failed to launch updater script");
                }

                // Stage 5: Completed
                reportProgress(progressCallback, UpdateStage.COMPLETED, 0, -1,
                        "Update ready. Application will restart...");

                return UpdateResult.success(extractedDir);

            } catch (IOException e) {
                logger.error("Update failed", e);
                reportProgress(progressCallback, UpdateStage.FAILED, 0, -1,
                        "Update failed: " + e.getMessage());
                return UpdateResult.failure("Download failed: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error during update", e);
                reportProgress(progressCallback, UpdateStage.FAILED, 0, -1,
                        "Unexpected error: " + e.getMessage());
                return UpdateResult.failure("Update error: " + e.getMessage());
            } finally {
                updateInProgress.set(false);
                currentConnection = null;
            }
        }, executorService);
    }

    /**
     * Downloads a file from the given URL with progress reporting.
     */
    private void downloadFile(String urlString, Path destination,
                              Consumer<UpdateProgress> progressCallback) throws IOException {

        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(urlString);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            currentConnection = connection;

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "FreeXmlToolkit-AutoUpdater");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            // Handle redirects manually for GitHub releases
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                String newUrl = connection.getHeaderField("Location");
                logger.debug("Following redirect to: {}", newUrl);
                connection.disconnect();
                downloadFile(newUrl, destination, progressCallback);
                return;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + responseCode);
            }

            long totalBytes = connection.getContentLengthLong();
            logger.info("Downloading {} bytes", totalBytes > 0 ? totalBytes : "unknown");

            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                 OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(destination))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesDownloaded = 0;
                int bytesRead;
                long lastReportTime = System.currentTimeMillis();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (cancelRequested.get()) {
                        throw new IOException("Download cancelled by user");
                    }

                    outputStream.write(buffer, 0, bytesRead);
                    bytesDownloaded += bytesRead;

                    // Report progress every 100ms to avoid flooding the UI
                    long now = System.currentTimeMillis();
                    if (now - lastReportTime > 100) {
                        String message = formatDownloadProgress(bytesDownloaded, totalBytes);
                        reportProgress(progressCallback, UpdateStage.DOWNLOADING,
                                bytesDownloaded, totalBytes, message);
                        lastReportTime = now;
                    }
                }

                // Final progress report
                reportProgress(progressCallback, UpdateStage.DOWNLOADING,
                        bytesDownloaded, totalBytes,
                        formatDownloadProgress(bytesDownloaded, totalBytes));
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Formats download progress as a human-readable string.
     */
    private String formatDownloadProgress(long bytesDownloaded, long totalBytes) {
        String downloaded = formatBytes(bytesDownloaded);
        if (totalBytes > 0) {
            String total = formatBytes(totalBytes);
            int percentage = (int) ((bytesDownloaded * 100) / totalBytes);
            return String.format("Downloaded %s of %s (%d%%)", downloaded, total, percentage);
        } else {
            return String.format("Downloaded %s", downloaded);
        }
    }

    /**
     * Formats bytes as a human-readable string.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Extracts a ZIP file to the given directory.
     */
    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        logger.info("Extracting {} to {}", zipFile, targetDir);

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
                Files.newInputStream(zipFile)))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (cancelRequested.get()) {
                    throw new IOException("Extraction cancelled by user");
                }

                Path entryPath = targetDir.resolve(entry.getName()).normalize();

                // Security check: prevent zip slip vulnerability
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // Create parent directories if needed
                    Files.createDirectories(entryPath.getParent());

                    // Extract file
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    // Preserve executable permission on Unix
                    if (!isWindows() && isExecutable(entry.getName())) {
                        setExecutable(entryPath);
                    }
                }
                zis.closeEntry();
            }
        }

        logger.info("Extraction completed");
    }

    /**
     * Checks if a file should be marked as executable based on its name.
     */
    private boolean isExecutable(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".sh") ||
                lower.contains("/bin/") ||
                fileName.equals("FreeXmlToolkit") ||
                fileName.contains("/FreeXmlToolkit");
    }

    /**
     * Sets executable permission on a file (Unix only).
     */
    private void setExecutable(Path path) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException e) {
            logger.debug("Could not set executable permission on {}: {}", path, e.getMessage());
        }
    }

    /**
     * Launches the platform-specific updater script.
     *
     * @param extractedDir Directory containing the extracted update
     * @return true if the updater was launched successfully
     */
    private boolean launchUpdater(Path extractedDir) {
        try {
            Path appDir = getApplicationDirectory();
            Path launcher = getApplicationLauncher();

            logger.info("Application directory: {}", appDir);
            logger.info("Launcher: {}", launcher);
            logger.info("Update directory: {}", extractedDir);

            // SECURITY: Validate paths before passing to shell scripts
            if (!validateUpdaterPaths(appDir, extractedDir, launcher)) {
                logger.error("Security validation failed for updater paths");
                return false;
            }

            // Create the updater script in the temp directory
            Path updaterScript = createUpdaterScript(extractedDir, appDir, launcher);

            // Launch the updater script
            // Note: Paths are embedded directly in the script, so no arguments needed.
            // This avoids all quoting issues with ProcessBuilder and cmd.exe argument parsing.
            ProcessBuilder pb;
            if (isWindows()) {
                // Simple call: script has all paths embedded, no argument parsing needed
                pb = new ProcessBuilder("cmd.exe", "/c", updaterScript.toString());
            } else {
                // Make the script executable
                setExecutable(updaterScript);
                // Simple call: script has all paths embedded, no argument parsing needed
                pb = new ProcessBuilder("/bin/bash", updaterScript.toString());
            }

            pb.directory(extractedDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            logger.info("Updater script launched successfully (PID: {})", process.pid());

            return true;

        } catch (IOException e) {
            logger.error("Failed to launch updater", e);
            return false;
        }
    }

    /**
     * Validates paths before passing them to updater scripts.
     *
     * <p>This method ensures paths don't contain characters that could
     * be used for command injection attacks.
     *
     * @return true if all paths are valid, false otherwise
     */
    private boolean validateUpdaterPaths(Path appDir, Path extractedDir, Path launcher) {
        // Check all paths are valid and don't target system directories
        if (!PathValidator.isOutputPathSafe(appDir.toString())) {
            logger.warn("SECURITY: Application directory targets system path: {}", appDir);
            return false;
        }

        if (!PathValidator.isOutputPathSafe(extractedDir.toString())) {
            logger.warn("SECURITY: Extract directory targets system path: {}", extractedDir);
            return false;
        }

        if (!PathValidator.isOutputPathSafe(launcher.toString())) {
            logger.warn("SECURITY: Launcher path targets system path: {}", launcher);
            return false;
        }

        // Check for suspicious characters that could break shell parsing
        // Even though we use array-form ProcessBuilder and quoted variables in scripts,
        // this is defense-in-depth
        String[] paths = {appDir.toString(), extractedDir.toString(), launcher.toString()};
        for (String path : paths) {
            if (path.contains("`") || path.contains("$(") || path.contains("${")) {
                logger.warn("SECURITY: Path contains suspicious shell characters: {}", path);
                return false;
            }
        }

        return true;
    }

    /**
     * Creates the updater script in the temp directory.
     * <p>
     * Paths are embedded directly into the generated script to avoid
     * quoting issues with ProcessBuilder and shell argument parsing.
     *
     * @param extractedDir Directory containing the extracted update
     * @param appDir Application installation directory
     * @param launcher Path to the application launcher executable
     * @return Path to the created updater script
     */
    private Path createUpdaterScript(Path extractedDir, Path appDir, Path launcher) throws IOException {
        String scriptContent;
        Path scriptPath;

        if (isWindows()) {
            scriptPath = extractedDir.resolve("updater.bat");
            scriptContent = createWindowsUpdaterScript(appDir, extractedDir, launcher);
        } else {
            scriptPath = extractedDir.resolve("updater.sh");
            scriptContent = createUnixUpdaterScript(appDir, extractedDir, launcher);
        }

        Files.writeString(scriptPath, scriptContent);
        logger.debug("Created updater script: {}", scriptPath);

        return scriptPath;
    }

    /**
     * Creates the Windows updater batch script content.
     * Includes comprehensive logging, parameter validation, and nested directory search.
     * <p>
     * Paths are embedded directly into the script to avoid quoting issues
     * with Windows cmd.exe and ProcessBuilder.
     *
     * @param appDir Application installation directory
     * @param extractedDir Directory containing the extracted update
     * @param launcher Path to the application launcher executable
     * @return The batch script content with embedded paths
     */
    private String createWindowsUpdaterScript(Path appDir, Path extractedDir, Path launcher) {
        String appDirStr = appDir.toString();
        String updateDirStr = extractedDir.toString();
        String launcherStr = launcher.toString();

        // Use String.format with text block to embed paths directly into the script.
        // This avoids all quoting issues with ProcessBuilder and cmd.exe argument parsing.
        return String.format("""
                @echo off
                setlocal enabledelayedexpansion

                set "LOG_FILE=%%TEMP%%\\fxt-update-%%DATE:~-4,4%%%%DATE:~-7,2%%%%DATE:~-10,2%%-%%TIME:~0,2%%%%TIME:~3,2%%%%TIME:~6,2%%.log"
                set "LOG_FILE=%%LOG_FILE: =0%%"

                :: Also create a latest.log symlink/copy for easy access
                set "LATEST_LOG=%%TEMP%%\\fxt-update-latest.log"

                call :log "========================================"
                call :log "FreeXmlToolkit Updater"
                call :log "Started: %%DATE%% %%TIME%%"
                call :log "========================================"
                call :log ""
                call :log "[INFO] Log file: %%LOG_FILE%%"

                :: Paths are embedded directly (no command-line argument parsing needed)
                set "APP_DIR=%s"
                set "UPDATE_DIR=%s"
                set "LAUNCHER=%s"

                call :log ""
                call :log "[CONFIG] Application directory: %%APP_DIR%%"
                call :log "[CONFIG] Update directory: %%UPDATE_DIR%%"
                call :log "[CONFIG] Launcher: %%LAUNCHER%%"
                call :log ""

                :: Validate directories exist
                call :log "[CHECK] Validating directories..."
                if not exist "%%APP_DIR%%" (
                    call :log "[ERROR] Application directory does not exist: %%APP_DIR%%"
                    call :finish_error
                )
                call :log "[OK] Application directory exists"

                if not exist "%%UPDATE_DIR%%" (
                    call :log "[ERROR] Update directory does not exist: %%UPDATE_DIR%%"
                    call :finish_error
                )
                call :log "[OK] Update directory exists"

                :: Check if launcher exists (current version)
                if exist "%%LAUNCHER%%" (
                    call :log "[OK] Current launcher exists: %%LAUNCHER%%"
                ) else (
                    call :log "[WARN] Current launcher not found: %%LAUNCHER%%"
                )

                :: Log current app directory contents
                call :log ""
                call :log "[INFO] Current application directory contents (top level):"
                for %%%%f in ("%%APP_DIR%%\\*") do (
                    call :log "       %%%%~nxf"
                )
                for /d %%%%d in ("%%APP_DIR%%\\*") do (
                    call :log "       [DIR] %%%%~nxd"
                )

                call :log ""
                call :log "[WAIT] Waiting for application to exit..."

                set "WAIT_COUNT=0"
                :wait_loop
                tasklist /FI "IMAGENAME eq FreeXmlToolkit.exe" 2>NUL | find /I "FreeXmlToolkit.exe" >NUL
                if %%ERRORLEVEL%%==0 (
                    set /a WAIT_COUNT+=1
                    if %%WAIT_COUNT%% GEQ 60 (
                        call :log "[ERROR] Timeout waiting for application to exit after 60 seconds"
                        call :finish_error
                    )
                    call :log "[WAIT] Application still running (%%WAIT_COUNT%%s)..."
                    timeout /t 1 /nobreak >NUL
                    goto wait_loop
                )
                call :log "[OK] Application process not found"

                :: Additional wait to ensure all file handles are released
                call :log "[WAIT] Waiting 3 seconds for file handles to be released..."
                timeout /t 3 /nobreak >NUL
                call :log "[OK] File handle wait complete"

                call :log ""
                call :log "[SEARCH] Looking for update files..."

                :: Log update directory structure
                call :log "[INFO] Update directory contents:"
                call :log "       Root: %%UPDATE_DIR%%"
                for %%%%f in ("%%UPDATE_DIR%%\\*") do (
                    call :log "       %%%%~nxf"
                )
                for /d %%%%d in ("%%UPDATE_DIR%%\\*") do (
                    call :log "       [DIR] %%%%~nxd"
                    for %%%%f in ("%%%%d\\*") do (
                        call :log "             %%%%~nxf"
                    )
                    for /d %%%%e in ("%%%%d\\*") do (
                        call :log "             [DIR] %%%%~nxe"
                    )
                )

                :: Find the extracted app folder - check first level
                set "UPDATE_APP_DIR="
                call :log ""
                call :log "[SEARCH] Checking first level for FreeXmlToolkit.exe..."
                for /d %%%%d in ("%%UPDATE_DIR%%\\*") do (
                    call :log "[SEARCH] Checking: %%%%d"
                    if exist "%%%%d\\FreeXmlToolkit.exe" (
                        set "UPDATE_APP_DIR=%%%%d"
                        call :log "[FOUND] FreeXmlToolkit.exe found in: %%%%d"
                    ) else (
                        call :log "[SEARCH] Not found in: %%%%d"
                    )
                )

                :: If not found, check second level (nested zip structure)
                if "%%UPDATE_APP_DIR%%"=="" (
                    call :log ""
                    call :log "[SEARCH] Checking second level (nested structure)..."
                    for /d %%%%d in ("%%UPDATE_DIR%%\\*") do (
                        for /d %%%%e in ("%%%%d\\*") do (
                            call :log "[SEARCH] Checking: %%%%e"
                            if exist "%%%%e\\FreeXmlToolkit.exe" (
                                set "UPDATE_APP_DIR=%%%%e"
                                call :log "[FOUND] FreeXmlToolkit.exe found in: %%%%e"
                            )
                        )
                    )
                )

                :: If still not found, check third level
                if "%%UPDATE_APP_DIR%%"=="" (
                    call :log ""
                    call :log "[SEARCH] Checking third level..."
                    for /d %%%%d in ("%%UPDATE_DIR%%\\*") do (
                        for /d %%%%e in ("%%%%d\\*") do (
                            for /d %%%%f in ("%%%%e\\*") do (
                                call :log "[SEARCH] Checking: %%%%f"
                                if exist "%%%%f\\FreeXmlToolkit.exe" (
                                    set "UPDATE_APP_DIR=%%%%f"
                                    call :log "[FOUND] FreeXmlToolkit.exe found in: %%%%f"
                                )
                            )
                        )
                    )
                )

                if "%%UPDATE_APP_DIR%%"=="" (
                    call :log ""
                    call :log "[ERROR] Could not find FreeXmlToolkit.exe in update directory"
                    call :log "[ERROR] Full directory listing:"
                    dir /b /s "%%UPDATE_DIR%%" >> "%%LOG_FILE%%" 2>&1
                    call :finish_error
                )

                call :log ""
                call :log "[INFO] Update source directory: %%UPDATE_APP_DIR%%"
                call :log "[INFO] Contents of update source:"
                for %%%%f in ("%%UPDATE_APP_DIR%%\\*") do (
                    call :log "       %%%%~nxf (%%~zf bytes)"
                )
                for /d %%%%d in ("%%UPDATE_APP_DIR%%\\*") do (
                    call :log "       [DIR] %%%%~nxd"
                )

                :: Check file counts
                set "SRC_FILE_COUNT=0"
                for /r "%%UPDATE_APP_DIR%%" %%%%f in (*) do set /a SRC_FILE_COUNT+=1
                call :log "[INFO] Total files in update: %%SRC_FILE_COUNT%%"

                call :log ""
                call :log "[COPY] Starting file copy..."
                call :log "[COPY] Source: %%UPDATE_APP_DIR%%\\*"
                call :log "[COPY] Destination: %%APP_DIR%%\\"

                :: First, try to delete old files that might be locked
                call :log "[COPY] Attempting to clear old exe first..."
                if exist "%%APP_DIR%%\\FreeXmlToolkit.exe" (
                    del /f "%%APP_DIR%%\\FreeXmlToolkit.exe" >NUL 2>&1
                    if exist "%%APP_DIR%%\\FreeXmlToolkit.exe" (
                        call :log "[WARN] Could not delete old FreeXmlToolkit.exe - might be locked"
                    ) else (
                        call :log "[OK] Old FreeXmlToolkit.exe deleted"
                    )
                )

                :: Copy new files with full output
                call :log "[COPY] Executing xcopy..."
                xcopy /E /Y /I "%%UPDATE_APP_DIR%%\\*" "%%APP_DIR%%\\" >> "%%LOG_FILE%%" 2>&1
                set "XCOPY_RESULT=%%ERRORLEVEL%%"

                call :log "[COPY] xcopy exit code: %%XCOPY_RESULT%%"

                if %%XCOPY_RESULT%% neq 0 (
                    call :log "[ERROR] xcopy failed with error code: %%XCOPY_RESULT%%"
                    call :log "[ERROR] Error codes: 0=OK, 1=No files, 2=CTRL+C, 4=Init error, 5=Disk write error"
                    call :log ""
                    call :log "[DEBUG] Attempting robocopy as fallback..."

                    robocopy "%%UPDATE_APP_DIR%%" "%%APP_DIR%%" /E /IS /IT /NFL /NDL /NJH /NJS >> "%%LOG_FILE%%" 2>&1
                    set "ROBO_RESULT=%%ERRORLEVEL%%"
                    call :log "[DEBUG] robocopy exit code: %%ROBO_RESULT%% (codes < 8 are success)"

                    if %%ROBO_RESULT%% GEQ 8 (
                        call :log "[ERROR] Both xcopy and robocopy failed!"
                        call :log "[ERROR] Please try running as Administrator"
                        call :finish_error
                    )
                    call :log "[OK] robocopy completed successfully"
                )

                :: Verify the copy
                call :log ""
                call :log "[VERIFY] Verifying installation..."
                if exist "%%APP_DIR%%\\FreeXmlToolkit.exe" (
                    call :log "[OK] FreeXmlToolkit.exe exists in destination"
                    for %%%%f in ("%%APP_DIR%%\\FreeXmlToolkit.exe") do (
                        call :log "[INFO] New exe size: %%%%~zf bytes"
                        call :log "[INFO] New exe date: %%%%~tf"
                    )
                ) else (
                    call :log "[ERROR] FreeXmlToolkit.exe NOT FOUND in destination after copy!"
                    call :log "[ERROR] Listing destination directory:"
                    dir "%%APP_DIR%%" >> "%%LOG_FILE%%" 2>&1
                    call :finish_error
                )

                :: Count files in destination
                set "DST_FILE_COUNT=0"
                for /r "%%APP_DIR%%" %%%%f in (*) do set /a DST_FILE_COUNT+=1
                call :log "[INFO] Total files in destination: %%DST_FILE_COUNT%%"

                call :log ""
                call :log "[CLEANUP] Cleaning up temporary files..."
                rmdir /S /Q "%%UPDATE_DIR%%" 2>NUL
                if exist "%%UPDATE_DIR%%" (
                    call :log "[WARN] Could not fully remove update directory"
                ) else (
                    call :log "[OK] Update directory cleaned up"
                )

                :: Small delay before starting
                call :log ""
                call :log "[START] Preparing to launch application..."
                timeout /t 2 /nobreak >NUL

                :: Verify launcher exists
                if not exist "%%LAUNCHER%%" (
                    call :log "[ERROR] Launcher not found after update: %%LAUNCHER%%"
                    call :log "[ERROR] Available executables in app dir:"
                    dir /b "%%APP_DIR%%\\*.exe" >> "%%LOG_FILE%%" 2>&1
                    call :finish_error
                )

                call :log "[START] Launching: %%LAUNCHER%%"
                start "" "%%LAUNCHER%%"
                set "START_RESULT=%%ERRORLEVEL%%"

                if %%START_RESULT%% neq 0 (
                    call :log "[ERROR] Failed to start application, error code: %%START_RESULT%%"
                ) else (
                    call :log "[OK] Application launch command executed"
                )

                call :log ""
                call :log "========================================"
                call :log "UPDATE COMPLETED SUCCESSFULLY"
                call :log "Finished: %%DATE%% %%TIME%%"
                call :log "========================================"

                :: Copy log to latest
                copy /Y "%%LOG_FILE%%" "%%LATEST_LOG%%" >NUL 2>&1

                exit /b 0

                :log
                echo %%~1
                echo %%~1 >> "%%LOG_FILE%%"
                goto :eof

                :finish_error
                call :log ""
                call :log "========================================"
                call :log "UPDATE FAILED"
                call :log "See log file: %%LOG_FILE%%"
                call :log "========================================"
                copy /Y "%%LOG_FILE%%" "%%LATEST_LOG%%" >NUL 2>&1
                echo.
                echo UPDATE FAILED - See log file:
                echo %%LOG_FILE%%
                echo.
                echo Press any key to open log file...
                pause >NUL
                notepad "%%LOG_FILE%%"
                exit /b 1
                """, appDirStr, updateDirStr, launcherStr);
    }

    /**
     * Creates the Unix (macOS/Linux) updater shell script content.
     * <p>
     * Paths are embedded directly into the script for consistency with
     * the Windows implementation.
     *
     * @param appDir Application installation directory
     * @param extractedDir Directory containing the extracted update
     * @param launcher Path to the application launcher executable
     * @return The shell script content with embedded paths
     */
    private String createUnixUpdaterScript(Path appDir, Path extractedDir, Path launcher) {
        String appDirStr = appDir.toString();
        String updateDirStr = extractedDir.toString();
        String launcherStr = launcher.toString();

        return String.format("""
                #!/bin/bash

                # Paths are embedded directly (no command-line argument parsing needed)
                APP_DIR="%s"
                UPDATE_DIR="%s"
                LAUNCHER="%s"

                echo "FreeXmlToolkit Updater"
                echo "======================"
                echo "Application directory: $APP_DIR"
                echo "Update directory: $UPDATE_DIR"
                echo "Launcher: $LAUNCHER"

                echo "Waiting for application to exit..."

                # Wait for application to exit
                while pgrep -f "FreeXmlToolkit" > /dev/null 2>&1; do
                    sleep 1
                done

                echo "Application has exited. Installing update..."

                # Find the extracted app folder
                UPDATE_APP_DIR=$(find "$UPDATE_DIR" -maxdepth 2 -name "FreeXmlToolkit" -type d 2>/dev/null | head -1)

                if [ -z "$UPDATE_APP_DIR" ]; then
                    # Try to find by looking for the launcher
                    UPDATE_APP_DIR=$(find "$UPDATE_DIR" -maxdepth 3 -name "FreeXmlToolkit" -type f 2>/dev/null | head -1)
                    if [ -n "$UPDATE_APP_DIR" ]; then
                        UPDATE_APP_DIR=$(dirname "$UPDATE_APP_DIR")
                    fi
                fi

                if [ -z "$UPDATE_APP_DIR" ]; then
                    echo "Error: Could not find update files"
                    exit 1
                fi

                echo "Found update in: $UPDATE_APP_DIR"

                # Copy new files
                echo "Copying files..."
                cp -R "$UPDATE_APP_DIR"/* "$APP_DIR/" 2>/dev/null || {
                    cp -R "$UPDATE_APP_DIR"/../* "$APP_DIR/" 2>/dev/null
                }

                if [ $? -ne 0 ]; then
                    echo "Error copying files!"
                    exit 1
                fi

                # Make launcher executable
                chmod +x "$LAUNCHER" 2>/dev/null

                # Find and make all executables in bin directory executable
                find "$APP_DIR" -type f -name "*.sh" -exec chmod +x {} \\;
                find "$APP_DIR/bin" -type f -exec chmod +x {} \\; 2>/dev/null

                # Cleanup update directory
                echo "Cleaning up..."
                rm -rf "$UPDATE_DIR"

                # Restart application
                echo "Starting updated application..."
                "$LAUNCHER" &

                exit 0
                """, appDirStr, updateDirStr, launcherStr);
    }

    /**
     * Gets the application installation directory.
     */
    private Path getApplicationDirectory() {
        // Get the directory where the application JAR is located
        try {
            Path jarPath = Path.of(getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            // If running from JAR, go up to the app directory
            if (jarPath.toString().endsWith(".jar")) {
                // Typical structure: app/lib/FreeXmlToolkit.jar -> app
                return jarPath.getParent().getParent();
            }

            // If running from IDE, use current working directory
            return Path.of(System.getProperty("user.dir"));

        } catch (Exception e) {
            logger.warn("Could not determine application directory from JAR location", e);
            return Path.of(System.getProperty("user.dir"));
        }
    }

    /**
     * Gets the path to the application launcher.
     */
    private Path getApplicationLauncher() {
        Path appDir = getApplicationDirectory();

        if (isWindows()) {
            return appDir.resolve("FreeXmlToolkit.exe");
        } else if (isMacOS()) {
            // On macOS, the app is in a .app bundle or directly in the folder
            Path macOsLauncher = appDir.resolve("Contents/MacOS/FreeXmlToolkit");
            if (Files.exists(macOsLauncher)) {
                return macOsLauncher;
            }
            return appDir.resolve("bin/FreeXmlToolkit");
        } else {
            return appDir.resolve("bin/FreeXmlToolkit");
        }
    }

    @Override
    public void cancelUpdate() {
        logger.info("Update cancellation requested");
        cancelRequested.set(true);

        // Try to abort the current connection
        HttpURLConnection conn = currentConnection;
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception e) {
                logger.debug("Error disconnecting during cancel", e);
            }
        }
    }

    @Override
    public boolean isUpdateInProgress() {
        return updateInProgress.get();
    }

    @Override
    public String getPlatformIdentifier() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch");

        if (os.contains("win")) {
            return "windows-x64"; // Only x64 supported on Windows
        } else if (os.contains("mac")) {
            // Check for ARM64 (Apple Silicon)
            if (arch.equals("aarch64") || arch.contains("arm64")) {
                return "macos-arm64";
            }
            return "macos-x64";
        } else {
            return "linux-x64"; // Only x64 supported on Linux
        }
    }

    @Override
    public String getDownloadUrl(UpdateInfo updateInfo) {
        String platform = getPlatformIdentifier();
        String version = updateInfo.latestVersion();

        // Normalize version (remove 'v' prefix if present)
        if (version != null && version.startsWith("v")) {
            version = version.substring(1);
        }

        // Build the download URL for the app-image ZIP
        // Format: https://github.com/karlkauc/FreeXmlToolkit/releases/download/v{VERSION}/FreeXmlToolkit-{PLATFORM}-app-image-{VERSION}.zip
        return String.format(
                "https://github.com/%s/%s/releases/download/v%s/FreeXmlToolkit-%s-app-image-%s.zip",
                GITHUB_OWNER, GITHUB_REPO, version, platform, version
        );
    }

    /**
     * Reports progress to the callback.
     */
    private void reportProgress(Consumer<UpdateProgress> callback, UpdateStage stage,
                                long bytesDownloaded, long totalBytes, String message) {
        if (callback != null) {
            try {
                callback.accept(new UpdateProgress(stage, bytesDownloaded, totalBytes, message));
            } catch (Exception e) {
                logger.warn("Error in progress callback", e);
            }
        }
    }

    /**
     * Checks if the current platform is Windows.
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Checks if the current platform is macOS.
     */
    private boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down AutoUpdateService...");
        cancelUpdate();

        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("AutoUpdateService ExecutorService did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for AutoUpdateService shutdown", e);
            Thread.currentThread().interrupt();
        }
        logger.info("AutoUpdateService shutdown completed");
    }
}
