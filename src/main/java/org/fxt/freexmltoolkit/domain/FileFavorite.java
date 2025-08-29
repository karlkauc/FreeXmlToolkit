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
    
    public enum FileType {
        XML("XML Document", "bi-file-earmark-code", "#28a745"),
        XSD("XSD Schema", "bi-file-earmark-check", "#007bff"),
        SCHEMATRON("Schematron Rules", "bi-file-earmark-ruled", "#dc3545"),
        XSLT("XSLT Stylesheet", "bi-file-earmark-arrow-right", "#fd7e14"),
        OTHER("Other", "bi-file-earmark", "#6c757d");
        
        private final String displayName;
        private final String iconLiteral;
        private final String defaultColor;
        
        FileType(String displayName, String iconLiteral, String defaultColor) {
            this.displayName = displayName;
            this.iconLiteral = iconLiteral;
            this.defaultColor = defaultColor;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getIconLiteral() {
            return iconLiteral;
        }
        
        public String getDefaultColor() {
            return defaultColor;
        }
        
        public static FileType fromExtension(String filePath) {
            if (filePath == null) return OTHER;
            String lower = filePath.toLowerCase();
            if (lower.endsWith(".xml")) return XML;
            if (lower.endsWith(".xsd")) return XSD;
            if (lower.endsWith(".sch")) return SCHEMATRON;
            if (lower.endsWith(".xsl") || lower.endsWith(".xslt")) return XSLT;
            return OTHER;
        }
    }
    
    public FileFavorite() {
        this.id = java.util.UUID.randomUUID().toString();
        this.addedDate = LocalDateTime.now();
    }
    
    public FileFavorite(String name, String filePath) {
        this();
        this.name = name;
        this.filePath = filePath;
        this.fileType = FileType.fromExtension(filePath);
        this.iconColor = fileType.getDefaultColor();
    }
    
    public FileFavorite(String name, String filePath, String folderName) {
        this(name, filePath);
        this.folderName = folderName;
    }
    
    public boolean fileExists() {
        return filePath != null && new File(filePath).exists();
    }
    
    public String getFileName() {
        if (filePath == null) return "";
        return new File(filePath).getName();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
        this.fileType = FileType.fromExtension(filePath);
    }
    
    public FileType getFileType() {
        return fileType;
    }
    
    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }
    
    public String getFolderName() {
        return folderName;
    }
    
    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }
    
    public LocalDateTime getAddedDate() {
        return addedDate;
    }
    
    public void setAddedDate(LocalDateTime addedDate) {
        this.addedDate = addedDate;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getIconColor() {
        return iconColor;
    }
    
    public void setIconColor(String iconColor) {
        this.iconColor = iconColor;
    }

    // Enhanced metadata getters and setters
    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getLastAccessed() {
        // Return addedDate as fallback if lastAccessed is null
        return lastAccessed != null ? lastAccessed : addedDate;
    }

    public void setLastAccessed(LocalDateTime lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String[] getRelatedFiles() {
        return relatedFiles;
    }

    public void setRelatedFiles(String[] relatedFiles) {
        this.relatedFiles = relatedFiles;
    }

    public boolean isTemplate() {
        return isTemplate;
    }

    public void setTemplate(boolean template) {
        isTemplate = template;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public int getColorCode() {
        return colorCode;
    }

    public void setColorCode(int colorCode) {
        this.colorCode = colorCode;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    // Alias for compatibility with existing code
    public LocalDateTime getDateAdded() {
        return addedDate;
    }

    public void setDateAdded(LocalDateTime dateAdded) {
        this.addedDate = dateAdded;
    }
}