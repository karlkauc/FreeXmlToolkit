package org.fxt.freexmltoolkit.controls.unified;

import javafx.scene.control.Tab;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Base class for tool tabs in the Unified Editor.
 * Tool tabs represent multi-file workflows (e.g., FOP, Signatures)
 * rather than single-file editors. They have no source file or dirty tracking.
 */
public abstract class AbstractToolTab extends Tab {

    private final String toolId;

    protected AbstractToolTab(String toolId, String title, String iconLiteral, String iconColor) {
        super(title);
        this.toolId = toolId;
        setClosable(true);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(16);
        icon.setIconColor(javafx.scene.paint.Color.web(iconColor));
        setGraphic(icon);
    }

    /**
     * Gets the unique tool identifier (e.g., "tool:fop", "tool:signature").
     */
    public String getToolId() {
        return toolId;
    }
}
