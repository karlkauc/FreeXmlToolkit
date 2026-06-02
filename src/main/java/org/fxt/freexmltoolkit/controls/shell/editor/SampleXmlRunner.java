package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import org.fxt.freexmltoolkit.service.XsdDocumentationService;

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
        return generate(xsd, mandatoryOnly, maxOccurrences, false);
    }

    /**
     * @param realistic when {@code true}, generate facet/enumeration/pattern-aware leaf values via
     *                  the profiled generator instead of plain type-based placeholders
     * @see #generate(File, boolean, int)
     */
    public static String generate(File xsd, boolean mandatoryOnly, int maxOccurrences, boolean realistic) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(xsd.getAbsolutePath());
            if (!realistic) {
                return service.generateSampleXml(mandatoryOnly, maxOccurrences);
            }
            service.processXsd(Boolean.TRUE);
            var profile = new org.fxt.freexmltoolkit.domain.GenerationProfile("Realistic");
            profile.setMandatoryOnly(mandatoryOnly);
            profile.setMaxOccurrences(maxOccurrences);
            return new org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService()
                    .generateRealistic(profile, service.xsdDocumentationData, xsd.getAbsolutePath());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
