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

import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlCanvasRenderingHelper utility class.
 * Tests rendering constants, font caching, and text estimation.
 */
class XmlCanvasRenderingHelperTest {

    @Test
    void getHeaderFont_returnsCachedInstance() {
        Font font1 = XmlCanvasRenderingHelper.getHeaderFont();
        Font font2 = XmlCanvasRenderingHelper.getHeaderFont();
        assertSame(font1, font2, "Should return the same cached instance");
    }

    @Test
    void getRegularFont_returnsCachedInstance() {
        Font font1 = XmlCanvasRenderingHelper.getRegularFont();
        Font font2 = XmlCanvasRenderingHelper.getRegularFont();
        assertSame(font1, font2, "Should return the same cached instance");
    }

    @Test
    void getSmallFont_returnsCachedInstance() {
        Font font1 = XmlCanvasRenderingHelper.getSmallFont();
        Font font2 = XmlCanvasRenderingHelper.getSmallFont();
        assertSame(font1, font2, "Should return the same cached instance");
    }

    @Test
    void getHeaderFont_hasCorrectSize() {
        Font font = XmlCanvasRenderingHelper.getHeaderFont();
        assertEquals(XmlCanvasRenderingHelper.FONT_SIZE_HEADER, font.getSize());
    }

    @Test
    void getRegularFont_hasCorrectSize() {
        Font font = XmlCanvasRenderingHelper.getRegularFont();
        assertEquals(XmlCanvasRenderingHelper.FONT_SIZE_REGULAR, font.getSize());
    }

    @Test
    void getSmallFont_hasCorrectSize() {
        Font font = XmlCanvasRenderingHelper.getSmallFont();
        assertEquals(XmlCanvasRenderingHelper.FONT_SIZE_SMALL, font.getSize());
    }

    @Test
    void estimateTextWidth_nullText() {
        double width = XmlCanvasRenderingHelper.estimateTextWidth(null, 12);
        assertEquals(0, width);
    }

    @Test
    void estimateTextWidth_emptyText() {
        double width = XmlCanvasRenderingHelper.estimateTextWidth("", 12);
        assertEquals(0, width);
    }

    @Test
    void estimateTextWidth_calculatesBasedOnLength() {
        double width = XmlCanvasRenderingHelper.estimateTextWidth("Hello", 12);
        // 5 characters * 12 fontSize * 0.6 = 36
        assertEquals(5 * 12 * 0.6, width);
    }

    @Test
    void estimateTextWidth_scalesWithFontSize() {
        double width12 = XmlCanvasRenderingHelper.estimateTextWidth("Test", 12);
        double width24 = XmlCanvasRenderingHelper.estimateTextWidth("Test", 24);
        assertEquals(width24, width12 * 2);
    }

    @Test
    void truncateText_nullText() {
        String result = XmlCanvasRenderingHelper.truncateText(null, 100);
        assertEquals(null, result);
    }

    @Test
    void truncateText_emptyText() {
        String result = XmlCanvasRenderingHelper.truncateText("", 100);
        assertEquals("", result);
    }

    @Test
    void truncateText_fitsInWidth() {
        String text = "Short";
        String result = XmlCanvasRenderingHelper.truncateText(text, 500);
        assertEquals("Short", result);
    }

    @Test
    void truncateText_exceedsWidth() {
        String text = "This is a very long text";
        String result = XmlCanvasRenderingHelper.truncateText(text, 50);
        assertTrue(result.endsWith("..."));
    }

    @Test
    void colorConstants_areDefined() {
        assertNotNull(XmlCanvasRenderingHelper.COLOR_BACKGROUND);
        assertNotNull(XmlCanvasRenderingHelper.COLOR_TEXT_DEFAULT);
        assertNotNull(XmlCanvasRenderingHelper.COLOR_ELEMENT_TAG);
        assertNotNull(XmlCanvasRenderingHelper.COLOR_ATTRIBUTE_NAME);
        assertNotNull(XmlCanvasRenderingHelper.COLOR_TEXT_CONTENT);
    }

    @Test
    void layoutConstants_areDefined() {
        assertTrue(XmlCanvasRenderingHelper.HEADER_HEIGHT > 0);
        assertTrue(XmlCanvasRenderingHelper.ROW_HEIGHT > 0);
        assertTrue(XmlCanvasRenderingHelper.INDENT > 0);
        assertTrue(XmlCanvasRenderingHelper.MIN_GRID_WIDTH > 0);
    }

    @Test
    void fontSizeConstants_arePositive() {
        assertTrue(XmlCanvasRenderingHelper.FONT_SIZE_HEADER > 0);
        assertTrue(XmlCanvasRenderingHelper.FONT_SIZE_REGULAR > 0);
        assertTrue(XmlCanvasRenderingHelper.FONT_SIZE_SMALL > 0);
    }

    @Test
    void fontSizeOrdering() {
        // Header should be larger than regular, regular should be larger than small
        assertTrue(XmlCanvasRenderingHelper.FONT_SIZE_HEADER > XmlCanvasRenderingHelper.FONT_SIZE_REGULAR);
        assertTrue(XmlCanvasRenderingHelper.FONT_SIZE_REGULAR > XmlCanvasRenderingHelper.FONT_SIZE_SMALL);
    }
}
