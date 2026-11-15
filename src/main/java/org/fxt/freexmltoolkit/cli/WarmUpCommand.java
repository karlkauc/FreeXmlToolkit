package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli warm-up} (hidden): opens every file of the installation once.
 *
 * <p>The Windows installer runs it as its last step. A virus scanner checks files a new
 * installation wrote when they are first opened; without this, that happens at the first start of
 * the application, which then took 13 s and more just to open its JAR files (telemetry
 * {@code phase_cp_open_ms}). Opening them here moves that wait into the installation. Always exits
 * with 0: a failing warm-up must never fail an installation.
 */
@Command(name = "warm-up",
        hidden = true,
        mixinStandardHelpOptions = true,
        header = "Opens every file of the installation once (run by the Windows installer).",
        description = "Opens every file below DIR once so that a virus scanner checks it now, not at the "
                + "first start. Always exits with 0.")
final class WarmUpCommand implements Callable<Integer> {

    /** How many files are opened at the same time. */
    static final int THREADS = 4;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", arity = "0..1", paramLabel = "DIR",
            description = "The installation folder (default: the one this launcher belongs to).")
    Path dir;

    @Option(names = "--timeout", paramLabel = "SECONDS", defaultValue = "120",
            description = "Stop after this many seconds (default: ${DEFAULT-VALUE}).")
    long timeoutSeconds;

    @Override
    public Integer call() {
        Path root = dir != null ? dir : installationRoot();
        if (root == null || !Files.isDirectory(root)) {
            spec.commandLine().getErr().println("fxt-cli warm-up: no installation folder (" + root + ")");
            return ExitCodes.OK;
        }
        Result result = warm(root, Duration.ofSeconds(Math.max(1, timeoutSeconds)));
        spec.commandLine().getOut().printf("Opened %d of %d files in %d ms%s%n", result.opened(), result.files(),
                result.millis(), result.complete() ? "" : " (stopped at the time limit)");
        return ExitCodes.OK;
    }

    /**
     * The installation folder: the folder of the launcher that started this JVM (jpackage sets
     * {@code jpackage.app-path}), else the parent of the Java runtime.
     */
    static Path installationRoot() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null && !launcher.isBlank()) {
            Path parent = Path.of(launcher).toAbsolutePath().getParent();
            // Linux puts the launchers into bin/, next to lib/ with the runtime and the app
            if (parent != null && parent.getFileName() != null && parent.getFileName().toString().equals("bin")
                    && parent.getParent() != null) {
                return parent.getParent();
            }
            return parent;
        }
        Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath();
        return javaHome.getParent();
    }

    /**
     * What a warm-up did.
     *
     * @param files    regular files found
     * @param opened   files opened and read
     * @param millis   time taken
     * @param complete whether every file was tried before the deadline (unreadable ones count)
     */
    record Result(int files, int opened, long millis, boolean complete) {
    }

    /**
     * Opens every regular file below {@code root} and reads its first byte; files that cannot be
     * read are skipped. Symbolic links are not followed.
     *
     * @param root    the folder
     * @param timeout when to stop
     * @return what was done; never throws
     */
    static Result warm(Path root, Duration timeout) {
        long start = System.nanoTime();
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            // whatever was found is still opened
        }
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger late = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS, runnable -> {
            Thread thread = new Thread(runnable, "fxt-warm-up");
            thread.setDaemon(true);
            return thread;
        });
        long deadline = start + timeout.toNanos();
        for (Path file : files) {
            pool.execute(() -> {
                if (System.nanoTime() - deadline >= 0) {
                    late.incrementAndGet();
                } else if (touch(file)) {
                    opened.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        boolean complete;
        try {
            complete = pool.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            complete = false;
        }
        pool.shutdownNow();
        return new Result(files.size(), opened.get(), (System.nanoTime() - start) / 1_000_000L,
                complete && late.get() == 0);
    }

    private static boolean touch(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            in.read();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
