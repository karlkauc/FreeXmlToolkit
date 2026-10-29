package org.fxt.freexmltoolkit.service.telemetry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the head of the crash file a HotSpot JVM writes when it dies in native code
 * ({@code hs_err_pid<pid>.log}, in the working directory or, if that is not writable, in the
 * temp folder). A session that ended without a clean exit is otherwise indistinguishable from a
 * forced close; the crash file tells the signal, the faulting library or Java method and the
 * thread. Only those three fixed pieces are extracted — no paths, addresses, registers or
 * environment.
 *
 * <p>The file is matched by the dead session's process id and must have been written after that
 * process started (pids are reused). It is left in place.
 */
public final class JvmCrashLog {

    /** How many lines of the file are read; the header is well within. */
    static final int MAX_LINES = 400;
    private static final int MAX_FIELD = 160;

    /**
     * What a crash file says.
     *
     * @param signal the signal / exception, e.g. {@code SIGSEGV}, {@code EXCEPTION_ACCESS_VIOLATION},
     *               {@code Internal Error}
     * @param frame  the problematic frame: a native library name ({@code fontmanager.dll}) or a Java
     *               method ({@code org.foo.Bar#baz}), prefixed with the frame kind ({@code C}, {@code J}, …)
     * @param thread the name of the crashed thread, e.g. {@code JavaFX Application Thread}
     */
    public record Crash(String signal, String frame, String thread) {
    }

    private static final Pattern SIGNAL = Pattern.compile("^#\\s+(.+?)\\s+\\((?:0x|os_|[a-z]).*pid=\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNAL_NO_PID = Pattern.compile("^#\\s+((?:SIG[A-Z]+|EXCEPTION_[A-Z_]+|Internal Error|Out of Memory Error))\\b");
    private static final Pattern FRAME_LIBRARY = Pattern.compile("\\[([^\\]+]+)\\+0x");
    private static final Pattern FRAME_JAVA = Pattern.compile("([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)\\.([A-Za-z_$<][\\w$>]*)\\s*\\(");
    private static final Pattern THREAD = Pattern.compile("^Current thread.*?\"([^\"]+)\"");

    private final List<Path> dirs;

    JvmCrashLog(List<Path> dirs) {
        this.dirs = List.copyOf(dirs);
    }

    /** The folders the JVM writes crash files to: working dir, temp dir, home and the app's own folder. */
    public static JvmCrashLog createDefault() {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        for (String property : new String[] {"user.dir", "java.io.tmpdir", "user.home"}) {
            String value = System.getProperty(property);
            if (value != null && !value.isBlank()) {
                dirs.add(Path.of(value));
            }
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            dirs.add(Path.of(home, ".freeXmlToolkit"));
        }
        return new JvmCrashLog(new ArrayList<>(dirs));
    }

    /**
     * Looks for the crash file of a process.
     *
     * @param pid                the process id of the dead session
     * @param processStartMillis when that process started (epoch ms); {@code 0} = unknown, any file counts
     * @return the crash, or empty when no (recent enough) crash file exists. Never throws.
     */
    public Optional<Crash> find(long pid, long processStartMillis) {
        if (pid <= 0) {
            return Optional.empty();
        }
        String name = "hs_err_pid" + pid + ".log";
        for (Path dir : dirs) {
            try {
                Path file = dir.resolve(name);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (processStartMillis > 0 && Files.getLastModifiedTime(file).toMillis() < processStartMillis) {
                    continue; // a crash of an earlier process that happened to have the same pid
                }
                Optional<Crash> crash = parse(file);
                if (crash.isPresent()) {
                    return crash;
                }
            } catch (IOException | RuntimeException e) {
                // best effort
            }
        }
        return Optional.empty();
    }

    /** Parses the head of a crash file. Never throws. */
    static Optional<Crash> parse(Path file) {
        List<String> head = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            while (head.size() < MAX_LINES && (line = reader.readLine()) != null) {
                head.add(line);
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        return parse(head);
    }

    /** Parses the first lines of a crash file; empty when they do not look like one. */
    static Optional<Crash> parse(List<String> lines) {
        String signal = null;
        String frame = null;
        String thread = null;
        boolean fatal = false;
        boolean frameNext = false;
        for (String line : lines) {
            if (line.contains("A fatal error has been detected by the Java Runtime Environment")) {
                fatal = true;
                continue;
            }
            if (frameNext) {
                frameNext = false;
                frame = frameOf(line);
                continue;
            }
            if (line.startsWith("# Problematic frame")) {
                frameNext = true;
                continue;
            }
            if (signal == null && line.startsWith("#")) {
                Matcher m = SIGNAL.matcher(line);
                if (m.find()) {
                    signal = m.group(1).strip();
                } else {
                    Matcher n = SIGNAL_NO_PID.matcher(line);
                    if (n.find()) {
                        signal = n.group(1);
                    }
                }
                continue;
            }
            if (thread == null) {
                Matcher m = THREAD.matcher(line);
                if (m.find()) {
                    thread = m.group(1);
                }
            }
        }
        if (!fatal || signal == null) {
            return Optional.empty();
        }
        return Optional.of(new Crash(clip(signal), clip(frame), clip(thread)));
    }

    /**
     * Reduces a "Problematic frame" line to its kind and the library or method, e.g.
     * {@code C  [fontmanager.dll+0x1234]  foo+0x12} → {@code C fontmanager.dll},
     * {@code J 1234 c2 org.foo.Bar.baz(Ljava/lang/String;)V (12 bytes) @ 0x…} → {@code J org.foo.Bar#baz}.
     */
    static String frameOf(String line) {
        String body = line.startsWith("#") ? line.substring(1).strip() : line.strip();
        if (body.isEmpty()) {
            return null;
        }
        String kind = body.substring(0, 1);
        // J / j = compiled / interpreted Java method; the trailing "[0x…+0x…]" is a code address
        Matcher java = FRAME_JAVA.matcher(body);
        if (java.find()) {
            return kind + " " + java.group(1) + "#" + java.group(2);
        }
        Matcher lib = FRAME_LIBRARY.matcher(body);
        if (lib.find() && !lib.group(1).startsWith("0x")) {
            String name = lib.group(1);
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            return kind + " " + name.substring(slash + 1);
        }
        return kind;
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_FIELD ? value : value.substring(0, MAX_FIELD);
    }
}
