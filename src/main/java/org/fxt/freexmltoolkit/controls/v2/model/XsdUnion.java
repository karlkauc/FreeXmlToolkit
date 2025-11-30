package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an XSD union type (xs:union).
 * A union type allows values from multiple member types.
 *
 * @since 2.0
 */
public class XsdUnion extends XsdNode {

    private final List<String> memberTypes = new ArrayList<>();

    /**
     * Creates a new XSD union.
     */
    public XsdUnion() {
        super("union");
    }

    /**
     * Creates a new XSD union with member types.
     *
     * @param memberTypes the member types
     */
    public XsdUnion(String... memberTypes) {
        super("union");
        this.memberTypes.addAll(Arrays.asList(memberTypes));
    }

    /**
     * Gets all member types.
     *
     * @return a copy of the member types list
     */
    public List<String> getMemberTypes() {
        return new ArrayList<>(memberTypes);
    }

    /**
     * Adds a member type to this union.
     *
     * @param memberType the member type to add
     */
    public void addMemberType(String memberType) {
        List<String> oldValue = new ArrayList<>(memberTypes);
        memberTypes.add(memberType);
        pcs.firePropertyChange("memberTypes", oldValue, new ArrayList<>(memberTypes));
    }

    /**
     * Removes a member type from this union.
     *
     * @param memberType the member type to remove
     */
    public void removeMemberType(String memberType) {
        List<String> oldValue = new ArrayList<>(memberTypes);
        memberTypes.remove(memberType);
        pcs.firePropertyChange("memberTypes", oldValue, new ArrayList<>(memberTypes));
    }

    /**
     * Sets all member types at once.
     *
     * @param memberTypes the member types
     */
    public void setMemberTypes(List<String> memberTypes) {
        List<String> oldValue = new ArrayList<>(this.memberTypes);
        this.memberTypes.clear();
        this.memberTypes.addAll(memberTypes);
        pcs.firePropertyChange("memberTypes", oldValue, new ArrayList<>(this.memberTypes));
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.UNION;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Union name is always "union", suffix is not applied
        XsdUnion copy = new XsdUnion();

        // Copy XsdUnion-specific properties
        copy.setMemberTypes(new ArrayList<>(this.memberTypes));

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
