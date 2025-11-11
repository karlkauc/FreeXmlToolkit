package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD 1.1 openContent element (xs:openContent).
 * OpenContent allows additional elements from a namespace wildcard to be mixed
 * into a content model in a controlled way.
 *
 * The mode attribute controls how the wildcard elements are inserted:
 * - "interleave": Elements can appear anywhere in the content model
 * - "suffix": Elements can only appear after the declared content
 *
 * @since 2.0
 */
public class XsdOpenContent extends XsdNode {

    /**
     * Mode for openContent processing.
     */
    public enum Mode {
        INTERLEAVE("interleave"),
        SUFFIX("suffix");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Mode fromValue(String value) {
            for (Mode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private Mode mode; // "interleave" or "suffix"

    /**
     * Creates a new XSD openContent element with default name.
     */
    public XsdOpenContent() {
        super("openContent");
        this.mode = Mode.INTERLEAVE; // Default mode
    }

    /**
     * Creates a new XSD openContent element with specified mode.
     *
     * @param mode the openContent mode
     */
    public XsdOpenContent(Mode mode) {
        super("openContent");
        this.mode = mode;
    }

    /**
     * Gets the mode attribute.
     *
     * @return the mode, or null
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Sets the mode attribute.
     *
     * @param mode the openContent mode ("interleave" or "suffix")
     */
    public void setMode(Mode mode) {
        Mode oldValue = this.mode;
        this.mode = mode;
        pcs.firePropertyChange("mode", oldValue, mode);
    }

    /**
     * Gets the wildcard child element if present.
     * In XSD, this would typically be an xs:any element representing a namespace wildcard.
     *
     * @return the first child node (representing the wildcard), or null
     */
    public XsdNode getWildcard() {
        return getChildren().isEmpty() ? null : getChildren().get(0);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.OPEN_CONTENT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdOpenContent copy = new XsdOpenContent(this.mode);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
