package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;

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
    private final NewAttributeSpec spec;
    private boolean checkSpec;  // created from user input: refuse properties that are not valid XSD
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
        this(parentNode, NewAttributeSpec.named(attributeName));
    }

    /**
     * Creates a new add attribute command that sets every property of {@code spec} on the new
     * attribute before it is added (one undo step). Refuses a spec that is not valid XSD
     * ({@link XsdEditRules#newAttributeProperties}).
     *
     * @param parentNode the parent node to add the attribute to
     * @param spec       name, type, use and the other properties of the new attribute
     */
    public static AddAttributeCommand of(XsdNode parentNode, NewAttributeSpec spec) {
        AddAttributeCommand command = new AddAttributeCommand(parentNode, spec);
        command.checkSpec = true;
        return command;
    }

    private AddAttributeCommand(XsdNode parentNode, NewAttributeSpec spec) {
        CommandValidation.requireNonNull(parentNode, "Parent node");
        CommandValidation.requireNonNull(spec, "Attribute spec");
        CommandValidation.requireNonEmpty(spec.name(), "Attribute name");

        this.parentNode = parentNode;
        this.attributeName = spec.name().trim();
        this.spec = spec;
    }

    @Override
    public boolean execute() {
        EditCheck conflict = XsdEditRules.contentConflict(parentNode);
        if (conflict.isBlocked()) {
            logger.warn("Refused to add attribute '{}': {}", attributeName, conflict.reason());
            return false;
        }
        EditCheck valid = checkSpec ? XsdEditRules.newAttributeProperties(spec) : EditCheck.allowed();
        if (valid.isBlocked()) {
            logger.warn("Refused to add attribute '{}': {}", attributeName, valid.reason());
            return false;
        }
        // Find the correct parent to add the attribute to
        actualParent = findTargetParent(parentNode);

        if (actualParent == null) {
            logger.error("Cannot find valid parent for attribute in node '{}'", parentNode.getName());
            return false;
        }

        // Create the attribute with the entered properties (no type unless one was given)
        addedAttribute = new XsdAttribute(attributeName);
        applySpec(addedAttribute);

        // Add to parent
        actualParent.addChild(addedAttribute);

        logger.info("Added attribute '{}' to '{}'",
                attributeName, actualParent.getClass().getSimpleName());
        return true;
    }

    /** Sets the properties of the spec besides the name (defaults are left untouched). */
    private void applySpec(XsdAttribute attribute) {
        if (hasText(spec.type())) {
            attribute.setType(spec.type().trim());
        }
        if (hasText(spec.use()) && !"optional".equals(spec.use().trim())) {
            attribute.setUse(spec.use().trim());
        }
        if (hasText(spec.documentation())) {
            attribute.setDocumentation(spec.documentation().trim());
        }
        if (spec.defaultValue() != null && !spec.defaultValue().isEmpty()) {
            attribute.setDefaultValue(spec.defaultValue());
        }
        if (spec.fixed() != null && !spec.fixed().isEmpty()) {
            attribute.setFixed(spec.fixed());
        }
        if (hasText(spec.form())) {
            attribute.setForm(spec.form().trim());
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Finds the correct parent node to add the attribute to.
     * Attributes can only be added to complex types.
     * If parent is an Element without a complexType, creates an inline complexType.
     */
    private XsdNode findTargetParent(XsdNode parent) {
        // Case 1: Parent is already a ComplexType - add directly (into its simple/complex content derivation)
        if (parent instanceof XsdComplexType) {
            XsdNode container = XsdEditRules.attributeContainer(parent);
            if (container != null && container != parent) {
                return container;
            }
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

        // Case 4: Parent is an Element - the referenced named complex type, or the inline
        // complexType (into the extension/restriction of a simple/complex content)
        if (parent instanceof XsdElement element) {
            XsdNode container = XsdEditRules.attributeContainer(element);
            if (container != null) {
                return container;
            }
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
