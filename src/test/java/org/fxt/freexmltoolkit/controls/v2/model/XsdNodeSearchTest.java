package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link XsdNodeSearch}, the UI-free matcher backing the search bar
 * in the structured XSD views (Tree and Graphic). Pure model tests — no JavaFX.
 */
class XsdNodeSearchTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <!-- top-level remark about invoices -->
              <xs:element name="invoice" type="InvoiceType"/>
              <xs:complexType name="InvoiceType">
                <xs:annotation>
                  <xs:documentation>An invoice sent to a customer</xs:documentation>
                  <xs:appinfo>billing-subsystem</xs:appinfo>
                </xs:annotation>
                <xs:sequence>
                  <xs:element name="amount" type="xs:decimal" default="0"/>
                  <xs:element name="currency" type="CurrencyCode"/>
                </xs:sequence>
                <xs:attribute name="status" type="xs:string" fixed="open"/>
              </xs:complexType>
              <xs:simpleType name="CurrencyCode">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="3"/>
                  <xs:enumeration value="EUR"/>
                  <xs:enumeration value="USD"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private XsdSchema schema() throws Exception {
        return new XsdNodeFactory().fromString(XSD);
    }

    private static List<String> names(List<XsdNode> nodes) {
        return nodes.stream().map(XsdNode::getName).toList();
    }

    @Test
    void findsByElementName() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "amount");
        assertEquals(1, matches.size());
        assertEquals("amount", matches.get(0).getName());
    }

    @Test
    void matchingIsCaseInsensitive() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "AMOUNT");
        assertEquals(1, matches.size());
        assertEquals("amount", matches.get(0).getName());
    }

    @Test
    void findsByDocumentationText() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "customer");
        assertTrue(matches.stream().anyMatch(n -> "InvoiceType".equals(n.getName())),
                "documentation text should match its owning type, got: " + names(matches));
    }

    @Test
    void findsByAppinfo() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "billing-subsystem");
        assertTrue(matches.stream().anyMatch(n -> "InvoiceType".equals(n.getName())),
                "appinfo should match its owning type, got: " + names(matches));
    }

    @Test
    void findsByAttributeFixedValue() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "open");
        assertTrue(matches.stream().anyMatch(n -> n instanceof XsdAttribute a && "status".equals(a.getName())),
                "fixed value should match the attribute, got: " + names(matches));
    }

    @Test
    void findsByElementType() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "xs:decimal");
        assertTrue(matches.stream().anyMatch(n -> "amount".equals(n.getName())),
                "type should match the element, got: " + names(matches));
    }

    @Test
    void findsByEnumerationValue() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "EUR");
        assertFalse(matches.isEmpty(), "enumeration value EUR should produce a match");
    }

    @Test
    void findsByFacetValueAndFacetName() throws Exception {
        assertFalse(XsdNodeSearch.findMatches(schema(), "maxLength").isEmpty(),
                "facet xml name should match");
        List<XsdNode> byValue = XsdNodeSearch.findMatches(schema(), "3");
        assertTrue(byValue.stream().anyMatch(n -> n instanceof XsdFacet),
                "facet value 3 should match a facet node");
    }

    @Test
    void findsByCommentContent() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "remark");
        assertTrue(matches.stream().anyMatch(n -> n instanceof XsdComment),
                "comment content should match, got: " + names(matches));
    }

    @Test
    void resultsAreInDocumentOrder() throws Exception {
        List<XsdNode> matches = XsdNodeSearch.findMatches(schema(), "invoice");
        List<String> matchNames = names(matches);
        int comment = matches.indexOf(matches.stream().filter(n -> n instanceof XsdComment).findFirst().orElseThrow());
        int element = matchNames.indexOf("invoice");
        int type = matchNames.indexOf("InvoiceType");
        assertTrue(comment >= 0 && element >= 0 && type >= 0, "expected comment, element and type, got: " + matchNames);
        assertTrue(comment < element, "comment precedes the element in document order");
        assertTrue(element < type, "global element precedes the type in document order");
    }

    @Test
    void blankOrNullInputsYieldEmptyResult() throws Exception {
        assertTrue(XsdNodeSearch.findMatches(schema(), "").isEmpty());
        assertTrue(XsdNodeSearch.findMatches(schema(), "   ").isEmpty());
        assertTrue(XsdNodeSearch.findMatches(schema(), null).isEmpty());
        assertTrue(XsdNodeSearch.findMatches(null, "x").isEmpty());
    }

    @Test
    void noMatchYieldsEmptyResult() throws Exception {
        assertTrue(XsdNodeSearch.findMatches(schema(), "no-such-token-xyz").isEmpty());
    }

    @Test
    void cyclicStructureDoesNotHang() {
        // The real model is structurally acyclic, but the traversal must stay
        // cycle-safe as defense-in-depth. Simulate a cycle via getChildren().
        XsdElement[] holder = new XsdElement[1];
        XsdElement a = new XsdElement("alpha") {
            @Override
            public List<XsdNode> getChildren() {
                return List.of(holder[0]);
            }
        };
        holder[0] = a;
        List<XsdNode> matches = XsdNodeSearch.findMatches(a, "alpha");
        assertEquals(1, matches.size());
    }
}
