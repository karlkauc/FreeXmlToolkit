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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.fxt.freexmltoolkit.service.ConnectionService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin wrapper around the GitHub REST v3 API for the bits we need to mirror a
 * release-based content repository (schema, examples, schematron, queries).
 *
 * <p>Two surface areas:
 * <ul>
 *   <li>JSON parsing — pure static helpers so they can be unit-tested against
 *       captured response fixtures without touching the network.</li>
 *   <li>HTTP I/O — uses {@link HttpURLConnection} (to match the rest of the codebase,
 *       which intentionally avoids Apache HttpClient for corporate-proxy compatibility),
 *       and pulls the proxy from {@link ConnectionService#resolveProxy()}.</li>
 * </ul>
 *
 * <p>Tests inject a {@link HttpConnectionFactory} to simulate responses or capture
 * the requested URLs.
 */
public class GitHubReleaseClient {

    private static final Logger logger = LogManager.getLogger(GitHubReleaseClient.class);
    private static final String API_BASE = "https://api.github.com/repos/";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String USER_AGENT = "FreeXmlToolkit-FundsXML";

    /** Pluggable connection opener — production code uses {@link #openProductionConnection}. */
    @FunctionalInterface
    public interface HttpConnectionFactory {
        HttpURLConnection open(URI uri) throws IOException;
    }

    private final HttpConnectionFactory connectionFactory;

    public GitHubReleaseClient() {
        this(GitHubReleaseClient::openProductionConnection);
    }

    public GitHubReleaseClient(HttpConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Returns all releases for {@code <owner>/<repo>}, newest first as ordered by
     * the GitHub API. Returns an empty list on HTTP errors.
     */
    public List<GitHubRelease> listReleases(String repo) {
        URI uri = URI.create(API_BASE + repo + "/releases");
        try {
            String json = httpGetString(uri);
            return parseReleases(json);
        } catch (IOException e) {
            logger.warn("Failed to list releases for {}: {}", repo, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns the latest published release for the given repo, or {@code null} on error /
     * if no releases exist.
     */
    public GitHubRelease getLatestRelease(String repo) {
        URI uri = URI.create(API_BASE + repo + "/releases/latest");
        try {
            String json = httpGetString(uri);
            return parseRelease(JsonParser.parseString(json).getAsJsonObject());
        } catch (IOException e) {
            logger.warn("Failed to fetch latest release for {}: {}", repo, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Failed to parse latest release JSON for {}: {}", repo, e.getMessage());
            return null;
        }
    }

    /**
     * Downloads the source-zip artifact for a release to {@code destination}. Reports
     * progress via {@code callback}. Throws {@link IOException} on any failure so the
     * caller can decide whether to retry / surface an error dialog.
     */
    public void downloadZipball(GitHubRelease release, Path destination,
                                DownloadProgressCallback callback) throws IOException {
        if (release == null || release.zipballUrl() == null || release.zipballUrl().isBlank()) {
            throw new IOException("Release has no zipball URL");
        }
        downloadBinary(URI.create(release.zipballUrl()), destination,
                callback == null ? DownloadProgressCallback.NO_OP : callback,
                "Downloading " + release.tagName());
    }

    /**
     * Downloads an arbitrary URL (e.g. {@code raw.githubusercontent.com/...}) to disk.
     * Exposed so the extension service can pull individual files (a Schematron rule,
     * an XSD include) without going through a full release archive.
     */
    public void downloadFile(URI uri, Path destination,
                             DownloadProgressCallback callback, String stage) throws IOException {
        downloadBinary(uri, destination,
                callback == null ? DownloadProgressCallback.NO_OP : callback,
                stage == null ? "Downloading" : stage);
    }

    // ---------------------------------------------------------------------
    // JSON parsing (static — easy to unit-test)
    // ---------------------------------------------------------------------

    static List<GitHubRelease> parseReleases(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = root.getAsJsonArray();
        List<GitHubRelease> releases = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            GitHubRelease release = parseRelease(element.getAsJsonObject());
            if (release != null) {
                releases.add(release);
            }
        }
        return releases;
    }

    static GitHubRelease parseRelease(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        String tagName = getString(obj, "tag_name");
        if (tagName == null || tagName.isBlank()) {
            return null;
        }
        return new GitHubRelease(
                tagName,
                getString(obj, "name"),
                getString(obj, "body"),
                getString(obj, "html_url"),
                getString(obj, "tarball_url"),
                getString(obj, "zipball_url"),
                getString(obj, "published_at")
        );
    }

    private static String getString(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        return obj.get(field).getAsString();
    }

    // ---------------------------------------------------------------------
    // HTTP I/O
    // ---------------------------------------------------------------------

    private String httpGetString(URI uri) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = connectionFactory.open(uri);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status / 100 != 2) {
                throw new IOException("HTTP " + status + " for " + uri);
            }
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void downloadBinary(URI uri, Path destination,
                                DownloadProgressCallback callback, String stage) throws IOException {
        downloadBinary(uri, destination, callback, stage, 0);
    }

    private void downloadBinary(URI uri, Path destination,
                                DownloadProgressCallback callback,
                                String stage, int redirectDepth) throws IOException {
        if (redirectDepth > 5) {
            throw new IOException("Too many redirects (" + redirectDepth + ") for " + uri);
        }
        HttpURLConnection conn = null;
        try {
            conn = connectionFactory.open(uri);
            conn.setRequestMethod("GET");
            // GitHub's zipball/tarball endpoints reject "application/octet-stream" with HTTP 415.
            // "*/*" works for both the api.github.com redirect and the codeload.github.com download.
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();

            // Some hosts (or some proxy combos) require manual redirect handling
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER
                    || status == 307 || status == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect with empty Location header from " + uri);
                }
                logger.debug("Following redirect to {}", location);
                downloadBinary(URI.create(location), destination, callback, stage, redirectDepth + 1);
                return;
            }

            if (status / 100 != 2) {
                throw new IOException("HTTP " + status + " for " + uri);
            }

            long totalBytes = conn.getContentLengthLong();
            Files.createDirectories(destination.getParent());

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesDownloaded = 0;
                long lastReport = System.currentTimeMillis();
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytesDownloaded += read;
                    long now = System.currentTimeMillis();
                    if (now - lastReport > 100) {
                        callback.onProgress(stage, bytesDownloaded, totalBytes,
                                formatProgress(bytesDownloaded, totalBytes));
                        lastReport = now;
                    }
                }
                callback.onProgress(stage, bytesDownloaded, totalBytes,
                        formatProgress(bytesDownloaded, totalBytes));
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String formatProgress(long bytes, long total) {
        if (total <= 0) {
            return formatBytes(bytes) + " downloaded";
        }
        int pct = (int) ((bytes * 100) / total);
        return String.format("Downloaded %s of %s (%d%%)",
                formatBytes(bytes), formatBytes(total), pct);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Default connection opener — resolves proxy from {@link ConnectionService}, falls
     * back to the JVM's default {@code ProxySelector} (PAC/WPAD) when no service is
     * registered.
     */
    private static HttpURLConnection openProductionConnection(URI uri) throws IOException {
        Proxy proxy = null;
        try {
            ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);
            if (connectionService != null) {
                proxy = connectionService.resolveProxy();
            }
        } catch (Exception e) {
            logger.debug("ConnectionService unavailable; using ProxySelector default: {}", e.getMessage());
        }
        if (proxy != null) {
            return (HttpURLConnection) uri.toURL().openConnection(proxy);
        }
        return (HttpURLConnection) uri.toURL().openConnection();
    }
}
