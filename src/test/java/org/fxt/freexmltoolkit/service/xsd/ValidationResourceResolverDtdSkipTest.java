package org.fxt.freexmltoolkit.service.xsd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.ls.LSInput;

/**
 * Tests that ValidationResourceResolver answers DTD requests with an empty DTD.
 * DTDs (e.g. XMLSchema.dtd, datatypes.dtd) are not needed for XSD validation and should not
 * trigger remote downloads from w3.org. Answering null would leave them to the parser, which
 * fetches them itself and rejects the whole schema if that fails.
 */
class ValidationResourceResolverDtdSkipTest {

    private org.w3c.dom.ls.LSResourceResolver resolver;

    @BeforeEach
    void setUp() {
        var schemaResolver = new SchemaResolver(XsdParseOptions.defaults());
        resolver = schemaResolver.createLSResourceResolver(Path.of("."));
    }

    @Test
    void shouldSkipXmlSchemaDtd() throws IOException {
        var result = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema",
                null,
                "-//W3C//DTD XMLSchema 200102//EN",
                "http://www.w3.org/2001/XMLSchema.dtd",
                null
        );
        assertEmpty(result);
    }

    @Test
    void shouldSkipDatatypesDtd() throws IOException {
        var result = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema",
                null,
                null,
                "datatypes.dtd",
                null
        );
        assertEmpty(result);
    }

    @Test
    void shouldSkipAnyDtdSystemId() throws IOException {
        var result = resolver.resolveResource(
                null,
                null,
                null,
                "http://example.com/some-schema.dtd",
                null
        );
        assertEmpty(result);
    }

    @Test
    void shouldSkipADtdWithoutTheUsualSuffix() throws IOException {
        var result = resolver.resolveResource(
                javax.xml.XMLConstants.XML_DTD_NS_URI,
                null,
                null,
                "http://example.com/doctype",
                null
        );
        assertEmpty(result);
    }

    private static void assertEmpty(LSInput result) throws IOException {
        assertNotNull(result, "an empty DTD keeps the parser from fetching one");
        assertEquals(0, result.getByteStream().readAllBytes().length);
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
