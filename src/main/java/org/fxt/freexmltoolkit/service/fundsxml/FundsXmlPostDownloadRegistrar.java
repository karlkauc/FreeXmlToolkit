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
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public static final String FAVORITE_FOLDER_SCHEMA = "FundsXML Schema";
    public static final String FAVORITE_FOLDER_XSLT = "FundsXML XSLT";

    /** Snippet tag applied to all seeded snippets. */
    public static final String SNIPPET_TAG = "fundsxml";

    /** Category used for FundsXML sample XMLs registered in {@link TemplateRepository}. */
    public static final String TEMPLATE_CATEGORY = "FundsXML";

    /** Prefix used to build deterministic, idempotent template IDs for FundsXML samples. */
    public static final String TEMPLATE_ID_PREFIX = "fundsxml-sample-";

    /** Cap on the number of "featured" sample-XMLs surfaced as individual favourites/templates. */
    public static final int FEATURED_SAMPLE_LIMIT = 10;

    /** Optional manifest filename in {@code examples/} that overrides the size-based heuristic. */
    public static final String FEATURED_MANIFEST = "featured.json";

    private final FavoritesService favoritesService;
    private final XPathSnippetRepository snippetRepository;
    private final TemplateRepository templateRepository;

    public FundsXmlPostDownloadRegistrar(FavoritesService favoritesService,
                                         XPathSnippetRepository snippetRepository,
                                         TemplateRepository templateRepository) {
        this.favoritesService = favoritesService;
        this.snippetRepository = snippetRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Backwards-compatible constructor — template registration becomes a no-op.
     */
    public FundsXmlPostDownloadRegistrar(FavoritesService favoritesService,
                                         XPathSnippetRepository snippetRepository) {
        this(favoritesService, snippetRepository, null);
    }

    /**
     * Convenience constructor using production singletons.
     */
    public FundsXmlPostDownloadRegistrar() {
        this(FavoritesService.getInstance(),
                XPathSnippetRepository.getInstance(),
                TemplateRepository.getInstance());
    }

    /**
     * Returns a registrar that performs no work — useful in tests that exercise the
     * download flow without touching the global favorites/snippet stores.
     */
    public static FundsXmlPostDownloadRegistrar disabled() {
        return new FundsXmlPostDownloadRegistrar(null, null, null) {
            @Override
            public RegistrarResult registerFavorites(Path examplesDir, Path schematronDir) {
                return new RegistrarResult.Builder().build();
            }
            @Override
            public RegistrarResult seedSnippets(Path queriesDir) {
                return new RegistrarResult.Builder().build();
            }
            @Override
            public RegistrarResult registerSchemaFavorite(Path schemaFile) {
                return new RegistrarResult.Builder().build();
            }
            @Override
            public RegistrarResult registerXsltFavorites(Path examplesDir) {
                return new RegistrarResult.Builder().build();
            }
            @Override
            public RegistrarResult registerFeaturedXmlFavorites(Path examplesDir) {
                return new RegistrarResult.Builder().build();
            }
            @Override
            public RegistrarResult registerXmlTemplates(Path examplesDir) {
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

    /**
     * Registers the active FundsXML schema file as a single XSD favourite under
     * {@link #FAVORITE_FOLDER_SCHEMA}. No-op if the path is {@code null} or missing.
     */
    public RegistrarResult registerSchemaFavorite(Path schemaFile) {
        RegistrarResult.Builder result = new RegistrarResult.Builder();
        if (schemaFile == null || !Files.isRegularFile(schemaFile)) {
            return result.build();
        }
        if (favoritesService == null) {
            return result.build();
        }
        String absolute = schemaFile.toAbsolutePath().toString();
        if (favoritesService.isFavorite(absolute)) {
            result.schemaFavoriteSkipped();
            return result.build();
        }
        String displayName = stripExtension(schemaFile.getFileName().toString());
        // Version-qualify the display name so multiple installed versions don't collide visually.
        String versionFolder = schemaFile.getParent() != null && schemaFile.getParent().getFileName() != null
                ? schemaFile.getParent().getFileName().toString()
                : null;
        if (versionFolder != null && !versionFolder.isBlank()) {
            displayName = displayName + " (" + versionFolder + ")";
        }
        favoritesService.addFavorite(absolute, displayName, FAVORITE_FOLDER_SCHEMA);
        result.schemaFavoriteAdded();
        return result.build();
    }

    /**
     * Registers each {@code *.xsl} / {@code *.xslt} file under {@code examplesDir} as an
     * individual XSLT favourite under {@link #FAVORITE_FOLDER_XSLT}. Idempotent.
     */
    public RegistrarResult registerXsltFavorites(Path examplesDir) {
        RegistrarResult.Builder result = new RegistrarResult.Builder();
        if (examplesDir == null || !Files.isDirectory(examplesDir) || favoritesService == null) {
            return result.build();
        }
        try (Stream<Path> walk = Files.walk(examplesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(FundsXmlPostDownloadRegistrar::isXsltFile)
                    .forEach(p -> {
                        String path = p.toAbsolutePath().toString();
                        if (favoritesService.isFavorite(path)) {
                            result.xsltFileSkipped();
                        } else {
                            favoritesService.addFavorite(path,
                                    stripExtension(p.getFileName().toString()), FAVORITE_FOLDER_XSLT);
                            result.xsltFileAdded();
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to walk examples dir for XSLT: {}: {}", examplesDir, e.getMessage());
        }
        return result.build();
    }

    /**
     * Registers up to {@link #FEATURED_SAMPLE_LIMIT} sample-XML files as individual
     * favourites under {@link #FAVORITE_FOLDER_EXAMPLES}, complementing the folder
     * favourite created by {@link #registerFavorites}. Selection order:
     * <ol>
     *   <li>If {@code examples/featured.json} exists, its file list (relative paths)
     *       defines the set.</li>
     *   <li>Otherwise the smallest {@code .xml} files are picked — compact samples are
     *       the most useful for getting started.</li>
     * </ol>
     */
    public RegistrarResult registerFeaturedXmlFavorites(Path examplesDir) {
        RegistrarResult.Builder result = new RegistrarResult.Builder();
        if (examplesDir == null || !Files.isDirectory(examplesDir) || favoritesService == null) {
            return result.build();
        }
        Map<Path, String> featured = collectFeaturedSamples(examplesDir);
        for (Map.Entry<Path, String> entry : featured.entrySet()) {
            Path p = entry.getKey();
            String displayName = entry.getValue();
            String absolute = p.toAbsolutePath().toString();
            if (favoritesService.isFavorite(absolute)) {
                result.featuredXmlSkipped();
                continue;
            }
            favoritesService.addFavorite(absolute, displayName, FAVORITE_FOLDER_EXAMPLES);
            result.featuredXmlAdded();
        }
        return result.build();
    }

    /**
     * Seeds up to {@link #FEATURED_SAMPLE_LIMIT} sample-XMLs as templates in the
     * {@link TemplateRepository} under category {@link #TEMPLATE_CATEGORY}, so users can
     * start a new document from a real FundsXML sample. Idempotent — template IDs are
     * deterministic ({@link #TEMPLATE_ID_PREFIX} + slug of the relative path).
     */
    public RegistrarResult registerXmlTemplates(Path examplesDir) {
        RegistrarResult.Builder result = new RegistrarResult.Builder();
        if (examplesDir == null || !Files.isDirectory(examplesDir) || templateRepository == null) {
            return result.build();
        }
        Map<Path, String> featured = collectFeaturedSamples(examplesDir);
        for (Map.Entry<Path, String> entry : featured.entrySet()) {
            Path p = entry.getKey();
            String displayName = entry.getValue();
            String relative = examplesDir.relativize(p).toString().replace('\\', '/');
            String id = TEMPLATE_ID_PREFIX + slugify(relative);
            if (templateRepository.getTemplate(id) != null) {
                result.templateSkipped();
                continue;
            }
            try {
                String content = Files.readString(p);
                // Construct explicitly so the deterministic ID survives — the
                // convenience method createNewTemplate(id, name, ...) treats its
                // first arg as the *name*, leaving a random UUID as the ID.
                XmlTemplate template = new XmlTemplate();
                template.setId(id);
                template.setName(displayName);
                template.setContent(content);
                template.setCategory(TEMPLATE_CATEGORY);
                template.setDescription("Sample from FundsXML examples: " + relative);
                template.setBuiltIn(false);
                templateRepository.addTemplate(template, true);
                result.templateAdded();
            } catch (Exception e) {
                logger.warn("Failed to seed FundsXML template from {}: {}", p, e.getMessage());
            }
        }
        return result.build();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Returns an ordered map of {@code path -> display name} for the featured samples.
     * Uses {@code examples/featured.json} when present, otherwise picks the smallest
     * XML files (up to {@link #FEATURED_SAMPLE_LIMIT}).
     */
    private Map<Path, String> collectFeaturedSamples(Path examplesDir) {
        Map<Path, String> result = new LinkedHashMap<>();
        Path manifest = examplesDir.resolve(FEATURED_MANIFEST);
        if (Files.isRegularFile(manifest)) {
            try {
                String json = Files.readString(manifest);
                JsonElement root = JsonParser.parseString(json);
                JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : null;
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) continue;
                        JsonObject obj = element.getAsJsonObject();
                        String file = asString(obj, "file");
                        if (file == null || file.isBlank()) continue;
                        Path p = examplesDir.resolve(file);
                        if (!Files.isRegularFile(p) || !isXmlFile(p)) continue;
                        String name = asString(obj, "name");
                        if (name == null || name.isBlank()) {
                            name = stripExtension(p.getFileName().toString());
                        }
                        result.put(p, name);
                        if (result.size() >= FEATURED_SAMPLE_LIMIT) break;
                    }
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            } catch (IOException | RuntimeException e) {
                logger.warn("Failed to read {}; falling back to size heuristic: {}",
                        manifest, e.getMessage());
            }
        }
        // Fallback: smallest XML files first
        List<Path> xmlFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(examplesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(FundsXmlPostDownloadRegistrar::isXmlFile)
                    .forEach(xmlFiles::add);
        } catch (IOException e) {
            logger.warn("Failed to walk examples dir {}: {}", examplesDir, e.getMessage());
            return result;
        }
        xmlFiles.sort(Comparator.comparingLong(FundsXmlPostDownloadRegistrar::sizeOf));
        xmlFiles.stream()
                .limit(FEATURED_SAMPLE_LIMIT)
                .forEach(p -> result.put(p, stripExtension(p.getFileName().toString())));
        return result;
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean isXmlFile(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    private static boolean isXsltFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xsl") || name.endsWith(".xslt");
    }

    private static String slugify(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    // ---------------------------------------------------------------------
    // Legacy helpers (kept below the new ones to minimise diff)
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
        private final boolean schemaFavoriteAdded;
        private final boolean schemaFavoriteSkipped;
        private final int xsltFilesAdded;
        private final int xsltFilesSkipped;
        private final int featuredXmlAdded;
        private final int featuredXmlSkipped;
        private final int templatesAdded;
        private final int templatesSkipped;

        private RegistrarResult(Builder b) {
            this.examplesFolderAdded = b.examplesFolderAdded;
            this.examplesFolderSkipped = b.examplesFolderSkipped;
            this.schematronFilesAdded = b.schematronFilesAdded;
            this.schematronFilesSkipped = b.schematronFilesSkipped;
            this.snippetsAdded = b.snippetsAdded;
            this.snippetsSkipped = b.snippetsSkipped;
            this.schemaFavoriteAdded = b.schemaFavoriteAdded;
            this.schemaFavoriteSkipped = b.schemaFavoriteSkipped;
            this.xsltFilesAdded = b.xsltFilesAdded;
            this.xsltFilesSkipped = b.xsltFilesSkipped;
            this.featuredXmlAdded = b.featuredXmlAdded;
            this.featuredXmlSkipped = b.featuredXmlSkipped;
            this.templatesAdded = b.templatesAdded;
            this.templatesSkipped = b.templatesSkipped;
        }

        public boolean examplesFolderAdded() { return examplesFolderAdded; }
        public boolean examplesFolderSkipped() { return examplesFolderSkipped; }
        public int schematronFilesAdded() { return schematronFilesAdded; }
        public int schematronFilesSkipped() { return schematronFilesSkipped; }
        public int snippetsAdded() { return snippetsAdded; }
        public int snippetsSkipped() { return snippetsSkipped; }
        public boolean schemaFavoriteAdded() { return schemaFavoriteAdded; }
        public boolean schemaFavoriteSkipped() { return schemaFavoriteSkipped; }
        public int xsltFilesAdded() { return xsltFilesAdded; }
        public int xsltFilesSkipped() { return xsltFilesSkipped; }
        public int featuredXmlAdded() { return featuredXmlAdded; }
        public int featuredXmlSkipped() { return featuredXmlSkipped; }
        public int templatesAdded() { return templatesAdded; }
        public int templatesSkipped() { return templatesSkipped; }

        static final class Builder {
            boolean examplesFolderAdded;
            boolean examplesFolderSkipped;
            int schematronFilesAdded;
            int schematronFilesSkipped;
            int snippetsAdded;
            int snippetsSkipped;
            boolean schemaFavoriteAdded;
            boolean schemaFavoriteSkipped;
            int xsltFilesAdded;
            int xsltFilesSkipped;
            int featuredXmlAdded;
            int featuredXmlSkipped;
            int templatesAdded;
            int templatesSkipped;

            Builder examplesFolderAdded() { this.examplesFolderAdded = true; return this; }
            Builder examplesFolderSkipped() { this.examplesFolderSkipped = true; return this; }
            Builder schematronFileAdded() { this.schematronFilesAdded++; return this; }
            Builder schematronFileSkipped() { this.schematronFilesSkipped++; return this; }
            Builder snippetAdded() { this.snippetsAdded++; return this; }
            Builder snippetSkipped() { this.snippetsSkipped++; return this; }
            Builder schemaFavoriteAdded() { this.schemaFavoriteAdded = true; return this; }
            Builder schemaFavoriteSkipped() { this.schemaFavoriteSkipped = true; return this; }
            Builder xsltFileAdded() { this.xsltFilesAdded++; return this; }
            Builder xsltFileSkipped() { this.xsltFilesSkipped++; return this; }
            Builder featuredXmlAdded() { this.featuredXmlAdded++; return this; }
            Builder featuredXmlSkipped() { this.featuredXmlSkipped++; return this; }
            Builder templateAdded() { this.templatesAdded++; return this; }
            Builder templateSkipped() { this.templatesSkipped++; return this; }

            RegistrarResult build() { return new RegistrarResult(this); }
        }
    }
}
