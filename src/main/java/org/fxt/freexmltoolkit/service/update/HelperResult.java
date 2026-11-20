package org.fxt.freexmltoolkit.service.update;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The outcome of a Windows update helper run, read from the {@code RESULT} line the helper writes to
 * its log ({@code update-helper/src/result.rs}): a fixed code ({@code ok}, {@code copy_exhausted},
 * {@code parent_timeout}, …) and, for a copy error, the top-level folder of the install the failing
 * file is in ({@code app}, {@code runtime}, {@code .}). Both are checked against narrow patterns, so
 * nothing else from the log can reach the telemetry.
 *
 * @param code   the helper's result code
 * @param folder the install folder of a failed copy, or {@code null}
 */
public record HelperResult(String code, String folder) {

    /** Marker of the line; keep in sync with {@code result::MARKER} in the helper. */
    static final String MARKER = "RESULT ";
    /** Only the end of the log is read: it grows by one run per update. */
    private static final int TAIL_BYTES = 64 * 1024;
    /** Clock difference tolerated between the launch (Java) and the helper's timestamp. */
    private static final Duration SLACK = Duration.ofSeconds(5);
    /** {@code 2026-11-20T06:00:54.123456 [INFO] RESULT copy_exhausted folder=app} (UTC). */
    private static final Pattern LINE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?) \\[[A-Z]+\\] RESULT ([a-z_]{1,32})(?: folder=([A-Za-z0-9._-]{1,32}))?\\s*$");

    /** The helper's log, as {@code AutoUpdateServiceImpl} configures it. */
    public static Path defaultLog() {
        return Path.of(System.getProperty("user.home"), "fxt-update-helper.log");
    }

    /**
     * The last result in {@code log} written no earlier than {@code notBefore}.
     *
     * @param log       the helper's log file (may be missing)
     * @param notBefore when the helper was launched; earlier results belong to older updates
     * @return the result, or empty when the helper wrote none for this update (or the log is unreadable)
     */
    public static Optional<HelperResult> lastSince(Path log, Instant notBefore) {
        if (log == null || !Files.isRegularFile(log)) {
            return Optional.empty();
        }
        String tail;
        try {
            tail = readTail(log);
        } catch (IOException e) {
            return Optional.empty();
        }
        String[] lines = tail.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            Matcher m = LINE.matcher(lines[i]);
            if (!m.matches()) {
                continue;
            }
            Instant written = parse(m.group(1));
            if (written == null || (notBefore != null && written.isBefore(notBefore.minus(SLACK)))) {
                return Optional.empty(); // the newest result is from an earlier update
            }
            return Optional.of(new HelperResult(m.group(2), m.group(3)));
        }
        return Optional.empty();
    }

    private static String readTail(Path log) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            long length = file.length();
            long start = Math.max(0, length - TAIL_BYTES);
            byte[] bytes = new byte[(int) (length - start)];
            file.seek(start);
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static Instant parse(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
