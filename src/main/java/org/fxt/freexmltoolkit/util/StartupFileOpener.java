package org.fxt.freexmltoolkit.util;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Decouples "open this file" events (command-line arguments, macOS open-file Apple
 * events) from UI readiness: events arriving before the shell is up are queued and
 * drained once a consumer is registered.
 */
public final class StartupFileOpener {

    private static final Logger logger = LogManager.getLogger(StartupFileOpener.class);

    private static final Queue<Path> pending = new ConcurrentLinkedQueue<>();
    private static volatile Consumer<Path> consumer;

    /**
     * Dispatcher used to hop onto the UI thread; swappable for tests.
     */
    static Consumer<Runnable> uiDispatcher = Platform::runLater;

    private StartupFileOpener() {
    }

    /**
     * Queues files to open. Non-existing paths (e.g. stray JVM arguments) are ignored.
     * Files are delivered immediately when a consumer is already registered.
     */
    public static void enqueue(Collection<Path> files) {
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                pending.add(file.toAbsolutePath());
            } else {
                logger.debug("Ignoring startup argument that is no regular file: {}", file);
            }
        }
        drain();
    }

    /**
     * Queues raw command-line arguments, ignoring anything that is not a parseable
     * path to an existing file.
     */
    public static void enqueueRawArgs(Collection<String> args) {
        for (String arg : args) {
            try {
                enqueue(java.util.List.of(Path.of(arg)));
            } catch (java.nio.file.InvalidPathException e) {
                logger.debug("Ignoring unparseable startup argument: {}", arg);
            }
        }
    }

    /**
     * Registers the consumer that opens files in the editor (called once the shell is
     * ready) and drains everything queued so far. Delivery happens on the UI thread.
     */
    public static void setConsumer(Consumer<Path> newConsumer) {
        consumer = newConsumer;
        drain();
    }

    private static void drain() {
        Consumer<Path> target = consumer;
        if (target == null) {
            return;
        }
        Path file;
        while ((file = pending.poll()) != null) {
            Path toOpen = file;
            logger.info("Opening startup file: {}", toOpen);
            uiDispatcher.accept(() -> target.accept(toOpen));
        }
    }

    /**
     * Visible for tests.
     */
    static void reset() {
        pending.clear();
        consumer = null;
        uiDispatcher = Platform::runLater;
    }
}
