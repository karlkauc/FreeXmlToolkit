package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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
        Field instanceField = FavoritesService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
        
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
    @DisplayName("Sollte Daten zwischen Instanzen persistieren")
    void testPersistence() throws Exception {
        service.addFavorite("/persist.xml", "Persist", "PFolder");
        
        // Simulate application restart
        Field instanceField = FavoritesService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
        
        FavoritesService newService = FavoritesService.getInstance();
        assertTrue(newService.isFavorite("/persist.xml"));
        assertEquals(1, newService.getFavoritesByFolder("PFolder").size());
    }
}
