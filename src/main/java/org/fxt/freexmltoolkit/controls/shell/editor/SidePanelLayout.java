package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Small layout helpers shared by the activity side panels. The panels are narrow (~250px),
 * so action buttons are laid out full-width in a single column with left-aligned icon + label
 * to keep their text readable instead of ellipsizing to "…".
 */
final class SidePanelLayout {

    private SidePanelLayout() {
    }

    /** Makes an action button fill the side-panel width with a left-aligned icon + label. */
    static <T extends ButtonBase> T fill(T button) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        return button;
    }

    /**
     * A collapsible section header: chevron + small bold label (shared mockup style,
     * {@code fxt-sp-section-header}). Clicking the header hides/shows the {@code content}
     * nodes and flips the chevron.
     */
    static HBox sectionHeader(Label label, Node... content) {
        return sectionHeader(false, label, content);
    }

    /**
     * A collapsible section header whose {@code content} nodes start out collapsed
     * when {@code initiallyCollapsed} is set (e.g. for secondary tools).
     */
    static HBox sectionHeader(boolean initiallyCollapsed, Label label, Node... content) {
        IconifyIcon chevron = new IconifyIcon(initiallyCollapsed ? "bi-chevron-right" : "bi-chevron-down");
        chevron.setIconSize(11);
        HBox header = new HBox(6, chevron, label);
        header.getStyleClass().add("fxt-sp-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        if (initiallyCollapsed) {
            setExpanded(content, false);
        }
        header.setOnMouseClicked(e -> {
            boolean expand = content.length > 0 && !content[0].isVisible();
            setExpanded(content, expand);
            chevron.setIconLiteral(expand ? "bi-chevron-down" : "bi-chevron-right");
        });
        return header;
    }

    private static void setExpanded(Node[] content, boolean expanded) {
        for (Node node : content) {
            node.setVisible(expanded);
            node.setManaged(expanded);
        }
    }
}
