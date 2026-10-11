package org.fxt.freexmltoolkit.service;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.helger.schematron.ISchematronResource;
import com.helger.schematron.schxslt.xslt2.SchematronResourceSchXslt_XSLT2;
import com.helger.schematron.schxslt.xslt2.SchematronSchXslt_XSLT2Cache;
import com.helger.schematron.svrl.AbstractSVRLMessage;
import com.helger.schematron.svrl.SVRLHelper;
import com.helger.schematron.svrl.SVRLMarshaller;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;

/**
 * Implementation of SchematronService backed by ph-schematron's SchXslt compiler
 * (XSLT 2.0 based, executed by Saxon). SchXslt resolves {@code sch:include},
 * {@code sch:extends} and abstract patterns itself and supports
 * {@code queryBinding="xslt2"/"xslt3"} rules.
 */
public class SchematronServiceImpl implements SchematronService {

    private static final Logger logger = LogManager.getLogger(SchematronServiceImpl.class);

    /**
     * Compiled Schematron resources shared across all service instances, keyed by
     * absolute path and invalidated when the file's last-modified timestamp changes.
     */
    private static final Map<String, CachedResource> COMPILED_CACHE = new ConcurrentHashMap<>();

    private record CachedResource(long lastModified, ISchematronResource resource) {
    }

    /**
     * Constructs a new SchematronServiceImpl. Compiled Schematron stylesheets are
     * cached statically, so instances are cheap to create.
     */
    public SchematronServiceImpl() {
        // Constructor
    }

    private static ISchematronResource resource(File schematronFile) {
        String key = schematronFile.getAbsolutePath();
        long lastModified = schematronFile.lastModified();
        CachedResource cached = COMPILED_CACHE.compute(key, (k, old) -> {
            if (old != null && old.lastModified() == lastModified) {
                return old;
            }
            if (old != null) {
                // ph-schematron's internal provider cache is keyed by path only and
                // would keep serving the stale compiled XSLT for the changed file
                SchematronSchXslt_XSLT2Cache.shared().clear();
            }
            SchematronResourceSchXslt_XSLT2 r = SchematronResourceSchXslt_XSLT2.builderFromFile(schematronFile)
                    .useCache(true)
                    // Schematrons without @queryBinding must compile like the previous
                    // Saxon/XSLT2 skeleton pipeline did, not fail with SchXslt's E0002.
                    .parameter("schxslt.compile.default-query-binding", "xslt2")
                    // Custom parameters would otherwise disable caching of the compiled XSLT
                    .forceCacheResult(true)
                    // SchXslt emits svrl:metadata, which the strict SVRL schema check of
                    // ph-schematron 10 rejects; the report itself is read fine without it.
                    .validateSVRL(false)
                    .build();
            return new CachedResource(lastModified, r);
        });
        return cached.resource();
    }

    @Override
    public List<SchematronValidationError> validateXml(String xmlContent, File schematronFile) throws SchematronLoadException {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return List.of(new SchematronValidationError(
                    "XML content is null or empty", null, null, 0, 0, "error"));
        }
        requireExistingSchematron(schematronFile);
        try {
            return performValidationWithSvrl(new StreamSource(new StringReader(xmlContent)), schematronFile).errors();
        } catch (SchematronLoadException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(), null, null, 0, 0, "error"));
        }
    }

    @Override
    public List<SchematronValidationError> validateXmlFile(File xmlFile, File schematronFile) throws SchematronLoadException {
        if (xmlFile == null || !xmlFile.exists()) {
            return List.of(new SchematronValidationError(
                    "XML file is null or does not exist", null, null, 0, 0, "error"));
        }
        requireExistingSchematron(schematronFile);
        try {
            return performValidationWithSvrl(new StreamSource(xmlFile), schematronFile).errors();
        } catch (SchematronLoadException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(), "validation-error", null, 0, 0, "error"));
        }
    }

    @Override
    public SchematronReport validateXmlWithSvrl(String xmlContent, File schematronFile)
            throws SchematronLoadException {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new SchematronReport(List.of(new SchematronValidationError(
                    "XML content is null or empty", null, null, 0, 0, "error")), null);
        }
        requireExistingSchematron(schematronFile);
        try {
            return performValidationWithSvrl(new StreamSource(new StringReader(xmlContent)), schematronFile);
        } catch (SchematronLoadException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return new SchematronReport(List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(), null, null, 0, 0, "error")), null);
        }
    }

    private static void requireExistingSchematron(File schematronFile) throws SchematronLoadException {
        if (schematronFile == null || !schematronFile.exists()) {
            throw new SchematronLoadException("Schematron file is null or does not exist: "
                    + (schematronFile != null ? schematronFile.getAbsolutePath() : "null"));
        }
    }

    private SchematronReport performValidationWithSvrl(Source xmlSource, File schematronFile) throws SchematronLoadException {
        SchematronOutputType svrl;
        try {
            svrl = resource(schematronFile).applySchematronValidationToSVRL(xmlSource);
        } catch (Exception e) {
            throw new SchematronLoadException("Failed to run Schematron validation with "
                    + schematronFile.getAbsolutePath() + ". Reason: " + e.getMessage(), e);
        }
        if (svrl == null) {
            // ph-schematron returns null when the Schematron itself could not be compiled
            throw new SchematronLoadException("Failed to compile Schematron file: "
                    + schematronFile.getAbsolutePath()
                    + ". The file may not be valid XML or contains invalid rules.");
        }

        List<SchematronValidationError> validationEvents = new ArrayList<>();
        for (AbstractSVRLMessage message : SVRLHelper.getAllFailedAssertions(svrl)) {
            validationEvents.add(toError(message, "error"));
        }
        for (AbstractSVRLMessage message : SVRLHelper.getAllSuccessfulReports(svrl)) {
            validationEvents.add(toError(message, "warning"));
        }

        String svrlString = null;
        try {
            // unvalidated for the same reason as validateSVRL(false) above
            svrlString = new SVRLMarshaller(false).getAsString(svrl);
        } catch (Exception e) {
            logger.warn("Could not marshal SVRL report for {}: {}", schematronFile, e.getMessage());
        }
        return new SchematronReport(validationEvents, svrlString);
    }

    private static SchematronValidationError toError(AbstractSVRLMessage message, String defaultSeverity) {
        String text = message.getText();
        String role = message.getRole();
        return new SchematronValidationError(
                text != null ? text.trim() : "",
                message.getTest(),
                message.getLocation(),
                0, 0,
                role != null && !role.isEmpty() ? role : defaultSeverity);
    }

    @Override
    public SchematronValidationResult validateXmlWithSchematron(File xmlFile, File schematronFile) throws SchematronLoadException {
        SchematronValidationResult result = new SchematronValidationResult();
        List<SchematronValidationError> errors = validateXmlFile(xmlFile, schematronFile);
        for (SchematronValidationError error : errors) {
            if ("error".equalsIgnoreCase(error.severity()) || "fatal".equalsIgnoreCase(error.severity())) {
                result.addError(error.message());
            } else if ("warning".equalsIgnoreCase(error.severity())) {
                result.addWarning(error.message());
            }
        }
        return result;
    }

    @Override
    public boolean isValidSchematronFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        try {
            return resource(file).isValidSchematron();
        } catch (Exception e) {
            logger.warn("isValidSchematronFile check failed for {}", file.getAbsolutePath(), e);
            return false;
        }
    }
}
