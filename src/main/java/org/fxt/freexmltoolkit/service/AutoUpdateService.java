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

import org.fxt.freexmltoolkit.domain.UpdateInfo;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for downloading and applying application updates.
 *
 * <p>This service handles the automatic update process by:
 * <ol>
 *   <li>Downloading the appropriate app-image ZIP from GitHub Releases</li>
 *   <li>Extracting the ZIP to a temporary folder</li>
 *   <li>Launching an external updater script that replaces files and restarts the app</li>
 * </ol>
 *
 * <p>The update is performed using a platform-specific updater script that:
 * <ul>
 *   <li>Waits for the current application to exit</li>
 *   <li>Copies new files to the application directory</li>
 *   <li>Restarts the application</li>
 * </ul>
 *
 * @since 2.0
 */
public interface AutoUpdateService {

    /**
     * Progress information for update operations.
     *
     * @param stage           Current stage of the update process
     * @param bytesDownloaded Bytes downloaded so far (only for DOWNLOADING stage)
     * @param totalBytes      Total bytes to download (-1 if unknown)
     * @param message         Human-readable status message
     */
    record UpdateProgress(
            UpdateStage stage,
            long bytesDownloaded,
            long totalBytes,
            String message
    ) {
        /**
         * Calculates the download progress as a percentage.
         *
         * @return progress percentage (0.0 to 1.0), or -1 if unknown
         */
        public double percentage() {
            if (totalBytes <= 0 || stage != UpdateStage.DOWNLOADING) {
                return -1;
            }
            return (double) bytesDownloaded / totalBytes;
        }
    }

    /**
     * Stages of the update process.
     */
    enum UpdateStage {
        /** Determining the correct download URL */
        PREPARING,
        /** Downloading the update ZIP file */
        DOWNLOADING,
        /** Extracting the ZIP file */
        EXTRACTING,
        /** Launching the updater script */
        LAUNCHING_UPDATER,
        /** Update completed successfully */
        COMPLETED,
        /** Update failed */
        FAILED
    }

    /**
     * Result of an update operation.
     *
     * @param success      True if update was initiated successfully
     * @param errorMessage Error message if success is false
     * @param extractedDir Path to the extracted update files (if successful)
     */
    record UpdateResult(
            boolean success,
            String errorMessage,
            Path extractedDir
    ) {
        public static UpdateResult success(Path extractedDir) {
            return new UpdateResult(true, null, extractedDir);
        }

        public static UpdateResult failure(String errorMessage) {
            return new UpdateResult(false, errorMessage, null);
        }
    }

    /**
     * Downloads and applies an update asynchronously.
     *
     * <p>This method will:
     * <ol>
     *   <li>Download the appropriate app-image ZIP for the current platform</li>
     *   <li>Extract the ZIP to a temporary folder</li>
     *   <li>Launch the updater script</li>
     *   <li>Signal the caller to exit the application</li>
     * </ol>
     *
     * <p>Progress is reported via the progressCallback. The caller should update
     * the UI based on the progress information.
     *
     * @param updateInfo       Information about the available update
     * @param progressCallback Callback for progress updates (called on background thread)
     * @return CompletableFuture that completes with the update result
     */
    CompletableFuture<UpdateResult> downloadAndApplyUpdate(
            UpdateInfo updateInfo,
            Consumer<UpdateProgress> progressCallback
    );

    /**
     * Cancels any ongoing update operation.
     *
     * <p>If an update is in progress, this method will attempt to cancel it.
     * Downloaded files will be cleaned up.
     */
    void cancelUpdate();

    /**
     * Checks if an update is currently in progress.
     *
     * @return true if an update is being downloaded or applied
     */
    boolean isUpdateInProgress();

    /**
     * Returns the platform identifier for the current system.
     *
     * <p>The platform identifier is used to determine which app-image ZIP to download:
     * <ul>
     *   <li>windows-x64 - Windows 64-bit</li>
     *   <li>macos-x64 - macOS Intel</li>
     *   <li>macos-arm64 - macOS Apple Silicon</li>
     *   <li>linux-x64 - Linux 64-bit</li>
     * </ul>
     *
     * @return the platform identifier string
     */
    String getPlatformIdentifier();

    /**
     * Constructs the download URL for the app-image ZIP.
     *
     * @param updateInfo Information about the available update
     * @return the URL to download the app-image ZIP for the current platform
     */
    String getDownloadUrl(UpdateInfo updateInfo);

    /**
     * Shuts down the service and releases resources.
     */
    void shutdown();
}
