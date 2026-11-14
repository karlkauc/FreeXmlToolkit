/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The labels of the FMA taxonomy: template and table names, row and column codes with their name,
 * explanation and domain, and the values of the code lists (domains such as {@code PK_JN}). Read
 * from the taxonomy's <em>Positionsbeschreibung</em> and <em>Dictionary</em> workbooks and cached as
 * {@code labels.json} in the taxonomy folder, so the workbooks are read once per version.
 */
public final class PkDictionary {

    private static final Logger logger = LogManager.getLogger(PkDictionary.class);

    /** Name of the cache file in a taxonomy folder. */
    static final String CACHE_FILE = "labels.json";
    /** Bumped when the cache's content changes. */
    static final int CACHE_FORMAT = 1;

    private static final Pattern TEMPLATE_SHEET = Pattern.compile("PK\\.\\d{3}\\.\\d{2}");
    private static final Pattern DOMAIN_SHEET = Pattern.compile("P[KF]_[A-Z]{2}");
    private static final Pattern DOMAIN_SUFFIX = Pattern.compile("_\\d+$");

    /**
     * A row or column code.
     *
     * @param code        {@code R800-110}, {@code C0010PK}
     * @param name        the FMA's name of it
     * @param explanation the FMA's explanation (may be empty)
     * @param domain      the domain ({@code PK_JN_1}, {@code xs:decimal}), may be empty
     * @param mandatory   the reporting obligation ({@code Ja}, {@code Sofern zutreffend}), may be empty
     */
    public record Code(String code, String name, String explanation, String domain, String mandatory) {
    }

    /** The content of the cache file. */
    static final class Data {
        int format = CACHE_FORMAT;
        Map<String, String> templates = new LinkedHashMap<>();
        Map<String, Code> rows = new LinkedHashMap<>();
        Map<String, Code> rowColumns = new LinkedHashMap<>();
        Map<String, Code> columns = new LinkedHashMap<>();
        Map<String, Map<String, String>> domains = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Map<Path, PkDictionary> CACHE = new HashMap<>();

    private final Data data;

    private PkDictionary(Data data) {
        this.data = data;
    }

    /**
     * @return the dictionary of a taxonomy folder: from its cache file, else read from the
     *         workbooks (and cached); an empty one when neither is there
     */
    public static synchronized PkDictionary of(Path taxonomyDir) {
        return CACHE.computeIfAbsent(taxonomyDir.toAbsolutePath().normalize(), PkDictionary::load);
    }

    /** Drops a loaded dictionary (after its taxonomy was imported again). */
    static synchronized void forget(Path taxonomyDir) {
        CACHE.remove(taxonomyDir.toAbsolutePath().normalize());
    }

    /** @return the dictionary if it is loaded already, else {@code null} (never reads) */
    static synchronized PkDictionary ifLoaded(Path taxonomyDir) {
        return CACHE.get(taxonomyDir.toAbsolutePath().normalize());
    }

    private static final java.util.Set<Path> LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * The dictionary of the taxonomy in use without waiting: returns it when loaded, else starts
     * loading it on a background thread and returns {@code null} (callers on the JavaFX thread
     * show nothing until it is there).
     */
    public static PkDictionary active(PkTaxonomy taxonomy) {
        Path dir = taxonomy.dir(taxonomy.activeVersion()).toAbsolutePath().normalize();
        PkDictionary loaded = ifLoaded(dir);
        if (loaded == null && LOADING.add(dir)) {
            Thread.ofVirtual().name("pk-dictionary").start(() -> {
                try {
                    of(dir);
                } finally {
                    LOADING.remove(dir);
                }
            });
        }
        return loaded;
    }

    private static PkDictionary load(Path dir) {
        Path cache = dir.resolve(CACHE_FILE);
        if (Files.isRegularFile(cache)) {
            try (Reader in = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
                Data data = GSON.fromJson(in, Data.class);
                if (data != null && data.format == CACHE_FORMAT && !data.rows.isEmpty()) {
                    return new PkDictionary(data);
                }
            } catch (IOException | RuntimeException e) {
                logger.debug("PK label cache unreadable, reading the workbooks: {}", e.getMessage());
            }
        }
        Data data = new Data();
        Path positions = firstWorkbook(dir, "positionsbeschreibung");
        Path dictionary = firstWorkbook(dir, "dictionary");
        try {
            if (positions != null) {
                readPositions(positions, data);
            }
            if (dictionary != null) {
                readDomains(dictionary, data);
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("PK workbooks unreadable in {}: {}", dir, e.getMessage());
        }
        if (!data.rows.isEmpty()) {
            try (Writer out = Files.newBufferedWriter(cache, StandardCharsets.UTF_8)) {
                GSON.toJson(data, out);
            } catch (IOException e) {
                logger.debug("PK label cache not written: {}", e.getMessage());
            }
        }
        return new PkDictionary(data);
    }

    private static Path firstWorkbook(Path dir, String word) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var files = Files.list(dir)) {
            return files.filter(f -> {
                String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".xlsx") && name.contains(word);
            }).sorted().findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads templates, rows and columns from the Positionsbeschreibung workbook. */
    static void readPositions(Path workbook, Data data) throws IOException {
        DataFormatter format = new DataFormatter(Locale.GERMANY);
        try (InputStream in = Files.newInputStream(workbook); Workbook book = WorkbookFactory.create(in)) {
            for (Sheet sheet : book) {
                String title = sheet.getSheetName().strip();
                if (title.equals("Templates - Allgemeines")) {
                    readTemplates(sheet, format, data);
                } else if (TEMPLATE_SHEET.matcher(title).matches()) {
                    readTemplateSheet(sheet, format, data);
                }
            }
        }
    }

    private static void readTemplates(Sheet sheet, DataFormatter format, Data data) {
        Map<String, Integer> header = header(sheet, format);
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            String template = text(row, header.get("template"), format);
            String templateName = text(row, header.get("template - bezeichnung"), format);
            String table = text(row, header.get("table"), format);
            String tableName = text(row, header.get("table - bezeichnung"), format);
            if (!template.isEmpty() && !templateName.isEmpty()) {
                data.templates.putIfAbsent(template, templateName);
            }
            if (!table.isEmpty() && !tableName.isEmpty()) {
                data.templates.putIfAbsent(table, tableName);
            }
        }
    }

    private static void readTemplateSheet(Sheet sheet, DataFormatter format, Data data) {
        Map<String, Integer> header = header(sheet, format);
        Integer rowCol = header.get("row code");
        Integer rowName = firstOf(header, "row name (adaptiert)", "row name");
        Integer colCol = header.get("column code");
        Integer colName = firstOf(header, "column name (adaptiert)", "column name");
        Integer explanation = header.get("erläuterung");
        Integer domain = header.get("domain code");
        Integer mandatory = header.get("meldepflicht");
        Integer table = header.get("table");
        if (colCol == null) {
            return;
        }
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            String template = text(row, header.get("template"), format);
            String column = text(row, colCol, format);
            if (template.isEmpty() || column.isEmpty()) {
                continue;
            }
            String rowCode = text(row, rowCol, format);
            String expl = text(row, explanation, format);
            String dom = text(row, domain, format);
            String must = text(row, mandatory, format);
            if (!rowCode.isEmpty()) {
                data.rows.putIfAbsent(rowCode, new Code(rowCode, text(row, rowName, format), expl, dom, must));
                data.rowColumns.putIfAbsent(template + "|" + column, new Code(column, text(row, colName, format), "", "", ""));
            } else {
                String scope = table != null && !text(row, table, format).isEmpty() ? text(row, table, format) : template;
                data.columns.putIfAbsent(scope + "|" + column, new Code(column, text(row, colName, format), expl, dom, must));
            }
        }
    }

    /** Reads the code lists (sheets {@code PK_xx} / {@code PF_xx}: label, name) of the Dictionary workbook. */
    static void readDomains(Path workbook, Data data) throws IOException {
        DataFormatter format = new DataFormatter(Locale.GERMANY);
        try (InputStream in = Files.newInputStream(workbook); Workbook book = WorkbookFactory.create(in)) {
            for (Sheet sheet : book) {
                String title = sheet.getSheetName().strip();
                if (!DOMAIN_SHEET.matcher(title).matches()) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) {
                        continue;
                    }
                    String label = text(row, 0, format);
                    String name = text(row, 1, format);
                    if (!name.isEmpty() && !label.isEmpty()) {
                        values.putIfAbsent(name, label);
                    }
                }
                if (!values.isEmpty()) {
                    data.domains.put(title, values);
                }
            }
        }
    }

    private static Map<String, Integer> header(Sheet sheet, DataFormatter format) {
        Map<String, Integer> header = new HashMap<>();
        Row first = sheet.getRow(0);
        if (first != null) {
            for (Cell cell : first) {
                String name = format.formatCellValue(cell).strip().toLowerCase(Locale.ROOT);
                if (!name.isEmpty()) {
                    header.putIfAbsent(name, cell.getColumnIndex());
                }
            }
        }
        return header;
    }

    private static Integer firstOf(Map<String, Integer> header, String... names) {
        for (String name : names) {
            if (header.containsKey(name)) {
                return header.get(name);
            }
        }
        return null;
    }

    private static String text(Row row, Integer column, DataFormatter format) {
        if (column == null) {
            return "";
        }
        Cell cell = row.getCell(column);
        return cell == null ? "" : format.formatCellValue(cell).strip();
    }

    /** @return the name of a template or table ({@code PK.800.01}, {@code PK.006.02.01.01}), or {@code null} */
    public String template(String code) {
        return data.templates.get(code);
    }

    /** @return a row code of a closed template ({@code R800-110}), or {@code null} */
    public Code row(String rowCode) {
        return data.rows.get(rowCode);
    }

    /**
     * @param templateOrTable the template or table code the column belongs to ({@code PK.006.02.01.01})
     * @param columnCode      {@code C0010PK} or {@code C800-100}
     * @return the column, searched in the table and then in its template, or {@code null}
     */
    public Code column(String templateOrTable, String columnCode) {
        String scope = templateOrTable;
        while (scope != null) {
            Code code = data.columns.get(scope + "|" + columnCode);
            if (code == null) {
                code = data.rowColumns.get(scope + "|" + columnCode);
            }
            if (code != null) {
                return code;
            }
            int dot = scope.lastIndexOf('.');
            scope = dot > 2 && scope.substring(0, dot).matches("PK\\.\\d{3}(\\.\\d{2})+") ? scope.substring(0, dot) : null;
        }
        return null;
    }

    /**
     * @param domain a domain code as the workbook names it ({@code PK_JN_1}, {@code PF_CG_2}) or a code list ({@code PK_JN})
     * @return the label of a value of the code list, or {@code null}
     */
    public String valueLabel(String domain, String value) {
        if (domain == null || domain.isEmpty() || value == null) {
            return null;
        }
        Map<String, String> values = data.domains.get(DOMAIN_SUFFIX.matcher(domain).replaceFirst(""));
        return values == null ? null : values.get(value);
    }

    /** @return the code list of a domain (value → label), empty when unknown */
    public Map<String, String> values(String domain) {
        if (domain == null) {
            return Map.of();
        }
        return data.domains.getOrDefault(DOMAIN_SUFFIX.matcher(domain).replaceFirst(""), Map.of());
    }

    /** @return whether there is nothing in it (no workbooks found) */
    public boolean isEmpty() {
        return data.rows.isEmpty() && data.columns.isEmpty();
    }

    /** @return counts for tests and logs: templates, rows, columns, domains */
    List<Integer> sizes() {
        return List.of(data.templates.size(), data.rows.size(), data.columns.size() + data.rowColumns.size(), data.domains.size());
    }
}
