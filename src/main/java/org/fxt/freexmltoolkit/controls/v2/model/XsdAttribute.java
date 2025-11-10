package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD attribute (xs:attribute).
 *
 * @since 2.0
 */
public class XsdAttribute extends XsdNode {

    private String type;
    private String use; // required, optional, prohibited
    private String fixed;
    private String defaultValue;
    private String form; // qualified, unqualified

    /**
     * Creates a new XSD attribute.
     *
     * @param name the attribute name
     */
    public XsdAttribute(String name) {
        super(name);
        this.use = "optional"; // default
    }

    /**
     * Gets the type reference.
     *
     * @return the type name
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
     * Gets the use attribute (required, optional, prohibited).
     *
     * @return the use value
     */
    public String getUse() {
        return use;
    }

    /**
     * Sets the use attribute.
     *
     * @param use the use value (required, optional, prohibited)
     */
    public void setUse(String use) {
        String oldValue = this.use;
        this.use = use;
        pcs.firePropertyChange("use", oldValue, use);
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
        return XsdNodeType.ATTRIBUTE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdAttribute copy = new XsdAttribute(newName);

        // Copy XsdAttribute-specific properties
        copy.setType(this.type);
        copy.setUse(this.use);
        copy.setFixed(this.fixed);
        copy.setDefaultValue(this.defaultValue);
        copy.setForm(this.form);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
