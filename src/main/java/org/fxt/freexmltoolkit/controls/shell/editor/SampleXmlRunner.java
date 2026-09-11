package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import org.fxt.freexmltoolkit.service.SampleXmlLimits;
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
        return generate(xsd, mandatoryOnly, maxOccurrences, realistic, null);
    }

    /**
     * @param seed seeds choices, repetitions and values for a reproducible sample, or {@code null} for random ones
     * @see #generate(File, boolean, int, boolean)
     */
    public static String generate(File xsd, boolean mandatoryOnly, int maxOccurrences, boolean realistic, Long seed) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(xsd.getAbsolutePath());
            if (!realistic) {
                if (seed != null) {
                    service.setSampleSeed(seed);
                }
                return service.generateSampleXml(mandatoryOnly, maxOccurrences);
            }
            // Expand only the first root, not every global element (bounded memory for large schemas)
            service.loadSchema(XsdDocumentationService.MarkdownMode.ALL);
            String root = service.getDefaultRootElementName();
            if (root == null) {
                return "<!-- No root element found in XSD -->";
            }
            service.expandForSample(root, mandatoryOnly, XsdDocumentationService.MarkdownMode.ALL);
            var profile = new org.fxt.freexmltoolkit.domain.GenerationProfile("Realistic");
            profile.setMandatoryOnly(mandatoryOnly);
            profile.setMaxOccurrences(maxOccurrences);
            var generator = seed != null
                    ? new org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService(seed)
                    : new org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService();
            return generator.generateRealistic(profile, service.xsdDocumentationData, xsd.getAbsolutePath(), root);
        } catch (SampleXmlLimits.LimitExceededException e) {
            return e.toXmlComment();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
