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
 * Unit tests for PdfDocumentationConfig.
 */
class PdfDocumentationConfigTest {

    @Test
    void testDefaultValues() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();

        // Page Layout defaults
        assertEquals(PdfDocumentationConfig.PageSize.A4, config.getPageSize());
        assertEquals(PdfDocumentationConfig.Orientation.PORTRAIT, config.getOrientation());
        assertEquals(PdfDocumentationConfig.Margins.NORMAL, config.getMargins());

        // Content Section defaults
        assertTrue(config.isIncludeCoverPage());
        assertTrue(config.isIncludeToc());
        assertTrue(config.isIncludeSchemaOverview());
        assertTrue(config.isIncludeSchemaDiagram());
        assertTrue(config.isIncludeComplexTypes());
        assertTrue(config.isIncludeSimpleTypes());
        assertTrue(config.isIncludeDataDictionary());

        // Typography defaults
        assertEquals(PdfDocumentationConfig.FontSize.PT_11, config.getFontSize());
        assertEquals(PdfDocumentationConfig.FontFamily.HELVETICA, config.getFontFamily());
        assertEquals(PdfDocumentationConfig.HeadingStyle.BOLD_BLUE, config.getHeadingStyle());
        assertEquals(PdfDocumentationConfig.LineSpacing.NORMAL, config.getLineSpacing());

        // Header & Footer defaults
        assertEquals(PdfDocumentationConfig.HeaderFooterStyle.STANDARD, config.getHeaderStyle());
        assertEquals(PdfDocumentationConfig.HeaderFooterStyle.STANDARD, config.getFooterStyle());
        assertTrue(config.isIncludePageNumbers());
        assertEquals(PdfDocumentationConfig.PageNumberPosition.CENTER, config.getPageNumberPosition());

        // Design defaults
        assertEquals(PdfDocumentationConfig.ColorScheme.BLUE, config.getColorScheme());
        assertEquals(PdfDocumentationConfig.TableStyle.BORDERED, config.getTableStyle());
        assertEquals(PdfDocumentationConfig.Watermark.NONE, config.getWatermark());

        // PDF Metadata defaults
        assertTrue(config.isGenerateBookmarks());
        assertNull(config.getTitle());
        assertNull(config.getAuthor());
        assertNull(config.getSubject());
        assertNull(config.getKeywords());
    }

    @Test
    void testCopyConstructor() {
        PdfDocumentationConfig original = new PdfDocumentationConfig();
        original.setPageSize(PdfDocumentationConfig.PageSize.LETTER);
        original.setOrientation(PdfDocumentationConfig.Orientation.LANDSCAPE);
        original.setColorScheme(PdfDocumentationConfig.ColorScheme.GREEN);
        original.setIncludeCoverPage(false);
        original.setTitle("Test Title");

        PdfDocumentationConfig copy = new PdfDocumentationConfig(original);

        assertEquals(original.getPageSize(), copy.getPageSize());
        assertEquals(original.getOrientation(), copy.getOrientation());
        assertEquals(original.getColorScheme(), copy.getColorScheme());
        assertEquals(original.isIncludeCoverPage(), copy.isIncludeCoverPage());
        assertEquals(original.getTitle(), copy.getTitle());

        // Verify it's a deep copy (changing original doesn't affect copy)
        original.setTitle("Modified Title");
        assertNotEquals(original.getTitle(), copy.getTitle());
    }

    @ParameterizedTest
    @EnumSource(PdfDocumentationConfig.PageSize.class)
    void testPageSizePortraitDimensions(PdfDocumentationConfig.PageSize pageSize) {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setPageSize(pageSize);
        config.setOrientation(PdfDocumentationConfig.Orientation.PORTRAIT);

        String width = config.getPageWidth();
        String height = config.getPageHeight();

        assertNotNull(width);
        assertNotNull(height);
        assertTrue(width.endsWith("mm") || width.endsWith("in"));
        assertTrue(height.endsWith("mm") || height.endsWith("in"));
    }

    @Test
    void testA4PortraitDimensions() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setPageSize(PdfDocumentationConfig.PageSize.A4);
        config.setOrientation(PdfDocumentationConfig.Orientation.PORTRAIT);

        assertEquals("210mm", config.getPageWidth());
        assertEquals("297mm", config.getPageHeight());
    }

    @Test
    void testA4LandscapeDimensions() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setPageSize(PdfDocumentationConfig.PageSize.A4);
        config.setOrientation(PdfDocumentationConfig.Orientation.LANDSCAPE);

        assertEquals("297mm", config.getPageWidth());
        assertEquals("210mm", config.getPageHeight());
    }

    @Test
    void testLetterPortraitDimensions() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setPageSize(PdfDocumentationConfig.PageSize.LETTER);
        config.setOrientation(PdfDocumentationConfig.Orientation.PORTRAIT);

        assertEquals("8.5in", config.getPageWidth());
        assertEquals("11in", config.getPageHeight());
    }

    @Test
    void testMarginValues() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();

        config.setMargins(PdfDocumentationConfig.Margins.NARROW);
        assertEquals("15mm", config.getMarginSize());

        config.setMargins(PdfDocumentationConfig.Margins.NORMAL);
        assertEquals("20mm", config.getMarginSize());

        config.setMargins(PdfDocumentationConfig.Margins.WIDE);
        assertEquals("30mm", config.getMarginSize());
    }

    @ParameterizedTest
    @EnumSource(PdfDocumentationConfig.ColorScheme.class)
    void testColorSchemeValues(PdfDocumentationConfig.ColorScheme colorScheme) {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setColorScheme(colorScheme);

        assertNotNull(config.getPrimaryColor());
        assertTrue(config.getPrimaryColor().startsWith("#"));
        assertEquals(7, config.getPrimaryColor().length()); // #RRGGBB format
    }

    @Test
    void testBluePrimaryColor() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setColorScheme(PdfDocumentationConfig.ColorScheme.BLUE);

        assertEquals("#2563EB", config.getPrimaryColor());
    }

    @Test
    void testFontSizeValue() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();

        config.setFontSize(PdfDocumentationConfig.FontSize.PT_9);
        assertEquals("9pt", config.getFontSizeValue());

        config.setFontSize(PdfDocumentationConfig.FontSize.PT_10);
        assertEquals("10pt", config.getFontSizeValue());

        config.setFontSize(PdfDocumentationConfig.FontSize.PT_11);
        assertEquals("11pt", config.getFontSizeValue());

        config.setFontSize(PdfDocumentationConfig.FontSize.PT_12);
        assertEquals("12pt", config.getFontSizeValue());
    }

    @Test
    void testFontFamilyName() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();

        config.setFontFamily(PdfDocumentationConfig.FontFamily.HELVETICA);
        assertEquals("Helvetica", config.getFontFamilyName());

        config.setFontFamily(PdfDocumentationConfig.FontFamily.TIMES);
        assertEquals("Times", config.getFontFamilyName());

        config.setFontFamily(PdfDocumentationConfig.FontFamily.COURIER);
        assertEquals("Courier", config.getFontFamilyName());
    }

    @Test
    void testLineHeightValue() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();

        config.setLineSpacing(PdfDocumentationConfig.LineSpacing.COMPACT);
        assertEquals("1.0", config.getLineHeightValue());

        config.setLineSpacing(PdfDocumentationConfig.LineSpacing.NORMAL);
        assertEquals("1.2", config.getLineHeightValue());

        config.setLineSpacing(PdfDocumentationConfig.LineSpacing.RELAXED);
        assertEquals("1.5", config.getLineHeightValue());
    }

    @Test
    void testWatermark() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();

        // No watermark by default
        assertFalse(config.hasWatermark());
        assertEquals("", config.getWatermarkText());

        // Set watermark
        config.setWatermark(PdfDocumentationConfig.Watermark.DRAFT);
        assertTrue(config.hasWatermark());
        assertEquals("DRAFT", config.getWatermarkText());

        config.setWatermark(PdfDocumentationConfig.Watermark.CONFIDENTIAL);
        assertTrue(config.hasWatermark());
        assertEquals("CONFIDENTIAL", config.getWatermarkText());

        // Reset to none
        config.setWatermark(PdfDocumentationConfig.Watermark.NONE);
        assertFalse(config.hasWatermark());
    }

    @Test
    void testHeadingStyle() {
        PdfDocumentationConfig.HeadingStyle boldBlue = PdfDocumentationConfig.HeadingStyle.BOLD_BLUE;
        assertTrue(boldBlue.isBold());
        assertFalse(boldBlue.isUnderlined());
        assertEquals("#2563EB", boldBlue.getColor());

        PdfDocumentationConfig.HeadingStyle boldBlack = PdfDocumentationConfig.HeadingStyle.BOLD_BLACK;
        assertTrue(boldBlack.isBold());
        assertFalse(boldBlack.isUnderlined());
        assertEquals("#000000", boldBlack.getColor());

        PdfDocumentationConfig.HeadingStyle underlined = PdfDocumentationConfig.HeadingStyle.UNDERLINED;
        assertFalse(underlined.isBold());
        assertTrue(underlined.isUnderlined());
    }

    @Test
    void testPageNumberPosition() {
        assertEquals("left", PdfDocumentationConfig.PageNumberPosition.LEFT.getAlignment());
        assertEquals("center", PdfDocumentationConfig.PageNumberPosition.CENTER.getAlignment());
        assertEquals("right", PdfDocumentationConfig.PageNumberPosition.RIGHT.getAlignment());
    }

    @Test
    void testEnumDisplayNames() {
        // PageSize
        assertEquals("A4", PdfDocumentationConfig.PageSize.A4.getDisplayName());
        assertEquals("Letter", PdfDocumentationConfig.PageSize.LETTER.getDisplayName());

        // Orientation
        assertEquals("Portrait", PdfDocumentationConfig.Orientation.PORTRAIT.getDisplayName());
        assertEquals("Landscape", PdfDocumentationConfig.Orientation.LANDSCAPE.getDisplayName());

        // ColorScheme
        assertEquals("Blue", PdfDocumentationConfig.ColorScheme.BLUE.getDisplayName());
        assertEquals("Professional", PdfDocumentationConfig.ColorScheme.PROFESSIONAL.getDisplayName());
    }

    @Test
    void testToString() {
        PdfDocumentationConfig config = new PdfDocumentationConfig();
        String str = config.toString();

        assertNotNull(str);
        assertTrue(str.contains("pageSize=A4"));
        assertTrue(str.contains("orientation=Portrait"));
        assertTrue(str.contains("colorScheme=Blue"));
    }
}
