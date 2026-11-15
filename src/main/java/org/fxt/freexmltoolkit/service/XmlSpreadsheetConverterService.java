/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.excel.FxtExcelTheme;
import org.fxt.freexmltoolkit.service.spreadsheet.RecordTableLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Service for converting between XML and spreadsheet formats (Excel XLSX/XLS, CSV)
 * Provides bidirectional conversion with full XML structure preservation
 */
public class XmlSpreadsheetConverterService {
    private static final Logger logger = LogManager.getLogger(XmlSpreadsheetConverterService.class);

    /** Rows an {@link SXSSFWorkbook} keeps in memory; older rows are flushed to a temp file. */
    private static final int STREAMING_WINDOW_ROWS = 100;
    /** Rows sampled for the column widths (auto-sizing every row of a large sheet took minutes). */
    private static final int WIDTH_SAMPLE_ROWS = 1000;
    /** Excel's maximum column width in characters. */
    private static final int MAX_COLUMN_CHARS = 255;
    /** Widest value column in characters; longer values stay readable in the cell / formula bar. */
    private static final int MAX_VALUE_CHARS = 80;

    /** Maximum rows of an .xlsx sheet; package-private and mutable so tests can use a small limit. */
    static int maxXlsxRows = SpreadsheetVersion.EXCEL2007.getMaxRows();
    /** Maximum rows of an .xls sheet; package-private and mutable so tests can use a small limit. */
    static int maxXlsRows = SpreadsheetVersion.EXCEL97.getMaxRows();

    /**
     * Thrown when the document yields more rows than one sheet of the target format holds.
     * This is a property of the input, not a failure of the converter: CSV has no such limit.
     */
    public static final class RowLimitExceededException extends Exception {
        private final int rows;
        private final int maxRows;

        public RowLimitExceededException(int rows, int maxRows) {
            super(String.format("%,d rows exceed the limit of %,d rows per sheet", rows, maxRows));
            this.rows = rows;
            this.maxRows = maxRows;
        }

        /** @return the rows the export would need (including the header row) */
        public int getRows() {
            return rows;
        }

        /** @return the maximum rows of one sheet in the target format */
        public int getMaxRows() {
            return maxRows;
        }
    }

    /**
     * Represents a row in the spreadsheet with XPath, Value, and Type information
     */
    public static class RowData {
        private String xpath;
        private String value;
        private String nodeType;
        private int originalIndex;

        public RowData(String xpath, String value, String nodeType) {
            this.xpath = xpath;
            this.value = value != null ? value : "";
            this.nodeType = nodeType;
            this.originalIndex = -1; // Will be set by the calling code
        }

        public RowData(String xpath, String value, String nodeType, int originalIndex) {
            this.xpath = xpath;
            this.value = value != null ? value : "";
            this.nodeType = nodeType;
            this.originalIndex = originalIndex;
        }

        // Getters and setters
        public String getXpath() {
            return xpath;
        }

        public void setXpath(String xpath) {
            this.xpath = xpath;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public int getOriginalIndex() {
            return originalIndex;
        }

        public void setOriginalIndex(int originalIndex) {
            this.originalIndex = originalIndex;
        }

        @Override
        public String toString() {
            return String.format("RowData{xpath='%s', value='%s', type='%s'}", xpath, value, nodeType);
        }
    }

    /** How an export lays out the spreadsheet. */
    public enum Layout {
        /** One row per node: XPath, value, type - the exact way back. */
        XPATH_LIST,
        /** One row per record, one column per value ({@link RecordTableLayout}). */
        TABLE
    }

    /** Sheet of the records in the table layout. */
    public static final String RECORDS_SHEET = "Records";
    /** Sheet of the values outside the records (XPath form) in the table layout. */
    public static final String OTHER_SHEET = "Other data";
    /** Sheet explaining the workbook. */
    public static final String INFO_SHEET = "Info";
    /** Label of the info row naming the record path; read back by the import. */
    public static final String INFO_RECORD_LABEL = "Record element";

    /**
     * Configuration for conversion options
     */
    public static class ConversionConfig {
        private boolean includeComments = true;
        private boolean includeNamespaces = true;
        private boolean includeCData = true;
        private boolean includeTypeColumn = true;
        private boolean prettyPrintXml = true;
        private Layout layout = Layout.XPATH_LIST;
        private String recordPath;
        private String rootName = "Records";
        private String recordName = "Record";

        /** @return how an export lays out the spreadsheet (an import reads the layout from the file) */
        public Layout getLayout() {
            return layout;
        }

        public void setLayout(Layout layout) {
            this.layout = layout == null ? Layout.XPATH_LIST : layout;
        }

        /** @return the record of a table export (path without positions), {@code null} for the default */
        public String getRecordPath() {
            return recordPath;
        }

        public void setRecordPath(String recordPath) {
            this.recordPath = recordPath == null || recordPath.isBlank() ? null : recordPath;
        }

        /** @return the document element a foreign table (no XPath column, no info sheet) is imported into */
        public String getRootName() {
            return rootName;
        }

        public void setRootName(String rootName) {
            this.rootName = rootName == null || rootName.isBlank() ? "Records" : rootName.strip();
        }

        /** @return the element each row of a foreign table becomes */
        public String getRecordName() {
            return recordName;
        }

        public void setRecordName(String recordName) {
            this.recordName = recordName == null || recordName.isBlank() ? "Record" : recordName.strip();
        }

        // Getters and setters
        public boolean isIncludeComments() {
            return includeComments;
        }

        public void setIncludeComments(boolean includeComments) {
            this.includeComments = includeComments;
        }

        public boolean isIncludeNamespaces() {
            return includeNamespaces;
        }

        public void setIncludeNamespaces(boolean includeNamespaces) {
            this.includeNamespaces = includeNamespaces;
        }

        public boolean isIncludeCData() {
            return includeCData;
        }

        public void setIncludeCData(boolean includeCData) {
            this.includeCData = includeCData;
        }

        public boolean isIncludeTypeColumn() {
            return includeTypeColumn;
        }

        public void setIncludeTypeColumn(boolean includeTypeColumn) {
            this.includeTypeColumn = includeTypeColumn;
        }

        public boolean isPrettyPrintXml() {
            return prettyPrintXml;
        }

        public void setPrettyPrintXml(boolean prettyPrintXml) {
            this.prettyPrintXml = prettyPrintXml;
        }
    }

    /**
     * Converts XML document to Excel file; every value is written as text.
     */
    public void convertXmlToExcel(Document doc, File outputFile, ConversionConfig config) throws Exception {
        convertXmlToExcel(doc, outputFile, config, SpreadsheetTypeResolver.NONE);
    }

    /**
     * Converts XML document to Excel file, storing each value as the cell type {@code types} names
     * for it (number, boolean, date …) when its lexical form survives that losslessly.
     *
     * @param types the value types, usually from the bound XSD; {@code null} for text only
     */
    public void convertXmlToExcel(Document doc, File outputFile, ConversionConfig config,
                                  SpreadsheetTypeResolver types) throws Exception {
        logger.info("Converting XML to Excel: {}", outputFile.getName());
        SpreadsheetTypeResolver resolver = types == null ? SpreadsheetTypeResolver.NONE : types;

        List<RowData> rows = extractRowsFromXml(doc, config);

        boolean isXlsx = outputFile.getName().toLowerCase().endsWith(".xlsx");
        int maxRows = isXlsx ? maxXlsxRows : maxXlsRows;
        if (config.getLayout() == Layout.TABLE) {
            writeTableWorkbook(toTable(rows, config), outputFile, config, resolver, isXlsx, maxRows);
            return;
        }
        if (rows.size() + 1 > maxRows) { // + header row
            throw new RowLimitExceededException(rows.size() + 1, maxRows);
        }
        // .xlsx streams its rows to a temp file: an in-memory XSSFWorkbook needs about 1 KB per
        // cell, so a large document (telemetry: 56 MB XML) filled a 4 GB heap and froze the UI.
        SXSSFWorkbook streaming = isXlsx ? new SXSSFWorkbook(STREAMING_WINDOW_ROWS) : null;
        try (Workbook workbook = isXlsx ? streaming : new HSSFWorkbook()) {
            if (streaming != null) {
                ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
                metadataService.setExcelMetadata(streaming.getXSSFWorkbook(), "XML to Excel Conversion");
            }

            XmlSpreadsheetExcelStyles styles = new XmlSpreadsheetExcelStyles(workbook);
            Sheet sheet = workbook.createSheet("XML Structure");
            FxtExcelTheme.tableSheet(sheet);
            createExcelHeader(sheet, config, styles);
            populateExcelSheet(sheet, rows, config, styles, resolver);
            formatExcelSheet(sheet, rows, config);
            writeInfoSheet(workbook, streaming != null, null, rows.size());

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            logger.info("Successfully converted XML to Excel with {} rows", rows.size());
        } // closing an SXSSFWorkbook also deletes the temp file of its flushed rows
    }

    /**
     * Converts XML document to CSV file; every value is text.
     */
    public void convertXmlToCsv(Document doc, File outputFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config) throws Exception {
        convertXmlToCsv(doc, outputFile, csvConfig, config, SpreadsheetTypeResolver.NONE);
    }

    /**
     * Converts XML document to CSV file. With a schema-based {@code types}, a "Data Type" column names
     * each value's XSD built-in type, values that fit it are written bare and every text field quoted.
     *
     * @param types the value types, usually from the bound XSD; {@code null} for text only
     */
    public void convertXmlToCsv(Document doc, File outputFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config,
                                SpreadsheetTypeResolver types) throws Exception {
        logger.info("Converting XML to CSV: {}", outputFile.getName());

        List<RowData> rows = extractRowsFromXml(doc, config);

        CsvHandler csvHandler = new CsvHandler();
        if (config.getLayout() == Layout.TABLE) {
            csvHandler.writeTableCsv(toTable(rows, config), outputFile, csvConfig);
            return;
        }
        csvHandler.writeCsv(rows, outputFile, csvConfig, config, types == null ? SpreadsheetTypeResolver.NONE : types);

        logger.info("Successfully converted XML to CSV with {} rows", rows.size());
    }

    /**
     * Converts Excel file to XML document; each value is read as its cell type and format suggest.
     */
    public Document convertExcelToXml(File excelFile, ConversionConfig config) throws Exception {
        return convertExcelToXml(excelFile, config, rootElement -> SpreadsheetTypeResolver.NONE);
    }

    /**
     * Converts Excel file to XML document, reading each value as the schema type {@code typesFor}
     * names for it (see {@link ExcelCellLexical}): a date cell under {@code xs:dateTime} gets a time,
     * a serial number under {@code xs:date} becomes a date, {@code 1}/{@code 0} under {@code xs:boolean}
     * become {@code true}/{@code false}.
     *
     * @param typesFor given the document element with its attributes as XML text (so its
     *                 {@code xsi:schemaLocation} can be read), the value types to use; may return
     *                 {@code null} or {@link SpreadsheetTypeResolver#NONE} for none
     */
    public Document convertExcelToXml(File excelFile, ConversionConfig config,
                                      java.util.function.Function<String, SpreadsheetTypeResolver> typesFor) throws Exception {
        logger.info("Converting Excel to XML: {}", excelFile.getName());

        List<RowData> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (!isXPathList(sheet)) {
                Document doc = buildXmlFromRows(readTableWorkbook(workbook, config, typesFor), config);
                logger.info("Converted the Excel table to XML");
                return doc;
            }
            SpreadsheetTypeResolver types = typesFor == null ? null : typesFor.apply(rootElementXml(sheet, config));
            readExcelSheet(sheet, rows, types == null ? SpreadsheetTypeResolver.NONE : types);
        }

        Document doc = buildXmlFromRows(rows, config);
        logger.info("Successfully converted Excel to XML with {} rows", rows.size());
        return doc;
    }

    /** @return the document element and its attributes (namespace and schema declarations) as XML */
    private String rootElementXml(Sheet sheet, ConversionConfig config) throws Exception {
        List<RowData> rootRows = new ArrayList<>();
        readExcelSheet(sheet, rootRows, SpreadsheetTypeResolver.NONE, XmlSpreadsheetConverterService::isRootRow);
        return rootElementXml(rootRows, config);
    }

    /** @return whether the XPath is the document element or one of its attributes */
    private static boolean isRootRow(String xpath) {
        return xpath != null && xpath.matches("/[^/]+(/@[^/]+)?");
    }

    /** @return the document element and its attributes, from the rows of a whole sheet, as XML */
    private String rootElementXml(List<RowData> rows, ConversionConfig config) throws Exception {
        List<RowData> rootRows = new ArrayList<>();
        for (RowData row : rows) {
            boolean elementOrAttribute = "element".equals(row.getNodeType()) || "attribute".equals(row.getNodeType());
            if (elementOrAttribute && isRootRow(row.getXpath())) {
                // copies: the element's own text does not matter, only its start tag
                rootRows.add(new RowData(row.getXpath(), "element".equals(row.getNodeType()) ? "" : row.getValue(),
                        row.getNodeType(), row.getOriginalIndex()));
            }
        }
        return rootRows.isEmpty() ? "" : documentToString(buildXmlFromRows(rootRows, config), config);
    }

    /**
     * Converts CSV file to XML document
     */
    public Document convertCsvToXml(File csvFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config) throws Exception {
        return convertCsvToXml(csvFile, csvConfig, config, rootElement -> SpreadsheetTypeResolver.NONE);
    }

    /**
     * Converts CSV file to XML document, repairing each value for the schema type {@code typesFor}
     * names for it ({@link XsdLexicalNormalizer}): {@code 31.01.2024} under {@code xs:date} becomes
     * {@code 2024-01-31}, {@code 10,50} under {@code xs:decimal} {@code 10.50}. A value already valid for
     * its type is kept as written. The delimiter hints at the locale: a semicolon file reads {@code ,}
     * as decimal separator and {@code 1/2/2024} as 1 February, a comma file the other way round.
     *
     * @param typesFor given the document element with its attributes as XML text, the value types to use;
     *                 may return {@code null} or {@link SpreadsheetTypeResolver#NONE} for none
     */
    public Document convertCsvToXml(File csvFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config,
                                    java.util.function.Function<String, SpreadsheetTypeResolver> typesFor) throws Exception {
        logger.info("Converting CSV to XML: {}", csvFile.getName());

        CsvHandler csvHandler = new CsvHandler();
        List<List<String>> records = csvHandler.readAllRecords(csvFile, csvConfig);
        List<RowData> rows = !records.isEmpty() && isXPathHeader(records.getFirst().isEmpty() ? "" : records.getFirst().getFirst())
                ? csvHandler.readCsv(csvFile, csvConfig, config)
                : csvTableRows(records, config);

        SpreadsheetTypeResolver types = typesFor == null ? null : typesFor.apply(rootElementXml(rows, config));
        if (types != null && types.isSchemaBased()) {
            XsdLexicalNormalizer.LocaleHint hint = XsdLexicalNormalizer.LocaleHint.ofCsvDelimiter(csvConfig.getDelimiter());
            for (RowData row : rows) {
                String builtin = builtinTypeOf(row, types);
                if (builtin != null) {
                    row.setValue(XsdLexicalNormalizer.normalize(SpreadsheetCellKind.fromBuiltin(builtin), row.getValue(), hint));
                }
            }
        }

        Document doc = buildXmlFromRows(rows, config);
        logger.info("Successfully converted CSV to XML with {} rows", rows.size());
        return doc;
    }

    /**
     * Extracts row data from XML document
     */
    public List<RowData> extractRowsFromXml(Document doc, ConversionConfig config) {
        List<RowData> rows = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger indexCounter = new java.util.concurrent.atomic.AtomicInteger(0);

        if (doc.getDocumentElement() != null) {
            Element root = doc.getDocumentElement();
            traverseNode(root, buildXPath(root, ""), rows, config, indexCounter);
        }

        return rows;
    }

    /**
     * Recursively traverses XML nodes and extracts data.
     *
     * @param xpath the node's own XPath, computed by the caller: the parent knows each child's
     *              position from one pass over its children (counting the preceding siblings per
     *              child made wide parents quadratic - an export of 600,000 sibling elements never finished)
     */
    private void traverseNode(Node node, String xpath, List<RowData> rows, ConversionConfig config,
                              java.util.concurrent.atomic.AtomicInteger indexCounter) {
        if (node == null) {
            return;
        }

        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> {
                Element element = (Element) node;

                // Add element itself
                String elementValue;
                boolean hasOnlyTextChild = hasOnlyTextContent(element);

                if (hasOnlyTextChild) {
                    elementValue = element.getTextContent().trim();
                } else {
                    elementValue = "";
                }
                rows.add(new RowData(xpath, elementValue, "element", indexCounter.getAndIncrement()));

                // Add attributes
                if (element.hasAttributes()) {
                    NamedNodeMap attributes = element.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        Node attr = attributes.item(i);
                        String attrXPath = xpath + "/@" + attr.getNodeName();
                        rows.add(new RowData(attrXPath, attr.getNodeValue(), "attribute", indexCounter.getAndIncrement()));
                    }
                }

                // Traverse child nodes if not a simple text element
                if (!hasOnlyTextChild) {
                    Map<String, Integer> elementCounts = new HashMap<>();

                    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            elementCounts.merge(child.getNodeName(), 1, Integer::sum);
                        }
                    }

                    Map<String, Integer> currentCounts = new HashMap<>();

                    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                        switch (child.getNodeType()) {
                            case Node.ELEMENT_NODE -> {
                                String childName = child.getNodeName();
                                int currentCount = currentCounts.merge(childName, 1, Integer::sum);
                                String childXPath = xpath + "/" + childName
                                        + (elementCounts.get(childName) > 1 ? "[" + currentCount + "]" : "");

                                traverseNode(child, childXPath, rows, config, indexCounter);
                            }
                            case Node.TEXT_NODE -> {
                                String textContent = child.getNodeValue();
                                if (textContent != null && !textContent.trim().isEmpty()) {
                                    String textXPath = xpath + "/text()";
                                    rows.add(new RowData(textXPath, textContent.trim(), "text", indexCounter.getAndIncrement()));
                                }
                            }
                            case Node.COMMENT_NODE -> {
                                if (config.isIncludeComments()) {
                                    String commentXPath = xpath + "/comment()";
                                    rows.add(new RowData(commentXPath, child.getNodeValue(), "comment", indexCounter.getAndIncrement()));
                                }
                            }
                            case Node.CDATA_SECTION_NODE -> {
                                if (config.isIncludeCData()) {
                                    String cdataXPath = xpath + "/cdata()";
                                    rows.add(new RowData(cdataXPath, child.getNodeValue(), "cdata", indexCounter.getAndIncrement()));
                                }
                            }
                            default -> { }
                        }
                    }
                }
            }
            case Node.TEXT_NODE -> {
                String textContent = node.getNodeValue();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    rows.add(new RowData(xpath + "/text()", textContent.trim(), "text", indexCounter.getAndIncrement()));
                }
            }
            case Node.COMMENT_NODE -> {
                if (config.isIncludeComments()) {
                    rows.add(new RowData(xpath + "/comment()", node.getNodeValue(), "comment", indexCounter.getAndIncrement()));
                }
            }
            case Node.CDATA_SECTION_NODE -> {
                if (config.isIncludeCData()) {
                    rows.add(new RowData(xpath + "/cdata()", node.getNodeValue(), "cdata", indexCounter.getAndIncrement()));
                }
            }
            default -> { }
        }
    }

    /**
     * Builds XPath for a given node
     */
    private String buildXPath(Node node, String parentXPath) {
        if (node == null || node.getNodeType() == Node.DOCUMENT_NODE) {
            return "";
        }

        String nodeName = node.getNodeName();

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            // Count siblings with same name to determine position
            int position = 1;
            Node sibling = node.getPreviousSibling();
            while (sibling != null) {
                if (sibling.getNodeType() == Node.ELEMENT_NODE &&
                        sibling.getNodeName().equals(nodeName)) {
                    position++;
                }
                sibling = sibling.getPreviousSibling();
            }

            // Check if there are multiple elements with same name
            boolean hasMultipleSiblings = false;
            sibling = node.getParentNode().getFirstChild();
            int count = 0;
            while (sibling != null) {
                if (sibling.getNodeType() == Node.ELEMENT_NODE &&
                        sibling.getNodeName().equals(nodeName)) {
                    count++;
                    if (count > 1) {
                        hasMultipleSiblings = true;
                        break;
                    }
                }
                sibling = sibling.getNextSibling();
            }

            if (parentXPath.isEmpty()) {
                return "/" + nodeName + (hasMultipleSiblings ? "[" + position + "]" : "");
            } else {
                return parentXPath + "/" + nodeName + (hasMultipleSiblings ? "[" + position + "]" : "");
            }
        }

        return parentXPath;
    }

    /**
     * Checks if element has only text content (no child elements)
     */
    private boolean hasOnlyTextContent(Element element) {
        NodeList children = element.getChildNodes();
        int textNodeCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            } else if (child.getNodeType() == Node.TEXT_NODE &&
                    child.getNodeValue() != null &&
                    !child.getNodeValue().trim().isEmpty()) {
                textNodeCount++;
            } else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                return false; // CDATA should be handled separately
            }
        }
        return textNodeCount == 1;
    }

    /**
     * Creates Excel header row (frozen, repeated on every printed page)
     */
    private void createExcelHeader(Sheet sheet, ConversionConfig config, XmlSpreadsheetExcelStyles styles) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        header.createCell(0).setCellValue("XPath");
        header.createCell(1).setCellValue("Value");
        header.getCell(0).setCellStyle(styles.header);
        header.getCell(1).setCellStyle(styles.header);
        if (config.isIncludeTypeColumn()) {
            Cell typeCell = header.createCell(2);
            typeCell.setCellValue("Type");
            typeCell.setCellStyle(styles.headerCenter);
        }
        header.setHeightInPoints(22);
    }

    /**
     * Populates Excel sheet with row data. Styles are set while each row is written - a streaming
     * sheet has flushed its earlier rows, so they cannot be styled in a second pass.
     */
    private void populateExcelSheet(Sheet sheet, List<RowData> rows, ConversionConfig config,
                                    XmlSpreadsheetExcelStyles styles, SpreadsheetTypeResolver types) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = sheet.createRow(i + 1);
            RowData rowData = rows.get(i);
            boolean stripe = i % 2 != 0;
            String nodeType = rowData.getNodeType();
            boolean container = "element".equals(nodeType) && rowData.getValue().isEmpty();

            Cell xpathCell = row.createCell(0);
            xpathCell.setCellValue(rowData.getXpath());
            xpathCell.setCellStyle(styles.body(container
                    ? XmlSpreadsheetExcelStyles.Role.XPATH_CONTAINER : XmlSpreadsheetExcelStyles.Role.XPATH,
                    stripe, null));

            writeValue(row.createCell(1), rowData, styles, stripe, types);

            if (config.isIncludeTypeColumn()) {
                Cell typeCell = row.createCell(2);
                typeCell.setCellValue(nodeType);
                typeCell.setCellStyle(styles.body(XmlSpreadsheetExcelStyles.Role.TYPE, stripe, null));
            }
        }
    }

    /**
     * @return the XSD built-in type of the row's value: element text and attribute values only,
     *         {@code null} for other rows and values the resolver does not type
     */
    static String builtinTypeOf(RowData rowData, SpreadsheetTypeResolver types) {
        return rowData.getValue().isEmpty() ? null : builtinTypeOf(rowData.getXpath(), rowData.getNodeType(), types);
    }

    /** @return the XSD built-in type of the element text / attribute value at {@code xpath}, or {@code null} */
    static String builtinTypeOf(String xpath, String nodeType, SpreadsheetTypeResolver types) {
        return switch (nodeType) {
            case "element" -> types.builtinTypeOf(xpath, null);
            case "attribute" -> {
                int at = xpath.lastIndexOf("/@");
                yield at < 0 ? null : types.builtinTypeOf(xpath.substring(0, at), xpath.substring(at + 2));
            }
            default -> null;
        };
    }

    /** @return the row's value typed as {@code builtin} says, or text when it does not fit losslessly */
    static SpreadsheetTypedValue typedValue(RowData rowData, String builtin) {
        return SpreadsheetTypedValue.of(SpreadsheetCellKind.fromBuiltin(builtin), rowData.getValue());
    }

    /** Writes the value as the schema type's cell type, or as text when it is not typed or does not fit. */
    private static void writeValue(Cell cell, RowData rowData, XmlSpreadsheetExcelStyles styles, boolean stripe,
                                   SpreadsheetTypeResolver types) {
        SpreadsheetTypedValue typed = typedValue(rowData, builtinTypeOf(rowData, types));
        switch (typed) {
            case SpreadsheetTypedValue.Numeric n -> {
                cell.setCellValue(n.value());
                cell.setCellStyle(styles.body(XmlSpreadsheetExcelStyles.Role.NUMBER, stripe,
                        XmlSpreadsheetExcelStyles.numberFormat(n.decimals())));
            }
            case SpreadsheetTypedValue.Bool b -> {
                cell.setCellValue(b.value());
                cell.setCellStyle(styles.body(XmlSpreadsheetExcelStyles.Role.BOOLEAN, stripe, null));
            }
            case SpreadsheetTypedValue.Temporal t -> {
                if (t.date() == null) {
                    cell.setCellValue(t.time().toNanoOfDay() / 86_400_000_000_000.0); // Excel time = day fraction
                } else {
                    cell.setCellValue(SpreadsheetTypedValue.dateTimeOf(t));
                }
                cell.setCellStyle(styles.body(XmlSpreadsheetExcelStyles.Role.TEMPORAL, stripe, t.format()));
            }
            case SpreadsheetTypedValue.Text text -> {
                if (!text.value().isEmpty()) {
                    cell.setCellValue(text.value());
                }
                cell.setCellStyle(styles.body("comment".equals(rowData.getNodeType())
                        ? XmlSpreadsheetExcelStyles.Role.COMMENT : XmlSpreadsheetExcelStyles.Role.TEXT, stripe, null));
            }
        }
    }

    /**
     * Formats Excel sheet
     */
    private void formatExcelSheet(Sheet sheet, List<RowData> rows, ConversionConfig config) {
        // Column widths from a sample of the rows instead of autoSizeColumn: that measures every
        // cell with AWT font metrics (minutes for a large sheet, impossible once a streaming sheet
        // has flushed its rows, and unavailable in some headless environments).
        int sample = Math.min(rows.size(), WIDTH_SAMPLE_ROWS);
        int xpathChars = "XPath".length();
        int valueChars = "Value".length();
        int typeChars = "Type".length();
        for (int i = 0; i < sample; i++) {
            RowData row = rows.get(i);
            xpathChars = Math.max(xpathChars, length(row.getXpath()));
            valueChars = Math.max(valueChars, length(row.getValue()));
            typeChars = Math.max(typeChars, length(row.getNodeType()));
        }
        sheet.setColumnWidth(0, columnWidth(xpathChars));
        sheet.setColumnWidth(1, columnWidth(Math.min(valueChars, MAX_VALUE_CHARS)));
        if (config.isIncludeTypeColumn()) {
            sheet.setColumnWidth(2, columnWidth(typeChars));
        }

        // Frozen header, autofilter, printed one page wide with the header on every page
        FxtExcelTheme.finishTable(sheet, 0, rows.size(), config.isIncludeTypeColumn() ? 2 : 1);
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }

    /** @return the POI column width (1/256 of a character) for a column of the given characters */
    private static int columnWidth(int chars) {
        return Math.min(chars + 2, MAX_COLUMN_CHARS) * 256;
    }

    /**
     * Reads data from Excel sheet, each value as its schema type says
     */
    private void readExcelSheet(Sheet sheet, List<RowData> rows, SpreadsheetTypeResolver types) {
        readExcelSheet(sheet, rows, types, xpath -> true);
    }

    /** Reads the rows whose XPath {@code include} accepts. */
    private void readExcelSheet(Sheet sheet, List<RowData> rows, SpreadsheetTypeResolver types,
                                java.util.function.Predicate<String> include) {
        boolean hasTypeColumn = false;

        // Check header to determine if type column exists
        Row headerRow = sheet.getRow(0);
        if (headerRow != null && headerRow.getLastCellNum() > 2) {
            hasTypeColumn = "Type".equalsIgnoreCase(getCellValueAsString(headerRow.getCell(2)).trim());
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            Cell xpathCell = row.getCell(0);
            Cell typeCell = hasTypeColumn ? row.getCell(2) : null;

            if (xpathCell != null) {
                String xpath = getCellValueAsString(xpathCell);
                if (xpath.isBlank() || !include.test(xpath)) {
                    continue;
                }
                String type = typeCell != null ? getCellValueAsString(typeCell) : inferTypeFromXPath(xpath);
                String builtin = builtinTypeOf(xpath, type, types);
                String value = ExcelCellLexical.lexical(row.getCell(1), SpreadsheetCellKind.fromBuiltin(builtin));

                rows.add(new RowData(xpath, value, type, i - 1)); // i-1 because we start from row 1 (header is row 0)
            }
        }
    }

    /**
     * Gets cell value as string regardless of cell type
     */
    private String getCellValueAsString(Cell cell) {
        return ExcelCellLexical.lexical(cell, SpreadsheetCellKind.TEXT);
    }

    /**
     * Infers node type from XPath when type column is not available
     */
    private String inferTypeFromXPath(String xpath) {
        if (xpath.contains("/@")) {
            return "attribute";
        }
        if (xpath.endsWith("/text()")) {
            return "text";
        }
        if (xpath.endsWith("/comment()")) {
            return "comment";
        }
        if (xpath.endsWith("/cdata()")) {
            return "cdata";
        }
        return "element";
    }

    // ------------------------------------------------------------------ table layout

    /** @return the table of {@code rows} with the configured record, or the default one */
    public RecordTableLayout.Table toTable(List<RowData> rows, ConversionConfig config) {
        String recordPath = config.getRecordPath() != null ? config.getRecordPath()
                : RecordTableLayout.defaultRecordPath(rows);
        return RecordTableLayout.toTable(rows, recordPath);
    }

    /**
     * Writes the table layout: sheet {@value #RECORDS_SHEET} (one row per record), {@value #OTHER_SHEET} (the
     * values outside the records as XPath / Value / Type, with the placeholders the import puts the records
     * back at) and {@value #INFO_SHEET} (what the workbook holds, how to edit it, the record path).
     */
    private void writeTableWorkbook(RecordTableLayout.Table table, File outputFile, ConversionConfig config,
                                    SpreadsheetTypeResolver types, boolean isXlsx, int maxRows) throws Exception {
        int records = table.records().size() + 1;
        int other = table.otherRows().size() + 1;
        if (Math.max(records, other) > maxRows) {
            throw new RowLimitExceededException(Math.max(records, other), maxRows);
        }
        SXSSFWorkbook streaming = isXlsx ? new SXSSFWorkbook(STREAMING_WINDOW_ROWS) : null;
        try (Workbook workbook = isXlsx ? streaming : new HSSFWorkbook()) {
            if (streaming != null) {
                ServiceRegistry.get(ExportMetadataService.class)
                        .setExcelMetadata(streaming.getXSSFWorkbook(), "XML to Excel Conversion");
            }
            XmlSpreadsheetExcelStyles styles = new XmlSpreadsheetExcelStyles(workbook);
            Sheet sheet = workbook.createSheet(RECORDS_SHEET);
            FxtExcelTheme.tableSheet(sheet);
            List<String> headers = table.headerRow();
            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            int[] widths = new int[headers.size()];
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(styles.header);
                widths[c] = headers.get(c).length();
            }
            int r = 1;
            for (RecordTableLayout.Record record : table.records()) {
                Row row = sheet.createRow(r);
                boolean stripe = r % 2 == 0;
                List<RowData> cells = table.cellsOf(record);
                for (int c = 0; c < cells.size(); c++) {
                    RowData data = cells.get(c);
                    Cell cell = row.createCell(c);
                    if (data == null) {
                        cell.setCellStyle(styles.body(XmlSpreadsheetExcelStyles.Role.TEXT, stripe, null));
                    } else if (RecordTableLayout.PARENT.equals(data.getNodeType())) {
                        cell.setCellValue(data.getValue());
                        cell.setCellStyle(styles.body(XmlSpreadsheetExcelStyles.Role.XPATH, stripe, null));
                    } else {
                        writeValue(cell, data, styles, stripe, types);
                    }
                    if (r <= WIDTH_SAMPLE_ROWS && data != null) {
                        widths[c] = Math.max(widths[c], Math.min(length(data.getValue()), MAX_VALUE_CHARS));
                    }
                }
                r++;
            }
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, columnWidth(widths[c]));
            }
            FxtExcelTheme.finishTable(sheet, 0, table.records().size(), Math.max(0, headers.size() - 1));

            if (!table.otherRows().isEmpty()) {
                // the import needs the type column (placeholders), whatever the option says
                ConversionConfig typed = new ConversionConfig();
                typed.setIncludeTypeColumn(true);
                Sheet otherSheet = workbook.createSheet(OTHER_SHEET);
                FxtExcelTheme.tableSheet(otherSheet);
                createExcelHeader(otherSheet, typed, styles);
                populateExcelSheet(otherSheet, table.otherRows(), typed, styles, types);
                formatExcelSheet(otherSheet, table.otherRows(), typed);
            }
            writeInfoSheet(workbook, isXlsx, table, table.records().size());
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
            logger.info("Converted XML to an Excel table with {} records", table.records().size());
        }
    }

    /**
     * Writes the {@value #INFO_SHEET} sheet: what the workbook holds, how to read it and how to edit it so the
     * way back works. In English like the column headers; {@value #INFO_RECORD_LABEL} is read by the import.
     *
     * @param table the table layout's table, or {@code null} for the XPath list
     * @param rows  the data rows written
     */
    private static void writeInfoSheet(Workbook workbook, boolean xlsx, RecordTableLayout.Table table, int rows) {
        Sheet sheet = workbook.createSheet(INFO_SHEET);
        List<String[]> lines = new ArrayList<>();
        String created = "Created with FreeXmlToolkit on "
                + java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ');
        if (table != null) {
            String name = table.recordPath().substring(table.recordPath().lastIndexOf('/') + 1);
            lines.add(new String[]{"#", "Contents"});
            lines.add(new String[]{"Layout", "Table - one row per record"});
            lines.add(new String[]{INFO_RECORD_LABEL, table.recordPath()});
            lines.add(new String[]{"Records", Integer.toString(rows)});
            lines.add(new String[]{"Columns", Integer.toString(table.headerRow().size())});
            lines.add(new String[]{"#", "How to read the table"});
            lines.add(new String[]{"Row", "One record: one <" + name + "> element of the XML document."});
            lines.add(new String[]{"Column", "One value inside the record. \"Date\" is a child element, "
                    + "\"Customer/Name\" an element inside another one, \"@id\" an attribute, "
                    + "\"Item[2]/Qty\" the Qty of the second Item, \"#text\" the record's own text."});
            if (table.hasParentColumn()) {
                lines.add(new String[]{RecordTableLayout.PARENT,
                        "Where in the document the record sits (the records sit in several places)."});
            }
            if (!table.otherRows().isEmpty()) {
                lines.add(new String[]{OTHER_SHEET, "The values outside the records, one per row: the XPath "
                        + "(their place in the document), the value and the kind of node."});
            }
            lines.add(new String[]{"#", "Editing and converting back"});
            lines.add(new String[]{"Allowed", "Change values, add or delete rows, sort and filter."});
            lines.add(new String[]{"Keep", "The header row, the sheet names and the \"" + OTHER_SHEET
                    + "\" sheet: converting back needs them."});
            lines.add(new String[]{"Empty cell", "No element or attribute is created for it."});
        } else {
            lines.add(new String[]{"#", "Contents"});
            lines.add(new String[]{"Layout", "XPath list - one row per node of the XML document"});
            lines.add(new String[]{"Rows", Integer.toString(rows)});
            lines.add(new String[]{"#", "How to read the list"});
            lines.add(new String[]{"XPath", "The node's place in the document: /Orders/Order[2]/Date is the Date "
                    + "of the second Order, /Orders/@version an attribute."});
            lines.add(new String[]{"Value", "The element's text or the attribute's value; empty for elements that "
                    + "only contain other elements."});
            lines.add(new String[]{"Type", "element, attribute, text, comment or cdata."});
            lines.add(new String[]{"#", "Editing and converting back"});
            lines.add(new String[]{"Allowed", "Change values; add rows with a new XPath to add nodes."});
            lines.add(new String[]{"Keep", "The XPath column and the header row: converting back needs them."});
        }
        lines.add(new String[]{"Convert back", "Tools > Spreadsheet Converter > Spreadsheet -> XML."});
        if (xlsx) {
            FxtExcelTheme theme = new FxtExcelTheme(workbook);
            FxtExcelTheme.infoSheet(sheet);
            int r = theme.writeTitle(sheet, 0, "XML as a spreadsheet");
            r = theme.writeSubtitle(sheet, r, created);
            for (String[] line : lines) {
                r = "#".equals(line[0]) ? theme.writeSection(sheet, r + 1, line[1], 1)
                        : theme.writeLabelValue(sheet, r, line[0], line[1], theme.note());
            }
        } else {
            int r = 0;
            sheet.createRow(r++).createCell(0).setCellValue("XML as a spreadsheet");
            sheet.createRow(r++).createCell(0).setCellValue(created);
            for (String[] line : lines) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue("#".equals(line[0]) ? line[1] : line[0]);
                if (!"#".equals(line[0])) {
                    row.createCell(1).setCellValue(line[1]);
                }
            }
            sheet.setColumnWidth(0, FxtExcelTheme.INFO_LABEL_WIDTH);
            sheet.setColumnWidth(1, FxtExcelTheme.INFO_VALUE_WIDTH);
        }
    }

    /** @return whether the sheet is an XPath list (header "XPath" in A1) */
    private boolean isXPathList(Sheet sheet) {
        Row header = sheet.getRow(0);
        return header != null && isXPathHeader(getCellValueAsString(header.getCell(0)));
    }

    /** @return whether a first header cell names the XPath list's first column */
    static boolean isXPathHeader(String header) {
        return header != null && header.strip().replace("\uFEFF", "").equalsIgnoreCase("XPath");
    }

    /** @return the record path the info sheet names, or {@code null} (a foreign workbook) */
    private String infoRecordPath(Workbook workbook) {
        Sheet info = workbook.getSheet(INFO_SHEET);
        if (info == null) {
            return null;
        }
        for (Row row : info) {
            if (INFO_RECORD_LABEL.equals(getCellValueAsString(row.getCell(0)).strip())) {
                String path = getCellValueAsString(row.getCell(1)).strip();
                return path.startsWith("/") ? path : null;
            }
        }
        return null;
    }

    /** @return the record path of a foreign table: {@code /<root>/<record>} from the options */
    static String foreignRecordPath(ConversionConfig config) {
        return "/" + RecordTableLayout.xmlName(config.getRootName()) + "/"
                + RecordTableLayout.xmlName(config.getRecordName());
    }

    /** Reads a workbook in the table layout (ours, or a foreign one: first sheet, one row per record). */
    private List<RowData> readTableWorkbook(Workbook workbook, ConversionConfig config,
                                            java.util.function.Function<String, SpreadsheetTypeResolver> typesFor)
            throws Exception {
        String recordPath = infoRecordPath(workbook);
        Sheet otherSheet = recordPath == null ? null : workbook.getSheet(OTHER_SHEET);
        Sheet sheet = recordPath != null && workbook.getSheet(RECORDS_SHEET) != null
                ? workbook.getSheet(RECORDS_SHEET) : workbook.getSheetAt(0);
        if (recordPath == null) {
            recordPath = foreignRecordPath(config);
        }
        String rootXml = "";
        if (otherSheet != null) {
            List<RowData> rootRows = new ArrayList<>();
            readExcelSheet(otherSheet, rootRows, SpreadsheetTypeResolver.NONE, XmlSpreadsheetConverterService::isRootRow);
            rootXml = rootElementXml(rootRows, config);
        }
        SpreadsheetTypeResolver resolved = typesFor == null ? null : typesFor.apply(rootXml);
        SpreadsheetTypeResolver types = resolved == null ? SpreadsheetTypeResolver.NONE : resolved;
        List<RowData> other = new ArrayList<>();
        if (otherSheet != null) {
            readExcelSheet(otherSheet, other, types);
        }
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow != null) {
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(getCellValueAsString(headerRow.getCell(c)));
            }
        }
        List<Row> dataRows = new ArrayList<>();
        List<List<String>> parentValues = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                dataRows.add(row);
                List<String> texts = new ArrayList<>();
                for (int c = 0; c < headers.size(); c++) {
                    texts.add(RecordTableLayout.PARENT.equals(headers.get(c).strip())
                            ? getCellValueAsString(row.getCell(c)) : "");
                }
                parentValues.add(texts);
            }
        }
        int parentColumn = RecordTableLayout.parentColumn(headers, parentValues);
        return tableRows(recordPath, headers, parentColumn, dataRows.size(), other,
                (r, c) -> parentColumn == c ? getCellValueAsString(dataRows.get(r).getCell(c)) : null,
                (r, c, xpath, nodeType) -> ExcelCellLexical.lexical(dataRows.get(r).getCell(c),
                        SpreadsheetCellKind.fromBuiltin(builtinTypeOf(xpath, nodeType, types))));
    }

    /** The rows of a CSV file in the table layout (no XPath column): a foreign table, records from the options. */
    private List<RowData> csvTableRows(List<List<String>> records, ConversionConfig config) {
        if (records.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> headers = records.getFirst();
        List<List<String>> values = records.subList(1, records.size());
        int parentColumn = RecordTableLayout.parentColumn(headers, values);
        return tableRows(foreignRecordPath(config), headers, parentColumn, values.size(), List.of(),
                (r, c) -> c < values.get(r).size() ? values.get(r).get(c) : "",
                (r, c, xpath, nodeType) -> c < values.get(r).size() ? values.get(r).get(c) : "");
    }

    /** Reads one cell's text. */
    @FunctionalInterface
    private interface TextCell {
        String text(int row, int column);
    }

    /** Reads one value cell, knowing the XPath and node type it becomes (for schema typing). */
    @FunctionalInterface
    private interface ValueCell {
        String value(int row, int column, String xpath, String nodeType);
    }

    /**
     * Turns table rows back into XPath rows: each row a record under its parent (the {@link
     * RecordTableLayout#PARENT} column, else the default), numbered per parent; empty cells and empty rows
     * create nothing; the records go where the placeholders in {@code other} are.
     */
    private static List<RowData> tableRows(String recordPath, List<String> headers, int parentColumn, int rows,
                                           List<RowData> other, TextCell parentCell, ValueCell valueCell) {
        List<String> paths = RecordTableLayout.columnPaths(headers, parentColumn);
        String defaultParent = RecordTableLayout.defaultParent(recordPath, other);
        Map<String, List<List<RowData>>> byParent = new java.util.LinkedHashMap<>();
        Map<String, Integer> ordinals = new HashMap<>();
        for (int r = 0; r < rows; r++) {
            String parent = parentColumn < 0 ? "" : parentCell.text(r, parentColumn);
            parent = parent == null || parent.isBlank() ? defaultParent : parent.strip();
            String instance = RecordTableLayout.instancePath(recordPath, parent, ordinals.getOrDefault(parent, 0) + 1);
            RowData element = new RowData(instance, "", "element");
            List<RowData> record = new ArrayList<>();
            record.add(element);
            for (int c = 0; c < paths.size(); c++) {
                String path = paths.get(c);
                if (path == null) {
                    continue;
                }
                String xpath = RecordTableLayout.cellXPath(instance, path);
                String nodeType = RecordTableLayout.nodeTypeOf(path);
                String value = valueCell.value(r, c, xpath, nodeType);
                if (value == null || value.isEmpty()) {
                    continue;
                }
                if (RecordTableLayout.OWN_VALUE.equals(path)) {
                    element.setValue(value);
                } else {
                    record.add(new RowData(xpath, value, nodeType));
                }
            }
            if (record.size() > 1 || !element.getValue().isEmpty()) {
                ordinals.merge(parent, 1, Integer::sum);
                byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(record);
            }
        }
        return new ArrayList<>(RecordTableLayout.assemble(other, byParent));
    }

    /**
     * Builds XML document from row data
     */
    public Document buildXmlFromRows(List<RowData> rows, ConversionConfig config) throws Exception {
        DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        XPathParser xpathParser = new XPathParser();

        // Sort rows by original index to preserve the order from the Excel file
        rows.sort(Comparator.comparing(RowData::getOriginalIndex));

        for (RowData rowData : rows) {
            xpathParser.createNodeFromXPath(doc, rowData.getXpath(), rowData.getValue(), rowData.getNodeType());
        }

        return doc;
    }

    /**
     * Converts Document to formatted XML string
     */
    public String documentToString(Document doc, ConversionConfig config) throws Exception {
        TransformerFactory transformerFactory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureTransformerFactory();
        Transformer transformer = transformerFactory.newTransformer();

        if (config.isPrettyPrintXml()) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        }
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        return writer.toString();
    }
}
