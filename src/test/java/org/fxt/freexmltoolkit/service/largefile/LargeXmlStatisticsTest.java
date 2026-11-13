package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.service.largefile.LargeXmlStatistics.PathStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeXmlStatistics}. */
@DisplayName("LargeXmlStatistics")
class LargeXmlStatisticsTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("counts, depth, per-parent occurrences, attributes, text and samples per path")
    void statistics() throws Exception {
        Path file = dir.resolve("s.xml");
        Files.writeString(file, """
                <?xml version="1.0"?>
                <!-- <ignored/> -->
                <orders xmlns="urn:o" v="1">
                  <order id="1" st="a"><item sku="A"/><item sku="B"/><note>first <![CDATA[note]]></note></order>
                  <order id="2"><item sku="C" qty="2"/></order>
                  <order id="3"/>
                </orders>
                """, StandardCharsets.UTF_8);
        var structure = new LargeXmlStructure(LargeFileIndex.build(file, null, null));
        var result = LargeXmlStatistics.scan(structure, null, null);
        assertEquals(8, result.elements());
        assertEquals(9, result.attributes(), "v, 3 ids, st, 3 skus, qty; xmlns is no attribute");
        assertEquals(3, result.maxDepth());
        assertEquals(0, result.droppedPaths());
        Map<String, PathStats> paths = result.paths().stream().collect(Collectors.toMap(PathStats::path, Function.identity()));
        assertEquals(List.of("/orders", "/orders/order", "/orders/order/item", "/orders/order/note"),
                result.paths().stream().map(PathStats::path).toList());
        PathStats item = paths.get("/orders/order/item");
        assertEquals(3, item.count());
        assertEquals(0, item.minPerParent(), "order 3 has no item");
        assertEquals(2, item.maxPerParent());
        assertEquals(List.of("sku", "qty"), item.attributes());
        assertFalse(item.hasText());
        PathStats order = paths.get("/orders/order");
        assertEquals(3, order.minPerParent());
        assertEquals(List.of("id", "st"), order.attributes());
        PathStats note = paths.get("/orders/order/note");
        assertTrue(note.hasText());
        assertEquals("first note", note.sample());
        assertEquals(3, note.depth());
    }
}
