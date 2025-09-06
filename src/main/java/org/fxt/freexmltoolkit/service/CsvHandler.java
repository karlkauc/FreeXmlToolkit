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
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles CSV file operations for XML-to-CSV conversion
 * Supports various CSV formats and configurations
 */
public class CsvHandler {
    private static final Logger logger = LogManager.getLogger(CsvHandler.class);

    /**
     * CSV configuration settings
     */
    public static class CsvConfig {
        private char delimiter = ',';
        private char quoteChar = '"';
        private boolean alwaysQuote = false;
        private boolean includeBOM = false; // BOM for Excel compatibility
        private String charset = "UTF-8";
        private String lineEnding = System.lineSeparator();

        // Constructors
        public CsvConfig() {
        }

        public CsvConfig(char delimiter) {
            this.delimiter = delimiter;
        }

        // Predefined configurations
        public static CsvConfig comma() {
            return new CsvConfig(',');
        }

        public static CsvConfig semicolon() {
            CsvConfig config = new CsvConfig(';');
            config.setIncludeBOM(true); // For German Excel compatibility
            return config;
        }

        public static CsvConfig tab() {
            return new CsvConfig('\t');
        }

        public static CsvConfig pipe() {
            return new CsvConfig('|');
        }

        // Getters and setters
        public char getDelimiter() {
            return delimiter;
        }

        public void setDelimiter(char delimiter) {
            this.delimiter = delimiter;
        }

        public char getQuoteChar() {
            return quoteChar;
        }

        public void setQuoteChar(char quoteChar) {
            this.quoteChar = quoteChar;
        }

        public boolean isAlwaysQuote() {
            return alwaysQuote;
        }

        public void setAlwaysQuote(boolean alwaysQuote) {
            this.alwaysQuote = alwaysQuote;
        }

        public boolean isIncludeBOM() {
            return includeBOM;
        }

        public void setIncludeBOM(boolean includeBOM) {
            this.includeBOM = includeBOM;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        public String getLineEnding() {
            return lineEnding;
        }

        public void setLineEnding(String lineEnding) {
            this.lineEnding = lineEnding;
        }

        @Override
        public String toString() {
            return String.format("CsvConfig{delimiter='%c', quote='%c', BOM=%b}",
                    delimiter, quoteChar, includeBOM);
        }
    }

    /**
     * Writes row data to CSV file
     */
    public void writeCsv(List<RowData> rows, File outputFile, CsvConfig csvConfig, ConversionConfig conversionConfig)
            throws IOException {
        logger.info("Writing {} rows to CSV file: {}", rows.size(), outputFile.getName());

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {

            // Write BOM if requested
            if (csvConfig.isIncludeBOM()) {
                writer.write('\uFEFF'); // UTF-8 BOM
            }

            // Write header
            writeHeader(bufferedWriter, csvConfig, conversionConfig);

            // Write data rows
            for (RowData row : rows) {
                writeCsvRow(bufferedWriter, row, csvConfig, conversionConfig);
            }
        }

        logger.info("Successfully wrote CSV file with {} rows", rows.size());
    }

    /**
     * Reads CSV file and returns row data
     */
    public List<RowData> readCsv(File csvFile, CsvConfig csvConfig, ConversionConfig conversionConfig)
            throws IOException {
        logger.info("Reading CSV file: {}", csvFile.getName());

        List<RowData> rows = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(csvFile), StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {

            // Skip BOM if present
            bufferedReader.mark(1);
            int firstChar = bufferedReader.read();
            if (firstChar != 0xFEFF) {
                bufferedReader.reset();
            }

            String line;
            boolean firstLine = true;
            boolean hasTypeColumn = false;
            int lineIndex = 0; // Track line index for ordering

            while ((line = bufferedReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line, csvConfig);

                if (firstLine) {
                    // Check if we have a type column
                    hasTypeColumn = fields.size() > 2 &&
                            fields.get(2).trim().equalsIgnoreCase("type");
                    firstLine = false;
                    continue; // Skip header
                }

                if (fields.size() >= 2) {
                    String xpath = fields.get(0);
                    String value = fields.get(1);
                    String type = hasTypeColumn && fields.size() > 2 ?
                            fields.get(2) : inferTypeFromXPath(xpath);

                    rows.add(new RowData(xpath, value, type, lineIndex++));
                }
            }
        }

        logger.info("Successfully read CSV file with {} rows", rows.size());
        return rows;
    }

    /**
     * Writes CSV header
     */
    private void writeHeader(BufferedWriter writer, CsvConfig csvConfig, ConversionConfig conversionConfig)
            throws IOException {
        List<String> headers = new ArrayList<>();
        headers.add("XPath");
        headers.add("Value");

        if (conversionConfig.isIncludeTypeColumn()) {
            headers.add("Type");
        }

        writeCsvLine(writer, headers, csvConfig);
    }

    /**
     * Writes a single CSV row
     */
    private void writeCsvRow(BufferedWriter writer, RowData row, CsvConfig csvConfig, ConversionConfig conversionConfig)
            throws IOException {
        List<String> fields = new ArrayList<>();
        fields.add(row.getXpath());
        fields.add(row.getValue());

        if (conversionConfig.isIncludeTypeColumn()) {
            fields.add(row.getNodeType());
        }

        writeCsvLine(writer, fields, csvConfig);
    }

    /**
     * Writes a line of CSV data
     */
    private void writeCsvLine(BufferedWriter writer, List<String> fields, CsvConfig csvConfig) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                writer.write(csvConfig.getDelimiter());
            }

            String field = fields.get(i);
            String escapedField = escapeValue(field, csvConfig);
            writer.write(escapedField);
        }
        writer.write(csvConfig.getLineEnding());
    }

    /**
     * Escapes a field value for CSV format
     */
    private String escapeValue(String value, CsvConfig csvConfig) {
        if (value == null) {
            return "";
        }

        boolean needsQuoting = csvConfig.isAlwaysQuote() ||
                value.indexOf(csvConfig.getDelimiter()) >= 0 ||
                value.indexOf(csvConfig.getQuoteChar()) >= 0 ||
                value.indexOf('\n') >= 0 ||
                value.indexOf('\r') >= 0 ||
                value.trim().length() != value.length(); // Leading/trailing whitespace

        if (needsQuoting) {
            StringBuilder escaped = new StringBuilder();
            escaped.append(csvConfig.getQuoteChar());

            for (char c : value.toCharArray()) {
                if (c == csvConfig.getQuoteChar()) {
                    escaped.append(csvConfig.getQuoteChar());
                    escaped.append(csvConfig.getQuoteChar()); // Double the quote
                } else {
                    escaped.append(c);
                }
            }

            escaped.append(csvConfig.getQuoteChar());
            return escaped.toString();
        }

        return value;
    }

    /**
     * Parses a CSV line into fields
     */
    private List<String> parseCsvLine(String line, CsvConfig csvConfig) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        boolean quoteEncountered = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == csvConfig.getQuoteChar()) {
                if (inQuotes) {
                    // Check if this is an escaped quote (double quote)
                    if (i + 1 < line.length() && line.charAt(i + 1) == csvConfig.getQuoteChar()) {
                        currentField.append(csvConfig.getQuoteChar());
                        i++; // Skip the next quote
                    } else {
                        inQuotes = false;
                        quoteEncountered = true;
                    }
                } else {
                    inQuotes = true;
                    quoteEncountered = true;
                }
            } else if (c == csvConfig.getDelimiter() && !inQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
                quoteEncountered = false;
            } else {
                currentField.append(c);
            }
        }

        // Add the last field
        fields.add(currentField.toString());

        return fields;
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
     * Detects CSV delimiter from file content
     */
    public static char detectDelimiter(File csvFile) throws IOException {
        char[] delimiters = {',', ';', '\t', '|'};
        int[] counts = new int[delimiters.length];

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {

            String line;
            int linesRead = 0;

            while ((line = reader.readLine()) != null && linesRead < 5) {
                for (int i = 0; i < delimiters.length; i++) {
                    for (char c : line.toCharArray()) {
                        if (c == delimiters[i]) {
                            counts[i]++;
                        }
                    }
                }
                linesRead++;
            }
        }

        // Find the delimiter with the highest count
        int maxCount = 0;
        char bestDelimiter = ',';

        for (int i = 0; i < delimiters.length; i++) {
            if (counts[i] > maxCount) {
                maxCount = counts[i];
                bestDelimiter = delimiters[i];
            }
        }

        logger.debug("Detected CSV delimiter: '{}' (count: {})", bestDelimiter, maxCount);
        return bestDelimiter;
    }

    /**
     * Validates CSV file structure
     */
    public static boolean isValidCsvStructure(File csvFile, CsvConfig csvConfig) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return false;
            }

            CsvHandler handler = new CsvHandler();
            List<String> headers = handler.parseCsvLine(headerLine, csvConfig);

            // Check if we have at least XPath and Value columns
            return headers.size() >= 2 &&
                    headers.get(0).trim().equalsIgnoreCase("xpath") &&
                    headers.get(1).trim().equalsIgnoreCase("value");
        }
    }
}