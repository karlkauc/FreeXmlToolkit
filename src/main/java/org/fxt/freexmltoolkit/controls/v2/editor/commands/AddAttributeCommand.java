package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add a new attribute to an XSD element or complex type.
 * <p>
 * Attributes can be added to:
 * - XsdElement with an inline XsdComplexType
 * - XsdComplexType (directly)
 * - XsdExtension, XsdRestriction (directly)
 * - XsdAttributeGroup (directly)
 * <p>
 * If the target element has no inline complexType, one will be created automatically.
 * The attribute is created without a type - the type can be set later in the Properties Panel.
 * <p>
 * Supports undo by removing the added attribute (and complexType if it was created).
 *
 * @since 2.0
 */
public class AddAttributeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddAttributeCommand.class);

    private final XsdNode parentNode;
    private final String attributeName;
    private XsdAttribute addedAttribute;
    private XsdNode actualParent;
    private XsdComplexType createdComplexType;  // If we had to create a complexType

    /**
     * Creates a new add attribute command.
     *
     * @param parentNode     the parent node to add the attribute to
     * @param attributeName  the name of the new attribute
     */
    public AddAttributeCommand(XsdNode parentNode, String attributeName) {
        CommandValidation.requireNonNull(parentNode, "Parent node");
        CommandValidation.requireNonEmpty(attributeName, "Attribute name");

        this.parentNode = parentNode;
        this.attributeName = attributeName.trim();
    }

    @Override
    public boolean execute() {
        // Find the correct parent to add the attribute to
        actualParent = findTargetParent(parentNode);

        if (actualParent == null) {
            logger.error("Cannot find valid parent for attribute in node '{}'", parentNode.getName());
            return false;
        }

        // Create the attribute without a type
        addedAttribute = new XsdAttribute(attributeName);

        // Add to parent
        actualParent.addChild(addedAttribute);

        logger.info("Added attribute '{}' to '{}'",
                attributeName, actualParent.getClass().getSimpleName());
        return true;
    }

    /**
     * Finds the correct parent node to add the attribute to.
     * Attributes can only be added to complex types.
     * If parent is an Element without a complexType, creates an inline complexType.
     */
    private XsdNode findTargetParent(XsdNode parent) {
        // Case 1: Parent is already a ComplexType - add directly
        if (parent instanceof XsdComplexType) {
            logger.debug("Parent is ComplexType, adding attribute directly");
            return parent;
        }

        // Case 2: Parent is an Extension or Restriction - add directly
        if (parent instanceof XsdExtension || parent instanceof XsdRestriction) {
            logger.debug("Parent is Extension/Restriction, adding attribute directly");
            return parent;
        }

        // Case 3: Parent is an AttributeGroup - add directly
        if (parent instanceof XsdAttributeGroup) {
            logger.debug("Parent is AttributeGroup, adding attribute directly");
            return parent;
        }

        // Case 4: Parent is an Element - look for inline complexType
        if (parent instanceof XsdElement element) {
            // Look for existing inline complexType
            for (XsdNode child : element.getChildren()) {
                if (child instanceof XsdComplexType) {
                    logger.debug("Element '{}' has inline complexType, adding attribute to it", element.getName());
                    return child;
                }
            }

            // No inline complexType found, create one
            logger.debug("Element '{}' has no inline complexType, creating one", element.getName());
            createdComplexType = new XsdComplexType("");  // Anonymous complexType
            element.addChild(createdComplexType);
            return createdComplexType;
        }

        // Case 5: For other node types (Schema, Sequence, Choice, etc.), cannot add attributes
        logger.warn("Parent node type '{}' does not support attributes", parent.getClass().getSimpleName());
        return null;
    }

    @Override
    public boolean undo() {
        if (addedAttribute == null || actualParent == null) {
            logger.warn("Cannot undo: no attribute was added");
            return false;
        }

        // Remove the attribute
        actualParent.removeChild(addedAttribute);
        logger.info("Removed attribute '{}' from '{}'", attributeName, actualParent.getClass().getSimpleName());

        // If we created a complexType, remove it as well
        if (createdComplexType != null) {
            XsdElement parentElement = null;
            if (parentNode instanceof XsdElement) {
                parentElement = (XsdElement) parentNode;
            }

            if (parentElement != null) {
                parentElement.removeChild(createdComplexType);
                logger.info("Removed inline complexType from element '{}'", parentElement.getName());
            }
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Add attribute '" + attributeName + "' to '" + parentNode.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return addedAttribute != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Add commands should not be merged
        return false;
    }

    /**
     * Gets the added attribute (after execute() has been called).
     *
     * @return the added attribute, or null if not yet executed
     */
    public XsdAttribute getAddedAttribute() {
        return addedAttribute;
    }
}
