package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.TypeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Command for deleting global type definitions from XSD schema.
 * Performs reference checking to ensure safe deletion.
 */
public class DeleteTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteTypeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final TypeInfo typeToDelete;
    private String backupXml;
    private boolean wasDeleted = false;

    public DeleteTypeCommand(XsdDomManipulator domManipulator, TypeInfo typeToDelete) {
        this.domManipulator = domManipulator;
        this.typeToDelete = typeToDelete;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Attempting to delete type: {}", typeToDelete.name());

            // Create backup before deletion
            backupXml = domManipulator.getXmlContent();

            // Check if type can be safely deleted
            List<Element> references = domManipulator.findTypeReferences(typeToDelete.name());
            if (!references.isEmpty()) {
                logger.warn("Type '{}' has {} references, deletion may cause validation errors",
                        typeToDelete.name(), references.size());
            }

            // Attempt to delete the type
            wasDeleted = domManipulator.deleteGlobalType(typeToDelete.name());

            if (wasDeleted) {
                logger.info("Successfully deleted type: {}", typeToDelete.name());
            } else {
                logger.error("Failed to delete type: {}", typeToDelete.name());
            }

            return wasDeleted;

        } catch (Exception e) {
            logger.error("Error deleting type: " + typeToDelete.name(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (wasDeleted && backupXml != null) {
                // Restore from backup
                domManipulator.loadXsd(backupXml);
                logger.info("Restored type after deletion: {}", typeToDelete.name());
                wasDeleted = false;
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing type deletion: " + typeToDelete.name(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Delete type '%s'", typeToDelete.name());
    }

    @Override
    public boolean canUndo() {
        return wasDeleted && backupXml != null;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    /**
     * Check if the type can be safely deleted without breaking references
     */
    public boolean canSafelyDelete() {
        return domManipulator.canDeleteType(typeToDelete.name());
    }

    /**
     * Get list of elements that reference this type
     */
    public List<Element> getReferences() {
        return domManipulator.findTypeReferences(typeToDelete.name());
    }

    /**
     * Get the type being deleted
     */
    public TypeInfo getTypeInfo() {
        return typeToDelete;
    }
}