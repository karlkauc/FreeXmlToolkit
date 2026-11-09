package org.fxt.freexmltoolkit.plugins.findatex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.stage.FileChooser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugins.IdentifierKind;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * FinDatEx templates (EMT, EET, EPT, TPT V7/V8): checks template files in CSV form against the official field
 * definitions ({@link FinDatExValidator}), opens Excel template files as CSV, and creates new
 * files with the template's header row. Off by default.
 */
public final class FinDatExPlugin implements FxtPlugin {

    public static final String ID = "findatex";

    @Override public String id() { return ID; }
    @Override public String name() { return "FinDatEx Templates (EMT, EET, EPT, TPT)"; }
    @Override public String version() { return "1.0"; }
    @Override public boolean enabledByDefault() { return false; }

    @Override
    public String description() {
        return "Checks EMT, EET, EPT and TPT files (CSV or Excel) against the official field definitions.";
    }

    @Override
    public void register(PluginContext context) {
        FinDatExValidator validator = new FinDatExValidator();
        String versions = FinDatExTemplates.all().stream().map(t -> t.template + " " + t.version)
                .collect(Collectors.joining(", "));
        List<IndustryPanel.Action> actions = new java.util.ArrayList<>();
        actions.add(new IndustryPanel.Action("findatex-open-excel", "Open Excel Template File…", "bi-file-earmark-spreadsheet",
                ActionColor.NAVIGATE, FinDatExPlugin::openExcel));
        for (FinDatExTemplates.Template template : FinDatExTemplates.all()) {
            actions.add(new IndustryPanel.Action(newFileActionId(template),
                    "New " + template.template + " " + template.version + " File", "bi-file-earmark-plus", ActionColor.CREATE,
                    ui -> newFile(ui, template)));
        }
        context.addActivity(new PluginActivity(ID, "FinDatEx", "bi-table", Workflow.VALIDATION,
                ui -> new IndustryPanel(ui, "FINDATEX",
                        "Checks FinDatEx template files against the field definitions of " + versions
                                + ": columns, template version, the format of every value (dates, Y/N and other "
                                + "code lists, numbers, currencies, countries, CIC codes, LEIs, ISINs) and the mandatory "
                                + "fields (in TPT those of each instrument's CIC category). TPT V7 and V8 are told apart "
                                + "by the version in the file. Open the file (CSV with one instrument per row or per column, "
                                + "or an Excel file) and click Check Active Document.",
                        actions, FinDatExPlugin::describe, validator, List.of(IdentifierKind.ISIN, IdentifierKind.LEI)),
                null));
    }

    /**
     * @return the id of a template's "New … File" action: {@code findatex-new-emt}; with the version when
     *         the plugin knows several versions of the template ({@code findatex-new-tpt-v8-0})
     */
    static String newFileActionId(FinDatExTemplates.Template template) {
        String id = "findatex-new-" + template.template.toLowerCase(java.util.Locale.ROOT);
        long versions = FinDatExTemplates.all().stream().filter(t -> t.template.equals(template.template)).count();
        return versions > 1 ? id + "-" + template.version.toLowerCase(java.util.Locale.ROOT).replace('.', '-') : id;
    }

    /** @return what the file is, or {@code null} when it is no template file */
    static String describe(String content) {
        return FinDatExFile.parse(content).map(file -> {
            String version = file.records().isEmpty() ? "" : file.cell(file.records().getFirst(),
                    file.template().versionField()).value().strip();
            long known = file.codes().stream().filter(c -> file.template().field(c).isPresent()).count();
            int instruments = file.records().size();
            return file.template().template + (version.isEmpty() ? "" : " " + version) + " file · " + instruments
                    + (instruments == 1 ? " instrument" : " instruments") + " · " + known + " of "
                    + file.template().fields.size() + " fields" + (file.transposed() ? " · one instrument per column" : "");
        }).orElse(null);
    }

    /** A new file: the template's header row and one empty row, semicolon separated. */
    static void newFile(PluginUi ui, FinDatExTemplates.Template template) {
        ui.editorHost().openGeneratedDocument(headerFile(template), EditorFileType.OTHER,
                template.template + "-" + template.version + ".csv");
    }

    static String headerFile(FinDatExTemplates.Template template) {
        String header = template.fields.stream().map(f -> f.code).collect(Collectors.joining(";"));
        String row = template.fields.stream().map(f -> "version".equals(f.type) ? template.version
                : "datetime".equals(f.type) ? LocalDateTime.now(java.time.ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "").collect(Collectors.joining(";"));
        return header + "\n" + row + "\n";
    }

    /** Opens an Excel template file (first sheet) as CSV text. */
    static void openExcel(PluginUi ui) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("FinDatExPlugin.open-findatex-excel-file"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"));
        File file = FileChooserHelper.showOpenDialog(chooser, ui.window());
        if (file == null) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String csv = excelToCsv(file);
                Platform.runLater(() -> ui.editorHost().openGeneratedDocument(csv, EditorFileType.OTHER,
                        file.getName().replaceFirst("\\.xlsx?$", "") + ".csv"));
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> DialogHelper.showError(I18n.t("FinDatExPlugin.findatex"), I18n.t("FinDatExPlugin.the-excel-file-could-not"),
                        String.valueOf(e.getMessage())));
            }
        });
    }

    /** @return the first sheet as semicolon-separated text: ISO dates, plain numbers, quoted where needed */
    static String excelToCsv(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath()); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            StringBuilder csv = new StringBuilder();
            for (Row row : sheet) {
                int last = row.getLastCellNum();
                StringBuilder line = new StringBuilder();
                for (int c = 0; c < last; c++) {
                    if (c > 0) {
                        line.append(';');
                    }
                    line.append(quote(text(row.getCell(c))));
                }
                csv.append(line.toString().replaceAll(";+$", "")).append('\n');
            }
            return csv.toString();
        }
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime value = cell.getLocalDateTimeCellValue();
                    yield value.toLocalTime().toSecondOfDay() == 0 ? value.toLocalDate().toString()
                            : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "Y" : "N";
            case STRING -> cell.getStringCellValue();
            default -> "";
        };
    }

    private static String quote(String value) {
        return value.contains(";") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
