package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** A file's standalone breakpoints: toggling, persistence callback and foreign entries. */
class FileBreakpointsTest {

    private static final String OWN = "/tmp/sheets/main.xsl";
    private static final String OTHER = "/tmp/sheets/lib.xsl";

    @Test
    void toggleSetsRemovesAndPersists() {
        List<Set<Breakpoint>> saved = new ArrayList<>();
        FileBreakpoints bps = new FileBreakpoints(OWN, Set.of(), saved::add);

        assertTrue(bps.toggleBreakpoint(OWN, 5), "first toggle sets");
        assertTrue(bps.hasBreakpoint(OWN, 5));
        assertFalse(bps.hasBreakpoint(OWN, 6));
        assertEquals(1, saved.size());
        assertEquals(Set.of(new Breakpoint(OWN, 5, true)), saved.get(0));

        assertFalse(bps.toggleBreakpoint(OWN, 5), "second toggle removes");
        assertFalse(bps.hasBreakpoint(OWN, 5));
        assertEquals(2, saved.size());
        assertTrue(saved.get(1).isEmpty());
    }

    @Test
    void pathSpellingsAreNormalised() {
        FileBreakpoints bps = new FileBreakpoints("file:" + OWN, Set.of(), set -> { });
        assertTrue(bps.toggleBreakpoint(OWN, 3));
        assertTrue(bps.hasBreakpoint("file://" + OWN, 3));
        assertEquals(SourceLocations.normalize(OWN), bps.path());
    }

    @Test
    void foreignEntriesAreCarriedAlongButNeverShown() {
        Breakpoint foreign = new Breakpoint(OTHER, 9, true);
        List<Set<Breakpoint>> saved = new ArrayList<>();
        FileBreakpoints bps = new FileBreakpoints(OWN, Set.of(foreign, new Breakpoint(OWN, 2, true)), saved::add);

        assertFalse(bps.hasBreakpoint(OTHER, 9), "another file's entry is not this file's breakpoint");
        assertTrue(bps.hasBreakpoint(OWN, 2));
        assertEquals(Set.of(new Breakpoint(OWN, 2, true)), bps.ownBreakpoints());

        assertFalse(bps.toggleBreakpoint(OTHER, 9), "cannot toggle another file's line");
        assertTrue(saved.isEmpty(), "ignored toggles do not save");

        bps.toggleBreakpoint(OWN, 7);
        assertTrue(saved.get(0).contains(foreign), "legacy aggregate entries survive a save");
        assertTrue(saved.get(0).contains(new Breakpoint(OWN, 7, true)));
    }

    @Test
    void persistentInstanceToleratesMissingPropertiesService() {
        // Whether or not the ServiceRegistry is initialised in this JVM, building and toggling
        // must not throw — persistence degrades to a no-op without a PropertiesService.
        FileBreakpoints bps = FileBreakpoints.persistent(java.nio.file.Path.of(OWN));
        assertDoesNotThrow(() -> bps.toggleBreakpoint(OWN, 1));
        assertDoesNotThrow(() -> bps.toggleBreakpoint(OWN, 1));
    }
}
