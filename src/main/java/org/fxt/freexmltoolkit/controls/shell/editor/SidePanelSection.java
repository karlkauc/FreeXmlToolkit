package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * A collapsible side-panel section: a header (chevron + small bold label, shared mockup style
 * {@code fxt-sp-section-header}) followed by the section's content. Clicking the header hides
 * or shows the content and flips the chevron. The FXML counterpart of
 * {@link SidePanelLayout#sectionHeader}.
 *
 * <p>FXML: {@code <SidePanelSection title="INPUT">…content nodes…</SidePanelSection>}; add
 * {@code collapsed="true"} for secondary tools that start out closed.
 */
public final class SidePanelSection extends VBox {

    private final Label label = new Label();
    private final IconifyIcon chevron = new IconifyIcon("bi-chevron-down");
    private final HBox header = new HBox(6, chevron, label);
    private final BooleanProperty collapsed = new SimpleBooleanProperty(this, "collapsed", false);

    public SidePanelSection() {
        chevron.setIconSize(11);
        header.getStyleClass().add("fxt-sp-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMouseClicked(e -> setCollapsed(!isCollapsed()));
        getChildren().add(header);

        collapsed.addListener((obs, was, is) -> applyCollapsed());
        // Content arrives after the attributes when loaded from FXML.
        getChildren().addListener((ListChangeListener<Node>) change -> applyCollapsed());
    }

    private void applyCollapsed() {
        boolean expanded = !isCollapsed();
        chevron.setIconLiteral(expanded ? "bi-chevron-down" : "bi-chevron-right");
        for (Node node : getChildren()) {
            if (node != header) {
                node.setVisible(expanded);
                node.setManaged(expanded);
            }
        }
    }

    /** The section title, shown as given (upper case by convention). */
    public StringProperty titleProperty() {
        return label.textProperty();
    }

    public String getTitle() {
        return label.getText();
    }

    public void setTitle(String title) {
        label.setText(title);
    }

    /** Whether the content is hidden; the header stays visible. */
    public BooleanProperty collapsedProperty() {
        return collapsed;
    }

    public boolean isCollapsed() {
        return collapsed.get();
    }

    public void setCollapsed(boolean value) {
        collapsed.set(value);
    }
}
