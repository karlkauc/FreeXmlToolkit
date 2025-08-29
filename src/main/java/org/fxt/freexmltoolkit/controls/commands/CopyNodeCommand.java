package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdClipboardService;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;

/**
 * Command for copying XSD nodes to clipboard
 * Note: Copy operations don't typically need undo, but included for consistency
 */
public class CopyNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(CopyNodeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo nodeToCopy;
    private XsdClipboardService.XsdClipboardData previousClipboardData;

    public CopyNodeCommand(XsdDomManipulator domManipulator, XsdNodeInfo nodeToCopy) {
        this.domManipulator = domManipulator;
        this.nodeToCopy = nodeToCopy;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Copying node: {} ({})", nodeToCopy.name(), nodeToCopy.nodeType());

            // Store current clipboard content for potential undo
            previousClipboardData = XsdClipboardService.getClipboardData();

            // Copy the node to clipboard
            boolean success = XsdClipboardService.copyNode(nodeToCopy, domManipulator.getDocument());

            if (success) {
                logger.info("Successfully copied node to clipboard: {}", nodeToCopy.name());
            } else {
                logger.error("Failed to copy node to clipboard: {}", nodeToCopy.name());
            }

            return success;

        } catch (Exception e) {
            logger.error("Error copying node to clipboard", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        // Restore previous clipboard content
        // Note: This is unusual for copy operations, but provides consistency
        try {
            if (previousClipboardData != null) {
                // We can't easily restore clipboard data without recreating it
                // For now, just clear the clipboard
                XsdClipboardService.clearClipboard();
                logger.info("Cleared clipboard as undo for copy operation");
            }
            return true;
        } catch (Exception e) {
            logger.error("Error undoing copy operation", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String typeName = getNodeTypeDisplayName(nodeToCopy.nodeType());
        return String.format("Copy %s '%s'", typeName, nodeToCopy.name());
    }

    @Override
    public boolean canUndo() {
        // Copy operations typically don't need undo, but we provide it for consistency
        return true;
    }

    @Override
    public boolean isModifying() {
        // Copy doesn't modify the document itself
        return false;
    }

    /**
     * Get display name for node type
     */
    private String getNodeTypeDisplayName(XsdNodeInfo.NodeType nodeType) {
        return switch (nodeType) {
            case ELEMENT -> "element";
            case ATTRIBUTE -> "attribute";
            case SEQUENCE -> "sequence";
            case CHOICE -> "choice";
            case ANY -> "any";
            case SIMPLE_TYPE -> "simpleType";
            case COMPLEX_TYPE -> "complexType";
            case SCHEMA -> "schema";
        };
    }
}