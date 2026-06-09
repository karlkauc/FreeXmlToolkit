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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;

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
            Path tempDir;
            Path zipFile;

            // Write debug log to user's home directory (always accessible)
            Path debugLog = Path.of(System.getProperty("user.home"), "fxt-update-debug.log");
            writeDebugLog(debugLog, "=== UPDATE PROCESS STARTED ===");
            writeDebugLog(debugLog, "Timestamp: " + java.time.LocalDateTime.now());
            writeDebugLog(debugLog, "Java temp dir: " + System.getProperty("java.io.tmpdir"));
            writeDebugLog(debugLog, "User home: " + System.getProperty("user.home"));
            writeDebugLog(debugLog, "Working dir: " + System.getProperty("user.dir"));
            writeDebugLog(debugLog, "Update info: " + updateInfo);

            try {
                // Stage 1: Preparing
                reportProgress(progressCallback, UpdateStage.PREPARING, 0, -1,
                        "Determining download URL...");

                String downloadUrl = getDownloadUrl(updateInfo);
                writeDebugLog(debugLog, "Download URL: " + downloadUrl);
                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    return UpdateResult.failure("Could not determine download URL for this platform");
                }

                logger.info("Download URL: {}", downloadUrl);

                // Create temp directory for update
                tempDir = Files.createTempDirectory("fxt-update-");
                zipFile = tempDir.resolve("update.zip");
                writeDebugLog(debugLog, "Temp directory created: " + tempDir);
                writeDebugLog(debugLog, "Zip file path: " + zipFile);

                // Stage 2: Downloading
                reportProgress(progressCallback, UpdateStage.DOWNLOADING, 0, -1,
                        "Starting download...");
                writeDebugLog(debugLog, "Starting download...");

                if (cancelRequested.get()) {
                    writeDebugLog(debugLog, "CANCELLED: User cancelled before download");
                    return UpdateResult.failure("Update cancelled by user");
                }

                downloadFile(downloadUrl, zipFile, progressCallback);
                writeDebugLog(debugLog, "Download completed. File size: " + Files.size(zipFile) + " bytes");

                if (cancelRequested.get()) {
                    writeDebugLog(debugLog, "CANCELLED: User cancelled after download");
                    return UpdateResult.failure("Update cancelled by user");
                }

                // Stage 3: Extracting
                reportProgress(progressCallback, UpdateStage.EXTRACTING, 0, -1,
                        "Extracting update...");
                writeDebugLog(debugLog, "Starting extraction...");

                Path extractedDir = tempDir.resolve("extracted");
                Files.createDirectories(extractedDir);
                extractZip(zipFile, extractedDir);
                writeDebugLog(debugLog, "Extraction completed to: " + extractedDir);

                // List extracted contents
                try (var stream = Files.walk(extractedDir, 2)) {
                    writeDebugLog(debugLog, "Extracted contents:");
                    stream.forEach(p -> writeDebugLog(debugLog, "  " + p));
                }

                // Delete the zip file after extraction to save space
                Files.deleteIfExists(zipFile);

                if (cancelRequested.get()) {
                    writeDebugLog(debugLog, "CANCELLED: User cancelled after extraction");
                    return UpdateResult.failure("Update cancelled by user");
                }

                // Stage 4: Launching updater
                reportProgress(progressCallback, UpdateStage.LAUNCHING_UPDATER, 0, -1,
                        "Preparing to install update...");
                writeDebugLog(debugLog, "=== LAUNCHING UPDATER ===");

                boolean updaterLaunched = launchUpdater(extractedDir, debugLog);
                writeDebugLog(debugLog, "Updater launch result: " + updaterLaunched);

                if (!updaterLaunched) {
                    writeDebugLog(debugLog, "ERROR: Failed to launch updater script");
                    return UpdateResult.failure("Failed to launch updater script");
                }

                // Stage 5: Completed
                reportProgress(progressCallback, UpdateStage.COMPLETED, 0, -1,
                        "Update ready. Application will restart...");
                writeDebugLog(debugLog, "=== UPDATE PROCESS COMPLETED SUCCESSFULLY ===");
                writeDebugLog(debugLog, "Application should exit now and updater script should take over.");

                return UpdateResult.success(extractedDir);

            } catch (IOException e) {
                logger.error("Update failed", e);
                writeDebugLog(debugLog, "ERROR (IOException): " + e.getMessage());
                writeDebugLog(debugLog, "Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
                reportProgress(progressCallback, UpdateStage.FAILED, 0, -1,
                        "Update failed: " + e.getMessage());
                return UpdateResult.failure("Download failed: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error during update", e);
                writeDebugLog(debugLog, "ERROR (Exception): " + e.getMessage());
                writeDebugLog(debugLog, "Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
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
     * Writes a debug message to the log file.
     */
    private void writeDebugLog(Path logFile, String message) {
        try {
            String line = java.time.LocalDateTime.now().toString() + " | " + message + System.lineSeparator();
            Files.writeString(logFile, line,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warn("Failed to write debug log: {}", e.getMessage());
        }
    }

    /**
     * Downloads a file from the given URL with progress reporting.
     * Uses the application's proxy settings via ConnectionService.
     */
    private void downloadFile(String urlString, Path destination,
                              Consumer<UpdateProgress> progressCallback) throws IOException {

        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(urlString);

            // Resolve proxy from application settings (manual/system/none)
            Proxy proxy = null;
            try {
                ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);
                proxy = connectionService.resolveProxy();
                logger.debug("Resolved proxy for update download: {}", proxy);
            } catch (Exception e) {
                logger.warn("Could not resolve proxy from settings, falling back to ProxySelector: {}", e.getMessage());
            }

            // Open connection using resolved proxy
            if (proxy != null) {
                connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
                logger.debug("Update download using proxy: {}", proxy);
            } else {
                // null means delegate to Java's ProxySelector (PAC/WPAD)
                connection = (HttpURLConnection) uri.toURL().openConnection();
                logger.debug("Update download using Java ProxySelector (PAC/WPAD delegation)");
            }
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
     * Launches the platform-specific update helper.
     *
     * @param extractedDir Directory containing the extracted update
     * @param debugLog Path to debug log file
     * @return true if the helper was launched successfully
     */
    private boolean launchUpdater(Path extractedDir, Path debugLog) {
        if (isWindows()) {
            return launchUpdaterWindowsRust(extractedDir, debugLog);
        } else {
            return launchUpdaterPosix(extractedDir, debugLog);
        }
    }

    /**
     * New Windows updater path (since v1.9.0): copies the installed Rust helper
     * to %TEMP% and launches it from there with a TOML config file.
     *
     * @param extractedDir directory containing the extracted update payload
     * @param debugLog debug log file path
     * @return true if the helper was launched successfully
     */
    private boolean launchUpdaterWindowsRust(Path extractedDir, Path debugLog) {
        try {
            Path appDir = getApplicationDirectory();
            Path launcher = getApplicationLauncher();

            // 1. Find the helper. Prefer installed copy, fall back to extracted.
            Path installedHelper = appDir.resolve("fxt-update-helper.exe");
            Path extractedHelper = extractedDir.resolve("FreeXmlToolkit").resolve("fxt-update-helper.exe");
            Path sourceHelper;
            if (Files.exists(installedHelper)) {
                sourceHelper = installedHelper;
                writeDebugLog(debugLog, "Helper source (installed): " + sourceHelper);
            } else if (Files.exists(extractedHelper)) {
                sourceHelper = extractedHelper;
                writeDebugLog(debugLog, "Helper source (extracted fallback): " + sourceHelper);
            } else {
                writeDebugLog(debugLog, "ERROR: No helper found in install or extracted location");
                return false;
            }

            // 2. Copy helper to %TEMP% so its install-dir copy is free for overwrite
            Path tempHelper = Files.createTempFile("fxt-helper-", ".exe");
            Files.copy(sourceHelper, tempHelper, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            writeDebugLog(debugLog, "Helper copied to temp: " + tempHelper);

            // 3. Write TOML config
            Path configFile = extractedDir.resolve("helper-config.toml");
            Path helperLog = Path.of(System.getProperty("user.home"), "fxt-update-helper.log");
            writeRustHelperConfig(configFile, extractedDir, appDir, launcher, helperLog);
            writeDebugLog(debugLog, "Helper config written: " + configFile);

            // 4. Launch (UAC if needed)
            boolean writable = isAppDirectoryWritable(appDir);
            writeDebugLog(debugLog, "App directory writable without elevation: " + writable);

            ProcessBuilder pb;
            if (writable) {
                pb = new ProcessBuilder(tempHelper.toString(), configFile.toString());
                writeDebugLog(debugLog, "Launching Rust helper directly (no elevation)");
            } else {
                String command = "Start-Process -FilePath '" + tempHelper +
                    "' -ArgumentList '" + configFile + "' -Verb RunAs";
                pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command);
                writeDebugLog(debugLog, "Launching Rust helper via PowerShell elevation: " + command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            writeDebugLog(debugLog, "Helper process started with PID: " + process.pid());
            return true;

        } catch (IOException e) {
            writeDebugLog(debugLog, "ERROR (IOException) in launchUpdaterWindowsRust: " + e.getMessage());
            logger.error("Failed to launch Rust update helper", e);
            return false;
        }
    }

    /**
     * Writes a TOML config file consumable by the Rust helper.
     * See spec section 4.4 for schema.
     */
    private void writeRustHelperConfig(Path configFile, Path extractedDir, Path appDir,
                                        Path launcher, Path helperLog) throws IOException {
        long parentPid = ProcessHandle.current().pid();
        long parentCreationTime = currentProcessFiletime();
        String oldVersion = currentVersionString();
        String newVersion = "unknown"; // populated below if available

        StringBuilder sb = new StringBuilder();
        sb.append("schema_version = 1\n");
        sb.append("parent_pid = ").append(parentPid).append("\n");
        sb.append("parent_creation_time = ").append(parentCreationTime).append("\n");
        sb.append("extracted_dir = '").append(extractedDir.toAbsolutePath()).append("'\n");
        sb.append("install_dir = '").append(appDir.toAbsolutePath()).append("'\n");
        sb.append("launcher_path = '").append(launcher.toAbsolutePath()).append("'\n");
        sb.append("log_path = '").append(helperLog.toAbsolutePath()).append("'\n");
        sb.append("old_version = \"").append(oldVersion).append("\"\n");
        sb.append("new_version = \"").append(newVersion).append("\"\n");

        Files.writeString(configFile, sb.toString());
    }

    /**
     * Returns the current process's creation time as a Win32 FILETIME (100-ns
     * intervals since 1601-01-01 UTC). On non-Windows, returns 0.
     */
    private long currentProcessFiletime() {
        java.time.Instant start = ProcessHandle.current().info().startInstant().orElse(null);
        if (start == null) {
            return 0L;
        }
        // Convert UNIX epoch instant to Win32 FILETIME:
        // FILETIME epoch = 1601-01-01, in 100-ns ticks.
        // Difference between 1601 and 1970 = 11644473600 seconds.
        long unixSeconds = start.getEpochSecond();
        long unixNanos = start.getNano();
        long ticksSince1601 = (unixSeconds + 11644473600L) * 10_000_000L
            + unixNanos / 100;
        return ticksSince1601;
    }

    private String currentVersionString() {
        try {
            Package pkg = AutoUpdateServiceImpl.class.getPackage();
            String v = pkg != null ? pkg.getImplementationVersion() : null;
            return v != null ? v : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Mac/Linux updater path: copies extracted update directly over the install
     * directory and starts the new launcher. Works because POSIX inode-replace
     * allows overwriting files of running processes.
     */
    private boolean launchUpdaterPosix(Path extractedDir, Path debugLog) {
        try {
            Path appDir = getApplicationDirectory();
            Path launcher = getApplicationLauncher();

            // Locate the source dir (top-level "FreeXmlToolkit" inside extract).
            Path source = extractedDir.resolve("FreeXmlToolkit");
            if (!Files.exists(source)) {
                writeDebugLog(debugLog, "ERROR: extracted source not found: " + source);
                return false;
            }

            writeDebugLog(debugLog, "POSIX in-process copy: " + source + " -> " + appDir);
            copyTreeReplaceExisting(source, appDir);

            writeDebugLog(debugLog, "Launching new app: " + launcher);
            new ProcessBuilder(launcher.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            return true;

        } catch (IOException e) {
            writeDebugLog(debugLog, "ERROR in launchUpdaterPosix: " + e.getMessage());
            logger.error("POSIX updater failed", e);
            return false;
        }
    }

    private void copyTreeReplaceExisting(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path rel = source.relativize(path);
                    Path dest = target.resolve(rel);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(path, dest,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioCause) {
                throw ioCause; // NOPMD - intentionally unwrap and rethrow the original IOException (keeps its own trace)
            }
            throw e;
        }
    }

    /**
     * Checks whether the application directory is writable by the current user.
     *
     * <p>Uses a probe-file approach instead of {@link Files#isWritable(Path)} because
     * the latter is unreliable on Windows with UAC virtualisation enabled.
     *
     * @param appDir the application installation directory
     * @return true if the directory is writable without elevation
     */
    boolean isAppDirectoryWritable(Path appDir) {
        try {
            Path probe = appDir.resolve(".fxt-write-probe-" + ProcessHandle.current().pid());
            Files.createFile(probe);
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
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
