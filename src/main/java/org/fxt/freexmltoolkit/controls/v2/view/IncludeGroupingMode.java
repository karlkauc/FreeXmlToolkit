package org.fxt.freexmltoolkit.controls.v2.view;

/**
 * Defines how nodes from multi-file XSD schemas (with xs:include) are displayed in the visual tree.
 * <p>
 * When an XSD schema uses xs:include to bring in components from other files,
 * this enum controls whether those components are shown flattened together
 * or grouped by their source file.
 *
 * @since 2.0
 */
public enum IncludeGroupingMode {

    /**
     * All components are shown together in a flat list, regardless of source file.
     * This is the traditional view mode where included content is merged with main schema content.
     * <p>
     * Best for: Editing when you don't care about file boundaries.
     */
    FLAT("Flat View", "Show all components together"),

    /**
     * Components are visually grouped by their source file.
     * File boundaries are indicated with separators or headers.
     * <p>
     * Best for: Understanding schema structure and finding components by file.
     */
    GROUPED("Grouped by File", "Group components by source file"),

    /**
     * Include nodes are expandable containers that show their included content.
     * The xs:include elements become tree nodes with children.
     * <p>
     * Best for: Navigating large schemas with many includes.
     */
    TREE("Tree View", "Show includes as expandable containers");

    private final String displayName;
    private final String description;

    IncludeGroupingMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the user-friendly display name.
     *
     * @return the display name for UI
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this mode.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
