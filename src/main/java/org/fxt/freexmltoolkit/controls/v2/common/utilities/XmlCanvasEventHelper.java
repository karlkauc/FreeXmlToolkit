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

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Utility class for XML Canvas event handling.
 *
 * <p>Provides utilities for processing keyboard and mouse events in canvas-based editing.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasEventHelper {

    private XmlCanvasEventHelper() {
        // Utility class - no instantiation
    }

    /**
     * Determines if a key event represents an editing action.
     *
     * @param event the key event
     * @return true if the key event is for editing
     */
    public static boolean isEditingKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return code == KeyCode.ENTER || code == KeyCode.F2 || code == KeyCode.SPACE;
    }

    /**
     * Determines if a key event represents navigation.
     *
     * @param event the key event
     * @return true if the key event is for navigation
     */
    public static boolean isNavigationKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return code == KeyCode.UP || code == KeyCode.DOWN ||
               code == KeyCode.LEFT || code == KeyCode.RIGHT ||
               code == KeyCode.HOME || code == KeyCode.END;
    }

    /**
     * Determines if an event is an expand/collapse action.
     *
     * @param event the key event
     * @return true if the event is expand/collapse
     */
    public static boolean isExpandCollapseKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return (code == KeyCode.LEFT && event.isControlDown()) ||
               (code == KeyCode.RIGHT && event.isControlDown());
    }

    /**
     * Determines if an event is for parent navigation.
     *
     * @param event the key event
     * @return true if the event is for going to parent
     */
    public static boolean isParentNavigationKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return code == KeyCode.UP && event.isControlDown();
    }

    /**
     * Determines if an event is for first child navigation.
     *
     * @param event the key event
     * @return true if the event is for going to first child
     */
    public static boolean isFirstChildKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return code == KeyCode.DOWN && event.isControlDown();
    }

    /**
     * Determines if an event is for cancel/escape.
     *
     * @param event the key event
     * @return true if the event is escape
     */
    public static boolean isEscapeKey(KeyEvent event) {
        return event != null && event.getCode() == KeyCode.ESCAPE;
    }

    /**
     * Determines if an event is for delete.
     *
     * @param event the key event
     * @return true if the event is delete or backspace
     */
    public static boolean isDeleteKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return code == KeyCode.DELETE || code == KeyCode.BACK_SPACE;
    }

    /**
     * Determines if an event should trigger context menu.
     *
     * @param event the key event
     * @return true if context menu should appear
     */
    public static boolean isContextMenuKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        KeyCode code = event.getCode();
        return code == KeyCode.CONTEXT_MENU || (code == KeyCode.F10 && event.isShiftDown());
    }

    /**
     * Determines if a key event represents up arrow navigation.
     *
     * @param event the key event
     * @return true if the event is up arrow
     */
    public static boolean isUpArrow(KeyEvent event) {
        return event != null && event.getCode() == KeyCode.UP;
    }

    /**
     * Determines if a key event represents down arrow navigation.
     *
     * @param event the key event
     * @return true if the event is down arrow
     */
    public static boolean isDownArrow(KeyEvent event) {
        return event != null && event.getCode() == KeyCode.DOWN;
    }

    /**
     * Determines if a key event represents left arrow navigation.
     *
     * @param event the key event
     * @return true if the event is left arrow
     */
    public static boolean isLeftArrow(KeyEvent event) {
        return event != null && event.getCode() == KeyCode.LEFT;
    }

    /**
     * Determines if a key event represents right arrow navigation.
     *
     * @param event the key event
     * @return true if the event is right arrow
     */
    public static boolean isRightArrow(KeyEvent event) {
        return event != null && event.getCode() == KeyCode.RIGHT;
    }

    /**
     * Determines if a mouse click is a double-click based on click count.
     *
     * @param clickCount the click count from mouse event
     * @return true if it's a double-click
     */
    public static boolean isDoubleClick(int clickCount) {
        return clickCount >= 2;
    }

    /**
     * Determines if a point is within a rectangular region.
     *
     * @param px point x coordinate
     * @param py point y coordinate
     * @param rx rectangle x coordinate
     * @param ry rectangle y coordinate
     * @param rw rectangle width
     * @param rh rectangle height
     * @return true if point is in rectangle
     */
    public static boolean isPointInRegion(double px, double py, double rx, double ry, double rw, double rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    /**
     * Calculates the offset within a region for a point.
     *
     * @param px point x coordinate
     * @param rx region x coordinate
     * @return offset from region start
     */
    public static double calculateOffsetInRegion(double px, double rx) {
        return Math.max(0, px - rx);
    }

    /**
     * Determines if a double-click should start editing.
     *
     * @param clickCount click count from mouse event
     * @return true if editing should start
     */
    public static boolean shouldStartEditing(int clickCount) {
        return isDoubleClick(clickCount);
    }

    /**
     * Determines if an event should be consumed (prevent default behavior).
     *
     * @param event the key event
     * @return true if event should be consumed
     */
    public static boolean shouldConsumeEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }

        return isNavigationKey(event) || isEditingKey(event) ||
               isExpandCollapseKey(event) || isEscapeKey(event);
    }
}
