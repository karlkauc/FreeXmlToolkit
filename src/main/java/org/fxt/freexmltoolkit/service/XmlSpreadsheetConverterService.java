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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.util.*;

/**
 * Service for converting between XML and spreadsheet formats (Excel XLSX/XLS, CSV)
 * Provides bidirectional conversion with full XML structure preservation
 */
public class XmlSpreadsheetConverterService {
    private static final Logger logger = LogManager.getLogger(XmlSpreadsheetConverterService.class);

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
        Workbook workbook = isXlsx ? new XSSFWorkbook() : new HSSFWorkbook();

        // Set document metadata for XLSX files
        if (isXlsx && workbook instanceof XSSFWorkbook xssfWorkbook) {
            ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
            metadataService.setExcelMetadata(xssfWorkbook, "XML to Excel Conversion");
        }

        try {
            Sheet sheet = workbook.createSheet("XML Structure");
            createExcelHeader(sheet, config);
            populateExcelSheet(sheet, rows, config);
            formatExcelSheet(sheet, config);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            logger.info("Successfully converted XML to Excel with {} rows", rows.size());
        } finally {
            workbook.close();
        }
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
            traverseNode(doc.getDocumentElement(), "", rows, config, indexCounter);
        }

        return rows;
    }

    /**
     * Recursively traverses XML nodes and extracts data
     */
    private void traverseNode(Node node, String parentXPath, List<RowData> rows, ConversionConfig config,
                              java.util.concurrent.atomic.AtomicInteger indexCounter) {
        if (node == null) return;

        String xpath = buildXPath(node, parentXPath);

        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> {
                Element element = (Element) node;

                // Add element itself
                String elementValue = "";
                boolean hasOnlyTextChild = hasOnlyTextContent(element);
                boolean hasOnlyCDataChild = hasOnlyCDataContent(element);

                if (hasOnlyTextChild) {
                    elementValue = element.getTextContent().trim();
                } else if (hasOnlyCDataChild && config.isIncludeCData()) {
                    // Don't add element value for CDATA-only elements, 
                    // the CDATA will be added separately
                    elementValue = "";
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
                    NodeList children = element.getChildNodes();
                    Map<String, Integer> elementCounts = new HashMap<>();

                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);

                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            String childName = child.getNodeName();
                            elementCounts.put(childName, elementCounts.getOrDefault(childName, 0) + 1);
                        }
                    }

                    Map<String, Integer> currentCounts = new HashMap<>();

                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);

                        switch (child.getNodeType()) {
                            case Node.ELEMENT_NODE -> {
                                String childName = child.getNodeName();
                                int currentCount = currentCounts.getOrDefault(childName, 0) + 1;
                                currentCounts.put(childName, currentCount);

                                String childParentXPath = xpath;
                                if (elementCounts.get(childName) > 1) {
                                    childParentXPath = xpath;
                                }
                                traverseNode(child, childParentXPath, rows, config, indexCounter);
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
     * Checks if element has only CDATA content
     */
    private boolean hasOnlyCDataContent(Element element) {
        NodeList children = element.getChildNodes();
        int cdataCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            } else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                cdataCount++;
            } else if (child.getNodeType() == Node.TEXT_NODE &&
                    child.getNodeValue() != null &&
                    !child.getNodeValue().trim().isEmpty()) {
                return false; // Mixed content
            }
        }
        return cdataCount == 1;
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
        for (int i = 0; i < rows.size(); i++) {
            Row row = sheet.createRow(i + 1);
            RowData rowData = rows.get(i);

            row.createCell(0).setCellValue(rowData.getXpath());
            row.createCell(1).setCellValue(rowData.getValue());

            if (config.isIncludeTypeColumn()) {
                row.createCell(2).setCellValue(rowData.getNodeType());
            }
        }
    }

    /**
     * Formats Excel sheet
     */
    private void formatExcelSheet(Sheet sheet, ConversionConfig config) {
        // Auto-size columns
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        if (config.isIncludeTypeColumn()) {
            sheet.autoSizeColumn(2);
        }

        // Set XPath column as text format
        CellStyle textStyle = sheet.getWorkbook().createCellStyle();
        DataFormat format = sheet.getWorkbook().createDataFormat();
        textStyle.setDataFormat(format.getFormat("@"));

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell xpathCell = row.getCell(0);
                if (xpathCell != null) {
                    xpathCell.setCellStyle(textStyle);
                }
            }
        }

        // Enable autofilter
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, sheet.getLastRowNum(), 0, config.isIncludeTypeColumn() ? 2 : 1));
    }

    /**
     * Reads data from Excel sheet
     */
    private void readExcelSheet(Sheet sheet, List<RowData> rows, ConversionConfig config) {
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
            if (row == null) continue;

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
        if (cell == null) return "";

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
        if (xpath.contains("/@")) return "attribute";
        if (xpath.endsWith("/text()")) return "text";
        if (xpath.endsWith("/comment()")) return "comment";
        if (xpath.endsWith("/cdata()")) return "cdata";
        return "element";
    }

    /**
     * Builds XML document from row data
     */
    public Document buildXmlFromRows(List<RowData> rows, ConversionConfig config) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
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