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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * Visual indicator for XSD cardinality (minOccurs/maxOccurs).
 *
 * <p>Displays cardinality information in various formats:</p>
 * <ul>
 *   <li>Badge: [0..1], [1], [1..*], etc.</li>
 *   <li>Required marker: Red asterisk (*) for mandatory elements</li>
 *   <li>Compact indicator: Small colored dot</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class CardinalityIndicator {

    // Colour roles: style classes fxt-cardinality-<role> (token colours in unified-shell.css)
    private static final String REQUIRED_COLOR = "required";
    private static final String OPTIONAL_COLOR = "optional";
    private static final String MULTI_COLOR = "multi";
    private static final String UNBOUNDED_COLOR = "unbounded";

    private CardinalityIndicator() {
        // Utility class
    }

    /**
     * Cardinality type enumeration.
     */
    public enum CardinalityType {
        /** [1] - Required, exactly one. */
        EXACTLY_ONE,
        /** [0..1] - Optional, zero or one. */
        OPTIONAL,
        /** [1..*] - Required, one or more. */
        REQUIRED_MULTI,
        /** [0..*] - Optional, any number. */
        OPTIONAL_MULTI,
        /** [n..m] - Bounded range. */
        BOUNDED,
        /** Unknown or unspecified. */
        UNKNOWN
    }

    /**
     * Creates a cardinality badge as an HBox.
     *
     * @param minOccurs minimum occurrences (as string, e.g., "0", "1")
     * @param maxOccurs maximum occurrences (as string, e.g., "1", "unbounded")
     * @return an HBox containing the cardinality badge
     */
    public static HBox createBadge(String minOccurs, String maxOccurs) {
        CardinalityInfo info = parseCardinality(minOccurs, maxOccurs);

        HBox badge = new HBox(2);
        badge.setAlignment(Pos.CENTER);
        badge.setPadding(new Insets(1, 4, 1, 4));

        // Badge label
        Label label = new Label(info.displayText());
        label.getStyleClass().add("fxt-text-2xs");

        badge.getChildren().add(label);
        badge.getStyleClass().addAll("fxt-cardinality-badge", roleClass(info.color()));

        // Tooltip
        Tooltip tooltip = new Tooltip(info.tooltip());
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(badge, tooltip);

        return badge;
    }

    /**
     * Creates a required indicator (red asterisk) if element is required.
     *
     * @param minOccurs minimum occurrences
     * @return a Label with asterisk, or null if not required
     */
    public static Label createRequiredIndicator(String minOccurs) {
        int min = parseMinOccurs(minOccurs);
        if (min < 1) {
            return null;
        }

        Label asterisk = new Label("*");
        asterisk.getStyleClass().addAll("fxt-text-lg", "fxt-cardinality-asterisk");

        Tooltip tooltip = new Tooltip(I18n.t("CardinalityIndicator.required-element"));
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(asterisk, tooltip);

        return asterisk;
    }

    /**
     * Creates a compact cardinality indicator (small icon).
     *
     * @param minOccurs minimum occurrences
     * @param maxOccurs maximum occurrences
     * @return a IconifyIcon representing the cardinality
     */
    public static IconifyIcon createCompactIndicator(String minOccurs, String maxOccurs) {
        CardinalityInfo info = parseCardinality(minOccurs, maxOccurs);

        IconifyIcon icon;
        switch (info.type()) {
            case EXACTLY_ONE -> {
                icon = new IconifyIcon("bi-record-fill");
                icon.getStyleClass().add(roleClass(REQUIRED_COLOR));
            }
            case OPTIONAL -> {
                icon = new IconifyIcon("bi-record");
                icon.getStyleClass().add(roleClass(OPTIONAL_COLOR));
            }
            case REQUIRED_MULTI -> {
                icon = new IconifyIcon("bi-plus-circle-fill");
                icon.getStyleClass().add(roleClass(MULTI_COLOR));
            }
            case OPTIONAL_MULTI -> {
                icon = new IconifyIcon("bi-plus-circle");
                icon.getStyleClass().add(roleClass(UNBOUNDED_COLOR));
            }
            default -> {
                icon = new IconifyIcon("bi-question-circle");
                icon.getStyleClass().add(roleClass(OPTIONAL_COLOR));
            }
        }

        icon.setIconSize(10);

        Tooltip tooltip = new Tooltip(info.tooltip());
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(icon, tooltip);

        return icon;
    }

    /**
     * Creates a colored dot indicator.
     *
     * @param minOccurs minimum occurrences
     * @param maxOccurs maximum occurrences
     * @return a Circle representing the cardinality
     */
    public static Circle createDotIndicator(String minOccurs, String maxOccurs) {
        CardinalityInfo info = parseCardinality(minOccurs, maxOccurs);

        Circle dot = new Circle(4);
        dot.getStyleClass().addAll("fxt-cardinality-dot", roleClass(info.color()));
        dot.setStrokeWidth(1);

        Tooltip tooltip = new Tooltip(info.tooltip());
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(dot, tooltip);

        return dot;
    }

    /**
     * Creates an inline cardinality text.
     *
     * @param minOccurs minimum occurrences
     * @param maxOccurs maximum occurrences
     * @return formatted cardinality string (e.g., "[0..1]")
     */
    public static String createInlineText(String minOccurs, String maxOccurs) {
        CardinalityInfo info = parseCardinality(minOccurs, maxOccurs);
        return info.displayText();
    }

    /**
     * Determines the cardinality type.
     *
     * @param minOccurs minimum occurrences
     * @param maxOccurs maximum occurrences
     * @return the CardinalityType
     */
    public static CardinalityType getCardinalityType(String minOccurs, String maxOccurs) {
        return parseCardinality(minOccurs, maxOccurs).type();
    }

    /**
     * Checks if the element is required (minOccurs >= 1).
     *
     * @param minOccurs minimum occurrences
     * @return true if required
     */
    public static boolean isRequired(String minOccurs) {
        return parseMinOccurs(minOccurs) >= 1;
    }

    /**
     * Checks if the element allows multiple occurrences.
     *
     * @param maxOccurs maximum occurrences
     * @return true if multiple allowed
     */
    public static boolean allowsMultiple(String maxOccurs) {
        if (maxOccurs == null) {
            return false;
        }
        if ("unbounded".equalsIgnoreCase(maxOccurs)) {
            return true;
        }
        try {
            return Integer.parseInt(maxOccurs) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== Private helpers ====================

    private record CardinalityInfo(
            CardinalityType type,
            String displayText,
            String tooltip,
            String color
    ) {
    }

    private static CardinalityInfo parseCardinality(String minOccursStr, String maxOccursStr) {
        int minOccurs = parseMinOccurs(minOccursStr);
        int maxOccurs = parseMaxOccurs(maxOccursStr);

        // Determine type and formatting
        if (minOccurs == 1 && maxOccurs == 1) {
            return new CardinalityInfo(
                    CardinalityType.EXACTLY_ONE,
                    "[1]",
                    "Required - exactly one occurrence",
                    REQUIRED_COLOR
            );
        } else if (minOccurs == 0 && maxOccurs == 1) {
            return new CardinalityInfo(
                    CardinalityType.OPTIONAL,
                    "[0..1]",
                    "Optional - zero or one occurrence",
                    OPTIONAL_COLOR
            );
        } else if (minOccurs == 0 && maxOccurs == -1) {
            return new CardinalityInfo(
                    CardinalityType.OPTIONAL_MULTI,
                    "[0..*]",
                    "Optional - any number of occurrences",
                    UNBOUNDED_COLOR
            );
        } else if (minOccurs == 1 && maxOccurs == -1) {
            return new CardinalityInfo(
                    CardinalityType.REQUIRED_MULTI,
                    "[1..*]",
                    "Required - one or more occurrences",
                    MULTI_COLOR
            );
        } else if (maxOccurs == -1) {
            return new CardinalityInfo(
                    CardinalityType.OPTIONAL_MULTI,
                    "[" + minOccurs + "..*]",
                    "At least " + minOccurs + " occurrences",
                    UNBOUNDED_COLOR
            );
        } else if (minOccurs == maxOccurs) {
            return new CardinalityInfo(
                    CardinalityType.BOUNDED,
                    "[" + minOccurs + "]",
                    "Exactly " + minOccurs + " occurrences",
                    minOccurs > 0 ? REQUIRED_COLOR : OPTIONAL_COLOR
            );
        } else {
            return new CardinalityInfo(
                    CardinalityType.BOUNDED,
                    "[" + minOccurs + ".." + maxOccurs + "]",
                    "Between " + minOccurs + " and " + maxOccurs + " occurrences",
                    minOccurs > 0 ? MULTI_COLOR : OPTIONAL_COLOR
            );
        }
    }

    private static int parseMinOccurs(String minOccursStr) {
        if (minOccursStr == null || minOccursStr.isEmpty()) {
            return 1; // Default
        }
        try {
            return Integer.parseInt(minOccursStr);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int parseMaxOccurs(String maxOccursStr) {
        if (maxOccursStr == null || maxOccursStr.isEmpty()) {
            return 1; // Default
        }
        if ("unbounded".equalsIgnoreCase(maxOccursStr)) {
            return -1;
        }
        try {
            return Integer.parseInt(maxOccursStr);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** @return the style class of a colour role */
    private static String roleClass(String role) {
        return "fxt-cardinality-" + role;
    }
}
