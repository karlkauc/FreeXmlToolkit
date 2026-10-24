package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Round-trips the persisted breakpoint format and keys stylesheets apart. */
class BreakpointStoreTest {

    @Test
    void formatAndParseRoundTrip() {
        List<Breakpoint> breakpoints = List.of(
                new Breakpoint("/tmp/a.xsl", 4, true),
                new Breakpoint("/tmp/lib/b.xsl", 12, false),
                new Breakpoint("", 7, true));
        String stored = BreakpointStore.format(breakpoints);
        Set<Breakpoint> parsed = BreakpointStore.parse(stored);
        assertEquals(Set.copyOf(breakpoints), parsed);
    }

    @Test
    void emptyAndMalformedInputYieldNoBreakpoints() {
        assertTrue(BreakpointStore.parse(null).isEmpty());
        assertTrue(BreakpointStore.parse("").isEmpty());
        assertEquals("", BreakpointStore.format(List.of()));
        assertTrue(BreakpointStore.parse("garbage").isEmpty(), "an entry without a line is skipped");
        assertEquals(1, BreakpointStore.parse("garbage\n/tmp/a.xsl\t3\ttrue").size(), "valid entries survive");
    }

    @Test
    void keysDependOnTheNormalizedStylesheetPath() {
        String a = BreakpointStore.key("/tmp/a.xsl");
        assertEquals(a, BreakpointStore.key("file:/tmp/a.xsl"), "URI and path spellings share a key");
        assertNotEquals(a, BreakpointStore.key("/tmp/b.xsl"));
        assertTrue(a.startsWith(BreakpointStore.KEY_PREFIX));
    }

    @Test
    void groupByFileSplitsAFilesBreakpointsUnderItsOwnKey() {
        List<Breakpoint> all = List.of(
                new Breakpoint("/tmp/main.xsl", 4, true),
                new Breakpoint("/tmp/lib/b.xsl", 12, false),
                new Breakpoint("/tmp/main.xsl", 9, true),
                new Breakpoint("", 7, true));
        var grouped = BreakpointStore.groupByFile(all, List.of("file:/tmp/main.xsl", "/tmp/lib/c.xsl"));

        assertEquals(Set.of("/tmp/main.xsl", "/tmp/lib/b.xsl", "/tmp/lib/c.xsl"), grouped.keySet(),
                "every file with a breakpoint plus every known file; the in-memory entry is dropped");
        assertEquals(Set.of(all.get(0), all.get(2)), grouped.get("/tmp/main.xsl"));
        assertEquals(Set.of(all.get(1)), grouped.get("/tmp/lib/b.xsl"));
        assertTrue(grouped.get("/tmp/lib/c.xsl").isEmpty(), "a known file without breakpoints is cleared");
    }

    @Test
    void saveByFileWithoutPropertiesServiceIsNotAnError() {
        assertDoesNotThrow(() -> BreakpointStore.saveByFile(
                List.of(new Breakpoint("/tmp/never-debugged.xsl", 2, true)), List.of("/tmp/never-debugged.xsl")));
    }

    @Test
    void loadWithoutPropertiesServiceIsEmptyNotAnError() {
        // The ServiceRegistry may or may not be initialised in a unit-test JVM; either way
        // loading must not throw and saving must be a no-op or succeed silently.
        assertDoesNotThrow(() -> BreakpointStore.save("/tmp/never-debugged.xsl", List.of()));
        assertNotNull(BreakpointStore.load("/tmp/never-debugged.xsl"));
    }

    @Test
    void conditionsRoundTripIncludingSeparators() {
        Breakpoint plain = new Breakpoint("/tmp/a.xsl", 3, true);
        Breakpoint conditional = new Breakpoint("/tmp/a.xsl", 7, true, "@id = 'a\tb' and\n contains(., '\\')");
        Set<Breakpoint> parsed = BreakpointStore.parse(BreakpointStore.format(List.of(plain, conditional)));
        assertEquals(Set.of(plain, conditional), parsed);
        assertFalse(BreakpointStore.format(List.of(plain)).contains("\t\t"), "no empty condition field");
    }

    @Test
    void entriesWrittenBeforeConditionsStillLoad() {
        Set<Breakpoint> parsed = BreakpointStore.parse("/tmp/a.xsl\t3\ttrue\n/tmp/a.xsl\t4");
        assertEquals(2, parsed.size());
        assertTrue(parsed.stream().noneMatch(Breakpoint::hasCondition));
    }

    @Test
    void hitRulesRoundTripWithAndWithoutCondition() {
        Breakpoint onlyHit = new Breakpoint("/tmp/a.xsl", 3, true, "", new HitCondition(HitCondition.Mode.AT_LEAST, 4));
        Breakpoint both = new Breakpoint("/tmp/a.xsl", 5, true, "@n = 1", new HitCondition(HitCondition.Mode.MULTIPLE_OF, 10));
        Breakpoint plain = new Breakpoint("/tmp/a.xsl", 7, false);
        assertEquals(Set.of(onlyHit, both, plain),
                BreakpointStore.parse(BreakpointStore.format(List.of(onlyHit, both, plain))));
    }

    @Test
    void hitConditionFormatsAndParses() {
        for (HitCondition h : List.of(HitCondition.ALWAYS, new HitCondition(HitCondition.Mode.EQUAL, 3),
                new HitCondition(HitCondition.Mode.AT_LEAST, 12), new HitCondition(HitCondition.Mode.MULTIPLE_OF, 2))) {
            assertEquals(h, HitCondition.parse(h.format()));
        }
        assertEquals(HitCondition.ALWAYS, HitCondition.parse("=x"), "unreadable = every hit");
        assertEquals("every 3rd hit", new HitCondition(HitCondition.Mode.MULTIPLE_OF, 3).describe());
        assertEquals("every 11th hit", new HitCondition(HitCondition.Mode.MULTIPLE_OF, 11).describe());
        assertEquals("hit ≥ 2", new HitCondition(HitCondition.Mode.AT_LEAST, 2).describe());
    }
}
