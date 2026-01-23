package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.service.xsd.adapters.XsdModelAdapter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the "Track source files (xs:appinfo)" feature works correctly.
 *
 * <p>When enabled, flattening should add xs:appinfo elements with source file information
 * to elements that came from included files.
 */
class XsdFlatteningSourceFileTrackingTest {

    @Test
    void testSourceFileTrackingAddedToIncludedElements() throws Exception {
        // Create a main XSD that includes another XSD
        String includedXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="PersonType">
                <xs:sequence>
                  <xs:element name="Name" type="xs:string"/>
                  <xs:element name="Age" type="xs:integer"/>
                </xs:sequence>
              </xs:complexType>
              <xs:simpleType name="EmailType">
                <xs:restriction base="xs:string">
                  <xs:pattern value=".+@.+"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="types.xsd"/>
              <xs:element name="Root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="Person" type="PersonType"/>
                    <xs:element name="Email" type="EmailType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        // Create temp files
        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-source-tracking-test");
        Path includedFile = tempDir.resolve("types.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(includedFile, includedXsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            // Parse with FLATTEN mode and source file tracking ENABLED
            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .addSourceFileAsAppinfo(true)
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            // Convert to XsdSchema model
            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Serialize to string
            XsdSerializer serializer = new XsdSerializer();
            String flattenedXsd = serializer.serialize(schema, XsdSortOrder.NAME_BEFORE_TYPE);

            // Verify that the flattened XSD contains source file tracking for included types
            assertTrue(flattenedXsd.contains("fxt:sourceFile") || flattenedXsd.contains("sourceFile"),
                    "Flattened XSD should contain source file tracking appinfo");

            // Verify that the included file name "types.xsd" is mentioned
            assertTrue(flattenedXsd.contains("types.xsd"),
                    "Flattened XSD should reference the included file name 'types.xsd'");

            // Verify that fxt:sourceFile does NOT contain extraneous namespace declarations
            // The element should only have xmlns:fxt, not xmlns:altova, xmlns:ds, etc.
            assertFalse(flattenedXsd.contains("xmlns:altova"),
                    "fxt:sourceFile should not contain xmlns:altova namespace");
            assertFalse(flattenedXsd.contains("xmlns:ds"),
                    "fxt:sourceFile should not contain xmlns:ds namespace");
            assertFalse(flattenedXsd.contains("xmlns:vc"),
                    "fxt:sourceFile should not contain xmlns:vc namespace");

            // Verify PersonType has source tracking
            boolean personTypeHasSource = false;
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType ct && "PersonType".equals(ct.getName())) {
                    XsdAppInfo appinfo = ct.getAppinfo();
                    if (appinfo != null && appinfo.hasEntries()) {
                        String appinfoStr = appinfo.toDisplayString();
                        if (appinfoStr.contains("types.xsd")) {
                            personTypeHasSource = true;
                        }
                    }
                }
            }
            assertTrue(personTypeHasSource, "PersonType should have source file appinfo from types.xsd");

            // Verify EmailType has source tracking
            boolean emailTypeHasSource = false;
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdSimpleType st && "EmailType".equals(st.getName())) {
                    XsdAppInfo appinfo = st.getAppinfo();
                    if (appinfo != null && appinfo.hasEntries()) {
                        String appinfoStr = appinfo.toDisplayString();
                        if (appinfoStr.contains("types.xsd")) {
                            emailTypeHasSource = true;
                        }
                    }
                }
            }
            assertTrue(emailTypeHasSource, "EmailType should have source file appinfo from types.xsd");

            System.out.println("Flattened XSD with source tracking:");
            System.out.println(flattenedXsd);

        } finally {
            // Cleanup
            java.nio.file.Files.deleteIfExists(includedFile);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testSourceFileTrackingNotAddedWhenDisabled() throws Exception {
        // Create a main XSD that includes another XSD
        String includedXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="AddressType">
                <xs:sequence>
                  <xs:element name="Street" type="xs:string"/>
                  <xs:element name="City" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="address.xsd"/>
              <xs:element name="Root" type="AddressType"/>
            </xs:schema>
            """;

        // Create temp files
        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-no-source-tracking-test");
        Path includedFile = tempDir.resolve("address.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(includedFile, includedXsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            // Parse with FLATTEN mode but source file tracking DISABLED (default)
            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .addSourceFileAsAppinfo(false)
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            // Convert to XsdSchema model
            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Serialize to string
            XsdSerializer serializer = new XsdSerializer();
            String flattenedXsd = serializer.serialize(schema, XsdSortOrder.NAME_BEFORE_TYPE);

            // Verify that no source file tracking is present
            assertFalse(flattenedXsd.contains("fxt:sourceFile"),
                    "Flattened XSD should NOT contain fxt:sourceFile when tracking is disabled");

            // Verify AddressType does NOT have source tracking appinfo
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType ct && "AddressType".equals(ct.getName())) {
                    XsdAppInfo appinfo = ct.getAppinfo();
                    if (appinfo != null && appinfo.hasEntries()) {
                        String appinfoStr = appinfo.toDisplayString();
                        assertFalse(appinfoStr.contains("address.xsd"),
                                "AddressType should NOT have source file appinfo when tracking is disabled");
                    }
                }
            }

            System.out.println("Flattened XSD without source tracking:");
            System.out.println(flattenedXsd);

        } finally {
            // Cleanup
            java.nio.file.Files.deleteIfExists(includedFile);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }
}
