package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * UI-free XML &harr; spreadsheet conversion for the shell, reusing
 * {@link XmlSpreadsheetConverterService}. Export results are returned as
 * {@code "OK: <path>"}; imports return the resulting XML string. Failures are
 * returned as {@code "ERROR: …"}.
 */
public final class SpreadsheetActionRunner {

    private SpreadsheetActionRunner() {
    }

    // ----- XML -> spreadsheet ---------------------------------------------

    /** Converts the XML to a comma-separated CSV file (default options). */
    public static String exportToCsv(String xmlContent, File output) {
        return exportToCsv(xmlContent, output, CsvHandler.CsvConfig.comma(), new ConversionConfig());
    }

    /** Converts the XML to a CSV file with the given delimiter/conversion options. */
    public static String exportToCsv(String xmlContent, File output,
                                     CsvHandler.CsvConfig csvConfig, ConversionConfig config) {
        try {
            Document doc = parseXml(xmlContent);
            new XmlSpreadsheetConverterService().convertXmlToCsv(doc, output, csvConfig, config);
            return "OK: " + output.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Converts the XML to an Excel (.xlsx) workbook (default options). */
    public static String exportToExcel(String xmlContent, File output) {
        return exportToExcel(xmlContent, output, new ConversionConfig());
    }

    /** Converts the XML to an Excel (.xlsx) workbook with the given conversion options. */
    public static String exportToExcel(String xmlContent, File output, ConversionConfig config) {
        try {
            Document doc = parseXml(xmlContent);
            new XmlSpreadsheetConverterService().convertXmlToExcel(doc, output, config);
            return "OK: " + output.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ----- spreadsheet -> XML ---------------------------------------------

    /** Imports an Excel (.xlsx) workbook back to XML (default options). @return XML or {@code "ERROR: …"}. */
    public static String excelToXml(File excelFile) {
        return excelToXml(excelFile, new ConversionConfig());
    }

    /** Imports an Excel (.xlsx) workbook back to XML. @return XML or {@code "ERROR: …"}. */
    public static String excelToXml(File excelFile, ConversionConfig config) {
        try {
            XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();
            return service.documentToString(service.convertExcelToXml(excelFile, config), config);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Imports a comma-separated CSV file back to XML (default options). @return XML or {@code "ERROR: …"}. */
    public static String csvToXml(File csvFile) {
        return csvToXml(csvFile, CsvHandler.CsvConfig.comma(), new ConversionConfig());
    }

    /** Imports a CSV file back to XML with the given delimiter/options. @return XML or {@code "ERROR: …"}. */
    public static String csvToXml(File csvFile, CsvHandler.CsvConfig csvConfig, ConversionConfig config) {
        try {
            XmlSpreadsheetConverterService service = new XmlSpreadsheetConverterService();
            return service.documentToString(service.convertCsvToXml(csvFile, csvConfig, config), config);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ----- helpers ---------------------------------------------------------

    /** Parses XML with an XXE-hardened factory (no DOCTYPE / external entities / external DTD / XInclude). */
    private static Document parseXml(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));
    }
}
