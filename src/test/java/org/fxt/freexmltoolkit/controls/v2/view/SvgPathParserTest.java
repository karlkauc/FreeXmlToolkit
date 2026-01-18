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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SvgPathParser.
 */
@DisplayName("SvgPathParser Tests")
class SvgPathParserTest {

    @Test
    @DisplayName("Parse empty path data returns empty list")
    void parseEmptyPathReturnsEmptyList() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("");
        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("Parse null path data returns empty list")
    void parseNullPathReturnsEmptyList() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(null);
        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("Parse simple move-to command")
    void parseSimpleMoveToCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M10 20");
        assertEquals(1, commands.size());
        assertEquals(SvgPathParser.CommandType.MOVE_TO, commands.get(0).type());
        assertArrayEquals(new double[]{10.0, 20.0}, commands.get(0).args());
        assertFalse(commands.get(0).relative());
    }

    @Test
    @DisplayName("Parse relative move-to command")
    void parseRelativeMoveToCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("m10 20");
        assertEquals(1, commands.size());
        assertEquals(SvgPathParser.CommandType.MOVE_TO, commands.get(0).type());
        assertTrue(commands.get(0).relative());
    }

    @Test
    @DisplayName("Parse line-to command")
    void parseLineToCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 L10 20");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.LINE_TO, commands.get(1).type());
        assertArrayEquals(new double[]{10.0, 20.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse horizontal line-to command")
    void parseHorizontalLineToCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 H15");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.HORIZONTAL_LINE_TO, commands.get(1).type());
        assertArrayEquals(new double[]{15.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse vertical line-to command")
    void parseVerticalLineToCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 V25");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.VERTICAL_LINE_TO, commands.get(1).type());
        assertArrayEquals(new double[]{25.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse close path command")
    void parseClosePathCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 L10 10 Z");
        assertEquals(3, commands.size());
        assertEquals(SvgPathParser.CommandType.CLOSE_PATH, commands.get(2).type());
    }

    @Test
    @DisplayName("Parse cubic Bezier curve command")
    void parseCubicCurveCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 C10 20 30 40 50 60");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.CUBIC_CURVE, commands.get(1).type());
        assertArrayEquals(new double[]{10.0, 20.0, 30.0, 40.0, 50.0, 60.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse smooth cubic Bezier curve command")
    void parseSmoothCubicCurveCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 S30 40 50 60");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.SMOOTH_CUBIC_CURVE, commands.get(1).type());
        assertArrayEquals(new double[]{30.0, 40.0, 50.0, 60.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse quadratic curve command")
    void parseQuadraticCurveCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 Q10 20 30 40");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.QUADRATIC_CURVE, commands.get(1).type());
        assertArrayEquals(new double[]{10.0, 20.0, 30.0, 40.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse smooth quadratic curve command")
    void parseSmoothQuadraticCurveCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 T30 40");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.SMOOTH_QUADRATIC_CURVE, commands.get(1).type());
        assertArrayEquals(new double[]{30.0, 40.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse arc command")
    void parseArcCommand() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0 0 A5 5 0 0 1 10 10");
        assertEquals(2, commands.size());
        assertEquals(SvgPathParser.CommandType.ARC, commands.get(1).type());
        assertArrayEquals(new double[]{5.0, 5.0, 0.0, 0.0, 1.0, 10.0, 10.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse complex path with multiple commands")
    void parseComplexPath() {
        // A simplified Bootstrap icon path
        String path = "M6 3.5A1.5 1.5 0 0 1 7.5 2h1A1.5 1.5 0 0 1 10 3.5v1";
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(path);
        assertFalse(commands.isEmpty());
        assertEquals(SvgPathParser.CommandType.MOVE_TO, commands.get(0).type());
    }

    @Test
    @DisplayName("Parse negative numbers correctly")
    void parseNegativeNumbers() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M-10 -20 L-30 -40");
        assertEquals(2, commands.size());
        assertArrayEquals(new double[]{-10.0, -20.0}, commands.get(0).args());
        assertArrayEquals(new double[]{-30.0, -40.0}, commands.get(1).args());
    }

    @Test
    @DisplayName("Parse decimal numbers correctly")
    void parseDecimalNumbers() {
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse("M0.5 1.25 L2.75 3.125");
        assertEquals(2, commands.size());
        assertArrayEquals(new double[]{0.5, 1.25}, commands.get(0).args());
        assertArrayEquals(new double[]{2.75, 3.125}, commands.get(1).args());
    }

    @Test
    @DisplayName("Bootstrap icon path data is available for common icons")
    void bootstrapIconPathsAvailable() {
        assertTrue(BootstrapIconPaths.hasPath("bi-diagram-3"));
        assertTrue(BootstrapIconPaths.hasPath("bi-file-earmark-code"));
        assertTrue(BootstrapIconPaths.hasPath("bi-folder"));
        assertTrue(BootstrapIconPaths.hasPath("bi-key"));
        assertTrue(BootstrapIconPaths.hasPath("bi-globe"));
    }

    @Test
    @DisplayName("Bootstrap icon paths can be parsed without errors")
    void bootstrapIconPathsCanBeParsed() {
        String[] testIcons = {"bi-diagram-3", "bi-folder", "bi-key", "bi-globe", "bi-calculator"};

        for (String icon : testIcons) {
            String pathData = BootstrapIconPaths.getPath(icon);
            assertNotNull(pathData, "Path data should exist for " + icon);

            List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(pathData);
            assertFalse(commands.isEmpty(), "Commands should not be empty for " + icon);
        }
    }

    @Test
    @DisplayName("Register custom icon path")
    void registerCustomIconPath() {
        String customIcon = "bi-custom-test";
        String customPath = "M0 0 L10 10 Z";

        assertFalse(BootstrapIconPaths.hasPath(customIcon));
        BootstrapIconPaths.registerPath(customIcon, customPath);
        assertTrue(BootstrapIconPaths.hasPath(customIcon));
        assertEquals(customPath, BootstrapIconPaths.getPath(customIcon));
    }

    @Test
    @DisplayName("VIEWBOX_SIZE constant is 16.0")
    void viewboxSizeIs16() {
        assertEquals(16.0, BootstrapIconPaths.VIEWBOX_SIZE);
    }
}
