/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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

package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimap component that shows a scaled-down overview of the entire document
 * with syntax highlighting and a viewport indicator.
 */
public class MinimapView extends StackPane {

    private static final Logger logger = LogManager.getLogger(MinimapView.class);

    private static final double MINIMAP_WIDTH = 120;
    private static final double LINE_HEIGHT = 1.2;
    private static final double CHAR_WIDTH = 0.6;
    private static final int MAX_CHARS_PER_LINE = 200;

    private final Canvas canvas;
    private final Canvas viewportCanvas;
    private final CodeArea sourceCodeArea;
    private final VirtualizedScrollPane<CodeArea> sourceScrollPane;
    private final Map<Integer, String> errorLines;

    private final BooleanProperty visible = new SimpleBooleanProperty(true);

    // XML syntax highlighting patterns
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->");
    private static final Pattern XML_ATTRIBUTE_PATTERN = Pattern.compile("\\w+\\s*=\\s*\"[^\"]*\"");
    private static final Pattern XML_TEXT_PATTERN = Pattern.compile(">[^<]+<");

    public MinimapView(CodeArea codeArea, VirtualizedScrollPane<CodeArea> scrollPane, Map<Integer, String> errors) {
        this.sourceCodeArea = codeArea;
        this.sourceScrollPane = scrollPane;
        this.errorLines = errors;

        // Create canvases
        canvas = new Canvas(MINIMAP_WIDTH, 400);
        viewportCanvas = new Canvas(MINIMAP_WIDTH, 400);

        // Set up styling
        this.setMaxWidth(MINIMAP_WIDTH);
        this.setMinWidth(MINIMAP_WIDTH);
        this.setPrefWidth(MINIMAP_WIDTH);
        this.getStyleClass().add("minimap-container");

        // Add canvases to stack
        this.getChildren().addAll(canvas, viewportCanvas);

        // Set up event handlers
        setupEventHandlers();

        // Bind visibility
        this.visibleProperty().bind(visible);
        this.managedProperty().bind(visible);

        // Don't render initially - wait until visible
    }

    /**
     * Sets up mouse event handlers for navigation.
     */
    private void setupEventHandlers() {
        this.setOnMouseClicked(this::handleMinimapClick);

        // Update minimap when source text changes (only if visible)
        if (sourceCodeArea != null) {
            sourceCodeArea.textProperty().addListener((obs, oldText, newText) -> {
                if (visible.get()) {
                    CompletableFuture.runAsync(this::renderMinimap);
                }
            });

            // Update viewport indicator when scrolling (only if visible)
            if (sourceScrollPane != null) {
                // VirtualizedScrollPane doesn't have vvalueProperty, use estimatedScrollY
                sourceScrollPane.estimatedScrollYProperty().addListener((obs, oldVal, newVal) -> {
                    if (visible.get()) {
                        Platform.runLater(this::renderViewportIndicator);
                    }
                });
            }
        }

        // Listen for visibility changes to trigger initial render
        visible.addListener((obs, wasVisible, isVisible) -> {
            if (isVisible && !wasVisible) {
                // First time becoming visible - render everything
                CompletableFuture.runAsync(this::renderMinimap);
            }
        });
    }

    /**
     * Handles click events on the minimap for navigation.
     */
    private void handleMinimapClick(MouseEvent event) {
        if (sourceCodeArea == null || sourceScrollPane == null) return;

        double clickY = event.getY();
        double totalHeight = canvas.getHeight();
        double ratio = clickY / totalHeight;

        // Calculate target line
        int totalLines = sourceCodeArea.getParagraphs().size();
        int targetLine = (int) (ratio * totalLines);
        final int finalTargetLine = Math.max(0, Math.min(targetLine, totalLines - 1));

        // Navigate to target line
        Platform.runLater(() -> {
            sourceCodeArea.moveTo(finalTargetLine, 0);
            sourceCodeArea.showParagraphAtTop(finalTargetLine);
            sourceCodeArea.requestFocus();
        });

        logger.debug("Minimap click: navigated to line {}", finalTargetLine + 1);
    }

    /**
     * Renders the complete minimap with syntax highlighting.
     */
    private void renderMinimap() {
        if (sourceCodeArea == null) return;

        Platform.runLater(() -> {
            try {
                String text = sourceCodeArea.getText();
                if (text == null || text.isEmpty()) {
                    clearCanvas();
                    return;
                }

                GraphicsContext gc = canvas.getGraphicsContext2D();
                clearCanvas();

                // Set small font for minimap
                gc.setFont(Font.font("Monaco", FontWeight.NORMAL, FontPosture.REGULAR, 2));

                String[] lines = text.split("\n", -1);
                double y = 0;

                // Calculate canvas height based on content
                double contentHeight = lines.length * LINE_HEIGHT;
                if (contentHeight != canvas.getHeight()) {
                    canvas.setHeight(Math.max(400, contentHeight));
                    viewportCanvas.setHeight(canvas.getHeight());
                }

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    int lineNumber = i + 1;

                    // Check if this line has errors
                    boolean hasError = errorLines.containsKey(lineNumber);

                    if (hasError) {
                        // Draw error background
                        gc.setFill(Color.rgb(255, 200, 200, 0.3));
                        gc.fillRect(0, y, MINIMAP_WIDTH, LINE_HEIGHT);
                    }

                    // Render line content with basic syntax coloring
                    renderLineContent(gc, line, y, hasError);

                    y += LINE_HEIGHT;
                }

                // Render viewport indicator
                renderViewportIndicator();

            } catch (Exception e) {
                logger.error("Error rendering minimap: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Renders a single line with basic syntax highlighting.
     */
    private void renderLineContent(GraphicsContext gc, String line, double y, boolean hasError) {
        if (line.length() > MAX_CHARS_PER_LINE) {
            line = line.substring(0, MAX_CHARS_PER_LINE) + "...";
        }

        double x = 2;
        int pos = 0;

        // Find and color XML tags
        Matcher tagMatcher = XML_TAG_PATTERN.matcher(line);
        while (tagMatcher.find() && pos < line.length()) {
            // Draw text before tag
            if (tagMatcher.start() > pos) {
                gc.setFill(hasError ? Color.DARKRED : Color.DARKGRAY);
                String beforeTag = line.substring(pos, tagMatcher.start());
                gc.fillText(beforeTag, x, y + LINE_HEIGHT * 0.8);
                x += beforeTag.length() * CHAR_WIDTH;
            }

            // Draw XML tag in blue
            gc.setFill(hasError ? Color.rgb(100, 0, 0) : Color.BLUE);
            String tag = tagMatcher.group();
            gc.fillText(tag, x, y + LINE_HEIGHT * 0.8);
            x += tag.length() * CHAR_WIDTH;

            pos = tagMatcher.end();
        }

        // Draw remaining text
        if (pos < line.length()) {
            gc.setFill(hasError ? Color.DARKRED : Color.DARKGRAY);
            String remaining = line.substring(pos);
            gc.fillText(remaining, x, y + LINE_HEIGHT * 0.8);
        }
    }

    /**
     * Renders the viewport indicator showing the currently visible area.
     */
    private void renderViewportIndicator() {
        if (sourceScrollPane == null || sourceCodeArea == null) return;

        GraphicsContext gc = viewportCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, viewportCanvas.getWidth(), viewportCanvas.getHeight());

        try {
            // Calculate visible area using VirtualizedScrollPane methods
            double scrollY = sourceScrollPane.getEstimatedScrollY();
            double viewportHeight = sourceScrollPane.getHeight();
            double contentHeight = sourceCodeArea.getTotalHeightEstimate();

            if (contentHeight <= viewportHeight || contentHeight <= 0) {
                // All content is visible or invalid height
                return;
            }

            // Calculate viewport indicator position and size
            double minimapHeight = canvas.getHeight();
            double scrollRatio = scrollY / Math.max(1, contentHeight - viewportHeight);
            double indicatorHeight = (viewportHeight / contentHeight) * minimapHeight;
            double indicatorY = scrollRatio * (minimapHeight - indicatorHeight);

            // Ensure indicator stays within bounds
            indicatorY = Math.max(0, Math.min(indicatorY, minimapHeight - indicatorHeight));
            indicatorHeight = Math.max(10, Math.min(indicatorHeight, minimapHeight));

            // Draw semi-transparent viewport indicator
            gc.setFill(Color.rgb(100, 150, 255, 0.3));
            gc.fillRect(0, indicatorY, MINIMAP_WIDTH, indicatorHeight);

            // Draw viewport border
            gc.setStroke(Color.rgb(100, 150, 255, 0.8));
            gc.setLineWidth(1);
            gc.strokeRect(0, indicatorY, MINIMAP_WIDTH, indicatorHeight);
        } catch (Exception e) {
            logger.debug("Error rendering viewport indicator: {}", e.getMessage());
        }
    }

    /**
     * Clears the canvas.
     */
    private void clearCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    /**
     * Updates the minimap when errors change.
     */
    public void updateErrors() {
        if (visible.get()) {
            renderMinimap();
        }
    }

    /**
     * Gets the minimap visibility property for binding.
     */
    public BooleanProperty minimapVisibleProperty() {
        return visible;
    }

    /**
     * Sets minimap visibility.
     */
    public void setMinimapVisible(boolean visible) {
        this.visible.set(visible);
    }

    /**
     * Gets minimap visibility.
     */
    public boolean isMinimapVisible() {
        return visible.get();
    }
}