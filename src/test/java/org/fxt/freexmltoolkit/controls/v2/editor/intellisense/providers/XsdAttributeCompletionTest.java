package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Schema-driven attribute completion of {@link XsdCompletionProvider}. The caret is at the
 * {@code |} marker.
 */
class XsdAttributeCompletionTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:attributeGroup name="Audit">
                <xs:attribute name="createdBy" type="xs:string"/>
              </xs:attributeGroup>
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:attribute name="sku" type="xs:string" use="required"/>
                        <xs:attribute name="quantity" type="xs:int"/>
                        <xs:attribute name="note" type="xs:string"/>
                        <xs:attribute name="status">
                          <xs:simpleType>
                            <xs:restriction base="xs:string">
                              <xs:enumeration value="open"/>
                              <xs:enumeration value="shipped"/>
                            </xs:restriction>
                          </xs:simpleType>
                        </xs:attribute>
                        <xs:attribute name="gift" type="xs:boolean"/>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:ID" use="required"/>
                  <xs:attributeGroup ref="Audit"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdCompletionProvider provider;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        Path xsd = Files.writeString(tmp.resolve("order.xsd"), XSD);
        MutableXmlSchemaProvider schemaProvider = new MutableXmlSchemaProvider();
        assertTrue(schemaProvider.loadSchema(xsd.toFile()));
        provider = new XsdCompletionProvider(schemaProvider);
    }

    @Test
    void requiredAttributesComeFirst() {
        List<CompletionItem> items = complete("<order id=\"o1\">\n  <item |/>\n</order>");
        assertEquals(List.of("sku", "gift", "note", "quantity", "status"), labels(items));
        assertTrue(items.getFirst().isRequired());
        assertTrue(items.stream().allMatch(i -> i.getType() == CompletionItemType.ATTRIBUTE));
        assertEquals("sku", items.getFirst().getInsertText());
    }

    @Test
    void attributesInTheTagAreLeftOut() {
        assertEquals(List.of("gift", "note", "status"),
                labels(complete("<order id=\"o1\">\n  <item sku=\"a\" | quantity=\"2\"/>\n</order>")));
    }

    @Test
    void rootAttributesIncludeAttributeGroups() {
        List<String> labels = labels(complete("<order |>\n  <item sku=\"a\"/>\n</order>"));
        assertEquals("id", labels.getFirst());
        assertTrue(labels.contains("createdBy"), labels::toString);
    }

    @Test
    void attributeValuesFromEnumerationsAndBooleans() {
        List<CompletionItem> items = complete("<order id=\"o1\">\n  <item sku=\"a\" status=\"|\"/>\n</order>");
        assertEquals(List.of("open", "shipped"), labels(items));
        assertTrue(items.stream().allMatch(i -> i.getType() == CompletionItemType.VALUE));
        assertEquals(List.of("true", "false"), labels(complete("<order id=\"o1\">\n  <item\n    gift='|'/>\n</order>")));
        assertEquals(List.of(), labels(complete("<order id=\"o1\">\n  <item note=\"|\"/>\n</order>")));
    }

    @Test
    void specializedModesKeepTheirOwnValueSuggestions() {
        XmlContext ctx = ContextAnalyzer.analyze("<order id=\"\">", 11);
        assertTrue(provider.canProvideCompletions(ctx, EditorMode.XML_WITH_XSD));
        assertFalse(provider.canProvideCompletions(ctx, EditorMode.XSLT));
        assertFalse(provider.canProvideCompletions(ctx, EditorMode.SCHEMATRON));
    }

    @Test
    void xsltModeLeavesAttributesToTheXsltProvider() {
        XmlContext ctx = ContextAnalyzer.analyze("<order >", 7);
        assertTrue(provider.canProvideCompletions(ctx, EditorMode.XML_WITH_XSD));
        assertFalse(provider.canProvideCompletions(ctx, EditorMode.XSLT));
    }

    private List<CompletionItem> complete(String textWithMarker) {
        int caret = textWithMarker.indexOf('|');
        String text = textWithMarker.substring(0, caret) + textWithMarker.substring(caret + 1);
        return provider.getCompletions(ContextAnalyzer.analyze(text, caret));
    }

    private static List<String> labels(List<CompletionItem> items) {
        return items.stream().map(CompletionItem::getLabel).toList();
    }
}
