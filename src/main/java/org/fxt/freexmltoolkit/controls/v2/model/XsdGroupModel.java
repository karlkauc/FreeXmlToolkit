package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Objects;

/**
 * Model representing an XSD group definition (xs:group).
 * Groups allow reusing a set of element declarations.
 *
 * Example:
 * <pre>{@code
 * <xs:group name="AddressGroup">
 *   <xs:sequence>
 *     <xs:element name="street" type="xs:string"/>
 *     <xs:element name="city" type="xs:string"/>
 *   </xs:sequence>
 * </xs:group>
 * }</pre>
 *
 * @since 2.0
 */
public class XsdGroupModel {

    private final String id;
    private final String name;
    private String documentation;

    // A group contains a compositor (sequence, choice, or all)
    private XsdCompositorModel compositor;

    public XsdGroupModel(String id, String name) {
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

    public XsdCompositorModel getCompositor() {
        return compositor;
    }

    public void setCompositor(XsdCompositorModel compositor) {
        this.compositor = compositor;
    }

    @Override
    public String toString() {
        return "XsdGroupModel{name='" + name + "'}";
    }
}
