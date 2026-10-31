package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab-separated text for the exchange between an embedded table and spreadsheet applications
 * (XMLSpy "Copy as Tab-separated Text" / pasting a spreadsheet range): formats a rectangular
 * cell selection (optionally with the header row) as TSV and as an HTML table, and parses pasted
 * text back into a grid of cell values. Values travel unchanged: tabs, line breaks and quotes
 * inside a value are quoted the way spreadsheets read and write them.
 */
public final class TsvClipboard {

    private TsvClipboard() {
    }

    /**
     * Formats the cells of the rectangle {@code [r1..r2] x [c1..c2]} (inclusive, any order) as
     * tab-separated lines. A value holding a tab, a line break or a leading quote is quoted the way
     * spreadsheets write it ({@code "a<tab>b"}, quotes doubled), so it stays one cell, unchanged.
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
        return toTsv(cells(table, r1, c1, r2, c2, withHeader));
    }

    /** @return the whole table as TSV with a header line */
    public static String formatTable(RepeatingElementsTable table) {
        return format(table, 0, 0, table.getRows().size() - 1, table.getColumns().size() - 1, true);
    }

    /**
     * The clipboard content of a cell range for spreadsheets: the TSV text plus an HTML table,
     * which spreadsheet applications prefer — every value lands in its own cell exactly (tabs and
     * line breaks included), and values a spreadsheet would convert (leading zeros, long digit
     * strings, formula-like text) are marked as text.
     *
     * @see #format
     */
    public static javafx.scene.input.ClipboardContent content(RepeatingElementsTable table, int r1, int c1,
                                                              int r2, int c2, boolean withHeader) {
        List<List<String>> cells = cells(table, r1, c1, r2, c2, withHeader);
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(toTsv(cells));
        content.putHtml(toHtml(cells, withHeader));
        return content;
    }

    /** @return the clipboard content of the whole table with a header line */
    public static javafx.scene.input.ClipboardContent tableContent(RepeatingElementsTable table) {
        return content(table, 0, 0, table.getRows().size() - 1, table.getColumns().size() - 1, true);
    }

    /** @return the values of the range, row by row (the header first when asked for) */
    static List<List<String>> cells(RepeatingElementsTable table, int r1, int c1, int r2, int c2, boolean withHeader) {
        int rowFrom = Math.max(0, Math.min(r1, r2));
        int rowTo = Math.min(table.getRows().size() - 1, Math.max(r1, r2));
        int colFrom = Math.max(0, Math.min(c1, c2));
        int colTo = Math.min(table.getColumns().size() - 1, Math.max(c1, c2));
        List<List<String>> cells = new ArrayList<>();
        if (withHeader) {
            List<String> header = new ArrayList<>();
            for (int c = colFrom; c <= colTo; c++) {
                header.add(nullToEmpty(table.getColumn(c).getDisplayName()));
            }
            cells.add(header);
        }
        for (int r = rowFrom; r <= rowTo; r++) {
            RepeatingElementsTable.TableRow row = table.getRows().get(r);
            List<String> line = new ArrayList<>();
            for (int c = colFrom; c <= colTo; c++) {
                line.add(nullToEmpty(row.getValue(table.getColumn(c).getName())));
            }
            cells.add(line);
        }
        return cells;
    }

    static String toTsv(List<List<String>> cells) {
        StringBuilder sb = new StringBuilder();
        for (List<String> line : cells) {
            for (int c = 0; c < line.size(); c++) {
                if (c > 0) {
                    sb.append('\t');
                }
                sb.append(quote(line.get(c)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** @return the value, quoted (quotes doubled) when it holds a tab, a line break or starts with a quote */
    static String quote(String value) {
        boolean needsQuotes = value.startsWith("\"") || value.indexOf('\t') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    static String toHtml(List<List<String>> cells, boolean withHeader) {
        StringBuilder sb = new StringBuilder("<html><head><meta charset=\"utf-8\"></head><body><table>");
        for (int r = 0; r < cells.size(); r++) {
            sb.append("<tr>");
            String tag = withHeader && r == 0 ? "th" : "td";
            for (String value : cells.get(r)) {
                sb.append('<').append(tag);
                if (keepAsText(value)) {
                    sb.append(" style=\"mso-number-format:'\\@'\"");
                }
                sb.append('>').append(escapeHtml(value)).append("</").append(tag).append('>');
            }
            sb.append("</tr>");
        }
        return sb.append("</table></body></html>").toString();
    }

    /** @return whether a spreadsheet would change the value when reading it as a number or formula */
    static boolean keepAsText(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (value.matches("0\\d+") || value.matches("\\d{16,}")) {
            return true; // leading zeros, digits beyond a spreadsheet's precision
        }
        char first = value.charAt(0);
        return first == '=' || first == '@' || first == '+' || (first == '-' && !value.matches("-\\d+([.,]\\d+)?"));
    }

    private static String escapeHtml(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        String text = value.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\n' -> sb.append("<br style=\"mso-data-placement:same-cell\"/>");
                case '\t' -> sb.append("&#9;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
     * ignored; rows may have different lengths. A cell that starts with a quote is read the way
     * spreadsheets write it: up to the closing quote, tabs and line breaks included, {@code ""}
     * standing for one quote.
     *
     * @param text the text
     * @return the cell values, row by row (never {@code null})
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        int i = 0;
        int n = text.length();
        boolean atCellStart = true;
        while (i < n) {
            char ch = text.charAt(i);
            if (atCellStart && ch == '"') {
                // Quoted cell: up to the closing quote; "" is one quote
                i++;
                while (i < n) {
                    char q = text.charAt(i);
                    if (q == '"') {
                        if (i + 1 < n && text.charAt(i + 1) == '"') {
                            cell.append('"');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    cell.append(q);
                    i++;
                }
                atCellStart = false;
                continue;
            }
            atCellStart = false;
            if (ch == '\t') {
                row.add(cell.toString());
                cell.setLength(0);
                atCellStart = true;
                i++;
            } else if (ch == '\r' || ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                atCellStart = true;
                i += ch == '\r' && i + 1 < n && text.charAt(i + 1) == '\n' ? 2 : 1;
            } else {
                cell.append(ch);
                i++;
            }
        }
        if (!atCellStart || !row.isEmpty() || cell.length() > 0) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
