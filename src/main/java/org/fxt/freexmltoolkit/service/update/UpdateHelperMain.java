package org.fxt.freexmltoolkit.service.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

public class UpdateHelperMain {

    private static final int WAIT_TIMEOUT_SECONDS = 60;
    private static final int COPY_RETRY_COUNT = 10;
    private static final int COPY_RETRY_DELAY_MS = 2000;
    private static final int POST_EXIT_COOLDOWN_MS = 5000;

    public static void main(String[] args) {
        int exitCode = 1;
        UpdateHelperLogger logger = null;
        try {
            if (args.length == 0) {
                System.err.println("Missing config file path.");
                System.exit(1);
            }

            Path configPath = Path.of(args[0]);
            UpdateHelperConfig config = UpdateHelperConfig.load(configPath);
            logger = new UpdateHelperLogger(config.getLogFile());
            logger.info("UpdateHelper started");
            logger.info("Config path: " + configPath);
            logger.info("App dir: " + config.getAppDir());
            logger.info("Update dir: " + config.getUpdateDir());
            logger.info("Launcher: " + config.getLauncher());
            logger.info("Platform: " + config.getPlatform());
            logger.info("Process name: " + config.getProcessName());

            validateConfig(config, logger);

            waitForAppExit(config.getProcessName(), logger);

            Path updateAppDir = findUpdateAppDir(config.getUpdateDir(), config.getLauncher().getFileName().toString(), logger);
            logger.info("Update source dir: " + updateAppDir);

            copyUpdate(updateAppDir, config.getAppDir(), logger);

            cleanupUpdateDir(config.getUpdateDir(), logger);

            restartApplication(config.getLauncher(), logger);

            logger.info("UpdateHelper completed successfully");
            exitCode = 0;
        } catch (Exception e) {
            if (logger != null) {
                logger.error("UpdateHelper failed: " + e.getMessage());
            } else {
                System.err.println("UpdateHelper failed: " + e.getMessage());
            }
        } finally {
            System.exit(exitCode);
        }
    }

    private static void validateConfig(UpdateHelperConfig config, UpdateHelperLogger logger) throws IOException {
        if (!Files.exists(config.getAppDir())) {
            throw new IOException("Application directory does not exist: " + config.getAppDir());
        }
        if (!Files.exists(config.getUpdateDir())) {
            throw new IOException("Update directory does not exist: " + config.getUpdateDir());
        }
        if (Files.isDirectory(config.getLauncher())) {
            throw new IOException("Launcher path is a directory: " + config.getLauncher());
        }
        logger.info("Config validation passed");
    }

    private static void waitForAppExit(String processName, UpdateHelperLogger logger) throws InterruptedException {
        logger.info("Waiting for application to exit...");
        int waited = 0;
        while (waited < WAIT_TIMEOUT_SECONDS) {
            if (!isProcessRunning(processName) && !isJavaProcessRunningWithApp(processName, logger)) {
                logger.info("Application process not found");
                logger.info("Waiting " + (POST_EXIT_COOLDOWN_MS / 1000) + "s cooldown for file handles to be released...");
                Thread.sleep(POST_EXIT_COOLDOWN_MS);
                logger.info("Cooldown completed");
                return;
            }
            Thread.sleep(1000);
            waited++;
            logger.info("Application still running (" + waited + "s)");
        }
        throw new IllegalStateException("Timeout waiting for application to exit after " + WAIT_TIMEOUT_SECONDS + " seconds");
    }

    private static boolean isProcessRunning(String processName) {
        String nameLower = processName.toLowerCase();
        return ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .map(info -> info.command().orElse(""))
                .map(cmd -> Path.of(cmd).getFileName().toString().toLowerCase())
                .anyMatch(cmd -> cmd.equals(nameLower));
    }

    /**
     * On Windows with jpackage, the launcher (FreeXmlToolkit.exe) exits quickly while a
     * separate java.exe process holds the JAR file. This method checks for any Java
     * processes that might be running the application.
     */
    private static boolean isJavaProcessRunningWithApp(String processName, UpdateHelperLogger logger) {
        String appBaseName = processName.replace(".exe", "").replace(".EXE", "").toLowerCase();
        boolean found = ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .filter(info -> {
                    String cmd = info.command().orElse("").toLowerCase();
                    String cmdLine = info.commandLine().orElse("").toLowerCase();
                    // Check for java.exe or javaw.exe with our app name in the command line
                    boolean isJava = cmd.contains("java");
                    boolean hasAppName = cmdLine.contains(appBaseName);
                    return isJava && hasAppName;
                })
                .findAny()
                .isPresent();
        if (found) {
            logger.info("Found Java process running with " + appBaseName);
        }
        return found;
    }

    static Path findUpdateAppDir(Path updateDir, String launcherName, UpdateHelperLogger logger) throws IOException {
        if (!Files.exists(updateDir)) {
            throw new IOException("Update directory does not exist: " + updateDir);
        }
        int maxDepth = 3;
        Optional<Path> match = Files.find(updateDir, maxDepth,
                (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equalsIgnoreCase(launcherName))
                .findFirst();
        if (match.isPresent()) {
            return match.get().getParent();
        }
        logger.error("Could not locate launcher in update directory. Launcher name: " + launcherName);
        throw new IOException("Launcher not found in update directory: " + launcherName);
    }

    private static void copyUpdate(Path sourceDir, Path destinationDir, UpdateHelperLogger logger) throws IOException {
        logger.info("Copying update files...");
        FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = destinationDir.resolve(sourceDir.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = destinationDir.resolve(sourceDir.relativize(file));
                copyWithRetry(file, targetFile, logger);
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(sourceDir, visitor);
        logger.info("Copy completed");
    }

    private static void copyWithRetry(Path source, Path target, UpdateHelperLogger logger) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= COPY_RETRY_COUNT; attempt++) {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return;
            } catch (IOException e) {
                lastError = e;
                logger.warn("Copy failed (attempt " + attempt + "): " + source + " -> " + target + " (" + e.getMessage() + ")");
                sleepQuietly(COPY_RETRY_DELAY_MS);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Unknown copy failure");
    }

    private static void cleanupUpdateDir(Path updateDir, UpdateHelperLogger logger) {
        logger.info("Cleaning up update directory: " + updateDir);
        try {
            if (Files.exists(updateDir)) {
                Files.walk(updateDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete: " + path + " (" + e.getMessage() + ")");
                            }
                        });
            }
        } catch (IOException e) {
            logger.warn("Cleanup failed: " + e.getMessage());
        }
    }

    private static void restartApplication(Path launcher, UpdateHelperLogger logger) throws IOException {
        logger.info("Restarting application...");
        ProcessBuilder pb;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", launcher.toString());
        } else {
            pb = new ProcessBuilder(launcher.toString());
        }
        pb.start();
        logger.info("Restart command executed");
    }

    private static void sleepQuietly(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static class UpdateHelperLogger {
        private final Path logFile;

        UpdateHelperLogger(Path logFile) {
            this.logFile = logFile;
        }

        void info(String message) {
            write("INFO", message);
        }

        void warn(String message) {
            write("WARN", message);
        }

        void error(String message) {
            write("ERROR", message);
        }

        private void write(String level, String message) {
            String line = LocalDateTime.now() + " [" + level + "] " + message + System.lineSeparator();
            try {
                Files.writeString(logFile, line, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println(line);
            }
        }
    }
}
