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
            LOGGER.error("Could not extract the XPaths of {}", xsd, e);
            return List.of();
        }
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(ProfiledSampleRunner.class);

    /**
     * Generates a single sample document for the profile.
     *
     * @return the generated XML, or {@code "ERROR: …"} on failure
     */
    public static String generate(File xsd, GenerationProfile profile) {
        return generateResult(xsd, profile).text();
    }

    /** As {@link #generate(File, GenerationProfile)}, additionally reporting why a failed run failed. */
    public static SampleXmlRunner.SampleRun generateResult(File xsd, GenerationProfile profile) {
        if (xsd == null || !xsd.isFile()) {
            return new SampleXmlRunner.SampleRun("ERROR: file not found: " + xsd,
                    org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("file_not_found"));
        }
        try {
            XsdDocumentationData data = process(xsd);
            if (data == null) {
                return new SampleXmlRunner.SampleRun("ERROR: could not parse schema: " + xsd.getName(),
                        org.fxt.freexmltoolkit.service.telemetry.QueryFailure.invalidInput("schema_not_parsed"));
            }
            return new SampleXmlRunner.SampleRun(
                    new ProfiledXmlGeneratorService().generate(profile, data, xsd.getAbsolutePath()), null);
        } catch (Exception e) {
            return new SampleXmlRunner.SampleRun("ERROR: " + e.getMessage(), org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(e));
        }
    }

    /**
     * Generates the profile's batch of files ({@code batchCount} documents named by
     * {@code fileNamePattern}).
     *
     * @return the generated files, or an empty list on failure
     */
    public static List<GeneratedFile> generateBatch(File xsd, GenerationProfile profile) {
        return generateBatch(xsd, profile, null);
    }

    /**
     * Like {@link #generateBatch(File, GenerationProfile)}, for files that will be written into
     * {@code outputDir}: their schema reference is made relative to that folder, so the batch
     * validates from where it lands (see {@link org.fxt.freexmltoolkit.service.SampleSchemaLocation}).
     *
     * @param outputDir the folder the files will be written to, or {@code null} when unknown
     * @return the generated files, or an empty list on failure
     */
    public static List<GeneratedFile> generateBatch(File xsd, GenerationProfile profile, File outputDir) {
        if (xsd == null || !xsd.isFile()) {
            return List.of();
        }
        try {
            XsdDocumentationData data = process(xsd);
            if (data == null) {
                return List.of();
            }
            ProfiledXmlGeneratorService generator = new ProfiledXmlGeneratorService();
            generator.setOutputDirectory(outputDir != null ? outputDir.toPath() : null);
            return generator.generateBatch(profile, data, xsd.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Sample batch generation failed for {}", xsd, e);
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
                // the file is skipped; the caller reports how many were written
                LOGGER.error("Could not write sample file {}", file.fileName(), e);
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
            LOGGER.warn("Could not parse {} for sample generation", xsd, e);
            return null;
        }
    }
}
