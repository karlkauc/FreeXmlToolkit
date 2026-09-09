package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.fxt.freexmltoolkit.service.xsd.adapters.XsdModelAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the three encodings of a JavaDoc-style {@code xs:appinfo} tag that the toolkit accepts:
 * the canonical {@code source="@since"} plus text content, the legacy
 * {@code source="@since 4.0.0"} with no text, and the duplicated artifact older versions of the
 * editor produced. All of them are written back in the canonical form.
 */
@DisplayName("xs:appinfo tag encodings")
class XsdAppInfoTagFormatTest {

    private static String schema(String annotationBody) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="Transaction">
                    <xs:annotation>
                      <xs:documentation>A single financial transaction.</xs:documentation>
                %s
                    </xs:annotation>
                    <xs:complexType><xs:sequence/></xs:complexType>
                  </xs:element>
                </xs:schema>
                """.formatted(annotationBody);
    }

    private static XsdAppInfo viaNodeFactory(String annotationBody) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(schema(annotationBody));
        return firstElement(schema).getAppinfo();
    }

    private static XsdAppInfo viaModelAdapter(String annotationBody) throws Exception {
        ParsedSchema parsed = new XsdParsingServiceImpl()
                .parse(schema(annotationBody), null, XsdParseOptions.defaults());
        XsdSchema model = new XsdModelAdapter(XsdParseOptions.defaults()).toXsdModel(parsed);
        return firstElement(model).getAppinfo();
    }

    private static XsdElement firstElement(XsdSchema schema) {
        assertNotNull(schema, "schema must parse");
        return schema.getChildren().stream()
                .filter(XsdElement.class::isInstance)
                .map(XsdElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no top-level element in the parsed schema"));
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("canonical form: tag in @source, value in the text content")
        void canonicalForm() throws Exception {
            String body = """
                          <xs:appinfo source="@since">4.0.0</xs:appinfo>
                          <xs:appinfo source="@version">1.2</xs:appinfo>
                          <xs:appinfo source="@see">{@link /FundsXML4/ControlData}</xs:appinfo>
                          <xs:appinfo source="@deprecated">Use {@link /New} instead.</xs:appinfo>
                          <xs:appinfo source="@markdown">true</xs:appinfo>
                    """;
            for (XsdAppInfo appinfo : List.of(viaNodeFactory(body), viaModelAdapter(body))) {
                assertEquals("4.0.0", appinfo.getSince());
                assertEquals("1.2", appinfo.getVersion());
                assertEquals(List.of("{@link /FundsXML4/ControlData}"), appinfo.getSeeReferences());
                assertEquals("Use {@link /New} instead.", appinfo.getDeprecated());
                assertEquals(Boolean.TRUE, appinfo.getMarkdown());
            }
        }

        @Test
        @DisplayName("legacy form: tag and value packed into @source")
        void legacyForm() throws Exception {
            String body = """
                          <xs:appinfo source="@since 4.0.0"/>
                          <xs:appinfo source="@see {@link /FundsXML4/ControlData}"/>
                    """;
            for (XsdAppInfo appinfo : List.of(viaNodeFactory(body), viaModelAdapter(body))) {
                assertEquals("4.0.0", appinfo.getSince());
                assertEquals(List.of("{@link /FundsXML4/ControlData}"), appinfo.getSeeReferences());
            }
        }

        @Test
        @DisplayName("duplicated form written by older editor versions")
        void duplicatedForm() throws Exception {
            String body = """
                          <xs:appinfo source="@since 4.0.0">@since 4.0.0</xs:appinfo>
                    """;
            for (XsdAppInfo appinfo : List.of(viaNodeFactory(body), viaModelAdapter(body))) {
                assertEquals("4.0.0", appinfo.getSince(),
                        "the repeated tag must not leak into the value");
            }
        }

        @Test
        @DisplayName("a tag without a value keeps the tag")
        void tagWithoutValue() throws Exception {
            String body = """
                          <xs:appinfo source="@deprecated"/>
                    """;
            for (XsdAppInfo appinfo : List.of(viaNodeFactory(body), viaModelAdapter(body))) {
                assertTrue(appinfo.isDeprecated());
                assertEquals("", appinfo.getDeprecated());
            }
        }

        @Test
        @DisplayName("a real URI source is not mistaken for a tag")
        void uriSourceIsNotATag() throws Exception {
            String body = """
                          <xs:appinfo source="urn:example:tooling">generated by the mapping tool</xs:appinfo>
                    """;
            for (XsdAppInfo appinfo : List.of(viaNodeFactory(body), viaModelAdapter(body))) {
                assertEquals(1, appinfo.size());
                XsdAppInfo.AppInfoEntry entry = appinfo.getEntries().getFirst();
                assertNull(entry.getTag());
                assertEquals("urn:example:tooling", entry.getSource());
                assertEquals("generated by the mapping tool", entry.getContent());
            }
        }

        @Test
        @DisplayName("a tag-like prefix does not match a different tag")
        void tagBoundaryIsRespected() throws Exception {
            String body = """
                          <xs:appinfo source="@sinceVersion">4.0.0</xs:appinfo>
                    """;
            XsdAppInfo appinfo = viaNodeFactory(body);
            assertNull(appinfo.getSince(), "@sinceVersion is not @since");
            assertEquals("4.0.0", appinfo.getEntriesWithTag("@sinceVersion").getFirst().getContent());
        }
    }

    @Nested
    @DisplayName("Writing")
    class Writing {

        @Test
        @DisplayName("the legacy form is migrated to the canonical one on save")
        void legacyIsMigratedOnSave() throws Exception {
            XsdSchema schema = new XsdNodeFactory().fromString(schema("""
                          <xs:appinfo source="@since 4.0.0"/>
                          <xs:appinfo source="@see {@link /FundsXML4/ControlData}"/>
                    """));

            String serialized = new XsdSerializer().serialize(schema);

            assertTrue(serialized.contains("<xs:appinfo source=\"@since\">4.0.0</xs:appinfo>"), serialized);
            assertTrue(serialized.contains(
                    "<xs:appinfo source=\"@see\">{@link /FundsXML4/ControlData}</xs:appinfo>"), serialized);
            assertFalse(serialized.contains("source=\"@since 4.0.0\""), serialized);
        }

        @Test
        @DisplayName("the canonical form round-trips unchanged")
        void canonicalRoundTrip() throws Exception {
            XsdSchema schema = new XsdNodeFactory().fromString(schema("""
                          <xs:appinfo source="@since">4.0.0</xs:appinfo>
                          <xs:appinfo source="@markdown">true</xs:appinfo>
                    """));

            String serialized = new XsdSerializer().serialize(schema);

            assertTrue(serialized.contains("<xs:appinfo source=\"@since\">4.0.0</xs:appinfo>"), serialized);
            assertTrue(serialized.contains("<xs:appinfo source=\"@markdown\">true</xs:appinfo>"), serialized);
        }

        @Test
        @DisplayName("a tag without a value serializes self-closing")
        void tagWithoutValueSerializesSelfClosing() {
            XsdAppInfo appinfo = new XsdAppInfo();
            appinfo.setDeprecated("");

            assertEquals(List.of("<xs:appinfo source=\"@deprecated\"/>"), appinfo.toXmlStrings());
        }

        @Test
        @DisplayName("@markdown is written as a plain boolean")
        void markdownRoundTrip() {
            XsdAppInfo appinfo = new XsdAppInfo();
            appinfo.setMarkdown(Boolean.TRUE);
            assertEquals(List.of("<xs:appinfo source=\"@markdown\">true</xs:appinfo>"), appinfo.toXmlStrings());
            assertEquals(Boolean.TRUE, appinfo.getMarkdown());

            appinfo.setMarkdown(Boolean.FALSE);
            assertEquals(List.of("<xs:appinfo source=\"@markdown\">false</xs:appinfo>"), appinfo.toXmlStrings());
            assertEquals(Boolean.FALSE, appinfo.getMarkdown());

            appinfo.setMarkdown(null);
            assertNull(appinfo.getMarkdown());
            assertFalse(appinfo.hasEntries());
        }
    }
}
