package org.fxt.freexmltoolkit.controls.v2.editor.usage;

/**
 * Enumeration of ways a type can be referenced in XSD.
 * Used by {@link TypeUsageFinder} to categorize usage locations.
 *
 * @since 2.0
 */
public enum UsageReferenceType {

    /**
     * Element type attribute: {@code <xs:element type="MyType"/>}
     */
    ELEMENT_TYPE("Element type"),

    /**
     * Attribute type attribute: {@code <xs:attribute type="MyType"/>}
     */
    ATTRIBUTE_TYPE("Attribute type"),

    /**
     * Restriction base type: {@code <xs:restriction base="MyType"/>}
     */
    RESTRICTION_BASE("Restriction base"),

    /**
     * Extension base type: {@code <xs:extension base="MyType"/>}
     */
    EXTENSION_BASE("Extension base"),

    /**
     * List item type: {@code <xs:list itemType="MyType"/>}
     */
    LIST_ITEM_TYPE("List item type"),

    /**
     * Union member type: {@code <xs:union memberTypes="MyType OtherType"/>}
     */
    UNION_MEMBER_TYPE("Union member type"),

    /**
     * Alternative type (XSD 1.1): {@code <xs:alternative type="MyType"/>}
     */
    ALTERNATIVE_TYPE("Alternative type");

    private final String displayName;

    UsageReferenceType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name for this reference type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
