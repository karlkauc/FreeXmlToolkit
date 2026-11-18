package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ExportMetadataService;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Accent;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme.Role;

/**
 * Exports {@link ValidationProblem}s to an Excel (.xlsx) workbook in the shared house style ({@link FxtExcelTheme}),
 * as a work list for large reports:
 * <ul>
 *   <li>"Summary" - export information, the filter, counts per severity and per category;</li>
 *   <li>"Problems" - one row per problem: severity, category, rule, the security / entity / record it is about,
 *       line and column, message, link (frozen header, auto-filter; split into "Problems (2)" … beyond Excel's
 *       row limit);</li>
 *   <li>"By Object" - one row per security / entity / record: occurrences per severity, the kinds of problems,
 *       first line;</li>
 *   <li>"By Rule" - one row per kind of problem: category, severity, occurrences, an example.</li>
 * </ul>
 * Streams (SXSSF), so 200,000 problems fit in little memory.
 */
public final class ValidationExcelExporter {

    private static final Logger logger = LogManager.getLogger(ValidationExcelExporter.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Columns of the Problems sheet (the first five as in earlier versions). */
    static final String[] HEADERS = {"#", "Source", "Severity", "Line", "Message", "Category", "Rule", "Object",
            "Object Name", "Column", "Link"};
    private static final int[] WIDTHS = {2000, 4000, 3000, 2500, 24000, 7000, 4500, 4800, 7000, 2200, 9000};
    private static final int SEVERITY_COLUMN = 2;
    /** Data rows per sheet (Excel's limit is 1,048,576 rows, one is the header). */
    static int maxRowsPerSheet = 1_048_575;
    private static final int ROW_WINDOW = 200;

    private ValidationExcelExporter() {
    }

    /**
     * Writes the given problems to {@code outputPath} as an .xlsx workbook.
     *
     * @param problems       the problems to export (may be empty)
     * @param sourceFileName the validated document's name (for the summary), or {@code null}
     * @param outputPath     the target file
     * @throws IOException if writing fails
     */
    public static void export(List<ValidationProblem> problems, String sourceFileName, Path outputPath)
            throws IOException {
        export(problems, sourceFileName, null, outputPath);
    }

    /**
     * @param filter what the export is limited to (shown in the summary), or {@code null} for all problems
     */
    public static void export(List<ValidationProblem> problems, String sourceFileName, String filter, Path outputPath)
            throws IOException {
        logger.info("Exporting {} validation problem(s) to Excel: {}", problems.size(), outputPath);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW)) {
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            if (metadataService != null) {
                metadataService.setExcelMetadata(workbook.getXSSFWorkbook(), "Validation Problems");
            }
            setProblemMetadata(workbook.getXSSFWorkbook(), problems, sourceFileName, filter);
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            writeSummary(workbook.createSheet("Summary"), theme, problems, sourceFileName, filter, metadataService);
            writeProblems(workbook, theme, problems);
            writeByObject(workbook.createSheet("By Object"), theme, problems);
            writeByRule(workbook.createSheet("By Rule"), theme, problems);
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
        logger.info("Excel export completed: {}", outputPath);
    }

    /**
     * The workbook's document properties (File → Info in Excel, the Windows Explorer columns): title and subject
     * name the validated document, the description sums up the counts and the filter, keywords and category make
     * the file findable; custom properties carry the figures one by one (source file, filter, problems per
     * severity, objects, categories, export time, generator).
     */
    static void setProblemMetadata(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook, List<ValidationProblem> problems,
                                   String sourceFileName, String filter) {
        long errors = problems.stream().filter(p -> p.level() == ValidationProblem.Level.ERROR).count();
        long warnings = problems.stream().filter(p -> p.level() == ValidationProblem.Level.WARNING).count();
        long hints = problems.size() - errors - warnings;
        long objects = problems.stream().filter(p -> p.subject() != null).map(p -> p.subject().groupKey()).distinct().count();
        boolean hasSource = sourceFileName != null && !sourceFileName.isBlank();
        String filterText = filter == null || filter.isBlank() ? "All problems" : filter;
        Map<ProblemCategory, Integer> byCategory = ProblemGroups.countByCategory(problems);

        var properties = workbook.getProperties();
        var core = properties.getCoreProperties();
        core.setTitle(hasSource ? "Validation Problems — " + sourceFileName : "Validation Problems");
        if (hasSource) {
            core.setSubjectProperty(sourceFileName);
        }
        core.setDescription(problems.size() + " problems (" + errors + " errors, " + warnings + " warnings, " + hints
                + " hints) in " + objects + " objects. Filter: " + filterText + ".");
        Set<String> keywords = new LinkedHashSet<>(List.of("validation", "problems"));
        byCategory.keySet().forEach(c -> keywords.add(c.group().title()));
        core.setKeywords(String.join(", ", keywords));
        core.setCategory("Validation Report");
        core.setContentStatus(errors > 0 ? "Errors" : warnings > 0 ? "Warnings" : "OK");
        if (core.getCreated() == null) {
            core.setCreated(java.util.Optional.of(new java.util.Date()));
        }

        var custom = properties.getCustomProperties();
        putCustom(custom, "Source File", hasSource ? sourceFileName : "");
        putCustom(custom, "Filter", filterText);
        putCustom(custom, "Problems", problems.size());
        putCustom(custom, "Errors", errors);
        putCustom(custom, "Warnings", warnings);
        putCustom(custom, "Hints", hints);
        putCustom(custom, "Objects", objects);
        putCustom(custom, "Categories", byCategory.entrySet().stream()
                .map(e -> e.getKey().fullTitle() + " (" + e.getValue() + ")").collect(java.util.stream.Collectors.joining("; ")));
        putCustom(custom, "Export Timestamp", DATE_FORMATTER.format(LocalDateTime.now()));
        putCustom(custom, "Generated by", "FreeXmlToolkit");
    }

    private static void putCustom(org.apache.poi.ooxml.POIXMLProperties.CustomProperties custom, String name, Object value) {
        if (custom.contains(name)) {
            return;
        }
        if (value instanceof Number n) {
            custom.addProperty(name, n.intValue());
        } else {
            String text = String.valueOf(value);
            custom.addProperty(name, text.length() > 255 ? text.substring(0, 254) + "…" : text);
        }
    }

    private static void writeSummary(Sheet sheet, FxtExcelTheme theme, List<ValidationProblem> problems,
                                     String sourceFileName, String filter, ExportMetadataService metadataService) {
        FxtExcelTheme.infoSheet(sheet);
        long errors = problems.stream().filter(p -> p.level() == ValidationProblem.Level.ERROR).count();
        long warnings = problems.stream().filter(p -> p.level() == ValidationProblem.Level.WARNING).count();
        long hints = problems.size() - errors - warnings;
        boolean hasSource = sourceFileName != null && !sourceFileName.isBlank();

        int row = theme.writeTitle(sheet, 0, hasSource
                ? "Validation Problems — " + FxtExcelTheme.safe(sourceFileName)
                : "Validation Problems");
        row++;
        row = theme.writeSection(sheet, row, "Export Information", 1);
        if (hasSource) {
            row = theme.writeLabelValue(sheet, row, "Source File", FxtExcelTheme.safe(sourceFileName));
        }
        row = theme.writeLabelValue(sheet, row, "Export Timestamp", DATE_FORMATTER.format(LocalDateTime.now()));
        row = theme.writeLabelValue(sheet, row, "Generated by", "FreeXmlToolkit");
        if (metadataService != null) {
            row = theme.writeLabelValue(sheet, row, "Application Version", metadataService.getAppVersion());
        }
        row = theme.writeLabelValue(sheet, row, "Filter", filter == null || filter.isBlank() ? "All problems"
                : FxtExcelTheme.safe(filter));
        row++;
        row = theme.writeSection(sheet, row, "Counts", 1);
        row = theme.writeLabelValue(sheet, row, "Total Problems", String.valueOf(problems.size()));
        row = theme.writeLabelValue(sheet, row, "Errors", String.valueOf(errors),
                errors > 0 ? theme.value(Accent.ERROR) : theme.value());
        row = theme.writeLabelValue(sheet, row, "Warnings", String.valueOf(warnings),
                warnings > 0 ? theme.value(Accent.WARNING) : theme.value());
        row = theme.writeLabelValue(sheet, row, "Hints", String.valueOf(hints),
                hints > 0 ? theme.value(Accent.INFO) : theme.value());
        long objects = problems.stream().filter(p -> p.subject() != null).map(p -> p.subject().groupKey()).distinct().count();
        row = theme.writeLabelValue(sheet, row, "Objects (securities, entities, records)", String.valueOf(objects));
        Map<ProblemCategory, Integer> byCategory = ProblemGroups.countByCategory(problems);
        if (!byCategory.isEmpty()) {
            row++;
            row = theme.writeSection(sheet, row, "Problems per Category", 1);
            for (var e : byCategory.entrySet()) {
                row = theme.writeLabelValue(sheet, row, e.getKey().fullTitle(), String.valueOf(e.getValue()));
            }
        }
    }

    private static void writeProblems(SXSSFWorkbook workbook, FxtExcelTheme theme, List<ValidationProblem> problems) {
        int sheetNumber = 1;
        int from = 0;
        do {
            Sheet sheet = workbook.createSheet(sheetNumber == 1 ? "Problems" : "Problems (" + sheetNumber + ")");
            int to = Math.min(problems.size(), from + maxRowsPerSheet);
            writeProblemRows(sheet, theme, problems, from, to);
            from = to;
            sheetNumber++;
        } while (from < problems.size());
    }

    private static void writeProblemRows(Sheet sheet, FxtExcelTheme theme, List<ValidationProblem> problems, int from,
                                         int to) {
        FxtExcelTheme.tableSheet(sheet);
        int rowNum = theme.writeHeaderRow(sheet, 0, HEADERS, i -> i == 0 || i == SEVERITY_COLUMN || i == 3 || i == 9);
        for (int i = from; i < to; i++) {
            ValidationProblem problem = problems.get(i);
            boolean stripe = rowNum % 2 == 0;
            Row row = sheet.createRow(rowNum++);
            FxtExcelTheme.cell(row, 0, i + 1, theme.body(Role.CENTER, stripe));
            FxtExcelTheme.cell(row, 1, FxtExcelTheme.safe(problem.source()), theme.body(Role.MUTED, stripe));
            FxtExcelTheme.cell(row, SEVERITY_COLUMN, FxtExcelTheme.safe(problem.severity()),
                    theme.accentText(accent(problem.level()), stripe));
            number(row, 3, problem.line(), theme, stripe);
            FxtExcelTheme.cell(row, 4, FxtExcelTheme.clip(FxtExcelTheme.safe(problem.message())), theme.body(Role.WRAP, stripe));
            FxtExcelTheme.cell(row, 5, problem.effectiveCategory().fullTitle(), theme.body(Role.TEXT, stripe));
            FxtExcelTheme.cell(row, 6, FxtExcelTheme.clip(FxtExcelTheme.safe(rule(problem))), theme.body(Role.MONO, stripe));
            ProblemSubject subject = problem.subject();
            FxtExcelTheme.cell(row, 7, subject == null ? "" : FxtExcelTheme.safe(subject.kind() + " " + subject.key()),
                    theme.body(Role.MONO, stripe));
            FxtExcelTheme.cell(row, 8, subject == null || subject.label() == null ? "" : FxtExcelTheme.safe(subject.label()),
                    theme.body(Role.TEXT, stripe));
            number(row, 9, problem.column(), theme, stripe);
            FxtExcelTheme.cell(row, 10, problem.hasLink() ? FxtExcelTheme.safe(problem.link()) : "",
                    theme.body(Role.MUTED, stripe));
        }
        for (int i = 0; i < WIDTHS.length; i++) {
            sheet.setColumnWidth(i, WIDTHS[i]);
        }
        FxtExcelTheme.finishTable(sheet, 0, Math.max(rowNum - 1, 1), HEADERS.length - 1);
    }

    private static final String[] OBJECT_HEADERS = {"Object Kind", "Object", "Name", "Occurrences", "Errors", "Warnings",
            "Hints", "Kinds of Problems", "First Line"};

    private static void writeByObject(Sheet sheet, FxtExcelTheme theme, List<ValidationProblem> problems) {
        FxtExcelTheme.tableSheet(sheet);
        int rowNum = theme.writeHeaderRow(sheet, 0, OBJECT_HEADERS, i -> i >= 3 && i <= 6 || i == 8);
        List<ProblemGroups.Group> groups = ProblemGroups.group(
                problems.stream().filter(p -> p.subject() != null).toList(), ProblemGroups.By.SUBJECT);
        for (ProblemGroups.Group g : groups) {
            if (rowNum > maxRowsPerSheet) {
                break;
            }
            boolean stripe = rowNum % 2 == 0;
            Row row = sheet.createRow(rowNum++);
            List<ValidationProblem> all = g.problems();
            FxtExcelTheme.cell(row, 0, g.subject().kind().name(), theme.body(Role.MUTED, stripe));
            FxtExcelTheme.cell(row, 1, FxtExcelTheme.safe(g.subject().key()), theme.body(Role.MONO, stripe));
            FxtExcelTheme.cell(row, 2, g.subject().label() == null ? "" : FxtExcelTheme.safe(g.subject().label()),
                    theme.body(Role.TEXT, stripe));
            FxtExcelTheme.cell(row, 3, g.count(), theme.body(Role.CENTER, stripe));
            FxtExcelTheme.cell(row, 4, count(all, ValidationProblem.Level.ERROR), theme.body(Role.CENTER, stripe));
            FxtExcelTheme.cell(row, 5, count(all, ValidationProblem.Level.WARNING), theme.body(Role.CENTER, stripe));
            FxtExcelTheme.cell(row, 6, count(all, ValidationProblem.Level.INFO), theme.body(Role.CENTER, stripe));
            Set<String> kinds = new LinkedHashSet<>();
            g.kinds().forEach(k -> kinds.add(k.title() + " (" + k.problems().size() + ")"));
            FxtExcelTheme.cell(row, 7, FxtExcelTheme.clip(FxtExcelTheme.safe(String.join("; ", kinds))),
                    theme.body(Role.WRAP, stripe));
            number(row, 8, all.isEmpty() ? 0 : all.getFirst().line(), theme, stripe);
        }
        int[] widths = {3200, 5200, 9000, 3000, 2500, 2800, 2500, 24000, 2800};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }
        FxtExcelTheme.finishTable(sheet, 0, Math.max(rowNum - 1, 1), OBJECT_HEADERS.length - 1);
    }

    private static final String[] RULE_HEADERS = {"Category", "Rule", "Severity", "Occurrences", "Objects", "Example"};

    private static void writeByRule(Sheet sheet, FxtExcelTheme theme, List<ValidationProblem> problems) {
        FxtExcelTheme.tableSheet(sheet);
        int rowNum = theme.writeHeaderRow(sheet, 0, RULE_HEADERS, i -> i >= 2 && i <= 4);
        Map<String, List<ValidationProblem>> byRule = new LinkedHashMap<>();
        for (ValidationProblem p : problems) {
            byRule.computeIfAbsent(ProblemGroups.ruleKey(p), k -> new ArrayList<>()).add(p);
        }
        List<List<ValidationProblem>> rules = new ArrayList<>(byRule.values());
        rules.sort(Comparator.comparing((List<ValidationProblem> l) -> worst(l)).thenComparing(l -> -l.size()));
        for (List<ValidationProblem> list : rules) {
            ValidationProblem example = list.getFirst();
            boolean stripe = rowNum % 2 == 0;
            Row row = sheet.createRow(rowNum++);
            FxtExcelTheme.cell(row, 0, example.effectiveCategory().fullTitle(), theme.body(Role.TEXT, stripe));
            FxtExcelTheme.cell(row, 1, FxtExcelTheme.clip(FxtExcelTheme.safe(rule(example))), theme.body(Role.MONO, stripe));
            ValidationProblem.Level level = worst(list);
            FxtExcelTheme.cell(row, 2, level.name().toLowerCase(java.util.Locale.ROOT), theme.accentText(accent(level), stripe));
            FxtExcelTheme.cell(row, 3, list.size(), theme.body(Role.CENTER, stripe));
            FxtExcelTheme.cell(row, 4, list.stream().filter(p -> p.subject() != null).map(p -> p.subject().groupKey())
                    .distinct().count(), theme.body(Role.CENTER, stripe));
            FxtExcelTheme.cell(row, 5, FxtExcelTheme.clip(FxtExcelTheme.safe(example.message())), theme.body(Role.WRAP, stripe));
        }
        int[] widths = {7000, 6000, 3000, 3000, 2600, 26000};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }
        FxtExcelTheme.finishTable(sheet, 0, Math.max(rowNum - 1, 1), RULE_HEADERS.length - 1);
    }

    /** @return the rule code, or the XSD error code / the kind of problem when the source names none */
    private static String rule(ValidationProblem p) {
        if (p.ruleId() != null && !p.ruleId().isBlank()) {
            return p.ruleId();
        }
        String key = ProblemGroups.ruleKey(p);
        int bar = key.indexOf('|');
        String rule = bar >= 0 ? key.substring(bar + 1) : key;
        return rule.equals(p.message()) ? "" : rule;
    }

    private static void number(Row row, int column, int value, FxtExcelTheme theme, boolean stripe) {
        if (value > 0) {
            FxtExcelTheme.cell(row, column, value, theme.body(Role.CENTER, stripe));
        } else {
            FxtExcelTheme.cell(row, column, "", theme.body(Role.CENTER, stripe));
        }
    }

    private static long count(List<ValidationProblem> problems, ValidationProblem.Level level) {
        return problems.stream().filter(p -> p.level() == level).count();
    }

    private static ValidationProblem.Level worst(List<ValidationProblem> problems) {
        return problems.stream().map(ValidationProblem::level).min(Comparator.naturalOrder())
                .orElse(ValidationProblem.Level.INFO);
    }

    private static Accent accent(ValidationProblem.Level level) {
        return switch (level) {
            case ERROR -> Accent.ERROR;
            case WARNING -> Accent.WARNING;
            case INFO -> Accent.INFO;
        };
    }
}
