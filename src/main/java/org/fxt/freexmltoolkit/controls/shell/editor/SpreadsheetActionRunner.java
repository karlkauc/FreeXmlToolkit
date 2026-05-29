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
            // Harden against XXE: no DOCTYPE / external entities / external DTD / XInclude.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
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
