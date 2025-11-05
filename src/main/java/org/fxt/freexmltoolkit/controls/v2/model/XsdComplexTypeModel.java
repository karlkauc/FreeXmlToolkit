package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model representing an XSD complex type.
 *
 * @since 2.0
 */
public class XsdComplexTypeModel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id;

    private String name;
    private boolean mixedContent = false;
    private boolean abstractType = false;
    private String baseType; // for extensions/restrictions
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Content model
    private final List<XsdElementModel> elements = new ArrayList<>();
    private final List<XsdAttributeModel> attributes = new ArrayList<>();

    // Compositors (sequence, choice, all) that organize elements
    private final List<XsdCompositorModel> compositors = new ArrayList<>();

    public XsdComplexTypeModel(String id, String name) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isMixedContent() {
        return mixedContent;
    }

    public void setMixedContent(boolean mixedContent) {
        this.mixedContent = mixedContent;
    }

    public boolean isAbstractType() {
        return abstractType;
    }

    public void setAbstractType(boolean abstractType) {
        this.abstractType = abstractType;
    }

    public String getBaseType() {
        return baseType;
    }

    public void setBaseType(String baseType) {
        this.baseType = baseType;
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

    public List<XsdElementModel> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void addElement(XsdElementModel element) {
        elements.add(element);
    }

    public List<XsdAttributeModel> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(XsdAttributeModel attribute) {
        attributes.add(attribute);
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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return "XsdComplexTypeModel{name='" + name + "'}";
    }
}
