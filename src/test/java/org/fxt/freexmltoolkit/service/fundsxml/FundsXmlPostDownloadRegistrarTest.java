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
import org.fxt.freexmltoolkit.domain.XPathSnippet;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XPathSnippetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
