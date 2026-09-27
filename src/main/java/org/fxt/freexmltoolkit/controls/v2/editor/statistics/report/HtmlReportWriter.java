package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-contained HTML rendering (inline CSS, no scripts): title block, a table of contents,
 * then one chapter per section with a facts table, the data tables and the note. Tones colour
 * table rows and fact values.
 */
public final class HtmlReportWriter implements ReportWriter {

    @Override
    public void write(ReportModel model, Path target) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n");
            out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            out.write(ReportMetadata.service().generateHtmlMetaTags());
            out.write("  <title>" + escape(model.title()) + "</title>\n  <style>\n" + STYLES + "  </style>\n</head>\n<body>\n");
            out.write("<div class=\"container\">\n");
            out.write("  <h1>" + escape(model.title()) + "</h1>\n");
            if (!model.subject().isBlank()) {
                out.write("  <p class=\"subject\">" + escape(model.subject()) + "</p>\n");
            }
            out.write("  <p class=\"timestamp\">Generated: " + escape(model.generatedAt().format(ReportWriters.TIMESTAMP)) + "</p>\n");
            if (model.sections().size() > 1) {
                out.write("  <nav class=\"toc\"><ol>\n");
                for (ReportSection section : model.sections()) {
                    out.write("    <li><a href=\"#" + escape(section.id()) + "\">" + escape(section.title()) + "</a></li>\n");
                }
                out.write("  </ol></nav>\n");
            }
            for (ReportSection section : model.sections()) {
                writeSection(out, section);
            }
            out.write("</div>\n</body>\n</html>\n");
        }
    }

    private static void writeSection(BufferedWriter out, ReportSection section) throws IOException {
        out.write("  <section class=\"section\" id=\"" + escape(section.id()) + "\">\n");
        out.write("    <h2>" + escape(section.title()) + "</h2>\n");
        if (!section.facts().isEmpty()) {
            out.write("    <table class=\"facts\">\n");
            for (KeyValue fact : section.facts()) {
                String style = fact.tone() == Tone.NONE ? "" : " style=\"color:" + fact.tone().foreground() + ";font-weight:600\"";
                out.write("      <tr><th>" + escape(fact.key()) + "</th><td" + style + ">" + escape(fact.value()) + "</td></tr>\n");
            }
            out.write("    </table>\n");
        }
        for (ReportTable table : section.tables()) {
            out.write("    <h3 id=\"" + escape(section.id() + "-" + table.id()) + "\">" + escape(table.title()) + "</h3>\n");
            if (table.isEmpty()) {
                out.write("    <p class=\"empty\">(none)</p>\n");
                continue;
            }
            out.write("    <table class=\"data\">\n      <thead><tr>");
            for (String header : table.headers()) {
                out.write("<th>" + escape(header) + "</th>");
            }
            out.write("</tr></thead>\n      <tbody>\n");
            for (ReportRow row : table.rows()) {
                String style = row.tone() == Tone.NONE ? "" : " style=\"background:" + row.tone().background() + "\"";
                out.write("        <tr" + style + ">");
                for (String cell : row.cells()) {
                    out.write("<td>" + escape(cell) + "</td>");
                }
                out.write("</tr>\n");
            }
            out.write("      </tbody>\n    </table>\n");
        }
        if (section.note() != null && !section.note().isBlank()) {
            out.write("    <p class=\"note\">" + escape(section.note()) + "</p>\n");
        }
        out.write("  </section>\n");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String STYLES = """
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Ubuntu, sans-serif;
                       line-height: 1.5; color: #333; background: #f5f5f5; margin: 0; padding: 20px; }
                .container { max-width: 1100px; margin: 0 auto; background: #fff; padding: 30px;
                             border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,.1); }
                h1 { color: #2c3e50; border-bottom: 3px solid #1373D9; padding-bottom: 10px; margin-bottom: 4px; }
                h2 { color: #34495e; margin-top: 32px; border-bottom: 1px solid #bdc3c7; padding-bottom: 5px; }
                h3 { color: #34495e; margin: 18px 0 6px; font-size: 1.05em; }
                .subject { margin: 0; color: #555; }
                .timestamp { color: #7f8c8d; font-size: .9em; margin-top: 0; }
                .toc ol { columns: 2; margin: 16px 0; padding-left: 20px; }
                .toc a { color: #1373D9; text-decoration: none; }
                table { border-collapse: collapse; width: 100%; margin: 6px 0 12px; font-size: .92em; }
                table.facts th { text-align: left; width: 34%; color: #555; font-weight: 500; }
                th, td { border: 1px solid #e0e0e0; padding: 6px 8px; vertical-align: top; text-align: left; }
                table.data thead th { background: #ecf0f1; }
                table.data tbody tr:nth-child(even) { background: #fafafa; }
                td { word-break: break-word; }
                .empty { color: #999; font-style: italic; margin: 4px 0 12px; }
                .note { color: #666; font-size: .9em; }
            """;
}
