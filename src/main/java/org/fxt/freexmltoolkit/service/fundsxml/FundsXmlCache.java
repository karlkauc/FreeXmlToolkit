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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages the on-disk layout of locally cached FundsXML content.
 *
 * <p>Directory layout under {@code ~/.freeXmlToolkit/fundsxml/}:
 * <pre>
 * fundsxml/
 * ├── schema/
 * │   ├── 4.2.10/FundsXML4.xsd + include_files/...
 * │   └── 4.2.9/...
 * ├── examples/
 * ├── schematron/
 * ├── queries/
 * └── metadata.json
 * </pre>
 *
 * <p>Thread-safety: instance methods are not synchronized. Callers using the singleton
 * from background threads should serialize writes externally, or use one of the test
 * factories which return isolated instances.
 */
public class FundsXmlCache {

    private static final Logger logger = LogManager.getLogger(FundsXmlCache.class);
    private static final String METADATA_FILENAME = "metadata.json";
    private static final String SCHEMA_PRIMARY_FILE = "FundsXML4.xsd";

    private static FundsXmlCache instance;

    private final Path baseDir;
    private final Path schemaDir;
    private final Path examplesDir;
    private final Path schematronDir;
    private final Path queriesDir;
    private final Path metadataFile;
    private final Gson gson;

    /**
     * Default constructor — uses {@code ~/.freeXmlToolkit/fundsxml/}.
     */
    private FundsXmlCache() {
        this(defaultBaseDir());
    }

    /**
     * Test-friendly constructor allowing a custom base directory.
     */
    FundsXmlCache(Path baseDir) {
        this.baseDir = baseDir;
        this.schemaDir = baseDir.resolve("schema");
        this.examplesDir = baseDir.resolve("examples");
        this.schematronDir = baseDir.resolve("schematron");
        this.queriesDir = baseDir.resolve("queries");
        this.metadataFile = baseDir.resolve(METADATA_FILENAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        ensureDirectories();
    }

    private static Path defaultBaseDir() {
        return Paths.get(System.getProperty("user.home"), ".freeXmlToolkit", "fundsxml");
    }

    public static synchronized FundsXmlCache getInstance() {
        if (instance == null) {
            instance = new FundsXmlCache();
        }
        return instance;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(schemaDir);
            Files.createDirectories(examplesDir);
            Files.createDirectories(schematronDir);
            Files.createDirectories(queriesDir);
        } catch (IOException e) {
            logger.error("Failed to create FundsXML cache directories under {}", baseDir, e);
        }
    }

    // ---------------------------------------------------------------------
    // Directory accessors
    // ---------------------------------------------------------------------

    public Path getBaseDir() {
        return baseDir;
    }

    public Path getSchemaDir() {
        return schemaDir;
    }

    /**
     * Directory for a specific schema version (created if missing).
     */
    public Path getSchemaVersionDir(String version) {
        Path dir = schemaDir.resolve(version);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            logger.warn("Failed to create schema version dir {}", dir, e);
        }
        return dir;
    }

    public Path getExamplesDir() {
        return examplesDir;
    }

    public Path getSchematronDir() {
        return schematronDir;
    }

    public Path getQueriesDir() {
        return queriesDir;
    }

    // ---------------------------------------------------------------------
    // Version listing & active version
    // ---------------------------------------------------------------------

    /**
     * Lists schema versions present on disk, sorted descending (newest first by lexical
     * comparison — works correctly for semver-like {@code 4.2.10}, {@code 4.2.9} when
     * components are equal-width, otherwise see {@link #compareVersionsDesc}).
     */
    public List<String> listInstalledVersions() {
        if (!Files.isDirectory(schemaDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> entries = Files.list(schemaDir)) {
            return entries
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(FundsXmlCache::compareVersionsDesc)
                    .toList();
        } catch (IOException e) {
            logger.warn("Failed to list installed FundsXML versions", e);
            return Collections.emptyList();
        }
    }

    /**
     * Resolves the primary schema file ({@code FundsXML4.xsd}) for the given version,
     * or {@code null} if it does not exist on disk.
     */
    public Path getSchemaFile(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Path file = schemaDir.resolve(version).resolve(SCHEMA_PRIMARY_FILE);
        return Files.isRegularFile(file) ? file : null;
    }

    /**
     * Convenience: schema file for the currently active version (per metadata.json).
     */
    public Path getActiveSchemaFile() {
        FundsXmlMetadata meta = loadMetadata();
        return getSchemaFile(meta.getActiveSchemaVersion());
    }

    /**
     * Removes the given schema version directory (and all files within it). No-op if
     * the directory does not exist.
     */
    public boolean removeVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        Path dir = schemaDir.resolve(version);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try {
            deleteRecursive(dir);
            FundsXmlMetadata meta = loadMetadata();
            meta.getInstalledSchemaVersions().remove(version);
            if (version.equals(meta.getActiveSchemaVersion())) {
                List<String> remaining = listInstalledVersions();
                meta.setActiveSchemaVersion(remaining.isEmpty() ? null : remaining.get(0));
            }
            saveMetadata(meta);
            return true;
        } catch (IOException e) {
            logger.error("Failed to remove FundsXML schema version {}", version, e);
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Metadata
    // ---------------------------------------------------------------------

    /**
     * Reads metadata.json. Returns a fresh {@link FundsXmlMetadata} if the file is
     * missing or unreadable — never null.
     */
    public FundsXmlMetadata loadMetadata() {
        if (!Files.isRegularFile(metadataFile)) {
            return new FundsXmlMetadata();
        }
        try {
            String json = Files.readString(metadataFile);
            FundsXmlMetadata meta = gson.fromJson(json, FundsXmlMetadata.class);
            return meta == null ? new FundsXmlMetadata() : meta;
        } catch (IOException e) {
            logger.warn("Failed to read FundsXML metadata; returning empty", e);
            return new FundsXmlMetadata();
        }
    }

    /**
     * Writes metadata.json. Refreshes the installed-versions list from disk before
     * persisting so the file stays consistent with the filesystem state.
     */
    public void saveMetadata(FundsXmlMetadata metadata) {
        if (metadata == null) {
            return;
        }
        metadata.setInstalledSchemaVersions(listInstalledVersions());
        try {
            Files.writeString(metadataFile, gson.toJson(metadata));
        } catch (IOException e) {
            logger.error("Failed to write FundsXML metadata to {}", metadataFile, e);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Descending comparator that handles numeric semver-style components.
     * Falls back to lexical comparison for non-numeric segments.
     */
    static int compareVersionsDesc(String a, String b) {
        String[] sa = a.split("\\.");
        String[] sb = b.split("\\.");
        int n = Math.max(sa.length, sb.length);
        for (int i = 0; i < n; i++) {
            String pa = i < sa.length ? sa[i] : "0";
            String pb = i < sb.length ? sb[i] : "0";
            int cmp;
            try {
                cmp = Integer.compare(Integer.parseInt(pa), Integer.parseInt(pb));
            } catch (NumberFormatException e) {
                cmp = pa.compareTo(pb);
            }
            if (cmp != 0) {
                return -cmp; // descending
            }
        }
        return 0;
    }

    private static void deleteRecursive(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    logger.warn("Failed to delete {}", p, e);
                }
            });
        }
    }

    /**
     * Test helper — resets the singleton so the next {@link #getInstance()} call
     * re-initializes against the (possibly redirected) user.home.
     */
    static synchronized void resetForTesting() {
        instance = null;
    }
}
