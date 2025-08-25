package org.fxt.freexmltoolkit.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FileFavorite;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing file favorites in the XML Editor.
 * Handles persistence, organization, and retrieval of favorite files.
 */
public class FavoritesService {
    
    private static final Logger logger = LogManager.getLogger(FavoritesService.class);
    private static FavoritesService instance;
    
    private final Path favoritesFile;
    private final Gson gson;
    private List<FileFavorite> favorites;
    private final Map<String, List<FileFavorite>> favoritesByFolder;
    
    private FavoritesService() {
        // Initialize favorites file in user's home directory
        String userHome = System.getProperty("user.home");
        Path configDir = Paths.get(userHome, ".freexmltoolkit");
        
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
        
        this.favoritesFile = configDir.resolve("favorites.json");
        
        // Configure Gson with custom date adapter
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setPrettyPrinting()
            .create();
        
        this.favorites = new ArrayList<>();
        this.favoritesByFolder = new HashMap<>();
        
        loadFavorites();
    }
    
    public static synchronized FavoritesService getInstance() {
        if (instance == null) {
            instance = new FavoritesService();
        }
        return instance;
    }
    
    /**
     * Load favorites from the JSON file
     */
    private void loadFavorites() {
        if (!Files.exists(favoritesFile)) {
            logger.info("No favorites file found, starting with empty list");
            return;
        }
        
        try {
            String json = Files.readString(favoritesFile);
            Type listType = new TypeToken<List<FileFavorite>>(){}.getType();
            favorites = gson.fromJson(json, listType);
            
            if (favorites == null) {
                favorites = new ArrayList<>();
            }
            
            organizeFavoritesByFolder();
            logger.info("Loaded {} favorites", favorites.size());
            
        } catch (Exception e) {
            logger.error("Failed to load favorites", e);
            favorites = new ArrayList<>();
        }
    }
    
    /**
     * Save favorites to the JSON file
     */
    private void saveFavorites() {
        try {
            String json = gson.toJson(favorites);
            Files.writeString(favoritesFile, json);
            logger.debug("Saved {} favorites", favorites.size());
        } catch (IOException e) {
            logger.error("Failed to save favorites", e);
        }
    }
    
    /**
     * Organize favorites by folder
     */
    private void organizeFavoritesByFolder() {
        favoritesByFolder.clear();
        for (FileFavorite favorite : favorites) {
            String folder = favorite.getFolderName() != null ? favorite.getFolderName() : "Uncategorized";
            favoritesByFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(favorite);
        }
    }
    
    /**
     * Add a new favorite
     */
    public void addFavorite(FileFavorite favorite) {
        if (favorite == null || favorite.getFilePath() == null) {
            logger.warn("Cannot add null or invalid favorite");
            return;
        }
        
        // Check if already exists
        boolean exists = favorites.stream()
            .anyMatch(f -> f.getFilePath().equals(favorite.getFilePath()));
        
        if (exists) {
            logger.info("File already in favorites: {}", favorite.getFilePath());
            return;
        }
        
        favorites.add(favorite);
        organizeFavoritesByFolder();
        saveFavorites();
        logger.info("Added favorite: {}", favorite.getName());
    }
    
    /**
     * Add a file to favorites with a custom name
     */
    public void addFavorite(String filePath, String name, String folderName) {
        FileFavorite favorite = new FileFavorite(name, filePath, folderName);
        addFavorite(favorite);
    }
    
    /**
     * Add a file to favorites with auto-generated name
     */
    public void addFavorite(File file) {
        if (file == null || !file.exists()) {
            logger.warn("Cannot add non-existent file to favorites");
            return;
        }
        
        String name = file.getName();
        // Remove extension for cleaner display
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        
        FileFavorite favorite = new FileFavorite(name, file.getAbsolutePath());
        addFavorite(favorite);
    }
    
    /**
     * Remove a favorite by ID
     */
    public void removeFavorite(String id) {
        favorites.removeIf(f -> f.getId().equals(id));
        organizeFavoritesByFolder();
        saveFavorites();
        logger.info("Removed favorite with ID: {}", id);
    }
    
    /**
     * Remove a favorite by file path
     */
    public void removeFavoriteByPath(String filePath) {
        favorites.removeIf(f -> f.getFilePath().equals(filePath));
        organizeFavoritesByFolder();
        saveFavorites();
        logger.info("Removed favorite: {}", filePath);
    }
    
    /**
     * Update a favorite
     */
    public void updateFavorite(FileFavorite favorite) {
        if (favorite == null || favorite.getId() == null) return;
        
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).getId().equals(favorite.getId())) {
                favorites.set(i, favorite);
                organizeFavoritesByFolder();
                saveFavorites();
                logger.info("Updated favorite: {}", favorite.getName());
                return;
            }
        }
    }
    
    /**
     * Move a favorite to a different folder
     */
    public void moveFavoriteToFolder(String favoriteId, String newFolder) {
        FileFavorite favorite = getFavoriteById(favoriteId);
        if (favorite != null) {
            favorite.setFolderName(newFolder);
            updateFavorite(favorite);
        }
    }
    
    /**
     * Get all favorites
     */
    public List<FileFavorite> getAllFavorites() {
        return new ArrayList<>(favorites);
    }
    
    /**
     * Get favorites by folder
     */
    public List<FileFavorite> getFavoritesByFolder(String folderName) {
        return favoritesByFolder.getOrDefault(folderName, new ArrayList<>());
    }
    
    /**
     * Get all folder names
     */
    public Set<String> getAllFolders() {
        return new HashSet<>(favoritesByFolder.keySet());
    }
    
    /**
     * Get a favorite by ID
     */
    public FileFavorite getFavoriteById(String id) {
        return favorites.stream()
            .filter(f -> f.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if a file is in favorites
     */
    public boolean isFavorite(String filePath) {
        return favorites.stream()
            .anyMatch(f -> f.getFilePath().equals(filePath));
    }
    
    /**
     * Get favorites by file type
     */
    public List<FileFavorite> getFavoritesByType(FileFavorite.FileType type) {
        return favorites.stream()
            .filter(f -> f.getFileType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Create a new folder
     */
    public void createFolder(String folderName) {
        if (folderName != null && !folderName.isEmpty()) {
            favoritesByFolder.putIfAbsent(folderName, new ArrayList<>());
            logger.info("Created folder: {}", folderName);
        }
    }
    
    /**
     * Rename a folder
     */
    public void renameFolder(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        
        List<FileFavorite> favoritesInFolder = favoritesByFolder.get(oldName);
        if (favoritesInFolder != null) {
            favoritesInFolder.forEach(f -> f.setFolderName(newName));
            organizeFavoritesByFolder();
            saveFavorites();
            logger.info("Renamed folder from {} to {}", oldName, newName);
        }
    }
    
    /**
     * Delete a folder (moves favorites to Uncategorized)
     */
    public void deleteFolder(String folderName) {
        if (folderName == null || "Uncategorized".equals(folderName)) return;
        
        List<FileFavorite> favoritesInFolder = favoritesByFolder.get(folderName);
        if (favoritesInFolder != null) {
            favoritesInFolder.forEach(f -> f.setFolderName(null));
            organizeFavoritesByFolder();
            saveFavorites();
            logger.info("Deleted folder: {}", folderName);
        }
    }
    
    /**
     * Clean up non-existent files from favorites
     */
    public void cleanupNonExistentFiles() {
        int originalSize = favorites.size();
        favorites.removeIf(f -> !f.fileExists());
        
        if (favorites.size() < originalSize) {
            organizeFavoritesByFolder();
            saveFavorites();
            logger.info("Removed {} non-existent files from favorites", originalSize - favorites.size());
        }
    }
    
    /**
     * Custom adapter for LocalDateTime serialization
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
            com.google.gson.JsonDeserializer<LocalDateTime> {
        
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, Type typeOfSrc,
                com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(formatter.format(src));
        }
        
        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, Type typeOfT,
                com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}