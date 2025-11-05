package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base model representing XSD compositor elements (sequence, choice, all).
 * Compositors define how child elements are organized within a complex type or element.
 *
 * @since 2.0
 */
public abstract class XsdCompositorModel {

    private final String id;
    private final CompositorType type;
    private int minOccurs = 1;
    private int maxOccurs = 1;

    // Child elements within the compositor
    private final List<XsdElementModel> elements = new ArrayList<>();

    // Nested compositors (e.g., choice within sequence)
    private final List<XsdCompositorModel> compositors = new ArrayList<>();

    // Ordered list of all children (elements and compositors) in document order
    // This preserves the original sequence from the XSD file
    private final List<Object> childrenInOrder = new ArrayList<>();

    /**
     * Compositor types as defined in XSD specification.
     */
    public enum CompositorType {
        SEQUENCE,  // Elements must appear in the specified order
        CHOICE,    // Only one of the elements can appear
        ALL        // All elements can appear in any order, each 0 or 1 times
    }

    protected XsdCompositorModel(String id, CompositorType type) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.type = Objects.requireNonNull(type, "Compositor type cannot be null");
    }

    public String getId() {
        return id;
    }

    public CompositorType getType() {
        return type;
    }

    public int getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    public int getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(int maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public List<XsdElementModel> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void addElement(XsdElementModel element) {
        elements.add(element);
        childrenInOrder.add(element);
    }

    public void removeElement(XsdElementModel element) {
        elements.remove(element);
        childrenInOrder.remove(element);
    }

    public List<XsdCompositorModel> getCompositors() {
        return Collections.unmodifiableList(compositors);
    }

    public void addCompositor(XsdCompositorModel compositor) {
        compositors.add(compositor);
        childrenInOrder.add(compositor);
    }

    public void removeCompositor(XsdCompositorModel compositor) {
        compositors.remove(compositor);
        childrenInOrder.remove(compositor);
    }

    /**
     * Returns all children (elements and compositors) in document order.
     * This preserves the original sequence from the XSD file.
     *
     * @return unmodifiable list of children in document order
     */
    public List<Object> getChildrenInOrder() {
        return Collections.unmodifiableList(childrenInOrder);
    }

    /**
     * Returns the display label for this compositor.
     */
    public String getLabel() {
        return type.name().toLowerCase();
    }

    @Override
    public String toString() {
        return "XsdCompositorModel{type=" + type + ", elements=" + elements.size() + "}";
    }

    /**
     * Factory method to create compositor of specified type.
     */
    public static XsdCompositorModel create(String id, CompositorType type) {
        return switch (type) {
            case SEQUENCE -> new XsdSequenceModel(id);
            case CHOICE -> new XsdChoiceModel(id);
            case ALL -> new XsdAllModel(id);
        };
    }

    /**
     * Sequence compositor - elements must appear in order.
     */
    public static class XsdSequenceModel extends XsdCompositorModel {
        public XsdSequenceModel(String id) {
            super(id, CompositorType.SEQUENCE);
        }
    }

    /**
     * Choice compositor - only one element can appear.
     */
    public static class XsdChoiceModel extends XsdCompositorModel {
        public XsdChoiceModel(String id) {
            super(id, CompositorType.CHOICE);
        }
    }

    /**
     * All compositor - all elements can appear in any order.
     */
    public static class XsdAllModel extends XsdCompositorModel {
        public XsdAllModel(String id) {
            super(id, CompositorType.ALL);
        }
    }
}
