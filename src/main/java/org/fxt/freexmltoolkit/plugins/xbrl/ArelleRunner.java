package org.fxt.freexmltoolkit.plugins.xbrl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;

/**
 * Validates XBRL instances and inline XBRL reports with Arelle (arelle.org), the open-source XBRL
 * processor, run as a separate program: {@code arelleCmdLine -f FILE -v --logFile LOG.json}. Its
 * JSON log (one entry per message: level, code, text, references with source lines) becomes the
 * problems. Arelle is not bundled: it is installed with {@code pip install arelle-release}.
 */
public final class ArelleRunner {

    /** The program name looked up on the PATH. */
    static final String COMMAND = "arelleCmdLine";

    private ArelleRunner() {
    }

    /**
     * @param configured the command set by the user, or {@code null}/blank
     * @return the command to run: the configured one, else {@value #COMMAND} found on the PATH
     */
    public static Optional<Path> findCommand(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.strip());
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        String pathVariable = System.getenv("PATH");
        if (pathVariable == null) {
            return Optional.empty();
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (String dir : pathVariable.split(File.pathSeparator)) {
            for (String name : windows ? List.of(COMMAND + ".exe", COMMAND + ".cmd", COMMAND) : List.of(COMMAND)) {
                Path candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Runs Arelle on a file.
     *
     * @return the errors and warnings (information messages are left out)
     * @throws IOException when Arelle cannot be started, times out or writes no log
     */
    public static List<DocumentValidator.Problem> validate(Path command, Path file, Duration timeout)
            throws IOException, InterruptedException {
        if (unsafeForBatch(command, file)) {
            throw new IOException("The file name " + file.getFileName() + " contains characters that cmd.exe would "
                    + "interpret; rename it or configure arelleCmdLine.exe instead of a .cmd/.bat script.");
        }
        Path log = Files.createTempFile("fxt-arelle", ".json");
        try {
            ProcessBuilder builder = new ProcessBuilder(command.toString(), "-f", file.toAbsolutePath().toString(), "-v",
                    "--logFile", log.toString(), "--logFileMode", "w", "--logFormat", "%(message)s");
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Arelle did not finish within " + timeout.toMinutes() + " minutes.");
            }
            String json = Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "";
            if (json.isBlank()) {
                throw new IOException("Arelle wrote no log (exit code " + process.exitValue() + ").");
            }
            return parseLog(json, file.getFileName().toString());
        } finally {
            Files.deleteIfExists(log);
        }
    }

    /**
     * Windows runs a {@code .cmd} / {@code .bat} through {@code cmd.exe}, which reads {@code &}, {@code |},
     * {@code %} and the like in arguments as its own syntax, whatever the quoting.
     */
    static boolean unsafeForBatch(Path command, Path file) {
        String name = command.getFileName() == null ? "" : command.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".cmd") && !name.endsWith(".bat")) {
            return false;
        }
        return file.toAbsolutePath().toString().chars().anyMatch(c -> "&|<>^%!\"\r\n".indexOf(c) >= 0);
    }

    /** Turns Arelle's JSON log into problems; {@code fileName} picks the line of a reference into the file. */
    static List<DocumentValidator.Problem> parseLog(String json, String fileName) {
        List<DocumentValidator.Problem> problems = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        JsonArray entries = root.isJsonObject() && root.getAsJsonObject().has("log")
                ? root.getAsJsonObject().getAsJsonArray("log") : new JsonArray();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String level = string(entry, "level").toLowerCase(Locale.ROOT);
            String severity = switch (level) {
                case "error", "critical", "fatal", "inconsistency" -> "error";
                case "warning" -> "warning";
                default -> null; // info, debug: timing and progress
            };
            if (severity == null) {
                continue;
            }
            String code = string(entry, "code");
            String text = entry.has("message") && entry.get("message").isJsonObject()
                    ? string(entry.getAsJsonObject("message"), "text") : "";
            int line = 0;
            if (entry.has("refs") && entry.get("refs").isJsonArray()) {
                for (JsonElement ref : entry.getAsJsonArray("refs")) {
                    JsonObject r = ref.getAsJsonObject();
                    String href = string(r, "href");
                    String target = href.contains("#") ? href.substring(0, href.indexOf('#')) : href;
                    if (r.has("sourceLine") && (target.isEmpty() || target.endsWith(fileName))) {
                        line = r.get("sourceLine").getAsInt();
                        break;
                    }
                }
            }
            String message = text.strip();
            problems.add(new DocumentValidator.Problem(severity, line,
                    code.isEmpty() || message.startsWith("[" + code + "]") ? message : "[" + code + "] " + message));
        }
        return problems;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
