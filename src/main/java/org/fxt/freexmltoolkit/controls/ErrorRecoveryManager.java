package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Comprehensive error recovery and resilience manager for the Schematron Editor.
 * Provides auto-save, crash recovery, retry mechanisms, and graceful degradation.
 */
public class ErrorRecoveryManager {

    private static final Logger logger = LogManager.getLogger(ErrorRecoveryManager.class);

    private static ErrorRecoveryManager instance;

    // Auto-save configuration
    private static final String AUTO_SAVE_DIR = System.getProperty("user.home") + "/.freexmltoolkit/autosave";
    private static final int AUTO_SAVE_INTERVAL_SECONDS = 30;
    private static final int MAX_AUTO_SAVE_FILES = 10;

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    // Recovery state
    private final Map<String, String> autoSaveData = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastModifiedTimes = new ConcurrentHashMap<>();
    private final Timer autoSaveTimer;
    private boolean autoSaveEnabled = true;

    // Operation tracking for recovery
    private final Map<String, Integer> operationAttempts = new ConcurrentHashMap<>();
    private final Set<String> criticalErrors = ConcurrentHashMap.newKeySet();

    private ErrorRecoveryManager() {
        // Ensure auto-save directory exists
        createAutoSaveDirectory();

        // Initialize auto-save timer
        autoSaveTimer = new Timer("AutoSave-Timer", true);
        scheduleAutoSave();

        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        logger.info("ErrorRecoveryManager initialized with auto-save every {} seconds", AUTO_SAVE_INTERVAL_SECONDS);
    }

    public static synchronized ErrorRecoveryManager getInstance() {
        if (instance == null) {
            instance = new ErrorRecoveryManager();
        }
        return instance;
    }

    /**
     * Register content for auto-save protection
     */
    public void registerForAutoSave(String fileId, String content) {
        if (!autoSaveEnabled || content == null) {
            return;
        }

        autoSaveData.put(fileId, content);
        lastModifiedTimes.put(fileId, LocalDateTime.now());

        logger.debug("Registered content for auto-save: {} ({} chars)", fileId, content.length());
    }

    /**
     * Execute operation with retry mechanism
     */
    public <T> CompletableFuture<T> executeWithRetry(String operationId, Function<Integer, T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = operationAttempts.getOrDefault(operationId, 0);
            Exception lastException = null;

            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    T result = operation.apply(attempt);
                    operationAttempts.remove(operationId); // Clear on success

                    if (attempt > 1) {
                        logger.info("Operation {} succeeded on attempt {}/{}", operationId, attempt, MAX_RETRY_ATTEMPTS);
                    }

                    return result;

                } catch (Exception e) {
                    lastException = e;
                    logger.warn("Operation {} failed on attempt {}/{}: {}",
                            operationId, attempt, MAX_RETRY_ATTEMPTS, e.getMessage());

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            // All attempts failed
            operationAttempts.put(operationId, MAX_RETRY_ATTEMPTS);
            criticalErrors.add(operationId);

            logger.error("Operation {} failed after {} attempts", operationId, MAX_RETRY_ATTEMPTS);
            throw new RuntimeException("Operation failed after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
        });
    }

    /**
     * Recover unsaved content on startup
     */
    public Map<String, String> recoverUnsavedContent() {
        Map<String, String> recoveredContent = new HashMap<>();

        try {
            Path autoSavePath = Paths.get(AUTO_SAVE_DIR);
            if (!Files.exists(autoSavePath)) {
                return recoveredContent;
            }

            Files.list(autoSavePath)
                    .filter(path -> path.toString().endsWith(".autosave"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString().replace(".autosave", "");
                            recoveredContent.put(fileName, content);

                            logger.info("Recovered auto-save content for: {}", fileName);
                        } catch (IOException e) {
                            logger.warn("Failed to read auto-save file: {}", path, e);
                        }
                    });

        } catch (IOException e) {
            logger.error("Failed to recover auto-save content", e);
        }

        return recoveredContent;
    }

    /**
     * Show recovery dialog to user
     */
    public void showRecoveryDialog(Map<String, String> recoveredContent) {
        if (recoveredContent.isEmpty()) {
            return;
        }

        Platform.runLater(() -> {
            Alert recoveryAlert = new Alert(Alert.AlertType.INFORMATION);
            recoveryAlert.setTitle("Crash Recovery");
            recoveryAlert.setHeaderText("Unsaved content recovered");

            StringBuilder contentInfo = new StringBuilder();
            contentInfo.append("The following files have recovered content:\n\n");

            for (String fileName : recoveredContent.keySet()) {
                contentInfo.append("• ").append(fileName).append("\n");
            }

            contentInfo.append("\nWould you like to restore this content?");

            TextArea textArea = new TextArea(contentInfo.toString());
            textArea.setEditable(false);
            textArea.setPrefRowCount(10);
            textArea.setPrefColumnCount(50);

            recoveryAlert.getDialogPane().setContent(textArea);
            recoveryAlert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            recoveryAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    logger.info("User chose to restore recovered content");
                    // Notify recovery listeners here
                } else {
                    logger.info("User chose not to restore recovered content");
                    clearAutoSaveFiles();
                }
            });
        });
    }

    /**
     * Handle graceful degradation for non-critical failures
     */
    public <T> T handleWithGracefulDegradation(String operationId,
                                               Function<Void, T> primaryOperation,
                                               Function<Exception, T> fallbackOperation) {
        try {
            return primaryOperation.apply(null);

        } catch (Exception e) {
            logger.warn("Primary operation {} failed, attempting graceful degradation: {}",
                    operationId, e.getMessage());

            try {
                T result = fallbackOperation.apply(e);
                logger.info("Graceful degradation successful for operation: {}", operationId);
                return result;

            } catch (Exception fallbackException) {
                logger.error("Both primary and fallback operations failed for: {}", operationId);
                throw new RuntimeException("Complete operation failure for " + operationId, fallbackException);
            }
        }
    }

    /**
     * Validate file before critical operations
     */
    public boolean validateFileIntegrity(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            // Basic file accessibility check
            Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);

            // Check if file is locked
            if (!file.canRead() || !file.canWrite()) {
                logger.warn("File permissions issue detected: {}", file.getPath());
                return false;
            }

            return true;

        } catch (IOException e) {
            logger.error("File integrity validation failed for: {}", file.getPath(), e);
            return false;
        }
    }

    /**
     * Create safe backup before risky operations
     */
    public File createSafetyBackup(File originalFile) {
        if (originalFile == null || !originalFile.exists()) {
            return null;
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupName = originalFile.getName() + ".backup_" + timestamp;
            File backupFile = new File(originalFile.getParent(), backupName);

            Files.copy(originalFile.toPath(), backupFile.toPath());

            logger.info("Safety backup created: {}", backupFile.getPath());
            return backupFile;

        } catch (IOException e) {
            logger.error("Failed to create safety backup for: {}", originalFile.getPath(), e);
            return null;
        }
    }

    /**
     * Get system health status
     */
    public HealthStatus getSystemHealthStatus() {
        int criticalErrorCount = criticalErrors.size();
        int activeAutoSaves = autoSaveData.size();

        boolean isHealthy = criticalErrorCount == 0;
        String status = isHealthy ? "Healthy" : "Degraded";

        return new HealthStatus(isHealthy, status, criticalErrorCount, activeAutoSaves);
    }

    // ========== Private Methods ==========

    private void createAutoSaveDirectory() {
        try {
            Path autoSavePath = Paths.get(AUTO_SAVE_DIR);
            if (!Files.exists(autoSavePath)) {
                Files.createDirectories(autoSavePath);
                logger.info("Created auto-save directory: {}", AUTO_SAVE_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create auto-save directory", e);
            autoSaveEnabled = false;
        }
    }

    private void scheduleAutoSave() {
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
                                              @Override
                                              public void run() {
                                                  performAutoSave();
                                              }
                                          }, TimeUnit.SECONDS.toMillis(AUTO_SAVE_INTERVAL_SECONDS),
                TimeUnit.SECONDS.toMillis(AUTO_SAVE_INTERVAL_SECONDS));
    }

    private void performAutoSave() {
        if (!autoSaveEnabled || autoSaveData.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : autoSaveData.entrySet()) {
            try {
                String fileId = entry.getKey();
                String content = entry.getValue();

                File autoSaveFile = new File(AUTO_SAVE_DIR, fileId + ".autosave");
                Files.writeString(autoSaveFile.toPath(), content);

                logger.debug("Auto-saved: {}", fileId);

            } catch (IOException e) {
                logger.warn("Auto-save failed for: {}", entry.getKey(), e);
            }
        }

        // Cleanup old auto-save files
        cleanupOldAutoSaveFiles();
    }

    private void cleanupOldAutoSaveFiles() {
        try {
            Path autoSavePath = Paths.get(AUTO_SAVE_DIR);
            Files.list(autoSavePath)
                    .filter(path -> path.toString().endsWith(".autosave"))
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .skip(MAX_AUTO_SAVE_FILES)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.debug("Deleted old auto-save file: {}", path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete old auto-save file: {}", path, e);
                        }
                    });

        } catch (IOException e) {
            logger.warn("Failed to cleanup old auto-save files", e);
        }
    }

    private void clearAutoSaveFiles() {
        try {
            Path autoSavePath = Paths.get(AUTO_SAVE_DIR);
            Files.list(autoSavePath)
                    .filter(path -> path.toString().endsWith(".autosave"))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete auto-save file: {}", path, e);
                        }
                    });

        } catch (IOException e) {
            logger.warn("Failed to clear auto-save files", e);
        }
    }

    private void shutdown() {
        logger.info("Shutting down ErrorRecoveryManager");

        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
        }

        // Perform final auto-save
        performAutoSave();
    }

    // ========== Inner Classes ==========

    public record HealthStatus(boolean healthy, String status, int criticalErrors, int activeAutoSaves) {

        @Override
            public String toString() {
                return String.format("HealthStatus{healthy=%s, status='%s', criticalErrors=%d, activeAutoSaves=%d}",
                        healthy, status, criticalErrors, activeAutoSaves);
            }
        }
}