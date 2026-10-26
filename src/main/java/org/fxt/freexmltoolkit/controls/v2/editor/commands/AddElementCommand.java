package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

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
    private final NewElementSpec spec;
    private boolean checkSpec;  // created from user input: refuse properties that are not valid XSD
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
     * @param elementType the XSD type of the new element (e.g., "xs:string", "xs:int"), or null for no type
     */
    public AddElementCommand(XsdNode parentNode, String elementName, String elementType) {
        this(parentNode, NewElementSpec.of(elementName, elementType));
    }

    /**
     * Creates a new add element command that sets every property of {@code spec} on the new
     * element before it is added (one undo step). Refuses a spec that is not valid XSD
     * ({@link XsdEditRules#newElementProperties}).
     *
     * @param parentNode the parent node to add the element to
     * @param spec       name, type, cardinality and the other properties of the new element
     */
    public static AddElementCommand of(XsdNode parentNode, NewElementSpec spec) {
        AddElementCommand command = new AddElementCommand(parentNode, spec);
        command.checkSpec = true;
        return command;
    }

    private AddElementCommand(XsdNode parentNode, NewElementSpec spec) {
        String elementName = spec == null ? null : spec.name();
        if (parentNode == null) {
            throw new IllegalArgumentException("Parent node cannot be null");
        }
        if (elementName == null || elementName.trim().isEmpty()) {
            throw new IllegalArgumentException("Element name cannot be null or empty");
        }

        this.parentNode = parentNode;
        this.elementName = elementName.trim();
        this.elementType = blankToNull(spec.type());
        this.spec = spec;
    }

    @Override
    public boolean execute() {
        EditCheck conflict = XsdEditRules.contentConflict(parentNode);
        if (conflict.isBlocked()) {
            logger.warn("Refused to add element '{}': {}", elementName, conflict.reason());
            return false;
        }
        EditCheck valid = checkSpec ? XsdEditRules.newElementProperties(parentNode, spec) : EditCheck.allowed();
        if (valid.isBlocked()) {
            logger.warn("Refused to add element '{}': {}", elementName, valid.reason());
            return false;
        }
        // Create actual XsdElement and add to parent model
        // This will fire PropertyChangeEvent which triggers automatic view refresh
        addedElement = new XsdElement(elementName);
        if (elementType != null) {
            addedElement.setType(elementType);
        }
        applySpec(addedElement);

        // Find the correct parent to add to:
        // If parent is an element with complexType containing a compositor, add to compositor
        // Otherwise add directly to parent
        actualParent = findTargetParent(parentNode);
        actualParent.addChild(addedElement);

        logger.info("Added element '{}' with type '{}' to parent '{}'",
                elementName, elementType, actualParent.getClass().getSimpleName());
        return true;
    }

    /** Sets the properties of the spec besides name and type (defaults are left untouched). */
    private void applySpec(XsdElement element) {
        if (spec.minOccurs() != 1) {
            element.setMinOccurs(spec.minOccurs());
        }
        if (spec.maxOccurs() != 1) {
            element.setMaxOccurs(spec.maxOccurs());
        }
        if (blankToNull(spec.documentation()) != null) {
            element.setDocumentation(spec.documentation().trim());
        }
        if (spec.nillable()) {
            element.setNillable(true);
        }
        if (spec.abstractElement()) {
            element.setAbstract(true);
        }
        if (spec.defaultValue() != null && !spec.defaultValue().isEmpty()) {
            element.setDefaultValue(spec.defaultValue());
        }
        if (spec.fixed() != null && !spec.fixed().isEmpty()) {
            element.setFixed(spec.fixed());
        }
        if (blankToNull(spec.form()) != null) {
            element.setForm(spec.form().trim());
        }
        if (blankToNull(spec.substitutionGroup()) != null) {
            element.setSubstitutionGroup(spec.substitutionGroup().trim());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Finds the correct parent node to add the new element to.
     * If the parent element has a complexType with a compositor (sequence/choice/all),
     * returns the compositor. Otherwise returns the parent itself.
     */
    private XsdNode findTargetParent(XsdNode parent) {
        // First, check if parent is an Element that references a ComplexType
        if (parent instanceof XsdElement element) {
            String typeName = element.getType();
            if (typeName != null && !typeName.isEmpty() && !typeName.startsWith("xs:")) {
                // Element references a custom type - find it in schema
                logger.debug("Element '{}' references type '{}', searching in schema", element.getName(), typeName);
                XsdComplexType referencedType = findComplexTypeInSchema(element, typeName);
                if (referencedType != null) {
                    logger.debug("Found referenced ComplexType '{}'", typeName);
                    // Search for compositor in the referenced ComplexType
                    for (XsdNode complexTypeChild : referencedType.getChildren()) {
                        if (complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
                                complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice ||
                                complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll) {
                            logger.debug("Found compositor {} in referenced ComplexType, using as target parent",
                                    complexTypeChild.getClass().getSimpleName());
                            return complexTypeChild;
                        }
                    }
                }
            }
        }

        // Check if parent has children (inline complexType, sequence, etc.)
        for (XsdNode child : parent.getChildren()) {
            // If it's a complexType, look for compositor inside
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType) {
                for (XsdNode complexTypeChild : child.getChildren()) {
                    // Found a compositor - this is where elements should go
                    if (complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
                            complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice ||
                            complexTypeChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll) {
                        logger.debug("Found compositor {} in inline complexType, using as target parent",
                                complexTypeChild.getClass().getSimpleName());
                        return complexTypeChild;
                    }
                }
            // If it's directly a compositor (for ref elements or groups)
            } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
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

    /**
     * Finds a ComplexType by name in the schema.
     */
    private XsdComplexType findComplexTypeInSchema(XsdElement element, String typeName) {
        // Navigate up to schema
        XsdNode current = element.getParent();
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }

        if (current instanceof XsdSchema schema) {
            // Search for ComplexType in schema's children
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType complexType && typeName.equals(complexType.getName())) {
                    logger.debug("Found ComplexType '{}' in schema", typeName);
                    return complexType;
                }
            }
        }

        logger.debug("ComplexType '{}' not found in schema", typeName);
        return null;
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
