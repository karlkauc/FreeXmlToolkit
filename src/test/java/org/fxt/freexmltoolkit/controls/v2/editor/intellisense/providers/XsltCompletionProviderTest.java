package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.junit.jupiter.api.Test;

/**
 * XSLT attribute, value and element completion from {@link XsltElementCatalog}. The caret
 * is at the {@code |} marker.
 */
class XsltCompletionProviderTest {

    private static final String HEAD =
            "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
            + "  <xsl:template match=\"/\">\n";
    private static final String TAIL = "\n  </xsl:template>\n</xsl:stylesheet>";

    private final XsltCompletionProvider provider = new XsltCompletionProvider();

    @Test
    void variableOffersNameSelectAsWithNameRequiredFirst() {
        List<CompletionItem> items = complete(HEAD + "    <xsl:variable |/>" + TAIL);
        List<String> labels = labels(items);
        assertEquals("name", labels.getFirst());
        assertTrue(items.getFirst().isRequired());
        assertTrue(labels.containsAll(List.of("select", "as", "static", "use-when")), labels::toString);
        assertTrue(items.stream().allMatch(i -> i.getType() == CompletionItemType.ATTRIBUTE));
        assertEquals("name", items.getFirst().getInsertText());
    }

    @Test
    void attributesAlreadyInTheTagAreLeftOut() {
        List<String> labels = labels(complete(HEAD + "    <xsl:variable name=\"x\" | as=\"xs:string\"/>" + TAIL));
        assertFalse(labels.contains("name"));
        assertFalse(labels.contains("as"));
        assertEquals("select", labels.getFirst());
    }

    @Test
    void templateAndParamAndFunctionAreCovered() {
        assertTrue(labels(complete(HEAD.replace("  <xsl:template match=\"/\">\n", "") + "  <xsl:template |>"))
                .containsAll(List.of("match", "name", "mode", "priority", "as")));
        assertTrue(labels(complete(HEAD + "    <xsl:param |/>" + TAIL))
                .containsAll(List.of("name", "select", "as", "required", "tunnel")));
        assertTrue(labels(complete(HEAD + "    <xsl:function |>" + TAIL)).contains("name"));
    }

    @Test
    void multiLineStartTagIsAnAttributeContext() {
        List<String> labels = labels(complete(HEAD + "    <xsl:for-each\n        |\n    >" + TAIL));
        assertEquals("select", labels.getFirst());
    }

    @Test
    void customPrefixBoundToXsltIsRecognised() {
        String text = "<x:stylesheet version=\"3.0\" xmlns:x=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <x:template |>\n</x:stylesheet>";
        assertTrue(labels(complete(text)).containsAll(List.of("match", "name", "mode")));
    }

    @Test
    void literalResultElementsGetNoXsltAttributes() {
        assertEquals(List.of(), labels(complete(HEAD + "    <html |>" + TAIL)));
        // "xsl" bound to some other namespace is not XSLT
        assertEquals(List.of(), labels(complete("<r xmlns:xsl=\"urn:other\"><xsl:variable |/></r>")));
    }

    @Test
    void attributeValuesComeFromTheCatalog() {
        assertEquals(List.of("xml", "html", "xhtml", "text", "json", "adaptive"),
                labels(complete(HEAD + "    <xsl:output method=\"|\"/>" + TAIL)));
        assertEquals(List.of("ascending", "descending"),
                labels(complete(HEAD + "    <xsl:sort\n        order=\"|\"/>" + TAIL)));
        assertEquals(List.of(), labels(complete(HEAD + "    <xsl:variable select=\"|\"/>" + TAIL)));
    }

    @Test
    void elementInsertTextIsTheQualifiedNameWithoutAngleBracket() {
        List<CompletionItem> items = complete(HEAD + "    <|" + TAIL);
        assertTrue(labels(items).containsAll(List.of("xsl:variable", "xsl:value-of", "xsl:for-each")));
        assertFalse(labels(items).contains("xsl:template"), "declarations are not offered inside a template");
        items.forEach(i -> assertEquals(i.getLabel(), i.getInsertText()));
    }

    @Test
    void topLevelOffersDeclarationsAndChooseOffersBranches() {
        String top = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n  <|\n</xsl:stylesheet>";
        List<String> labels = labels(complete(top));
        assertTrue(labels.containsAll(List.of("xsl:template", "xsl:function", "xsl:output", "xsl:param")));
        assertFalse(labels.contains("xsl:if"));
        assertEquals(List.of("xsl:when", "xsl:otherwise"),
                labels(complete(HEAD + "    <xsl:choose>\n      <|\n    </xsl:choose>" + TAIL)));
    }

    @Test
    void literalResultElementsContainInstructions() {
        String text = HEAD + "    <div class=\"check-result\">\n      <!-- a <comment> -->\n      <|\n    </div>" + TAIL;
        List<String> labels = labels(complete(text));
        assertTrue(labels.containsAll(List.of("xsl:attribute", "xsl:choose", "xsl:value-of", "xsl:variable")),
                labels::toString);
        assertFalse(labels.contains("xsl:template"));

        int caret = text.indexOf('|');
        XmlContext ctx = ContextAnalyzer.analyze(text.replace("|", ""), caret);
        assertFalse(XsltCompletionProvider.insertsIntoXsltElement(ctx));
        String inTemplate = HEAD + "    <|" + TAIL;
        assertTrue(XsltCompletionProvider.insertsIntoXsltElement(
                ContextAnalyzer.analyze(inTemplate.replace("|", ""), inTemplate.indexOf('|'))));
    }

    @Test
    void partlyTypedNameIsNotTakenAsTheParent() {
        // Ctrl+Space after "<xsl:w" inside xsl:choose: the parent is still xsl:choose
        assertEquals(List.of("xsl:when", "xsl:otherwise"),
                labels(complete(HEAD + "    <xsl:choose>\n      <xsl:w|\n    </xsl:choose>" + TAIL)));
    }

    @Test
    void onlyActiveInXsltMode() {
        XmlContext ctx = ContextAnalyzer.analyze("<a |/>", 3);
        assertEquals(ContextType.ATTRIBUTE, ctx.getType());
        assertTrue(provider.canProvideCompletions(ctx, EditorMode.XSLT));
        assertFalse(provider.canProvideCompletions(ctx, EditorMode.XML_WITHOUT_XSD));
    }

    @Test
    void catalogCoversTheCommonElements() {
        for (String name : List.of("template", "param", "variable", "function", "apply-templates", "call-template",
                "for-each", "for-each-group", "if", "when", "value-of", "copy-of", "sequence", "output", "key",
                "iterate", "result-document", "with-param", "sort", "attribute", "element", "number")) {
            assertTrue(XsltElementCatalog.element(name).isPresent(), name);
            assertFalse(XsltElementCatalog.attributesOf(name).isEmpty(), name);
        }
        // own attributes win over the standard ones of the same name
        assertTrue(XsltElementCatalog.attribute("stylesheet", "version").orElseThrow().required());
        assertEquals(1, XsltElementCatalog.attributesOf("stylesheet").stream()
                .filter(a -> a.name().equals("version")).count());
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
