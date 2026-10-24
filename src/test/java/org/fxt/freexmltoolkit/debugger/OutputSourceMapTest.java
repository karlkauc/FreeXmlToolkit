package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Output offsets map to the instruction that wrote them. */
class OutputSourceMapTest {

    @Test
    void eachEntryCoversTheTextUpToTheNext() {
        OutputSourceMap map = new OutputSourceMap();
        map.add(0, "a.xsl", 3);
        map.add(5, "a.xsl", 3); // the same instruction goes on: merged
        map.add(10, "b.xsl", 7);
        map.add(20, "a.xsl", 0); // no line: ignored
        assertEquals(2, map.size());
        assertEquals(Optional.of(new OutputSourceMap.Source("a.xsl", 3)), map.locate(0));
        assertEquals(Optional.of(new OutputSourceMap.Source("a.xsl", 3)), map.locate(9));
        assertEquals(Optional.of(new OutputSourceMap.Source("b.xsl", 7)), map.locate(10));
        assertEquals(Optional.of(new OutputSourceMap.Source("b.xsl", 7)), map.locate(500), "the rest is the last writer's");
        assertEquals(Optional.empty(), map.locate(-1));
        map.clear();
        assertEquals(Optional.empty(), map.locate(0));
    }
}
