package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports Data Dictionary content to Excel format with multi-language support.
 * Creates one sheet per language with comprehensive element information.
 */
public class DataDictionaryExcelExporter {

    private static final Logger logger = LogManager.getLogger(DataDictionaryExcelExporter.class);

    private static final String[] COLUMN_HEADERS = {
            "#",
            "XPath",
            "Element Name",
            "Type",
            "Mandatory",
            "Min Occurs",
            "Max Occurs",
            "Cardinality",
            "Documentation",
            "Type Documentation",
            "Restriction Base",
            "Enumerations",
            "Pattern",
            "Min/Max Length",
            "Min/Max Value",
            "Total/Fraction Digits",
            "Sample Data",
            "Level"
    };

    private static final int[] COLUMN_WIDTHS = {
            2000,   // #
            12000,  // XPath
            6000,   // Element Name
            6000,   // Type
            3000,   // Mandatory
            3000,   // Min Occurs
            3000,   // Max Occurs
            4000,   // Cardinality
            15000,  // Documentation
            15000,  // Type Documentation
            5000,   // Restriction Base
            10000,  // Enumerations
            8000,   // Pattern
            5000,   // Min/Max Length
            5000,   // Min/Max Value
            5000,   // Total/Fraction Digits
            6000,   // Sample Data
            2500    // Level
    };

    private final XsdDocumentationData xsdDocumentationData;
    private final XsdDocumentationHtmlService htmlService;

    // Metadata sheet styles (initialized in exportToExcel)
    private CellStyle sectionHeaderStyle;
    private CellStyle labelStyle;
    private CellStyle valueStyle;

    public DataDictionaryExcelExporter(XsdDocumentationData xsdDocumentationData,
                                       XsdDocumentationHtmlService htmlService) {
        this.xsdDocumentationData = xsdDocumentationData;
        this.htmlService = htmlService;
    }

    /**
     * Exports the Data Dictionary to an Excel file with one sheet per language.
     *
     * @param outputFile the output Excel file
     * @param elements   the list of elements to export
     */
    public void exportToExcel(File outputFile, List<XsdExtendedElement> elements) {
        logger.info("Exporting Data Dictionary to Excel: {}", outputFile.getAbsolutePath());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Set document metadata
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            if (metadataService != null) {
                metadataService.setExcelMetadata(workbook, "Data Dictionary - XSD Documentation");
            }

            // Discover all languages
            Set<String> allLanguages = discoverLanguages(elements);
            logger.debug("Discovered languages: {}", allLanguages);

            // Create styles for language sheets
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle mandatoryYesStyle = createMandatoryYesStyle(workbook);
            CellStyle mandatoryNoStyle = createMandatoryNoStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);

            // Create styles for metadata sheet
            initMetadataStyles(workbook);

            // Create metadata sheet FIRST (will be moved to position 0)
            createMetadataSheet(workbook, elements, allLanguages);

            // Create one sheet per language
            for (String language : allLanguages) {
                String sheetName = language.toUpperCase();
                // Excel sheet names have a 31 character limit and can't contain certain chars
                if (sheetName.length() > 31) {
                    sheetName = sheetName.substring(0, 31);
                }
                createLanguageSheet(workbook, sheetName, language, elements,
                        headerStyle, mandatoryYesStyle, mandatoryNoStyle, normalStyle, wrapStyle);
            }

            // Ensure Schema Info is the first sheet
            workbook.setSheetOrder("Schema Info", 0);

            // Write to file
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
            }

            logger.info("Excel export completed: {} sheets created (including Schema Info)", allLanguages.size() + 1);

        } catch (IOException e) {
            logger.error("Failed to export Data Dictionary to Excel", e);
            throw new RuntimeException("Failed to export Data Dictionary to Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Discovers all unique languages from the element documentation.
     */
    private Set<String> discoverLanguages(List<XsdExtendedElement> elements) {
        Set<String> languages = new LinkedHashSet<>();
        languages.add("default"); // Always include default first

        for (XsdExtendedElement element : elements) {
            if (element.getDocumentations() != null) {
                for (DocumentationInfo doc : element.getDocumentations()) {
                    if (doc.lang() != null && !doc.lang().isBlank()) {
                        languages.add(doc.lang().toLowerCase());
                    }
                }
            }
        }

        return languages;
    }

    /**
     * Creates a sheet for a specific language with all element data.
     */
    private void createLanguageSheet(XSSFWorkbook workbook, String sheetName, String language,
                                     List<XsdExtendedElement> elements,
                                     CellStyle headerStyle, CellStyle mandatoryYesStyle,
                                     CellStyle mandatoryNoStyle, CellStyle normalStyle,
                                     CellStyle wrapStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowNum = 0;

        // Create header row
        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(COLUMN_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        // Freeze header row
        sheet.createFreezePane(0, 1);

        // Create data rows
        int counter = 1;
        for (XsdExtendedElement element : elements) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;

            // #
            createCell(row, colNum++, String.valueOf(counter++), normalStyle);

            // XPath
            createCell(row, colNum++, htmlService.getCleanXPath(element), normalStyle);

            // Element Name
            createCell(row, colNum++, element.getElementName(), normalStyle);

            // Type
            createCell(row, colNum++, element.getElementType(), normalStyle);

            // Mandatory
            boolean isMandatory = element.isMandatory();
            Cell mandatoryCell = row.createCell(colNum++);
            mandatoryCell.setCellValue(isMandatory ? "Yes" : "No");
            mandatoryCell.setCellStyle(isMandatory ? mandatoryYesStyle : mandatoryNoStyle);

            // Min Occurs
            createCell(row, colNum++, getMinOccurs(element), normalStyle);

            // Max Occurs
            createCell(row, colNum++, getMaxOccurs(element), normalStyle);

            // Cardinality
            createCell(row, colNum++, htmlService.getCardinality(element), normalStyle);

            // Documentation (language-specific)
            createCell(row, colNum++, getDocumentationForLanguage(element, language), wrapStyle);

            // Type Documentation (language-specific)
            createCell(row, colNum++, getTypeDocumentationForLanguage(element, language), wrapStyle);

            // Restriction Base
            createCell(row, colNum++, getRestrictionBase(element), normalStyle);

            // Enumerations
            createCell(row, colNum++, getEnumerations(element), wrapStyle);

            // Pattern
            createCell(row, colNum++, getPattern(element), normalStyle);

            // Min/Max Length
            createCell(row, colNum++, getMinMaxLength(element), normalStyle);

            // Min/Max Value
            createCell(row, colNum++, getMinMaxValue(element), normalStyle);

            // Total/Fraction Digits
            createCell(row, colNum++, getTotalFractionDigits(element), normalStyle);

            // Sample Data
            createCell(row, colNum++, element.getDisplaySampleData(), normalStyle);

            // Level
            createCell(row, colNum++, String.valueOf(element.getLevel()), normalStyle);
        }

        // Set column widths
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTHS[i]);
        }

        // Enable auto-filter
        if (rowNum > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowNum - 1, 0, COLUMN_HEADERS.length - 1));
        }
    }

    private void createCell(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Gets the documentation for a specific language, with fallback to default.
     */
    private String getDocumentationForLanguage(XsdExtendedElement element, String language) {
        if (element.getDocumentations() == null || element.getDocumentations().isEmpty()) {
            return "";
        }

        // First try to find exact language match
        for (DocumentationInfo doc : element.getDocumentations()) {
            if (language.equalsIgnoreCase(doc.lang())) {
                return stripHtml(doc.content());
            }
        }

        // For "default" language, look for entries without lang or with "default"
        if ("default".equalsIgnoreCase(language)) {
            for (DocumentationInfo doc : element.getDocumentations()) {
                if (doc.lang() == null || "default".equalsIgnoreCase(doc.lang())) {
                    return stripHtml(doc.content());
                }
            }
            // If no default found, return first available
            return stripHtml(element.getDocumentations().get(0).content());
        }

        return "";
    }

    /**
     * Gets the type documentation for a specific language.
     */
    private String getTypeDocumentationForLanguage(XsdExtendedElement element, String language) {
        Map<String, String> typeDocs = htmlService.getTypeDocumentations(element.getCurrentXpath());
        if (typeDocs == null || typeDocs.isEmpty()) {
            return "";
        }

        // Try exact match first
        String doc = typeDocs.get(language);
        if (doc != null) {
            return stripHtml(doc);
        }

        // For default, try various fallbacks
        if ("default".equalsIgnoreCase(language)) {
            doc = typeDocs.get("default");
            if (doc != null) {
                return stripHtml(doc);
            }
            // Return first available
            return stripHtml(typeDocs.values().iterator().next());
        }

        return "";
    }

    /**
     * Strips HTML tags from content for Excel display.
     */
    private String stripHtml(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // Remove HTML tags
        String text = content.replaceAll("<[^>]+>", "");
        // Decode common HTML entities
        text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private String getMinOccurs(XsdExtendedElement element) {
        if (element.getCurrentNode() == null) {
            return "1";
        }
        return getAttributeValue(element.getCurrentNode(), "minOccurs", "1");
    }

    private String getMaxOccurs(XsdExtendedElement element) {
        if (element.getCurrentNode() == null) {
            return "1";
        }
        return getAttributeValue(element.getCurrentNode(), "maxOccurs", "1");
    }

    private String getRestrictionBase(XsdExtendedElement element) {
        if (element.getRestrictionInfo() != null && element.getRestrictionInfo().base() != null) {
            return element.getRestrictionInfo().base();
        }
        return "";
    }

    private String getEnumerations(XsdExtendedElement element) {
        if (element.getRestrictionInfo() == null || element.getRestrictionInfo().facets() == null) {
            return "";
        }
        List<String> enums = element.getRestrictionInfo().facets().get("enumeration");
        if (enums != null && !enums.isEmpty()) {
            return String.join(", ", enums);
        }
        return "";
    }

    private String getPattern(XsdExtendedElement element) {
        if (element.getRestrictionInfo() == null || element.getRestrictionInfo().facets() == null) {
            return "";
        }
        List<String> patterns = element.getRestrictionInfo().facets().get("pattern");
        if (patterns != null && !patterns.isEmpty()) {
            return String.join("; ", patterns);
        }
        return "";
    }

    private String getMinMaxLength(XsdExtendedElement element) {
        if (element.getRestrictionInfo() == null || element.getRestrictionInfo().facets() == null) {
            return "";
        }
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        List<String> parts = new ArrayList<>();

        List<String> length = facets.get("length");
        if (length != null && !length.isEmpty()) {
            parts.add("length=" + length.get(0));
        }

        List<String> minLength = facets.get("minLength");
        if (minLength != null && !minLength.isEmpty()) {
            parts.add("minLength=" + minLength.get(0));
        }

        List<String> maxLength = facets.get("maxLength");
        if (maxLength != null && !maxLength.isEmpty()) {
            parts.add("maxLength=" + maxLength.get(0));
        }

        return String.join(", ", parts);
    }

    private String getMinMaxValue(XsdExtendedElement element) {
        if (element.getRestrictionInfo() == null || element.getRestrictionInfo().facets() == null) {
            return "";
        }
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        List<String> parts = new ArrayList<>();

        List<String> minInclusive = facets.get("minInclusive");
        if (minInclusive != null && !minInclusive.isEmpty()) {
            parts.add("min=" + minInclusive.get(0));
        }

        List<String> maxInclusive = facets.get("maxInclusive");
        if (maxInclusive != null && !maxInclusive.isEmpty()) {
            parts.add("max=" + maxInclusive.get(0));
        }

        List<String> minExclusive = facets.get("minExclusive");
        if (minExclusive != null && !minExclusive.isEmpty()) {
            parts.add("min>" + minExclusive.get(0));
        }

        List<String> maxExclusive = facets.get("maxExclusive");
        if (maxExclusive != null && !maxExclusive.isEmpty()) {
            parts.add("max<" + maxExclusive.get(0));
        }

        return String.join(", ", parts);
    }

    private String getTotalFractionDigits(XsdExtendedElement element) {
        if (element.getRestrictionInfo() == null || element.getRestrictionInfo().facets() == null) {
            return "";
        }
        Map<String, List<String>> facets = element.getRestrictionInfo().facets();
        List<String> parts = new ArrayList<>();

        List<String> totalDigits = facets.get("totalDigits");
        if (totalDigits != null && !totalDigits.isEmpty()) {
            parts.add("totalDigits=" + totalDigits.get(0));
        }

        List<String> fractionDigits = facets.get("fractionDigits");
        if (fractionDigits != null && !fractionDigits.isEmpty()) {
            parts.add("fractionDigits=" + fractionDigits.get(0));
        }

        return String.join(", ", parts);
    }

    private String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) {
            return defaultValue;
        }
        Node attr = node.getAttributes().getNamedItem(attrName);
        return attr != null ? attr.getNodeValue() : defaultValue;
    }

    // ==================== Metadata Sheet Methods ====================

    /**
     * Initializes styles for the metadata sheet.
     */
    private void initMetadataStyles(Workbook workbook) {
        // Section header: Blue background, white bold text
        sectionHeaderStyle = workbook.createCellStyle();
        sectionHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        sectionHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 12);
        sectionHeaderStyle.setFont(headerFont);
        sectionHeaderStyle.setAlignment(HorizontalAlignment.LEFT);
        sectionHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Label: Bold, light grey background
        labelStyle = workbook.createCellStyle();
        labelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);
        labelStyle.setAlignment(HorizontalAlignment.LEFT);
        labelStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Value: Normal white background
        valueStyle = workbook.createCellStyle();
        valueStyle.setAlignment(HorizontalAlignment.LEFT);
        valueStyle.setVerticalAlignment(VerticalAlignment.CENTER);
    }

    /**
     * Creates the metadata sheet with export info, schema info, and statistics.
     */
    private void createMetadataSheet(Workbook workbook, List<XsdExtendedElement> elements, Set<String> languages) {
        Sheet sheet = workbook.createSheet("Schema Info");
        int rowNum = 0;

        // === SECTION 1: Export Information ===
        rowNum = createSectionHeader(sheet, rowNum, "Export Information");
        rowNum = createMetadataRow(sheet, rowNum, "Export Timestamp",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        rowNum = createMetadataRow(sheet, rowNum, "Generated by", "FreeXmlToolkit");
        // Add application version
        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
        String appVersion = metadataService != null ? metadataService.getAppVersion() : "Unknown";
        rowNum = createMetadataRow(sheet, rowNum, "Application Version", appVersion);
        rowNum++; // Empty row

        // === SECTION 2: Schema Information ===
        rowNum = createSectionHeader(sheet, rowNum, "Schema Information");
        rowNum = createMetadataRow(sheet, rowNum, "Schema File",
                getSchemaFileName(xsdDocumentationData.getXsdFilePath()));
        rowNum = createMetadataRow(sheet, rowNum, "Target Namespace",
                xsdDocumentationData.getTargetNamespace());
        rowNum = createMetadataRow(sheet, rowNum, "Version",
                xsdDocumentationData.getVersion());
        rowNum = createMetadataRow(sheet, rowNum, "Element Form Default",
                xsdDocumentationData.getElementFormDefault());
        rowNum = createMetadataRow(sheet, rowNum, "Attribute Form Default",
                xsdDocumentationData.getAttributeFormDefault());
        rowNum++; // Empty row

        // === SECTION 3: Statistics ===
        rowNum = createSectionHeader(sheet, rowNum, "Statistics");
        rowNum = createMetadataRow(sheet, rowNum, "Total Elements (Data Dictionary)",
                String.valueOf(elements.size()));
        rowNum = createMetadataRow(sheet, rowNum, "Global Elements",
                String.valueOf(xsdDocumentationData.getGlobalElements().size()));
        rowNum = createMetadataRow(sheet, rowNum, "Global Complex Types",
                String.valueOf(xsdDocumentationData.getGlobalComplexTypes().size()));
        rowNum = createMetadataRow(sheet, rowNum, "Global Simple Types",
                String.valueOf(xsdDocumentationData.getGlobalSimpleTypes().size()));

        // Additional calculated statistics
        long mandatoryCount = elements.stream().filter(XsdExtendedElement::isMandatory).count();
        long optionalCount = elements.size() - mandatoryCount;
        rowNum = createMetadataRow(sheet, rowNum, "Mandatory Elements", String.valueOf(mandatoryCount));
        rowNum = createMetadataRow(sheet, rowNum, "Optional Elements", String.valueOf(optionalCount));

        int maxDepth = elements.stream().mapToInt(XsdExtendedElement::getLevel).max().orElse(0);
        rowNum = createMetadataRow(sheet, rowNum, "Maximum Nesting Depth", String.valueOf(maxDepth));

        long elementsWithDocs = elements.stream()
                .filter(e -> e.getDocumentations() != null && !e.getDocumentations().isEmpty())
                .count();
        String docCoverage = elements.isEmpty() ? "0 (0.0%)" :
                String.format("%d (%.1f%%)", elementsWithDocs, 100.0 * elementsWithDocs / elements.size());
        rowNum = createMetadataRow(sheet, rowNum, "Elements with Documentation", docCoverage);

        long elementsWithRestrictions = elements.stream()
                .filter(e -> e.getRestrictionInfo() != null &&
                        e.getRestrictionInfo().facets() != null &&
                        !e.getRestrictionInfo().facets().isEmpty())
                .count();
        rowNum = createMetadataRow(sheet, rowNum, "Elements with Restrictions",
                String.valueOf(elementsWithRestrictions));

        long elementsWithEnumerations = elements.stream()
                .filter(e -> e.getRestrictionInfo() != null &&
                        e.getRestrictionInfo().facets() != null &&
                        e.getRestrictionInfo().facets().containsKey("enumeration"))
                .count();
        rowNum = createMetadataRow(sheet, rowNum, "Elements with Enumerations",
                String.valueOf(elementsWithEnumerations));

        long elementsWithPatterns = elements.stream()
                .filter(e -> e.getRestrictionInfo() != null &&
                        e.getRestrictionInfo().facets() != null &&
                        e.getRestrictionInfo().facets().containsKey("pattern"))
                .count();
        rowNum = createMetadataRow(sheet, rowNum, "Elements with Patterns",
                String.valueOf(elementsWithPatterns));
        rowNum++; // Empty row

        // === SECTION 4: Namespaces ===
        if (xsdDocumentationData.getNamespaces() != null &&
                !xsdDocumentationData.getNamespaces().isEmpty()) {
            rowNum = createSectionHeader(sheet, rowNum, "Namespaces");
            for (Map.Entry<String, String> ns : xsdDocumentationData.getNamespaces().entrySet()) {
                String prefix = ns.getKey().isEmpty() ? "(default)" : ns.getKey();
                rowNum = createMetadataRow(sheet, rowNum, prefix, ns.getValue());
            }
            rowNum++; // Empty row
        }

        // === SECTION 5: Documentation Languages ===
        rowNum = createSectionHeader(sheet, rowNum, "Documentation Languages");
        rowNum = createMetadataRow(sheet, rowNum, "Languages Found",
                String.join(", ", languages.stream().map(String::toUpperCase).toList()));
        rowNum = createMetadataRow(sheet, rowNum, "Number of Language Sheets",
                String.valueOf(languages.size()));

        // Set column widths
        sheet.setColumnWidth(0, 10000);  // Label column
        sheet.setColumnWidth(1, 20000);  // Value column

        logger.debug("Created Schema Info sheet with {} rows", rowNum);
    }

    /**
     * Creates a section header row in the metadata sheet.
     */
    private int createSectionHeader(Sheet sheet, int rowNum, String title) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(sectionHeaderStyle);
        // Merge cells for section header
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowNum, rowNum, 0, 1));
        return rowNum + 1;
    }

    /**
     * Creates a data row with label and value in the metadata sheet.
     */
    private int createMetadataRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "");
        valueCell.setCellStyle(valueStyle);

        return rowNum + 1;
    }

    /**
     * Extracts the filename from a file path.
     */
    private String getSchemaFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }

    // ==================== Cell Style Methods ====================

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createMandatoryYesStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createMandatoryNoStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createWrapStyle(Workbook workbook) {
        CellStyle style = createNormalStyle(workbook);
        style.setWrapText(true);
        return style;
    }
}
