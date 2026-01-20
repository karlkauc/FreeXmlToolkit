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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdTypeIconPaths - custom XSD type icons.
 */
@DisplayName("XsdTypeIconPaths Tests")
class XsdTypeIconPathsTest {

    @BeforeAll
    static void setUp() {
        // Register all XSD type icons
        XsdTypeIconPaths.registerAll();
    }

    @Test
    @DisplayName("ComplexType icon is registered and can be parsed")
    void complexTypeIconIsRegistered() {
        assertTrue(BootstrapIconPaths.hasPath("xsd-complex-type"),
            "xsd-complex-type icon should be registered");

        String path = BootstrapIconPaths.getPath("xsd-complex-type");
        assertNotNull(path, "ComplexType path should not be null");
        assertFalse(path.isEmpty(), "ComplexType path should not be empty");

        // Verify it can be parsed
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(path);
        assertFalse(commands.isEmpty(), "ComplexType path should parse to commands");
    }

    @Test
    @DisplayName("Element with ComplexType reference icon is registered and can be parsed")
    void elementComplexIconIsRegistered() {
        assertTrue(BootstrapIconPaths.hasPath("xsd-element-complex"),
            "xsd-element-complex icon should be registered");

        String path = BootstrapIconPaths.getPath("xsd-element-complex");
        assertNotNull(path, "Element complex path should not be null");
        assertFalse(path.isEmpty(), "Element complex path should not be empty");

        // Verify it can be parsed
        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(path);
        assertFalse(commands.isEmpty(), "Element complex path should parse to commands");
    }

    @Test
    @DisplayName("Generic SimpleType icon is registered")
    void simpleGenericIconIsRegistered() {
        assertTrue(BootstrapIconPaths.hasPath("xsd-simple-generic"),
            "xsd-simple-generic icon should be registered");

        String path = BootstrapIconPaths.getPath("xsd-simple-generic");
        assertNotNull(path);

        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(path);
        assertFalse(commands.isEmpty(), "Generic SimpleType path should parse");
    }

    @Test
    @DisplayName("All SimpleType icons are registered")
    void allSimpleTypeIconsAreRegistered() {
        String[] simpleTypeIcons = {
            "xsd-simple-generic",
            "xsd-simple-string",
            "xsd-simple-number",
            "xsd-simple-decimal",
            "xsd-simple-boolean",
            "xsd-simple-date",
            "xsd-simple-time",
            "xsd-simple-id",
            "xsd-simple-uri",
            "xsd-simple-binary",
            "xsd-simple-token",
            "xsd-simple-name",
            "xsd-simple-language"
        };

        for (String iconName : simpleTypeIcons) {
            assertTrue(BootstrapIconPaths.hasPath(iconName),
                iconName + " should be registered");
        }
    }

    @Test
    @DisplayName("All SimpleType icons can be parsed without errors")
    void allSimpleTypeIconsCanBeParsed() {
        String[] simpleTypeIcons = {
            "xsd-simple-generic",
            "xsd-simple-string",
            "xsd-simple-number",
            "xsd-simple-decimal",
            "xsd-simple-boolean",
            "xsd-simple-date",
            "xsd-simple-time",
            "xsd-simple-id",
            "xsd-simple-uri",
            "xsd-simple-binary",
            "xsd-simple-token",
            "xsd-simple-name",
            "xsd-simple-language"
        };

        for (String iconName : simpleTypeIcons) {
            String path = BootstrapIconPaths.getPath(iconName);
            assertNotNull(path, iconName + " path should not be null");

            List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(path);
            assertFalse(commands.isEmpty(),
                iconName + " should parse to non-empty commands");
        }
    }

    @Test
    @DisplayName("SimpleType icons have separate base paths for badge rendering")
    void simpleTypeIconsHaveBasePaths() {
        String[] simpleTypeIcons = {
            "xsd-simple-generic",
            "xsd-simple-string",
            "xsd-simple-number"
        };

        for (String iconName : simpleTypeIcons) {
            assertTrue(XsdTypeIconPaths.isSimpleTypeIcon(iconName),
                iconName + " should be identified as SimpleType icon");

            String basePath = XsdTypeIconPaths.getBaseIconPath(iconName);
            assertNotNull(basePath,
                iconName + " should have a base path");
            assertFalse(basePath.isEmpty(),
                iconName + " base path should not be empty");
        }
    }

    @Test
    @DisplayName("S badge components are valid SVG paths")
    void sBadgeComponentsAreValid() {
        // Badge circle
        assertNotNull(XsdTypeIconPaths.S_BADGE_CIRCLE);
        List<SvgPathParser.PathCommand> circleCommands = SvgPathParser.parse(XsdTypeIconPaths.S_BADGE_CIRCLE);
        assertFalse(circleCommands.isEmpty(), "Badge circle should parse to commands");

        // Badge letter
        assertNotNull(XsdTypeIconPaths.S_BADGE_LETTER);
        List<SvgPathParser.PathCommand> letterCommands = SvgPathParser.parse(XsdTypeIconPaths.S_BADGE_LETTER);
        assertFalse(letterCommands.isEmpty(), "Badge letter should parse to commands");
    }

    @Test
    @DisplayName("ComplexType icon shows structure (contains multiple path segments)")
    void complexTypeIconShowsStructure() {
        String path = BootstrapIconPaths.getPath("xsd-complex-type");
        assertNotNull(path);

        List<SvgPathParser.PathCommand> commands = SvgPathParser.parse(path);

        // ComplexType should have multiple segments showing nested structure
        // At minimum: outer box + header line + nested elements
        assertTrue(commands.size() > 5,
            "ComplexType should have multiple path commands for structure");
    }

    @Test
    @DisplayName("Icon paths start with valid SVG commands")
    void iconPathsStartWithValidCommands() {
        String[] allIcons = {
            "xsd-complex-type",
            "xsd-simple-generic",
            "xsd-simple-string",
            "xsd-simple-number"
        };

        for (String iconName : allIcons) {
            String path = BootstrapIconPaths.getPath(iconName);
            assertNotNull(path);

            // SVG paths should start with M (moveto) command
            assertTrue(path.trim().startsWith("M"),
                iconName + " path should start with M command");
        }
    }
}
