package org.fxt.freexmltoolkit.domain;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Represents a favorite file entry in the application.
 * Favorites allow users to quickly access frequently used files
 * such as XML, XSD, Schematron, XSLT, XPath, and XQuery files.
 */
public class Favorite {
    private String id;
    private String name;
    private Path filePath;
    private String category;
    private FileType fileType;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessed;
    private String description;

    /**
     * Enumeration of supported file types for favorites.
     */
    public enum FileType {
        /** XML document files */
        XML("XML Files", ".xml"),
        /** XSD schema files */
        XSD("XSD Schema Files", ".xsd"),
        /** Schematron validation files */
        SCHEMATRON("Schematron Files", ".sch"),
        /** XSLT transformation files */
        XSLT("XSLT Files", ".xsl", ".xslt"),
        /** XPath query files */
        XPATH("XPath Query Files", ".xpath"),
        /** XQuery script files */
        XQUERY("XQuery Files", ".xquery", ".xq"),
        /** Other file types */
        OTHER("Other Files", "");

        private final String displayName;
        private final String[] extensions;

        FileType(String displayName, String... extensions) {
            this.displayName = displayName;
            this.extensions = extensions;
        }

        /**
         * Gets the human-readable display name.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Gets the file extensions associated with this type.
         *
         * @return array of file extensions
         */
        public String[] getExtensions() {
            return extensions;
        }

        /**
         * Determines the file type from a file path.
         *
         * @param path the file path
         * @return the determined file type, or {@link #OTHER} if not recognized
         */
        public static FileType fromPath(Path path) {
            String fileName = path.getFileName().toString().toLowerCase();
            for (FileType type : values()) {
                for (String ext : type.extensions) {
                    if (!ext.isEmpty() && fileName.endsWith(ext)) {
                        return type;
                    }
                }
            }
            return OTHER;
        }
    }

    /**
     * Creates a new Favorite with default values.
     */
    public Favorite() {
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastAccessed = LocalDateTime.now();
        this.category = "General";
    }

    /**
     * Creates a new Favorite with the specified name, file path, and category.
     *
     * @param name     the display name for the favorite
     * @param filePath the path to the file
     * @param category the category for organization (null defaults to "General")
     */
    public Favorite(String name, Path filePath, String category) {
        this();
        this.name = name;
        this.filePath = filePath;
        this.category = category != null ? category : "General";
        this.fileType = FileType.fromPath(filePath);
    }

    /**
     * Gets the unique identifier.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier.
     *
     * @param id the ID to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the display name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the file path.
     *
     * @return the file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Sets the file path and automatically determines the file type.
     *
     * @param filePath the file path to set
     */
    public void setFilePath(Path filePath) {
        this.filePath = filePath;
        this.fileType = FileType.fromPath(filePath);
    }

    /**
     * Gets the category.
     *
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category.
     *
     * @param category the category to set
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Gets the file type.
     *
     * @return the file type
     */
    public FileType getFileType() {
        return fileType;
    }

    /**
     * Sets the file type.
     *
     * @param fileType the file type to set
     */
    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return the creation date and time
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the creation date and time to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the last accessed timestamp.
     *
     * @return the last accessed date and time
     */
    public LocalDateTime getLastAccessed() {
        return lastAccessed;
    }

    /**
     * Sets the last accessed timestamp.
     *
     * @param lastAccessed the last accessed date and time to set
     */
    public void setLastAccessed(LocalDateTime lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Updates the last accessed timestamp to the current time.
     */
    public void updateLastAccessed() {
        this.lastAccessed = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Favorite favorite = (Favorite) o;
        return id.equals(favorite.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + fileType.getDisplayName() + ")";
    }
}