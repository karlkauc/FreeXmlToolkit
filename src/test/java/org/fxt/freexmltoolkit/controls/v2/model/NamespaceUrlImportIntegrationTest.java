package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-network integration test for the namespace-URL import fallback.
 *
 * <p>Loads a schema importing the W3C xmldsig namespace WITHOUT a local copy of
 * {@code xmldsig-core-schema.xsd} and verifies the schema is discovered via the
 * namespace URL (redirect chain to the spec directory), downloaded, cached, and
 * on a second load served offline from the cache.</p>
 *
 * <p>Opt-in (requires internet access to www.w3.org):
 * <pre>
 *   ./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.model.NamespaceUrlImportIntegrationTest" -Dnamespace.integration=true
 * </pre></p>
 */
@EnabledIfSystemProperty(named = "namespace.integration", matches = "true")
class NamespaceUrlImportIntegrationTest {

    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";

    private static final String MAIN_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                       elementFormDefault="qualified">
                <xs:import namespace="http://www.w3.org/2000/09/xmldsig#"
                           schemaLocation="xmldsig-core-schema.xsd"/>
                <xs:element name="FundsXML4">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element ref="ds:Signature" minOccurs="0"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void xmldsigImportResolvesViaNamespaceUrlAndThenFromCache(@TempDir Path tempDir) throws Exception {
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), MAIN_XSD);

        // First load: no local xmldsig-core-schema.xsd -> network fallback via namespace URL
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        XsdSchema schema = factory.fromFile(mainFile);

        XsdImport xsdImport = firstImport(schema);
        assertNotNull(xsdImport.getImportedSchema(),
                "xmldsig import should be resolved via the namespace URL");
        assertNotNull(xsdImport.getResolvedPath(),
                "Resolved path should point to the cached schema file");
        assertTrue(Files.exists(xsdImport.getResolvedPath()),
                "Cached schema file should exist: " + xsdImport.getResolvedPath());
        assertTrue(schema.getImportedSchemas().containsKey(XMLDSIG_NS),
                "importedSchemas should be keyed by the xmldsig namespace");
        assertEquals(XMLDSIG_NS, xsdImport.getImportedSchema().getTargetNamespace(),
                "Downloaded schema must have the xmldsig target namespace");

        // Second load (fresh factory): must be served from the disk cache by targetNamespace
        XsdNodeFactory secondFactory = new XsdNodeFactory();
        secondFactory.setRemoteNamespaceFallbackEnabled(true);
        XsdSchema secondSchema = secondFactory.fromFile(mainFile);
        assertNotNull(firstImport(secondSchema).getImportedSchema(),
                "Second load should resolve from the schema cache");
    }

    private static XsdImport firstImport(XsdSchema schema) {
        return schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Import node should be present"));
    }
}
