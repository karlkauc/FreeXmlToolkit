package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Static include/import scan used to pre-load module breakpoints at session start. */
class StylesheetModulesTest {

    private static final String NS = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"";

    @Test
    void resolvesNestedIncludesAndImportsRelativeToEachFile(@TempDir Path tmp) throws Exception {
        Path lib = Files.createDirectories(tmp.resolve("lib"));
        Path main = tmp.resolve("main.xsl");
        Path a = lib.resolve("a.xsl");
        Path b = lib.resolve("b.xsl");
        Files.writeString(main, "<xsl:stylesheet " + NS + ">\n  <xsl:include href=\"lib/a.xsl\"/>\n</xsl:stylesheet>");
        Files.writeString(a, "<xsl:stylesheet " + NS + ">\n  <xsl:import\n     href='b.xsl' />\n</xsl:stylesheet>");
        Files.writeString(b, "<xsl:stylesheet " + NS + "/>");

        Set<String> modules = StylesheetModules.resolve(main);

        assertEquals(Set.of(SourceLocations.normalize(a.toString()), SourceLocations.normalize(b.toString())), modules);
    }

    @Test
    void ignoresCyclesMissingFilesRemoteHrefsAndTheMainFileItself(@TempDir Path tmp) throws Exception {
        Path main = tmp.resolve("main.xsl");
        Path a = tmp.resolve("a.xsl");
        Files.writeString(main, "<xsl:stylesheet " + NS + ">"
                + "<xsl:include href=\"a.xsl\"/>"
                + "<xsl:include href=\"missing.xsl\"/>"
                + "<xsl:import href=\"http://example.com/remote.xsl\"/>"
                + "<xsl:include href=\"main.xsl\"/>"
                + "</xsl:stylesheet>");
        Files.writeString(a, "<xsl:stylesheet " + NS + "><xsl:include href=\"main.xsl\"/></xsl:stylesheet>");

        Set<String> modules = StylesheetModules.resolve(main);

        assertEquals(Set.of(SourceLocations.normalize(a.toString())), modules);
    }

    @Test
    void acceptsOtherPrefixesAndFileUris(@TempDir Path tmp) throws Exception {
        Path main = tmp.resolve("main.xsl");
        Path a = tmp.resolve("a.xsl");
        Path b = tmp.resolve("b.xsl");
        Files.writeString(a, "<x/>");
        Files.writeString(b, "<x/>");
        Files.writeString(main, "<t:stylesheet xmlns:t=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                + "<t:include href=\"a.xsl#frag\"/>"
                + "<t:import href=\"" + b.toUri() + "\"/>"
                + "</t:stylesheet>");

        Set<String> modules = StylesheetModules.resolve(main);

        assertEquals(Set.of(SourceLocations.normalize(a.toString()), SourceLocations.normalize(b.toString())), modules);
    }

    @Test
    void stopsAtTheDepthCap(@TempDir Path tmp) throws Exception {
        int chain = StylesheetModules.MAX_DEPTH + 3;
        for (int i = chain; i >= 0; i--) {
            String next = i < chain ? "<xsl:include href=\"m" + (i + 1) + ".xsl\"/>" : "";
            Files.writeString(tmp.resolve("m" + i + ".xsl"), "<xsl:stylesheet " + NS + ">" + next + "</xsl:stylesheet>");
        }

        Set<String> modules = StylesheetModules.resolve(tmp.resolve("m0.xsl"));

        assertTrue(modules.contains(SourceLocations.normalize(tmp.resolve("m1.xsl").toString())));
        assertEquals(StylesheetModules.MAX_DEPTH + 1, modules.size(), "one module per depth level up to the cap");
    }

    @Test
    void unreadableOrNullInputYieldsNothing(@TempDir Path tmp) {
        assertTrue(StylesheetModules.resolve(null).isEmpty());
        assertTrue(StylesheetModules.resolve(tmp.resolve("nope.xsl")).isEmpty());
    }
}
