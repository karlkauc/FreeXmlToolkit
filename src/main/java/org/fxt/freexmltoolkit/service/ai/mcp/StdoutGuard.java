package org.fxt.freexmltoolkit.service.ai.mcp;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Keeps standard output for MCP protocol messages only. While installed, {@link System#out} is the standard
 * error stream, so whatever a library prints during a tool call lands there; the transport writes to
 * {@link #protocolStream()}, the original standard output. A single stray line on stdout would break the client.
 */
public final class StdoutGuard implements AutoCloseable {

    private final PrintStream original;

    private StdoutGuard(PrintStream original) {
        this.original = original;
    }

    /** Redirects {@link System#out} to {@link System#err}. */
    public static StdoutGuard install() {
        PrintStream original = System.out;
        System.out.flush();
        System.setOut(System.err);
        return new StdoutGuard(original);
    }

    /** The original standard output, for the protocol. */
    public OutputStream protocolStream() {
        return original;
    }

    /** Restores {@link System#out}. */
    @Override
    public void close() {
        System.out.flush();
        System.setOut(original);
    }
}
