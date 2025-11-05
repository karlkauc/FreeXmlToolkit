package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model representing an XSD attribute group definition (xs:attributeGroup).
 * Attribute groups allow reusing a set of attribute declarations.
 * <p>
 * Example:
 * <pre>{@code
 * <xs:attributeGroup name="CommonAttributes">
 *   <xs:attribute name="id" type="xs:ID"/>
 *   <xs:attribute name="version" type="xs:string"/>
 * </xs:attributeGroup>
 * }</pre>
 *
 * @since 2.0
 */
public class XsdAttributeGroupModel {

    private final String id;
    private final String name;
    private String documentation;

    // List of attributes in this group
    private final List<XsdAttributeModel> attributes = new ArrayList<>();

    public XsdAttributeGroupModel(String id, String name) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public List<XsdAttributeModel> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(XsdAttributeModel attribute) {
        attributes.add(attribute);
    }

    @Override
    public String toString() {
        return "XsdAttributeGroupModel{name='" + name + "', attributes=" + attributes.size() + "}";
    }
}
