package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.util.List;

/**
 * A chapter of a report: a few facts (key/value), any number of tables and an optional note.
 *
 * @param id     stable identifier (JSON key, HTML anchor, Excel sheet base name), kebab-case
 * @param title  chapter heading
 * @param facts  key/value facts shown first
 * @param tables tables shown after the facts
 * @param note   free text shown last (may be {@code null})
 */
public record ReportSection(String id, String title, List<KeyValue> facts, List<ReportTable> tables, String note) {

    public ReportSection {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        facts = facts == null ? List.of() : List.copyOf(facts);
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
