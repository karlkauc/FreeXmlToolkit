package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;

import org.fxt.freexmltoolkit.service.progress.Progress;

/**
 * Progress of a long command on stderr: one line, rewritten in place (at most five times a second), cleared at the
 * end. Only on a terminal ({@link #forTerminal}): redirected or piped output gets nothing, stdout never anything.
 */
final class CliProgress implements Progress {

    private static final int WIDTH = 100;
    private static final long INTERVAL_MILLIS = 200;

    private final PrintWriter err;
    private volatile String message = "";
    private volatile long done;
    private volatile long total;
    private long lastPrint;
    private int lastLength;

    CliProgress(PrintWriter err) {
        this.err = err;
    }

    /** @return a progress line on {@code err} when the command runs in a terminal, else {@link Progress#NONE} */
    static Progress forTerminal(PrintWriter err) {
        return System.console() != null ? new CliProgress(err) : Progress.NONE;
    }

    /** Clears the line of a progress made by {@link #forTerminal} (nothing for {@link Progress#NONE}). */
    static void end(Progress progress) {
        if (progress instanceof CliProgress cli) {
            cli.clear();
        }
    }

    @Override
    public void message(String text) {
        message = text == null ? "" : text;
        print(false);
    }

    @Override
    public void progress(long done, long total) {
        this.done = done;
        this.total = total;
        print(false);
    }

    @Override
    public boolean isCancelled() {
        return false; // Ctrl+C ends the process
    }

    /** @return the line as printed: message and, when known, the step count */
    String line() {
        String line = message + (total > 0 ? "  [" + done + "/" + total + "]" : "");
        return line.length() > WIDTH ? line.substring(0, WIDTH - 1) + "…" : line;
    }

    private synchronized void print(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastPrint < INTERVAL_MILLIS) {
            return;
        }
        lastPrint = now;
        String line = line();
        err.print("\r" + line + " ".repeat(Math.max(0, lastLength - line.length())));
        err.flush();
        lastLength = line.length();
    }

    /** Clears the progress line. */
    synchronized void clear() {
        if (lastLength > 0) {
            err.print("\r" + " ".repeat(lastLength) + "\r");
            err.flush();
            lastLength = 0;
        }
    }
}
