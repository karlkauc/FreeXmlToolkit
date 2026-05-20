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

import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FundsXmlValidator Tests")
class FundsXmlValidatorTest {

    private static final String TEST_VERSION = "4.2.0";

    @TempDir
    Path tempDir;

    private FundsXmlCache cache;
    private FundsXmlValidator validator;
    private final XmlService xmlService = XmlServiceImpl.getInstance();

    @BeforeEach
    void setUp() throws Exception {
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        cache = ctor.newInstance(tempDir.resolve("fundsxml"));
        validator = new FundsXmlValidator(cache, xmlService);
    }

    /**
     * Copies the FundsXML 4.2.0 schema fixture into the cache as the active version.
     */
    private void installSchema() throws Exception {
        Path src = new File("src/test/resources/FundsXML_420.xsd").toPath();
        assertTrue(Files.isRegularFile(src), "fixture FundsXML_420.xsd must exist");
        Path target = cache.getSchemaVersionDir(TEST_VERSION).resolve("FundsXML4.xsd");
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setActiveSchemaVersion(TEST_VERSION);
        cache.saveMetadata(meta);
    }

    @Test
    @DisplayName("VALID — passes a known-good FundsXML document")
    void validDocument() throws Exception {
        installSchema();
        String xml = Files.readString(new File("src/test/resources/FundsXML_420.xml").toPath());

        FundsXmlValidator.ValidationOutcome outcome = validator.validate(xml);

        assertEquals(FundsXmlValidator.Status.VALID, outcome.status());
        assertTrue(outcome.isValid());
        assertEquals(TEST_VERSION, outcome.schemaVersion());
        assertTrue(outcome.errors().isEmpty());
    }

    @Test
    @DisplayName("INVALID — surfaces parse errors for a known-bad FundsXML document")
    void invalidDocument() throws Exception {
        installSchema();
        String xml = Files.readString(new File("src/test/resources/FundsXML_420_Error.xml").toPath());

        FundsXmlValidator.ValidationOutcome outcome = validator.validate(xml);

        assertEquals(FundsXmlValidator.Status.INVALID, outcome.status());
        assertFalse(outcome.isValid());
        assertFalse(outcome.errors().isEmpty(),
                "the *_Error fixture is expected to produce at least one validation error");
    }

    @Test
    @DisplayName("NO_ACTIVE_SCHEMA — clear status when nothing is installed")
    void noActiveSchema() {
        FundsXmlValidator.ValidationOutcome outcome = validator.validate("<root/>");

        assertEquals(FundsXmlValidator.Status.NO_ACTIVE_SCHEMA, outcome.status());
        assertNull(outcome.schemaPath());
        assertTrue(outcome.errors().isEmpty());
    }

    @Test
    @DisplayName("NO_XML_CONTENT — null / blank input is reported separately from validity errors")
    void noXmlContent() throws Exception {
        installSchema();

        assertEquals(FundsXmlValidator.Status.NO_XML_CONTENT, validator.validate(null).status());
        assertEquals(FundsXmlValidator.Status.NO_XML_CONTENT, validator.validate("").status());
        assertEquals(FundsXmlValidator.Status.NO_XML_CONTENT, validator.validate("   \n\t").status());
    }

    @Test
    @DisplayName("schemaPath and schemaVersion reflect the active selection")
    void resultMetadata() throws Exception {
        installSchema();
        FundsXmlValidator.ValidationOutcome outcome = validator.validate("<not-fundsxml/>");

        assertNotNull(outcome.schemaPath());
        assertEquals(TEST_VERSION, outcome.schemaVersion());
        assertEquals("FundsXML4.xsd", outcome.schemaPath().getFileName().toString());
    }

    @Test
    @DisplayName("activeVersion helper extracts the parent directory name")
    void activeVersionHelper() {
        Path schema = Path.of("/cache/fundsxml/schema/4.2.10/FundsXML4.xsd");
        assertEquals("4.2.10", FundsXmlValidator.activeVersion(schema));
        assertNull(FundsXmlValidator.activeVersion(null));
        assertNull(FundsXmlValidator.activeVersion(Path.of("standalone.xsd")));
    }
}
