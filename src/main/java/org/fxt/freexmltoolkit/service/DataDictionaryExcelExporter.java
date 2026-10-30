package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.fxt.freexmltoolkit.util.MarkdownSupport;
import org.w3c.dom.Node;

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

    /** Index of the "Mandatory" column, styled by its value. */
    private static final int MANDATORY_COLUMN = 4;

    /** How each column's cells look (parallel to {@link #COLUMN_HEADERS}). */
    private static final DataDictionaryExcelStyles.Role[] COLUMN_ROLES = {
            DataDictionaryExcelStyles.Role.CENTER,  // #
            DataDictionaryExcelStyles.Role.XPATH,   // XPath
            DataDictionaryExcelStyles.Role.NAME,    // Element Name
            DataDictionaryExcelStyles.Role.MONO,    // Type
            DataDictionaryExcelStyles.Role.CENTER,  // Mandatory (replaced by its value's role)
            DataDictionaryExcelStyles.Role.CENTER,  // Min Occurs
            DataDictionaryExcelStyles.Role.CENTER,  // Max Occurs
            DataDictionaryExcelStyles.Role.CENTER,  // Cardinality
            DataDictionaryExcelStyles.Role.WRAP,    // Documentation
            DataDictionaryExcelStyles.Role.WRAP,    // Type Documentation
            DataDictionaryExcelStyles.Role.MONO,    // Restriction Base
            DataDictionaryExcelStyles.Role.WRAP,    // Enumerations
            DataDictionaryExcelStyles.Role.MONO,    // Pattern
            DataDictionaryExcelStyles.Role.TEXT,    // Min/Max Length
            DataDictionaryExcelStyles.Role.TEXT,    // Min/Max Value
            DataDictionaryExcelStyles.Role.TEXT,    // Total/Fraction Digits
            DataDictionaryExcelStyles.Role.TEXT,    // Sample Data
            DataDictionaryExcelStyles.Role.CENTER   // Level
    };

    private static final int[] COLUMN_WIDTHS = {
            2000,   // #
            14000,  // XPath
            6000,   // Element Name
            6000,   // Type
            3000,   // Mandatory
            3000,   // Min Occurs
            3000,   // Max Occurs
            4000,   // Cardinality
            16000,  // Documentation
            16000,  // Type Documentation
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

    // Styles of the Schema Info sheet's helpers (set by createMetadataSheet)
    private DataDictionaryExcelStyles metadataStyles;

    /**
     * Creates a new exporter.
     *
     * @param xsdDocumentationData The documentation data.
     * @param htmlService The HTML service.
     */
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
        exportToExcel(outputFile, elements, null);
    }

    /**
     * Exports the Data Dictionary to an Excel file with one sheet per language.
     * When includedLanguages is specified, only those languages will be exported.
     *
     * @param outputFile        the output Excel file
     * @param elements          the list of elements to export
     * @param includedLanguages the languages to include, or null for all languages
     */
    public void exportToExcel(File outputFile, List<XsdExtendedElement> elements, Set<String> includedLanguages) {
        logger.info("Exporting Data Dictionary to Excel: {}", outputFile.getAbsolutePath());

        // Use the streaming SXSSF API: with potentially many language sheets x tens of thousands of
        // rows, the in-memory XSSF model exhausts the heap (OutOfMemoryError). SXSSF keeps only a
        // small window of rows in memory and flushes the rest to temporary files, giving constant
        // memory and far higher throughput. close() (try-with-resources) removes those temp files.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW)) {
            workbook.setCompressTempFiles(true);
            // Set document metadata (on the backing XSSF workbook)
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            if (metadataService != null) {
                metadataService.setExcelMetadata(workbook.getXSSFWorkbook(), "Data Dictionary - XSD Documentation");
            }

            // Discover all languages
            Set<String> allLanguages = discoverLanguages(elements);
            logger.debug("Discovered languages: {}", allLanguages);

            // Filter languages if includedLanguages is specified
            Set<String> languagesToExport;
            if (includedLanguages != null && !includedLanguages.isEmpty()) {
                // Create a case-insensitive filter
                Set<String> lowerCaseFilter = includedLanguages.stream()
                        .filter(Objects::nonNull)  // Filter out null values
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                // Always include "default" if it's in the discovered languages and in the filter (or no filter)
                languagesToExport = new LinkedHashSet<>();
                for (String lang : allLanguages) {
                    if (lowerCaseFilter.contains(lang.toLowerCase())) {
                        languagesToExport.add(lang);
                    }
                }
                // If filter specified but no matching languages found, fall back to all
                if (languagesToExport.isEmpty()) {
                    logger.warn("No matching languages found for filter {}, exporting all languages", includedLanguages);
                    languagesToExport = allLanguages;
                } else {
                    logger.info("Filtered languages for Excel export: {}", languagesToExport);
                }
            } else {
                languagesToExport = allLanguages;
            }

            DataDictionaryExcelStyles styles = new DataDictionaryExcelStyles(workbook);

            // Create metadata sheet FIRST (will be moved to position 0)
            createMetadataSheet(workbook, styles, elements, languagesToExport);

            // Pre-compute the language-INDEPENDENT columns (XPath, type, occurs, restrictions,
            // sample data, ...) once per element instead of recomputing them for every one of the
            // potentially many language sheets. Only the two documentation columns vary per language.
            List<PrecomputedRow> precomputedRows = elements.parallelStream()
                    .map(this::precomputeRow)
                    .toList();

            // Create one sheet per language
            for (String language : languagesToExport) {
                String sheetName = language.toUpperCase();
                // Excel sheet names have a 31 character limit and can't contain certain chars
                if (sheetName.length() > 31) {
                    sheetName = sheetName.substring(0, 31);
                }
                createLanguageSheet(workbook, sheetName, language, precomputedRows, styles);
            }

            // Ensure Schema Info is the first sheet
            workbook.setSheetOrder("Schema Info", 0);

            // Write to file
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
            }

            logger.info("Excel export completed: {} sheets created (including Schema Info)", languagesToExport.size() + 1);

        } catch (IOException e) {
            logger.error("Failed to export Data Dictionary to Excel", e);
            throw new RuntimeException("Failed to export Data Dictionary to Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Maximum number of rows SXSSF keeps in memory per sheet before flushing older rows to disk.
     */
    private static final int SXSSF_ROW_WINDOW = 200;

    /**
     * Pre-computed, language-independent cell values for a single data-dictionary row. The owning
     * {@link XsdExtendedElement} is retained so the two language-specific documentation columns can
     * still be resolved per sheet.
     */
    private record PrecomputedRow(XsdExtendedElement element, String cleanXPath, String elementName,
                                  String elementType, boolean mandatory, String minOccurs,
                                  String maxOccurs, String cardinality, String restrictionBase,
                                  String enumerations, String pattern, String minMaxLength,
                                  String minMaxValue, String totalFractionDigits, String sampleData,
                                  String level) {
    }

    /**
     * Resolves all language-independent column values for the given element once.
     */
    private PrecomputedRow precomputeRow(XsdExtendedElement element) {
        return new PrecomputedRow(
                element,
                htmlService.getCleanXPath(element),
                element.getElementName(),
                element.getElementType(),
                element.isMandatory(),
                getMinOccurs(element),
                getMaxOccurs(element),
                htmlService.getCardinality(element),
                getRestrictionBase(element),
                getEnumerations(element),
                getPattern(element),
                getMinMaxLength(element),
                getMinMaxValue(element),
                getTotalFractionDigits(element),
                element.getDisplaySampleData(),
                String.valueOf(element.getLevel()));
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
    private void createLanguageSheet(Workbook workbook, String sheetName, String language,
                                     List<PrecomputedRow> rows, DataDictionaryExcelStyles styles) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(false);
        if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet streaming) {
            streaming.setTabColor(DataDictionaryExcelStyles.color(DataDictionaryExcelStyles.ACCENT_RULE));
        }
        int rowNum = 0;

        // Create header row
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(COLUMN_HEADERS[i]);
            cell.setCellStyle(COLUMN_ROLES[i] == DataDictionaryExcelStyles.Role.CENTER
                    ? styles.headerCenter : styles.header);
        }

        // Freeze header row
        sheet.createFreezePane(0, 1);

        // Create data rows (language-independent values are pre-computed; only the two
        // documentation columns are resolved per language here)
        int counter = 1;
        for (PrecomputedRow data : rows) {
            boolean stripe = rowNum % 2 == 0;
            Row row = sheet.createRow(rowNum++);
            String[] values = {
                    String.valueOf(counter++),
                    data.cleanXPath(),
                    data.elementName(),
                    data.elementType(),
                    data.mandatory() ? "Yes" : "No",
                    data.minOccurs(),
                    data.maxOccurs(),
                    data.cardinality(),
                    getDocumentationForLanguage(data.element(), language),
                    getTypeDocumentationForLanguage(data.element(), language),
                    data.restrictionBase(),
                    data.enumerations(),
                    data.pattern(),
                    data.minMaxLength(),
                    data.minMaxValue(),
                    data.totalFractionDigits(),
                    data.sampleData(),
                    data.level()
            };
            for (int col = 0; col < values.length; col++) {
                DataDictionaryExcelStyles.Role role = col == MANDATORY_COLUMN
                        ? (data.mandatory() ? DataDictionaryExcelStyles.Role.MANDATORY_YES
                                : DataDictionaryExcelStyles.Role.MANDATORY_NO)
                        : COLUMN_ROLES[col];
                createCell(row, col, values[col], styles.body(role, stripe));
            }
        }

        // Set column widths
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTHS[i]);
        }

        printOnePageWide(sheet, true);
        sheet.setRepeatingRows(org.apache.poi.ss.util.CellRangeAddress.valueOf("1:1"));

        // Enable auto-filter
        if (rowNum > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowNum - 1, 0, COLUMN_HEADERS.length - 1));
        }
    }

    /** Prints the sheet one page wide (as many pages tall as needed), landscape for the wide tables. */
    private static void printOnePageWide(Sheet sheet, boolean landscape) {
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(landscape);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
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
        return MarkdownSupport.toPlainText(content);
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
     * Creates the metadata sheet with export info, schema info, and statistics.
     */
    private void createMetadataSheet(Workbook workbook, DataDictionaryExcelStyles styles,
                                     List<XsdExtendedElement> elements, Set<String> languages) {
        Sheet sheet = workbook.createSheet("Schema Info");
        sheet.setDisplayGridlines(false);
        if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet streaming) {
            streaming.setTabColor(DataDictionaryExcelStyles.color(DataDictionaryExcelStyles.HEADER_FILL));
        }
        metadataStyles = styles;
        int rowNum = 0;

        // Title
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        String schemaName = getSchemaFileName(xsdDocumentationData.getXsdFilePath());
        titleCell.setCellValue(schemaName.isEmpty() ? "Data Dictionary" : "Data Dictionary \u2014 " + schemaName);
        titleCell.setCellStyle(styles.title);
        rowNum++; // Empty row

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
        printOnePageWide(sheet, false);

        logger.debug("Created Schema Info sheet with {} rows", rowNum);
    }

    /**
     * Creates a section header row in the metadata sheet.
     */
    private int createSectionHeader(Sheet sheet, int rowNum, String title) {
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(22);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(metadataStyles.section);
        // The rule below the title spans both columns
        row.createCell(1).setCellStyle(metadataStyles.section);
        return rowNum + 1;
    }

    /**
     * Creates a data row with label and value in the metadata sheet.
     */
    private int createMetadataRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(metadataStyles.label);

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "");
        valueCell.setCellStyle(metadataStyles.value);

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
}
