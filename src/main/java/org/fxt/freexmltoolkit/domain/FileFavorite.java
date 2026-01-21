package org.fxt.freexmltoolkit.domain;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Represents a favorite file in the XML editor.
 * Supports XML, XSD, and Schematron files.
 */
public class FileFavorite {
    
    private String id;
    private String name;
    private String filePath;
    private FileType fileType;
    private String folderName;
    private LocalDateTime addedDate;
    private String description;
    private String iconColor;

    // Enhanced metadata fields
    private String alias;              // User-friendly name
    private String category;           // User-defined category
    private LocalDateTime lastAccessed; // Last time file was opened
    private int accessCount;           // Number of times accessed
    private long fileSize;             // File size in bytes
    private String checksum;           // MD5/SHA hash for change detection
    private LocalDateTime lastModified; // File modification time
    private String notes;              // User notes/annotations
    private String[] tags;             // User-defined tags
    private String thumbnail;          // Base64 encoded preview thumbnail
    private String projectName;        // Associated project
    private String[] relatedFiles;     // Related file paths (XSD, XSLT, etc.)
    private boolean isTemplate;        // Whether this is a template
    private boolean isPinned;          // Pinned to top of list
    private int colorCode;             // Custom color coding
    private String validationStatus;   // Last validation result
    
    /**
     * Enumeration of supported file types with associated display properties.
     */
    public enum FileType {
        /** XML document files */
        XML("XML Document", "bi-file-earmark-code", "#28a745"),
        /** XSD schema definition files */
        XSD("XSD Schema", "bi-file-earmark-check", "#007bff"),
        /** Schematron validation rule files */
        SCHEMATRON("Schematron Rules", "bi-file-earmark-ruled", "#dc3545"),
        /** XSLT transformation stylesheet files */
        XSLT("XSLT Stylesheet", "bi-file-earmark-arrow-right", "#fd7e14"),
        /** XPath query files */
        XPATH("XPath Query", "bi-code-slash", "#6f42c1"),
        /** XQuery script files */
        XQUERY("XQuery Query", "bi-braces", "#20c997"),
        /** Other/unrecognized file types */
        OTHER("Other", "bi-file-earmark", "#6c757d");

        private final String displayName;
        private final String iconLiteral;
        private final String defaultColor;

        FileType(String displayName, String iconLiteral, String defaultColor) {
            this.displayName = displayName;
            this.iconLiteral = iconLiteral;
            this.defaultColor = defaultColor;
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
         * Gets the Ikonli Bootstrap icon literal.
         *
         * @return the icon literal (e.g., "bi-file-earmark-code")
         */
        public String getIconLiteral() {
            return iconLiteral;
        }

        /**
         * Gets the default color in hex format.
         *
         * @return the default color (e.g., "#28a745")
         */
        public String getDefaultColor() {
            return defaultColor;
        }

        /**
         * Determines the file type from a file path based on its extension.
         *
         * @param filePath the file path to analyze
         * @return the determined file type, or {@link #OTHER} if not recognized
         */
        public static FileType fromExtension(String filePath) {
            if (filePath == null) return OTHER;
            String lower = filePath.toLowerCase();
            if (lower.endsWith(".xml")) return XML;
            if (lower.endsWith(".xsd")) return XSD;
            if (lower.endsWith(".sch")) return SCHEMATRON;
            if (lower.endsWith(".xsl") || lower.endsWith(".xslt")) return XSLT;
            if (lower.endsWith(".xpath")) return XPATH;
            if (lower.endsWith(".xquery") || lower.endsWith(".xq")) return XQUERY;
            return OTHER;
        }
    }
    
    /**
     * Creates a new FileFavorite with a generated UUID and current timestamp.
     */
    public FileFavorite() {
        this.id = java.util.UUID.randomUUID().toString();
        this.addedDate = LocalDateTime.now();
    }

    /**
     * Creates a new FileFavorite with the specified name and file path.
     *
     * @param name     the display name for the favorite
     * @param filePath the absolute path to the file
     */
    public FileFavorite(String name, String filePath) {
        this();
        this.name = name;
        this.filePath = filePath;
        this.fileType = FileType.fromExtension(filePath);
        this.iconColor = fileType.getDefaultColor();
    }

    /**
     * Creates a new FileFavorite with the specified name, file path, and folder name.
     *
     * @param name       the display name for the favorite
     * @param filePath   the absolute path to the file
     * @param folderName the folder/category name for organization
     */
    public FileFavorite(String name, String filePath, String folderName) {
        this(name, filePath);
        this.folderName = folderName;
    }

    /**
     * Checks if the file exists on the filesystem.
     *
     * @return true if the file exists, false otherwise
     */
    public boolean fileExists() {
        return filePath != null && new File(filePath).exists();
    }

    /**
     * Gets the filename without the directory path.
     *
     * @return the filename, or empty string if path is null
     */
    public String getFileName() {
        if (filePath == null) return "";
        return new File(filePath).getName();
    }
    
    // Getters and Setters

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
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the file path and automatically determines the file type.
     *
     * @param filePath the file path to set
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
        this.fileType = FileType.fromExtension(filePath);
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
     * Gets the folder name for organization.
     *
     * @return the folder name
     */
    public String getFolderName() {
        return folderName;
    }

    /**
     * Sets the folder name for organization.
     *
     * @param folderName the folder name to set
     */
    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    /**
     * Gets the date when this favorite was added.
     *
     * @return the added date
     */
    public LocalDateTime getAddedDate() {
        return addedDate;
    }

    /**
     * Sets the date when this favorite was added.
     *
     * @param addedDate the added date to set
     */
    public void setAddedDate(LocalDateTime addedDate) {
        this.addedDate = addedDate;
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
     * Gets the icon color in hex format.
     *
     * @return the icon color
     */
    public String getIconColor() {
        return iconColor;
    }

    /**
     * Sets the icon color in hex format.
     *
     * @param iconColor the icon color to set
     */
    public void setIconColor(String iconColor) {
        this.iconColor = iconColor;
    }

    // Enhanced metadata getters and setters

    /**
     * Gets the user-friendly alias.
     *
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Sets the user-friendly alias.
     *
     * @param alias the alias to set
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * Gets the user-defined category.
     *
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the user-defined category.
     *
     * @param category the category to set
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Gets the last accessed timestamp.
     * Falls back to added date if last accessed is null.
     *
     * @return the last accessed date, or added date as fallback
     */
    public LocalDateTime getLastAccessed() {
        // Return addedDate as fallback if lastAccessed is null
        return lastAccessed != null ? lastAccessed : addedDate;
    }

    /**
     * Sets the last accessed timestamp.
     *
     * @param lastAccessed the last accessed date to set
     */
    public void setLastAccessed(LocalDateTime lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    /**
     * Gets the number of times this file has been accessed.
     *
     * @return the access count
     */
    public int getAccessCount() {
        return accessCount;
    }

    /**
     * Sets the number of times this file has been accessed.
     *
     * @param accessCount the access count to set
     */
    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    /**
     * Gets the file size in bytes.
     *
     * @return the file size
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Sets the file size in bytes.
     *
     * @param fileSize the file size to set
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Gets the checksum for change detection (MD5/SHA hash).
     *
     * @return the checksum
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * Sets the checksum for change detection.
     *
     * @param checksum the checksum to set
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    /**
     * Gets the last modified timestamp of the file.
     *
     * @return the last modified date
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    /**
     * Sets the last modified timestamp of the file.
     *
     * @param lastModified the last modified date to set
     */
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * Gets the user notes/annotations.
     *
     * @return the notes
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Sets the user notes/annotations.
     *
     * @param notes the notes to set
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Gets the user-defined tags.
     *
     * @return array of tags
     */
    public String[] getTags() {
        return tags;
    }

    /**
     * Sets the user-defined tags.
     *
     * @param tags the tags to set
     */
    public void setTags(String[] tags) {
        this.tags = tags;
    }

    /**
     * Gets the base64-encoded preview thumbnail.
     *
     * @return the thumbnail
     */
    public String getThumbnail() {
        return thumbnail;
    }

    /**
     * Sets the base64-encoded preview thumbnail.
     *
     * @param thumbnail the thumbnail to set
     */
    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    /**
     * Gets the associated project name.
     *
     * @return the project name
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Sets the associated project name.
     *
     * @param projectName the project name to set
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Gets the related file paths (e.g., associated XSD, XSLT files).
     *
     * @return array of related file paths
     */
    public String[] getRelatedFiles() {
        return relatedFiles;
    }

    /**
     * Sets the related file paths.
     *
     * @param relatedFiles the related files to set
     */
    public void setRelatedFiles(String[] relatedFiles) {
        this.relatedFiles = relatedFiles;
    }

    /**
     * Checks if this file is marked as a template.
     *
     * @return true if this is a template
     */
    public boolean isTemplate() {
        return isTemplate;
    }

    /**
     * Sets whether this file is a template.
     *
     * @param template true to mark as template
     */
    public void setTemplate(boolean template) {
        isTemplate = template;
    }

    /**
     * Checks if this file is pinned to the top of the list.
     *
     * @return true if pinned
     */
    public boolean isPinned() {
        return isPinned;
    }

    /**
     * Sets whether this file is pinned to the top.
     *
     * @param pinned true to pin
     */
    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    /**
     * Gets the custom color code for visual distinction.
     *
     * @return the color code
     */
    public int getColorCode() {
        return colorCode;
    }

    /**
     * Sets the custom color code.
     *
     * @param colorCode the color code to set
     */
    public void setColorCode(int colorCode) {
        this.colorCode = colorCode;
    }

    /**
     * Gets the last validation result status.
     *
     * @return the validation status
     */
    public String getValidationStatus() {
        return validationStatus;
    }

    /**
     * Sets the last validation result status.
     *
     * @param validationStatus the validation status to set
     */
    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    /**
     * Gets the date when this favorite was added.
     * Alias for {@link #getAddedDate()} for compatibility.
     *
     * @return the date added
     */
    public LocalDateTime getDateAdded() {
        return addedDate;
    }

    /**
     * Sets the date when this favorite was added.
     * Alias for {@link #setAddedDate(LocalDateTime)} for compatibility.
     *
     * @param dateAdded the date added to set
     */
    public void setDateAdded(LocalDateTime dateAdded) {
        this.addedDate = dateAdded;
    }
}