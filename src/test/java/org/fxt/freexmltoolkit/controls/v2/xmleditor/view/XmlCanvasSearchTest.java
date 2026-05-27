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

package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow.RowType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure (UI-free) canvas search helper {@link XmlCanvasSearch}.
 */
class XmlCanvasSearchTest {

    private static FlatRow row(RowType type, String label, String value) {
        return new FlatRow(type, 0, null, null, label, value, 0);
    }

    @Test
    void matchesElementLabelCaseInsensitively() {
        FlatRow book = row(RowType.ELEMENT, "Book", null);
        FlatRow title = row(RowType.ELEMENT, "title", "XML Guide");
        List<FlatRow> rows = List.of(book, title);

        List<FlatRow> matches = XmlCanvasSearch.findMatches(rows, "book");

        assertEquals(1, matches.size());
        assertSame(book, matches.get(0));
    }

    @Test
    void matchesValueAndPreservesDocumentOrder() {
        FlatRow a = row(RowType.ELEMENT, "author", "Jane Guidesmith");
        FlatRow b = row(RowType.ELEMENT, "title", "XML Guide");
        FlatRow c = row(RowType.ATTRIBUTE, "lang", "en");
        List<FlatRow> rows = List.of(a, b, c);

        // "guide" appears in a's value ("Guidesmith") and b's value ("XML Guide")
        List<FlatRow> matches = XmlCanvasSearch.findMatches(rows, "guide");

        assertEquals(2, matches.size());
        assertSame(a, matches.get(0));
        assertSame(b, matches.get(1));
    }

    @Test
    void matchesAttributeNameAndValue() {
        FlatRow attr = row(RowType.ATTRIBUTE, "id", "123");
        List<FlatRow> rows = List.of(attr);

        assertEquals(1, XmlCanvasSearch.findMatches(rows, "id").size());
        assertEquals(1, XmlCanvasSearch.findMatches(rows, "123").size());
    }

    @Test
    void noMatchReturnsEmptyList() {
        List<FlatRow> rows = List.of(row(RowType.ELEMENT, "book", "content"));
        assertTrue(XmlCanvasSearch.findMatches(rows, "missing").isEmpty());
    }

    @Test
    void emptyOrNullInputsReturnEmptyList() {
        List<FlatRow> rows = List.of(row(RowType.ELEMENT, "book", null));
        assertTrue(XmlCanvasSearch.findMatches(rows, "").isEmpty());
        assertTrue(XmlCanvasSearch.findMatches(rows, null).isEmpty());
        assertTrue(XmlCanvasSearch.findMatches(null, "book").isEmpty());
    }

    @Test
    void ignoresRowsWithNullLabelAndValue() {
        FlatRow comment = row(RowType.COMMENT, null, null);
        FlatRow match = row(RowType.ELEMENT, "node", null);
        List<FlatRow> rows = List.of(comment, match);

        List<FlatRow> matches = XmlCanvasSearch.findMatches(rows, "node");

        assertEquals(1, matches.size());
        assertSame(match, matches.get(0));
    }
}
