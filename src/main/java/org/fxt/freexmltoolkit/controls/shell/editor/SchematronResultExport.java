package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;

/**
 * UI-free CSV / JSON export of Schematron rule-check results, ported from the
 * retired legacy {@code SchematronController} report export and adapted to the
 * shell's result model ({@link SchematronError}: severity, type, line, column,
 * message). The CSV/JSON escaping mirrors the legacy behaviour exactly.
 */
public final class SchematronResultExport {

    private static final String CSV_HEADER = "Severity,Type,Line,Column,Message";

    private SchematronResultExport() {
    }

    /** @return a CSV document (header row plus one escaped row per issue). */
    public static String toCsv(List<SchematronError> issues) {
        StringBuilder csv = new StringBuilder(CSV_HEADER).append("\n");
        if (issues != null) {
            for (SchematronError e : issues) {
                csv.append(e.severity().name()).append(",")
                        .append(e.type().name()).append(",")
                        .append(e.line()).append(",")
                        .append(e.column()).append(",")
                        .append(escapeCsv(e.message())).append("\n");
            }
        }
        return csv.toString();
    }

    /** @return a JSON array with one object per issue (numbers unquoted, strings escaped). */
    public static String toJson(List<SchematronError> issues) {
        StringBuilder json = new StringBuilder("[\n");
        if (issues != null) {
            for (int i = 0; i < issues.size(); i++) {
                SchematronError e = issues.get(i);
                json.append("  {\n")
                        .append("    \"severity\": \"").append(escapeJson(e.severity().name())).append("\",\n")
                        .append("    \"type\": \"").append(escapeJson(e.type().name())).append("\",\n")
                        .append("    \"line\": ").append(e.line()).append(",\n")
                        .append("    \"column\": ").append(e.column()).append(",\n")
                        .append("    \"message\": \"").append(escapeJson(e.message())).append("\"\n")
                        .append("  }").append(i < issues.size() - 1 ? "," : "").append("\n");
            }
        }
        json.append("]\n");
        return json.toString();
    }

    /** Escapes a CSV value (wrap + double inner quotes when it contains comma/quote/newline). */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Escapes a JSON string value (backslash, quote, control whitespace). */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
