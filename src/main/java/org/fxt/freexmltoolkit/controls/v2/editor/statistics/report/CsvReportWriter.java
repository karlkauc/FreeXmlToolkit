package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CSV rendering: one block per section, headed by a {@code # Section} comment line. Facts form
 * a {@code Key,Value} block; every table is its own block with its header row. Blocks are
 * separated by an empty line, and cells are RFC 4180 quoted when they contain a comma, quote
 * or line break — spreadsheet tools open the file directly, scripts split on the blank lines.
 */
public final class CsvReportWriter implements ReportWriter {

    @Override
    public void write(ReportModel model, Path target) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            out.write("# " + model.title());
            out.newLine();
            if (!model.subject().isBlank()) {
                out.write("# Subject: " + model.subject());
                out.newLine();
            }
            out.write("# Generated: " + model.generatedAt().format(ReportWriters.TIMESTAMP));
            out.newLine();
            for (ReportSection section : model.sections()) {
                out.newLine();
                out.write("## " + section.title());
                out.newLine();
                if (!section.facts().isEmpty()) {
                    out.write("Key,Value");
                    out.newLine();
                    for (KeyValue fact : section.facts()) {
                        out.write(escape(fact.key()) + "," + escape(fact.value()));
                        out.newLine();
                    }
                }
                for (ReportTable table : section.tables()) {
                    out.newLine();
                    out.write("### " + table.title());
                    out.newLine();
                    out.write(join(table.headers()));
                    out.newLine();
                    for (ReportRow row : table.rows()) {
                        out.write(join(row.cells()));
                        out.newLine();
                    }
                }
                if (section.note() != null && !section.note().isBlank()) {
                    out.write("# " + section.note().replace("\n", " "));
                    out.newLine();
                }
            }
        }
    }

    private static String join(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.toString();
    }

    /** RFC 4180 quoting. */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0 || value.startsWith("#")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
