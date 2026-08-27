package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The namespace an instance element must carry is determined by the schema document that
 * declares it (its {@code targetNamespace} / {@code elementFormDefault}), not by the main
 * schema — children of a type from an imported schema live in the imported namespace.
 */
class XsdElementNamespaceResolverTest {

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="urn:common" elementFormDefault="qualified">
              <xs:element name="global"/>
              <xs:complexType name="Address">
                <xs:sequence>
                  <xs:element name="street"/>
                  <xs:element name="local" form="unqualified"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>""";

    private static final String UNQUALIFIED = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:main">
              <xs:element name="root">
                <xs:complexType><xs:sequence>
                  <xs:element name="child"/>
                  <xs:element name="forced" form="qualified"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>""";

    private static final String CHAMELEON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="anything"/>
            </xs:schema>""";

    @Test
    void localElementOfQualifiedImportedTypeIsInImportedNamespace() {
        assertEquals("urn:common", resolve(COMMON, "street", null));
    }

    @Test
    void globalElementIsAlwaysInTargetNamespace() {
        assertEquals("urn:common", resolve(COMMON, "global", null));
        assertEquals("urn:main", resolve(UNQUALIFIED, "root", null));
    }

    @Test
    void localElementWithFormUnqualifiedHasNoNamespace() {
        assertNull(resolve(COMMON, "local", null));
    }

    @Test
    void localElementOfUnqualifiedSchemaHasNoNamespaceUnlessFormQualified() {
        assertNull(resolve(UNQUALIFIED, "child", null));
        assertEquals("urn:main", resolve(UNQUALIFIED, "forced", null));
    }

    @Test
    void explicitSourceNamespaceWins() {
        XsdExtendedElement el = element(COMMON, "street");
        el.setSourceNamespace("urn:ref");
        assertEquals("urn:ref", XsdElementNamespaceResolver.resolveNamespaceUri(el, data(null)));
    }

    @Test
    void chameleonSchemaWithoutTargetNamespaceFallsBackToMainTargetNamespace() {
        assertEquals("urn:main", resolve(CHAMELEON, "anything", "urn:main"));
    }

    @Test
    void elementWithoutDomNodeFallsBackToMainTargetNamespace() {
        XsdExtendedElement el = new XsdExtendedElement();
        el.setElementName("x");
        assertEquals("urn:main", XsdElementNamespaceResolver.resolveNamespaceUri(el, data("urn:main")));
    }

    // -- helpers --------------------------------------------------------------------------

    private static String resolve(String schema, String elementName, String mainTargetNamespace) {
        return XsdElementNamespaceResolver.resolveNamespaceUri(element(schema, elementName), data(mainTargetNamespace));
    }

    private static XsdDocumentationData data(String targetNamespace) {
        XsdDocumentationData data = new XsdDocumentationData();
        data.setTargetNamespace(targetNamespace);
        return data;
    }

    private static XsdExtendedElement element(String schema, String elementName) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(schema)));
            NodeList elements = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
            for (int i = 0; i < elements.getLength(); i++) {
                Element e = (Element) elements.item(i);
                if (elementName.equals(e.getAttribute("name"))) {
                    XsdExtendedElement el = new XsdExtendedElement();
                    el.setElementName(elementName);
                    el.setCurrentNode(e);
                    return el;
                }
            }
            throw new AssertionError("no element " + elementName);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
