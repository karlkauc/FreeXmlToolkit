package org.fxt.freexmltoolkit.service.telemetry;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Telemetry of the in-app update, as a funnel of three events:
 * <ol>
 *   <li>{@code update_download} — the release asset was fetched and its integrity checked
 *       ({@code status ok | error | cancelled}, {@code duration_ms}, {@code input_bytes},
 *       {@code error_code} = exception class on failure)</li>
 *   <li>{@code update_apply} — the platform updater was launched (Windows helper, macOS
 *       bundle swap, Linux copy); {@code status error} when it could not be launched</li>
 *   <li>{@code update_installed} — sent by the <em>next</em> start: {@code ok} when the running
 *       version is the one the update installed, else {@code error} (the swap failed)</li>
 * </ol>
 * All three carry {@code meta.from} / {@code meta.to} (versions) and {@code meta.platform}.
 * Before the updater takes over, the pending versions are persisted in the settings
 * ({@link #PROP_FROM}, {@link #PROP_TO}, {@link #PROP_AT}); the next {@code app_start}
 * reports and clears them ({@link #reportInstalled}).
 *
 * <p>Like every usage event these obey the usage-statistics toggle; all methods never throw
 * (telemetry must never break the update).
 */
public final class UpdateFunnel {

    /** Settings key: version the pending update replaces. */
    public static final String PROP_FROM = "update.pending.from";
    /** Settings key: version the pending update installs. */
    public static final String PROP_TO = "update.pending.to";
    /** Settings key: when the updater was launched (ISO instant). */
    public static final String PROP_AT = "update.pending.at";

    /** {@code error_code} of {@code update_installed} when the old version is still running. */
    static final String CODE_VERSION_MISMATCH = "version_mismatch";
    /** {@code error_code} of {@code update_apply} when the updater could not be launched. */
    static final String CODE_LAUNCH_FAILED = "launch_failed";

    private UpdateFunnel() {
    }

    /**
     * The download stage ended.
     *
     * @param from       running version
     * @param to         version being downloaded
     * @param startNanos {@link System#nanoTime()} when the download started ({@code 0} = not measured)
     * @param bytes      downloaded size (negative = unknown)
     * @param failure    the error that ended the stage, or null on success / cancel
     * @param cancelled  the user cancelled the update
     */
    public static void downloaded(String from, String to, long startNanos, long bytes,
                                  Throwable failure, boolean cancelled) {
        downloaded(from, to, startNanos, bytes, failure == null ? null : failure.getClass().getSimpleName(), cancelled);
    }

    /**
     * The download stage ended.
     *
     * @param errorCode fixed failure code (exception class name, {@code no_asset}, …), or null
     */
    public static void downloaded(String from, String to, long startNanos, long bytes,
                                  String errorCode, boolean cancelled) {
        try {
            Telemetry.get().trackAction("update_download", b -> {
                versions(b, from, to);
                if (cancelled) {
                    b.status(TelemetryEvent.Status.CANCELLED);
                } else if (errorCode != null) {
                    b.status(TelemetryEvent.Status.ERROR).errorCode(errorCode);
                }
                if (bytes >= 0) {
                    b.inputBytes(bytes);
                }
                if (startNanos != 0) {
                    b.durationSinceNanos(startNanos);
                }
            });
        } catch (Throwable ignored) {
            // never disturb the update
        }
    }

    /**
     * The platform updater was launched (or failed to launch).
     *
     * @param launched whether the updater took over
     */
    public static void applied(String from, String to, boolean launched) {
        try {
            Telemetry.get().trackAction("update_apply", b -> {
                versions(b, from, to);
                if (!launched) {
                    b.status(TelemetryEvent.Status.ERROR).errorCode(CODE_LAUNCH_FAILED);
                }
            });
        } catch (Throwable ignored) {
            // never disturb the update
        }
    }

    /**
     * Persists the pending update so that the next start can report whether the swap worked.
     * Written to disk right away ({@link PropertiesService#flush()}): on Linux the new version
     * starts while this process is still running. No-op while telemetry is inactive.
     */
    public static void markPending(PropertiesService props, String from, String to, Clock clock) {
        if (props == null || from == null || to == null) {
            return;
        }
        try {
            if (!Telemetry.get().isActive()) {
                return;
            }
            props.set(PROP_FROM, from);
            props.set(PROP_TO, to);
            props.set(PROP_AT, (clock != null ? clock : Clock.systemUTC()).instant().toString());
            props.flush();
        } catch (Throwable ignored) {
            // best effort
        }
    }

    /** Forgets a pending update (the updater could not be launched). */
    public static void clearPending(PropertiesService props) {
        if (props == null) {
            return;
        }
        try {
            if (props.get(PROP_FROM) == null && props.get(PROP_TO) == null && props.get(PROP_AT) == null) {
                return;
            }
            // PropertiesService#set ignores null values: blank marks "absent"
            props.set(PROP_FROM, "");
            props.set(PROP_TO, "");
            props.set(PROP_AT, "");
            props.flush();
        } catch (Throwable ignored) {
            // best effort
        }
    }

    /**
     * Reports the result of a pending update ({@code update_installed}) and clears it. Call once
     * per start, after {@code app_start}.
     *
     * @param props          the settings holding the pending update
     * @param runningVersion the version of this process
     * @param telemetry      where to record the event
     * @param clock          for {@code duration_ms} (launch of the updater → this start)
     * @return whether a pending update was found (and reported, if usage statistics are on)
     */
    public static boolean reportInstalled(PropertiesService props, String runningVersion,
                                          TelemetryService telemetry, Clock clock) {
        if (props == null || telemetry == null) {
            return false;
        }
        try {
            String from = blankToNull(props.get(PROP_FROM));
            String to = blankToNull(props.get(PROP_TO));
            String at = blankToNull(props.get(PROP_AT));
            if (from == null && to == null) {
                return false;
            }
            clearPending(props);
            boolean ok = to != null && normalize(to).equals(normalize(runningVersion));
            Long ms = millisSince(at, clock);
            telemetry.track("update_installed", TelemetryEvent.Category.LIFECYCLE, b -> {
                versions(b, from, to);
                b.meta("actual", normalize(runningVersion));
                if (!ok) {
                    b.status(TelemetryEvent.Status.ERROR).errorCode(CODE_VERSION_MISMATCH);
                }
                if (ms != null) {
                    b.durationMs(ms);
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code meta.platform}: {@code windows | macos | linux | other}. */
    static String platform(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        return os.contains("linux") || os.contains("nux") ? "linux" : "other";
    }

    /** Strips a leading {@code v} and blanks, so {@code v2.3.0} equals {@code 2.3.0}. */
    static String normalize(String version) {
        return version == null ? "" : version.strip().replaceFirst("^[vV]", "");
    }

    private static void versions(TelemetryEvent.Builder b, String from, String to) {
        b.meta("from", from == null ? null : normalize(from))
                .meta("to", to == null ? null : normalize(to))
                .meta("platform", platform(System.getProperty("os.name")));
    }

    private static Long millisSince(String isoInstant, Clock clock) {
        if (isoInstant == null) {
            return null;
        }
        try {
            long ms = (clock != null ? clock : Clock.systemUTC()).millis() - Instant.parse(isoInstant).toEpochMilli();
            return ms >= 0 ? ms : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
