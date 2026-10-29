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

import java.util.ArrayList;
import java.util.List;

/**
 * Pure (UI-free) search helper for the graphical canvas view.
 *
 * <p>Kept separate from {@link XmlCanvasView} so the match-finding logic can be
 * unit-tested without a JavaFX runtime.</p>
 */
public final class XmlCanvasSearch {

    private XmlCanvasSearch() {
        // Utility class
    }

    /**
     * Returns, in document order, all rows whose label or value contains the
     * search text (case-insensitively).
     *
     * @param rows       the complete flat row list (typically {@code allRows})
     * @param searchText the text to match; null/blank yields an empty result
     * @return matching rows in their original order (never null)
     */
    public static List<FlatRow> findMatches(List<FlatRow> rows, String searchText) {
        List<FlatRow> matches = new ArrayList<>();
        if (rows == null || searchText == null || searchText.isEmpty()) {
            return matches;
        }
        String needle = searchText.toLowerCase();
        for (FlatRow row : rows) {
            if (rowMatches(row, needle)) {
                matches.add(row);
            }
        }
        return matches;
    }

    /**
     * A search hit: a row, or a cell of the embedded table of a table-owner row.
     *
     * @param row         the matching row, or the table owner of a cell hit
     * @param tableRow    the table row of a cell hit, -1 for a row hit
     * @param tableColumn the table column of a cell hit, -1 for a row hit
     */
    public record Hit(FlatRow row, int tableRow, int tableColumn) {
        /** @return whether this hit is a table cell */
        public boolean isCell() {
            return tableRow >= 0;
        }
    }

    /**
     * Like {@link #findMatches} but also searches the cells of embedded tables (the members of
     * a repeating group have no rows of their own): every matching cell is a hit, after the
     * owner row itself when that matches. Builds the tables of matching groups.
     *
     * @param rows       the complete flat row list
     * @param searchText the text to match; null/blank yields an empty result
     * @return the hits in document order (never null)
     */
    public static List<Hit> findHits(List<FlatRow> rows, String searchText) {
        List<Hit> hits = new ArrayList<>();
        if (rows == null || searchText == null || searchText.isEmpty()) {
            return hits;
        }
        String needle = searchText.toLowerCase();
        for (FlatRow row : rows) {
            if (rowMatches(row, needle)) {
                hits.add(new Hit(row, -1, -1));
            }
            if (row.hasRepeatingTable()) {
                RepeatingElementsTable table = row.getRepeatingTable();
                List<RepeatingElementsTable.TableColumn> columns = table.getColumns();
                for (int r = 0; r < table.getRows().size(); r++) {
                    RepeatingElementsTable.TableRow tableRow = table.getRows().get(r);
                    for (int c = 0; c < columns.size(); c++) {
                        String value = tableRow.getValue(columns.get(c).getName());
                        if (value != null && value.toLowerCase().contains(needle)) {
                            hits.add(new Hit(row, r, c));
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * Tests whether a single row matches an already-lowercased needle.
     *
     * @param row    the row to test
     * @param needle the lowercased search text (must not be null/empty)
     * @return true if the row's label or value contains the needle
     */
    private static boolean rowMatches(FlatRow row, String needle) {
        if (row == null) {
            return false;
        }
        String label = row.getLabel();
        if (label != null && label.toLowerCase().contains(needle)) {
            return true;
        }
        String value = row.getValue();
        return value != null && value.toLowerCase().contains(needle);
    }
}
