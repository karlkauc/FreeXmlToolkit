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
     * A search hit: a row, a cell of the embedded table of a table-owner row, or a closed complex
     * cell whose hidden content matches.
     *
     * @param row         the matching row, or the table owner of a cell hit
     * @param tableRow    the table row of a cell hit, -1 for a row hit
     * @param tableColumn the table column of a cell hit, -1 for a row hit
     * @param hidden      for a closed complex cell: how many nodes inside it match (they become
     *                    hits of their own once the cell is opened); 0 otherwise
     */
    public record Hit(FlatRow row, int tableRow, int tableColumn, int hidden) {

        /** A row hit ({@code tableRow < 0}) or a cell hit. */
        public Hit(FlatRow row, int tableRow, int tableColumn) {
            this(row, tableRow, tableColumn, 0);
        }

        /** @return whether this hit is a table cell */
        public boolean isCell() {
            return tableRow >= 0;
        }

        /** @return whether this hit is a closed cell whose content matches (open it to reach the hits) */
        public boolean isHidden() {
            return hidden > 0;
        }

        /** @return how many matches this hit stands for (1, or the matches inside a closed cell) */
        public int weight() {
            return Math.max(1, hidden);
        }
    }

    /**
     * Like {@link #findMatches} but also searches embedded tables (the members of a repeating group
     * have no rows of their own), in document order: after a matching owner row come its matching
     * cells; an opened complex cell contributes the hits of its grid at its place, a closed one a
     * single {@link Hit#isHidden() hidden} hit when anything inside it matches. Builds the tables
     * of the groups it visits.
     *
     * @param rows       the rows of a grid (the root's, or an opened cell's)
     * @param searchText the text to match; null/blank yields an empty result
     * @return the hits in document order (never null)
     */
    public static List<Hit> findHits(List<FlatRow> rows, String searchText) {
        List<Hit> hits = new ArrayList<>();
        if (rows == null || searchText == null || searchText.isEmpty()) {
            return hits;
        }
        collectHits(rows, searchText.toLowerCase(), hits);
        return hits;
    }

    /** @return the number of matches the hits stand for (a closed cell counts its inner matches) */
    public static int countMatches(List<Hit> hits) {
        int count = 0;
        for (Hit hit : hits) {
            count += hit.weight();
        }
        return count;
    }

    private static void collectHits(List<FlatRow> rows, String needle, List<Hit> hits) {
        for (FlatRow row : rows) {
            if (rowMatches(row, needle)) {
                hits.add(new Hit(row, -1, -1));
            }
            if (row.hasRepeatingTable()) {
                collectTableHits(row, needle, hits);
            }
        }
    }

    private static void collectTableHits(FlatRow owner, String needle, List<Hit> hits) {
        RepeatingElementsTable table = owner.getRepeatingTable();
        if (table == null) {
            return;
        }
        List<RepeatingElementsTable.TableColumn> columns = table.getColumns();
        for (int r = 0; r < table.getRows().size(); r++) {
            RepeatingElementsTable.TableRow tableRow = table.getRows().get(r);
            for (int c = 0; c < columns.size(); c++) {
                String name = columns.get(c).getName();
                boolean suffixMatches = contains(tableRow.getAttributeSuffix(name), needle);
                if (tableRow.hasComplexChild(name)) {
                    GridBlock grid = tableRow.getCellGrid(name);
                    if (grid != null) {
                        // An opened cell: its own rows are searched where they are drawn
                        if (suffixMatches) {
                            hits.add(new Hit(owner, r, c));
                        }
                        collectHits(grid.rows(), needle, hits);
                        continue;
                    }
                    int inside = tableRow.getRecord().countCellMatches(name, needle);
                    if (inside > 0) {
                        hits.add(new Hit(owner, r, c, inside));
                    } else if (suffixMatches || contains(tableRow.getValue(name), needle)) {
                        hits.add(new Hit(owner, r, c));
                    }
                } else if (suffixMatches || contains(tableRow.getValue(name), needle)) {
                    hits.add(new Hit(owner, r, c));
                }
            }
        }
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase().contains(needle);
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
