package org.fxt.freexmltoolkit.domain;

import java.nio.file.Path;
import java.time.LocalDateTime;

public class Favorite {
    private String id;
    private String name;
    private Path filePath;
    private String category;
    private FileType fileType;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessed;
    private String description;

    public enum FileType {
        XML("XML Files", ".xml"),
        XSD("XSD Schema Files", ".xsd"),
        SCHEMATRON("Schematron Files", ".sch"),
        XSLT("XSLT Files", ".xsl", ".xslt"),
        XPATH("XPath Query Files", ".xpath"),
        XQUERY("XQuery Files", ".xquery", ".xq"),
        OTHER("Other Files", "");

        private final String displayName;
        private final String[] extensions;

        FileType(String displayName, String... extensions) {
            this.displayName = displayName;
            this.extensions = extensions;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String[] getExtensions() {
            return extensions;
        }

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

    public Favorite() {
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastAccessed = LocalDateTime.now();
        this.category = "General";
    }

    public Favorite(String name, Path filePath, String category) {
        this();
        this.name = name;
        this.filePath = filePath;
        this.category = category != null ? category : "General";
        this.fileType = FileType.fromPath(filePath);
    }

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

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
        this.fileType = FileType.fromPath(filePath);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(LocalDateTime lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

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