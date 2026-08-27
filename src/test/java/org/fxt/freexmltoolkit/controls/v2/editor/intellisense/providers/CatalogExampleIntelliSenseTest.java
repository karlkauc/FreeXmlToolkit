package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end regression for the shipped catalog example: element completion inside
 * {@code <billTo>} (type {@code c:Address} from the imported common schema) must insert the
 * instance prefix {@code c:} and must only offer the elements allowed at the caret position.
 */
class CatalogExampleIntelliSenseTest {

    private static final Path EXAMPLE = Path.of("release/examples/catalog").toAbsolutePath();

    private SchemaLibraryServiceImpl library;
    private XsdCompletionProvider provider;
    private String xml;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        library = new SchemaLibraryServiceImpl(tmp.resolve("lib.json"), new SchemaResourceCache(tmp.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        library.addCatalog(EXAMPLE.resolve("catalog.xml"));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, library);

        MutableXmlSchemaProvider schemaProvider = new MutableXmlSchemaProvider();
        assertTrue(schemaProvider.loadSchema(EXAMPLE.resolve("schemas/invoice.xsd").toFile()));
        provider = new XsdCompletionProvider(schemaProvider);
        xml = Files.readString(EXAMPLE.resolve("invoice-namespace-only.xml"));
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
        library.awaitSave();
    }

    @Test
    void emptyBillToOffersOnlyQualifiedStreet() {
        String text = xml.replaceAll("(?s)<billTo>.*?</billTo>", "<billTo>\n        <\n    </billTo>");
        List<CompletionItem> items = completeIn(text);
        assertEquals(List.of("c:street"), labels(items));
        assertEquals("c:street", items.getFirst().getInsertText());
        assertEquals("c", items.getFirst().getPrefix());
    }

    @Test
    void afterStreetOnlyQualifiedCityIsOffered() {
        String text = xml.replaceAll("(?s)<billTo>.*?</billTo>",
                "<billTo>\n        <c:street>Hauptplatz 5</c:street>\n        <\n    </billTo>");
        assertEquals(List.of("c:city"), labels(completeIn(text)));
    }

    @Test
    void nothingFitsInFrontOfAnExistingStreet() {
        // street is the first particle of the sequence and already present after the caret
        assertEquals(List.of(), labels(completeAt(xml.indexOf("<c:street"))));
    }

    @Test
    void mainNamespaceElementsStayUnprefixedWithDefaultNamespace() {
        List<CompletionItem> items = completeAt(xml.indexOf("<line>"));
        assertEquals(List.of("line"), labels(items));
    }

    @Test
    void lineIsRepeatableButTotalEndsTheSequence() {
        List<CompletionItem> items = completeAt(xml.indexOf("<total"));
        // total already exists after the caret (maxOccurs=1), so only another line fits here
        assertEquals(List.of("line"), labels(items));
    }

    @Test
    void prefixedMainNamespaceIsHonoured() {
        String text = """
                <inv:invoice xmlns:inv="urn:example:invoice" xmlns:c="http://schemas.example.org/common/1.0">
                    <inv:number>1</inv:number>
                    <
                </inv:invoice>""";
        assertEquals(List.of("inv:issued"), labels(completeIn(text)));
    }

    /** Completes at the lone {@code <} marker line of {@code text}. */
    private List<CompletionItem> completeIn(String text) {
        int caret = text.indexOf("<\n") + 1;
        assertTrue(caret > 0, "marker missing");
        return provider.getCompletions(ContextAnalyzer.analyze(text, caret));
    }

    /** Simulates typing {@code <} at {@code offset} (text after the offset stays in place). */
    private List<CompletionItem> completeAt(int offset) {
        String text = xml.substring(0, offset) + "<" + xml.substring(offset);
        XmlContext ctx = ContextAnalyzer.analyze(text, offset + 1);
        return provider.getCompletions(ctx);
    }

    private static List<String> labels(List<CompletionItem> items) {
        return items.stream().map(CompletionItem::getLabel).toList();
    }
}
