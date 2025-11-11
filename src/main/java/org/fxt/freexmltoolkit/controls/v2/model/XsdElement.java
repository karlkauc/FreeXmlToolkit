package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD element (xs:element).
 *
 * @since 2.0
 */
public class XsdElement extends XsdNode {

    private String type;
    private boolean nillable;
    private boolean abstractElement;
    private String fixed;
    private String defaultValue;
    private String substitutionGroup;
    private String form; // qualified, unqualified

    /**
     * Creates a new XSD element.
     *
     * @param name the element name
     */
    public XsdElement(String name) {
        super(name);
    }

    /**
     * Gets the type reference.
     *
     * @return the type name (e.g., "xs:string", "MyCustomType")
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type reference.
     *
     * @param type the type name
     */
    public void setType(String type) {
        String oldValue = this.type;
        this.type = type;
        pcs.firePropertyChange("type", oldValue, type);
    }

    /**
     * Checks if this element is nillable.
     *
     * @return true if nillable
     */
    public boolean isNillable() {
        return nillable;
    }

    /**
     * Sets the nillable flag.
     *
     * @param nillable true if nillable
     */
    public void setNillable(boolean nillable) {
        boolean oldValue = this.nillable;
        this.nillable = nillable;
        pcs.firePropertyChange("nillable", oldValue, nillable);
    }

    /**
     * Checks if this element is abstract.
     *
     * @return true if abstract
     */
    public boolean isAbstract() {
        return abstractElement;
    }

    /**
     * Sets the abstract flag.
     *
     * @param abstractElement true if abstract
     */
    public void setAbstract(boolean abstractElement) {
        boolean oldValue = this.abstractElement;
        this.abstractElement = abstractElement;
        pcs.firePropertyChange("abstract", oldValue, abstractElement);
    }

    /**
     * Gets the fixed value.
     *
     * @return the fixed value, or null
     */
    public String getFixed() {
        return fixed;
    }

    /**
     * Sets the fixed value.
     *
     * @param fixed the fixed value
     */
    public void setFixed(String fixed) {
        String oldValue = this.fixed;
        this.fixed = fixed;
        pcs.firePropertyChange("fixed", oldValue, fixed);
    }

    /**
     * Gets the default value.
     *
     * @return the default value, or null
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(String defaultValue) {
        String oldValue = this.defaultValue;
        this.defaultValue = defaultValue;
        pcs.firePropertyChange("defaultValue", oldValue, defaultValue);
    }

    /**
     * Gets the substitution group.
     *
     * @return the substitution group, or null
     */
    public String getSubstitutionGroup() {
        return substitutionGroup;
    }

    /**
     * Sets the substitution group.
     *
     * @param substitutionGroup the substitution group
     */
    public void setSubstitutionGroup(String substitutionGroup) {
        String oldValue = this.substitutionGroup;
        this.substitutionGroup = substitutionGroup;
        pcs.firePropertyChange("substitutionGroup", oldValue, substitutionGroup);
    }

    /**
     * Gets the form attribute (qualified, unqualified).
     *
     * @return the form value
     */
    public String getForm() {
        return form;
    }

    /**
     * Sets the form attribute.
     *
     * @param form the form value (qualified, unqualified)
     */
    public void setForm(String form) {
        String oldValue = this.form;
        this.form = form;
        pcs.firePropertyChange("form", oldValue, form);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ELEMENT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Create new element with suffix appended to name
        String newName = suffix != null ? getName() + suffix : getName();
        XsdElement copy = new XsdElement(newName);

        // Copy XsdElement-specific properties
        copy.setType(this.type);
        copy.setNillable(this.nillable);
        copy.setAbstract(this.abstractElement);
        copy.setFixed(this.fixed);
        copy.setDefaultValue(this.defaultValue);
        copy.setSubstitutionGroup(this.substitutionGroup);
        copy.setForm(this.form);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
