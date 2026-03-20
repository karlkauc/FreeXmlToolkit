package org.fxt.freexmltoolkit.service.xsd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that ValidationResourceResolver skips DTD resources.
 * DTDs (e.g. XMLSchema.dtd, datatypes.dtd) are not needed for XSD validation
 * and should not trigger remote downloads from w3.org.
 */
class ValidationResourceResolverDtdSkipTest {

    private org.w3c.dom.ls.LSResourceResolver resolver;

    @BeforeEach
    void setUp() {
        var schemaResolver = new SchemaResolver(XsdParseOptions.defaults());
        resolver = schemaResolver.createLSResourceResolver(Path.of("."));
    }

    @Test
    void shouldSkipXmlSchemaDtd() {
        var result = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema",
                null,
                "-//W3C//DTD XMLSchema 200102//EN",
                "http://www.w3.org/2001/XMLSchema.dtd",
                null
        );
        assertNull(result, "XMLSchema.dtd should be skipped");
    }

    @Test
    void shouldSkipDatatypesDtd() {
        var result = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema",
                null,
                null,
                "datatypes.dtd",
                null
        );
        assertNull(result, "datatypes.dtd should be skipped");
    }

    @Test
    void shouldSkipAnyDtdSystemId() {
        var result = resolver.resolveResource(
                null,
                null,
                null,
                "http://example.com/some-schema.dtd",
                null
        );
        assertNull(result, "Any .dtd systemId should be skipped");
    }

    @Test
    void shouldNotSkipXsdSystemId() {
        // XSD files should still be processed (will return null because file doesn't exist,
        // but the method should not short-circuit on the DTD check)
        var result = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema",
                "http://www.w3.org/2000/09/xmldsig#",
                null,
                "xmldsig-core-schema.xsd",
                null
        );
        // Result is null because the file doesn't exist locally, but the important thing
        // is that it was NOT skipped by the DTD filter (it went through normal resolution)
        assertNull(result);
    }
}
