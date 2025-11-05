package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a change in the XSD model for incremental updates.
 * This class is used by the diff algorithm to track changes between model states.
 *
 * @since 2.0
 */
public class XsdModelChange {

    /**
     * Type of change that occurred in the model.
     */
    public enum ChangeType {
        /**
         * A new node was added to the model
         */
        NODE_ADDED,
        /**
         * An existing node was removed from the model
         */
        NODE_REMOVED,
        /**
         * Properties of an existing node were modified
         */
        NODE_MODIFIED,
        /**
         * A node was moved to a different position or parent
         */
        NODE_MOVED,
        /**
         * Children order of a node changed
         */
        CHILDREN_REORDERED
    }

    private final ChangeType type;
    private final String nodeId;
    private final String parentId;
    private final NodeType nodeType;
    private final Set<String> changedProperties;
    private final Object oldValue;
    private final Object newValue;
    private final int oldPosition;
    private final int newPosition;

    /**
     * Type of XSD node that changed.
     */
    public enum NodeType {
        SCHEMA,
        ELEMENT,
        ATTRIBUTE,
        COMPLEX_TYPE,
        SIMPLE_TYPE,
        GROUP,
        SEQUENCE,
        CHOICE,
        ALL
    }

    private XsdModelChange(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "Change type cannot be null");
        this.nodeId = Objects.requireNonNull(builder.nodeId, "Node ID cannot be null");
        this.parentId = builder.parentId;
        this.nodeType = Objects.requireNonNull(builder.nodeType, "Node type cannot be null");
        this.changedProperties = builder.changedProperties != null
                ? Set.copyOf(builder.changedProperties)
                : Collections.emptySet();
        this.oldValue = builder.oldValue;
        this.newValue = builder.newValue;
        this.oldPosition = builder.oldPosition;
        this.newPosition = builder.newPosition;
    }

    /**
     * Creates a builder for NODE_ADDED change.
     *
     * @param nodeId   the ID of the added node
     * @param parentId the ID of the parent node
     * @param nodeType the type of the added node
     * @return a new builder instance
     */
    public static Builder nodeAdded(String nodeId, String parentId, NodeType nodeType) {
        return new Builder(ChangeType.NODE_ADDED, nodeId, nodeType).parentId(parentId);
    }

    /**
     * Creates a builder for NODE_REMOVED change.
     *
     * @param nodeId   the ID of the removed node
     * @param parentId the ID of the parent node
     * @param nodeType the type of the removed node
     * @return a new builder instance
     */
    public static Builder nodeRemoved(String nodeId, String parentId, NodeType nodeType) {
        return new Builder(ChangeType.NODE_REMOVED, nodeId, nodeType).parentId(parentId);
    }

    /**
     * Creates a builder for NODE_MODIFIED change.
     *
     * @param nodeId   the ID of the modified node
     * @param nodeType the type of the modified node
     * @return a new builder instance
     */
    public static Builder nodeModified(String nodeId, NodeType nodeType) {
        return new Builder(ChangeType.NODE_MODIFIED, nodeId, nodeType);
    }

    /**
     * Creates a builder for NODE_MOVED change.
     *
     * @param nodeId      the ID of the moved node
     * @param oldParentId the ID of the old parent
     * @param newParentId the ID of the new parent
     * @param nodeType    the type of the moved node
     * @return a new builder instance
     */
    public static Builder nodeMoved(String nodeId, String oldParentId, String newParentId, NodeType nodeType) {
        return new Builder(ChangeType.NODE_MOVED, nodeId, nodeType)
                .oldValue(oldParentId)
                .newValue(newParentId);
    }

    /**
     * Creates a builder for CHILDREN_REORDERED change.
     *
     * @param parentId the ID of the parent node whose children were reordered
     * @param nodeType the type of the parent node
     * @return a new builder instance
     */
    public static Builder childrenReordered(String parentId, NodeType nodeType) {
        return new Builder(ChangeType.CHILDREN_REORDERED, parentId, nodeType);
    }

    public ChangeType getType() {
        return type;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getParentId() {
        return parentId;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public Set<String> getChangedProperties() {
        return changedProperties;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public int getOldPosition() {
        return oldPosition;
    }

    public int getNewPosition() {
        return newPosition;
    }

    /**
     * Checks if a specific property was changed.
     *
     * @param propertyName the name of the property to check
     * @return true if the property was changed
     */
    public boolean hasPropertyChanged(String propertyName) {
        return changedProperties.contains(propertyName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("XsdModelChange{");
        sb.append("type=").append(type);
        sb.append(", nodeId='").append(nodeId).append('\'');
        sb.append(", nodeType=").append(nodeType);

        if (parentId != null) {
            sb.append(", parentId='").append(parentId).append('\'');
        }

        if (!changedProperties.isEmpty()) {
            sb.append(", changedProperties=").append(changedProperties);
        }

        if (oldValue != null || newValue != null) {
            sb.append(", oldValue=").append(oldValue);
            sb.append(", newValue=").append(newValue);
        }

        if (oldPosition >= 0 || newPosition >= 0) {
            sb.append(", oldPosition=").append(oldPosition);
            sb.append(", newPosition=").append(newPosition);
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Builder for creating XsdModelChange instances.
     */
    public static class Builder {
        private final ChangeType type;
        private final String nodeId;
        private final NodeType nodeType;
        private String parentId;
        private Set<String> changedProperties;
        private Object oldValue;
        private Object newValue;
        private int oldPosition = -1;
        private int newPosition = -1;

        private Builder(ChangeType type, String nodeId, NodeType nodeType) {
            this.type = type;
            this.nodeId = nodeId;
            this.nodeType = nodeType;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder changedProperties(Set<String> properties) {
            this.changedProperties = properties;
            return this;
        }

        public Builder changedProperty(String property) {
            if (this.changedProperties == null) {
                this.changedProperties = Set.of(property);
            } else {
                this.changedProperties = Set.copyOf(
                        new java.util.HashSet<>(this.changedProperties) {{
                            add(property);
                        }}
                );
            }
            return this;
        }

        public Builder oldValue(Object oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(Object newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder oldPosition(int oldPosition) {
            this.oldPosition = oldPosition;
            return this;
        }

        public Builder newPosition(int newPosition) {
            this.newPosition = newPosition;
            return this;
        }

        public XsdModelChange build() {
            return new XsdModelChange(this);
        }
    }
}
