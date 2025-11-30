package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD list type (xs:list).
 * A list type defines a whitespace-separated list of values from a specified item type.
 *
 * @since 2.0
 */
public class XsdList extends XsdNode {

    private String itemType; // The type of items in the list

    /**
     * Creates a new XSD list.
     */
    public XsdList() {
        super("list");
    }

    /**
     * Creates a new XSD list with an item type.
     *
     * @param itemType the item type
     */
    public XsdList(String itemType) {
        super("list");
        this.itemType = itemType;
    }

    /**
     * Gets the item type.
     *
     * @return the item type, or null
     */
    public String getItemType() {
        return itemType;
    }

    /**
     * Sets the item type.
     *
     * @param itemType the item type
     */
    public void setItemType(String itemType) {
        String oldValue = this.itemType;
        this.itemType = itemType;
        pcs.firePropertyChange("itemType", oldValue, itemType);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.LIST;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // List name is always "list", suffix is not applied
        XsdList copy = new XsdList();

        // Copy XsdList-specific properties
        copy.setItemType(this.itemType);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
