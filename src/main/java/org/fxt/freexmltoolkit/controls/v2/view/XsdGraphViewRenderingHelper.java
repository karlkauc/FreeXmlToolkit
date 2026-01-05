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

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for rendering operations in XsdGraphView.
 *
 * <p>Provides utilities for calculating zoom, canvas sizes, and viewport management.</p>
 *
 * @since 2.0
 */
public class XsdGraphViewRenderingHelper {
    private static final Logger logger = LogManager.getLogger(XsdGraphViewRenderingHelper.class);

    private static final double ZOOM_MIN = 0.1;
    private static final double ZOOM_MAX = 5.0;
    private static final double ZOOM_STEP = 0.1;

    /**
     * Validates and constrains a zoom level.
     *
     * @param zoomLevel the requested zoom level
     * @return constrained zoom level
     */
    public double constrainZoomLevel(double zoomLevel) {
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomLevel));
    }

    /**
     * Calculates new zoom level for zoom in operation.
     *
     * @param currentZoom the current zoom level
     * @return new zoom level after zoom in
     */
    public double calculateZoomIn(double currentZoom) {
        return constrainZoomLevel(currentZoom + ZOOM_STEP);
    }

    /**
     * Calculates new zoom level for zoom out operation.
     *
     * @param currentZoom the current zoom level
     * @return new zoom level after zoom out
     */
    public double calculateZoomOut(double currentZoom) {
        return constrainZoomLevel(currentZoom - ZOOM_STEP);
    }

    /**
     * Gets the minimum zoom level.
     *
     * @return minimum zoom level
     */
    public double getMinZoom() {
        return ZOOM_MIN;
    }

    /**
     * Gets the maximum zoom level.
     *
     * @return maximum zoom level
     */
    public double getMaxZoom() {
        return ZOOM_MAX;
    }

    /**
     * Gets the zoom step increment.
     *
     * @return zoom step value
     */
    public double getZoomStep() {
        return ZOOM_STEP;
    }

    /**
     * Formats zoom level for display.
     *
     * @param zoomLevel the zoom level
     * @return formatted string (e.g., "150%")
     */
    public String formatZoomLevel(double zoomLevel) {
        return String.format("%.0f%%", zoomLevel * 100);
    }

    /**
     * Calculates scaling factor for zoom.
     *
     * @param zoomLevel the zoom level
     * @return scale factor for transformations
     */
    public double getZoomScale(double zoomLevel) {
        return zoomLevel;
    }

    /**
     * Checks if zoom is at default level.
     *
     * @param zoomLevel the zoom level
     * @return true if zoom is at 100%
     */
    public boolean isDefaultZoom(double zoomLevel) {
        return Math.abs(zoomLevel - 1.0) < 0.01;
    }

    /**
     * Calculates canvas padding for content.
     *
     * @param canvasWidth the canvas width
     * @param canvasHeight the canvas height
     * @param contentWidth the content width
     * @param contentHeight the content height
     * @return array [top, bottom, left, right] padding in pixels
     */
    public int[] calculateCanvasPadding(double canvasWidth, double canvasHeight,
                                        double contentWidth, double contentHeight) {
        int padding = 20;
        int topPadding = padding;
        int bottomPadding = (int) (contentHeight < canvasHeight ? canvasHeight - contentHeight + padding : padding);
        int leftPadding = padding;
        int rightPadding = (int) (contentWidth < canvasWidth ? canvasWidth - contentWidth + padding : padding);

        return new int[]{topPadding, bottomPadding, leftPadding, rightPadding};
    }

    /**
     * Clears the canvas with background color.
     *
     * @param gc the graphics context
     * @param width the canvas width
     * @param height the canvas height
     * @param backgroundColor the background color
     */
    public void clearCanvas(GraphicsContext gc, double width, double height, Color backgroundColor) {
        gc.setFill(backgroundColor);
        gc.fillRect(0, 0, width, height);
    }

    /**
     * Applies zoom transform to graphics context.
     *
     * @param gc the graphics context
     * @param zoomLevel the zoom level
     */
    public void applyZoomTransform(GraphicsContext gc, double zoomLevel) {
        gc.scale(zoomLevel, zoomLevel);
    }

    /**
     * Resets graphics context transforms.
     *
     * @param gc the graphics context
     */
    public void resetTransforms(GraphicsContext gc) {
        gc.setTransform(1, 0, 0, 1, 0, 0);
    }

    /**
     * Logs rendering operation.
     *
     * @param operationType the operation type (e.g., "RENDER_TREE", "ZOOM")
     * @param details additional details
     */
    public void logRenderingOperation(String operationType, String details) {
        logger.debug("Rendering operation: {} - {}", operationType, details);
    }

    /**
     * Checks if content fits in viewport.
     *
     * @param contentWidth the content width
     * @param contentHeight the content height
     * @param viewportWidth the viewport width
     * @param viewportHeight the viewport height
     * @return true if content fits
     */
    public boolean contentFitsInViewport(double contentWidth, double contentHeight,
                                        double viewportWidth, double viewportHeight) {
        return contentWidth <= viewportWidth && contentHeight <= viewportHeight;
    }
}
