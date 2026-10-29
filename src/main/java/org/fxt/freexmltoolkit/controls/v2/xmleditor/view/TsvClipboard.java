package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab-separated text for the exchange between an embedded table and spreadsheet applications
 * (XMLSpy "Copy as Tab-separated Text" / pasting a spreadsheet range): formats a rectangular
 * cell selection (optionally with the header row) and parses pasted text back into a grid of
 * cell values.
 */
public final class TsvClipboard {

    private TsvClipboard() {
    }

    /**
     * Formats the cells of the rectangle {@code [r1..r2] x [c1..c2]} (inclusive, any order) as
     * tab-separated lines. Tabs and line breaks inside a value become spaces.
     *
     * @param table      the table
     * @param r1         first row
     * @param c1         first column
     * @param r2         last row
     * @param c2         last column
     * @param withHeader whether to prepend the column names as the first line
     * @return the TSV text (lines end with {@code \n})
     */
    public static String format(RepeatingElementsTable table, int r1, int c1, int r2, int c2, boolean withHeader) {
        int rowFrom = Math.max(0, Math.min(r1, r2));
        int rowTo = Math.min(table.getRows().size() - 1, Math.max(r1, r2));
        int colFrom = Math.max(0, Math.min(c1, c2));
        int colTo = Math.min(table.getColumns().size() - 1, Math.max(c1, c2));
        StringBuilder sb = new StringBuilder();
        if (withHeader) {
            for (int c = colFrom; c <= colTo; c++) {
                if (c > colFrom) {
                    sb.append('\t');
                }
                sb.append(clean(table.getColumn(c).getDisplayName()));
            }
            sb.append('\n');
        }
        for (int r = rowFrom; r <= rowTo; r++) {
            RepeatingElementsTable.TableRow row = table.getRows().get(r);
            for (int c = colFrom; c <= colTo; c++) {
                if (c > colFrom) {
                    sb.append('\t');
                }
                sb.append(clean(row.getValue(table.getColumn(c).getName())));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** @return the whole table as TSV with a header line */
    public static String formatTable(RepeatingElementsTable table) {
        return format(table, 0, 0, table.getRows().size() - 1, table.getColumns().size() - 1, true);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * @param text pasted text
     * @return whether the text spans more than one cell (contains a tab or a line break
     *         between lines), i.e. is a block to paste rather than one value
     */
    public static boolean isBlock(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.replaceAll("[\\r\\n]+$", "");
        return trimmed.indexOf('\t') >= 0 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0;
    }

    /**
     * Parses tab-separated text into rows of cell values. A single trailing line break is
     * ignored; rows may have different lengths.
     *
     * @param text the text
     * @return the cell values, row by row (never {@code null})
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        for (String line : normalized.split("\n", -1)) {
            List<String> cells = new ArrayList<>();
            for (String cell : line.split("\t", -1)) {
                cells.add(cell);
            }
            rows.add(cells);
        }
        return rows;
    }
}
