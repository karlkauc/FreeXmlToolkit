package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An unreachable remote schema is not fetched again for a while: opening the same document waited for the
 * connection timeout (~7 s) on every open (telemetry: {@code schema_bind ref_kind=http}).
 */
class UnreachableSchemasTest {

    private final AtomicLong now = new AtomicLong();

    @BeforeEach
    void clock() {
        UnreachableSchemas.clear();
        UnreachableSchemas.setClock(now::get);
    }

    @AfterEach
    void reset() {
        UnreachableSchemas.clear();
        UnreachableSchemas.setClock(null);
    }

    @Test
    void aFailedRemoteLocationIsSkippedUntilTheTtlIsOver() {
        String url = "https://schemas.example.com/a.xsd";
        UnreachableSchemas.record(url, false);
        assertTrue(UnreachableSchemas.recentlyFailed(url));

        now.addAndGet(UnreachableSchemas.TTL.toNanos() + 1);
        assertFalse(UnreachableSchemas.recentlyFailed(url), "asked again after the TTL");
    }

    @Test
    void aSuccessForgetsTheFailure() {
        String url = "http://schemas.example.com/b.xsd";
        UnreachableSchemas.record(url, false);
        UnreachableSchemas.record(url, true);
        assertFalse(UnreachableSchemas.recentlyFailed(url));
    }

    @Test
    void localLocationsAreNeverCached() {
        UnreachableSchemas.record("schema.xsd", false);
        UnreachableSchemas.record("file:///tmp/x.xsd", false);
        assertFalse(UnreachableSchemas.recentlyFailed("schema.xsd"));
        assertFalse(UnreachableSchemas.recentlyFailed("file:///tmp/x.xsd"));
    }
}
