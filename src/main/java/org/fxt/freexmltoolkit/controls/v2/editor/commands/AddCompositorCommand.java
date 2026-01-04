package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import java.util.function.Supplier;

/**
 * Generic command to add a compositor (sequence, choice, or all) to an element.
 * <p>
 * Uses the template method pattern with a supplier function to create the specific
 * compositor type. This eliminates code duplication across AddSequenceCommand,
 * AddChoiceCommand, and AddAllCommand.
 * <p>
 * If the element has no complexType, creates an inline complexType with the compositor.
 * If the element has a complexType without a compositor, adds the compositor to it.
 * <p>
 * Supports undo by removing the added compositor/complexType.
 *
 * @param <T> the type of compositor (XsdSequence, XsdChoice, or XsdAll)
 * @since 2.0
 */
public class AddCompositorCommand<T extends XsdNode> implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddCompositorCommand.class);

    private final XsdElement targetElement;
    private final Supplier<T> compositorFactory;
    private final String compositorName;
    private T addedCompositor;
    private XsdComplexType addedComplexType;
    private boolean createdComplexType;
    private XsdNode previousCompositor;

    /**
     * Creates a new generic add compositor command.
     *
     * @param targetElement    the element to add the compositor to
     * @param compositorFactory supplier to create the compositor instance
     * @param compositorName    human-readable name of the compositor type (e.g., "sequence", "choice", "all")
     * @throws IllegalArgumentException if targetElement or compositorFactory is null
     */
    public AddCompositorCommand(XsdElement targetElement, Supplier<T> compositorFactory, String compositorName) {
        if (targetElement == null) {
            throw new IllegalArgumentException("Target element cannot be null");
        }
        if (compositorFactory == null) {
            throw new IllegalArgumentException("Compositor factory cannot be null");
        }
        this.targetElement = targetElement;
        this.compositorFactory = compositorFactory;
        this.compositorName = compositorName != null ? compositorName : "compositor";
    }

    /**
     * Factory method for creating a sequence compositor command.
     *
     * @param targetElement the element to add the sequence to
     * @return a new AddCompositorCommand configured for sequences
     */
    public static AddCompositorCommand<XsdSequence> sequence(XsdElement targetElement) {
        return new AddCompositorCommand<>(targetElement, XsdSequence::new, "sequence");
    }

    /**
     * Factory method for creating a choice compositor command.
     *
     * @param targetElement the element to add the choice to
     * @return a new AddCompositorCommand configured for choices
     */
    public static AddCompositorCommand<XsdChoice> choice(XsdElement targetElement) {
        return new AddCompositorCommand<>(targetElement, XsdChoice::new, "choice");
    }

    /**
     * Factory method for creating an all compositor command.
     *
     * @param targetElement the element to add the all to
     * @return a new AddCompositorCommand configured for all
     */
    public static AddCompositorCommand<XsdAll> all(XsdElement targetElement) {
        return new AddCompositorCommand<>(targetElement, XsdAll::new, "all");
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

        // Create and add the compositor
        addedCompositor = compositorFactory.get();
        addedComplexType.addChild(addedCompositor);

        logger.info("Added {} to element '{}'", compositorName, targetElement.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (addedCompositor == null || addedComplexType == null) {
            logger.warn("Cannot undo: no {} was added", compositorName);
            return false;
        }

        // Remove the compositor
        addedComplexType.removeChild(addedCompositor);

        // Restore previous compositor if there was one
        if (previousCompositor != null) {
            addedComplexType.addChild(previousCompositor);
        }

        // Remove complexType if we created it
        if (createdComplexType && addedComplexType != null) {
            targetElement.removeChild(addedComplexType);
        }

        logger.info("Removed {} from element '{}'", compositorName, targetElement.getName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Add " + compositorName + " to element '" + targetElement.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return addedCompositor != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return false;
    }

    /**
     * Gets the added compositor (after execute() has been called).
     *
     * @return the added compositor, or null if not yet executed
     */
    public T getAddedCompositor() {
        return addedCompositor;
    }
}
