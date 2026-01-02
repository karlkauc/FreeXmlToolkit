package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add a choice compositor to an element.
 * <p>
 * If the element has no complexType, creates an inline complexType with a choice.
 * If the element has a complexType without a compositor, adds a choice to it.
 * <p>
 * Creates structure:
 * <pre>{@code
 * <xs:element name="elementName">
 *     <xs:complexType>
 *         <xs:choice/>
 *     </xs:complexType>
 * </xs:element>
 * }</pre>
 * <p>
 * Supports undo by removing the added choice/complexType.
 *
 * @since 2.0
 */
public class AddChoiceCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddChoiceCommand.class);

    private final XsdElement targetElement;
    private XsdChoice addedChoice;
    private XsdComplexType addedComplexType;
    private boolean createdComplexType;
    private XsdNode previousCompositor;

    /**
     * Creates a new add choice command.
     *
     * @param targetElement the element to add the choice to
     */
    public AddChoiceCommand(XsdElement targetElement) {
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

        // Create and add the choice
        addedChoice = new XsdChoice();
        addedComplexType.addChild(addedChoice);

        logger.info("Added choice to element '{}'", targetElement.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (addedChoice == null || addedComplexType == null) {
            logger.warn("Cannot undo: no choice was added");
            return false;
        }

        // Remove the choice
        addedComplexType.removeChild(addedChoice);

        // Restore previous compositor if there was one
        if (previousCompositor != null) {
            addedComplexType.addChild(previousCompositor);
        }

        // Remove complexType if we created it
        if (createdComplexType && addedComplexType != null) {
            targetElement.removeChild(addedComplexType);
        }

        logger.info("Removed choice from element '{}'", targetElement.getName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Add choice to element '" + targetElement.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return addedChoice != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return false;
    }

    /**
     * Gets the added choice (after execute() has been called).
     *
     * @return the added choice, or null if not yet executed
     */
    public XsdChoice getAddedChoice() {
        return addedChoice;
    }
}
