package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeXmlStructure}: children, pages, fields, byte ranges, skipped constructs. */
@DisplayName("LargeXmlStructure")
class LargeXmlStructureTest {

    @TempDir
    Path dir;

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE orders [ <!ENTITY e "x"> <!ELEMENT orders ANY> ]>
            <!-- <fake/> -->
            <orders xmlns="urn:o" version="2">
              <order id="1" status="open"><customer>Ada &amp; Co</customer><item sku="A"/><item sku="B"/><note><![CDATA[a<b]]></note></order>
              <?pi <fake/>?>
              <order id="2"><customer>Grüße</customer><address><city>Wien</city></address></order>
              <summary/>
              <order id="3">plain text</order>
            </orders>
            """;

    private LargeXmlStructure structure(String xml) throws IOException {
        Path file = dir.resolve("s.xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return new LargeXmlStructure(LargeFileIndex.build(file, null, null));
    }

    @Test
    @DisplayName("root, children with positions, XPaths, fields, texts and exact byte ranges")
    void children() throws Exception {
        LargeXmlStructure structure = structure(XML);
        Node root = structure.root();
        assertEquals("orders", root.name());
        assertEquals(Map.of("@version", "2"), root.fields(), "xmlns is no field");
        Page page = structure.children(root, root.start(), null, 100, null, null);
        assertEquals(-1, page.next());
        List<Node> children = page.children();
        assertEquals(List.of("order", "order", "summary", "order"), children.stream().map(Node::name).toList());
        Node first = children.get(0);
        assertEquals("/orders[1]/order[1]", first.xpath());
        assertEquals("/orders[1]/order[3]", children.get(3).xpath());
        assertEquals(1, children.get(2).position());
        assertTrue(first.hasChildren());
        assertEquals("1", first.fields().get("@id"));
        assertEquals("open", first.fields().get("@status"));
        assertEquals("Ada & Co", first.fields().get("customer"));
        assertEquals("a<b", first.fields().get("note"), "CDATA is text");
        assertFalse(first.fields().containsKey("item"), "an empty element has no text field");
        assertEquals("Grüße", children.get(1).fields().get("customer"));
        assertFalse(children.get(1).fields().containsKey("address"), "only elements without children are fields");
        assertFalse(children.get(2).hasChildren());
        assertEquals("plain text", children.get(3).text());

        byte[] bytes = XML.getBytes(StandardCharsets.UTF_8);
        String firstText = new String(bytes, (int) first.start(), (int) (first.end() - first.start()), StandardCharsets.UTF_8);
        assertTrue(firstText.startsWith("<order id=\"1\"") && firstText.endsWith("</order>"), firstText);
        assertEquals("<summary/>", structure.text(children.get(2), 100));

        Page grandchildren = structure.children(first, first.start(), null, 100, null, null);
        assertEquals(List.of("customer", "item", "item", "note"), grandchildren.children().stream().map(Node::name).toList());
        assertEquals("/orders[1]/order[1]/item[2]", grandchildren.children().get(2).xpath());
        assertEquals("B", grandchildren.children().get(2).fields().get("@sku"));
    }

    @Test
    @DisplayName("pages continue where the previous one stopped, positions included")
    void pages() throws Exception {
        StringBuilder xml = new StringBuilder("<r>\n");
        for (int i = 1; i <= 2500; i++) {
            xml.append("  <row n=\"").append(i).append("\"><v>").append(i * 2).append("</v></row>\n");
            if (i % 1000 == 0) {
                xml.append("  <mark/>\n");
            }
        }
        xml.append("</r>\n");
        LargeXmlStructure structure = structure(xml.toString());
        Node root = structure.root();
        Page one = structure.children(root, root.start(), null, 1000, null, null);
        assertEquals(1000, one.children().size());
        assertTrue(one.next() > 0);
        Page two = structure.children(root, one.next(), one.positions(), 1000, null, null);
        assertEquals("mark", two.children().get(0).name(), "the page continues at the next child");
        assertEquals("/r[1]/row[1001]", two.children().get(1).xpath());
        assertEquals("2002", two.children().get(1).fields().get("v"));
        Page three = structure.children(root, two.next(), two.positions(), 1000, null, null);
        assertEquals(-1, three.next());
        assertEquals("/r[1]/row[2500]", three.children().get(three.children().size() - 1).xpath());
        assertEquals(2502, one.children().size() + two.children().size() + three.children().size(),
                "2500 rows and 2 marks");
    }

    @Test
    @DisplayName("not XML; an element too large to open; cancellation")
    void errors() throws Exception {
        assertThrows(IOException.class, () -> structure("just text").root());
        LargeXmlStructure structure = structure("<a><b>x</b></a>");
        Node root = structure.root();
        assertThrows(IOException.class, () -> structure.text(root, 3));
        assertThrows(java.util.concurrent.CancellationException.class, () -> {
            StringBuilder big = new StringBuilder("<r>");
            big.append("<x/>".repeat(400_000));
            big.append("</r>");
            LargeXmlStructure s = structure(big.toString());
            s.children(s.root(), 0, null, 1_000_000, () -> true, null);
        });
    }

    @Test
    @DisplayName("character references are decoded; a value cut inside a UTF-8 character stays readable")
    void characterReferencesAndCuts() throws Exception {
        LargeXmlStructure structure = structure("<r><e a=\"caf&#233; &#x1F600; &#99999999999;\">x&#xE9;&amp;</e>"
                + "<l>" + "ä".repeat(LargeXmlStructure.MAX_VALUE * 3) + "</l></r>");
        Node root = structure.root();
        List<Node> children = structure.children(root, root.start(), null, 10, null, null).children();
        assertEquals("café 😀 &#99999999999;", children.get(0).fields().get("@a"));
        assertEquals("xé&", children.get(0).text());
        String cut = children.get(1).text();
        assertFalse(cut.contains("�"), "no replacement character");
        assertTrue(cut.endsWith("…"));
        byte[] bytes = "aä".getBytes(StandardCharsets.UTF_8);
        assertEquals(1, LargeXmlStructure.completeUtf8(bytes, 2), "a lead byte without its continuation is dropped");
        assertEquals(3, LargeXmlStructure.completeUtf8(bytes, 3));
    }

    @Test
    @DisplayName("content start, the root's real end, indexes, cursors and page index")
    void cursorsAndPageIndex() throws Exception {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?>\n<r a=\"1\">\n");
        for (int i = 1; i <= 25; i++) {
            xml.append(i % 5 == 0 ? "  <m/>\n" : "  <row n=\"" + i + "\"/>\n");
        }
        xml.append("</r>\n<!-- trailing -->\n");
        LargeXmlStructure structure = structure(xml.toString());
        Node root = structure.root();
        String text = xml.toString();
        assertEquals(text.indexOf("</r>") + 4, root.end(), "the root's end is read from the end of the file");
        assertEquals(text.indexOf("<r a=\"1\">") + 9, root.contentStart());
        LargeXmlStructure.PageIndex pages = structure.pageIndex(root, 10, null, null);
        assertEquals(25, pages.total());
        assertEquals(3, pages.pages().size());
        LargeXmlStructure.Cursor third = pages.pageOf(21);
        assertEquals(20, third.index());
        Page page = structure.children(root, third, 10, null, null);
        assertEquals(5, page.children().size());
        assertEquals(20, page.children().get(0).index());
        assertEquals("/r[1]/row[17]", page.children().get(0).xpath(), "positions continue from the cursor");
        assertEquals("/r[1]/m[5]", page.children().get(4).xpath());
        Node self = page.children().get(4);
        assertEquals(self.end(), self.contentStart(), "an empty element has no content");
    }

    @Test
    @DisplayName("pathAt finds the ancestors of an offset with the page that lists each one")
    void pathAt() throws Exception {
        StringBuilder xml = new StringBuilder("<r>");
        for (int i = 1; i <= 30; i++) {
            xml.append("<o id=\"").append(i).append("\"><c>v").append(i).append("</c></o>");
        }
        xml.append("</r>");
        LargeXmlStructure structure = structure(xml.toString());
        long offset = xml.indexOf("v23");
        List<LargeXmlStructure.Step> steps = structure.pathAt(offset, 10, null, null);
        assertEquals(List.of("r", "o", "c"), steps.stream().map(LargeXmlStructure.Step::name).toList());
        assertEquals(23, steps.get(1).position());
        assertEquals(22, steps.get(1).index());
        assertEquals(20, steps.get(1).page().index(), "the page of 10 that lists o[23]");
        Node root = structure.root();
        Node o23 = structure.childAt(root, steps.get(1));
        assertEquals("/r[1]/o[23]", o23.xpath());
        assertEquals("v23", o23.fields().get("c"));
        assertTrue(structure.pathAt(0, 10, null, null).size() == 1, "the root's start tag is in the root");
        assertTrue(structure.pathAt(xml.length() + 5, 10, null, null).isEmpty(), "after the root");
    }

    @Test
    @DisplayName("resolve: names, positions, * and errors naming the step")
    void resolve() throws Exception {
        LargeXmlStructure structure = structure("<p:r xmlns:p=\"urn:p\"><a/><b/><a><x/><x/></a></p:r>");
        List<LargeXmlStructure.Step> steps = structure.resolve("/p:r/a[2]/x[2]", 1000, null, null);
        assertEquals(List.of("p:r", "a", "x"), steps.stream().map(LargeXmlStructure.Step::name).toList());
        assertEquals(2, steps.get(1).index());
        assertEquals(1, steps.get(2).index());
        assertEquals("b", structure.resolve("*/*[2]", 1000, null, null).get(1).name());
        assertEquals("a", structure.resolve("p:r/a", 1000, null, null).get(1).name());
        IOException missing = assertThrows(IOException.class, () -> structure.resolve("/p:r/a[3]", 1000, null, null));
        assertTrue(missing.getMessage().contains("a[3]"), missing.getMessage());
        assertThrows(IOException.class, () -> structure.resolve("/other", 1000, null, null));
        assertThrows(IOException.class, () -> structure.resolve("", 1000, null, null));
    }

    @Test
    @DisplayName("firstOffsetOf finds the first element with a name path, also under a later parent")
    void firstOffsetOf() throws Exception {
        String xml = "<r><a><x/></a><b/><a><c/><c><d>1</d></c></a></r>";
        LargeXmlStructure structure = structure(xml);
        assertEquals(xml.indexOf("<d>"), structure.firstOffsetOf("/r/a/c/d", null, null));
        assertEquals(xml.indexOf("<c/>"), structure.firstOffsetOf("r/a/c", null, null));
        assertEquals(-1, structure.firstOffsetOf("/r/b/x", null, null));
        assertEquals(0, structure.firstOffsetOf("/r", null, null));
    }
}
