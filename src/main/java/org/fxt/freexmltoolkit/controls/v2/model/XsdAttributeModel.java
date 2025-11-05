package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

/**
 * Model representing an XSD attribute.
 *
 * @since 2.0
 */
public class XsdAttributeModel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id;

    private String name;
    private String type;
    private boolean required = false;
    private String defaultValue;
    private String fixedValue;
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Property names
    public static final String PROP_NAME = "name";
    public static final String PROP_TYPE = "type";
    public static final String PROP_REQUIRED = "required";

    public XsdAttributeModel(String id, String name) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String oldValue = this.name;
        this.name = name;
        pcs.firePropertyChange(PROP_NAME, oldValue, name);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        String oldValue = this.type;
        this.type = type;
        pcs.firePropertyChange(PROP_TYPE, oldValue, type);
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        boolean oldValue = this.required;
        this.required = required;
        pcs.firePropertyChange(PROP_REQUIRED, oldValue, required);
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getFixedValue() {
        return fixedValue;
    }

    public void setFixedValue(String fixedValue) {
        this.fixedValue = fixedValue;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public XsdDocInfo getDocInfo() {
        return docInfo;
    }

    public void setDocInfo(XsdDocInfo docInfo) {
        this.docInfo = docInfo != null ? docInfo : new XsdDocInfo();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return "XsdAttributeModel{name='" + name + "', type='" + type + "', required=" + required + '}';
    }
}
