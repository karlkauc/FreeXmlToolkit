package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.util.ArrayList;
import java.util.List;

/** One table row: cells in header order (nulls become empty strings) plus an optional tone. */
public record ReportRow(List<String> cells, Tone tone) {

    public ReportRow {
        List<String> copy = new ArrayList<>(cells == null ? 0 : cells.size());
        if (cells != null) {
            for (String cell : cells) {
                copy.add(cell == null ? "" : cell);
            }
        }
        cells = List.copyOf(copy);
        tone = tone == null ? Tone.NONE : tone;
    }

    public static ReportRow of(Object... cells) {
        return withTone(Tone.NONE, cells);
    }

    public static ReportRow withTone(Tone tone, Object... cells) {
        List<String> values = new ArrayList<>(cells.length);
        for (Object cell : cells) {
            values.add(cell == null ? "" : String.valueOf(cell));
        }
        return new ReportRow(values, tone);
    }
}
