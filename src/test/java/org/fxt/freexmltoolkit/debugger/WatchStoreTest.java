package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Watch expressions survive the round trip through the stored preference format. */
class WatchStoreTest {

    @Test
    void formatAndParseRoundTripIncludingSeparators() {
        List<String> watches = List.of("count(item)", "$total", "concat('a\tb', '\\\\')", "string-join(\n  //x, ',')");
        assertEquals(watches, WatchStore.parse(WatchStore.format(watches)));
    }

    @Test
    void blanksAreDroppedAndEmptyMeansNone() {
        assertEquals(List.of("$a"), WatchStore.parse(WatchStore.format(List.of(" ", "$a ", ""))));
        assertEquals("", WatchStore.format(List.of()));
        assertEquals(List.of(), WatchStore.parse(null));
    }

    @Test
    void keysAreSeparateFromBreakpointKeys() {
        String watchKey = WatchStore.key("/tmp/a.xsl");
        assertTrue(watchKey.startsWith("debug.watches."));
        assertEquals(watchKey.substring("debug.watches.".length()),
                BreakpointStore.key("/tmp/a.xsl").substring("debug.breakpoints.".length()),
                "same path hash, different prefix");
    }

    @Test
    void loadAndSaveWithoutPropertiesServiceAreNoErrors() {
        assertDoesNotThrow(() -> WatchStore.save("/tmp/none.xsl", List.of("$x")));
        assertNotNull(WatchStore.load("/tmp/none.xsl"));
    }
}
