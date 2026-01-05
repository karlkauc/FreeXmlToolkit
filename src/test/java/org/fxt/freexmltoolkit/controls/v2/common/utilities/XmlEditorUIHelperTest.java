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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for XmlEditorUIHelper utility class.
 * Tests text formatting, HTML stripping, and display utilities.
 */
class XmlEditorUIHelperTest {

    @Test
    void stripHtmlTags_withSimpleHtmlTags() {
        String html = "<p>Hello <b>world</b></p>";
        String result = XmlEditorUIHelper.stripHtmlTags(html);
        assertEquals("Hello world", result);
    }

    @Test
    void stripHtmlTags_withHtmlEntities() {
        String html = "<div>Price: &lt;100&amp;gt;</div>";
        String result = XmlEditorUIHelper.stripHtmlTags(html);
        // After removing tags: "Price: &lt;100&amp;gt;"
        // After replacing &amp; with &: "Price: &lt;100&gt;"
        // After replacing &lt; with <: "Price: <100&gt;"
        // After replacing &gt; with >: "Price: <100>"
        assertEquals("Price: <100>", result);
    }

    @Test
    void stripHtmlTags_withNonBreakingSpace() {
        String html = "<p>Word1&nbsp;Word2</p>";
        String result = XmlEditorUIHelper.stripHtmlTags(html);
        assertEquals("Word1 Word2", result);
    }

    @Test
    void stripHtmlTags_withComplexHtml() {
        String html = "<html><body><p>Test &lt;tag&gt; &amp; more</p></body></html>";
        String result = XmlEditorUIHelper.stripHtmlTags(html);
        assertEquals("Test <tag> & more", result);
    }

    @Test
    void stripHtmlTags_withNull() {
        String result = XmlEditorUIHelper.stripHtmlTags(null);
        assertEquals("", result);
    }

    @Test
    void stripHtmlTags_withEmptyString() {
        String result = XmlEditorUIHelper.stripHtmlTags("");
        assertEquals("", result);
    }

    @Test
    void stripHtmlTags_withWhitespaceOnly() {
        String html = "   <p>   </p>   ";
        String result = XmlEditorUIHelper.stripHtmlTags(html);
        assertEquals("", result);
    }

    @Test
    void stripHtmlTags_trimsLeadingTrailingWhitespace() {
        String html = "  <p>Content</p>  ";
        String result = XmlEditorUIHelper.stripHtmlTags(html);
        assertEquals("Content", result);
    }

    @Test
    void truncateText_withinMaxLength() {
        String text = "Hello";
        String result = XmlEditorUIHelper.truncateText(text, 10);
        assertEquals("Hello", result);
    }

    @Test
    void truncateText_exceedsMaxLength() {
        String text = "Hello World";
        String result = XmlEditorUIHelper.truncateText(text, 8);
        assertEquals("Hello...", result);
    }

    @Test
    void truncateText_exactMaxLength() {
        String text = "Hello";
        String result = XmlEditorUIHelper.truncateText(text, 5);
        assertEquals("Hello", result);
    }

    @Test
    void truncateText_withNull() {
        String result = XmlEditorUIHelper.truncateText(null, 5);
        assertEquals("", result);
    }

    @Test
    void truncateText_verySmallMaxLength() {
        String text = "Hello World";
        String result = XmlEditorUIHelper.truncateText(text, 3);
        assertEquals("...", result);
    }

    @Test
    void truncateText_verySmallMaxLengthThrowsException() {
        // When maxLength < 3, substring(0, negative) throws exception
        // This is an edge case in the implementation
        String text = "Hello";
        assertThrows(StringIndexOutOfBoundsException.class,
            () -> XmlEditorUIHelper.truncateText(text, 1));
    }

    @Test
    void formatChildElementsForDisplay_emptyList() {
        List<String> children = List.of();
        List<String> result = XmlEditorUIHelper.formatChildElementsForDisplay(children, true);
        assertTrue(result.isEmpty());
    }

    @Test
    void formatChildElementsForDisplay_withContainerMarkers() {
        List<String> children = List.of("SEQUENCE_container", "element1", "CHOICE_container");
        List<String> result = XmlEditorUIHelper.formatChildElementsForDisplay(children, true);
        assertEquals(1, result.size());
        assertEquals("element1", result.get(0));
    }

    @Test
    void formatChildElementsForDisplay_withAllMarker() {
        List<String> children = List.of("ALL_container", "item1", "item2");
        List<String> result = XmlEditorUIHelper.formatChildElementsForDisplay(children, true);
        assertEquals(2, result.size());
        assertTrue(result.contains("item1"));
        assertTrue(result.contains("item2"));
    }

    @Test
    void formatChildElementsForDisplay_keepContainerMarkers() {
        List<String> children = List.of("SEQUENCE_container", "element1");
        List<String> result = XmlEditorUIHelper.formatChildElementsForDisplay(children, false);
        assertEquals(2, result.size());
        assertTrue(result.contains("SEQUENCE_container"));
        assertTrue(result.contains("element1"));
    }

    @Test
    void extractElementNameFromXPath_simpleXPath() {
        String xpath = "/root/parent/element";
        String result = XmlEditorUIHelper.extractElementNameFromXPath(xpath);
        assertEquals("element", result);
    }

    @Test
    void extractElementNameFromXPath_rootOnly() {
        String xpath = "/root";
        String result = XmlEditorUIHelper.extractElementNameFromXPath(xpath);
        assertEquals("root", result);
    }

    @Test
    void extractElementNameFromXPath_withNull() {
        String result = XmlEditorUIHelper.extractElementNameFromXPath(null);
        assertNull(result);
    }

    @Test
    void extractElementNameFromXPath_withEmptyString() {
        String result = XmlEditorUIHelper.extractElementNameFromXPath("");
        assertNull(result);
    }

    @Test
    void extractElementNameFromXPath_slashOnly() {
        String result = XmlEditorUIHelper.extractElementNameFromXPath("/");
        assertNull(result);
    }

    @Test
    void extractElementNameFromXPath_withValidPathElements() {
        // Test various valid XPath expressions
        assertEquals("z", XmlEditorUIHelper.extractElementNameFromXPath("/a/b/c/z"));
        assertEquals("element", XmlEditorUIHelper.extractElementNameFromXPath("/root/element"));
    }

    @Test
    void isValidXPath_withValidXPath() {
        assertTrue(XmlEditorUIHelper.isValidXPath("/root/element"));
        assertTrue(XmlEditorUIHelper.isValidXPath("/item"));
        assertTrue(XmlEditorUIHelper.isValidXPath("/a/b/c"));
    }

    @Test
    void isValidXPath_withErrorMessages() {
        assertFalse(XmlEditorUIHelper.isValidXPath("Invalid XML structure"));
        assertFalse(XmlEditorUIHelper.isValidXPath("No XML content"));
        assertFalse(XmlEditorUIHelper.isValidXPath("Unable to determine XPath"));
    }

    @Test
    void isValidXPath_withNull() {
        assertFalse(XmlEditorUIHelper.isValidXPath(null));
    }

    @Test
    void isValidXPath_withEmptyString() {
        assertFalse(XmlEditorUIHelper.isValidXPath(""));
    }

    @Test
    void tagMatchRecord_properties() {
        XmlEditorUIHelper.TagMatch match = new XmlEditorUIHelper.TagMatch(5, "element", XmlEditorUIHelper.TagType.OPEN);
        assertEquals(5, match.position());
        assertEquals("element", match.name());
        assertEquals(XmlEditorUIHelper.TagType.OPEN, match.type());
    }

    @Test
    void tagTypeEnum_hasAllValues() {
        assertTrue(XmlEditorUIHelper.TagType.OPEN.name().equals("OPEN"));
        assertTrue(XmlEditorUIHelper.TagType.CLOSE.name().equals("CLOSE"));
        assertTrue(XmlEditorUIHelper.TagType.SELF_CLOSING.name().equals("SELF_CLOSING"));
    }
}
