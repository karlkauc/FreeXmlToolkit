package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add a new container element to the XSD schema.
 * A container element has no type but instead an inline complexType with a sequence,
 * allowing it to have child elements.
 * <p>
 * Creates structure:
 * <pre>{@code
 * <xs:element name="containerName">
 *     <xs:complexType>
 *         <xs:sequence/>
 *     </xs:complexType>
 * </xs:element>
 * }</pre>
 * <p>
 * Supports undo by removing the added element.
 *
 * @since 2.0
 */
public class AddContainerElementCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddContainerElementCommand.class);

    private final XsdNode parentNode;
    private final String elementName;
    private XsdElement addedElement;
    private XsdNode actualParent;

    /**
     * Creates a new add container element command.
     *
     * @param parentNode  the parent node to add the element to
     * @param elementName the name of the new container element
     */
    public AddContainerElementCommand(XsdNode parentNode, String elementName) {
        if (parentNode == null) {
            throw new IllegalArgumentException("Parent node cannot be null");
        }
        if (elementName == null || elementName.trim().isEmpty()) {
            throw new IllegalArgumentException("Element name cannot be null or empty");
        }

        this.parentNode = parentNode;
        this.elementName = elementName.trim();
    }

    @Override
    public boolean execute() {
        // Create the element without a type (type=null)
        addedElement = new XsdElement(elementName);
        // Explicitly set type to null to indicate this is a container element
        addedElement.setType(null);

        // Create anonymous inline complexType (empty name = anonymous)
        XsdComplexType inlineComplexType = new XsdComplexType("");

        // Create sequence as the compositor for child elements
        XsdSequence sequence = new XsdSequence();

        // Build the structure: element -> complexType -> sequence
        inlineComplexType.addChild(sequence);
        addedElement.addChild(inlineComplexType);

        // Find the correct parent to add to (uses same logic as AddElementCommand)
        actualParent = findTargetParent(parentNode);
        actualParent.addChild(addedElement);

        logger.info("Added container element '{}' with inline complexType to parent '{}'",
                elementName, actualParent.getClass().getSimpleName());
        return true;
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
                XsdComplexType referencedType = findComplexTypeInSchema(element, typeName);
                if (referencedType != null) {
                    // Search for compositor in the referenced ComplexType
                    for (XsdNode complexTypeChild : referencedType.getChildren()) {
                        if (complexTypeChild instanceof XsdSequence ||
                                complexTypeChild instanceof XsdChoice ||
                                complexTypeChild instanceof XsdAll) {
                            return complexTypeChild;
                        }
                    }
                }
            }
        }

        // Check if parent has children (inline complexType, sequence, etc.)
        for (XsdNode child : parent.getChildren()) {
            // If it's a complexType, look for compositor inside
            if (child instanceof XsdComplexType) {
                for (XsdNode complexTypeChild : child.getChildren()) {
                    if (complexTypeChild instanceof XsdSequence ||
                            complexTypeChild instanceof XsdChoice ||
                            complexTypeChild instanceof XsdAll) {
                        return complexTypeChild;
                    }
                }
            }
            // If it's directly a compositor
            else if (child instanceof XsdSequence ||
                    child instanceof XsdChoice ||
                    child instanceof XsdAll) {
                return child;
            }
        }

        // No compositor found, add directly to parent
        return parent;
    }

    /**
     * Finds a ComplexType by name in the schema.
     */
    private XsdComplexType findComplexTypeInSchema(XsdElement element, String typeName) {
        XsdNode current = element.getParent();
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }

        if (current instanceof XsdSchema schema) {
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType complexType) {
                    if (typeName.equals(complexType.getName())) {
                        return complexType;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean undo() {
        if (addedElement == null || actualParent == null) {
            logger.warn("Cannot undo: no element was added");
            return false;
        }

        actualParent.removeChild(addedElement);
        logger.info("Removed container element '{}' from '{}'",
                elementName, actualParent.getClass().getSimpleName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Add container element '" + elementName + "' to '" + parentNode.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return addedElement != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
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
