package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;

import org.fxt.freexmltoolkit.domain.GeneratedFile;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

/**
 * UI-free generation of sample XML from an XSD with a {@link GenerationProfile} — per-XPath rules
 * (fixed/sequence/enum-cycle/template/…), named profiles and batch output. Wraps
 * {@link ProfiledXmlGeneratorService} over a parsed {@link XsdDocumentationData}. The richer
 * counterpart of {@link SampleXmlRunner}; run off the UI thread (it parses and walks the schema).
 */
public final class ProfiledSampleRunner {

    private ProfiledSampleRunner() {
    }

    /**
     * @return the element/attribute XPaths of the schema, for building per-XPath rules; an empty
     * list if the schema is missing or cannot be parsed.
     */
    public static List<XPathInfo> extractXPaths(File xsd) {
        XsdDocumentationData data = process(xsd);
        if (data == null) {
            return List.of();
        }
        try {
            return new ProfiledXmlGeneratorService().extractXPaths(data);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Generates a single sample document for the profile.
     *
     * @return the generated XML, or {@code "ERROR: …"} on failure
     */
    public static String generate(File xsd, GenerationProfile profile) {
        if (xsd == null || !xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            XsdDocumentationData data = process(xsd);
            if (data == null) {
                return "ERROR: could not parse schema: " + xsd.getName();
            }
            return new ProfiledXmlGeneratorService().generate(profile, data, xsd.getAbsolutePath());
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Generates the profile's batch of files ({@code batchCount} documents named by
     * {@code fileNamePattern}).
     *
     * @return the generated files, or an empty list on failure
     */
    public static List<GeneratedFile> generateBatch(File xsd, GenerationProfile profile) {
        if (xsd == null || !xsd.isFile()) {
            return List.of();
        }
        try {
            XsdDocumentationData data = process(xsd);
            if (data == null) {
                return List.of();
            }
            return new ProfiledXmlGeneratorService().generateBatch(profile, data, xsd.getAbsolutePath());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Writes each generated file into {@code dir} (named by its {@code fileName}).
     *
     * @return the files actually written
     */
    public static List<File> writeBatch(File dir, List<GeneratedFile> files) {
        List<File> written = new java.util.ArrayList<>();
        if (dir == null || files == null) {
            return written;
        }
        for (GeneratedFile file : files) {
            try {
                File out = new File(dir, file.fileName());
                java.nio.file.Files.writeString(out.toPath(), file.content());
                written.add(out);
            } catch (Exception e) {
                // best-effort: skip files that fail to write
            }
        }
        return written;
    }

    /** Parses the XSD into the documentation data the generator needs; {@code null} on failure. */
    private static XsdDocumentationData process(File xsd) {
        if (xsd == null || !xsd.isFile()) {
            return null;
        }
        try {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(xsd.getAbsolutePath());
            service.processXsd(Boolean.TRUE);
            return service.xsdDocumentationData;
        } catch (Exception e) {
            return null;
        }
    }
}
