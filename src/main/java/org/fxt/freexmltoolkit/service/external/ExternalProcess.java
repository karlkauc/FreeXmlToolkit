package org.fxt.freexmltoolkit.service.external;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.util.ExternalCommands;

/**
 * Runs an external program (XSL-FO or XSLT processor) once: no shell, no input, standard error merged
 * into standard output, the last {@link #MAX_OUTPUT_LINES} output lines kept, and stopped together with
 * everything it started (wrapper scripts start a JVM) after the time limit or when the calling thread is
 * interrupted.
 */
public final class ExternalProcess {

    private static final Logger logger = LogManager.getLogger(ExternalProcess.class);

    /** Output lines kept per run (the oldest are dropped). */
    public static final int MAX_OUTPUT_LINES = 2000;

    /**
     * What a run ended with.
     *
     * @param finished whether the program ended within the time limit (else it was stopped)
     * @param exitCode the exit code ({@code -1} when it was stopped)
     * @param lines    the output lines
     */
    public record Outcome(boolean finished, int exitCode, List<String> lines) {
    }

    private ExternalProcess() {
    }

    /** @return the message when an argument would be interpreted by cmd.exe, else {@code null} */
    public static String unsafeArguments(String processorName, Path command, List<String> arguments) {
        return ExternalCommands.unsafeForBatch(command, arguments)
                ? processorName + ": an argument contains characters that cmd.exe would interpret (& | < > ^ % ! \"). "
                        + "Rename the file, or configure the program's .exe instead of a .cmd/.bat script."
                : null;
    }

    /**
     * @param commandLine the program and its arguments
     * @throws IOException          when the program cannot be started
     * @throws InterruptedException when the calling thread is interrupted (the program is stopped)
     */
    public static Outcome run(String label, List<String> commandLine, Path workingDir, int timeoutSeconds)
            throws IOException, InterruptedException {
        logger.info("Running {}: {}", label, commandLine);
        ProcessBuilder builder = new ProcessBuilder(commandLine)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);
        builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
        Process process = builder.start();
        Deque<String> lines = new ArrayDeque<>();
        Thread reader = Thread.ofVirtual().name("external-process-output").start(() -> readLines(process, lines));
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            stop(process);
            throw e;
        }
        if (!finished) {
            stop(process);
        }
        reader.join(TimeUnit.SECONDS.toMillis(5));
        List<String> collected;
        synchronized (lines) {
            collected = new ArrayList<>(lines);
        }
        return new Outcome(finished, finished ? process.exitValue() : -1, collected);
    }

    private static void readLines(Process process, Deque<String> lines) {
        Charset charset = consoleCharset();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (lines) {
                    lines.addLast(line);
                    if (lines.size() > MAX_OUTPUT_LINES) {
                        lines.removeFirst();
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Reading the program's output stopped: {}", e.getMessage());
        }
    }

    private static Charset consoleCharset() {
        try {
            String name = System.getProperty("native.encoding");
            return name == null ? Charset.defaultCharset() : Charset.forName(name);
        } catch (RuntimeException e) {
            return Charset.defaultCharset();
        }
    }

    /** Stops the program and whatever it started. */
    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static java.io.File nullDevice() {
        return new java.io.File(ExternalCommands.isWindows() ? "NUL" : "/dev/null");
    }
}
