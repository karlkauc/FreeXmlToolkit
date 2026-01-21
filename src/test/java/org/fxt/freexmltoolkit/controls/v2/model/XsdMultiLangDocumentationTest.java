package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for multi-language documentation parsing and serialization.
 */
class XsdMultiLangDocumentationTest {

    @Test
    void testParseMultipleDocumentationsWithXmlLang() {
        String xsdContent = """
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

        XsdSchema schema = XsdNodeFactory.parseString(xsdContent);
        assertNotNull(schema);

        // Get the FundsXML4 element
        List<XsdNode> children = schema.getChildren();
        assertEquals(1, children.size());

        XsdElement element = (XsdElement) children.get(0);
        assertEquals("FundsXML4", element.getName());

        // Check documentations
        List<XsdDocumentation> documentations = element.getDocumentations();
        System.out.println("Number of documentations found: " + documentations.size());

        for (XsdDocumentation doc : documentations) {
            System.out.println("Lang: " + doc.getLang() + ", Text: " + doc.getText());
        }

        assertEquals(4, documentations.size(), "Should have 4 documentation entries");

        // Verify each language
        assertTrue(documentations.stream().anyMatch(d -> "en".equals(d.getLang())));
        assertTrue(documentations.stream().anyMatch(d -> "de".equals(d.getLang())));
        assertTrue(documentations.stream().anyMatch(d -> "fr".equals(d.getLang())));
        assertTrue(documentations.stream().anyMatch(d -> "nl".equals(d.getLang())));
    }
}
