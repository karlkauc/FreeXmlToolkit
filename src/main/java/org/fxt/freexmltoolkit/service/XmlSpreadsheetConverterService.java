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
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
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

    /**
     * Configuration for conversion options
     */
    public static class ConversionConfig {
        private boolean includeComments = true;
        private boolean includeNamespaces = true;
        private boolean includeCData = true;
        private boolean includeTypeColumn = true;
        private boolean prettyPrintXml = true;

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
     * Converts XML document to Excel file
     */
    public void convertXmlToExcel(Document doc, File outputFile, ConversionConfig config) throws Exception {
        logger.info("Converting XML to Excel: {}", outputFile.getName());

        List<RowData> rows = extractRowsFromXml(doc, config);

        boolean isXlsx = outputFile.getName().toLowerCase().endsWith(".xlsx");
        int maxRows = isXlsx ? maxXlsxRows : maxXlsRows;
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

            Sheet sheet = workbook.createSheet("XML Structure");
            createExcelHeader(sheet, config);
            populateExcelSheet(sheet, rows, config);
            formatExcelSheet(sheet, rows, config);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            logger.info("Successfully converted XML to Excel with {} rows", rows.size());
        } // closing an SXSSFWorkbook also deletes the temp file of its flushed rows
    }

    /**
     * Converts XML document to CSV file
     */
    public void convertXmlToCsv(Document doc, File outputFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config) throws Exception {
        logger.info("Converting XML to CSV: {}", outputFile.getName());

        List<RowData> rows = extractRowsFromXml(doc, config);

        CsvHandler csvHandler = new CsvHandler();
        csvHandler.writeCsv(rows, outputFile, csvConfig, config);

        logger.info("Successfully converted XML to CSV with {} rows", rows.size());
    }

    /**
     * Converts Excel file to XML document
     */
    public Document convertExcelToXml(File excelFile, ConversionConfig config) throws Exception {
        logger.info("Converting Excel to XML: {}", excelFile.getName());

        List<RowData> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            readExcelSheet(sheet, rows, config);
        }

        Document doc = buildXmlFromRows(rows, config);
        logger.info("Successfully converted Excel to XML with {} rows", rows.size());
        return doc;
    }

    /**
     * Converts CSV file to XML document
     */
    public Document convertCsvToXml(File csvFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config) throws Exception {
        logger.info("Converting CSV to XML: {}", csvFile.getName());

        CsvHandler csvHandler = new CsvHandler();
        List<RowData> rows = csvHandler.readCsv(csvFile, csvConfig, config);

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
     * Creates Excel header row
     */
    private void createExcelHeader(Sheet sheet, ConversionConfig config) {
        Row header = sheet.createRow(0);

        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Cell xpathCell = header.createCell(0);
        xpathCell.setCellValue("XPath");
        xpathCell.setCellStyle(headerStyle);

        Cell valueCell = header.createCell(1);
        valueCell.setCellValue("Value");
        valueCell.setCellStyle(headerStyle);

        if (config.isIncludeTypeColumn()) {
            Cell typeCell = header.createCell(2);
            typeCell.setCellValue("Type");
            typeCell.setCellStyle(headerStyle);
        }
    }

    /**
     * Populates Excel sheet with row data
     */
    private void populateExcelSheet(Sheet sheet, List<RowData> rows, ConversionConfig config) {
        // XPath column as text; set while the row is written - a streaming sheet has flushed
        // its earlier rows, so they cannot be styled in a second pass
        CellStyle textStyle = sheet.getWorkbook().createCellStyle();
        DataFormat format = sheet.getWorkbook().createDataFormat();
        textStyle.setDataFormat(format.getFormat("@"));

        for (int i = 0; i < rows.size(); i++) {
            Row row = sheet.createRow(i + 1);
            RowData rowData = rows.get(i);

            Cell xpathCell = row.createCell(0);
            xpathCell.setCellValue(rowData.getXpath());
            xpathCell.setCellStyle(textStyle);
            row.createCell(1).setCellValue(rowData.getValue());

            if (config.isIncludeTypeColumn()) {
                row.createCell(2).setCellValue(rowData.getNodeType());
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
        sheet.setColumnWidth(1, columnWidth(valueChars));
        if (config.isIncludeTypeColumn()) {
            sheet.setColumnWidth(2, columnWidth(typeChars));
        }

        // Enable autofilter
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, rows.size(), 0, config.isIncludeTypeColumn() ? 2 : 1));
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }

    /** @return the POI column width (1/256 of a character) for a column of the given characters */
    private static int columnWidth(int chars) {
        return Math.min(chars + 2, MAX_COLUMN_CHARS) * 256;
    }

    /**
     * Reads data from Excel sheet
     */
    private void readExcelSheet(Sheet sheet, List<RowData> rows, ConversionConfig _config) {
        boolean hasTypeColumn = false;

        // Check header to determine if type column exists
        Row headerRow = sheet.getRow(0);
        if (headerRow != null && headerRow.getLastCellNum() > 2) {
            Cell typeHeaderCell = headerRow.getCell(2);
            if (typeHeaderCell != null && "Type".equalsIgnoreCase(typeHeaderCell.getStringCellValue())) {
                hasTypeColumn = true;
            }
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            Cell xpathCell = row.getCell(0);
            Cell valueCell = row.getCell(1);
            Cell typeCell = hasTypeColumn ? row.getCell(2) : null;

            if (xpathCell != null) {
                String xpath = getCellValueAsString(xpathCell);
                String value = valueCell != null ? getCellValueAsString(valueCell) : "";
                String type = typeCell != null ? getCellValueAsString(typeCell) : inferTypeFromXPath(xpath);

                rows.add(new RowData(xpath, value, type, i - 1)); // i-1 because we start from row 1 (header is row 0)
            }
        }
    }

    /**
     * Gets cell value as string regardless of cell type
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
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
