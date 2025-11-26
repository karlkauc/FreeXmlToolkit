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

package org.fxt.freexmltoolkit.domain;

/**
 * Record holding information about an available software update.
 *
 * @param currentVersion  The currently installed version of the application
 * @param latestVersion   The latest available version from GitHub Releases
 * @param releaseName     The name/title of the release
 * @param releaseNotes    The release notes (body) from GitHub, typically in Markdown format
 * @param downloadUrl     The URL to the GitHub release page for downloading
 * @param publishedDate   The date when the release was published
 * @param updateAvailable True if a newer version is available, false otherwise
 */
public record UpdateInfo(
        String currentVersion,
        String latestVersion,
        String releaseName,
        String releaseNotes,
        String downloadUrl,
        String publishedDate,
        boolean updateAvailable
) {
    /**
     * Creates an UpdateInfo indicating no update is available.
     *
     * @param currentVersion The current application version
     * @return UpdateInfo with updateAvailable set to false
     */
    public static UpdateInfo noUpdateAvailable(String currentVersion) {
        return new UpdateInfo(currentVersion, currentVersion, null, null, null, null, false);
    }

    /**
     * Creates an UpdateInfo indicating an error occurred during update check.
     *
     * @param currentVersion The current application version
     * @return UpdateInfo with updateAvailable set to false
     */
    public static UpdateInfo errorDuringCheck(String currentVersion) {
        return new UpdateInfo(currentVersion, null, null, null, null, null, false);
    }
}
