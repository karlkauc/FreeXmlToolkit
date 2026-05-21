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

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test that hits real GitHub. Gated by the
 * {@code fundsxml.integration} system property so normal {@code ./gradlew test}
 * runs skip it (no flaky CI failures from network blips).
 *
 * <p>Run with:
 * <pre>
 *   ./gradlew test --tests "FundsXmlDownloadIntegrationTest" -Dfundsxml.integration=true
 * </pre>
 *
 * <p>The test redirects {@code user.home} to a temp directory so the user's real
 * {@code ~/.freeXmlToolkit/} cache is not touched, and resets all singletons so they
 * reinitialise against the redirected home.
 */
@DisplayName("FundsXML Download Integration (real GitHub)")
@EnabledIfSystemProperty(named = "fundsxml.integration", matches = "true")
class FundsXmlDownloadIntegrationTest {

    @TempDir
    Path tempDir;

    private String originalUserHome;

    @BeforeEach
    void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());

        resetSingleton(FavoritesService.class);
        resetSingleton(XPathSnippetRepository.class);
        resetSingleton(TemplateRepository.class);
        FundsXmlCache.resetForTesting();
        FundsXmlExtensionService.resetForTesting();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton(FavoritesService.class);
        resetSingleton(XPathSnippetRepository.class);
        resetSingleton(TemplateRepository.class);
        FundsXmlCache.resetForTesting();
        FundsXmlExtensionService.resetForTesting();
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
    @DisplayName("downloadOrUpdate fetches schema + examples and registers everything end-to-end")
    void downloadEndToEnd() {
        FundsXmlExtensionService service = FundsXmlExtensionService.getInstance();

        FundsXmlExtensionService.DownloadResult result =
                service.downloadOrUpdate((stage, bytes, total, msg) ->
                        System.out.printf("[stage=%s] %s%n", stage, msg));

        // ----- Download succeeded ---------------------------------------
        assertTrue(result.isSuccess(),
                "Download failed: " + result.error());
        assertNotNull(result.schemaVersion(), "schemaVersion should be set");
        assertTrue(result.schemaDownloaded() || result.schemaVersion() != null,
                "Either fresh schema or already-installed should report a version");
        assertTrue(result.examplesDownloaded() > 0,
                "Expected at least one example file, got " + result.examplesDownloaded());

        // ----- Cache layout populated -----------------------------------
        FundsXmlCache cache = FundsXmlCache.getInstance();
        Path activeSchema = cache.getActiveSchemaFile();
        assertNotNull(activeSchema, "Active schema file must be resolvable after download");
        assertTrue(Files.isRegularFile(activeSchema),
                "Schema XSD must exist on disk: " + activeSchema);

        // ----- Favorites populated --------------------------------------
        FavoritesService favs = FavoritesService.getInstance();
        List<FileFavorite> schemaFavs = favs.getFavoritesByFolder(
                FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_SCHEMA);
        assertTrue(schemaFavs.size() == 1,
                "Expected exactly one schema favorite, got " + schemaFavs.size());
        assertTrue(schemaFavs.get(0).getFileType() == FileFavorite.FileType.XSD,
                "Schema favorite must be XSD-typed");

        // Folder favorites are surfaced separately by FavoritesService (filtered out of
        // the file-oriented `getFavoritesByFolder` view).
        List<FileFavorite> xmlFolderFavs = favs.getFolderFavorites(FileFavorite.FileType.XML);
        assertTrue(xmlFolderFavs.stream().anyMatch(f -> FundsXmlPostDownloadRegistrar
                        .FAVORITE_FOLDER_EXAMPLES.equals(f.getName())),
                "Expected an examples folder favorite");

        List<FileFavorite> exampleFileFavs = favs.getFavoritesByFolder(
                FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_EXAMPLES);
        long featuredXmls = exampleFileFavs.size();
        // Featured XMLs may legitimately be 0 if upstream only ships .xsl/.xslt files.
        System.out.printf("Featured sample-XML favourites: %d%n", featuredXmls);

        List<FileFavorite> xsltFavs = favs.getFavoritesByFolder(
                FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_XSLT);
        System.out.printf("XSLT favourites: %d%n", xsltFavs.size());

        List<FileFavorite> schematronFavs = favs.getFavoritesByFolder(
                FundsXmlPostDownloadRegistrar.FAVORITE_FOLDER_SCHEMATRON);
        System.out.printf("Schematron favourites: %d%n", schematronFavs.size());

        // ----- Snippets populated ---------------------------------------
        long fundsxmlSnippets = XPathSnippetRepository.getInstance().getAllSnippets().stream()
                .filter(s -> s.getTags() != null
                        && s.getTags().contains(FundsXmlPostDownloadRegistrar.SNIPPET_TAG))
                .count();
        System.out.printf("FundsXML-tagged XPath/XQuery snippets: %d%n", fundsxmlSnippets);

        // ----- Templates populated --------------------------------------
        long fundsxmlTemplates = TemplateRepository.getInstance().getAllTemplates().stream()
                .filter(t -> t.getId() != null
                        && t.getId().startsWith(FundsXmlPostDownloadRegistrar.TEMPLATE_ID_PREFIX))
                .count();
        System.out.printf("FundsXML new-document templates: %d%n", fundsxmlTemplates);
        assertTrue(fundsxmlTemplates == result.templatesAdded(),
                "Template count via getAllTemplates(" + fundsxmlTemplates
                        + ") must equal templatesAdded(" + result.templatesAdded() + ")");

        // ----- Print result summary so the user sees the actual numbers --
        System.out.println();
        System.out.println("============ FundsXML download result ============");
        System.out.println("schemaVersion           : " + result.schemaVersion());
        System.out.println("schemaDownloaded        : " + result.schemaDownloaded());
        System.out.println("examplesDownloaded      : " + result.examplesDownloaded());
        System.out.println("schematronDownloaded    : " + result.schematronDownloaded());
        System.out.println("queriesDownloaded       : " + result.queriesDownloaded());
        System.out.println("favoritesAdded (total)  : " + result.favoritesAdded());
        System.out.println("  schemaFavoriteAdded   : " + result.schemaFavoriteAdded());
        System.out.println("  xsltFavoritesAdded    : " + result.xsltFavoritesAdded());
        System.out.println("  featuredXmlAdded      : " + result.featuredXmlFavoritesAdded());
        System.out.println("templatesAdded          : " + result.templatesAdded());
        System.out.println("snippetsAdded           : " + result.snippetsAdded());
        System.out.println("cache base              : " + cache.getBaseDir());
        System.out.println("active schema           : " + activeSchema);
        System.out.println("====================================================");

        // Light sample template inspection
        TemplateRepository.getInstance().getAllTemplates().stream()
                .filter(t -> t.getId() != null
                        && t.getId().startsWith(FundsXmlPostDownloadRegistrar.TEMPLATE_ID_PREFIX))
                .findFirst()
                .ifPresent((XmlTemplate t) -> System.out.printf(
                        "First template: id=%s, name=%s, category=%s%n",
                        t.getId(), t.getName(), t.getCategory()));
    }
}
