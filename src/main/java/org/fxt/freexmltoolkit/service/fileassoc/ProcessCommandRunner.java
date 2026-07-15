package org.fxt.freexmltoolkit.service.fileassoc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link CommandRunner} backed by {@link ProcessBuilder}. Commands are executed
 * directly (no shell), with a hard timeout so a stuck OS tool can never block the caller.
 */
public class ProcessCommandRunner implements CommandRunner {

    private static final Logger logger = LogManager.getLogger(ProcessCommandRunner.class);
    private static final long TIMEOUT_SECONDS = 15;

    @Override
    public CommandResult run(List<String> command) throws IOException {
        logger.debug("Running command: {}", command);
        Process process = new ProcessBuilder(command).start();
        try {
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out after " + TIMEOUT_SECONDS + "s: " + command);
            }
            CommandResult result = new CommandResult(process.exitValue(), stdout, stderr);
            if (!result.success()) {
                logger.debug("Command exited with {}: {} — stderr: {}", result.exitCode(), command, stderr.trim());
            }
            return result;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for command: " + command, e);
        }
    }
}
