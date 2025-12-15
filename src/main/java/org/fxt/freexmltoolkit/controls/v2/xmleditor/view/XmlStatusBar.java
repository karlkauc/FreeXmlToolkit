package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Status bar component for the XML Editor V2.
 * Displays breadcrumb navigation, element count, dirty indicator, and last action.
 */
public class XmlStatusBar extends HBox {

    // Breadcrumb navigation
    private final HBox breadcrumbContainer;
    private final List<Label> breadcrumbLabels = new ArrayList<>();

    // Status info
    private final Label elementCountLabel;
    private final Label dirtyIndicator;
    private final Label lastActionLabel;

    // Callback for breadcrumb navigation
    private Consumer<XmlNode> onBreadcrumbClick;

    // Current path
    private final List<XmlNode> currentPath = new ArrayList<>();

    // Styling constants
    private static final String BREADCRUMB_SEPARATOR = " > ";
    private static final Color BREADCRUMB_COLOR = Color.rgb(59, 130, 246);  // Blue
    private static final Color BREADCRUMB_HOVER_COLOR = Color.rgb(37, 99, 235);
    private static final Color STATUS_TEXT_COLOR = Color.rgb(107, 114, 128);  // Gray
    private static final Color DIRTY_COLOR = Color.rgb(239, 68, 68);  // Red
    private static final Color ACTION_COLOR = Color.rgb(34, 197, 94);  // Green

    public XmlStatusBar() {
        super(10);  // spacing
        setPadding(new Insets(6, 12, 6, 12));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 1 0 0 0;");

        // Breadcrumb container (left side)
        breadcrumbContainer = new HBox(0);
        breadcrumbContainer.setAlignment(Pos.CENTER_LEFT);

        // Spacer to push status info to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status info container (right side)
        HBox statusContainer = new HBox(12);
        statusContainer.setAlignment(Pos.CENTER_RIGHT);

        // Element count
        FontIcon countIcon = new FontIcon(BootstrapIcons.CODE_SLASH);
        countIcon.setIconSize(12);
        countIcon.setIconColor(STATUS_TEXT_COLOR);
        elementCountLabel = createStatusLabel("0 elements");
        HBox countBox = new HBox(4, countIcon, elementCountLabel);
        countBox.setAlignment(Pos.CENTER);

        // Dirty indicator
        dirtyIndicator = new Label("");
        dirtyIndicator.setFont(Font.font("System", FontWeight.BOLD, 14));
        dirtyIndicator.setTextFill(DIRTY_COLOR);
        dirtyIndicator.setVisible(false);

        // Last action
        lastActionLabel = createStatusLabel("");
        lastActionLabel.setTextFill(ACTION_COLOR);

        // Separator
        Separator separator1 = createVerticalSeparator();
        Separator separator2 = createVerticalSeparator();

        statusContainer.getChildren().addAll(
            lastActionLabel,
            separator1,
            countBox,
            separator2,
            dirtyIndicator
        );

        getChildren().addAll(breadcrumbContainer, spacer, statusContainer);
    }

    /**
     * Sets the callback for breadcrumb clicks.
     */
    public void setOnBreadcrumbClick(Consumer<XmlNode> callback) {
        this.onBreadcrumbClick = callback;
    }

    /**
     * Updates the breadcrumb to show the path to the given node.
     */
    public void updateBreadcrumb(XmlNode selectedNode) {
        breadcrumbContainer.getChildren().clear();
        breadcrumbLabels.clear();
        currentPath.clear();

        if (selectedNode == null) {
            Label emptyLabel = new Label("No selection");
            emptyLabel.setTextFill(STATUS_TEXT_COLOR);
            emptyLabel.setFont(Font.font("System", 11));
            breadcrumbContainer.getChildren().add(emptyLabel);
            return;
        }

        // Build path from root to selected node
        buildPath(selectedNode);

        // Create breadcrumb labels
        for (int i = 0; i < currentPath.size(); i++) {
            XmlNode node = currentPath.get(i);
            boolean isLast = (i == currentPath.size() - 1);

            // Add separator before (except for first item)
            if (i > 0) {
                Label separatorLabel = new Label(BREADCRUMB_SEPARATOR);
                separatorLabel.setTextFill(STATUS_TEXT_COLOR);
                separatorLabel.setFont(Font.font("System", 11));
                breadcrumbContainer.getChildren().add(separatorLabel);
            }

            // Create breadcrumb label
            Label label = createBreadcrumbLabel(node, isLast);
            breadcrumbLabels.add(label);
            breadcrumbContainer.getChildren().add(label);
        }
    }

    /**
     * Updates the element count display.
     */
    public void updateElementCount(int count) {
        elementCountLabel.setText(count + (count == 1 ? " element" : " elements"));
    }

    /**
     * Updates the dirty indicator visibility.
     */
    public void setDirty(boolean dirty) {
        dirtyIndicator.setText(dirty ? "*" : "");
        dirtyIndicator.setVisible(dirty);
    }

    /**
     * Shows a temporary action message.
     */
    public void showLastAction(String action) {
        lastActionLabel.setText(action);

        // Auto-clear after 3 seconds
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(e -> lastActionLabel.setText(""));
        pause.play();
    }

    // ==================== Private Helper Methods ====================

    private void buildPath(XmlNode node) {
        if (node == null) return;

        // Build path recursively (parent first)
        if (node.getParent() != null && !(node.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument)) {
            buildPath(node.getParent());
        }

        currentPath.add(node);
    }

    private Label createBreadcrumbLabel(XmlNode node, boolean isLast) {
        String name = getNodeDisplayName(node);
        Label label = new Label(name);
        label.setFont(Font.font("System", isLast ? FontWeight.BOLD : FontWeight.NORMAL, 11));

        if (isLast) {
            // Current selection - not clickable
            label.setTextFill(Color.rgb(31, 41, 55));  // Dark gray
        } else {
            // Clickable ancestor
            label.setTextFill(BREADCRUMB_COLOR);
            label.setCursor(javafx.scene.Cursor.HAND);

            // Hover effect
            label.setOnMouseEntered(e -> label.setTextFill(BREADCRUMB_HOVER_COLOR));
            label.setOnMouseExited(e -> label.setTextFill(BREADCRUMB_COLOR));

            // Click handler
            final XmlNode targetNode = node;
            label.setOnMouseClicked(e -> {
                if (onBreadcrumbClick != null) {
                    onBreadcrumbClick.accept(targetNode);
                }
            });
        }

        return label;
    }

    private String getNodeDisplayName(XmlNode node) {
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element) {
            return element.getName();
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText) {
            return "#text";
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment) {
            return "#comment";
        } else {
            return node.getClass().getSimpleName();
        }
    }

    private Label createStatusLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(STATUS_TEXT_COLOR);
        label.setFont(Font.font("System", 11));
        return label;
    }

    private Separator createVerticalSeparator() {
        Separator separator = new Separator(javafx.geometry.Orientation.VERTICAL);
        separator.setPrefHeight(14);
        return separator;
    }
}
