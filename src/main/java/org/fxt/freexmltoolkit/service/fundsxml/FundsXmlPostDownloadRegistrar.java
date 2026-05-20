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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Registers freshly-downloaded FundsXML content with the favorites and snippet
 * subsystems so the user can find it through the regular UI without having to
 * re-discover the cache folder.
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li><b>Favorites</b> — one folder favorite for {@code examples/}, plus individual
 *       Schematron file favorites (the favorites service only supports folder
 *       favorites for XML/XSLT, so {@code schematron/} is registered file-by-file).</li>
 *   <li><b>Snippets</b> — XPath/XQuery files in {@code queries/} become saved snippets
 *       tagged {@code fundsxml}. An optional {@code index.json} manifest in
 *       {@code queries/} can supply richer metadata (name, description, category).</li>
 * </ul>
 *
 * <p>Both steps are idempotent — re-running the download does not create duplicates.
 */
public class FundsXmlPostDownloadRegistrar {

    private static final Logger logger = LogManager.getLogger(FundsXmlPostDownloadRegistrar.class);

    /** Folder name used in {@link FavoritesService} for FundsXML entries. */
    public static final String FAVORITE_FOLDER_EXAMPLES = "FundsXML Examples";
    public static final String FAVORITE_FOLDER_SCHEMATRON = "FundsXML Schematron";

    /** Snippet tag applied to all seeded snippets. */
    public static final String SNIPPET_TAG = "fundsxml";

    private final FavoritesService favoritesService;
    private final XPathSnippetRepository snippetRepository;

    public FundsXmlPostDownloadRegistrar(FavoritesService favoritesService,
                                         XPathSnippetRepository snippetRepository) {
        this.favoritesService = favoritesService;
        this.snippetRepository = snippetRepository;
    }

    /**
     * Convenience constructor using production singletons.
     */
    public FundsXmlPostDownloadRegistrar() {
        this(FavoritesService.getInstance(), XPathSnippetRepository.getInstance());
    }

    /**
     * Returns a registrar that performs no work — useful in tests that exercise the
     * download flow without touching the global favorites/snippet stores.
     */
    public static FundsXmlPostDownloadRegistrar disabled() {
        return new FundsXmlPostDownloadRegistrar(null, null) {
            @Override
            public RegistrarResult registerFavorites(Path examplesDir, Path schematronDir) {
                return new RegistrarResult.Builder().build();
            }
            @Override
            public RegistrarResult seedSnippets(Path queriesDir) {
                return new RegistrarResult.Builder().build();
            }
        };
    }

    /**
     * Adds a folder favorite for {@code examplesDir} (XML type) and individual file
     * favorites for each {@code .sch} file in {@code schematronDir}.
     *
     * @return summary of what was added (skip-counts for existing entries)
     */
    public RegistrarResult registerFavorites(Path examplesDir, Path schematronDir) {
        RegistrarResult.Builder result = new RegistrarResult.Builder();

        if (examplesDir != null && Files.isDirectory(examplesDir)) {
            if (folderFavoriteExists(examplesDir, FileFavorite.FileType.XML)) {
                result.examplesFolderSkipped();
            } else {
                FileFavorite f = favoritesService.addFolderFavorite(
                        FAVORITE_FOLDER_EXAMPLES, examplesDir, FileFavorite.FileType.XML);
                if (f != null) {
                    result.examplesFolderAdded();
                }
            }
        }

        if (schematronDir != null && Files.isDirectory(schematronDir)) {
            try (Stream<Path> walk = Files.walk(schematronDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sch"))
                        .forEach(p -> {
                            String path = p.toAbsolutePath().toString();
                            if (favoritesService.isFavorite(path)) {
                                result.schematronFileSkipped();
                            } else {
                                String displayName = stripExtension(p.getFileName().toString());
                                favoritesService.addFavorite(path, displayName, FAVORITE_FOLDER_SCHEMATRON);
                                result.schematronFileAdded();
                            }
                        });
            } catch (IOException e) {
                logger.warn("Failed to walk schematron dir {}: {}", schematronDir, e.getMessage());
            }
        }

        return result.build();
    }

    /**
     * Seeds XPath/XQuery snippets from {@code queriesDir}. Looks for an
     * {@code index.json} manifest first; if absent, scans for individual {@code .xpath},
     * {@code .xq}, {@code .xquery} files and creates one snippet per file.
     *
     * <p>Snippets with a name that already exists (and carries the {@link #SNIPPET_TAG}
     * tag) are skipped — this keeps repeat downloads idempotent without overwriting
     * the user's own edits.
     */
    public RegistrarResult seedSnippets(Path queriesDir) {
        RegistrarResult.Builder result = new RegistrarResult.Builder();
        if (queriesDir == null || !Files.isDirectory(queriesDir)) {
            return result.build();
        }

        Set<String> existingFundsXmlNames = collectExistingFundsXmlSnippetNames();

        Path manifest = queriesDir.resolve("index.json");
        if (Files.isRegularFile(manifest)) {
            try {
                String json = Files.readString(manifest);
                seedFromManifest(json, queriesDir, existingFundsXmlNames, result);
                return result.build();
            } catch (IOException e) {
                logger.warn("Failed to read snippets manifest {}: {} — falling back to file scan",
                        manifest, e.getMessage());
            }
        }

        seedFromFileScan(queriesDir, existingFundsXmlNames, result);
        return result.build();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private boolean folderFavoriteExists(Path folder, FileFavorite.FileType type) {
        String absolute = folder.toAbsolutePath().toString();
        List<FileFavorite> existing = favoritesService.getFolderFavorites(type);
        return existing.stream().anyMatch(f -> absolute.equals(f.getFilePath()));
    }

    private Set<String> collectExistingFundsXmlSnippetNames() {
        Set<String> names = new HashSet<>();
        for (XPathSnippet s : snippetRepository.getAllSnippets()) {
            if (s.getTags() != null && s.getTags().contains(SNIPPET_TAG)) {
                names.add(s.getName());
            }
        }
        return names;
    }

    private void seedFromManifest(String json, Path queriesDir, Set<String> existing,
                                  RegistrarResult.Builder result) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            logger.warn("Snippets manifest must be a JSON array; skipping");
            return;
        }
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String name = asString(entry, "name");
            String query = asString(entry, "query");
            String file = asString(entry, "file");

            // If "query" is absent but "file" is given, read the query from disk
            if ((query == null || query.isBlank()) && file != null && !file.isBlank()) {
                Path p = queriesDir.resolve(file);
                if (Files.isRegularFile(p)) {
                    try {
                        query = Files.readString(p);
                    } catch (IOException e) {
                        logger.warn("Failed to read snippet file {}: {}", p, e.getMessage());
                    }
                }
            }

            if (name == null || query == null || name.isBlank() || query.isBlank()) {
                continue;
            }
            if (existing.contains(name)) {
                result.snippetSkipped();
                continue;
            }
            String description = asString(entry, "description");
            XPathSnippet.SnippetType type = parseType(asString(entry, "type"));
            XPathSnippet.SnippetCategory category = parseCategory(asString(entry, "category"));

            XPathSnippet snippet = new XPathSnippet(name, type, category, query);
            if (description != null) {
                snippet.setDescription(description);
            }
            tagAsFundsXml(snippet);
            snippetRepository.saveSnippet(snippet);
            result.snippetAdded();
        }
    }

    private void seedFromFileScan(Path queriesDir, Set<String> existing,
                                  RegistrarResult.Builder result) {
        try (Stream<Path> walk = Files.walk(queriesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(FundsXmlPostDownloadRegistrar::isQueryFile)
                    .forEach(p -> {
                        String name = stripExtension(p.getFileName().toString());
                        if (existing.contains(name)) {
                            result.snippetSkipped();
                            return;
                        }
                        try {
                            String query = Files.readString(p);
                            XPathSnippet.SnippetType type = isXQueryFile(p)
                                    ? XPathSnippet.SnippetType.XQUERY
                                    : XPathSnippet.SnippetType.XPATH;
                            XPathSnippet snippet = new XPathSnippet(
                                    name, type, XPathSnippet.SnippetCategory.CUSTOM, query);
                            tagAsFundsXml(snippet);
                            snippetRepository.saveSnippet(snippet);
                            result.snippetAdded();
                        } catch (IOException e) {
                            logger.warn("Failed to read snippet file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to walk queries dir {}: {}", queriesDir, e.getMessage());
        }
    }

    private static void tagAsFundsXml(XPathSnippet snippet) {
        Set<String> tags = new HashSet<>(snippet.getTags());
        tags.add(SNIPPET_TAG);
        snippet.setTags(tags);
    }

    private static boolean isQueryFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xpath") || name.endsWith(".xq") || name.endsWith(".xquery");
    }

    private static boolean isXQueryFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xq") || name.endsWith(".xquery");
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String asString(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        return obj.get(field).getAsString();
    }

    private static XPathSnippet.SnippetType parseType(String s) {
        if (s == null) return XPathSnippet.SnippetType.XPATH;
        try {
            return XPathSnippet.SnippetType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return XPathSnippet.SnippetType.XPATH;
        }
    }

    private static XPathSnippet.SnippetCategory parseCategory(String s) {
        if (s == null) return XPathSnippet.SnippetCategory.CUSTOM;
        try {
            return XPathSnippet.SnippetCategory.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return XPathSnippet.SnippetCategory.CUSTOM;
        }
    }

    // ---------------------------------------------------------------------
    // Result type
    // ---------------------------------------------------------------------

    /** Summary returned by registrar methods so callers can show meaningful UI feedback. */
    public static final class RegistrarResult {
        private final boolean examplesFolderAdded;
        private final boolean examplesFolderSkipped;
        private final int schematronFilesAdded;
        private final int schematronFilesSkipped;
        private final int snippetsAdded;
        private final int snippetsSkipped;

        private RegistrarResult(Builder b) {
            this.examplesFolderAdded = b.examplesFolderAdded;
            this.examplesFolderSkipped = b.examplesFolderSkipped;
            this.schematronFilesAdded = b.schematronFilesAdded;
            this.schematronFilesSkipped = b.schematronFilesSkipped;
            this.snippetsAdded = b.snippetsAdded;
            this.snippetsSkipped = b.snippetsSkipped;
        }

        public boolean examplesFolderAdded() { return examplesFolderAdded; }
        public boolean examplesFolderSkipped() { return examplesFolderSkipped; }
        public int schematronFilesAdded() { return schematronFilesAdded; }
        public int schematronFilesSkipped() { return schematronFilesSkipped; }
        public int snippetsAdded() { return snippetsAdded; }
        public int snippetsSkipped() { return snippetsSkipped; }

        static final class Builder {
            boolean examplesFolderAdded;
            boolean examplesFolderSkipped;
            int schematronFilesAdded;
            int schematronFilesSkipped;
            int snippetsAdded;
            int snippetsSkipped;

            Builder examplesFolderAdded() { this.examplesFolderAdded = true; return this; }
            Builder examplesFolderSkipped() { this.examplesFolderSkipped = true; return this; }
            Builder schematronFileAdded() { this.schematronFilesAdded++; return this; }
            Builder schematronFileSkipped() { this.schematronFilesSkipped++; return this; }
            Builder snippetAdded() { this.snippetsAdded++; return this; }
            Builder snippetSkipped() { this.snippetsSkipped++; return this; }

            RegistrarResult build() { return new RegistrarResult(this); }
        }
    }
}
