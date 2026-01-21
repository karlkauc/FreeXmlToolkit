package org.fxt.freexmltoolkit.controls.v2.view;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ExportMetadataService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports Type Library data to various formats.
 *
 * Supported formats:
 * - CSV (Comma-Separated Values)
 * - Excel (XLSX)
 * - HTML (styled with XMLSpy look &amp; feel)
 * - JSON
 * - XML
 * - Markdown
 *
 * @since 2.0
 */
public class TypeLibraryExporter {

    /**
     * Private constructor to prevent instantiation.
     */
    private TypeLibraryExporter() {
        // Utility class
    }

    /**
     * Type information data structure.
     */
    public static class TypeInfo {
        /**
         * The kind of type (Simple/Complex).
         */
        public String kind;
        /** The name of the type. */
        public String name;
        /** The base type name. */
        public String baseType;
        /** The documentation for the type. */
        public String documentation;
        /** The number of times the type is used. */
        public int usageCount;
        /** List of XPaths where the type is used. */
        public List<String> usageLocations;

        /**
         * Creates a new TypeInfo instance.
         */
        public TypeInfo() {
        }
    }

    /**
     * Export to CSV format.
     *
     * @param types The list of types to export
     * @param file  The file to export to
     * @throws IOException If an I/O error occurs
     */
    public static void exportToCSV(List<TypeInfo> types, File file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            // Header
            writer.write("Type,Name,Base Type,Documentation,Usage Count,Used In (XPath)");
            writer.newLine();

            // Data rows
            for (TypeInfo type : types) {
                writer.write(escapeCsv(type.kind));
                writer.write(",");
                writer.write(escapeCsv(type.name));
                writer.write(",");
                writer.write(escapeCsv(type.baseType));
                writer.write(",");
                writer.write(escapeCsv(type.documentation));
                writer.write(",");
                writer.write(String.valueOf(type.usageCount));
                writer.write(",");
                writer.write(escapeCsv(String.join("; ", type.usageLocations)));
                writer.newLine();
            }
        }
    }

    /**
     * Export to Excel (XLSX) format with multiple sheets.
     *
     * @param types The list of types to export
     * @param file  The file to export to
     * @throws IOException If an I/O error occurs
     */
    public static void exportToExcel(List<TypeInfo> types, File file) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Set document metadata
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            metadataService.setExcelMetadata(workbook, "XSD Type Library Export");

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle unusedStyle = createUnusedStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            // Sheet 1: All Types
            createSheet(workbook, "All Types", types, headerStyle, unusedStyle, normalStyle);

            // Sheet 2: Simple Types
            List<TypeInfo> simpleTypes = types.stream()
                .filter(t -> "Simple".equals(t.kind))
                .toList();
            createSheet(workbook, "Simple Types", simpleTypes, headerStyle, unusedStyle, normalStyle);

            // Sheet 3: Complex Types
            List<TypeInfo> complexTypes = types.stream()
                .filter(t -> "Complex".equals(t.kind))
                .toList();
            createSheet(workbook, "Complex Types", complexTypes, headerStyle, unusedStyle, normalStyle);

            // Sheet 4: Unused Types
            List<TypeInfo> unusedTypes = types.stream()
                .filter(t -> t.usageCount == 0)
                .toList();
            createSheet(workbook, "Unused Types", unusedTypes, headerStyle, unusedStyle, normalStyle);

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }

    /**
     * Export to HTML format with XMLSpy styling.
     *
     * @param types      The list of types to export
     * @param file       The file to export to
     * @param schemaName The name of the schema
     * @throws IOException If an I/O error occurs
     */
    public static void exportToHTML(List<TypeInfo> types, File file, String schemaName) throws IOException {
        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append(metadataService.generateHtmlMetaTags());
        html.append("<title>Type Library - ").append(escapeHtml(schemaName)).append("</title>\n");
        html.append("<style>\n");
        html.append(getXmlSpyCSS());
        html.append("</style>\n");
        html.append("</head>\n<body>\n");

        html.append("<div class=\"header\">\n");
        html.append("<h1>Type Library</h1>\n");
        html.append("<p class=\"schema-name\">Schema: ").append(escapeHtml(schemaName)).append("</p>\n");
        html.append("<p class=\"export-date\">Generated: ").append(getCurrentDateTime()).append("</p>\n");

        // Statistics
        long simpleCount = types.stream().filter(t -> "Simple".equals(t.kind)).count();
        long complexCount = types.stream().filter(t -> "Complex".equals(t.kind)).count();
        long unusedCount = types.stream().filter(t -> t.usageCount == 0).count();

        html.append("<p class=\"stats\">Total: ").append(types.size())
            .append(" | Simple: ").append(simpleCount)
            .append(" | Complex: ").append(complexCount)
            .append(" | Unused: ").append(unusedCount).append("</p>\n");
        html.append("</div>\n");

        html.append("<table>\n");
        html.append("<thead>\n<tr>\n");
        html.append("<th>Type</th><th>Name</th><th>Base Type</th><th>Documentation</th><th>Usage</th><th>Used In (XPath)</th>\n");
        html.append("</tr>\n</thead>\n");
        html.append("<tbody>\n");

        for (TypeInfo type : types) {
            String rowClass = type.usageCount == 0 ? "unused" : "";
            html.append("<tr class=\"").append(rowClass).append("\">\n");
            html.append("<td>").append(escapeHtml(type.kind)).append("</td>\n");
            html.append("<td class=\"name\">").append(escapeHtml(type.name)).append("</td>\n");
            html.append("<td>").append(escapeHtml(type.baseType)).append("</td>\n");
            html.append("<td class=\"documentation\">").append(escapeHtml(type.documentation)).append("</td>\n");
            html.append("<td class=\"center\">").append(type.usageCount).append("</td>\n");
            html.append("<td class=\"xpath\">");
            if (type.usageLocations.isEmpty()) {
                html.append("<em>Not used</em>");
            } else {
                html.append("<ul>\n");
                for (String location : type.usageLocations) {
                    html.append("<li>").append(escapeHtml(location)).append("</li>\n");
                }
                html.append("</ul>");
            }
            html.append("</td>\n");
            html.append("</tr>\n");
        }

        html.append("</tbody>\n</table>\n");
        html.append("</body>\n</html>");

        Files.writeString(file.toPath(), html.toString());
    }

    /**
     * Export to JSON format.
     *
     * @param types      The list of types to export
     * @param file       The file to export to
     * @param schemaName The name of the schema
     * @throws IOException If an I/O error occurs
     */
    public static void exportToJSON(List<TypeInfo> types, File file, String schemaName) throws IOException {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("  \"schemaName\": \"").append(escapeJson(schemaName)).append("\",\n");
        json.append("  \"exportDate\": \"").append(getCurrentDateTime()).append("\",\n");
        json.append("  \"statistics\": {\n");
        json.append("    \"total\": ").append(types.size()).append(",\n");
        json.append("    \"simpleTypes\": ").append(types.stream().filter(t -> "Simple".equals(t.kind)).count()).append(",\n");
        json.append("    \"complexTypes\": ").append(types.stream().filter(t -> "Complex".equals(t.kind)).count()).append(",\n");
        json.append("    \"unusedTypes\": ").append(types.stream().filter(t -> t.usageCount == 0).count()).append("\n");
        json.append("  },\n");
        json.append("  \"types\": [\n");

        for (int i = 0; i < types.size(); i++) {
            TypeInfo type = types.get(i);
            json.append("    {\n");
            json.append("      \"kind\": \"").append(escapeJson(type.kind)).append("\",\n");
            json.append("      \"name\": \"").append(escapeJson(type.name)).append("\",\n");
            json.append("      \"baseType\": \"").append(escapeJson(type.baseType)).append("\",\n");
            json.append("      \"documentation\": \"").append(escapeJson(type.documentation)).append("\",\n");
            json.append("      \"usageCount\": ").append(type.usageCount).append(",\n");
            json.append("      \"usageLocations\": [");

            for (int j = 0; j < type.usageLocations.size(); j++) {
                json.append("\"").append(escapeJson(type.usageLocations.get(j))).append("\"");
                if (j < type.usageLocations.size() - 1) json.append(", ");
            }

            json.append("]\n");
            json.append("    }");
            if (i < types.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        Files.writeString(file.toPath(), json.toString());
    }

    /**
     * Export to XML format.
     *
     * @param types      The list of types to export
     * @param file       The file to export to
     * @param schemaName The name of the schema
     * @throws IOException If an I/O error occurs
     */
    public static void exportToXML(List<TypeInfo> types, File file, String schemaName) throws IOException {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<typeLibrary>\n");
        xml.append("  <schemaName>").append(escapeXml(schemaName)).append("</schemaName>\n");
        xml.append("  <exportDate>").append(getCurrentDateTime()).append("</exportDate>\n");

        xml.append("  <statistics>\n");
        xml.append("    <total>").append(types.size()).append("</total>\n");
        xml.append("    <simpleTypes>").append(types.stream().filter(t -> "Simple".equals(t.kind)).count()).append("</simpleTypes>\n");
        xml.append("    <complexTypes>").append(types.stream().filter(t -> "Complex".equals(t.kind)).count()).append("</complexTypes>\n");
        xml.append("    <unusedTypes>").append(types.stream().filter(t -> t.usageCount == 0).count()).append("</unusedTypes>\n");
        xml.append("  </statistics>\n");

        xml.append("  <types>\n");
        for (TypeInfo type : types) {
            xml.append("    <type kind=\"").append(escapeXml(type.kind)).append("\"");
            if (type.usageCount == 0) xml.append(" unused=\"true\"");
            xml.append(">\n");
            xml.append("      <name>").append(escapeXml(type.name)).append("</name>\n");
            xml.append("      <baseType>").append(escapeXml(type.baseType)).append("</baseType>\n");
            xml.append("      <documentation>").append(escapeXml(type.documentation)).append("</documentation>\n");
            xml.append("      <usageCount>").append(type.usageCount).append("</usageCount>\n");

            if (!type.usageLocations.isEmpty()) {
                xml.append("      <usageLocations>\n");
                for (String location : type.usageLocations) {
                    xml.append("        <location>").append(escapeXml(location)).append("</location>\n");
                }
                xml.append("      </usageLocations>\n");
            }

            xml.append("    </type>\n");
        }
        xml.append("  </types>\n");
        xml.append("</typeLibrary>\n");

        Files.writeString(file.toPath(), xml.toString());
    }

    /**
     * Export to Markdown format.
     *
     * @param types      The list of types to export
     * @param file       The file to export to
     * @param schemaName The name of the schema
     * @throws IOException If an I/O error occurs
     */
    public static void exportToMarkdown(List<TypeInfo> types, File file, String schemaName) throws IOException {
        StringBuilder md = new StringBuilder();

        md.append("# Type Library\n\n");
        md.append("**Schema:** ").append(schemaName).append("  \n");
        md.append("**Generated:** ").append(getCurrentDateTime()).append("\n\n");

        // Statistics
        long simpleCount = types.stream().filter(t -> "Simple".equals(t.kind)).count();
        long complexCount = types.stream().filter(t -> "Complex".equals(t.kind)).count();
        long unusedCount = types.stream().filter(t -> t.usageCount == 0).count();

        md.append("## Statistics\n\n");
        md.append("- **Total Types:** ").append(types.size()).append("\n");
        md.append("- **Simple Types:** ").append(simpleCount).append("\n");
        md.append("- **Complex Types:** ").append(complexCount).append("\n");
        md.append("- **Unused Types:** ").append(unusedCount).append("\n\n");

        md.append("## All Types\n\n");
        md.append("| Type | Name | Base Type | Documentation | Usage | Used In (XPath) |\n");
        md.append("|------|------|-----------|---------------|-------|----------------|\n");

        for (TypeInfo type : types) {
            md.append("| ").append(type.kind).append(" | ");
            md.append("**").append(type.name).append("**");
            if (type.usageCount == 0) md.append(" ⚠️");
            md.append(" | ");
            md.append(type.baseType).append(" | ");
            md.append(type.documentation.replace("\n", " ").replace("|", "\\|")).append(" | ");
            md.append(type.usageCount).append(" | ");

            if (type.usageLocations.isEmpty()) {
                md.append("_Not used_");
            } else {
                md.append(String.join("<br>", type.usageLocations));
            }
            md.append(" |\n");
        }

        // Unused Types Section
        if (unusedCount > 0) {
            md.append("\n## Unused Types\n\n");
            md.append("The following types are defined but not used:\n\n");
            types.stream()
                .filter(t -> t.usageCount == 0)
                .forEach(t -> md.append("- **").append(t.name).append("** (").append(t.kind).append(")\n"));
        }

        Files.writeString(file.toPath(), md.toString());
    }

    // Helper methods for Excel styling
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setFontName("Segoe UI");
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createUnusedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        font.setFontName("Segoe UI");
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 11);
        font.setFontName("Segoe UI");
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private static void createSheet(Workbook workbook, String sheetName, List<TypeInfo> types,
                                     CellStyle headerStyle, CellStyle unusedStyle, CellStyle normalStyle) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Type", "Name", "Base Type", "Documentation", "Usage", "Used In (XPath)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowNum = 1;
        for (TypeInfo type : types) {
            Row row = sheet.createRow(rowNum++);
            CellStyle dataStyle = type.usageCount == 0 ? unusedStyle : normalStyle;

            Cell cell0 = row.createCell(0);
            cell0.setCellValue(type.kind);
            cell0.setCellStyle(dataStyle);

            Cell cell1 = row.createCell(1);
            cell1.setCellValue(type.name);
            cell1.setCellStyle(dataStyle);

            Cell cell2 = row.createCell(2);
            cell2.setCellValue(type.baseType);
            cell2.setCellStyle(dataStyle);

            Cell cell3 = row.createCell(3);
            cell3.setCellValue(type.documentation);
            cell3.setCellStyle(dataStyle);

            Cell cell4 = row.createCell(4);
            cell4.setCellValue(type.usageCount);
            cell4.setCellStyle(dataStyle);

            Cell cell5 = row.createCell(5);
            cell5.setCellValue(String.join("\n", type.usageLocations));
            cell5.setCellStyle(dataStyle);
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Escape methods
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("\n", "<br>");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }

    private static String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String getXmlSpyCSS() {
        return """
            body {
                font-family: 'Segoe UI', Arial, sans-serif;
                font-size: 11px;
                margin: 20px;
                background-color: #ffffff;
            }
            .header {
                background: linear-gradient(to bottom, #f8f9fa, #e9ecef);
                border: 1px solid #dee2e6;
                border-radius: 4px;
                padding: 15px;
                margin-bottom: 20px;
                box-shadow: 1px 1px 3px rgba(0,0,0,0.1);
            }
            h1 {
                color: #2c5aa0;
                font-size: 20px;
                margin: 0 0 10px 0;
                font-weight: bold;
            }
            .schema-name, .export-date, .stats {
                margin: 5px 0;
                color: #333333;
            }
            .stats {
                font-weight: bold;
                color: #2c5aa0;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                background-color: white;
                border: 1px solid #c0c0c0;
                box-shadow: 1px 1px 3px rgba(0,0,0,0.1);
            }
            thead {
                background: linear-gradient(to bottom, #f5f5f5, #e8e8e8);
            }
            th {
                padding: 8px;
                text-align: left;
                border: 1px solid #c0c0c0;
                font-weight: bold;
                color: #333333;
            }
            td {
                padding: 8px;
                border: 1px solid #e0e0e0;
            }
            tbody tr:nth-child(even) {
                background-color: #f8f8f8;
            }
            tbody tr:nth-child(odd) {
                background-color: #ffffff;
            }
            tr.unused {
                background-color: #fff3cd !important;
            }
            .name {
                font-weight: bold;
                color: #2c5aa0;
            }
            .documentation {
                font-style: italic;
                color: #6c757d;
            }
            .xpath {
                font-family: 'Courier New', monospace;
                font-size: 10px;
            }
            .center {
                text-align: center;
            }
            ul {
                margin: 0;
                padding-left: 20px;
            }
            li {
                margin: 2px 0;
            }
            """;
    }
}
