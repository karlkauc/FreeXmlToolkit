package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.MultiFileXsdSerializer.SaveResult;
import org.fxt.freexmltoolkit.controls.v2.model.IncludeSourceInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

        // Create editor context
        editorContext = new XsdEditorContext(schema);

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

    // ========== Multi-File Save Tests ==========

    @Nested
    @DisplayName("Multi-File Save")
    class MultiFileSaveTests {

        private XsdSchema multiFileSchema;
        private XsdEditorContext multiFileContext;
        private Path mainFilePath;
        private Path includeFilePath;

        @BeforeEach
        void setUpMultiFile() throws IOException {
            // Create schema with nodes from main and include files
            multiFileSchema = new XsdSchema();
            multiFileSchema.setTargetNamespace("http://example.com/multifile");

            // Create include directory
            Path includeDir = tempDir.resolve("include");
            Files.createDirectories(includeDir);

            mainFilePath = tempDir.resolve("main.xsd");
            includeFilePath = includeDir.resolve("types.xsd");

            // Add main file element
            XsdElement mainElement = new XsdElement("MainElement");
            mainElement.setType("xs:string");
            IncludeSourceInfo mainSourceInfo = IncludeSourceInfo.forMainSchema(mainFilePath);
            mainElement.setSourceInfo(mainSourceInfo);
            multiFileSchema.addChild(mainElement);

            // Add included file element
            XsdElement includedElement = new XsdElement("IncludedElement");
            includedElement.setType("xs:int");
            IncludeSourceInfo includeSourceInfo = IncludeSourceInfo.forIncludedSchema(
                    includeFilePath, "include/types.xsd", null);
            includedElement.setSourceInfo(includeSourceInfo);
            multiFileSchema.addChild(includedElement);

            // Set main schema path
            multiFileSchema.setMainSchemaPath(mainFilePath);

            multiFileContext = new XsdEditorContext(multiFileSchema);
        }

        @Test
        @DisplayName("execute() should detect multi-file schema")
        void testExecuteDetectsMultiFileSchema() {
            // Arrange
            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            boolean result = command.execute();

            // Assert
            assertTrue(result, "execute() should return true");
            assertTrue(command.isMultiFileSave(), "Should detect multi-file schema");
        }

        @Test
        @DisplayName("execute() should save multiple files")
        void testExecuteSavesMultipleFiles() {
            // Arrange
            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            boolean result = command.execute();

            // Assert
            assertTrue(result, "execute() should return true");
            assertTrue(Files.exists(mainFilePath), "Main file should exist");
            assertTrue(Files.exists(includeFilePath), "Include file should exist");
        }

        @Test
        @DisplayName("getSavedFileCount() should return correct count for multi-file save")
        void testGetSavedFileCountMultiFile() {
            // Arrange
            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            command.execute();

            // Assert
            assertEquals(2, command.getSavedFileCount(), "Should have saved 2 files");
        }

        @Test
        @DisplayName("getMultiFileSaveResults() should return results for each file")
        void testGetMultiFileSaveResultsReturnsAllFiles() {
            // Arrange
            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            command.execute();
            Map<Path, SaveResult> results = command.getMultiFileSaveResults();

            // Assert
            assertNotNull(results, "Results should not be null");
            assertEquals(2, results.size(), "Should have results for 2 files");
            assertTrue(results.containsKey(mainFilePath), "Results should contain main file");
            assertTrue(results.containsKey(includeFilePath), "Results should contain include file");
        }

        @Test
        @DisplayName("getSaveSummary() should show all saved files")
        void testGetSaveSummaryMultiFile() {
            // Arrange
            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            command.execute();
            String summary = command.getSaveSummary();

            // Assert
            assertTrue(summary.contains("2"), "Summary should mention file count");
            assertTrue(summary.contains("main.xsd"), "Summary should contain main file name");
            assertTrue(summary.contains("types.xsd"), "Summary should contain include file name");
        }

        @Test
        @DisplayName("execute() should create backups for all files when requested")
        void testExecuteCreatesBackupsForAllFiles() throws IOException {
            // Arrange - create existing files
            Files.writeString(mainFilePath, "<?xml version=\"1.0\"?>\n<xs:schema/>");
            Files.writeString(includeFilePath, "<?xml version=\"1.0\"?>\n<xs:schema/>");

            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, true);

            // Act
            boolean result = command.execute();
            Map<Path, SaveResult> results = command.getMultiFileSaveResults();

            // Assert
            assertTrue(result, "execute() should return true");
            assertNotNull(results, "Results should not be null");

            // Check backup was created for main file
            SaveResult mainResult = results.get(mainFilePath);
            assertNotNull(mainResult, "Main file result should exist");
            assertNotNull(mainResult.backupPath(), "Main file should have backup");
            assertTrue(Files.exists(mainResult.backupPath()), "Main file backup should exist");
        }

        @Test
        @DisplayName("execute() should reset dirty flag on successful multi-file save")
        void testExecuteResetsDirtyFlagMultiFile() {
            // Arrange
            multiFileContext.setDirty(true);

            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            command.execute();

            // Assert
            assertFalse(multiFileContext.isDirty(), "Dirty flag should be reset after successful save");
        }

        @Test
        @DisplayName("each file should contain correct elements")
        void testEachFileContainsCorrectElements() throws IOException {
            // Arrange
            SaveCommand command = new SaveCommand(multiFileContext, multiFileSchema, mainFilePath, false);

            // Act
            command.execute();

            // Assert
            String mainContent = Files.readString(mainFilePath);
            String includeContent = Files.readString(includeFilePath);

            assertTrue(mainContent.contains("MainElement"), "Main file should contain MainElement");
            assertTrue(includeContent.contains("IncludedElement"), "Include file should contain IncludedElement");
        }
    }

    @Nested
    @DisplayName("Single-File Save Detection")
    class SingleFileSaveDetectionTests {

        @Test
        @DisplayName("isMultiFileSave() should return false for single-file schema")
        void testIsMultiFileSaveReturnsFalseForSingleFile() {
            // Arrange - schema without include source info (default from setUp)
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

            // Act
            command.execute();

            // Assert
            assertFalse(command.isMultiFileSave(), "Single-file schema should not be multi-file save");
        }

        @Test
        @DisplayName("getSavedFileCount() should return 1 for single-file save")
        void testGetSavedFileCountSingleFile() {
            // Arrange
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

            // Act
            command.execute();

            // Assert
            assertEquals(1, command.getSavedFileCount(), "Single-file save should count as 1");
        }

        @Test
        @DisplayName("getMultiFileSaveResults() should return null for single-file save")
        void testGetMultiFileSaveResultsNullForSingleFile() {
            // Arrange
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

            // Act
            command.execute();

            // Assert
            assertNull(command.getMultiFileSaveResults(),
                    "Multi-file results should be null for single-file save");
        }

        @Test
        @DisplayName("getSaveSummary() should show single file for single-file save")
        void testGetSaveSummarySingleFile() {
            // Arrange
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

            // Act
            command.execute();
            String summary = command.getSaveSummary();

            // Assert
            assertTrue(summary.contains("test.xsd"), "Summary should contain filename");
            assertFalse(summary.contains("files:"), "Summary should not show multi-file format");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("getSaveSummary() should return 'Save failed' when save unsuccessful")
        void testGetSaveSummaryWhenFailed() {
            // Arrange
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);
            // Don't execute - save not performed

            // Act
            String summary = command.getSaveSummary();

            // Assert
            assertEquals("Save failed", summary, "Summary should indicate failure when not saved");
        }

        @Test
        @DisplayName("getSavedFileCount() should return 0 when save unsuccessful")
        void testGetSavedFileCountWhenFailed() {
            // Arrange
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);
            // Don't execute - save not performed

            // Act
            int count = command.getSavedFileCount();

            // Assert
            assertEquals(0, count, "File count should be 0 when not saved");
        }

        @Test
        @DisplayName("schema with null children should use single-file save")
        void testSchemaWithNullChildrenUsesSingleFileSave() {
            // Arrange
            XsdSchema emptySchema = new XsdSchema();
            XsdEditorContext emptyContext = new XsdEditorContext(emptySchema);
            SaveCommand command = new SaveCommand(emptyContext, emptySchema, testFilePath, false);

            // Act
            boolean result = command.execute();

            // Assert
            assertTrue(result, "execute() should succeed");
            assertFalse(command.isMultiFileSave(), "Empty schema should not use multi-file save");
        }

        @Test
        @DisplayName("schema with elements but no sourceInfo should use single-file save")
        void testSchemaWithNoSourceInfoUsesSingleFileSave() {
            // Arrange - use default schema from setUp which has no sourceInfo
            SaveCommand command = new SaveCommand(editorContext, schema, testFilePath, false);

            // Act
            command.execute();

            // Assert
            assertFalse(command.isMultiFileSave(),
                    "Schema with elements but no sourceInfo should use single-file save");
        }

        @Test
        @DisplayName("schema with main-only sourceInfo should use single-file save")
        void testSchemaWithMainOnlySourceInfoUsesSingleFileSave() {
            // Arrange
            XsdSchema mainOnlySchema = new XsdSchema();
            XsdElement element = new XsdElement("MainOnly");
            element.setSourceInfo(IncludeSourceInfo.forMainSchema(testFilePath));
            mainOnlySchema.addChild(element);

            XsdEditorContext mainOnlyContext = new XsdEditorContext(mainOnlySchema);
            SaveCommand command = new SaveCommand(mainOnlyContext, mainOnlySchema, testFilePath, false);

            // Act
            command.execute();

            // Assert
            assertFalse(command.isMultiFileSave(),
                    "Schema with only main-schema sourceInfo should use single-file save");
        }
    }
}
