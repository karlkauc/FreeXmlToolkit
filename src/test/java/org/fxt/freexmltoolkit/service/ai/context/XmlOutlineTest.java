package org.fxt.freexmltoolkit.service.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlOutlineTest {

    private static final String XML = """
            <?xml version="1.0"?>
            <f:Funds xmlns:f="urn:funds" xmlns="urn:default"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="urn:funds funds.xsd">
              <f:Fund id="1"><Name>  Alpha MARKER-VALUE fund with a very long name that goes on and on and on beyond eighty characters  </Name><Nav>10.5</Nav></f:Fund>
              <f:Fund id="2" currency="EUR"><Name>Beta</Name><Nav>11</Nav><Nav>12</Nav></f:Fund>
              <!-- comment -->
            </f:Funds>
            """;

    private static XmlOutline.Outline outline(String xml, int maxPaths, boolean samples) throws Exception {
        return XmlOutline.of(new StringReader(xml), xml.length(), maxPaths, samples);
    }

    @Test
    void pathsCountsAttributesAndSamples() throws Exception {
        XmlOutline.Outline o = outline(XML, 200, true);
        assertEquals("f:Funds", o.root());
        assertEquals("urn:funds", o.namespaces().get("f"));
        assertEquals("urn:default", o.namespaces().get(""));
        assertEquals("urn:funds funds.xsd", o.declaredSchema());
        assertEquals(List.of("/f:Funds", "/f:Funds/f:Fund", "/f:Funds/f:Fund/Name", "/f:Funds/f:Fund/Nav"),
                o.paths().stream().map(XmlOutline.PathInfo::path).toList());
        XmlOutline.PathInfo fund = o.paths().get(1);
        assertEquals(2, fund.count());
        assertEquals(List.of("id", "currency"), fund.attributes());
        assertEquals(3, o.paths().get(3).count());
        String sample = o.paths().get(2).sample();
        assertTrue(sample.startsWith("Alpha MARKER-VALUE"), sample);
        assertTrue(sample.length() <= 81, sample);
        assertEquals(8, o.elementCount());
        assertEquals(3, o.maxDepth());
        assertFalse(o.truncated());
    }

    @Test
    void maxPathsTruncatesButKeepsCounting() throws Exception {
        XmlOutline.Outline o = outline(XML, 2, true);
        assertEquals(2, o.paths().size());
        assertTrue(o.truncated());
        assertEquals(8, o.elementCount());
    }

    @Test
    void withoutSamplesNoDocumentValueAppears() throws Exception {
        String json = outline(XML, 200, false).toJson().toString();
        assertFalse(json.contains("MARKER-VALUE"), json);
        assertFalse(json.contains("Beta"), json);
        assertFalse(json.contains("EUR"), json);
        assertFalse(json.contains("10.5"), json);
    }

    @Test
    void aLargeFileIsStreamed(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("big.xml");
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<rows>");
            for (int i = 0; i < 400_000; i++) {
                out.write("<row n=\"" + i + "\"><a>value " + i + "</a><b>x</b></row>\n");
            }
            out.write("</rows>");
        }
        assertTrue(Files.size(file) > 15_000_000);
        XmlOutline.Outline o = XmlOutline.of(file, 200, true);
        assertEquals(400_000, o.paths().get(1).count());
        assertEquals(1 + 3 * 400_000, o.elementCount());
        assertEquals(Files.size(file), o.sizeBytes());
    }
}
