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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.fxt.freexmltoolkit.domain.GitHubRelease;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Coordinates downloads of FundsXML content from GitHub into the local cache.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Talk to {@link GitHubReleaseClient} to discover releases.</li>
 *   <li>Download the schema zipball for a release and unpack relevant files into
 *       {@code ~/.freeXmlToolkit/fundsxml/schema/&lt;version&gt;/}.</li>
 *   <li>Download the examples repo (latest commit) and split it into the
 *       {@code examples/}, {@code schematron/}, and {@code queries/} sub-caches based
 *       on file extension.</li>
 *   <li>Maintain {@code metadata.json} (last-updated timestamps, active version,
 *       latest-known tag).</li>
 * </ul>
 *
 * <p>The service is a singleton in production, but tests should construct it directly
 * with mock collaborators.
 */
public class FundsXmlExtensionService {

    private static final Logger logger = LogManager.getLogger(FundsXmlExtensionService.class);

    public static final String SCHEMA_REPO = "fundsxml/schema";
    public static final String EXAMPLES_REPO = "fundsxml/examples";

    private static FundsXmlExtensionService instance;

    private final FundsXmlCache cache;
    private final GitHubReleaseClient client;
    private final FundsXmlPostDownloadRegistrar registrar;

    public FundsXmlExtensionService(FundsXmlCache cache,
                                    GitHubReleaseClient client,
                                    FundsXmlPostDownloadRegistrar registrar) {
        this.cache = Objects.requireNonNull(cache);
        this.client = Objects.requireNonNull(client);
        this.registrar = Objects.requireNonNull(registrar);
    }

    /** Convenience overload for tests that do not exercise favorites/snippets. */
    public FundsXmlExtensionService(FundsXmlCache cache, GitHubReleaseClient client) {
        this(cache, client, FundsXmlPostDownloadRegistrar.disabled());
    }

    public static synchronized FundsXmlExtensionService getInstance() {
        if (instance == null) {
            instance = new FundsXmlExtensionService(
                    FundsXmlCache.getInstance(),
                    new GitHubReleaseClient(),
                    new FundsXmlPostDownloadRegistrar());
        }
        return instance;
    }

    static synchronized void resetForTesting() {
        instance = null;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Lists all schema releases known to GitHub. Empty list on error.
     */
    public List<GitHubRelease> getAvailableReleases() {
        return client.listReleases(SCHEMA_REPO);
    }

    /**
     * Versions currently installed on disk (mirrors {@link FundsXmlCache#listInstalledVersions()}).
     */
    public List<String> getInstalledVersions() {
        return cache.listInstalledVersions();
    }

    /**
     * Returns the schema file (FundsXML4.xsd) for the active version, or {@code null}.
     */
    public Path getActiveSchemaPath() {
        return cache.getActiveSchemaFile();
    }

    /**
     * Sets the active version and persists it. No-op if the version is not installed.
     */
    public boolean setActiveVersion(String version) {
        if (version == null || !cache.listInstalledVersions().contains(version)) {
            return false;
        }
        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setActiveSchemaVersion(version);
        cache.saveMetadata(meta);
        return true;
    }

    /**
     * Returns the latest schema release tag if it is newer than anything installed,
     * else {@code null}. Also updates {@code metadata.lastUpdateCheck} and
     * {@code latestKnownSchemaTag}.
     */
    public GitHubRelease checkForUpdates() {
        GitHubRelease latest = client.getLatestRelease(SCHEMA_REPO);
        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setLastUpdateCheck(Instant.now().toString());
        if (latest != null) {
            meta.setLatestKnownSchemaTag(latest.tagName());
        }
        cache.saveMetadata(meta);
        if (latest == null) {
            return null;
        }
        String latestVersion = latest.normalizedVersion();
        List<String> installed = cache.listInstalledVersions();
        if (installed.contains(latestVersion)) {
            return null;
        }
        // No installed versions yet → any release counts as "new"
        if (installed.isEmpty()) {
            return latest;
        }
        // Newer (descending sort puts newest first)
        String newest = installed.get(0);
        return FundsXmlCache.compareVersionsDesc(latestVersion, newest) < 0 ? latest : null;
    }

    /**
     * One-shot "Download / Update" flow. Pulls the latest schema release and the
     * examples repo's main branch into the cache, registers timestamps in metadata,
     * and (if no active version is set yet) marks the freshly downloaded one as active.
     *
     * @param callback progress callback ({@code null} → {@link DownloadProgressCallback#NO_OP})
     * @return summary describing what happened
     */
    public DownloadResult downloadOrUpdate(DownloadProgressCallback callback) {
        DownloadProgressCallback cb = callback == null ? DownloadProgressCallback.NO_OP : callback;
        DownloadResult.Builder result = DownloadResult.builder();

        cb.onProgress("Fetching release info", -1, -1, "Querying GitHub for the latest FundsXML schema release");
        GitHubRelease latest = client.getLatestRelease(SCHEMA_REPO);
        if (latest == null) {
            result.error("Could not fetch latest schema release from GitHub.");
            return result.build();
        }
        result.latestRelease(latest);

        String version = latest.normalizedVersion();
        boolean alreadyInstalled = cache.listInstalledVersions().contains(version);

        if (!alreadyInstalled) {
            try {
                downloadSchemaRelease(latest, version, cb);
                result.schemaVersion(version);
                result.schemaDownloaded(true);
            } catch (IOException e) {
                logger.error("Schema download failed for {}", version, e);
                result.error("Schema download failed: " + e.getMessage());
                return result.build();
            }
        } else {
            logger.info("Schema version {} already installed; skipping download", version);
            result.schemaVersion(version);
        }

        try {
            downloadExamplesRepo(cb, result);
        } catch (IOException e) {
            logger.error("Examples download failed", e);
            result.error("Examples download failed: " + e.getMessage());
            return result.build();
        }

        // Register downloaded files with favorites / snippets subsystems
        cb.onProgress("Registering favorites", -1, -1, "Adding examples to favorites");
        FundsXmlPostDownloadRegistrar.RegistrarResult favResult =
                registrar.registerFavorites(cache.getExamplesDir(), cache.getSchematronDir());
        cb.onProgress("Seeding snippets", -1, -1, "Importing XPath/XQuery snippets");
        FundsXmlPostDownloadRegistrar.RegistrarResult snipResult =
                registrar.seedSnippets(cache.getQueriesDir());

        // Active version may have just been set, but the metadata save below also covers
        // the first-install case. Resolve schema for whichever version is now active.
        FundsXmlMetadata metaForActive = cache.loadMetadata();
        String activeVersion = metaForActive.getActiveSchemaVersion();
        if (activeVersion == null || activeVersion.isBlank()) {
            activeVersion = version;
        }
        Path activeSchema = cache.getSchemaFile(activeVersion);
        cb.onProgress("Registering schema favorite", -1, -1, "Adding active schema to favorites");
        FundsXmlPostDownloadRegistrar.RegistrarResult schemaResult =
                registrar.registerSchemaFavorite(activeSchema);
        cb.onProgress("Registering XSLT favorites", -1, -1, "Indexing FundsXML XSLT stylesheets");
        FundsXmlPostDownloadRegistrar.RegistrarResult xsltResult =
                registrar.registerXsltFavorites(cache.getExamplesDir());
        cb.onProgress("Registering featured samples", -1, -1, "Surfacing the most compact samples");
        FundsXmlPostDownloadRegistrar.RegistrarResult featuredResult =
                registrar.registerFeaturedXmlFavorites(cache.getExamplesDir());
        cb.onProgress("Seeding new-document templates", -1, -1,
                "Adding samples to the template library");
        FundsXmlPostDownloadRegistrar.RegistrarResult tmplResult =
                registrar.registerXmlTemplates(cache.getExamplesDir());

        int totalFavoritesAdded = (favResult.examplesFolderAdded() ? 1 : 0)
                + favResult.schematronFilesAdded()
                + (schemaResult.schemaFavoriteAdded() ? 1 : 0)
                + xsltResult.xsltFilesAdded()
                + featuredResult.featuredXmlAdded();
        result.favoritesAdded(totalFavoritesAdded);
        result.snippetsAdded(snipResult.snippetsAdded());
        result.schemaFavoriteAdded(schemaResult.schemaFavoriteAdded());
        result.xsltFavoritesAdded(xsltResult.xsltFilesAdded());
        result.featuredXmlFavoritesAdded(featuredResult.featuredXmlAdded());
        result.templatesAdded(tmplResult.templatesAdded());

        FundsXmlMetadata meta = cache.loadMetadata();
        String now = Instant.now().toString();
        meta.setSchemaLastUpdated(now);
        meta.setExamplesLastUpdated(now);
        meta.setSchematronLastUpdated(now);
        meta.setQueriesLastUpdated(now);
        meta.setLastUpdateCheck(now);
        meta.setLatestKnownSchemaTag(latest.tagName());
        if (meta.getActiveSchemaVersion() == null || meta.getActiveSchemaVersion().isBlank()) {
            meta.setActiveSchemaVersion(version);
        }
        cache.saveMetadata(meta);

        cb.onProgress("Done", -1, -1, "FundsXML content updated.");
        return result.build();
    }

    // ---------------------------------------------------------------------
    // Internal: schema release download
    // ---------------------------------------------------------------------

    private void downloadSchemaRelease(GitHubRelease release, String version,
                                       DownloadProgressCallback cb) throws IOException {
        Path tempDir = Files.createTempDirectory("fxt-fundsxml-schema-");
        Path zip = tempDir.resolve("schema.zip");
        try {
            cb.onProgress("Downloading schema " + version, 0, -1,
                    "Downloading schema release " + release.tagName());
            client.downloadZipball(release, zip, cb);

            cb.onProgress("Extracting schema", -1, -1, "Extracting schema archive");
            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            extractZip(zip, extractDir);

            Path versionDir = cache.getSchemaVersionDir(version);
            copySchemaFiles(extractDir, versionDir);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    /**
     * Copies XSD-related files from the extracted zipball root into the target version
     * directory. We keep this loose: any {@code *.xsd} file or any path containing
     * {@code include_files/} is copied, preserving the directory structure relative to
     * the zipball's single root folder.
     */
    private void copySchemaFiles(Path extractedRoot, Path versionDir) throws IOException {
        Path zipRoot = singleRootDir(extractedRoot);
        try (Stream<Path> walk = Files.walk(zipRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> isSchemaFile(p))
                    .forEach(p -> copyPreservingTree(zipRoot, p, versionDir));
        }
    }

    private static boolean isSchemaFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xsd") || name.endsWith(".dtd")) {
            return true;
        }
        // Include resource files often shipped alongside the schema (e.g. catalog.xml)
        return name.equals("catalog.xml");
    }

    // ---------------------------------------------------------------------
    // Internal: examples repo download
    // ---------------------------------------------------------------------

    private void downloadExamplesRepo(DownloadProgressCallback cb, DownloadResult.Builder result) throws IOException {
        cb.onProgress("Fetching examples", -1, -1, "Querying GitHub for the latest examples release");

        GitHubRelease latestExamples = null;
        try {
            latestExamples = client.getLatestRelease(EXAMPLES_REPO);
        } catch (Exception ignored) {
            // The examples repo may not publish releases — fall back to main branch zip
        }

        Path tempDir = Files.createTempDirectory("fxt-fundsxml-examples-");
        Path zip = tempDir.resolve("examples.zip");
        try {
            if (latestExamples != null && latestExamples.zipballUrl() != null) {
                client.downloadZipball(latestExamples, zip, cb);
            } else {
                // Fall back to the default branch tarball: zipball with "refs/heads/main"
                String fallbackUrl = "https://api.github.com/repos/" + EXAMPLES_REPO + "/zipball/main";
                client.downloadFile(java.net.URI.create(fallbackUrl), zip, cb, "Downloading examples");
            }

            cb.onProgress("Extracting examples", -1, -1, "Extracting examples archive");
            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            extractZip(zip, extractDir);

            Path zipRoot = singleRootDir(extractDir);
            ExampleCounts counts = new ExampleCounts();
            try (Stream<Path> walk = Files.walk(zipRoot)) {
                walk.filter(Files::isRegularFile)
                        .forEach(p -> sortExampleFile(zipRoot, p, counts));
            }
            result.examplesDownloaded(counts.xmlFiles);
            result.schematronDownloaded(counts.schematronFiles);
            result.queriesDownloaded(counts.queryFiles);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private void sortExampleFile(Path zipRoot, Path file, ExampleCounts counts) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Path target;
        if (name.endsWith(".xml") || name.endsWith(".xsl") || name.endsWith(".xslt")) {
            target = cache.getExamplesDir();
            counts.xmlFiles++;
        } else if (name.endsWith(".sch") || name.endsWith(".scm")) {
            target = cache.getSchematronDir();
            counts.schematronFiles++;
        } else if (name.endsWith(".xpath") || name.endsWith(".xq") || name.endsWith(".xquery")
                || name.equals("index.json")) {
            target = cache.getQueriesDir();
            counts.queryFiles++;
        } else {
            return;
        }
        copyPreservingTree(zipRoot, file, target);
    }

    private static class ExampleCounts {
        int xmlFiles;
        int schematronFiles;
        int queryFiles;
    }

    // ---------------------------------------------------------------------
    // Internal: extraction & file helpers
    // ---------------------------------------------------------------------

    static void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Invalid zip entry: " + entry.getName()); // zip-slip guard
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        zis.transferTo(out);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * GitHub zipballs put everything under a single top-level folder named
     * {@code <owner>-<repo>-<sha>}. Returns that folder, or {@code extractDir} itself
     * if there is no single root.
     */
    static Path singleRootDir(Path extractDir) throws IOException {
        try (Stream<Path> top = Files.list(extractDir)) {
            List<Path> dirs = top.filter(Files::isDirectory).toList();
            return dirs.size() == 1 ? dirs.get(0) : extractDir;
        }
    }

    private static void copyPreservingTree(Path zipRoot, Path sourceFile, Path targetRoot) {
        try {
            Path relative = zipRoot.relativize(sourceFile);
            Path dest = targetRoot.resolve(relative.toString());
            Files.createDirectories(dest.getParent() == null ? targetRoot : dest.getParent());
            Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warn("Failed to copy {} → {}: {}", sourceFile, targetRoot, e.getMessage());
        }
    }

    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            logger.debug("Failed to clean temp dir {}: {}", dir, e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Download result
    // ---------------------------------------------------------------------

    /** Summary of one {@link #downloadOrUpdate} invocation. */
    public static final class DownloadResult {
        private final GitHubRelease latestRelease;
        private final String schemaVersion;
        private final boolean schemaDownloaded;
        private final int examplesDownloaded;
        private final int schematronDownloaded;
        private final int queriesDownloaded;
        private final int favoritesAdded;
        private final int snippetsAdded;
        private final boolean schemaFavoriteAdded;
        private final int xsltFavoritesAdded;
        private final int featuredXmlFavoritesAdded;
        private final int templatesAdded;
        private final String error;

        private DownloadResult(Builder b) {
            this.latestRelease = b.latestRelease;
            this.schemaVersion = b.schemaVersion;
            this.schemaDownloaded = b.schemaDownloaded;
            this.examplesDownloaded = b.examplesDownloaded;
            this.schematronDownloaded = b.schematronDownloaded;
            this.queriesDownloaded = b.queriesDownloaded;
            this.favoritesAdded = b.favoritesAdded;
            this.snippetsAdded = b.snippetsAdded;
            this.schemaFavoriteAdded = b.schemaFavoriteAdded;
            this.xsltFavoritesAdded = b.xsltFavoritesAdded;
            this.featuredXmlFavoritesAdded = b.featuredXmlFavoritesAdded;
            this.templatesAdded = b.templatesAdded;
            this.error = b.error;
        }

        public GitHubRelease latestRelease() { return latestRelease; }
        public String schemaVersion() { return schemaVersion; }
        public boolean schemaDownloaded() { return schemaDownloaded; }
        public int examplesDownloaded() { return examplesDownloaded; }
        public int schematronDownloaded() { return schematronDownloaded; }
        public int queriesDownloaded() { return queriesDownloaded; }
        public int favoritesAdded() { return favoritesAdded; }
        public int snippetsAdded() { return snippetsAdded; }
        public boolean schemaFavoriteAdded() { return schemaFavoriteAdded; }
        public int xsltFavoritesAdded() { return xsltFavoritesAdded; }
        public int featuredXmlFavoritesAdded() { return featuredXmlFavoritesAdded; }
        public int templatesAdded() { return templatesAdded; }
        public String error() { return error; }
        public boolean isSuccess() { return error == null; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private GitHubRelease latestRelease;
            private String schemaVersion;
            private boolean schemaDownloaded;
            private int examplesDownloaded;
            private int schematronDownloaded;
            private int queriesDownloaded;
            private int favoritesAdded;
            private int snippetsAdded;
            private boolean schemaFavoriteAdded;
            private int xsltFavoritesAdded;
            private int featuredXmlFavoritesAdded;
            private int templatesAdded;
            private String error;

            public Builder latestRelease(GitHubRelease r) { this.latestRelease = r; return this; }
            public Builder schemaVersion(String v) { this.schemaVersion = v; return this; }
            public Builder schemaDownloaded(boolean v) { this.schemaDownloaded = v; return this; }
            public Builder examplesDownloaded(int n) { this.examplesDownloaded = n; return this; }
            public Builder schematronDownloaded(int n) { this.schematronDownloaded = n; return this; }
            public Builder queriesDownloaded(int n) { this.queriesDownloaded = n; return this; }
            public Builder favoritesAdded(int n) { this.favoritesAdded = n; return this; }
            public Builder snippetsAdded(int n) { this.snippetsAdded = n; return this; }
            public Builder schemaFavoriteAdded(boolean v) { this.schemaFavoriteAdded = v; return this; }
            public Builder xsltFavoritesAdded(int n) { this.xsltFavoritesAdded = n; return this; }
            public Builder featuredXmlFavoritesAdded(int n) { this.featuredXmlFavoritesAdded = n; return this; }
            public Builder templatesAdded(int n) { this.templatesAdded = n; return this; }
            public Builder error(String msg) { this.error = msg; return this; }
            public DownloadResult build() { return new DownloadResult(this); }
        }
    }
}
