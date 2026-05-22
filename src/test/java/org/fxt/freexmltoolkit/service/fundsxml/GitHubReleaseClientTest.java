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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GitHubReleaseClient")
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

    @Nested
    @DisplayName("Binary download (HTTP)")
    class BinaryDownload {

        @Test
        @DisplayName("Sends Accept: */* — regression for HTTP 415 on GitHub /zipball")
        void usesWildcardAccept(@TempDir Path tmp) throws IOException {
            FakeConnection conn = new FakeConnection(200, "schema-bytes".getBytes(StandardCharsets.UTF_8));
            GitHubReleaseClient client = new GitHubReleaseClient(uri -> conn);

            client.downloadFile(URI.create("https://api.github.com/repos/x/y/zipball/1.0"),
                    tmp.resolve("out.zip"), null, "download");

            assertEquals("*/*", conn.requestProperties.get("Accept"),
                    "GitHub's /zipball/{ref} endpoint returns HTTP 415 for application/octet-stream");
            assertEquals("FreeXmlToolkit-FundsXML", conn.requestProperties.get("User-Agent"));
        }

        @Test
        @DisplayName("Writes the response body to disk verbatim")
        void writesBytesToDisk(@TempDir Path tmp) throws IOException {
            byte[] payload = {'P', 'K', 0x03, 0x04, 0x14, 0x00};
            FakeConnection conn = new FakeConnection(200, payload);
            GitHubReleaseClient client = new GitHubReleaseClient(uri -> conn);

            Path out = tmp.resolve("nested/out.zip");
            client.downloadFile(URI.create("https://example.com/x"), out, null, "stage");

            assertArrayEquals(payload, Files.readAllBytes(out));
        }

        @Test
        @DisplayName("Invokes the progress callback with the final byte count")
        void invokesProgressCallback(@TempDir Path tmp) throws IOException {
            byte[] payload = new byte[4096];
            FakeConnection conn = new FakeConnection(200, payload);
            GitHubReleaseClient client = new GitHubReleaseClient(uri -> conn);

            List<Long> progress = new ArrayList<>();
            client.downloadFile(URI.create("https://example.com/x"),
                    tmp.resolve("out.bin"),
                    (stage, bytes, total, msg) -> progress.add(bytes),
                    "download");

            assertFalse(progress.isEmpty(), "Callback must fire at least once");
            assertEquals(payload.length, progress.get(progress.size() - 1).longValue());
        }

        @Test
        @DisplayName("Follows a 302 redirect to the codeload archive host")
        void followsRedirects(@TempDir Path tmp) throws IOException {
            FakeConnection redirect = new FakeConnection(302, new byte[0]);
            redirect.headers.put("Location", "https://codeload.github.com/x/y/zip/1.0");
            byte[] payload = "final-bytes".getBytes(StandardCharsets.UTF_8);
            FakeConnection finalConn = new FakeConnection(200, payload);

            AtomicInteger calls = new AtomicInteger();
            GitHubReleaseClient client = new GitHubReleaseClient(uri ->
                    calls.getAndIncrement() == 0 ? redirect : finalConn);

            Path out = tmp.resolve("out.zip");
            client.downloadFile(URI.create("https://api.github.com/repos/x/y/zipball/1.0"),
                    out, null, "download");

            assertEquals(2, calls.get(), "Expected exactly one redirect hop");
            assertArrayEquals(payload, Files.readAllBytes(out));
        }

        @Test
        @DisplayName("Throws IOException on HTTP 415 (regression for the original failure)")
        void throwsOnHttp415(@TempDir Path tmp) {
            FakeConnection conn = new FakeConnection(415, new byte[0]);
            GitHubReleaseClient client = new GitHubReleaseClient(uri -> conn);

            URI uri = URI.create("https://api.github.com/repos/x/y/zipball/1.0");
            IOException ex = assertThrows(IOException.class, () ->
                    client.downloadFile(uri, tmp.resolve("out.zip"), null, "download"));
            assertTrue(ex.getMessage().contains("415"),
                    "Message should mention status code, was: " + ex.getMessage());
        }

        @Test
        @DisplayName("downloadZipball rejects a release without a zipball URL")
        void rejectsMissingZipballUrl(@TempDir Path tmp) {
            GitHubReleaseClient client = new GitHubReleaseClient(uri -> {
                throw new AssertionError("Should not open a connection");
            });
            GitHubRelease release = new GitHubRelease("1.0", null, null, null, null, "", null);
            assertThrows(IOException.class, () ->
                    client.downloadZipball(release, tmp.resolve("out.zip"), null));
        }
    }

    /** Fake HttpURLConnection that records request headers and serves canned bytes. */
    private static class FakeConnection extends HttpURLConnection {
        final int status;
        final byte[] body;
        final Map<String, String> requestProperties = new HashMap<>();
        final Map<String, String> headers = new HashMap<>();

        FakeConnection(int status, byte[] body) {
            super(stubUrl());
            this.status = status;
            this.body = body;
        }

        private static URL stubUrl() {
            try {
                return URI.create("http://stub.invalid/").toURL();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override public void setRequestProperty(String key, String value) {
            requestProperties.put(key, value);
        }

        @Override public int getResponseCode() { return status; }

        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }

        @Override public long getContentLengthLong() { return body.length; }

        @Override public String getHeaderField(String name) { return headers.get(name); }

        @Override public void disconnect() { /* no-op */ }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { /* no-op */ }
    }
}
