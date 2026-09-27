package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.util.List;

/**
 * A table inside a report section.
 *
 * @param id      stable identifier (JSON key, HTML anchor), kebab-case
 * @param title   heading shown above the table
 * @param headers column headers
 * @param rows    the rows; each row has {@code headers.size()} cells
 */
public record ReportTable(String id, String title, List<String> headers, List<ReportRow> rows) {

    public ReportTable {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : List.copyOf(rows);
        for (ReportRow row : rows) {
            if (row.cells().size() != headers.size()) {
                throw new IllegalArgumentException("Row has " + row.cells().size() + " cells but table '" + id
                        + "' has " + headers.size() + " columns");
            }
        }
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
