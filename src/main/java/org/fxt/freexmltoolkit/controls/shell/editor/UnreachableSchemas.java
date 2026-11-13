package org.fxt.freexmltoolkit.controls.shell.editor;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Remote schema locations that could not be fetched a moment ago. Opening a document whose
 * {@code xsi:schemaLocation} points at an unreachable server waited for the connection timeout (about
 * 7 s) on every open, again and again for the same file (telemetry: {@code schema_bind ref_kind=http});
 * within {@link #TTL} the same location is not asked again. A successful fetch forgets the entry.
 */
final class UnreachableSchemas {

    /** How long a failed remote location is not asked again. */
    static final Duration TTL = Duration.ofMinutes(10);

    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static volatile LongSupplier clock = System::nanoTime;

    private UnreachableSchemas() {
    }

    /** @return whether the location is fetched over the network (http, https, ftp) */
    static boolean isRemote(String location) {
        if (location == null) {
            return false;
        }
        String lower = location.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://");
    }

    /** @return whether the remote location failed within {@link #TTL} */
    static boolean recentlyFailed(String location) {
        if (!isRemote(location)) {
            return false;
        }
        Long at = FAILED_AT.get(location.strip());
        if (at == null) {
            return false;
        }
        if (clock.getAsLong() - at > TTL.toNanos()) {
            FAILED_AT.remove(location.strip(), at);
            return false;
        }
        return true;
    }

    /** Records the outcome of a fetch of a remote location. */
    static void record(String location, boolean reached) {
        if (!isRemote(location)) {
            return;
        }
        if (reached) {
            FAILED_AT.remove(location.strip());
        } else {
            FAILED_AT.put(location.strip(), clock.getAsLong());
        }
    }

    /** Forgets every failure (tests). */
    static void clear() {
        FAILED_AT.clear();
    }

    /** Test hook: the time source in nanoseconds; {@code null} restores the system clock. */
    static void setClock(LongSupplier nanos) {
        clock = nanos != null ? nanos : System::nanoTime;
    }
}
