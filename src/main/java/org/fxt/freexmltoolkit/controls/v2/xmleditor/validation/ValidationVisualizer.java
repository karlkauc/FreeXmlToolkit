/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.fxt.freexmltoolkit.controls.v2.xmleditor.validation;

import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides visual feedback for validation results in the XML editor.
 *
 * <p>This class creates and manages visual indicators for validation status:</p>
 * <ul>
 *   <li>Border colors (red for errors, orange for warnings, green for valid)</li>
 *   <li>Icons (error, warning, info)</li>
 *   <li>Tooltips with error messages</li>
 *   <li>Overlays for highlighting invalid regions</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class ValidationVisualizer {

    // Color constants
    public static final Color ERROR_COLOR = Color.web("#dc3545");      // Red
    public static final Color WARNING_COLOR = Color.web("#ffc107");    // Yellow/Orange
    public static final Color VALID_COLOR = Color.web("#28a745");      // Green
    public static final Color INFO_COLOR = Color.web("#17a2b8");       // Teal

    // Background colors (lighter versions)
    public static final Color ERROR_BG_COLOR = Color.web("#ffe6e6");
    public static final Color WARNING_BG_COLOR = Color.web("#fff8e1");
    public static final Color VALID_BG_COLOR = Color.web("#e8f5e9");

    // Style strings
    private static final String ERROR_STYLE =
            "-fx-border-color: #dc3545; -fx-border-width: 2px; -fx-background-color: #ffe6e6;";
    private static final String WARNING_STYLE =
            "-fx-border-color: #ffc107; -fx-border-width: 2px; -fx-background-color: #fff8e1;";
    private static final String VALID_STYLE =
            "-fx-border-color: #28a745; -fx-border-width: 1px;";
    private static final String NEUTRAL_STYLE =
            "-fx-border-color: #ced4da; -fx-border-width: 1px;";

    // Cache for validation overlays
    private final Map<String, Node> overlayCache = new HashMap<>();

    /**
     * Applies validation styling to a node based on the validation result.
     *
     * @param node   the node to style
     * @param result the validation result
     */
    public void applyValidationStyle(Node node, ValidationResult result) {
        if (node == null) return;

        String style = getValidationStyle(result);
        node.setStyle(style);

        // Apply tooltip
        if (result != null && result.errorMessage() != null && !result.errorMessage().isEmpty()) {
            Tooltip tooltip = new Tooltip(result.errorMessage());
            tooltip.setShowDelay(Duration.millis(200));
            tooltip.setStyle(getTooltipStyle(result.severity()));
            Tooltip.install(node, tooltip);
        } else {
            Tooltip.uninstall(node, null);
        }
    }

    /**
     * Gets the CSS style string for a validation result.
     *
     * @param result the validation result
     * @return CSS style string
     */
    public String getValidationStyle(ValidationResult result) {
        if (result == null) {
            return NEUTRAL_STYLE;
        }

        if (!result.isValid()) {
            return ERROR_STYLE;
        }

        return switch (result.severity()) {
            case ERROR -> ERROR_STYLE;
            case WARNING -> WARNING_STYLE;
            case INFO -> VALID_STYLE;
        };
    }

    /**
     * Gets the border color for a validation result.
     *
     * @param result the validation result
     * @return the border color
     */
    public Color getBorderColor(ValidationResult result) {
        if (result == null) {
            return Color.web("#ced4da");
        }

        if (!result.isValid()) {
            return ERROR_COLOR;
        }

        return switch (result.severity()) {
            case ERROR -> ERROR_COLOR;
            case WARNING -> WARNING_COLOR;
            case INFO -> VALID_COLOR;
        };
    }

    /**
     * Gets the background color for a validation result.
     *
     * @param result the validation result
     * @return the background color
     */
    public Color getBackgroundColor(ValidationResult result) {
        if (result == null) {
            return Color.TRANSPARENT;
        }

        if (!result.isValid()) {
            return ERROR_BG_COLOR;
        }

        return switch (result.severity()) {
            case ERROR -> ERROR_BG_COLOR;
            case WARNING -> WARNING_BG_COLOR;
            case INFO -> VALID_BG_COLOR;
        };
    }

    /**
     * Creates an icon for the given validation severity.
     *
     * @param severity the validation severity
     * @return a FontIcon for the severity
     */
    public FontIcon createValidationIcon(ValidationSeverity severity) {
        FontIcon icon;
        Color color;

        switch (severity) {
            case ERROR -> {
                icon = new FontIcon(BootstrapIcons.X_CIRCLE_FILL);
                color = ERROR_COLOR;
            }
            case WARNING -> {
                icon = new FontIcon(BootstrapIcons.EXCLAMATION_TRIANGLE_FILL);
                color = WARNING_COLOR;
            }
            default -> {
                icon = new FontIcon(BootstrapIcons.CHECK_CIRCLE_FILL);
                color = VALID_COLOR;
            }
        }

        icon.setIconColor(color);
        icon.setIconSize(14);
        return icon;
    }

    /**
     * Creates a validation badge (icon + text) for display.
     *
     * @param result the validation result
     * @return an HBox containing the badge
     */
    public HBox createValidationBadge(ValidationResult result) {
        HBox badge = new HBox(4);
        badge.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = createValidationIcon(result != null ? result.severity() : ValidationSeverity.INFO);
        badge.getChildren().add(icon);

        if (result != null && result.errorMessage() != null && !result.errorMessage().isEmpty()) {
            Label label = new Label(truncateMessage(result.errorMessage(), 50));
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: " + toHex(getBorderColor(result)) + ";");
            badge.getChildren().add(label);

            // Full message in tooltip
            Tooltip tooltip = new Tooltip(result.errorMessage());
            tooltip.setShowDelay(Duration.millis(200));
            Tooltip.install(badge, tooltip);
        }

        return badge;
    }

    /**
     * Creates a validation indicator circle.
     *
     * @param result the validation result
     * @return a colored circle indicator
     */
    public Circle createValidationIndicator(ValidationResult result) {
        Circle indicator = new Circle(5);
        indicator.setFill(getBorderColor(result));
        indicator.setStroke(Color.TRANSPARENT);

        if (result != null && result.errorMessage() != null && !result.errorMessage().isEmpty()) {
            Tooltip tooltip = new Tooltip(result.errorMessage());
            tooltip.setShowDelay(Duration.millis(200));
            Tooltip.install(indicator, tooltip);
        }

        return indicator;
    }

    /**
     * Creates a highlight overlay for a region.
     *
     * @param bounds the bounds to highlight
     * @param result the validation result
     * @return a Rectangle overlay
     */
    public Rectangle createHighlightOverlay(Bounds bounds, ValidationResult result) {
        Rectangle overlay = new Rectangle(
                bounds.getMinX(), bounds.getMinY(),
                bounds.getWidth(), bounds.getHeight()
        );

        Color bgColor = getBackgroundColor(result);
        overlay.setFill(Color.color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 0.3));
        overlay.setStroke(getBorderColor(result));
        overlay.setStrokeWidth(2);
        overlay.setMouseTransparent(true);

        return overlay;
    }

    /**
     * Adds a validation overlay to a pane.
     *
     * @param pane   the pane to add the overlay to
     * @param nodeId identifier for the overlay (for caching)
     * @param bounds the bounds to highlight
     * @param result the validation result
     */
    public void addValidationOverlay(Pane pane, String nodeId, Bounds bounds, ValidationResult result) {
        // Remove existing overlay for this node
        removeValidationOverlay(pane, nodeId);

        if (result == null || result.isValid() && result.severity() == ValidationSeverity.INFO) {
            return; // Don't show overlay for valid results
        }

        Rectangle overlay = createHighlightOverlay(bounds, result);
        overlayCache.put(nodeId, overlay);
        pane.getChildren().add(overlay);
    }

    /**
     * Removes a validation overlay from a pane.
     *
     * @param pane   the pane to remove the overlay from
     * @param nodeId identifier for the overlay
     */
    public void removeValidationOverlay(Pane pane, String nodeId) {
        Node existing = overlayCache.remove(nodeId);
        if (existing != null) {
            pane.getChildren().remove(existing);
        }
    }

    /**
     * Clears all validation overlays from a pane.
     *
     * @param pane the pane to clear overlays from
     */
    public void clearAllOverlays(Pane pane) {
        for (Node overlay : overlayCache.values()) {
            pane.getChildren().remove(overlay);
        }
        overlayCache.clear();
    }

    /**
     * Creates a tooltip style for the given severity.
     *
     * @param severity the validation severity
     * @return CSS style string for the tooltip
     */
    private String getTooltipStyle(ValidationSeverity severity) {
        String bgColor = switch (severity) {
            case ERROR -> "#dc3545";
            case WARNING -> "#ffc107";
            case INFO -> "#17a2b8";
        };
        String textColor = severity == ValidationSeverity.WARNING ? "#212529" : "#ffffff";

        return "-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + "; " +
                "-fx-font-size: 12px; -fx-padding: 5 10;";
    }

    /**
     * Truncates a message to a maximum length.
     *
     * @param message   the message to truncate
     * @param maxLength maximum length
     * @return truncated message
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Converts a Color to hex string.
     *
     * @param color the color to convert
     * @return hex string (e.g., "#dc3545")
     */
    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    // ==================== Static helper methods ====================

    /**
     * Creates a simple validation border style.
     *
     * @param isValid    whether the value is valid
     * @param hasWarning whether there's a warning
     * @return CSS border style
     */
    public static String createBorderStyle(boolean isValid, boolean hasWarning) {
        if (!isValid) {
            return "-fx-border-color: " + toHexStatic(ERROR_COLOR) + "; -fx-border-width: 2px;";
        } else if (hasWarning) {
            return "-fx-border-color: " + toHexStatic(WARNING_COLOR) + "; -fx-border-width: 2px;";
        } else {
            return "-fx-border-color: " + toHexStatic(VALID_COLOR) + "; -fx-border-width: 1px;";
        }
    }

    /**
     * Creates a compact validation indicator (small colored dot).
     *
     * @param result the validation result
     * @return a small Circle indicator
     */
    public static Circle createCompactIndicator(ValidationResult result) {
        Circle dot = new Circle(3);

        if (result == null || (result.isValid() && result.severity() == ValidationSeverity.INFO)) {
            dot.setFill(VALID_COLOR);
        } else if (!result.isValid()) {
            dot.setFill(ERROR_COLOR);
        } else if (result.severity() == ValidationSeverity.WARNING) {
            dot.setFill(WARNING_COLOR);
        } else {
            dot.setFill(INFO_COLOR);
        }

        if (result != null && result.errorMessage() != null) {
            Tooltip.install(dot, new Tooltip(result.errorMessage()));
        }

        return dot;
    }

    private static String toHexStatic(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
