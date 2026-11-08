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
}
