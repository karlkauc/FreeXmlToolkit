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

import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.RowData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlSpreadsheetConverterServiceTest {

    private XmlSpreadsheetConverterService converterService;
    private ConversionConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        converterService = new XmlSpreadsheetConverterService();
        config = new ConversionConfig();
    }

    @Test
    void testExtractRowsFromSimpleXml() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root version="1.0">
                    <item id="1">First Item</item>
                    <item id="2">Second Item</item>
                    <!-- This is a comment -->
                    <data><![CDATA[Some <special> content]]></data>
                </root>
                """;

        Document doc = parseXml(xml);
        List<RowData> rows = converterService.extractRowsFromXml(doc, config);

        assertNotNull(rows);
        assertFalse(rows.isEmpty());

        // Print results for verification
        System.out.println("Extracted rows:");
        for (RowData row : rows) {
            System.out.printf("XPath: %s | Value: %s | Type: %s%n",
                    row.getXpath(), row.getValue(), row.getNodeType());
        }

        // Check for expected rows
        assertTrue(rows.stream().anyMatch(r -> r.getXpath().equals("/root")));
        assertTrue(rows.stream().anyMatch(r -> r.getXpath().equals("/root/@version") && r.getValue().equals("1.0")));
        assertTrue(rows.stream().anyMatch(r -> r.getXpath().equals("/root/item[1]") && r.getValue().equals("First Item")));
        assertTrue(rows.stream().anyMatch(r -> r.getNodeType().equals("comment")));
        assertTrue(rows.stream().anyMatch(r -> r.getNodeType().equals("cdata")));
    }

    @Test
    void testConvertXmlToExcel() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <item>Test Item</item>
                    <value>123</value>
                </root>
                """;

        Document doc = parseXml(xml);
        File excelFile = tempDir.resolve("test-output.xlsx").toFile();

        converterService.convertXmlToExcel(doc, excelFile, config);

        assertTrue(excelFile.exists());
        assertTrue(excelFile.length() > 0);

        System.out.println("Excel file created: " + excelFile.getAbsolutePath());
        System.out.println("File size: " + excelFile.length() + " bytes");
    }

    @Test
    void testConvertXmlToCsv() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <item>Test Item</item>
                    <value>123</value>
                </root>
                """;

        Document doc = parseXml(xml);
        File csvFile = tempDir.resolve("test-output.csv").toFile();
        CsvHandler.CsvConfig csvConfig = CsvHandler.CsvConfig.comma();

        converterService.convertXmlToCsv(doc, csvFile, csvConfig, config);

        assertTrue(csvFile.exists());
        assertTrue(csvFile.length() > 0);

        // Read and print CSV content
        String csvContent = java.nio.file.Files.readString(csvFile.toPath());
        System.out.println("CSV file created: " + csvFile.getAbsolutePath());
        System.out.println("CSV content:");
        System.out.println(csvContent);

        // Verify CSV structure
        String[] lines = csvContent.split("\n");
        assertTrue(lines.length > 1); // At least header + one data row
        assertTrue(lines[0].contains("XPath"));
        assertTrue(lines[0].contains("Value"));
    }

    @Test
    void testRoundTripXmlToCsvToXml() throws Exception {
        String originalXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root version="1.0">
                    <item id="1">Test Item</item>
                    <value>123</value>
                </root>
                """;

        Document originalDoc = parseXml(originalXml);

        // Convert XML to CSV
        File csvFile = tempDir.resolve("roundtrip.csv").toFile();
        CsvHandler.CsvConfig csvConfig = CsvHandler.CsvConfig.comma();
        converterService.convertXmlToCsv(originalDoc, csvFile, csvConfig, config);

        // Convert CSV back to XML
        Document reconstructedDoc = converterService.convertCsvToXml(csvFile, csvConfig, config);

        assertNotNull(reconstructedDoc);
        assertNotNull(reconstructedDoc.getDocumentElement());

        String reconstructedXml = converterService.documentToString(reconstructedDoc, config);
        System.out.println("Original XML:");
        System.out.println(originalXml);
        System.out.println("\nReconstructed XML:");
        System.out.println(reconstructedXml);

        // Basic validation - should contain the root element and key data
        assertTrue(reconstructedXml.contains("<root"));
        assertTrue(reconstructedXml.contains("version=\"1.0\""));
        assertTrue(reconstructedXml.contains("Test Item"));
        assertTrue(reconstructedXml.contains("123"));
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}