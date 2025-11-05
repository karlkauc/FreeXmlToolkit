package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Model representing an XSD simple type.
 *
 * @since 2.0
 */
public class XsdSimpleTypeModel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id;

    private String name;
    private String baseType; // xs:string, xs:integer, etc.
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Facets
    private final Map<String, String> facets = new LinkedHashMap<>();
    private final List<String> enumerations = new ArrayList<>();

    public XsdSimpleTypeModel(String id, String name) {
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

    public Map<String, String> getFacets() {
        return Collections.unmodifiableMap(facets);
    }

    public void addFacet(String facetName, String value) {
        facets.put(facetName, value);
    }

    public void removeFacet(String facetName) {
        facets.remove(facetName);
    }

    public List<String> getEnumerations() {
        return Collections.unmodifiableList(enumerations);
    }

    public void addEnumeration(String value) {
        enumerations.add(value);
    }

    public void removeEnumeration(String value) {
        enumerations.remove(value);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return "XsdSimpleTypeModel{name='" + name + "', baseType='" + baseType + "'}";
    }
}
