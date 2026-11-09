package org.fxt.freexmltoolkit.plugins.findatex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.plugin.DocumentValidator.Problem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FinDatEx plugin")
class FinDatExPluginTest {

    private final FinDatExValidator validator = new FinDatExValidator();

    /** The mandatory EMT fields of the general sections, filled validly. */
    private static Map<String, String> emt() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("00001_EMT_Version", "V4.3");
        values.put("00005_File_Generation_Date_And_Time", "2026-11-08 10:00:00");
        values.put("00006_EMT_Data_Reporting_Target_Market", "Y");
        values.put("00007_EMT_Data_Reporting_Ex_Ante", "N");
        values.put("00008_EMT_Data_Reporting_Ex_Post", "N");
        values.put("00010_Financial_Instrument_Identifying_Data", "US0378331005");
        values.put("00020_Type_Of_Identification_Code_For_The_Financial_Instrument", "1");
        values.put("00030_Financial_Instrument_Name", "Example Fund; Class A");
        values.put("00040_Financial_Instrument_Currency", "EUR");
        values.put("00045_Financial_Instrument_Performance_Fee", "N");
        values.put("00047_Financial_Instrument_Distribution_Of_Cash", "Y");
        values.put("00050_General_Reference_Date", "2026-10-31");
        values.put("00060_Financial_Instrument_Product_Type", "U");
        values.put("00070_Financial_Instrument_Manufacturer_Name", "Example Asset Management");
        values.put("00073_Financial_Instrument_Manufacturer_LEI", "5493001KJTIIGC8Y1R12");
        return values;
    }

    /** The fields of a TPT version that are mandatory for an instrument of a CIC category, filled validly. */
    private static Map<String, String> tpt(String version, String cic) {
        var template = FinDatExTemplates.byName("TPT", version).orElseThrow();
        Map<String, String> values = new LinkedHashMap<>();
        for (var field : template.fields) {
            if (!field.isMandatoryFor(FinDatExValidator.category(cic))) {
                continue;
            }
            values.put(field.code, switch (field.type) {
                case "version" -> template.version;
                case "date" -> "2026-09-30";
                case "datetime" -> "2026-09-30 10:00:00";
                case "currency" -> "EUR";
                case "country" -> "DE";
                case "cic" -> cic;
                case "identifier" -> "US0378331005";
                case "identifierType" -> "1";
                case "lei" -> "5493001KJTIIGC8Y1R12";
                case "enum" -> field.values.getFirst();
                case "integer" -> field.min == null ? "1" : String.valueOf(field.min);
                case "number" -> "0.5";
                default -> "x";
            });
        }
        // the issuer is identified internally (type 9): no LEI check
        values.computeIfPresent("48_Type_of_identification_code_for_issuer", (k, v) -> "9");
        values.computeIfPresent("51_Type_of_identification_code_for_issuer_group", (k, v) -> "9");
        return values;
    }

    @Test
    @DisplayName("TPT: V7 and V8 told apart by the version value; CIC, country, LEI and per-category mandatory fields")
    void tpt() {
        Map<String, String> v8 = tpt("V8.0", "DE31");
        assertEquals(List.of(), messages(csv(v8)));
        assertEquals("V8.0", FinDatExFile.parse(csv(v8)).orElseThrow().template().version);
        Map<String, String> v7 = tpt("V7.0", "DE31");
        assertEquals(List.of(), messages(csv(v7)));
        assertEquals("V7.0", FinDatExFile.parse(csv(v7)).orElseThrow().template().version);
        assertTrue(FinDatExPlugin.describe(csv(v7)).startsWith("TPT V7.0 file · 1 instrument"), FinDatExPlugin.describe(csv(v7)));

        Map<String, String> old = tpt("V8.0", "DE31");
        old.put("1000_TPT_Version", "V6.0");
        assertEquals("V8.0", FinDatExFile.parse(csv(old)).orElseThrow().template().version, "unknown versions: the newest");
        assertTrue(messages(csv(old)).getFirst().contains("the checks follow V8.0"), messages(csv(old)).toString());

        Map<String, String> bad = tpt("V8.0", "DE31");
        bad.put("12_CIC_code_of_the_instrument", "D31");
        bad.put("52_Issuer_country", "Germany");
        List<String> problems = messages(csv(bad));
        assertTrue(problems.stream().anyMatch(m -> m.contains("12_CIC_code_of_the_instrument \"D31\": expected a CIC code")), problems.toString());
        assertTrue(problems.stream().anyMatch(m -> m.contains("52_Issuer_country \"Germany\": expected an ISO 3166")), problems.toString());
        assertTrue(problems.getFirst().startsWith("Row 1 (US0378331005): "), "rows are named after the instrument code");
        bad.put("12_CIC_code_of_the_instrument", "QQ31");
        assertTrue(messages(csv(bad)).stream().anyMatch(m -> m.contains("QQ is no ISO 3166 country code")));

        Map<String, String> lei = tpt("V8.0", "DE31");
        lei.put("47_Issuer_identification_code", "5493001KJTIIGC8Y1R13");
        lei.put("48_Type_of_identification_code_for_issuer", "1");
        assertTrue(messages(csv(lei)).stream().anyMatch(m -> m.contains("not a valid LEI (identification type 1)")),
                messages(csv(lei)).toString());

        // The issuer is mandatory for equity (category 3), not for property (category 9)
        Map<String, String> issuer = tpt("V8.0", "DE31");
        issuer.put("47_Issuer_identification_code", "");
        assertTrue(messages(csv(issuer)).contains("Row 1 (US0378331005): 47_Issuer_identification_code: "
                + "the field is mandatory for CIC category 3 and empty."), messages(csv(issuer)).toString());
        issuer.put("12_CIC_code_of_the_instrument", "DE91");
        assertTrue(messages(csv(issuer)).stream().noneMatch(m -> m.contains("47_Issuer")), messages(csv(issuer)).toString());

        String transposed = v7.entrySet().stream().map(e -> e.getKey() + ";" + e.getValue()).collect(Collectors.joining("\n"));
        assertEquals("V7.0", FinDatExFile.parse(transposed).orElseThrow().template().version);
        assertEquals(List.of(), messages(transposed));

        String fresh = FinDatExPlugin.headerFile(FinDatExTemplates.byName("TPT", "V8.0").orElseThrow());
        assertEquals("V8.0", FinDatExFile.parse(fresh).orElseThrow().template().version);
        assertTrue(validator.validate(fresh, null).stream().allMatch(p -> p.severity().equals("warning")),
                validator.validate(fresh, null).toString());
    }

    private static String csv(Map<String, String> values) {
        return String.join(";", values.keySet()) + "\n"
                + values.values().stream().map(v -> v.contains(";") ? "\"" + v + "\"" : v).collect(Collectors.joining(";")) + "\n";
    }

    private List<String> messages(String text) {
        return validator.validate(text, null).stream().map(Problem::message).toList();
    }

    @Test
    @DisplayName("the field definitions of EMT, EET, EPT and TPT V7/V8 are bundled")
    void templates() {
        assertEquals(List.of("EMT V4.3 114", "EET V1.1.3 614", "EPT V21 144", "TPT V7.0 152", "TPT V8.0 154"),
                FinDatExTemplates.all().stream().map(t -> t.template + " " + t.version + " " + t.fields.size()).toList());
        assertEquals("V8.0", FinDatExTemplates.byName("TPT").orElseThrow().version, "the newest version");
        assertEquals("V7.0", FinDatExTemplates.byName("TPT", "V7").orElseThrow().version);
        var cic = FinDatExTemplates.byName("TPT").orElseThrow().field("12_CIC_code_of_the_instrument").orElseThrow();
        assertEquals("cic", cic.type);
        assertTrue(cic.isGenerallyMandatory());
        assertEquals(List.of("findatex-new-emt", "findatex-new-eet", "findatex-new-ept", "findatex-new-tpt-v7-0",
                "findatex-new-tpt-v8-0"), FinDatExTemplates.all().stream().map(FinDatExPlugin::newFileActionId).toList());
        var currency = FinDatExTemplates.byName("EMT").orElseThrow().field("00040_Financial_Instrument_Currency").orElseThrow();
        assertEquals("currency", currency.type);
        assertTrue(currency.isGenerallyMandatory());
    }

    @Test
    @DisplayName("a valid row passes; formats, ISIN, currency, code lists, columns are checked")
    void checks() {
        assertEquals(List.of(), messages(csv(emt())));
        assertTrue(validator.accepts(csv(emt()), null));
        assertFalse(validator.accepts("<a/>", null));

        Map<String, String> bad = emt();
        bad.put("00010_Financial_Instrument_Identifying_Data", "US0378331006");
        bad.put("00040_Financial_Instrument_Currency", "Euro");
        bad.put("00050_General_Reference_Date", "31.10.2026");
        bad.put("00045_Financial_Instrument_Performance_Fee", "yes");
        bad.put("00073_Financial_Instrument_Manufacturer_LEI", "5493001KJTIIGC8Y1R13");
        List<String> problems = messages(csv(bad));
        assertEquals(List.of(
                "Row 1 (US0378331006): 00010_Financial_Instrument_Identifying_Data \"US0378331006\": not a valid ISIN (identification type 1).",
                "Row 1 (US0378331006): 00040_Financial_Instrument_Currency \"Euro\": expected an ISO 4217 currency code like EUR.",
                "Row 1 (US0378331006): 00045_Financial_Instrument_Performance_Fee \"yes\": expected one of Y, N.",
                "Row 1 (US0378331006): 00050_General_Reference_Date \"31.10.2026\": expected a date like 2026-01-31.",
                "Row 1 (US0378331006): 00073_Financial_Instrument_Manufacturer_LEI \"5493001KJTIIGC8Y1R13\": not a valid LEI."),
                problems);

        Map<String, String> other = emt();
        other.put("00020_Type_Of_Identification_Code_For_The_Financial_Instrument", "4");
        other.put("00010_Financial_Instrument_Identifying_Data", "865985");
        assertEquals(List.of(), messages(csv(other)), "a WKN is no ISIN");

        Map<String, String> columns = emt();
        columns.remove("00030_Financial_Instrument_Name");
        columns.put("99999_Own_Field", "x");
        List<String> header = messages(csv(columns));
        assertEquals(List.of("99999_Own_Field is no field of EMT V4.3.",
                "Mandatory field 00030_Financial_Instrument_Name is missing."), header);

        Map<String, String> empty = emt();
        empty.put("00030_Financial_Instrument_Name", "");
        assertEquals(List.of("Row 1 (US0378331005): 00030_Financial_Instrument_Name: the mandatory field is empty."),
                messages(csv(empty)));
        var problem = validator.validate(csv(empty), null).getFirst();
        assertEquals("warning", problem.severity());
        assertEquals(2, problem.line());
    }

    @Test
    @DisplayName("transposed files: one field per row, an instrument per column")
    void transposed() {
        Map<String, String> values = emt();
        values.put("00040_Financial_Instrument_Currency", "eur");
        String text = values.entrySet().stream().map(e -> e.getKey() + ";" + (e.getValue().contains(";") ? "\"" + e.getValue() + "\"" : e.getValue())).collect(Collectors.joining("\n"));
        FinDatExFile file = FinDatExFile.parse(text).orElseThrow();
        assertTrue(file.transposed());
        List<Problem> problems = validator.validate(text, null);
        assertEquals(1, problems.size(), problems.toString());
        assertEquals("Instrument 1 (US0378331005): 00040_Financial_Instrument_Currency \"eur\": expected an ISO 4217 currency code like EUR.",
                problems.getFirst().message());
        assertEquals(9, problems.getFirst().line());
        assertEquals("EMT V4.3 file · 1 instrument · 15 of 114 fields · one instrument per column", FinDatExPlugin.describe(text));
    }

    @Test
    @DisplayName("a new file has the header and passes except for its empty mandatory fields")
    void newFile() {
        var emt = FinDatExTemplates.byName("EMT").orElseThrow();
        String text = FinDatExPlugin.headerFile(emt);
        assertTrue(text.startsWith("00001_EMT_Version;00002_EMT_Producer_Name;"));
        assertTrue(validator.validate(text, null).stream().allMatch(p -> p.severity().equals("warning")
                && p.message().endsWith("the mandatory field is empty.")));
        assertEquals("EMT V4.3 file · 1 instrument · 114 of 114 fields", FinDatExPlugin.describe(text));
        assertNull(FinDatExPlugin.describe("a;b\n1;2"));
    }

    @Test
    @DisplayName("Excel files become CSV: ISO dates, plain numbers, quoted text")
    void excel(@TempDir Path dir) throws Exception {
        Path xlsx = dir.resolve("emt.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(xlsx)) {
            var sheet = workbook.createSheet("EMT");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("00001_EMT_Version");
            header.createCell(1).setCellValue("00050_General_Reference_Date");
            header.createCell(2).setCellValue("00030_Financial_Instrument_Name");
            header.createCell(3).setCellValue("01010_Max_Value");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("V4.3");
            var date = row.createCell(1);
            date.setCellValue(LocalDate.of(2026, 10, 31));
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy"));
            date.setCellStyle(style);
            row.createCell(2).setCellValue("Fund; A");
            row.createCell(3).setCellValue(0.0115);
            workbook.write(out);
        }
        assertEquals("00001_EMT_Version;00050_General_Reference_Date;00030_Financial_Instrument_Name;01010_Max_Value\n"
                + "V4.3;2026-10-31;\"Fund; A\";0.0115\n", FinDatExPlugin.excelToCsv(xlsx.toFile()));
    }
}
