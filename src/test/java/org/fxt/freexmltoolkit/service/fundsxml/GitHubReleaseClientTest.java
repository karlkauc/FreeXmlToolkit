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

import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GitHubReleaseClient JSON parsing")
class GitHubReleaseClientTest {

    private static final String RELEASES_FIXTURE = """
            [
              {
                "tag_name": "4.2.10",
                "name": "FundsXML 4.2.10",
                "body": "Release notes for 4.2.10",
                "html_url": "https://github.com/fundsxml/schema/releases/tag/4.2.10",
                "tarball_url": "https://api.github.com/repos/fundsxml/schema/tarball/4.2.10",
                "zipball_url": "https://api.github.com/repos/fundsxml/schema/zipball/4.2.10",
                "published_at": "2026-01-23T10:30:00Z"
              },
              {
                "tag_name": "v4.2.9",
                "name": null,
                "body": null,
                "html_url": "https://github.com/fundsxml/schema/releases/tag/v4.2.9",
                "tarball_url": "https://api.github.com/repos/fundsxml/schema/tarball/v4.2.9",
                "zipball_url": "https://api.github.com/repos/fundsxml/schema/zipball/v4.2.9",
                "published_at": "2025-11-15T08:00:00Z"
              }
            ]
            """;

    private static final String SINGLE_RELEASE_FIXTURE = """
            {
              "tag_name": "4.2.10",
              "name": "FundsXML 4.2.10",
              "body": "Latest stable release",
              "html_url": "https://github.com/fundsxml/schema/releases/tag/4.2.10",
              "tarball_url": "https://api.github.com/repos/fundsxml/schema/tarball/4.2.10",
              "zipball_url": "https://api.github.com/repos/fundsxml/schema/zipball/4.2.10",
              "published_at": "2026-01-23T10:30:00Z"
            }
            """;

    @Nested
    @DisplayName("parseReleases")
    class ParseReleases {

        @Test
        @DisplayName("Parses every release in the array")
        void parsesArray() {
            List<GitHubRelease> releases = GitHubReleaseClient.parseReleases(RELEASES_FIXTURE);
            assertEquals(2, releases.size());
            assertEquals("4.2.10", releases.get(0).tagName());
            assertEquals("v4.2.9", releases.get(1).tagName());
        }

        @Test
        @DisplayName("Returns empty list for null/blank input")
        void emptyOnNullInput() {
            assertTrue(GitHubReleaseClient.parseReleases(null).isEmpty());
            assertTrue(GitHubReleaseClient.parseReleases("").isEmpty());
            assertTrue(GitHubReleaseClient.parseReleases("   ").isEmpty());
        }

        @Test
        @DisplayName("Returns empty list when JSON is an object, not an array")
        void emptyOnObjectInput() {
            assertTrue(GitHubReleaseClient.parseReleases(SINGLE_RELEASE_FIXTURE).isEmpty());
        }

        @Test
        @DisplayName("Preserves null name and body")
        void preservesNullableFields() {
            List<GitHubRelease> releases = GitHubReleaseClient.parseReleases(RELEASES_FIXTURE);
            GitHubRelease second = releases.get(1);
            assertNull(second.name());
            assertNull(second.body());
        }
    }

    @Nested
    @DisplayName("parseRelease (single object)")
    class ParseSingleRelease {

        @Test
        @DisplayName("Extracts all expected fields")
        void parsesSingleRelease() {
            GitHubRelease release = GitHubReleaseClient.parseRelease(
                    com.google.gson.JsonParser.parseString(SINGLE_RELEASE_FIXTURE).getAsJsonObject());
            assertNotNull(release);
            assertEquals("4.2.10", release.tagName());
            assertEquals("FundsXML 4.2.10", release.name());
            assertEquals("Latest stable release", release.body());
            assertEquals("https://github.com/fundsxml/schema/releases/tag/4.2.10", release.htmlUrl());
            assertEquals("https://api.github.com/repos/fundsxml/schema/tarball/4.2.10", release.tarballUrl());
            assertEquals("https://api.github.com/repos/fundsxml/schema/zipball/4.2.10", release.zipballUrl());
            assertEquals("2026-01-23T10:30:00Z", release.publishedAt());
        }

        @Test
        @DisplayName("Returns null when tag_name is missing or blank")
        void rejectsBlankTag() {
            String blankTag = "{\"tag_name\": \"\"}";
            assertNull(GitHubReleaseClient.parseRelease(
                    com.google.gson.JsonParser.parseString(blankTag).getAsJsonObject()));

            String noTag = "{\"name\": \"thing\"}";
            assertNull(GitHubReleaseClient.parseRelease(
                    com.google.gson.JsonParser.parseString(noTag).getAsJsonObject()));
        }

        @Test
        @DisplayName("Returns null on null input")
        void rejectsNull() {
            assertNull(GitHubReleaseClient.parseRelease(null));
        }
    }

    @Nested
    @DisplayName("GitHubRelease.normalizedVersion")
    class NormalizedVersion {

        @Test
        @DisplayName("Strips leading 'v'")
        void stripsV() {
            GitHubRelease r = new GitHubRelease("v4.2.10", null, null, null, null, null, null);
            assertEquals("4.2.10", r.normalizedVersion());
        }

        @Test
        @DisplayName("Strips leading 'V'")
        void stripsCapitalV() {
            GitHubRelease r = new GitHubRelease("V4.2.10", null, null, null, null, null, null);
            assertEquals("4.2.10", r.normalizedVersion());
        }

        @Test
        @DisplayName("Returns unchanged when no 'v' prefix")
        void passThrough() {
            GitHubRelease r = new GitHubRelease("4.2.10", null, null, null, null, null, null);
            assertEquals("4.2.10", r.normalizedVersion());
        }

        @Test
        @DisplayName("Returns empty for null/blank tag")
        void emptyForBlank() {
            assertEquals("", new GitHubRelease(null, null, null, null, null, null, null).normalizedVersion());
            assertEquals("", new GitHubRelease("", null, null, null, null, null, null).normalizedVersion());
        }
    }
}
