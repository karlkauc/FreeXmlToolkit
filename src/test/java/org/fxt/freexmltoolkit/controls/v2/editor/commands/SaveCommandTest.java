package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SaveCommand.
 * Tests save functionality, backup creation, and error handling.
 *
 * @since 2.0
 */
class SaveCommandTest {

    @TempDir
    Path tempDir;

    private XsdEditorContext editorContext;
    private XsdSchema schema;
    private Path testFilePath;

    @BeforeEach
    void setUp() throws IOException {
        // Create test schema
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");
        XsdElement rootElement = new XsdElement("root");
        rootElement.setType("xs:string");
        schema.addChild(rootElement);

        // Create schema model
        XsdSchemaModel schemaModel = new XsdSchemaModel();
        schemaModel.setTargetNamespace("http://example.com/test");

        // Create editor context
        editorContext = new XsdEditorContext(schemaModel);

        // Create test file path
        testFilePath = tempDir.resolve("test.xsd");
    }

    @AfterEach
    void tearDown() {
        // Cleanup is handled by @TempDir
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should save XSD file successfully")
    void testExecuteSavesFile() {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertTrue(Files.exists(testFilePath), "File should exist after save");
        assertTrue(command.isSaveSuccessful(), "Save should be marked as successful");
    }

    @Test
    @DisplayName("execute() should create backup when requested")
    void testExecuteCreatesBackup() throws IOException {
        // Arrange - create initial file
        Files.writeString(testFilePath, "<?xml version=\"1.0\"?>\n<xs:schema/>");

        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, true);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertNotNull(command.getBackupPath(), "Backup path should not be null");
        assertTrue(Files.exists(command.getBackupPath()), "Backup file should exist");
        assertTrue(command.getBackupPath().getFileName().toString().contains("_backup_"),
                "Backup filename should contain '_backup_'");
    }

    @Test
    @DisplayName("execute() should not create backup for new file")
    void testExecuteNoBackupForNewFile() {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, true);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertNull(command.getBackupPath(), "No backup should be created for new file");
    }

    @Test
    @DisplayName("execute() should write valid XSD content")
    void testExecuteWritesValidXsdContent() throws IOException {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        String content = Files.readString(testFilePath);
        assertTrue(content.contains("<?xml"), "Content should contain XML declaration");
        assertTrue(content.contains("xs:schema"), "Content should contain xs:schema");
        assertTrue(content.contains("targetNamespace=\"http://example.com/test\""),
                "Content should contain target namespace");
    }

    @Test
    @DisplayName("execute() should reset dirty flag on successful save")
    void testExecuteResetsDirtyFlag() {
        // Arrange
        editorContext.setDirty(true);
        assertTrue(editorContext.isDirty(), "Context should be dirty before save");

        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertFalse(editorContext.isDirty(), "Context should not be dirty after successful save");
    }

    // ========== File Size and Metadata Tests ==========

    @Test
    @DisplayName("execute() should create file with non-zero size")
    void testExecuteCreatesNonEmptyFile() throws IOException {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        long fileSize = Files.size(testFilePath);
        assertTrue(fileSize > 0, "File size should be greater than 0");
    }

    @Test
    @DisplayName("getBackupPath() should return actual backup path with timestamp")
    void testGetBackupPathReturnsActualPath() throws IOException {
        // Arrange - create initial file
        Files.writeString(testFilePath, "<?xml version=\"1.0\"?>\n<xs:schema/>");

        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, true);

        // Act
        command.execute();
        Path backupPath = command.getBackupPath();

        // Assert
        assertNotNull(backupPath, "Backup path should not be null");
        String backupFileName = backupPath.getFileName().toString();
        assertTrue(backupFileName.matches(".*_backup_\\d{8}_\\d{6}\\.xsd"),
                "Backup filename should match pattern with timestamp");
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should return false (not undoable)")
    void testUndoReturnsFalse() {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false");
    }

    @Test
    @DisplayName("canUndo() should return false")
    void testCanUndoReturnsFalse() {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act & Assert
        assertFalse(command.canUndo(), "Save commands should not be undoable");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new SaveCommand(null, schema, testFilePath, false),
                "Constructor should throw IllegalArgumentException for null context");
    }

    @Test
    @DisplayName("constructor should throw exception for null schema")
    void testConstructorThrowsExceptionForNullSchema() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new SaveCommand(editorContext, null, testFilePath, false),
                "Constructor should throw IllegalArgumentException for null schema");
    }

    @Test
    @DisplayName("constructor should throw exception for null filePath")
    void testConstructorThrowsExceptionForNullFilePath() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new SaveCommand(editorContext, schema, null, false),
                "Constructor should throw IllegalArgumentException for null filePath");
    }

    // ========== Merge Tests ==========

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        // Arrange
        SaveCommand command1 = new SaveCommand(editorContext, schema, testFilePath, false);
        SaveCommand command2 = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Save commands should not be mergeable");
    }

    // ========== Description Tests ==========

    @Test
    @DisplayName("getDescription() should contain filename")
    void testGetDescriptionContainsFilename() {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("test.xsd"), "Description should contain filename");
    }

    @Test
    @DisplayName("getFilePath() should return correct file path")
    void testGetFilePathReturnsCorrectPath() {
        // Arrange
        SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

        // Act
        Path returnedPath = command.getFilePath();

        // Assert
        assertEquals(testFilePath, returnedPath, "getFilePath() should return the correct path");
    }
}
