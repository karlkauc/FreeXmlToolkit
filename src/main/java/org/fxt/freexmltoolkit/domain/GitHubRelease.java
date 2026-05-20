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

package org.fxt.freexmltoolkit.domain;

/**
 * Lightweight view of a GitHub release as returned by the GitHub REST API.
 *
 * @param tagName     The git tag for this release, e.g. {@code "v4.2.10"} or {@code "4.2.10"}.
 * @param name        Human-readable release name (may be null/blank).
 * @param body        Release notes in Markdown (may be null/blank).
 * @param htmlUrl     URL to the release page on github.com.
 * @param tarballUrl  URL to download the source code as a tar.gz archive.
 * @param zipballUrl  URL to download the source code as a zip archive.
 * @param publishedAt ISO-8601 publish timestamp, e.g. {@code "2026-01-23T10:30:00Z"}.
 */
public record GitHubRelease(
        String tagName,
        String name,
        String body,
        String htmlUrl,
        String tarballUrl,
        String zipballUrl,
        String publishedAt
) {

    /**
     * Returns the version portion of the tag with any leading "v" stripped.
     * Useful for matching against the on-disk cache directory layout
     * ({@code schema/<version>/}).
     */
    public String normalizedVersion() {
        if (tagName == null || tagName.isBlank()) {
            return "";
        }
        return tagName.startsWith("v") || tagName.startsWith("V")
                ? tagName.substring(1)
                : tagName;
    }
}
