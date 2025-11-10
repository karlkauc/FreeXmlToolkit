package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add a new element to the XSD schema.
 * Supports undo by removing the added element.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class AddElementCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddElementCommand.class);

    private final XsdNode parentNode;
    private final String elementName;
    private final String elementType;
    private XsdElement addedElement;
    private XsdNode actualParent;  // The actual parent where element was added (might be compositor)

    /**
     * Creates a new add element command with default string type.
     *
     * @param parentNode  the parent node to add the element to
     * @param elementName the name of the new element
     */
    public AddElementCommand(XsdNode parentNode, String elementName) {
        this(parentNode, elementName, "xs:string");
    }

    /**
     * Creates a new add element command with specified type.
     *
     * @param parentNode  the parent node to add the element to
     * @param elementName the name of the new element
     * @param elementType the XSD type of the new element (e.g., "xs:string", "xs:int")
     */
    public AddElementCommand(XsdNode parentNode, String elementName, String elementType) {
        if (parentNode == null) {
            throw new IllegalArgumentException("Parent node cannot be null");
        }
        if (elementName == null || elementName.trim().isEmpty()) {
            throw new IllegalArgumentException("Element name cannot be null or empty");
        }

        this.parentNode = parentNode;
        this.elementName = elementName.trim();
        this.elementType = elementType != null ? elementType : "xs:string";
    }

    @Override
    public boolean execute() {
        // Create actual XsdElement and add to parent model
        // This will fire PropertyChangeEvent which triggers automatic view refresh
        addedElement = new XsdElement(elementName);
        addedElement.setType(elementType);

        // Find the correct parent to add to:
        // If parent is an element with complexType containing a compositor, add to compositor
        // Otherwise add directly to parent
        actualParent = findTargetParent(parentNode);
        actualParent.addChild(addedElement);

        logger.info("Added element '{}' with type '{}' to parent '{}'",
                elementName, elementType, actualParent.getClass().getSimpleName());
        return true;
    }

    /**
     * Finds the correct parent node to add the new element to.
     * If the parent element has a complexType with a compositor (sequence/choice/all),
     * returns the compositor. Otherwise returns the parent itself.
     */
    private XsdNode findTargetParent(XsdNode parent) {
        // Check if parent has children (complexType, sequence, etc.)
        for (XsdNode child : parent.getChildren()) {
            // If it's a complexType, look for compositor inside
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType) {
                for (XsdNode complexTypeChild : child.getChildren()) {
                    // Found a compositor - this is where elements should go
                    if (complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
                            complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice ||
                            complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll) {
                        logger.debug("Found compositor {} in complexType, using as target parent",
                                complexTypeChild.getClass().getSimpleName());
                        return complexTypeChild;
                    }
                }
            }
            // If it's directly a compositor (for ref elements or groups)
            else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
                    child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice ||
                    child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll) {
                logger.debug("Found direct compositor {}, using as target parent",
                        child.getClass().getSimpleName());
                return child;
            }
        }

        // No compositor found, add directly to parent
        logger.debug("No compositor found, adding directly to parent");
        return parent;
    }

    @Override
    public boolean undo() {
        if (addedElement == null || actualParent == null) {
            logger.warn("Cannot undo: no element was added");
            return false;
        }

        // Remove from model - this will fire PropertyChangeEvent
        actualParent.removeChild(addedElement);
        logger.info("Removed added element '{}' from '{}'", elementName, actualParent.getClass().getSimpleName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Add element '" + elementName + "' to '" + parentNode.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return addedElement != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Add commands should not be merged
        return false;
    }

    /**
     * Gets the added element (after execute() has been called).
     *
     * @return the added element, or null if not yet executed
     */
    public XsdElement getAddedElement() {
        return addedElement;
    }
}
