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
}