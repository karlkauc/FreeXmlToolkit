package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model representing an XSD element.
 *
 * @since 2.0
 */
public class XsdElementModel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id;

    private String name;
    private String type;
    private int minOccurs = 1;
    private int maxOccurs = 1;
    private boolean nillable = false;
    private String defaultValue;
    private String fixedValue;
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Child elements (for inline complex types)
    private final List<XsdElementModel> children = new ArrayList<>();
    private final List<XsdAttributeModel> attributes = new ArrayList<>();

    // Compositors (sequence, choice, all) that organize child elements
    private final List<XsdCompositorModel> compositors = new ArrayList<>();

    // Inline simple type (for elements with inline simple type definitions)
    private XsdSimpleTypeModel inlineSimpleType;

    // XSD 1.1 alternatives for conditional type assignment
    private final List<XsdAlternativeModel> alternatives = new ArrayList<>();

    // Property names
    public static final String PROP_NAME = "name";
    public static final String PROP_TYPE = "type";
    public static final String PROP_MIN_OCCURS = "minOccurs";
    public static final String PROP_MAX_OCCURS = "maxOccurs";

    public XsdElementModel(String id, String name) {
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

    public int getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(int minOccurs) {
        int oldValue = this.minOccurs;
        this.minOccurs = minOccurs;
        pcs.firePropertyChange(PROP_MIN_OCCURS, oldValue, minOccurs);
    }

    public int getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(int maxOccurs) {
        int oldValue = this.maxOccurs;
        this.maxOccurs = maxOccurs;
        pcs.firePropertyChange(PROP_MAX_OCCURS, oldValue, maxOccurs);
    }

    public boolean isNillable() {
        return nillable;
    }

    public void setNillable(boolean nillable) {
        this.nillable = nillable;
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

    public List<XsdElementModel> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(XsdElementModel child) {
        children.add(child);
    }

    public void removeChild(XsdElementModel child) {
        children.remove(child);
    }

    public List<XsdAttributeModel> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(XsdAttributeModel attribute) {
        attributes.add(attribute);
    }

    public void removeAttribute(XsdAttributeModel attribute) {
        attributes.remove(attribute);
    }

    public List<XsdCompositorModel> getCompositors() {
        return Collections.unmodifiableList(compositors);
    }

    public void addCompositor(XsdCompositorModel compositor) {
        compositors.add(compositor);
    }

    public void removeCompositor(XsdCompositorModel compositor) {
        compositors.remove(compositor);
    }

    public XsdSimpleTypeModel getInlineSimpleType() {
        return inlineSimpleType;
    }

    public void setInlineSimpleType(XsdSimpleTypeModel inlineSimpleType) {
        this.inlineSimpleType = inlineSimpleType;
    }

    /**
     * Returns all XSD 1.1 alternatives (xs:alternative) for this element.
     *
     * @return unmodifiable list of alternatives
     */
    public List<XsdAlternativeModel> getAlternatives() {
        return Collections.unmodifiableList(alternatives);
    }

    /**
     * Adds an XSD 1.1 alternative (xs:alternative) to this element.
     *
     * @param alternative the alternative to add
     */
    public void addAlternative(XsdAlternativeModel alternative) {
        alternatives.add(alternative);
    }

    /**
     * Removes an alternative from this element.
     *
     * @param alternative the alternative to remove
     */
    public void removeAlternative(XsdAlternativeModel alternative) {
        alternatives.remove(alternative);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return "XsdElementModel{name='" + name + "', type='" + type + "'}";
    }
}
