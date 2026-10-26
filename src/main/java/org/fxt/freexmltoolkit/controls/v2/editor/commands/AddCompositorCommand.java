package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAll;
import org.fxt.freexmltoolkit.controls.v2.model.XsdChoice;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;

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
    private int previousIndex;
    private final java.util.List<XsdNode> movedParticles = new java.util.ArrayList<>();
    private boolean replaceType;
    private String removedType;

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

    /**
     * Makes this command remove the element's {@code type} reference before it creates the inline
     * complex type, for the case {@link XsdEditRules#addCompositor} reports as replaceable.
     *
     * @return this command
     */
    public AddCompositorCommand<T> replacingType() {
        this.replaceType = true;
        return this;
    }

    @Override
    public boolean execute() {
        EditCheck check = XsdEditRules.addCompositor(targetElement, compositorName);
        if (check.isBlocked() || (check.needsConfirmation() && !replaceType)) {
            logger.warn("Refused to add {} to '{}': {}", compositorName, targetElement.getName(), check.reason());
            return false;
        }
        removedType = null;
        if (check.needsConfirmation()) {
            removedType = targetElement.getType();
            targetElement.setType(null);
        }

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
                    previousIndex = addedComplexType.getChildren().indexOf(child);
                    addedComplexType.removeChild(child);
                    break;
                }
            }
        }

        // Create and add the compositor; XSD wants it before the attribute declarations
        addedCompositor = compositorFactory.get();
        addedComplexType.addChild(compositorIndex(addedComplexType), addedCompositor);

        // Changing the compositor kind keeps the content: the particles move over
        movedParticles.clear();
        if (previousCompositor != null) {
            movedParticles.addAll(previousCompositor.getChildren());
            for (XsdNode particle : movedParticles) {
                previousCompositor.removeChild(particle);
                addedCompositor.addChild(particle);
            }
        }

        logger.info("Added {} to element '{}'", compositorName, targetElement.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (addedCompositor == null || addedComplexType == null) {
            logger.warn("Cannot undo: no {} was added", compositorName);
            return false;
        }

        // Remove the compositor (its moved particles go back to the previous one)
        addedComplexType.removeChild(addedCompositor);
        for (XsdNode particle : movedParticles) {
            addedCompositor.removeChild(particle);
            previousCompositor.addChild(particle);
        }

        // Restore previous compositor if there was one
        if (previousCompositor != null) {
            addedComplexType.addChild(Math.min(previousIndex, addedComplexType.getChildren().size()),
                    previousCompositor);
        }

        // Remove complexType if we created it
        if (createdComplexType && addedComplexType != null) {
            targetElement.removeChild(addedComplexType);
        }
        if (removedType != null) {
            targetElement.setType(removedType);
        }

        logger.info("Removed {} from element '{}'", compositorName, targetElement.getName());
        return true;
    }

    /** The index before the first attribute-like child (attribute, attribute group, wildcard, assertion). */
    private static int compositorIndex(XsdNode complexType) {
        var children = complexType.getChildren();
        for (int i = 0; i < children.size(); i++) {
            XsdNode child = children.get(i);
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute
                    || child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup
                    || child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute
                    || child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAssert) {
                return i;
            }
        }
        return children.size();
    }

    @Override
    public String getDescription() {
        if (replaceType) {
            return "Replace type of element '" + targetElement.getName() + "' with a " + compositorName;
        }
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
