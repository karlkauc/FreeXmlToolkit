package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Breakpoints set on an editor document must match every spelling Saxon uses for the same file. */
class SourceLocationsTest {

    @Test
    void emptyAndNullBecomeEmpty() {
        assertEquals("", SourceLocations.normalize(null));
        assertEquals("", SourceLocations.normalize(""));
        assertEquals("", SourceLocations.normalize("   "));
    }

    @Test
    void fileUriSpellingsNormalizeToTheAbsolutePath(@TempDir Path tmp) {
        File file = tmp.resolve("lib").resolve("a.xsl").toFile();
        String expected = file.toPath().toAbsolutePath().normalize().toString();

        assertEquals(expected, SourceLocations.normalize(file.getAbsolutePath()), "plain path");
        assertEquals(expected, SourceLocations.normalize(file.toURI().toString()), "File.toURI (file:/...)");
        assertEquals(expected, SourceLocations.normalize(file.toURI().toString().replaceFirst("^file:/", "file:///")),
                "triple-slash form");
        assertEquals(expected, SourceLocations.normalize(tmp.resolve("lib").resolve("..").resolve("lib")
                .resolve("a.xsl").toString()), "dot segments collapse");
    }

    @Test
    void urlEncodedFileUriDecodes(@TempDir Path tmp) {
        File file = tmp.resolve("my sheet.xsl").toFile();
        String uri = file.toURI().toString(); // contains %20
        assertTrue(uri.contains("%20"), uri);
        assertEquals(file.toPath().toAbsolutePath().normalize().toString(), SourceLocations.normalize(uri));
    }

    @Test
    void nonFileSchemesAreKeptAsIs() {
        assertEquals("jar:file:/x.jar!/a.xsl", SourceLocations.normalize("jar:file:/x.jar!/a.xsl"));
        assertEquals("http://example.org/a.xsl", SourceLocations.normalize("http://example.org/a.xsl"));
    }

    @Test
    void sameFileAndFileName(@TempDir Path tmp) {
        File file = tmp.resolve("a.xsl").toFile();
        assertTrue(SourceLocations.sameFile(file.getAbsolutePath(), file.toURI().toString()));
        assertFalse(SourceLocations.sameFile(file.getAbsolutePath(), tmp.resolve("b.xsl").toString()));
        assertFalse(SourceLocations.sameFile("", ""), "two unknown locations are never the same file");
        assertEquals("a.xsl", SourceLocations.fileName(file.toURI().toString()));
        assertEquals("", SourceLocations.fileName(null));
    }

    @Test
    void breakpointsOnlyMatchTheirOwnModule(@TempDir Path tmp) {
        File main = tmp.resolve("a.xsl").toFile();
        File include = tmp.resolve("lib").resolve("a.xsl").toFile(); // same name, other directory
        Breakpoint bp = new Breakpoint(main.getAbsolutePath(), 7, true);

        assertTrue(bp.matches(main.toURI().toString(), 7), "Saxon reports the main module as a file: URI");
        assertFalse(bp.matches(include.toURI().toString(), 7), "an include with the same file name must not trigger it");
        assertTrue(bp.matches("", 7), "no systemId (in-memory stylesheet) falls back to the line");
        assertFalse(bp.matches(main.toURI().toString(), 8));
    }

    @Test
    void sessionNormalizesPathsOnBothSides(@TempDir Path tmp) {
        File main = tmp.resolve("a.xsl").toFile();
        DebugSession session = new DebugSession();
        assertTrue(session.toggleBreakpoint(main.getAbsolutePath(), 3));
        assertTrue(session.hasBreakpoint(main.toURI().toString(), 3), "URI spelling finds the path breakpoint");
        session.removeBreakpoint(main.toURI().toString(), 3);
        assertFalse(session.hasBreakpoint(main.getAbsolutePath(), 3));
    }
}
