package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serializes XML model to XML text.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Pretty printing with configurable indentation</li>
 *   <li>Automatic timestamped backups</li>
 *   <li>Character encoding support (default UTF-8)</li>
 *   <li>File and string output</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlSerializer serializer = new XmlSerializer();
 * String xml = serializer.serialize(document);
 *
 * // Save to file with backup
 * serializer.saveToFile(document, "output.xml", true);
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlSerializer {

    private static final Logger logger = LogManager.getLogger(XmlSerializer.class);

    /**
     * Backup file suffix format.
     */
    private static final String BACKUP_SUFFIX = ".backup_%s";

    /**
     * Date format for backup files.
     */
    private static final DateTimeFormatter BACKUP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Default indentation (2 spaces).
     */
    private static final int DEFAULT_INDENT = 2;

    /**
     * Indentation size (number of spaces per level).
     */
    private int indentSize = DEFAULT_INDENT;

    /**
     * Whether to include XML declaration.
     */
    private boolean includeXmlDeclaration = true;

    /**
     * Constructs a new XmlSerializer with default settings.
     */
    public XmlSerializer() {
    }

    /**
     * Constructs a new XmlSerializer with custom indent size.
     *
     * @param indentSize the number of spaces per indentation level
     */
    public XmlSerializer(int indentSize) {
        this.indentSize = indentSize;
    }

    // ==================== Serialization Methods ====================

    /**
     * Serializes a document to XML string.
     *
     * @param document the document to serialize
     * @return the XML string
     */
    public String serialize(XmlDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        return document.serialize(0);
    }

    /**
     * Serializes any XML node to string.
     *
     * @param node the node to serialize
     * @return the XML string
     */
    public String serialize(XmlNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        return node.serialize(0);
    }

    /**
     * Serializes a document to XML string with custom indentation.
     *
     * @param document    the document to serialize
     * @param prettyPrint whether to pretty print
     * @return the XML string
     */
    public String serialize(XmlDocument document, boolean prettyPrint) {
        if (!prettyPrint) {
            // Compact serialization (no indentation)
            return serializeCompact(document);
        }
        return serialize(document);
    }

    /**
     * Serializes a document without any formatting (compact).
     *
     * @param document the document to serialize
     * @return the compact XML string
     */
    private String serializeCompact(XmlDocument document) {
        // For now, just use the standard serialize
        // In a full implementation, we would remove all extra whitespace
        return document.serialize(0).replaceAll("\\n\\s*", "");
    }

    // ==================== File I/O Methods ====================

    /**
     * Saves a document to a file.
     *
     * @param document the document to save
     * @param filePath the file path
     * @throws IOException if an I/O error occurs
     */
    public void saveToFile(XmlDocument document, String filePath) throws IOException {
        saveToFile(document, filePath, false);
    }

    /**
     * Saves a document to a file with optional backup.
     *
     * @param document     the document to save
     * @param filePath     the file path
     * @param createBackup whether to create a timestamped backup
     * @throws IOException if an I/O error occurs
     */
    public void saveToFile(XmlDocument document, String filePath, boolean createBackup) throws IOException {
        Path path = Paths.get(filePath);

        // Create backup if requested and file exists
        if (createBackup && Files.exists(path)) {
            createBackup(path);
        }

        // Serialize to string
        String xml = serialize(document);

        // Write to file
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(filePath),
                        document.getEncoding() != null ? document.getEncoding() : "UTF-8"))) {
            writer.write(xml);
        }
    }

    /**
     * Saves a document to a file with specified encoding.
     *
     * @param document     the document to save
     * @param filePath     the file path
     * @param encoding     the character encoding
     * @param createBackup whether to create a backup
     * @throws IOException if an I/O error occurs
     */
    public void saveToFile(XmlDocument document, String filePath, String encoding, boolean createBackup) throws IOException {
        Path path = Paths.get(filePath);

        // Create backup if requested and file exists
        if (createBackup && Files.exists(path)) {
            createBackup(path);
        }

        // Update document encoding
        document.setEncoding(encoding);

        // Serialize to string
        String xml = serialize(document);

        // Write to file
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(filePath),
                        encoding))) {
            writer.write(xml);
        }
    }

    /**
     * Loads a document from a file.
     * Note: This method uses XmlParser to parse the file.
     *
     * @param filePath the file path
     * @return the loaded document
     * @throws IOException if an I/O error occurs
     */
    public XmlDocument loadFromFile(String filePath) throws IOException {
        // Read file content
        String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);

        // Parse using XmlParser
        XmlParser parser = new XmlParser();
        return parser.parse(content);
    }

    // ==================== Backup Methods ====================

    /**
     * Creates a timestamped backup of a file.
     * The backup location is determined by application settings:
     * - If "use separate directory" is enabled, backups go to the configured backup directory
     * - Otherwise, backups are created in the same directory as the original file
     *
     * @param originalPath the original file path
     * @throws IOException if an I/O error occurs
     */
    private void createBackup(Path originalPath) throws IOException {
        // Determine backup directory based on settings
        Path backupDir;
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);

        if (propertiesService.isBackupUseSeparateDirectory()) {
            backupDir = Path.of(propertiesService.getBackupDirectory());
            // Auto-create the backup directory if it doesn't exist
            Files.createDirectories(backupDir);
            logger.debug("Using separate backup directory: {}", backupDir);
        } else {
            backupDir = originalPath.getParent();
        }

        // Create backup filename with timestamp
        String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
        String backupFileName = originalPath.getFileName().toString() +
                String.format(BACKUP_SUFFIX, timestamp);
        Path backupPath = backupDir.resolve(backupFileName);

        Files.copy(originalPath, backupPath);
        logger.info("Created backup: {}", backupPath);
    }

    /**
     * Creates a backup with a custom suffix.
     *
     * @param filePath     the file path
     * @param backupSuffix the backup suffix
     * @throws IOException if an I/O error occurs
     */
    public void createBackupWithSuffix(String filePath, String backupSuffix) throws IOException {
        Path originalPath = Paths.get(filePath);
        Path backupPath = Paths.get(filePath + backupSuffix);

        if (Files.exists(originalPath)) {
            Files.copy(originalPath, backupPath);
        }
    }

    /**
     * Deletes old backup files, keeping only the most recent N backups.
     *
     * @param filePath  the original file path
     * @param keepCount the number of backups to keep
     * @throws IOException if an I/O error occurs
     */
    public void cleanupOldBackups(String filePath, int keepCount) throws IOException {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();
        String fileName = path.getFileName().toString();

        if (parentDir == null || !Files.exists(parentDir)) {
            return;
        }

        // Find all backup files for this file
        Files.list(parentDir)
                .filter(p -> p.getFileName().toString().startsWith(fileName + ".backup_"))
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .skip(keepCount)
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore errors when deleting old backups
                    }
                });
    }

    // ==================== Configuration Methods ====================

    /**
     * Sets the indentation size.
     *
     * @param indentSize the number of spaces per level
     */
    public void setIndentSize(int indentSize) {
        if (indentSize < 0) {
            throw new IllegalArgumentException("Indent size must be >= 0");
        }
        this.indentSize = indentSize;
    }

    /**
     * Returns the indentation size.
     *
     * @return the indent size
     */
    public int getIndentSize() {
        return indentSize;
    }

    /**
     * Sets whether to include the XML declaration.
     *
     * @param includeXmlDeclaration true to include
     */
    public void setIncludeXmlDeclaration(boolean includeXmlDeclaration) {
        this.includeXmlDeclaration = includeXmlDeclaration;
    }

    /**
     * Returns whether XML declaration is included.
     *
     * @return true if included
     */
    public boolean isIncludeXmlDeclaration() {
        return includeXmlDeclaration;
    }

    // ==================== Utility Methods ====================

    /**
     * Validates that an XML string is well-formed.
     *
     * @param xml the XML string to validate
     * @return true if well-formed
     */
    public boolean isWellFormed(String xml) {
        try {
            XmlParser parser = new XmlParser();
            parser.parse(xml);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Formats an XML string with pretty printing.
     *
     * @param xml the XML string to format
     * @return the formatted XML
     */
    public String format(String xml) {
        try {
            XmlParser parser = new XmlParser();
            XmlDocument doc = parser.parse(xml);
            return serialize(doc);
        } catch (Exception e) {
            // Return original if parsing fails
            return xml;
        }
    }
}
