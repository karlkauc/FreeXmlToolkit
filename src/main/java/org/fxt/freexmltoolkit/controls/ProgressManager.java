package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Centralized progress management for long-running operations.
 * Provides consistent progress indication across the Schematron Editor.
 */
public class ProgressManager {

    private static final Logger logger = LogManager.getLogger(ProgressManager.class);

    private static ProgressManager instance;
    private final ExecutorService executor;

    // UI Components for progress indication
    private Stage progressStage;
    private ProgressIndicator progressIndicator;
    private Label progressLabel;

    private ProgressManager() {
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("ProgressManager-Worker");
            return thread;
        });
        logger.info("ProgressManager initialized");
    }

    public static synchronized ProgressManager getInstance() {
        if (instance == null) {
            instance = new ProgressManager();
        }
        return instance;
    }

    /**
     * Execute a task with progress indication
     */
    public <T> CompletableFuture<T> executeWithProgress(String taskName,
                                                        Supplier<T> task,
                                                        Consumer<T> onSuccess,
                                                        Consumer<Throwable> onError) {

        logger.info("Starting background task: {}", taskName);

        // Show progress dialog
        Platform.runLater(() -> showProgressDialog(taskName));

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return task.get();
                    } catch (Exception e) {
                        logger.error("Task failed: {}", taskName, e);
                        throw new RuntimeException(e);
                    }
                }, executor)
                .whenComplete((result, throwable) -> {
                    Platform.runLater(() -> {
                        hideProgressDialog();

                        if (throwable != null) {
                            if (onError != null) {
                                onError.accept(throwable);
                            } else {
                                showErrorDialog("Task Failed",
                                        "An error occurred during: " + taskName + "\n" + throwable.getMessage());
                            }
                        } else {
                            if (onSuccess != null) {
                                onSuccess.accept(result);
                            }
                        }
                    });
                });
    }

    /**
     * Execute a task with custom progress updates
     */
    public <T> void executeWithProgressUpdates(String taskName,
                                               Task<T> task,
                                               Consumer<T> onSuccess,
                                               Consumer<Throwable> onError) {

        logger.info("Starting task with progress updates: {}", taskName);

        // Set up progress binding
        task.progressProperty().addListener((obs, oldProgress, newProgress) -> {
            Platform.runLater(() -> {
                if (progressIndicator != null) {
                    progressIndicator.setProgress(newProgress.doubleValue());
                }
            });
        });

        task.messageProperty().addListener((obs, oldMessage, newMessage) -> {
            Platform.runLater(() -> {
                if (progressLabel != null) {
                    progressLabel.setText(newMessage);
                }
            });
        });

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                hideProgressDialog();
                if (onSuccess != null) {
                    onSuccess.accept(task.getValue());
                }
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                hideProgressDialog();
                Throwable exception = task.getException();
                if (onError != null) {
                    onError.accept(exception);
                } else {
                    showErrorDialog("Task Failed",
                            "An error occurred during: " + taskName + "\n" +
                                    (exception != null ? exception.getMessage() : "Unknown error"));
                }
            });
        });

        // Show progress dialog and start task
        Platform.runLater(() -> showProgressDialog(taskName));
        executor.execute(task);
    }

    /**
     * Show progress dialog
     */
    private void showProgressDialog(String taskName) {
        if (progressStage != null && progressStage.isShowing()) {
            return; // Already showing
        }

        progressStage = new Stage();
        progressStage.initStyle(StageStyle.UTILITY);
        progressStage.setTitle("Processing...");
        progressStage.setResizable(false);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(-1); // Indeterminate by default

        progressLabel = new Label(taskName);
        progressLabel.setStyle("-fx-font-size: 12px;");

        VBox content = new VBox(10);
        content.getChildren().addAll(progressLabel, progressIndicator);
        content.setStyle("-fx-padding: 20px; -fx-alignment: center;");

        javafx.scene.Scene scene = new javafx.scene.Scene(content, 250, 100);
        progressStage.setScene(scene);

        // Center on screen
        progressStage.centerOnScreen();
        progressStage.show();

        logger.debug("Progress dialog shown for task: {}", taskName);
    }

    /**
     * Hide progress dialog
     */
    private void hideProgressDialog() {
        if (progressStage != null && progressStage.isShowing()) {
            progressStage.close();
            progressStage = null;
            progressIndicator = null;
            progressLabel = null;
            logger.debug("Progress dialog hidden");
        }
    }

    /**
     * Show error dialog
     */
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Update progress for currently running task
     */
    public void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            if (progressIndicator != null) {
                progressIndicator.setProgress(progress);
            }
            if (progressLabel != null && message != null) {
                progressLabel.setText(message);
            }
        });
    }

    /**
     * Check if a task is currently running
     */
    public boolean isTaskRunning() {
        return progressStage != null && progressStage.isShowing();
    }

    /**
     * Shutdown the progress manager
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            logger.info("ProgressManager executor shutdown");
        }

        Platform.runLater(() -> hideProgressDialog());
    }

    /**
     * Get the executor service for manual task submission
     */
    public ExecutorService getExecutor() {
        return executor;
    }
}