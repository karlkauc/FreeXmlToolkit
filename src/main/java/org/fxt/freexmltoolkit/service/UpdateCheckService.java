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

import java.util.concurrent.CompletableFuture;

/**
 * Service for checking application updates against GitHub Releases.
 *
 * <p>This service queries the GitHub Releases API to determine if a newer version
 * of FreeXmlToolkit is available. The check runs asynchronously to avoid blocking
 * the UI thread during application startup.
 *
 * @since 2.0
 */
public interface UpdateCheckService {

    /**
     * Checks for updates asynchronously against GitHub Releases API.
     *
     * <p>This method fetches the latest release information from GitHub and compares
     * it with the current application version. The operation runs on a background
     * thread and returns a CompletableFuture that completes with the update information.
     *
     * <p>Network errors are handled gracefully - the returned UpdateInfo will indicate
     * no update is available if an error occurs.
     *
     * @return a CompletableFuture containing UpdateInfo with version comparison results
     */
    CompletableFuture<UpdateInfo> checkForUpdates();

    /**
     * Returns whether automatic update checking is enabled.
     *
     * @return true if update checking is enabled in settings, false otherwise
     */
    boolean isUpdateCheckEnabled();

    /**
     * Enables or disables automatic update checking.
     *
     * @param enabled true to enable update checking, false to disable
     */
    void setUpdateCheckEnabled(boolean enabled);

    /**
     * Returns the version that the user has chosen to skip.
     *
     * <p>When a user clicks "Skip This Version" in the update dialog, this
     * version is stored and the dialog won't be shown again for this specific version.
     *
     * @return the skipped version string, or null if no version has been skipped
     */
    String getSkippedVersion();

    /**
     * Sets the version to skip for update notifications.
     *
     * @param version the version string to skip, or null to clear the skipped version
     */
    void setSkippedVersion(String version);

    /**
     * Returns the current application version.
     *
     * @return the current version string
     */
    String getCurrentVersion();

    /**
     * Shuts down the update check service and releases all resources.
     *
     * <p>This method should be called when the application is closing to ensure
     * that all background threads are properly terminated.
     */
    void shutdown();
}
