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

/**
 * Utility class for XML Canvas layout calculations.
 *
 * <p>Provides layout-related calculations for canvas-based XML visualization,
 * including size calculations, positioning, and scroll management.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasLayoutHelper {

    private XmlCanvasLayoutHelper() {
        // Utility class - no instantiation
    }

    /**
     * Calculates the total height needed to display all elements.
     *
     * <p>This is used for vertical scroll bar sizing.</p>
     *
     * @param elementCount number of elements to display
     * @param rowHeight height of each row
     * @param spacing spacing between elements
     * @return total height needed
     */
    public static double calculateTotalHeight(int elementCount, double rowHeight, double spacing) {
        if (elementCount <= 0) {
            return 0;
        }
        return (elementCount * rowHeight) + ((elementCount - 1) * spacing);
    }

    /**
     * Calculates the total width needed to display grid content.
     *
     * <p>This is used for horizontal scroll bar sizing.</p>
     *
     * @param contentWidth width of the main content
     * @param indentLevel current indentation level
     * @param indentWidth width per indentation level
     * @return total width needed
     */
    public static double calculateTotalWidth(double contentWidth, int indentLevel, double indentWidth) {
        return contentWidth + (indentLevel * indentWidth);
    }

    /**
     * Calculates the minimum grid width.
     *
     * @param minimumWidth the minimum width constant
     * @param contentWidth the desired content width
     * @return the actual grid width to use
     */
    public static double calculateGridWidth(double minimumWidth, double contentWidth) {
        return Math.max(minimumWidth, contentWidth);
    }

    /**
     * Calculates visible range based on scroll position and viewport height.
     *
     * @param scrollOffset current scroll offset
     * @param viewportHeight height of visible viewport
     * @param itemHeight height of each item
     * @return array with [startIndex, endIndex]
     */
    public static int[] calculateVisibleRange(double scrollOffset, double viewportHeight, double itemHeight) {
        if (itemHeight <= 0) {
            return new int[]{0, 0};
        }

        int startIndex = Math.max(0, (int) Math.floor(scrollOffset / itemHeight));
        int endIndex = Math.min(Integer.MAX_VALUE, (int) Math.ceil((scrollOffset + viewportHeight) / itemHeight));

        return new int[]{startIndex, endIndex};
    }

    /**
     * Calculates scroll bar position and size based on content and viewport.
     *
     * @param scrollOffset current scroll offset
     * @param viewportSize size of visible viewport
     * @param totalContentSize total size of all content
     * @return scroll position (0.0 to 1.0)
     */
    public static double calculateScrollPosition(double scrollOffset, double viewportSize, double totalContentSize) {
        if (totalContentSize <= viewportSize || totalContentSize <= 0) {
            return 0.0;
        }

        double maxScroll = totalContentSize - viewportSize;
        return Math.min(1.0, scrollOffset / maxScroll);
    }

    /**
     * Constrains scroll offset to valid range.
     *
     * @param offset proposed scroll offset
     * @param viewportSize size of visible viewport
     * @param totalContentSize total size of all content
     * @return constrained scroll offset
     */
    public static double constrainScrollOffset(double offset, double viewportSize, double totalContentSize) {
        double maxScroll = Math.max(0, totalContentSize - viewportSize);
        return Math.max(0, Math.min(offset, maxScroll));
    }

    /**
     * Calculates the Y position of an item based on index and item height.
     *
     * @param itemIndex the item index
     * @param itemHeight height of each item
     * @return y position of the item
     */
    public static double calculateItemPosition(int itemIndex, double itemHeight) {
        return itemIndex * itemHeight;
    }

    /**
     * Calculates indentation based on nesting level.
     *
     * @param nestingLevel the nesting level (0 = root)
     * @param indentPerLevel indentation width per level
     * @return total indentation amount
     */
    public static double calculateIndentation(int nestingLevel, double indentPerLevel) {
        return Math.max(0, nestingLevel * indentPerLevel);
    }

    /**
     * Calculates available width for content after accounting for margins and padding.
     *
     * @param totalWidth total available width
     * @param leftMargin left margin/padding
     * @param rightMargin right margin/padding
     * @param scrollBarWidth width of scroll bar (if visible)
     * @return available width for content
     */
    public static double calculateAvailableWidth(double totalWidth, double leftMargin, double rightMargin, double scrollBarWidth) {
        return totalWidth - leftMargin - rightMargin - scrollBarWidth;
    }

    /**
     * Calculates if content is visible within viewport.
     *
     * @param contentStart start position of content
     * @param contentEnd end position of content
     * @param viewportStart start position of viewport
     * @param viewportEnd end position of viewport
     * @return true if content intersects viewport
     */
    public static boolean isContentVisible(double contentStart, double contentEnd, double viewportStart, double viewportEnd) {
        return contentStart < viewportEnd && contentEnd > viewportStart;
    }

    /**
     * Calculates center position of an element.
     *
     * @param x x position
     * @param width width of element
     * @return x position of center
     */
    public static double calculateCenter(double x, double width) {
        return x + (width / 2.0);
    }
}
