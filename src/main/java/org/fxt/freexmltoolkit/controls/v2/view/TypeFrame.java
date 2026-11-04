package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;

/**
 * A run of a card's children that comes from a named complex type: the content of an element with
 * {@code type="T"}, or the base type's content inside an {@code xs:extension}. The graphic view
 * draws it as a titled, dashed box (XMLSpy style) and keeps the nodes inside it read-only: they
 * belong to the type, so they are changed by editing the type.
 *
 * <p>The range is {@code [from, to)} of the owning card's children; frames on one card nest (an
 * extension's base frame lies inside the derived type's frame) and {@link #level()} counts how many
 * frames of the same card lie inside this one. The bounds are set by the layout pass.
 */
public final class TypeFrame {

    private final String typeName;
    private final XsdComplexType type;
    private final int from;
    private final int to;
    private final int level;

    private double x;
    private double y;
    private double width;
    private double height;

    /**
     * @param typeName the type name as written in the schema (may carry a prefix)
     * @param type     the resolved type definition
     * @param from     the first child index (inclusive)
     * @param to       the end child index (exclusive)
     * @param level    the number of frames of the same card nested inside this one
     */
    public TypeFrame(String typeName, XsdComplexType type, int from, int to, int level) {
        this.typeName = typeName;
        this.type = type;
        this.from = from;
        this.to = to;
        this.level = level;
    }

    public String typeName() {
        return typeName;
    }

    public XsdComplexType type() {
        return type;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public int level() {
        return level;
    }

    /** @return whether the child at {@code index} lies inside this frame */
    public boolean contains(int index) {
        return index >= from && index < to;
    }

    /** @return whether {@code other}'s range lies inside this frame's range */
    public boolean encloses(TypeFrame other) {
        return other.from >= from && other.to <= to;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    /** Sets the frame rectangle in diagram coordinates (layout pass). */
    public void setBounds(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return whether the point lies inside the laid-out frame */
    public boolean containsPoint(double px, double py) {
        return width > 0 && px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Explains why a node inside this frame cannot be changed in place.
     *
     * @param nodeLabel the label of the node the user tried to change
     */
    public String lockReason(String nodeLabel) {
        String subject = nodeLabel == null || nodeLabel.isBlank() ? "This node" : "'" + nodeLabel + "'";
        return subject + " belongs to the type '" + typeName + "' and is shown read-only here. "
                + "To change it, edit the type: double-click the frame title or use "
                + "\"Edit Type '" + typeName + "'\" in the context menu.";
    }

    /**
     * Explains why nothing can be added under a node whose content is this frame.
     *
     * @param nodeLabel the label of the node the user tried to add to
     */
    public String contentLockReason(String nodeLabel) {
        if (nodeLabel != null && !nodeLabel.isBlank() && !nodeLabel.equals(typeName)) {
            return "The content of '" + nodeLabel + "' comes from the type '" + typeName
                    + "' and is shown read-only here. To add to it, edit the type: double-click the "
                    + "frame title or use \"Edit Referenced Type in Editor\" in the context menu.";
        }
        return lockReason(nodeLabel);
    }

    @Override
    public String toString() {
        return "TypeFrame[" + typeName + " " + from + ".." + to + " level " + level + "]";
    }
}
