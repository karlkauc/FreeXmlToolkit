package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The per-stylesheet debug configuration and its stored form. */
class DebugLaunchConfigTest {

    @Test
    void roundTripKeepsEverythingInOrder() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("lang", "de");
        params.put("title", "Order\t#7\nnew line");
        DebugLaunchConfig config = new DebugLaunchConfig("/tmp/in put.xml", params, "HTML", true);
        DebugLaunchConfig parsed = DebugLaunchConfig.parse(config.format()).orElseThrow();
        assertEquals(config, parsed);
        assertEquals(java.util.List.of("lang", "title"), java.util.List.copyOf(parsed.parameters().keySet()));
        assertTrue(DebugLaunchConfig.parse("").isEmpty());
        assertEquals(DebugLaunchConfig.defaults(), DebugLaunchConfig.parse("unknown\tx").orElseThrow(),
                "unknown keys are ignored");
    }

    @Test
    void parametersFromNameValueLines() {
        assertEquals(Map.of("a", "1", "b", "x = y"), DebugLaunchConfig.parseParameters("a=1\n\n b = x = y \nnoequals\n=v"));
        Map<String, String> two = new LinkedHashMap<>();
        two.put("a", "1");
        two.put("b", "2");
        assertEquals("a=1\nb=2", new DebugLaunchConfig("", two, "", false).parametersText());
    }
}
