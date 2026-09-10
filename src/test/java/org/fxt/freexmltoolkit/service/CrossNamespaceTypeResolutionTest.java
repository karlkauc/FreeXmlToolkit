package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

/**
 * A type may derive from a type with the same local name in another namespace (UBL {@code udt:IdentifierType}
 * extends {@code ccts-cct:IdentifierType}, KSeF {@code TData} restricts {@code etd:TData}). Type lookups must follow
 * the prefix to the right namespace instead of resolving the local name back to the deriving type itself.
 */
class CrossNamespaceTypeResolutionTest {

    /** The deriving types; {@code BASE_PREFIX} is replaced by the prefix of the namespace holding the base types. */
    private static final String DERIVED_TYPES = """
              <xs:simpleType name="Code">
                <xs:restriction base="BASE_PREFIX:Code"><xs:length value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:complexType name="IdentifierType">
                <xs:simpleContent><xs:extension base="BASE_PREFIX:IdentifierType"/></xs:simpleContent>
              </xs:complexType>
            """;

    private static final String BASE_TYPES = """
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{3}"/></xs:restriction>
              </xs:simpleType>
              <xs:complexType name="IdentifierType">
                <xs:simpleContent>
                  <xs:extension base="xs:normalizedString">
                    <xs:attribute name="schemeID" type="xs:token"/>
                  </xs:extension>
                </xs:simpleContent>
              </xs:complexType>
            """;

    @TempDir
    Path dir;

    /**
     * @param derivedInMain {@code true}: the main schema derives from the imported one; {@code false}: the imported
     *                      schema derives from the main one. Both orders are covered because the local-name maps are
     *                      filled in document order, so either document can win the name clash.
     */
    @ParameterizedTest(name = "derived types in main schema: {0}")
    @ValueSource(booleans = {true, false})
    void sameLocalNameInAnotherNamespaceIsResolvedThroughThePrefix(boolean derivedInMain) throws Exception {
        Path main = writeSchemas(derivedInMain);
        String typePrefix = derivedInMain ? "m" : "l";

        String plain = assertDoesNotOverflow(() -> {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(main.toString());
            return service.generateSampleXml(false, 1);
        });
        assertGenerated(main, plain, typePrefix);

        String realistic = assertDoesNotOverflow(() -> {
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(main.toString());
            service.processXsd(Boolean.TRUE);
            GenerationProfile profile = new GenerationProfile("Realistic");
            profile.setMaxOccurrences(1);
            return new ProfiledXmlGeneratorService().generateRealistic(profile, service.xsdDocumentationData, main.toString());
        });
        assertGenerated(main, realistic, typePrefix);
    }

    private Path writeSchemas(boolean derivedInMain) throws Exception {
        String typePrefix = derivedInMain ? "m" : "l";
        String mainTypes = derivedInMain ? DERIVED_TYPES.replace("BASE_PREFIX", "l") : BASE_TYPES;
        String libTypes = derivedInMain ? BASE_TYPES : DERIVED_TYPES.replace("BASE_PREFIX", "m");

        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:m="urn:main" xmlns:l="urn:lib"
                           targetNamespace="urn:main" elementFormDefault="qualified">
                  <xs:import namespace="urn:lib" schemaLocation="lib.xsd"/>
                %s
                  <xs:element name="record">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="code" type="%s:Code"/>
                        <xs:element name="id" type="%s:IdentifierType"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """.formatted(mainTypes, typePrefix, typePrefix));
        Files.writeString(dir.resolve("lib.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:m="urn:main" xmlns:l="urn:lib"
                           targetNamespace="urn:lib">
                  <xs:import namespace="urn:main" schemaLocation="main.xsd"/>
                %s
                </xs:schema>
                """.formatted(libTypes));
        return main;
    }

    private static void assertGenerated(Path main, String xml, String typePrefix) throws Exception {
        assertFalse(xml.startsWith("<!--"), xml);
        assertTrue(xml.contains("<record"), xml);
        assertTrue(xml.contains("<code>"), "code element of type " + typePrefix + ":Code missing:\n" + xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(main.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    private static String assertDoesNotOverflow(java.util.concurrent.Callable<String> generation) throws Exception {
        try {
            return generation.call();
        } catch (StackOverflowError e) {
            throw new AssertionError("type resolution recursed endlessly", e);
        }
    }
}
