package org.fxt.freexmltoolkit.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps one running application per user: a second launch (e.g. "Open with" on a file while
 * the application is already open) hands its files to the running instance and exits before
 * JavaFX starts.
 *
 * <p>The first instance holds an OS file lock on {@code instance.lock} (released by the OS when
 * the process dies, so it never goes stale) and listens on a loopback port. Port and a random
 * token are written to {@code instance.port}. A later instance that cannot get the lock sends the
 * token followed by one absolute path per line and an empty line; the primary answers {@code OK}.
 * Connections with a wrong token are dropped. Any failure leads to a normal, independent start.
 */
public final class SingleInstance implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(SingleInstance.class);

    static final String LOCK_FILE = "instance.lock";
    static final String PORT_FILE = "instance.port";
    static final String ACK = "OK";
    /**
     * Environment variable set when the application relaunches itself (POSIX updater) while the
     * old process is still exiting: the new process waits for the lock instead of forwarding.
     */
    public static final String RELAUNCH_ENV = "FXT_RELAUNCH";
    private static final long RELAUNCH_WAIT_MILLIS = 15_000;
    private static final long RELAUNCH_POLL_MILLIS = 250;
    private static final int TIMEOUT_MILLIS = 2000;
    private static final int PORT_FILE_RETRIES = 20;
    private static final long PORT_FILE_RETRY_MILLIS = 100;

    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ServerSocket server;
    private final String token;
    private final Consumer<List<Path>> onFiles;
    private volatile Runnable onActivate = () -> { };

    private SingleInstance(Path directory, FileChannel lockChannel, FileLock lock, ServerSocket server,
                           String token, Consumer<List<Path>> onFiles) {
        this.directory = directory;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.server = server;
        this.token = token;
        this.onFiles = onFiles;
    }

    /**
     * Whether single-instance mode applies: only in the packaged application (so development
     * runs and tests stay independent) and not with {@code -Dfxt.multiInstance=true}.
     */
    public static boolean isEnabled() {
        return System.getProperty("jpackage.app-path") != null && !Boolean.getBoolean("fxt.multiInstance");
    }

    /**
     * Tries to become the primary instance. On success the loopback listener is running and
     * forwarded files are passed to {@code onFiles} (on the listener thread).
     *
     * @param directory per-user directory for the lock and port file
     * @param onFiles   receives files forwarded by later instances
     * @return the primary instance, or {@code null} if another instance holds the lock or the
     *         listener could not be started
     */
    public static SingleInstance claim(Path directory, Consumer<List<Path>> onFiles) {
        FileChannel channel = null;
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(directory.resolve(LOCK_FILE),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null; // held by this JVM already
            }
            if (lock == null) {
                channel.close();
                return null;
            }
            ServerSocket server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            String token = HexFormat.of().formatHex(random);
            SingleInstance instance = new SingleInstance(directory, channel, lock, server, token, onFiles);
            instance.writePortFile();
            instance.startListener();
            logger.info("Single-instance listener on port {}", server.getLocalPort());
            return instance;
        } catch (IOException | RuntimeException e) {
            logger.warn("Single-instance mode unavailable: {}", e.toString());
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // nothing left to clean up
                }
            }
            return null;
        }
    }

    /**
     * Like {@link #claim}, but keeps retrying for up to {@code waitMillis} while another
     * instance holds the lock — used after a self-relaunch, where the old process is exiting.
     */
    public static SingleInstance claimWaiting(Path directory, Consumer<List<Path>> onFiles, long waitMillis) {
        long deadline = System.currentTimeMillis() + waitMillis;
        SingleInstance instance = claim(directory, onFiles);
        while (instance == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(RELAUNCH_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            instance = claim(directory, onFiles);
        }
        return instance;
    }

    /**
     * Startup entry point: becomes the primary instance, or forwards the file arguments to the
     * running one.
     *
     * @return the primary instance, {@code null} otherwise; {@link Startup#forwarded()} tells
     *         whether this process should exit because the running instance took over
     */
    public static Startup start(Path directory, List<String> args, Consumer<List<Path>> onFiles) {
        if ("1".equals(System.getenv(RELAUNCH_ENV))) {
            // Never forward to the process that is just shutting down for the update.
            return new Startup(claimWaiting(directory, onFiles, RELAUNCH_WAIT_MILLIS), false);
        }
        SingleInstance instance = claim(directory, onFiles);
        if (instance != null) {
            return new Startup(instance, false);
        }
        return new Startup(null, forward(directory, fileArguments(args)));
    }

    /**
     * Outcome of {@link #start}.
     *
     * @param primary   this process's primary instance, or {@code null}
     * @param forwarded whether the running instance took the files over (exit now)
     */
    public record Startup(SingleInstance primary, boolean forwarded) {
    }

    /**
     * Hands files to the running primary instance.
     *
     * @param directory the directory passed to {@link #claim}
     * @param files     absolute paths to open (may be empty: the primary is only brought to front)
     * @return whether the primary instance acknowledged them
     */
    public static boolean forward(Path directory, List<Path> files) {
        String[] endpoint = readPortFile(directory.resolve(PORT_FILE));
        if (endpoint == null) {
            return false;
        }
        try (Socket socket = new Socket()) {
            int port = Integer.parseInt(endpoint[0]);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            out.write(endpoint[1] + "\n");
            for (Path file : files) {
                out.write(file.toAbsolutePath() + "\n");
            }
            out.write("\n");
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            boolean acknowledged = ACK.equals(in.readLine());
            logger.info("Forwarded {} file(s) to the running instance: {}", files.size(), acknowledged);
            return acknowledged;
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not reach the running instance: {}", e.toString());
            return false;
        }
    }

    /**
     * Resolves command-line arguments that name existing files to absolute paths, ignoring
     * anything else (stray launcher arguments, unparseable strings).
     */
    public static List<Path> fileArguments(List<String> args) {
        List<Path> files = new ArrayList<>();
        for (String arg : args) {
            try {
                Path path = Path.of(arg);
                if (Files.isRegularFile(path)) {
                    files.add(path.toAbsolutePath());
                }
            } catch (InvalidPathException e) {
                logger.debug("Ignoring unparseable argument: {}", arg);
            }
        }
        return files;
    }

    /**
     * Sets what happens after a later instance contacted this one (bring the window to front).
     * Called on the listener thread.
     */
    public void setActivationHandler(Runnable handler) {
        onActivate = handler != null ? handler : () -> { };
    }

    /** Stops listening, removes the port file and releases the lock. */
    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException e) {
            logger.debug("Closing the single-instance listener failed", e);
        }
        try {
            Files.deleteIfExists(directory.resolve(PORT_FILE));
        } catch (IOException e) {
            logger.debug("Could not delete the port file", e);
        }
        try {
            lock.release();
            lockChannel.close();
        } catch (IOException e) {
            logger.debug("Could not release the instance lock", e);
        }
    }

    private void writePortFile() throws IOException {
        Path target = directory.resolve(PORT_FILE);
        Path temp = Files.createTempFile(directory, "instance", ".tmp");
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // Windows: the user profile directory is private already
        }
        Files.writeString(temp, server.getLocalPort() + "\n" + token + "\n", StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Reads {@code port} and {@code token}; waits briefly because a primary that has just
     * taken the lock may not have written the file yet.
     */
    private static String[] readPortFile(Path portFile) {
        for (int attempt = 0; attempt < PORT_FILE_RETRIES; attempt++) {
            try {
                if (Files.exists(portFile)) {
                    List<String> lines = Files.readAllLines(portFile, StandardCharsets.UTF_8);
                    if (lines.size() >= 2 && !lines.get(0).isBlank() && !lines.get(1).isBlank()) {
                        return new String[]{lines.get(0).trim(), lines.get(1).trim()};
                    }
                }
                Thread.sleep(PORT_FILE_RETRY_MILLIS);
            } catch (IOException e) {
                logger.debug("Could not read the port file", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private void startListener() {
        Thread listener = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    if (!server.isClosed()) {
                        logger.debug("Single-instance connection failed", e);
                    }
                } catch (RuntimeException e) {
                    logger.warn("Could not handle a forwarded open request", e);
                }
            }
        }, "SingleInstanceListener");
        listener.setDaemon(true);
        listener.start();
    }

    private void handle(Socket socket) throws IOException {
        socket.setSoTimeout(TIMEOUT_MILLIS);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String received = in.readLine();
        if (received == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8))) {
            logger.warn("Rejected a single-instance connection with an invalid token");
            return;
        }
        List<Path> files = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            try {
                files.add(Path.of(line));
            } catch (InvalidPathException e) {
                logger.debug("Ignoring unparseable forwarded path: {}", line);
            }
        }
        Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        out.write(ACK + "\n");
        out.flush();
        logger.info("Another launch forwarded {} file(s)", files.size());
        if (!files.isEmpty()) {
            onFiles.accept(files);
        }
        onActivate.run();
    }
}
