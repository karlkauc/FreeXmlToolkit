package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;

/**
 * UI-free XML→CSV export for the editor, reusing
 * {@link XmlSpreadsheetConverterService}. Errors are returned as {@code "ERROR: …"}.
 */
public final class SpreadsheetActionRunner {

    private SpreadsheetActionRunner() {
    }

    /** Converts the XML to a comma-separated CSV file. @return {@code "OK: <path>"} or {@code "ERROR: …"}. */
    public static String exportToCsv(String xmlContent, File output) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));
            new XmlSpreadsheetConverterService().convertXmlToCsv(
                    doc, output, CsvHandler.CsvConfig.comma(),
                    new XmlSpreadsheetConverterService.ConversionConfig());
            return "OK: " + output.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
