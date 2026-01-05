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

package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for handling mouse events in XsdGraphView.
 *
 * <p>Provides utilities for detecting and classifying mouse events.</p>
 *
 * @since 2.0
 */
public class XsdGraphViewEventHandler {
    private static final Logger logger = LogManager.getLogger(XsdGraphViewEventHandler.class);

    private static final double DRAG_THRESHOLD = 5.0; // Pixels before drag starts

    /**
     * Checks if a mouse event is a left-click.
     *
     * @param event the mouse event
     * @return true if left-click
     */
    public boolean isLeftClick(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1;
    }

    /**
     * Checks if a mouse event is a double-click.
     *
     * @param event the mouse event
     * @return true if double-click
     */
    public boolean isDoubleClick(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 2;
    }

    /**
     * Checks if a mouse event is a right-click (context menu).
     *
     * @param event the mouse event
     * @return true if right-click
     */
    public boolean isRightClick(MouseEvent event) {
        return event.getButton() == MouseButton.SECONDARY;
    }

    /**
     * Checks if a mouse event has a control/command modifier.
     *
     * @param event the mouse event
     * @return true if Ctrl/Cmd is pressed
     */
    public boolean hasControlModifier(MouseEvent event) {
        return event.isControlDown() || event.isMetaDown();
    }

    /**
     * Checks if a mouse event has a shift modifier.
     *
     * @param event the mouse event
     * @return true if Shift is pressed
     */
    public boolean hasShiftModifier(MouseEvent event) {
        return event.isShiftDown();
    }

    /**
     * Checks if a mouse event has an alt modifier.
     *
     * @param event the mouse event
     * @return true if Alt is pressed
     */
    public boolean hasAltModifier(MouseEvent event) {
        return event.isAltDown();
    }

    /**
     * Calculates the distance moved since a starting point.
     *
     * @param startX the starting X coordinate
     * @param startY the starting Y coordinate
     * @param currentX the current X coordinate
     * @param currentY the current Y coordinate
     * @return the distance moved in pixels
     */
    public double calculateDragDistance(double startX, double startY, double currentX, double currentY) {
        double dx = currentX - startX;
        double dy = currentY - startY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Checks if the drag distance exceeds the threshold.
     *
     * @param startX the starting X coordinate
     * @param startY the starting Y coordinate
     * @param currentX the current X coordinate
     * @param currentY the current Y coordinate
     * @return true if drag threshold exceeded
     */
    public boolean isDragThresholdExceeded(double startX, double startY, double currentX, double currentY) {
        return calculateDragDistance(startX, startY, currentX, currentY) > DRAG_THRESHOLD;
    }

    /**
     * Gets the event button name.
     *
     * @param event the mouse event
     * @return button name (PRIMARY, SECONDARY, MIDDLE, NONE, UNKNOWN)
     */
    public String getButtonName(MouseEvent event) {
        return switch (event.getButton()) {
            case PRIMARY -> "LEFT";
            case SECONDARY -> "RIGHT";
            case MIDDLE -> "MIDDLE";
            case NONE -> "NONE";
            default -> "UNKNOWN";
        };
    }

    /**
     * Logs mouse event information.
     *
     * @param eventType the event type (MOVE, CLICK, PRESS, DRAG, RELEASE)
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param button the button name
     * @param message additional message
     */
    public void logMouseEvent(String eventType, double x, double y, String button, String message) {
        logger.debug("Mouse {} at ({}, {}) [{}]: {}", eventType, (int) x, (int) y, button, message);
    }

    /**
     * Checks if event should trigger expand/collapse.
     *
     * @param event the mouse event
     * @return true if event should expand/collapse
     */
    public boolean shouldToggleExpansion(MouseEvent event) {
        return isLeftClick(event) && !hasControlModifier(event) && !hasShiftModifier(event);
    }

    /**
     * Checks if event should trigger multi-select.
     *
     * @param event the mouse event
     * @return true if event should add to selection
     */
    public boolean shouldMultiSelect(MouseEvent event) {
        return isLeftClick(event) && hasControlModifier(event);
    }

    /**
     * Checks if event should trigger range select.
     *
     * @param event the mouse event
     * @return true if event should select range
     */
    public boolean shouldRangeSelect(MouseEvent event) {
        return isLeftClick(event) && hasShiftModifier(event);
    }
}
