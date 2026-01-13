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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WordDocumentationConfig.
 */
class WordDocumentationConfigTest {

    @Test
    void testDefaultValues() {
        WordDocumentationConfig config = new WordDocumentationConfig();

        // Page Layout defaults
        assertEquals(WordDocumentationConfig.PageSize.A4, config.getPageSize());
        assertEquals(WordDocumentationConfig.Orientation.PORTRAIT, config.getOrientation());

        // Content Section defaults
        assertFalse(config.isIncludeCoverPage()); // Default is false
        assertTrue(config.isIncludeToc());
        assertTrue(config.isIncludeDataDictionary());
        assertTrue(config.isIncludeSchemaDiagram());

        // Styling defaults
        assertEquals(WordDocumentationConfig.HeaderStyle.PROFESSIONAL, config.getHeaderStyle());
    }

    @Test
    void testCopyConstructor() {
        WordDocumentationConfig original = new WordDocumentationConfig();
        original.setPageSize(WordDocumentationConfig.PageSize.LETTER);
        original.setOrientation(WordDocumentationConfig.Orientation.LANDSCAPE);
        original.setIncludeCoverPage(true);
        original.setIncludeToc(false);
        original.setHeaderStyle(WordDocumentationConfig.HeaderStyle.MINIMAL);

        WordDocumentationConfig copy = new WordDocumentationConfig(original);

        assertEquals(original.getPageSize(), copy.getPageSize());
        assertEquals(original.getOrientation(), copy.getOrientation());
        assertEquals(original.isIncludeCoverPage(), copy.isIncludeCoverPage());
        assertEquals(original.isIncludeToc(), copy.isIncludeToc());
        assertEquals(original.getHeaderStyle(), copy.getHeaderStyle());
    }

    @ParameterizedTest
    @EnumSource(WordDocumentationConfig.PageSize.class)
    void testPageSizeEnumValues(WordDocumentationConfig.PageSize pageSize) {
        assertNotNull(pageSize.getDisplayName());
        assertFalse(pageSize.getDisplayName().isEmpty());
    }

    @Test
    void testPageSizeDisplayNames() {
        assertEquals("A4", WordDocumentationConfig.PageSize.A4.getDisplayName());
        assertEquals("Letter", WordDocumentationConfig.PageSize.LETTER.getDisplayName());
        assertEquals("Legal", WordDocumentationConfig.PageSize.LEGAL.getDisplayName());
    }

    @ParameterizedTest
    @EnumSource(WordDocumentationConfig.Orientation.class)
    void testOrientationEnumValues(WordDocumentationConfig.Orientation orientation) {
        assertNotNull(orientation.getDisplayName());
        assertFalse(orientation.getDisplayName().isEmpty());
    }

    @Test
    void testOrientationDisplayNames() {
        assertEquals("Portrait", WordDocumentationConfig.Orientation.PORTRAIT.getDisplayName());
        assertEquals("Landscape", WordDocumentationConfig.Orientation.LANDSCAPE.getDisplayName());
    }

    @ParameterizedTest
    @EnumSource(WordDocumentationConfig.HeaderStyle.class)
    void testHeaderStyleEnumValues(WordDocumentationConfig.HeaderStyle headerStyle) {
        assertNotNull(headerStyle.getDisplayName());
        assertFalse(headerStyle.getDisplayName().isEmpty());
    }

    @Test
    void testHeaderStyleDisplayNames() {
        assertEquals("Professional", WordDocumentationConfig.HeaderStyle.PROFESSIONAL.getDisplayName());
        assertEquals("Minimal", WordDocumentationConfig.HeaderStyle.MINIMAL.getDisplayName());
        assertEquals("Colorful", WordDocumentationConfig.HeaderStyle.COLORFUL.getDisplayName());
    }

    @Test
    void testSettersAndGetters() {
        WordDocumentationConfig config = new WordDocumentationConfig();

        // Test page size
        config.setPageSize(WordDocumentationConfig.PageSize.LEGAL);
        assertEquals(WordDocumentationConfig.PageSize.LEGAL, config.getPageSize());

        // Test orientation
        config.setOrientation(WordDocumentationConfig.Orientation.LANDSCAPE);
        assertEquals(WordDocumentationConfig.Orientation.LANDSCAPE, config.getOrientation());

        // Test include cover page
        config.setIncludeCoverPage(true);
        assertTrue(config.isIncludeCoverPage());

        // Test include TOC
        config.setIncludeToc(false);
        assertFalse(config.isIncludeToc());

        // Test include data dictionary
        config.setIncludeDataDictionary(false);
        assertFalse(config.isIncludeDataDictionary());

        // Test include schema diagram
        config.setIncludeSchemaDiagram(false);
        assertFalse(config.isIncludeSchemaDiagram());

        // Test header style
        config.setHeaderStyle(WordDocumentationConfig.HeaderStyle.COLORFUL);
        assertEquals(WordDocumentationConfig.HeaderStyle.COLORFUL, config.getHeaderStyle());
    }

    @Test
    void testToString() {
        WordDocumentationConfig config = new WordDocumentationConfig();
        String str = config.toString();

        assertNotNull(str);
        assertTrue(str.contains("pageSize=A4"));
        assertTrue(str.contains("orientation=Portrait"));
        assertTrue(str.contains("headerStyle=Professional"));
    }

    @Test
    void testEnumToStringMethods() {
        // PageSize toString returns display name
        assertEquals("A4", WordDocumentationConfig.PageSize.A4.toString());
        assertEquals("Letter", WordDocumentationConfig.PageSize.LETTER.toString());

        // Orientation toString returns display name
        assertEquals("Portrait", WordDocumentationConfig.Orientation.PORTRAIT.toString());
        assertEquals("Landscape", WordDocumentationConfig.Orientation.LANDSCAPE.toString());

        // HeaderStyle toString returns display name
        assertEquals("Professional", WordDocumentationConfig.HeaderStyle.PROFESSIONAL.toString());
        assertEquals("Minimal", WordDocumentationConfig.HeaderStyle.MINIMAL.toString());
    }

    @Test
    void testAllCombinations() {
        // Test that all page sizes work with all orientations
        for (WordDocumentationConfig.PageSize pageSize : WordDocumentationConfig.PageSize.values()) {
            for (WordDocumentationConfig.Orientation orientation : WordDocumentationConfig.Orientation.values()) {
                WordDocumentationConfig config = new WordDocumentationConfig();
                config.setPageSize(pageSize);
                config.setOrientation(orientation);

                assertNotNull(config.getPageSize());
                assertNotNull(config.getOrientation());
                assertEquals(pageSize, config.getPageSize());
                assertEquals(orientation, config.getOrientation());
            }
        }
    }

    @Test
    void testContentSectionToggles() {
        WordDocumentationConfig config = new WordDocumentationConfig();

        // Toggle all off
        config.setIncludeCoverPage(false);
        config.setIncludeToc(false);
        config.setIncludeDataDictionary(false);
        config.setIncludeSchemaDiagram(false);

        assertFalse(config.isIncludeCoverPage());
        assertFalse(config.isIncludeToc());
        assertFalse(config.isIncludeDataDictionary());
        assertFalse(config.isIncludeSchemaDiagram());

        // Toggle all on
        config.setIncludeCoverPage(true);
        config.setIncludeToc(true);
        config.setIncludeDataDictionary(true);
        config.setIncludeSchemaDiagram(true);

        assertTrue(config.isIncludeCoverPage());
        assertTrue(config.isIncludeToc());
        assertTrue(config.isIncludeDataDictionary());
        assertTrue(config.isIncludeSchemaDiagram());
    }
}
