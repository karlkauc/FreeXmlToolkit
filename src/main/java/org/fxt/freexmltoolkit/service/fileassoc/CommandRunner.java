package org.fxt.freexmltoolkit.service.fileassoc;

import java.util.List;

/**
 * Executes an external command (no shell involved) and captures its result.
 * Abstracted as an interface so the file-association strategies can be unit-tested
 * without touching the operating system.
 */
public interface CommandRunner {

    /**
     * Runs the given command, waiting for completion.
     *
     * @param command the executable and its arguments, one list element each
     * @return the process result (never null)
     * @throws java.io.IOException if the process cannot be started or times out
     */
    CommandResult run(List<String> command) throws java.io.IOException;

    /**
     * Result of an external command execution.
     *
     * @param exitCode the process exit code
     * @param stdout   captured standard output (UTF-8)
     * @param stderr   captured standard error (UTF-8)
     */
    record CommandResult(int exitCode, String stdout, String stderr) {
        public boolean success() {
            return exitCode == 0;
        }
    }
}
