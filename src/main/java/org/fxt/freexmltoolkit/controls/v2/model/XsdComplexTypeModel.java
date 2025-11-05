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

    // XSD 1.1 assertions (xs:assert) for complex types
    private final List<XsdAssertModel> assertions = new ArrayList<>();

    // XSD 1.1 open content (xs:openContent)
    private XsdOpenContentModel openContent;

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

    /**
     * Returns all XSD 1.1 assertions (xs:assert) for this complex type.
     *
     * @return unmodifiable list of assertions
     */
    public List<XsdAssertModel> getAssertions() {
        return Collections.unmodifiableList(assertions);
    }

    /**
     * Adds an XSD 1.1 assertion (xs:assert) to this complex type.
     *
     * @param assertion the assertion to add
     */
    public void addAssertion(XsdAssertModel assertion) {
        assertions.add(assertion);
    }

    /**
     * Removes an assertion from this complex type.
     *
     * @param assertion the assertion to remove
     */
    public void removeAssertion(XsdAssertModel assertion) {
        assertions.remove(assertion);
    }

    /**
     * Returns the XSD 1.1 open content (xs:openContent) for this complex type.
     *
     * @return the open content, or null if not present
     */
    public XsdOpenContentModel getOpenContent() {
        return openContent;
    }

    /**
     * Sets the XSD 1.1 open content (xs:openContent) for this complex type.
     *
     * @param openContent the open content
     */
    public void setOpenContent(XsdOpenContentModel openContent) {
        this.openContent = openContent;
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
