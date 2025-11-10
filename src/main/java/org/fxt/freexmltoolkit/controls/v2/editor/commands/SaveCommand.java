package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Command to save the XSD schema to a file.
 * Creates a backup before overwriting existing files.
 * Resets the dirty flag on successful save.
 * <p>
 * Note: Save commands are typically not undoable since they involve
 * file system operations. This command returns false for canUndo().
 *
 * @since 2.0
 */
public class SaveCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(SaveCommand.class);

    private final XsdEditorContext editorContext;
    private final VisualNode rootNode;
    private final Path filePath;
    private final boolean createBackup;
    private final XsdSerializer serializer;

    private Path backupPath;
    private boolean saveSuccessful = false;

    /**
     * Creates a new save command.
     *
     * @param editorContext the editor context
     * @param rootNode      the root visual node to serialize
     * @param filePath      the file path to save to
     * @param createBackup  whether to create a backup before saving
     */
    public SaveCommand(XsdEditorContext editorContext, VisualNode rootNode,
                       Path filePath, boolean createBackup) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (rootNode == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        this.editorContext = editorContext;
        this.rootNode = rootNode;
        this.filePath = filePath;
        this.createBackup = createBackup;
        this.serializer = new XsdSerializer();
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Saving XSD to file: {}", filePath);

            // Serialize the visual node tree to XSD XML
            String xsdContent = serializer.serialize(rootNode);

            // Save to file (with optional backup)
            serializer.saveToFile(filePath, xsdContent, createBackup);

            // Track backup path if created
            if (createBackup) {
                backupPath = filePath.getParent().resolve(
                        filePath.getFileName().toString().replaceFirst(
                                "(\\.[^.]+)$",
                                "_backup_*$1" // Pattern for backup files
                        )
                );
            }

            // Reset dirty flag on successful save
            editorContext.resetDirty();

            saveSuccessful = true;
            logger.info("Successfully saved XSD to: {}", filePath);
            return true;

        } catch (IOException e) {
            logger.error("Failed to save XSD to file: {}", filePath, e);
            saveSuccessful = false;
            return false;
        }
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
}
