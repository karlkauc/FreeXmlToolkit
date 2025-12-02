package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.MultiFileXsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.MultiFileXsdSerializer.SaveResult;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Command to save the XSD schema to a file.
 * Creates a backup before overwriting existing files.
 * Resets the dirty flag on successful save.
 * <p>
 * Serializes from XsdSchema model (not VisualNode view layer).
 * Note: Save commands are typically not undoable since they involve
 * file system operations. This command returns false for canUndo().
 *
 * @since 2.0
 */
public class SaveCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(SaveCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdSchema schema;
    private final Path filePath;
    private final boolean createBackup;
    private final XsdSerializer serializer;

    private Path backupPath;
    private boolean saveSuccessful = false;
    private Map<Path, SaveResult> multiFileSaveResults;
    private boolean isMultiFileSave = false;

    /**
     * Creates a new save command.
     *
     * @param editorContext the editor context
     * @param schema        the XSD schema model to serialize
     * @param filePath      the file path to save to
     * @param createBackup  whether to create a backup before saving
     */
    public SaveCommand(XsdEditorContext editorContext, XsdSchema schema,
                       Path filePath, boolean createBackup) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        this.editorContext = editorContext;
        this.schema = schema;
        this.filePath = filePath;
        this.createBackup = createBackup;
        this.serializer = new XsdSerializer();
    }

    @Override
    public boolean execute() {
        try {
            // Check if this is a multi-file schema (has nodes from included files)
            if (hasIncludedFiles(schema)) {
                return executeMultiFileSave();
            } else {
                return executeSingleFileSave();
            }
        } catch (Exception e) {
            logger.error("Failed to save XSD: {}", e.getMessage(), e);
            saveSuccessful = false;
            return false;
        }
    }

    /**
     * Executes a single-file save (original behavior for schemas without includes).
     */
    private boolean executeSingleFileSave() throws IOException {
        logger.info("Saving XSD schema to single file: {}", filePath);

        // Create backup before saving if requested
        if (createBackup && java.nio.file.Files.exists(filePath)) {
            backupPath = serializer.createBackup(filePath);
            logger.info("Created backup at: {}", backupPath);
        }

        // Serialize the XSD schema model to XSD XML
        String xsdContent = serializer.serialize(schema);

        // Save to file (without backup since we already created it)
        serializer.saveToFile(filePath, xsdContent, false);

        // Reset dirty flag on successful save
        editorContext.resetDirty();

        saveSuccessful = true;
        isMultiFileSave = false;
        logger.info("Successfully saved XSD schema to: {}", filePath);
        return true;
    }

    /**
     * Executes a multi-file save for schemas with xs:include statements.
     * Each node is saved back to its original source file.
     */
    private boolean executeMultiFileSave() {
        logger.info("Saving XSD schema to multiple files (multi-file save)");

        MultiFileXsdSerializer multiSerializer = new MultiFileXsdSerializer();
        multiFileSaveResults = multiSerializer.saveAll(schema, filePath, createBackup);

        // Check if all saves were successful
        boolean allSuccessful = multiFileSaveResults.values().stream()
                .allMatch(SaveResult::success);

        if (allSuccessful) {
            // Reset dirty flag on successful save
            editorContext.resetDirty();
            saveSuccessful = true;
            isMultiFileSave = true;

            // Log summary
            int fileCount = multiFileSaveResults.size();
            int totalNodes = multiFileSaveResults.values().stream()
                    .mapToInt(SaveResult::nodeCount)
                    .sum();
            logger.info("Successfully saved {} files ({} total nodes)", fileCount, totalNodes);

            // Set backup path from main file result
            SaveResult mainResult = multiFileSaveResults.get(filePath);
            if (mainResult != null) {
                backupPath = mainResult.backupPath();
            }
        } else {
            saveSuccessful = false;
            isMultiFileSave = true;

            // Log failures
            multiFileSaveResults.forEach((path, result) -> {
                if (!result.success()) {
                    logger.error("Failed to save {}: {}", path, result.errorMessage());
                }
            });
        }

        return allSuccessful;
    }

    /**
     * Checks if the schema has nodes from included files.
     * If any top-level node has sourceInfo indicating it's from an include,
     * we use multi-file save mode.
     */
    private boolean hasIncludedFiles(XsdSchema schema) {
        if (schema == null || schema.getChildren() == null) {
            return false;
        }

        return schema.getChildren().stream()
                .anyMatch(node -> {
                    var sourceInfo = node.getSourceInfo();
                    return sourceInfo != null && sourceInfo.isFromInclude();
                });
    }

    @Override
    public boolean undo() {
        // Save commands are not undoable
        // Undo would require restoring from backup or previous state,
        // which is complex and error-prone for file system operations
        logger.warn("Save command cannot be undone");
        return false;
    }

    @Override
    public String getDescription() {
        return "Save XSD to file: " + filePath.getFileName();
    }

    @Override
    public boolean canUndo() {
        // Save commands are typically not undoable
        return false;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Save commands should not be merged
        return false;
    }

    /**
     * Gets the file path where the XSD was saved.
     *
     * @return the file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Gets the backup path if a backup was created.
     *
     * @return the backup path, or null if no backup was created
     */
    public Path getBackupPath() {
        return backupPath;
    }

    /**
     * Checks if the save operation was successful.
     *
     * @return true if save was successful
     */
    public boolean isSaveSuccessful() {
        return saveSuccessful;
    }

    /**
     * Checks if this was a multi-file save operation.
     *
     * @return true if multiple files were saved
     */
    public boolean isMultiFileSave() {
        return isMultiFileSave;
    }

    /**
     * Gets the results of a multi-file save operation.
     * Each entry maps a file path to its save result.
     *
     * @return map of file paths to save results, or null if this was a single-file save
     */
    public Map<Path, SaveResult> getMultiFileSaveResults() {
        return multiFileSaveResults;
    }

    /**
     * Gets the number of files saved in a multi-file save operation.
     *
     * @return the number of files saved, or 1 for single-file saves
     */
    public int getSavedFileCount() {
        if (multiFileSaveResults != null) {
            return (int) multiFileSaveResults.values().stream()
                    .filter(SaveResult::success)
                    .count();
        }
        return saveSuccessful ? 1 : 0;
    }

    /**
     * Gets a human-readable summary of the save operation.
     *
     * @return summary string describing what was saved
     */
    public String getSaveSummary() {
        if (!saveSuccessful) {
            return "Save failed";
        }

        if (isMultiFileSave && multiFileSaveResults != null) {
            int successCount = getSavedFileCount();
            int totalCount = multiFileSaveResults.size();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Saved %d/%d files:", successCount, totalCount));
            multiFileSaveResults.forEach((path, result) -> {
                if (result.success()) {
                    sb.append("\n  - ").append(path.getFileName());
                    if (result.nodeCount() > 0) {
                        sb.append(" (").append(result.nodeCount()).append(" nodes)");
                    }
                }
            });
            return sb.toString();
        } else {
            return "Saved to " + filePath.getFileName();
        }
    }
}
