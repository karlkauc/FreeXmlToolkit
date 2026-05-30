package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.service.XsdDocumentationService;

import java.io.File;

/**
 * UI-free generation of a sample XML instance from an XSD, reusing
 * {@link XsdDocumentationService#generateSampleXml(boolean, int)}. Returns the
 * generated XML, or {@code "ERROR: …"} on failure. Run off the UI thread — the
 * generator parses and walks the whole schema.
 */
public final class SampleXmlRunner {

    private SampleXmlRunner() {
    }

    /**
     * @param xsd            the schema file (its location is referenced via {@code xsi:schemaLocation})
     * @param mandatoryOnly  emit only required elements/attributes when {@code true}
     * @param maxOccurrences cap on repeated elements
     * @return the sample XML document, or {@code "ERROR: …"}
     */
    public static String generate(File xsd, boolean mandatoryOnly, int maxOccurrences) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(xsd.getAbsolutePath());
            return service.generateSampleXml(mandatoryOnly, maxOccurrences);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
