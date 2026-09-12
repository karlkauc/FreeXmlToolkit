package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

/**
 * Type-level documentation (complexType/simpleType pages, attribute and enumeration docs) is read
 * straight off the DOM by {@link XsdDocumentationHtmlService}, bypassing {@link XsdExtendedElement}.
 * It must follow the same Markdown rules as the element pipeline: OFF is a hard kill switch,
 * otherwise the node's own {@code @markdown} beats the mode's default.
 */
@DisplayName("XsdDocumentationHtmlService - type-level Markdown")
class XsdDocumentationTypeMarkdownTest {

    private static final File FIXTURE = new File("src/test/resources/test-appinfo-formats.xsd");

    private record Fixture(XsdDocumentationService service, XsdDocumentationHtmlService html) {
    }

    private static XsdDocumentationService parse(XsdDocumentationService.MarkdownMode mode) throws Exception {
        assertTrue(FIXTURE.exists(), "fixture must exist: " + FIXTURE);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(FIXTURE.getAbsolutePath());
        service.processXsd(mode);
        return service;
    }

    private static Fixture process(XsdDocumentationService.MarkdownMode mode) throws Exception {
        XsdDocumentationService service = parse(mode);
        XsdDocumentationHtmlService html = new XsdDocumentationHtmlService();
        html.setXsdDocumentationService(service);
        html.setDocumentationData(service.xsdDocumentationData);
        return new Fixture(service, html);
    }

    /** The joined documentation of a globally named type, as the type pages render it. */
    private static String typeDoc(Fixture f, String typeName) {
        Node typeNode = f.service().findTypeNodeByName(typeName);
        assertNotNull(typeNode, "type not found: " + typeName);
        return String.join("\n", f.html().getDocumentationsFromNode(typeNode).values());
    }

    /** First descendant element with this local name (optionally carrying attribute name=value). */
    private static Node descendant(Node parent, String localName, String attr, String value) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.ELEMENT_NODE && localName.equals(c.getLocalName())) {
                if (attr == null) {
                    return c;
                }
                Node a = (c.getAttributes() == null) ? null : c.getAttributes().getNamedItem(attr);
                if (a != null && value.equals(a.getNodeValue())) {
                    return c;
                }
            }
            Node nested = descendant(c, localName, attr, value);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String xpathOf(XsdDocumentationService service, String elementName) {
        return service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> elementName.equals(e.getElementName()))
                .map(XsdExtendedElement::getCurrentXpath)
                .findFirst()
                .orElseThrow(() -> new AssertionError("element not found: " + elementName));
    }

    private static void assertRendered(String html, String what) {
        assertTrue(html.contains("<strong>bold</strong>"), what + " must render, was: " + html);
    }

    private static void assertPlain(String html, String what) {
        assertTrue(html.contains("**bold**"), what + " must stay plain text, was: " + html);
        assertFalse(html.contains("<strong>"), what + " must not be rendered, was: " + html);
    }

    @Test
    @DisplayName("ALL renders type documentation, which used to be plain in every mode")
    void typePageRendersMarkdownInAllMode() throws Exception {
        Fixture f = process(XsdDocumentationService.MarkdownMode.ALL);

        assertRendered(typeDoc(f, "MarkdownType"), "simpleType with @markdown = true");
        assertRendered(typeDoc(f, "DocumentedComplexType"), "complexType with @markdown = true");
    }

    @Test
    @DisplayName("a type that opts out stays plain even in ALL")
    void typePageStaysPlainWhenTypeSaysFalse() throws Exception {
        Fixture f = process(XsdDocumentationService.MarkdownMode.ALL);

        assertPlain(typeDoc(f, "PlainType"), "simpleType with @markdown = false");
    }

    @Test
    @DisplayName("PER_NODE renders only the types that ask for it")
    void perNodeRendersOnlyFlaggedTypes() throws Exception {
        Fixture f = process(XsdDocumentationService.MarkdownMode.PER_NODE);

        assertRendered(typeDoc(f, "MarkdownType"), "@markdown = true");
        assertPlain(typeDoc(f, "PlainType"), "@markdown = false");
        assertPlain(typeDoc(f, "EnumMarkdownType"), "a silent type in PER_NODE");
    }

    @Test
    @DisplayName("OFF never renders a type, whatever @markdown says")
    void offModeNeverRenders() throws Exception {
        Fixture f = process(XsdDocumentationService.MarkdownMode.OFF);

        assertPlain(typeDoc(f, "MarkdownType"), "@markdown = true under OFF");
        assertPlain(typeDoc(f, "DocumentedComplexType"), "@markdown = true under OFF");
    }

    @Test
    @DisplayName("attribute and enumeration docs follow their own flag, not the enclosing type's")
    void attributeAndEnumerationDocsFollowTheirOwnFlag() throws Exception {
        Fixture f = process(XsdDocumentationService.MarkdownMode.ALL);

        // The complexType says @markdown = true, its attribute says false.
        Node complexType = f.service().findTypeNodeByName("DocumentedComplexType");
        Node attribute = descendant(complexType, "attribute", "name", "plainAttr");
        assertNotNull(attribute, "plainAttr not found");
        assertPlain(String.join("\n", f.html().getDocumentationsFromNode(attribute).values()),
                "an attribute that opts out");

        // The enumeration opts in while its own type stays silent.
        Fixture perNode = process(XsdDocumentationService.MarkdownMode.PER_NODE);
        Node enumType = perNode.service().findTypeNodeByName("EnumMarkdownType");
        Node enumeration = descendant(enumType, "enumeration", "value", "A");
        assertNotNull(enumeration, "enumeration A not found");
        assertRendered(String.join("\n", perNode.html().getDocumentationsFromNode(enumeration).values()),
                "an enumeration that opts in");
    }

    @Test
    @DisplayName("the type documentation cache is not shared across modes")
    void typeDocumentationCacheIsNotSharedAcrossModes() throws Exception {
        // One html service reused for two runs - it is a long-lived field of XsdDocumentationService,
        // so a second run with another mode must not be served the first run's HTML.
        XsdDocumentationHtmlService html = new XsdDocumentationHtmlService();

        XsdDocumentationService all = parse(XsdDocumentationService.MarkdownMode.ALL);
        html.setXsdDocumentationService(all);
        html.setDocumentationData(all.xsdDocumentationData);
        String rendered = String.join("\n",
                html.getTypeDocumentations(xpathOf(all, "TypeMarkdownInherited")).values());
        assertRendered(rendered, "first run in ALL");

        XsdDocumentationService off = parse(XsdDocumentationService.MarkdownMode.OFF);
        html.setXsdDocumentationService(off);
        html.setDocumentationData(off.xsdDocumentationData);
        String plain = String.join("\n",
                html.getTypeDocumentations(xpathOf(off, "TypeMarkdownInherited")).values());
        assertPlain(plain, "second run in OFF");
    }

    @Test
    @DisplayName("a html service without a documentation service is treated as OFF")
    void nullServiceIsTreatedAsOff() throws Exception {
        XsdDocumentationService service = parse(XsdDocumentationService.MarkdownMode.ALL);
        XsdDocumentationHtmlService bare = new XsdDocumentationHtmlService();

        Node typeNode = service.findTypeNodeByName("MarkdownType");
        String doc = String.join("\n", bare.getDocumentationsFromNode(typeNode).values());

        assertPlain(doc, "no service wired - must not NPE and must not render");
    }
}
