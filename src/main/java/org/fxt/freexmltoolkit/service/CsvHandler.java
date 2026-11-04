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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowData;

/**
 * Handles CSV file operations for XML-to-CSV conversion
 * Supports various CSV formats and configurations
 */
public class CsvHandler {
    private static final Logger logger = LogManager.getLogger(CsvHandler.class);

    /**
     * Creates a new CsvHandler instance.
     */
    public CsvHandler() {
        // Default constructor
    }

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

        /**
         * Default constructor.
         */
        public CsvConfig() {
        }

        /**
         * Constructor with delimiter.
         *
         * @param delimiter The delimiter character.
         */
        public CsvConfig(char delimiter) {
            this.delimiter = delimiter;
        }

        // Predefined configurations
        /**
         * Creates a comma-delimited configuration.
         *
         * @return A CsvConfig with comma delimiter.
         */
        public static CsvConfig comma() {
            return new CsvConfig(',');
        }

        /**
         * Creates a semicolon-delimited configuration.
         *
         * @return A CsvConfig with semicolon delimiter.
         */
        public static CsvConfig semicolon() {
            CsvConfig config = new CsvConfig(';');
            config.setIncludeBOM(true); // For German Excel compatibility
            return config;
        }

        /**
         * Creates a tab-delimited configuration.
         *
         * @return A CsvConfig with tab delimiter.
         */
        public static CsvConfig tab() {
            return new CsvConfig('\t');
        }

        /**
         * Creates a pipe-delimited configuration.
         *
         * @return A CsvConfig with pipe delimiter.
         */
        public static CsvConfig pipe() {
            return new CsvConfig('|');
        }

        // Getters and setters
        /**
         * Gets the delimiter character.
         *
         * @return The delimiter.
         */
        public char getDelimiter() {
            return delimiter;
        }

        /**
         * Sets the delimiter character.
         *
         * @param delimiter The delimiter to set.
         */
        public void setDelimiter(char delimiter) {
            this.delimiter = delimiter;
        }

        /**
         * Gets the quote character.
         *
         * @return The quote character.
         */
        public char getQuoteChar() {
            return quoteChar;
        }

        /**
         * Sets the quote character.
         *
         * @param quoteChar The quote character to set.
         */
        public void setQuoteChar(char quoteChar) {
            this.quoteChar = quoteChar;
        }

        /**
         * Checks if values should always be quoted.
         *
         * @return True if always quoted.
         */
        public boolean isAlwaysQuote() {
            return alwaysQuote;
        }

        /**
         * Sets whether values should always be quoted.
         *
         * @param alwaysQuote True to always quote.
         */
        public void setAlwaysQuote(boolean alwaysQuote) {
            this.alwaysQuote = alwaysQuote;
        }

        /**
         * Checks if BOM should be included.
         *
         * @return True if BOM included.
         */
        public boolean isIncludeBOM() {
            return includeBOM;
        }

        /**
         * Sets whether BOM should be included.
         *
         * @param includeBOM True to include BOM.
         */
        public void setIncludeBOM(boolean includeBOM) {
            this.includeBOM = includeBOM;
        }

        /**
         * Gets the charset.
         *
         * @return The charset string.
         */
        public String getCharset() {
            return charset;
        }

        /**
         * Sets the charset.
         *
         * @param charset The charset to set.
         */
        public void setCharset(String charset) {
            this.charset = charset;
        }

        /**
         * Gets the line ending.
         *
         * @return The line ending string.
         */
        public String getLineEnding() {
            return lineEnding;
        }

        /**
         * Sets the line ending.
         *
         * @param lineEnding The line ending to set.
         */
        public void setLineEnding(String lineEnding) {
            this.lineEnding = lineEnding;
        }

        @Override
        public String toString() {
            return String.format("CsvConfig{delimiter='%c', quote='%c', BOM=%b}",
                    delimiter, quoteChar, includeBOM);
        }
    }

    /** Header of the column naming each value's XSD built-in type (only written with a schema). */
    static final String DATA_TYPE_HEADER = "Data Type";

    /**
     * Writes row data to CSV file; every value is text.
     *
     * @param rows The list of rows to write.
     * @param outputFile The file to write to.
     * @param csvConfig The CSV configuration.
     * @param conversionConfig The conversion configuration.
     * @throws IOException If an I/O error occurs.
     */
    public void writeCsv(List<RowData> rows, File outputFile, CsvConfig csvConfig, ConversionConfig conversionConfig)
            throws IOException {
        writeCsv(rows, outputFile, csvConfig, conversionConfig, SpreadsheetTypeResolver.NONE);
    }

    /**
     * Writes row data to CSV file. With a schema-based {@code types}, the file gets a "Data Type" column
     * with each value's XSD built-in type; a value in its type's lexical space is written bare (its XML
     * lexical form, unchanged - CSV has no precision limit, so {@code 007} or a timezone stay), every other non-empty field is quoted - the "quote non-numeric" convention, so a
     * reader can tell numbers from text. A field holding the delimiter, the quote character, a line
     * break or surrounding blanks is always quoted, quotes inside it doubled.
     *
     * @param types the value types; {@link SpreadsheetTypeResolver#NONE} for the plain format
     * @throws IOException If an I/O error occurs.
     */
    public void writeCsv(List<RowData> rows, File outputFile, CsvConfig csvConfig, ConversionConfig conversionConfig,
                         SpreadsheetTypeResolver types) throws IOException {
        logger.info("Writing {} rows to CSV file: {}", rows.size(), outputFile.getName());
        boolean typed = types != null && types.isSchemaBased();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {

            // Write BOM if requested
            if (csvConfig.isIncludeBOM()) {
                bufferedWriter.write('\uFEFF'); // UTF-8 BOM
            }

            List<String> headers = new ArrayList<>(List.of("XPath", "Value"));
            if (conversionConfig.isIncludeTypeColumn()) {
                headers.add("Type");
            }
            if (typed) {
                headers.add(DATA_TYPE_HEADER);
            }
            writeCsvLine(bufferedWriter, headers, new boolean[headers.size()], typed, csvConfig);

            boolean[] bare = new boolean[headers.size()];
            List<String> fields = new ArrayList<>(headers.size());
            for (RowData row : rows) {
                fields.clear();
                fields.add(row.getXpath());
                fields.add(row.getValue());
                if (conversionConfig.isIncludeTypeColumn()) {
                    fields.add(row.getNodeType());
                }
                bare[1] = false;
                if (typed) {
                    String builtin = XmlSpreadsheetConverterService.builtinTypeOf(row, types);
                    fields.add(builtin == null ? "" : builtin);
                    bare[1] = SpreadsheetTypedValue.isLexicallyValid(
                            SpreadsheetCellKind.fromBuiltin(builtin), row.getValue());
                }
                writeCsvLine(bufferedWriter, fields, bare, typed, csvConfig);
            }
        }

        logger.info("Successfully wrote CSV file with {} rows", rows.size());
    }

    /**
     * Reads CSV file and returns row data. Quoted fields may hold the delimiter, doubled quotes and
     * line breaks; columns after XPath / Value / Type (such as "Data Type") are ignored.
     *
     * @param csvFile The CSV file to read.
     * @param csvConfig The CSV configuration.
     * @param conversionConfig The conversion configuration.
     * @return The list of parsed rows.
     * @throws IOException If an I/O error occurs.
     */
    public List<RowData> readCsv(File csvFile, CsvConfig csvConfig, ConversionConfig conversionConfig)
            throws IOException {
        logger.info("Reading CSV file: {}", csvFile.getName());

        List<RowData> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(csvFile), StandardCharsets.UTF_8))) {

            // Skip BOM if present
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            List<List<String>> records = readRecords(reader, csvConfig);
            boolean hasTypeColumn = !records.isEmpty() && records.get(0).size() > 2
                    && records.get(0).get(2).trim().equalsIgnoreCase("type");
            int lineIndex = 0; // Track record index for ordering
            for (int r = 1; r < records.size(); r++) { // record 0 is the header
                List<String> fields = records.get(r);
                if (fields.size() >= 2) {
                    String xpath = fields.get(0);
                    String value = fields.get(1);
                    String type = hasTypeColumn && fields.size() > 2
                            ? fields.get(2) : inferTypeFromXPath(xpath);
                    rows.add(new RowData(xpath, value, type, lineIndex++));
                }
            }
        }

        logger.info("Successfully read CSV file with {} rows", rows.size());
        return rows;
    }

    /**
     * Writes one record.
     *
     * @param bare        per field: written as is (a typed value) unless it must be quoted
     * @param quoteText   quote every other non-empty field ("quote non-numeric")
     */
    private void writeCsvLine(BufferedWriter writer, List<String> fields, boolean[] bare, boolean quoteText,
                              CsvConfig csvConfig) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                writer.write(csvConfig.getDelimiter());
            }
            String field = fields.get(i);
            boolean forceQuote = quoteText && !bare[i] && field != null && !field.isEmpty();
            writer.write(escapeValue(field, csvConfig, forceQuote));
        }
        writer.write(csvConfig.getLineEnding());
    }

    /**
     * Escapes a field value for CSV format
     *
     * @param value The value to escape
     * @param csvConfig The CSV configuration
     * @param forceQuote quote it even when nothing in it requires that
     * @return The escaped value
     */
    static String escapeValue(String value, CsvConfig csvConfig, boolean forceQuote) {
        if (value == null) {
            return "";
        }
        char quote = csvConfig.getQuoteChar();
        boolean needsQuoting = forceQuote || csvConfig.isAlwaysQuote()
                || value.indexOf(csvConfig.getDelimiter()) >= 0
                || value.indexOf(quote) >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.trim().length() != value.length(); // Leading/trailing whitespace
        if (!needsQuoting) {
            return value;
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append(quote);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == quote) {
                escaped.append(quote); // Double the quote
            }
            escaped.append(c);
        }
        return escaped.append(quote).toString();
    }

    /**
     * Splits CSV text into records (RFC 4180): a quote opens a quoted field only at the start of a
     * field; inside it the delimiter and CR/LF are data and a doubled quote is one quote. Records end at
     * LF, CRLF or CR outside quotes; blank lines are skipped.
     */
    static List<List<String>> readRecords(java.io.Reader reader, CsvConfig csvConfig) throws IOException {
        char quote = csvConfig.getQuoteChar();
        char delimiter = csvConfig.getDelimiter();
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStart = true;
        int c;
        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (inQuotes) {
                if (ch == quote) {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == quote) {
                        field.append(quote);
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == quote && fieldStart) {
                inQuotes = true;
                fieldStart = false;
            } else if (ch == delimiter) {
                record.add(field.toString());
                field.setLength(0);
                fieldStart = true;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') {
                    reader.mark(1);
                    if (reader.read() != '\n') {
                        reader.reset();
                    }
                }
                endRecord(records, record, field);
                record = new ArrayList<>();
                fieldStart = true;
            } else {
                field.append(ch);
                fieldStart = false;
            }
        }
        endRecord(records, record, field);
        return records;
    }

    private static void endRecord(List<List<String>> records, List<String> record, StringBuilder field) {
        record.add(field.toString());
        field.setLength(0);
        boolean blank = record.size() == 1 && record.get(0).isEmpty();
        if (!blank) {
            records.add(record);
        }
    }

    /**
     * Parses a single CSV line into fields
     *
     * @param line The CSV line to parse
     * @param csvConfig The CSV configuration
     * @return A list of parsed fields
     */
    private List<String> parseCsvLine(String line, CsvConfig csvConfig) {
        try {
            List<List<String>> records = readRecords(new java.io.StringReader(line), csvConfig);
            return records.isEmpty() ? new ArrayList<>(List.of("")) : records.get(0);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e); // a StringReader does not fail
        }
    }

    /**
     * Infers node type from XPath when type column is not available
     *
     * @param xpath The XPath to analyze
     * @return The inferred node type
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
     * Detects CSV delimiter from file content.
     *
     * @param csvFile The CSV file to analyze.
     * @return The detected delimiter character.
     * @throws IOException If an I/O error occurs.
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
     * Validates CSV file structure.
     *
     * @param csvFile The CSV file to validate.
     * @param csvConfig The CSV configuration.
     * @return True if valid, false otherwise.
     * @throws IOException If an I/O error occurs.
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
