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

    /**
     * Derivation method for simple types.
     */
    public enum DerivationMethod {
        RESTRICTION,  // xs:restriction
        LIST,         // xs:list
        UNION         // xs:union
    }

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id;

    private String name;
    private String baseType; // xs:string, xs:integer, etc. (for restriction)
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();
    private DerivationMethod derivationMethod = DerivationMethod.RESTRICTION;

    // Facets (for restriction)
    private final Map<String, String> facets = new LinkedHashMap<>();
    private final List<String> enumerations = new ArrayList<>();

    // xs:list support
    private String listItemType;  // itemType attribute (type reference)
    private XsdSimpleTypeModel inlineListItemType;  // inline simpleType child

    // xs:union support
    private final List<String> unionMemberTypes = new ArrayList<>();  // memberTypes attribute (space-separated list)
    private final List<XsdSimpleTypeModel> inlineUnionMemberTypes = new ArrayList<>();  // inline simpleType children

    // XSD 1.1 assertions (xs:assertion) for simple types
    private final List<XsdAssertModel> assertions = new ArrayList<>();

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

    /**
     * Returns the derivation method for this simple type.
     *
     * @return the derivation method
     */
    public DerivationMethod getDerivationMethod() {
        return derivationMethod;
    }

    /**
     * Sets the derivation method for this simple type.
     *
     * @param derivationMethod the derivation method
     */
    public void setDerivationMethod(DerivationMethod derivationMethod) {
        this.derivationMethod = derivationMethod != null ? derivationMethod : DerivationMethod.RESTRICTION;
    }

    /**
     * Returns the item type for xs:list (type reference).
     *
     * @return the item type, or null if not a list or uses inline type
     */
    public String getListItemType() {
        return listItemType;
    }

    /**
     * Sets the item type for xs:list (type reference).
     *
     * @param listItemType the item type
     */
    public void setListItemType(String listItemType) {
        this.listItemType = listItemType;
    }

    /**
     * Returns the inline item type for xs:list.
     *
     * @return the inline item type, or null if not present
     */
    public XsdSimpleTypeModel getInlineListItemType() {
        return inlineListItemType;
    }

    /**
     * Sets the inline item type for xs:list.
     *
     * @param inlineListItemType the inline item type
     */
    public void setInlineListItemType(XsdSimpleTypeModel inlineListItemType) {
        this.inlineListItemType = inlineListItemType;
    }

    /**
     * Returns the member types for xs:union (type references).
     *
     * @return unmodifiable list of member types
     */
    public List<String> getUnionMemberTypes() {
        return Collections.unmodifiableList(unionMemberTypes);
    }

    /**
     * Adds a member type reference for xs:union.
     *
     * @param memberType the member type reference
     */
    public void addUnionMemberType(String memberType) {
        unionMemberTypes.add(memberType);
    }

    /**
     * Returns the inline member types for xs:union.
     *
     * @return unmodifiable list of inline member types
     */
    public List<XsdSimpleTypeModel> getInlineUnionMemberTypes() {
        return Collections.unmodifiableList(inlineUnionMemberTypes);
    }

    /**
     * Adds an inline member type for xs:union.
     *
     * @param memberType the inline member type
     */
    public void addInlineUnionMemberType(XsdSimpleTypeModel memberType) {
        inlineUnionMemberTypes.add(memberType);
    }

    /**
     * Returns all XSD 1.1 assertions (xs:assertion) for this simple type.
     *
     * @return unmodifiable list of assertions
     */
    public List<XsdAssertModel> getAssertions() {
        return Collections.unmodifiableList(assertions);
    }

    /**
     * Adds an XSD 1.1 assertion (xs:assertion) to this simple type.
     *
     * @param assertion the assertion to add
     */
    public void addAssertion(XsdAssertModel assertion) {
        assertions.add(assertion);
    }

    /**
     * Removes an assertion from this simple type.
     *
     * @param assertion the assertion to remove
     */
    public void removeAssertion(XsdAssertModel assertion) {
        assertions.remove(assertion);
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
