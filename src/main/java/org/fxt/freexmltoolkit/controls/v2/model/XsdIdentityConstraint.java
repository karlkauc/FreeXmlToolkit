package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Base class for XSD identity constraints (key, keyref, unique).
 * Identity constraints define uniqueness and referential integrity rules.
 *
 * @since 2.0
 */
public abstract class XsdIdentityConstraint extends XsdNode {

    /**
     * Creates a new identity constraint.
     *
     * @param name the constraint name
     */
    protected XsdIdentityConstraint(String name) {
        super(name);
    }

    /**
     * Gets the selector child, if present.
     *
     * @return the selector, or null
     */
    public XsdSelector getSelector() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdSelector)
                .map(child -> (XsdSelector) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets all field children.
     *
     * @return list of fields
     */
    public java.util.List<XsdField> getFields() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdField)
                .map(child -> (XsdField) child)
                .toList();
    }

    /**
     * Creates a deep copy of this identity constraint.
     * This is a base implementation that subclasses should call.
     *
     * @param suffix suffix to append to the node name (e.g., "_copy")
     * @return a deep copy of this node
     */
    @Override
    public abstract XsdNode deepCopy(String suffix);
}
