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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FundsXmlPostDownloadRegistrar Tests")
class FundsXmlPostDownloadRegistrarTest {

    @TempDir
    Path tempDir;

    private String originalUserHome;
    private Path examplesDir;
    private Path schematronDir;
    private Path queriesDir;
    private FundsXmlPostDownloadRegistrar registrar;

    @BeforeEach
    void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());

        // Reset both singletons so they reinitialize against the redirected home
        resetSingleton(FavoritesService.class);
        resetSingleton(XPathSnippetRepository.class);

        examplesDir = Files.createDirectories(tempDir.resolve("fundsxml/examples"));
        schematronDir = Files.createDirectories(tempDir.resolve("fundsxml/schematron"));
        queriesDir = Files.createDirectories(tempDir.resolve("fundsxml/queries"));

        registrar = new FundsXmlPostDownloadRegistrar(
                FavoritesService.getInstance(),
                XPathSnippetRepository.getInstance());
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton(FavoritesService.class);
        resetSingleton(XPathSnippetRepository.class);
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private void resetSingleton(Class<?> type) throws Exception {
        Field f = type.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    @DisplayName("registerFavorites creates the examples folder favorite")
    void registersExamplesFolderFavorite() throws Exception {
        Files.writeString(examplesDir.resolve("sample.xml"), "<root/>");

        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerFavorites(examplesDir, schematronDir);

        assertTrue(result.examplesFolderAdded());
        List<FileFavorite> folderFavs = FavoritesService.getInstance()
                .getFolderFavorites(FileFavorite.FileType.XML);
        assertEquals(1, folderFavs.size());
        assertEquals(examplesDir.toAbsolutePath().toString(), folderFavs.get(0).getFilePath());
        assertEquals(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_EXAMPLES, folderFavs.get(0).getName());
    }

    @Test
    @DisplayName("registerFavorites adds individual schematron files")
    void registersSchematronFiles() throws Exception {
        Files.writeString(schematronDir.resolve("rules.sch"), "<sch:schema/>");
        Files.writeString(schematronDir.resolve("more.sch"), "<sch:schema/>");
        Files.writeString(schematronDir.resolve("readme.txt"), "ignored"); // not .sch

        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerFavorites(examplesDir, schematronDir);

        assertEquals(2, result.schematronFilesAdded());
        List<FileFavorite> schematronFavs = FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_SCHEMATRON);
        assertEquals(2, schematronFavs.size());
    }

    @Test
    @DisplayName("registerFavorites is idempotent — running twice does not duplicate")
    void registerFavoritesIdempotent() throws Exception {
        Files.writeString(schematronDir.resolve("rules.sch"), "<sch:schema/>");

        registrar.registerFavorites(examplesDir, schematronDir);
        FundsXmlPostDownloadRegistrar.RegistrarResult second =
                registrar.registerFavorites(examplesDir, schematronDir);

        assertTrue(second.examplesFolderSkipped());
        assertEquals(1, second.schematronFilesSkipped());
        assertEquals(0, second.schematronFilesAdded());

        List<FileFavorite> folderFavs = FavoritesService.getInstance()
                .getFolderFavorites(FileFavorite.FileType.XML);
        assertEquals(1, folderFavs.size());
        assertEquals(1, FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_SCHEMATRON)
                .size());
    }

    @Test
    @DisplayName("seedSnippets creates one snippet per query file (file scan)")
    void seedSnippetsFromFiles() throws Exception {
        Files.writeString(queriesDir.resolve("AllFunds.xpath"), "//Fund");
        Files.writeString(queriesDir.resolve("FundNames.xq"), "for $f in //Fund return $f/Name");
        Files.writeString(queriesDir.resolve("readme.txt"), "ignored");

        FundsXmlPostDownloadRegistrar.RegistrarResult result = registrar.seedSnippets(queriesDir);

        assertEquals(2, result.snippetsAdded());
        long taggedCount = XPathSnippetRepository.getInstance().getAllSnippets().stream()
                .filter(s -> s.getTags() != null
                        && s.getTags().contains(FundsXmlPostDownloadRegistrar.SNIPPET_TAG))
                .count();
        assertEquals(2, taggedCount);
    }

    @Test
    @DisplayName("seedSnippets respects index.json manifest")
    void seedSnippetsFromManifest() throws Exception {
        Files.writeString(queriesDir.resolve("AllFunds.xpath"), "//Fund");
        Files.writeString(queriesDir.resolve("index.json"), """
                [
                  {
                    "name": "All Funds",
                    "description": "Selects every Fund element",
                    "type": "XPATH",
                    "category": "NAVIGATION",
                    "file": "AllFunds.xpath"
                  },
                  {
                    "name": "Fund Names",
                    "description": "Inline XQuery example",
                    "type": "XQUERY",
                    "category": "EXTRACTION",
                    "query": "for $f in //Fund return $f/Name"
                  }
                ]
                """);

        FundsXmlPostDownloadRegistrar.RegistrarResult result = registrar.seedSnippets(queriesDir);

        assertEquals(2, result.snippetsAdded());
        List<XPathSnippet> tagged = XPathSnippetRepository.getInstance().getAllSnippets().stream()
                .filter(s -> s.getTags() != null
                        && s.getTags().contains(FundsXmlPostDownloadRegistrar.SNIPPET_TAG))
                .toList();
        assertEquals(2, tagged.size());
        // Verify the manifest's descriptions made it through
        boolean foundDescription = tagged.stream()
                .anyMatch(s -> "Selects every Fund element".equals(s.getDescription()));
        assertTrue(foundDescription);
        // Verify XQUERY type was honored
        boolean foundXQuery = tagged.stream()
                .anyMatch(s -> s.getType() == XPathSnippet.SnippetType.XQUERY);
        assertTrue(foundXQuery);
    }

    @Test
    @DisplayName("seedSnippets skips snippets that already exist")
    void seedSnippetsIdempotent() throws Exception {
        Files.writeString(queriesDir.resolve("AllFunds.xpath"), "//Fund");

        FundsXmlPostDownloadRegistrar.RegistrarResult first = registrar.seedSnippets(queriesDir);
        FundsXmlPostDownloadRegistrar.RegistrarResult second = registrar.seedSnippets(queriesDir);

        assertEquals(1, first.snippetsAdded());
        assertEquals(0, second.snippetsAdded());
        assertEquals(1, second.snippetsSkipped());
    }

    @Test
    @DisplayName("seedSnippets gracefully handles missing or non-directory paths")
    void seedSnippetsMissingDir() {
        FundsXmlPostDownloadRegistrar.RegistrarResult nullResult = registrar.seedSnippets(null);
        FundsXmlPostDownloadRegistrar.RegistrarResult missingResult =
                registrar.seedSnippets(tempDir.resolve("nope"));

        assertEquals(0, nullResult.snippetsAdded());
        assertEquals(0, missingResult.snippetsAdded());
    }

    @Test
    @DisplayName("disabled() registrar performs no work")
    void disabledRegistrar() throws Exception {
        Files.writeString(schematronDir.resolve("rules.sch"), "<sch:schema/>");
        Files.writeString(queriesDir.resolve("q.xpath"), "//Fund");
        FundsXmlPostDownloadRegistrar noOp = FundsXmlPostDownloadRegistrar.disabled();

        FundsXmlPostDownloadRegistrar.RegistrarResult favResult = noOp.registerFavorites(examplesDir, schematronDir);
        FundsXmlPostDownloadRegistrar.RegistrarResult snipResult = noOp.seedSnippets(queriesDir);

        assertFalse(favResult.examplesFolderAdded());
        assertEquals(0, favResult.schematronFilesAdded());
        assertEquals(0, snipResult.snippetsAdded());
    }

    // ---------------------------------------------------------------------
    // New: schema, XSLT, featured-XML registration
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("registerSchemaFavorite adds the XSD as a single favorite under 'FundsXML Schema'")
    void registersSchemaFavorite() throws Exception {
        Path schemaVersionDir = Files.createDirectories(tempDir.resolve("fundsxml/schema/4.2.10"));
        Path schemaFile = schemaVersionDir.resolve("FundsXML4.xsd");
        Files.writeString(schemaFile, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerSchemaFavorite(schemaFile);

        assertTrue(result.schemaFavoriteAdded());
        java.util.List<FileFavorite> schemaFavs = FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_SCHEMA);
        assertEquals(1, schemaFavs.size());
        assertEquals(schemaFile.toAbsolutePath().toString(), schemaFavs.get(0).getFilePath());
        assertEquals(FileFavorite.FileType.XSD, schemaFavs.get(0).getFileType());
        // Version-qualified display name
        assertTrue(schemaFavs.get(0).getName().contains("4.2.10"),
                "Expected schema favorite name to include version, got: " + schemaFavs.get(0).getName());
    }

    @Test
    @DisplayName("registerSchemaFavorite is idempotent")
    void registerSchemaFavoriteIdempotent() throws Exception {
        Path schemaVersionDir = Files.createDirectories(tempDir.resolve("fundsxml/schema/4.2.10"));
        Path schemaFile = schemaVersionDir.resolve("FundsXML4.xsd");
        Files.writeString(schemaFile, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        registrar.registerSchemaFavorite(schemaFile);
        FundsXmlPostDownloadRegistrar.RegistrarResult second =
                registrar.registerSchemaFavorite(schemaFile);

        assertTrue(second.schemaFavoriteSkipped());
        assertFalse(second.schemaFavoriteAdded());
        assertEquals(1, FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_SCHEMA)
                .size());
    }

    @Test
    @DisplayName("registerSchemaFavorite handles null / missing files gracefully")
    void registerSchemaFavoriteHandlesMissing() {
        FundsXmlPostDownloadRegistrar.RegistrarResult nullResult =
                registrar.registerSchemaFavorite(null);
        FundsXmlPostDownloadRegistrar.RegistrarResult missingResult =
                registrar.registerSchemaFavorite(tempDir.resolve("nope.xsd"));

        assertFalse(nullResult.schemaFavoriteAdded());
        assertFalse(missingResult.schemaFavoriteAdded());
    }

    @Test
    @DisplayName("registerXsltFavorites picks up .xsl and .xslt only")
    void registersXsltFavorites() throws Exception {
        Files.writeString(examplesDir.resolve("transform.xsl"), "<xsl:stylesheet/>");
        Files.writeString(examplesDir.resolve("report.xslt"), "<xsl:stylesheet/>");
        Files.writeString(examplesDir.resolve("sample.xml"), "<root/>"); // ignored

        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerXsltFavorites(examplesDir);

        assertEquals(2, result.xsltFilesAdded());
        java.util.List<FileFavorite> xsltFavs = FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_XSLT);
        assertEquals(2, xsltFavs.size());
        for (FileFavorite f : xsltFavs) {
            assertEquals(FileFavorite.FileType.XSLT, f.getFileType());
        }
    }

    @Test
    @DisplayName("registerXsltFavorites is idempotent")
    void registerXsltFavoritesIdempotent() throws Exception {
        Files.writeString(examplesDir.resolve("transform.xsl"), "<xsl:stylesheet/>");

        registrar.registerXsltFavorites(examplesDir);
        FundsXmlPostDownloadRegistrar.RegistrarResult second =
                registrar.registerXsltFavorites(examplesDir);

        assertEquals(0, second.xsltFilesAdded());
        assertEquals(1, second.xsltFilesSkipped());
    }

    @Test
    @DisplayName("registerFeaturedXmlFavorites picks smallest XML files, capped at 10")
    void registerFeaturedXmlBySizeHeuristic() throws Exception {
        // Create 12 XML files of varying sizes; smallest 10 should be picked.
        for (int i = 0; i < 12; i++) {
            String content = "<root>" + "x".repeat(i * 20) + "</root>";
            Files.writeString(examplesDir.resolve("sample-" + i + ".xml"), content);
        }
        // Non-XML must be ignored.
        Files.writeString(examplesDir.resolve("readme.txt"), "ignored");

        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerFeaturedXmlFavorites(examplesDir);

        assertEquals(FundsXmlPostDownloadRegistrar.FEATURED_SAMPLE_LIMIT, result.featuredXmlAdded());
        java.util.List<FileFavorite> favs = FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_EXAMPLES);
        // All featured favorites are XML files (not the folder favorite, which is separate)
        assertEquals(FundsXmlPostDownloadRegistrar.FEATURED_SAMPLE_LIMIT,
                favs.stream().filter(f -> !f.isDirectory()).count());
    }

    @Test
    @DisplayName("registerFeaturedXmlFavorites honors featured.json when present")
    void registerFeaturedXmlFromManifest() throws Exception {
        Files.writeString(examplesDir.resolve("big.xml"), "<root>" + "x".repeat(10_000) + "</root>");
        Files.writeString(examplesDir.resolve("featured.json"), """
                [
                  { "file": "big.xml", "name": "Big Featured Sample" }
                ]
                """);

        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerFeaturedXmlFavorites(examplesDir);

        assertEquals(1, result.featuredXmlAdded());
        java.util.List<FileFavorite> favs = FavoritesService.getInstance()
                .getFavoritesByFolder(FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_EXAMPLES);
        assertTrue(favs.stream().anyMatch(f -> "Big Featured Sample".equals(f.getName())));
    }

    @Test
    @DisplayName("registerXmlTemplates is a no-op when no TemplateRepository is wired")
    void registerXmlTemplatesNoOpWithoutRepo() throws Exception {
        Files.writeString(examplesDir.resolve("sample.xml"), "<root/>");
        // The default test setUp() uses the 2-arg ctor, so templateRepository is null
        FundsXmlPostDownloadRegistrar.RegistrarResult result =
                registrar.registerXmlTemplates(examplesDir);

        assertEquals(0, result.templatesAdded());
        assertEquals(0, result.templatesSkipped());
    }

    @Test
    @DisplayName("disabled() registrar performs no work for new methods either")
    void disabledRegistrarNoOpForNewMethods() throws Exception {
        Path schemaFile = Files.createFile(tempDir.resolve("schema.xsd"));
        Files.writeString(examplesDir.resolve("transform.xsl"), "<xsl:stylesheet/>");
        Files.writeString(examplesDir.resolve("sample.xml"), "<root/>");
        FundsXmlPostDownloadRegistrar noOp = FundsXmlPostDownloadRegistrar.disabled();

        assertFalse(noOp.registerSchemaFavorite(schemaFile).schemaFavoriteAdded());
        assertEquals(0, noOp.registerXsltFavorites(examplesDir).xsltFilesAdded());
        assertEquals(0, noOp.registerFeaturedXmlFavorites(examplesDir).featuredXmlAdded());
        assertEquals(0, noOp.registerXmlTemplates(examplesDir).templatesAdded());
    }
}
