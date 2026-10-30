package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;

/**
 * Deletes old log files of the application. Log4j's own rollover rules only run when a log rolls over and
 * miss older naming schemes; this runs at startup (when enabled in the settings) and on demand from the
 * Settings page, with the user's limits.
 *
 * <p>Only files directly in the log folder whose names look like this application's logs are touched
 * ({@code fxt.log}, {@code fxt-2026-10-30-1.log.gz}, {@code fxt-test.log-20261015.gz}, {@code 123-fxt.log}),
 * never the files the logging system currently writes.</p>
 */
public final class LogCleanupService {

    private static final Logger logger = LogManager.getLogger(LogCleanupService.class);

    /** Names of this application's log files and their archives. */
    private static final Pattern LOG_FILE = Pattern.compile(
            "(fxt[^/\\\\]*|[^/\\\\]*-fxt)\\.log(\\.gz|-\\d+(\\.gz)?|\\.\\d+(\\.gz)?)?");

    /** What a clean-up removed. */
    public record Result(int files, long bytes) {
        public static final Result NONE = new Result(0, 0);
    }

    /** Number and total size of the log files in a folder. */
    public record Usage(int files, long bytes) {
    }

    private LogCleanupService() {
    }

    /** @return whether {@code fileName} is one of this application's log files or archives */
    static boolean isLogFile(String fileName) {
        return fileName != null && LOG_FILE.matcher(fileName).matches();
    }

    /**
     * The folder the file appenders write to (absolute), taken from the running logging configuration so an
     * external {@code log4j2.xml} is honoured; {@code logs} under the working directory if none is found.
     */
    public static Path logDirectory() {
        for (Path active : activeLogFiles()) {
            if (active.getParent() != null) {
                return active.getParent();
            }
        }
        return Path.of("logs").toAbsolutePath().normalize();
    }

    /** @return the files the logging system currently writes (absolute, normalized) */
    static Set<Path> activeLogFiles() {
        Set<Path> files = new HashSet<>();
        try {
            if (LogManager.getContext(false) instanceof LoggerContext context) {
                for (Appender appender : context.getConfiguration().getAppenders().values()) {
                    String name = switch (appender) {
                        case RollingFileAppender rolling -> rolling.getFileName();
                        case FileAppender file -> file.getFileName();
                        default -> null;
                    };
                    if (name != null) {
                        files.add(Path.of(name).toAbsolutePath().normalize());
                    }
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Could not read the log file appenders: {}", e.toString());
        }
        return files;
    }

    /** Counts the log files in {@code dir} (call off the FX thread). */
    public static Usage usage(Path dir) {
        int files = 0;
        long bytes = 0;
        for (Path file : logFiles(dir)) {
            files++;
            bytes += sizeOf(file);
        }
        return new Usage(files, bytes);
    }

    /** Deletes old log files in {@link #logDirectory()} (call off the FX thread). */
    public static Result clean(int maxAgeDays, long maxTotalMb) {
        return clean(logDirectory(), maxAgeDays, maxTotalMb);
    }

    /** Deletes old log files in {@code dir}, keeping the files in use (call off the FX thread). */
    public static Result clean(Path dir, int maxAgeDays, long maxTotalMb) {
        return clean(dir, maxAgeDays, maxTotalMb, Instant.now(), activeLogFiles());
    }

    /**
     * Deletes log files in {@code dir} that are older than {@code maxAgeDays}, then — newest kept first — those
     * beyond {@code maxTotalMb} in total. Files in {@code keep} are never deleted but count toward the size.
     * A file that cannot be deleted is skipped.
     *
     * @param maxAgeDays days to keep, 0 = no age limit
     * @param maxTotalMb total size to keep, 0 = no size limit
     * @return what was deleted
     */
    static Result clean(Path dir, int maxAgeDays, long maxTotalMb, Instant now, Set<Path> keep) {
        if (maxAgeDays <= 0 && maxTotalMb <= 0) {
            return Result.NONE;
        }
        record Entry(Path path, Instant modified, long size, boolean kept) {
        }
        List<Entry> entries = new ArrayList<>();
        for (Path file : logFiles(dir)) {
            try {
                Path normalized = file.toAbsolutePath().normalize();
                entries.add(new Entry(file, Files.getLastModifiedTime(file).toInstant(), Files.size(file),
                        keep.contains(normalized)));
            } catch (IOException e) {
                logger.debug("Skipping log file {}: {}", file, e.toString());
            }
        }
        entries.sort(Comparator.comparing(Entry::kept).reversed().thenComparing(Entry::modified,
                Comparator.reverseOrder()));

        Instant cutoff = maxAgeDays > 0 ? now.minus(Duration.ofDays(maxAgeDays)) : null;
        long limit = maxTotalMb > 0 ? maxTotalMb * 1024 * 1024 : Long.MAX_VALUE;
        long kept = 0;
        int deleted = 0;
        long freed = 0;
        for (Entry entry : entries) {
            boolean tooOld = cutoff != null && entry.modified().isBefore(cutoff);
            boolean overLimit = kept + entry.size() > limit;
            if (entry.kept() || (!tooOld && !overLimit)) {
                kept += entry.size();
                continue;
            }
            try {
                if (Files.deleteIfExists(entry.path())) {
                    deleted++;
                    freed += entry.size();
                }
            } catch (IOException | SecurityException e) {
                kept += entry.size();
                logger.debug("Could not delete log file {}: {}", entry.path(), e.toString());
            }
        }
        if (deleted > 0) {
            logger.info("Deleted {} old log file(s), {} KB, in {}", deleted, freed / 1024, dir);
        }
        return new Result(deleted, freed);
    }

    /** The log files directly in {@code dir}; empty if it does not exist or cannot be read. */
    private static List<Path> logFiles(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> isLogFile(p.getFileName().toString()))
                    .toList();
        } catch (IOException | SecurityException e) {
            logger.debug("Could not list log folder {}: {}", dir, e.toString());
            return List.of();
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }
}
