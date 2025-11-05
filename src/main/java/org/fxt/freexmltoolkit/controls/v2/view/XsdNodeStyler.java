package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;

/**
 * Handles styling and theming for XSD nodes in the graph view.
 * Provides color-coded styles for different node types.
 *
 * @since 2.0
 */
public class XsdNodeStyler {

    // Color scheme for different node types
    private static final String ELEMENT_STYLE = "-fx-text-fill: #2563eb; -fx-font-weight: bold;";
    private static final String ATTRIBUTE_STYLE = "-fx-text-fill: #dc2626; -fx-font-style: italic;";
    private static final String COMPLEX_TYPE_STYLE = "-fx-text-fill: #7c3aed; -fx-font-weight: bold;";
    private static final String SIMPLE_TYPE_STYLE = "-fx-text-fill: #059669; -fx-font-weight: bold;";
    private static final String GROUP_STYLE = "-fx-text-fill: #ea580c; -fx-font-weight: bold;";
    private static final String SCHEMA_STYLE = "-fx-text-fill: #1e293b; -fx-font-weight: bold; -fx-font-size: 14px;";
    private static final String ENUMERATION_STYLE = "-fx-text-fill: #64748b; -fx-font-size: 11px;";
    private static final String SEQUENCE_STYLE = "-fx-text-fill: #0891b2; -fx-font-weight: bold; -fx-font-style: italic;";  // Cyan
    private static final String CHOICE_STYLE = "-fx-text-fill: #f59e0b; -fx-font-weight: bold; -fx-font-style: italic;";    // Amber
    private static final String ALL_STYLE = "-fx-text-fill: #8b5cf6; -fx-font-weight: bold; -fx-font-style: italic;";       // Violet
    private static final String DEFAULT_STYLE = "-fx-text-fill: #334155;";

    /**
     * Returns the CSS style for the given node type.
     *
     * @param type the node type
     * @return CSS style string
     */
    public String getNodeStyle(NodeWrapperType type) {
        return switch (type) {
            case SCHEMA -> SCHEMA_STYLE;
            case ELEMENT -> ELEMENT_STYLE;
            case ATTRIBUTE -> ATTRIBUTE_STYLE;
            case COMPLEX_TYPE -> COMPLEX_TYPE_STYLE;
            case SIMPLE_TYPE -> SIMPLE_TYPE_STYLE;
            case GROUP -> GROUP_STYLE;
            case ENUMERATION -> ENUMERATION_STYLE;
            case SEQUENCE -> SEQUENCE_STYLE;
            case CHOICE -> CHOICE_STYLE;
            case ALL -> ALL_STYLE;
            default -> DEFAULT_STYLE;
        };
    }

    /**
     * Returns a description of the color scheme used.
     *
     * @return color scheme description
     */
    public String getColorSchemeDescription() {
        return """
                XSD Node Color Scheme:
                - Blue (Elements): Primary structure nodes
                - Red (Attributes): Element attributes
                - Purple (Complex Types): Complex type definitions
                - Green (Simple Types): Simple type definitions
                - Orange (Groups): Element/attribute groups
                - Dark (Schema): Root schema node
                - Gray (Enumerations): Enumeration values
                """;
    }
}
