package org.fxt.freexmltoolkit.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The user's maximum-heap setting ({@code memory.max.mb}, Settings &gt; Memory).
 *
 * <p>The maximum heap is fixed when the JVM starts. The packaged launcher starts with
 * {@code -XX:MaxRAMPercentage} (half of the physical memory); a different limit chosen in
 * the settings is applied by the packaged application relaunching itself once at startup with
 * {@code _JAVA_OPTIONS=-Xmx…}, which the JVM reads after the launcher's own options. The
 * launcher configuration itself is never changed: on macOS that would break the bundle's code
 * signature, on Windows it needs administrator rights, and every update would overwrite it.
 *
 * <p>Safety: the relaunched process carries {@value #ENV_MARKER} so it never relaunches
 * again for the same value. If it dies right away (for example because the JVM cannot reserve
 * the requested heap), the setting is reset to automatic and the application is started once
 * more without it, so a bad value can never lock the user out.
 */
public final class MemoryLimit {

    private static final Logger logger = LogManager.getLogger(MemoryLimit.class);

    /** Property key: the maximum heap in MB; absent, 0 or invalid means automatic. */
    public static final String PROPERTY = "memory.max.mb";
    /** Set on a relaunched process: the limit it was started with in MB ({@code 0} = automatic). */
    static final String ENV_MARKER = "FXT_HEAP_MB";
    static final String JAVA_OPTIONS_ENV = "_JAVA_OPTIONS";
    /** Smallest limit that may be configured. */
    static final long MIN_MB = 1024;
    /** A limit within this fraction of the configured one counts as applied. */
    static final double TOLERANCE = 0.10;
    /** How long a relaunched process must survive to count as started. */
    static final long CHILD_FAILURE_WAIT_MS = 1500;
    /** How long macOS gets to deliver the files the application was opened with. */
    static final long MAC_OPEN_FILES_WAIT_MS = 400;
    /** Choices offered in the settings (MB), limited to the physical memory. */
    private static final long[] CHOICES_MB = {2048, 4096, 6144, 8192, 12288, 16384, 24576, 32768, 49152, 65536};

    /** What startup has to do about the configured limit. */
    enum Action {
        /** The running JVM already has the wanted limit. */
        NONE,
        /** Relaunch with the configured limit. */
        APPLY,
        /** Relaunch without a limit override (the setting went back to automatic). */
        RESET
    }

    private MemoryLimit() {
    }

    /**
     * @param raw the stored property value (nullable)
     * @return the configured limit in MB, or {@code 0} for automatic
     */
    public static long configuredMb(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            long mb = Long.parseLong(raw.strip());
            return mb >= MIN_MB ? mb : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @param physicalMb physical memory in MB ({@code <= 0} = unknown: all choices)
     * @return the limits the settings offer, ascending, never more than the physical memory
     */
    public static List<Long> choicesMb(long physicalMb) {
        List<Long> choices = new ArrayList<>();
        for (long mb : CHOICES_MB) {
            if (physicalMb <= 0 || mb <= physicalMb) {
                choices.add(mb);
            }
        }
        return choices;
    }

    /** @return the physical memory in MB, or {@code -1} when it cannot be determined */
    public static long physicalMemoryMb() {
        try {
            var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize() / (1024 * 1024);
            }
        } catch (Throwable t) {
            logger.debug("Physical memory unknown: {}", t.toString());
        }
        return -1;
    }

    /**
     * Decides what startup has to do.
     *
     * @param desiredMb       the configured limit ({@code 0} = automatic)
     * @param currentMaxBytes {@link Runtime#maxMemory()} of this JVM
     * @param marker          {@value #ENV_MARKER} of this process (null when not relaunched)
     */
    static Action decide(long desiredMb, long currentMaxBytes, String marker) {
        if (desiredMb <= 0) {
            // automatic: only undo an override this process inherited from a relaunch
            return marker != null && !"0".equals(marker.strip()) ? Action.RESET : Action.NONE;
        }
        if (String.valueOf(desiredMb).equals(marker == null ? null : marker.strip())) {
            return Action.NONE; // relaunched for exactly this value: never loop
        }
        long currentMb = currentMaxBytes / (1024 * 1024);
        return Math.abs(currentMb - desiredMb) <= desiredMb * TOLERANCE ? Action.NONE : Action.APPLY;
    }

    /**
     * @param existing the inherited {@code _JAVA_OPTIONS} (nullable)
     * @param mb       the limit to apply, or {@code 0} for none
     * @return {@code existing} without any {@code -Xmx}, plus {@code -Xmx<mb>m}; null if nothing is left
     */
    static String javaOptions(String existing, long mb) {
        List<String> tokens = new ArrayList<>();
        if (existing != null) {
            for (String token : existing.trim().split("\\s+")) {
                if (!token.isEmpty() && !token.startsWith("-Xmx") && !token.startsWith("-XX:MaxRAMPercentage")) {
                    tokens.add(token);
                }
            }
        }
        if (mb > 0) {
            tokens.add("-Xmx" + mb + "m");
        }
        return tokens.isEmpty() ? null : String.join(" ", tokens);
    }

    /**
     * Prepares the environment of a relaunched process.
     *
     * @param env the child's environment (modified)
     * @param mb  the limit to apply, or {@code 0} for automatic
     */
    static void prepareEnvironment(Map<String, String> env, long mb) {
        String options = javaOptions(env.get(JAVA_OPTIONS_ENV), mb);
        if (options == null) {
            env.remove(JAVA_OPTIONS_ENV);
        } else {
            env.put(JAVA_OPTIONS_ENV, options);
        }
        env.put(ENV_MARKER, String.valueOf(mb));
        // the primary instance releases its lock only when it exits: wait for it, do not forward
        env.put(SingleInstance.RELAUNCH_ENV, "1");
    }

    /**
     * Applies the configured limit at startup of the packaged application: relaunches this
     * application with it when the running JVM has a different one. Returns normally when
     * nothing has to change (or the relaunch is not possible); otherwise the caller must exit.
     *
     * @param args      the command-line arguments (passed on)
     * @param primary   this process's single-instance registration, released before the relaunch (nullable)
     * @param pending   takes the files queued so far (forwarded by other instances, macOS open events)
     * @param readRaw   reads the stored property value
     * @param resetRaw  resets the stored value to automatic
     * @return {@code true} if a new process took over and this one must exit now
     */
    public static boolean relaunchIfNeeded(String[] args, SingleInstance primary,
                                           java.util.function.Supplier<List<Path>> pending,
                                           java.util.function.Supplier<String> readRaw,
                                           Consumer<String> resetRaw) {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null || Boolean.getBoolean("fxt.dev")) {
            return false; // development run: the IDE / Gradle sets the heap
        }
        long desired;
        try {
            desired = configuredMb(readRaw.get());
        } catch (RuntimeException e) {
            logger.warn("Could not read the memory setting: {}", e.toString());
            return false;
        }
        long physical = physicalMemoryMb();
        if (desired > 0 && physical > 0 && desired > physical) {
            desired = physical; // the settings never offer more; a hand-edited value is capped
        }
        Action action = decide(desired, Runtime.getRuntime().maxMemory(), System.getenv(ENV_MARKER));
        if (action == Action.NONE) {
            return false;
        }
        List<String> command = new ArrayList<>();
        command.add(appPath);
        command.addAll(List.of(args));
        if (isMac()) {
            // Files opened from Finder arrive as Apple events, delivered to this process only
            for (Path file : collectMacOpenFiles(pending)) {
                command.add(file.toString());
            }
        }
        for (Path file : pending.get()) {
            command.add(file.toString());
        }
        long mb = action == Action.APPLY ? desired : 0;
        logger.info("Relaunching with a maximum heap of {} (now {} MB)",
                mb > 0 ? mb + " MB" : "automatic", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        if (primary != null) {
            primary.close();
        }
        try {
            Process child = start(command, mb);
            if (!child.waitFor(CHILD_FAILURE_WAIT_MS, TimeUnit.MILLISECONDS) || child.exitValue() == 0) {
                return true;
            }
            logger.warn("Relaunch with {} MB exited with {}; resetting the memory setting", mb, child.exitValue());
        } catch (Exception e) {
            logger.warn("Relaunch with {} MB failed: {}; resetting the memory setting", mb, e.toString());
        }
        // The configured limit cannot be applied: back to automatic, start once more without it
        try {
            resetRaw.accept("0");
            start(command, 0);
            return true;
        } catch (Exception e) {
            logger.error("Relaunch without a memory limit failed: {}", e.toString());
            return primary != null; // without its released lock this process cannot continue cleanly
        }
    }

    private static Process start(List<String> command, long mb) throws java.io.IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        prepareEnvironment(builder.environment(), mb);
        return builder.start();
    }

    /**
     * Starts the JavaFX toolkit with the macOS open-files handler so the files this process
     * was opened with are delivered (Glass replays them when the handler is set), waits briefly
     * for late ones and returns everything queued.
     */
    private static List<Path> collectMacOpenFiles(java.util.function.Supplier<List<Path>> pending) {
        try {
            CountDownLatch installed = new CountDownLatch(1);
            javafx.application.Platform.startup(() -> {
                MacOpenFilesHandler.install();
                installed.countDown();
            });
            if (!installed.await(2, TimeUnit.SECONDS)) {
                logger.warn("JavaFX did not start within 2 s; files macOS opened the application with may be missing");
            }
            Thread.sleep(MAC_OPEN_FILES_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            logger.warn("Could not collect the files macOS opened the application with: {}", e.toString());
        }
        return pending.get();
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }
}
