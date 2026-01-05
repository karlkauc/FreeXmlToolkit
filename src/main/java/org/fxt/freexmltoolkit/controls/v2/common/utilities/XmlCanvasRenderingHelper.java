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

package org.fxt.freexmltoolkit.controls.v2.common.utilities;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Utility class for XML Canvas rendering operations.
 *
 * <p>Provides constants, colors, and helper methods for canvas-based XML visualization.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasRenderingHelper {

    // ==================== Layout Constants ====================

    public static final double HEADER_HEIGHT = 28;
    public static final double ROW_HEIGHT = 24;
    public static final double CHILDREN_HEADER_HEIGHT = 0;
    public static final double INDENT = 20;
    public static final double CHILD_SPACING = 8;
    public static final double GRID_PADDING = 8;
    public static final double MIN_GRID_WIDTH = 200;
    public static final double ATTR_NAME_WIDTH = 120;
    public static final double SCROLLBAR_WIDTH = 14;

    // ==================== Color Constants ====================

    public static final Color COLOR_BACKGROUND = Color.web("#ffffff");
    public static final Color COLOR_GRID_BORDER = Color.web("#e0e0e0");
    public static final Color COLOR_HEADER_BG = Color.web("#f5f5f5");
    public static final Color COLOR_HEADER_BORDER = Color.web("#d0d0d0");
    public static final Color COLOR_ROW_BORDER = Color.web("#f0f0f0");
    public static final Color COLOR_TEXT_DEFAULT = Color.web("#333333");
    public static final Color COLOR_TEXT_HOVER = Color.web("#000000");
    public static final Color COLOR_SELECTED_BG = Color.web("#e3f2fd");
    public static final Color COLOR_SELECTED_BORDER = Color.web("#2196f3");
    public static final Color COLOR_HOVER_BG = Color.web("#f5f5f5");
    public static final Color COLOR_HOVER_BORDER = Color.web("#999999");
    public static final Color COLOR_ATTRIBUTE_NAME = Color.web("#e81416");
    public static final Color COLOR_ELEMENT_TAG = Color.web("#0066cc");
    public static final Color COLOR_TEXT_CONTENT = Color.web("#008000");
    public static final Color COLOR_ICON_ELEMENT = Color.web("#0066cc");
    public static final Color COLOR_ICON_ATTRIBUTE = Color.web("#e81416");
    public static final Color COLOR_ICON_TEXT = Color.web("#008000");
    public static final Color COLOR_DISABLED_TEXT = Color.web("#999999");
    public static final Color COLOR_ERROR = Color.web("#ff0000");

    // ==================== Font Constants ====================

    public static final double FONT_SIZE_HEADER = 12;
    public static final double FONT_SIZE_REGULAR = 11;
    public static final double FONT_SIZE_SMALL = 10;

    // Cached font objects to reduce allocations
    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, FONT_SIZE_HEADER);
    private static final Font REGULAR_FONT = Font.font("Monospaced", FontWeight.NORMAL, FONT_SIZE_REGULAR);
    private static final Font SMALL_FONT = Font.font("Monospaced", FontWeight.NORMAL, FONT_SIZE_SMALL);

    private XmlCanvasRenderingHelper() {
        // Utility class - no instantiation
    }

    /**
     * Gets a standard header font (bold).
     * Returns a cached instance to reduce object allocations.
     *
     * @return font for headers
     */
    public static Font getHeaderFont() {
        return HEADER_FONT;
    }

    /**
     * Gets a standard regular font.
     * Returns a cached instance to reduce object allocations.
     *
     * @return font for regular text
     */
    public static Font getRegularFont() {
        return REGULAR_FONT;
    }

    /**
     * Gets a small font for secondary text.
     * Returns a cached instance to reduce object allocations.
     *
     * @return font for small text
     */
    public static Font getSmallFont() {
        return SMALL_FONT;
    }

    /**
     * Estimates text width based on character count and font size.
     *
     * <p>This is a rough estimation. For precise measurement, use TextFlow or Text nodes.</p>
     *
     * @param text the text to measure
     * @param fontSize the font size
     * @return estimated width of the text
     */
    public static double estimateTextWidth(String text, double fontSize) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Monospace font: roughly 0.6 * fontSize per character
        return text.length() * fontSize * 0.6;
    }

    /**
     * Truncates text to fit within a maximum width.
     *
     * @param text the text to truncate
     * @param maxWidth the maximum width available
     * @return truncated text with ellipsis if needed
     */
    public static String truncateText(String text, double maxWidth) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Rough estimation: assume average character width
        double charWidth = 6.5;
        int maxChars = (int) (maxWidth / charWidth);

        if (text.length() <= maxChars) {
            return text;
        }

        if (maxChars <= 3) {
            return "...";
        }

        return text.substring(0, maxChars - 3) + "...";
    }

    /**
     * Draws a rectangle with border.
     *
     * @param gc the graphics context
     * @param x x coordinate
     * @param y y coordinate
     * @param width width
     * @param height height
     * @param fillColor fill color
     * @param borderColor border color
     * @param borderWidth border width
     */
    public static void drawRectangle(GraphicsContext gc, double x, double y, double width, double height,
                                     Color fillColor, Color borderColor, double borderWidth) {
        if (fillColor != null) {
            gc.setFill(fillColor);
            gc.fillRect(x, y, width, height);
        }

        if (borderColor != null && borderWidth > 0) {
            gc.setStroke(borderColor);
            gc.setLineWidth(borderWidth);
            gc.strokeRect(x, y, width, height);
        }
    }

    /**
     * Draws a horizontal line.
     *
     * @param gc the graphics context
     * @param x1 start x
     * @param x2 end x
     * @param y y coordinate
     * @param color line color
     * @param width line width
     */
    public static void drawLine(GraphicsContext gc, double x1, double x2, double y, Color color, double width) {
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.strokeLine(x1, y, x2, y);
    }

    /**
     * Sets text rendering properties on graphics context.
     *
     * @param gc the graphics context
     * @param font the font to use
     * @param fillColor the text color
     * @param alignment the text alignment
     */
    public static void setTextProperties(GraphicsContext gc, Font font, Color fillColor, TextAlignment alignment) {
        gc.setFont(font);
        gc.setFill(fillColor);
        gc.setTextAlign(alignment);
    }

    /**
     * Draws text at specified position.
     *
     * @param gc the graphics context
     * @param text the text to draw
     * @param x x coordinate
     * @param y y coordinate
     * @param font the font to use
     * @param color the text color
     */
    public static void drawText(GraphicsContext gc, String text, double x, double y, Font font, Color color) {
        setTextProperties(gc, font, color, TextAlignment.LEFT);
        gc.fillText(text, x, y);
    }

    /**
     * Draws centered text.
     *
     * @param gc the graphics context
     * @param text the text to draw
     * @param x x coordinate
     * @param y y coordinate
     * @param font the font to use
     * @param color the text color
     */
    public static void drawTextCentered(GraphicsContext gc, String text, double x, double y, Font font, Color color) {
        setTextProperties(gc, font, color, TextAlignment.CENTER);
        gc.fillText(text, x, y);
    }

    /**
     * Clears a rectangular area with background color.
     *
     * @param gc the graphics context
     * @param x x coordinate
     * @param y y coordinate
     * @param width width
     * @param height height
     */
    public static void clearRect(GraphicsContext gc, double x, double y, double width, double height) {
        gc.setFill(COLOR_BACKGROUND);
        gc.fillRect(x, y, width, height);
    }

    /**
     * Checks if a point is within a rectangle.
     *
     * @param px point x
     * @param py point y
     * @param rx rectangle x
     * @param ry rectangle y
     * @param rw rectangle width
     * @param rh rectangle height
     * @return true if point is in rectangle
     */
    public static boolean isPointInRect(double px, double py, double rx, double ry, double rw, double rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }
}
