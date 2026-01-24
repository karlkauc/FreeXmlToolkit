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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.UpdateInfo;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of UpdateCheckService that checks for updates via GitHub Releases API.
 *
 * <p>This service queries the GitHub Releases API for the latest release and compares
 * it with the current application version to determine if an update is available.
 *
 * <p>The service uses a singleton pattern and caches the update check result for the
 * duration of the application session to avoid repeated API calls.
 *
 * @since 2.0
 */
public class UpdateCheckServiceImpl implements UpdateCheckService {

    private static final Logger logger = LogManager.getLogger(UpdateCheckServiceImpl.class);

    /**
     * GitHub API endpoint for the latest release
     */
    private static final String GITHUB_RELEASES_API_URL =
            "https://api.github.com/repos/karlkauc/FreeXmlToolkit/releases/latest";

    /**
     * Property key for update check enabled setting
     */
    private static final String PROP_UPDATE_CHECK_ENABLED = "update.check.enabled";

    /**
     * Property key for skipped version
     */
    private static final String PROP_SKIPPED_VERSION = "update.skipped.version";

    /**
     * Default version when not available from manifest.
     * IMPORTANT: Keep this in sync with build.gradle.kts version when releasing!
     */
    private static final String DEFAULT_VERSION = "1.4.5";

    // Lazy-initialized services to avoid circular dependency
    private PropertiesService propertiesService;
    private ConnectionService connectionService;

    /**
     * ExecutorService for background update checks
     */
    private ExecutorService executorService;

    /**
     * Cached update info for the current session
     */
    private UpdateInfo cachedUpdateInfo = null;

    /**
     * Flag indicating if an update check has been performed in this session
     */
    private boolean updateCheckPerformed = false;

    /**
     * Holder class for lazy singleton initialization.
     * The instance is created only when getInstance() is first called.
     */
    private static class InstanceHolder {
        private static final UpdateCheckService INSTANCE = new UpdateCheckServiceImpl();
    }

    /**
     * Returns the singleton instance of UpdateCheckServiceImpl.
     * Uses lazy initialization to avoid circular dependencies during class loading.
     *
     * @return the singleton instance
     */
    public static UpdateCheckService getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Private constructor for singleton pattern.
     * Services are initialized lazily to avoid circular dependencies.
     */
    private UpdateCheckServiceImpl() {
        // Services will be initialized on first use
    }

    /**
     * Constructor for testing with dependency injection.
     *
     * @param propertiesService the properties service to use
     * @param connectionService the connection service to use
     */
    UpdateCheckServiceImpl(PropertiesService propertiesService, ConnectionService connectionService) {
        this.propertiesService = propertiesService;
        this.connectionService = connectionService;
    }

    /**
     * Lazy getter for PropertiesService to avoid circular dependency during initialization.
     *
     * @return the PropertiesService instance
     */
    private PropertiesService getPropertiesService() {
        if (propertiesService == null) {
            propertiesService = ServiceRegistry.get(PropertiesService.class);
        }
        return propertiesService;
    }

    /**
     * Lazy getter for ConnectionService to avoid circular dependency during initialization.
     *
     * @return the ConnectionService instance
     */
    private ConnectionService getConnectionService() {
        if (connectionService == null) {
            connectionService = ServiceRegistry.get(ConnectionService.class);
        }
        return connectionService;
    }

    /**
     * Lazy getter for ExecutorService to avoid initialization issues.
     *
     * @return the ExecutorService instance for background update checks
     */
    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "UpdateCheckService-Thread");
                t.setDaemon(true);
                return t;
            });
        }
        return executorService;
    }

    @Override
    public CompletableFuture<UpdateInfo> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            // Return cached result if already checked in this session
            if (updateCheckPerformed && cachedUpdateInfo != null) {
                logger.debug("Returning cached update info");
                return cachedUpdateInfo;
            }

            String currentVersion = getCurrentVersion();
            logger.info("Checking for updates. Current version: {}", currentVersion);

            try {
                // Fetch latest release from GitHub API
                var connectionResult = getConnectionService().executeHttpRequest(new URI(GITHUB_RELEASES_API_URL));

                if (connectionResult == null || connectionResult.httpStatus() != 200) {
                    logger.warn("Failed to fetch update info. HTTP Status: {}",
                            connectionResult != null ? connectionResult.httpStatus() : "null");
                    cachedUpdateInfo = UpdateInfo.errorDuringCheck(currentVersion);
                    updateCheckPerformed = true;
                    return cachedUpdateInfo;
                }

                String responseBody = connectionResult.resultBody();
                if (responseBody == null || responseBody.isBlank()) {
                    logger.warn("Empty response from GitHub API");
                    cachedUpdateInfo = UpdateInfo.errorDuringCheck(currentVersion);
                    updateCheckPerformed = true;
                    return cachedUpdateInfo;
                }

                // Parse JSON response
                JsonObject release = JsonParser.parseString(responseBody).getAsJsonObject();

                String tagName = getJsonString(release, "tag_name");
                String releaseName = getJsonString(release, "name");
                String body = getJsonString(release, "body");
                String htmlUrl = getJsonString(release, "html_url");
                String publishedAt = getJsonString(release, "published_at");

                if (tagName == null || tagName.isBlank()) {
                    logger.warn("No tag_name found in GitHub release response");
                    cachedUpdateInfo = UpdateInfo.noUpdateAvailable(currentVersion);
                    updateCheckPerformed = true;
                    return cachedUpdateInfo;
                }

                // Compare versions
                String latestVersion = normalizeVersion(tagName);
                boolean updateAvailable = isNewerVersion(latestVersion, currentVersion);

                // Check if this version was skipped by the user
                String skippedVersion = getSkippedVersion();
                if (updateAvailable && latestVersion.equals(skippedVersion)) {
                    logger.info("Update {} available but skipped by user", latestVersion);
                    updateAvailable = false;
                }

                cachedUpdateInfo = new UpdateInfo(
                        currentVersion,
                        latestVersion,
                        releaseName,
                        body,
                        htmlUrl,
                        formatPublishedDate(publishedAt),
                        updateAvailable
                );

                updateCheckPerformed = true;
                logger.info("Update check complete. Latest version: {}, Update available: {}",
                        latestVersion, updateAvailable);

                return cachedUpdateInfo;

            } catch (Exception e) {
                logger.warn("Error checking for updates: {}", e.getMessage());
                cachedUpdateInfo = UpdateInfo.errorDuringCheck(currentVersion);
                updateCheckPerformed = true;
                return cachedUpdateInfo;
            }
        }, getExecutorService());
    }

    @Override
    public boolean isUpdateCheckEnabled() {
        Properties props = getPropertiesService().loadProperties();
        return Boolean.parseBoolean(props.getProperty(PROP_UPDATE_CHECK_ENABLED, "true"));
    }

    @Override
    public void setUpdateCheckEnabled(boolean enabled) {
        Properties props = getPropertiesService().loadProperties();
        props.setProperty(PROP_UPDATE_CHECK_ENABLED, String.valueOf(enabled));
        getPropertiesService().saveProperties(props);
        logger.debug("Update check enabled set to: {}", enabled);
    }

    @Override
    public String getSkippedVersion() {
        Properties props = getPropertiesService().loadProperties();
        return props.getProperty(PROP_SKIPPED_VERSION, null);
    }

    @Override
    public void setSkippedVersion(String version) {
        Properties props = getPropertiesService().loadProperties();
        if (version == null || version.isBlank()) {
            props.remove(PROP_SKIPPED_VERSION);
        } else {
            props.setProperty(PROP_SKIPPED_VERSION, version);
        }
        getPropertiesService().saveProperties(props);
        logger.debug("Skipped version set to: {}", version);
    }

    @Override
    public String getCurrentVersion() {
        // Try to get version from manifest (set by Gradle build)
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = DEFAULT_VERSION;
        }
        return normalizeVersion(version);
    }

    /**
     * Normalizes a version string by removing the 'v' prefix if present.
     *
     * @param version the version string to normalize
     * @return the normalized version string
     */
    private String normalizeVersion(String version) {
        if (version == null) {
            return DEFAULT_VERSION;
        }
        return version.replaceFirst("^[vV]", "").trim();
    }

    /**
     * Compares two version strings and determines if the latest version is newer.
     *
     * <p>Supports semantic versioning (e.g., "1.0.0", "1.2.3", "2.0.0-beta").
     * Non-numeric suffixes like "-beta" or "-SNAPSHOT" are stripped for comparison.
     *
     * @param latest  the latest version string
     * @param current the current version string
     * @return true if latest is newer than current, false otherwise
     */
    boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null || latest.isEmpty() || current.isEmpty()) {
            return false;
        }

        // Remove non-numeric suffixes for comparison
        String v1 = latest.replaceAll("-.*$", "").trim();
        String v2 = current.replaceAll("-.*$", "").trim();

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int p1 = 0;
            int p2 = 0;

            if (i < parts1.length) {
                try {
                    p1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    p1 = 0;
                }
            }

            if (i < parts2.length) {
                try {
                    p2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    p2 = 0;
                }
            }

            if (p1 > p2) {
                return true;
            }
            if (p1 < p2) {
                return false;
            }
        }

        return false; // Versions are equal
    }

    /**
     * Safely extracts a string value from a JSON object.
     *
     * @param obj the JSON object
     * @param key the key to extract
     * @return the string value, or null if not present or null
     */
    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /**
     * Formats the published date from ISO 8601 format to a more readable format.
     *
     * @param isoDate the date in ISO 8601 format (e.g., "2024-01-15T10:30:00Z")
     * @return formatted date string (e.g., "2024-01-15")
     */
    private String formatPublishedDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        // Extract date part (YYYY-MM-DD) from ISO 8601 format
        if (isoDate.contains("T")) {
            return isoDate.substring(0, isoDate.indexOf("T"));
        }
        return isoDate;
    }

    /**
     * Resets the cached update info. Useful for testing or forcing a re-check.
     */
    void resetCache() {
        cachedUpdateInfo = null;
        updateCheckPerformed = false;
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down UpdateCheckService...");
        if (executorService != null && !executorService.isShutdown()) {
            logger.debug("Shutting down update check executor service...");
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warn("UpdateCheck ExecutorService did not terminate within 2 seconds");
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for update check executor shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
        logger.info("UpdateCheckService shutdown completed");
    }
}
