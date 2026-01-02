package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add an all compositor to an element.
 * <p>
 * If the element has no complexType, creates an inline complexType with an all.
 * If the element has a complexType without a compositor, adds an all to it.
 * <p>
 * Creates structure:
 * <pre>{@code
 * <xs:element name="elementName">
 *     <xs:complexType>
 *         <xs:all/>
 *     </xs:complexType>
 * </xs:element>
 * }</pre>
 * <p>
 * Supports undo by removing the added all/complexType.
 *
 * @since 2.0
 */
public class AddAllCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddAllCommand.class);

    private final XsdElement targetElement;
    private XsdAll addedAll;
    private XsdComplexType addedComplexType;
    private boolean createdComplexType;
    private XsdNode previousCompositor;

    /**
     * Creates a new add all command.
     *
     * @param targetElement the element to add the all to
     */
    public AddAllCommand(XsdElement targetElement) {
        if (targetElement == null) {
            throw new IllegalArgumentException("Target element cannot be null");
        }
        this.targetElement = targetElement;
    }

    @Override
    public boolean execute() {
        // Check if element already has a complexType child
        XsdComplexType existingComplexType = null;
        for (XsdNode child : targetElement.getChildren()) {
            if (child instanceof XsdComplexType) {
                existingComplexType = (XsdComplexType) child;
                break;
            }
        }

        // Create or reuse complexType
        if (existingComplexType == null) {
            addedComplexType = new XsdComplexType("");
            targetElement.addChild(addedComplexType);
            createdComplexType = true;
            logger.info("Created inline complexType for element '{}'", targetElement.getName());
        } else {
            addedComplexType = existingComplexType;
            createdComplexType = false;

            // Check if there's already a compositor - store it for undo
            for (XsdNode child : addedComplexType.getChildren()) {
                if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                    previousCompositor = child;
                    addedComplexType.removeChild(child);
                    break;
                }
            }
        }

        // Create and add the all
        addedAll = new XsdAll();
        addedComplexType.addChild(addedAll);

        logger.info("Added all to element '{}'", targetElement.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (addedAll == null || addedComplexType == null) {
            logger.warn("Cannot undo: no all was added");
            return false;
        }

        // Remove the all
        addedComplexType.removeChild(addedAll);

        // Restore previous compositor if there was one
        if (previousCompositor != null) {
            addedComplexType.addChild(previousCompositor);
        }

        // Remove complexType if we created it
        if (createdComplexType && addedComplexType != null) {
            targetElement.removeChild(addedComplexType);
        }

        logger.info("Removed all from element '{}'", targetElement.getName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Add all to element '" + targetElement.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return addedAll != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return false;
    }

    /**
     * Gets the added all (after execute() has been called).
     *
     * @return the added all, or null if not yet executed
     */
    public XsdAll getAddedAll() {
        return addedAll;
    }
}
