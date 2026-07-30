package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FavoritesService Tests")
public class FavoritesServiceTest {

    private FavoritesService service;
    private String originalUserHome;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        // Redirect user.home to temp directory for testing
        System.setProperty("user.home", tempDir.toString());
        
        // Reset Singleton
        FavoritesService.resetInstanceForTests();

        service = FavoritesService.getInstance();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    @DisplayName("Sollte Singleton bereitstellen")
    void testSingleton() {
        FavoritesService instance2 = FavoritesService.getInstance();
        assertSame(service, instance2);
    }

    @Test
    @DisplayName("Sollte Favoriten hinzufügen und abrufen")
    void testAddAndGetFavorite() {
        FileFavorite favorite = new FileFavorite("Test", "/path/to/file.xml", "TestFolder");
        service.addFavorite(favorite);
        
        assertTrue(service.isFavorite("/path/to/file.xml"));
        assertEquals(1, service.getAllFavorites().size());
        
        FileFavorite retrieved = service.getFavoriteById(favorite.getId());
        assertNotNull(retrieved);
        assertEquals("Test", retrieved.getName());
        assertEquals("TestFolder", retrieved.getFolderName());
    }

    @Test
    @DisplayName("Sollte keine Duplikate hinzufügen")
    void testNoDuplicateFavorites() {
        service.addFavorite("/path/to/file.xml", "Test 1", "Folder");
        service.addFavorite("/path/to/file.xml", "Test 2", "Folder");
        
        assertEquals(1, service.getAllFavorites().size());
    }

    @Test
    @DisplayName("Sollte Favoriten entfernen")
    void testRemoveFavorite() {
        FileFavorite favorite = new FileFavorite("RemoveMe", "/path/remove.xml");
        service.addFavorite(favorite);
        
        service.removeFavorite(favorite.getId());
        assertFalse(service.isFavorite("/path/remove.xml"));
    }

    @Test
    @DisplayName("Sollte Ordner-Operationen unterstützen")
    void testFolderOperations() {
        service.addFavorite("/file1.xml", "F1", "FolderA");
        service.addFavorite("/file2.xml", "F2", "FolderA");
        
        Set<String> folders = service.getAllFolders();
        assertTrue(folders.contains("FolderA"));
        
        List<FileFavorite> inFolder = service.getFavoritesByFolder("FolderA");
        assertEquals(2, inFolder.size());
        
        service.renameFolder("FolderA", "NewFolder");
        assertTrue(service.getAllFolders().contains("NewFolder"));
        assertFalse(service.getAllFolders().contains("FolderA"));
        
        service.deleteFolder("NewFolder");
        // Favorites should still exist but folder name is null (Uncategorized)
        assertTrue(service.getFavoritesByFolder("Uncategorized").size() >= 2);
    }

    @Test
    @DisplayName("Sollte XPath-Abfragen speichern und laden")
    void testQueryPersistence() throws IOException {
        String queryName = "MyQuery";
        String content = "/*[local-name()='root']";
        
        File saved = service.saveXPathQuery(queryName, content);
        assertNotNull(saved);
        assertTrue(saved.exists());
        
        String loaded = service.loadQuery(saved);
        assertEquals(content, loaded);
        
        List<File> allQueries = service.getSavedXPathQueries();
        assertTrue(allQueries.contains(saved));
        
        service.deleteQuery(saved);
        assertFalse(saved.exists());
    }

    @Test
    @DisplayName("Sollte Query-Dateinamen sanitisieren")
    void testSanitizeQueryFileName() {
        assertEquals("a_b.xpath", FavoritesService.sanitizeQueryFileName("a b", ".xpath"));
        assertEquals("my-query.xquery", FavoritesService.sanitizeQueryFileName("my-query", ".xquery"));
        // Dots are sanitized away too, so a name carrying the extension gains a fresh one
        // (unchanged saveQuery behavior).
        assertEquals("done_xpath.xpath", FavoritesService.sanitizeQueryFileName("done.xpath", ".xpath"));
    }

    @Test
    @DisplayName("Sollte Abfragen umbenennen und den Inhalt erhalten")
    void testRenameQuery() {
        File saved = service.saveXPathQuery("Old Name", "//item");
        assertNotNull(saved);
        assertEquals("Old_Name.xpath", saved.getName());

        File renamed = service.renameQuery(saved, "New Query!");
        assertNotNull(renamed);
        assertEquals("New_Query_.xpath", renamed.getName());
        assertTrue(renamed.exists());
        assertFalse(saved.exists(), "the old file must be gone after the rename");
        assertEquals("//item", service.loadQuery(renamed));
    }

    @Test
    @DisplayName("Sollte Umbenennen bei Namenskollision ablehnen")
    void testRenameQueryRejectsCollision() {
        File queryA = service.saveXPathQuery("QueryA", "//a");
        File queryB = service.saveXPathQuery("QueryB", "//b");

        assertNull(service.renameQuery(queryA, "QueryB"));
        assertTrue(queryA.exists(), "the source file must survive a rejected rename");
        assertTrue(queryB.exists(), "the target file must survive a rejected rename");
        assertEquals("//b", service.loadQuery(queryB));
    }

    @Test
    @DisplayName("Sollte Umbenennen auf denselben Namen als No-op behandeln")
    void testRenameQueryNoOp() {
        File saved = service.saveXPathQuery("SameName", "//x");
        File renamed = service.renameQuery(saved, "SameName");
        assertEquals(saved, renamed);
        assertTrue(saved.exists());
    }

    @Test
    @DisplayName("Sollte beim Umbenennen die XQuery-Erweiterung erhalten")
    void testRenameQueryPreservesXQueryExtension() {
        File saved = service.saveXQueryQuery("FlworQuery", "for $x in /r return $x");
        File renamed = service.renameQuery(saved, "RenamedFlwor");
        assertNotNull(renamed);
        assertEquals("RenamedFlwor.xquery", renamed.getName());
        assertEquals("for $x in /r return $x", service.loadQuery(renamed));
    }

    @Test
    @DisplayName("Sollte Daten zwischen Instanzen persistieren")
    void testPersistence() throws Exception {
        service.addFavorite("/persist.xml", "Persist", "PFolder");
        
        // Simulate application restart
        FavoritesService.resetInstanceForTests();

        FavoritesService newService = FavoritesService.getInstance();
        assertTrue(newService.isFavorite("/persist.xml"));
        assertEquals(1, newService.getFavoritesByFolder("PFolder").size());
    }
}
