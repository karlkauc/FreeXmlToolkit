package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.xsd.adapters.XsdModelAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that multi-language documentations are preserved during XSD flattening.
 *
 * <p>This test addresses the bug where flattening an XSD file would lose all but one
 * documentation element when multiple xs:documentation elements with xml:lang attributes
 * were present.
 *
 * <p>Root cause: XsdNodeFactory was using {@code getAttribute("xml:lang")} instead of
 * {@code getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang")} to read the
 * xml:lang attribute, which is a namespace-qualified attribute.
 */
class XsdFlatteningMultiLangDocTest {

    @Test
    void testMultiLanguageDocumentationsPreservedAfterFlattening() throws XsdParseException {
        // Create a test XSD with multiple documentation elements
        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="FundsXML4">
                <xs:annotation>
                  <xs:documentation xml:lang="en">Root element of FundsXML 4.2.11</xs:documentation>
                  <xs:documentation xml:lang="de">Wurzelelement von FundsXML 4.2.11</xs:documentation>
                  <xs:documentation xml:lang="fr">Élément racine de FundsXML 4.2.11</xs:documentation>
                  <xs:documentation xml:lang="nl">Hoofdelement van FundsXML 4.2.11</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                  <xs:sequence/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        // Parse the XSD
        XsdParsingService parsingService = new XsdParsingServiceImpl();
        ParsedSchema parsed = parsingService.parse(mainXsd, null, XsdParseOptions.defaults());

        // Convert to XsdSchema model using XsdModelAdapter
        XsdModelAdapter adapter = new XsdModelAdapter(XsdParseOptions.defaults());
        XsdSchema schema = adapter.toXsdModel(parsed);
        assertNotNull(schema);

        // Get the FundsXML4 element
        List<XsdNode> children = schema.getChildren();
        assertEquals(1, children.size(), "Should have one root element");

        XsdElement element = (XsdElement) children.get(0);
        assertEquals("FundsXML4", element.getName());

        // Check that all 4 documentations were parsed
        List<XsdDocumentation> documentations = element.getDocumentations();
        assertEquals(4, documentations.size(),
            "Should have 4 documentation entries (one for each language)");

        // Verify each language is present
        assertTrue(documentations.stream().anyMatch(d -> "en".equals(d.getLang()) && d.getText().contains("Root element")),
            "Should have English documentation");
        assertTrue(documentations.stream().anyMatch(d -> "de".equals(d.getLang()) && d.getText().contains("Wurzelelement")),
            "Should have German documentation");
        assertTrue(documentations.stream().anyMatch(d -> "fr".equals(d.getLang()) && d.getText().contains("Élément racine")),
            "Should have French documentation");
        assertTrue(documentations.stream().anyMatch(d -> "nl".equals(d.getLang()) && d.getText().contains("Hoofdelement")),
            "Should have Dutch documentation");

        // Serialize back to XSD
        XsdSerializer serializer = new XsdSerializer();
        String serialized = serializer.serialize(schema);

        // Verify all 4 documentations are in the serialized output
        assertTrue(serialized.contains("xml:lang=\"en\""), "Serialized XSD should contain xml:lang=\"en\"");
        assertTrue(serialized.contains("xml:lang=\"de\""), "Serialized XSD should contain xml:lang=\"de\"");
        assertTrue(serialized.contains("xml:lang=\"fr\""), "Serialized XSD should contain xml:lang=\"fr\"");
        assertTrue(serialized.contains("xml:lang=\"nl\""), "Serialized XSD should contain xml:lang=\"nl\"");

        assertTrue(serialized.contains("Root element of FundsXML 4.2.11"), "Should contain English text");
        assertTrue(serialized.contains("Wurzelelement von FundsXML 4.2.11"), "Should contain German text");
        assertTrue(serialized.contains("Élément racine de FundsXML 4.2.11"), "Should contain French text");
        assertTrue(serialized.contains("Hoofdelement van FundsXML 4.2.11"), "Should contain Dutch text");
    }

    @Test
    void testDocumentationWithoutLangAttribute() throws XsdParseException {
        // Test that documentations without xml:lang still work
        String xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="TestElement">
                <xs:annotation>
                  <xs:documentation>This is a documentation without language attribute</xs:documentation>
                </xs:annotation>
              </xs:element>
            </xs:schema>
            """;

        XsdParsingService parsingService = new XsdParsingServiceImpl();
        ParsedSchema parsed = parsingService.parse(xsd, null, XsdParseOptions.defaults());
        XsdModelAdapter adapter = new XsdModelAdapter(XsdParseOptions.defaults());
        XsdSchema schema = adapter.toXsdModel(parsed);

        XsdElement element = (XsdElement) schema.getChildren().get(0);
        List<XsdDocumentation> documentations = element.getDocumentations();

        assertEquals(1, documentations.size());
        assertNull(documentations.get(0).getLang(), "Should have no language attribute");
        assertTrue(documentations.get(0).getText().contains("This is a documentation"));
    }
}
