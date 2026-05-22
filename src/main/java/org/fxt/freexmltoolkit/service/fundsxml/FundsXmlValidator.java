/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.fundsxml;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

/**
 * Headless helper that runs "Validate against the active FundsXML schema" without
 * touching any JavaFX UI types. Separated from the menu/sidebar handlers so the flow
 * can be unit-tested with real fixture files.
 *
 * <p>Resolves the active schema path through {@link FundsXmlCache}, hands the XML
 * string off to {@link XmlService#validateText(String, File)}, and bundles the result
 * into a {@link ValidationOutcome} with a status enum and the parse-exception list.
 */
public class FundsXmlValidator {

    private static final Logger logger = LogManager.getLogger(FundsXmlValidator.class);

    private final FundsXmlCache cache;
    private final XmlService xmlService;

    public FundsXmlValidator(FundsXmlCache cache, XmlService xmlService) {
        this.cache = cache;
        this.xmlService = xmlService;
    }

    /**
     * Validates the given XML content against the active FundsXML schema.
     *
     * @param xmlContent the XML to check; {@code null} or blank yields
     *                   {@link Status#NO_XML_CONTENT}
     * @return a non-null outcome describing what happened
     */
    public ValidationOutcome validate(String xmlContent) {
        Path schemaPath = cache.getActiveSchemaFile();
        if (schemaPath == null) {
            return new ValidationOutcome(Status.NO_ACTIVE_SCHEMA, null, null, Collections.emptyList(), null);
        }
        if (xmlContent == null || xmlContent.isBlank()) {
            return new ValidationOutcome(Status.NO_XML_CONTENT, schemaPath, activeVersion(schemaPath),
                    Collections.emptyList(), null);
        }
        try {
            List<SAXParseException> errors = xmlService.validateText(xmlContent, schemaPath.toFile());
            if (errors == null || errors.isEmpty()) {
                return new ValidationOutcome(Status.VALID, schemaPath, activeVersion(schemaPath),
                        Collections.emptyList(), null);
            }
            return new ValidationOutcome(Status.INVALID, schemaPath, activeVersion(schemaPath), errors, null);
        } catch (Exception e) {
            logger.error("FundsXML validation crashed", e);
            return new ValidationOutcome(Status.ERROR, schemaPath, activeVersion(schemaPath),
                    Collections.emptyList(), e.getMessage());
        }
    }

    /**
     * Best-effort extraction of the version directory name from a schema path like
     * {@code .../fundsxml/schema/4.2.10/FundsXML4.xsd} → {@code "4.2.10"}.
     */
    static String activeVersion(Path schemaPath) {
        if (schemaPath == null || schemaPath.getParent() == null) {
            return null;
        }
        Path versionDir = schemaPath.getParent().getFileName();
        return versionDir == null ? null : versionDir.toString();
    }

    /** Status enum for {@link ValidationOutcome}. */
    public enum Status {
        VALID,             // Schema exists, XML parses, no errors
        INVALID,           // Schema exists, XML parses, but validation errors found
        NO_ACTIVE_SCHEMA,  // No active schema version configured
        NO_XML_CONTENT,    // The caller passed null/blank XML
        ERROR              // Unhandled exception during validation
    }

    /** Immutable result bundle. */
    public static final class ValidationOutcome {
        private final Status status;
        private final Path schemaPath;
        private final String schemaVersion;
        private final List<SAXParseException> errors;
        private final String errorMessage;

        ValidationOutcome(Status status, Path schemaPath, String schemaVersion,
                          List<SAXParseException> errors, String errorMessage) {
            this.status = status;
            this.schemaPath = schemaPath;
            this.schemaVersion = schemaVersion;
            this.errors = errors == null ? Collections.emptyList() : errors;
            this.errorMessage = errorMessage;
        }

        public Status status() { return status; }
        public Path schemaPath() { return schemaPath; }
        public String schemaVersion() { return schemaVersion; }
        public List<SAXParseException> errors() { return errors; }
        public String errorMessage() { return errorMessage; }
        public boolean isValid() { return status == Status.VALID; }
    }
}
