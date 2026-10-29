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

    /** Indentation of the editor's Format Document action ({@code EditorHost.formatActive}). */
    private static final int FORMAT_INDENT = 2;

    /**
     * Pretty-prints a generated sample exactly like the editor's Format Document action
     * (Shift+Alt+F), so the opened tab or written file needs no manual reformatting.
     * Error results ({@code "ERROR: …"}) and non-document results (e.g. a limit comment)
     * pass through unchanged. Run off the UI thread — it parses the whole document.
     */
    public static String format(String xml) {
        if (xml == null) {
            return null;
        }
        String head = xml.stripLeading();
        if (xml.startsWith("ERROR:") || !head.startsWith("<") || head.startsWith("<!--")) {
            return xml;
        }
        try {
            String formatted = org.fxt.freexmltoolkit.service.XmlService.prettyFormat(xml, FORMAT_INDENT);
            return formatted == null || formatted.isBlank() ? xml : formatted;
        } catch (Exception e) {
            return xml; // never lose the generated content over a formatting problem
        }
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
        return generateResult(xsd, mandatoryOnly, maxOccurrences, realistic, seed).text();
    }

    /**
     * A generated sample as shown to the user plus, for a failed run, its telemetry classification.
     *
     * @param text    the sample XML, an XML comment explaining a limit, or {@code "ERROR: …"}
     * @param failure why the run failed ({@code null} = a sample was produced)
     */
    public record SampleRun(String text, org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure) {
    }

    /** As {@link #generate(File, boolean, int, boolean, Long)}, additionally reporting why a failed run failed. */
    public static SampleRun generateResult(File xsd, boolean mandatoryOnly, int maxOccurrences, boolean realistic,
                                           Long seed) {
        if (!xsd.isFile()) {
            return new SampleRun("ERROR: file not found: " + xsd, org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("file_not_found"));
        }
        try {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(xsd.getAbsolutePath());
            if (seed != null) {
                // The expansion generates the sample data of every element and attribute, which the realistic
                // generator emits for attributes
                service.setSampleSeed(seed);
            }
            if (!realistic) {
                return new SampleRun(service.generateSampleXml(mandatoryOnly, maxOccurrences), null);
            }
            // Expand only the first root, not every global element (bounded memory for large schemas)
            service.loadSchema(XsdDocumentationService.MarkdownMode.ALL);
            String root = service.getDefaultRootElementName();
            if (root == null) {
                return new SampleRun("<!-- No root element found in XSD -->", org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("no_root_element"));
            }
            service.expandForSample(root, mandatoryOnly, XsdDocumentationService.MarkdownMode.ALL);
            var profile = new org.fxt.freexmltoolkit.domain.GenerationProfile("Realistic");
            profile.setMandatoryOnly(mandatoryOnly);
            profile.setMaxOccurrences(maxOccurrences);
            var generator = seed != null
                    ? new org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService(seed)
                    : new org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService();
            return new SampleRun(generator.generateRealistic(profile, service.xsdDocumentationData,
                    xsd.getAbsolutePath(), root), null);
        } catch (SampleXmlLimits.LimitExceededException e) {
            // the schema asks for more than the sample limits allow: not a failure of the app
            return new SampleRun(e.toXmlComment(), new org.fxt.freexmltoolkit.service.telemetry.QueryFailure("limit_exceeded",
                    org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.UNSUPPORTED, null));
        } catch (Exception e) {
            return new SampleRun("ERROR: " + e.getMessage(), org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
        }
    }
}
