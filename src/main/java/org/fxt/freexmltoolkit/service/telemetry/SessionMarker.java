package org.fxt.freexmltoolkit.service.telemetry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Detects sessions that ended without a clean shutdown (crash, kill, power loss, a freeze
 * that was force-closed): every running session keeps a small marker file in
 * {@code ~/.freeXmlToolkit/sessions/}, deleted on a clean exit. A marker whose process no
 * longer runs belongs to a session that died.
 *
 * <p>One file per session, so several app instances can run side by side. A marker holds
 * only the session id, app version, process id and process start time — nothing personal.
 */
public final class SessionMarker {

    private static final String SUFFIX = ".running";
    /** Upper bound of reported dead sessions per start (a long-unused machine may hold many). */
    static final int MAX_REPORTED = 3;

    /** A session that ended without removing its marker. */
    public record DeadSession(String sessionId, String appVersion) {
    }

    private final Path dir;
    private final Predicate<Marker> alive;
    private Path own;
    /** Set by {@link #end()}; a late {@link #begin} must then not leave a marker behind. */
    private boolean ended;

    record Marker(String sessionId, String appVersion, long pid, long processStartMillis) {
    }

    /**
     * @param dir   the marker folder (created on demand)
     * @param alive whether the process of a marker still runs
     */
    SessionMarker(Path dir, Predicate<Marker> alive) {
        this.dir = dir;
        this.alive = alive;
    }

    /** Marker folder below {@code ~/.freeXmlToolkit}, liveness checked via {@link ProcessHandle}. */
    public static SessionMarker createDefault() {
        return new SessionMarker(Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "sessions"),
                SessionMarker::processAlive);
    }

    /**
     * Collects (and removes) the markers of dead sessions, then writes the marker of this
     * session. Never throws.
     *
     * @return the dead sessions, oldest marker files first, at most {@link #MAX_REPORTED}
     */
    public synchronized List<DeadSession> begin(String sessionId, String appVersion) {
        List<DeadSession> dead = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + SUFFIX)) {
                for (Path file : files) {
                    Optional<Marker> marker = read(file);
                    if (marker.isPresent() && alive.test(marker.get())) {
                        continue; // another instance is running
                    }
                    Files.deleteIfExists(file);
                    if (marker.isPresent() && dead.size() < MAX_REPORTED) {
                        dead.add(new DeadSession(marker.get().sessionId(), marker.get().appVersion()));
                    }
                }
            }
            if (ended) {
                return dead;
            }
            ProcessHandle self = ProcessHandle.current();
            long started = self.info().startInstant().map(Instant::toEpochMilli).orElse(0L);
            own = dir.resolve(sanitize(sessionId) + SUFFIX);
            Files.writeString(own, String.join("\n", sessionId, String.valueOf(appVersion),
                    String.valueOf(self.pid()), String.valueOf(started)) + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // markers are best effort
        }
        return dead;
    }

    /** Removes this session's marker (clean shutdown). Never throws. */
    public synchronized void end() {
        ended = true;
        if (own == null) {
            return;
        }
        try {
            Files.deleteIfExists(own);
        } catch (IOException | RuntimeException ignored) {
            // best effort
        }
        own = null;
    }

    static Optional<Marker> read(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() < 4) {
                return Optional.empty();
            }
            return Optional.of(new Marker(lines.get(0).strip(), lines.get(1).strip(),
                    Long.parseLong(lines.get(2).strip()), Long.parseLong(lines.get(3).strip())));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Alive = a process with that pid runs and (when known) started at the recorded time. */
    static boolean processAlive(Marker marker) {
        Optional<ProcessHandle> handle = ProcessHandle.of(marker.pid());
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return false;
        }
        if (marker.processStartMillis() <= 0) {
            return true;
        }
        return handle.get().info().startInstant()
                .map(i -> Math.abs(i.toEpochMilli() - marker.processStartMillis()) < 2000)
                .orElse(true);
    }

    private static String sanitize(String sessionId) {
        return sessionId == null ? "unknown" : sessionId.replaceAll("[^A-Za-z0-9-]", "_");
    }
}
