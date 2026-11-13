package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeXmlWindow}: the window document, inherited namespaces, write-back and its refusals. */
@DisplayName("LargeXmlWindow")
class LargeXmlWindowTest {

    @TempDir
    Path dir;

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <db xmlns="urn:db" xmlns:x="urn:x">
              <orders kind="all">
                <order id="1"><x:amount>10</x:amount></order>
                <order id="2"><x:amount>20</x:amount></order>
                <order id="3"><x:amount>30</x:amount></order>
              </orders>
            </db>
            """;

    private LargeXmlStructure structure(String xml, Charset charset) throws IOException {
        Path file = dir.resolve("w.xml");
        Files.writeString(file, xml, charset);
        return new LargeXmlStructure(LargeFileIndex.build(file, null, null));
    }

    @Test
    @DisplayName("a run of children parses on its own with the ancestors' namespaces and writes back into its range")
    void roundTrip() throws Exception {
        LargeXmlStructure structure = structure(XML, StandardCharsets.UTF_8);
        Node db = structure.root();
        Node orders = structure.children(db, db.start(), null, 10, null, null).children().getFirst();
        List<Node> children = structure.children(orders, orders.start(), null, 10, null, null).children();
        LargeXmlWindow window = LargeXmlWindow.ofChildren(structure, orders, List.of(db), children.subList(1, 3));
        assertEquals(1, window.firstIndex());
        assertEquals(2, window.count());
        String text = window.text();
        assertTrue(text.startsWith("<orders xmlns=\"urn:db\" xmlns:x=\"urn:x\" kind=\"all\">"), text);
        assertTrue(text.endsWith("</order></orders>"), text);

        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(text);
        String edited = context.serializeToString().replace(">20<", ">25<");
        LargeFilePatcher.Patch patch = window.patchFor(edited);
        assertEquals(children.get(1).start(), patch.start());
        assertEquals(children.get(2).end(), patch.end());
        Path file = structure.index().path();
        structure.index().close();
        LargeFilePatcher.apply(file, window.fileSize(), window.fileModified(), List.of(patch), false, null, null);
        String written = Files.readString(file);
        assertTrue(written.contains(">25<"), written);
        assertTrue(written.startsWith(XML.substring(0, (int) children.get(1).start())), "bytes before the window are unchanged");
        String after = XML.substring((int) children.get(2).end());
        assertTrue(written.endsWith(after), "bytes after the window are unchanged");
        LargeXmlStructure again = new LargeXmlStructure(LargeFileIndex.build(file, null, null));
        Node root = again.root();
        assertEquals("db", root.name());
        Node ordersAgain = again.children(root, root.start(), null, 10, null, null).children().getFirst();
        assertEquals(3, again.children(ordersAgain, ordersAgain.start(), null, 10, null, null).children().size());
    }

    @Test
    @DisplayName("deleting every child writes an empty run; the parent's attributes cannot be changed")
    void refusals() throws Exception {
        LargeXmlStructure structure = structure(XML, StandardCharsets.UTF_8);
        Node db = structure.root();
        Node orders = structure.children(db, db.start(), null, 10, null, null).children().getFirst();
        List<Node> children = structure.children(orders, orders.start(), null, 10, null, null).children();
        LargeXmlWindow window = LargeXmlWindow.ofChildren(structure, orders, List.of(db), children);
        assertArrayEquals(new byte[0], window.patch("<orders xmlns=\"urn:db\" xmlns:x=\"urn:x\" kind=\"all\"/>"));
        IOException changed = assertThrows(IOException.class,
                () -> window.patch("<orders xmlns=\"urn:db\" xmlns:x=\"urn:x\" kind=\"some\"></orders>"));
        assertTrue(changed.getMessage().contains("attributes"), changed.getMessage());
        assertThrows(IOException.class, () -> window.patch("<other/>"));
        assertEquals("<order/>", new String(window.patch("<?xml version=\"1.0\"?>\n<!-- c -->\n<orders kind='all' "
                + "xmlns:x='urn:x' xmlns='urn:db'>\n   <order/>\n</orders>"), StandardCharsets.UTF_8),
                "prolog, attribute order and quotes do not matter; the content is trimmed");
    }

    @Test
    @DisplayName("the root window has no wrapper; ISO-8859-1 gets character references for other characters")
    void rootAndCharset() throws Exception {
        LargeXmlStructure structure = structure("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r a=\"ä\">x</r>",
                StandardCharsets.ISO_8859_1);
        Node root = structure.root();
        LargeXmlWindow window = LargeXmlWindow.ofRoot(structure, root);
        assertEquals("<r a=\"ä\">x</r>", window.text());
        byte[] bytes = window.patch("<r a=\"ä€\">x€</r>");
        assertEquals("<r a=\"ä&#x20AC;\">x&#x20AC;</r>", new String(bytes, StandardCharsets.ISO_8859_1));
        assertThrows(IOException.class, () -> window.patch("<r€/>"), "a name cannot hold a character reference");
    }

    @Test
    @DisplayName("fitting keeps the longest prefix within the byte limit, at least one child")
    void fitting() {
        List<Node> nodes = List.of(node(0, 10), node(10, 20), node(20, 35));
        assertEquals(2, LargeXmlWindow.fitting(nodes, 20).size());
        assertEquals(1, LargeXmlWindow.fitting(nodes, 5).size());
        assertEquals(3, LargeXmlWindow.fitting(nodes, 100).size());
    }

    private static Node node(long start, long end) {
        return new Node("n", start, start + 3, end, 1, 0, "/n", false, "", java.util.Map.of());
    }
}
