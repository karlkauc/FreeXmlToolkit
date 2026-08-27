package org.fxt.freexmltoolkit.service.catalog;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.fxt.freexmltoolkit.service.xsd.SchemaResolver;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the shipped {@code release/examples/catalog} demo end to end: every schema
 * reference in it points at the non-existent host {@code schemas.example.org} and must be
 * served from the local {@code schemas/} folder through the registered catalog.
 */
class CatalogExampleTest {

    private static final Path EXAMPLE = Path.of("release/examples/catalog").toAbsolutePath();

    private SchemaLibraryServiceImpl library;

    @BeforeEach
    void registerCatalog(@TempDir Path tmp) {
        assertTrue(EXAMPLE.resolve("catalog.xml").toFile().isFile(), "example catalog missing");
        library = new SchemaLibraryServiceImpl(tmp.resolve("lib.json"), new SchemaResourceCache(tmp.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        library.addCatalog(EXAMPLE.resolve("catalog.xml"));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, library);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
        library.awaitSave();   // background writer must finish before @TempDir cleanup
    }

    @Test
    void catalogParsesWithChainedNextCatalog() throws Exception {
        ParsedCatalog parsed = SchemaCatalogParser.parse(EXAMPLE.resolve("catalog.xml"));
        assertEquals(3, parsed.entryCount(), "system + rewriteSystem + public");
        assertEquals(5, parsed.allEntries().size(), "plus the two uri entries of namespaces-catalog.xml");
        assertTrue(library.catalogErrors().isEmpty(), library.catalogErrors().toString());
    }

    @Test
    void systemRewriteAndUriEntriesResolveToLocalFiles() {
        Path common = EXAMPLE.resolve("schemas/common.xsd");
        Path invoice = EXAMPLE.resolve("schemas/invoice.xsd");
        assertEquals(common.toUri(), library.resolveSystemId("http://schemas.example.org/common/1.0/common.xsd", null).orElseThrow());
        assertEquals(invoice.toUri(), library.resolveSystemId("http://schemas.example.org/invoice/1.0/invoice.xsd", null).orElseThrow());
        assertEquals(common.toUri(), library.resolvePublicId("-//Example//DTD Common Types 1.0//EN").orElseThrow());
        assertEquals(invoice.toString(), library.resolveNamespace("urn:example:invoice", SchemaKind.XSD).orElseThrow().location());
    }

    @Test
    void invoiceSchemaCompilesAndValidatesThroughTheCatalog() throws Exception {
        Schema schema = compileViaCatalog("http://schemas.example.org/invoice/1.0/invoice.xsd");
        Validator validator = schema.newValidator();
        assertDoesNotThrow(() -> validator.validate(new StreamSource(EXAMPLE.resolve("invoice.xml").toFile())));
        assertDoesNotThrow(() -> validator.validate(new StreamSource(EXAMPLE.resolve("invoice-namespace-only.xml").toFile())));
        SAXException error = assertThrows(SAXException.class,
                () -> validator.validate(new StreamSource(EXAMPLE.resolve("invoice-invalid.xml").toFile())));
        assertTrue(error.getMessage().contains("euro") || error.getMessage().contains("CurrencyCode")
                || error.getMessage().contains("total"), error.getMessage());
    }

    /** Compiles the schema referenced by an unreachable URL: only the catalog can satisfy it. */
    private Schema compileViaCatalog(String remoteSchemaUrl) throws Exception {
        File local = Path.of(library.resolveSystemId(remoteSchemaUrl, null).orElseThrow()).toFile();
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(EXAMPLE));
        // invoice.xsd's xs:import points at http://schemas.example.org/common/1.0/common.xsd
        return factory.newSchema(new StreamSource(local));
    }
}
