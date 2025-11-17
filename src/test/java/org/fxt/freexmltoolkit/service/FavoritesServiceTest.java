package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FavoritesService - file favorites management.
 * Uses reflection to reset singleton instance between tests.
 */
class FavoritesServiceTest {

    @TempDir
    Path tempDir;

    private FavoritesService favoritesService;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton instance using reflection
        java.lang.reflect.Field instanceField = FavoritesService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Override user.home for testing to use temp directory
        System.setProperty("user.home", tempDir.toString());

        favoritesService = FavoritesService.getInstance();
    }

    @Test
    @DisplayName("Should get singleton instance")
    void testGetInstance() {
        FavoritesService instance1 = FavoritesService.getInstance();
        FavoritesService instance2 = FavoritesService.getInstance();

        assertSame(instance1, instance2, "Should return same instance");
    }

    @Test
    @DisplayName("Should start with empty favorites")
    void testInitialState() {
        List<FileFavorite> favorites = favoritesService.getAllFavorites();

        assertNotNull(favorites);
        assertTrue(favorites.isEmpty(), "Should start with no favorites");
    }

    @Test
    @DisplayName("Should add favorite from file")
    void testAddFavoriteFromFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test.xml");
        Files.writeString(testFile, "<root/>");

        // Act
        favoritesService.addFavorite(testFile.toFile());

        // Assert
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        assertEquals(1, favorites.size());
        assertEquals("test", favorites.get(0).getName()); // Extension removed
        assertEquals(testFile.toAbsolutePath().toString(), favorites.get(0).getFilePath());
    }

    @Test
    @DisplayName("Should add favorite with custom name and folder")
    void testAddFavoriteWithCustomNameAndFolder() {
        // Arrange
        String filePath = "/path/to/file.xml";
        String name = "My Custom XML";
        String folder = "Important Files";

        // Act
        favoritesService.addFavorite(filePath, name, folder);

        // Assert
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        assertEquals(1, favorites.size());
        assertEquals(name, favorites.get(0).getName());
        assertEquals(filePath, favorites.get(0).getFilePath());
        assertEquals(folder, favorites.get(0).getFolderName());
    }

    @Test
    @DisplayName("Should add favorite object")
    void testAddFavoriteObject() {
        // Arrange
        FileFavorite favorite = new FileFavorite("Test File", "/path/to/test.xsd");

        // Act
        favoritesService.addFavorite(favorite);

        // Assert
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        assertEquals(1, favorites.size());
        assertEquals("Test File", favorites.get(0).getName());
    }

    @Test
    @DisplayName("Should not add duplicate favorites")
    void testNoDuplicates() {
        // Arrange
        String filePath = "/path/to/test.xml";

        // Act
        favoritesService.addFavorite(filePath, "First", null);
        favoritesService.addFavorite(filePath, "Second", null); // Same path

        // Assert
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        assertEquals(1, favorites.size(), "Should not add duplicate");
    }

    @Test
    @DisplayName("Should not add null favorite")
    void testAddNullFavorite() {
        // Act
        favoritesService.addFavorite((FileFavorite) null);

        // Assert
        assertTrue(favoritesService.getAllFavorites().isEmpty());
    }

    @Test
    @DisplayName("Should not add non-existent file")
    void testAddNonExistentFile() {
        // Act
        favoritesService.addFavorite(new File("/nonexistent/file.xml"));

        // Assert
        assertTrue(favoritesService.getAllFavorites().isEmpty());
    }

    @Test
    @DisplayName("Should remove favorite by ID")
    void testRemoveFavoriteById() {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "File 1", null);
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        String idToRemove = favorites.get(0).getId();

        // Act
        favoritesService.removeFavorite(idToRemove);

        // Assert
        assertTrue(favoritesService.getAllFavorites().isEmpty());
    }

    @Test
    @DisplayName("Should remove favorite by object")
    void testRemoveFavoriteByObject() {
        // Arrange
        FileFavorite favorite = new FileFavorite("Test", "/path/to/test.xml");
        favoritesService.addFavorite(favorite);

        // Act
        favoritesService.removeFavorite(favorite);

        // Assert
        assertTrue(favoritesService.getAllFavorites().isEmpty());
    }

    @Test
    @DisplayName("Should remove favorite by path")
    void testRemoveFavoriteByPath() {
        // Arrange
        String filePath = "/path/to/test.xml";
        favoritesService.addFavorite(filePath, "Test", null);

        // Act
        favoritesService.removeFavoriteByPath(filePath);

        // Assert
        assertTrue(favoritesService.getAllFavorites().isEmpty());
    }

    @Test
    @DisplayName("Should update favorite")
    void testUpdateFavorite() {
        // Arrange
        favoritesService.addFavorite("/path/to/test.xml", "Original Name", "Original Folder");
        FileFavorite favorite = favoritesService.getAllFavorites().get(0);
        String originalId = favorite.getId();

        // Act
        favorite.setName("Updated Name");
        favorite.setFolderName("Updated Folder");
        favoritesService.updateFavorite(favorite);

        // Assert
        FileFavorite updated = favoritesService.getFavoriteById(originalId);
        assertNotNull(updated);
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated Folder", updated.getFolderName());
    }

    @Test
    @DisplayName("Should move favorite to different folder")
    void testMoveFavoriteToFolder() {
        // Arrange
        favoritesService.addFavorite("/path/to/test.xml", "Test", "Folder1");
        String favoriteId = favoritesService.getAllFavorites().get(0).getId();

        // Act
        favoritesService.moveFavoriteToFolder(favoriteId, "Folder2");

        // Assert
        FileFavorite favorite = favoritesService.getFavoriteById(favoriteId);
        assertEquals("Folder2", favorite.getFolderName());
    }

    @Test
    @DisplayName("Should get favorites by folder")
    void testGetFavoritesByFolder() {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "File 1", "Folder1");
        favoritesService.addFavorite("/path/to/file2.xml", "File 2", "Folder1");
        favoritesService.addFavorite("/path/to/file3.xml", "File 3", "Folder2");

        // Act
        List<FileFavorite> folder1Favorites = favoritesService.getFavoritesByFolder("Folder1");
        List<FileFavorite> folder2Favorites = favoritesService.getFavoritesByFolder("Folder2");

        // Assert
        assertEquals(2, folder1Favorites.size());
        assertEquals(1, folder2Favorites.size());
    }

    @Test
    @DisplayName("Should get all folder names")
    void testGetAllFolders() {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "File 1", "Folder1");
        favoritesService.addFavorite("/path/to/file2.xml", "File 2", "Folder2");
        favoritesService.addFavorite("/path/to/file3.xml", "File 3", "Folder3");

        // Act
        Set<String> folders = favoritesService.getAllFolders();

        // Assert
        assertEquals(3, folders.size());
        assertTrue(folders.contains("Folder1"));
        assertTrue(folders.contains("Folder2"));
        assertTrue(folders.contains("Folder3"));
    }

    @Test
    @DisplayName("Should get favorite by ID")
    void testGetFavoriteById() {
        // Arrange
        favoritesService.addFavorite("/path/to/test.xml", "Test File", null);
        String id = favoritesService.getAllFavorites().get(0).getId();

        // Act
        FileFavorite favorite = favoritesService.getFavoriteById(id);

        // Assert
        assertNotNull(favorite);
        assertEquals("Test File", favorite.getName());
    }

    @Test
    @DisplayName("Should return null for non-existent ID")
    void testGetNonExistentFavorite() {
        FileFavorite favorite = favoritesService.getFavoriteById("non-existent-id");
        assertNull(favorite);
    }

    @Test
    @DisplayName("Should check if file is favorite")
    void testIsFavorite() {
        // Arrange
        String filePath = "/path/to/test.xml";
        favoritesService.addFavorite(filePath, "Test", null);

        // Act & Assert
        assertTrue(favoritesService.isFavorite(filePath));
        assertFalse(favoritesService.isFavorite("/other/path.xml"));
    }

    @Test
    @DisplayName("Should get favorites by file type")
    void testGetFavoritesByType() {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "XML File", null);
        favoritesService.addFavorite("/path/to/file2.xsd", "XSD File", null);
        favoritesService.addFavorite("/path/to/file3.xml", "Another XML", null);

        // Act
        List<FileFavorite> xmlFavorites = favoritesService.getFavoritesByType(FileFavorite.FileType.XML);
        List<FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(FileFavorite.FileType.XSD);

        // Assert
        assertEquals(2, xmlFavorites.size());
        assertEquals(1, xsdFavorites.size());
    }

    @Test
    @DisplayName("Should create new folder")
    void testCreateFolder() {
        // Act
        favoritesService.createFolder("New Folder");

        // Assert
        Set<String> folders = favoritesService.getAllFolders();
        assertTrue(folders.contains("New Folder"));
    }

    @Test
    @DisplayName("Should not create folder with null or empty name")
    void testCreateInvalidFolder() {
        // Act
        favoritesService.createFolder(null);
        favoritesService.createFolder("");

        // Assert
        Set<String> folders = favoritesService.getAllFolders();
        assertTrue(folders.isEmpty());
    }

    @Test
    @DisplayName("Should rename folder")
    void testRenameFolder() {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "File 1", "OldName");
        favoritesService.addFavorite("/path/to/file2.xml", "File 2", "OldName");

        // Act
        favoritesService.renameFolder("OldName", "NewName");

        // Assert
        List<FileFavorite> favoritesInNewFolder = favoritesService.getFavoritesByFolder("NewName");
        List<FileFavorite> favoritesInOldFolder = favoritesService.getFavoritesByFolder("OldName");

        assertEquals(2, favoritesInNewFolder.size());
        assertTrue(favoritesInOldFolder.isEmpty());
    }

    @Test
    @DisplayName("Should not rename folder with null names")
    void testRenameInvalidFolder() {
        // Arrange
        favoritesService.addFavorite("/path/to/file.xml", "File", "OldName");

        // Act
        favoritesService.renameFolder(null, "NewName");
        favoritesService.renameFolder("OldName", null);
        favoritesService.renameFolder("OldName", "OldName"); // Same name

        // Assert
        List<FileFavorite> favorites = favoritesService.getFavoritesByFolder("OldName");
        assertEquals(1, favorites.size()); // Should remain unchanged
    }

    @Test
    @DisplayName("Should delete folder and move favorites to Uncategorized")
    void testDeleteFolder() {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "File 1", "ToDelete");
        favoritesService.addFavorite("/path/to/file2.xml", "File 2", "ToDelete");

        // Act
        favoritesService.deleteFolder("ToDelete");

        // Assert
        List<FileFavorite> deletedFolder = favoritesService.getFavoritesByFolder("ToDelete");
        List<FileFavorite> uncategorized = favoritesService.getFavoritesByFolder("Uncategorized");

        assertTrue(deletedFolder.isEmpty());
        assertEquals(2, uncategorized.size());
    }

    @Test
    @DisplayName("Should not delete Uncategorized folder")
    void testCannotDeleteUncategorized() {
        // Arrange
        favoritesService.addFavorite("/path/to/file.xml", "File", null);

        // Act
        favoritesService.deleteFolder("Uncategorized");

        // Assert
        List<FileFavorite> uncategorized = favoritesService.getFavoritesByFolder("Uncategorized");
        assertEquals(1, uncategorized.size());
    }

    @Test
    @DisplayName("Should cleanup non-existent files")
    void testCleanupNonExistentFiles() throws Exception {
        // Arrange - Add mix of existing and non-existing files
        Path existingFile = tempDir.resolve("existing.xml");
        Files.writeString(existingFile, "<root/>");

        favoritesService.addFavorite(existingFile.toFile());
        favoritesService.addFavorite("/nonexistent/file1.xml", "Non-existent 1", null);
        favoritesService.addFavorite("/nonexistent/file2.xml", "Non-existent 2", null);

        assertEquals(3, favoritesService.getAllFavorites().size());

        // Act
        favoritesService.cleanupNonExistentFiles();

        // Assert
        List<FileFavorite> favorites = favoritesService.getAllFavorites();
        assertEquals(1, favorites.size());
        assertEquals(existingFile.toAbsolutePath().toString(), favorites.get(0).getFilePath());
    }

    @Test
    @DisplayName("Should persist favorites across instances")
    void testPersistence() throws Exception {
        // Arrange
        favoritesService.addFavorite("/path/to/file1.xml", "File 1", "Folder1");
        favoritesService.addFavorite("/path/to/file2.xml", "File 2", "Folder2");

        // Act - Create new instance (simulating app restart)
        java.lang.reflect.Field instanceField = FavoritesService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        FavoritesService newInstance = FavoritesService.getInstance();

        // Assert
        List<FileFavorite> loadedFavorites = newInstance.getAllFavorites();
        assertEquals(2, loadedFavorites.size());
    }
}
