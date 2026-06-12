package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The pre-save gate for downloaded schemas must accept a well-formed schema even when
 * its relative imports are not resolvable yet (they are downloaded afterwards), while
 * still rejecting HTML error pages and garbage.
 */
class XmlServiceDownloadGateTest {

    @Test
    void acceptsSchemaWithUnresolvableRelativeImport() {
        // The FundsXML case: a valid schema importing a sibling that is not cached yet.
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                  <xs:import namespace="http://www.w3.org/2000/09/xmldsig#"
                             schemaLocation="xmldsig-core-schema.xsd"/>
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;
        assertTrue(XmlServiceImpl.isPlausibleSchemaDocument(xsd),
                "a schema with a not-yet-cached relative import must pass the download gate");
    }

    @Test
    void rejectsHtmlErrorPage() {
        String html = "<html><head><title>404</title></head><body>Not found</body></html>";
        assertFalse(XmlServiceImpl.isPlausibleSchemaDocument(html),
                "an HTML error page must not be cached as a schema");
    }

    @Test
    void rejectsGarbageAndEmptyContent() {
        assertFalse(XmlServiceImpl.isPlausibleSchemaDocument("this is not XML <"));
        assertFalse(XmlServiceImpl.isPlausibleSchemaDocument(""));
        assertFalse(XmlServiceImpl.isPlausibleSchemaDocument(null));
    }
}
