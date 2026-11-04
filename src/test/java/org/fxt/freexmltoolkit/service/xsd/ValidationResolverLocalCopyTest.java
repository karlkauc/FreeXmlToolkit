package org.fxt.freexmltoolkit.service.xsd;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.service.LocalSchemaCopies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validation reads a remote import from its local copy next to the importing schema.
 */
class ValidationResolverLocalCopyTest {

    private String previous;

    @BeforeEach
    void enable() {
        previous = System.getProperty(LocalSchemaCopies.PROPERTY);
        System.setProperty(LocalSchemaCopies.PROPERTY, "true");
    }

    @AfterEach
    void restore() {
        if (previous == null) {
            System.clearProperty(LocalSchemaCopies.PROPERTY);
        } else {
            System.setProperty(LocalSchemaCopies.PROPERTY, previous);
        }
    }

    @Test
    void remoteImportIsServedFromItsLocalCopy(@TempDir Path dir) throws Exception {
        Path main = Files.writeString(dir.resolve("main.xsd"), "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        Path copy = Files.writeString(dir.resolve("xml.xsd"),
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' "
                        + "targetNamespace='http://www.w3.org/XML/1998/namespace'/>");
        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        var input = resolver.resolveResource("http://www.w3.org/2001/XMLSchema",
                "http://www.w3.org/XML/1998/namespace", null, "http://www.w3.org/2001/xml.xsd",
                main.toUri().toString());
        assertNotNull(input);
        assertEquals(copy.toUri().toString(), input.getSystemId());
    }

    @Test
    void anExistingLocalFileWinsOverEverythingElse(@TempDir Path dir) throws Exception {
        Path main = Files.writeString(dir.resolve("main.xsd"), "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        Path local = Files.writeString(dir.resolve("xmldsig-core-schema.xsd"),
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='http://www.w3.org/2000/09/xmldsig#'/>");
        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        var input = resolver.resolveResource("http://www.w3.org/2001/XMLSchema",
                "http://www.w3.org/2000/09/xmldsig#", null, "xmldsig-core-schema.xsd", main.toUri().toString());
        assertNotNull(input);
        assertEquals(local.toUri().toString(), input.getBaseURI());
    }
}
