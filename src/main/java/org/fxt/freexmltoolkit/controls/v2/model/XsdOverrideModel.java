package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Model representing an XSD 1.1 override element.
 * <p>
 * The xs:override element allows a schema to include another schema while
 * overriding specific component definitions from the included schema.
 * This is a more flexible alternative to xs:redefine.
 * </p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
 *   <xs:override schemaLocation="base.xsd">
 *     <xs:complexType name="PersonType">
 *       <xs:sequence>
 *         <xs:element name="name" type="xs:string"/>
 *         <xs:element name="email" type="xs:string"/>
 *       </xs:sequence>
 *     </xs:complexType>
 *   </xs:override>
 * </xs:schema>
 * }</pre>
 *
 * @since 2.0
 */
public class XsdOverrideModel {

    private final String id;
    private String schemaLocation;
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Override components (complexType, simpleType, group, attributeGroup)
    private final Map<String, XsdComplexTypeModel> overriddenComplexTypes = new LinkedHashMap<>();
    private final Map<String, XsdSimpleTypeModel> overriddenSimpleTypes = new LinkedHashMap<>();
    private final Map<String, XsdGroupModel> overriddenGroups = new LinkedHashMap<>();
    private final Map<String, XsdAttributeGroupModel> overriddenAttributeGroups = new LinkedHashMap<>();

    /**
     * Creates a new override model.
     *
     * @param id             unique identifier
     * @param schemaLocation the schema location to override
     */
    public XsdOverrideModel(String id, String schemaLocation) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.schemaLocation = schemaLocation;
    }

    /**
     * Returns the unique identifier.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the schema location.
     *
     * @return the schema location
     */
    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Sets the schema location.
     *
     * @param schemaLocation the schema location
     */
    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    /**
     * Returns the documentation.
     *
     * @return the documentation
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation.
     *
     * @param documentation the documentation
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Returns the documentation info.
     *
     * @return the doc info
     */
    public XsdDocInfo getDocInfo() {
        return docInfo;
    }

    /**
     * Sets the documentation info.
     *
     * @param docInfo the doc info
     */
    public void setDocInfo(XsdDocInfo docInfo) {
        this.docInfo = docInfo != null ? docInfo : new XsdDocInfo();
    }

    /**
     * Returns all overridden complex types.
     *
     * @return unmodifiable map of type name to type model
     */
    public Map<String, XsdComplexTypeModel> getOverriddenComplexTypes() {
        return Collections.unmodifiableMap(overriddenComplexTypes);
    }

    /**
     * Adds an overridden complex type.
     *
     * @param name the type name
     * @param type the type model
     */
    public void addOverriddenComplexType(String name, XsdComplexTypeModel type) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        overriddenComplexTypes.put(name, type);
    }

    /**
     * Returns all overridden simple types.
     *
     * @return unmodifiable map of type name to type model
     */
    public Map<String, XsdSimpleTypeModel> getOverriddenSimpleTypes() {
        return Collections.unmodifiableMap(overriddenSimpleTypes);
    }

    /**
     * Adds an overridden simple type.
     *
     * @param name the type name
     * @param type the type model
     */
    public void addOverriddenSimpleType(String name, XsdSimpleTypeModel type) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        overriddenSimpleTypes.put(name, type);
    }

    /**
     * Returns all overridden groups.
     *
     * @return unmodifiable map of group name to group model
     */
    public Map<String, XsdGroupModel> getOverriddenGroups() {
        return Collections.unmodifiableMap(overriddenGroups);
    }

    /**
     * Adds an overridden group.
     *
     * @param name  the group name
     * @param group the group model
     */
    public void addOverriddenGroup(String name, XsdGroupModel group) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(group, "Group cannot be null");
        overriddenGroups.put(name, group);
    }

    /**
     * Returns all overridden attribute groups.
     *
     * @return unmodifiable map of attribute group name to attribute group model
     */
    public Map<String, XsdAttributeGroupModel> getOverriddenAttributeGroups() {
        return Collections.unmodifiableMap(overriddenAttributeGroups);
    }

    /**
     * Adds an overridden attribute group.
     *
     * @param name           the attribute group name
     * @param attributeGroup the attribute group model
     */
    public void addOverriddenAttributeGroup(String name, XsdAttributeGroupModel attributeGroup) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(attributeGroup, "Attribute group cannot be null");
        overriddenAttributeGroups.put(name, attributeGroup);
    }

    @Override
    public String toString() {
        String sb = "XsdOverrideModel{" + "schemaLocation='" + schemaLocation + "'" +
                ", overriddenComplexTypes=" + overriddenComplexTypes.size() +
                ", overriddenSimpleTypes=" + overriddenSimpleTypes.size() +
                ", overriddenGroups=" + overriddenGroups.size() +
                ", overriddenAttributeGroups=" + overriddenAttributeGroups.size() +
                "}";
        return sb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdOverrideModel that = (XsdOverrideModel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
