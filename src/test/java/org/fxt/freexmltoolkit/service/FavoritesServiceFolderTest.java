package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.FileFavorite.FileType;
import org.junit.jupiter.api.Test;

/**
 * Verifies the folder-favorite extensions to {@link FavoritesService}.
 * <p>
 * Uses the real singleton because FavoritesService is designed as a singleton that persists
 * to the user's home directory. Tests clean up after themselves to avoid pollution.
 */
class FavoritesServiceFolderTest {

    @Test
    void addAndRetrieveXmlFolderFavorite() {
        FavoritesService svc = FavoritesService.getInstance();
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));

        FileFavorite added = svc.addFolderFavorite("tmpXml", tmp, FileType.XML);
        try {
            assertNotNull(added);
            assertTrue(added.isDirectory());
            assertEquals(FileType.XML, added.getFileType());

            List<FileFavorite> xmlFolders = svc.getFolderFavorites(FileType.XML);
            assertTrue(xmlFolders.stream().anyMatch(f -> f.getId().equals(added.getId())));

            List<FileFavorite> xsltFolders = svc.getFolderFavorites(FileType.XSLT);
            assertTrue(xsltFolders.stream().noneMatch(f -> f.getId().equals(added.getId())),
                    "XML folder must not appear in XSLT list");
        } finally {
            svc.removeFolderFavorite(added.getId());
        }
        assertTrue(svc.getFolderFavorites(FileType.XML).stream()
                .noneMatch(f -> f.getId().equals(added.getId())));
    }

    @Test
    void folderFavoritesExcludedFromFileFavoriteQueries() {
        FavoritesService svc = FavoritesService.getInstance();
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        FileFavorite folder = svc.addFolderFavorite("tmp2", tmp, FileType.XSLT);
        try {
            // getFavoritesByType is used by the file-favorites panel - folders MUST NOT leak.
            List<FileFavorite> fileEntries = svc.getFavoritesByType(FileType.XSLT);
            assertTrue(fileEntries.stream().noneMatch(FileFavorite::isDirectory),
                    "getFavoritesByType must not return directory favorites");

            // getAllFavorites is also used by the panel - must also exclude directories.
            List<FileFavorite> all = svc.getAllFavorites();
            assertTrue(all.stream().noneMatch(FileFavorite::isDirectory),
                    "getAllFavorites must not return directory favorites");
        } finally {
            svc.removeFolderFavorite(folder.getId());
        }
    }

    @Test
    void updateFolderFavoriteNameChangesLabel() {
        FavoritesService svc = FavoritesService.getInstance();
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        FileFavorite added = svc.addFolderFavorite("before", tmp, FileType.XML);
        try {
            boolean updated = svc.updateFolderFavoriteName(added.getId(), "after");
            assertTrue(updated);
            FileFavorite reloaded = svc.getFolderFavorites(FileType.XML).stream()
                    .filter(f -> f.getId().equals(added.getId()))
                    .findFirst().orElseThrow();
            assertEquals("after", reloaded.getName());
        } finally {
            svc.removeFolderFavorite(added.getId());
        }
    }
}
