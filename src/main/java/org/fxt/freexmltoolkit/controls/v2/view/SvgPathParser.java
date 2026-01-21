/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SVG Path Parser and Renderer for JavaFX Canvas.
 * <p>
 * This class provides full SVG path data parsing and rendering capabilities,
 * supporting all standard SVG path commands:
 * <ul>
 *   <li>M/m - moveto</li>
 *   <li>L/l - lineto</li>
 *   <li>H/h - horizontal lineto</li>
 *   <li>V/v - vertical lineto</li>
 *   <li>C/c - cubic Bezier curve</li>
 *   <li>S/s - smooth cubic Bezier curve</li>
 *   <li>Q/q - quadratic Bezier curve</li>
 *   <li>T/t - smooth quadratic Bezier curve</li>
 *   <li>A/a - elliptical arc</li>
 *   <li>Z/z - closepath</li>
 * </ul>
 * <p>
 * The parser handles both absolute (uppercase) and relative (lowercase) commands.
 *
 * @see <a href="https://www.w3.org/TR/SVG/paths.html">SVG Path Specification</a>
 */
public class SvgPathParser {

    private static final Logger logger = LogManager.getLogger(SvgPathParser.class);

    // Pattern to extract path commands and their arguments
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "([MmZzLlHhVvCcSsQqTtAa])([^MmZzLlHhVvCcSsQqTtAa]*)"
    );

    // Pattern to extract numbers (including negative and decimal)
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?"
    );

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SvgPathParser() {
        // Utility class
    }

    /**
     * Parses SVG path data into a list of path commands.
     *
     * @param pathData the SVG path data string (e.g., "M0 0 L10 10 Z")
     * @return list of parsed path commands
     */
    public static List<PathCommand> parse(String pathData) {
        List<PathCommand> commands = new ArrayList<>();

        if (pathData == null || pathData.trim().isEmpty()) {
            return commands;
        }

        Matcher commandMatcher = COMMAND_PATTERN.matcher(pathData.trim());

        while (commandMatcher.find()) {
            char commandType = commandMatcher.group(1).charAt(0);
            String args = commandMatcher.group(2).trim();
            double[] numbers = parseNumbers(args);

            try {
                parseCommand(commands, commandType, numbers);
            } catch (Exception e) {
                logger.debug("Error parsing SVG command '{}': {}", commandType, e.getMessage());
            }
        }

        return commands;
    }

    /**
     * Extracts all numbers from an argument string.
     *
     * @param args the argument string containing numbers separated by whitespace or commas
     * @return an array of parsed double values
     */
    private static double[] parseNumbers(String args) {
        if (args == null || args.isEmpty()) {
            return new double[0];
        }

        List<Double> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(args);

        while (matcher.find()) {
            try {
                numbers.add(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException e) {
                // Skip invalid numbers
            }
        }

        return numbers.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * Parses a single command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param type the command character (e.g., 'M', 'L', 'C')
     * @param args the numeric arguments for the command
     */
    private static void parseCommand(List<PathCommand> commands, char type, double[] args) {
        boolean relative = Character.isLowerCase(type);
        char cmd = Character.toUpperCase(type);

        switch (cmd) {
            case 'M' -> parseMoveTo(commands, args, relative);
            case 'L' -> parseLineTo(commands, args, relative);
            case 'H' -> parseHorizontalLineTo(commands, args, relative);
            case 'V' -> parseVerticalLineTo(commands, args, relative);
            case 'C' -> parseCubicCurve(commands, args, relative);
            case 'S' -> parseSmoothCubicCurve(commands, args, relative);
            case 'Q' -> parseQuadraticCurve(commands, args, relative);
            case 'T' -> parseSmoothQuadraticCurve(commands, args, relative);
            case 'A' -> parseArc(commands, args, relative);
            case 'Z' -> commands.add(new PathCommand(CommandType.CLOSE_PATH, new double[0], false));
        }
    }

    /**
     * Parses a moveto command and adds it to the command list.
     * Subsequent coordinate pairs are treated as implicit lineto commands.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the coordinate pairs for the command
     * @param relative true if coordinates are relative to current position
     */
    private static void parseMoveTo(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 1 < args.length; i += 2) {
            CommandType type = (i == 0) ? CommandType.MOVE_TO : CommandType.LINE_TO;
            commands.add(new PathCommand(type, new double[]{args[i], args[i + 1]}, relative && i == 0));
        }
    }

    /**
     * Parses a lineto command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the coordinate pairs for the line endpoints
     * @param relative true if coordinates are relative to current position
     */
    private static void parseLineTo(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 1 < args.length; i += 2) {
            commands.add(new PathCommand(CommandType.LINE_TO, new double[]{args[i], args[i + 1]}, relative));
        }
    }

    /**
     * Parses a horizontal lineto command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the x-coordinates for the horizontal line endpoints
     * @param relative true if coordinates are relative to current position
     */
    private static void parseHorizontalLineTo(List<PathCommand> commands, double[] args, boolean relative) {
        for (double arg : args) {
            commands.add(new PathCommand(CommandType.HORIZONTAL_LINE_TO, new double[]{arg}, relative));
        }
    }

    /**
     * Parses a vertical lineto command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the y-coordinates for the vertical line endpoints
     * @param relative true if coordinates are relative to current position
     */
    private static void parseVerticalLineTo(List<PathCommand> commands, double[] args, boolean relative) {
        for (double arg : args) {
            commands.add(new PathCommand(CommandType.VERTICAL_LINE_TO, new double[]{arg}, relative));
        }
    }

    /**
     * Parses a cubic Bezier curve command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the control points and endpoint coordinates (cp1x, cp1y, cp2x, cp2y, x, y)
     * @param relative true if coordinates are relative to current position
     */
    private static void parseCubicCurve(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 5 < args.length; i += 6) {
            commands.add(new PathCommand(CommandType.CUBIC_CURVE,
                    new double[]{args[i], args[i + 1], args[i + 2], args[i + 3], args[i + 4], args[i + 5]}, relative));
        }
    }

    /**
     * Parses a smooth cubic Bezier curve command and adds it to the command list.
     * The first control point is the reflection of the previous command's control point.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the second control point and endpoint coordinates (cp2x, cp2y, x, y)
     * @param relative true if coordinates are relative to current position
     */
    private static void parseSmoothCubicCurve(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 3 < args.length; i += 4) {
            commands.add(new PathCommand(CommandType.SMOOTH_CUBIC_CURVE,
                    new double[]{args[i], args[i + 1], args[i + 2], args[i + 3]}, relative));
        }
    }

    /**
     * Parses a quadratic Bezier curve command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the control point and endpoint coordinates (cpx, cpy, x, y)
     * @param relative true if coordinates are relative to current position
     */
    private static void parseQuadraticCurve(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 3 < args.length; i += 4) {
            commands.add(new PathCommand(CommandType.QUADRATIC_CURVE,
                    new double[]{args[i], args[i + 1], args[i + 2], args[i + 3]}, relative));
        }
    }

    /**
     * Parses a smooth quadratic Bezier curve command and adds it to the command list.
     * The control point is the reflection of the previous command's control point.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the endpoint coordinates (x, y)
     * @param relative true if coordinates are relative to current position
     */
    private static void parseSmoothQuadraticCurve(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 1 < args.length; i += 2) {
            commands.add(new PathCommand(CommandType.SMOOTH_QUADRATIC_CURVE,
                    new double[]{args[i], args[i + 1]}, relative));
        }
    }

    /**
     * Parses an elliptical arc command and adds it to the command list.
     *
     * @param commands the list to which parsed commands will be added
     * @param args the arc parameters (rx, ry, rotation, large-arc-flag, sweep-flag, x, y)
     * @param relative true if endpoint coordinates are relative to current position
     */
    private static void parseArc(List<PathCommand> commands, double[] args, boolean relative) {
        for (int i = 0; i + 6 < args.length; i += 7) {
            commands.add(new PathCommand(CommandType.ARC,
                    new double[]{args[i], args[i + 1], args[i + 2], args[i + 3], args[i + 4], args[i + 5], args[i + 6]},
                    relative));
        }
    }

    /**
     * Renders parsed path commands on a GraphicsContext.
     *
     * @param gc         the GraphicsContext to render on
     * @param commands   the parsed path commands
     * @param x          x offset for rendering
     * @param y          y offset for rendering
     * @param scale      scale factor for the path
     * @param color      the color to use for rendering
     * @param fill       true to fill the path, false to stroke
     */
    public static void render(GraphicsContext gc, List<PathCommand> commands,
                              double x, double y, double scale, Color color, boolean fill) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        gc.save();
        gc.setFill(color);
        gc.setStroke(color);
        gc.setLineWidth(Math.max(0.5, scale / 16.0));

        // Current position
        double currentX = 0;
        double currentY = 0;
        // Start position for close path
        double startX = 0;
        double startY = 0;
        // Last control point for smooth curves
        double lastControlX = 0;
        double lastControlY = 0;
        CommandType lastCommand = null;

        gc.beginPath();

        for (PathCommand cmd : commands) {
            double[] args = cmd.args();
            boolean relative = cmd.relative();

            switch (cmd.type()) {
                case MOVE_TO -> {
                    double newX = relative ? currentX + args[0] : args[0];
                    double newY = relative ? currentY + args[1] : args[1];
                    gc.moveTo(x + newX * scale, y + newY * scale);
                    currentX = newX;
                    currentY = newY;
                    startX = newX;
                    startY = newY;
                }

                case LINE_TO -> {
                    double newX = relative ? currentX + args[0] : args[0];
                    double newY = relative ? currentY + args[1] : args[1];
                    gc.lineTo(x + newX * scale, y + newY * scale);
                    currentX = newX;
                    currentY = newY;
                }

                case HORIZONTAL_LINE_TO -> {
                    double newX = relative ? currentX + args[0] : args[0];
                    gc.lineTo(x + newX * scale, y + currentY * scale);
                    currentX = newX;
                }

                case VERTICAL_LINE_TO -> {
                    double newY = relative ? currentY + args[0] : args[0];
                    gc.lineTo(x + currentX * scale, y + newY * scale);
                    currentY = newY;
                }

                case CUBIC_CURVE -> {
                    double cp1x = relative ? currentX + args[0] : args[0];
                    double cp1y = relative ? currentY + args[1] : args[1];
                    double cp2x = relative ? currentX + args[2] : args[2];
                    double cp2y = relative ? currentY + args[3] : args[3];
                    double endX = relative ? currentX + args[4] : args[4];
                    double endY = relative ? currentY + args[5] : args[5];
                    gc.bezierCurveTo(
                            x + cp1x * scale, y + cp1y * scale,
                            x + cp2x * scale, y + cp2y * scale,
                            x + endX * scale, y + endY * scale
                    );
                    lastControlX = cp2x;
                    lastControlY = cp2y;
                    currentX = endX;
                    currentY = endY;
                }

                case SMOOTH_CUBIC_CURVE -> {
                    // Reflect the previous control point
                    double cp1x, cp1y;
                    if (lastCommand == CommandType.CUBIC_CURVE || lastCommand == CommandType.SMOOTH_CUBIC_CURVE) {
                        cp1x = 2 * currentX - lastControlX;
                        cp1y = 2 * currentY - lastControlY;
                    } else {
                        cp1x = currentX;
                        cp1y = currentY;
                    }
                    double cp2x = relative ? currentX + args[0] : args[0];
                    double cp2y = relative ? currentY + args[1] : args[1];
                    double endX = relative ? currentX + args[2] : args[2];
                    double endY = relative ? currentY + args[3] : args[3];
                    gc.bezierCurveTo(
                            x + cp1x * scale, y + cp1y * scale,
                            x + cp2x * scale, y + cp2y * scale,
                            x + endX * scale, y + endY * scale
                    );
                    lastControlX = cp2x;
                    lastControlY = cp2y;
                    currentX = endX;
                    currentY = endY;
                }

                case QUADRATIC_CURVE -> {
                    double cpx = relative ? currentX + args[0] : args[0];
                    double cpy = relative ? currentY + args[1] : args[1];
                    double endX = relative ? currentX + args[2] : args[2];
                    double endY = relative ? currentY + args[3] : args[3];
                    gc.quadraticCurveTo(
                            x + cpx * scale, y + cpy * scale,
                            x + endX * scale, y + endY * scale
                    );
                    lastControlX = cpx;
                    lastControlY = cpy;
                    currentX = endX;
                    currentY = endY;
                }

                case SMOOTH_QUADRATIC_CURVE -> {
                    double cpx, cpy;
                    if (lastCommand == CommandType.QUADRATIC_CURVE || lastCommand == CommandType.SMOOTH_QUADRATIC_CURVE) {
                        cpx = 2 * currentX - lastControlX;
                        cpy = 2 * currentY - lastControlY;
                    } else {
                        cpx = currentX;
                        cpy = currentY;
                    }
                    double endX = relative ? currentX + args[0] : args[0];
                    double endY = relative ? currentY + args[1] : args[1];
                    gc.quadraticCurveTo(
                            x + cpx * scale, y + cpy * scale,
                            x + endX * scale, y + endY * scale
                    );
                    lastControlX = cpx;
                    lastControlY = cpy;
                    currentX = endX;
                    currentY = endY;
                }

                case ARC -> {
                    // Arc parameters: rx, ry, x-axis-rotation, large-arc-flag, sweep-flag, x, y
                    double rx = args[0];
                    double ry = args[1];
                    double rotation = args[2];
                    boolean largeArc = args[3] != 0;
                    boolean sweep = args[4] != 0;
                    double endX = relative ? currentX + args[5] : args[5];
                    double endY = relative ? currentY + args[6] : args[6];

                    // Convert arc to bezier curves (simplified approximation)
                    renderArcAsBezier(gc, x, y, scale, currentX, currentY, rx, ry, rotation, largeArc, sweep, endX, endY);

                    currentX = endX;
                    currentY = endY;
                }

                case CLOSE_PATH -> {
                    gc.closePath();
                    currentX = startX;
                    currentY = startY;
                }
            }

            lastCommand = cmd.type();
        }

        if (fill) {
            gc.fill();
        } else {
            gc.stroke();
        }

        gc.restore();
    }

    /**
     * Renders an elliptical arc as bezier curves (approximation).
     * This is a simplified implementation for common use cases.
     *
     * @param gc the GraphicsContext to render on
     * @param offsetX the x offset for rendering
     * @param offsetY the y offset for rendering
     * @param scale the scale factor for rendering
     * @param x1 the starting x coordinate
     * @param y1 the starting y coordinate
     * @param rx the x-axis radius of the ellipse
     * @param ry the y-axis radius of the ellipse
     * @param rotation the rotation angle of the ellipse in degrees
     * @param largeArc true to use the large arc, false for the small arc
     * @param sweep true for positive angle sweep, false for negative
     * @param x2 the ending x coordinate
     * @param y2 the ending y coordinate
     */
    private static void renderArcAsBezier(GraphicsContext gc, double offsetX, double offsetY, double scale,
                                          double x1, double y1, double rx, double ry, double rotation,
                                          boolean largeArc, boolean sweep, double x2, double y2) {
        // Simple arc approximation using quadratic curves
        // For a full implementation, this would need proper elliptical arc parameterization

        if (rx == 0 || ry == 0) {
            // Degenerate arc - just draw a line
            gc.lineTo(offsetX + x2 * scale, offsetY + y2 * scale);
            return;
        }

        // Calculate midpoint
        double midX = (x1 + x2) / 2;
        double midY = (y1 + y2) / 2;

        // Calculate perpendicular offset for arc
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist == 0) {
            return;
        }

        // Perpendicular direction
        double perpX = -dy / dist;
        double perpY = dx / dist;

        // Arc height based on radius (simplified)
        double arcHeight = Math.min(rx, ry) * 0.5;
        if (!sweep) {
            arcHeight = -arcHeight;
        }

        // Control point
        double cpX = midX + perpX * arcHeight;
        double cpY = midY + perpY * arcHeight;

        gc.quadraticCurveTo(
                offsetX + cpX * scale, offsetY + cpY * scale,
                offsetX + x2 * scale, offsetY + y2 * scale
        );
    }

    /**
     * Convenience method to parse and render an SVG path in one call.
     *
     * @param gc        the GraphicsContext
     * @param pathData  the SVG path data string
     * @param x         x offset
     * @param y         y offset
     * @param size      the target size (path will be scaled to fit)
     * @param color     the rendering color
     * @param fill      true to fill, false to stroke
     * @param viewBoxSize the original viewBox size of the SVG (typically 16 for Bootstrap Icons)
     */
    public static void renderPath(GraphicsContext gc, String pathData,
                                  double x, double y, double size, Color color, boolean fill, double viewBoxSize) {
        List<PathCommand> commands = parse(pathData);
        double scale = size / viewBoxSize;
        render(gc, commands, x, y, scale, color, fill);
    }

    /**
     * Enumeration of all supported SVG path command types.
     * Each type corresponds to a specific SVG path command letter.
     */
    public enum CommandType {
        MOVE_TO,
        LINE_TO,
        HORIZONTAL_LINE_TO,
        VERTICAL_LINE_TO,
        CUBIC_CURVE,
        SMOOTH_CUBIC_CURVE,
        QUADRATIC_CURVE,
        SMOOTH_QUADRATIC_CURVE,
        ARC,
        CLOSE_PATH
    }

    /**
     * Represents a parsed SVG path command with its type, arguments, and positioning mode.
     *
     * @param type the command type (e.g., MOVE_TO, LINE_TO, CUBIC_CURVE)
     * @param args the numeric arguments for the command
     * @param relative true if coordinates are relative to current position, false for absolute
     */
    public record PathCommand(CommandType type, double[] args, boolean relative) {
    }
}
