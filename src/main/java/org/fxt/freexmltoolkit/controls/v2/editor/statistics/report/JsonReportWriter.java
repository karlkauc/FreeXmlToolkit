package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fxt.freexmltoolkit.service.ExportMetadataService;

/**
 * JSON rendering, machine-friendly: sections keyed by id, facts as an object, tables as arrays
 * of objects keyed by column header. Row and fact tones are emitted only when set.
 * <pre>
 * { "_metadata": {...}, "title": ..., "subject": ..., "generatedAt": ...,
 *   "sections": { "overview": { "title": ..., "facts": {...}, "tables": { "schema-references":
 *       { "title": ..., "columns": [...], "rows": [ { "Type": "import", ... } ] } }, "note": ... } } }
 * </pre>
 */
public final class JsonReportWriter implements ReportWriter {

    @Override
    public void write(ReportModel model, Path target) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_metadata", metadata());
        root.put("title", model.title());
        root.put("subject", model.subject());
        root.put("generatedAt", model.generatedAt().format(ReportWriters.TIMESTAMP));
        Map<String, Object> sections = new LinkedHashMap<>();
        for (ReportSection section : model.sections()) {
            sections.put(uniqueKey(sections, section.id()), section(section));
        }
        root.put("sections", sections);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            gson.toJson(root, out);
        }
    }

    private static Map<String, Object> section(ReportSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", section.title());
        Map<String, Object> facts = new LinkedHashMap<>();
        for (KeyValue fact : section.facts()) {
            String key = uniqueKey(facts, fact.key());
            if (fact.tone() == Tone.NONE) {
                facts.put(key, fact.value());
            } else {
                Map<String, Object> toned = new LinkedHashMap<>();
                toned.put("value", fact.value());
                toned.put("tone", fact.tone().name());
                facts.put(key, toned);
            }
        }
        out.put("facts", facts);
        Map<String, Object> tables = new LinkedHashMap<>();
        for (ReportTable table : section.tables()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("title", table.title());
            t.put("columns", table.headers());
            List<Object> rows = new ArrayList<>(table.rows().size());
            for (ReportRow row : table.rows()) {
                Map<String, Object> r = new LinkedHashMap<>();
                for (int i = 0; i < table.headers().size(); i++) {
                    r.put(uniqueKey(r, table.headers().get(i)), row.cells().get(i));
                }
                if (row.tone() != Tone.NONE) {
                    r.put("_tone", row.tone().name());
                }
                rows.add(r);
            }
            t.put("rows", rows);
            tables.put(uniqueKey(tables, table.id()), t);
        }
        out.put("tables", tables);
        out.put("note", section.note());
        return out;
    }

    private static String uniqueKey(Map<String, ?> map, String key) {
        String base = key == null || key.isBlank() ? "value" : key;
        String candidate = base;
        int n = 2;
        while (map.containsKey(candidate)) {
            candidate = base + " (" + n++ + ")";
        }
        return candidate;
    }

    private static Map<String, String> metadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        ExportMetadataService service = ReportMetadata.service();
        metadata.put("generator", service.getAppName());
        metadata.put("version", service.getAppVersion());
        if (service.getUserName() != null) {
            metadata.put("author", service.getUserName());
        }
        if (service.getUserCompany() != null) {
            metadata.put("company", service.getUserCompany());
        }
        metadata.put("generatedAt", service.getTimestamp() + "Z");
        return metadata;
    }
}
